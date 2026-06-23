package io.livelattice.importexport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livelattice.importexport")
public record ImportExportProperties(
    long maxFileSizeBytes,
    long syncThresholdBytes,
    String storageEndpoint,
    String storageAccessKey,
    String storageSecretKey,
    String storageBucket,
    int storageUrlExpiryMinutes,
    String jobTopic,
    boolean clamavEnabled,
    String clamavHost,
    int clamavPort
) {
}
