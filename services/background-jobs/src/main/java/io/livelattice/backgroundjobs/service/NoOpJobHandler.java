package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import org.springframework.stereotype.Component;

@Component
public class NoOpJobHandler implements JobHandler {

    private JobService jobService;

    @Override
    public String type() {
        return "NOOP";
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        if (jobService != null) {
            jobService.updateProgress(job.getId(), 100);
        }
        execution.setProgress(100);
        JobResult result = new JobResult();
        result.getData().put("handled", true);
        return result;
    }
}
