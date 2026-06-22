package io.livelattice.core.model.dto;

public record UpdateWidgetRequest(
    String type,
    String title,
    String dataSourceId,
    java.util.Map<String, Object> query,
    java.util.Map<String, Object> options,
    java.util.Map<String, Object> position
) {}
