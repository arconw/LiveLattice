package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import io.livelattice.backgroundjobs.model.JobStatus;
import io.livelattice.backgroundjobs.repository.JobDefinitionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerPool {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

    private final BackgroundJobsProperties properties;
    private final JobService jobService;
    private final JobDefinitionRepository jobDefinitionRepository;
    private final DeadLetterManager deadLetterManager;
    private final List<JobHandler> handlers;
    private final MaintenanceJobHandler maintenanceHandler;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> semaphores = new ConcurrentHashMap<>();
    private final Map<String, JobHandler> handlerMap = new ConcurrentHashMap<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final Set<String> supportedTypes = Set.of("EXPORT", "IMPORT", "NOOP", "CLEANUP", "DIGEST",
        "TEMP_CLEANUP", "SNAPSHOT_COMPACTION", "PARTITION_MAINTENANCE", "QUOTA_RECONCILIATION", "INDEX_SYNC", "WEBHOOK");

    public WorkerPool(BackgroundJobsProperties properties,
                      JobService jobService,
                      JobDefinitionRepository jobDefinitionRepository,
                      DeadLetterManager deadLetterManager,
                      List<JobHandler> handlers,
                      MaintenanceJobHandler maintenanceHandler,
                      StringRedisTemplate redisTemplate,
                      MeterRegistry meterRegistry) {
        this.properties = properties;
        this.jobService = jobService;
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.deadLetterManager = deadLetterManager;
        this.handlers = handlers;
        this.maintenanceHandler = maintenanceHandler;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        for (JobHandler handler : handlers) {
            handler.setJobService(jobService);
            handlerMap.put(handler.type().toUpperCase(), handler);
        }
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        for (String type : supportedTypes) {
            int concurrency = properties.concurrencyForType(type);
            executors.put(type, Executors.newFixedThreadPool(concurrency));
            semaphores.put(type, new Semaphore(concurrency, true));
        }
        Thread polling = new Thread(this::pollLoop, "worker-poll");
        polling.setDaemon(true);
        polling.start();
        Thread retry = new Thread(this::retryLoop, "retry-poll");
        retry.setDaemon(true);
        retry.start();
    }

    @PreDestroy
    public void gracefulShutdown() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        paused.set(true);
        shutdown.set(true);
        int timeoutSeconds = properties.getWorker().getShutdownTimeoutSeconds();
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        for (ExecutorService executor : executors.values()) {
            executor.shutdown();
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0 || !executor.awaitTermination(remaining, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        try {
            List<JobDefinition> running = jobService.findRunningJobs(properties.getWorker().getWorkerId());
            jobService.requeueIncomplete(running);
        } catch (Exception e) {
            log.warn("Failed to requeue incomplete jobs during shutdown: {}", e.getMessage());
        }
    }

    private void pollLoop() {
        while (!shutdown.get()) {
            try {
                if (paused.get()) {
                    Thread.sleep(1000);
                    continue;
                }
                for (String type : supportedTypes) {
                    try {
                        Semaphore semaphore = semaphores.get(type);
                        if (semaphore == null || !semaphore.tryAcquire()) {
                            continue;
                        }
                        Optional<JobDefinition> claimed = claimFromQueueOrDatabase(type);
                        if (claimed.isEmpty()) {
                            semaphore.release();
                            continue;
                        }
                        if (!submit(type, claimed.get())) {
                            jobService.revertClaimedToQueued(claimed.get());
                            semaphore.release();
                        }
                    } catch (Exception e) {
                        log.error("Worker poll error for type {}", type, e);
                    }
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private Optional<JobDefinition> claimFromQueueOrDatabase(String type) {
        Duration timeout = Duration.ofMillis(100);
        Optional<UUID> queuedId = jobService.dequeue(type, timeout);
        if (queuedId.isPresent()) {
            String workerId = properties.getWorker().getWorkerId();
            if (jobService.atomicClaim(queuedId.get(), workerId)) {
                return jobDefinitionRepository.findById(queuedId.get());
            }
        }
        Optional<JobDefinition> claimed = jobService.claimNextQueued(type, properties.getWorker().getWorkerId());
        if (claimed.isPresent()) {
            jobService.removeFromQueue(claimed.get());
        }
        return claimed;
    }

    private void retryLoop() {
        while (!shutdown.get()) {
            try {
                if (paused.get()) {
                    Thread.sleep(1000);
                    continue;
                }
                Optional<JobDefinition> candidate = jobService.findFirstRetryable();
                if (candidate.isEmpty()) {
                    Thread.sleep(2000);
                    continue;
                }
                JobDefinition candidateJob = candidate.get();
                String type = candidateJob.getJobType();
                Semaphore semaphore = semaphores.get(type);
                if (semaphore == null || !semaphore.tryAcquire()) {
                    Thread.sleep(2000);
                    continue;
                }
                String workerId = properties.getWorker().getWorkerId();
                if (!jobService.atomicClaim(candidateJob.getId(), workerId)) {
                    semaphore.release();
                    Thread.sleep(2000);
                    continue;
                }
                Optional<JobDefinition> claimed = jobDefinitionRepository.findById(candidateJob.getId());
                if (claimed.isEmpty()) {
                    semaphore.release();
                    Thread.sleep(2000);
                    continue;
                }
                if (!submit(type, claimed.get())) {
                    jobService.revertClaimedToQueued(claimed.get());
                    semaphore.release();
                }
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean submit(String type, JobDefinition job) {
        ExecutorService executor = executors.get(type);
        if (executor == null || executor.isShutdown()) {
            return false;
        }
        try {
            executor.submit(() -> run(job));
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private void run(JobDefinition job) {
        String workerId = properties.getWorker().getWorkerId();
        job.setStatus(JobStatus.RUNNING);
        job.setWorkerId(workerId);
        job.setUpdatedAt(Instant.now());
        jobDefinitionRepository.save(job);
        JobExecution execution = jobService.startExecution(job, workerId);
        JobHandler handler = handlerMap.get(job.getJobType());
        if (handler == null && maintenanceHandler.supports(job.getJobType())) {
            handler = maintenanceHandler;
        }
        try {
            if (handler == null) {
                throw new IllegalStateException("No handler for job type: " + job.getJobType());
            }
            JobResult result = handler.handle(job, execution);
            if (handler.blocksUntilTerminal() && result instanceof DelegatedResult delegated) {
                pollDownstream(job, execution, delegated.getDownstreamJobId(), handler);
            } else {
                jobService.completeExecution(job, execution, result);
            }
            meterRegistry.counter("background_jobs_completed", "type", job.getJobType()).increment();
        } catch (Exception e) {
            log.error("Job execution failed: {}", job.getId(), e);
            jobService.failExecution(job, execution, e.getMessage());
            if (!retryManager().canRetry(job)) {
                deadLetterManager.moveToDeadLetter(job, execution);
            }
            meterRegistry.counter("background_jobs_failed", "type", job.getJobType()).increment();
        } finally {
            job.setWorkerId(null);
            job.setUpdatedAt(Instant.now());
            jobDefinitionRepository.save(job);
            Semaphore semaphore = semaphores.get(job.getJobType());
            if (semaphore != null) {
                semaphore.release();
            }
            redisTemplate.delete("background-jobs:progress:" + job.getId());
        }
    }

    private void pollDownstream(JobDefinition job, JobExecution execution, UUID downstreamJobId, JobHandler handler) {
        int maxAttempts = properties.getWorker().getDownstreamPollMaxAttempts();
        long intervalMs = properties.getWorker().getDownstreamPollIntervalMs();
        for (int i = 0; i < maxAttempts && !shutdown.get(); i++) {
            boolean terminal = false;
            if (handler instanceof ExportJobHandler exportHandler) {
                terminal = exportHandler.pollTerminal(job, execution, downstreamJobId.toString());
            } else if (handler instanceof ImportJobHandler importHandler) {
                terminal = importHandler.pollTerminal(job, execution, downstreamJobId.toString());
            }
            if (terminal) {
                jobService.completeExecution(job, execution, execution.getResult());
                return;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while polling downstream job", e);
            }
        }
        throw new IllegalStateException("Downstream job did not reach terminal state within deadline: " + downstreamJobId);
    }

    private RetryManager retryManager() {
        return new RetryManager(properties);
    }
}
