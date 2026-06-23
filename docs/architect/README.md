# Architecture Documentation

LiveLattice architecture is split into four tiers: a frontend web app, a Node.js (NestJS) gateway and realtime layer, a Java (Spring Boot) core domain/integration layer, and a unified observability/infra layer.

## Document Index

| Document | Description |
|---|---|
| [System Architecture](overview/system-architecture.md) | High-level component diagram, data flows, deployment topology |
| [Frontend Architecture](frontend/frontend-architecture.md) | Web app shell, route model, frontend state ownership, and backend contract boundaries |
| [Data Architecture](data/data-architecture.md) | Storage strategy, partitioning, caching, indexing |
| [Security & Tenancy](security/security-and-tenancy.md) | Authentication, RBAC, mTLS, encryption, isolation |
| [Realtime Collaboration](realtime/realtime-collaboration.md) | WebSocket rooms, OT/CRDT, presence, broadcasting |
| [Testing Strategy](testing/testing-strategy.md) | Unit, integration, contract, E2E, performance |
| [Deployment & CI](infra/deployment-and-ci.md) | Docker Compose, monorepo, versioning, pipelines |
| [Local Docker Compose Stack](infra/local-dev-compose.md) | Local containers, health checks, placeholder services, environment files |
| [Performance & Observability](operations/performance-observability.md) | OpenTelemetry, metrics, traces, logs, profiling |

## Cross-Cutting Concerns

- **Low latency**: in-memory caches (Redis), connection pooling, async I/O, batching
- **Backpressure**: Kafka consumer lag monitoring, circuit breakers, rate limiters
- **Observability**: every service exports OTLP traces, Prometheus metrics, structured JSON logs
- **Tenancy**: workspace-level data isolation via schema-per-tenant or row-level security
