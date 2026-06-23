package io.livelattice.importexport.repository;

import io.livelattice.importexport.model.CoreCanvasEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoreCanvasRepository extends JpaRepository<CoreCanvasEntity, UUID> {
    Optional<CoreCanvasEntity> findByIdAndDeletedAtIsNull(UUID id);
}
