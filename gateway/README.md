# Gateway

LiveLattice API Gateway is a NestJS/Fastify service that fronts backend services in the local Docker Compose stack and owns the public auth boundary.

## Current responsibilities

- Exposes `/health`, `/ready`, and `/metrics`.
- Adds request correlation through `x-request-id`.
- Applies fixed-window request rate limiting.
- Exchanges login, refresh, and logout requests with Keycloak.
- Validates bearer JWTs with Keycloak JWKS and caches JWKS/session data.
- Provisions users in Core through the internal auth flow after login and refresh.
- Protects `/api/core/*` by default after auth.
- Strips client-supplied trusted internal headers and injects trusted identity headers after validation.
- Proxies `X-API-Key` requests to Core so Core can validate workspace service tokens.
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
- `AUTH_REQUIRED` defaults to `true`.
- `AUTH_JWKS_URI`, `AUTH_ISSUER`, `AUTH_TOKEN_ENDPOINT`, and `AUTH_LOGOUT_ENDPOINT` configure Keycloak integration.
- `AUTH_CLIENT_ID`, `CORE_PROVISION_URL`, `INTERNAL_AUTH_SECRET`, and Redis cache settings configure the Gateway/Core auth bridge.
- `RATE_LIMIT_WINDOW_MS` defaults to `60000`.
- `RATE_LIMIT_MAX` defaults to `120`.
- `CORE_URL`, `SEARCH_URL`, `NOTIFICATIONS_URL`, `IMPORT_EXPORT_URL`, `AUDIT_LOG_URL`, `BACKGROUND_JOBS_URL`, and `REALTIME_URL` override upstream targets.

## Commands

```bash
npm install
npm test
npm start
```

The Dockerfile runs the gateway test suite during image build.
