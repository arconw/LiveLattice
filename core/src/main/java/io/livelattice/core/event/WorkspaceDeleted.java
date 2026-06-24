package io.livelattice.core.event;

import java.util.UUID;

public record WorkspaceDeleted(UUID workspaceId, UUID userId) {}
