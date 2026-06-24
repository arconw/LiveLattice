package io.livelattice.auditlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livelattice.audit")
public class AuditProperties {

    private String kafkaGroupId = "livelattice-audit-log";
    private String kafkaTopics = "canvas.created,canvas.updated,canvas.deleted,comment.added,comment.deleted";
    private boolean kafkaEnabled = true;
    private int verificationBatchSize = 1000;
    private String exportBucket = "audit-exports";
    private String exportPrefix = "audit-exports";
    private String archiveBucket = "audit-archive";
    private String archivePrefix = "audit-archive";
    private String storageEndpoint = "http://localhost:9100";
    private String storageAccessKey = "livelattice";
    private String storageSecretKey = "livelattice_dev_password";
    private long exportProcessingIntervalMs = 5000;
    private String retentionCron = "0 0 3 * * *";
    private int retentionHotDays = 90;

    public String getKafkaGroupId() {
        return kafkaGroupId;
    }

    public void setKafkaGroupId(String kafkaGroupId) {
        this.kafkaGroupId = kafkaGroupId;
    }

    public String getKafkaTopics() {
        return kafkaTopics;
    }

    public void setKafkaTopics(String kafkaTopics) {
        this.kafkaTopics = kafkaTopics;
    }

    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }

    public void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    public int getVerificationBatchSize() {
        return verificationBatchSize;
    }

    public void setVerificationBatchSize(int verificationBatchSize) {
        this.verificationBatchSize = verificationBatchSize;
    }

    public String getExportBucket() {
        return exportBucket;
    }

    public void setExportBucket(String exportBucket) {
        this.exportBucket = exportBucket;
    }

    public String getExportPrefix() {
        return exportPrefix;
    }

    public void setExportPrefix(String exportPrefix) {
        this.exportPrefix = exportPrefix;
    }

    public String getArchiveBucket() {
        return archiveBucket;
    }

    public void setArchiveBucket(String archiveBucket) {
        this.archiveBucket = archiveBucket;
    }

    public String getArchivePrefix() {
        return archivePrefix;
    }

    public void setArchivePrefix(String archivePrefix) {
        this.archivePrefix = archivePrefix;
    }

    public String getStorageEndpoint() {
        return storageEndpoint;
    }

    public void setStorageEndpoint(String storageEndpoint) {
        this.storageEndpoint = storageEndpoint;
    }

    public String getStorageAccessKey() {
        return storageAccessKey;
    }

    public void setStorageAccessKey(String storageAccessKey) {
        this.storageAccessKey = storageAccessKey;
    }

    public String getStorageSecretKey() {
        return storageSecretKey;
    }

    public void setStorageSecretKey(String storageSecretKey) {
        this.storageSecretKey = storageSecretKey;
    }

    public long getExportProcessingIntervalMs() {
        return exportProcessingIntervalMs;
    }

    public void setExportProcessingIntervalMs(long exportProcessingIntervalMs) {
        this.exportProcessingIntervalMs = exportProcessingIntervalMs;
    }

    public String getRetentionCron() {
        return retentionCron;
    }

    public void setRetentionCron(String retentionCron) {
        this.retentionCron = retentionCron;
    }

    public int getRetentionHotDays() {
        return retentionHotDays;
    }

    public void setRetentionHotDays(int retentionHotDays) {
        this.retentionHotDays = retentionHotDays;
    }
}
