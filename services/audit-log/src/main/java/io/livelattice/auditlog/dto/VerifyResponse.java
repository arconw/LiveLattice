package io.livelattice.auditlog.dto;

import java.time.Instant;

public record VerifyResponse(
    boolean valid,
    String firstInvalidId,
    String firstInvalidHash,
    long checkedCount
) {}
