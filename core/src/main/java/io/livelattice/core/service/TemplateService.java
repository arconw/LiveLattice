package io.livelattice.core.service;

import io.livelattice.core.exception.BadRequestException;
import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.model.dto.CreateTemplateRequest;
import io.livelattice.core.model.dto.TemplateResponse;
import io.livelattice.core.model.entity.Canvas;
import io.livelattice.core.model.entity.CanvasTemplate;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.repository.CanvasRepository;
import io.livelattice.core.repository.CanvasTemplateRepository;
import io.livelattice.core.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TemplateService {

    private final CanvasTemplateRepository templateRepository;
    private final CanvasRepository canvasRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public TemplateService(CanvasTemplateRepository templateRepository,
                           CanvasRepository canvasRepository,
                           UserRepository userRepository,
                           PermissionService permissionService) {
        this.templateRepository = templateRepository;
        this.canvasRepository = canvasRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    private User resolveUser(String externalSubject) {
        return userRepository.findByExternalSubject(externalSubject)
            .orElseThrow(() -> new NotFoundException("User not found: " + externalSubject));
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid UUID for " + field + ": " + value);
        }
    }

    private void checkCreate(String workspaceId, String userId) {
        permissionService.requirePermission(workspaceId, userId, "canvas:create");
    }

    private void checkRead(String workspaceId, String userId) {
        permissionService.requirePermission(workspaceId, userId, "canvas:read");
    }

    public TemplateResponse saveFromCanvas(String canvasId, CreateTemplateRequest request, String workspaceId, String userId) {
        User user = resolveUser(userId);
        Canvas canvas = canvasRepository.findByIdAndDeletedAtIsNull(parseUuid(canvasId, "canvasId"))
            .orElseThrow(() -> new NotFoundException("Canvas not found: " + canvasId));
        UUID workspaceUuid = workspaceId != null && !workspaceId.isBlank() ? parseUuid(workspaceId, "workspaceId") : canvas.getWorkspaceId();
        if (!canvas.getWorkspaceId().equals(workspaceUuid)) {
            throw new ForbiddenException("Canvas does not belong to workspace");
        }
        checkCreate(workspaceUuid.toString(), user.getId().toString());

        CanvasTemplate template = new CanvasTemplate(
            workspaceUuid,
            request.name(),
            request.category(),
            request.thumbnail(),
            request.content() != null ? request.content() : canvas.getContent(),
            user.getId()
        );
        template = templateRepository.save(template);
        return TemplateResponse.from(template);
    }

    public TemplateResponse create(CreateTemplateRequest request, String workspaceId, String userId) {
        User user = resolveUser(userId);
        UUID workspaceUuid = workspaceId != null && !workspaceId.isBlank() ? parseUuid(workspaceId, "workspaceId") : null;
        Map<String, Object> content = request.content();
        if (request.canvasId() != null && !request.canvasId().isBlank()) {
            Canvas canvas = canvasRepository.findByIdAndDeletedAtIsNull(parseUuid(request.canvasId(), "canvasId"))
                .orElseThrow(() -> new NotFoundException("Canvas not found: " + request.canvasId()));
            if (workspaceUuid == null) {
                workspaceUuid = canvas.getWorkspaceId();
            }
            if (!canvas.getWorkspaceId().equals(workspaceUuid)) {
                throw new ForbiddenException("Canvas does not belong to workspace");
            }
            content = canvas.getContent();
        }
        if (workspaceUuid == null) {
            throw new BadRequestException("workspaceId or canvasId is required");
        }
        checkCreate(workspaceUuid.toString(), user.getId().toString());
        if (content == null) {
            throw new BadRequestException("Template content is required");
        }
        CanvasTemplate template = new CanvasTemplate(
            workspaceUuid,
            request.name(),
            request.category(),
            request.thumbnail(),
            content,
            user.getId()
        );
        template = templateRepository.save(template);
        return TemplateResponse.from(template);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(String workspaceId, String category, String userId) {
        User user = resolveUser(userId);
        List<CanvasTemplate> templates;
        if (workspaceId != null && !workspaceId.isBlank()) {
            checkRead(workspaceId, user.getId().toString());
            UUID workspaceUuid = parseUuid(workspaceId, "workspaceId");
            if (category != null && !category.isBlank()) {
                templates = templateRepository.findByWorkspaceIdAndCategoryOrderByCreatedAtDesc(workspaceUuid, category);
            } else {
                templates = templateRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceUuid);
            }
        } else {
            if (category != null && !category.isBlank()) {
                templates = templateRepository.findByWorkspaceIdIsNullAndCategoryOrderByCreatedAtDesc(category);
            } else {
                templates = templateRepository.findByWorkspaceIdIsNullOrderByCreatedAtDesc();
            }
        }
        return templates.stream()
            .map(TemplateResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse getById(String id) {
        CanvasTemplate template = templateRepository.findById(parseUuid(id, "templateId"))
            .orElseThrow(() -> new NotFoundException("Template not found: " + id));
        return TemplateResponse.from(template);
    }
}
