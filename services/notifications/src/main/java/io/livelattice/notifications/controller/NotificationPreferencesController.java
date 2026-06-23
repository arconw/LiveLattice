package io.livelattice.notifications.controller;

import io.livelattice.notifications.dto.NotificationPreferencesResponse;
import io.livelattice.notifications.dto.UpdatePreferencesRequest;
import io.livelattice.notifications.dto.WebhookEndpointRequest;
import io.livelattice.notifications.dto.WebhookEndpointResponse;
import io.livelattice.notifications.service.NotificationPreferencesService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationPreferencesController {

    private final NotificationPreferencesService preferencesService;

    public NotificationPreferencesController(NotificationPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping("/notification-preferences")
    public NotificationPreferencesResponse get(@RequestHeader("x-user-id") UUID userId) {
        return preferencesService.get(userId);
    }

    @PatchMapping("/notification-preferences")
    public NotificationPreferencesResponse update(@RequestHeader("x-user-id") UUID userId,
                                                  @Valid @RequestBody UpdatePreferencesRequest request) {
        return preferencesService.update(userId, request);
    }

    @PostMapping("/notification-preferences/webhooks")
    public WebhookEndpointResponse addWebhook(@RequestHeader("x-user-id") UUID userId,
                                              @Valid @RequestBody WebhookEndpointRequest request) {
        return preferencesService.addWebhook(userId, request);
    }

    @DeleteMapping("/notification-preferences/webhooks/{id}")
    public void removeWebhook(@RequestHeader("x-user-id") UUID userId, @PathVariable UUID id) {
        preferencesService.removeWebhook(userId, id);
    }
}
