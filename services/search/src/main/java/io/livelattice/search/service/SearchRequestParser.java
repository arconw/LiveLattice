package io.livelattice.search.service;

import io.livelattice.search.exception.ValidationException;
import io.livelattice.search.model.SearchCriteria;
import io.livelattice.search.model.SearchType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Component
public class SearchRequestParser {

    public SearchCriteria parse(String query,
                                String type,
                                String workspaceId,
                                String tags,
                                String from,
                                String to,
                                int page,
                                int size,
                                String searchAfter) {
        List<SearchType> types = parseTypes(type);
        Instant fromInstant = parseInstant(from, false);
        Instant toInstant = parseInstant(to, true);
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new ValidationException("from must be before to");
        }
        return new SearchCriteria(
            query.trim(),
            types,
            blankToNull(workspaceId),
            parseList(tags),
            fromInstant,
            toInstant,
            page,
            size,
            blankToNull(searchAfter)
        );
    }

    public List<SearchType> parseTypes(String type) {
        if (type == null || type.isBlank()) {
            return List.of();
        }
        return Arrays.stream(type.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> SearchType.fromValue(value)
                .orElseThrow(() -> new ValidationException("Invalid type: " + value)))
            .distinct()
            .toList();
    }

    public List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .distinct()
            .toList();
    }

    private Instant parseInstant(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(value);
                return endOfDay
                    ? date.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusNanos(1).toInstant()
                    : date.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ex) {
                throw new ValidationException("Invalid date: " + value);
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
