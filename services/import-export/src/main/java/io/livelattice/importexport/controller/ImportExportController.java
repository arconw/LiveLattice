package io.livelattice.importexport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.config.ImportExportProperties;
import io.livelattice.importexport.dto.BatchResponse;
import io.livelattice.importexport.dto.ImportOptions;
import io.livelattice.importexport.dto.ImportResponse;
import io.livelattice.importexport.dto.JobResponse;
import io.livelattice.importexport.exception.ForbiddenException;
import io.livelattice.importexport.model.JobState;
import io.livelattice.importexport.model.JobStatus;
import io.livelattice.importexport.service.ArtifactService;
import io.livelattice.importexport.service.AuditEventPublisher;
import io.livelattice.importexport.service.CanvasLookupService;
import io.livelattice.importexport.service.FileValidator;
import io.livelattice.importexport.service.FormatTransformer;
import io.livelattice.importexport.service.ImportExportAuthorizationService;
import io.livelattice.importexport.service.JobService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/")
public class ImportExportController {

    private final FileValidator fileValidator;
    private final FormatTransformer transformer;
    private final CanvasLookupService canvasLookupService;
    private final JobService jobService;
    private final ArtifactService artifactService;
    private final ImportExportAuthorizationService authorizationService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final ImportExportProperties properties;

    public ImportExportController(FileValidator fileValidator,
                                  FormatTransformer transformer,
                                  CanvasLookupService canvasLookupService,
                                  JobService jobService,
                                  ArtifactService artifactService,
                                  ImportExportAuthorizationService authorizationService,
                                  AuditEventPublisher auditEventPublisher,
                                  ObjectMapper objectMapper,
                                  ImportExportProperties properties) {
        this.fileValidator = fileValidator;
        this.transformer = transformer;
        this.canvasLookupService = canvasLookupService;
        this.jobService = jobService;
        this.artifactService = artifactService;
        this.authorizationService = authorizationService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> importFile(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("options") String optionsJson,
                                                     @RequestHeader("x-auth-subject") String userSubject) throws IOException {
        ImportOptions options = objectMapper.readValue(optionsJson, ImportOptions.class);
        fileValidator.validate(file);
        UUID workspaceId = UUID.fromString(options.workspaceId());
        canvasLookupService.requireWorkspacePermission(workspaceId, userSubject, "canvas:create");
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "import";
        if (file.getSize() <= properties.syncThresholdBytes()) {
            Map<String, Object> canvas = transformer.importFile(file, options.title());
            UUID canvasId = canvasLookupService.save(canvas, workspaceId, userSubject);
            auditEventPublisher.publishCanvasImport(workspaceId, canvasId, userSubject, importMetadata("sync", null, filename, options.title()));
            return ResponseEntity.ok(new ImportResponse(canvasId, null, "completed", "Canvas imported synchronously"));
        }
        UUID jobId = jobService.createJob("import", workspaceId, userSubject);
        String stagedPath = artifactService.stageImportFile(workspaceId, jobId, filename, file.getBytes());
        Map<String, Object> payload = Map.of(
            "type", "import",
            "jobId", jobId.toString(),
            "workspaceId", options.workspaceId(),
            "userSubject", userSubject,
            "title", options.title(),
            "stagedPath", stagedPath,
            "filename", filename
        );
        jobService.submitAsyncJob(jobId, objectMapper.writeValueAsString(payload));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ImportResponse(null, jobId, "pending", "Large import queued for async processing"));
    }

    @GetMapping("/import/jobs")
    public ResponseEntity<Map<String, Object>> listImportJobs(@RequestParam(name = "workspace_id", required = false) String workspaceId,
                                                              @RequestParam(name = "workspaceId", required = false) String workspaceIdCamel,
                                                              @RequestParam(name = "status", required = false) String status,
                                                              @RequestParam(name = "type", required = false) String type,
                                                              @RequestParam(name = "page", defaultValue = "0") int page,
                                                              @RequestParam(name = "size", defaultValue = "20") int size,
                                                              @RequestHeader("x-auth-subject") String userSubject) {
        return listJobs("import", firstNonBlank(workspaceId, workspaceIdCamel), status, type, page, size, userSubject);
    }

    @GetMapping("/import/jobs/{jobId}")
    public ResponseEntity<JobResponse> getImportJob(@PathVariable UUID jobId,
                                                    @RequestHeader("x-auth-subject") String userSubject) {
        JobState state = jobService.find(jobId);
        authorizeJobAccess(state, userSubject);
        return ResponseEntity.ok(toResponse(state));
    }

    @PostMapping("/export/{canvasId}")
    public ResponseEntity<StreamingResponseBody> exportCanvas(@PathVariable UUID canvasId,
                                                                 @RequestParam(name = "format", defaultValue = "svg") String format,
                                                                 @RequestHeader("x-auth-subject") String userSubject) throws Exception {
        Map<String, Object> canvas = canvasLookupService.load(canvasId, userSubject);
        byte[] content = exportContent(canvas, format);
        UUID workspaceId = canvasWorkspaceId(canvas);
        if (content.length > properties.syncThresholdBytes()) {
            UUID jobId = jobService.createJob("export", workspaceId, userSubject);
            Map<String, Object> payload = Map.of(
                "type", "export",
                "jobId", jobId.toString(),
                "workspaceId", workspaceId.toString(),
                "format", format,
                "canvasId", canvasId.toString(),
                "userSubject", userSubject
            );
            jobService.submitAsyncJob(jobId, objectMapper.writeValueAsString(payload));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .headers(asyncJobHeaders(jobId))
                .body(output -> output.write(objectMapper.writeValueAsBytes(new JobResponse(jobId, "export", "pending", 0, null, null, null, null))));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType(format));
        headers.setContentDispositionFormData("attachment", "canvas-" + canvasId + "." + format);
        auditEventPublisher.publishCanvasExport(workspaceId, canvasId, userSubject, exportMetadata("sync", format, null, content.length));
        return ResponseEntity.ok().headers(headers).body(output -> output.write(content));
    }

    @GetMapping("/export/jobs")
    public ResponseEntity<Map<String, Object>> listExportJobs(@RequestParam(name = "workspace_id", required = false) String workspaceId,
                                                              @RequestParam(name = "workspaceId", required = false) String workspaceIdCamel,
                                                              @RequestParam(name = "status", required = false) String status,
                                                              @RequestParam(name = "type", required = false) String type,
                                                              @RequestParam(name = "page", defaultValue = "0") int page,
                                                              @RequestParam(name = "size", defaultValue = "20") int size,
                                                              @RequestHeader("x-auth-subject") String userSubject) {
        return listJobs("export", firstNonBlank(workspaceId, workspaceIdCamel), status, type, page, size, userSubject);
    }

    @PostMapping("/export/dashboard/{dashboardId}")
    public ResponseEntity<StreamingResponseBody> exportDashboard(@PathVariable UUID dashboardId,
                                                                   @RequestParam(name = "format", defaultValue = "csv") String format,
                                                                   @RequestHeader("x-auth-subject") String userSubject) throws Exception {
        var data = canvasLookupService.loadDashboard(dashboardId, userSubject);
        byte[] content = switch (format.toLowerCase()) {
            case "xlsx" -> transformer.dashboardToXlsx(data);
            case "json" -> transformer.dashboardToJson(data);
            default -> transformer.dashboardToCsv(data);
        };
        MediaType mediaType = switch (format.toLowerCase()) {
            case "xlsx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "json" -> MediaType.APPLICATION_JSON;
            default -> MediaType.valueOf("text/csv");
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDispositionFormData("attachment", "dashboard-" + dashboardId + "." + format);
        return ResponseEntity.ok().headers(headers).body(output -> output.write(content));
    }

    @GetMapping("/export/jobs/{jobId}")
    public ResponseEntity<JobResponse> getExportJob(@PathVariable UUID jobId,
                                                    @RequestHeader("x-auth-subject") String userSubject) {
        JobState state = jobService.find(jobId);
        authorizeJobAccess(state, userSubject);
        return ResponseEntity.ok(toResponse(state));
    }

    @GetMapping("/export/jobs/{jobId}/download")
    public ResponseEntity<Map<String, String>> downloadExportJob(@PathVariable UUID jobId,
                                                                 @RequestHeader("x-auth-subject") String userSubject) {
        JobState state = jobService.find(jobId);
        authorizeJobAccess(state, userSubject);
        if (state == null || state.status() != JobStatus.COMPLETED || state.result() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Export not ready"));
        }
        String signedUrl = artifactService.signedUrl(state.result());
        return ResponseEntity.ok(Map.of("url", signedUrl, "jobId", jobId.toString()));
    }

    @PostMapping(value = "/batch/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchResponse> batchImport(@RequestParam("files") List<MultipartFile> files,
                                                   @RequestParam("options") String optionsJson,
                                                   @RequestHeader("x-auth-subject") String userSubject) throws IOException {
        ImportOptions options = objectMapper.readValue(optionsJson, ImportOptions.class);
        UUID workspaceId = UUID.fromString(options.workspaceId());
        canvasLookupService.requireWorkspacePermission(workspaceId, userSubject, "canvas:create");
        UUID jobId = jobService.createJob("batch-import", workspaceId, userSubject);
        List<String> filenames = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> stagedPaths = new ArrayList<>();
        List<UUID> items = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            fileValidator.validate(file);
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "import-" + i;
            String stagedPath = artifactService.stageImportFile(workspaceId, jobId, filename, file.getBytes());
            String title = options.title() + " - " + filename;
            filenames.add(filename);
            titles.add(title);
            stagedPaths.add(stagedPath);
            items.add(UUID.randomUUID());
        }
        Map<String, Object> payload = Map.of(
            "type", "batch-import",
            "jobId", jobId.toString(),
            "workspaceId", options.workspaceId(),
            "userSubject", userSubject,
            "stagedPaths", stagedPaths,
            "filenames", filenames,
            "titles", titles
        );
        jobService.submitAsyncJob(jobId, objectMapper.writeValueAsString(payload));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BatchResponse(jobId, "pending", items, "Batch import queued"));
    }

    @PostMapping("/batch/export")
    public ResponseEntity<BatchResponse> batchExport(@RequestParam(name = "format", defaultValue = "svg") String format,
                                                     @RequestParam(name = "canvasIds") List<String> canvasIds,
                                                     @RequestParam(name = "workspaceId") String workspaceId,
                                                     @RequestHeader("x-auth-subject") String userSubject) throws Exception {
        UUID wsId = UUID.fromString(workspaceId);
        List<UUID> items = canvasIds.stream().map(UUID::fromString).toList();
        for (UUID canvasId : items) {
            Map<String, Object> canvas = canvasLookupService.load(canvasId, userSubject);
            if (!wsId.toString().equals(String.valueOf(canvas.get("workspaceId")))) {
                throw new IllegalArgumentException("Canvas does not belong to workspace: " + canvasId);
            }
        }
        UUID jobId = jobService.createJob("batch-export", wsId, userSubject);
        Map<String, Object> payload = Map.of(
            "type", "batch-export",
            "jobId", jobId.toString(),
            "workspaceId", wsId.toString(),
            "format", format,
            "canvasIds", canvasIds,
            "userSubject", userSubject
        );
        jobService.submitAsyncJob(jobId, objectMapper.writeValueAsString(payload));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BatchResponse(jobId, "pending", items, "Batch export queued"));
    }

    private byte[] exportContent(Map<String, Object> canvas, String format) throws Exception {
        return switch (format.toLowerCase()) {
            case "svg" -> transformer.exportCanvasToSvg(canvas);
            case "png" -> transformer.exportCanvasToPng(canvas);
            case "pdf" -> transformer.exportCanvasToPdf(canvas);
            case "json" -> objectMapper.writeValueAsBytes(canvas);
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        };
    }

    private MediaType contentType(String format) {
        return switch (format.toLowerCase()) {
            case "png" -> MediaType.IMAGE_PNG;
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "json" -> MediaType.APPLICATION_JSON;
            default -> MediaType.valueOf("image/svg+xml");
        };
    }

    private UUID canvasWorkspaceId(Map<String, Object> canvas) {
        return UUID.fromString(String.valueOf(canvas.getOrDefault("workspaceId", "00000000-0000-0000-0000-000000000000")));
    }

    private Map<String, Object> importMetadata(String mode, UUID jobId, String filename, String title) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", mode);
        if (jobId != null) {
            metadata.put("job_id", jobId.toString());
        }
        if (filename != null && !filename.isBlank()) {
            metadata.put("filename", filename);
        }
        if (title != null && !title.isBlank()) {
            metadata.put("title", title);
        }
        return metadata;
    }

    private Map<String, Object> exportMetadata(String mode, String format, UUID jobId, int bytes) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", mode);
        metadata.put("format", format);
        if (jobId != null) {
            metadata.put("job_id", jobId.toString());
        }
        metadata.put("bytes", bytes);
        return metadata;
    }

    private HttpHeaders asyncJobHeaders(UUID jobId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Job-Id", jobId.toString());
        return headers;
    }

    private void authorizeJobAccess(JobState state, String userSubject) {
        if (state == null) {
            return;
        }
        if (state.userSubject() != null && state.userSubject().equals(userSubject)) {
            return;
        }
        if (state.workspaceId() == null) {
            throw new ForbiddenException("Job access is not scoped to this user");
        }
        authorizationService.requirePermission(state.workspaceId(), userSubject, "workspace:read");
    }

    private JobResponse toResponse(JobState state) {
        if (state == null) {
            return new JobResponse(null, null, "not_found", 0, null, null, null, null);
        }
        return new JobResponse(state.jobId(), state.type(), state.status().name().toLowerCase(), state.progress(), state.result(), state.error(), state.createdAt(), state.updatedAt());
    }

    private ResponseEntity<Map<String, Object>> listJobs(String domain, String workspaceId, String status, String type, int page, int size, String userSubject) {
        UUID workspace = optionalUuid(workspaceId);
        JobStatus jobStatus = parseJobStatus(status);
        List<JobState> jobs = jobService.listJobs(domain, type, jobStatus, workspace, page, size, userSubject);
        long total = jobService.countJobs(domain, type, jobStatus, workspace, userSubject);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobs", jobs.stream().map(state -> toJobMap(state, domain)).toList());
        response.put("total", total);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toJobMap(JobState state, String domain) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("id", state.jobId().toString());
        job.put("jobId", state.jobId().toString());
        job.put("domain", domain);
        job.put("type", state.type());
        job.put("workspaceId", state.workspaceId() != null ? state.workspaceId().toString() : null);
        job.put("ownerId", state.userSubject());
        job.put("status", listStatus(state.status()));
        job.put("progress", state.progress());
        job.put("retryCount", 0);
        job.put("maxRetries", 3);
        job.put("failureReason", state.error());
        job.put("downloadUrl", null);
        job.put("downloadExpiresAt", null);
        job.put("createdAt", state.createdAt());
        job.put("updatedAt", state.updatedAt());
        job.put("startedAt", null);
        job.put("completedAt", state.status() == JobStatus.COMPLETED ? state.updatedAt() : null);
        return job;
    }

    private String listStatus(JobStatus status) {
        return switch (status) {
            case PENDING -> "queued";
            case PROCESSING -> "running";
            case COMPLETED -> "succeeded";
            case FAILED -> "failed";
        };
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private UUID optionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private JobStatus parseJobStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.toLowerCase()) {
            case "queued", "pending" -> JobStatus.PENDING;
            case "running", "processing", "in_progress" -> JobStatus.PROCESSING;
            case "succeeded", "success", "completed", "complete" -> JobStatus.COMPLETED;
            case "failed" -> JobStatus.FAILED;
            default -> throw new IllegalArgumentException("Unsupported job status: " + status);
        };
    }
}
