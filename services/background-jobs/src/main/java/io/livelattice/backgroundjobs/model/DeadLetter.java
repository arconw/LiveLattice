package io.livelattice.backgroundjobs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "background_job_dead_letters",
    indexes = {
        @Index(name = "idx_bgd_dl_job_id", columnList = "jobDefinitionId")
    }
)
public class DeadLetter {

    @Id
    private UUID id;

    @Column(name = "job_definition_id", nullable = false)
    private UUID jobDefinitionId;

    @Column(name = "failed_execution_id")
    private UUID failedExecutionId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobDefinitionId() {
        return jobDefinitionId;
    }

    public void setJobDefinitionId(UUID jobDefinitionId) {
        this.jobDefinitionId = jobDefinitionId;
    }

    public UUID getFailedExecutionId() {
        return failedExecutionId;
    }

    public void setFailedExecutionId(UUID failedExecutionId) {
        this.failedExecutionId = failedExecutionId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
