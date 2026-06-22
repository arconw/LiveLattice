package io.livelattice.core.model.dto;

import io.livelattice.core.model.entity.Dashboard;
import java.time.Instant;

public record DashboardResponse(
    String id,
    String workspaceId,
    String title,
    String description,
    java.util.Map<String, Object> layout,
    java.util.Map<String, Object> timeRange,
    int autoRefresh,
    boolean isPublic,
    String createdBy,
    Instant createdAt,
    Instant updatedAt
) {
    public static DashboardResponse from(Dashboard d) {
        return new DashboardResponse(
            d.getId().toString(),
            d.getWorkspaceId().toString(),
            d.getTitle(),
            d.getDescription(),
            d.getLayout(),
            d.getTimeRange(),
            d.getAutoRefresh(),
            d.isPublic(),
            d.getCreatedBy().toString(),
            d.getCreatedAt(),
            d.getUpdatedAt()
        );
    }
}
