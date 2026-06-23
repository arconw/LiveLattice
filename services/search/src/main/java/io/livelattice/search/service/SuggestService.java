package io.livelattice.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.livelattice.search.config.SearchProperties;
import io.livelattice.search.dto.SuggestResponse;
import io.livelattice.search.opensearch.OpenSearchGateway;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class SuggestService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final OpenSearchGateway gateway;
    private final SearchProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public SuggestService(OpenSearchGateway gateway,
                          SearchProperties properties,
                          ObjectMapper objectMapper,
                          StringRedisTemplate redisTemplate) {
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public SuggestResponse suggest(String query, String workspaceId) {
        String normalized = query.trim();
        String cacheKey = cacheKey(normalized, workspaceId);
        List<String> cached = cached(cacheKey);
        if (cached != null) {
            return new SuggestResponse(cached);
        }
        List<String> suggestions = fetchSuggestions(normalized, workspaceId);
        cache(cacheKey, suggestions);
        return new SuggestResponse(suggestions);
    }

    private List<String> fetchSuggestions(String query, String workspaceId) {
        JsonNode body = suggestBody(query);
        String endpoint = "/" + String.join(",", properties.suggestIndexNames()) + "/_search";
        JsonNode response = gateway.request("POST", endpoint, body, 200).body();
        Set<String> suggestions = new LinkedHashSet<>();
        JsonNode options = response.path("suggest").path("title-suggest").path(0).path("options");
        if (!options.isArray()) {
            return List.of();
        }
        for (JsonNode option : options) {
            JsonNode source = option.path("_source");
            if (workspaceId != null && !workspaceId.isBlank() && !workspaceId.equals(source.path("workspace_id").asText())) {
                continue;
            }
            String text = firstText(source, "title", "name");
            if (text != null && !text.isBlank()) {
                suggestions.add(text);
            }
            if (suggestions.size() >= properties.getSuggestCacheSize()) {
                break;
            }
        }
        return new ArrayList<>(suggestions);
    }

    private ObjectNode suggestBody(String query) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", 0);
        body.put("timeout", properties.getSuggestTimeoutSeconds() + "s");
        body.set("_source", array("id", "workspace_id", "title", "name"));
        ObjectNode suggest = body.putObject("suggest");
        ObjectNode titleSuggest = suggest.putObject("title-suggest");
        titleSuggest.put("prefix", query);
        ObjectNode completion = titleSuggest.putObject("completion");
        completion.put("field", "suggest_title");
        completion.put("size", Math.max(properties.getSuggestCacheSize() * 3, properties.getSuggestCacheSize()));
        completion.put("skip_duplicates", true);
        return body;
    }

    private List<String> cached(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cached, STRING_LIST);
        } catch (Exception ex) {
            return null;
        }
    }

    private void cache(String cacheKey, List<String> suggestions) {
        try {
            redisTemplate.opsForValue().set(
                cacheKey,
                objectMapper.writeValueAsString(suggestions),
                properties.getSuggestCacheTtl().toMillis(),
                TimeUnit.MILLISECONDS
            );
        } catch (Exception ignored) {
        }
    }

    private String cacheKey(String query, String workspaceId) {
        String workspace = workspaceId == null || workspaceId.isBlank() ? "all" : workspaceId.trim();
        return "suggest:" + workspace + ":" + query.toLowerCase();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private ArrayNode array(String... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
