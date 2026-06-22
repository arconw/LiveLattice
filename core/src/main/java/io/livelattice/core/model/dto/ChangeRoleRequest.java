package io.livelattice.core.model.dto;

import io.livelattice.core.model.enums.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
    @NotNull Role role
) {}
