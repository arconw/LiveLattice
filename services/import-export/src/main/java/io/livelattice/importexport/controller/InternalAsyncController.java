package io.livelattice.importexport.controller;

import io.livelattice.importexport.service.JobService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalAsyncController {

    private final JobService jobService;

    public InternalAsyncController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping(value = "/export/async", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exportAsync(@RequestBody Map<String, Object> request,
                                                    @RequestHeader("x-auth-subject") String userSubject) throws Exception {
        String canvasId = String.valueOf(request.get("canvasId"));
        String format = String.valueOf(request.getOrDefault("format", "svg"));
        if (canvasId == null || canvasId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "canvasId is required"));
        }
        Map<String, Object> canvas;
        try {
            canvas = jobService.lookupCanvas(UUID.fromString(canvasId), userSubject);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
        UUID workspaceId = jobService.workspaceIdFromCanvas(canvas);
        UUID jobId = jobService.createJob("export", workspaceId, userSubject);
        Map<String, Object> payload = Map.of(
            "type", "export",
            "jobId", jobId.toString(),
            "workspaceId", workspaceId.toString(),
            "format", format,
            "canvasId", canvasId,
            "userSubject", userSubject
        );
        jobService.submitAsyncJob(jobId, jobService.serialize(payload));
        return ResponseEntity.accepted().body(Map.of("jobId", jobId.toString()));
    }

    @PostMapping(value = "/import/async", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importAsync(@RequestBody Map<String, Object> request,
                                                    @RequestHeader("x-auth-subject") String userSubject) throws Exception {
        String workspaceId = String.valueOf(request.get("workspaceId"));
        String stagedPath = String.valueOf(request.get("stagedPath"));
        String filename = String.valueOf(request.getOrDefault("filename", "import"));
        String title = String.valueOf(request.getOrDefault("title", "Imported"));
        if (workspaceId == null || workspaceId.isBlank() || stagedPath == null || stagedPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "workspaceId and stagedPath are required"));
        }
        UUID wsId = UUID.fromString(workspaceId);
        jobService.requireWorkspacePermission(wsId, userSubject, "canvas:create");
        UUID jobId = jobService.createJob("import", wsId, userSubject);
        Map<String, Object> payload = Map.of(
            "type", "import",
            "jobId", jobId.toString(),
            "workspaceId", workspaceId,
            "userSubject", userSubject,
            "title", title,
            "stagedPath", stagedPath,
            "filename", filename
        );
        jobService.submitAsyncJob(jobId, jobService.serialize(payload));
        return ResponseEntity.accepted().body(Map.of("jobId", jobId.toString()));
    }

    @GetMapping("/export/async/{jobId}/status")
    public ResponseEntity<Map<String, Object>> exportStatus(@PathVariable UUID jobId,
                                                     @RequestHeader("x-auth-subject") String userSubject) {
        return ResponseEntity.ok(jobService.statusResponse(jobId, userSubject));
    }

    @GetMapping("/import/async/{jobId}/status")
    public ResponseEntity<Map<String, Object>> importStatus(@PathVariable UUID jobId,
                                                     @RequestHeader("x-auth-subject") String userSubject) {
        return ResponseEntity.ok(jobService.statusResponse(jobId, userSubject));
    }
}
