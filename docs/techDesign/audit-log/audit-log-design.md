# Audit Log - Technical Design

## Responsibilities

- Immutable record of all state-changing operations
- Tamper-evident logging with hash chaining
- Compliance (SOC 2, GDPR) support
- Queryable audit trail with filtering and export
- Retention management (partitioned archival)

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline; exact patch version is pinned during implementation
- **Storage**: PostgreSQL (partitioned by month) + MinIO (Parquet archives)
- **Events**: Kafka consumer for all domain events
- **Hashing**: SHA-256 hash chain for tamper evidence
- **Export**: Async export to CSV/Parquet
- **Query**: Spring Data JPA with pagination and filtering

## Data Model

```
AuditEvent
|-- id: UUID PK (KSUID for chronological ordering)
|-- workspace_id: UUID FK
|-- actor_id: UUID (user who performed action)
|-- actor_ip: INET
|-- actor_user_agent: VARCHAR(500)
|-- action: VARCHAR(100) {
|     workspace.create, workspace.update, workspace.delete,
|     member.invite, member.role_change, member.remove,
|     canvas.create, canvas.update, canvas.delete, canvas.restore,
|     canvas.export, canvas.import,
|     dashboard.create, dashboard.update, dashboard.delete,
|     data_source.create, data_source.update, data_source.delete,
|     auth.login, auth.logout, auth.mfa_enable, auth.mfa_disable,
|     api_key.create, api_key.revoke,
|     settings.update, tier.change
|   }
|-- target_type: VARCHAR(50) { workspace, canvas, dashboard, member, data_source, api_key }
|-- target_id: UUID (the resource that was changed)
|-- changes: JSONB {
|     before: { ... },  (previous state, partial)
|     after: { ... },   (new state, partial)
|     diff: { ... }     (RFC 6902 JSON Patch)
|   }
|-- metadata: JSONB {
|     correlation_id, trace_id, session_id, request_id
|   }
|-- previous_hash: VARCHAR(64) (SHA-256 of previous event)
|-- hash: VARCHAR(64) (SHA-256 of this event)
|-- occurred_at: TIMESTAMPTZ
+-- PARTITION BY RANGE (occurred_at)
```

## Hash Chain

```
event_1: hash = SHA256( id + action + target_id + changes + prev_hash )
  where prev_hash = "0000...0000" (genesis block)

event_2: hash = SHA256( id + action + target_id + changes + event_1.hash )
event_3: hash = SHA256( id + action + target_id + changes + event_2.hash )
...

Verification: recompute chain -> assert hash matches stored hash
             -> assert no gaps in sequence
```

## Processing Pipeline

```
Kafka Domain Event -> AuditLogConsumer
  -> Map event -> AuditEvent entity
  -> Compute hash: SHA-256( concatenated fields + previous_hash )
  -> Insert into partitioned audit_events table
  -> If retention exceeded -> partition is detached (monthly cron job)
  -> Detached partitions exported to Parquet -> MinIO -> partition dropped from PG
```

## Query API

```
GET /audit-log?workspace_id=abc&action=canvas.update&actor_id=xyz
              &target_type=canvas&target_id=uuid
              &from=2024-01-01&to=2024-06-01
              &page=1&size=50
              &export=csv (optional: returns file download)
```

## API Endpoints

```
GET    /audit-log                  -> Query audit log (paginated, filtered)
GET    /audit-log/:id              -> Single event details
GET    /audit-log/verify           -> Verify hash chain integrity
POST   /audit-log/export           -> Async export to CSV/Parquet
GET    /audit-log/exports/:jobId   -> Check export status
```

## Retention Policy

| Tier | Hot (PostgreSQL) | Cold (MinIO Parquet) | Total |
|---|---|---|---|
| Free | 30 days | 90 days | 120 days |
| Pro | 90 days | 1 year | 1 year |
| Enterprise | 1 year | 3 years | 3 years |

## Performance Considerations

- **Partitioning**: Monthly range partitions; auto-create with pg_partman; detach after retention
- **Batch insert**: Write to PostgreSQL in batches of 100, flush every 500ms
- **Query optimization**: Composite index `(workspace_id, occurred_at DESC)` + partial indexes on `action` and `target_type`
- **Export**: Async; reads from partition directly via COPY TO; converts to Parquet with Apache Parquet library
- **Hash computation**: Single SHA-256 per event; < 1µs overhead per event
- **Verify endpoint**: Batch verification in chunks of 1000; returns { valid, firstInvalidId, firstInvalidHash }
