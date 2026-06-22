package io.livelattice.core.repository;

import io.livelattice.core.model.entity.Widget;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WidgetRepository extends JpaRepository<Widget, UUID> {
    List<Widget> findByDashboardId(UUID dashboardId);
}
