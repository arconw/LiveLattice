package io.livelattice.core.event;

import java.util.UUID;

public record TierChanged(UUID workspaceId, UUID userId, String tier) {}
