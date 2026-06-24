package io.livelattice.auditlog.controller;

import io.livelattice.auditlog.dto.AuditEventResponse;
import io.livelattice.auditlog.dto.ExportRequest;
import io.livelattice.auditlog.dto.ExportStatusResponse;
import io.livelattice.auditlog.dto.PagedAuditEventsResponse;
import io.livelattice.auditlog.dto.VerifyResponse;
import io.livelattice.auditlog.exception.NotFoundException;
import io.livelattice.auditlog.service.AuditLogAuthorizationService;
import io.livelattice.auditlog.service.AuditEventService;
import io.livelattice.auditlog.service.AuditEventService.AuditQuery;
import io.livelattice.auditlog.service.ExportService;
import io.livelattice.auditlog.service.RetentionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class AuditLogController {

    private final AuditEventService auditEventService;
    private final ExportService exportService;
    private final RetentionService retentionService;
    private final AuditLogAuthorizationService authorizationService;

    public AuditLogController(AuditEventService auditEventService,
                              ExportService exportService,
                              RetentionService retentionService,
                              AuditLogAuthorizationService authorizationService) {
        this.auditEventService = auditEventService;
        this.exportService = exportService;
        this.retentionService = retentionService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/audit-log")
    public PagedAuditEventsResponse query(
            @RequestParam(required = false) String workspace_id,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor_id,
            @RequestParam(required = false) String target_type,
            @RequestParam(required = false) String target_id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestHeader("x-auth-subject") String subject,
            @RequestHeader(value = "x-auth-roles", required = false) String roles) {
        authorizationService.requireWorkspaceAccess(workspace_id, subject, roles);
        AuditQuery query = new AuditQuery(workspace_id, action, actor_id, target_type, target_id, from, to);
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return auditEventService.query(query, pageable);
    }

    @GetMapping("/audit-log/{id}")
    public ResponseEntity<AuditEventResponse> getById(
            @PathVariable String id,
            @RequestHeader("x-auth-subject") String subject,
            @RequestHeader(value = "x-auth-roles", required = false) String roles) {
        AuditEventResponse response = auditEventService.findById(id)
            .orElseThrow(() -> new NotFoundException("Audit event not found: " + id));
        authorizationService.requireWorkspaceAccess(response.workspaceId(), subject, roles);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/audit-log/verify")
    public VerifyResponse verify(
            @RequestParam(defaultValue = "1000") @Min(1) @Max(10000) int batchSize,
            @RequestHeader(value = "x-auth-roles", required = false) String roles) {
        authorizationService.requireAdmin(roles);
        return auditEventService.verify(batchSize);
    }

    @PostMapping("/audit-log/export")
    public ExportStatusResponse export(
            @Valid @RequestBody ExportRequest request,
            @RequestHeader("x-auth-subject") String subject,
            @RequestHeader(value = "x-auth-roles", required = false) String roles) {
        authorizationService.requireWorkspaceAccess(request.workspaceId(), subject, roles);
        return exportService.create(request);
    }

    @GetMapping("/audit-log/exports/{jobId}")
    public ResponseEntity<ExportStatusResponse> exportStatus(
            @PathVariable String jobId,
            @RequestHeader("x-auth-subject") String subject,
            @RequestHeader(value = "x-auth-roles", required = false) String roles) {
        ExportStatusResponse status = exportService.findById(jobId);
        if (status == null) {
            throw new NotFoundException("Export job not found: " + jobId);
        }
        String workspaceId = exportService.findWorkspaceId(jobId)
            .orElseThrow(() -> new NotFoundException("Export job not found: " + jobId));
        authorizationService.requireWorkspaceAccess(workspaceId, subject, roles);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/audit-log/admin/retention")
    public ResponseEntity<Map<String, String>> runRetention(
            @RequestHeader(value = "x-auth-roles", required = false) String roles) {
        authorizationService.requireAdmin(roles);
        retentionService.runRetention();
        return ResponseEntity.ok(Map.of("status", "retention executed"));
    }
}
