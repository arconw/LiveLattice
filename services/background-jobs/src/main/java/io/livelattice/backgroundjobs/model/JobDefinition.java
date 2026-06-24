package io.livelattice.backgroundjobs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "background_job_definitions",
    indexes = {
        @Index(name = "idx_bgd_jobs_status_type", columnList = "status, jobType"),
        @Index(name = "idx_bgd_jobs_workspace_id", columnList = "workspaceId"),
        @Index(name = "idx_bgd_jobs_scheduled_at", columnList = "scheduledAt")
    }
)
public class JobDefinition {

    @Id
    private UUID id;

    @Column(name = "job_type", length = 100, nullable = false)
    private String jobType;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "owner_subject", length = 255)
    private String ownerSubject;

    @Column(name = "payload_jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JobPayload payload;

    @Column(nullable = false)
    private Integer priority = 50;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "retry_delay_seconds", nullable = false)
    private Integer retryDelaySeconds = 60;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public void setOwnerSubject(String ownerSubject) {
        this.ownerSubject = ownerSubject;
    }

    public JobPayload getPayload() {
        return payload;
    }

    public void setPayload(JobPayload payload) {
        this.payload = payload;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Integer getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(Integer retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }
}
