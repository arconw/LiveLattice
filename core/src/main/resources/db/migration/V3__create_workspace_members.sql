CREATE TABLE IF NOT EXISTS workspace_members (
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspaces(id),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (workspace_id, user_id)
);
