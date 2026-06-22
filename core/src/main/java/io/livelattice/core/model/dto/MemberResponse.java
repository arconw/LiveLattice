package io.livelattice.core.model.dto;

import io.livelattice.core.model.entity.User;
import io.livelattice.core.model.entity.WorkspaceMember;
import java.time.Instant;

public record MemberResponse(
    String userId,
    String role,
    Instant joinedAt
) {
    public static MemberResponse from(WorkspaceMember member, User user) {
        return new MemberResponse(user.getExternalSubject(), member.getRole().name(), member.getJoinedAt());
    }
}
