package io.livelattice.core.controller;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final String SERVICE = "core";
    private static final String VERSION = "0.1.0";
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final Instant startedAt;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AdminClient adminClient;
    private final MinioClient minioClient;
    private final String snapshotBucket;
    private final String clickHouseUrl;
    private final String clickHouseUser;
    private final String clickHousePassword;
    private volatile CachedReadiness cachedReadiness;

    public HealthController() {
        this.startedAt = Instant.now();
        this.jdbcTemplate = null;
        this.redisTemplate = null;
        this.adminClient = null;
        this.minioClient = null;
        this.snapshotBucket = null;
        this.clickHouseUrl = null;
        this.clickHouseUser = null;
        this.clickHousePassword = null;
    }

    @Autowired
    public HealthController(JdbcTemplate jdbcTemplate,
                            StringRedisTemplate redisTemplate,
                            AdminClient adminClient,
                            @Value("${livelattice.snapshots.endpoint:http://localhost:9100}") String snapshotEndpoint,
                            @Value("${livelattice.snapshots.access-key:local}") String snapshotAccessKey,
                            @Value("${livelattice.snapshots.secret-key:local}") String snapshotSecretKey,
                            @Value("${livelattice.snapshots.bucket:livelattice-snapshots}") String snapshotBucket,
                            @Value("${livelattice.analytics.clickhouse-url:jdbc:clickhouse://localhost:8123/livelattice}") String clickHouseUrl,
                            @Value("${livelattice.analytics.clickhouse-user:default}") String clickHouseUser,
                            @Value("${livelattice.analytics.clickhouse-password:}") String clickHousePassword) {
        this.startedAt = Instant.now();
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.adminClient = adminClient;
        this.minioClient = MinioClient.builder()
            .endpoint(snapshotEndpoint)
            .credentials(snapshotAccessKey, snapshotSecretKey)
            .build();
        this.snapshotBucket = snapshotBucket;
        this.clickHouseUrl = clickHouseUrl;
        this.clickHouseUser = clickHouseUser;
        this.clickHousePassword = clickHousePassword;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(base("UP"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        CachedReadiness readiness = cached();
        return ResponseEntity.status(readiness.healthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(readiness.body());
    }

    private CachedReadiness cached() {
        Instant now = Instant.now();
        CachedReadiness current = cachedReadiness;
        if (current != null && now.isBefore(current.expiresAt())) {
            return current;
        }
        CachedReadiness next = evaluate(now);
        cachedReadiness = next;
        return next;
    }

    private CachedReadiness evaluate(Instant now) {
        Map<String, Object> database = checkDatabase();
        Map<String, Object> cache = checkCache();
        Map<String, Object> queue = checkQueue();
        Map<String, Object> storage = checkStorage();
        Map<String, Object> analytics = checkAnalytics();
        boolean healthy = healthy(database) && healthy(cache) && healthy(queue) && healthy(storage) && healthy(analytics);
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", database);
        checks.put("cache", cache);
        checks.put("queue", queue);
        checks.put("storage", storage);
        checks.put("analytics", analytics);
        Map<String, Object> body = base(healthy ? "UP" : "DEGRADED");
        body.put("checks", checks);
        body.putAll(checks);
        return new CachedReadiness(body, healthy, now.plus(CACHE_TTL));
    }

    private Map<String, Object> base(String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("service", SERVICE);
        body.put("version", VERSION);
        body.put("uptimeSeconds", Duration.between(startedAt, Instant.now()).toSeconds());
        return body;
    }

    private Map<String, Object> checkDatabase() {
        if (jdbcTemplate == null) {
            return healthyCheck("not_configured");
        }
        long started = System.nanoTime();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1 ? healthyCheck(started) : unhealthyCheck("unexpected_result", started);
        } catch (Exception ex) {
            return unhealthyCheck(ex, started);
        }
    }

    private Map<String, Object> checkCache() {
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            return healthyCheck("not_configured");
        }
        long started = System.nanoTime();
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            Map<String, Object> check = healthyCheck(started);
            check.put("ping", ping == null ? "PONG" : ping);
            return check;
        } catch (Exception ex) {
            return unhealthyCheck(ex, started);
        }
    }

    private Map<String, Object> checkQueue() {
        if (adminClient == null) {
            return healthyCheck("not_configured");
        }
        long started = System.nanoTime();
        try {
            int nodes = adminClient.describeCluster().nodes().get(3, TimeUnit.SECONDS).size();
            Map<String, Object> check = healthyCheck(started);
            check.put("nodes", nodes);
            return check;
        } catch (Exception ex) {
            return unhealthyCheck(ex, started);
        }
    }

    private Map<String, Object> checkStorage() {
        if (minioClient == null || snapshotBucket == null) {
            return healthyCheck("not_configured");
        }
        long started = System.nanoTime();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(snapshotBucket).build());
            Map<String, Object> check = exists ? healthyCheck(started) : unhealthyCheck("bucket_missing", started);
            check.put("bucket", snapshotBucket);
            return check;
        } catch (Exception ex) {
            return unhealthyCheck(ex, started);
        }
    }

    private Map<String, Object> checkAnalytics() {
        if (clickHouseUrl == null || clickHouseUser == null || clickHousePassword == null) {
            return healthyCheck("not_configured");
        }
        long started = System.nanoTime();
        try (Connection connection = DriverManager.getConnection(clickHouseUrl, clickHouseUser, clickHousePassword)) {
            boolean valid = connection.isValid(3);
            return valid ? healthyCheck(started) : unhealthyCheck("connection_invalid", started);
        } catch (Exception ex) {
            return unhealthyCheck(ex, started);
        }
    }

    private Map<String, Object> healthyCheck(long started) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("status", "healthy");
        check.put("latencyMs", elapsedMillis(started));
        return check;
    }

    private Map<String, Object> healthyCheck(String detail) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("status", "healthy");
        check.put("detail", detail);
        return check;
    }

    private Map<String, Object> unhealthyCheck(Exception ex, long started) {
        return unhealthyCheck(errorMessage(ex), started);
    }

    private Map<String, Object> unhealthyCheck(String error, long started) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("status", "unhealthy");
        check.put("error", error);
        check.put("latencyMs", elapsedMillis(started));
        return check;
    }

    private long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private boolean healthy(Map<String, Object> check) {
        return "healthy".equals(check.get("status"));
    }

    private String errorMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private record CachedReadiness(Map<String, Object> body, boolean healthy, Instant expiresAt) {
    }
}
