package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.model.JobDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryManagerTest {

    @Test
    void exponentialBackoffIncreasesWithRetryCount() {
        BackgroundJobsProperties properties = new BackgroundJobsProperties();
        RetryManager manager = new RetryManager(properties);
        JobDefinition job = new JobDefinition();
        job.setRetryCount(0);
        job.setRetryDelaySeconds(60);
        Instant first = manager.nextRetryAt(job);
        job.setRetryCount(1);
        Instant second = manager.nextRetryAt(job);
        job.setRetryCount(2);
        Instant third = manager.nextRetryAt(job);
        job.setRetryCount(3);
        Instant fourth = manager.nextRetryAt(job);
        assertTrue(Duration.between(Instant.now(), first).toMinutes() <= 1);
        assertTrue(Duration.between(Instant.now(), second).toMinutes() >= 4);
        assertTrue(Duration.between(Instant.now(), third).toMinutes() >= 14);
        assertTrue(Duration.between(Instant.now(), fourth).toMinutes() >= 59);
    }

    @Test
    void canRetryUntilMaxExceeded() {
        BackgroundJobsProperties properties = new BackgroundJobsProperties();
        RetryManager manager = new RetryManager(properties);
        JobDefinition job = new JobDefinition();
        job.setMaxRetries(2);
        job.setRetryCount(1);
        assertTrue(manager.canRetry(job));
        job.setRetryCount(2);
        assertFalse(manager.canRetry(job));
        assertEquals(2, manager.maxRetries(job));
    }
}
