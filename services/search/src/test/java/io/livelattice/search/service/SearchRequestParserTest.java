package io.livelattice.search.service;

import io.livelattice.search.exception.ValidationException;
import io.livelattice.search.model.SearchCriteria;
import io.livelattice.search.model.SearchType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRequestParserTest {

    private final SearchRequestParser parser = new SearchRequestParser();

    @Test
    void parsesTypesTagsAndDates() {
        SearchCriteria criteria = parser.parse(
            " diagram ",
            "canvas,document",
            "workspace-1",
            "blue, important,blue",
            "2026-06-01",
            "2026-06-30T12:30:00Z",
            1,
            20,
            null
        );

        assertThat(criteria.query()).isEqualTo("diagram");
        assertThat(criteria.types()).containsExactly(SearchType.CANVAS, SearchType.DOCUMENT);
        assertThat(criteria.tags()).containsExactly("blue", "important");
        assertThat(criteria.from()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(criteria.to()).isEqualTo(Instant.parse("2026-06-30T12:30:00Z"));
    }

    @Test
    void rejectsUnknownType() {
        assertThatThrownBy(() -> parser.parse("q", "unknown", null, null, null, null, 1, 20, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid type");
    }

    @Test
    void rejectsInvalidDateRange() {
        assertThatThrownBy(() -> parser.parse("q", null, null, null, "2026-06-30", "2026-06-01", 1, 20, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("from must be before to");
    }
}
