package io.livelattice.notifications.template;

import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationType;
import java.util.Set;

public record NotificationTemplate(
    NotificationType type,
    String title,
    String body,
    String actionUrl,
    String emailTemplate,
    Set<NotificationChannel> defaultChannels
) {
}
