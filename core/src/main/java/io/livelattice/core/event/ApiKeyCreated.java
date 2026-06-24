package io.livelattice.core.event;

import java.util.UUID;

public record ApiKeyCreated(UUID keyId, UUID workspaceId, UUID userId) {}
