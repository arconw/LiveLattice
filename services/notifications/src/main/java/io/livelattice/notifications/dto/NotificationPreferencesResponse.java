package io.livelattice.notifications.dto;

import io.livelattice.notifications.model.EmailDigest;
import io.livelattice.notifications.model.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record NotificationPreferencesResponse(
    UUID userId,
    EmailDigest emailDigest,
    Set<NotificationType> mutedTypes,
    List<WebhookEndpointResponse> webhooks,
    Instant updatedAt
) {
}
