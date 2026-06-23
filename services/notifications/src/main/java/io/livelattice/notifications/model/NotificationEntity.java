package io.livelattice.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;
    private UUID workspaceId;
    @Column(nullable = false)
    private UUID recipientId;
    @Column(nullable = false, length = 64)
    private String type;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(columnDefinition = "text")
    private String actionUrl;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> data = new LinkedHashMap<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;
    private Instant readAt;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public NotificationEntity() {
    }

    public NotificationEntity(UUID workspaceId,
                              UUID recipientId,
                              NotificationType type,
                              String title,
                              String body,
                              String actionUrl,
                              Map<String, Object> data,
                              NotificationChannel channel,
                              NotificationStatus status) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.recipientId = recipientId;
        this.type = type.value();
        this.title = title;
        this.body = body;
        this.actionUrl = actionUrl;
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        this.channel = channel;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(UUID recipientId) {
        this.recipientId = recipientId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
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
