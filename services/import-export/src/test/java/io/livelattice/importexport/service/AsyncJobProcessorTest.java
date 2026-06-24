package io.livelattice.importexport.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.config.ObjectMapperConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class AsyncJobProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapperConfig().objectMapper();

    @Test
    void asyncImportPublishesCanvasImportAuditEventAfterCompletion() throws Exception {
        FormatTransformer transformer = mock(FormatTransformer.class);
        ArtifactService artifactService = mock(ArtifactService.class);
        CanvasLookupService canvasLookupService = mock(CanvasLookupService.class);
        JobService jobService = mock(JobService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AsyncJobProcessor processor = new AsyncJobProcessor(transformer, artifactService, canvasLookupService, jobService, auditEventPublisher, objectMapper);
        UUID workspaceId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID canvasId = UUID.randomUUID();
        byte[] bytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> canvas = Map.of("elements", List.of());
        when(artifactService.loadArtifact("staged.svg")).thenReturn(bytes);
        when(transformer.importFile(any(MultipartFile.class), eq("Imported"))).thenReturn(canvas);
        when(canvasLookupService.save(canvas, workspaceId, "subject")).thenReturn(canvasId);
        Map<String, Object> payload = Map.of(
            "type", "import",
            "jobId", jobId.toString(),
            "workspaceId", workspaceId.toString(),
            "userSubject", "subject",
            "title", "Imported",
            "stagedPath", "staged.svg",
            "filename", "diagram.svg"
        );

        processor.onJobEvent(objectMapper.writeValueAsString(payload));

        verify(auditEventPublisher).publishCanvasImport(
            eq(workspaceId),
            eq(canvasId),
            eq("subject"),
            argThat(metadata -> "async".equals(metadata.get("mode"))
                && jobId.toString().equals(metadata.get("job_id"))
                && "diagram.svg".equals(metadata.get("filename")))
        );
    }

    @Test
    void asyncExportPublishesCanvasExportAuditEventAfterArtifactStored() throws Exception {
        FormatTransformer transformer = mock(FormatTransformer.class);
        ArtifactService artifactService = mock(ArtifactService.class);
        CanvasLookupService canvasLookupService = mock(CanvasLookupService.class);
        JobService jobService = mock(JobService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        AsyncJobProcessor processor = new AsyncJobProcessor(transformer, artifactService, canvasLookupService, jobService, auditEventPublisher, objectMapper);
        UUID workspaceId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID canvasId = UUID.randomUUID();
        Map<String, Object> canvas = Map.of(
            "id", canvasId.toString(),
            "workspaceId", workspaceId.toString(),
            "title", "Canvas",
            "elements", List.of()
        );
        when(canvasLookupService.load(canvasId, "subject")).thenReturn(canvas);
        when(artifactService.storeArtifact(eq(workspaceId), eq(jobId), eq("export.json"), any(byte[].class), eq("application/json")))
            .thenReturn("exports/" + jobId + "/export.json");
        Map<String, Object> payload = Map.of(
            "type", "export",
            "jobId", jobId.toString(),
            "workspaceId", workspaceId.toString(),
            "format", "json",
            "canvasId", canvasId.toString(),
            "userSubject", "subject"
        );

        processor.onJobEvent(objectMapper.writeValueAsString(payload));

        verify(auditEventPublisher).publishCanvasExport(
            eq(workspaceId),
            eq(canvasId),
            eq("subject"),
            argThat(metadata -> "async".equals(metadata.get("mode"))
                && "json".equals(metadata.get("format"))
                && jobId.toString().equals(metadata.get("job_id"))
                && "exports/".concat(jobId.toString()).concat("/export.json").equals(metadata.get("artifact_path"))
                && ((Number) metadata.get("bytes")).intValue() > 0)
        );
    }
}
