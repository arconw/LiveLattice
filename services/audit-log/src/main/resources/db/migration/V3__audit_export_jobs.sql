CREATE TABLE IF NOT EXISTS audit_export_jobs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL,
    from_time TIMESTAMPTZ NOT NULL,
    to_time TIMESTAMPTZ NOT NULL,
    format VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    artifact_path VARCHAR(500),
    error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_export_jobs_status_created
    ON audit_export_jobs (status, created_at);
