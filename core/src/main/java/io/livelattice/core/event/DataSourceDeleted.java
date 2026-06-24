package io.livelattice.core.event;

import java.util.UUID;

public record DataSourceDeleted(UUID dataSourceId, UUID workspaceId, UUID userId) {}
