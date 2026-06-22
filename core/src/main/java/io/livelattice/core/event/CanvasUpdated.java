package io.livelattice.core.event;

import java.util.UUID;

public record CanvasUpdated(UUID canvasId, UUID workspaceId, UUID userId, long version) {}
