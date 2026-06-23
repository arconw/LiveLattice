# Implementation Prompts

Each prompt is a self-contained implementation stage for one domain of the LiveLattice system. Prompts are designed to be fed to an AI coding assistant or followed by a developer.

## How to Use

1. Run `docker compose up` to start all infrastructure dependencies (PostgreSQL, Redis, Kafka, etc.)
2. Open the target service directory in your editor
3. Feed the prompt to your AI assistant or implement manually
4. Run the relevant tests after each stage completes

## Conventions

- **Do not implement frontend code** - applies to backend/infra prompts only; frontend prompts are the explicit exception
- **Do not commit changes** - prompts leave version control decisions to the developer
- **Do not leave comments in code** - code should be self-documenting; use descriptive naming
- **Docker Compose is the local execution path** - all services must run via `docker compose up`
- **Run relevant tests** - each prompt specifies which tests to run and verify

## Prompt Stages

| # | Domain | Service | Dependencies |
|---|---|---|---|
| 1 | [Infrastructure & Docker Compose](infra-compose.md) | - | None (sets up everything) |
| 2 | [API Gateway](api-gateway.md) | `gateway/` | Infrastructure |
| 3 | [Auth & Identity](auth-identity.md) | `gateway/` + Keycloak | Infrastructure |
| 4 | [Core Domain - Workspaces & RBAC](workspaces-rbac.md) | `core/` | Infrastructure |
| 5 | [Core Domain - Canvas & Documents](canvas-documents.md) | `core/` | Stage 4 |
| 6 | [Core Domain - Dashboard & Analytics](dashboard-analytics.md) | `core/` | Stage 4 |
| 7 | [Realtime Collaboration](realtime.md) | `realtime/` | Stage 5 |
| 8 | [Import & Export](import-export.md) | `services/import-export/` | Stage 5 |
| 9 | [Search](search.md) | `services/search/` | Stage 5 |
| 10 | [Notifications](notifications.md) | `services/notifications/` | Stage 4 |
| 11 | [Audit Log](audit-log.md) | `services/audit-log/` | Stage 4 |
| 12 | [Background Jobs](background-jobs.md) | `services/background-jobs/` | Stage 8+ |
| 13 | [Health & Observability](health-observability.md) | All services | Stage 1 |
| 14 | [Performance Testing (k6)](k6-performance.md) | `k6/` | All backend stages |
| 15A | [Frontend Foundation, Design System, and App Shell](frontend-foundation-shell.md) | `frontend/` | `.ctx` mock + frontend architecture |
| 15B | [Frontend Auth, Session, Workspaces, and RBAC Shell](frontend-auth-workspaces.md) | `frontend/` | Stage 15A + backend auth/workspace contracts |
| 15C | [Frontend Canvas Editor, Comments, Snapshots, Templates, and Realtime Collaboration](frontend-canvas-realtime.md) | `frontend/` | Stage 15B + canvas/realtime/import-export contracts |
| 15D | [Frontend Dashboards, Widgets, Data Sources, and Query States](frontend-dashboards-data.md) | `frontend/` | Stage 15B + dashboard/data contracts |
| 15E | [Frontend Search, Jobs, Notifications, Audit, and Activity Surfaces](frontend-activity-search.md) | `frontend/` | Stage 15B + search/jobs/notifications/audit contracts |
| 15F | [Frontend Quality Gate, Contract Fixtures, Accessibility, and Compose Integration](frontend-quality-compose.md) | `frontend/` | Stages 15A-15E |


## Frontend Prompt Requirements

Frontend agents must inspect the local `.ctx` mock before implementation. The `.ctx` directory is gitignored and exists as local design context. Use it as the visual and interaction reference, especially the lattice cockpit, design tokens, typography, canvas interaction zones, inspector, command palette, and empty/error states.

Frontend prompts must preserve Gateway auth boundaries, workspace scoping, trusted-header restrictions, canvas JSONB shape, realtime protocol, dashboard widget/query contracts, search facets/highlights, import/export job semantics, notification preferences, and audit read-only behavior.
