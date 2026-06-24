package io.livelattice.auditlog.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.livelattice.auditlog.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogAuthorizationServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditLogAuthorizationService service = new AuditLogAuthorizationService(jdbcTemplate);

    @Test
    void parsesAdminRoleAsExactRole() {
        assertTrue(service.hasAdminRole("viewer, admin"));
        assertTrue(service.hasAdminRole("ADMIN"));
        assertFalse(service.hasAdminRole("viewer,notadmin"));
        assertFalse(service.hasAdminRole("administrator"));
    }

    @Test
    void adminCanAccessAnyWorkspaceWithoutMembershipLookup() {
        assertDoesNotThrow(() -> service.requireWorkspaceAccess("ws-other", "subject-1", "admin"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void nonAdminRequiresWorkspaceScope() {
        assertThrows(ForbiddenException.class, () -> service.requireWorkspaceAccess(null, "subject-1", "viewer"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void nonAdminRequiresWorkspaceMembership() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any())).thenReturn(true);
        assertDoesNotThrow(() -> service.requireWorkspaceAccess("workspace-1", "subject-1", "viewer"));
    }

    @Test
    void nonMemberIsForbidden() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.requireWorkspaceAccess("workspace-1", "subject-1", "viewer"));
    }

    @Test
    void nearMissRoleCannotRunAdminOperation() {
        assertThrows(ForbiddenException.class, () -> service.requireAdmin("notadmin"));
    }
}
