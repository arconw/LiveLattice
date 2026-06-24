package io.livelattice.core.event;

import java.util.UUID;

public record WorkspaceCreated(UUID workspaceId, UUID userId) {}
