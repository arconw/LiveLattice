package io.livelattice.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateApiKeyRequest(
    @NotBlank String workspaceId,
    @NotBlank @Size(max = 120) String name,
    @NotEmpty List<@NotBlank String> permissions,
    Instant expiresAt
) {}
