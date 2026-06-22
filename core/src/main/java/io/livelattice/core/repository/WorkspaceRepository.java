package io.livelattice.core.repository;

import io.livelattice.core.model.entity.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    boolean existsBySlugAndDeletedAtIsNull(String slug);

    Optional<Workspace> findBySlugAndDeletedAtIsNull(String slug);

    @Query("SELECT w FROM Workspace w JOIN WorkspaceMember m ON w.id = m.workspaceId WHERE m.userId = :userId AND w.deletedAt IS NULL")
    List<Workspace> findByUserId(@Param("userId") UUID userId);
}
