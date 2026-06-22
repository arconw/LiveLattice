# LiveLattice Roadmap

This roadmap tracks implementation status, next backend stages, and working rules for the repository. Detailed implementation instructions live in `docs/prompts/`; technical designs and diagrams live in `docs/techDesign/`.

## Current Released Milestones

| Milestone | Status | Commit | Tag | Notes |
|---|---|---:|---|---|
| Architecture documentation | Done | `664d036` | none | Architecture, tech design, flows, and implementation prompts. |
| Stage 1: Infrastructure & Docker Compose | Done | `6515286` | `v0.1.0-infra` | Local infra stack, observability stack, test compose, backend placeholders. |
| Stage 2: API Gateway | Done | `bc1535d` | `v0.2.0-gateway` | NestJS/Fastify gateway, health, readiness, metrics, proxying, rate limit, optional JWT boundary. |
| Stage 4: Core Domain - Workspaces & RBAC | Done | `b4edbca` | `v0.3.0-core` | Java 21 Spring Boot 4.1.0 service, workspace/member APIs, RBAC, quotas, Flyway migrations. |
| Stage 5: Core Domain - Canvas & Documents | Done | `d33b36e` | `v0.4.0-canvas-documents` | Canvas CRUD, snapshots backed by MinIO, comments, templates, events, Docker Compose smoke verified. |
| Stage 6: Core Domain - Dashboard & Analytics | Done | `aa48406` | `v0.5.0-dashboard-analytics` | Dashboard CRUD, widgets, encrypted data sources, Redis query cache, ClickHouse init, Docker Compose smoke verified. |

## Stage Status

| Stage | Area | Status | Prompt | Tech Design |
|---:|---|---|---|---|
| 1 | Infrastructure & Docker Compose | Done | `docs/prompts/infra-compose.md` | `docs/techDesign/infra-compose/infra-compose-design.md` |
| 2 | API Gateway | Done | `docs/prompts/api-gateway.md` | `docs/techDesign/api-gateway/api-gateway-design.md` |
| 3 | Auth & Identity | Next | `docs/prompts/auth-identity.md` | `docs/techDesign/auth-identity/auth-identity-design.md` |
| 4 | Core Domain - Workspaces & RBAC | Done | `docs/prompts/workspaces-rbac.md` | `docs/techDesign/workspaces-rbac/workspaces-rbac-design.md` |
| 5 | Core Domain - Canvas & Documents | Done | `docs/prompts/canvas-documents.md` | `docs/techDesign/canvas-documents/canvas-documents-design.md` |
| 6 | Core Domain - Dashboard & Analytics | Done | `docs/prompts/dashboard-analytics.md` | `docs/techDesign/dashboard-analytics/dashboard-analytics-design.md` |
| 7 | Realtime Collaboration | Pending | `docs/prompts/realtime.md` | `docs/techDesign/realtime/realtime-design.md` |
| 8 | Import & Export | Pending | `docs/prompts/import-export.md` | `docs/techDesign/import-export/import-export-design.md` |
| 9 | Search | Pending | `docs/prompts/search.md` | `docs/techDesign/search/search-design.md` |
| 10 | Notifications | Pending | `docs/prompts/notifications.md` | `docs/techDesign/notifications/notifications-design.md` |
| 11 | Audit Log | Pending | `docs/prompts/audit-log.md` | `docs/techDesign/audit-log/audit-log-design.md` |
| 12 | Background Jobs | Pending | `docs/prompts/background-jobs.md` | `docs/techDesign/background-jobs/background-jobs-design.md` |
| 13 | Health & Observability | Pending | `docs/prompts/health-observability.md` | `docs/techDesign/health-observability/health-observability-design.md` |
| 14 | Performance Testing | Pending | `docs/prompts/k6-performance.md` | `docs/techDesign/health-observability/health-observability-design.md` |

## Current Backend State

Implemented services:

- `gateway`: real service image built from `gateway/`.
- `core`: real Java Spring Boot service image built from `core/`, including workspace/RBAC, canvas/document APIs, and dashboard/analytics APIs.

Remaining placeholder services in `compose.yaml`:

- `realtime`
- `search`
- `notifications`
- `import-export`
- `audit-log`
- `background-jobs`

## Recommended Next Work

1. Implement Stage 3: Auth & Identity before Stage 7 Realtime Collaboration.
2. Use Keycloak as the identity source of truth and complete Gateway/Core auth integration.
3. Provision users through the auth/login path only, with no development fallback that creates users outside auth.
4. Implement workspace-scoped API keys unless later design review changes the scope.
5. Keep all database schema changes forward-only through Flyway migrations.
6. Verify through Docker Compose, including gateway auth paths and protected core proxy paths.
7. Commit and push only after tests, image build, Compose startup, and smoke checks pass.

Stage 3 Auth & Identity is next because Stage 7 Realtime Collaboration depends on JWT-based connection authentication. Keycloak is available in Compose, but full JWT/identity integration is not yet implemented. Stage 6 is complete and verified through direct core and gateway smoke checks.

## Auth Implementation Decisions

- Keycloak is the identity source of truth. Application `users.external_subject` must map to the Keycloak subject (`sub`).
- User provisioning must happen through the auth/login flow only, including local development.
- Gateway is the external auth boundary and should pass trusted internal identity headers to backend services.
- Core keeps domain RBAC, API key validation, and a `UserService` for idempotent user provisioning from authenticated Keycloak claims.
- Direct Core access after Auth must be closed by default.
- Stage 7 Realtime Collaboration must be implemented after Stage 3 so WebSocket JWT authentication can be real.
- Realm export is required for deterministic local Keycloak setup.
- API keys should be workspace-scoped service tokens for the MVP.
- MFA and social login are backend/auth capabilities in this stage. Frontend UX is out of scope.

## Verification Gate for Every Functional Stage

A stage is considered done only when all relevant checks pass:

```bash
docker compose config
docker compose build <service>
docker compose up -d <service>
docker compose ps
```

Service-specific checks must also include:

- Unit or integration tests inside the Docker build path.
- `/health` endpoint returns `UP`.
- `/ready` endpoint returns expected readiness data.
- Gateway proxy path works when the service is behind `gateway`.
- Smoke checks cover at least one successful flow and one permission or validation failure when applicable.

## Working Rules

- Backend and infrastructure only unless explicitly requested otherwise.
- Docker Compose is the local execution path.
- Documentation and prompts must be written in English.
- Do not leave comments in code files.
- Do not commit broken or partially verified stages.
- Tags are for functional milestones only, not documentation-only changes.
- Codex and OpenCode must be used only through MCP harness sessions, not through shell CLI commands.
- OpenCode may produce draft implementations; Codex should review where available. Final edits, tests, commits, tags, and pushes remain manually controlled.

## Agent Usage Pattern

Preferred sequence for complex backend work:

1. Create a self-contained task file in `.agent-tasks/` when delegation is useful.
2. Run OpenCode through MCP harness for a draft implementation.
3. Run Codex through MCP harness for review when available.
4. Manually inspect diffs and remove any code comments or local artifacts.
5. Run Docker Compose verification.
6. Commit, push, and tag only after the stage is functionally green.
