package io.livelattice.search.controller;

import io.livelattice.search.exception.SearchBackendException;
import io.livelattice.search.dto.IndexStatus;
import io.livelattice.search.opensearch.IndexManager;
import io.livelattice.search.opensearch.OpenSearchGateway;
import java.time.Duration;
import java.time.Instant;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class HealthController {

    private static final String SERVICE = "search";
    private static final String VERSION = "0.1.0";
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final OpenSearchGateway gateway;
    private final AdminClient adminClient;
    private final StringRedisTemplate redisTemplate;
    private final IndexManager indexManager;
    private final JdbcTemplate jdbcTemplate;
    private final Instant startedAt = Instant.now();
    private volatile CachedReadiness cachedReadiness;

    public HealthController(OpenSearchGateway gateway,
                            AdminClient adminClient,
                            StringRedisTemplate redisTemplate,
                            IndexManager indexManager,
                            JdbcTemplate jdbcTemplate) {
        this.gateway = gateway;
        this.adminClient = adminClient;
        this.redisTemplate = redisTemplate;
        this.indexManager = indexManager;
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
        Map<String, Object> search = checkSearch();
        Map<String, Object> queue = checkQueue();
        Map<String, Object> cache = checkCache();
        Map<String, Object> indexes = checkIndexes();
        boolean healthy = healthy(database) && healthy(search) && healthy(queue) && healthy(cache) && healthy(indexes);
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", database);
        checks.put("search", search);
        checks.put("queue", queue);
        checks.put("cache", cache);
        checks.put("indexes", indexes);
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
        long started = System.nanoTime();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1 ? healthyCheck(started) : unhealthyCheck("unexpected_result", started);
        } catch (Exception ex) {
            return unhealthyCheck(ex, started);
        }
    }

    private Map<String, Object> checkSearch() {
        long started = System.nanoTime();
        try {
            gateway.request("GET", "/", null, 200);
            return healthyCheck(started);
        } catch (SearchBackendException ex) {
            return unhealthyCheck(ex, started);
        }
    }

    private Map<String, Object> checkQueue() {
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

    private Map<String, Object> checkCache() {
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

    private Map<String, Object> checkIndexes() {
        long started = System.nanoTime();
        try {
            List<IndexStatus> statuses = indexManager.indexStatuses();
            long ready = statuses.stream().filter(IndexStatus::exists).count();
            boolean healthy = ready == statuses.size() && !statuses.isEmpty();
            Map<String, Object> check = healthy ? healthyCheck(started) : unhealthyCheck("indexes_not_ready", started);
            check.put("ready", ready);
            check.put("expected", statuses.size());
            return check;
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
