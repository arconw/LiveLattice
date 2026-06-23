package io.livelattice.importexport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.dto.ImportOptions;
import io.livelattice.importexport.dto.ImportResponse;
import io.livelattice.importexport.model.JobStatus;
import io.livelattice.importexport.service.JobService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class ImportExportIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String USER_SUBJECT = "test-subject";
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final String OTHER_USER_SUBJECT = "other-subject";
    private static final String INTERNAL_SECRET = "livelattice_internal_dev_secret";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.1-alpine"))
        .withDatabaseName("livelattice")
        .withUsername("livelattice")
        .withPassword("livelattice_dev_password");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.4-alpine"))
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    @Container
    static MinIOContainer minio = new MinIOContainer(
        DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("livelattice.importexport.storage-endpoint", minio::getS3URL);
        registry.add("livelattice.importexport.storage-access-key", minio::getUserName);
        registry.add("livelattice.importexport.storage-secret-key", minio::getPassword);
        registry.add("livelattice.importexport.storage-bucket", () -> "livelattice-imports");
        registry.add("livelattice.importexport.storage-url-expiry-minutes", () -> "1");
        registry.add("livelattice.auth.internal-secret", () -> INTERNAL_SECRET);
        registry.add("server.port", () -> "0");
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobService jobService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void readyReportsDependenciesHealthy() throws Exception {
        mockMvc.perform(get("/ready"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.storage.status").value("healthy"))
            .andExpect(jsonPath("$.storage.bucket").value("livelattice-imports"))
            .andExpect(jsonPath("$.queue.status").value("healthy"))
            .andExpect(jsonPath("$.cache.status").value("healthy"))
            .andExpect(jsonPath("$.cache.ping").value("PONG"));
    }

    @Test
    void syncSvgImportReturnsCanvasIdAndCanBeExported() throws Exception {
        byte[] svg = ("\u003c?xml version=\"1.0\" encoding=\"UTF-8\"?\u003e\n" +
            "\u003csvg xmlns=\"http://www.w3.org/2000/svg\"\u003e\n" +
            "  \u003crect x=\"10\" y=\"20\" width=\"200\" height=\"100\" fill=\"#cccccc\" stroke=\"#000000\" /\u003e\n" +
            "  \u003ccircle cx=\"150\" cy=\"150\" r=\"30\" fill=\"#ff0000\" /\u003e\n" +
            "\u003c/svg\u003e").getBytes(StandardCharsets.UTF_8);

        String workspaceId = UUID.randomUUID().toString();
        seedWorkspace(UUID.fromString(workspaceId));
        ImportOptions options = new ImportOptions(workspaceId, "Integration SVG", null);

        MvcResult result = mockMvc.perform(multipart("/import")
                .file(new MockMultipartFile("file", "shapes.svg", "image/svg+xml", svg))
                .param("options", objectMapper.writeValueAsString(options))
                .with(authenticated()))
            .andExpect(status().isOk())
            .andReturn();

        ImportResponse importResponse = objectMapper.readValue(result.getResponse().getContentAsString(), ImportResponse.class);
        org.assertj.core.api.Assertions.assertThat(importResponse.status()).isEqualTo("completed");
        org.assertj.core.api.Assertions.assertThat(importResponse.canvasId()).isNotNull();

        mockMvc.perform(post("/export/" + importResponse.canvasId() + "?format=svg").with(authenticated()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/svg+xml"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\u003csvg")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\u003c/svg\u003e")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("rect")));
    }

    @Test
    void unsupportedContentIsRejected() throws Exception {
        byte[] unknown = {0x50, 0x4b, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        String workspaceId = UUID.randomUUID().toString();
        seedWorkspace(UUID.fromString(workspaceId));
        ImportOptions options = new ImportOptions(workspaceId, "Bad file", null);

        mockMvc.perform(multipart("/import")
                .file(new MockMultipartFile("file", "data.zip", "application/zip", unknown))
                .param("options", objectMapper.writeValueAsString(options))
                .with(authenticated()))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void importIntoUnauthorizedWorkspaceIsForbidden() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect x=\"10\" y=\"20\" width=\"100\" height=\"50\"/></svg>".getBytes(StandardCharsets.UTF_8);
        String workspaceId = UUID.randomUUID().toString();
        ImportOptions options = new ImportOptions(workspaceId, "Forbidden SVG", null);

        mockMvc.perform(multipart("/import")
                .file(new MockMultipartFile("file", "shapes.svg", "image/svg+xml", svg))
                .param("options", objectMapper.writeValueAsString(options))
                .with(authenticated()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void svgWithDoctypeIsRejected() throws Exception {
        byte[] svg = """
            <?xml version="1.0"?>
            <!DOCTYPE svg [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <svg xmlns="http://www.w3.org/2000/svg"><text>&xxe;</text></svg>
            """.getBytes(StandardCharsets.UTF_8);
        String workspaceId = UUID.randomUUID().toString();
        seedWorkspace(UUID.fromString(workspaceId));
        ImportOptions options = new ImportOptions(workspaceId, "Unsafe SVG", null);

        mockMvc.perform(multipart("/import")
                .file(new MockMultipartFile("file", "unsafe.svg", "image/svg+xml", svg))
                .param("options", objectMapper.writeValueAsString(options))
                .with(authenticated()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void drawioGeometryFromMxGeometryIsImported() throws Exception {
        byte[] drawio = ("\u003c?xml version=\"1.0\" encoding=\"UTF-8\"?\u003e\n" +
            "\u003cmxGraphModel\u003e\n" +
            "  \u003croot\u003e\n" +
            "    \u003cmxCell id=\"0\" /\u003e\n" +
            "    \u003cmxCell id=\"1\" parent=\"0\" /\u003e\n" +
            "    \u003cmxCell id=\"2\" value=\"Box\" style=\"rounded\" vertex=\"1\" parent=\"1\"\u003e\n" +
            "      \u003cmxGeometry x=\"120\" y=\"80\" width=\"160\" height=\"90\" as=\"geometry\" /\u003e\n" +
            "    \u003c/mxCell\u003e\n" +
            "    \u003cmxCell id=\"3\" value=\"Circle\" style=\"ellipse\" vertex=\"1\" parent=\"1\"\u003e\n" +
            "      \u003cmxGeometry x=\"300\" y=\"200\" width=\"80\" height=\"80\" as=\"geometry\" /\u003e\n" +
            "    \u003c/mxCell\u003e\n" +
            "  \u003c/root\u003e\n" +
            "\u003c/mxGraphModel\u003e").getBytes(StandardCharsets.UTF_8);

        String workspaceId = UUID.randomUUID().toString();
        seedWorkspace(UUID.fromString(workspaceId));
        ImportOptions options = new ImportOptions(workspaceId, "DrawIO", null);

        MvcResult result = mockMvc.perform(multipart("/import")
                .file(new MockMultipartFile("file", "diagram.drawio", "application/xml", drawio))
                .param("options", objectMapper.writeValueAsString(options))
                .with(authenticated()))
            .andExpect(status().isOk())
            .andReturn();

        ImportResponse importResponse = objectMapper.readValue(result.getResponse().getContentAsString(), ImportResponse.class);
        org.assertj.core.api.Assertions.assertThat(importResponse.canvasId()).isNotNull();

        MvcResult exportResult = mockMvc.perform(post("/export/" + importResponse.canvasId() + "?format=json").with(authenticated()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andReturn();

        Map<String, Object> canvas = objectMapper.readValue(exportResult.getResponse().getContentAsString(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        List<?> elements = (List<?>) canvas.get("elements");
        org.assertj.core.api.Assertions.assertThat(elements).hasSize(2);
    }

    @Test
    void asyncJobEndpointsRejectAuthenticatedNonOwnerWithoutWorkspaceAccess() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        seedWorkspace(workspaceId);
        seedUser(OTHER_USER_ID, OTHER_USER_SUBJECT, "other@example.com", "Other User");

        UUID importJobId = jobService.createJob("import", workspaceId, USER_SUBJECT);
        UUID exportJobId = jobService.createJob("export", workspaceId, USER_SUBJECT);
        jobService.updateStatus(exportJobId, JobStatus.COMPLETED, 100, "exports/" + exportJobId + "/canvas.svg", null);

        mockMvc.perform(get("/import/jobs/" + importJobId).with(authenticated(OTHER_USER_SUBJECT, "other@example.com", "Other User")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("forbidden"));

        mockMvc.perform(get("/export/jobs/" + exportJobId).with(authenticated(OTHER_USER_SUBJECT, "other@example.com", "Other User")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("forbidden"));

        mockMvc.perform(get("/export/jobs/" + exportJobId + "/download").with(authenticated(OTHER_USER_SUBJECT, "other@example.com", "Other User")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("forbidden"));

        mockMvc.perform(get("/export/jobs/" + exportJobId).with(authenticated()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"));
    }

    private void seedWorkspace(UUID workspaceId) {
        seedUser(USER_ID, USER_SUBJECT, "owner@example.com", "Test Owner");
        jdbcTemplate.update("""
            INSERT INTO workspaces (id, owner_id, name, slug, tier, settings)
            VALUES (?, ?, ?, ?, 'FREE', '{}'::jsonb)
            ON CONFLICT (id) DO NOTHING
            """, workspaceId, USER_ID, "Integration Workspace", "workspace-" + workspaceId);
        jdbcTemplate.update("""
            INSERT INTO workspace_members (workspace_id, user_id, role)
            VALUES (?, ?, 'OWNER')
            ON CONFLICT (workspace_id, user_id) DO NOTHING
            """, workspaceId, USER_ID);
    }

    private void seedUser(UUID userId, String subject, String email, String displayName) {
        jdbcTemplate.update("""
            INSERT INTO users (id, external_subject, email, display_name)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (external_subject) DO UPDATE
            SET email = EXCLUDED.email, display_name = EXCLUDED.display_name
            """, userId, subject, email, displayName);
    }

    private RequestPostProcessor authenticated() {
        return authenticated(USER_SUBJECT, "owner@example.com", "Test Owner");
    }

    private RequestPostProcessor authenticated(String subject, String email, String displayName) {
        return request -> {
            request.addHeader("x-internal-auth-token", INTERNAL_SECRET);
            request.addHeader("x-auth-subject", subject);
            request.addHeader("x-auth-email", email);
            request.addHeader("x-auth-display-name", displayName);
            return request;
        };
    }
}
