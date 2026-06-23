package io.livelattice.importexport.repository;

import io.livelattice.importexport.model.CoreWidgetEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreWidgetRepository extends JpaRepository<CoreWidgetEntity, UUID> {
    List<CoreWidgetEntity> findByDashboardIdOrderByCreatedAtAscIdAsc(UUID dashboardId);
}
