package io.livelattice.core.event;

import java.util.UUID;

public record CommentDeleted(UUID commentId, UUID canvasId, UUID workspaceId, UUID userId) {}
