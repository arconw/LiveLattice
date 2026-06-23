package io.livelattice.importexport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportOptions(
    @NotBlank String workspaceId,
    @NotBlank String title,
    String description
) {
}
