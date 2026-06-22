package io.livelattice.core.controller;

import io.livelattice.core.model.dto.CreateWorkspaceRequest;
import io.livelattice.core.model.dto.UpdateWorkspaceRequest;
import io.livelattice.core.model.dto.WorkspaceResponse;
import io.livelattice.core.service.WorkspaceService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @RequestHeader("x-user-id") String userId) {
        WorkspaceResponse response = workspaceService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> list(
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(workspaceService.listByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> get(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(workspaceService.getById(id, userId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateWorkspaceRequest request,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(workspaceService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader("x-user-id") String userId) {
        workspaceService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
