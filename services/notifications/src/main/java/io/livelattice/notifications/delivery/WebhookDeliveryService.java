package io.livelattice.notifications.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.notifications.config.NotificationsProperties;
import io.livelattice.notifications.model.DeliveryAttemptEntity;
import io.livelattice.notifications.model.DeliveryStatus;
import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationEntity;
import io.livelattice.notifications.model.NotificationPreferencesEntity;
import io.livelattice.notifications.model.NotificationStatus;
import io.livelattice.notifications.model.WebhookEndpoint;
import io.livelattice.notifications.repository.DeliveryAttemptRepository;
import io.livelattice.notifications.repository.NotificationPreferencesRepository;
import io.livelattice.notifications.repository.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final List<Duration> BACKOFF = List.of(
        Duration.ofMinutes(1),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Duration.ofHours(1)
    );

    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationsProperties properties;

    public WebhookDeliveryService(DeliveryAttemptRepository deliveryAttemptRepository,
                                  NotificationRepository notificationRepository,
                                  NotificationPreferencesRepository preferencesRepository,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper,
                                  NotificationsProperties properties) {
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.notificationRepository = notificationRepository;
        this.preferencesRepository = preferencesRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void enqueue(NotificationEntity notification, WebhookEndpoint endpoint) {
        DeliveryAttemptEntity attempt = new DeliveryAttemptEntity(notification.getId(), NotificationChannel.WEBHOOK, endpoint.getUrl());
        deliveryAttemptRepository.save(attempt);
    }

    @Scheduled(fixedDelayString = "${livelattice.notifications.webhook-scan-interval:30s}")
    @Transactional
    public void processDueAttempts() {
        List<DeliveryAttemptEntity> attempts = deliveryAttemptRepository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(DeliveryStatus.PENDING, Instant.now());
        for (DeliveryAttemptEntity attempt : attempts) {
            processAttempt(attempt);
        }
    }

    private void processAttempt(DeliveryAttemptEntity attempt) {
        notificationRepository.findById(attempt.getNotificationId()).ifPresentOrElse(
            notification -> deliver(attempt, notification),
            () -> markFailed(attempt, "Notification not found", true)
        );
    }

    private void deliver(DeliveryAttemptEntity attempt, NotificationEntity notification) {
        WebhookEndpoint endpoint = findEndpoint(notification, attempt.getTargetUrl());
        if (endpoint == null) {
            markFailed(attempt, "Webhook endpoint not found", true);
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(payload(notification));
            HttpHeaders headers = new HttpHeaders();
            headers.add("content-type", "application/json");
            headers.add("x-livelattice-event", notification.getType());
            headers.add("x-livelattice-signature", signature(endpoint.getSecret(), body));
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint.getUrl(), new HttpEntity<>(body, headers), String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                attempt.setStatus(DeliveryStatus.DELIVERED);
                attempt.setLastError(null);
                notification.setStatus(NotificationStatus.DELIVERED);
                deliveryAttemptRepository.save(attempt);
                notificationRepository.save(notification);
            } else if (response.getStatusCode().is4xxClientError()) {
                markFailed(attempt, "Webhook returned " + response.getStatusCode().value(), true);
                notification.setStatus(NotificationStatus.FAILED);
                notificationRepository.save(notification);
            } else {
                if (retry(attempt, "Webhook returned " + response.getStatusCode().value())) {
                    notification.setStatus(NotificationStatus.FAILED);
                    notificationRepository.save(notification);
                }
            }
        } catch (JsonProcessingException ex) {
            markFailed(attempt, "Webhook payload serialization failed", true);
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);
        } catch (HttpClientErrorException ex) {
            markFailed(attempt, "Webhook returned " + ex.getStatusCode().value(), true);
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);
        } catch (RestClientException ex) {
            if (retry(attempt, ex.getMessage())) {
                notification.setStatus(NotificationStatus.FAILED);
                notificationRepository.save(notification);
            }
        }
    }

    private WebhookEndpoint findEndpoint(NotificationEntity notification, String targetUrl) {
        return preferencesRepository.findById(notification.getRecipientId())
            .map(NotificationPreferencesEntity::getWebhooks)
            .orElse(List.of())
            .stream()
            .filter(endpoint -> endpoint.getUrl().equals(targetUrl))
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> payload(NotificationEntity notification) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", notification.getId());
        payload.put("workspaceId", notification.getWorkspaceId());
        payload.put("recipientId", notification.getRecipientId());
        payload.put("type", notification.getType());
        payload.put("title", notification.getTitle());
        payload.put("body", notification.getBody());
        payload.put("actionUrl", notification.getActionUrl());
        payload.put("data", notification.getData());
        payload.put("createdAt", notification.getCreatedAt());
        return payload;
    }

    private String signature(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign webhook payload", ex);
        }
    }

    private boolean retry(DeliveryAttemptEntity attempt, String error) {
        if (attempt.getAttemptNumber() >= properties.getWebhookMaxAttempts()) {
            markFailed(attempt, error, true);
            return true;
        }
        int nextAttempt = attempt.getAttemptNumber() + 1;
        Duration delay = BACKOFF.get(Math.min(nextAttempt - 2, BACKOFF.size() - 1));
        attempt.setAttemptNumber(nextAttempt);
        attempt.setNextAttemptAt(Instant.now().plus(delay));
        attempt.setLastError(error);
        deliveryAttemptRepository.save(attempt);
        return false;
    }

    private void markFailed(DeliveryAttemptEntity attempt, String error, boolean deadLetter) {
        attempt.setStatus(deadLetter ? DeliveryStatus.DEAD_LETTER : DeliveryStatus.FAILED);
        attempt.setLastError(error);
        attempt.setNextAttemptAt(null);
        deliveryAttemptRepository.save(attempt);
        if (deadLetter) {
            log.warn("Webhook delivery dead-lettered for notification {}", attempt.getNotificationId());
        }
    }
}
