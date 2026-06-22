package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(
    @NotBlank String name,
    @NotBlank String slug
) {}
