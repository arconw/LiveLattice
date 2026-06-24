CREATE TABLE IF NOT EXISTS audit_events (
    id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    changes JSONB NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}',
    previous_hash VARCHAR(64) NOT NULL,
    hash VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE TABLE IF NOT EXISTS audit_events_default PARTITION OF audit_events
    DEFAULT;

CREATE INDEX IF NOT EXISTS idx_audit_workspace_occurred
    ON audit_events (workspace_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_action
    ON audit_events (action);

CREATE INDEX IF NOT EXISTS idx_audit_target_type
    ON audit_events (target_type);

CREATE INDEX IF NOT EXISTS idx_audit_actor
    ON audit_events (actor_id);
