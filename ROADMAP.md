# LiveLattice Roadmap

This roadmap tracks implementation status, next backend stages, and working rules for the repository. Detailed implementation instructions live in `docs/prompts/`; technical designs and diagrams live in `docs/techDesign/`.

## Current Released Milestones

| Milestone | Status | Commit | Tag | Notes |
|---|---|---:|---|---|
| Architecture documentation | Done | `664d036` | none | Architecture, tech design, flows, and implementation prompts. |
| Stage 1: Infrastructure & Docker Compose | Done | `6515286` | `v0.1.0-infra` | Local infra stack, observability stack, test compose, backend placeholders. |
| Stage 2: API Gateway | Done | `bc1535d` | `v0.2.0-gateway` | NestJS/Fastify gateway, health, readiness, metrics, proxying, rate limit, optional JWT boundary. |
| Stage 4: Core Domain - Workspaces & RBAC | Done | `b4edbca` | `v0.3.0-core` | Java 21 Spring Boot 4.1.0 service, workspace/member APIs, RBAC, quotas, Flyway migrations. |

## Stage Status

| Stage | Area | Status | Prompt | Tech Design |
|---:|---|---|---|---|
| 1 | Infrastructure & Docker Compose | Done | `docs/prompts/infra-compose.md` | `docs/techDesign/infra-compose/infra-compose-design.md` |
| 2 | API Gateway | Done | `docs/prompts/api-gateway.md` | `docs/techDesign/api-gateway/api-gateway-design.md` |
| 3 | Auth & Identity | Pending | `docs/prompts/auth-identity.md` | `docs/techDesign/auth-identity/auth-identity-design.md` |
| 4 | Core Domain - Workspaces & RBAC | Done | `docs/prompts/workspaces-rbac.md` | `docs/techDesign/workspaces-rbac/workspaces-rbac-design.md` |
| 5 | Core Domain - Canvas & Documents | Next | `docs/prompts/canvas-documents.md` | `docs/techDesign/canvas-documents/canvas-documents-design.md` |
| 6 | Core Domain - Dashboard & Analytics | Pending | `docs/prompts/dashboard-analytics.md` | `docs/techDesign/dashboard-analytics/dashboard-analytics-design.md` |
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
- `core`: real Java Spring Boot service image built from `core/`.

Remaining placeholder services in `compose.yaml`:

- `realtime`
- `search`
- `notifications`
- `import-export`
- `audit-log`
- `background-jobs`

## Recommended Next Work

1. Implement Stage 5: Core Domain - Canvas & Documents.
2. Add canvas, canvas snapshots, comments, and templates to the Java `core` service.
3. Keep all database schema changes forward-only through Flyway migrations.
4. Verify through Docker Compose, including gateway proxy paths under `/api/core/*`.
5. Commit and push only after tests, image build, Compose startup, and smoke checks pass.

Stage 3 Auth & Identity remains pending. Local backend currently uses `x-user-id` as the development identity boundary. Keycloak is available in Compose, but full JWT/identity integration is not yet implemented.

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
