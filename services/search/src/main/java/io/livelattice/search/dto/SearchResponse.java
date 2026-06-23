package io.livelattice.search.dto;

import java.util.List;
import java.util.Map;

public record SearchResponse(
    List<SearchResult> results,
    long total,
    int page,
    int size,
    String nextSearchAfter,
    Map<String, Map<String, Long>> facets
) {
}
