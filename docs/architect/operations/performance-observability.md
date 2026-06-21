# Performance & Observability

## Performance Targets

| Metric | Target | Measurement |
|---|---|---|
| API p95 latency | < 100ms | Prometheus histogram |
| WebSocket op latency p99 | < 200ms | Tempo trace |
| Canvas load time | < 500ms | k6 + Tempo |
| Dashboard query p95 | < 500ms | ClickHouse query log |
| Import 10MB file | < 30s | k6 |
| Concurrent editors per canvas | 50+ | k6 |
| Gateway throughput | 5000 req/s | Prometheus counter |

## Performance Techniques

### Low Latency
- **Connection pooling**: HikariCP (Spring Boot), `pg-pool` (NestJS)
- **Caching**: Redis cache-aside with compressed values (Snappy)
- **Batching**: Kafka producer batches (16KB / 10ms); WebSocket op batches (50 ops / 50ms)
- **Async everywhere**: Spring WebFlux for non-blocking I/O; NestJS async pipes
- **Indexing**: Carefully designed composite indexes, BRIN for time-series, GIN for JSONB

### Backpressure
- **Kafka consumer lag**: Alert at 1000 messages lag; pause consumer at 10000
- **Rate limiting**: Token bucket algorithm, per-user and per-workspace
- **Circuit breakers**: Resilience4j (Spring Boot), timeout + retry patterns
- **WebSocket flow control**: Server sends backpressure signal when client send rate exceeds threshold

### Partitions
- **Kafka topics**: Partitioned by `workspace_id % num_partitions`, 3 partitions per topic minimum
- **ClickHouse**: Distributed table across shards, `workspace_id` as sharding key
- **PostgreSQL**: Partitioned tables for audit_log, canvas_events by month

## OpenTelemetry

### Instrumentation
- **Traces**: OTel SDK auto-instrumentation for Spring Boot (HTTP, JDBC, Kafka, Redis); NestJS OpenTelemetry instrumentation
- **Metrics**: Micrometer (Spring Boot) + Prometheus exporter; NestJS Prometheus client
- **Logs**: Structured JSON with trace_id, span_id correlation; OTel Collector processes and forwards to Loki

### OTel Collector Pipeline

```
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 1s
    send_batch_size: 1024
  memory_limiter:
    check_interval: 1s
    limit_mib: 512

exporters:
  prometheus:
    endpoint: 0.0.0.0:8889
  otlp/tempo:
    endpoint: tempo:4317
  loki:
    endpoint: http://loki:3100/loki/api/v1/push

service:
  pipelines:
    traces: [otlp, batch, otlp/tempo]
    metrics: [otlp, prometheus]
    logs: [otlp, batch, loki]
```

## Dashboards

### Prometheus Metrics

| Metric | Type | Labels |
|---|---|---|
| `http_requests_total` | Counter | `service`, `method`, `path`, `status` |
| `http_request_duration_ms` | Histogram | `service`, `method`, `path` |
| `ws_messages_total` | Counter | `service`, `event_type` |
| `ws_connections_active` | Gauge | `service` |
| `canvas_ops_total` | Counter | `workspace_id`, `op_type` |
| `kafka_consumer_lag` | Gauge | `service`, `topic`, `partition` |
| `db_query_duration_ms` | Histogram | `service`, `query_name` |
| `cache_hit_ratio` | Gauge | `service`, `cache_name` |

### Grafana Dashboards

1. **Service Overview**: CPU, memory, request rate, error rate, p50/p95/p99 latency per service
2. **Kafka Dashboard**: Producer/consumer lag, throughput, partition distribution
3. **Database Dashboard**: Connection pool, query performance, cache hit ratio, replication lag
4. **WebSocket Dashboard**: Active connections, message throughput, op latency, presence events
5. **Business Dashboard**: Active workspaces, canvas operations, dashboard views (from ClickHouse)

## Logging

- **Format**: Structured JSON with mandatory fields: `timestamp`, `level`, `service`, `trace_id`, `span_id`, `message`
- **Storage**: Loki with 30-day retention; long-term in MinIO as Parquet
- **Log levels**: `INFO` in production, `DEBUG` on-demand via config reload, `TRACE` for specific user sessions
- **Sensitive data**: Masked via logback/log4j2 pattern filters; never log JWT bodies, passwords, API keys

## Profiling

- **JVM**: async-profiler via JMX; JDK Flight Recorder continuous recording (60s window)
- **Node**: `clinic.js` for flamegraphs on-demand; `--heap-prof` for memory analysis
- **Always-on**: Metrics exported for CPU, memory, GC (JVM), event loop lag (Node)

## Alerting Rules (Prometheus)

```yaml
groups:
  - name: livelattice
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.01
        for: 5m
      - alert: HighLatency
        expr: histogram_quantile(0.95, rate(http_request_duration_ms_bucket[5m])) > 500
      - alert: KafkaConsumerLag
        expr: kafka_consumer_lag > 1000
      - alert: ServiceDown
        expr: up{job=~"gateway|core|realtime"} == 0
        for: 1m
```
