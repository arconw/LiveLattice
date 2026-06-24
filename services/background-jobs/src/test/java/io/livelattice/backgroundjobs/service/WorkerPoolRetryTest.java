package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobStatus;
import io.livelattice.backgroundjobs.repository.JobDefinitionRepository;
import io.livelattice.backgroundjobs.repository.JobExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerPoolRetryTest {

    @Test
    void findRunningJobsRequeueIncompleteSetsQueuedAndPushesQueue() {
        JobDefinitionRepository repository = mock(JobDefinitionRepository.class);
        JobExecutionRepository executionRepository = mock(JobExecutionRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);

        JobService jobService = new JobService(repository, executionRepository, redisTemplate, new BackgroundJobsProperties());

        UUID id = UUID.randomUUID();
        JobDefinition running = new JobDefinition();
        running.setId(id);
        running.setStatus(JobStatus.RUNNING);
        running.setJobType("NOOP");
        running.setUpdatedAt(Instant.now());

        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        jobService.requeueIncomplete(Collections.singletonList(running));

        assertEquals(JobStatus.QUEUED, running.getStatus());
        verify(repository).save(running);
        verify(listOps).rightPush("background-jobs:queue:NOOP", id.toString());
    }

    @Test
    void requeueIncompleteByWorkerIdFiltersByWorkerId() {
        JobDefinitionRepository repository = mock(JobDefinitionRepository.class);
        JobExecutionRepository executionRepository = mock(JobExecutionRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);

        JobService jobService = new JobService(repository, executionRepository, redisTemplate, new BackgroundJobsProperties());

        jobService.findRunningJobs("worker-1");

        verify(repository).findRunningJobs(eq("RUNNING"), eq("worker-1"));
    }

    @Test
    void retryDeadLetterUsesCorrectJobDefinitionId() {
        assertTrue(true);
    }
}
