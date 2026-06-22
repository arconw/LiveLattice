CREATE DATABASE IF NOT EXISTS livelattice;

CREATE TABLE IF NOT EXISTS livelattice.canvas_events (
    event_id UUID,
    workspace_id UUID,
    event_type LowCardinality(String),
    payload String,
    timestamp DateTime64(3, 'UTC'),
    inserted_at DateTime DEFAULT now()
)
ENGINE = MergeTree()
ORDER BY (workspace_id, event_type, timestamp)
PARTITION BY toYYYYMMDD(timestamp)
TTL timestamp + INTERVAL 90 DAY;

CREATE TABLE IF NOT EXISTS livelattice.dashboard_queries (
    query_id UUID,
    widget_id UUID,
    dashboard_id UUID,
    query_text String,
    executed_at DateTime64(3, 'UTC'),
    duration_ms UInt64,
    rows_returned UInt64
)
ENGINE = MergeTree()
ORDER BY (dashboard_id, executed_at)
PARTITION BY toYYYYMMDD(executed_at)
TTL executed_at + INTERVAL 30 DAY;
