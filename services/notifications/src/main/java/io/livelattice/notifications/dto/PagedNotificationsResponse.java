package io.livelattice.notifications.dto;

import java.util.List;

public record PagedNotificationsResponse(
    List<NotificationResponse> notifications,
    int page,
    int size,
    long total
) {
}
