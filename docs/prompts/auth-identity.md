# Stage 3: Auth & Identity

## Objective

Implement the intended Auth & Identity stage for LiveLattice backend and infrastructure. Keycloak is the identity source of truth, Gateway is the public auth boundary, and Core owns domain RBAC, idempotent user provisioning from authenticated claims, and workspace-scoped service API keys.

## Version and Path Targets

- Keycloak image: `quay.io/keycloak/keycloak:26.4.4`
- Realm name: `livelattice`
- Realm export path: `infra/keycloak/livelattice-realm.json`
- Realm import mount: `./infra/keycloak:/opt/keycloak/data/import:ro`
- Gateway service: `gateway/`, NestJS/Fastify
- Core service: `core/`, Java 21 Spring Boot 4.1.0
- Local runtime: Docker Compose only

## Intended Requirements

1. Keycloak local identity setup:
   - Use Keycloak 26.4.4 with PostgreSQL backing store.
   - Import a deterministic `livelattice` realm from the versioned realm export.
   - Include clients for browser login and backend service integration.
   - Include realm roles and deterministic development users for smoke tests.
   - Keep production secrets out of git. Development-only values must be clearly local and replaceable through environment variables where supported.
2. Gateway auth boundary:
   - Implement `POST /auth/login` for Keycloak OIDC token exchange.
   - Implement `POST /auth/refresh` for refresh token rotation.
   - Implement `POST /auth/logout` for Keycloak session logout.
   - Implement backend contracts for social login initiation.
   - Implement backend contracts for MFA setup/verification delegated to Keycloak where full frontend UX is unavailable.
   - Validate bearer JWTs with Keycloak JWKS.
   - Cache JWKS/session data through Redis when enabled.
   - Protect `/api/core/*` after auth.
   - Strip client-supplied trusted internal headers and inject trusted identity headers only after validation.
   - Allow `X-API-Key` on `/api/core/*` to pass to Core for service token validation.
3. Core user provisioning:
   - Store `users.external_subject` as the Keycloak `sub` claim.
   - Provision users idempotently only through the Gateway login/refresh auth flow via an internal Core endpoint.
   - Do not keep local fallback user creation in domain flows.
   - Normal trusted domain requests must require the user to already exist.
4. Core auth boundary:
   - Close direct Core access by default.
   - Keep `/health` and `/ready` public.
   - Require either the trusted Gateway internal token or a valid `X-API-Key` for domain requests.
   - Keep public OIDC login logic out of Core.
5. API keys:
   - Implement workspace-scoped service tokens created and revoked by OWNER/ADMIN users.
   - Generate 32 random bytes for each token secret.
   - Return plaintext only once on creation.
   - Store only bcrypt or Argon2 hashes in PostgreSQL.
   - Store workspace id, creator id, scoped permissions, status, last used timestamp, optional expiration, creation timestamp, and revocation timestamp.
   - Validate `X-API-Key` in Core.
   - Cache validated key metadata in Redis for 5 minutes.
   - Enforce token workspace scope and token permissions in addition to creator RBAC.
6. Tests:
   - Gateway tests for login, JWKS validation, provisioning call, proxy protection, API key pass-through, and health/readiness.
   - Core tests for UserService, auth boundary behavior, API key creation/validation/revocation, and permission scoping.
   - Docker build paths must run relevant tests.
7. Docker Compose verification:
   - `docker compose config`
   - `docker compose build gateway core`
   - `docker compose up -d postgres redis keycloak core gateway`
   - Gateway `/health` and `/ready`
   - Core `/health` and `/ready`
   - login through Gateway with a seeded Keycloak user
   - unauthenticated protected Core proxy request rejected
   - valid bearer token protected Core proxy request accepted
   - user provisioning verified after login
   - API key create/list/revoke verified
   - valid API key request accepted
   - invalid or revoked API key request rejected

## Constraints

- Backend and infrastructure only.
- Do not implement frontend code.
- Docker Compose is the mandatory local execution path.
- Do not leave comments or TODOs in code files.
- Do not commit, tag, or push until tests, Docker Compose verification, smoke checks, diff checks, and final docs review are green.
- Use forward-only Flyway migrations. Do not rewrite old applied migrations.
- New migrations must use UUID-compatible foreign keys for the Compose schema.
- Codex and OpenCode must be used only through MCP harnesses, not direct shell CLIs.

## Documentation Rule

Implementation docs describe what is actually implemented. Prompts describe the intended implementation target. Before commit and push, verify the final implementation against both this prompt and the auth technical design.
