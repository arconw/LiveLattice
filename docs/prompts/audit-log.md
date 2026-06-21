# Stage 11: Audit Log

## Objective

Implement an immutable, tamper-evident audit log with SHA-256 hash chaining, partitioned storage, monthly retention management, and export to Parquet.

## Requirements

1. Initialize Spring Boot project in `services/audit-log/` with:
   - Spring Data JPA, Kafka consumer, PostgreSQL (partitioned)
   - MinIO client for Parquet archives
2. Implement Kafka consumer:
   - Consume all domain events (workspace, member, canvas, dashboard, auth events)
   - Map each event to `AuditEvent` entity
   - Compute SHA-256 hash chain
3. Implement hash chaining:
   - Each event stores `previous_hash` and its own `hash`
   - Genesis block: previous_hash = 64 zero bytes
   - hash = SHA-256(id + action + target_id + changes_json + previous_hash)
4. Implement partitioned storage:
   - `audit_events` table partitioned by `RANGE (occurred_at)` monthly
   - Auto-create partitions with pg_partman (or manual function)
5. Implement query API:
   - `GET /audit-log?filters` - paginated, filterable by workspace_id, action, actor_id, target_type, date range
   - Composite index `(workspace_id, occurred_at DESC)` for performance
6. Implement chain verification:
   - `GET /audit-log/verify` - recompute hash chain and compare with stored hashes
   - Batch verification in chunks of 1000
   - Return `{ valid, firstInvalidId, firstInvalidHash }`
7. Implement retention:
   - Nightly cron job (03:00) detaches partitions past retention period
   - Exports detached partition to Parquet format via Apache Parquet library
   - Uploads Parquet to MinIO `audit-archive/` bucket
   - Drops detached partition from PostgreSQL
8. Implement export:
   - `POST /audit-log/export` - async export to CSV or Parquet
   - Job tracking via Redis + PostgreSQL
9. Write unit and integration tests with Testcontainers

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Audit events must be immutable (no UPDATE, only INSERT)
- Partition management must be automated (no manual DDL)

## Verification

```bash
# Query audit log
curl "http://localhost:8084/audit-log?workspace_id=ws-123&action=canvas.create&page=1&size=20"

# Get single event
curl http://localhost:8084/audit-log/event-uuid

# Verify hash chain
curl http://localhost:8084/audit-log/verify

# Request export
curl -X POST http://localhost:8084/audit-log/export \
  -H "Content-Type: application/json" \
  -d '{"workspaceId":"ws-123","from":"2026-01-01","to":"2026-06-01","format":"parquet"}'

# Run tests
cd services/audit-log && ./gradlew test
```
