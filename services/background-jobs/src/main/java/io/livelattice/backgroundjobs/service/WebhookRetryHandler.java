package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebhookRetryHandler implements JobHandler {

    private final JdbcTemplate jdbcTemplate;
    private JobService jobService;

    public WebhookRetryHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "WEBHOOK";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        int retried = retryDueWebhookAttempts();
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        JobResult result = new JobResult();
        result.getData().put("processed", true);
        result.getData().put("retriedAttempts", retried);
        return result;
    }

    private int retryDueWebhookAttempts() {
        if (!hasDeliveryAttemptsTable()) {
            return 0;
        }
        List<Map<String, Object>> attempts = jdbcTemplate.queryForList("""
            SELECT id, target_url
            FROM notification_delivery_attempts
            WHERE status = 'PENDING'
            AND next_attempt_at <= now()
            ORDER BY next_attempt_at ASC
            LIMIT 50
            """);
        for (Map<String, Object> attempt : attempts) {
            Object id = attempt.get("id");
            jdbcTemplate.update("""
                UPDATE notification_delivery_attempts
                SET next_attempt_at = now() + interval '15 minutes',
                    attempt_number = attempt_number + 1,
                    updated_at = now()
                WHERE id = ?
                AND attempt_number < 5
                """, id);
        }
        return attempts.size();
    }

    private boolean hasDeliveryAttemptsTable() {
        try {
            Integer result = jdbcTemplate.queryForObject(
                "SELECT 1 FROM information_schema.tables WHERE table_name = 'notification_delivery_attempts' LIMIT 1",
                Integer.class
            );
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }
}
