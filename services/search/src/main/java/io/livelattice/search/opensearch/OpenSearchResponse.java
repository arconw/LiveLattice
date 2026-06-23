package io.livelattice.search.opensearch;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenSearchResponse(
    int status,
    JsonNode body
) {
}
