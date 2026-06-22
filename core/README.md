# Core Domain

Spring Boot backend for workspaces, RBAC, and domain entities.

## Implemented Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /health | Service health (status, service name, version) |
| GET | /ready | Readiness check (database connectivity) |
| POST | /workspaces | Create a workspace (materializes user if needed) |
| GET | /workspaces | List user's workspaces |
| GET | /workspaces/{id} | Get workspace details |
| PATCH | /workspaces/{id} | Update workspace (owner/admin) |
| DELETE | /workspaces/{id} | Delete workspace (owner only) |
| POST | /workspaces/{id}/members | Add member (owner/admin) |
| GET | /workspaces/{id}/members | List workspace members |
| PATCH | /workspaces/{id}/members/{userId}/role | Change member role (owner/admin) |
| DELETE | /workspaces/{id}/members/{userId} | Remove member (owner/admin) |

## Local Commands

```bash
docker compose build core
docker compose up -d core
docker compose exec core wget -qO- 127.0.0.1:8080/health
```

## Permissions

- OWNER: full access including delete workspace
- ADMIN: manage members and workspace settings
- EDITOR: create and edit canvases
- VIEWER: read-only access

## Quotas

- FREE tier: max 5 members, 10 canvases
- PRO tier: max 50 members, 500 canvases
