package io.livelattice.auditlog.service;

import io.livelattice.auditlog.config.AuditProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditArchiveReader {

    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final AuditProperties properties;

    public AuditArchiveReader(JdbcTemplate jdbcTemplate, MinioClient minioClient, AuditProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public boolean hasArchivedEvents() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_archived_partitions WHERE record_count > 0",
            Long.class
        );
        return count != null && count > 0;
    }

    public List<ChainEvent> readArchivedEvents() {
        List<ChainEvent> events = new ArrayList<>();
        readArchivedEvents(1000, events::addAll);
        return events;
    }

    public void readArchivedEvents(int batchSize, Consumer<List<ChainEvent>> consumer) {
        List<ArchivedPartition> partitions = jdbcTemplate.query("""
            SELECT partition_name, record_count, artifact_path
            FROM audit_archived_partitions
            WHERE record_count > 0
            ORDER BY archived_at, partition_name
            """, (resultSet, rowNum) -> new ArchivedPartition(
            resultSet.getString("partition_name"),
            resultSet.getLong("record_count"),
            resultSet.getString("artifact_path")
        ));

        for (ArchivedPartition partition : partitions) {
            if (partition.artifactPath() == null || partition.artifactPath().isBlank()) {
                throw new IllegalStateException("Missing archive artifact for " + partition.partitionName());
            }
            long archivedEvents = readPartitionEvents(partition, batchSize, consumer);
            if (archivedEvents != partition.recordCount()) {
                throw new IllegalStateException("Archive record count mismatch for " + partition.partitionName());
            }
        }
    }

    private long readPartitionEvents(ArchivedPartition partition, int batchSize, Consumer<List<ChainEvent>> consumer) {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
            .bucket(properties.getArchiveBucket())
            .object(partition.artifactPath())
            .build())) {
            return ParquetWriterHelper.readChainEvents(stream.readAllBytes(), batchSize, consumer);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read archive artifact for " + partition.partitionName(), ex);
        }
    }

    private record ArchivedPartition(String partitionName, long recordCount, String artifactPath) {
    }
}
