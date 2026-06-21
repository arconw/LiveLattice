# Data Architecture

## Storage Strategy

| Store | Purpose | Technology |
|---|---|---|
| **PostgreSQL** | Entity state, transactional data, JSONB for flexible schemas | 16+ replicas, PgBouncer |
| **ClickHouse / TimescaleDB** | Time-series metrics, analytics, dashboard data | Columnar, sharded, replicated |
| **Redis** | Session cache, rate-limit counters, pub/sub, presence | Cluster mode, AOF persistence |
| **OpenSearch** | Full-text search, suggest, highlight | 3+ node cluster, ILM policies |
| **MinIO** | Canvas snapshots, exported files, asset uploads | S3 API, erasure coding |

## PostgreSQL Schema Design

### Multi-Tenancy
- **Isolation model**: Row-level security (RLS) with `workspace_id` column on every entity table
- **Connection pooling**: PgBouncer with transaction-level pooling, one pool per workspace tier
- **Migration tool**: Flyway, versioned, repeatable migrations in monorepo `migrations/` directory

### Key Tables

```
workspaces
  id UUID PK, slug VARCHAR UNIQUE, name TEXT, settings JSONB, tier VARCHAR, created_at, updated_at

users
  id UUID PK, email VARCHAR UNIQUE, display_name TEXT, auth_provider VARCHAR, auth_sub VARCHAR
  UNIQUE(auth_provider, auth_sub)

workspace_members
  workspace_id UUID FK, user_id UUID FK, role VARCHAR, joined_at
  PK(workspace_id, user_id)

canvases
  id UUID PK, workspace_id UUID FK, title TEXT, content JSONB, version BIGINT, lock_version INT
  created_by UUID FK, updated_by UUID FK, created_at, updated_at

canvas_snapshots
  id UUID PK, canvas_id UUID FK, version BIGINT, content JSONB, snapshot_at TIMESTAMPTZ

dashboards
  id UUID PK, workspace_id UUID FK, title TEXT, layout JSONB, created_at, updated_at

widgets
  id UUID PK, dashboard_id UUID FK, type VARCHAR, data_source TEXT, query JSONB, position JSONB

data_sources
  id UUID PK, workspace_id UUID FK, type VARCHAR, config JSONB (encrypted), created_at

audit_events
  id UUID PK, workspace_id UUID FK, actor_id UUID, action VARCHAR, target_type VARCHAR
  target_id UUID, changes JSONB, occurred_at TIMESTAMPTZ
  PARTITION BY RANGE (occurred_at)
```

### Indexing Strategy
- B-tree on all FK columns and `(workspace_id, created_at DESC)` for listing
- GIN on JSONB columns (`content`, `settings`) for partial index queries
- Partial index on `canvases WHERE NOT deleted`
- BRIN on time-series tables for large append-only tables
- Covering indexes for frequent query patterns

## ClickHouse Schema

```sql
CREATE TABLE canvas_events (
    event_id UUID,
    workspace_id UUID,
    canvas_id UUID,
    user_id UUID,
    event_type LowCardinality(String),
    payload String,
    timestamp DateTime64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (workspace_id, canvas_id, timestamp);

CREATE TABLE dashboard_queries (
    query_id UUID,
    dashboard_id UUID,
    widget_id UUID,
    duration_ms UInt32,
    rows_returned UInt32,
    queried_at DateTime64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(queried_at)
ORDER BY (dashboard_id, queried_at);
```

## Caching Strategy

| Cache | Type | TTL | Invalidation |
|---|---|---|---|
| Session tokens | Redis String | 15m | On logout / refresh |
| Workspace members | Redis Hash | 5m | On membership change (pub/sub) |
| Canvas content | Redis String (compressed) | 1m | On save event |
| Dashboard query results | Redis String | 30s | On data source change |
| Rate limit counters | Redis Sorted Set | sliding window | Auto-expire |

### Cache-Aside Pattern
```
GET -> check Redis -> hit -> return
                  -> miss -> query PG -> set Redis w/ TTL -> return
WRITE -> write PG -> publish invalidation -> delete cache key
```

## Partitioning & Sharding

- **Audit log**: monthly range partitions in PostgreSQL, auto-create with pg_partman
- **Canvas events**: monthly partitions in ClickHouse by `toYYYYMM(timestamp)`
- **Workspaces**: hash-based shard key in Kafka topics (`workspace_id % partitions`)

## Data Retention

| Data | Retention | Action |
|---|---|---|
| Audit events | 1 year (hot), 3 years (cold in MinIO Parquet) | Partition detach -> export -> drop |
| Canvas snapshots | 90 days | Delete after threshold |
| Dashboard query logs | 30 days | TTL in ClickHouse |
| Session tokens | 24 hours | Redis key TTL |
| Deleted workspaces | 30 days grace | Soft delete -> hard delete |
