CREATE TABLE IF NOT EXISTS data_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    config JSONB NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_data_sources_workspace_id ON data_sources(workspace_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS dashboards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    layout JSONB NOT NULL,
    time_range JSONB NOT NULL,
    auto_refresh INT NOT NULL DEFAULT 0,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_dashboards_workspace_id ON dashboards(workspace_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS widgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dashboard_id UUID NOT NULL REFERENCES dashboards(id),
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    data_source_id UUID REFERENCES data_sources(id),
    query JSONB NOT NULL,
    options JSONB NOT NULL,
    position JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_widgets_dashboard_id ON widgets(dashboard_id);

INSERT INTO role_permissions (role, permission)
SELECT * FROM (VALUES
    ('OWNER', 'dashboard:read'),
    ('OWNER', 'dashboard:create'),
    ('OWNER', 'dashboard:edit'),
    ('OWNER', 'dashboard:delete'),
    ('OWNER', 'widget:read'),
    ('OWNER', 'widget:create'),
    ('OWNER', 'widget:edit'),
    ('OWNER', 'widget:delete'),
    ('OWNER', 'data_source:read'),
    ('OWNER', 'data_source:create'),
    ('OWNER', 'data_source:edit'),
    ('OWNER', 'data_source:delete'),
    ('ADMIN', 'dashboard:read'),
    ('ADMIN', 'dashboard:create'),
    ('ADMIN', 'dashboard:edit'),
    ('ADMIN', 'dashboard:delete'),
    ('ADMIN', 'widget:read'),
    ('ADMIN', 'widget:create'),
    ('ADMIN', 'widget:edit'),
    ('ADMIN', 'widget:delete'),
    ('ADMIN', 'data_source:read'),
    ('ADMIN', 'data_source:create'),
    ('ADMIN', 'data_source:edit'),
    ('ADMIN', 'data_source:delete'),
    ('EDITOR', 'dashboard:read'),
    ('EDITOR', 'dashboard:create'),
    ('EDITOR', 'dashboard:edit'),
    ('EDITOR', 'dashboard:delete'),
    ('EDITOR', 'widget:read'),
    ('EDITOR', 'widget:create'),
    ('EDITOR', 'widget:edit'),
    ('EDITOR', 'widget:delete'),
    ('EDITOR', 'data_source:read'),
    ('EDITOR', 'data_source:create'),
    ('EDITOR', 'data_source:edit'),
    ('EDITOR', 'data_source:delete'),
    ('VIEWER', 'dashboard:read'),
    ('VIEWER', 'widget:read'),
    ('VIEWER', 'data_source:read')
) AS data(role, permission)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role = data.role AND rp.permission = data.permission
);
