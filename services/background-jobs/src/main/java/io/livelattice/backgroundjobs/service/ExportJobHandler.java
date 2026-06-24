package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.config.BackgroundJobsProperties;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobExecution;
import io.livelattice.backgroundjobs.model.JobResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ExportJobHandler implements JobHandler {

    private final BackgroundJobsProperties properties;
    private final RestTemplate restTemplate;
    private JobService jobService;

    public ExportJobHandler(BackgroundJobsProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public String type() {
        return "EXPORT";
    }

    @Override
    public JobResult handle(JobDefinition job, JobExecution execution) {
        try {
            updateProgress(job, execution, 10);
            Object canvasId = job.getPayload() != null ? job.getPayload().get("canvasId") : null;
            Object format = job.getPayload() != null ? job.getPayload().get("format") : "svg";
            if (canvasId == null || canvasId.toString().isBlank()) {
                throw new IllegalArgumentException("canvasId is required for export");
            }
            UUID workspaceId = requireWorkspaceId(job);
            updateProgress(job, execution, 40);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-internal-auth-token", properties.getImportExport().getInternalSecret());
            headers.set("x-auth-subject", job.getOwnerSubject());
            headers.set("x-auth-email", job.getOwnerSubject());
            headers.set("x-auth-display-name", job.getOwnerSubject());
            headers.set("x-auth-roles", "user");
            headers.set("x-user-id", job.getOwnerSubject());
            Map<String, Object> body = Map.of(
                "canvasId", canvasId.toString(),
                "format", format != null ? format.toString() : "svg"
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                properties.getImportExport().getServiceUrl() + "/internal/export/async",
                request,
                Map.class
            );
            updateProgress(job, execution, 70);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Import-export export async request failed: " + response.getStatusCode());
            }
            Object downstreamJobId = response.getBody().get("jobId");
            if (downstreamJobId == null || downstreamJobId.toString().isBlank()) {
                throw new IllegalStateException("Import-export did not return downstream jobId");
            }
            return new DelegatedResult(UUID.fromString(downstreamJobId.toString()));
        } catch (Exception e) {
            throw new RuntimeException("Export delegation failed: " + e.getMessage(), e);
        }
    }

    private UUID requireWorkspaceId(JobDefinition job) {
        if (job.getWorkspaceId() != null) {
            return job.getWorkspaceId();
        }
        Object workspaceId = job.getPayload() != null ? job.getPayload().get("workspaceId") : null;
        if (workspaceId == null || workspaceId.toString().isBlank()) {
            throw new IllegalArgumentException("workspaceId is required for export");
        }
        return UUID.fromString(workspaceId.toString());
    }

    @Override
    public boolean blocksUntilTerminal() {
        return true;
    }

    public boolean pollTerminal(JobDefinition job, JobExecution execution, String downstreamJobId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-internal-auth-token", properties.getImportExport().getInternalSecret());
            headers.set("x-auth-subject", job.getOwnerSubject());
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                properties.getImportExport().getServiceUrl() + "/internal/export/async/{jobId}/status",
                org.springframework.http.HttpMethod.GET,
                request,
                Map.class,
                downstreamJobId
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return false;
            }
            Map<String, Object> body = response.getBody();
            String status = String.valueOf(body.getOrDefault("status", "")).toUpperCase();
            int progress = parseProgress(body.get("progress"));
            updateProgress(job, execution, progress);
            if ("COMPLETED".equals(status)) {
                JobResult result = new JobResult();
                result.getData().put("delegated", true);
                result.getData().put("downstreamJobId", downstreamJobId);
                result.getData().put("result", body.get("result"));
                result.getData().put("serviceUrl", properties.getImportExport().getServiceUrl());
                execution.setResult(result);
                return true;
            }
            if ("FAILED".equals(status)) {
                Object error = body.get("error");
                throw new IllegalStateException(error != null ? error.toString() : "Downstream export failed");
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Export downstream status check failed: " + e.getMessage(), e);
        }
    }

    private int parseProgress(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            if (value instanceof Number n) {
                return Math.max(0, Math.min(100, n.intValue()));
            }
            return Math.max(0, Math.min(100, Integer.parseInt(value.toString())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateProgress(JobDefinition job, JobExecution execution, int progress) {
        execution.setProgress(progress);
        if (jobService != null) {
            jobService.updateProgress(job.getId(), progress);
        }
    }
}
