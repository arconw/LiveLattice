package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class QuotaReconciliationHandler implements JobHandler {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private JobService jobService;

    public QuotaReconciliationHandler(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String type() {
        return "QUOTA_RECONCILIATION";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        List<Map<String, Object>> rows = queryCanvasesCount();
        int reconciled = 0;
        for (Map<String, Object> row : rows) {
            Object workspaceId = row.get("workspace_id");
            Number count = (Number) row.get("count");
            if (workspaceId != null && count != null) {
                redisTemplate.opsForValue().set(
                    "quota:canvases:" + workspaceId,
                    String.valueOf(count.longValue())
                );
                reconciled++;
            }
        }
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        JobResult result = new JobResult();
        result.getData().put("reconciledWorkspaces", reconciled);
        return result;
    }

    private List<Map<String, Object>> queryCanvasesCount() {
        if (!hasCanvasesTable()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
            SELECT workspace_id, COUNT(*) AS count
            FROM canvases
            WHERE deleted_at IS NULL
            GROUP BY workspace_id
            """);
    }

    private boolean hasCanvasesTable() {
        try {
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1 FROM information_schema.tables WHERE table_name = 'canvases' LIMIT 1",
                Integer.class
            );
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
}
