package io.livelattice.search.kafka;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record IndexEvent(
    String eventType,
    String entityType,
    String id,
    String workspaceId,
    String title,
    String contentText,
    List<String> tags,
    String authorId,
    String canvasId,
    Instant createdAt,
    Instant updatedAt,
    boolean deleted,
    boolean resolved,
    Map<String, Object> metadata
) {
    public IndexEvent withDefaults(String topic) {
        return new IndexEvent(
            firstNonBlank(eventType, topic),
            firstNonBlank(entityType, entityTypeFrom(topic)),
            id,
            workspaceId,
            title,
            contentText,
            tags,
            authorId,
            canvasId,
            createdAt,
            updatedAt,
            deleted,
            resolved,
            metadata
        );
    }

    public boolean isDeletedEvent() {
        String eventName = eventType == null ? "" : eventType.toLowerCase();
        return deleted || eventName.endsWith(".deleted") || eventName.endsWith(".delete") || eventName.contains("deleted");
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String entityTypeFrom(String topic) {
        if (topic == null || topic.isBlank()) {
            return null;
        }
        int separator = topic.indexOf('.');
        return separator == -1 ? topic : topic.substring(0, separator);
    }
}
