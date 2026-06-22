package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDataSourceRequest(
    String name,
    java.util.Map<String, Object> config
) {}
