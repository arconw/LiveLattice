package io.livelattice.importexport.dto;

import java.util.UUID;

public record ImportResponse(
    UUID canvasId,
    UUID jobId,
    String status,
    String message
) {
}
