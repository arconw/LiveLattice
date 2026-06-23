package io.livelattice.notifications.kafka;

import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record NotificationDomainEvent(
    NotificationType type,
    UUID workspaceId,
    UUID recipientId,
    List<UUID> recipientIds,
    Set<NotificationChannel> channels,
    String title,
    String body,
    String actionUrl,
    Map<String, Object> data,
    String deduplicationKey,
    Instant occurredAt
) {
    public NotificationDomainEvent withDefaults(String topic) {
        NotificationType resolvedType = type == null ? NotificationType.fromValue(topic) : type;
        Map<String, Object> resolvedData = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        Instant resolvedOccurredAt = occurredAt == null ? Instant.now() : occurredAt;
        String resolvedDeduplicationKey = deduplicationKey == null || deduplicationKey.isBlank()
            ? topic + ":" + resolvedOccurredAt
            : deduplicationKey;
        return new NotificationDomainEvent(
            resolvedType,
            workspaceId,
            recipientId,
            recipientIds,
            channels,
            title,
            body,
            actionUrl,
            resolvedData,
            resolvedDeduplicationKey,
            resolvedOccurredAt
        );
    }
}
