package io.livelattice.search.dto;

public record ErrorResponse(
    String error,
    String message
) {
}
