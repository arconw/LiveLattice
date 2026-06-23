package io.livelattice.search.model;

import java.time.Instant;
import java.util.List;

public record SearchCriteria(
    String query,
    List<SearchType> types,
    String workspaceId,
    List<String> tags,
    Instant from,
    Instant to,
    int page,
    int size,
    String searchAfter
) {
}
