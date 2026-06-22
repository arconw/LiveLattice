package io.livelattice.core.service.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.core.exception.ConflictException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioSnapshotContentStore implements SnapshotContentStore {

    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final String bucket;

    public MinioSnapshotContentStore(@Value("${livelattice.snapshots.endpoint:http://localhost:9100}") String endpoint,
                                     @Value("${livelattice.snapshots.access-key:livelattice}") String accessKey,
                                     @Value("${livelattice.snapshots.secret-key:livelattice_dev_password}") String secretKey,
                                     @Value("${livelattice.snapshots.bucket:livelattice-snapshots}") String bucket) {
        this.objectMapper = new ObjectMapper();
        this.minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
        this.bucket = bucket;
    }

    @Override
    public String put(UUID workspaceId, UUID canvasId, long version, Map<String, Object> content) {
        try {
            ensureBucket();
            String path = String.format("snapshots/%s/%s/%d.json", workspaceId, canvasId, version);
            byte[] bytes = objectMapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(path)
                .contentType("application/json")
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .build());
            return path;
        } catch (Exception e) {
            throw new ConflictException("Failed to store snapshot content");
        }
    }

    @Override
    public Map<String, Object> get(String path) {
        try (var input = minioClient.getObject(GetObjectArgs.builder()
            .bucket(bucket)
            .object(path)
            .build())) {
            return objectMapper.readValue(input, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ConflictException("Failed to load snapshot content");
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
