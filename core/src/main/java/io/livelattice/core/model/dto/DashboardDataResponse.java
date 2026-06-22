package io.livelattice.core.model.dto;

import java.util.Map;

public record DashboardDataResponse(
    String dashboardId,
    java.util.List<WidgetData> widgets
) {
    public record WidgetData(
        String widgetId,
        Map<String, Object> data,
        String error
    ) {}
}
