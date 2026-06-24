package io.livelattice.core.event;

import java.util.UUID;

public record DashboardDeleted(UUID dashboardId, UUID workspaceId, UUID userId) {}
