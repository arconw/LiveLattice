package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDashboardRequest(
    @NotBlank String workspaceId,
    @NotBlank String title,
    String description,
    @NotNull java.util.Map<String, Object> layout,
    java.util.Map<String, Object> timeRange,
    Integer autoRefresh
) {}
