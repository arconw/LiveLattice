package io.livelattice.notifications.service;

import io.livelattice.notifications.config.NotificationsProperties;
import io.livelattice.notifications.delivery.DeliveryResult;
import io.livelattice.notifications.delivery.EmailDeliveryService;
import io.livelattice.notifications.delivery.InAppNotificationPublisher;
import io.livelattice.notifications.delivery.WebhookDeliveryService;
import io.livelattice.notifications.dto.CreateNotificationRequest;
import io.livelattice.notifications.dto.NotificationResponse;
import io.livelattice.notifications.dto.PagedNotificationsResponse;
import io.livelattice.notifications.dto.UnreadCountResponse;
import io.livelattice.notifications.exception.ForbiddenException;
import io.livelattice.notifications.exception.NotFoundException;
import io.livelattice.notifications.exception.RateLimitExceededException;
import io.livelattice.notifications.exception.ValidationException;
import io.livelattice.notifications.kafka.NotificationDomainEvent;
import io.livelattice.notifications.kafka.NotificationEventProducer;
import io.livelattice.notifications.kafka.RecipientResolver;
import io.livelattice.notifications.model.EmailDigest;
import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationEntity;
import io.livelattice.notifications.model.NotificationPreferencesEntity;
import io.livelattice.notifications.model.NotificationStatus;
import io.livelattice.notifications.model.NotificationType;
import io.livelattice.notifications.model.WebhookEndpoint;
import io.livelattice.notifications.repository.NotificationRepository;
import io.livelattice.notifications.template.NotificationTemplate;
import io.livelattice.notifications.template.RenderedNotification;
import io.livelattice.notifications.template.TemplateCatalog;
import io.livelattice.notifications.template.TemplateRenderer;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesService preferencesService;
    private final TemplateCatalog templateCatalog;
    private final TemplateRenderer templateRenderer;
    private final InAppNotificationPublisher inAppNotificationPublisher;
    private final EmailDeliveryService emailDeliveryService;
    private final WebhookDeliveryService webhookDeliveryService;
    private final RedisDeduplicationService deduplicationService;
    private final RedisRateLimitService rateLimitService;
    private final NotificationEventProducer eventProducer;
    private final RecipientResolver recipientResolver;
    private final NotificationMapper mapper;
    private final NotificationsProperties properties;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferencesService preferencesService,
                               TemplateCatalog templateCatalog,
                               TemplateRenderer templateRenderer,
                               InAppNotificationPublisher inAppNotificationPublisher,
                               EmailDeliveryService emailDeliveryService,
                               WebhookDeliveryService webhookDeliveryService,
                               RedisDeduplicationService deduplicationService,
                               RedisRateLimitService rateLimitService,
                               NotificationEventProducer eventProducer,
                               RecipientResolver recipientResolver,
                               NotificationMapper mapper,
                               NotificationsProperties properties) {
        this.notificationRepository = notificationRepository;
        this.preferencesService = preferencesService;
        this.templateCatalog = templateCatalog;
        this.templateRenderer = templateRenderer;
        this.inAppNotificationPublisher = inAppNotificationPublisher;
        this.emailDeliveryService = emailDeliveryService;
        this.webhookDeliveryService = webhookDeliveryService;
        this.deduplicationService = deduplicationService;
        this.rateLimitService = rateLimitService;
        this.eventProducer = eventProducer;
        this.recipientResolver = recipientResolver;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional
    public List<NotificationResponse> create(CreateNotificationRequest request) {
        NotificationDomainEvent event = new NotificationDomainEvent(
            request.type(),
            request.workspaceId(),
            null,
            request.recipientIds(),
            request.channels(),
            request.title(),
            request.body(),
            request.actionUrl(),
            request.safeData(),
            request.deduplicationKey(),
            Instant.now()
        );
        return handleDomainEvent(event).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional
    public List<NotificationEntity> handleDomainEvent(NotificationDomainEvent event) {
        String topic = event.type() == null ? NotificationType.SYSTEM_ANNOUNCEMENT.value() : event.type().value();
        NotificationDomainEvent resolvedEvent = event.withDefaults(topic);
        NotificationTemplate template = templateCatalog.template(resolvedEvent.type());
        if (template == null) {
            throw new ValidationException("Unsupported notification type");
        }
        Set<UUID> recipients = recipientResolver.resolve(resolvedEvent);
        if (recipients.isEmpty()) {
            throw new ValidationException("At least one recipient is required");
        }
        Set<NotificationChannel> channels = channels(resolvedEvent.channels(), template);
        List<NotificationEntity> created = new ArrayList<>();
        for (UUID recipientId : recipients) {
            NotificationPreferencesEntity preferences = preferencesService.load(recipientId);
            if (preferences.isMuted(resolvedEvent.type())) {
                continue;
            }
            RenderedNotification rendered = templateRenderer.render(template, resolvedEvent.title(), resolvedEvent.body(), resolvedEvent.actionUrl(), resolvedEvent.data());
            for (NotificationChannel channel : channels) {
                created.addAll(createForChannel(resolvedEvent, recipientId, channel, preferences, rendered));
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public PagedNotificationsResponse list(UUID userId,
                                           NotificationType type,
                                           NotificationChannel channel,
                                           NotificationStatus status,
                                           Boolean unread,
                                           Pageable pageable) {
        Page<NotificationEntity> page = notificationRepository.findAll(filter(userId, type, channel, status, unread), pageable);
        return new PagedNotificationsResponse(
            page.getContent().stream().map(mapper::toResponse).toList(),
            pageable.getPageNumber() + 1,
            pageable.getPageSize(),
            page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID userId) {
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndChannelAndReadAtIsNull(userId, NotificationChannel.IN_APP));
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!notification.getRecipientId().equals(userId)) {
            throw new ForbiddenException("Notification belongs to another user");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification.setStatus(NotificationStatus.READ);
        }
        return mapper.toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public UnreadCountResponse markAllRead(UUID userId) {
        List<NotificationEntity> unread = notificationRepository.findByRecipientIdAndChannelAndReadAtIsNull(userId, NotificationChannel.IN_APP);
        Instant now = Instant.now();
        unread.forEach(notification -> {
            notification.setReadAt(now);
            notification.setStatus(NotificationStatus.READ);
        });
        notificationRepository.saveAll(unread);
        return new UnreadCountResponse(0);
    }

    private List<NotificationEntity> createForChannel(NotificationDomainEvent event,
                                                      UUID recipientId,
                                                      NotificationChannel channel,
                                                      NotificationPreferencesEntity preferences,
                                                      RenderedNotification rendered) {
        if (channel == NotificationChannel.EMAIL && preferences.getEmailDigest() == EmailDigest.NEVER) {
            return List.of();
        }
        if (channel == NotificationChannel.WEBHOOK) {
            return createWebhookNotifications(event, recipientId, preferences, rendered);
        }
        if (!allow(event.workspaceId(), recipientId)) {
            throw new RateLimitExceededException("Notification rate limit exceeded");
        }
        if (!claim(event.deduplicationKey(), recipientId, channel, null)) {
            return List.of();
        }
        NotificationEntity notification = newNotification(event, recipientId, channel, rendered, null);
        notification = notificationRepository.save(notification);
        if (channel == NotificationChannel.IN_APP) {
            publishInApp(notification);
        } else if (channel == NotificationChannel.EMAIL) {
            deliverEmail(notification, preferences);
        }
        notification = notificationRepository.save(notification);
        eventProducer.publishCreated(notification);
        return List.of(notification);
    }

    private List<NotificationEntity> createWebhookNotifications(NotificationDomainEvent event,
                                                                UUID recipientId,
                                                                NotificationPreferencesEntity preferences,
                                                                RenderedNotification rendered) {
        List<NotificationEntity> created = new ArrayList<>();
        for (WebhookEndpoint endpoint : preferences.getWebhooks()) {
            if (!endpoint.matches(event.type())) {
                continue;
            }
            if (!allow(event.workspaceId(), recipientId)) {
                throw new RateLimitExceededException("Notification rate limit exceeded");
            }
            if (!claim(event.deduplicationKey(), recipientId, NotificationChannel.WEBHOOK, endpoint.getId())) {
                continue;
            }
            NotificationEntity notification = newNotification(event, recipientId, NotificationChannel.WEBHOOK, rendered, endpoint.getId());
            notification = notificationRepository.save(notification);
            webhookDeliveryService.enqueue(notification, endpoint);
            eventProducer.publishCreated(notification);
            created.add(notification);
        }
        return created;
    }

    private NotificationEntity newNotification(NotificationDomainEvent event,
                                               UUID recipientId,
                                               NotificationChannel channel,
                                               RenderedNotification rendered,
                                               UUID webhookId) {
        Map<String, Object> data = new LinkedHashMap<>(rendered.data());
        if (webhookId != null) {
            data.put("webhookId", webhookId.toString());
        }
        return new NotificationEntity(
            event.workspaceId(),
            recipientId,
            event.type(),
            defaultTitle(rendered.title(), event.type()),
            defaultBody(rendered.body()),
            rendered.actionUrl(),
            data,
            channel,
            NotificationStatus.PENDING
        );
    }

    private void publishInApp(NotificationEntity notification) {
        try {
            inAppNotificationPublisher.publish(notification);
            notification.setStatus(NotificationStatus.SENT);
        } catch (RuntimeException ex) {
            notification.setStatus(NotificationStatus.FAILED);
        }
    }

    private void deliverEmail(NotificationEntity notification, NotificationPreferencesEntity preferences) {
        if (preferences.getEmailDigest() == EmailDigest.HOURLY || preferences.getEmailDigest() == EmailDigest.DAILY) {
            notification.setStatus(NotificationStatus.PENDING);
            return;
        }
        DeliveryResult result = emailDeliveryService.send(notification);
        notification.setStatus(result.successful() ? NotificationStatus.SENT : NotificationStatus.FAILED);
    }

    private boolean allow(UUID workspaceId, UUID recipientId) {
        String key = "notifications:rate:" + (workspaceId == null ? recipientId : workspaceId);
        return rateLimitService.allow(key, properties.getRateLimitMax(), properties.getRateLimitWindow());
    }

    private boolean claim(String deduplicationKey, UUID recipientId, NotificationChannel channel, UUID webhookId) {
        if (deduplicationKey == null || deduplicationKey.isBlank()) {
            return true;
        }
        String key = "notifications:dedupe:" + deduplicationKey + ":" + recipientId + ":" + channel + ":" + (webhookId == null ? "none" : webhookId);
        return deduplicationService.claim(key, properties.getDeduplicationTtl());
    }

    private Set<NotificationChannel> channels(Set<NotificationChannel> requested, NotificationTemplate template) {
        if (requested == null || requested.isEmpty()) {
            return EnumSet.copyOf(template.defaultChannels());
        }
        return new LinkedHashSet<>(requested);
    }

    private Specification<NotificationEntity> filter(UUID userId,
                                                     NotificationType type,
                                                     NotificationChannel channel,
                                                     NotificationStatus status,
                                                     Boolean unread) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("recipientId"), userId));
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type.value()));
            }
            if (channel != null) {
                predicates.add(builder.equal(root.get("channel"), channel));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (Boolean.TRUE.equals(unread)) {
                predicates.add(builder.isNull(root.get("readAt")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String defaultTitle(String title, NotificationType type) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return type.value();
    }

    private String defaultBody(String body) {
        return body == null ? "" : body;
    }
}
