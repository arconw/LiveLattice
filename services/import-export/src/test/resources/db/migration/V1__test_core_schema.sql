CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    external_subject TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    tier TEXT NOT NULL DEFAULT 'FREE',
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS workspace_members (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    invited_by UUID REFERENCES users(id),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (workspace_id, user_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role VARCHAR(20) NOT NULL,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role, permission)
);

INSERT INTO role_permissions (role, permission)
SELECT * FROM (VALUES
    ('OWNER', 'workspace:delete'),
    ('OWNER', 'workspace:update'),
    ('OWNER', 'workspace:read'),
    ('OWNER', 'member:add'),
    ('OWNER', 'member:remove'),
    ('OWNER', 'member:change_role'),
    ('OWNER', 'member:read'),
    ('OWNER', 'canvas:create'),
    ('OWNER', 'canvas:edit'),
    ('OWNER', 'canvas:delete'),
    ('OWNER', 'canvas:read'),
    ('OWNER', 'dashboard:read'),
    ('OWNER', 'dashboard:create'),
    ('OWNER', 'dashboard:edit'),
    ('OWNER', 'dashboard:delete'),
    ('ADMIN', 'workspace:update'),
    ('ADMIN', 'workspace:read'),
    ('ADMIN', 'member:add'),
    ('ADMIN', 'member:remove'),
    ('ADMIN', 'member:change_role'),
    ('ADMIN', 'member:read'),
    ('ADMIN', 'canvas:create'),
    ('ADMIN', 'canvas:edit'),
    ('ADMIN', 'canvas:delete'),
    ('ADMIN', 'canvas:read'),
    ('ADMIN', 'dashboard:read'),
    ('ADMIN', 'dashboard:create'),
    ('ADMIN', 'dashboard:edit'),
    ('ADMIN', 'dashboard:delete'),
    ('EDITOR', 'workspace:read'),
    ('EDITOR', 'member:read'),
    ('EDITOR', 'canvas:create'),
    ('EDITOR', 'canvas:edit'),
    ('EDITOR', 'canvas:delete'),
    ('EDITOR', 'canvas:read'),
    ('EDITOR', 'dashboard:read'),
    ('EDITOR', 'dashboard:create'),
    ('EDITOR', 'dashboard:edit'),
    ('EDITOR', 'dashboard:delete'),
    ('VIEWER', 'workspace:read'),
    ('VIEWER', 'member:read'),
    ('VIEWER', 'canvas:read'),
    ('VIEWER', 'dashboard:read')
) AS data(role, permission)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role = data.role AND rp.permission = data.permission
);

CREATE TABLE IF NOT EXISTS canvases (
    id UUID PRIMARY KEY,
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

CREATE TABLE IF NOT EXISTS dashboards (
    id UUID PRIMARY KEY,
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

CREATE TABLE IF NOT EXISTS widgets (
    id UUID PRIMARY KEY,
    dashboard_id UUID NOT NULL REFERENCES dashboards(id),
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    data_source_id UUID,
    query JSONB NOT NULL,
    options JSONB NOT NULL,
    position JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
