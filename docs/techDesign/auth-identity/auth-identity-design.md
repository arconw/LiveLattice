# Auth & Identity - Technical Design

## Responsibilities

- User registration, login, social login, MFA, passwordless
- JWT issuance, validation, refresh, revocation
- API key management for external integrations
- Identity federation (OAuth2/OIDC with Google, GitHub, Microsoft)

## Technology Stack

- **Provider**: Keycloak 26.x baseline (self-hosted) with PostgreSQL backing store; exact patch version is pinned during implementation
- **Bridge service**: Custom Spring Boot service for Keycloak user provisioning webhooks
- **JWT**: RS256 signed by Keycloak realm; public keys cached from `{realm}/certs`
- **API keys**: Generated via `crypto.randomBytes(32).toString('hex')`, stored as bcrypt hash
- **MFA**: TOTP via Keycloak OTP policy; WebAuthn for passkeys

## Key Flows

### Login Flow

```
Client -> Gateway POST /auth/login
  -> Gateway -> Keycloak (OAuth2 token endpoint)
  <- Keycloak: access_token (15m), refresh_token (7d), id_token
  -> Gateway: cache user info in Redis (5m TTL)
  <- Client: { accessToken, refreshToken, expiresIn, user }
```

### API Key Flow

```
Client -> Gateway GET /workspaces/:id/data-sources with X-API-Key header
  -> Gateway: HMAC sign(api_key_id + timestamp + body) with stored secret
  -> Gateway: look up API key in Redis cache -> miss -> PostgreSQL
  -> Gateway: verify hash matches, check expiry, permissions
  -> Route to Core Domain with x-user-id header set
```

## Key Modules

| Module | Responsibility |
|---|---|
| `TokenService` | JWT validation via JWKS, refresh token rotation |
| `ApiKeyService` | Generate, hash, validate API keys |
| `KeycloakAdminClient` | User provisioning, role sync via Keycloak Admin REST API |
| `SessionCache` | Redis-backed active sessions with TTL |

## API Endpoints

```
POST   /auth/login              -> { email, password } -> tokens
POST   /auth/social             -> { provider, code } -> tokens
POST   /auth/refresh            -> { refreshToken } -> new tokens
POST   /auth/logout             -> { refreshToken } -> invalidate
POST   /auth/mfa/setup          -> { type: totp | webauthn } -> challenge
POST   /auth/mfa/verify         -> { code | credentialId }
POST   /auth/passwordless       -> { email } -> magic link sent
GET    /auth/keys               -> list API keys
POST   /auth/keys               -> create API key
DELETE /auth/keys/:id           -> revoke API key
```

## Token Revocation

- On logout: add `access_token` jti to Redis deny list for remaining TTL
- On password change: invalidate all refresh tokens
- On MFA disable: require re-authentication
- Keycloak session management for admin-initiated logout

## Performance Considerations

- JWKS response cached in Redis for 1 hour (or until `cache-control` max-age from Keycloak)
- API key lookup cached in Redis for 5 minutes
- Rate limit on `/auth/login`: 10 attempts per IP per minute (anti-brute-force)
- Password hash cost: bcrypt 12 rounds (target: <300ms verification)
- Keycloak connection pool: 10 connections, 30s timeout
