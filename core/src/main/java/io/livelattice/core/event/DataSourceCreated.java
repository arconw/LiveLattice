package io.livelattice.core.event;

import java.util.UUID;

public record DataSourceCreated(UUID dataSourceId, UUID workspaceId, UUID userId) {}
