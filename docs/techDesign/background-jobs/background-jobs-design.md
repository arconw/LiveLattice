# Background Jobs - Technical Design

## Responsibilities

- Execute long-running or scheduled tasks asynchronously
- Job queue management (enqueue, dequeue, retry, dead letter)
- Scheduled/cron jobs (cleanup, archival, billing)
- Job progress tracking and reporting
- Graceful shutdown and job persistence

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline; exact patch version is pinned during implementation
- **Queue**: Redis (Redisson RQueue / Bull-compatible) for lightweight jobs + Kafka for heavy jobs
- **Scheduling**: Spring `@Scheduled` + `@Async` with configurable thread pool
- **Persistence**: PostgreSQL for job definitions + state
- **Dead letters**: Redis + PostgreSQL backed

## Job Types

| Job | Type | Schedule | Description |
|---|---|---|---|
| `WorkspaceCleanup` | Cron | Daily 03:00 | Hard-delete workspaces past grace period, notify owners |
| `SnapshotCompaction` | Cron | Hourly | Compact old canvas snapshots (keep last 100) |
| `PartitionMaintenance` | Cron | Daily 02:00 | Detach old audit log partitions, export to MinIO |
| `QuotaReconciliation` | Cron | Daily 04:00 | Reconcile Redis quota counters with PostgreSQL |
| `ExportJob` | Async | On demand | Generate export file, upload to MinIO, notify user |
| `ImportJob` | Async | On demand | Process large import files |
| `WebhookRetry` | Cron | Every 15m | Retry failed webhook deliveries |
| `EmailDigest` | Cron | Hourly/daily | Send digest emails |
| `TempFileCleanup` | Cron | Hourly | Delete staging files older than 24h |
| `IndexSync` | Cron | Every 5m | Ensure OpenSearch index is in sync (reconciliation) |

## Job Data Model

```
JobDefinition
|-- id: UUID PK
|-- type: VARCHAR(100) { EXPORT, IMPORT, CLEANUP, DIGEST, ... }
|-- workspace_id: UUID (nullable)
|-- payload: JSONB (job-specific parameters)
|-- priority: INT (0-100, higher = more important)
|-- max_retries: INT (default 3)
|-- retry_delay_seconds: INT (default 60)
|-- created_at: TIMESTAMPTZ
+-- updated_at: TIMESTAMPTZ

JobExecution
|-- id: UUID PK
|-- job_definition_id: UUID FK
|-- status: VARCHAR(20) { QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED }
|-- worker_id: VARCHAR(100) (which instance picked it up)
|-- started_at: TIMESTAMPTZ
|-- completed_at: TIMESTAMPTZ
|-- error_message: TEXT
|-- progress: INT (0-100)
|-- result: JSONB (job-specific output)
+-- created_at: TIMESTAMPTZ
```

## Execution Model

```
Enqueue:
  JobService.create(type, payload, priority)
    -> INSERT job_definition
    -> Redis: LPUSH queue:{type} job_id
    -> Return { jobId }

Dequeue:
  Worker: BLPOP queue:{type} timeout=5s
    -> SELECT ... FOR UPDATE SKIP LOCKED
    -> INSERT job_execution (status=RUNNING)
    -> Execute job handler
    -> UPDATE status = SUCCESS | FAILED
    -> On FAILED: if retries < max_retries -> re-enqueue after delay
    -> On FAILED: if retries >= max_retries -> move to dead letter queue
```

## Worker Configuration

```yaml
background-jobs:
  workers:
    export:
      concurrency: 5
      max-execution-minutes: 30
      retry-delay-seconds: 60
    import:
      concurrency: 3
      max-execution-minutes: 60
    cleanup:
      concurrency: 1
      cron: "0 3 * * *"
    digest:
      concurrency: 1
      cron: "0 * * * *"  # hourly
```

## Graceful Shutdown

```
SIGTERM -> JobService:
  1. Stop accepting new jobs (consumer paused)
  2. Wait for running jobs to complete (max 30s timeout)
  3. Re-queue jobs that didn't complete -> re-enqueue
  4. Close Redis and Kafka connections
```

## API Endpoints

```
POST   /jobs/export                -> Enqueue export job
POST   /jobs/import                -> Enqueue import job
GET    /jobs/:jobId                -> Check job status + progress
GET    /jobs?type=&status=         -> List jobs (paginated, filtered)
POST   /jobs/:jobId/cancel         -> Cancel queued job
GET    /jobs/dead-letters          -> List dead letters
POST   /jobs/dead-letters/:id/retry -> Retry dead letter
```

## Performance Considerations

- **Concurrency control**: Per-queue thread pool limits; Semaphore per job type
- **Job persistence**: Jobs survive worker restart (PostgreSQL + Redis persistence)
- **Rate limiting**: Max 100 export jobs per workspace per hour
- **Progress reporting**: Job updates progress to Redis every 5s; client polls
- **Dead letter alert**: Prometheus alert when dead letter queue exceeds threshold
- **Database cleanup**: Job executions older than 30 days archived to MinIO Parquet
