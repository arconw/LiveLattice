# Stage 2: API Gateway

## Objective

Implement the NestJS API Gateway with Fastify adapter, JWT authentication guard, rate limiting, and proxying to downstream services.

## Requirements

1. Initialize NestJS project in `gateway/` with Fastify adapter
2. Implement `AuthModule`:
   - JWT validation via `passport-jwt` strategy with JWKS endpoint (Keycloak)
   - Extract `user_id` and `workspace_id` from JWT claims
   - `@Auth()` decorator for route protection
3. Implement `RateLimitModule`:
   - Token bucket algorithm backed by Redis
   - Per-user (1000 req/min burst, 100 sustained) and per-workspace (5000 req/min) limits
   - Configurable via environment variables
4. Implement `ProxyModule`:
   - Dynamic HTTP proxy to core, search, notifications, import-export services
   - Read target URL from request path prefix (`/api/core/*`, `/api/search/*`, etc.)
   - Add `x-user-id`, `x-workspace-id` headers to proxied requests
   - 10s timeout per proxied request
5. Implement `MetricsModule`:
   - Prometheus metrics: `http_requests_total`, `http_request_duration_ms` (histogram), `http_requests_active`
   - Export at `GET /metrics`
6. Implement global `ExceptionFilter`:
   - Catch all exceptions, log with `trace_id`, return standard `ApiError` format
7. Implement health check endpoint `GET /health` and readiness `GET /ready`
8. Add OpenAPI documentation via `@nestjs/swagger`
9. Write unit tests for all guards, pipes, and filters

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Rate limit counters must be Redis-backed (not in-memory)
- All errors must return the standard `ApiError` format

## Verification

```bash
# Ensure infra is running
docker compose up -d

# Start gateway in dev mode
cd gateway && npm install && npm run start:dev

# Health check
curl http://localhost:3000/health

# Readiness
curl http://localhost:3000/ready

# Metrics
curl http://localhost:3000/metrics

# Protected route (should 401)
curl http://localhost:3000/api/core/workspaces

# Unit tests
npm test

# Test coverage
npm run test:cov
```
