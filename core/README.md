# Core Domain

Spring Boot backend for authenticated users, workspace RBAC, API keys, and domain entities.

## Implemented Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /health | Service health (status, service name, version) |
| GET | /ready | Readiness check (database connectivity) |
| POST | /internal/auth/users/provision | Idempotently provision authenticated Keycloak claims from Gateway |
| POST | /auth/keys | Create a workspace-scoped service token |
| GET | /auth/keys?workspaceId={id} | List API key metadata |
| DELETE | /auth/keys/{workspaceId}/{keyId} | Revoke an API key |
| POST | /workspaces | Create a workspace for an already provisioned user |
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

## Auth Boundary

- `/health` and `/ready` are public.
- Domain requests require either trusted Gateway headers or a valid `X-API-Key`.
- User records are provisioned only through the internal auth endpoint.
- Workspace flows no longer create fallback local users.
- API keys are workspace-scoped, hashed at rest, returned only once, and restricted by both token scope and creator RBAC.

## Quotas

- FREE tier: max 5 members, 10 canvases
- PRO tier: max 50 members, 500 canvases
