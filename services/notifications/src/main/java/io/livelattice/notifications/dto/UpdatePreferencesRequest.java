package io.livelattice.notifications.dto;

import io.livelattice.notifications.model.EmailDigest;
import io.livelattice.notifications.model.NotificationType;
import java.util.Set;

public record UpdatePreferencesRequest(
    EmailDigest emailDigest,
    Set<NotificationType> mutedTypes
) {
}
