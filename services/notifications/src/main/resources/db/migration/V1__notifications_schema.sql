CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    workspace_id UUID,
    recipient_id UUID NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    action_url TEXT,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications (recipient_id, channel, read_at) WHERE read_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_workspace_created ON notifications (workspace_id, created_at DESC);

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id UUID PRIMARY KEY,
    email_digest VARCHAR(10) NOT NULL,
    muted_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    webhooks JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS notification_delivery_attempts (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    target_url TEXT,
    status VARCHAR(20) NOT NULL,
    attempt_number INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_due ON notification_delivery_attempts (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_notification_delivery_notification ON notification_delivery_attempts (notification_id);
