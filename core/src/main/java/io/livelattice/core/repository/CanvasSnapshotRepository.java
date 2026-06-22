package io.livelattice.core.repository;

import io.livelattice.core.model.entity.CanvasSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanvasSnapshotRepository extends JpaRepository<CanvasSnapshot, UUID> {
    List<CanvasSnapshot> findByCanvasIdAndDeletedAtIsNullOrderByVersionDesc(UUID canvasId, Pageable pageable);

    Optional<CanvasSnapshot> findByCanvasIdAndVersionAndDeletedAtIsNull(UUID canvasId, long version);

    long countByCanvasIdAndDeletedAtIsNull(UUID canvasId);
}
