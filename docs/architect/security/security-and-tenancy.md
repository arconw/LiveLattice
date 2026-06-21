# Security & Tenancy

## Authentication

- **Provider**: Keycloak (self-hosted) with OAuth2/OIDC; optional social login (Google, GitHub, Microsoft)
- **Token flow**: Authorization code flow -> short-lived JWT access token (15m) + refresh token (7d)
- **Service-to-service**: mTLS with internal CA, short-lived client certificates rotated daily
- **API keys**: HMAC-signed keys for external integrations, stored as bcrypt hash in PostgreSQL

### JWT Structure

```json
{
  "sub": "user-uuid",
  "ws": ["workspace-uuid-1", "workspace-uuid-2"],
  "role": "admin",
  "iat": 1700000000,
  "exp": 1700000900
}
```

## Authorization (RBAC)

### Roles per Workspace

| Role | Permissions |
|---|---|
| `owner` | All operations, billing, member management, workspace deletion |
| `admin` | All content operations, member invitation, settings |
| `editor` | Create/edit/delete canvases, dashboards, data sources |
| `viewer` | Read-only access to canvases and dashboards |
| `commenter` | View + add comments on canvases |

### Permission Enforcement
- **Gateway level**: JWT validation, workspace membership check (cached in Redis)
- **Service level**: Spring Method Security with custom `@WorkspacePermission` annotation
- **Data level**: PostgreSQL RLS policy `workspace_id = current_setting('app.current_workspace_id')`

## Multi-Tenancy

| Concern | Strategy |
|---|---|
| **Data isolation** | RLS via `workspace_id` column; ClickHouse `WHERE workspace_id = ?` |
| **Rate limits** | Per-workspace token bucket; burst limits scaled by tier |
| **Resource quotas** | Max canvases, dashboards, members, API calls/minute |
| **Encryption** | Per-workspace encryption key (AES-256-GCM) stored in Vault, used for sensitive fields |
| **Audit isolation** | Audit log includes `workspace_id`; queries filtered by membership |

## Encryption

- **At rest**: AES-256 encrypted volumes, Transparent Data Encryption for PostgreSQL
- **In transit**: TLS 1.3 everywhere, including internal service mesh
- **Field-level**: Encrypted JSONB columns for secrets (data source passwords, API tokens)
- **Key management**: HashiCorp Vault with auto-unseal, key rotation every 90 days

## Secrets Management

- **No secrets in code**: All credentials injected via Docker Compose environment variables or Vault agent sidecar
- **CI secrets**: GitHub Actions secrets for registry, deployment; never logged
- **Local dev**: `.env` files gitignored, example `.env.example` committed with placeholder values

## Security Headers

```nginx
# Applied at reverse proxy / gateway
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; connect-src 'self' wss://*.livelattice.io
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
```

## Rate Limiting

- **Per-user**: 1000 req/min burst, 100 sustained
- **Per-workspace**: 5000 req/min
- **WebSocket messages**: 100 msg/s per connection
- **Export endpoints**: 10 req/min per user
- **Implementation**: Token bucket in Redis, sharded by `user_id`

## Compliance Considerations

- GDPR: data export, right to deletion, processing records in audit log
- SOC 2: audit trail, access reviews, change management
- Data residency: configurable storage region, per-workspace setting
