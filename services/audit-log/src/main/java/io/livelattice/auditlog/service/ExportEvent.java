package io.livelattice.auditlog.service;

import java.time.Instant;

record ExportEvent(
    String id,
    String workspaceId,
    String actorId,
    String action,
    String targetType,
    String targetId,
    String changes,
    String metadata,
    String previousHash,
    String hash,
    Instant occurredAt
) {
}
