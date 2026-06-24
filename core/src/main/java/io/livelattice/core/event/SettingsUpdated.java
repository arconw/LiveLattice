package io.livelattice.core.event;

import java.util.UUID;

public record SettingsUpdated(UUID workspaceId, UUID userId) {}
