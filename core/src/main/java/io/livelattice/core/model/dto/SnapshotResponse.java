package io.livelattice.core.model.dto;

import java.time.Instant;
import java.util.UUID;

public record SnapshotResponse(
    String id,
    String canvasId,
    long version,
    String minioPath,
    String createdBy,
    Instant snapshotAt
) {
    public static SnapshotResponse from(io.livelattice.core.model.entity.CanvasSnapshot snapshot) {
        return new SnapshotResponse(
            snapshot.getId().toString(),
            snapshot.getCanvasId().toString(),
            snapshot.getVersion(),
            snapshot.getMinioPath(),
            snapshot.getCreatedBy().toString(),
            snapshot.getSnapshotAt()
        );
    }
}
