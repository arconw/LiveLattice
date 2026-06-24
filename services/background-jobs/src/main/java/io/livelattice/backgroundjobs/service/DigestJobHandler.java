package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DigestJobHandler implements JobHandler {

    private final JdbcTemplate jdbcTemplate;
    private JobService jobService;

    public DigestJobHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "DIGEST";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        int grouped = groupPendingNotifications();
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        JobResult result = new JobResult();
        result.getData().put("handled", true);
        result.getData().put("groupedRecipients", grouped);
        return result;
    }

    private int groupPendingNotifications() {
        if (!hasNotificationsTable()) {
            return 0;
        }
        return jdbcTemplate.update("""
            UPDATE notifications
            SET status = 'DIGESTED', updated_at = now()
            WHERE channel = 'EMAIL'
            AND status = 'PENDING'
            AND created_at < now() - interval '1 hour'
            AND recipient_id IN (
                SELECT recipient_id FROM notifications
                WHERE channel = 'EMAIL' AND status = 'PENDING'
                AND created_at < now() - interval '1 hour'
                GROUP BY recipient_id
            )
            """);
    }

    private boolean hasNotificationsTable() {
        try {
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1 FROM information_schema.tables WHERE table_name = 'notifications' LIMIT 1",
                Integer.class
            );
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
}
