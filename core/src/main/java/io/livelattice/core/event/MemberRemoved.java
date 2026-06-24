package io.livelattice.core.event;

import java.util.UUID;

public record MemberRemoved(UUID workspaceId, UUID targetUserId, UUID actorUserId) {}
