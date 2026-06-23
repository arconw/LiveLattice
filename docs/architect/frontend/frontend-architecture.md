# Frontend Architecture

## Purpose

The LiveLattice frontend is the user-facing workspace for collaborative diagrams, canvas documents, dashboards, search, imports/exports, notifications, audit trails, and system activity. It must treat backend objects as one connected workspace graph rather than as disconnected pages.

The frontend implementation must use the local design mock as the visual and interaction reference:

- `.ctx/index.html`
- `.ctx/css/variables.css`
- `.ctx/css/typography.css`
- `.ctx/css/styles.css`
- `.ctx/js/interactions.js`

The `.ctx` directory is intentionally local and gitignored. Agents must inspect it before implementing frontend UI and must preserve its core design idea: the lattice cockpit as the primary workspace metaphor.

## Product Model

LiveLattice exposes these user concepts:

| Concept | User meaning | Backend source |
|---|---|---|
| Workspace | Tenant boundary, members, roles, quotas, settings | Core workspaces and RBAC |
| Canvas | Collaborative diagram/document with JSONB content, comments, templates, snapshots | Core canvas endpoints and Realtime room protocol |
| Canvas element | Shape, text, image, connector, arrow, freehand mark, or grouped object | `Canvas.content.elements[]` |
| Comment thread | Conversation attached to a canvas or a specific element | Core comment endpoints and Realtime comment events |
| Snapshot | Recoverable canvas version stored through PostgreSQL and MinIO | Core snapshot/history endpoints |
| Dashboard | 12-column analytics layout with widgets and time range | Core dashboard endpoints |
| Widget | Query-backed visualization, stat, table, markdown, or heatmap | Core widget and dashboard data endpoints |
| Data source | Encrypted workspace connection to ClickHouse, PostgreSQL, Prometheus, REST API, or CSV | Core data source endpoints |
| Search result | Indexed canvas, document, dashboard, comment, template, or user | Search service via Gateway |
| Import/export job | Long-running file conversion or artifact generation | Import-export service via Gateway |
| Notification | In-app/email/webhook user event with preferences and read state | Notifications service via Gateway |
| Audit event | Workspace-scoped history of actor/action/target changes | Audit service via Gateway |

## Architectural Position

```
Browser App
  |-- Auth/session client
  |-- Workspace graph shell
  |-- Canvas editor and collaboration client
  |-- Dashboard/query client
  |-- Search, jobs, notifications, audit clients
  |-- Design system and accessibility layer
      |
      v
API Gateway only
  |-- /auth/*
  |-- /api/core/*
  |-- /api/search/*
  |-- /api/import-export/*
  |-- /api/notifications/*
  |-- /api/audit-log/*
  |-- /api/background-jobs/*
  |-- WebSocket entrypoint for realtime collaboration
```

The browser must never call internal backend services directly. All REST calls go through the Gateway. The browser must never set trusted internal headers such as `x-internal-auth-token`, `x-auth-subject`, `x-auth-email`, `x-auth-display-name`, `x-auth-roles`, or `x-user-id`. The Gateway owns trusted identity injection.

## Recommended Frontend Stack

The implementation stage should create a dedicated `frontend/` app using:

- React with TypeScript for the web app.
- Vite or another lightweight React build tool, with exact versions pinned during implementation.
- CSS variables and component styles derived from `.ctx/css/variables.css` and `.ctx/css/typography.css`.
- A canvas rendering layer that can support selection, drag, resize, connectors, comments, presence cursors, and viewport transforms.
- A query/data layer with typed API clients, cache keys scoped by workspace, and explicit loading/empty/error states.
- A test setup covering unit tests, component tests, accessibility checks, and contract fixtures.

Do not introduce a frontend framework, component library, state library, canvas library, chart library, or CRDT binding without documenting why it preserves the backend contracts better than a smaller custom layer.

## Route Model

| Route | Purpose | Required contracts |
|---|---|---|
| `/login` | Authenticate with Keycloak-backed Gateway auth | `/auth/login`, `/auth/refresh`, `/auth/logout` |
| `/workspaces` | Select, create, and manage workspaces | Workspace list/create/update, membership, quota errors |
| `/w/:workspaceSlug` | Workspace home and lattice cockpit | Workspace membership, recent canvases, dashboards, notifications, jobs |
| `/w/:workspaceSlug/c/:canvasId` | Full canvas editor | Canvas CRUD, comments, snapshots, realtime room protocol, import/export |
| `/w/:workspaceSlug/d/:dashboardId` | Dashboard editor/viewer | Dashboard layout, widgets, data source queries, data endpoint |
| `/w/:workspaceSlug/search` | Global workspace search | Search query, facets, suggestions, highlighted snippets |
| `/w/:workspaceSlug/jobs` | Import/export and background job activity | Job status, progress, artifact downloads |
| `/w/:workspaceSlug/notifications` | Notification inbox and preferences | Notification list, unread count, read state, preferences |
| `/w/:workspaceSlug/audit` | Audit trail | Audit event filters and workspace-scoped access |
| `/settings` | Account/session preferences | Auth session and user profile contracts |

## State Ownership

| State | Owner | Frontend handling |
|---|---|---|
| Access token/session | Gateway/Keycloak | Store in memory where possible; refresh through Gateway; clear on logout or 401 loop |
| Workspace membership and role | Core | Cache per workspace; invalidate after member changes; never assume permissions from UI only |
| Canvas content | Core + Realtime | Load initial content from Core; apply local optimistic edits through collaboration layer; reconcile with server version |
| Presence and cursors | Realtime | Ephemeral WebSocket state; never persist as canvas content |
| Comments | Core + Realtime event broadcast | Store through Core; update active room from realtime events |
| Dashboard data | Core query engine | Cache by dashboard/widget/time range; show stale/loading/error per widget |
| Search suggestions | Search service | Debounce, cancel stale requests, preserve facets and highlights |
| Jobs | Import-export/background jobs | Poll or subscribe; show progress, failure, retry/download actions |
| Notifications | Notifications + Realtime | Show unread count, read state, preferences, toasts, digest hints |
| Audit | Audit service | Read-only filtered lists; no optimistic writes |

## Contract Boundaries

### Gateway and Authentication

- The frontend calls Gateway public endpoints only.
- Login, refresh, logout, and user provisioning happen through Gateway auth flows.
- Protected `/api/*` routes require bearer authentication unless a documented API-key route explicitly allows an API key.
- On `401`, the frontend attempts one refresh if a refresh token/session exists, then redirects to login.
- On `403`, the frontend shows a permission-specific denial and does not retry automatically.
- The frontend must not send internal identity headers.

### Workspaces and RBAC

- Every workspace-scoped request must use the workspace selected in UI and encoded in the route/state.
- UI controls must be disabled or hidden based on known role, but backend permission failures remain authoritative.
- Quota failures must be displayed as actionable product states, not generic errors.
- Member roles must match backend roles: owner, admin, editor, viewer, commenter.

### Canvas Content

Frontend canvas data must match the backend `Canvas.content` shape:

```json
{
  "elements": [
    {
      "id": "uuid",
      "type": "rectangle|circle|text|image|connector|arrow|freehand",
      "x": 10,
      "y": 10,
      "width": 100,
      "height": 50,
      "rotation": 0,
      "style": { "fill": "#ffffff", "stroke": "#4d7cfe", "strokeWidth": 2, "opacity": 1 },
      "data": { "text": "optional type-specific data" },
      "zIndex": 1,
      "locked": false,
      "groupId": null
    }
  ],
  "viewport": { "zoom": 1, "panX": 0, "panY": 0 },
  "metadata": { "width": 2400, "height": 1400, "backgroundColor": "#eef2f5", "gridEnabled": true }
}
```

The frontend may keep richer transient interaction state locally, but persisted canvas content must remain compatible with this contract.

### Realtime Collaboration

- The active canvas editor connects to the documented realtime endpoint through Gateway or the approved realtime entrypoint.
- The client joins rooms by canvas id.
- Presence and awareness use the documented payloads: cursor coordinates, selection ids, and online/away state.
- Canvas operations must be idempotent, ordered, and associated with local sequence ids.
- The UI must reconcile acknowledgements and remote operations without duplicating local changes.
- If realtime is disconnected, the editor must show offline/reconnecting state and prevent misleading saved indicators.

### Dashboards and Data Sources

- Dashboard layout uses a 12-column grid with widget positions `{ x, y, w, h }`.
- Widget types must match backend enum values: `BAR_CHART`, `LINE_CHART`, `PIE_CHART`, `TABLE`, `STAT`, `HEATMAP`, `MARKDOWN`.
- Dashboard data is requested through dashboard data endpoints, not by exposing data source credentials to the browser.
- Data source secrets are never shown after creation and must not be stored in frontend state beyond a form submission.
- Widget errors are isolated to the widget card whenever possible.

### Search

- Search calls use Gateway-protected `/api/search/*` routes.
- The UI must support query, type, workspace, tags, date range, size, `search_after`, highlights, and facets.
- Deep pagination must use `search_after`; do not implement naive infinite scroll with `page` beyond backend limits.
- Suggest requests must be debounced and cancellable.

### Import, Export, and Background Jobs

- Small synchronous exports may download immediately.
- Async jobs must show progress, current state, failure reason, and download action when available.
- Signed download URLs are time-bound and must not be cached permanently.
- Upload UI must validate size/type for user feedback, but backend validation remains authoritative.

### Notifications

- Unread count is a product state in the shell.
- In-app toasts must link to the target canvas, dashboard, job, or workspace context.
- Preference UI must preserve backend vocabulary: instant, hourly, daily, never, muted types, webhooks.
- Webhook secret values must not be displayed after creation.

### Audit

- Audit UI is read-only.
- Filters must preserve workspace isolation and actor/action/target terminology.
- Audit events must never be edited or deleted by frontend flows.

## Application Shell

The shell owns:

- Workspace switcher.
- Primary navigation.
- Command palette.
- Global search entry.
- Unread notification badge.
- Service health indicator.
- Current user/session menu.
- Toast region.
- Route-level loading/error boundaries.

The shell should use the `.ctx` topbar, command palette, and lattice cockpit as the starting visual reference.

## Design System Architecture

The first frontend implementation must create a small internal design system before feature screens:

- Tokens: colors, typography, spacing, radius, shadows, z-index, motion.
- Layout primitives: app shell, panel, paper surface, split view, grid, toolbar, inspector.
- Controls: button, icon button, input, select, segmented control, menu, dialog, toast, badge.
- Data states: loading, empty, error, permission denied, stale, offline, syncing, saved.
- Product components: canvas node, comment pin, presence cursor, widget card, job progress, notification item, audit row.

Tokens must start from `.ctx/css/variables.css` and `.ctx/css/typography.css`; agents may refine names but must preserve the palette roles and typography intent.

## Accessibility and Interaction Rules

- All controls must be keyboard reachable.
- Canvas editor must expose keyboard alternatives for selection, movement, deletion, duplicate, comment, and zoom.
- Canvas elements need accessible names in side panels and selection lists even if the rendered canvas is not fully semantic.
- Visible focus must be preserved for all interactive controls.
- Reduced motion must disable non-essential event pulses and transitions.
- Color cannot be the only indicator for health, validation, role, or unread state.
- Toasts and async job updates need an accessible live region.
- Drag/drop and file upload flows require keyboard and button alternatives.

## Failure and Empty States

Frontend agents must implement intentional states for:

- No workspaces.
- Workspace access revoked.
- No canvases yet.
- Canvas deleted or not found.
- Realtime disconnected.
- Save conflict or optimistic lock failure.
- Comment target missing after element deletion.
- No dashboards yet.
- Widget query failed.
- Data source connection failed.
- Search no results.
- Upload rejected.
- Export job failed.
- Notification inbox empty.
- Audit log empty.

## Testing Architecture

Frontend stage must include:

- Unit tests for contract mappers and state reducers.
- Component tests for shell, forms, canvas side panels, dashboard widgets, search results, jobs, notifications, audit rows.
- Accessibility tests for shell, dialogs, forms, command palette, and core routes.
- Mock Service Worker or equivalent contract fixtures for Gateway responses.
- Realtime client tests with mocked socket protocol.
- Visual smoke screenshots for the lattice cockpit and responsive mobile layout.
- End-to-end smoke paths against Docker Compose where backend services are available.

## Implementation Order

1. Frontend foundation and design system.
2. Auth/session and workspace shell.
3. Canvas editor, comments, snapshots, templates, and realtime collaboration.
4. Dashboard/data source/widget UI.
5. Search, import/export jobs, notifications, audit, and background activity.
6. Accessibility, contract fixtures, E2E smoke, and frontend Docker Compose integration.

Do not mark the frontend stage complete until the implementation preserves Gateway auth, workspace scoping, canvas content shape, realtime room protocol, dashboard query contracts, search facets/highlights, import/export job semantics, notification preferences, and audit read-only semantics.
