package io.livelattice.importexport.controller;

import io.livelattice.importexport.config.ImportExportProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final StringRedisTemplate redisTemplate;
    private final MinioClient minioClient;
    private final AdminClient adminClient;
    private final ImportExportProperties properties;

    public HealthController(StringRedisTemplate redisTemplate,
                            MinioClient minioClient,
                            AdminClient adminClient,
                            ImportExportProperties properties) {
        this.redisTemplate = redisTemplate;
        this.minioClient = minioClient;
        this.adminClient = adminClient;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "import-export",
            "version", "0.1.0"
        ));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> storage = checkStorage();
        Map<String, String> queue = checkQueue();
        Map<String, String> cache = checkRedis();
        boolean healthy = "healthy".equals(storage.get("status"))
            && "healthy".equals(queue.get("status"))
            && "healthy".equals(cache.get("status"));
        Map<String, Object> readiness = new java.util.LinkedHashMap<>();
        readiness.put("status", healthy ? "UP" : "DEGRADED");
        readiness.put("storage", storage);
        readiness.put("queue", queue);
        readiness.put("cache", cache);
        return ResponseEntity.ok(readiness);
    }

    private Map<String, Object> checkStorage() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.storageBucket()).build());
            return Map.of("status", exists ? "healthy" : "unhealthy", "bucket", properties.storageBucket());
        } catch (Exception e) {
            return Map.of("status", "unhealthy", "error", e.getMessage());
        }
    }

    private Map<String, String> checkQueue() {
        try {
            adminClient.describeCluster().nodes().get(3, TimeUnit.SECONDS);
            return Map.of("status", "healthy");
        } catch (Exception e) {
            return Map.of("status", "unhealthy", "error", e.getMessage());
        }
    }

    private Map<String, String> checkRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return Map.of("status", "healthy", "ping", pong != null ? pong : "PONG");
        } catch (Exception e) {
            return Map.of("status", "unhealthy", "error", e.getMessage());
        }
    }
}
