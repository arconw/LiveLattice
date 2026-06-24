package io.livelattice.backgroundjobs.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final String SERVICE = "background-jobs";
    private static final String VERSION = "0.1.0";
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;
    private final AdminClient adminClient;
    private final JdbcTemplate jdbcTemplate;
    private final Instant startedAt = Instant.now();
    private volatile CachedReadiness cachedReadiness;

    public HealthController(StringRedisTemplate redisTemplate, AdminClient adminClient) {
        this(redisTemplate, adminClient, null);
    }

    @Autowired
    public HealthController(StringRedisTemplate redisTemplate, AdminClient adminClient, JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.adminClient = adminClient;
        this.jdbcTemplate = jdbcTemplate;
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
        Map<String, Object> cache = checkRedis();
        Map<String, Object> queue = checkQueue();
        boolean healthy = healthy(database) && healthy(cache) && healthy(queue);
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", database);
        checks.put("cache", cache);
        checks.put("queue", queue);
        Map<String, Object> readiness = base(healthy ? "UP" : "DEGRADED");
        readiness.put("checks", checks);
        readiness.putAll(checks);
        return new CachedReadiness(readiness, healthy, now.plus(CACHE_TTL));
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

    private Map<String, Object> checkRedis() {
        long started = System.nanoTime();
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            Map<String, Object> check = healthyCheck(started);
            check.put("ping", pong != null ? pong : "PONG");
            return check;
        } catch (Exception e) {
            return unhealthyCheck(e, started);
        }
    }

    private Map<String, Object> checkQueue() {
        long started = System.nanoTime();
        try {
            int nodes = adminClient.describeCluster().nodes().get(3, TimeUnit.SECONDS).size();
            Map<String, Object> check = healthyCheck(started);
            check.put("nodes", nodes);
            return check;
        } catch (Exception e) {
            return unhealthyCheck(e, started);
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
