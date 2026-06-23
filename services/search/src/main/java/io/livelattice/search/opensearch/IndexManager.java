package io.livelattice.search.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.livelattice.search.config.SearchProperties;
import io.livelattice.search.dto.IndexStatus;
import io.livelattice.search.exception.SearchBackendException;
import io.livelattice.search.model.SearchType;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class IndexManager {

    private final OpenSearchGateway gateway;
    private final SearchProperties properties;
    private final ObjectMapper objectMapper;

    public IndexManager(OpenSearchGateway gateway, SearchProperties properties, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initIndexes() {
        if (properties.isAutoCreateIndexes()) {
            createIndexes();
        }
    }

    public void createIndexes() {
        createLifecyclePolicy();
        properties.indexNamesByType().forEach((type, index) -> {
            if (!indexExists(index)) {
                gateway.request("PUT", "/" + index, indexDefinition(type), 200);
            }
        });
    }

    public void recreateIndexes() {
        properties.indexNamesByType().values().forEach(index -> {
            if (indexExists(index)) {
                gateway.request("DELETE", "/" + index, null, 200);
            }
        });
        createIndexes();
    }

    public List<IndexStatus> indexStatuses() {
        List<IndexStatus> statuses = new ArrayList<>();
        for (String index : properties.indexNamesByType().values()) {
            if (!indexExists(index)) {
                statuses.add(new IndexStatus(index, false, 0, 0, "missing"));
                continue;
            }
            long docsCount = countDocuments(index);
            long sizeBytes = storeSize(index);
            String health = health(index);
            statuses.add(new IndexStatus(index, true, docsCount, sizeBytes, health));
        }
        return statuses;
    }

    public boolean indexExists(String index) {
        return gateway.request("HEAD", "/" + index, null, 200, 404).status() == 200;
    }

    private long countDocuments(String index) {
        JsonNode body = gateway.request("GET", "/" + index + "/_count", null, 200).body();
        return body.path("count").asLong(0);
    }

    private long storeSize(String index) {
        JsonNode body = gateway.request("GET", "/" + index + "/_stats/store", null, 200).body();
        return body.path("indices").path(index).path("total").path("store").path("size_in_bytes").asLong(0);
    }

    private String health(String index) {
        JsonNode body = gateway.request("GET", "/_cluster/health/" + index, null, 200).body();
        return body.path("status").asText("unknown");
    }

    private ObjectNode indexDefinition(SearchType type) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("settings", settings());
        root.set("mappings", mappings(type));
        return root;
    }

    private ObjectNode settings() {
        ObjectNode settings = objectMapper.createObjectNode();
        settings.put("number_of_shards", 3);
        settings.put("number_of_replicas", 1);
        settings.put("plugins.index_state_management.policy_id", properties.getLifecyclePolicyName());
        ObjectNode analysis = settings.putObject("analysis");
        ObjectNode filters = analysis.putObject("filter");
        ObjectNode edgeNgram = filters.putObject("livelattice_edge_ngram");
        edgeNgram.put("type", "edge_ngram");
        edgeNgram.put("min_gram", 2);
        edgeNgram.put("max_gram", 20);
        ObjectNode analyzers = analysis.putObject("analyzer");
        ObjectNode analyzer = analyzers.putObject("livelattice_text");
        analyzer.put("type", "custom");
        analyzer.put("tokenizer", "standard");
        analyzer.set("filter", array("lowercase", "stop", "snowball", "livelattice_edge_ngram"));
        return settings;
    }

    private ObjectNode mappings(SearchType type) {
        ObjectNode mappings = objectMapper.createObjectNode();
        ObjectNode propertiesNode = mappings.putObject("properties");
        keyword(propertiesNode, "id");
        keyword(propertiesNode, "type");
        keyword(propertiesNode, "workspace_id");
        keyword(propertiesNode, "tags");
        keyword(propertiesNode, "created_by");
        keyword(propertiesNode, "author_id");
        keyword(propertiesNode, "canvas_id");
        date(propertiesNode, "created_at");
        date(propertiesNode, "updated_at");
        bool(propertiesNode, "is_deleted");
        bool(propertiesNode, "resolved");
        switch (type) {
            case COMMENT -> {
                text(propertiesNode, "content", "standard", false);
                text(propertiesNode, "content_text", "standard", false);
            }
            case USER -> {
                text(propertiesNode, "name", "livelattice_text", true);
                text(propertiesNode, "email", "standard", false);
                text(propertiesNode, "content_text", "standard", false);
                completion(propertiesNode, "suggest_title");
            }
            default -> {
                text(propertiesNode, "title", "livelattice_text", true);
                text(propertiesNode, "content_text", "livelattice_text", false);
                completion(propertiesNode, "suggest_title");
            }
        }
        return mappings;
    }

    private void keyword(ObjectNode propertiesNode, String name) {
        propertiesNode.set(name, objectMapper.valueToTree(Map.of("type", "keyword")));
    }

    private void date(ObjectNode propertiesNode, String name) {
        propertiesNode.set(name, objectMapper.valueToTree(Map.of("type", "date")));
    }

    private void bool(ObjectNode propertiesNode, String name) {
        propertiesNode.set(name, objectMapper.valueToTree(Map.of("type", "boolean")));
    }

    private void completion(ObjectNode propertiesNode, String name) {
        propertiesNode.set(name, objectMapper.valueToTree(Map.of("type", "completion")));
    }

    private void text(ObjectNode propertiesNode, String name, String analyzer, boolean keywordField) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("type", "text");
        field.put("analyzer", analyzer);
        if (keywordField) {
            ObjectNode fields = field.putObject("fields");
            fields.set("keyword", objectMapper.valueToTree(Map.of("type", "keyword")));
        }
        propertiesNode.set(name, field);
    }

    private ArrayNode array(String... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private ArrayNode array(ObjectNode... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ObjectNode value : values) {
            array.add(value);
        }
        return array;
    }

    private void createLifecyclePolicy() {
        String endpoint = "/_plugins/_ism/policies/" + properties.getLifecyclePolicyName();
        try {
            gateway.request("PUT", endpoint, lifecyclePolicy(), 200, 201);
        } catch (SearchBackendException ex) {
            if (!lifecyclePolicyExists(endpoint)) {
                throw ex;
            }
        }
    }

    private boolean lifecyclePolicyExists(String endpoint) {
        try {
            return gateway.request("GET", endpoint, null, 200).status() == 200;
        } catch (SearchBackendException ex) {
            return false;
        }
    }

    private ObjectNode lifecyclePolicy() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode policy = root.putObject("policy");
        policy.put("description", "LiveLattice search index lifecycle");
        policy.put("default_state", "hot");
        ArrayNode states = policy.putArray("states");
        states.add(state("hot", array(rolloverAction()), array(transition("warm", "30d"))));
        states.add(state("warm", array(replicaAction()), array(transition("delete", "365d"))));
        states.add(state("delete", array(deleteAction()), objectMapper.createArrayNode()));
        ArrayNode templates = policy.putArray("ism_template");
        ObjectNode template = templates.addObject();
        template.set("index_patterns", objectMapper.valueToTree(properties.indexNamesByType().values().stream().toList()));
        template.put("priority", 100);
        return root;
    }

    private ObjectNode state(String name, ArrayNode actions, ArrayNode transitions) {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("name", name);
        state.set("actions", actions);
        state.set("transitions", transitions);
        return state;
    }

    private ObjectNode rolloverAction() {
        ObjectNode action = objectMapper.createObjectNode();
        ObjectNode rollover = action.putObject("rollover");
        rollover.put("min_size", "50gb");
        rollover.put("min_index_age", "30d");
        return action;
    }

    private ObjectNode replicaAction() {
        ObjectNode action = objectMapper.createObjectNode();
        ObjectNode replica = action.putObject("replica_count");
        replica.put("number_of_replicas", 1);
        return action;
    }

    private ObjectNode deleteAction() {
        ObjectNode action = objectMapper.createObjectNode();
        action.set("delete", objectMapper.createObjectNode());
        return action;
    }

    private ObjectNode transition(String state, String minIndexAge) {
        ObjectNode transition = objectMapper.createObjectNode();
        transition.put("state_name", state);
        ObjectNode conditions = transition.putObject("conditions");
        conditions.put("min_index_age", minIndexAge);
        return transition;
    }
}
