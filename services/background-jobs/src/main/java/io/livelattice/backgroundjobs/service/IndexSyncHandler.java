package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IndexSyncHandler implements JobHandler {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private JobService jobService;

    public IndexSyncHandler(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String type() {
        return "INDEX_SYNC";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        int synced = syncCanvases();
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        JobResult result = new JobResult();
        result.getData().put("synced", true);
        result.getData().put("indexedCanvases", synced);
        return result;
    }

    private int syncCanvases() {
        if (!hasCanvasesTable()) {
            return 0;
        }
        return jdbcTemplate.queryForList("""
            SELECT id, workspace_id, title, updated_at
            FROM canvases
            WHERE deleted_at IS NULL
            AND updated_at > now() - interval '5 minutes'
            """).stream().mapToInt(row -> {
            Object id = row.get("id");
            Object workspaceId = row.get("workspace_id");
            Object title = row.get("title");
            Object updatedAt = row.get("updated_at");
            if (id != null) {
                String redisKey = "search:index:canvas:" + id;
                redisTemplate.opsForValue().set(redisKey, toIndexValue(id, workspaceId, title, updatedAt));
            }
            return 1;
        }).sum();
    }

    private String toIndexValue(Object id, Object workspaceId, Object title, Object updatedAt) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("id", id != null ? id.toString() : "");
        value.put("workspaceId", workspaceId != null ? workspaceId.toString() : "");
        value.put("title", title != null ? title.toString() : "");
        value.put("updatedAt", updatedAt != null ? updatedAt.toString() : "");
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
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
