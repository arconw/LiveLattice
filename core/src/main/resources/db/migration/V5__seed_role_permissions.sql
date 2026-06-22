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
    ('EDITOR', 'workspace:read'),
    ('EDITOR', 'member:read'),
    ('EDITOR', 'canvas:create'),
    ('EDITOR', 'canvas:edit'),
    ('EDITOR', 'canvas:delete'),
    ('EDITOR', 'canvas:read'),
    ('VIEWER', 'workspace:read'),
    ('VIEWER', 'member:read'),
    ('VIEWER', 'canvas:read')
) AS data(role, permission)
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role = data.role AND rp.permission = data.permission
);
