package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.model.JobDefinition;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RetryManager {

    private static final int[] BACKOFF_MINUTES = {1, 5, 15, 60};

    private final BackgroundJobsProperties properties;

    public RetryManager(BackgroundJobsProperties properties) {
        this.properties = properties;
    }

    public Instant nextRetryAt(JobDefinition job) {
        int attempt = Math.max(0, job.getRetryCount());
        int index = Math.min(attempt, BACKOFF_MINUTES.length - 1);
        int minutes = BACKOFF_MINUTES[index];
        int delaySeconds = job.getRetryDelaySeconds() != null && job.getRetryDelaySeconds() > 0
            ? Math.max(job.getRetryDelaySeconds(), minutes * 60)
            : minutes * 60;
        return Instant.now().plus(Duration.ofSeconds(delaySeconds));
    }

    public boolean canRetry(JobDefinition job) {
        int max = job.getMaxRetries() != null ? job.getMaxRetries() : properties.getWorker().getDefaultMaxRetries();
        return job.getRetryCount() < max;
    }

    public int maxRetries(JobDefinition job) {
        return job.getMaxRetries() != null ? job.getMaxRetries() : properties.getWorker().getDefaultMaxRetries();
    }
}
