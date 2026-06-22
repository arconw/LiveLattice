package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.livelattice.core.exception.ConflictException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.model.dto.SnapshotContentResponse;
import io.livelattice.core.model.dto.SnapshotResponse;
import io.livelattice.core.model.entity.Canvas;
import io.livelattice.core.model.entity.CanvasSnapshot;
import io.livelattice.core.repository.CanvasRepository;
import io.livelattice.core.repository.CanvasSnapshotRepository;
import io.livelattice.core.service.snapshot.SnapshotContentStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SnapshotManagerTest {

    @Mock
    private CanvasRepository canvasRepository;
    @Mock
    private CanvasSnapshotRepository snapshotRepository;
    @Mock
    private SnapshotContentStore contentStore;

    private SnapshotManager snapshotManager;
    private final String canvasId = UUID.randomUUID().toString();
    private final String userId = UUID.randomUUID().toString();
    private final UUID canvasUuid = UUID.fromString(canvasId);
    private final UUID userUuid = UUID.fromString(userId);
    private final UUID workspaceUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        snapshotManager = new SnapshotManager(canvasRepository, snapshotRepository, contentStore);
    }

    private Canvas activeCanvas() {
        Canvas canvas = new Canvas(workspaceUuid, "C", Map.of("elements", List.of()), userUuid);
        canvas.setId(canvasUuid);
        return canvas;
    }

    @Test
    void createSnapshot_shouldPersistAndUpdateCanvas() {
        Canvas canvas = activeCanvas();
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        when(snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvasUuid, 1L)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(contentStore.put(any(), any(), eq(1L), any())).thenReturn("snapshots/path/1.json");

        SnapshotResponse response = snapshotManager.createSnapshot(canvasUuid, userUuid, 1L, Map.of("elements", List.of()));

        assertEquals(canvasUuid.toString(), response.canvasId());
        assertEquals(1L, response.version());
        assertEquals("snapshots/path/1.json", response.minioPath());
        assertEquals(0, canvas.getOperationCountSinceSnapshot());
    }

    @Test
    void createSnapshot_shouldReturnExistingForDuplicateVersion() {
        Canvas canvas = activeCanvas();
        CanvasSnapshot existing = new CanvasSnapshot(canvasUuid, 1L, Map.of("v", 1), "path1", userUuid);
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        when(snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvasUuid, 1L)).thenReturn(Optional.of(existing));

        SnapshotResponse response = snapshotManager.createSnapshot(canvasUuid, userUuid, 1L, Map.of("elements", List.of()));

        assertEquals(1L, response.version());
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void createSnapshot_shouldApplyRetentionBeyondLatest100() {
        Canvas canvas = activeCanvas();
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        when(snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvasUuid, 102L)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(contentStore.put(any(), any(), eq(102L), any())).thenReturn("snapshots/path/102.json");

        List<CanvasSnapshot> snapshots = IntStream.rangeClosed(1, 100)
            .mapToObj(i -> new CanvasSnapshot(canvasUuid, i, Map.of("v", i), "path" + i, userUuid))
            .toList();
        CanvasSnapshot latest = new CanvasSnapshot(canvasUuid, 101L, Map.of("v", 101), "path101", userUuid);
        snapshots = new java.util.ArrayList<>(snapshots);
        snapshots.add(latest);

        when(snapshotRepository.findByCanvasIdAndDeletedAtIsNullOrderByVersionDesc(eq(canvasUuid), any(Pageable.class)))
            .thenReturn(snapshots);

        snapshotManager.createSnapshot(canvasUuid, userUuid, 102L, Map.of("v", 102));

        verify(snapshotRepository, times(101)).save(any());
        verify(snapshotRepository, atLeastOnce()).save(argThat(s -> s.getContent() == null));
    }

    @Test
    void listSnapshots_shouldReturnSnapshots() {
        CanvasSnapshot s1 = new CanvasSnapshot(canvasUuid, 1L, Map.of(), "p1", userUuid);
        when(canvasRepository.existsById(canvasUuid)).thenReturn(true);
        when(snapshotRepository.findByCanvasIdAndDeletedAtIsNullOrderByVersionDesc(eq(canvasUuid), any(Pageable.class))).thenReturn(List.of(s1));

        List<SnapshotResponse> list = snapshotManager.listSnapshots(canvasUuid);

        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).version());
    }

    @Test
    void listSnapshots_shouldThrowIfCanvasNotFound() {
        when(canvasRepository.existsById(canvasUuid)).thenReturn(false);
        assertThrows(NotFoundException.class, () -> snapshotManager.listSnapshots(canvasUuid));
    }

    @Test
    void getSnapshotContent_shouldReturnContent() {
        Canvas canvas = activeCanvas();
        CanvasSnapshot snapshot = new CanvasSnapshot(canvasUuid, 2L, Map.of("elements", List.of(Map.of("id", "x"))), "p2", userUuid);
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        when(snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvasUuid, 2L)).thenReturn(Optional.of(snapshot));

        SnapshotContentResponse response = snapshotManager.getSnapshotContent(canvasUuid, 2L);

        assertEquals(2L, response.version());
        assertFalse(((List<?>) response.content().get("elements")).isEmpty());
    }

    @Test
    void restoreSnapshot_shouldReplaceCanvasContentAndBumpVersion() {
        Canvas canvas = activeCanvas();
        canvas.setVersion(5);
        canvas.setOperationCountSinceSnapshot(7);
        CanvasSnapshot snapshot = new CanvasSnapshot(canvasUuid, 2L, Map.of("restored", true), "p2", userUuid);
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        when(snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvasUuid, 2L)).thenReturn(Optional.of(snapshot));
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Canvas restored = snapshotManager.restoreSnapshot(canvasUuid, 2L, userUuid);

        assertEquals(6, restored.getVersion());
        assertEquals(8, restored.getOperationCountSinceSnapshot());
        assertEquals(Map.of("restored", true), restored.getContent());
    }

    @Test
    void restoreSnapshot_shouldLoadContentFromStoreWhenPostgresContentCleared() {
        Canvas canvas = activeCanvas();
        canvas.setVersion(5);
        CanvasSnapshot snapshot = new CanvasSnapshot(canvasUuid, 2L, null, "p2", userUuid);
        when(contentStore.get("p2")).thenReturn(Map.of("restored", true));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        when(snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvasUuid, 2L)).thenReturn(Optional.of(snapshot));
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Canvas restoredFromStore = snapshotManager.restoreSnapshot(canvasUuid, 2L, userUuid);

        assertEquals(Map.of("restored", true), restoredFromStore.getContent());
    }

    @Test
    void restoreSnapshot_shouldRejectEmptyContent() {
        Canvas canvas = activeCanvas();
        canvas.setVersion(5);
        CanvasSnapshot snapshot = new CanvasSnapshot(canvasUuid, 2L, Map.of(), "p2", userUuid);
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        when(snapshotRepository.findByCanvasIdAndVersionAndDeletedAtIsNull(canvasUuid, 2L)).thenReturn(Optional.of(snapshot));

        assertThrows(ConflictException.class, () -> snapshotManager.restoreSnapshot(canvasUuid, 2L, userUuid));
    }
}
