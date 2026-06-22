package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.livelattice.core.exception.ConflictException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.exception.QuotaExceededException;
import io.livelattice.core.model.dto.AddMemberRequest;
import io.livelattice.core.model.dto.ChangeRoleRequest;
import io.livelattice.core.model.dto.CreateWorkspaceRequest;
import io.livelattice.core.model.dto.MemberResponse;
import io.livelattice.core.model.dto.UpdateWorkspaceRequest;
import io.livelattice.core.model.dto.WorkspaceResponse;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.entity.Workspace;
import io.livelattice.core.model.entity.WorkspaceMember;
import io.livelattice.core.model.enums.Role;
import io.livelattice.core.model.enums.Tier;
import io.livelattice.core.repository.UserRepository;
import io.livelattice.core.repository.WorkspaceMemberRepository;
import io.livelattice.core.repository.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private WorkspaceMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private QuotaService quotaService;

    private WorkspaceService workspaceService;
    private final String userId = UUID.randomUUID().toString();
    private final String wsId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(
            workspaceRepository, memberRepository, userRepository,
            permissionService, quotaService
        );
    }

    @Test
    void create_shouldCreateWorkspaceAndAddOwner() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("Test WS", "test-ws");

        User user = new User(userId, "a@b.com", "User1");
        when(workspaceRepository.existsBySlugAndDeletedAtIsNull("test-ws")).thenReturn(false);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(workspaceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WorkspaceResponse response = workspaceService.create(request, userId);

        assertEquals("Test WS", response.name());
        assertEquals("test-ws", response.slug());
        assertEquals(Tier.FREE.name(), response.tier());
        assertEquals(user.getId().toString(), response.ownerId());
    }

    @Test
    void create_shouldMaterializeUserIfNotExists() {
        when(workspaceRepository.existsBySlugAndDeletedAtIsNull("ws")).thenReturn(false);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(workspaceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        workspaceService.create(new CreateWorkspaceRequest("WS", "ws"), userId);

        verify(userRepository).save(any());
    }

    @Test
    void create_shouldThrowOnDuplicateSlug() {
        when(workspaceRepository.existsBySlugAndDeletedAtIsNull("taken")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
            workspaceService.create(new CreateWorkspaceRequest("WS", "taken"), userId));
    }

    @Test
    void getById_shouldReturnWorkspaceIfMember() {
        Workspace ws = new Workspace("WS", "ws", Tier.FREE, UUID.fromString(userId));
        User user = new User(userId, "a@b.com", "User1");
        when(workspaceRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(ws));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "workspace:read");

        WorkspaceResponse response = workspaceService.getById(wsId, userId);
        assertEquals("WS", response.name());
    }

    @Test
    void getById_shouldThrowIfNotFound() {
        when(workspaceRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
            workspaceService.getById(wsId, userId));
    }

    @Test
    void listByUser_shouldReturnUserWorkspaces() {
        User user = new User(userId, "a@b.com", "User1");
        Workspace ws = new Workspace("WS", "ws", Tier.FREE, user.getId());
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        when(workspaceRepository.findByUserId(user.getId())).thenReturn(List.of(ws));

        List<WorkspaceResponse> list = workspaceService.listByUser(userId);
        assertEquals(1, list.size());
    }

    @Test
    void update_shouldModifyWorkspace() {
        Workspace ws = new Workspace("Old", "old-slug", Tier.FREE, UUID.fromString(userId));
        User user = new User(userId, "a@b.com", "User1");
        when(workspaceRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(ws));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "workspace:update");
        when(workspaceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WorkspaceResponse response = workspaceService.update(wsId,
            new UpdateWorkspaceRequest("New Name", null, null), userId);

        assertEquals("New Name", response.name());
    }

    @Test
    void delete_shouldSoftDeleteWorkspace() {
        Workspace ws = new Workspace("WS", "ws", Tier.FREE, UUID.fromString(userId));
        User user = new User(userId, "a@b.com", "User1");
        when(workspaceRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(ws));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "workspace:delete");

        workspaceService.delete(wsId, userId);

        assertNotNull(ws.getDeletedAt());
        verify(workspaceRepository).save(ws);
        verify(workspaceRepository, never()).delete(ws);
    }

    @Test
    void addMember_shouldAddIfPermitted() {
        String memberId = UUID.randomUUID().toString();
        AddMemberRequest request = new AddMemberRequest(memberId, Role.EDITOR);
        User inviter = new User(userId, "inviter@b.com", "Inviter");
        User invited = new User(memberId, "invited@b.com", "Invited");
        when(workspaceRepository.existsByIdAndDeletedAtIsNull(any())).thenReturn(true);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(inviter));
        when(userRepository.findByExternalSubject(memberId)).thenReturn(Optional.of(invited));
        doNothing().when(permissionService).requirePermission(wsId, inviter.getId().toString(), "member:add");
        doNothing().when(quotaService).checkMemberQuota(wsId);
        when(memberRepository.findByWorkspaceIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MemberResponse response = workspaceService.addMember(wsId, request, userId);

        assertEquals(memberId, response.userId());
        assertEquals(Role.EDITOR.name(), response.role());
    }

    @Test
    void addMember_shouldThrowOnDuplicate() {
        String memberId = UUID.randomUUID().toString();
        AddMemberRequest request = new AddMemberRequest(memberId, Role.EDITOR);
        User inviter = new User(userId, "inviter@b.com", "Inviter");
        User invited = new User(memberId, "invited@b.com", "Invited");
        UUID wsUuid = UUID.fromString(wsId);
        when(workspaceRepository.existsByIdAndDeletedAtIsNull(any())).thenReturn(true);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(inviter));
        when(userRepository.findByExternalSubject(memberId)).thenReturn(Optional.of(invited));
        doNothing().when(permissionService).requirePermission(wsId, inviter.getId().toString(), "member:add");
        doNothing().when(quotaService).checkMemberQuota(wsId);
        when(memberRepository.findByWorkspaceIdAndUserId(wsUuid, invited.getId()))
            .thenReturn(Optional.of(new WorkspaceMember(wsUuid, invited.getId(), Role.EDITOR, inviter.getId())));

        assertThrows(ConflictException.class, () ->
            workspaceService.addMember(wsId, request, userId));
    }

    @Test
    void changeRole_shouldUpdateRole() {
        String memberId = UUID.randomUUID().toString();
        User targetUser = new User(memberId, "target@b.com", "Target");
        User currentUser = new User(userId, "current@b.com", "Current");
        UUID wsUuid = UUID.fromString(wsId);
        WorkspaceMember member = new WorkspaceMember(wsUuid, targetUser.getId(), Role.VIEWER, currentUser.getId());
        when(userRepository.findByExternalSubject(memberId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(currentUser));
        when(memberRepository.findByWorkspaceIdAndUserId(wsUuid, targetUser.getId())).thenReturn(Optional.of(member));
        doNothing().when(permissionService).requirePermission(wsId, currentUser.getId().toString(), "member:change_role");
        when(memberRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MemberResponse response = workspaceService.changeRole(wsId, memberId,
            new ChangeRoleRequest(Role.ADMIN), userId);

        assertEquals(Role.ADMIN.name(), response.role());
    }

    @Test
    void removeMember_shouldDelete() {
        String memberId = UUID.randomUUID().toString();
        User targetUser = new User(memberId, "target@b.com", "Target");
        User currentUser = new User(userId, "current@b.com", "Current");
        UUID wsUuid = UUID.fromString(wsId);
        WorkspaceMember member = new WorkspaceMember(wsUuid, targetUser.getId(), Role.VIEWER, currentUser.getId());
        when(userRepository.findByExternalSubject(memberId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(currentUser));
        when(memberRepository.findByWorkspaceIdAndUserId(wsUuid, targetUser.getId())).thenReturn(Optional.of(member));
        doNothing().when(permissionService).requirePermission(wsId, currentUser.getId().toString(), "member:remove");

        workspaceService.removeMember(wsId, memberId, userId);

        verify(memberRepository).delete(member);
    }

    @Test
    void addMember_shouldThrowWhenQuotaExceeded() {
        String memberId = UUID.randomUUID().toString();
        AddMemberRequest request = new AddMemberRequest(memberId, Role.EDITOR);
        User inviter = new User(userId, "inviter@b.com", "Inviter");
        when(workspaceRepository.existsByIdAndDeletedAtIsNull(any())).thenReturn(true);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(inviter));
        doNothing().when(permissionService).requirePermission(wsId, inviter.getId().toString(), "member:add");
        doThrow(new QuotaExceededException("Member limit reached"))
            .when(quotaService).checkMemberQuota(wsId);

        assertThrows(QuotaExceededException.class, () ->
            workspaceService.addMember(wsId, request, userId));
    }

    @Test
    void listMembers_shouldEnforcePermission() {
        User user = new User(userId, "a@b.com", "User1");
        when(workspaceRepository.existsByIdAndDeletedAtIsNull(any())).thenReturn(true);
        when(userRepository.findByExternalSubject(userId)).thenReturn(Optional.of(user));
        doNothing().when(permissionService).requirePermission(wsId, user.getId().toString(), "member:read");
        when(memberRepository.findByWorkspaceIdOrderByJoinedAtAsc(any())).thenReturn(List.of(
            new WorkspaceMember(UUID.fromString(wsId), user.getId(), Role.OWNER, user.getId())
        ));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        List<MemberResponse> members = workspaceService.listMembers(wsId, userId);
        assertEquals(1, members.size());
    }
}
