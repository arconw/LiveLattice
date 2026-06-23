package io.livelattice.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.livelattice.search.config.OpenSearchConfig;
import io.livelattice.search.config.SearchProperties;
import io.livelattice.search.model.SearchCriteria;
import io.livelattice.search.model.SearchType;
import io.livelattice.search.opensearch.IndexManager;
import io.livelattice.search.opensearch.OpenSearchClientGateway;
import io.livelattice.search.service.SearchService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
class SearchOpenSearchIntegrationTest {

    @Container
    static GenericContainer<?> opensearch = new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:3.3.2"))
        .withEnv("discovery.type", "single-node")
        .withEnv("plugins.security.disabled", "true")
        .withExposedPorts(9200)
        .withStartupTimeout(Duration.ofMinutes(3));

    @Test
    void createsIndexAndSearchesDocument() {
        ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
        SearchProperties properties = new SearchProperties();
        properties.setOpensearchUrl("http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200));
        OpenSearchClientGateway gateway = new OpenSearchClientGateway(
            new OpenSearchConfig().openSearchClient(properties, objectMapper),
            objectMapper
        );
        IndexManager indexManager = new IndexManager(gateway, properties, objectMapper);
        indexManager.recreateIndexes();
        gateway.request("PUT", "/canvases/_doc/canvas-1?refresh=true", objectMapper.valueToTree(java.util.Map.of(
            "id", "canvas-1",
            "type", "canvas",
            "workspace_id", "workspace-1",
            "title", "Diagram Flow",
            "content_text", "Architecture diagram",
            "tags", List.of("diagram"),
            "is_deleted", false,
            "updated_at", "2026-06-01T10:00:00Z",
            "suggest_title", "Diagram Flow"
        )), 200, 201);
        SearchService searchService = new SearchService(gateway, properties, objectMapper);

        var response = searchService.search(new SearchCriteria(
            "diagram",
            List.of(SearchType.CANVAS),
            "workspace-1",
            List.of(),
            null,
            null,
            1,
            10,
            null
        ));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.results().getFirst().id()).isEqualTo("canvas-1");
    }
}
