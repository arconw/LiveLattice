package io.livelattice.core.service;

import io.livelattice.core.exception.ForbiddenException;
import io.livelattice.core.model.entity.WorkspaceMember;
import io.livelattice.core.repository.RolePermissionRepository;
import io.livelattice.core.repository.WorkspaceMemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final WorkspaceMemberRepository memberRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public PermissionService(WorkspaceMemberRepository memberRepository,
                              RolePermissionRepository rolePermissionRepository) {
        this.memberRepository = memberRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public boolean hasPermission(String workspaceId, String userId, String permission) {
        Optional<WorkspaceMember> member = memberRepository.findByWorkspaceIdAndUserId(
            UUID.fromString(workspaceId), UUID.fromString(userId));
        if (member.isEmpty()) {
            return false;
        }
        return rolePermissionRepository.existsByRoleAndPermission(member.get().getRole().name(), permission);
    }

    public void requirePermission(String workspaceId, String userId, String permission) {
        if (!hasPermission(workspaceId, userId, permission)) {
            throw new ForbiddenException("User " + userId + " lacks permission: " + permission);
        }
    }
}
