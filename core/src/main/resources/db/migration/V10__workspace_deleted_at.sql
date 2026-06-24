ALTER TABLE workspaces ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_workspaces_deleted_at ON workspaces(deleted_at);
CREATE INDEX IF NOT EXISTS idx_workspaces_active_slug ON workspaces(slug) WHERE deleted_at IS NULL;
