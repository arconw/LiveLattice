package io.livelattice.importexport.event;

import java.time.Instant;

public record DomainAuditEvent(
    String eventType,
    String targetType,
    String id,
    String targetId,
    String workspaceId,
    String actorId,
    Object changes,
    Object metadata,
    Instant occurredAt
) {}
