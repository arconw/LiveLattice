package io.livelattice.auditlog.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    private final AuditProperties properties;
    private final AuthProperties authProperties;

    public StorageConfig(AuditProperties properties, AuthProperties authProperties) {
        this.properties = properties;
        this.authProperties = authProperties;
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint(properties.getStorageEndpoint())
            .credentials(authProperties.getStorageAccessKey(), authProperties.getStorageSecretKey())
            .build();
    }

    @Bean
    public MinioBucketInitializer minioBucketInitializer(MinioClient minioClient, AuditProperties auditProperties) {
        return new MinioBucketInitializer(minioClient, auditProperties.getExportBucket(), auditProperties.getArchiveBucket());
    }

    public static class MinioBucketInitializer {
        private final MinioClient minioClient;
        private final String exportBucket;
        private final String archiveBucket;

        public MinioBucketInitializer(MinioClient minioClient, String exportBucket, String archiveBucket) {
            this.minioClient = minioClient;
            this.exportBucket = exportBucket;
            this.archiveBucket = archiveBucket;
        }

        public void initialize() {
            ensureBucket(exportBucket, "audit export");
            ensureBucket(archiveBucket, "audit archive");
        }

        private void ensureBucket(String bucket, String label) {
            try {
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to initialize " + label + " bucket", ex);
            }
        }
    }

}
