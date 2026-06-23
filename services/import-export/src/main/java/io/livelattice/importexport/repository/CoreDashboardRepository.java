package io.livelattice.importexport.repository;

import io.livelattice.importexport.model.CoreDashboardEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreDashboardRepository extends JpaRepository<CoreDashboardEntity, UUID> {
    Optional<CoreDashboardEntity> findByIdAndDeletedAtIsNull(UUID id);
}
