package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.livelattice.core.exception.QuotaExceededException;
import io.livelattice.core.model.entity.Workspace;
import io.livelattice.core.model.enums.Tier;
import io.livelattice.core.repository.WorkspaceMemberRepository;
import io.livelattice.core.repository.WorkspaceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private WorkspaceMemberRepository memberRepository;

    private QuotaService quotaService;
    private final String ownerId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        quotaService = new QuotaService(workspaceRepository, memberRepository);
    }

    @Test
    void checkMemberQuota_shouldAllowUpTo5ForFree() {
        String wsId = UUID.randomUUID().toString();
        Workspace ws = new Workspace("WS", "ws", Tier.FREE, UUID.fromString(ownerId));
        when(workspaceRepository.findById(any())).thenReturn(Optional.of(ws));
        when(memberRepository.countByWorkspaceId(any())).thenReturn(4L);

        assertDoesNotThrow(() -> quotaService.checkMemberQuota(wsId));
    }

    @Test
    void checkMemberQuota_shouldThrowWhenAtLimitForFree() {
        String wsId = UUID.randomUUID().toString();
        Workspace ws = new Workspace("WS", "ws", Tier.FREE, UUID.fromString(ownerId));
        when(workspaceRepository.findById(any())).thenReturn(Optional.of(ws));
        when(memberRepository.countByWorkspaceId(any())).thenReturn(5L);

        assertThrows(QuotaExceededException.class, () -> quotaService.checkMemberQuota(wsId));
    }

    @Test
    void checkMemberQuota_shouldAllowUpTo50ForPro() {
        String wsId = UUID.randomUUID().toString();
        Workspace ws = new Workspace("WS", "ws", Tier.PRO, UUID.fromString(ownerId));
        when(workspaceRepository.findById(any())).thenReturn(Optional.of(ws));
        when(memberRepository.countByWorkspaceId(any())).thenReturn(49L);

        assertDoesNotThrow(() -> quotaService.checkMemberQuota(wsId));
    }

    @Test
    void checkMemberQuota_shouldThrowWhenAtLimitForPro() {
        String wsId = UUID.randomUUID().toString();
        Workspace ws = new Workspace("WS", "ws", Tier.PRO, UUID.fromString(ownerId));
        when(workspaceRepository.findById(any())).thenReturn(Optional.of(ws));
        when(memberRepository.countByWorkspaceId(any())).thenReturn(50L);

        assertThrows(QuotaExceededException.class, () -> quotaService.checkMemberQuota(wsId));
    }
}
