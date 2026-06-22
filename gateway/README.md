# Gateway

LiveLattice API Gateway is a NestJS/Fastify service that fronts backend services in the local Docker Compose stack.

## Current responsibilities

- Exposes `/health`, `/ready`, and `/metrics`.
- Adds request correlation through `x-request-id`.
- Applies fixed-window request rate limiting.
- Supports optional JWT validation through a JWKS endpoint.
- Proxies `/api/:service/*` to backend services by service name.

## Local routes

- `/api/core/*` -> Core service.
- `/api/search/*` -> Search service.
- `/api/notifications/*` -> Notifications service.
- `/api/import-export/*` -> Import/export service.
- `/api/audit-log/*` -> Audit log service.
- `/api/background-jobs/*` -> Background jobs service.
- `/api/realtime/*` -> Realtime service.

## Environment

- `PORT` defaults to `3000`.
- `AUTH_REQUIRED` defaults to `false` for local development.
- `AUTH_JWKS_URI`, `AUTH_ISSUER`, and `AUTH_AUDIENCE` configure JWT validation when auth is required.
- `RATE_LIMIT_WINDOW_MS` defaults to `60000`.
- `RATE_LIMIT_MAX` defaults to `120`.
- `CORE_URL`, `SEARCH_URL`, `NOTIFICATIONS_URL`, `IMPORT_EXPORT_URL`, `AUDIT_LOG_URL`, `BACKGROUND_JOBS_URL`, and `REALTIME_URL` override upstream targets.

## Commands

```bash
npm install
npm test
npm start
```
