package io.livelattice.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SearchRequest(
    @NotBlank(message = "Query parameter q is required")
    String q,
    @Pattern(regexp = "^(canvas|comment|document|dashboard|template|user)?$", message = "Invalid type")
    String type,
    String workspaceId,
    String tags,
    String from,
    String to,
    @Min(value = 1, message = "Page must be at least 1")
    int page,
    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size must be at most 100")
    int size,
    String searchAfter
) {
}
