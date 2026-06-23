package io.livelattice.notifications.controller;

import io.livelattice.notifications.config.NotificationsProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AdminClient adminClient;
    private final NotificationsProperties properties;

    public HealthController(JdbcTemplate jdbcTemplate,
                            StringRedisTemplate redisTemplate,
                            AdminClient adminClient,
                            NotificationsProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.adminClient = adminClient;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "notifications",
            "version", "0.1.0"
        ));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, String> database = checkDatabase();
        Map<String, String> cache = checkCache();
        Map<String, String> queue = checkQueue();
        boolean healthy = "healthy".equals(database.get("status"))
            && "healthy".equals(cache.get("status"))
            && ("healthy".equals(queue.get("status")) || "disabled".equals(queue.get("status")));
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("status", healthy ? "UP" : "DEGRADED");
        readiness.put("database", database);
        readiness.put("cache", cache);
        readiness.put("queue", queue);
        HttpStatus status = healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(readiness);
    }

    private Map<String, String> checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Map.of("status", result != null && result == 1 ? "healthy" : "unhealthy");
        } catch (Exception ex) {
            return Map.of("status", "unhealthy", "error", ex.getMessage());
        }
    }

    private Map<String, String> checkCache() {
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            return Map.of("status", "healthy", "ping", ping == null ? "PONG" : ping);
        } catch (Exception ex) {
            return Map.of("status", "unhealthy", "error", ex.getMessage());
        }
    }

    private Map<String, String> checkQueue() {
        if (!properties.getKafka().isEnabled()) {
            return Map.of("status", "disabled");
        }
        try {
            adminClient.describeCluster().nodes().get(3, TimeUnit.SECONDS);
            return Map.of("status", "healthy");
        } catch (Exception ex) {
            return Map.of("status", "unhealthy", "error", ex.getMessage());
        }
    }
}
