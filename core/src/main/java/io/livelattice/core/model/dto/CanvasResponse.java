package io.livelattice.core.model.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CanvasResponse(
    String id,
    String workspaceId,
    String title,
    Map<String, Object> content,
    long version,
    int lockVersion,
    Long snapshotVersion,
    int operationCountSinceSnapshot,
    String createdBy,
    String updatedBy,
    Instant createdAt,
    Instant updatedAt
) {
    public static CanvasResponse from(io.livelattice.core.model.entity.Canvas canvas) {
        return new CanvasResponse(
            canvas.getId().toString(),
            canvas.getWorkspaceId().toString(),
            canvas.getTitle(),
            canvas.getContent(),
            canvas.getVersion(),
            canvas.getLockVersion(),
            canvas.getSnapshotVersion(),
            canvas.getOperationCountSinceSnapshot(),
            canvas.getCreatedBy().toString(),
            canvas.getUpdatedBy().toString(),
            canvas.getCreatedAt(),
            canvas.getUpdatedAt()
        );
    }
}
