package io.livelattice.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.livelattice.search.config.SearchProperties;
import io.livelattice.search.dto.SearchResponse;
import io.livelattice.search.model.SearchCriteria;
import io.livelattice.search.model.SearchType;
import io.livelattice.search.opensearch.OpenSearchGateway;
import io.livelattice.search.opensearch.OpenSearchResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .build();

    @Test
    void buildsSearchRequestAndParsesResponse() throws Exception {
        RecordingGateway gateway = new RecordingGateway(objectMapper.readTree("""
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "id": "canvas-1",
                      "type": "canvas",
                      "workspace_id": "workspace-1",
                      "title": "Diagram",
                      "content_text": "Flow content",
                      "tags": ["diagram"],
                      "created_by": "user-1",
                      "created_at": "2026-06-01T10:00:00Z",
                      "updated_at": "2026-06-02T10:00:00Z"
                    },
                    "highlight": {"title": ["<em>Diagram</em>"]},
                    "sort": [1.0, "2026-06-02T10:00:00Z", "canvas-1"]
                  }
                ]
              },
              "aggregations": {
                "types": {"buckets": [{"key": "canvas", "doc_count": 1}]},
                "tags": {"buckets": [{"key": "diagram", "doc_count": 1}]}
              }
            }
            """));
        SearchService service = new SearchService(gateway, new SearchProperties(), objectMapper);
        SearchCriteria criteria = new SearchCriteria(
            "diagram",
            List.of(SearchType.CANVAS),
            "workspace-1",
            List.of("diagram"),
            Instant.parse("2026-06-01T00:00:00Z"),
            null,
            1,
            20,
            null
        );

        SearchResponse response = service.search(criteria);

        assertThat(gateway.endpoint).isEqualTo("/canvases/_search");
        assertThat(gateway.body.path("query").path("bool").path("must").path(0).path("multi_match").path("fields"))
            .extracting(JsonNode::asText)
            .contains("title^3", "content_text", "tags^2");
        assertThat(gateway.body.path("query").path("bool").path("filter").toString())
            .contains("workspace_id")
            .contains("diagram")
            .contains("updated_at");
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().title()).isEqualTo("Diagram");
        assertThat(response.results().getFirst().highlights()).containsKey("title");
        assertThat(response.facets().get("type")).containsEntry("canvas", 1L);
        assertThat(response.nextSearchAfter()).isNotBlank();
    }

    @Test
    void acceptsJsonSearchAfterToken() throws Exception {
        RecordingGateway gateway = new RecordingGateway(objectMapper.readTree("""
            {"hits": {"total": 0, "hits": []}, "aggregations": {}}
            """));
        SearchService service = new SearchService(gateway, new SearchProperties(), objectMapper);
        SearchCriteria criteria = new SearchCriteria("diagram", List.of(), null, List.of(), null, null, 1, 20, "[1,\"a\"]");

        service.search(criteria);

        assertThat(gateway.body.path("search_after").isArray()).isTrue();
        assertThat(gateway.body.path("from").isMissingNode()).isTrue();
    }

    private static class RecordingGateway implements OpenSearchGateway {
        private final JsonNode response;
        private String method;
        private String endpoint;
        private JsonNode body;

        private RecordingGateway(JsonNode response) {
            this.response = response;
        }

        @Override
        public OpenSearchResponse request(String method, String endpoint, JsonNode body, int... acceptedStatuses) {
            this.method = method;
            this.endpoint = endpoint;
            this.body = body;
            return new OpenSearchResponse(200, response);
        }
    }
}
