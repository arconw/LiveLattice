# LiveLattice Roadmap

This roadmap tracks implementation status, next backend/frontend stages, and working rules for the repository. Detailed implementation instructions live in `docs/prompts/`; technical designs and diagrams live in `docs/techDesign/`.

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
| Stage 11: Audit Log | Done | release commit | `v0.10.0-audit-log` | Audit-log service with immutable SHA-256 hash-chained events, Kafka ingestion, partitioned Postgres storage, workspace-scoped query/export APIs, admin-gated verify/retention, internal-auth boundary, and Docker Compose verification. |
| Stage 12: Background Jobs | Done | release commit | `v0.11.0-background-jobs` | Background job orchestration with Postgres/Redis state, worker pools, retry/dead-letter handling, scheduled maintenance jobs, Import/Export delegation, internal-auth boundary, and Docker Compose verification. |
| Stage 13: Health & Observability | Done | release commit | `v0.12.0-health-observability` | Normalized health/readiness contracts, Prometheus metrics and alerts, structured logs, OpenTelemetry traces, Loki/Tempo/Jaeger wiring, Grafana dashboards, and Docker Compose verification. |
| Stage 14: Performance Testing | Done | release commit | `v0.13.0-performance-testing` | k6 smoke/load coverage for backend services, websocket reconnects, Kafka lag sampling, shared helpers/options, Compose execution, summary exports, and baseline documentation. |
| Stage 15: Frontend Application | Done | release commit | `v0.14.0-frontend` | React/Vite frontend shell with auth, workspaces, canvas, dashboards, search, jobs, notifications, audit, typed API contracts, accessibility coverage, and Docker Compose integration. |

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
| 11 | Audit Log | Done | `docs/prompts/audit-log.md` | `docs/techDesign/audit-log/audit-log-design.md` |
| 12 | Background Jobs | Done | `docs/prompts/background-jobs.md` | `docs/techDesign/background-jobs/background-jobs-design.md` |
| 13 | Health & Observability | Done | `docs/prompts/health-observability.md` | `docs/techDesign/health-observability/health-observability-design.md` |
| 14 | Performance Testing | Done | `docs/prompts/k6-performance.md` | `docs/techDesign/health-observability/health-observability-design.md` |
| 15 | Frontend Application | Done | `docs/prompts/frontend-*.md` | `docs/techDesign/frontend/frontend-design.md` |

## Current Backend State

Implemented services:

- `gateway`: real service image built from `gateway/`, including the external JWT auth boundary, Keycloak login/refresh/logout, MFA delegation endpoints, trusted identity header injection, auth audit event publication with actor/target metadata, API key proxying, protected Core proxy paths, and multipart form-data proxying to the import-export service.
- `core`: real Java Spring Boot service image built from `core/`, including workspace/RBAC, canvas/document APIs, dashboard/analytics APIs, authenticated user provisioning, and workspace-scoped API key lifecycle and enforcement.
- `realtime`: real Node 24 / TypeScript service image built from `realtime/`, including Socket.IO namespace auth through the Gateway JWT boundary, room membership via Redis, Yjs-based collaboration with snapshots and Kafka persistence, presence awareness, and cross-instance Redis pub/sub.
- `import-export`: real Java 21 / Spring Boot 4.1.0 service image built from `services/import-export/`, including sync and async canvas import/export (SVG, draw.io, PNG, PDF, JSON), dashboard CSV/XLSX/JSON export, batch jobs tracked in Redis with owner/workspace scoping, Kafka async events, audit publication for completed canvas import/export actions, MinIO artifact storage, and explicit Flyway migration wired through a service-specific history table (`import_export_flyway_history`) to coexist with the core schema migrations in the same Postgres database.
- `search`: real Java 21 / Spring Boot 4.1.0 service image built from `services/search/`, including OpenSearch-backed full-text search and suggestions, deterministic index initialization, Kafka-based bulk indexing, Redis suggestion caching, admin-gated reindex reset, Docker readiness over OpenSearch/Kafka/Redis/indexes, and protected Gateway routing for `/api/search/*`.
- `notifications`: real Java 21 / Spring Boot 4.1.0 service image built from `services/notifications/`, including Postgres-backed notification, preference, and delivery-attempt storage via forward-only Flyway migrations, Redis caching, Kafka event-driven delivery, internal-auth boundary, admin/service-gated creation through the Gateway, user-facing read/preference endpoints, and protected Gateway routing for `/api/notifications/*`.
- `audit-log`: real Java 21 / Spring Boot 4.1.0 service image built from `services/audit-log/`, including immutable SHA-256 hash-chained audit events, Kafka ingestion of Core domain, import-export canvas, and Gateway auth events on the `livelattice.audit.events` topic, partitioned Postgres storage with forward-only Flyway migrations and ingest-time monthly partition creation, event-time preservation through `occurred_at`, append metadata through `ingested_at`, retention-aware hash-chain verification across hot Postgres rows and archived Parquet links with batch-sized hot/archive reads, V6 repair of backfilled `ingested_at` ordering from stored hash-chain links, workspace-membership scoped query/get/export APIs, exact admin role gates for global verify and retention, async CSV/Parquet export to MinIO, nightly partition retention with Parquet archive to MinIO (partition dropped only after the whole monthly partition is past the hot retention window), internal-auth boundary, Core and MinIO readiness dependencies in Compose, and protected Gateway routing for `/api/audit-log/*`.
- `background-jobs`: real Java 21 / Spring Boot 4.1.0 service image built from `services/background-jobs/`, including PostgreSQL-backed `JobDefinition`, `JobExecution`, and `DeadLetter` entities with forward-only Flyway migrations isolated in `background_jobs_flyway_history`, Redis-backed job queues and progress tracking, scoped Import/Export internal async API delegation with downstream terminal polling, per-type worker pools with configurable concurrency, scheduled cron jobs, retry with exponential backoff, cancellation, dead-letter retry, Prometheus dead-letter metric, internal-auth boundary, Redis/PostgreSQL Testcontainers coverage, and Docker Compose verification.

Stage 13 Health & Observability is implemented across the real backend services with normalized `/health` and `/ready` contracts, Prometheus metrics, request id propagation, structured JSON logs, OpenTelemetry trace export, Docker healthchecks, Prometheus alert rules, Loki/Promtail log ingestion, Tempo/Jaeger tracing, and Grafana dashboards for service, Kafka, database, websocket, and business metrics.

Stage 14 Performance Testing is implemented under `k6/` with Docker Compose execution, shared k6 options/helpers, public smoke coverage for Gateway, Core, Realtime, Search, Notifications, Import/Export, Audit Log, and Background Jobs, auth-boundary failure checks, optional authenticated and API-key-aware scenarios driven by environment variables, Prometheus-backed Kafka lag sampling, per-script summary exports, and baseline documentation.

## Current Frontend State

Implemented application:

- `frontend`: real React 19 / Vite / TypeScript application built from `frontend/`, including authenticated shell and route boundaries, workspace context, lattice cockpit, command palette, canvas editor with realtime adapter, dashboard/data-source surfaces, search, jobs, notifications, audit, service health and activity views, typed Gateway-relative API contracts, fixture-backed contract tests, accessibility and route smoke coverage, nginx runtime proxying for Gateway-relative browser calls, and Docker Compose wiring through the `frontend` service.

## Recommended Next Work

1. Keep `K6_PROFILE=smoke bash k6/run-all.sh` as the backend performance smoke gate before heavier baselines.
2. Keep all database schema changes forward-only through Flyway migrations.
3. Verify through Docker Compose, including gateway auth paths, protected service proxy paths, and at least one permission or validation failure when applicable.
4. Commit and push only after tests, image build, Compose startup, smoke checks, diff checks, and docs review pass.

Stage 7 Realtime Collaboration is complete and verified.

Stage 8 Import & Export is complete and verified: the import-export image builds with unit tests in the Docker build path, the full Testcontainers suite for Postgres, Redis, Kafka, and MinIO passes, the Compose service starts healthy after Core, `/health` returns `UP`, `/ready` reports healthy storage, queue, and cache dependencies, the Gateway protects `/api/import-export/*` and returns 401 without a bearer token, SVG import/export plus unsupported-content validation are covered by integration tests, import/export operations enforce core workspace RBAC, async job state/download endpoints are scoped by job owner or workspace RBAC, and uploaded SVG/draw.io XML is parsed with DTD and external entity access disabled. Core Flyway migrations and import-export Flyway migrations are isolated using separate history tables.

Stage 9 Search is complete and verified: the search and core images build with unit tests in the Docker build path, Compose starts Core, Search, and Gateway healthy after OpenSearch, Kafka, Redis, and Postgres, `/health` returns `UP`, `/ready` reports healthy search, queue, cache, and index checks, six indexes are initialized deterministically with the LiveLattice OpenSearch ISM policy attached, Core publishes canvas/comment search events to Kafka after commits, Core-created canvases are consumed and bulk-indexed into OpenSearch, search returns highlighted/faceted results, suggestions are cached through Redis, validation returns 400 for invalid types, direct Search endpoints reject missing trusted Gateway identity with 401, trusted non-admin reindex returns 403, trusted admin reindex rebuilds Core-backed documents from PostgreSQL, and the Gateway protects `/api/search/*` with a 401 response when no bearer token is provided.

Stage 10 Notifications is complete and verified: the notifications image builds with unit tests in the Docker build path, Compose starts the real Java 21 / Spring Boot 4.1.0 service after Postgres, Redis, and Kafka, `/health` returns `UP`, `/ready` reports healthy database, cache, and queue dependencies, the Gateway protects `/api/notifications/*` and returns 401 without a bearer token or with an invalid token, direct notifications endpoints reject missing or incorrect internal auth with 401, validation returns 400 for empty recipients, POST `/notifications` through the Gateway requires an `admin` or `service` role so normal authenticated users receive 403, and read/preference endpoints are reachable for authenticated users.

Stage 11 Audit Log is complete and verified: the audit-log image builds from `services/audit-log/` with unit tests in the Docker build path, Compose starts the real Java 21 / Spring Boot 4.1.0 service after Core, Postgres, Redis, Kafka, and MinIO, `/health` returns `UP`, `/ready` reports healthy database, cache, queue, and storage dependencies, the Gateway protects `/api/audit-log/*` and `/auth/logout` with 401 without a bearer token, Gateway Compose wiring provides `KAFKA_BROKERS=kafka:9092` and `KAFKA_AUDIT_TOPIC=livelattice.audit.events` for auth audit publication, direct endpoints reject missing or incorrect internal auth with 401, Core publishes domain audit events including `canvas.restore`, dashboard duplicate `dashboard.create`, and widget mutation `dashboard.update`, import-export publishes completed `canvas.import` and `canvas.export` events, and Gateway publishes `auth.login`, `auth.logout`, `auth.refresh`, `auth.mfa_enable`, and `auth.mfa_disable` events to the `livelattice.audit.events` Kafka topic with actor and target metadata preserved, the audit-log consumer ingests them with the documented dotted action names (e.g., `canvas.create`, `canvas.restore`, `canvas.import`, `canvas.export`, `dashboard.update`, `workspace.create`, `auth.login`) and prefers explicit `targetId` over event id, query by workspace/action/actor/target/date range returns paginated results, non-admin audit reads/exports require Core workspace membership while global verify/retention require an exact `admin` role, incoming historical event timestamps are preserved in `occurred_at`, the target monthly partition is created before insert so backfilled events do not fall into `audit_events_default`, retention-aware hash-chain verification reconstructs the canonical chain across hot Postgres rows and archived Parquet links and honors the requested `batchSize` for hot/archive reads, V6 repairs legacy backfilled `ingested_at` order from the existing hash-chain links, validation returns 400 for unsupported export formats, async export accepts CSV/Parquet requests and uploads artifacts to MinIO, and the nightly retention job archives expired monthly partitions to Parquet in the `audit-archive` MinIO bucket only after the partition end is past the hot retention window before dropping them from Postgres. The latest smoke verified Core dashboard/widget actions are queryable in audit-log and `/audit-log/verify?batchSize=2` remains valid.

Stage 12 Background Jobs is complete and verified: the background-jobs image builds from `services/background-jobs/` with unit tests in the Docker build path, Redis/PostgreSQL Testcontainers integration coverage passes for queue/dequeue, worker ownership, requeue, and scheduled handler SQL, Compose starts the real Java 21 / Spring Boot 4.1.0 service after Postgres, Redis, Kafka, and import-export, `/health` returns `UP`, `/ready` reports healthy cache and queue dependencies, the Gateway protects `/api/background-jobs/*` and injects trusted internal identity headers so authenticated job APIs reach the service, direct endpoints reject missing or incorrect internal auth with 401, validation returns 400 for unsupported job types, a queued NOOP job is consumed from the Redis queue and processed to `SUCCESS` with Redis progress updates, a job is cancelled by its owner and read/cancel/dead-letter retry are forbidden for other users, dead-letter retry requeues by `job_definition_id`, retry polling atomically claims retryable jobs after acquiring worker capacity so unavailable capacity does not strand jobs in `RUNNING`, graceful shutdown requeues only jobs owned by the shutting-down `worker_id` (added via forward-only V2 migration and index), Redis-popped and database-fallback claims assign `worker_id`, scheduled maintenance handlers now perform concrete workspace cleanup, snapshot compaction, partition maintenance, quota reconciliation, temp file cleanup, digest grouping, webhook retry, and index sync work where the backing schemas are present, import/export delegation initializes scoped import-export job state through `/internal/export/async` and `/internal/import/async` and polls downstream terminal status, and the service uses an isolated Flyway history table to coexist with the core schema.

Stage 13 Health & Observability is complete and verified: Gateway, Realtime, Core, Search, Notifications, Import/Export, Audit Log, and Background Jobs expose normalized `/health` and `/ready` responses, readiness checks preserve existing compatibility fields while adding structured check details, Spring services expose `/actuator/prometheus`, Gateway and Realtime expose normalized Prometheus metrics, backend logs include propagated `x-request-id` and JSON fields, OpenTelemetry Java/Node instrumentation exports sampled traces to the collector, Prometheus scrapes all backend and observability targets with alert rules loaded, Grafana provisions Prometheus/Loki/Tempo datasources and dashboards, Promtail ships container logs to Loki, Tempo and Jaeger receive traces through the collector, Compose healthchecks are wired for backend and observability services, and Docker Compose verification passed for config, builds, startup, readiness, metrics, logs, traces, and protected Gateway 401 smoke checks.

Stage 14 Performance Testing is complete and verified: the k6 suite includes shared options/helpers, a Compose runner, smoke/load scripts for Gateway/Core REST, dashboard queries, Realtime reachability and websocket reconnect behavior, Import/Export, Search, Notifications, Audit Log, Background Jobs, and Kafka lag sampling through Prometheus, with local Docker Compose endpoint configuration and per-script JSON summary exports. The safe smoke profile passed on 2026-06-24 with the backend and observability Compose stacks healthy: all 11 k6 scripts completed, checks were 100%, unexpected response rate was 0, `http_req_failed` was 0, websocket reconnect and Kafka lag thresholds passed, and summaries were written under `k6/reports/`. Authenticated and API-key-aware scenarios are implemented and run when `K6_AUTH_USERNAME` plus `K6_AUTH_PASSWORD`, `K6_AUTH_TOKEN`, or `K6_WORKSPACE_ID` are supplied; the verified local smoke run did not supply auth env vars, so those destructive setup paths were skipped.

Stage 15 Frontend Application is complete and verified: the frontend image builds from `frontend/`, the React application exposes the authenticated LiveLattice shell, workspace navigation, lattice cockpit, canvas editor, dashboard/data-source management, search, jobs, notifications, audit, activity, and service health routes, browser API calls stay Gateway-relative, realtime configuration is supplied through a browser-safe `VITE_REALTIME_URL`, contract fixtures cover backend API shapes, and the quality gate covers type checking, linting, unit tests, route smoke tests, keyboard/accessibility checks, responsive behavior, reduced-motion handling, rollback/failure states, and Docker Compose startup behind nginx.

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
