package io.livelattice.core.service;

import io.livelattice.core.exception.QuotaExceededException;
import io.livelattice.core.model.entity.Workspace;
import io.livelattice.core.model.enums.Tier;
import io.livelattice.core.repository.CanvasRepository;
import io.livelattice.core.repository.WorkspaceMemberRepository;
import io.livelattice.core.repository.WorkspaceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuotaService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final CanvasRepository canvasRepository;

    public QuotaService(WorkspaceRepository workspaceRepository,
                          WorkspaceMemberRepository memberRepository,
                          CanvasRepository canvasRepository) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.canvasRepository = canvasRepository;
    }

    public void checkMemberQuota(String workspaceId) {
        Workspace workspace = workspaceRepository.findById(UUID.fromString(workspaceId))
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        int maxMembers = workspace.getTier() == Tier.PRO ? 50 : 5;
        long current = memberRepository.countByWorkspaceId(UUID.fromString(workspaceId));
        if (current >= maxMembers) {
            throw new QuotaExceededException(
                "Member limit reached for tier " + workspace.getTier() +
                " (max " + maxMembers + ", current " + current + ")"
            );
        }
    }

    public void checkCanvasQuota(String workspaceId) {
        Workspace workspace = workspaceRepository.findById(UUID.fromString(workspaceId))
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        int maxCanvases = workspace.getTier() == Tier.PRO ? 500 : 10;
        long current = getCanvasCount(workspaceId);
        if (current >= maxCanvases) {
            throw new QuotaExceededException(
                "Canvas limit reached for tier " + workspace.getTier() +
                " (max " + maxCanvases + ", current " + current + ")"
            );
        }
    }

    public long getCanvasCount(String workspaceId) {
        return canvasRepository.countByWorkspaceIdAndDeletedAtIsNull(UUID.fromString(workspaceId));
    }
}
