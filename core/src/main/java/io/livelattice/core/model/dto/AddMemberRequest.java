package io.livelattice.core.model.dto;

import io.livelattice.core.model.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
    @NotBlank String userId,
    @NotNull Role role
) {}
