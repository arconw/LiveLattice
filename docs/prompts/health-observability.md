# Stage 13: Health & Observability

## Objective

Instrument all services with OpenTelemetry for distributed tracing, Micrometer/prom-client for metrics, structured JSON logging, and configure the full observability stack.

## Requirements

1. Add OpenTelemetry instrumentation to all services:
   - **Spring Boot services**: OTel agent + Micrometer OTLP exporter
   - **NestJS services**: `@opentelemetry/instrumentation-http`, `@opentelemetry/instrumentation-express`, OTel SDK
   - Export traces to OTel Collector (gRPC, endpoint `otel-collector:4317`)
   - Export metrics to Prometheus via OTel Collector or direct `/metrics` endpoint
   - Add custom spans for business operations (e.g., `CanvasService.handle`, `QueryEngine.execute`)
2. Configure sampling:
   - 10% default head-based sampling
   - 100% for errors (status >= 500)
   - 100% for requests > p95 latency
   - 100% when `x-debug-trace: true` header present
3. Implement structured JSON logging:
   - Spring Boot: Logstash encoder with `trace_id`, `span_id`, `service`, `level`, `message`, `fields`
   - NestJS: `pino` logger with same fields
   - Sensitive data redaction: passwords, tokens, secrets -> `[REDACTED]`
   - Output to stdout (collected by Docker, shipped to Loki)
4. Implement health check endpoints (all services):
   - `GET /health` - liveness, in-process only
   - `GET /ready` - readiness, checks all dependencies (DB, Redis, Kafka, etc.)
   - Dependency checks cached in-memory for 10s
5. Add standard Prometheus metrics to all services:
   - `http_requests_total`, `http_request_duration_ms`, `http_requests_active`
   - `jvm_memory_used_bytes` (Spring), `node_event_loop_lag_seconds` (NestJS)
   - Business metrics: `active_workspaces`, `canvas_ops_total`, `ws_connections_active`
6. Create Grafana dashboards (provisioned as JSON):
   - **Service Overview**: CPU, memory, request rate, error rate, p50/p95/p99 latency
   - **Kafka Dashboard**: Consumer lag, throughput, partition distribution
   - **Database Dashboard**: Connection pool, query performance, cache hit ratio
   - **WebSocket Dashboard**: Active connections, message throughput, op latency
   - **Business Dashboard**: Active workspaces, canvas ops, dashboard views
7. Add Prometheus alerting rules:
   - `HighErrorRate`: > 1% 5xx in 5m
   - `HighLatency`: p95 > 500ms for 5m
   - `KafkaConsumerLag`: lag > 1000 for 5m
   - `ServiceDown`: up == 0 for 1m
8. Configure OTel Collector pipeline:
   - Batch processor (1s timeout, 1024 batch size)
   - Memory limiter (512 MiB)
   - Exporters: Prometheus (8889), Tempo (gRPC), Loki (HTTP)
9. Write end-to-end observability test:
   - Send test request -> verify trace appears in Tempo -> verify log appears in Loki -> verify metric appears in Prometheus -> verify dashboard shows data in Grafana

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- All services must export traces, metrics, and logs
- No credentials in dashboards (provisioned datasources use local URLs)

## Verification

```bash
# Start full stack with observability
docker compose up -d
docker compose -f compose.observability.yaml up -d

# Check OTel Collector
curl http://localhost:8889/metrics | head -20

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Generate some traffic
curl http://localhost:3000/api/core/workspaces

# Check logs in Loki
curl -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={service="core"}' \
  --data-urlencode 'limit=10'

# Open Grafana
echo "Open http://localhost:4000 in browser"

# View traces in Tempo
# (trigger a traced request and look up trace_id)

# Check alert rules
curl http://localhost:9090/api/v1/rules

# Run observability tests
cd tests && ./gradlew test --tests *ObservabilityE2E*
```
