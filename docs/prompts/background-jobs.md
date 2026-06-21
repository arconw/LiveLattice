# Stage 12: Background Jobs

## Objective

Implement the background job processing system with Redis-backed queues, scheduled cron jobs, worker pools with per-type concurrency, retry with backoff, and dead letter queues.

## Requirements

1. Initialize Spring Boot project in `services/background-jobs/` with:
   - Spring Scheduling, Redis (Redisson), PostgreSQL, Kafka
   - Worker pool abstraction with per-queue concurrency control
2. Implement `JobDefinition` and `JobExecution` entities in PostgreSQL
3. Implement `JobService`:
   - `createJob(type, payload, priority)` -> INSERT + Redis LPUSH
   - `getJob(jobId)` -> status + progress
   - `cancelJob(jobId)` -> remove from queue (if not running)
   - `listJobs(filters)` -> paginated job list
4. Implement `WorkerPool`:
   - Per-type thread pool with configurable concurrency
   - Redis BLPOP for job dequeue (5s timeout, polling)
   - SELECT ... FOR UPDATE SKIP LOCKED to prevent double-processing
   - Progress tracking: job reports 0-100% to Redis every 5s
5. Implement `RetryManager`:
   - On failure: if retries < max -> re-enqueue with `retry_delay` delay
   - On failure: if retries >= max -> move to dead letter queue
   - Exponential backoff: 1m, 5m, 15m, 1h (capped at max_retries)
6. Implement concrete job handlers:
   - `ExportJobHandler` - coordinate with Import/Export service
   - `ImportJobHandler` - coordinate with Import/Export service
   - `WorkspaceCleanupHandler` - hard-delete workspaces past 30-day grace period
   - `SnapshotCompactionHandler` - keep last 100 snapshots, delete older from MinIO
   - `PartitionMaintenanceHandler` - trigger audit log partition archival
   - `QuotaReconciliationHandler` - reconcile Redis quota counters with PostgreSQL counts
   - `TempFileCleanupHandler` - delete staging files older than 24h
   - `EmailDigestHandler` - group and send digest notifications
   - `WebhookRetryHandler` - retry failed webhook deliveries
7. Implement `DeadLetterManager`:
   - List dead letters
   - Retry dead letter (re-enqueue)
   - Alert when dead letter count > 100 (Prometheus metric)
8. Implement graceful shutdown:
   - SIGTERM -> pause consumers -> wait for running jobs (max 30s) -> re-queue incomplete -> close connections
9. Write unit and integration tests with Testcontainers (Redis, PostgreSQL)

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Jobs must survive worker restart (PostgreSQL + Redis persistence)
- Running jobs must be re-queued on graceful shutdown if not completed in 30s

## Verification

```bash
# Create an export job
curl -X POST http://localhost:8085/jobs/export \
  -H "Content-Type: application/json" \
  -d '{"type":"EXPORT","payload":{"canvasId":"canvas-123","format":"svg"},"priority":50}'

# Check job status
curl http://localhost:8085/jobs/job-uuid

# List dead letters
curl http://localhost:8085/jobs/dead-letters

# Retry a dead letter
curl -X POST http://localhost:8085/jobs/dead-letters/dl-uuid/retry

# Run tests
cd services/background-jobs && ./gradlew test
```
