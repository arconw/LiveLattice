package io.livelattice.core.service;

import java.util.List;

public record ApiKeyValidation(
    String apiKeyId,
    String workspaceId,
    String userSubject,
    String userEmail,
    String userDisplayName,
    List<String> permissions
) {}
