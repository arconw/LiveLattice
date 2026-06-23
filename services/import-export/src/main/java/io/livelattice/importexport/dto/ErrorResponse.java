package io.livelattice.importexport.dto;

public record ErrorResponse(
    String error,
    String message
) {
}
