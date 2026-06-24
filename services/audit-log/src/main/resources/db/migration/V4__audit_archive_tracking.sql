CREATE TABLE IF NOT EXISTS audit_archived_partitions (
    partition_name VARCHAR(64) PRIMARY KEY,
    record_count BIGINT NOT NULL DEFAULT 0,
    artifact_path VARCHAR(500),
    archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
