package io.livelattice.core.service;

import io.livelattice.core.exception.ConflictException;
import io.livelattice.core.exception.NotFoundException;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final QuotaService quotaService;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                             WorkspaceMemberRepository memberRepository,
                             UserRepository userRepository,
                             PermissionService permissionService,
                             QuotaService quotaService) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.quotaService = quotaService;
    }

    private UUID resolveUserId(String externalSubject) {
        return resolveUser(externalSubject).getId();
    }

    private User resolveUser(String externalSubject) {
        return userRepository.findByExternalSubject(externalSubject)
            .orElseThrow(() -> new NotFoundException("User not found: " + externalSubject));
    }

    public WorkspaceResponse create(CreateWorkspaceRequest request, String userId) {
        if (workspaceRepository.existsBySlugAndDeletedAtIsNull(request.slug())) {
            throw new ConflictException("Slug already taken: " + request.slug());
        }

        User user = resolveUser(userId);
        UUID internalUserId = user.getId();

        Workspace workspace = new Workspace(
            request.name(),
            request.slug(),
            Tier.FREE,
            internalUserId
        );
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember owner = new WorkspaceMember(workspace.getId(), internalUserId, Role.OWNER, internalUserId);
        memberRepository.save(owner);

        return WorkspaceResponse.from(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listByUser(String userId) {
        UUID internalUserId = resolveUserId(userId);
        return workspaceRepository.findByUserId(internalUserId).stream()
            .map(WorkspaceResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getById(String id, String userId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
            .orElseThrow(() -> new NotFoundException("Workspace not found: " + id));
        permissionService.requirePermission(id, resolveUserId(userId).toString(), "workspace:read");
        return WorkspaceResponse.from(workspace);
    }

    public WorkspaceResponse update(String id, UpdateWorkspaceRequest request, String userId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
            .orElseThrow(() -> new NotFoundException("Workspace not found: " + id));
        permissionService.requirePermission(id, resolveUserId(userId).toString(), "workspace:update");

        if (request.name() != null) {
            workspace.setName(request.name());
        }
        if (request.slug() != null) {
            if (!request.slug().equals(workspace.getSlug()) && workspaceRepository.existsBySlugAndDeletedAtIsNull(request.slug())) {
                throw new ConflictException("Slug already taken: " + request.slug());
            }
            workspace.setSlug(request.slug());
        }
        if (request.tier() != null) {
            workspace.setTier(Tier.valueOf(request.tier()));
        }
        workspace.setUpdatedAt(Instant.now());
        workspace = workspaceRepository.save(workspace);
        return WorkspaceResponse.from(workspace);
    }

    public void delete(String id, String userId) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
            .orElseThrow(() -> new NotFoundException("Workspace not found: " + id));
        permissionService.requirePermission(id, resolveUserId(userId).toString(), "workspace:delete");
        workspace.setDeletedAt(Instant.now());
        workspace.setUpdatedAt(Instant.now());
        workspaceRepository.save(workspace);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(String workspaceId, String userId) {
        if (!workspaceRepository.existsByIdAndDeletedAtIsNull(UUID.fromString(workspaceId))) {
            throw new NotFoundException("Workspace not found: " + workspaceId);
        }
        permissionService.requirePermission(workspaceId, resolveUserId(userId).toString(), "member:read");
        return memberRepository.findByWorkspaceIdOrderByJoinedAtAsc(UUID.fromString(workspaceId)).stream()
            .map(this::memberResponse)
            .toList();
    }

    public MemberResponse addMember(String workspaceId, AddMemberRequest request, String userId) {
        UUID wsUuid = UUID.fromString(workspaceId);
        UUID inviterUuid = resolveUserId(userId);

        if (!workspaceRepository.existsByIdAndDeletedAtIsNull(wsUuid)) {
            throw new NotFoundException("Workspace not found: " + workspaceId);
        }
        permissionService.requirePermission(workspaceId, inviterUuid.toString(), "member:add");
        quotaService.checkMemberQuota(workspaceId);

        User invitedUser = resolveUser(request.userId());
        UUID invitedUuid = invitedUser.getId();

        if (memberRepository.findByWorkspaceIdAndUserId(wsUuid, invitedUuid).isPresent()) {
            throw new ConflictException("User is already a member of this workspace");
        }

        WorkspaceMember member = new WorkspaceMember(wsUuid, invitedUuid, request.role(), inviterUuid);
        member = memberRepository.save(member);
        return MemberResponse.from(member, invitedUser);
    }

    public MemberResponse changeRole(String workspaceId, String targetUserId, ChangeRoleRequest request, String userId) {
        User targetUser = resolveUser(targetUserId);
        UUID targetUuid = targetUser.getId();
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(UUID.fromString(workspaceId), targetUuid)
            .orElseThrow(() -> new NotFoundException("Member not found: " + targetUserId));
        permissionService.requirePermission(workspaceId, resolveUserId(userId).toString(), "member:change_role");
        member.setRole(request.role());
        member = memberRepository.save(member);
        return MemberResponse.from(member, targetUser);
    }

    private MemberResponse memberResponse(WorkspaceMember member) {
        User user = userRepository.findById(member.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found: " + member.getUserId()));
        return MemberResponse.from(member, user);
    }

    public void removeMember(String workspaceId, String targetUserId, String userId) {
        UUID targetUuid = resolveUserId(targetUserId);
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(UUID.fromString(workspaceId), targetUuid)
            .orElseThrow(() -> new NotFoundException("Member not found: " + targetUserId));
        permissionService.requirePermission(workspaceId, resolveUserId(userId).toString(), "member:remove");
        memberRepository.delete(member);
    }
}
