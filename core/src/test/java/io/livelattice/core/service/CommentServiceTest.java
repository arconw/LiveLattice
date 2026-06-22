package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.livelattice.core.event.CommentAdded;
import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.model.dto.CommentResponse;
import io.livelattice.core.model.dto.CreateCommentRequest;
import io.livelattice.core.model.dto.UpdateCommentRequest;
import io.livelattice.core.model.entity.Canvas;
import io.livelattice.core.model.entity.Comment;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.repository.CanvasRepository;
import io.livelattice.core.repository.CommentRepository;
import io.livelattice.core.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private CanvasRepository canvasRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private EventPublisher eventPublisher;

    private CommentService commentService;
    private final String userId = UUID.randomUUID().toString();
    private final String canvasId = UUID.randomUUID().toString();
    private final String workspaceId = UUID.randomUUID().toString();
    private final UUID canvasUuid = UUID.fromString(canvasId);

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, canvasRepository, userRepository, permissionService, eventPublisher);
    }

    private User user() {
        return new User(userId, userId + "@livelattice.local", "User");
    }

    private Canvas canvas() {
        return new Canvas(UUID.fromString(workspaceId), "C", java.util.Map.of(), UUID.fromString(userId));
    }

    @Test
    void create_shouldAddComment() {
        User user = user();
        Canvas canvas = canvas();
        canvas.setId(canvasUuid);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");
        when(commentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CommentResponse response = commentService.create(canvasId, new CreateCommentRequest("Nice", null, "el-1"), userId);

        assertEquals("Nice", response.content());
        assertEquals("el-1", response.targetElementId());
        assertFalse(response.resolved());
        verify(eventPublisher).publish(any(CommentAdded.class));
    }

    @Test
    void create_shouldRejectMissingParent() {
        User user = user();
        Canvas canvas = canvas();
        canvas.setId(canvasUuid);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");
        when(commentRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
            commentService.create(canvasId, new CreateCommentRequest("Reply", UUID.randomUUID().toString(), null), userId));
    }

    @Test
    void listByCanvas_shouldReturnTopLevelComments() {
        User user = user();
        Canvas canvas = canvas();
        canvas.setId(canvasUuid);
        Comment comment = new Comment(canvasUuid, null, user.getId(), "Top", null);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:read");
        when(commentRepository.findByCanvasIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(eq(canvasUuid), any(Pageable.class))).thenReturn(List.of(comment));

        List<CommentResponse> list = commentService.listByCanvas(canvasId, userId, 50, null);

        assertEquals(1, list.size());
        assertNull(list.get(0).parentId());
    }

    @Test
    void update_shouldEditContentAndResolve() {
        User user = user();
        Canvas canvas = canvas();
        canvas.setId(canvasUuid);
        Comment comment = new Comment(canvasUuid, null, user.getId(), "Old", null);
        comment.setId(UUID.randomUUID());
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CommentResponse response = commentService.update(canvasId, comment.getId().toString(),
            new UpdateCommentRequest("New", true), userId);

        assertEquals("New", response.content());
        assertTrue(response.resolved());
        assertNotNull(response.resolvedAt());
    }

    @Test
    void update_shouldRejectNonAuthor() {
        User user = user();
        Canvas canvas = canvas();
        canvas.setId(canvasUuid);
        Comment comment = new Comment(canvasUuid, null, UUID.randomUUID(), "Old", null);
        comment.setId(UUID.randomUUID());
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));

        assertThrows(ForbiddenException.class, () ->
            commentService.update(canvasId, comment.getId().toString(), new UpdateCommentRequest("New", null), userId));
    }

    @Test
    void delete_shouldSoftDeleteForAuthor() {
        User user = user();
        Canvas canvas = canvas();
        canvas.setId(canvasUuid);
        Comment comment = new Comment(canvasUuid, null, user.getId(), "X", null);
        comment.setId(UUID.randomUUID());
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        commentService.delete(canvasId, comment.getId().toString(), userId);

        assertNotNull(comment.getDeletedAt());
    }

    @Test
    void delete_shouldAllowDeleterPermission() {
        User user = user();
        Canvas canvas = canvas();
        canvas.setId(canvasUuid);
        UUID authorId = UUID.randomUUID();
        Comment comment = new Comment(canvasUuid, null, authorId, "X", null);
        comment.setId(UUID.randomUUID());
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(canvasRepository.findByIdAndDeletedAtIsNull(canvasUuid)).thenReturn(Optional.of(canvas));
        doNothing().when(permissionService).requirePermission(workspaceId, user.getId().toString(), "canvas:edit");
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
        when(permissionService.hasPermission(workspaceId, user.getId().toString(), "canvas:delete")).thenReturn(true);
        when(commentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> commentService.delete(canvasId, comment.getId().toString(), userId));
    }
}
