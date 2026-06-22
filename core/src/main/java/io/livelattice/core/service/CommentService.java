package io.livelattice.core.service;

import io.livelattice.core.event.CommentAdded;
import io.livelattice.core.event.EventPublisher;
import io.livelattice.core.exception.BadRequestException;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final CanvasRepository canvasRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final EventPublisher eventPublisher;

    public CommentService(CommentRepository commentRepository,
                          CanvasRepository canvasRepository,
                          UserRepository userRepository,
                          PermissionService permissionService,
                          EventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.canvasRepository = canvasRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.eventPublisher = eventPublisher;
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

    private Canvas findActiveCanvas(String canvasId) {
        return canvasRepository.findByIdAndDeletedAtIsNull(parseUuid(canvasId, "canvasId"))
            .orElseThrow(() -> new NotFoundException("Canvas not found: " + canvasId));
    }

    private void checkRead(String workspaceId, String userId) {
        permissionService.requirePermission(workspaceId, userId, "canvas:read");
    }

    private void checkEdit(String workspaceId, String userId) {
        permissionService.requirePermission(workspaceId, userId, "canvas:edit");
    }

    public CommentResponse create(String canvasId, CreateCommentRequest request, String userId) {
        User user = resolveUser(userId);
        Canvas canvas = findActiveCanvas(canvasId);
        checkEdit(canvas.getWorkspaceId().toString(), user.getId().toString());

        UUID parentId = null;
        if (request.parentId() != null && !request.parentId().isBlank()) {
            parentId = parseUuid(request.parentId(), "parentId");
            Comment parent = commentRepository.findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new NotFoundException("Parent comment not found: " + request.parentId()));
            if (!parent.getCanvasId().equals(canvas.getId())) {
                throw new BadRequestException("Parent comment does not belong to canvas");
            }
        }

        Comment comment = new Comment(
            canvas.getId(),
            parentId,
            user.getId(),
            request.content(),
            request.targetElementId()
        );
        comment = commentRepository.save(comment);

        eventPublisher.publish(new CommentAdded(comment.getId(), canvas.getId(), user.getId()));
        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listByCanvas(String canvasId, String userId, int limit, String cursor) {
        User user = resolveUser(userId);
        Canvas canvas = findActiveCanvas(canvasId);
        checkRead(canvas.getWorkspaceId().toString(), user.getId().toString());

        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, safeLimit);
        if (cursor != null && !cursor.isBlank()) {
            Instant cursorTime;
            try {
                cursorTime = Instant.parse(cursor);
            } catch (RuntimeException e) {
                throw new BadRequestException("Invalid cursor: " + cursor);
            }
            return commentRepository.findByCanvasIdAndCreatedAtLessThanAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                canvas.getId(), cursorTime, pageable).stream()
                .map(CommentResponse::from)
                .toList();
        }
        return commentRepository.findByCanvasIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(canvas.getId(), pageable).stream()
            .map(CommentResponse::from)
            .toList();
    }

    public CommentResponse update(String canvasId, String commentId, UpdateCommentRequest request, String userId) {
        User user = resolveUser(userId);
        Canvas canvas = findActiveCanvas(canvasId);
        checkEdit(canvas.getWorkspaceId().toString(), user.getId().toString());

        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(parseUuid(commentId, "commentId"))
            .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
        if (!comment.getCanvasId().equals(canvas.getId())) {
            throw new BadRequestException("Comment does not belong to canvas");
        }

        if (request.content() != null && !request.content().isBlank()) {
            if (!comment.getAuthorId().equals(user.getId())) {
                throw new ForbiddenException("Only the author can edit this comment");
            }
            comment.setContent(request.content());
        }
        if (request.resolved() != null) {
            comment.setResolved(request.resolved());
            if (request.resolved()) {
                comment.setResolvedBy(user.getId());
                comment.setResolvedAt(Instant.now());
            } else {
                comment.setResolvedBy(null);
                comment.setResolvedAt(null);
            }
        }
        comment.setUpdatedAt(Instant.now());
        comment = commentRepository.save(comment);
        return CommentResponse.from(comment);
    }

    public void delete(String canvasId, String commentId, String userId) {
        User user = resolveUser(userId);
        Canvas canvas = findActiveCanvas(canvasId);
        checkEdit(canvas.getWorkspaceId().toString(), user.getId().toString());

        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(parseUuid(commentId, "commentId"))
            .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
        if (!comment.getCanvasId().equals(canvas.getId())) {
            throw new BadRequestException("Comment does not belong to canvas");
        }

        if (!comment.getAuthorId().equals(user.getId()) && !permissionService.hasPermission(canvas.getWorkspaceId().toString(), user.getId().toString(), "canvas:delete")) {
            throw new ForbiddenException("Cannot delete this comment");
        }

        comment.setDeletedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());
        commentRepository.save(comment);
    }
}
