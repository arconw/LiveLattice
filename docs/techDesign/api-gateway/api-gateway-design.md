# API Gateway - Technical Design

## Responsibilities

- Terminate TLS, validate JWT, rate-limit, route requests to downstream services
- Expose REST endpoints (port 3000) and WebSocket endpoint (port 3001)
- Aggregate responses from multiple services where needed (BFF pattern)

## Technology Stack

- **Runtime**: Node.js 24 LTS
- **Framework**: NestJS 11 with Fastify adapter
- **Auth**: `@nestjs/passport` + `passport-jwt` strategy; Keycloak JWKS endpoint
- **Rate limiting**: `@nestjs/throttler` with Redis store
- **HTTP client**: `@nestjs/axios` with circuit breaker (Resilience4j for Node via `opossum`)
- **Validation**: `class-validator` + `class-transformer` with auto-serialization
- **OpenAPI**: `@nestjs/swagger` for auto-generated spec

## Key Modules

| Module | Responsibility |
|---|---|
| `AuthModule` | JWT validation, API key validation, scope extraction |
| `RateLimitModule` | Token bucket per user/workspace/IP, Redis-backed |
| `ProxyModule` | Dynamic routing to core, search, notifications, etc. |
| `AggregationModule` | BFF endpoints composite multiple downstream calls |
| `MetricsModule` | Prometheus histograms on every route |

## Endpoints

```
POST   /auth/login              -> Keycloak token exchange
POST   /auth/refresh            -> Refresh token rotation
POST   /auth/logout             -> Invalidate tokens

GET    /workspaces              -> Core
POST   /workspaces              -> Core
GET    /workspaces/:id          -> Core
PATCH  /workspaces/:id          -> Core
DELETE /workspaces/:id          -> Core
GET    /workspaces/:id/members  -> Core

GET    /canvases                -> Core
POST   /canvases                -> Core
GET    /canvases/:id            -> Core
PATCH  /canvases/:id            -> Core (includes content)
DELETE /canvases/:id            -> Core

GET    /dashboards              -> Core
POST   /dashboards              -> Core
GET    /dashboards/:id          -> Core
PATCH  /dashboards/:id          -> Core

GET    /search?q=               -> Search service
POST   /import                  -> Import/Export service (multipart)
GET    /export/:id              -> Import/Export service
GET    /notifications           -> Notifications service

GET    /health                  -> Returns 200
GET    /ready                   -> Checks core, redis, kafka connectivity
```

## Error Handling

- All errors wrapped in standard `ApiError` response: `{ error: { code, message, details, traceId } }`
- HTTP status mapping: validation (422), auth (401), forbidden (403), not found (404), conflict (409), too many (429), internal (500)
- Unhandled exceptions caught by global `ExceptionFilter` that logs `trace_id` and returns sanitized 500

## Performance Considerations

- Fastify adapter (2-3x throughput vs Express)
- Connection pooling for Redis and downstream HTTP
- Response compression (Brotli for text, gzip fallback)
- Request body size limit: 10MB default, 100MB for imports
- Keepalive timeout: 120s
- Max header size: 16KB
