package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.model.DeadLetter;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobStatus;
import io.livelattice.backgroundjobs.repository.DeadLetterRepository;
import io.livelattice.backgroundjobs.repository.JobDefinitionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterManagerTest {

    @Test
    void retryDeadLetterLooksUpByJobDefinitionId() {
        UUID deadLetterId = UUID.randomUUID();
        UUID jobDefinitionId = UUID.randomUUID();

        DeadLetter deadLetter = new DeadLetter();
        deadLetter.setId(deadLetterId);
        deadLetter.setJobDefinitionId(jobDefinitionId);

        JobDefinition job = new JobDefinition();
        job.setId(jobDefinitionId);
        job.setStatus(JobStatus.DEAD);
        job.setJobType("NOOP");
        job.setRetryCount(2);

        DeadLetterRepository deadLetterRepository = mock(DeadLetterRepository.class);
        JobDefinitionRepository jobDefinitionRepository = mock(JobDefinitionRepository.class);
        JobService jobService = mock(JobService.class);

        when(deadLetterRepository.findById(deadLetterId)).thenReturn(Optional.of(deadLetter));
        when(jobDefinitionRepository.findById(jobDefinitionId)).thenReturn(Optional.of(job));
        when(jobDefinitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeadLetterManager manager = new DeadLetterManager(deadLetterRepository, jobDefinitionRepository, jobService,
            new BackgroundJobsProperties(), new SimpleMeterRegistry());
        JobDefinition retried = manager.retryDeadLetter(deadLetterId, "owner-1", "admin");

        assertNotNull(retried);
        assertEquals(jobDefinitionId, retried.getId());
        assertEquals(JobStatus.QUEUED, retried.getStatus());
        assertEquals(0, retried.getRetryCount());
        verify(deadLetterRepository).delete(deadLetter);
        verify(jobService).pushQueue(retried);
    }
}
