CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES users(id),
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    permissions TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_api_keys_workspace_id ON api_keys(workspace_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_creator_id ON api_keys(creator_id);

INSERT INTO role_permissions (role, permission)
SELECT * FROM (VALUES
    ('OWNER', 'api_key:read'),
    ('OWNER', 'api_key:create'),
    ('OWNER', 'api_key:revoke'),
    ('ADMIN', 'api_key:read'),
    ('ADMIN', 'api_key:create'),
    ('ADMIN', 'api_key:revoke')
) AS data(role, permission)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role = data.role AND rp.permission = data.permission
);
