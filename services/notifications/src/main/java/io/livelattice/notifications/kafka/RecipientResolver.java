package io.livelattice.notifications.kafka;

import io.livelattice.notifications.model.NotificationType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecipientResolver {

    public Set<UUID> resolve(NotificationDomainEvent event) {
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        if (event.recipientIds() != null) {
            recipients.addAll(event.recipientIds());
        }
        if (event.recipientId() != null) {
            recipients.add(event.recipientId());
        }
        Map<String, Object> data = event.data();
        if (data == null) {
            return recipients;
        }
        NotificationType type = event.type();
        if (type == NotificationType.MEMBER_INVITED) {
            addUuid(recipients, data.get("invitedUserId"));
        } else if (type == NotificationType.CANVAS_COMMENT) {
            addUuid(recipients, data.get("canvasAuthorId"));
            addUuidList(recipients, data.get("commentParticipantIds"));
        } else if (type == NotificationType.CANVAS_MENTION) {
            addUuidList(recipients, data.get("mentionedUserIds"));
        } else if (type == NotificationType.CANVAS_EXPORT_COMPLETE) {
            addUuid(recipients, data.get("requestingUserId"));
        } else if (type == NotificationType.WORKSPACE_QUOTA_WARNING) {
            addUuidList(recipients, data.get("workspaceAdminIds"));
        }
        return recipients;
    }

    private void addUuid(Set<UUID> recipients, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof UUID uuid) {
            recipients.add(uuid);
            return;
        }
        String text = value.toString();
        if (!text.isBlank()) {
            recipients.add(UUID.fromString(text));
        }
    }

    private void addUuidList(Set<UUID> recipients, Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addUuid(recipients, item);
            }
        } else if (value instanceof String text && !text.isBlank()) {
            List.of(text.split(",")).forEach(item -> addUuid(recipients, item.trim()));
        }
    }
}
