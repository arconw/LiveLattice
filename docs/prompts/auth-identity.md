# Stage 3: Auth & Identity

## Objective

Integrate Keycloak for identity management and implement the auth bridge service for user provisioning and API key management.

## Requirements

1. Add Keycloak to `compose.yaml`:
   - Keycloak 26.x baseline with PostgreSQL backend, using an exact pinned image tag
   - Pre-configured realm `livelattice` with clients, roles, and users
   - Import realm configuration from `keycloak/realm-export.json`
2. Implement `AuthController` in Gateway:
   - `POST /auth/login` - exchange credentials for tokens via Keycloak OIDC
   - `POST /auth/social` - social login (Google, GitHub, Microsoft)
   - `POST /auth/refresh` - refresh token rotation
   - `POST /auth/logout` - invalidate refresh token
3. Implement `ApiKeyService` in Core Domain:
   - Generate API keys (32-byte random hex)
   - Store bcrypt hash in PostgreSQL
   - Validate API key on requests with `X-API-Key` header
   - Cache validated keys in Redis (5 min TTL)
4. Implement Auth Bridge (Spring Boot service):
   - Webhook listener for Keycloak events (user registration, update, delete)
   - Provision users in PostgreSQL `users` table
   - Sync roles between Keycloak and application RBAC
5. Implement `SessionCache`:
   - Redis-backed active session map (user_id -> {workspace_ids, roles})
   - 15 min TTL, refreshed on each request
   - Invalidate on logout or role change
6. Add MFA endpoints:
   - `POST /auth/mfa/setup` - generate TOTP secret or WebAuthn challenge
   - `POST /auth/mfa/verify` - verify and enable MFA
7. Write integration tests using Testcontainers (Keycloak + PostgreSQL + Redis)

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- No secrets in code - all credentials via environment variables
- JWKS response must be cached in Redis (not fetched on every request)

## Verification

```bash
# Start with Keycloak
docker compose up -d

# Wait for Keycloak to be ready
docker compose exec keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password admin

# Login
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'

# Create API key
curl -X POST http://localhost:3000/auth/keys \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"ci-key","permissions":["canvas:read"]}'

# Use API key
curl http://localhost:3000/api/core/canvases \
  -H "X-API-Key: <generated_key>"

# Run integration tests
cd core && ./gradlew test --tests *AuthIntegration*
```
