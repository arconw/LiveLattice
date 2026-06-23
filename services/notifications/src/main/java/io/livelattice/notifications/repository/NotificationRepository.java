package io.livelattice.notifications.repository;

import io.livelattice.notifications.model.NotificationChannel;
import io.livelattice.notifications.model.NotificationEntity;
import io.livelattice.notifications.model.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID>, JpaSpecificationExecutor<NotificationEntity> {
    long countByRecipientIdAndChannelAndReadAtIsNull(UUID recipientId, NotificationChannel channel);

    List<NotificationEntity> findByRecipientIdAndChannelAndReadAtIsNull(UUID recipientId, NotificationChannel channel);

    List<NotificationEntity> findTop200ByChannelAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(NotificationChannel channel, NotificationStatus status, Instant createdAt);
}
