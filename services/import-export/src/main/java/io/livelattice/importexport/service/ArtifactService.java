package io.livelattice.importexport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.config.ImportExportProperties;
import io.livelattice.importexport.exception.NotFoundException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class ArtifactService {

    private final MinioClient minioClient;
    private final ImportExportProperties properties;
    private final ObjectMapper objectMapper;

    public ArtifactService(MinioClient minioClient, ImportExportProperties properties, ObjectMapper objectMapper) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String storeArtifact(UUID workspaceId, UUID jobId, String name, byte[] content, String contentType) {
        try {
            String path = String.format("exports/%s/%s/%s", workspaceId, jobId, name);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.storageBucket())
                .object(path)
                .contentType(contentType)
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            return path;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store artifact");
        }
    }

    public String storeCanvasJson(UUID workspaceId, UUID canvasId, Object content) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8);
            String path = String.format("canvases/%s/%s.json", workspaceId, canvasId);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.storageBucket())
                .object(path)
                .contentType("application/json")
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .build());
            return path;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store canvas JSON");
        }
    }

    public String stageImportFile(UUID workspaceId, UUID jobId, String filename, byte[] content) {
        try {
            String path = String.format("imports/%s/%s/%s", workspaceId, jobId, filename);
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.storageBucket())
                .object(path)
                .contentType("application/octet-stream")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
            return path;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stage import file");
        }
    }

    public byte[] loadArtifact(String path) {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
            .bucket(properties.storageBucket())
            .object(path)
            .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new NotFoundException("Artifact not found: " + path);
        }
    }

    public String signedUrl(String path) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(properties.storageBucket())
                .object(path)
                .method(Method.GET)
                .expiry(properties.storageUrlExpiryMinutes(), TimeUnit.MINUTES)
                .build());
        } catch (Exception e) {
            throw new NotFoundException("Artifact not found");
        }
    }
}
