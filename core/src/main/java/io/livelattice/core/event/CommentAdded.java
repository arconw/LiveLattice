package io.livelattice.core.event;

import java.util.UUID;

public record CommentAdded(UUID commentId, UUID canvasId, UUID authorId) {}
