package io.livelattice.auditlog.dto;

public record ErrorResponse(
    String error,
    String message
) {}
