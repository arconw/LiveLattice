# LiveLattice

Collaborative diagrams, dashboards, charts, and real-time data integrations - frontend, backend, and infrastructure.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React + TypeScript web app (planned Stage 15) |
| Gateway / Realtime | Node.js (NestJS 11, Fastify, Socket.IO) |
| Core Domain | Java 21 (Spring Boot 4.x baseline, exact patch pinned during implementation) |
| Identity | Keycloak 26.x (OAuth2/OIDC) |
| Message Bus | Kafka / RabbitMQ |
| Cache | Redis 7 |
| Primary DB | PostgreSQL 16 |
| Analytics DB | ClickHouse / TimescaleDB |
| Search | OpenSearch 2 |
| Object Storage | MinIO (S3-compatible) |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki, Tempo/Jaeger |
| Testing | Testcontainers, k6 |
| Containerization | Docker Compose |

## Architecture

```
                                   +-------------+
                                   |   Clients    |
                                   | (Web/Mobile) |
                                   +------+------+
                                          |
                                   +------v------+
                                   |   Gateway    |  NestJS :3000 REST + :3001 WebSocket
                                   +------+------+
                                          |
              +---------------------------+---------------------------+
              |                           |                           |
       +------v------+           +-------v-------+           +-------v-------+
       |   Realtime   |           |   Core Domain |           |  Integrations |
       | (NestJS/WS)  |           | (Spring Boot) |           | (Spring Boot) |
       +------+-------+           +-------+-------+           +-------+-------+
              |                           |                           |
              +---------------------------+---------------------------+
                                          |
                                   +------v------+
                                   |   Kafka /   |
                                   |  RabbitMQ   |
                                   +------+------+
                                          |
              +----------+----------+------+------+----------+----------+
              |          |          |             |          |          |
         +----v---+ +---v----+ +--v-----+  +----v---+  +---v----+  +--v------+
         |Postgres| | Redis  | |ClickHouse| |OpenSearch| | MinIO  | | Keycloak|
         +--------+ +--------+ +---------+  +---------+  +--------+  +---------+
```

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Node.js 24 LTS (for local dev)
- JDK 21 (for local dev)

### Quick Start

```bash
cp .env.example .env
docker compose up -d
docker compose ps
docker compose -f compose.observability.yaml up -d
```

Local stack entry points:

- `compose.yaml` - infrastructure plus backend placeholder containers.
- `compose.observability.yaml` - OpenTelemetry Collector, Prometheus, Grafana, Loki, Tempo.
- `compose.test.yaml` - lightweight test dependencies with offset ports.
- `.env.example` - documented local environment template.

## Documentation

| Section | Description |
|---|---|
| [Architecture Overview](docs/architect/README.md) | System architecture, data flow, deployment topology |
| [System Architecture](docs/architect/overview/system-architecture.md) | Component diagram, request flows, design decisions |
| [Frontend Architecture](docs/architect/frontend/frontend-architecture.md) | Web app shell, route model, contract boundaries, canvas/dashboard/search interaction architecture |
| [Data Architecture](docs/architect/data/data-architecture.md) | Storage strategy, schemas, indexing, caching, partitioning, retention |
| [Security & Tenancy](docs/architect/security/security-and-tenancy.md) | Auth, RBAC, multi-tenancy, encryption, secrets management |
| [Realtime Collaboration](docs/architect/realtime/realtime-collaboration.md) | WebSocket, CRDT, presence, broadcasting |
| [Testing Strategy](docs/architect/testing/testing-strategy.md) | Unit, integration, E2E, performance test approach |
| [Deployment & CI](docs/architect/infra/deployment-and-ci.md) | Monorepo, Docker Compose, versioning, CI pipeline |
| [Local Docker Compose Stack](docs/architect/infra/local-dev-compose.md) | Local containers, health checks, environment files |
| [Performance & Observability](docs/architect/operations/performance-observability.md) | Metrics, traces, logs, dashboards, alerting |

### Technical Designs

| Domain | Design | Flow |
|---|---|---|
| API Gateway | [Design](docs/techDesign/api-gateway/api-gateway-design.md) | [Flow](docs/techDesign/api-gateway/api-gateway-flow.mmd) |
| Realtime Collaboration | [Design](docs/techDesign/realtime/realtime-design.md) | [Flow](docs/techDesign/realtime/realtime-flow.mmd) |
| Core Domain | [Design](docs/techDesign/core-domain/core-domain-design.md) | [Flow](docs/techDesign/core-domain/core-domain-flow.mmd) |
| Auth & Identity | [Design](docs/techDesign/auth-identity/auth-identity-design.md) | [Flow](docs/techDesign/auth-identity/auth-identity-flow.mmd) |
| Workspaces & RBAC | [Design](docs/techDesign/workspaces-rbac/workspaces-rbac-design.md) | [Flow](docs/techDesign/workspaces-rbac/workspaces-rbac-flow.mmd) |
| Canvas & Documents | [Design](docs/techDesign/canvas-documents/canvas-documents-design.md) | [Flow](docs/techDesign/canvas-documents/canvas-documents-flow.mmd) |
| Dashboard & Analytics | [Design](docs/techDesign/dashboard-analytics/dashboard-analytics-design.md) | [Flow](docs/techDesign/dashboard-analytics/dashboard-analytics-flow.mmd) |
| Import & Export | [Design](docs/techDesign/import-export/import-export-design.md) | [Flow](docs/techDesign/import-export/import-export-flow.mmd) |
| Search | [Design](docs/techDesign/search/search-design.md) | [Flow](docs/techDesign/search/search-flow.mmd) |
| Notifications | [Design](docs/techDesign/notifications/notifications-design.md) | [Flow](docs/techDesign/notifications/notifications-flow.mmd) |
| Audit Log | [Design](docs/techDesign/audit-log/audit-log-design.md) | [Flow](docs/techDesign/audit-log/audit-log-flow.mmd) |
| Background Jobs | [Design](docs/techDesign/background-jobs/background-jobs-design.md) | [Flow](docs/techDesign/background-jobs/background-jobs-flow.mmd) |
| Health & Observability | [Design](docs/techDesign/health-observability/health-observability-design.md) | [Flow](docs/techDesign/health-observability/health-observability-flow.mmd) |
| Infrastructure & Compose | [Design](docs/techDesign/infra-compose/infra-compose-design.md) | [Flow](docs/techDesign/infra-compose/infra-compose-flow.mmd) |
| Frontend Web App | [Design](docs/techDesign/frontend/frontend-design.md) | [Flow](docs/techDesign/frontend/frontend-flow.mmd) |

### Implementation Prompts

| Stage | Domain | Link |
|---|---|---|
| 1 | Infrastructure & Docker Compose | [Prompt](docs/prompts/infra-compose.md) |
| 2 | API Gateway | [Prompt](docs/prompts/api-gateway.md) |
| 3 | Auth & Identity | [Prompt](docs/prompts/auth-identity.md) |
| 4 | Workspaces & RBAC | [Prompt](docs/prompts/workspaces-rbac.md) |
| 5 | Canvas & Documents | [Prompt](docs/prompts/canvas-documents.md) |
| 6 | Dashboard & Analytics | [Prompt](docs/prompts/dashboard-analytics.md) |
| 7 | Realtime Collaboration | [Prompt](docs/prompts/realtime.md) |
| 8 | Import & Export | [Prompt](docs/prompts/import-export.md) |
| 9 | Search | [Prompt](docs/prompts/search.md) |
| 10 | Notifications | [Prompt](docs/prompts/notifications.md) |
| 11 | Audit Log | [Prompt](docs/prompts/audit-log.md) |
| 12 | Background Jobs | [Prompt](docs/prompts/background-jobs.md) |
| 13 | Health & Observability | [Prompt](docs/prompts/health-observability.md) |
| 14 | Performance Testing (k6) | [Prompt](docs/prompts/k6-performance.md) |
| 15A | Frontend Foundation & Shell | [Prompt](docs/prompts/frontend-foundation-shell.md) |
| 15B | Frontend Auth & Workspaces | [Prompt](docs/prompts/frontend-auth-workspaces.md) |
| 15C | Frontend Canvas & Realtime | [Prompt](docs/prompts/frontend-canvas-realtime.md) |
| 15D | Frontend Dashboards & Data | [Prompt](docs/prompts/frontend-dashboards-data.md) |
| 15E | Frontend Search, Jobs, Notifications & Audit | [Prompt](docs/prompts/frontend-activity-search.md) |
| 15F | Frontend Quality & Compose | [Prompt](docs/prompts/frontend-quality-compose.md) |
