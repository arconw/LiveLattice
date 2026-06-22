package io.livelattice.core.repository;

import io.livelattice.core.model.entity.Dashboard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardRepository extends JpaRepository<Dashboard, UUID> {
    @Query(value = "SELECT * FROM dashboards WHERE workspace_id = :workspaceId AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Dashboard> findByWorkspaceIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
        @Param("workspaceId") UUID workspaceId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    Optional<Dashboard> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
}
