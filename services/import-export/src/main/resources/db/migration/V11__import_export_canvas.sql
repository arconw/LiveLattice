CREATE TABLE IF NOT EXISTS import_export_canvas (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_json TEXT NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_import_export_canvas_workspace_id ON import_export_canvas(workspace_id) WHERE deleted_at IS NULL;
