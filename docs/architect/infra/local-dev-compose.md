# Local Docker Compose Stack

LiveLattice local development runs through Docker Compose. Developers should not manually install databases, brokers, search engines, or observability services for normal local work.

## Files

- `compose.yaml` starts core infrastructure, the API Gateway, and backend placeholder containers.
- `frontend/` is included as an optional static nginx service when the frontend build is ready.
- `compose.observability.yaml` starts OpenTelemetry Collector, Prometheus, Grafana, Loki, and Tempo.
- `compose.test.yaml` starts lightweight dependencies on offset ports for integration tests.
- `.env.example` documents local environment variables.
- `migrations/init/001_livelattice_init.sql` initializes the local PostgreSQL schema and the Keycloak database.

## Placeholder containers

The first implementation stage uses lightweight backend placeholder containers with `/health` endpoints. They make dependency ordering, networking, ports, and observability targets testable before the actual NestJS and Spring Boot services are implemented.

The placeholders are replaced service by service as implementation progresses. The API Gateway is the first placeholder replaced by an executable service image.

## API Gateway

The gateway exposes `/health`, `/ready`, and `/metrics`, forwards `/api/:service/*` traffic to backend services, adds `x-request-id`, applies fixed-window rate limiting, and supports optional JWT validation through JWKS.

## Frontend

The frontend Compose service builds `./frontend`, serves static assets through nginx, and proxies `/auth/*`, `/api/*`, and `/ready` to the Gateway so browser REST calls stay same-origin. It exposes `FRONTEND_PORT` with a default of `8088` and accepts `FRONTEND_REALTIME_URL` as the browser-visible realtime base URL.

## Performance defaults

- Redis uses AOF persistence and `allkeys-lru` eviction for realistic cache behavior.
- Kafka starts with three partitions to expose partition-aware behavior early.
- ClickHouse has a high `nofile` limit for local analytical workloads.
- OpenSearch and JVM services use bounded heap settings to avoid starving developer machines.
- Compose health checks are explicit so application containers do not start before critical dependencies are available.

## Commands

```bash
cp .env.example .env
docker compose config
docker compose up -d
docker compose ps
docker compose build frontend
docker compose up -d frontend gateway
docker compose exec gateway wget -qO- 127.0.0.1:3000/health
docker compose exec gateway wget -qO- 127.0.0.1:3000/api/core/health
docker compose -f compose.observability.yaml up -d
docker compose -f compose.test.yaml up -d
```
