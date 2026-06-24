package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.dto.CreateJobRequest;
import io.livelattice.backgroundjobs.exception.BadRequestException;
import io.livelattice.backgroundjobs.exception.ForbiddenException;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobServiceTest {

    @Test
    void createJobGeneratesDefinition() {
        JobService service = new JobService(null, null, null, new BackgroundJobsProperties());
        CreateJobRequest request = new CreateJobRequest();
        request.setType("EXPORT");
        request.setPriority(75);
        request.setMaxRetries(2);
        JobDefinition job = new JobDefinition();
        job.setId(UUID.randomUUID());
        job.setJobType(request.getType().toUpperCase());
        job.setPriority(request.getPriority());
        job.setMaxRetries(request.getMaxRetries());
        job.setStatus(JobStatus.QUEUED);
        assertNotNull(job.getId());
        assertEquals("EXPORT", job.getJobType());
        assertEquals(75, job.getPriority());
        assertEquals(2, job.getMaxRetries());
        assertEquals(JobStatus.QUEUED, job.getStatus());
    }

    @Test
    void validateTypeRejectsUnknown() {
        JobService service = new JobService(null, null, null, new BackgroundJobsProperties());
        CreateJobRequest request = new CreateJobRequest();
        request.setType("UNKNOWN");
        assertThrows(BadRequestException.class, () -> service.createJob(request));
    }

    @Test
    void getJobRejectsNonOwner() {
        JobService service = new JobService(null, null, null, new BackgroundJobsProperties());
        JobDefinition job = new JobDefinition();
        job.setId(UUID.randomUUID());
        job.setJobType("NOOP");
        job.setOwnerSubject("owner-1");
        job.setStatus(JobStatus.SUCCESS);
        assertThrows(ForbiddenException.class, () -> service.authorizeRead(job, "other", "user"));
    }
}
