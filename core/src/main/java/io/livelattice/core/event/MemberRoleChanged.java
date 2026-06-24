package io.livelattice.core.event;

import java.util.UUID;

public record MemberRoleChanged(UUID workspaceId, UUID targetUserId, UUID actorUserId) {}
