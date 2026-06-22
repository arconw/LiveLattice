package io.livelattice.core.repository;

import io.livelattice.core.model.entity.CanvasTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanvasTemplateRepository extends JpaRepository<CanvasTemplate, UUID> {
    List<CanvasTemplate> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<CanvasTemplate> findByWorkspaceIdIsNullOrderByCreatedAtDesc();

    List<CanvasTemplate> findByWorkspaceIdAndCategoryOrderByCreatedAtDesc(UUID workspaceId, String category);

    List<CanvasTemplate> findByWorkspaceIdIsNullAndCategoryOrderByCreatedAtDesc(String category);

    Optional<CanvasTemplate> findById(UUID id);
}
