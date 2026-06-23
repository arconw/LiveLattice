package io.livelattice.importexport.service;

import io.livelattice.importexport.exception.ForbiddenException;
import io.livelattice.importexport.exception.NotFoundException;
import io.livelattice.importexport.exception.ValidationException;
import io.livelattice.importexport.repository.CoreUserRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ImportExportAuthorizationService {

    private final CoreUserRepository coreUserRepository;
    private final JdbcTemplate jdbcTemplate;

    public ImportExportAuthorizationService(CoreUserRepository coreUserRepository,
                                            JdbcTemplate jdbcTemplate) {
        this.coreUserRepository = coreUserRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID resolveUserId(String userSubject) {
        if (userSubject == null || userSubject.isBlank()) {
            throw new ValidationException("Authenticated user is required");
        }
        return coreUserRepository.findByExternalSubject(userSubject)
            .map(user -> user.getId())
            .orElseGet(() -> resolveInternalUserId(userSubject));
    }

    public void requirePermission(UUID workspaceId, String userSubject, String permission) {
        requirePermission(workspaceId, resolveUserId(userSubject), permission);
    }

    public void requirePermission(UUID workspaceId, UUID userId, String permission) {
        Boolean allowed = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM workspace_members wm
                JOIN role_permissions rp ON rp.role = wm.role AND rp.permission = ?
                WHERE wm.workspace_id = ? AND wm.user_id = ?
            )
            """, Boolean.class, permission, workspaceId, userId);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new ForbiddenException("User " + userId + " lacks permission: " + permission);
        }
    }

    private UUID resolveInternalUserId(String userSubject) {
        try {
            UUID userId = UUID.fromString(userSubject);
            return coreUserRepository.findById(userId)
                .map(user -> user.getId())
                .orElseThrow(() -> new NotFoundException("User not found: " + userSubject));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("User not found: " + userSubject);
        }
    }
}
