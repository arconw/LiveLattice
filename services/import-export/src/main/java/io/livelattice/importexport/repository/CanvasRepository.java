package io.livelattice.importexport.repository;

import io.livelattice.importexport.model.CanvasEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanvasRepository extends JpaRepository<CanvasEntity, UUID> {
    List<CanvasEntity> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
}
