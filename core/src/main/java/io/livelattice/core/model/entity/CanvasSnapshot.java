package io.livelattice.core.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "canvas_snapshots")
public class CanvasSnapshot {

    @Id
    private UUID id;

    @Column(name = "canvas_id", nullable = false)
    private UUID canvasId;

    @Column(nullable = false)
    private long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> content;

    @Column(name = "minio_path")
    private String minioPath;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "snapshot_at", nullable = false, updatable = false)
    private Instant snapshotAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public CanvasSnapshot() {
    }

    public CanvasSnapshot(UUID canvasId, long version, Map<String, Object> content, String minioPath, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.canvasId = canvasId;
        this.version = version;
        this.content = content;
        this.minioPath = minioPath;
        this.createdBy = createdBy;
        this.snapshotAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCanvasId() {
        return canvasId;
    }

    public void setCanvasId(UUID canvasId) {
        this.canvasId = canvasId;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public String getMinioPath() {
        return minioPath;
    }

    public void setMinioPath(String minioPath) {
        this.minioPath = minioPath;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }

    public void setSnapshotAt(Instant snapshotAt) {
        this.snapshotAt = snapshotAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
