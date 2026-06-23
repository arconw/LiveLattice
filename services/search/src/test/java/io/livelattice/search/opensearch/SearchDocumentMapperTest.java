package io.livelattice.search.opensearch;

import io.livelattice.search.kafka.IndexEvent;
import io.livelattice.search.model.SearchType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchDocumentMapperTest {

    private final SearchDocumentMapper mapper = new SearchDocumentMapper();

    @Test
    void mapsCanvasEventToSearchDocument() {
        IndexEvent event = new IndexEvent(
            "canvas.updated",
            "canvas",
            "canvas-1",
            "workspace-1",
            "Architecture Diagram",
            "Nodes and edges",
            List.of("architecture", "diagram"),
            "user-1",
            null,
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-02T10:00:00Z"),
            false,
            false,
            Map.of("source", "canvas-service")
        );

        Map<String, Object> document = mapper.toDocument(event, SearchType.CANVAS);

        assertThat(document)
            .containsEntry("id", "canvas-1")
            .containsEntry("type", "canvas")
            .containsEntry("workspace_id", "workspace-1")
            .containsEntry("title", "Architecture Diagram")
            .containsEntry("content_text", "Nodes and edges")
            .containsEntry("suggest_title", "Architecture Diagram")
            .containsEntry("is_deleted", false);
        assertThat(document.get("tags")).isEqualTo(List.of("architecture", "diagram"));
    }

    @Test
    void derivesEntityTypeAndDeleteStateFromTopic() {
        IndexEvent event = new IndexEvent(
            null,
            null,
            "comment-1",
            "workspace-1",
            null,
            null,
            null,
            "user-1",
            "canvas-1",
            null,
            null,
            false,
            false,
            null
        ).withDefaults("comment.deleted");

        assertThat(mapper.resolveType(event)).contains(SearchType.COMMENT);
        assertThat(event.isDeletedEvent()).isTrue();
    }

    @Test
    void mapsUserMetadataToSuggestionFields() {
        IndexEvent event = new IndexEvent(
            "user.updated",
            "user",
            "user-1",
            "workspace-1",
            null,
            "Product lead",
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            Map.of("name", "Ada Lovelace", "email", "ada@example.com")
        );

        Map<String, Object> document = mapper.toDocument(event, SearchType.USER);

        assertThat(document)
            .containsEntry("name", "Ada Lovelace")
            .containsEntry("email", "ada@example.com")
            .containsEntry("suggest_title", "Ada Lovelace")
            .containsEntry("content_text", "Product lead");
    }
}
