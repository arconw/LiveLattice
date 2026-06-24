package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobPayload;
import io.livelattice.backgroundjobs.model.JobStatus;
import io.livelattice.backgroundjobs.repository.JobDefinitionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledJobService {

    private final BackgroundJobsProperties properties;
    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobService jobService;

    public ScheduledJobService(BackgroundJobsProperties properties,
                               JobDefinitionRepository jobDefinitionRepository,
                               JobService jobService) {
        this.properties = properties;
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobService = jobService;
    }

    @Scheduled(cron = "${livelattice.jobs.cleanup.cron:0 0 3 * * *}")
    public void scheduleWorkspaceCleanup() {
        schedule("CLEANUP");
    }

    @Scheduled(cron = "${livelattice.jobs.digest.cron:0 0 * * * *}")
    public void scheduleDigest() {
        schedule("DIGEST");
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduleSnapshotCompaction() {
        schedule("SNAPSHOT_COMPACTION");
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void schedulePartitionMaintenance() {
        schedule("PARTITION_MAINTENANCE");
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void scheduleQuotaReconciliation() {
        schedule("QUOTA_RECONCILIATION");
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void scheduleWebhookRetry() {
        schedule("WEBHOOK");
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduleTempFileCleanup() {
        schedule("TEMP_CLEANUP");
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void scheduleIndexSync() {
        schedule("INDEX_SYNC");
    }

    private void schedule(String type) {
        JobDefinition job = new JobDefinition();
        job.setId(UUID.randomUUID());
        job.setJobType(type);
        job.setPayload(new JobPayload());
        job.setPriority(10);
        job.setMaxRetries(properties.getWorker().getDefaultMaxRetries());
        job.setRetryDelaySeconds(properties.getWorker().getDefaultRetryDelaySeconds());
        job.setRetryCount(0);
        job.setStatus(JobStatus.SCHEDULED);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job.setScheduledAt(Instant.now());
        jobDefinitionRepository.save(job);
        transitionToQueued(job);
    }

    private void transitionToQueued(JobDefinition job) {
        job.setStatus(JobStatus.QUEUED);
        job.setUpdatedAt(Instant.now());
        jobDefinitionRepository.save(job);
        jobService.pushQueue(job);
    }
}
