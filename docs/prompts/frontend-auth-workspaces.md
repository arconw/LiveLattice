# Stage 15B: Frontend Auth, Session, Workspaces, and RBAC Shell

## Objective

Implement frontend authentication, session handling, workspace selection, role-aware navigation, quota/permission states, and protected route behavior on top of the frontend foundation.

## Required Context

Read these files before implementation:

- `.ctx/index.html`
- `docs/architect/frontend/frontend-architecture.md`
- `docs/techDesign/frontend/frontend-design.md`
- `docs/architect/security/security-and-tenancy.md`
- `docs/prompts/auth-identity.md`
- `docs/prompts/workspaces-rbac.md`
- `docs/techDesign/auth-identity/auth-identity-design.md`
- `docs/techDesign/workspaces-rbac/workspaces-rbac-design.md`

## Scope

1. Implement auth routes and UI:
   - login
   - refresh/session restoration
   - logout
   - expired session state
   - invalid credentials state
2. Implement session provider:
   - stores access token according to the chosen frontend security strategy
   - refreshes once after a 401 when possible
   - clears session and redirects after refresh failure
   - exposes current user claims and roles
3. Implement protected routes:
   - redirect unauthenticated users to login
   - preserve intended destination
   - show loading while restoring session
4. Implement workspace list and switcher:
   - list workspaces
   - create workspace
   - switch active workspace
   - route by workspace slug
   - clear workspace-scoped caches when switching
5. Implement member and role surfaces:
   - show current role
   - show role-aware navigation affordances
   - disabled/hidden controls where appropriate
   - preserve backend authority on 403
6. Implement quota and permission states:
   - quota reached
   - workspace access revoked
   - viewer/commenter/editor/admin/owner differences
7. Implement API client integration for Gateway auth paths and Core workspace paths.
8. Add typed fixtures for auth and workspace responses.
9. Add route-level error boundaries for auth, permission, quota, and not found.

## Contract Rules

- Authentication must go through Gateway auth endpoints, not Keycloak directly unless the Gateway contract explicitly says otherwise.
- Frontend must not provision users directly; user provisioning happens through Gateway/Core auth flow.
- Frontend must not send trusted internal identity headers.
- Protected `/api/*` requests use bearer auth only unless a documented API-key flow explicitly applies.
- Workspace roles must match backend vocabulary:
  - owner
  - admin
  - editor
  - viewer
  - commenter
- Role-aware UI is advisory; backend permission responses are authoritative.
- Workspace cache keys must include workspace id or slug.
- Quota failures must render product-specific states, not generic network errors.

## Required UI States

- Login idle.
- Login submitting.
- Login failed.
- Session restoring.
- Session expired.
- No workspaces.
- Workspace loading.
- Workspace access revoked.
- Workspace not found.
- Permission denied.
- Quota reached.
- Member invite pending.
- Role changed.

## Accessibility Requirements

- Auth forms have labels and field-level errors.
- Login failures announce via accessible error region.
- Workspace switcher supports keyboard selection.
- Permission and quota states explain the next available action.
- Focus moves to the main heading after route navigation where practical.

## Verification

```bash
cd frontend
npm run typecheck
npm run lint
npm test
npm run build
```

Optional Compose smoke if backend auth/core are available:

```bash
docker compose up -d gateway core keycloak redis postgres
```

Manual or automated smoke:

- Login page renders.
- Invalid login shows an error.
- Protected route redirects when unauthenticated.
- Workspace shell renders after mocked or real session.
- Workspace switch changes route and clears workspace-scoped state.
- 403 response renders permission denied.
- Quota error renders quota state.

## Tests to Add

- Session provider refreshes once after 401.
- Session provider does not loop refresh attempts.
- Protected route redirects unauthenticated users.
- API client does not send trusted internal headers.
- Workspace switcher updates route and cache namespace.
- Role-aware nav hides/disables restricted actions.
- Permission denied and quota states render with actionable copy.

## Out of Scope

- Canvas editor implementation.
- Dashboard editor implementation.
- Realtime socket implementation.
- Notifications inbox implementation beyond shell unread placeholder.
- Audit trail implementation.

## Completion Criteria

The slice is complete when auth/session/workspace flows are typed, tested, accessible, and integrated into the app shell without violating Gateway or RBAC contracts.
