package io.livelattice.core.model.dto;

public record UpdateCommentRequest(
    String content,
    Boolean resolved
) {}
