# Health & Observability - Technical Design

## Responsibilities

- Health check endpoints (liveness, readiness, dependency checks)
- Metrics export (Prometheus format) for all services
- Distributed tracing (OpenTelemetry -> Tempo/Jaeger)
- Structured logging (JSON -> Loki)
- Centralized dashboards (Grafana)
- Alerting (Prometheus AlertManager -> Slack/PagerDuty)

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline + NestJS 11
- **Tracing**: OpenTelemetry SDK (auto-instrumentation + manual spans)
- **Metrics**: Micrometer (Spring) + prom-client (NestJS)
- **Logging**: Logstash Encoder (Java, JSON) + pino (NestJS)
- **Collector**: OpenTelemetry Collector Contrib
- **Storage**: Prometheus + Grafana + Loki + Tempo/Jaeger
- **Health**: Spring Boot Actuator + NestJS Terminus

## Health Checks

### Liveness (`GET /health`)

Simple in-process check returning 200:

```json
{ "status": "UP", "service": "core", "version": "1.2.3", "uptime": 3600 }
```

### Readiness (`GET /ready`)

Checks all dependencies:

```json
{
  "status": "UP",
  "checks": {
    "postgresql": { "status": "UP", "details": { "poolActive": 3, "poolMax": 40, "latencyMs": 2 } },
    "redis": { "status": "UP", "details": { "latencyMs": 1 } },
    "kafka": { "status": "UP", "details": { "partitions": 3, "consumerLag": 0 } },
    "opensearch": { "status": "UP", "details": { "clusterStatus": "green" } },
    "clickhouse": { "status": "UP", "details": { "latencyMs": 5 } },
    "minio": { "status": "UP", "details": { "buckets": ["snapshots", "imports", "exports"] } }
  }
}
```

## Metrics

### Core Metrics (all services)

| Metric | Type | Description |
|---|---|---|
| `service_info` | Gauge | Service metadata (version, commit) |
| `http_requests_total` | Counter | HTTP request count (method, path, status) |
| `http_request_duration_ms` | Histogram | Request duration buckets |
| `http_requests_active` | Gauge | Currently active requests |
| `db_query_duration_ms` | Histogram | Database query duration |
| `db_connections_active` | Gauge | Active DB connections |
| `cache_hit_total` | Counter | Cache hit count |
| `cache_miss_total` | Counter | Cache miss count |
| `kafka_messages_produced_total` | Counter | Kafka messages produced |
| `kafka_messages_consumed_total` | Counter | Kafka messages consumed |
| `kafka_consumer_lag` | Gauge | Consumer lag per topic/partition |
| `jvm_memory_used_bytes` | Gauge | JVM memory usage |
| `jvm_gc_pause_seconds` | Histogram | GC pause duration |
| `node_event_loop_lag_seconds` | Gauge | Node event loop lag |

### Business Metrics

| Metric | Type | Source |
|---|---|---|
| `active_workspaces` | Gauge | Core Domain |
| `active_canvases` | Gauge | Core Domain |
| `canvas_ops_total` | Counter | Realtime Service |
| `active_ws_connections` | Gauge | Realtime Service |
| `ws_messages_total` | Counter | Realtime Service |
| `search_queries_total` | Counter | Search Service |
| `imports_total` | Counter | Import/Export |
| `exports_total` | Counter | Import/Export |
| `notifications_sent_total` | Counter | Notifications |
| `audit_events_total` | Counter | Audit Log |

## Distributed Tracing

### Trace Example: Canvas Save

```
Span: HTTP POST /canvases/:id               (Gateway)
  |-- Span: JWT Validation                  (Gateway, 2ms)
  |-- Span: Rate Limit Check                (Gateway, 1ms)
  |-- Span: HTTP POST core:8080/canvases    (Gateway -> Core, 45ms)
  |   |-- Span: Command Bus Dispatch         (Core, 1ms)
  |   |-- Span: Validate Command             (Core, 3ms)
  |   |-- Span: CanvasService.handle         (Core, 30ms)
  |   |   |-- Span: Repository.find          (Core, 5ms)
  |   |   |-- Span: CRDT Apply Ops           (Core, 10ms)
  |   |   |-- Span: Repository.save          (Core, 8ms)
  |   |   +-- Span: Kafka Publish            (Core, 5ms)
  |   +-- Span: Response Serialization       (Core, 2ms)
  +-- Span: Response Serialization           (Gateway, 2ms)
```

### Sampling

| Traffic | Sampling Rate | Strategy |
|---|---|---|
| Normal | 10% | Head-based, random |
| High traffic (>1000 req/s) | 1% | Adaptive sampling |
| Errors | 100% | Always sample error spans |
| Slow requests (>p95) | 100% | Always sample slow spans |
| Specific users (dev mode) | 100% | Header-based `x-debug-trace: true` |

## Structured Logging

### Log Format (JSON)

```json
{
  "timestamp": "2026-06-22T10:30:00.123Z",
  "level": "INFO",
  "service": "core",
  "trace_id": "abc123def456",
  "span_id": "span789",
  "message": "Canvas updated successfully",
  "fields": {
    "canvasId": "abc-123",
    "workspaceId": "ws-456",
    "userId": "user-789",
    "version": 42,
    "opCount": 5,
    "durationMs": 35
  },
  "exception": null
}
```

### Sensitive Data Filtering

- `password`, `secret`, `token`, `authorization`, `cookie` fields replaced with `[REDACTED]`
- Logback `RegexBasedRewrite` appender + pino `redact` option
- PII fields (email, name) masked in production: `user@example.com` -> `u***@example.com`

## Health & Observability Infrastructure

```yaml
# compose.observability.yaml
services:
  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    ports: ["4317:4317", "4318:4318", "8889:8889"]
    volumes: [./otel-collector.yaml:/etc/otel/config.yaml]

  prometheus:
    image: prom/prometheus:latest
    ports: ["9090:9090"]
    volumes: [./prometheus.yaml:/etc/prometheus/prometheus.yaml]

  grafana:
    image: grafana/grafana:latest
    ports: ["4000:3000"]
    environment:
      GF_AUTH_ANONYMOUS_ENABLED: "true"
    volumes:
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards

  loki:
    image: grafana/loki:latest
    ports: ["3100:3100"]
    command: -config.file=/etc/loki/local-config.yaml

  tempo:
    image: grafana/tempo:latest
    ports: ["3200:3200", "4317:4317"]
    command: -config.file=/etc/tempo/tempo.yaml

  jaeger:
    image: jaegertracing/all-in-one:latest
    ports: ["16686:16686", "4317:4317"]
```

## API Endpoints

```
GET    /health              -> Liveness (all services)
GET    /ready               -> Readiness (all services)
GET    /metrics             -> Prometheus metrics (all services)
GET    /actuator/prometheus -> Spring Boot metrics
GET    /actuator/info       -> Build info
GET    /actuator/env        -> Environment properties (secured)

GET    /observability/dashboards -> List embedded Grafana URLs
GET    /observability/traces/:traceId -> Tempo trace detail
GET    /observability/logs?query= -> Loki log query (admin)
```

## Performance Considerations

- **Metrics performance**: Micrometer uses `step` counters to avoid contention; histograms with minimal buckets (6-8 per metric)
- **Trace overhead**: OTel SDK < 1% CPU overhead at 10% sampling; batch exporter with 1s interval
- **Logging**: Async appender with RingBuffer (disruptor); never block request thread on log I/O
- **Health check caching**: `/ready` dependencies checked every 10s, cached in-memory between checks
- **Grafana provisioning**: Datasources and dashboards provisioned at startup, no manual setup
- **Alerting**: Prometheus rules evaluated every 30s; AlertManager deduplicates, groups, and throttles notifications
