package io.livelattice.search.model;

import java.util.Arrays;
import java.util.Optional;

public enum SearchType {
    CANVAS("canvas"),
    COMMENT("comment"),
    DOCUMENT("document"),
    DASHBOARD("dashboard"),
    TEMPLATE("template"),
    USER("user");

    private final String value;

    SearchType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<SearchType> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase();
        return Arrays.stream(values())
            .filter(type -> type.value.equals(normalized))
            .findFirst();
    }
}
