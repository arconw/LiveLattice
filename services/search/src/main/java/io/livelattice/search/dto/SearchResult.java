package io.livelattice.search.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SearchResult(
    String id,
    String type,
    String workspaceId,
    String title,
    String content,
    List<String> tags,
    String authorId,
    Instant createdAt,
    Instant updatedAt,
    Map<String, List<String>> highlights
) {
}
