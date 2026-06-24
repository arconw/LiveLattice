package io.livelattice.core.event;

import java.util.UUID;

public record DashboardCreated(UUID dashboardId, UUID workspaceId, UUID userId) {}
