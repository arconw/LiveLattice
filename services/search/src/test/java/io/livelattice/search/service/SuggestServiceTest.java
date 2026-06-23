package io.livelattice.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.livelattice.search.config.SearchProperties;
import io.livelattice.search.dto.SuggestResponse;
import io.livelattice.search.opensearch.OpenSearchGateway;
import io.livelattice.search.opensearch.OpenSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void fetchesSuggestionsAndCachesThem() throws Exception {
        RecordingGateway gateway = new RecordingGateway(objectMapper.readTree("""
            {
              "suggest": {
                "title-suggest": [
                  {
                    "options": [
                      {"_source": {"workspace_id": "workspace-1", "title": "Diagram Flow"}},
                      {"_source": {"workspace_id": "workspace-2", "title": "Other Workspace"}},
                      {"_source": {"workspace_id": "workspace-1", "name": "Diagram User"}}
                    ]
                  }
                ]
              }
            }
            """));
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.get("suggest:workspace-1:diag")).thenReturn(null);
        SearchProperties properties = new SearchProperties();
        SuggestService service = new SuggestService(gateway, properties, objectMapper, redisTemplate);

        SuggestResponse response = service.suggest("Diag", "workspace-1");

        assertThat(response.suggestions()).containsExactly("Diagram Flow", "Diagram User");
        assertThat(gateway.endpoint).isEqualTo("/canvases,documents,dashboards,templates,users/_search");
        assertThat(gateway.body.path("suggest").path("title-suggest").path("completion").path("field").asText())
            .isEqualTo("suggest_title");
        verify(operations).set(eq("suggest:workspace-1:diag"), any(String.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void returnsCachedSuggestionsWithoutOpenSearchCall() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.get("suggest:all:diag")).thenReturn("[\"Diagram\"]");
        RecordingGateway gateway = new RecordingGateway(objectMapper.createObjectNode());
        SuggestService service = new SuggestService(gateway, new SearchProperties(), objectMapper, redisTemplate);

        SuggestResponse response = service.suggest("diag", null);

        assertThat(response.suggestions()).containsExactly("Diagram");
        assertThat(gateway.endpoint).isNull();
    }

    private static class RecordingGateway implements OpenSearchGateway {
        private final JsonNode response;
        private String endpoint;
        private JsonNode body;

        private RecordingGateway(JsonNode response) {
            this.response = response;
        }

        @Override
        public OpenSearchResponse request(String method, String endpoint, JsonNode body, int... acceptedStatuses) {
            this.endpoint = endpoint;
            this.body = body;
            return new OpenSearchResponse(200, response);
        }
    }
}
