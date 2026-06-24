package io.livelattice.core.event;

import java.util.UUID;

public record CanvasRestored(UUID canvasId, UUID workspaceId, UUID userId, long restoredSnapshotVersion, long version) {}
