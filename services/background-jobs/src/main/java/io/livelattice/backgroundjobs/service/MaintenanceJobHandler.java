package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceJobHandler implements JobHandler {

    private static final Set<String> TYPES = Set.of(
        "CLEANUP", "DIGEST", "TEMP_CLEANUP", "SNAPSHOT_COMPACTION",
        "PARTITION_MAINTENANCE", "QUOTA_RECONCILIATION", "INDEX_SYNC", "WEBHOOK"
    );

    private final WorkspaceCleanupHandler workspaceCleanupHandler;
    private final SnapshotCompactionHandler snapshotCompactionHandler;
    private final PartitionMaintenanceHandler partitionMaintenanceHandler;
    private final QuotaReconciliationHandler quotaReconciliationHandler;
    private final TempFileCleanupHandler tempFileCleanupHandler;
    private final WebhookRetryHandler webhookRetryHandler;
    private final IndexSyncHandler indexSyncHandler;
    private final DigestJobHandler digestJobHandler;
    private JobService jobService;

    public MaintenanceJobHandler(WorkspaceCleanupHandler workspaceCleanupHandler,
                                 SnapshotCompactionHandler snapshotCompactionHandler,
                                 PartitionMaintenanceHandler partitionMaintenanceHandler,
                                 QuotaReconciliationHandler quotaReconciliationHandler,
                                 TempFileCleanupHandler tempFileCleanupHandler,
                                 WebhookRetryHandler webhookRetryHandler,
                                 IndexSyncHandler indexSyncHandler,
                                 DigestJobHandler digestJobHandler) {
        this.workspaceCleanupHandler = workspaceCleanupHandler;
        this.snapshotCompactionHandler = snapshotCompactionHandler;
        this.partitionMaintenanceHandler = partitionMaintenanceHandler;
        this.quotaReconciliationHandler = quotaReconciliationHandler;
        this.tempFileCleanupHandler = tempFileCleanupHandler;
        this.webhookRetryHandler = webhookRetryHandler;
        this.indexSyncHandler = indexSyncHandler;
        this.digestJobHandler = digestJobHandler;
    }

    @Override
    public String type() {
        return "MAINTENANCE";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
        workspaceCleanupHandler.setJobService(jobService);
        snapshotCompactionHandler.setJobService(jobService);
        partitionMaintenanceHandler.setJobService(jobService);
        quotaReconciliationHandler.setJobService(jobService);
        tempFileCleanupHandler.setJobService(jobService);
        webhookRetryHandler.setJobService(jobService);
        indexSyncHandler.setJobService(jobService);
        digestJobHandler.setJobService(jobService);
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        JobResult result = dispatch(job, execution);
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        return result;
    }

    private JobResult dispatch(JobDefinition job, JobExecution execution) {
        return switch (job.getJobType()) {
            case "CLEANUP" -> workspaceCleanupHandler.handle(job, execution);
            case "SNAPSHOT_COMPACTION" -> snapshotCompactionHandler.handle(job, execution);
            case "PARTITION_MAINTENANCE" -> partitionMaintenanceHandler.handle(job, execution);
            case "QUOTA_RECONCILIATION" -> quotaReconciliationHandler.handle(job, execution);
            case "TEMP_CLEANUP" -> tempFileCleanupHandler.handle(job, execution);
            case "WEBHOOK" -> webhookRetryHandler.handle(job, execution);
            case "INDEX_SYNC" -> indexSyncHandler.handle(job, execution);
            case "DIGEST" -> digestJobHandler.handle(job, execution);
            default -> fallbackResult(job);
        };
    }

    private JobResult fallbackResult(JobDefinition job) {
        JobResult result = new JobResult();
        result.getData().put("handled", true);
        result.getData().put("maintenanceType", job.getJobType());
        return result;
    }

    public boolean supports(String type) {
        return TYPES.contains(type);
    }
}
