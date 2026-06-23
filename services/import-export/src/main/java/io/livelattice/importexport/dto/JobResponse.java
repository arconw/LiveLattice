package io.livelattice.importexport.dto;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
    UUID jobId,
    String type,
    String status,
    int progress,
    String result,
    String error,
    Instant createdAt,
    Instant updatedAt
) {
}
