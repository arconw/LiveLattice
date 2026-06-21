# Workspaces & RBAC - Technical Design

## Responsibilities

- Workspace lifecycle (create, update, delete, transfer ownership)
- Member management (invite, role assignment, removal)
- Role-based access control (RBAC) with fine-grained permissions
- Tier management (free, pro, enterprise) with feature flags
- Resource quotas per workspace

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline; exact patch version is pinned during implementation
- **Persistence**: Spring Data JPA with PostgreSQL
- **Caching**: Redis for permission sets
- **Events**: Kafka for workspace membership changes
- **Quotas**: Enum-based tier definitions with configurable limits

## Domain Model

```
Workspace
|-- id: UUID
|-- name: String
|-- slug: String (unique, URL-friendly)
|-- tier: WorkspaceTier { FREE, PRO, ENTERPRISE }
|-- settings: JSONB { features, branding, integrations }
|-- quota: JSONB { max_members, max_canvases, storage_bytes }
|-- owner_id: UUID
|-- created_at, updated_at
+-- deleted_at (soft delete)

WorkspaceMember
|-- workspace_id: UUID FK
|-- user_id: UUID FK
|-- role: WorkspaceRole { OWNER, ADMIN, EDITOR, VIEWER, COMMENTER }
|-- invited_by: UUID
|-- joined_at: TIMESTAMPTZ
+-- PK(workspace_id, user_id)

RolePermission (lookup table)
|-- role: WorkspaceRole PK
|-- permission: String PK
+-- (mapping: OWNER -> all, ADMIN -> manage_members, EDITOR -> write_content, etc.)
```

## Permission Evaluation

```
Request -> Extract user_id + workspace_id from JWT/header
       -> Check Redis: hget workspace:{ws_id}:members {user_id}
         -> miss -> query PostgreSQL -> set Redis (5m TTL)
       -> Get role -> lookup RolePermission for action
       -> If action = 'canvas:write' and role >= EDITOR -> allow
       -> If action = 'workspace:delete' and role == OWNER -> allow
       -> Else -> 403 Forbidden
```

## Permission Matrix (simplified)

| Permission | Owner | Admin | Editor | Viewer | Commenter |
|---|---|---|---|---|---|
| `workspace:delete` | yes | - | - | - | - |
| `workspace:update_settings` | yes | yes | - | - | - |
| `workspace:manage_members` | yes | yes | - | - | - |
| `canvas:create` | yes | yes | yes | - | - |
| `canvas:edit` | yes | yes | yes | - | - |
| `canvas:view` | yes | yes | yes | yes | yes |
| `canvas:comment` | yes | yes | yes | - | yes |
| `dashboard:create` | yes | yes | yes | - | - |
| `dashboard:edit` | yes | yes | yes | - | - |
| `dashboard:view` | yes | yes | yes | yes | yes |
| `data_source:manage` | yes | yes | yes | - | - |

## Quota Enforcement

Quotas checked at command handler level before processing:

```
class QuotaEnforcementAspect {
    @Around("@annotation(CheckQuota)")
    Object enforce(ProceedingJoinPoint pjp) {
        workspace = getWorkspace(pjp)
        currentUsage = getUsage(workspace.id)
        tierLimits = TierLimits.for(workspace.tier)
        if (currentUsage >= tierLimits.maxCanvases) {
            throw new QuotaExceededException("Canvas limit reached for " + workspace.tier);
        }
        return pjp.proceed();
    }
}
```

## Events Published

| Event | Consumers |
|---|---|
| `WorkspaceCreated` | Audit log, Search (index workspace) |
| `WorkspaceSettingsUpdated` | Audit log |
| `WorkspaceMemberAdded` | Notifications (welcome), Audit log, Realtime (permission update) |
| `WorkspaceMemberRoleChanged` | Audit log, Realtime (disconnect if demoted) |
| `WorkspaceMemberRemoved` | Audit log, Realtime (kick from rooms), Search (remove user docs) |
| `WorkspaceDeleted` | Audit log, Search (delete index), All services (cleanup) |

## Performance Considerations

- Permission cache: Redis hash with 5-minute TTL, invalidated on member change
- Quota counts: Redis counters with PostgreSQL as source of truth, reconciled nightly
- Member list: Paginated query with cursor-based pagination for large workspaces (>1000 members)
- Slug uniqueness: Checked at application level + DB unique constraint + retry on collision
- Soft delete: Workspace has 30-day grace period before hard delete via background job
