package io.livelattice.notifications.service;

import io.livelattice.notifications.delivery.DeliveryResult;
import io.livelattice.notifications.delivery.EmailDeliveryService;
import io.livelattice.notifications.model.EmailDigest;
import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationEntity;
import io.livelattice.notifications.model.NotificationStatus;
import io.livelattice.notifications.repository.NotificationPreferencesRepository;
import io.livelattice.notifications.repository.NotificationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigestManager {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final EmailDeliveryService emailDeliveryService;

    public DigestManager(NotificationRepository notificationRepository,
                         NotificationPreferencesRepository preferencesRepository,
                         EmailDeliveryService emailDeliveryService) {
        this.notificationRepository = notificationRepository;
        this.preferencesRepository = preferencesRepository;
        this.emailDeliveryService = emailDeliveryService;
    }

    @Scheduled(fixedDelayString = "${livelattice.notifications.digest-scan-interval:5m}")
    @Transactional
    public void processDueDigests() {
        processDigest(EmailDigest.HOURLY, Instant.now().minus(1, ChronoUnit.HOURS));
        processDigest(EmailDigest.DAILY, Instant.now().minus(24, ChronoUnit.HOURS));
    }

    private void processDigest(EmailDigest digest, Instant cutoff) {
        List<NotificationEntity> pending = notificationRepository.findTop200ByChannelAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(NotificationChannel.EMAIL, NotificationStatus.PENDING, cutoff)
            .stream()
            .filter(notification -> preferencesRepository.findById(notification.getRecipientId())
                .map(preferences -> preferences.getEmailDigest() == digest)
                .orElse(false))
            .toList();
        Map<UUID, List<NotificationEntity>> byRecipient = pending.stream()
            .collect(Collectors.groupingBy(NotificationEntity::getRecipientId));
        byRecipient.forEach((recipientId, notifications) -> {
            DeliveryResult result = emailDeliveryService.sendDigest(recipientId, notifications);
            NotificationStatus status = result.successful() ? NotificationStatus.SENT : NotificationStatus.FAILED;
            notifications.forEach(notification -> notification.setStatus(status));
            notificationRepository.saveAll(notifications);
        });
    }
}
