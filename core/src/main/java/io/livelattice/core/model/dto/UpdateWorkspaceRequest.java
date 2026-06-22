package io.livelattice.core.model.dto;

public record UpdateWorkspaceRequest(
    String name,
    String slug,
    String tier
) {}
