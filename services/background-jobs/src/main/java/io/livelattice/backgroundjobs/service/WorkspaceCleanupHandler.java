package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceCleanupHandler implements JobHandler {

    private final JdbcTemplate jdbcTemplate;
    private JobService jobService;

    public WorkspaceCleanupHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "CLEANUP";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        int deleted = cleanupDeletedWorkspaces();
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        JobResult result = new JobResult();
        result.getData().put("deletedWorkspaces", deleted);
        return result;
    }

    private int cleanupDeletedWorkspaces() {
        if (!hasDeletedAtColumn()) {
            return 0;
        }
        List<String> workspaceIds = jdbcTemplate.queryForList("""
            SELECT id::text
            FROM workspaces
            WHERE deleted_at IS NOT NULL
            AND deleted_at < now() - interval '30 days'
            """, String.class);
        int deleted = 0;
        for (String workspaceId : workspaceIds) {
            deleteWorkspaceReferences(workspaceId);
            deleted += jdbcTemplate.update("DELETE FROM workspaces WHERE id::text = ?", workspaceId);
        }
        return deleted;
    }

    private void deleteWorkspaceReferences(String workspaceId) {
        if (hasTable("widgets") && hasTable("dashboards")) {
            jdbcTemplate.update("""
                DELETE FROM widgets
                WHERE dashboard_id IN (
                    SELECT id FROM dashboards WHERE workspace_id::text = ?
                )
                """, workspaceId);
        }
        deleteByWorkspace("dashboards", workspaceId);
        deleteByWorkspace("data_sources", workspaceId);
        deleteByWorkspace("canvas_templates", workspaceId);
        if (hasTable("comments") && hasTable("canvases")) {
            jdbcTemplate.update("""
                DELETE FROM comments
                WHERE canvas_id IN (
                    SELECT id FROM canvases WHERE workspace_id::text = ?
                )
                """, workspaceId);
        }
        if (hasTable("canvas_snapshots") && hasTable("canvases")) {
            jdbcTemplate.update("""
                DELETE FROM canvas_snapshots
                WHERE canvas_id IN (
                    SELECT id FROM canvases WHERE workspace_id::text = ?
                )
                """, workspaceId);
        }
        deleteByWorkspace("canvases", workspaceId);
        deleteByWorkspace("api_keys", workspaceId);
        deleteByWorkspace("import_export_canvas", workspaceId);
        deleteByWorkspace("workspace_members", workspaceId);
    }

    private void deleteByWorkspace(String table, String workspaceId) {
        if (hasTable(table)) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE workspace_id::text = ?", workspaceId);
        }
    }

    private boolean hasDeletedAtColumn() {
        try {
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1 FROM information_schema.columns WHERE table_name = 'workspaces' AND column_name = 'deleted_at' LIMIT 1",
                Integer.class
            );
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasTable(String table) {
        try {
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1 FROM information_schema.tables WHERE table_name = ? LIMIT 1",
                Integer.class,
                table
            );
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
}
