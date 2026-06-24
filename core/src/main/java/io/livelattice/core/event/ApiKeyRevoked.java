package io.livelattice.core.event;

import java.util.UUID;

public record ApiKeyRevoked(UUID keyId, UUID workspaceId, UUID userId) {}
