# Auth & Identity - Technical Design

This document describes the implemented Stage 3 backend and infrastructure slice. The implementation uses Keycloak as the identity source of truth, Gateway as the public auth boundary, and Core as the domain authorization and API key authority.

## Implemented Versions

| Component | Version or path |
|---|---|
| Keycloak | `quay.io/keycloak/keycloak:26.4.4` in `compose.yaml` |
| Gateway | NestJS/Fastify from `gateway/` |
| Core | Java 21 Spring Boot 4.1.0 from `core/` |
| Realm export | `infra/keycloak/livelattice-realm.json` mounted at `/opt/keycloak/data/import` |
| PostgreSQL | `postgres:18.1-alpine` in Compose |
| Redis | `redis:8.4-alpine` in Compose |

## Responsibility Split

| Area | Owner | Implemented behavior |
|---|---|---|
| Public OIDC login, refresh, logout | Gateway | Exchanges credentials and refresh tokens with Keycloak token endpoints and returns normalized token responses. |
| JWT validation | Gateway | Validates bearer tokens using Keycloak RS256 JWKS and caches JWKS responses. |
| Trusted backend identity | Gateway | Removes client-supplied trusted headers and injects `x-internal-auth-token`, `x-auth-subject`, `x-auth-email`, `x-auth-display-name`, `x-auth-roles`, and `x-user-id` after token validation. |
| User provisioning | Core | Exposes `POST /internal/auth/users/provision` for Gateway login/refresh provisioning only. Normal trusted requests require the user to already exist. |
| Domain RBAC | Core | Existing workspace membership and role permission checks remain authoritative. |
| API keys | Core | Creates, lists, revokes, validates, hashes, caches, and scopes workspace service tokens. |
| Direct Core access | Core | Closed by `AuthBoundaryFilter` unless the request has a trusted internal token or a valid `X-API-Key`. |

## Gateway Auth Flow

```
Client -> Gateway POST /auth/login { email, password }
Gateway -> Keycloak token endpoint using password grant
Keycloak -> Gateway access_token, refresh_token, id_token, TTLs
Gateway -> Keycloak JWKS endpoint on cache miss
Gateway validates access_token and extracts sub/email/name/realm roles
Gateway -> Core POST /internal/auth/users/provision with trusted internal headers
Gateway -> Client { accessToken, refreshToken, idToken, expiresIn, refreshExpiresIn, tokenType, scope, user }
```

Gateway configuration is environment-driven:

| Variable | Purpose | Default in Compose |
|---|---|---|
| `AUTH_REQUIRED` | Protect Core proxy routes and auth key routes | `true` |
| `AUTH_ISSUER` | Keycloak realm issuer | `http://keycloak:8080/realms/livelattice` |
| `AUTH_JWKS_URI` | Keycloak JWKS endpoint | Keycloak realm certs endpoint |
| `AUTH_TOKEN_ENDPOINT` | Keycloak token endpoint | Keycloak realm token endpoint |
| `AUTH_LOGOUT_ENDPOINT` | Keycloak logout endpoint | Keycloak realm logout endpoint |
| `AUTH_CLIENT_ID` | Public login client | `livelattice-web` |
| `CORE_PROVISION_URL` | Core internal provisioning endpoint | `http://core:8080/internal/auth/users/provision` |
| `INTERNAL_AUTH_SECRET` | Shared internal Gateway/Core trust token | development fallback only |
| `SESSION_CACHE_ENABLED` | Enables Redis-backed Gateway auth cache | `true` |
| `REDIS_HOST` / `REDIS_PORT` | Redis cache location | `redis:6379` |

## Gateway Endpoints

| Method | Path | Implemented behavior |
|---|---|---|
| `POST` | `/auth/login` | Exchanges email/password with Keycloak, validates returned access token, provisions user in Core, returns tokens and user claims. |
| `POST` | `/auth/refresh` | Exchanges refresh token with Keycloak, validates returned access token, refreshes Core provisioning metadata. |
| `POST` | `/auth/logout` | Sends refresh token to Keycloak logout endpoint. |
| `POST` | `/auth/social` | Returns a Keycloak broker initiation contract for a provider and redirect URI. |
| `POST` | `/auth/mfa/setup` | Returns a backend contract indicating Keycloak TOTP support. |
| `POST` | `/auth/mfa/verify` | Returns a backend contract indicating verification is delegated to Keycloak. |
| `GET/POST/DELETE` | `/auth/keys...` | Proxies authenticated requests to Core API key endpoints with trusted identity headers. |
| `ALL` | `/api/core...` | Requires valid bearer token unless `X-API-Key` is present, then proxies to Core for API key validation. |

## Core Auth Boundary

Core uses `AuthBoundaryFilter` for every request:

- `/health` and `/ready` are public.
- Requests with `X-API-Key` are validated by Core `ApiKeyService`.
- Requests without `X-API-Key` must include the trusted internal token from Gateway.
- The internal provisioning endpoint accepts trusted Gateway claims and idempotently creates or updates users.
- Normal trusted requests do not provision users; they require the Keycloak subject to already exist in `users.external_subject`.
- Controllers continue to receive `x-user-id` as the external subject. Domain services resolve it to the internal user UUID before RBAC checks.

## User Provisioning

`users.external_subject` maps to the Keycloak `sub` claim. `UserService.provision` is idempotent and updates email, display name, status, and timestamp for an existing subject. New users can be created only through the internal provisioning endpoint, which Gateway calls during login and refresh flows.

Workspace and member flows no longer create fallback local users. Adding a workspace member requires that the target user already exists from an auth flow.

## API Key Flow

```
Authenticated owner/admin -> Gateway POST /auth/keys
Gateway -> Core /auth/keys with trusted user headers
Core verifies requester RBAC on workspace
Core creates workspace-scoped key, returns plaintext token once, stores bcrypt hash only
Client -> Gateway /api/core/... with X-API-Key
Gateway forwards X-API-Key without trusted identity headers
Core validates token hash/status/expiry and loads cached metadata when available
Core sets request identity to the key creator and restricts RBAC by key workspace and permissions
```

## API Key Storage and Token Format

`V9__auth_identity_api_keys.sql` creates `api_keys` with UUID foreign keys to `workspaces` and `users`:

- `workspace_id`
- `creator_id`
- `name`
- `token_hash`
- comma-separated `permissions`
- `status`
- `last_used_at`
- optional `expires_at`
- `created_at`
- `revoked_at`

The plaintext token is returned only on creation. The token has the form:

```
ll.<base64url-key-id>.<base64url-32-random-bytes>
```

Only a bcrypt hash is stored. Validation decodes the key id, verifies the bcrypt hash, checks active status and expiration, updates `last_used_at`, and caches validated metadata in Redis for 5 minutes.

API key authorization requires all of the following:

1. The token is valid, active, and not expired.
2. The requested workspace matches the token workspace.
3. The requested permission is included in the token scope.
4. The creator still has the corresponding workspace RBAC permission.

## Core API Key Endpoints

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/auth/keys` | Creates a workspace-scoped service token. Body: `workspaceId`, `name`, `permissions`, optional `expiresAt`. |
| `GET` | `/auth/keys?workspaceId=...` | Lists metadata only. Plaintext tokens are never returned after creation. |
| `DELETE` | `/auth/keys/{workspaceId}/{keyId}` | Revokes a token for the workspace. |

## Realm Export

The current realm export is deterministic for local development and includes:

- realm `livelattice`
- public client `livelattice-web` with standard flow and direct access grants enabled for local smoke tests
- confidential service client `livelattice-gateway`
- development owner user
- realm roles for owner/admin/editor/viewer

Social identity providers and advanced MFA enrollment are represented by Gateway backend contracts in this slice. Provider credentials and frontend UX remain out of scope for this implementation.

## Verification

Implemented automated checks:

```bash
cd gateway && npm test
cd core && gradle test --no-daemon
```

Docker Compose remains the required local execution path:

```bash
docker compose config
docker compose build gateway core
docker compose up -d postgres redis keycloak core gateway
curl http://localhost:3000/health
curl http://localhost:3000/ready
curl http://localhost:8080/health
curl http://localhost:8080/ready
```

Stage completion requires a Compose smoke covering login, protected Core rejection without credentials, protected Core success with bearer token, user provisioning, API key creation, API key usage, and revoked or invalid API key rejection.
