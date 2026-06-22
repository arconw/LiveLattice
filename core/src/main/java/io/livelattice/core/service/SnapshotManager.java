package io.livelattice.core.service;

import io.livelattice.core.exception.ConflictException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.exception.OptimisticLockException;
import io.livelattice.core.model.dto.SnapshotContentResponse;
import io.livelattice.core.model.dto.SnapshotResponse;
import io.livelattice.core.model.entity.Canvas;
import io.livelattice.core.model.entity.CanvasSnapshot;
import io.livelattice.core.repository.CanvasRepository;
import io.livelattice.core.repository.CanvasSnapshotRepository;
import io.livelattice.core.service.snapshot.SnapshotContentStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SnapshotManager {

    private static final int RETENTION_COUNT = 100;

    private final CanvasRepository canvasRepository;
    private final CanvasSnapshotRepository snapshotRepository;
    private final SnapshotContentStore contentStore;

    public SnapshotManager(CanvasRepository canvasRepository,
                           CanvasSnapshotRepository snapshotRepository,
                           SnapshotContentStore contentStore) {
        this.canvasRepository = canvasRepository;
        this.snapshotRepository = snapshotRepository;
        this.contentStore = contentStore;
    }

    public SnapshotResponse createSnapshot(UUID canvasId, UUID userId, long version, Map<String, Object> content) {
        Canvas canvas = canvasRepository.findByIdAndDeletedAtIsNull(canvasId)
            .orElseThrow(() -> new NotFoundException("Canvas not found: " + canvasId));

        Optional<CanvasSnapshot> existing = snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvas.getId(), version);
        if (existing.isPresent()) {
            return SnapshotResponse.from(existing.get());
        }

        String minioPath = contentStore.put(canvas.getWorkspaceId(), canvas.getId(), version, content);

        CanvasSnapshot snapshot = new CanvasSnapshot(
            canvas.getId(),
            version,
            content,
            minioPath,
            userId
        );
        try {
            snapshot = snapshotRepository.save(snapshot);
        } catch (DataIntegrityViolationException ex) {
            return snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvas.getId(), version)
                .map(SnapshotResponse::from)
                .orElseThrow(() -> new ConflictException("Failed to create snapshot for version " + version));
        }

        try {
            canvas.setSnapshotVersion(version);
            canvas.setOperationCountSinceSnapshot(0);
            canvasRepository.save(canvas);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockException("Canvas snapshot state was modified concurrently");
        }

        applyRetention(canvas.getId());

        return SnapshotResponse.from(snapshot);
    }

    private void applyRetention(UUID canvasId) {
        List<CanvasSnapshot> recent = snapshotRepository.findByCanvasIdAndDeletedAtIsNullOrderByVersionDesc(canvasId, Pageable.ofSize(RETENTION_COUNT));
        if (recent.size() < RETENTION_COUNT) {
            return;
        }
        long cutoff = recent.get(recent.size() - 1).getVersion();
        List<CanvasSnapshot> older = snapshotRepository.findByCanvasIdAndDeletedAtIsNullOrderByVersionDesc(canvasId, Pageable.unpaged()).stream()
            .filter(s -> s.getVersion() < cutoff)
            .toList();
        for (CanvasSnapshot snapshot : older) {
            snapshot.setContent(null);
            snapshotRepository.save(snapshot);
        }
    }

    @Transactional(readOnly = true)
    public List<SnapshotResponse> listSnapshots(UUID canvasId) {
        if (!canvasRepository.existsById(canvasId)) {
            throw new NotFoundException("Canvas not found: " + canvasId);
        }
        return snapshotRepository.findByCanvasIdAndDeletedAtIsNullOrderByVersionDesc(canvasId, Pageable.unpaged()).stream()
            .map(SnapshotResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public SnapshotContentResponse getSnapshotContent(UUID canvasId, long version) {
        Canvas canvas = canvasRepository.findByIdAndDeletedAtIsNull(canvasId)
            .orElseThrow(() -> new NotFoundException("Canvas not found: " + canvasId));
        CanvasSnapshot snapshot = snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvas.getId(), version)
            .orElseThrow(() -> new NotFoundException("Snapshot not found: version " + version));
        Map<String, Object> content = snapshotContent(snapshot);
        return new SnapshotContentResponse(canvasId.toString(), version, content, snapshot.getSnapshotAt());
    }

    private Map<String, Object> snapshotContent(CanvasSnapshot snapshot) {
        if (snapshot.getContent() != null) {
            return snapshot.getContent();
        }
        if (snapshot.getMinioPath() == null || snapshot.getMinioPath().isBlank()) {
            throw new NotFoundException("Snapshot content is not available: " + snapshot.getVersion());
        }
        return contentStore.get(snapshot.getMinioPath());
    }

    public Canvas restoreSnapshot(UUID canvasId, long version, UUID userId) {
        Canvas canvas = canvasRepository.findByIdAndDeletedAtIsNull(canvasId)
            .orElseThrow(() -> new NotFoundException("Canvas not found: " + canvasId));
        CanvasSnapshot snapshot = snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvas.getId(), version)
            .orElseThrow(() -> new NotFoundException("Snapshot not found: version " + version));

        Map<String, Object> content = snapshotContent(snapshot);
        if (content.isEmpty()) {
            throw new ConflictException("Snapshot content is not available for restore: " + version);
        }

        canvas.setContent(content);
        canvas.setVersion(canvas.getVersion() + 1);
        canvas.setOperationCountSinceSnapshot(canvas.getOperationCountSinceSnapshot() + 1);
        canvas.setUpdatedBy(userId);
        canvas.setUpdatedAt(Instant.now());
        try {
            return canvasRepository.save(canvas);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockException("Canvas was modified concurrently");
        }
    }
}
