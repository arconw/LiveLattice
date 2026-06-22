package io.livelattice.core.event;

import java.util.UUID;

public record CanvasCreated(UUID canvasId, UUID workspaceId, UUID userId) {}
