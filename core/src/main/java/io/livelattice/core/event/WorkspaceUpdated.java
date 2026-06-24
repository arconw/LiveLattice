package io.livelattice.core.event;

import java.util.UUID;

public record WorkspaceUpdated(UUID workspaceId, UUID userId) {}
