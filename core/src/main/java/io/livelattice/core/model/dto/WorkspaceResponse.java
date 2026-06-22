package io.livelattice.core.model.dto;

import io.livelattice.core.model.entity.Workspace;
import java.time.Instant;

public record WorkspaceResponse(
    String id,
    String name,
    String slug,
    String tier,
    String ownerId,
    Instant createdAt,
    Instant updatedAt
) {
    public static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(
            w.getId().toString(), w.getName(), w.getSlug(),
            w.getTier().name(), w.getOwnerId().toString(),
            w.getCreatedAt(), w.getUpdatedAt()
        );
    }
}
