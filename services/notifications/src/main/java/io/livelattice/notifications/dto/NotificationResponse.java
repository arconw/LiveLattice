package io.livelattice.notifications.dto;

import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationStatus;
import io.livelattice.notifications.model.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID workspaceId,
    UUID recipientId,
    NotificationType type,
    String title,
    String body,
    String actionUrl,
    Map<String, Object> data,
    NotificationChannel channel,
    NotificationStatus status,
    Instant readAt,
    Instant createdAt,
    Instant updatedAt
) {
}
