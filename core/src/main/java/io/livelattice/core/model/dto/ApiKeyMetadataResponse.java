package io.livelattice.core.model.dto;

import io.livelattice.core.model.entity.ApiKey;
import java.time.Instant;
import java.util.List;

public record ApiKeyMetadataResponse(
    String id,
    String workspaceId,
    String name,
    List<String> permissions,
    String status,
    Instant lastUsedAt,
    Instant expiresAt,
    Instant createdAt,
    Instant revokedAt
) {
    public static ApiKeyMetadataResponse from(ApiKey apiKey) {
        return new ApiKeyMetadataResponse(
            apiKey.getId().toString(),
            apiKey.getWorkspaceId().toString(),
            apiKey.getName(),
            apiKey.permissionList(),
            apiKey.getStatus(),
            apiKey.getLastUsedAt(),
            apiKey.getExpiresAt(),
            apiKey.getCreatedAt(),
            apiKey.getRevokedAt()
        );
    }
}
