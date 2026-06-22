package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.livelattice.core.event.CanvasCreated;
import io.livelattice.core.event.CanvasDeleted;
import io.livelattice.core.event.CanvasUpdated;
import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.exception.ConflictException;
import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.exception.OptimisticLockException;
import io.livelattice.core.model.dto.CanvasResponse;
import io.livelattice.core.model.dto.CreateCanvasRequest;
import io.livelattice.core.model.dto.UpdateCanvasRequest;
import io.livelattice.core.model.entity.Canvas;
import io.livelattice.core.model.entity.CanvasTemplate;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.entity.Workspace;
import io.livelattice.core.model.enums.Tier;
import io.livelattice.core.repository.CanvasRepository;
import io.livelattice.core.repository.CanvasSnapshotRepository;
import io.livelattice.core.repository.CanvasTemplateRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CanvasServiceTest {

    @Mock
    private CanvasRepository canvasRepository;
    @Mock
    private CanvasSnapshotRepository snapshotRepository;
    @Mock
    private CanvasTemplateRepository templateRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private QuotaService quotaService;
    @Mock
    private SnapshotManager snapshotManager;
    @Mock
    private EventPublisher eventPublisher;

    private CanvasService canvasService;
    private final String userId = UUID.randomUUID().toString();
    private final String workspaceId = UUID.randomUUID().toString();
    private final String canvasId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        canvasService = new CanvasService(
            canvasRepository, templateRepository,
            workspaceRepository, userRepository, permissionService,
            quotaService, snapshotManager, eventPublisher
        );
    }

    private User user() {
        return new User(userId, userId + "@livelattice.local", "User");
    }

    @Test
    void create_shouldCreateCanvas() {
        User user = user();
        Workspace ws = new Workspace("WS", "ws", Tier.FREE, user.getId());
        ws.setId(UUID.fromString(workspaceId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:create");
        when(workspaceRepository.findById(UUID.fromString(workspaceId))).thenReturn(Optional.of(ws));
        doNothing().when(quotaService).checkCanvasQuota(workspaceId);
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(snapshotManager.createSnapshot(any(), any(), anyLong(), any())).thenReturn(null);

        CanvasResponse response = canvasService.create(new CreateCanvasRequest(workspaceId, "Diagram", null), userId);

        assertEquals("Diagram", response.title());
        assertEquals(workspaceId, response.workspaceId());
        assertEquals(1, response.version());
        verify(eventPublisher).publish(any(CanvasCreated.class));
        verify(snapshotManager).createSnapshot(any(UUID.class), any(UUID.class), anyLong(), any());
    }

    @Test
    void create_shouldCloneTemplate() {
        User user = user();
        Workspace ws = new Workspace("WS", "ws", Tier.FREE, user.getId());
        ws.setId(UUID.fromString(workspaceId));
        String templateId = UUID.randomUUID().toString();
        CanvasTemplate template = new CanvasTemplate(UUID.fromString(workspaceId), "T", "cat", null, Map.of("elements", List.of(Map.of("id", "el-1"))), user.getId());

        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:create");
        when(workspaceRepository.findById(UUID.fromString(workspaceId))).thenReturn(Optional.of(ws));
        doNothing().when(quotaService).checkCanvasQuota(workspaceId);
        when(templateRepository.findById(UUID.fromString(templateId))).thenReturn(Optional.of(template));
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(snapshotManager.createSnapshot(any(), any(), anyLong(), any())).thenReturn(null);

        CanvasResponse response = canvasService.create(new CreateCanvasRequest(workspaceId, "Diagram", templateId), userId);

        assertEquals("Diagram", response.title());
        assertEquals(1, ((List<?>) response.content().get("elements")).size());
    }

    @Test
    void getById_shouldReturnCanvas() {
        User user = user();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "Diagram", Map.of("elements", List.of()), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(UUID.fromString(canvasId))).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:read");

        CanvasResponse response = canvasService.getById(canvasId, userId);

        assertEquals("Diagram", response.title());
    }

    @Test
    void update_shouldUpdateTitleAndContent() {
        User user = user();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "Old", Map.of("elements", List.of()), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(UUID.fromString(canvasId))).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CanvasResponse response = canvasService.update(canvasId,
            new UpdateCanvasRequest("New", Map.of("elements", List.of(Map.of("id", "el-1"))), null, null), userId);

        assertEquals("New", response.title());
        assertEquals(2, response.version());
        assertEquals(1, response.operationCountSinceSnapshot());
        verify(eventPublisher).publish(any(CanvasUpdated.class));
    }

    @Test
    void update_shouldThrowOnVersionConflict() {
        User user = user();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "Old", Map.of(), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        canvas.setVersion(3);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(UUID.fromString(canvasId))).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");

        assertThrows(ConflictException.class, () ->
            canvasService.update(canvasId, new UpdateCanvasRequest(null, Map.of(), 2L, null), userId));
    }

    @Test
    void update_shouldThrowOnLockVersionConflict() {
        User user = user();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "Old", Map.of(), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        canvas.setLockVersion(5);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(UUID.fromString(canvasId))).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");

        assertThrows(OptimisticLockException.class, () ->
            canvasService.update(canvasId, new UpdateCanvasRequest(null, Map.of(), null, 4), userId));
    }

    @Test
    void update_shouldAutoSnapshotAfterThreshold() {
        User user = user();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "Old", Map.of("elements", List.of()), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        canvas.setVersion(1);
        canvas.setOperationCountSinceSnapshot(49);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(UUID.fromString(canvasId))).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(snapshotManager.createSnapshot(any(), any(), anyLong(), any())).thenReturn(null);

        CanvasResponse response = canvasService.update(canvasId,
            new UpdateCanvasRequest(null, Map.of("elements", List.of(Map.of("id", "el-1"))), null, null), userId);

        assertEquals(2, response.version());
        assertEquals(0, response.operationCountSinceSnapshot());
        verify(snapshotManager).createSnapshot(eq(UUID.fromString(canvasId)), eq(user.getId()), eq(2L), any());
    }

    @Test
    void delete_shouldSoftDelete() {
        User user = user();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "Old", Map.of(), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(UUID.fromString(canvasId))).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:delete");
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        canvasService.delete(canvasId, userId);

        assertNotNull(canvas.getDeletedAt());
        verify(eventPublisher).publish(any(CanvasDeleted.class));
    }

    @Test
    void duplicate_shouldDeepCopyContent() {
        User user = user();
        Canvas source = new Canvas(UUID.fromString(workspaceId), "Original", Map.of("elements", List.of(Map.of("id", "el-1"))), user.getId());
        source.setId(UUID.fromString(canvasId));
        source.setVersion(5);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(UUID.fromString(canvasId))).thenReturn(Optional.of(source));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:create");
        doNothing().when(quotaService).checkCanvasQuota(workspaceId);
        when(canvasRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(snapshotManager.createSnapshot(any(), any(), anyLong(), any())).thenReturn(null);

        CanvasResponse response = canvasService.duplicate(canvasId, userId);

        assertEquals("Original (Copy)", response.title());
        assertEquals(1, response.version());
        assertEquals(0, response.operationCountSinceSnapshot());
        verify(eventPublisher).publish(any(CanvasCreated.class));
    }

    @Test
    void listByWorkspace_shouldFilterByWorkspaceWithLimitOffset() {
        User user = user();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "A", Map.of(), user.getId());
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:read");
        when(canvasRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID.fromString(workspaceId), 50, 0)).thenReturn(List.of(canvas));

        List<CanvasResponse> list = canvasService.listByWorkspace(workspaceId, userId, 50, 0);

        assertEquals(1, list.size());
    }

    @Test
    void create_shouldRejectMismatchedTemplateWorkspace() {
        User user = user();
        Workspace ws = new Workspace("WS", "ws", Tier.FREE, user.getId());
        ws.setId(UUID.fromString(workspaceId));
        String templateId = UUID.randomUUID().toString();
        CanvasTemplate template = new CanvasTemplate(UUID.randomUUID(), "T", "cat", null, Map.of("elements", List.of(Map.of("id", "el-1"))), user.getId());

        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:create");
        when(workspaceRepository.findById(UUID.fromString(workspaceId))).thenReturn(Optional.of(ws));
        doNothing().when(quotaService).checkCanvasQuota(workspaceId);
        when(templateRepository.findById(UUID.fromString(templateId))).thenReturn(Optional.of(template));

        assertThrows(ForbiddenException.class, () ->
            canvasService.create(new CreateCanvasRequest(workspaceId, "Diagram", templateId), userId));
    }
}
