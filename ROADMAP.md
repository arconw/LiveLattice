# LiveLattice Roadmap

This roadmap tracks implementation status, next backend stages, and working rules for the repository. Detailed implementation instructions live in `docs/prompts/`; technical designs and diagrams live in `docs/techDesign/`.

## Current Released Milestones

| Milestone | Status | Commit | Tag | Notes |
|---|---|---:|---|---|
| Architecture documentation | Done | `664d036` | none | Architecture, tech design, flows, and implementation prompts. |
| Stage 1: Infrastructure & Docker Compose | Done | `6515286` | `v0.1.0-infra` | Local infra stack, observability stack, test compose, backend placeholders. |
| Stage 2: API Gateway | Done | `bc1535d` | `v0.2.0-gateway` | NestJS/Fastify gateway, health, readiness, metrics, proxying, rate limit, optional JWT boundary. |
| Stage 3: Auth & Identity | Done | release commit | `v0.6.0-auth-identity` | Keycloak-backed auth, Gateway JWT boundary, Core user provisioning, workspace-scoped API keys, Docker Compose smoke verified. |
| Stage 4: Core Domain - Workspaces & RBAC | Done | `b4edbca` | `v0.3.0-core` | Java 21 Spring Boot 4.1.0 service, workspace/member APIs, RBAC, quotas, Flyway migrations. |
| Stage 5: Core Domain - Canvas & Documents | Done | `d33b36e` | `v0.4.0-canvas-documents` | Canvas CRUD, snapshots backed by MinIO, comments, templates, events, Docker Compose smoke verified. |
| Stage 6: Core Domain - Dashboard & Analytics | Done | `aa48406` | `v0.5.0-dashboard-analytics` | Dashboard CRUD, widgets, encrypted data sources, Redis query cache, ClickHouse init, Docker Compose smoke verified. |
| Stage 7: Realtime Collaboration | Done | `91b9f4d` | none | Realtime service with Socket.IO, Yjs collaboration, Redis room/presence state, Kafka persistence, and Docker Compose verification. |
| Stage 8: Import & Export | Done | `484beeb` | `v0.7.0-import-export` | Import/export service for SVG/draw.io/PNG/PDF/JSON/CSV/XLSX, async jobs, MinIO artifacts, and Docker Compose verification. |
| Stage 9: Search | Done | `7e0a092` | `v0.8.0-search` | OpenSearch-backed search service, Kafka indexing, Redis suggestions, Gateway protection, and Docker Compose verification. |
| Stage 10: Notifications | Done | release commit | `v0.9.0-notifications` | Notification service with Postgres storage, preferences, Kafka delivery, Redis support, Gateway protection, and Docker Compose verification. |

## Stage Status

| Stage | Area | Status | Prompt | Tech Design |
|---:|---|---|---|---|
| 1 | Infrastructure & Docker Compose | Done | `docs/prompts/infra-compose.md` | `docs/techDesign/infra-compose/infra-compose-design.md` |
| 2 | API Gateway | Done | `docs/prompts/api-gateway.md` | `docs/techDesign/api-gateway/api-gateway-design.md` |
| 3 | Auth & Identity | Done | `docs/prompts/auth-identity.md` | `docs/techDesign/auth-identity/auth-identity-design.md` |
| 4 | Core Domain - Workspaces & RBAC | Done | `docs/prompts/workspaces-rbac.md` | `docs/techDesign/workspaces-rbac/workspaces-rbac-design.md` |
| 5 | Core Domain - Canvas & Documents | Done | `docs/prompts/canvas-documents.md` | `docs/techDesign/canvas-documents/canvas-documents-design.md` |
| 6 | Core Domain - Dashboard & Analytics | Done | `docs/prompts/dashboard-analytics.md` | `docs/techDesign/dashboard-analytics/dashboard-analytics-design.md` |
| 7 | Realtime Collaboration | Done | `docs/prompts/realtime.md` | `docs/techDesign/realtime/realtime-design.md` |
| 8 | Import & Export | Done | `docs/prompts/import-export.md` | `docs/techDesign/import-export/import-export-design.md` |
| 9 | Search | Done | `docs/prompts/search.md` | `docs/techDesign/search/search-design.md` |
| 10 | Notifications | Done | `docs/prompts/notifications.md` | `docs/techDesign/notifications/notifications-design.md` |
| 11 | Audit Log | Pending | `docs/prompts/audit-log.md` | `docs/techDesign/audit-log/audit-log-design.md` |
| 12 | Background Jobs | Pending | `docs/prompts/background-jobs.md` | `docs/techDesign/background-jobs/background-jobs-design.md` |
| 13 | Health & Observability | Pending | `docs/prompts/health-observability.md` | `docs/techDesign/health-observability/health-observability-design.md` |
| 14 | Performance Testing | Pending | `docs/prompts/k6-performance.md` | `docs/techDesign/health-observability/health-observability-design.md` |

## Current Backend State

Implemented services:

- `gateway`: real service image built from `gateway/`, including the external JWT auth boundary, Keycloak login/refresh/logout, trusted identity header injection, API key proxying, protected Core proxy paths, and multipart form-data proxying to the import-export service.
- `core`: real Java Spring Boot service image built from `core/`, including workspace/RBAC, canvas/document APIs, dashboard/analytics APIs, authenticated user provisioning, and workspace-scoped API key lifecycle and enforcement.
- `realtime`: real Node 24 / TypeScript service image built from `realtime/`, including Socket.IO namespace auth through the Gateway JWT boundary, room membership via Redis, Yjs-based collaboration with snapshots and Kafka persistence, presence awareness, and cross-instance Redis pub/sub.
- `import-export`: real Java 21 / Spring Boot 4.1.0 service image built from `services/import-export/`, including sync and async canvas import/export (SVG, draw.io, PNG, PDF, JSON), dashboard CSV/XLSX/JSON export, batch jobs tracked in Redis with owner/workspace scoping, Kafka async events, MinIO artifact storage, and explicit Flyway migration wired through a service-specific history table (`import_export_flyway_history`) to coexist with the core schema migrations in the same Postgres database.
- `search`: real Java 21 / Spring Boot 4.1.0 service image built from `services/search/`, including OpenSearch-backed full-text search and suggestions, deterministic index initialization, Kafka-based bulk indexing, Redis suggestion caching, admin-gated reindex reset, Docker readiness over OpenSearch/Kafka/Redis/indexes, and protected Gateway routing for `/api/search/*`.
- `notifications`: real Java 21 / Spring Boot 4.1.0 service image built from `services/notifications/`, including Postgres-backed notification, preference, and delivery-attempt storage via forward-only Flyway migrations, Redis caching, Kafka event-driven delivery, internal-auth boundary, admin/service-gated creation through the Gateway, user-facing read/preference endpoints, and protected Gateway routing for `/api/notifications/*`.

Remaining placeholder services in `compose.yaml`:

- `audit-log`
- `background-jobs`

## Recommended Next Work

1. Pick up Stage 11: Audit Log for the next backend service.
2. Keep all database schema changes forward-only through Flyway migrations.
3. Verify through Docker Compose, including gateway auth paths, protected service proxy paths, and at least one permission or validation failure.
4. Commit and push only after tests, image build, Compose startup, smoke checks, diff checks, and docs review pass.

Stage 7 Realtime Collaboration is complete and verified.

Stage 8 Import & Export is complete and verified: the import-export image builds with unit tests in the Docker build path, the full Testcontainers suite for Postgres, Redis, Kafka, and MinIO passes, the Compose service starts healthy after Core, `/health` returns `UP`, `/ready` reports healthy storage, queue, and cache dependencies, the Gateway protects `/api/import-export/*` and returns 401 without a bearer token, SVG import/export plus unsupported-content validation are covered by integration tests, import/export operations enforce core workspace RBAC, async job state/download endpoints are scoped by job owner or workspace RBAC, and uploaded SVG/draw.io XML is parsed with DTD and external entity access disabled. Core Flyway migrations and import-export Flyway migrations are isolated using separate history tables.

Stage 9 Search is complete and verified: the search and core images build with unit tests in the Docker build path, Compose starts Core, Search, and Gateway healthy after OpenSearch, Kafka, Redis, and Postgres, `/health` returns `UP`, `/ready` reports healthy search, queue, cache, and index checks, six indexes are initialized deterministically with the LiveLattice OpenSearch ISM policy attached, Core publishes canvas/comment search events to Kafka after commits, Core-created canvases are consumed and bulk-indexed into OpenSearch, search returns highlighted/faceted results, suggestions are cached through Redis, validation returns 400 for invalid types, direct Search endpoints reject missing trusted Gateway identity with 401, trusted non-admin reindex returns 403, trusted admin reindex rebuilds Core-backed documents from PostgreSQL, and the Gateway protects `/api/search/*` with a 401 response when no bearer token is provided.

Stage 10 Notifications is complete and verified: the notifications image builds with unit tests in the Docker build path, Compose starts the real Java 21 / Spring Boot 4.1.0 service after Postgres, Redis, and Kafka, `/health` returns `UP`, `/ready` reports healthy database, cache, and queue dependencies, the Gateway protects `/api/notifications/*` and returns 401 without a bearer token or with an invalid token, direct notifications endpoints reject missing or incorrect internal auth with 401, validation returns 400 for empty recipients, POST `/notifications` through the Gateway requires an `admin` or `service` role so normal authenticated users receive 403, and read/preference endpoints are reachable for authenticated users.

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
