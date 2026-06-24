package io.livelattice.auditlog.service;

import io.livelattice.auditlog.config.AuditProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final DataSource dataSource;
    private final MinioClient minioClient;
    private final AuditProperties properties;

    public RetentionService(DataSource dataSource, MinioClient minioClient, AuditProperties properties) {
        this.dataSource = dataSource;
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Scheduled(cron = "${livelattice.audit.retention-cron:0 0 3 * * *}")
    @Transactional
    public void runRetention() {
        int hotDays = properties.getRetentionHotDays();
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(hotDays);
        List<String> partitions = listPartitionsOlderThan(cutoff);
        for (String partition : partitions) {
            try {
                archiveAndDropPartition(partition);
                log.info("Retained partition {} (hot retention {} days)", partition, hotDays);
            } catch (Exception ex) {
                log.warn("Failed to archive/drop partition {}: {}", partition, ex.getMessage());
            }
        }
    }

    private List<String> listPartitionsOlderThan(LocalDate cutoff) {
        List<String> partitions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                 SELECT c.relname AS partition_name
                 FROM pg_class c
                 JOIN pg_inherits i ON i.inhrelid = c.oid
                 JOIN pg_class p ON i.inhparent = p.oid
                 WHERE p.relname = 'audit_events'
                 """)) {
            while (resultSet.next()) {
                String name = resultSet.getString("partition_name");
                LocalDate partitionEnd = parsePartitionEndDate(name);
                if (partitionEnd != null && !partitionEnd.isAfter(cutoff)) {
                    partitions.add(name);
                }
            }
        } catch (SQLException ex) {
            log.warn("Failed to list audit partitions: {}", ex.getMessage());
        }
        return partitions;
    }

    private LocalDate parsePartitionEndDate(String partitionName) {
        LocalDate start = parsePartitionDate(partitionName);
        return start == null ? null : start.plusMonths(1);
    }

    private LocalDate parsePartitionDate(String partitionName) {
        if (partitionName == null || !partitionName.startsWith("audit_events_")) {
            return null;
        }
        String suffix = partitionName.substring("audit_events_".length());
        try {
            return LocalDate.parse(suffix + "-01", DateTimeFormatter.ofPattern("yyyy_MM-dd"));
        } catch (Exception ex) {
            return null;
        }
    }

    private void archiveAndDropPartition(String partitionName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (isArchived(connection, partitionName)) {
                detachAndDrop(connection, partitionName);
                return;
            }
            List<ExportEvent> events = readPartitionEvents(connection, partitionName);
            String artifactPath = null;
            if (!events.isEmpty()) {
                try {
                    byte[] parquet = ParquetWriterHelper.write(events);
                    artifactPath = archivePath(partitionName);
                    upload(artifactPath, parquet);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to write audit archive for partition: " + partitionName, ex);
                }
            }
            recordArchive(connection, partitionName, events.size(), artifactPath);
            detachAndDrop(connection, partitionName);
        }
    }

    private boolean isArchived(Connection connection, String partitionName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM audit_archived_partitions WHERE partition_name = ?")) {
            statement.setString(1, partitionName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private List<ExportEvent> readPartitionEvents(Connection connection, String partitionName) throws SQLException {
        List<ExportEvent> events = new ArrayList<>();
        String sql = "SELECT id, workspace_id, actor_id, action, target_type, target_id, changes, metadata, previous_hash, hash, occurred_at FROM "
            + quoteIdentifier(partitionName) + " ORDER BY ingested_at, id";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                events.add(new ExportEvent(
                    resultSet.getString("id"),
                    resultSet.getString("workspace_id"),
                    resultSet.getString("actor_id"),
                    resultSet.getString("action"),
                    resultSet.getString("target_type"),
                    resultSet.getString("target_id"),
                    resultSet.getString("changes"),
                    resultSet.getString("metadata"),
                    resultSet.getString("previous_hash"),
                    resultSet.getString("hash"),
                    resultSet.getTimestamp("occurred_at").toInstant()
                ));
            }
        }
        return events;
    }

    private String archivePath(String partitionName) {
        LocalDate partitionDate = parsePartitionDate(partitionName);
        String datePath = partitionDate == null
            ? partitionName
            : partitionDate.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return String.format("%s/%s/audit_events_%s.parquet", properties.getArchivePrefix(), datePath, partitionName);
    }

    private void upload(String path, byte[] content) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getArchiveBucket())
                .object(path)
                .contentType("application/octet-stream")
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload audit archive: " + path, ex);
        }
    }

    private void recordArchive(Connection connection, String partitionName, int recordCount, String artifactPath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO audit_archived_partitions (partition_name, record_count, artifact_path, archived_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, partitionName);
            statement.setLong(2, recordCount);
            statement.setString(3, artifactPath);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private void detachAndDrop(Connection connection, String partitionName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE audit_events DETACH PARTITION " + quoteIdentifier(partitionName));
            statement.executeUpdate("DROP TABLE IF EXISTS " + quoteIdentifier(partitionName));
        }
    }

    private String quoteIdentifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
