package io.livelattice.importexport.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public MinioClient minioClient(ImportExportProperties properties) {
        return MinioClient.builder()
            .endpoint(properties.storageEndpoint())
            .credentials(properties.storageAccessKey(), properties.storageSecretKey())
            .build();
    }
}
