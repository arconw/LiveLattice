package io.livelattice.notifications.repository;

import io.livelattice.notifications.model.DeliveryAttemptEntity;
import io.livelattice.notifications.model.DeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {
    List<DeliveryAttemptEntity> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(DeliveryStatus status, Instant nextAttemptAt);
}
