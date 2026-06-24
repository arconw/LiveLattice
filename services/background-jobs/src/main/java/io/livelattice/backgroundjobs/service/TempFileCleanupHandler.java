package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TempFileCleanupHandler implements JobHandler {

    private final StringRedisTemplate redisTemplate;
    private JobService jobService;

    public TempFileCleanupHandler(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String type() {
        return "TEMP_CLEANUP";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        int removed = removeTempKeys();
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        JobResult result = new JobResult();
        result.getData().put("removedTempKeys", removed);
        return result;
    }

    private int removeTempKeys() {
        Set<String> keys = redisTemplate.keys("temp:*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        long removed = redisTemplate.delete(keys);
        return (int) removed;
    }
}
