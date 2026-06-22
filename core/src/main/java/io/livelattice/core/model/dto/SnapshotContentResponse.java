package io.livelattice.core.model.dto;

import java.time.Instant;
import java.util.Map;

public record SnapshotContentResponse(
    String canvasId,
    long version,
    Map<String, Object> content,
    Instant snapshotAt
) {}
