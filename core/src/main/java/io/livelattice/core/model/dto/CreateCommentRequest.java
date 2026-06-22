package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(
    @NotBlank String content,
    String parentId,
    String targetElementId
) {}
