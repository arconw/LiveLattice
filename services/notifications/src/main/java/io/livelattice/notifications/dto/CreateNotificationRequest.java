package io.livelattice.notifications.dto;

import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CreateNotificationRequest(
    UUID workspaceId,
    @NotEmpty List<@NotNull UUID> recipientIds,
    @NotNull NotificationType type,
    @NotEmpty Set<@NotNull NotificationChannel> channels,
    @Size(max = 255) String title,
    String body,
    String actionUrl,
    Map<String, Object> data,
    String deduplicationKey
) {
    public Map<String, Object> safeData() {
        return data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }
}
