package io.livelattice.auditlog.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.livelattice.auditlog.dto.ExportStatusResponse;
import io.livelattice.auditlog.dto.PagedAuditEventsResponse;
import io.livelattice.auditlog.dto.VerifyResponse;
import io.livelattice.auditlog.model.AuditEventEntity;
import io.livelattice.auditlog.repository.AuditEventRepository;
import io.livelattice.auditlog.service.AuditEventService;
import io.livelattice.auditlog.service.ExportService;
import io.livelattice.auditlog.service.RetentionService;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@SpringBootTest
@Testcontainers
class AuditLogIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.1-alpine"))
        .withDatabaseName("livelattice")
        .withUsername("livelattice")
        .withPassword("livelattice_dev_password");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.4-alpine"))
        .withExposedPorts(6379);

    @Container
    static MinIOContainer minio = new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("livelattice.audit.storage-endpoint", () -> "http://" + minio.getHost() + ":" + minio.getFirstMappedPort());
        registry.add("livelattice.auth.storage-access-key", minio::getUserName);
        registry.add("livelattice.auth.storage-secret-key", minio::getPassword);
        registry.add("livelattice.audit.export-processing-interval-ms", () -> "3600000");
        registry.add("livelattice.audit.retention-cron", () -> "0 0 0 31 12 ? 2099");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AuditEventRepository repository;

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private ExportService exportService;

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void consumesDomainEventAndSupportsQueryVerifyExportAndRetention() throws Exception {
        String workspaceId = UUID.randomUUID().toString();
        String actorId = UUID.randomUUID().toString();
        String canvasId = UUID.randomUUID().toString();
        String message = """
            {
              "eventType": "canvas.create",
              "targetType": "canvas",
              "id": "%s",
              "workspaceId": "%s",
              "actorId": "%s",
              "changes": {"title": "My Canvas"},
              "metadata": {"correlation_id": "corr-1"},
              "occurredAt": "%s"
            }
            """.formatted(canvasId, workspaceId, actorId, Instant.now());

        kafkaTemplate.send("livelattice.audit.events", UUID.randomUUID().toString(), message);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(repository.count()).isEqualTo(1));

        PagedAuditEventsResponse query = auditEventService.query(
            new AuditEventService.AuditQuery(workspaceId, "canvas.create", null, null, null, null, null),
            org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(query.events()).hasSize(1);
        assertThat(query.events().get(0).action()).isEqualTo("canvas.create");
        assertThat(query.events().get(0).targetType()).isEqualTo("canvas");

        VerifyResponse verify = auditEventService.verify(1000);
        assertThat(verify.valid()).isTrue();
        assertThat(verify.checkedCount()).isEqualTo(1);

        ExportStatusResponse export = exportService.create(new io.livelattice.auditlog.dto.ExportRequest(workspaceId, Instant.EPOCH, Instant.now(), "csv"));
        assertThat(export.status()).isEqualTo("pending");
        exportService.processNextPendingJob();
        ExportStatusResponse completed = exportService.findById(export.jobId());
        assertThat(completed.status()).isEqualTo("completed");
        assertThat(completed.downloadUrl()).isNotNull();

        minioClient.bucketExists(BucketExistsArgs.builder().bucket("audit-exports").build());
        byte[] artifact = exportService.loadArtifact(completed.downloadUrl());
        assertThat(artifact).isNotEmpty();
        assertThat(new String(artifact, java.nio.charset.StandardCharsets.UTF_8)).contains("canvas.create");

        archivePartitionAndVerify();
    }

    private void archivePartitionAndVerify() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate oldMonthStart = today.minusDays(120).withDayOfMonth(1);
        AuditEventEntity oldEvent = new AuditEventEntity();
        oldEvent.setId(UUID.randomUUID().toString());
        oldEvent.setWorkspaceId(UUID.randomUUID().toString());
        oldEvent.setActorId(UUID.randomUUID().toString());
        oldEvent.setAction("workspace.create");
        oldEvent.setTargetType("workspace");
        oldEvent.setTargetId(oldEvent.getWorkspaceId());
        oldEvent.setChanges("{}");
        oldEvent.setMetadata("{}");
        oldEvent.setOccurredAt(oldMonthStart.atStartOfDay(ZoneOffset.UTC).toInstant());
        auditEventService.ingest(oldEvent);
        assertThat(countRowsInDefaultPartition(oldEvent.getId())).isZero();

        LocalDate boundaryMonthStart = today.minusDays(90).withDayOfMonth(1);
        AuditEventEntity boundaryEvent = new AuditEventEntity();
        boundaryEvent.setId(UUID.randomUUID().toString());
        boundaryEvent.setWorkspaceId(UUID.randomUUID().toString());
        boundaryEvent.setActorId(UUID.randomUUID().toString());
        boundaryEvent.setAction("workspace.update");
        boundaryEvent.setTargetType("workspace");
        boundaryEvent.setTargetId(boundaryEvent.getWorkspaceId());
        boundaryEvent.setChanges("{}");
        boundaryEvent.setMetadata("{}");
        boundaryEvent.setOccurredAt(boundaryMonthStart.atStartOfDay(ZoneOffset.UTC).toInstant());
        auditEventService.ingest(boundaryEvent);
        assertThat(countRowsInDefaultPartition(boundaryEvent.getId())).isZero();

        LocalDate currentMonthStart = today.withDayOfMonth(1);
        AuditEventEntity currentEvent = new AuditEventEntity();
        currentEvent.setId(UUID.randomUUID().toString());
        currentEvent.setWorkspaceId(UUID.randomUUID().toString());
        currentEvent.setActorId(UUID.randomUUID().toString());
        currentEvent.setAction("workspace.create");
        currentEvent.setTargetType("workspace");
        currentEvent.setTargetId(currentEvent.getWorkspaceId());
        currentEvent.setChanges("{}");
        currentEvent.setMetadata("{}");
        currentEvent.setOccurredAt(today.atStartOfDay(ZoneOffset.UTC).toInstant());
        auditEventService.ingest(currentEvent);

        retentionService.runRetention();
        VerifyResponse retainedVerify = auditEventService.verify(1000);
        assertThat(retainedVerify.valid()).isTrue();
        assertThat(retainedVerify.checkedCount()).isGreaterThanOrEqualTo(4);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM audit_archived_partitions")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isGreaterThanOrEqualTo(1);
        }

        minioClient.bucketExists(BucketExistsArgs.builder().bucket("audit-archive").build());
        var objects = minioClient.listObjects(io.minio.ListObjectsArgs.builder().bucket("audit-archive").recursive(true).build());
        assertThat(objects).hasSizeGreaterThanOrEqualTo(1);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                 SELECT c.relname AS partition_name
                 FROM pg_class c
                 JOIN pg_inherits i ON i.inhrelid = c.oid
                 JOIN pg_class p ON i.inhparent = p.oid
                 WHERE p.relname = 'audit_events'
                 """)) {
            boolean currentMonthStillPresent = false;
            boolean boundaryMonthStillPresent = false;
            String currentPartitionName = "audit_events_" + currentMonthStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM"));
            String boundaryPartitionName = "audit_events_" + boundaryMonthStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM"));
            while (resultSet.next()) {
                String partitionName = resultSet.getString("partition_name");
                if (currentPartitionName.equals(partitionName)) {
                    currentMonthStillPresent = true;
                }
                if (boundaryPartitionName.equals(partitionName)) {
                    boundaryMonthStillPresent = true;
                }
            }
            assertThat(currentMonthStillPresent).isTrue();
            assertThat(boundaryMonthStillPresent).isTrue();
        }
    }

    private long countRowsInDefaultPartition(String id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM audit_events_default WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }
}
