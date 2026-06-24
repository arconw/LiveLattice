ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_audit_ingested_at
    ON audit_events (ingested_at DESC, id DESC);
