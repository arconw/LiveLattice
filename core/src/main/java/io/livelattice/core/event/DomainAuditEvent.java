package io.livelattice.core.event;

import java.util.Map;
import java.util.UUID;

public record DomainAuditEvent(
    String eventType,
    String targetType,
    String id,
    String workspaceId,
    String actorId,
    Object changes,
    Object metadata
) {

    public static DomainAuditEvent canvasCreated(UUID canvasId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("canvas.create", "canvas", canvasId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent canvasUpdated(UUID canvasId, UUID workspaceId, UUID userId, long version) {
        return new DomainAuditEvent("canvas.update", "canvas", canvasId.toString(), workspaceId.toString(), userId.toString(), Map.of("version", version), null);
    }

    public static DomainAuditEvent canvasDeleted(UUID canvasId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("canvas.delete", "canvas", canvasId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent canvasRestored(UUID canvasId, UUID workspaceId, UUID userId, long restoredSnapshotVersion, long version) {
        return new DomainAuditEvent("canvas.restore", "canvas", canvasId.toString(), workspaceId.toString(), userId.toString(), Map.of("restored_snapshot_version", restoredSnapshotVersion, "version", version), null);
    }

    public static DomainAuditEvent commentAdded(UUID commentId, UUID canvasId, UUID workspaceId, UUID authorId) {
        return new DomainAuditEvent("comment.add", "comment", commentId.toString(), workspaceId.toString(), authorId.toString(), Map.of("canvas_id", canvasId.toString()), null);
    }

    public static DomainAuditEvent commentDeleted(UUID commentId, UUID canvasId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("comment.delete", "comment", commentId.toString(), workspaceId.toString(), userId.toString(), Map.of("canvas_id", canvasId.toString()), null);
    }

    public static DomainAuditEvent workspaceCreated(UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("workspace.create", "workspace", workspaceId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent workspaceUpdated(UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("workspace.update", "workspace", workspaceId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent workspaceDeleted(UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("workspace.delete", "workspace", workspaceId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent memberInvited(UUID workspaceId, UUID invitedId, UUID inviterId) {
        return new DomainAuditEvent("member.invite", "member", invitedId.toString(), workspaceId.toString(), inviterId.toString(), null, null);
    }

    public static DomainAuditEvent memberRoleChanged(UUID workspaceId, UUID targetId, UUID actorId) {
        return new DomainAuditEvent("member.role_change", "member", targetId.toString(), workspaceId.toString(), actorId.toString(), null, null);
    }

    public static DomainAuditEvent memberRemoved(UUID workspaceId, UUID targetId, UUID actorId) {
        return new DomainAuditEvent("member.remove", "member", targetId.toString(), workspaceId.toString(), actorId.toString(), null, null);
    }

    public static DomainAuditEvent dashboardCreated(UUID dashboardId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("dashboard.create", "dashboard", dashboardId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent dashboardUpdated(UUID dashboardId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("dashboard.update", "dashboard", dashboardId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent dashboardDeleted(UUID dashboardId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("dashboard.delete", "dashboard", dashboardId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent dataSourceCreated(UUID dataSourceId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("data_source.create", "data_source", dataSourceId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent dataSourceUpdated(UUID dataSourceId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("data_source.update", "data_source", dataSourceId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent dataSourceDeleted(UUID dataSourceId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("data_source.delete", "data_source", dataSourceId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent apiKeyCreated(UUID keyId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("api_key.create", "api_key", keyId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent apiKeyRevoked(UUID keyId, UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("api_key.revoke", "api_key", keyId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent settingsUpdated(UUID workspaceId, UUID userId) {
        return new DomainAuditEvent("settings.update", "settings", workspaceId.toString(), workspaceId.toString(), userId.toString(), null, null);
    }

    public static DomainAuditEvent tierChanged(UUID workspaceId, UUID userId, String tier) {
        return new DomainAuditEvent("tier.change", "tier", workspaceId.toString(), workspaceId.toString(), userId.toString(), Map.of("tier", tier), null);
    }

    public static DomainAuditEvent authLogin(String subject, String email) {
        return new DomainAuditEvent("auth.login", "auth", subject, subject, subject, Map.of("email", email), null);
    }

    public static DomainAuditEvent authLogout(String subject) {
        return new DomainAuditEvent("auth.logout", "auth", subject, subject, subject, null, null);
    }

    public static DomainAuditEvent authRefresh(String subject) {
        return new DomainAuditEvent("auth.refresh", "auth", subject, subject, subject, null, null);
    }

    public static DomainAuditEvent mfaEnabled(String subject) {
        return new DomainAuditEvent("auth.mfa_enable", "auth", subject, subject, subject, null, null);
    }

    public static DomainAuditEvent mfaDisabled(String subject) {
        return new DomainAuditEvent("auth.mfa_disable", "auth", subject, subject, subject, null, null);
    }
}
