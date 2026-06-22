package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateTemplateRequest(
    @NotBlank String name,
    String category,
    String thumbnail,
    Map<String, Object> content,
    String canvasId
) {}
