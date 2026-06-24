package io.livelattice.backgroundjobs.dto;

import io.livelattice.backgroundjobs.model.JobPayload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public class CreateJobRequest {

    @NotBlank
    private String type;

    private UUID workspaceId;

    private JobPayload payload;

    @Min(0)
    @Max(100)
    private Integer priority = 50;

    @Min(0)
    private Integer maxRetries;

    @Min(0)
    private Integer retryDelaySeconds;

    private String ownerSubject;

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

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public void setOwnerSubject(String ownerSubject) {
        this.ownerSubject = ownerSubject;
    }
}
