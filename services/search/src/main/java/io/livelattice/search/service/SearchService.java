package io.livelattice.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.livelattice.search.config.SearchProperties;
import io.livelattice.search.dto.SearchResponse;
import io.livelattice.search.dto.SearchResult;
import io.livelattice.search.exception.ValidationException;
import io.livelattice.search.model.SearchCriteria;
import io.livelattice.search.model.SearchType;
import io.livelattice.search.opensearch.OpenSearchGateway;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private final OpenSearchGateway gateway;
    private final SearchProperties properties;
    private final ObjectMapper objectMapper;

    public SearchService(OpenSearchGateway gateway, SearchProperties properties, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SearchResponse search(SearchCriteria criteria) {
        JsonNode body = requestBody(criteria);
        String endpoint = "/" + String.join(",", indexes(criteria.types())) + "/_search";
        JsonNode response = gateway.request("POST", endpoint, body, 200).body();
        List<SearchResult> results = parseResults(response.path("hits").path("hits"));
        return new SearchResponse(
            results,
            total(response.path("hits").path("total")),
            criteria.page(),
            criteria.size(),
            nextSearchAfter(response.path("hits").path("hits")),
            facets(response.path("aggregations"))
        );
    }

    private JsonNode requestBody(SearchCriteria criteria) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", criteria.size());
        body.put("track_total_hits", true);
        body.put("timeout", properties.getSearchTimeoutSeconds() + "s");
        if (criteria.searchAfter() != null) {
            body.set("search_after", decodeSearchAfter(criteria.searchAfter()));
        } else if (criteria.page() > 1) {
            int offset = (criteria.page() - 1) * criteria.size();
            if (offset >= 10000) {
                throw new ValidationException("Use search_after for pages beyond 10000 results");
            }
            body.put("from", offset);
        }
        body.set("query", query(criteria));
        body.set("sort", sort());
        body.set("highlight", highlight());
        body.set("aggs", aggregations());
        return body;
    }

    private ObjectNode query(SearchCriteria criteria) {
        ObjectNode query = objectMapper.createObjectNode();
        ObjectNode bool = query.putObject("bool");
        ArrayNode must = bool.putArray("must");
        ObjectNode multiMatch = objectMapper.createObjectNode();
        ObjectNode multiMatchBody = multiMatch.putObject("multi_match");
        multiMatchBody.put("query", criteria.query());
        multiMatchBody.set("fields", array("title^3", "content_text", "content", "tags^2", "name^3", "email"));
        must.add(multiMatch);
        ArrayNode filter = bool.putArray("filter");
        filter.add(term("is_deleted", false));
        if (criteria.workspaceId() != null) {
            filter.add(term("workspace_id", criteria.workspaceId()));
        }
        if (!criteria.tags().isEmpty()) {
            filter.add(terms("tags", criteria.tags()));
        }
        if (criteria.from() != null || criteria.to() != null) {
            filter.add(dateRange(criteria.from(), criteria.to()));
        }
        return query;
    }

    private ObjectNode term(String field, Object value) {
        ObjectNode query = objectMapper.createObjectNode();
        ObjectNode term = query.putObject("term");
        term.set(field, objectMapper.valueToTree(value));
        return query;
    }

    private ObjectNode terms(String field, List<String> values) {
        ObjectNode query = objectMapper.createObjectNode();
        ObjectNode terms = query.putObject("terms");
        terms.set(field, objectMapper.valueToTree(values));
        return query;
    }

    private ObjectNode dateRange(Instant from, Instant to) {
        ObjectNode query = objectMapper.createObjectNode();
        ObjectNode range = query.putObject("range");
        ObjectNode updatedAt = range.putObject("updated_at");
        if (from != null) {
            updatedAt.put("gte", from.toString());
        }
        if (to != null) {
            updatedAt.put("lte", to.toString());
        }
        return query;
    }

    private ArrayNode sort() {
        ArrayNode sort = objectMapper.createArrayNode();
        sort.add(sortField("_score", "desc", null));
        sort.add(sortField("updated_at", "desc", "date"));
        sort.add(sortField("id", "asc", "keyword"));
        return sort;
    }

    private ObjectNode sortField(String field, String order, String unmappedType) {
        ObjectNode sortField = objectMapper.createObjectNode();
        ObjectNode options = sortField.putObject(field);
        options.put("order", order);
        if (unmappedType != null) {
            options.put("unmapped_type", unmappedType);
        }
        return sortField;
    }

    private ObjectNode highlight() {
        ObjectNode highlight = objectMapper.createObjectNode();
        ObjectNode fields = highlight.putObject("fields");
        fields.set("title", objectMapper.createObjectNode());
        fields.set("content_text", objectMapper.createObjectNode());
        fields.set("content", objectMapper.createObjectNode());
        fields.set("name", objectMapper.createObjectNode());
        return highlight;
    }

    private ObjectNode aggregations() {
        ObjectNode aggregations = objectMapper.createObjectNode();
        aggregations.set("types", termsAggregation("type", 10));
        aggregations.set("tags", termsAggregation("tags", 50));
        return aggregations;
    }

    private ObjectNode termsAggregation(String field, int size) {
        ObjectNode aggregation = objectMapper.createObjectNode();
        ObjectNode terms = aggregation.putObject("terms");
        terms.put("field", field);
        terms.put("size", size);
        return aggregation;
    }

    private List<String> indexes(List<SearchType> types) {
        if (types == null || types.isEmpty()) {
            return properties.indexNamesByType().values().stream().toList();
        }
        return types.stream()
            .map(properties::indexName)
            .distinct()
            .toList();
    }

    private List<SearchResult> parseResults(JsonNode hits) {
        List<SearchResult> results = new ArrayList<>();
        if (!hits.isArray()) {
            return results;
        }
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            results.add(new SearchResult(
                text(source, "id"),
                text(source, "type"),
                text(source, "workspace_id"),
                firstText(source, "title", "name"),
                firstText(source, "content_text", "content", "email"),
                textList(source.path("tags")),
                firstText(source, "author_id", "created_by"),
                instant(source.path("created_at")),
                instant(source.path("updated_at")),
                highlights(hit.path("highlight"))
            ));
        }
        return results;
    }

    private long total(JsonNode total) {
        if (total.isNumber()) {
            return total.asLong();
        }
        return total.path("value").asLong(0);
    }

    private Map<String, Map<String, Long>> facets(JsonNode aggregations) {
        Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
        facets.put("type", buckets(aggregations.path("types").path("buckets")));
        facets.put("tags", buckets(aggregations.path("tags").path("buckets")));
        return facets;
    }

    private Map<String, Long> buckets(JsonNode bucketsNode) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        if (!bucketsNode.isArray()) {
            return buckets;
        }
        for (JsonNode bucket : bucketsNode) {
            buckets.put(bucket.path("key").asText(), bucket.path("doc_count").asLong());
        }
        return buckets;
    }

    private String nextSearchAfter(JsonNode hits) {
        if (!hits.isArray() || hits.isEmpty()) {
            return null;
        }
        JsonNode sort = hits.get(hits.size() - 1).path("sort");
        if (!sort.isArray()) {
            return null;
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(sort);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception ex) {
            throw new ValidationException("Failed to encode search_after");
        }
    }

    private ArrayNode decodeSearchAfter(String searchAfter) {
        try {
            String json = searchAfter.trim().startsWith("[")
                ? searchAfter
                : new String(Base64.getUrlDecoder().decode(searchAfter), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                throw new ValidationException("search_after must be a JSON array token");
            }
            return (ArrayNode) node;
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid search_after token");
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("Invalid search_after token");
        }
    }

    private Map<String, List<String>> highlights(JsonNode highlights) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (!highlights.isObject()) {
            return result;
        }
        highlights.fields().forEachRemaining(entry -> result.put(entry.getKey(), textList(entry.getValue())));
        return result;
    }

    private List<String> textList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            values.add(value.asText());
        }
        return values;
    }

    private Instant instant(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
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
