package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;

public interface JobHandler {

    String type();

    default boolean blocksUntilTerminal() {
        return false;
    }

    JobResult handle(JobDefinition job, JobExecution execution);

    default void setJobService(JobService jobService) {
    }
}
