package io.livelattice.core.model.entity;

import io.livelattice.core.model.enums.DashboardWidgetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "widgets")
public class Widget {

    @Id
    private UUID id;

    @Column(name = "dashboard_id", nullable = false)
    private UUID dashboardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DashboardWidgetType type;

    @Column(nullable = false)
    private String title;

    @Column(name = "data_source_id")
    private UUID dataSourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> query;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> options;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Widget() {
    }

    public Widget(UUID dashboardId, DashboardWidgetType type, String title, UUID dataSourceId,
                  Map<String, Object> query, Map<String, Object> options, Map<String, Object> position) {
        this.id = UUID.randomUUID();
        this.dashboardId = dashboardId;
        this.type = type;
        this.title = title;
        this.dataSourceId = dataSourceId;
        this.query = query;
        this.options = options;
        this.position = position;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(UUID dashboardId) {
        this.dashboardId = dashboardId;
    }

    public DashboardWidgetType getType() {
        return type;
    }

    public void setType(DashboardWidgetType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(UUID dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public Map<String, Object> getQuery() {
        return query;
    }

    public void setQuery(Map<String, Object> query) {
        this.query = query;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public Map<String, Object> getPosition() {
        return position;
    }

    public void setPosition(Map<String, Object> position) {
        this.position = position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
