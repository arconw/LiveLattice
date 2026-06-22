package io.livelattice.core.model.dto;

public record UpdateCanvasRequest(
    String title,
    java.util.Map<String, Object> content,
    Long expectedVersion,
    Integer expectedLockVersion
) {}
