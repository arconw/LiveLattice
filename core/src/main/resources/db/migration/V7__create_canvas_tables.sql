CREATE TABLE IF NOT EXISTS canvases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    title VARCHAR(255) NOT NULL,
    content JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 1,
    lock_version INT NOT NULL DEFAULT 0,
    snapshot_version BIGINT,
    operation_count_since_snapshot INT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_canvases_workspace_id ON canvases(workspace_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_canvases_created_by ON canvases(created_by);
CREATE INDEX IF NOT EXISTS idx_canvases_content_gin ON canvases USING GIN (content);

CREATE TABLE IF NOT EXISTS canvas_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canvas_id UUID NOT NULL REFERENCES canvases(id),
    version BIGINT NOT NULL,
    content JSONB,
    minio_path VARCHAR(500),
    created_by UUID NOT NULL REFERENCES users(id),
    snapshot_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(canvas_id, version)
);

CREATE INDEX IF NOT EXISTS idx_canvas_snapshots_canvas_id ON canvas_snapshots(canvas_id, version DESC);

CREATE TABLE IF NOT EXISTS comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canvas_id UUID NOT NULL REFERENCES canvases(id),
    parent_id UUID REFERENCES comments(id),
    author_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by UUID REFERENCES users(id),
    resolved_at TIMESTAMP WITH TIME ZONE,
    target_element_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_comments_canvas_id ON comments(canvas_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_comments_parent_id ON comments(parent_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS canvas_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id),
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50),
    thumbnail VARCHAR(500),
    content JSONB NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_canvas_templates_workspace_id ON canvas_templates(workspace_id);
CREATE INDEX IF NOT EXISTS idx_canvas_templates_category ON canvas_templates(category);
