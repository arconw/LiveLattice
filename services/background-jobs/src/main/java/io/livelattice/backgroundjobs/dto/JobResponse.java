package io.livelattice.backgroundjobs.dto;

import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import io.livelattice.backgroundjobs.model.JobStatus;
import java.time.Instant;
import java.util.UUID;

public class JobResponse {

    private UUID id;
    private String type;
    private UUID workspaceId;
    private String ownerSubject;
    private JobStatus status;
    private Integer priority;
    private Integer progress;
    private Integer retryCount;
    private Integer maxRetries;
    private Object result;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant completedAt;

    public JobResponse() {
    }

    public JobResponse(JobDefinition definition, JobExecution execution) {
        this.id = definition.getId();
        this.type = definition.getJobType();
        this.workspaceId = definition.getWorkspaceId();
        this.ownerSubject = definition.getOwnerSubject();
        this.status = definition.getStatus();
        this.priority = definition.getPriority();
        this.retryCount = definition.getRetryCount();
        this.maxRetries = definition.getMaxRetries();
        this.createdAt = definition.getCreatedAt();
        this.updatedAt = definition.getUpdatedAt();
        this.scheduledAt = definition.getScheduledAt();
        if (execution != null) {
            this.progress = execution.getProgress();
            this.result = execution.getResult() != null ? execution.getResult().getData() : null;
            this.errorMessage = execution.getErrorMessage();
            this.startedAt = execution.getStartedAt();
            this.completedAt = execution.getCompletedAt();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
