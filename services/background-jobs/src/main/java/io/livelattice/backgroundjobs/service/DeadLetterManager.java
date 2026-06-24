package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.exception.ForbiddenException;
import io.livelattice.backgroundjobs.exception.NotFoundException;
import io.livelattice.backgroundjobs.model.DeadLetter;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobStatus;
import io.livelattice.backgroundjobs.repository.DeadLetterRepository;
import io.livelattice.backgroundjobs.repository.JobDefinitionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterManager {

    private final DeadLetterRepository deadLetterRepository;
    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobService jobService;
    private final BackgroundJobsProperties properties;
    private final MeterRegistry meterRegistry;

    public DeadLetterManager(DeadLetterRepository deadLetterRepository,
                             JobDefinitionRepository jobDefinitionRepository,
                             JobService jobService,
                             BackgroundJobsProperties properties,
                             MeterRegistry meterRegistry) {
        this.deadLetterRepository = deadLetterRepository;
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobService = jobService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerMetric() {
        Gauge.builder("background_jobs_dead_letters", this, m -> m.deadLetterCount())
            .description("Number of dead letter jobs")
            .register(meterRegistry);
    }

    public double deadLetterCount() {
        return (double) deadLetterRepository.countDeadLetters();
    }

    @Transactional
    public DeadLetter moveToDeadLetter(JobDefinition job, JobExecution execution) {
        DeadLetter deadLetter = new DeadLetter();
        deadLetter.setId(UUID.randomUUID());
        deadLetter.setJobDefinitionId(job.getId());
        deadLetter.setFailedExecutionId(execution != null ? execution.getId() : null);
        deadLetter.setErrorMessage(execution != null ? execution.getErrorMessage() : null);
        deadLetter.setRetryCount(job.getRetryCount());
        deadLetter.setCreatedAt(Instant.now());
        job.setStatus(JobStatus.DEAD);
        job.setUpdatedAt(Instant.now());
        jobDefinitionRepository.save(job);
        return deadLetterRepository.save(deadLetter);
    }

    public Page<DeadLetter> listDeadLetters(String requesterSubject, String rolesHeader, Pageable pageable) {
        if (hasRole(rolesHeader, "admin") || hasRole(rolesHeader, "service")) {
            return deadLetterRepository.findAll(pageable);
        }
        return deadLetterRepository.findByOwnerSubject(requesterSubject, pageable);
    }

    @Transactional
    public JobDefinition retryDeadLetter(UUID deadLetterId, String requesterSubject, String rolesHeader) {
        DeadLetter deadLetter = deadLetterRepository.findById(deadLetterId)
            .orElseThrow(() -> new NotFoundException("Dead letter not found: " + deadLetterId));
        JobDefinition job = jobDefinitionRepository.findById(deadLetter.getJobDefinitionId())
            .orElseThrow(() -> new IllegalStateException("Job definition not found for dead letter: " + deadLetterId));
        authorizeRetry(job, requesterSubject, rolesHeader);
        if (job.getStatus() != JobStatus.DEAD) {
            throw new IllegalStateException("Job is not dead: " + job.getId());
        }
        job.setStatus(JobStatus.QUEUED);
        job.setRetryCount(0);
        job.setNextRetryAt(null);
        job.setUpdatedAt(Instant.now());
        JobDefinition saved = jobDefinitionRepository.save(job);
        deadLetterRepository.delete(deadLetter);
        jobService.pushQueue(saved);
        return saved;
    }

    private void authorizeRetry(JobDefinition job, String requesterSubject, String rolesHeader) {
        if (hasRole(rolesHeader, "admin") || hasRole(rolesHeader, "service")) {
            return;
        }
        if (job.getOwnerSubject() != null && job.getOwnerSubject().equals(requesterSubject)) {
            return;
        }
        throw new ForbiddenException("Cannot retry dead letter job owned by another user");
    }

    private boolean hasRole(String rolesHeader, String role) {
        if (rolesHeader == null || role == null) {
            return false;
        }
        for (String part : rolesHeader.split(",")) {
            if (role.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    public boolean alertThresholdExceeded() {
        return deadLetterRepository.countDeadLetters() > properties.getDeadLetterAlertThreshold();
    }
}
