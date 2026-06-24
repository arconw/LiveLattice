package io.livelattice.backgroundjobs.dto;

import io.livelattice.backgroundjobs.model.DeadLetter;
import java.time.Instant;
import java.util.UUID;

public class DeadLetterResponse {

    private UUID id;
    private UUID jobDefinitionId;
    private UUID failedExecutionId;
    private String errorMessage;
    private Integer retryCount;
    private Instant createdAt;

    public DeadLetterResponse(DeadLetter deadLetter) {
        this.id = deadLetter.getId();
        this.jobDefinitionId = deadLetter.getJobDefinitionId();
        this.failedExecutionId = deadLetter.getFailedExecutionId();
        this.errorMessage = deadLetter.getErrorMessage();
        this.retryCount = deadLetter.getRetryCount();
        this.createdAt = deadLetter.getCreatedAt();
    }

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
