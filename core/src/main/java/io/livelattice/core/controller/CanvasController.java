package io.livelattice.core.controller;

import io.livelattice.core.model.dto.CanvasResponse;
import io.livelattice.core.model.dto.CreateCanvasRequest;
import io.livelattice.core.model.dto.SnapshotContentResponse;
import io.livelattice.core.model.dto.SnapshotResponse;
import io.livelattice.core.model.dto.UpdateCanvasRequest;
import io.livelattice.core.service.CanvasService;
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
@RequestMapping("/canvases")
public class CanvasController {

    private final CanvasService canvasService;

    public CanvasController(CanvasService canvasService) {
        this.canvasService = canvasService;
    }

    @GetMapping
    public ResponseEntity<List<CanvasResponse>> list(
            @RequestParam String workspaceId,
            @RequestHeader("x-user-id") String userId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(canvasService.listByWorkspace(workspaceId, userId, limit, offset));
    }

    @PostMapping
    public ResponseEntity<CanvasResponse> create(
            @Valid @RequestBody CreateCanvasRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canvasService.create(request, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CanvasResponse> get(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(canvasService.getById(id, userId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CanvasResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCanvasRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(canvasService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        canvasService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<CanvasResponse> duplicate(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canvasService.duplicate(id, userId));
    }

    @PostMapping("/{id}/snapshot")
    public ResponseEntity<SnapshotResponse> snapshot(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canvasService.createSnapshot(id, userId));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<SnapshotResponse>> history(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(canvasService.listSnapshots(id, userId));
    }

    @GetMapping("/{id}/history/{version}")
    public ResponseEntity<SnapshotContentResponse> historyVersion(
            @PathVariable String id,
            @PathVariable long version,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(canvasService.getSnapshotContent(id, version, userId));
    }

    @PostMapping("/{id}/restore/{version}")
    public ResponseEntity<CanvasResponse> restore(
            @PathVariable String id,
            @PathVariable long version,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(canvasService.restoreSnapshot(id, version, userId));
    }
}
