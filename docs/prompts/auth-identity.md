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
   - MVP scope: workspace-scoped service tokens created and revoked by OWNER/ADMIN users
   - Generate random 32-byte tokens
   - Show the plaintext token only once on creation
   - Store only a bcrypt or Argon2 hash in PostgreSQL
   - Store workspace id, creator id, scoped permissions, status, last used timestamp, and optional expiration
   - Validate requests with `X-API-Key` header
   - Cache validated keys in Redis (5 min TTL)
4. Implement Gateway/Core auth boundary:
   - Gateway `AuthService` owns public OIDC login, refresh, and logout flows
   - Core `UserService` idempotently provisions users from authenticated Keycloak claims
   - `users.external_subject` must equal the Keycloak `sub` claim
   - User provisioning must happen through the auth/login flow only, including local development
   - Do not keep a fallback that creates users outside the auth flow
   - Gateway passes trusted internal identity headers to Core after validation
   - Core keeps domain RBAC and does not duplicate public OIDC login logic
5. Implement `SessionCache`:
   - Redis-backed active session map (user_id -> {workspace_ids, roles})
   - 15 min TTL, refreshed on each request
   - Invalidate on logout or role change
6. Add MFA and social-login backend support:
   - Configure Keycloak realm support for social identity providers where credentials are available through environment variables
   - Expose backend integration contracts for login initiation and callback handling as needed by Gateway
   - `POST /auth/mfa/setup` - generate TOTP secret or WebAuthn challenge
   - `POST /auth/mfa/verify` - verify and enable MFA
   - Frontend UX is out of scope; verify backend behavior with Keycloak admin/test flows and curl where possible
7. Write integration tests using Testcontainers (Keycloak + PostgreSQL + Redis)


## Implementation Decisions

- Keycloak is the identity source of truth.
- Version a Keycloak realm export in the repository so local auth can be recreated deterministically.
- Direct Core access after Auth is closed by default. Local development may use only an explicit dev profile or internal header path where identity still comes from the auth flow.
- Stage 7 Realtime Collaboration depends on this stage and must run after JWT authentication is implemented.
- API keys are workspace-scoped service tokens for the MVP, not general user API keys.

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
