package io.livelattice.notifications.repository;

import io.livelattice.notifications.model.NotificationPreferencesEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferencesEntity, UUID> {
}
