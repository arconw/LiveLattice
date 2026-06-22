package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private CanvasTemplateRepository templateRepository;
    @Mock
    private CanvasRepository canvasRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionService permissionService;

    private TemplateService templateService;
    private final String userId = UUID.randomUUID().toString();
    private final String workspaceId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(templateRepository, canvasRepository, userRepository, permissionService);
    }

    private User user() {
        return new User(userId, userId + "@livelattice.local", "User");
    }

    @Test
    void create_shouldSaveTemplate() {
        User user = user();
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:create");
        when(templateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TemplateResponse response = templateService.create(
            new CreateTemplateRequest("Blank", "basic", "thumb.png", Map.of("elements", List.of()), null),
            workspaceId, userId);

        assertEquals("Blank", response.name());
        assertEquals("basic", response.category());
        assertEquals(workspaceId, response.workspaceId());
    }

    @Test
    void create_shouldSaveFromCanvasId() {
        User user = user();
        String canvasId = UUID.randomUUID().toString();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "C", Map.of("elements", List.of(Map.of("id", "el-1"))), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:create");
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvas.getId())).thenReturn(Optional.of(canvas));
        when(templateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TemplateResponse response = templateService.create(
            new CreateTemplateRequest("From Canvas", "custom", null, null, canvasId), workspaceId, userId);

        assertEquals("From Canvas", response.name());
        assertEquals(1, ((List<?>) response.content().get("elements")).size());
    }

    @Test
    void create_shouldRejectMismatchedWorkspaceCanvas() {
        User user = user();
        String canvasId = UUID.randomUUID().toString();
        UUID otherWorkspace = UUID.randomUUID();
        Canvas canvas = new Canvas(otherWorkspace, "C", Map.of(), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvas.getId())).thenReturn(Optional.of(canvas));

        assertThrows(ForbiddenException.class, () ->
            templateService.create(new CreateTemplateRequest("From Canvas", "custom", null, null, canvasId), workspaceId, userId));
    }

    @Test
    void saveFromCanvas_shouldCopyCanvasContent() {
        User user = user();
        String canvasId = UUID.randomUUID().toString();
        Canvas canvas = new Canvas(UUID.fromString(workspaceId), "C", Map.of("elements", List.of(Map.of("id", "el-1"))), user.getId());
        canvas.setId(UUID.fromString(canvasId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:create");
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvas.getId())).thenReturn(Optional.of(canvas));
        when(templateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TemplateResponse response = templateService.saveFromCanvas(canvasId,
            new CreateTemplateRequest("From Canvas", "custom", null, null, null), workspaceId, userId);

        assertEquals("From Canvas", response.name());
        assertEquals(1, ((List<?>) response.content().get("elements")).size());
    }

    @Test
    void saveFromCanvas_shouldThrowWhenCanvasMissing() {
        User user = user();
        String canvasId = UUID.randomUUID().toString();
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(UUID.fromString(canvasId))).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
            templateService.saveFromCanvas(canvasId, new CreateTemplateRequest("X", null, null, Map.of(), null), workspaceId, userId));
    }

    @Test
    void list_shouldFilterByWorkspace() {
        User user = user();
        CanvasTemplate t1 = new CanvasTemplate(UUID.fromString(workspaceId), "A", null, null, Map.of(), UUID.fromString(userId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:read");
        when(templateRepository.findByWorkspaceIdOrderByCreatedAtDesc(UUID.fromString(workspaceId))).thenReturn(List.of(t1));

        List<TemplateResponse> list = templateService.list(workspaceId, null, userId);

        assertEquals(1, list.size());
    }

    @Test
    void list_shouldFilterByWorkspaceAndCategory() {
        User user = user();
        CanvasTemplate t1 = new CanvasTemplate(UUID.fromString(workspaceId), "A", "cat", null, Map.of(), UUID.fromString(userId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:read");
        when(templateRepository.findByWorkspaceIdAndCategoryOrderByCreatedAtDesc(UUID.fromString(workspaceId), "cat")).thenReturn(List.of(t1));

        List<TemplateResponse> list = templateService.list(workspaceId, "cat", userId);

        assertEquals(1, list.size());
    }

    @Test
    void list_shouldReturnGlobalTemplatesWhenNoWorkspace() {
        User user = user();
        CanvasTemplate t1 = new CanvasTemplate(null, "Global", null, null, Map.of(), UUID.fromString(userId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(templateRepository.findByWorkspaceIdIsNullOrderByCreatedAtDesc()).thenReturn(List.of(t1));

        List<TemplateResponse> list = templateService.list(null, null, userId);

        assertEquals(1, list.size());
        assertNull(list.get(0).workspaceId());
    }

    @Test
    void list_shouldFilterGlobalByCategory() {
        User user = user();
        CanvasTemplate t1 = new CanvasTemplate(null, "Global", "cat", null, Map.of(), UUID.fromString(userId));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(templateRepository.findByWorkspaceIdIsNullAndCategoryOrderByCreatedAtDesc("cat")).thenReturn(List.of(t1));

        List<TemplateResponse> list = templateService.list(null, "cat", userId);

        assertEquals(1, list.size());
    }

    @Test
    void getById_shouldReturnTemplate() {
        UUID templateId = UUID.randomUUID();
        CanvasTemplate template = new CanvasTemplate(UUID.fromString(workspaceId), "A", null, null, Map.of(), UUID.fromString(userId));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        TemplateResponse response = templateService.getById(templateId.toString());

        assertEquals("A", response.name());
    }

    @Test
    void getById_shouldThrowWhenMissing() {
        UUID templateId = UUID.randomUUID();
        when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> templateService.getById(templateId.toString()));
    }
}
