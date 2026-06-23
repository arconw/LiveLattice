package io.livelattice.search.dto;

import java.util.List;

public record SuggestResponse(
    List<String> suggestions
) {
}
