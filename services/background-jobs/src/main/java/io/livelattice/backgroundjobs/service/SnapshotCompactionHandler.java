package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SnapshotCompactionHandler implements JobHandler {

    private final JdbcTemplate jdbcTemplate;
    private JobService jobService;

    public SnapshotCompactionHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "SNAPSHOT_COMPACTION";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        List<UUID> removed = compactSnapshots();
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        JobResult result = new JobResult();
        result.getData().put("removedSnapshots", removed.size());
        return result;
    }

    private List<UUID> compactSnapshots() {
        if (!hasSnapshotAtColumn()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
            DELETE FROM canvas_snapshots
            WHERE id IN (
                SELECT id FROM (
                    SELECT id, row_number() OVER (PARTITION BY canvas_id ORDER BY snapshot_at DESC) AS rn
                    FROM canvas_snapshots
                ) ranked
                WHERE rn > 100
            )
            RETURNING id
            """, UUID.class);
    }

    private boolean hasSnapshotAtColumn() {
        try {
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1 FROM information_schema.columns WHERE table_name = 'canvas_snapshots' AND column_name = 'snapshot_at' LIMIT 1",
                Integer.class
            );
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
}
