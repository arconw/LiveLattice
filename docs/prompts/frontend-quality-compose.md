# Stage 15F: Frontend Quality Gate, Contract Fixtures, Accessibility, and Compose Integration

## Objective

Harden the frontend implementation with contract fixtures, accessibility coverage, responsive checks, visual smoke coverage, Docker build, and optional Docker Compose integration.

This slice should be run after the main frontend feature slices are implemented.

## Required Context

Read before implementation:

- `.ctx/README.md`
- `.ctx/index.html`
- `docs/architect/frontend/frontend-architecture.md`
- `docs/techDesign/frontend/frontend-design.md`
- `docs/architect/testing/testing-strategy.md`
- `docs/architect/infra/local-dev-compose.md`
- `docs/architect/infra/deployment-and-ci.md`
- `docs/prompts/health-observability.md`
- Existing frontend test setup and all frontend feature slices.

## Scope

1. Add or complete contract fixtures:
   - auth/session
   - workspaces and roles
   - canvas content
   - canvas comments
   - snapshots/templates
   - realtime messages
   - dashboards/widgets/data results
   - search results/facets/highlights
   - import/export jobs
   - background jobs
   - notifications/preferences/webhooks
   - audit events
   - health/readiness summaries
2. Add contract mapper tests for all API clients.
3. Add accessibility tests for:
   - app shell
   - login form
   - workspace switcher
   - command palette
   - canvas toolbar and inspector
   - comment thread panel
   - dashboard grid and widget cards
   - data source forms
   - search filters
   - job progress rows
   - notification inbox/preferences
   - audit filters
   - dialogs/toasts
4. Add responsive checks for:
   - mobile shell
   - lattice cockpit
   - canvas editor
   - dashboard grid
   - search/results layout
   - jobs/notifications/audit pages
5. Add reduced motion checks.
6. Add frontend Dockerfile if not already present.
7. Add frontend Compose service only when it can build and serve reliably.
8. Add frontend README with local development, test, build, Docker, and contract fixture instructions.
9. Add smoke tests for the main happy paths and failure states.
10. Update documentation only when implementation behavior differs from documented frontend architecture or tech design.

## Contract Fixture Matrix

| Contract | Fixture file intent | Required checks |
|---|---|---|
| Auth/session | login success, invalid login, refresh failure | 401 refresh once, logout clears session |
| Workspace/RBAC | owner/admin/editor/viewer/commenter | role-aware shell and 403 state |
| Canvas content | all element types and empty canvas | mapper preserves JSONB shape |
| Comments | target thread, reply, resolved, missing target | anchored comments and missing target state |
| Realtime | join, op, ack, awareness, disconnect | socket adapter and sync status |
| Dashboard | 12-column layout and all widget types | layout validator and widget registry |
| Data source | secret create and hidden persisted secret | write-only secret behavior |
| Search | facets, highlights, empty, `nextSearchAfter` | result mapper and pagination |
| Jobs | queued, running, success, failed, expired URL | state machine and actions |
| Notifications | unread, read, preferences, webhook | optimistic read rollback and enum preservation |
| Audit | actor/action/target rows | read-only UI and filters |
| Health | all healthy, degraded, down | affected feature messaging |

## Accessibility Gate

The stage is not complete until:

- No critical accessibility violations remain in automated tests.
- Keyboard-only navigation can reach every primary action.
- Command palette traps/restores focus.
- Dialogs trap/restores focus.
- Canvas editor has keyboard-operable toolbar and inspector paths.
- Canvas elements are reachable through an alternate list or inspector representation.
- Toasts and async updates use live regions.
- Reduced motion disables non-essential animations.
- Color is not the only state indicator.

## Docker and Compose Rules

- Frontend build must not require backend service secrets.
- Runtime config can include Gateway HTTP base URL and WebSocket URL.
- If Compose service is added, it must depend on Gateway readiness only when appropriate.
- Do not expose internal backend service URLs to the browser bundle.
- `docker compose config` must pass after Compose edits.
- `docker compose build frontend` must pass after Compose edits.

## Verification

Run:

```bash
cd frontend
npm ci
npm run typecheck
npm run lint
npm test
npm run build
```

If Dockerfile exists:

```bash
docker build -t livelattice-frontend ./frontend
```

If Compose service is added:

```bash
docker compose config
docker compose build frontend
docker compose up -d frontend gateway
docker compose ps frontend gateway
```

Recommended E2E smoke, with mocked or real backend depending on availability:

- Login page renders.
- Workspace shell renders.
- Command palette opens by keyboard.
- Canvas route renders element fixture and selected inspector.
- Realtime disconnected state renders.
- Dashboard route renders all widget state fixtures.
- Search page renders facets/highlights/empty states.
- Job list renders progress and failed states.
- Notification preferences preserve enums.
- Audit route has no mutation actions.

## Out of Scope

- New product features.
- Backend API changes.
- Replacing the `.ctx` design direction.
- Adding third-party UI kits unless already adopted and justified.

## Completion Criteria

The frontend stage is complete only when build, tests, accessibility checks, contract fixture checks, responsive smoke, reduced motion checks, and Docker/Compose checks pass, and the implementation still preserves the `.ctx` design direction plus all documented backend contracts.
