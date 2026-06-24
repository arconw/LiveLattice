package io.livelattice.core.event;

import java.util.UUID;

public record DataSourceUpdated(UUID dataSourceId, UUID workspaceId, UUID userId) {}
