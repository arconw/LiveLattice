package io.livelattice.core.event;

import java.util.UUID;

public record MemberInvited(UUID workspaceId, UUID invitedUserId, UUID inviterUserId) {}
