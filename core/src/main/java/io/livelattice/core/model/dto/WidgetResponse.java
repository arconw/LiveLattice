package io.livelattice.core.model.dto;

import io.livelattice.core.model.entity.Widget;
import java.time.Instant;

public record WidgetResponse(
    String id,
    String dashboardId,
    String type,
    String title,
    String dataSourceId,
    java.util.Map<String, Object> query,
    java.util.Map<String, Object> options,
    java.util.Map<String, Object> position,
    Instant createdAt,
    Instant updatedAt
) {
    public static WidgetResponse from(Widget w) {
        return new WidgetResponse(
            w.getId().toString(),
            w.getDashboardId().toString(),
            w.getType().name(),
            w.getTitle(),
            w.getDataSourceId() != null ? w.getDataSourceId().toString() : null,
            w.getQuery(),
            w.getOptions(),
            w.getPosition(),
            w.getCreatedAt(),
            w.getUpdatedAt()
        );
    }
}
