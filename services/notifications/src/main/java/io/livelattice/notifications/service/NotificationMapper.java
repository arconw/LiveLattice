package io.livelattice.notifications.service;

import io.livelattice.notifications.dto.NotificationResponse;
import io.livelattice.notifications.model.NotificationEntity;
import io.livelattice.notifications.model.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(NotificationEntity entity) {
        return new NotificationResponse(
            entity.getId(),
            entity.getWorkspaceId(),
            entity.getRecipientId(),
            NotificationType.fromValue(entity.getType()),
            entity.getTitle(),
            entity.getBody(),
            entity.getActionUrl(),
            entity.getData(),
            entity.getChannel(),
            entity.getStatus(),
            entity.getReadAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
