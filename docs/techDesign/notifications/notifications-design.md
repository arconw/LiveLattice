# Notifications - Technical Design

## Responsibilities

- Deliver in-app notifications (toast, bell icon)
- Send email notifications (SMTP / SendGrid / SES)
- Send webhook notifications to external integrations
- Template-based notification rendering
- Notification preferences per user
- Delivery status tracking and retry

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline; exact patch version is pinned during implementation
- **Message bus**: Kafka consumer (domain events -> notifications)
- **Email**: Spring Mail + SendGrid API (or SES)
- **Templates**: Thymeleaf (HTML email templates)
- **Webhooks**: `RestTemplate` with retry + circuit breaker
- **Storage**: PostgreSQL for notification records + preferences
- **In-app delivery**: Redis pub/sub -> WebSocket (realtime service)

## Notification Types

| Type | Channel | Template |
|---|---|---|
| `member.invited` | In-app, Email | "You've been invited to {workspace}" |
| `member.joined` | In-app | "{user} joined {workspace}" |
| `canvas.shared` | In-app, Email | "{user} shared a canvas with you" |
| `canvas.comment` | In-app, Email (digest) | "{user} commented on {canvas}" |
| `canvas.@mention` | In-app, Email | "{user} mentioned you in {canvas}" |
| `canvas.export.complete` | In-app | "Your export is ready for download" |
| `canvas.import.complete` | In-app | "Your import completed successfully" |
| `dashboard.shared` | In-app, Email | "{user} shared a dashboard with you" |
| `workspace.quota.warning` | In-app, Email | "You've reached 90% of your workspace quota" |
| `system.announcement` | In-app, Email | Platform announcements |

## Data Model

```
Notification
|-- id: UUID PK
|-- workspace_id: UUID (nullable for system-wide)
|-- recipient_id: UUID FK
|-- type: VARCHAR(50)
|-- title: VARCHAR(255)
|-- body: TEXT
|-- data: JSONB { canvasId, dashboardId, actorId, actionUrl }
|-- channel: VARCHAR(20) { IN_APP, EMAIL, WEBHOOK }
|-- status: VARCHAR(20) { PENDING, SENT, DELIVERED, READ, FAILED }
|-- read_at: TIMESTAMPTZ
|-- created_at: TIMESTAMPTZ
+-- updated_at: TIMESTAMPTZ

NotificationPreferences
|-- user_id: UUID PK
|-- email_digest: VARCHAR(10) { INSTANT, HOURLY, DAILY, NEVER }
|-- webhooks: JSONB [{ url, secret, events: string[] }]
|-- muted_types: TEXT[] (types user has muted)
+-- updated_at: TIMESTAMPTZ
```

## Processing Pipeline

```
Kafka Event -> NotificationConsumer
  -> Resolve recipients (event-specific: canvas members, workspace admins, etc.)
  -> For each recipient:
    -> Check NotificationPreferences:
      - Muted type? -> skip
      - Email channel: digest or instant?
      - In-app: always deliver
      - Webhook: configured?
    -> Render notification (title, body, data) from template
    -> Insert into notification table
    -> If in-app: publish to Redis pub/sub -> realtime service -> push to client
    -> If email (instant): send via SendGrid (async)
    -> If webhook: enqueue to webhook delivery queue
```

## Webhook Delivery

```
WebhookQueue -> WebhookWorker
  -> POST to configured URL with HMAC signature header
  -> On 2xx: mark delivered
  -> On 4xx: mark failed (don't retry)
  -> On 5xx/timeout: retry with backoff (1m, 5m, 15m, 1h, max 5 attempts)
  -> All failed -> mark dead letter, alert admin
```

## API Endpoints

```
GET    /notifications                -> List notifications (paginated, filterable)
PATCH  /notifications/:id/read       -> Mark as read
POST   /notifications/read-all       -> Mark all as read
GET    /notifications/unread-count   -> Unread badge count

GET    /notification-preferences     -> Get preferences
PATCH  /notification-preferences     -> Update preferences
POST   /notification-preferences/webhooks -> Add webhook
DELETE /notification-preferences/webhooks/:id -> Remove webhook
```

## Performance Considerations

- **Bulk insert**: Batch notification inserts (50 per batch), flushed every 1s
- **Email digest**: Hourly/daily digest groups notifications by user, sends single email
- **Webhook throttling**: Max 10 webhooks per user, 1000 deliveries per hour per workspace
- **Redis stream**: In-app notifications published via Redis stream -> realtime service consumes
- **Template caching**: Thymeleaf templates cached in memory; pre-compiled at startup
- **Retry queue**: Failed webhooks stored in PostgreSQL with exponential backoff; processed by scheduled job
