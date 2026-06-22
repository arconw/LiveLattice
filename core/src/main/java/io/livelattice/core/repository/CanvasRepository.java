package io.livelattice.core.repository;

import io.livelattice.core.model.entity.Canvas;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CanvasRepository extends JpaRepository<Canvas, UUID> {
    @Query(value = "SELECT * FROM canvases WHERE workspace_id = :workspaceId AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Canvas> findByWorkspaceIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
        @Param("workspaceId") UUID workspaceId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    Optional<Canvas> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT c FROM Canvas c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Canvas> findActiveById(@Param("id") UUID id);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
}
