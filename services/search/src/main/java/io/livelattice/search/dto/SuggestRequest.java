package io.livelattice.search.dto;

import jakarta.validation.constraints.NotBlank;

public record SuggestRequest(
    @NotBlank(message = "Query parameter q is required")
    String q,
    String workspaceId
) {
}
