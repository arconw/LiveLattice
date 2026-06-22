package io.livelattice.core.model.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    String id,
    String canvasId,
    String parentId,
    String authorId,
    String content,
    boolean resolved,
    String resolvedBy,
    Instant resolvedAt,
    String targetElementId,
    Instant createdAt,
    Instant updatedAt
) {
    public static CommentResponse from(io.livelattice.core.model.entity.Comment comment) {
        return new CommentResponse(
            comment.getId().toString(),
            comment.getCanvasId().toString(),
            comment.getParentId() != null ? comment.getParentId().toString() : null,
            comment.getAuthorId().toString(),
            comment.getContent(),
            comment.isResolved(),
            comment.getResolvedBy() != null ? comment.getResolvedBy().toString() : null,
            comment.getResolvedAt(),
            comment.getTargetElementId(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
