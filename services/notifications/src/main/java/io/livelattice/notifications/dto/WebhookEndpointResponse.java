package io.livelattice.notifications.dto;

import io.livelattice.notifications.model.NotificationType;
import java.util.Set;
import java.util.UUID;

public record WebhookEndpointResponse(
    UUID id,
    String url,
    Set<NotificationType> events
) {
}
