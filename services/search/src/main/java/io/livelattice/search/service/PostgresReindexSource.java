package io.livelattice.search.service;

import io.livelattice.search.kafka.IndexEvent;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PostgresReindexSource {

    private final JdbcTemplate jdbcTemplate;

    public PostgresReindexSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IndexEvent> events() {
        List<IndexEvent> events = new ArrayList<>();
        events.addAll(canvases());
        events.addAll(comments());
        events.addAll(dashboards());
        events.addAll(templates());
        events.addAll(users());
        return events;
    }

    private List<IndexEvent> canvases() {
        return jdbcTemplate.query("""
            SELECT id::text, workspace_id::text, title, content::text, created_by::text, created_at, updated_at
            FROM canvases
            WHERE deleted_at IS NULL
            """, (rs, rowNum) -> new IndexEvent(
            "canvas.reindexed",
            "canvas",
            rs.getString("id"),
            rs.getString("workspace_id"),
            rs.getString("title"),
            rs.getString("content"),
            List.of(),
            rs.getString("created_by"),
            null,
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            false,
            false,
            Map.of()
        ));
    }

    private List<IndexEvent> comments() {
        return jdbcTemplate.query("""
            SELECT c.id::text, c.canvas_id::text, ca.workspace_id::text, c.content, c.author_id::text, c.created_at, c.updated_at, c.resolved
            FROM comments c
            JOIN canvases ca ON ca.id = c.canvas_id
            WHERE c.deleted_at IS NULL AND ca.deleted_at IS NULL
            """, (rs, rowNum) -> new IndexEvent(
            "comment.reindexed",
            "comment",
            rs.getString("id"),
            rs.getString("workspace_id"),
            null,
            rs.getString("content"),
            List.of(),
            rs.getString("author_id"),
            rs.getString("canvas_id"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            false,
            rs.getBoolean("resolved"),
            Map.of()
        ));
    }

    private List<IndexEvent> dashboards() {
        return jdbcTemplate.query("""
            SELECT id::text, workspace_id::text, title, COALESCE(description, '') || ' ' || layout::text || ' ' || time_range::text AS content_text,
                   created_by::text, created_at, updated_at
            FROM dashboards
            WHERE deleted_at IS NULL
            """, (rs, rowNum) -> new IndexEvent(
            "dashboard.reindexed",
            "dashboard",
            rs.getString("id"),
            rs.getString("workspace_id"),
            rs.getString("title"),
            rs.getString("content_text"),
            List.of(),
            rs.getString("created_by"),
            null,
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            false,
            false,
            Map.of()
        ));
    }

    private List<IndexEvent> templates() {
        return jdbcTemplate.query("""
            SELECT id::text, workspace_id::text, name, category, content::text, created_by::text, created_at
            FROM canvas_templates
            """, (rs, rowNum) -> new IndexEvent(
            "template.reindexed",
            "template",
            rs.getString("id"),
            rs.getString("workspace_id"),
            rs.getString("name"),
            rs.getString("content"),
            category(rs.getString("category")),
            rs.getString("created_by"),
            null,
            instant(rs, "created_at"),
            instant(rs, "created_at"),
            false,
            false,
            Map.of()
        ));
    }

    private List<IndexEvent> users() {
        if (!columnExists("users", "display_name") || !columnExists("users", "external_subject") || !columnExists("users", "status")) {
            return List.of();
        }
        return jdbcTemplate.query("""
            SELECT id::text, email, display_name, status, created_at, updated_at
            FROM users
            WHERE status = 'ACTIVE'
            """, (rs, rowNum) -> new IndexEvent(
            "user.reindexed",
            "user",
            rs.getString("id"),
            null,
            rs.getString("display_name"),
            rs.getString("email"),
            List.of(),
            null,
            null,
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            false,
            false,
            Map.of("name", rs.getString("display_name"), "email", rs.getString("email"))
        ));
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
            """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private List<String> category(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value);
    }

    private Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
