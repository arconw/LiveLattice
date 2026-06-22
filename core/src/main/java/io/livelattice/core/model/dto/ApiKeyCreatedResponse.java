package io.livelattice.core.model.dto;

import io.livelattice.core.model.entity.ApiKey;
import java.time.Instant;
import java.util.List;

public record ApiKeyCreatedResponse(
    String id,
    String workspaceId,
    String name,
    List<String> permissions,
    String status,
    Instant expiresAt,
    Instant createdAt,
    String token
) {
    public static ApiKeyCreatedResponse from(ApiKey apiKey, String token) {
        return new ApiKeyCreatedResponse(
            apiKey.getId().toString(),
            apiKey.getWorkspaceId().toString(),
            apiKey.getName(),
            apiKey.permissionList(),
            apiKey.getStatus(),
            apiKey.getExpiresAt(),
            apiKey.getCreatedAt(),
            token
        );
    }
}
