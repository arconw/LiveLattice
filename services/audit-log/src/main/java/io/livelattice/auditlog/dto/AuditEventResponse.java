package io.livelattice.auditlog.dto;

import java.time.Instant;

public record AuditEventResponse(
    String id,
    String workspaceId,
    String actorId,
    String action,
    String targetType,
    String targetId,
    Object changes,
    Object metadata,
    String previousHash,
    String hash,
    Instant occurredAt
) {}
