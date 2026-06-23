# Stage 15A: Frontend Foundation, Design System, and App Shell

## Objective

Create the frontend application foundation, design tokens, app shell, command palette, route structure, and shared contract client without implementing deep feature screens yet.

This is the first frontend slice. It must establish the architecture that later frontend prompts build on.

## Required Context

Read these files before implementation:

- `.ctx/README.md`
- `.ctx/index.html`
- `.ctx/css/variables.css`
- `.ctx/css/typography.css`
- `.ctx/css/styles.css`
- `.ctx/js/interactions.js`
- `docs/architect/frontend/frontend-architecture.md`
- `docs/techDesign/frontend/frontend-design.md`
- `docs/techDesign/frontend/frontend-flow.mmd`
- `docs/architect/security/security-and-tenancy.md`
- `docs/architect/overview/system-architecture.md`

The `.ctx` mock is local and gitignored. Use it as the visual and interaction reference. Do not copy it blindly as production code; convert it into maintainable frontend components and tokens.

## Scope

1. Create `frontend/` web app with TypeScript.
2. Add package scripts for install/build/test/typecheck/lint.
3. Add a frontend Dockerfile if the app can be built reproducibly in Docker.
4. Implement design tokens based on `.ctx/css/variables.css`:
   - graphite surfaces
   - blueprint paper surfaces
   - CRDT blue
   - event amber
   - snapshot mint
   - index violet
   - conflict pink
   - spacing/radius/shadow/motion tokens
5. Implement typography based on `.ctx/css/typography.css`:
   - display face role
   - body face role
   - utility/mono role
   - text scale
6. Implement core design-system components:
   - AppShell
   - Panel
   - PaperSurface
   - Button
   - IconButton
   - Input
   - Select
   - Badge/StatusChip
   - Dialog
   - ToastRegion
   - EmptyState
   - ErrorState
   - LoadingState
7. Implement the logged-in app shell mock using real components:
   - topbar
   - workspace switcher placeholder
   - command palette trigger
   - notification badge placeholder
   - health/status chip
   - primary navigation
   - route outlet
8. Implement the lattice cockpit overview as a component using the `.ctx` concept:
   - connected nodes for canvas, dashboard, search, import/export, notifications, audit
   - selectable node state
   - inspector panel
   - event pulse respecting reduced motion
   - keyboard reachable nodes
9. Implement routing placeholders:
   - `/login`
   - `/workspaces`
   - `/w/:workspaceSlug`
   - `/w/:workspaceSlug/c/:canvasId`
   - `/w/:workspaceSlug/d/:dashboardId`
   - `/w/:workspaceSlug/search`
   - `/w/:workspaceSlug/jobs`
   - `/w/:workspaceSlug/notifications`
   - `/w/:workspaceSlug/audit`
10. Implement shared Gateway API client foundation:
    - relative Gateway paths only
    - bearer token hook point
    - request id support
    - abort/cancellation support
    - normalized `AppError`
    - explicit trusted header block list
11. Add contract fixtures directory and initial API client tests.

## Contract Rules

- The browser must only call Gateway routes.
- Do not call `core`, `search`, `notifications`, `import-export`, `audit-log`, or `background-jobs` service hostnames directly from frontend code.
- Do not send trusted internal headers from frontend code:
  - `x-internal-auth-token`
  - `x-auth-subject`
  - `x-auth-email`
  - `x-auth-display-name`
  - `x-auth-roles`
  - `x-user-id`
- Do not hard-code secrets or service tokens.
- Keep workspace context in routes and cache keys.
- Preserve `.ctx` design identity; do not replace it with generic SaaS cards or gradients.

## Accessibility Requirements

- All shell controls must be keyboard reachable.
- Command palette must open via button and keyboard shortcut.
- Command palette must close with Escape and restore focus.
- Toasts must use an accessible live region.
- Focus states must be visible.
- Reduced motion must disable event pulses and non-essential transitions.
- Color must not be the only indicator of state.

## Verification

Run and document results:

```bash
cd frontend
npm ci
npm run typecheck
npm run lint
npm test
npm run build
```

If a Dockerfile is added:

```bash
docker build -t livelattice-frontend ./frontend
```

## Tests to Add

- API client refuses or strips trusted internal headers.
- API client normalizes 400/401/403/404/409/429/500 errors.
- Command palette opens, focuses input, closes with Escape, restores focus.
- Lattice cockpit node selection updates inspector.
- Reduced motion disables pulse class or animation behavior.
- Shell renders route placeholders without backend connectivity.

## Out of Scope

- Real login flow implementation.
- Real workspace CRUD.
- Full canvas editor.
- Realtime collaboration socket.
- Dashboard widget editing.
- Search/jobs/notifications/audit real data pages.
- Compose integration unless the basic app and Dockerfile are ready.

## Completion Criteria

The slice is complete when the frontend app builds, tests pass, the shell/lattice cockpit matches the `.ctx` direction, and the Gateway client foundation has tests proving it preserves auth/header boundaries.
