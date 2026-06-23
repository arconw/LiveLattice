package io.livelattice.importexport.service;

import io.livelattice.importexport.config.ImportExportProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class BucketInitializer {

    private final MinioClient minioClient;
    private final ImportExportProperties properties;

    public BucketInitializer(MinioClient minioClient, ImportExportProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.storageBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.storageBucket()).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize storage bucket", e);
        }
    }
}
