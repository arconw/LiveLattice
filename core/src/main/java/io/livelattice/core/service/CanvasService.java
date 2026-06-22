package io.livelattice.core.service;

import io.livelattice.core.event.CanvasCreated;
import io.livelattice.core.event.CanvasDeleted;
import io.livelattice.core.event.CanvasUpdated;
import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.exception.BadRequestException;
import io.livelattice.core.exception.ConflictException;
import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.exception.OptimisticLockException;
import io.livelattice.core.model.dto.CanvasResponse;
import io.livelattice.core.model.dto.CreateCanvasRequest;
import io.livelattice.core.model.dto.SnapshotContentResponse;
import io.livelattice.core.model.dto.SnapshotResponse;
import io.livelattice.core.model.dto.UpdateCanvasRequest;
import io.livelattice.core.model.entity.Canvas;
import io.livelattice.core.model.entity.CanvasTemplate;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.entity.Workspace;
import io.livelattice.core.repository.CanvasRepository;
import io.livelattice.core.repository.CanvasTemplateRepository;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CanvasService {

    private final CanvasRepository canvasRepository;
    private final CanvasTemplateRepository templateRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final QuotaService quotaService;
    private final SnapshotManager snapshotManager;
    private final EventPublisher eventPublisher;

    private static final int AUTO_SNAPSHOT_THRESHOLD = 50;

    public CanvasService(CanvasRepository canvasRepository,
                         CanvasTemplateRepository templateRepository,
                         WorkspaceRepository workspaceRepository,
                         UserRepository userRepository,
                         PermissionService permissionService,
                         QuotaService quotaService,
                         SnapshotManager snapshotManager,
                         EventPublisher eventPublisher) {
        this.canvasRepository = canvasRepository;
        this.templateRepository = templateRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.quotaService = quotaService;
        this.snapshotManager = snapshotManager;
        this.eventPublisher = eventPublisher;
    }

    private User resolveUser(String externalSubject) {
        return userRepository.findByExternalSubject(externalSubject)
            .orElseThrow(() -> new NotFoundException("User not found: " + externalSubject));
    }

    private UUID resolveUserId(String externalSubject) {
        return resolveUser(externalSubject).getId();
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid UUID for " + field + ": " + value);
        }
    }

    private Canvas findActiveCanvas(UUID canvasId) {
        return canvasRepository.findByIdAndDeletedAtIsNull(canvasId)
            .orElseThrow(() -> new NotFoundException("Canvas not found: " + canvasId));
    }

    private void checkRead(String workspaceId, String userId) {
        permissionService.requirePermission(workspaceId, userId, "canvas:read");
    }

    private void checkEdit(String workspaceId, String userId) {
        permissionService.requirePermission(workspaceId, userId, "canvas:edit");
    }

    private void checkCreate(String workspaceId, String userId) {
        permissionService.requirePermission(workspaceId, userId, "canvas:create");
    }

    private void checkDelete(String workspaceId, String userId) {
        permissionService.requirePermission(workspaceId, userId, "canvas:delete");
    }

    private Map<String, Object> defaultContent() {
        Map<String, Object> content = new HashMap<>();
        content.put("elements", List.of());
        content.put("viewport", Map.of("zoom", 1, "panX", 0, "panY", 0));
        content.put("metadata", Map.of("width", 1920, "height", 1080, "backgroundColor", "#ffffff", "gridEnabled", true));
        return content;
    }

    public CanvasResponse create(CreateCanvasRequest request, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID workspaceUuid = parseUuid(request.workspaceId(), "workspaceId");
        checkCreate(request.workspaceId(), internalUserId.toString());

        Workspace workspace = workspaceRepository.findById(workspaceUuid)
            .orElseThrow(() -> new NotFoundException("Workspace not found: " + request.workspaceId()));

        quotaService.checkCanvasQuota(workspace.getId().toString());

        Map<String, Object> content = defaultContent();
        if (request.templateId() != null && !request.templateId().isBlank()) {
            UUID templateUuid = parseUuid(request.templateId(), "templateId");
            CanvasTemplate template = templateRepository.findById(templateUuid)
                .orElseThrow(() -> new NotFoundException("Template not found: " + request.templateId()));
            if (template.getWorkspaceId() != null && !template.getWorkspaceId().equals(workspaceUuid)) {
                throw new ForbiddenException("Template does not belong to workspace");
            }
            content = new HashMap<>(template.getContent());
        }

        Canvas canvas = new Canvas(workspace.getId(), request.title(), content, internalUserId);
        try {
            canvas = canvasRepository.save(canvas);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockException("Canvas was modified concurrently");
        }

        eventPublisher.publish(new CanvasCreated(canvas.getId(), canvas.getWorkspaceId(), internalUserId));

        snapshotManager.createSnapshot(canvas.getId(), internalUserId, canvas.getVersion(), canvas.getContent());
        canvas.setSnapshotVersion(canvas.getVersion());
        canvas = canvasRepository.save(canvas);

        return CanvasResponse.from(canvas);
    }

    @Transactional(readOnly = true)
    public List<CanvasResponse> listByWorkspace(String workspaceId, String userId, int limit, int offset) {
        UUID internalUserId = resolveUserId(userId);
        UUID workspaceUuid = parseUuid(workspaceId, "workspaceId");
        checkRead(workspaceId, internalUserId.toString());

        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        return canvasRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByUpdatedAtDesc(workspaceUuid, safeLimit, safeOffset).stream()
            .map(CanvasResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public CanvasResponse getById(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        Canvas canvas = findActiveCanvas(parseUuid(id, "canvasId"));
        checkRead(canvas.getWorkspaceId().toString(), internalUserId.toString());
        return CanvasResponse.from(canvas);
    }

    public CanvasResponse update(String id, UpdateCanvasRequest request, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID canvasUuid = parseUuid(id, "canvasId");
        Canvas canvas = findActiveCanvas(canvasUuid);
        checkEdit(canvas.getWorkspaceId().toString(), internalUserId.toString());

        if (request.expectedVersion() != null && canvas.getVersion() != request.expectedVersion()) {
            throw new ConflictException("Version conflict: expected " + request.expectedVersion() + ", found " + canvas.getVersion());
        }
        if (request.expectedLockVersion() != null && canvas.getLockVersion() != request.expectedLockVersion()) {
            throw new OptimisticLockException("Lock version conflict");
        }

        boolean contentChanged = false;
        if (request.title() != null) {
            canvas.setTitle(request.title());
        }
        if (request.content() != null) {
            canvas.setContent(request.content());
            canvas.setVersion(canvas.getVersion() + 1);
            canvas.setOperationCountSinceSnapshot(canvas.getOperationCountSinceSnapshot() + 1);
            contentChanged = true;
        }

        canvas.setUpdatedBy(internalUserId);
        canvas.setUpdatedAt(Instant.now());

        try {
            canvas = canvasRepository.save(canvas);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockException("Canvas was modified concurrently");
        }

        if (contentChanged && canvas.getOperationCountSinceSnapshot() >= AUTO_SNAPSHOT_THRESHOLD) {
            snapshotManager.createSnapshot(canvas.getId(), internalUserId, canvas.getVersion(), canvas.getContent());
            canvas.setSnapshotVersion(canvas.getVersion());
            canvas.setOperationCountSinceSnapshot(0);
            canvas = canvasRepository.save(canvas);
        }

        eventPublisher.publish(new CanvasUpdated(canvas.getId(), canvas.getWorkspaceId(), internalUserId, canvas.getVersion()));
        return CanvasResponse.from(canvas);
    }

    public void delete(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID canvasUuid = parseUuid(id, "canvasId");
        Canvas canvas = findActiveCanvas(canvasUuid);
        checkDelete(canvas.getWorkspaceId().toString(), internalUserId.toString());

        canvas.setDeletedAt(Instant.now());
        canvas.setUpdatedBy(internalUserId);
        canvas.setUpdatedAt(Instant.now());
        try {
            canvasRepository.save(canvas);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockException("Canvas was modified concurrently");
        }

        eventPublisher.publish(new CanvasDeleted(canvas.getId(), canvas.getWorkspaceId(), internalUserId));
    }

    public CanvasResponse duplicate(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID sourceUuid = parseUuid(id, "canvasId");
        Canvas source = findActiveCanvas(sourceUuid);
        checkCreate(source.getWorkspaceId().toString(), internalUserId.toString());

        quotaService.checkCanvasQuota(source.getWorkspaceId().toString());

        Canvas copy = new Canvas(
            source.getWorkspaceId(),
            source.getTitle() + " (Copy)",
            new HashMap<>(source.getContent()),
            internalUserId
        );
        copy.setVersion(1);
        copy.setOperationCountSinceSnapshot(0);
        copy = canvasRepository.save(copy);

        eventPublisher.publish(new CanvasCreated(copy.getId(), copy.getWorkspaceId(), internalUserId));

        snapshotManager.createSnapshot(copy.getId(), internalUserId, copy.getVersion(), copy.getContent());
        copy.setSnapshotVersion(copy.getVersion());
        copy = canvasRepository.save(copy);

        return CanvasResponse.from(copy);
    }

    public SnapshotResponse createSnapshot(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID canvasUuid = parseUuid(id, "canvasId");
        Canvas canvas = findActiveCanvas(canvasUuid);
        checkEdit(canvas.getWorkspaceId().toString(), internalUserId.toString());
        return snapshotManager.createSnapshot(canvas.getId(), internalUserId, canvas.getVersion(), canvas.getContent());
    }

    @Transactional(readOnly = true)
    public List<SnapshotResponse> listSnapshots(String id, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID canvasUuid = parseUuid(id, "canvasId");
        Canvas canvas = findActiveCanvas(canvasUuid);
        checkRead(canvas.getWorkspaceId().toString(), internalUserId.toString());
        return snapshotManager.listSnapshots(canvas.getId());
    }

    @Transactional(readOnly = true)
    public SnapshotContentResponse getSnapshotContent(String id, long version, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID canvasUuid = parseUuid(id, "canvasId");
        Canvas canvas = findActiveCanvas(canvasUuid);
        checkRead(canvas.getWorkspaceId().toString(), internalUserId.toString());
        return snapshotManager.getSnapshotContent(canvas.getId(), version);
    }

    public CanvasResponse restoreSnapshot(String id, long version, String userId) {
        UUID internalUserId = resolveUserId(userId);
        UUID canvasUuid = parseUuid(id, "canvasId");
        Canvas canvas = findActiveCanvas(canvasUuid);
        checkEdit(canvas.getWorkspaceId().toString(), internalUserId.toString());
        snapshotManager.restoreSnapshot(canvas.getId(), version, internalUserId);
        return CanvasResponse.from(canvasRepository.findByIdAndDeletedAtIsNull(canvas.getId()).orElseThrow(
            () -> new NotFoundException("Canvas not found: " + id)));
    }
}
