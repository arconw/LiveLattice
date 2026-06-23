package io.livelattice.notifications.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.notifications.config.NotificationsProperties;
import io.livelattice.notifications.model.DeliveryAttemptEntity;
import io.livelattice.notifications.model.DeliveryStatus;
import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationEntity;
import io.livelattice.notifications.model.NotificationPreferencesEntity;
import io.livelattice.notifications.model.NotificationStatus;
import io.livelattice.notifications.model.NotificationType;
import io.livelattice.notifications.model.WebhookEndpoint;
import io.livelattice.notifications.repository.DeliveryAttemptRepository;
import io.livelattice.notifications.repository.NotificationPreferencesRepository;
import io.livelattice.notifications.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferencesRepository preferencesRepository;
    @Mock
    private RestTemplate restTemplate;

    private WebhookDeliveryService service;

    @BeforeEach
    void setup() {
        service = new WebhookDeliveryService(
            deliveryAttemptRepository,
            notificationRepository,
            preferencesRepository,
            restTemplate,
            new ObjectMapper(),
            new NotificationsProperties()
        );
    }

    @Test
    void deadLettersClientErrorsWithoutRetrying() {
        UUID recipientId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(UUID.randomUUID(), "webhook-target", "secret", Set.of());
        NotificationPreferencesEntity preferences = new NotificationPreferencesEntity(recipientId);
        preferences.setWebhooks(List.of(endpoint));
        NotificationEntity notification = new NotificationEntity(
            UUID.randomUUID(),
            recipientId,
            NotificationType.CANVAS_COMMENT,
            "New comment",
            "A teammate commented",
            "/canvas/1",
            Map.of(),
            NotificationChannel.WEBHOOK,
            NotificationStatus.PENDING
        );
        DeliveryAttemptEntity attempt = new DeliveryAttemptEntity(notification.getId(), NotificationChannel.WEBHOOK, endpoint.getUrl());

        when(deliveryAttemptRepository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(eq(DeliveryStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of(attempt));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(preferencesRepository.findById(recipientId)).thenReturn(Optional.of(preferences));
        when(restTemplate.postForEntity(eq(endpoint.getUrl()), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad request"));

        service.processDueAttempts();

        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.DEAD_LETTER);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getNextAttemptAt()).isNull();
        assertThat(attempt.getLastError()).isEqualTo("Webhook returned 400");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(deliveryAttemptRepository).save(attempt);
        verify(notificationRepository).save(notification);
    }

    @Test
    void marksNotificationFailedWhenRetriesAreExhausted() {
        UUID recipientId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(UUID.randomUUID(), "webhook-target", "secret", Set.of());
        NotificationPreferencesEntity preferences = new NotificationPreferencesEntity(recipientId);
        preferences.setWebhooks(List.of(endpoint));
        NotificationEntity notification = new NotificationEntity(
            UUID.randomUUID(),
            recipientId,
            NotificationType.CANVAS_COMMENT,
            "New comment",
            "A teammate commented",
            "/canvas/1",
            Map.of(),
            NotificationChannel.WEBHOOK,
            NotificationStatus.PENDING
        );
        DeliveryAttemptEntity attempt = new DeliveryAttemptEntity(notification.getId(), NotificationChannel.WEBHOOK, endpoint.getUrl());
        attempt.setAttemptNumber(5);

        when(deliveryAttemptRepository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(eq(DeliveryStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of(attempt));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(preferencesRepository.findById(recipientId)).thenReturn(Optional.of(preferences));
        when(restTemplate.postForEntity(eq(endpoint.getUrl()), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error"));

        service.processDueAttempts();

        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.DEAD_LETTER);
        assertThat(attempt.getAttemptNumber()).isEqualTo(5);
        assertThat(attempt.getNextAttemptAt()).isNull();
        assertThat(attempt.getLastError()).isEqualTo("Webhook returned 500");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(deliveryAttemptRepository).save(attempt);
        verify(notificationRepository).save(notification);
    }
}
