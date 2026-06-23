package io.livelattice.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.livelattice.notifications.config.NotificationsProperties;
import io.livelattice.notifications.delivery.EmailDeliveryService;
import io.livelattice.notifications.delivery.InAppNotificationPublisher;
import io.livelattice.notifications.delivery.WebhookDeliveryService;
import io.livelattice.notifications.dto.CreateNotificationRequest;
import io.livelattice.notifications.dto.NotificationResponse;
import io.livelattice.notifications.kafka.NotificationEventProducer;
import io.livelattice.notifications.kafka.RecipientResolver;
import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationEntity;
import io.livelattice.notifications.model.NotificationPreferencesEntity;
import io.livelattice.notifications.model.NotificationStatus;
import io.livelattice.notifications.model.NotificationType;
import io.livelattice.notifications.repository.NotificationRepository;
import io.livelattice.notifications.template.RenderedNotification;
import io.livelattice.notifications.template.TemplateCatalog;
import io.livelattice.notifications.template.TemplateRenderer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferencesService preferencesService;
    @Mock
    private TemplateRenderer templateRenderer;
    @Mock
    private InAppNotificationPublisher inAppNotificationPublisher;
    @Mock
    private EmailDeliveryService emailDeliveryService;
    @Mock
    private WebhookDeliveryService webhookDeliveryService;
    @Mock
    private RedisDeduplicationService deduplicationService;
    @Mock
    private RedisRateLimitService rateLimitService;
    @Mock
    private NotificationEventProducer eventProducer;

    private NotificationService service;
    private final TemplateCatalog templateCatalog = new TemplateCatalog();
    private final RecipientResolver recipientResolver = new RecipientResolver();
    private final NotificationMapper mapper = new NotificationMapper();
    private final NotificationsProperties properties = new NotificationsProperties();

    @BeforeEach
    void setup() {
        service = new NotificationService(
            notificationRepository,
            preferencesService,
            templateCatalog,
            templateRenderer,
            inAppNotificationPublisher,
            emailDeliveryService,
            webhookDeliveryService,
            deduplicationService,
            rateLimitService,
            eventProducer,
            recipientResolver,
            mapper,
            properties
        );
    }

    @Test
    void createsInAppNotification() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(preferencesService.load(userId)).thenReturn(new NotificationPreferencesEntity(userId));
        stubSuccessfulInAppRendering();

        List<NotificationResponse> responses = service.create(new CreateNotificationRequest(
            workspaceId,
            List.of(userId),
            NotificationType.CANVAS_COMMENT,
            Set.of(NotificationChannel.IN_APP),
            null,
            null,
            null,
            Map.of("canvasName", "Roadmap"),
            "comment-1"
        ));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).recipientId()).isEqualTo(userId);
        assertThat(responses.get(0).type()).isEqualTo(NotificationType.CANVAS_COMMENT);
        assertThat(responses.get(0).status()).isEqualTo(NotificationStatus.SENT);
        verify(inAppNotificationPublisher).publish(any(NotificationEntity.class));
        verify(eventProducer).publishCreated(any(NotificationEntity.class));
    }

    @Test
    void skipsMutedNotificationType() {
        UUID userId = UUID.randomUUID();
        NotificationPreferencesEntity preferences = new NotificationPreferencesEntity(userId);
        preferences.setMutedTypes(Set.of(NotificationType.CANVAS_COMMENT.value()));
        when(preferencesService.load(userId)).thenReturn(preferences);

        List<NotificationResponse> responses = service.create(new CreateNotificationRequest(
            UUID.randomUUID(),
            List.of(userId),
            NotificationType.CANVAS_COMMENT,
            Set.of(NotificationChannel.IN_APP),
            null,
            null,
            null,
            Map.of("canvasName", "Roadmap"),
            "comment-1"
        ));

        assertThat(responses).isEmpty();
        verify(notificationRepository, never()).save(any(NotificationEntity.class));
        verifyNoInteractions(inAppNotificationPublisher, emailDeliveryService, webhookDeliveryService, eventProducer);
    }

    private void stubSuccessfulInAppRendering() {
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rateLimitService.allow(anyString(), anyInt(), any())).thenReturn(true);
        when(deduplicationService.claim(anyString(), any())).thenReturn(true);
        when(templateRenderer.render(any(), nullable(String.class), nullable(String.class), nullable(String.class), anyMap()))
            .thenReturn(new RenderedNotification("New comment", "A teammate commented", "/canvas/1", Map.of("recipientEmail", "user@example.com")));
    }
}
