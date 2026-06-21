# Stage 14: Performance Testing (k6)

## Objective

Write and run k6 performance test scripts for critical paths in the system. Establish baseline performance metrics and validate against defined thresholds.

## Requirements

1. Create `k6/` directory with shared configuration:
   - `options.js` - shared thresholds, ramp-up profiles
   - `helpers.js` - JWT generation, random data creation
2. Implement `k6/rest-api.js`:
   - Scenario: mixed REST endpoints (create workspace, create canvas, add widget)
   - 1000 req/s target for 5 minutes
   - Thresholds: p95 < 200ms, error rate < 0.1%
3. Implement `k6/canvas-collaboration.js`:
   - Scenario: 50 virtual users connect via WebSocket
   - Each user sends operations at 5 ops/s for 3 minutes
   - Thresholds: p99 op ack latency < 200ms
   - Track message rate and connection stability
4. Implement `k6/dashboard-queries.js`:
   - Scenario: 100 concurrent dashboards loaded
   - Each dashboard has 5 widgets querying ClickHouse
   - Thresholds: p95 query < 500ms
5. Implement `k6/import-export.js`:
   - Scenario: 10 concurrent 10MB import operations
   - Thresholds: p95 import duration < 30s
   - Track file size, throughput (MB/s)
6. Implement `k6/websocket-reconnect.js`:
   - Scenario: simulate network interruptions
   - 100 connections, disconnect/reconnect every 30s
   - Verify state recovery after reconnect
   - Thresholds: reconnection time < 2s, no data loss
7. Implement `k6/kafka-lag.js`:
   - Scenario: burst of canvas operations (5000 ops/s for 1 minute)
   - Monitor Kafka consumer lag in realtime
   - Thresholds: max lag < 1000 after 2 minutes of cooldown
8. Create `k6/run-all.sh` script that runs all tests and generates a summary report
9. Document baseline results in `k6/BASELINE.md`

## Thresholds Summary

| Test | Metric | Threshold |
|---|---|---|
| REST API | p95 latency | < 200ms |
| REST API | Error rate | < 0.1% |
| WebSocket ops | p99 ack latency | < 200ms |
| Dashboard queries | p95 duration | < 500ms |
| Import 10MB | p95 duration | < 30s |
| WebSocket reconnect | Reconnection time | < 2s |
| Kafka lag | Max after cooldown | < 1000 |

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- All tests run against `docker compose up` full stack
- JWT tokens must be generated programmatically (not hardcoded)

## Verification

```bash
# Ensure full stack is running
docker compose up -d
docker compose -f compose.observability.yaml up -d

# Run all performance tests
cd k6 && bash run-all.sh

# Run individual test
k6 run k6/rest-api.js

# Run with specific virtual users
k6 run --vus 100 --duration 5m k6/canvas-collaboration.js

# View Grafana during test
echo "Open http://localhost:4000 and select the Performance Test dashboard"

# Check results JSON
cat k6/reports/summary.json
```
