package io.livelattice.notifications.controller;

import io.livelattice.notifications.dto.CreateNotificationRequest;
import io.livelattice.notifications.dto.NotificationResponse;
import io.livelattice.notifications.dto.PagedNotificationsResponse;
import io.livelattice.notifications.dto.UnreadCountResponse;
import io.livelattice.notifications.exception.ForbiddenException;
import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationStatus;
import io.livelattice.notifications.model.NotificationType;
import io.livelattice.notifications.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class NotificationController {

    private static final String NOTIFICATION_SERVICE_ROLE = "service";
    private static final String NOTIFICATION_ADMIN_ROLE = "admin";

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/notifications")
    public List<NotificationResponse> create(@Valid @RequestBody CreateNotificationRequest request,
                                             @RequestHeader("x-auth-roles") String rolesHeader) {
        requireServiceOrAdminRole(rolesHeader);
        return notificationService.create(request);
    }

    @GetMapping("/notifications")
    public PagedNotificationsResponse list(@RequestHeader("x-user-id") UUID userId,
                                           @RequestParam(required = false) NotificationType type,
                                           @RequestParam(required = false) NotificationChannel channel,
                                           @RequestParam(required = false) NotificationStatus status,
                                           @RequestParam(required = false) Boolean unread,
                                           @RequestParam(defaultValue = "1") @Min(1) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationService.list(userId, type, channel, status, unread, pageable);
    }

    @PatchMapping("/notifications/{id}/read")
    public NotificationResponse markRead(@RequestHeader("x-user-id") UUID userId, @PathVariable UUID id) {
        return notificationService.markRead(userId, id);
    }

    @PostMapping("/notifications/read-all")
    public UnreadCountResponse markAllRead(@RequestHeader("x-user-id") UUID userId) {
        return notificationService.markAllRead(userId);
    }

    @GetMapping("/notifications/unread-count")
    public UnreadCountResponse unreadCount(@RequestHeader("x-user-id") UUID userId) {
        return notificationService.unreadCount(userId);
    }

    private void requireServiceOrAdminRole(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            throw new ForbiddenException("Notification creation requires service or admin role");
        }
        for (String role : rolesHeader.split(",")) {
            String normalized = role.trim().toLowerCase();
            if (normalized.equals(NOTIFICATION_SERVICE_ROLE) || normalized.equals(NOTIFICATION_ADMIN_ROLE)) {
                return;
            }
        }
        throw new ForbiddenException("Notification creation requires service or admin role");
    }
}
