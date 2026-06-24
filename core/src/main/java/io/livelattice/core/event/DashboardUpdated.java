package io.livelattice.core.event;

import java.util.UUID;

public record DashboardUpdated(UUID dashboardId, UUID workspaceId, UUID userId) {}
