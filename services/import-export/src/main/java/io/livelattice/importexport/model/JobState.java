package io.livelattice.importexport.model;

import java.time.Instant;
import java.util.UUID;

public record JobState(
    UUID jobId,
    String type,
    UUID workspaceId,
    String userSubject,
    JobStatus status,
    int progress,
    String result,
    String error,
    Instant createdAt,
    Instant updatedAt
) {
}
