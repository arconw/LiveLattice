package io.livelattice.search.dto;

public record IndexStatus(
    String index,
    boolean exists,
    long docsCount,
    long sizeBytes,
    String health
) {
}
