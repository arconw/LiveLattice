package io.livelattice.auditlog.service;

import io.livelattice.auditlog.exception.ForbiddenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogAuthorizationService {

    private static final String ADMIN_ROLE = "admin";
    private static final String WORKSPACE_READ_PERMISSION = "workspace:read";

    private final JdbcTemplate jdbcTemplate;

    public AuditLogAuthorizationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireWorkspaceAccess(String workspaceId, String subject, String rolesHeader) {
        if (hasAdminRole(rolesHeader)) {
            return;
        }
        if (blank(workspaceId)) {
            throw new ForbiddenException("Workspace scope is required");
        }
        if (blank(subject)) {
            throw new ForbiddenException("Authenticated user is required");
        }
        Boolean allowed = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM workspace_members wm
                JOIN users u ON u.id::text = wm.user_id::text
                JOIN role_permissions rp ON lower(rp.role) = lower(wm.role) AND rp.permission = ?
                WHERE wm.workspace_id::text = ? AND (u.external_subject = ? OR u.id::text = ?)
            )
            """, Boolean.class, WORKSPACE_READ_PERMISSION, workspaceId, subject, subject);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new ForbiddenException("Workspace access required");
        }
    }

    public void requireAdmin(String rolesHeader) {
        if (!hasAdminRole(rolesHeader)) {
            throw new ForbiddenException("Admin role required");
        }
    }

    public boolean hasAdminRole(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        for (String role : rolesHeader.split(",")) {
            if (ADMIN_ROLE.equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
