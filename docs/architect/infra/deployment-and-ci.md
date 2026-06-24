# Deployment & CI

## Monorepo Structure

```
livelattice/
|-- compose.yaml                  # Root Docker Compose (full stack)
|-- compose.test.yaml             # Test dependencies only
|-- compose.observability.yaml    # OTel, Prometheus, Grafana, etc.
|-- gateway/                      # NestJS API Gateway
|   |-- src/
|   |-- test/
|   |-- Dockerfile
|   |-- package.json
|   +-- tsconfig.json
|-- frontend/                     # React frontend static app
|   |-- src/
|   |-- Dockerfile
|   |-- nginx.conf
|   +-- package.json
|-- realtime/                     # NestJS Realtime Service
|   |-- src/
|   |-- test/
|   |-- Dockerfile
|   +-- package.json
|-- core/                         # Spring Boot Core Domain
|   |-- src/
|   |-- test/
|   |-- build.gradle.kts
|   |-- Dockerfile
|   +-- settings.gradle.kts
|-- services/                     # Additional Spring Boot services
|   |-- search/
|   |-- notifications/
|   |-- import-export/
|   |-- audit-log/
|   +-- background-jobs/
|-- migrations/                   # Flyway migrations
|-- k6/                           # Performance test scripts
|-- docs/                         # Documentation
|-- scripts/                      # CI helper scripts
+-- .github/                      # GitHub Actions workflows
```

## Docker Compose (Local Execution)

The primary local execution path is `docker compose up`. A root `compose.yaml` orchestrates all services:

```yaml
# compose.yaml (abbreviated)
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: livelattice
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]

  clickhouse:
    image: clickhouse/clickhouse-server:24

  opensearch:
    image: opensearchproject/opensearch:2

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"

  gateway:
    build: ./gateway
    ports: ["3000:3000", "3001:3001"]
    depends_on: [postgres, redis, kafka]

  realtime:
    build: ./realtime
    ports: ["3002:3002"]
    depends_on: [redis, kafka]

  frontend:
    build: ./frontend
    ports: ["8088:80"]
    depends_on: [gateway]

  core:
    build: ./core
    ports: ["8080:8080"]
    depends_on: [postgres, redis, kafka]

  observability-collector:
    image: otel/opentelemetry-collector-contrib:latest
    volumes: [./otel-collector.yaml:/etc/otel-collector.yaml]

  prometheus:
    image: prom/prometheus:latest
    volumes: [./prometheus.yaml:/etc/prometheus/prometheus.yaml]

  grafana:
    image: grafana/grafana:latest
    ports: ["4000:3000"]
    volumes: [./grafana/dashboards:/etc/grafana/provisioning/dashboards]
```

## Versioning & Releases

- **Semantic versioning**: `v{major}.{minor}.{patch}` with pre-release tags (`-alpha.1`, `-rc.1`)
- **Release process**:
  1. Merge feature branches to `main`
  2. GitHub Actions runs full test suite
  3. Manual trigger `Release` workflow: bumps version, creates tag, builds Docker images
  4. Images pushed to GitHub Container Registry (ghcr.io/livelattice/{service}:{version})
  5. Release notes auto-generated from conventional commits

## CI Pipeline (GitHub Actions)

```yaml
jobs:
  test:
    strategy:
      matrix:
        service: [gateway, realtime, frontend, core, search, notifications]
    steps:
      - uses: actions/checkout@v4
      - name: Start test dependencies
        run: docker compose -f compose.test.yaml up -d
      - name: Run ${{ matrix.service }} tests
        run: |
          cd ${{ matrix.service }}
          ./gradlew test || npm test
      - name: Upload coverage
        uses: actions/upload-artifact@v4

  integration:
    needs: [test]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: docker compose -f compose.yaml up -d --wait
      - run: k6 run k6/critical-path.js

  build:
    needs: [integration]
    steps:
      - uses: docker/build-push-action@v5
        with:
          tags: ghcr.io/livelattice/${{ matrix.service }}:${{ github.sha }}

  release:
    needs: [build]
    if: github.event_name == 'workflow_dispatch'
    steps:
      - uses: cycjimmy/semantic-release-action@v4
```

## Environment Strategy

| Environment | Infrastructure | Purpose |
|---|---|---|
| `local` | Docker Compose | Development, testing |
| `dev` | Single-node Docker Compose on VM | Integration testing |
| `staging` | Multi-node, scaled-down | Pre-release validation |
| `production` | Kubernetes or Nomad | Live traffic |

## Health Checks & Readiness

Every service exposes:
- `GET /health` - liveness (returns 200 if alive)
- `GET /ready` - readiness (checks dependencies: DB, Kafka, Redis)
- `GET /metrics` - Prometheus metrics
