package io.livelattice.core.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SearchIndexEvent(
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
) {}
