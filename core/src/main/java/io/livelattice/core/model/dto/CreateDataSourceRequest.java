package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDataSourceRequest(
    @NotBlank String workspaceId,
    @NotBlank String name,
    @NotBlank String type,
    @NotNull java.util.Map<String, Object> config
) {}
