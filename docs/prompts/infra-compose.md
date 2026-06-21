# Stage 1: Infrastructure & Docker Compose

## Objective

Set up the monorepo structure and Docker Compose configuration for all infrastructure dependencies required by LiveLattice.

## Requirements

1. Create the monorepo directory structure:
   - `gateway/` - NestJS API Gateway + Realtime (placeholder `package.json`)
   - `core/` - Spring Boot Core Domain (placeholder `build.gradle.kts`)
   - `services/search/`, `services/notifications/`, `services/import-export/`, `services/audit-log/`, `services/background-jobs/` (placeholder `build.gradle.kts`)
   - `migrations/init/` - Flyway SQL initialization scripts
   - `k6/` - Performance test scripts
   - `scripts/` - Utility scripts (e.g., `wait-for-it.sh`)

2. Create `compose.yaml` with services:
   - PostgreSQL 16 (health check, named volume, init scripts)
   - Redis 7 (AOF persistence, maxmemory policy)
   - Zookeeper + Kafka (Confluent, single broker)
   - ClickHouse 24
   - OpenSearch 2 (single-node, security disabled)
   - MinIO (with console)
   - Placeholder app services (gateway, realtime, core, search, notifications, import-export, audit-log, background-jobs)
   - All services on `livelattice_default` network
   - Proper `depends_on` with health check conditions

3. Create `compose.observability.yaml`:
   - OpenTelemetry Collector Contrib
   - Prometheus (30d retention)
   - Grafana (provisioned datasources + dashboards)
   - Loki
   - Tempo

4. Create `compose.test.yaml`:
   - Lightweight test-only dependencies (ports offset to avoid conflicts)

5. Create `.env.example` with all required environment variables

6. Create `secrets/` directory with `.gitkeep` and instructions

7. Create initial Flyway migration that creates `workspaces` and `users` tables

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Every infrastructure service must have a health check
- Application services must have `depends_on` with `condition: service_healthy` where applicable

## Verification

```bash
# Start all infrastructure
docker compose up -d

# Verify all services are healthy
docker compose ps

# Check PostgreSQL
docker compose exec postgres pg_isready -U livelattice

# Check Redis
docker compose exec redis redis-cli ping

# Check Kafka
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Start observability stack
docker compose -f compose.observability.yaml up -d

# Check Grafana is accessible
curl -s http://localhost:4000/api/health

# Stop everything
docker compose down
```

## Relevant Tests

None yet - subsequent stages add test suites.
