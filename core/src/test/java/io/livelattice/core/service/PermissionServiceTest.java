package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.livelattice.core.model.entity.WorkspaceMember;
import io.livelattice.core.model.enums.Role;
import io.livelattice.core.repository.RolePermissionRepository;
import io.livelattice.core.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private WorkspaceMemberRepository memberRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    @Test
    void hasPermission_shouldAllowWhenRoleAllowsWithoutServiceToken() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PermissionService service = new PermissionService(memberRepository, rolePermissionRepository);
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(new WorkspaceMember(workspaceId, userId, Role.OWNER, userId)));
        when(rolePermissionRepository.existsByRoleAndPermission("OWNER", "canvas:read")).thenReturn(true);

        assertTrue(service.hasPermission(workspaceId.toString(), userId.toString(), "canvas:read"));
    }

    @Test
    void hasPermission_shouldRejectServiceTokenWorkspaceMismatch() {
        UUID workspaceId = UUID.randomUUID();
        UUID otherWorkspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PermissionService service = new PermissionService(memberRepository, rolePermissionRepository);
        AuthContext.setApiKey(new ApiKeyValidation("key-1", otherWorkspaceId.toString(), "subject", "owner@example.com", "Owner", List.of("canvas:read")));

        assertFalse(service.hasPermission(workspaceId.toString(), userId.toString(), "canvas:read"));
    }

    @Test
    void hasPermission_shouldRejectServiceTokenPermissionMismatch() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PermissionService service = new PermissionService(memberRepository, rolePermissionRepository);
        AuthContext.setApiKey(new ApiKeyValidation("key-1", workspaceId.toString(), "subject", "owner@example.com", "Owner", List.of("canvas:read")));

        assertFalse(service.hasPermission(workspaceId.toString(), userId.toString(), "canvas:delete"));
    }

    @Test
    void hasPermission_shouldRequireServiceTokenScopeAndRolePermission() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PermissionService service = new PermissionService(memberRepository, rolePermissionRepository);
        AuthContext.setApiKey(new ApiKeyValidation("key-1", workspaceId.toString(), "subject", "owner@example.com", "Owner", List.of("canvas:read")));
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
            .thenReturn(Optional.of(new WorkspaceMember(workspaceId, userId, Role.OWNER, userId)));
        when(rolePermissionRepository.existsByRoleAndPermission("OWNER", "canvas:read")).thenReturn(true);

        assertTrue(service.hasPermission(workspaceId.toString(), userId.toString(), "canvas:read"));
    }
}
