package io.livelattice.notifications.template;

import java.util.Map;

public record RenderedNotification(
    String title,
    String body,
    String actionUrl,
    Map<String, Object> data
) {
}
