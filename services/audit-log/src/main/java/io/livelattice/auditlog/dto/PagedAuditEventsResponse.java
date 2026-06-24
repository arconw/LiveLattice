package io.livelattice.auditlog.dto;

import java.util.List;

public record PagedAuditEventsResponse(
    List<AuditEventResponse> events,
    long totalElements,
    int totalPages,
    int page,
    int size
) {}
