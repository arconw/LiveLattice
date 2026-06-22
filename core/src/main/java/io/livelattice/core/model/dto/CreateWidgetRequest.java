package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateWidgetRequest(
    @NotBlank String type,
    @NotBlank String title,
    String dataSourceId,
    @NotNull java.util.Map<String, Object> query,
    java.util.Map<String, Object> options,
    @NotNull java.util.Map<String, Object> position
) {}
