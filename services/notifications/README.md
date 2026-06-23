# Notifications

Spring Boot notification service for LiveLattice.

## Endpoints

- `GET /health`
- `GET /ready`
- `GET /notifications`
- `POST /notifications`
- `PATCH /notifications/{id}/read`
- `POST /notifications/read-all`
- `GET /notifications/unread-count`
- `GET /notification-preferences`
- `PATCH /notification-preferences`
- `POST /notification-preferences/webhooks`
- `DELETE /notification-preferences/webhooks/{id}`

## Runtime

Configuration is environment-driven:

- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`
- `KAFKA_BROKERS`
- `NOTIFICATIONS_KAFKA_TOPICS`
- `NOTIFICATIONS_KAFKA_OUTPUT_TOPIC`
- `INTERNAL_AUTH_SECRET`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`

Kafka events create in-app, email, and webhook notification records after preference checks. In-app delivery publishes to a Redis stream, instant email uses Spring Mail with cached Thymeleaf templates, and webhook delivery records attempts with retry backoff.

## Verification

```bash
gradle test
docker build -t livelattice-notifications services/notifications
```
