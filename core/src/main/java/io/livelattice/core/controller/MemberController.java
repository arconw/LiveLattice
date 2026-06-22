package io.livelattice.core.controller;

import io.livelattice.core.model.dto.AddMemberRequest;
import io.livelattice.core.model.dto.ChangeRoleRequest;
import io.livelattice.core.model.dto.MemberResponse;
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
@RequestMapping("/workspaces/{workspaceId}/members")
public class MemberController {

    private final WorkspaceService workspaceService;

    public MemberController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<MemberResponse>> list(
            @PathVariable String workspaceId,
            @RequestHeader("x-user-id") String userId) {
        return ResponseEntity.ok(workspaceService.listMembers(workspaceId, userId));
    }

    @PostMapping
    public ResponseEntity<MemberResponse> add(
            @PathVariable String workspaceId,
            @Valid @RequestBody AddMemberRequest request,
            @RequestHeader("x-user-id") String userId) {
        MemberResponse response = workspaceService.addMember(workspaceId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<MemberResponse> changeRole(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @Valid @RequestBody ChangeRoleRequest request,
            @RequestHeader("x-user-id") String currentUserId) {
        return ResponseEntity.ok(workspaceService.changeRole(workspaceId, userId, request, currentUserId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(
            @PathVariable String workspaceId,
            @PathVariable String userId,
            @RequestHeader("x-user-id") String currentUserId) {
        workspaceService.removeMember(workspaceId, userId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
