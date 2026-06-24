package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.dto.CreateJobRequest;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobPayload;
import io.livelattice.backgroundjobs.model.JobResult;
import io.livelattice.backgroundjobs.model.JobStatus;
import io.livelattice.backgroundjobs.repository.JobDefinitionRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class BackgroundJobsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @Autowired
    private JobService jobService;

    @Autowired
    private JobDefinitionRepository jobDefinitionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SnapshotCompactionHandler snapshotCompactionHandler;

    @Autowired
    private WorkspaceCleanupHandler workspaceCleanupHandler;

    @Autowired
    private PartitionMaintenanceHandler partitionMaintenanceHandler;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("livelattice.jobs.worker.enabled", () -> "false");
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS ensure_audit_partition(DATE)");
        jdbcTemplate.execute("DROP TABLE IF EXISTS workspace_members");
        jdbcTemplate.execute("DROP TABLE IF EXISTS workspaces");
        jdbcTemplate.execute("DROP TABLE IF EXISTS canvas_snapshots");
        jobDefinitionRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void redisQueueAndPostgresClaimStateRoundTrip() {
        CreateJobRequest request = new CreateJobRequest();
        request.setType("NOOP");
        request.setOwnerSubject("owner-1");
        request.setPriority(80);
        request.setPayload(new JobPayload());

        JobDefinition created = jobService.createJob(request);

        Optional<UUID> dequeued = jobService.dequeue("NOOP", Duration.ofSeconds(1));
        assertTrue(dequeued.isPresent());
        assertEquals(created.getId(), dequeued.get());
        assertTrue(jobService.atomicClaim(dequeued.get(), "worker-a"));

        JobDefinition claimed = jobDefinitionRepository.findById(created.getId()).orElseThrow();
        assertEquals(JobStatus.RUNNING, claimed.getStatus());
        assertEquals("worker-a", claimed.getWorkerId());
        assertEquals(1, jobService.findRunningJobs("worker-a").size());

        jobService.requeueIncomplete(List.of(claimed));

        JobDefinition requeued = jobDefinitionRepository.findById(created.getId()).orElseThrow();
        assertEquals(JobStatus.QUEUED, requeued.getStatus());
        assertNull(requeued.getWorkerId());
        assertEquals(created.getId(), jobService.dequeue("NOOP", Duration.ofSeconds(1)).orElseThrow());
    }

    @Test
    void databaseFallbackClaimAssignsWorkerId() {
        JobDefinition job = new JobDefinition();
        job.setId(UUID.randomUUID());
        job.setJobType("NOOP");
        job.setPayload(new JobPayload());
        job.setPriority(10);
        job.setMaxRetries(1);
        job.setRetryDelaySeconds(1);
        job.setRetryCount(0);
        job.setStatus(JobStatus.QUEUED);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobDefinitionRepository.save(job);

        JobDefinition claimed = jobService.claimNextQueued("NOOP", "worker-b").orElseThrow();

        assertEquals(JobStatus.RUNNING, claimed.getStatus());
        assertEquals("worker-b", claimed.getWorkerId());
    }

    @Test
    void snapshotCompactionKeepsLatestOneHundredSnapshots() {
        jdbcTemplate.execute("""
            CREATE TABLE canvas_snapshots (
                id UUID PRIMARY KEY,
                canvas_id UUID NOT NULL,
                snapshot_at TIMESTAMPTZ NOT NULL
            )
            """);
        UUID canvasId = UUID.randomUUID();
        Instant base = Instant.now();
        for (int i = 0; i < 105; i++) {
            jdbcTemplate.update(
                "INSERT INTO canvas_snapshots (id, canvas_id, snapshot_at) VALUES (?, ?, ?)",
                UUID.randomUUID(),
                canvasId,
                Timestamp.from(base.minusSeconds(i))
            );
        }

        JobResult result = snapshotCompactionHandler.handle(job("SNAPSHOT_COMPACTION"), new JobExecution());

        assertEquals(5, result.getData().get("removedSnapshots"));
        assertEquals(100, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM canvas_snapshots", Integer.class));
    }

    @Test
    void workspaceCleanupHardDeletesExpiredWorkspaces() {
        jdbcTemplate.execute("""
            CREATE TABLE workspaces (
                id UUID PRIMARY KEY,
                deleted_at TIMESTAMPTZ
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE workspace_members (
                workspace_id UUID NOT NULL REFERENCES workspaces(id)
            )
            """);
        UUID expiredWorkspace = UUID.randomUUID();
        UUID recentWorkspace = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO workspaces (id, deleted_at) VALUES (?, ?)",
            expiredWorkspace,
            Timestamp.from(Instant.now().minus(Duration.ofDays(31)))
        );
        jdbcTemplate.update(
            "INSERT INTO workspace_members (workspace_id) VALUES (?)",
            expiredWorkspace
        );
        jdbcTemplate.update(
            "INSERT INTO workspaces (id, deleted_at) VALUES (?, ?)",
            recentWorkspace,
            Timestamp.from(Instant.now().minus(Duration.ofDays(2)))
        );

        JobResult result = workspaceCleanupHandler.handle(job("CLEANUP"), new JobExecution());

        assertEquals(1, result.getData().get("deletedWorkspaces"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspaces", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspace_members", Integer.class));
    }

    @Test
    void partitionMaintenanceUsesAuditPartitionFunctionWhenPresent() {
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION ensure_audit_partition(month_date DATE)
            RETURNS TEXT AS $$
            BEGIN
                RETURN 'audit_events_' || TO_CHAR(month_date, 'YYYY_MM');
            END;
            $$ LANGUAGE plpgsql
            """);

        JobResult result = partitionMaintenanceHandler.handle(job("PARTITION_MAINTENANCE"), new JobExecution());

        assertEquals(2, result.getData().get("ensuredPartitions"));
        assertTrue(((List<?>) result.getData().get("partitions")).stream().allMatch(String.class::isInstance));
    }

    private JobDefinition job(String type) {
        JobDefinition job = new JobDefinition();
        job.setId(UUID.randomUUID());
        job.setJobType(type);
        job.setPayload(new JobPayload());
        job.setStatus(JobStatus.RUNNING);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }
}
