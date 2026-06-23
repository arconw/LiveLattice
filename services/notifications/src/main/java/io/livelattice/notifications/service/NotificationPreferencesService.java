package io.livelattice.notifications.service;

import io.livelattice.notifications.config.NotificationsProperties;
import io.livelattice.notifications.dto.NotificationPreferencesResponse;
import io.livelattice.notifications.dto.UpdatePreferencesRequest;
import io.livelattice.notifications.dto.WebhookEndpointRequest;
import io.livelattice.notifications.dto.WebhookEndpointResponse;
import io.livelattice.notifications.exception.NotFoundException;
import io.livelattice.notifications.exception.ValidationException;
import io.livelattice.notifications.model.EmailDigest;
import io.livelattice.notifications.model.NotificationPreferencesEntity;
import io.livelattice.notifications.model.NotificationType;
import io.livelattice.notifications.model.WebhookEndpoint;
import io.livelattice.notifications.repository.NotificationPreferencesRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository preferencesRepository;
    private final NotificationsProperties properties;

    public NotificationPreferencesService(NotificationPreferencesRepository preferencesRepository, NotificationsProperties properties) {
        this.preferencesRepository = preferencesRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse get(UUID userId) {
        return toResponse(load(userId));
    }

    @Transactional
    public NotificationPreferencesResponse update(UUID userId, UpdatePreferencesRequest request) {
        NotificationPreferencesEntity preferences = load(userId);
        if (request.emailDigest() != null) {
            preferences.setEmailDigest(request.emailDigest());
        }
        if (request.mutedTypes() != null) {
            preferences.setMutedTypes(toExternalTypes(request.mutedTypes()));
        }
        return toResponse(preferencesRepository.save(preferences));
    }

    @Transactional
    public WebhookEndpointResponse addWebhook(UUID userId, WebhookEndpointRequest request) {
        NotificationPreferencesEntity preferences = load(userId);
        if (preferences.getWebhooks().size() >= properties.getWebhookMaxPerUser()) {
            throw new ValidationException("Webhook limit exceeded");
        }
        WebhookEndpoint endpoint = new WebhookEndpoint(
            UUID.randomUUID(),
            request.url(),
            UUID.randomUUID() + "-" + UUID.randomUUID(),
            toExternalTypes(request.events())
        );
        preferences.getWebhooks().add(endpoint);
        preferencesRepository.save(preferences);
        return toResponse(endpoint);
    }

    @Transactional
    public void removeWebhook(UUID userId, UUID webhookId) {
        NotificationPreferencesEntity preferences = load(userId);
        boolean removed = preferences.getWebhooks().removeIf(endpoint -> endpoint.getId().equals(webhookId));
        if (!removed) {
            throw new NotFoundException("Webhook not found");
        }
        preferencesRepository.save(preferences);
    }

    public NotificationPreferencesEntity load(UUID userId) {
        return preferencesRepository.findById(userId)
            .orElseGet(() -> new NotificationPreferencesEntity(userId));
    }

    private NotificationPreferencesResponse toResponse(NotificationPreferencesEntity preferences) {
        return new NotificationPreferencesResponse(
            preferences.getUserId(),
            preferences.getEmailDigest() == null ? EmailDigest.INSTANT : preferences.getEmailDigest(),
            fromExternalTypes(preferences.getMutedTypes()),
            preferences.getWebhooks().stream().map(this::toResponse).toList(),
            preferences.getUpdatedAt()
        );
    }

    private WebhookEndpointResponse toResponse(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(endpoint.getId(), endpoint.getUrl(), fromExternalTypes(endpoint.getEvents()));
    }

    private Set<String> toExternalTypes(Set<NotificationType> types) {
        if (types == null) {
            return new LinkedHashSet<>();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        types.forEach(type -> values.add(type.value()));
        return values;
    }

    private Set<NotificationType> fromExternalTypes(Set<String> values) {
        if (values == null) {
            return new LinkedHashSet<>();
        }
        LinkedHashSet<NotificationType> types = new LinkedHashSet<>();
        values.forEach(value -> types.add(NotificationType.fromValue(value)));
        return types;
    }
}
