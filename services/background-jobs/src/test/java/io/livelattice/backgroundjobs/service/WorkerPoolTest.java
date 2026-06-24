package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import io.livelattice.backgroundjobs.repository.JobDefinitionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerPoolTest {

    @Test
    void noOpHandlerReturnsHandledResult() {
        NoOpJobHandler handler = new NoOpJobHandler();
        JobDefinition job = new JobDefinition();
        JobExecution execution = new JobExecution();
        JobResult result = handler.handle(job, execution);
        assertEquals(true, result.getData().get("handled"));
    }

    @Test
    void registryReturnsHandlerByType() {
        NoOpJobHandler handler = new NoOpJobHandler();
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(handler));
        assertEquals(handler, registry.get("NOOP"));
    }
}
