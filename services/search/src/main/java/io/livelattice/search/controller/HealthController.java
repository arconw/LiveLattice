package io.livelattice.search.controller;

import io.livelattice.search.exception.SearchBackendException;
import io.livelattice.search.dto.IndexStatus;
import io.livelattice.search.opensearch.IndexManager;
import io.livelattice.search.opensearch.OpenSearchGateway;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class HealthController {

    private final OpenSearchGateway gateway;
    private final AdminClient adminClient;
    private final StringRedisTemplate redisTemplate;
    private final IndexManager indexManager;

    public HealthController(OpenSearchGateway gateway,
                            AdminClient adminClient,
                            StringRedisTemplate redisTemplate,
                            IndexManager indexManager) {
        this.gateway = gateway;
        this.adminClient = adminClient;
        this.redisTemplate = redisTemplate;
        this.indexManager = indexManager;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "search",
            "version", "0.1.0"
        ));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, String> search = checkSearch();
        Map<String, String> queue = checkQueue();
        Map<String, String> cache = checkCache();
        Map<String, String> indexes = checkIndexes();
        boolean healthy = "healthy".equals(search.get("status"))
            && "healthy".equals(queue.get("status"))
            && "healthy".equals(cache.get("status"))
            && "healthy".equals(indexes.get("status"));
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("status", healthy ? "UP" : "DEGRADED");
        readiness.put("search", search);
        readiness.put("queue", queue);
        readiness.put("cache", cache);
        readiness.put("indexes", indexes);
        HttpStatus status = healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(readiness);
    }

    private Map<String, String> checkSearch() {
        try {
            gateway.request("GET", "/", null, 200);
            return Map.of("status", "healthy");
        } catch (SearchBackendException ex) {
            return Map.of("status", "unhealthy", "error", ex.getMessage());
        }
    }

    private Map<String, String> checkQueue() {
        try {
            adminClient.describeCluster().nodes().get(3, TimeUnit.SECONDS);
            return Map.of("status", "healthy");
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

    private Map<String, String> checkIndexes() {
        try {
            List<IndexStatus> statuses = indexManager.indexStatuses();
            long ready = statuses.stream().filter(IndexStatus::exists).count();
            boolean healthy = ready == statuses.size() && !statuses.isEmpty();
            return Map.of(
                "status", healthy ? "healthy" : "unhealthy",
                "ready", Long.toString(ready),
                "expected", Integer.toString(statuses.size())
            );
        } catch (Exception ex) {
            return Map.of("status", "unhealthy", "error", ex.getMessage());
        }
    }
}
