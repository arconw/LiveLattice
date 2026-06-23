package io.livelattice.importexport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.exception.UnsupportedFormatException;
import io.livelattice.importexport.model.JobStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class AsyncJobProcessor {

    private final FormatTransformer transformer;
    private final ArtifactService artifactService;
    private final CanvasLookupService canvasLookupService;
    private final JobService jobService;
    private final ObjectMapper objectMapper;

    public AsyncJobProcessor(FormatTransformer transformer,
                              ArtifactService artifactService,
                              CanvasLookupService canvasLookupService,
                              JobService jobService,
                              ObjectMapper objectMapper) {
        this.transformer = transformer;
        this.artifactService = artifactService;
        this.canvasLookupService = canvasLookupService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${livelattice.importexport.job-topic:import-export-jobs}", groupId = "import-export")
    public void onJobEvent(String payload) {
        try {
            Map<String, Object> message = objectMapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String type = String.valueOf(message.get("type"));
            UUID jobId = UUID.fromString(String.valueOf(message.get("jobId")));
            jobService.updateStatus(jobId, JobStatus.PROCESSING, 10, null, null);
            switch (type) {
                case "import" -> processImportJob(jobId, message);
                case "export" -> processExportJob(jobId, message);
                case "batch-import" -> processBatchImportJob(jobId, message);
                case "batch-export" -> processBatchExportJob(jobId, message);
                default -> jobService.updateStatus(jobId, JobStatus.FAILED, 0, null, "Unknown job type");
            }
        } catch (Exception e) {
            try {
                Map<String, Object> message = objectMapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                UUID jobId = UUID.fromString(String.valueOf(message.get("jobId")));
                jobService.updateStatus(jobId, JobStatus.FAILED, 0, null, e.getMessage());
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to handle async failure", ex);
            }
        }
    }

    private void processImportJob(UUID jobId, Map<String, Object> message) throws Exception {
        UUID workspaceId = UUID.fromString(String.valueOf(message.get("workspaceId")));
        String userSubject = stringValue(message.get("userSubject"));
        String title = String.valueOf(message.get("title"));
        String stagedPath = String.valueOf(message.get("stagedPath"));
        String filename = String.valueOf(message.get("filename"));
        byte[] bytes = artifactService.loadArtifact(stagedPath);
        jobService.updateStatus(jobId, JobStatus.PROCESSING, 40, null, null);
        Map<String, Object> canvas = transformer.importFile(new InMemoryMultipartFile(bytes, filename), title);
        jobService.updateStatus(jobId, JobStatus.PROCESSING, 70, null, null);
        UUID canvasId = canvasLookupService.save(canvas, workspaceId, userSubject);
        artifactService.storeCanvasJson(workspaceId, canvasId, canvas);
        jobService.updateStatus(jobId, JobStatus.COMPLETED, 100, canvasId.toString(), null);
    }

    private void processExportJob(UUID jobId, Map<String, Object> message) throws Exception {
        UUID workspaceId = UUID.fromString(String.valueOf(message.get("workspaceId")));
        String format = String.valueOf(message.get("format"));
        UUID canvasId = UUID.fromString(String.valueOf(message.get("canvasId")));
        String userSubject = stringValue(message.get("userSubject"));
        Map<String, Object> canvas = canvasLookupService.load(canvasId, userSubject);
        jobService.updateStatus(jobId, JobStatus.PROCESSING, 40, null, null);
        byte[] content = exportContent(canvas, format);
        jobService.updateStatus(jobId, JobStatus.PROCESSING, 70, null, null);
        String path = artifactService.storeArtifact(workspaceId, jobId, "export." + format, content, contentType(format));
        jobService.updateStatus(jobId, JobStatus.COMPLETED, 100, path, null);
    }

    private void processBatchImportJob(UUID jobId, Map<String, Object> message) throws Exception {
        UUID workspaceId = UUID.fromString(String.valueOf(message.get("workspaceId")));
        String userSubject = stringValue(message.get("userSubject"));
        @SuppressWarnings("unchecked")
        List<String> stagedPaths = objectMapper.convertValue(message.get("stagedPaths"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        @SuppressWarnings("unchecked")
        List<String> filenames = objectMapper.convertValue(message.get("filenames"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        @SuppressWarnings("unchecked")
        List<String> titles = objectMapper.convertValue(message.get("titles"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        int total = stagedPaths.size();
        List<String> canvasIds = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            byte[] bytes = artifactService.loadArtifact(stagedPaths.get(i));
            Map<String, Object> canvas = transformer.importFile(new InMemoryMultipartFile(bytes, filenames.get(i)), titles.get(i));
            UUID canvasId = canvasLookupService.save(canvas, workspaceId, userSubject);
            artifactService.storeCanvasJson(workspaceId, canvasId, canvas);
            canvasIds.add(canvasId.toString());
            int progress = 20 + (int) (((double) (i + 1) / total) * 70);
            jobService.updateStatus(jobId, JobStatus.PROCESSING, progress, null, null);
        }
        jobService.updateStatus(jobId, JobStatus.COMPLETED, 100, String.join(",", canvasIds), null);
    }

    private void processBatchExportJob(UUID jobId, Map<String, Object> message) throws Exception {
        UUID workspaceId = UUID.fromString(String.valueOf(message.get("workspaceId")));
        String format = String.valueOf(message.get("format"));
        String userSubject = stringValue(message.get("userSubject"));
        @SuppressWarnings("unchecked")
        List<String> canvasIds = objectMapper.convertValue(message.get("canvasIds"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        List<String> artifactPaths = new ArrayList<>();
        int total = canvasIds.size();
        for (int i = 0; i < total; i++) {
            UUID canvasId = UUID.fromString(canvasIds.get(i));
            Map<String, Object> canvas = canvasLookupService.load(canvasId, userSubject);
            byte[] content = exportContent(canvas, format);
            String path = artifactService.storeArtifact(workspaceId, jobId, canvasId + "." + format, content, contentType(format));
            artifactPaths.add(path);
            int progress = 20 + (int) (((double) (i + 1) / total) * 70);
            jobService.updateStatus(jobId, JobStatus.PROCESSING, progress, null, null);
        }
        String result = String.join(",", artifactPaths);
        jobService.updateStatus(jobId, JobStatus.COMPLETED, 100, result, null);
    }

    private byte[] exportContent(Map<String, Object> canvas, String format) throws Exception {
        return switch (format.toLowerCase()) {
            case "svg" -> transformer.exportCanvasToSvg(canvas);
            case "png" -> transformer.exportCanvasToPng(canvas);
            case "pdf" -> transformer.exportCanvasToPdf(canvas);
            case "json" -> objectMapper.writeValueAsBytes(canvas);
            default -> throw new UnsupportedFormatException("Unsupported export format: " + format);
        };
    }

    private String contentType(String format) {
        return switch (format.toLowerCase()) {
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "pdf" -> "application/pdf";
            case "json" -> "application/json";
            default -> "application/octet-stream";
        };
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
