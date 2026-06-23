package io.livelattice.search.opensearch;

import io.livelattice.search.kafka.IndexEvent;
import io.livelattice.search.model.SearchType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SearchDocumentMapper {

    public Optional<SearchType> resolveType(IndexEvent event) {
        return SearchType.fromValue(event.entityType());
    }

    public Map<String, Object> toDocument(IndexEvent event, SearchType type) {
        Map<String, Object> document = new HashMap<>();
        if (event.metadata() != null) {
            document.putAll(event.metadata());
        }
        put(document, "id", event.id());
        put(document, "type", type.value());
        put(document, "workspace_id", event.workspaceId());
        put(document, "tags", event.tags());
        put(document, "author_id", event.authorId());
        put(document, "created_by", event.authorId());
        put(document, "canvas_id", event.canvasId());
        put(document, "created_at", toString(event.createdAt()));
        put(document, "updated_at", toString(event.updatedAt()));
        document.put("is_deleted", event.isDeletedEvent());
        document.put("resolved", event.resolved());
        switch (type) {
            case COMMENT -> mapComment(document, event);
            case USER -> mapUser(document, event);
            default -> mapSearchableContent(document, event);
        }
        return document;
    }

    public Map<String, Object> deletedDocument(IndexEvent event, SearchType type) {
        Map<String, Object> document = new HashMap<>();
        put(document, "id", event.id());
        put(document, "type", type.value());
        put(document, "workspace_id", event.workspaceId());
        document.put("is_deleted", true);
        put(document, "updated_at", toString(Instant.now()));
        return document;
    }

    private void mapSearchableContent(Map<String, Object> document, IndexEvent event) {
        put(document, "title", event.title());
        put(document, "content_text", event.contentText());
        put(document, "suggest_title", event.title());
    }

    private void mapComment(Map<String, Object> document, IndexEvent event) {
        put(document, "content", event.contentText());
        put(document, "content_text", event.contentText());
    }

    private void mapUser(Map<String, Object> document, IndexEvent event) {
        put(document, "name", firstNonBlank(event.title(), stringValue(document.get("name"))));
        put(document, "email", stringValue(document.get("email")));
        put(document, "content_text", event.contentText());
        put(document, "suggest_title", firstNonBlank(event.title(), stringValue(document.get("name"))));
    }

    private void put(Map<String, Object> document, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String string && string.isBlank()) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        document.put(key, value);
    }

    private String toString(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
