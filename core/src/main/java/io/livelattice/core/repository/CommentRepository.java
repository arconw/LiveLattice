package io.livelattice.core.repository;

import io.livelattice.core.model.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByCanvasIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID canvasId, Pageable pageable);

    List<Comment> findByCanvasIdAndCreatedAtLessThanAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        UUID canvasId, Instant createdAt, Pageable pageable
    );

    List<Comment> findByCanvasIdAndParentIdIsNullAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID canvasId, Pageable pageable);

    List<Comment> findByCanvasIdAndParentIdIsNullAndCreatedAtLessThanAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        UUID canvasId, Instant createdAt, Pageable pageable
    );

    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);
}
