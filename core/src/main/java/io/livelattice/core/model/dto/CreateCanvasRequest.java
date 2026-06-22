package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCanvasRequest(
    @NotBlank String workspaceId,
    @NotBlank String title,
    String templateId
) {}
