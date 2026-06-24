package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.dto.CreateJobRequest;
import io.livelattice.backgroundjobs.dto.JobListResponse;
import io.livelattice.backgroundjobs.dto.JobResponse;
import io.livelattice.backgroundjobs.exception.BadRequestException;
import io.livelattice.backgroundjobs.exception.ForbiddenException;
import io.livelattice.backgroundjobs.exception.NotFoundException;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobPayload;
import io.livelattice.backgroundjobs.model.JobResult;
import io.livelattice.backgroundjobs.model.JobStatus;
import io.livelattice.backgroundjobs.repository.JobDefinitionRepository;
import io.livelattice.backgroundjobs.repository.JobExecutionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final StringRedisTemplate redisTemplate;
    private final BackgroundJobsProperties properties;

    public JobService(JobDefinitionRepository jobDefinitionRepository,
                      JobExecutionRepository jobExecutionRepository,
                      StringRedisTemplate redisTemplate,
                      BackgroundJobsProperties properties) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Transactional
    public JobDefinition createJob(CreateJobRequest request) {
        validateType(request.getType());
        JobDefinition job = new JobDefinition();
        job.setId(UUID.randomUUID());
        job.setJobType(request.getType().toUpperCase());
        job.setWorkspaceId(request.getWorkspaceId());
        job.setOwnerSubject(request.getOwnerSubject());
        job.setPayload(request.getPayload() != null ? request.getPayload() : new JobPayload());
        job.setPriority(request.getPriority() != null ? request.getPriority() : 50);
        job.setMaxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : properties.getWorker().getDefaultMaxRetries());
        job.setRetryDelaySeconds(request.getRetryDelaySeconds() != null ? request.getRetryDelaySeconds() : properties.getWorker().getDefaultRetryDelaySeconds());
        job.setRetryCount(0);
        job.setStatus(JobStatus.QUEUED);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        JobDefinition saved = jobDefinitionRepository.save(job);
        pushQueue(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID jobId, String requesterSubject, String rolesHeader) {
        JobDefinition job = jobDefinitionRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
        authorizeRead(job, requesterSubject, rolesHeader);
        JobExecution execution = jobExecutionRepository.findTopByJobDefinitionIdOrderByCreatedAtDesc(jobId).orElse(null);
        return new JobResponse(job, execution);
    }

    @Transactional(readOnly = true)
    public JobResponse getJobWithProgress(UUID jobId, String requesterSubject, String rolesHeader) {
        JobResponse response = getJob(jobId, requesterSubject, rolesHeader);
        String progressKey = progressKey(jobId);
        String progress = redisTemplate.opsForValue().get(progressKey);
        if (progress != null) {
            try {
                response.setProgress(Integer.parseInt(progress));
            } catch (NumberFormatException ignored) {
            }
        }
        return response;
    }

    @Transactional
    public JobResponse cancelJob(UUID jobId, String requesterSubject, String rolesHeader) {
        JobDefinition job = jobDefinitionRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
        if (job.getStatus() == JobStatus.RUNNING) {
            throw new BadRequestException("Cannot cancel running job");
        }
        if (job.getStatus() == JobStatus.SUCCESS || job.getStatus() == JobStatus.FAILED || job.getStatus() == JobStatus.DEAD) {
            throw new BadRequestException("Job already finished");
        }
        authorize(job, requesterSubject, rolesHeader);
        job.setStatus(JobStatus.CANCELLED);
        job.setUpdatedAt(Instant.now());
        removeFromQueue(job);
        return new JobResponse(jobDefinitionRepository.save(job), null);
    }

    @Transactional(readOnly = true)
    public JobListResponse listJobs(String type, JobStatus status, UUID workspaceId, int page, int size, String requesterSubject, String rolesHeader) {
        Pageable pageable = PageRequest.of(page, size);
        boolean adminOrService = hasRole(rolesHeader, "admin") || hasRole(rolesHeader, "service");
        String ownerFilter = adminOrService ? null : requesterSubject;
        Page<JobDefinition> result = jobDefinitionRepository.findScoped(type != null ? type.toUpperCase() : null,
            status != null ? status.name() : null, workspaceId, ownerFilter, pageable);
        List<JobResponse> content = result.getContent().stream()
            .map(j -> new JobResponse(j, null))
            .toList();
        return new JobListResponse(content, page, size, result.getTotalElements());
    }

    @Transactional
    public Optional<JobDefinition> claimNextQueued(String type, String workerId) {
        Optional<JobDefinition> found = jobDefinitionRepository.findNextQueuedByType(type, Instant.now());
        found.ifPresent(job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setWorkerId(workerId);
            job.setUpdatedAt(Instant.now());
            jobDefinitionRepository.save(job);
        });
        return found;
    }

    @Transactional(readOnly = true)
    public Optional<JobDefinition> findFirstRetryable() {
        return jobDefinitionRepository.findFirstRetryable(Instant.now());
    }

    @Transactional
    public Optional<JobDefinition> claimRetryable(UUID id) {
        Optional<JobDefinition> found = jobDefinitionRepository.claimRetryableById(id, Instant.now());
        found.ifPresent(job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setUpdatedAt(Instant.now());
            job.setNextRetryAt(null);
            jobDefinitionRepository.save(job);
        });
        return found;
    }

    @Transactional
    public void revertClaimedToQueued(JobDefinition job) {
        if (job.getStatus() == JobStatus.RUNNING) {
            job.setStatus(JobStatus.QUEUED);
            job.setWorkerId(null);
            job.setUpdatedAt(Instant.now());
            jobDefinitionRepository.save(job);
            pushQueue(job);
        }
    }

    @Transactional(readOnly = true)
    public Optional<UUID> dequeue(String type, Duration timeout) {
        try {
            String id = redisTemplate.opsForList().leftPop(queueKey(type), timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Transactional
    public boolean atomicClaim(UUID id, String workerId) {
        int rows = jobDefinitionRepository.atomicClaimById(id, workerId, Instant.now());
        return rows > 0;
    }

    @Transactional
    public JobExecution startExecution(JobDefinition job, String workerId) {
        JobExecution execution = new JobExecution();
        execution.setId(UUID.randomUUID());
        execution.setJobDefinitionId(job.getId());
        execution.setStatus(JobStatus.RUNNING);
        execution.setWorkerId(workerId);
        execution.setStartedAt(Instant.now());
        execution.setProgress(0);
        execution.setCreatedAt(Instant.now());
        return jobExecutionRepository.save(execution);
    }

    @Transactional
    public void updateProgress(UUID jobId, int progress) {
        redisTemplate.opsForValue().set(progressKey(jobId), String.valueOf(Math.max(0, Math.min(100, progress))));
    }

    @Transactional
    public void completeExecution(JobDefinition job, JobExecution execution, JobResult result) {
        execution.setStatus(JobStatus.SUCCESS);
        execution.setCompletedAt(Instant.now());
        execution.setProgress(100);
        execution.setResult(result);
        job.setStatus(JobStatus.SUCCESS);
        job.setWorkerId(null);
        job.setUpdatedAt(Instant.now());
        jobExecutionRepository.save(execution);
        jobDefinitionRepository.save(job);
        removeFromQueue(job);
    }

    @Transactional
    public void failExecution(JobDefinition job, JobExecution execution, String error) {
        execution.setStatus(JobStatus.FAILED);
        execution.setCompletedAt(Instant.now());
        execution.setErrorMessage(error);
        jobExecutionRepository.save(execution);
        job.setRetryCount(job.getRetryCount() + 1);
        job.setWorkerId(null);
        job.setUpdatedAt(Instant.now());
        RetryManager retryManager = new RetryManager(properties);
        if (retryManager.canRetry(job)) {
            job.setStatus(JobStatus.RETRYING);
            job.setNextRetryAt(retryManager.nextRetryAt(job));
        } else {
            job.setStatus(JobStatus.FAILED);
        }
        jobDefinitionRepository.save(job);
        removeFromQueue(job);
    }

    @Transactional
    public void markDead(JobDefinition job) {
        job.setStatus(JobStatus.DEAD);
        job.setUpdatedAt(Instant.now());
        jobDefinitionRepository.save(job);
        removeFromQueue(job);
    }

    public void pushQueue(JobDefinition job) {
        redisTemplate.opsForList().rightPush(queueKey(job.getJobType()), job.getId().toString());
    }

    public void removeFromQueue(JobDefinition job) {
        redisTemplate.opsForList().remove(queueKey(job.getJobType()), 0, job.getId().toString());
    }

    @Transactional(readOnly = true)
    public List<JobDefinition> findRunningJobs(String workerId) {
        return jobDefinitionRepository.findRunningJobs(JobStatus.RUNNING.name(), workerId);
    }

    @Transactional
    public void requeueIncomplete(List<JobDefinition> jobs) {
        for (JobDefinition job : jobs) {
            if (job.getStatus() == JobStatus.RUNNING) {
                job.setStatus(JobStatus.QUEUED);
                job.setWorkerId(null);
                job.setUpdatedAt(Instant.now());
                jobDefinitionRepository.save(job);
                pushQueue(job);
            }
        }
    }

    private void validateType(String type) {
        if (type == null || type.isBlank()) {
            throw new BadRequestException("Job type is required");
        }
        String normalized = type.toUpperCase();
        if (!List.of("EXPORT", "IMPORT", "CLEANUP", "DIGEST", "WEBHOOK", "TEMP_CLEANUP", "SNAPSHOT_COMPACTION",
                "PARTITION_MAINTENANCE", "QUOTA_RECONCILIATION", "INDEX_SYNC", "NOOP").contains(normalized)) {
            throw new BadRequestException("Unsupported job type: " + type);
        }
    }

    private void authorize(JobDefinition job, String requesterSubject, String rolesHeader) {
        if (canManage(job, requesterSubject, rolesHeader)) {
            return;
        }
        throw new ForbiddenException("Cannot cancel job owned by another user");
    }

    void authorizeRead(JobDefinition job, String requesterSubject, String rolesHeader) {
        if (canRead(job, requesterSubject, rolesHeader)) {
            return;
        }
        throw new ForbiddenException("Job access is not scoped to this user");
    }

    private boolean canRead(JobDefinition job, String requesterSubject, String rolesHeader) {
        if (hasRole(rolesHeader, "admin") || hasRole(rolesHeader, "service")) {
            return true;
        }
        if (job.getOwnerSubject() != null && job.getOwnerSubject().equals(requesterSubject)) {
            return true;
        }
        return false;
    }

    private boolean canManage(JobDefinition job, String requesterSubject, String rolesHeader) {
        if (hasRole(rolesHeader, "admin") || hasRole(rolesHeader, "service")) {
            return true;
        }
        if (job.getOwnerSubject() != null && job.getOwnerSubject().equals(requesterSubject)) {
            return true;
        }
        return false;
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

    private String queueKey(String type) {
        return "background-jobs:queue:" + type;
    }

    private String progressKey(UUID jobId) {
        return "background-jobs:progress:" + jobId;
    }
}
