package io.livelattice.core.event;

import java.util.UUID;

public record CanvasDeleted(UUID canvasId, UUID workspaceId, UUID userId) {}
