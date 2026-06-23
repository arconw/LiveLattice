package io.livelattice.search.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.search.exception.SearchBackendException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class OpenSearchClientGateway implements OpenSearchGateway {

    private final OpenSearchClient client;
    private final ObjectMapper objectMapper;

    public OpenSearchClientGateway(OpenSearchClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public OpenSearchResponse request(String method, String endpoint, JsonNode body, int... acceptedStatuses) {
        try (Response response = execute(method, endpoint, body)) {
            JsonNode responseBody = response.getBody()
                .map(Body::bodyAsString)
                .filter(value -> !value.isBlank())
                .map(this::readBody)
                .orElseGet(objectMapper::createObjectNode);
            if (!accepted(response.getStatus(), acceptedStatuses)) {
                throw new SearchBackendException("OpenSearch request failed with status " + response.getStatus());
            }
            return new OpenSearchResponse(response.getStatus(), responseBody);
        } catch (SearchBackendException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SearchBackendException("OpenSearch request failed", ex);
        }
    }

    private Response execute(String method, String endpoint, JsonNode body) throws Exception {
        var builder = Requests.builder()
            .endpoint(endpoint)
            .method(method);
        if (body != null && !body.isMissingNode()) {
            builder.json(objectMapper.writeValueAsString(body));
        }
        return client.generic().execute(builder.build());
    }

    private boolean accepted(int status, int... acceptedStatuses) {
        if (acceptedStatuses == null || acceptedStatuses.length == 0) {
            return status >= 200 && status < 300;
        }
        return Arrays.stream(acceptedStatuses).anyMatch(acceptedStatus -> acceptedStatus == status);
    }

    private JsonNode readBody(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new SearchBackendException("Failed to parse OpenSearch response", ex);
        }
    }
}
