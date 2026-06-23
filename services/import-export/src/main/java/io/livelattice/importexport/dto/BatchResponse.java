package io.livelattice.importexport.dto;

import java.util.List;
import java.util.UUID;

public record BatchResponse(
    UUID jobId,
    String status,
    List<UUID> items,
    String message
) {
}
