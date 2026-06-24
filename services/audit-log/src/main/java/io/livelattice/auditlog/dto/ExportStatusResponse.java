package io.livelattice.auditlog.dto;

public record ExportStatusResponse(
    String jobId,
    String status,
    String format,
    String downloadUrl,
    String error
) {}
