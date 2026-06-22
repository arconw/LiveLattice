package io.livelattice.core.controller;

import io.livelattice.core.model.dto.CommentResponse;
import io.livelattice.core.model.dto.CreateCommentRequest;
import io.livelattice.core.model.dto.UpdateCommentRequest;
import io.livelattice.core.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/canvases/{canvasId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> list(
            @PathVariable String canvasId,
            @RequestHeader("x-user-id") String userId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(commentService.listByCanvas(canvasId, userId, limit, cursor));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> create(
            @PathVariable String canvasId,
            @Valid @RequestBody CreateCommentRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(canvasId, request, userId));
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentResponse> update(
            @PathVariable String canvasId,
            @PathVariable String commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(commentService.update(canvasId, commentId, request, userId));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String canvasId,
            @PathVariable String commentId,
            @RequestHeader("x-user-id") String userId) {
        commentService.delete(canvasId, commentId, userId);
        return ResponseEntity.noContent().build();
    }
}
