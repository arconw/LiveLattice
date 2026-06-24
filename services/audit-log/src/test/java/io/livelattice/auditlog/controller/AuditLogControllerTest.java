package io.livelattice.auditlog.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.livelattice.auditlog.dto.AuditEventResponse;
import io.livelattice.auditlog.dto.ExportStatusResponse;
import io.livelattice.auditlog.exception.ForbiddenException;
import io.livelattice.auditlog.service.AuditEventService;
import io.livelattice.auditlog.service.AuditLogAuthorizationService;
import io.livelattice.auditlog.service.ExportService;
import io.livelattice.auditlog.service.RetentionService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogControllerTest {

    private final AuditEventService auditEventService = mock(AuditEventService.class);
    private final ExportService exportService = mock(ExportService.class);
    private final RetentionService retentionService = mock(RetentionService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditLogAuthorizationService authorizationService = new AuditLogAuthorizationService(jdbcTemplate);
    private final AuditLogController controller = new AuditLogController(auditEventService, exportService, retentionService, authorizationService);

    @Test
    void nonAdminQueryRequiresWorkspaceScope() {
        assertThrows(ForbiddenException.class, () ->
            controller.query(null, null, null, null, null, null, null, 1, 20, "subject-1", "viewer"));
        verifyNoInteractions(auditEventService);
    }

    @Test
    void nonMemberCannotReadEventFromAnotherWorkspace() {
        when(auditEventService.findById("event-1")).thenReturn(Optional.of(event("workspace-1")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(false);
        assertThrows(ForbiddenException.class, () ->
            controller.getById("event-1", "subject-1", "viewer"));
    }

    @Test
    void nonMemberCannotReadExportStatusFromAnotherWorkspace() {
        when(exportService.findById("job-1")).thenReturn(new ExportStatusResponse("job-1", "completed", "csv", "path", null));
        when(exportService.findWorkspaceId("job-1")).thenReturn(Optional.of("workspace-1"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(false);
        assertThrows(ForbiddenException.class, () ->
            controller.exportStatus("job-1", "subject-1", "viewer"));
    }

    @Test
    void nearMissAdminRoleCannotRunRetention() {
        assertThrows(ForbiddenException.class, () -> controller.runRetention("notadmin"));
        verifyNoInteractions(retentionService);
    }

    @Test
    void exactAdminRoleCanRunRetention() {
        controller.runRetention("viewer,admin");
        verify(retentionService).runRetention();
    }

    private AuditEventResponse event(String workspaceId) {
        return new AuditEventResponse("event-1", workspaceId, "actor-1", "workspace.create", "workspace", workspaceId, null, null, "prev", "hash", Instant.now());
    }
}
