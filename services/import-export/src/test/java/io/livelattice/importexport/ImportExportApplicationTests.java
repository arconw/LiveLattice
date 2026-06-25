package io.livelattice.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.config.ImportExportProperties;
import io.livelattice.importexport.config.ObjectMapperConfig;
import io.livelattice.importexport.controller.HealthController;
import io.livelattice.importexport.controller.ImportExportController;
import io.livelattice.importexport.dto.ImportOptions;
import io.livelattice.importexport.dto.ImportResponse;
import io.livelattice.importexport.exception.ForbiddenException;
import io.livelattice.importexport.exception.GlobalExceptionHandler;
import io.livelattice.importexport.exception.UnsupportedFormatException;
import io.livelattice.importexport.exception.ValidationException;
import io.livelattice.importexport.model.JobState;
import io.livelattice.importexport.model.JobStatus;
import io.livelattice.importexport.service.ArtifactService;
import io.livelattice.importexport.service.AuditEventPublisher;
import io.livelattice.importexport.service.CanvasLookupService;
import io.livelattice.importexport.service.FileValidator;
import io.livelattice.importexport.service.FormatTransformer;
import io.livelattice.importexport.service.ImportExportAuthorizationService;
import io.livelattice.importexport.service.JobService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.minio.MinioClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.HttpRequestMethodNotSupportedException;

class ImportExportApplicationTests {

    private final FormatTransformer formatTransformer = new FormatTransformer();
    private final ImportExportProperties properties = new ImportExportProperties(
        104857600L, 10485760L, "http://localhost:9100", "livelattice", "livelattice_dev_password",
        "livelattice-imports", 60, "import-export-jobs", false, "clamav", 3310
    );
    private final FileValidator fileValidator = new FileValidator(properties);
    private final HealthController healthController = new HealthController(
        mock(StringRedisTemplate.class),
        mock(MinioClient.class),
        mock(AdminClient.class),
        properties
    );

    @Test
    void healthReturnsUp() {
        ResponseEntity<Map<String, Object>> response = healthController.health();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void importSvgSyncReturnsCanvasId() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect x=\"10\" y=\"20\" width=\"100\" height=\"50\"/></svg>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.svg", "image/svg+xml", svg);
        ImportOptions options = new ImportOptions(UUID.randomUUID().toString(), "Imported SVG", null);

        CanvasLookupService canvasLookupService = mock(CanvasLookupService.class);
        when(canvasLookupService.save(any(Map.class), any(UUID.class), any(String.class))).thenAnswer(invocation -> {
            Map<String, Object> canvas = invocation.getArgument(0);
            return UUID.fromString((String) canvas.get("id"));
        });

        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ImportExportController controller = new ImportExportController(
            fileValidator, formatTransformer, canvasLookupService, mock(JobService.class),
            mock(ArtifactService.class), mock(ImportExportAuthorizationService.class), auditEventPublisher, new ObjectMapper(), properties
        );

        ObjectMapper mapper = new ObjectMapper();
        ResponseEntity<ImportResponse> response = controller.importFile(file, mapper.writeValueAsString(options), "test-user");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("completed");
        assertThat(response.getBody().canvasId()).isNotNull();
        verify(auditEventPublisher).publishCanvasImport(
            eq(UUID.fromString(options.workspaceId())),
            eq(response.getBody().canvasId()),
            eq("test-user"),
            argThat(metadata -> "sync".equals(metadata.get("mode")) && "test.svg".equals(metadata.get("filename")))
        );
    }

    @Test
    void importOptionsAllowAdditionalFields() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect x=\"10\" y=\"20\" width=\"100\" height=\"50\"/></svg>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.svg", "image/svg+xml", svg);
        UUID workspaceId = UUID.randomUUID();

        CanvasLookupService canvasLookupService = mock(CanvasLookupService.class);
        when(canvasLookupService.save(any(Map.class), any(UUID.class), any(String.class))).thenAnswer(invocation -> {
            Map<String, Object> canvas = invocation.getArgument(0);
            return UUID.fromString((String) canvas.get("id"));
        });

        ImportExportController controller = new ImportExportController(
            fileValidator, formatTransformer, canvasLookupService, mock(JobService.class),
            mock(ArtifactService.class), mock(ImportExportAuthorizationService.class), mock(AuditEventPublisher.class), new ObjectMapper(), properties
        );

        String optionsJson = "{\"workspaceId\":\"" + workspaceId + "\",\"title\":\"Imported SVG\",\"metadata\":{\"source\":\"smoke\"}}";
        ResponseEntity<ImportResponse> response = controller.importFile(file, optionsJson, "test-user");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("completed");
    }

    @Test
    void unsupportedFormatIsRejected() {
        byte[] unknown = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
        MockMultipartFile file = new MockMultipartFile("file", "data.bin", "application/octet-stream", unknown);
        ImportOptions options = new ImportOptions(UUID.randomUUID().toString(), "Bad file", null);

        ImportExportController controller = new ImportExportController(
            fileValidator, formatTransformer, mock(CanvasLookupService.class), mock(JobService.class),
            mock(ArtifactService.class), mock(ImportExportAuthorizationService.class), mock(AuditEventPublisher.class), new ObjectMapper(), properties
        );

        ObjectMapper mapper = new ObjectMapper();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.importFile(file, mapper.writeValueAsString(options), "test-user"))
            .isInstanceOf(UnsupportedFormatException.class);
    }

    @Test
    void svgImportProducesCanvasElements() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect x=\"5\" y=\"5\" width=\"80\" height=\"40\"/><circle cx=\"50\" cy=\"50\" r=\"20\"/></svg>".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> canvas = formatTransformer.importFile(new MockMultipartFile("file", "test.svg", "image/svg+xml", svg), "Shapes");
        assertThat(canvas).containsKey("elements");
        assertThat(canvas.get("elements")).asList().hasSize(2);
    }

    @Test
    void exportSvgProducesValidBytes() throws Exception {
        Map<String, Object> canvas = formatTransformer.svgToCanvas(
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect x=\"10\" y=\"10\" width=\"100\" height=\"50\"/></svg>".getBytes(StandardCharsets.UTF_8),
            "Source"
        );
        byte[] exported = formatTransformer.exportCanvasToSvg(canvas);
        String svg = new String(exported, StandardCharsets.UTF_8);
        assertThat(svg).contains("<svg").contains("</svg>");
    }

    @Test
    void exportCanvasSvgPreservesCanvasStylesAndContent() throws Exception {
        Map<String, Object> rectangle = new LinkedHashMap<>();
        rectangle.put("id", "el-gateway");
        rectangle.put("type", "rectangle");
        rectangle.put("x", 160);
        rectangle.put("y", 140);
        rectangle.put("width", 210);
        rectangle.put("height", 104);
        rectangle.put("rotation", 0);
        rectangle.put("style", Map.of("fill", "#ffffff", "stroke", "#4d7cfe", "strokeWidth", 2, "opacity", 1));
        rectangle.put("data", Map.of("text", "REST Gateway"));
        rectangle.put("zIndex", 1);

        Map<String, Object> arrow = new LinkedHashMap<>();
        arrow.put("id", "el-arrow");
        arrow.put("type", "arrow");
        arrow.put("x", 610);
        arrow.put("y", 220);
        arrow.put("width", 132);
        arrow.put("height", 96);
        arrow.put("style", Map.of("fill", "transparent", "stroke", "#273142", "strokeWidth", 3, "opacity", 1));
        arrow.put("data", Map.of("start", Map.of("x", 610, "y", 220), "end", Map.of("x", 740, "y", 316)));
        arrow.put("zIndex", 2);

        Map<String, Object> freehand = new LinkedHashMap<>();
        freehand.put("id", "el-freehand");
        freehand.put("type", "freehand");
        freehand.put("style", Map.of("fill", "transparent", "stroke", "#ff5f8f", "strokeWidth", 4, "opacity", 1));
        freehand.put("data", Map.of("points", List.of(Map.of("x", 540, "y", 470), Map.of("x", 584, "y", 430), Map.of("x", 650, "y", 504))));
        freehand.put("zIndex", 3);

        Map<String, Object> curve = new LinkedHashMap<>();
        curve.put("id", "el-curve");
        curve.put("type", "curve");
        curve.put("x", 210);
        curve.put("y", 300);
        curve.put("width", 260);
        curve.put("height", 118);
        curve.put("style", Map.of("fill", "transparent", "stroke", "#8b95a7", "strokeWidth", 2, "opacity", 1));
        curve.put("data", Map.of(
            "start", Map.of("x", 210, "y", 370),
            "controlStart", Map.of("x", 284, "y", 300),
            "controlEnd", Map.of("x", 392, "y", 418),
            "end", Map.of("x", 470, "y", 342)
        ));
        curve.put("zIndex", 4);

        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("metadata", Map.of("width", 1000, "height", 700, "backgroundColor", "#eef2f5", "gridEnabled", true));
        canvas.put("elements", List.of(rectangle, arrow, freehand, curve));

        String svg = new String(formatTransformer.exportCanvasToSvg(canvas), StandardCharsets.UTF_8);

        assertThat(svg)
            .contains("#eef2f5")
            .contains("viewBox=\"102 103.6 696 436.8\"")
            .doesNotContain("viewBox=\"0 0 1000 700\"")
            .contains("#4d7cfe")
            .contains("REST Gateway")
            .contains("marker-end=\"url(#canvas-arrow-head)\"")
            .contains("polyline")
            .contains("stroke-dasharray=\"7 8\"")
            .contains("M 210 370 C 284 300 392 418 470 342")
            .contains("#ff5f8f");
    }

    @Test
    void exportCanvasSyncPublishesAuditEvent() throws Exception {
        UUID canvasId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        CanvasLookupService canvasLookupService = mock(CanvasLookupService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        when(canvasLookupService.load(canvasId, "test-user")).thenReturn(Map.of(
            "id", canvasId.toString(),
            "workspaceId", workspaceId.toString(),
            "title", "Diagram",
            "elements", List.of()
        ));

        ImportExportController controller = new ImportExportController(
            fileValidator, formatTransformer, canvasLookupService, mock(JobService.class),
            mock(ArtifactService.class), mock(ImportExportAuthorizationService.class), auditEventPublisher, new ObjectMapper(), properties
        );

        ResponseEntity<?> response = controller.exportCanvas(canvasId, "json", "test-user");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditEventPublisher).publishCanvasExport(
            eq(workspaceId),
            eq(canvasId),
            eq("test-user"),
            argThat(metadata -> "sync".equals(metadata.get("mode"))
                && "json".equals(metadata.get("format"))
                && ((Number) metadata.get("bytes")).intValue() > 0)
        );
    }

    @Test
    void exportJobsListMapsFrontendFilters() {
        UUID jobId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Instant now = Instant.now();
        JobService jobService = mock(JobService.class);
        JobState state = new JobState(jobId, "export", workspaceId, "test-user", JobStatus.COMPLETED, 100, "exports/canvas.svg", null, now, now);
        when(jobService.listJobs("export", "EXPORT", JobStatus.COMPLETED, workspaceId, 0, 20, "test-user")).thenReturn(List.of(state));
        when(jobService.countJobs("export", "EXPORT", JobStatus.COMPLETED, workspaceId, "test-user")).thenReturn(1L);

        ImportExportController controller = new ImportExportController(
            fileValidator, formatTransformer, mock(CanvasLookupService.class), jobService,
            mock(ArtifactService.class), mock(ImportExportAuthorizationService.class), mock(AuditEventPublisher.class), new ObjectMapper(), properties
        );

        ResponseEntity<Map<String, Object>> response = controller.listExportJobs(workspaceId.toString(), null, "succeeded", "EXPORT", 0, 20, "test-user");
        List<?> jobs = (List<?>) response.getBody().get("jobs");
        Map<?, ?> job = (Map<?, ?>) jobs.get(0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("total", 1L);
        assertThat(job.get("jobId")).isEqualTo(jobId.toString());
        assertThat(job.get("status")).isEqualTo("succeeded");
        assertThat(job.get("workspaceId")).isEqualTo(workspaceId.toString());
    }

    @Test
    void methodNotSupportedReturns405InsteadOf500() {
        ResponseEntity<?> response = new GlobalExceptionHandler().handleMethodNotSupported(new HttpRequestMethodNotSupportedException("GET"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void exportJobDownloadRequiresOwnerOrWorkspacePermission() {
        UUID jobId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        JobService jobService = mock(JobService.class);
        ArtifactService artifactService = mock(ArtifactService.class);
        ImportExportAuthorizationService authorizationService = mock(ImportExportAuthorizationService.class);
        when(jobService.find(jobId)).thenReturn(new JobState(
            jobId,
            "export",
            workspaceId,
            "owner-subject",
            JobStatus.COMPLETED,
            100,
            "exports/" + jobId + "/canvas.svg",
            null,
            Instant.now(),
            Instant.now()
        ));
        doThrow(new ForbiddenException("denied"))
            .when(authorizationService)
            .requirePermission(workspaceId, "other-subject", "workspace:read");

        ImportExportController controller = new ImportExportController(
            fileValidator, formatTransformer, mock(CanvasLookupService.class), jobService,
            artifactService, authorizationService, mock(AuditEventPublisher.class), new ObjectMapper(), properties
        );

        assertThatThrownBy(() -> controller.downloadExportJob(jobId, "other-subject"))
            .isInstanceOf(ForbiddenException.class);
        verify(artifactService, never()).signedUrl(any(String.class));
    }

    @Test
    void jobStateJsonSupportsInstantFields() throws Exception {
        ObjectMapper mapper = new ObjectMapperConfig().objectMapper();
        Instant now = Instant.parse("2026-06-23T09:00:00Z");
        JobState state = new JobState(
            UUID.randomUUID(),
            "export",
            UUID.randomUUID(),
            "owner-subject",
            JobStatus.PENDING,
            0,
            null,
            null,
            now,
            now
        );

        JobState roundTrip = mapper.readValue(mapper.writeValueAsString(state), JobState.class);

        assertThat(roundTrip.createdAt()).isEqualTo(now);
        assertThat(roundTrip.updatedAt()).isEqualTo(now);
        assertThat(roundTrip.workspaceId()).isEqualTo(state.workspaceId());
        assertThat(roundTrip.userSubject()).isEqualTo("owner-subject");
    }

    @Test
    void fileExceedingMaxSizeIsRejected() {
        byte[] oversized = new byte[104857601];
        MockMultipartFile file = new MockMultipartFile("file", "big.svg", "image/svg+xml", oversized);
        ImportOptions options = new ImportOptions(UUID.randomUUID().toString(), "Big", null);

        ImportExportController controller = new ImportExportController(
            fileValidator, formatTransformer, mock(CanvasLookupService.class), mock(JobService.class),
            mock(ArtifactService.class), mock(ImportExportAuthorizationService.class), mock(AuditEventPublisher.class), new ObjectMapper(), properties
        );

        ObjectMapper mapper = new ObjectMapper();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.importFile(file, mapper.writeValueAsString(options), "test-user"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("File exceeds");
    }

    @Test
    void svgWithDoctypeIsRejected() {
        byte[] svg = """
            <?xml version="1.0"?>
            <!DOCTYPE svg [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <svg xmlns="http://www.w3.org/2000/svg"><text>&xxe;</text></svg>
            """.getBytes(StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> formatTransformer.svgToCanvas(svg, "Unsafe"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Failed to parse SVG");
    }
}
