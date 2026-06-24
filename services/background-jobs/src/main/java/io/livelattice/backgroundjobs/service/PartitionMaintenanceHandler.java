package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PartitionMaintenanceHandler implements JobHandler {

    private final JdbcTemplate jdbcTemplate;
    private JobService jobService;

    public PartitionMaintenanceHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "PARTITION_MAINTENANCE";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        List<String> ensured = ensureAuditPartitions();
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        JobResult result = new JobResult();
        result.getData().put("ensuredPartitions", ensured.size());
        result.getData().put("partitions", ensured);
        return result;
    }

    private List<String> ensureAuditPartitions() {
        if (!hasEnsureAuditPartitionFunction()) {
            return List.of();
        }
        LocalDate now = LocalDate.now();
        String current = jdbcTemplate.queryForObject("SELECT ensure_audit_partition(?)", String.class, now);
        String next = jdbcTemplate.queryForObject("SELECT ensure_audit_partition(?)", String.class, now.plusMonths(1));
        return java.util.stream.Stream.of(current, next)
            .filter(partition -> partition != null && !partition.isBlank())
            .distinct()
            .toList();
    }

    private boolean hasEnsureAuditPartitionFunction() {
        try {
            Integer result = jdbcTemplate.queryForObject(
                """
                SELECT 1
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE p.proname = 'ensure_audit_partition'
                AND n.nspname = 'public'
                LIMIT 1
                """,
                Integer.class
            );
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
}
