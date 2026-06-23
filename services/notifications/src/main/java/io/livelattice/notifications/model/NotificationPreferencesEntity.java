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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreferencesEntity {

    @Id
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EmailDigest emailDigest = EmailDigest.INSTANT;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Set<String> mutedTypes = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<WebhookEndpoint> webhooks = new ArrayList<>();
    @Column(nullable = false)
    private Instant updatedAt;

    public NotificationPreferencesEntity() {
    }

    public NotificationPreferencesEntity(UUID userId) {
        this.userId = userId;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public boolean isMuted(NotificationType type) {
        return mutedTypes != null && mutedTypes.contains(type.value());
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public EmailDigest getEmailDigest() {
        return emailDigest;
    }

    public void setEmailDigest(EmailDigest emailDigest) {
        this.emailDigest = emailDigest == null ? EmailDigest.INSTANT : emailDigest;
    }

    public Set<String> getMutedTypes() {
        return mutedTypes;
    }

    public void setMutedTypes(Set<String> mutedTypes) {
        this.mutedTypes = mutedTypes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(mutedTypes);
    }

    public List<WebhookEndpoint> getWebhooks() {
        return webhooks;
    }

    public void setWebhooks(List<WebhookEndpoint> webhooks) {
        this.webhooks = webhooks == null ? new ArrayList<>() : new ArrayList<>(webhooks);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
