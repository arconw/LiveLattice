package io.livelattice.core.model.dto;

public record UpdateDashboardRequest(
    String title,
    String description,
    java.util.Map<String, Object> layout,
    java.util.Map<String, Object> timeRange,
    Integer autoRefresh,
    Boolean isPublic
) {}
