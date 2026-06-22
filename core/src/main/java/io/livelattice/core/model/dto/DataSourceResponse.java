package io.livelattice.core.model.dto;

import io.livelattice.core.model.entity.DataSource;
import java.time.Instant;

public record DataSourceResponse(
    String id,
    String workspaceId,
    String name,
    String type,
    String createdBy,
    Instant createdAt,
    Instant updatedAt
) {
    public static DataSourceResponse from(DataSource ds) {
        return new DataSourceResponse(
            ds.getId().toString(),
            ds.getWorkspaceId().toString(),
            ds.getName(),
            ds.getType().name(),
            ds.getCreatedBy().toString(),
            ds.getCreatedAt(),
            ds.getUpdatedAt()
        );
    }
}
