package io.livelattice.notifications.dto;

public record ErrorResponse(
    String error,
    String message
) {
}
