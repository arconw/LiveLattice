package io.livelattice.search.opensearch;

import com.fasterxml.jackson.databind.JsonNode;

public interface OpenSearchGateway {
    OpenSearchResponse request(String method, String endpoint, JsonNode body, int... acceptedStatuses);
}
