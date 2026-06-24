package io.livelattice.backgroundjobs.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livelattice.jobs")
public class BackgroundJobsProperties {

    private final Worker worker = new Worker();
    private final ImportExport importExport = new ImportExport();
    private final Cleanup cleanup = new Cleanup();
    private final Digest digest = new Digest();
    private final Export export = new Export();
    private final Import imp = new Import();
    private int deadLetterAlertThreshold = 100;

    public Worker getWorker() {
        return worker;
    }

    public ImportExport getImportExport() {
        return importExport;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public Digest getDigest() {
        return digest;
    }

    public Export getExport() {
        return export;
    }

    public Import getImport() {
        return imp;
    }

    public int getDeadLetterAlertThreshold() {
        return deadLetterAlertThreshold;
    }

    public void setDeadLetterAlertThreshold(int deadLetterAlertThreshold) {
        this.deadLetterAlertThreshold = deadLetterAlertThreshold;
    }

    public static class Worker {
        private boolean enabled = true;
        private String workerId = "background-jobs-1";
        private int pollTimeoutSeconds = 5;
        private int progressReportSeconds = 5;
        private int shutdownTimeoutSeconds = 30;
        private int defaultMaxRetries = 3;
        private int defaultRetryDelaySeconds = 60;
        private int downstreamPollMaxAttempts = 180;
        private long downstreamPollIntervalMs = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWorkerId() {
            return workerId;
        }

        public void setWorkerId(String workerId) {
            this.workerId = workerId;
        }

        public int getPollTimeoutSeconds() {
            return pollTimeoutSeconds;
        }

        public void setPollTimeoutSeconds(int pollTimeoutSeconds) {
            this.pollTimeoutSeconds = pollTimeoutSeconds;
        }

        public int getProgressReportSeconds() {
            return progressReportSeconds;
        }

        public void setProgressReportSeconds(int progressReportSeconds) {
            this.progressReportSeconds = progressReportSeconds;
        }

        public int getShutdownTimeoutSeconds() {
            return shutdownTimeoutSeconds;
        }

        public void setShutdownTimeoutSeconds(int shutdownTimeoutSeconds) {
            this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        }

        public int getDefaultMaxRetries() {
            return defaultMaxRetries;
        }

        public void setDefaultMaxRetries(int defaultMaxRetries) {
            this.defaultMaxRetries = defaultMaxRetries;
        }

        public int getDefaultRetryDelaySeconds() {
            return defaultRetryDelaySeconds;
        }

        public void setDefaultRetryDelaySeconds(int defaultRetryDelaySeconds) {
            this.defaultRetryDelaySeconds = defaultRetryDelaySeconds;
        }

        public int getDownstreamPollMaxAttempts() {
            return downstreamPollMaxAttempts;
        }

        public void setDownstreamPollMaxAttempts(int downstreamPollMaxAttempts) {
            this.downstreamPollMaxAttempts = downstreamPollMaxAttempts;
        }

        public long getDownstreamPollIntervalMs() {
            return downstreamPollIntervalMs;
        }

        public void setDownstreamPollIntervalMs(long downstreamPollIntervalMs) {
            this.downstreamPollIntervalMs = downstreamPollIntervalMs;
        }
    }

    public static class ImportExport {
        private String jobTopic = "import-export-jobs";
        private String serviceUrl = "http://localhost:8083";
        private String internalSecret = "livelattice_internal_dev_secret";

        public String getJobTopic() {
            return jobTopic;
        }

        public void setJobTopic(String jobTopic) {
            this.jobTopic = jobTopic;
        }

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        public String getInternalSecret() {
            return internalSecret;
        }

        public void setInternalSecret(String internalSecret) {
            this.internalSecret = internalSecret;
        }
    }

    public static class Cleanup {
        private int concurrency = 1;
        private String cron = "0 0 3 * * *";

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class Digest {
        private int concurrency = 1;
        private String cron = "0 0 * * * *";

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class Export {
        private int concurrency = 5;
        private int maxExecutionMinutes = 30;

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getMaxExecutionMinutes() {
            return maxExecutionMinutes;
        }

        public void setMaxExecutionMinutes(int maxExecutionMinutes) {
            this.maxExecutionMinutes = maxExecutionMinutes;
        }
    }

    public static class Import {
        private int concurrency = 3;
        private int maxExecutionMinutes = 60;

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getMaxExecutionMinutes() {
            return maxExecutionMinutes;
        }

        public void setMaxExecutionMinutes(int maxExecutionMinutes) {
            this.maxExecutionMinutes = maxExecutionMinutes;
        }
    }

    public int concurrencyForType(String type) {
        return switch (type) {
            case "EXPORT" -> export.getConcurrency();
            case "IMPORT" -> imp.getConcurrency();
            case "CLEANUP" -> cleanup.getConcurrency();
            case "DIGEST" -> digest.getConcurrency();
            default -> 1;
        };
    }

    public Map<String, Object> toTypeConfig(String type) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("concurrency", concurrencyForType(type));
        return config;
    }
}
