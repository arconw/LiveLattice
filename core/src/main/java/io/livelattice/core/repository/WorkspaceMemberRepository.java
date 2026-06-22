package io.livelattice.core.repository;

import io.livelattice.core.model.entity.WorkspaceMember;
import io.livelattice.core.model.entity.WorkspaceMemberId;
import io.livelattice.core.model.enums.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {
    List<WorkspaceMember> findByWorkspaceIdOrderByJoinedAtAsc(UUID workspaceId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    long countByWorkspaceId(UUID workspaceId);

    void deleteAllByWorkspaceId(UUID workspaceId);
}
