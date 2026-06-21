# Stage 10: Notifications

## Objective

Implement the notification delivery service with in-app, email, and webhook channels, template-based rendering, digest grouping, and delivery tracking.

## Requirements

1. Initialize Spring Boot project in `services/notifications/` with:
   - Spring Mail + SendGrid SDK (or SES via Jakarta Mail)
   - Kafka consumer for domain events
   - Thymeleaf for email templates
   - Redis for in-app notification stream
2. Implement Kafka consumer:
   - Consume domain events: `member.invited`, `canvas.comment`, `canvas.@mention`, `canvas.export.complete`, `workspace.quota.warning`, `system.announcement`
   - Resolve recipients based on event type:
     - `member.invited` -> invited user
     - `canvas.comment` -> canvas author + comment participants
     - `canvas.@mention` -> mentioned users
     - `canvas.export.complete` -> requesting user
     - `workspace.quota.warning` -> workspace admins
   - Check notification preferences before delivering
3. Implement `NotificationStore`:
   - `notification` table with status tracking
   - Batch insert (50 per batch, flush every 1s)
   - Query endpoints with pagination and unread count
4. Implement Template Engine:
   - Thymeleaf HTML email templates (pre-compiled, cached)
   - In-app notification payloads (title, body, actionUrl, data)
5. Implement delivery channels:
   - **In-app**: publish to Redis stream -> Realtime service -> push to client
   - **Email**: Send via SendGrid (instant) or group into hourly/daily digest
   - **Webhook**: HTTP POST with HMAC signature (Retry: 1m, 5m, 15m, 1h, max 5)
6. Implement `DigestManager`:
   - Hourly digest: group notifications by user for the past hour
   - Daily digest: group notifications by user for the past 24h
   - Single email with all notifications grouped by type
7. Implement `NotificationPreferences`:
   - Email digest frequency (instant, hourly, daily, never)
   - Muted notification types
   - Webhook URL management (max 10 per user)
8. Write unit and integration tests with Testcontainers (Redis, Kafka, GreenMail)

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Webhook delivery must use exponential backoff retry
- Email templates must be pre-compiled (not rendered on every send)

## Verification

```bash
# Check unread count
curl http://localhost:8082/notifications/unread-count \
  -H "x-user-id: user-123"

# List notifications
curl http://localhost:8082/notifications?page=1&size=20 \
  -H "x-user-id: user-123"

# Get preferences
curl http://localhost:8082/notification-preferences \
  -H "x-user-id: user-123"

# Update preferences
curl -X PATCH http://localhost:8082/notification-preferences \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"emailDigest":"daily"}'

# Run tests
cd services/notifications && ./gradlew test
```
