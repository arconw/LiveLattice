# Frontend - Technical Design

## Responsibilities

- Provide the LiveLattice web application shell.
- Authenticate through the Gateway and maintain session state.
- Render workspace navigation and the lattice cockpit mental model.
- Provide a full canvas editor with element tools, selection, inspector, comments, snapshots, templates, import/export entry points, and realtime presence.
- Provide dashboard and widget management over backend query contracts.
- Provide global search, suggestions, facets, and highlighted results.
- Provide job progress, notification inbox/preferences, audit trail, and service health surfaces.
- Preserve all backend contracts through typed clients and test fixtures.

## Source of Visual Truth

Frontend agents must inspect the local `.ctx` mock before coding:

- `.ctx/README.md` for design plan and critique.
- `.ctx/index.html` for section coverage and interaction zones.
- `.ctx/css/variables.css` for color, spacing, radius, shadow, typography, and motion tokens.
- `.ctx/css/typography.css` for type scale and text roles.
- `.ctx/css/styles.css` for layout/component examples.
- `.ctx/js/interactions.js` for small interaction behaviors.

The frontend may implement these ideas using production React components, but it must preserve the distinctive lattice cockpit concept, palette roles, typography roles, command palette, inspector pattern, canvas interaction zones, and explicit empty/error states.

## Proposed App Structure

```
frontend/
  src/
    app/
      App.tsx
      routes.tsx
      providers/
      error-boundaries/
    design-system/
      tokens/
      primitives/
      controls/
      feedback/
      data-states/
    features/
      auth/
      workspaces/
      shell/
      command-palette/
      canvas/
      realtime/
      dashboards/
      data-sources/
      search/
      import-export/
      notifications/
      audit/
      background-jobs/
      health/
    contracts/
      api-client.ts
      auth.ts
      workspace.ts
      canvas.ts
      dashboard.ts
      search.ts
      jobs.ts
      notifications.ts
      audit.ts
      realtime.ts
      errors.ts
      fixtures/
    test/
      setup.ts
      accessibility.ts
      fixtures.ts
  Dockerfile
  package.json
  vite.config.ts
```

## Design Tokens

Production tokens should be implemented as CSS custom properties or a typed token module. The token source starts from `.ctx/css/variables.css`.

| Token family | Required examples | Purpose |
|---|---|---|
| Color | graphites, blueprint paper, CRDT blue, event amber, snapshot mint, index violet, conflict pink | Preserve product world: infra surface, canvas paper, collaboration, event bus, snapshots, search, conflicts |
| Typography | display, body, utility | Preserve display identity, readable dense UI, coordinate/data labels |
| Space | 4px-based scale | Predictable shell, panels, toolbar, inspector spacing |
| Radius | xs to xl plus pill | Distinguish canvas/paper surfaces from system panels |
| Shadow | panel, canvas, colored glows | Separate cockpit, sheets, dialogs, and focus states |
| Motion | fast, base, slow, easing | Event pulses and precise hover/focus transitions |
| Z-index | shell, menus, dialogs, toasts, cursors | Prevent canvas overlays from fighting global UI |

## Component Layers

### Design System Primitives

| Component | Responsibility | Contract concern |
|---|---|---|
| `AppShell` | Topbar, workspace switcher, nav, command entry, notification badge, health indicator | Does not bypass auth; shows route boundaries |
| `Panel` | Graphite system panels and blueprint paper surfaces | Uses tokenized contrast and focus rules |
| `Toolbar` | Action groups with keyboard focus order | Does not hide required actions behind hover-only UI |
| `Inspector` | Selected object details and edit controls | Shows backend ids, versions, and permission state where needed |
| `CommandPalette` | Search/jump/create actions | Uses keyboard navigation, command labels match actual actions |
| `ToastRegion` | Global async feedback | Accessible live region; does not replace persistent error states |
| `EmptyState` | No data flows | Invites the next valid action |
| `ErrorState` | Validation, permission, network, conflict errors | Explains cause and recovery path |
| `StatusBadge` | health, unread, syncing, stale, saved | Not color-only |

### Shell and Workspace Components

| Component | Data | Behavior |
|---|---|---|
| `WorkspaceSwitcher` | workspace id, slug, name, role, tier | Switch route context, clear workspace-scoped caches |
| `WorkspaceNav` | active route and permissions | Shows canvas, dashboards, search, jobs, notifications, audit where allowed |
| `LatticeCockpit` | recent canvases, dashboards, jobs, notifications, service health | Visual overview from `.ctx`; clicking nodes routes to source objects |
| `ServiceHealthPill` | Gateway/readiness summaries | Shows degraded state without blocking unrelated work |
| `NotificationBell` | unread count and latest in-app items | Links to target object and inbox |

### Canvas Components

| Component | Data | Behavior |
|---|---|---|
| `CanvasRoute` | canvas id, workspace context, permissions | Loads canvas, comments, history, presence connection |
| `CanvasViewport` | content, viewport, selection, presence | Pan, zoom, select, keyboard movement, drag, resize |
| `CanvasElementRenderer` | element contract | Renders rectangle, circle, text, image, connector, arrow, freehand |
| `CanvasToolbar` | active tool and permissions | Select, shape, connector, comment, snapshot, export, template actions |
| `CanvasInspector` | selected element or canvas | Edits title/style/position/lock/group with validation |
| `CommentPins` | comments by target element | Opens anchored threads; handles missing targets |
| `CommentThreadPanel` | paginated comments and replies | Add, edit, resolve, delete based on permissions |
| `SnapshotPanel` | history list and versions | Create, inspect, restore with confirmation |
| `TemplatePanel` | templates and thumbnails | Save as template, create canvas from template |
| `PresenceLayer` | realtime awareness | Shows cursors, selections, names, away state |
| `SyncStatus` | local/remote connection state | Saved, saving, offline, reconnecting, conflict |

### Dashboard Components

| Component | Data | Behavior |
|---|---|---|
| `DashboardRoute` | dashboard metadata, layout, time range | Loads dashboard and data, manages auto-refresh |
| `DashboardGrid` | 12-column layout | Drag/resize widgets while preserving `{ x, y, w, h }` |
| `WidgetCard` | widget metadata and data result | Loading/error/stale states per widget |
| `WidgetEditor` | type, query, options, position | Builds backend-compatible widget payloads |
| `TimeRangeControl` | relative/absolute time range | Preserves backend time range shape |
| `DataSourceList` | workspace data sources | Does not expose secrets after creation |
| `DataSourceForm` | type and config | Validates locally, submits secrets once, tests connection |
| `QueryPreview` | query DSL and transformed result | Shows preview without leaking encrypted config |

### Search, Jobs, Notifications, Audit Components

| Component | Data | Behavior |
|---|---|---|
| `GlobalSearchPage` | query, filters, facets, results | Uses `search_after` for deep pagination |
| `SearchBox` | query and suggestions | Debounced suggest, cancellable requests |
| `FacetPanel` | type, tags, dates | Keeps filters URL-addressable |
| `SearchResultCard` | result type, title, content, highlights | Links to canvas element/comment/dashboard context |
| `JobList` | import/export/background jobs | Shows progress, status, retries, download links |
| `UploadDropzone` | file input | Button alternative, size/type preview, backend error mapping |
| `NotificationInbox` | notification pages | Mark read/all read, link target, empty state |
| `NotificationPreferencesForm` | digest, muted types, webhooks | Preserves instant/hourly/daily/never vocabulary |
| `AuditTimeline` | actor/action/target/time | Read-only, filterable, workspace-scoped |
| `HealthOverview` | service readiness | Degraded service explanation and affected features |

## Data Contracts

### Shared Error Contract

All API clients should normalize errors into:

```ts
type AppError = {
  status: number;
  code: string;
  message: string;
  fieldErrors?: Record<string, string[]>;
  requestId?: string;
  retryable: boolean;
};
```

Map backend errors to product states:

| HTTP | UI state |
|---:|---|
| 400 | Validation message near field or action |
| 401 | Refresh once, then login |
| 403 | Permission denied state |
| 404 | Not found or deleted state |
| 409 | Save conflict / optimistic lock state |
| 413 | Upload too large state |
| 429 | Rate limited state with retry guidance |
| 500+ | Service error with request id and retry action |

### Canvas Types

```ts
type CanvasElementType = "rectangle" | "circle" | "text" | "image" | "connector" | "arrow" | "freehand";

type CanvasElement = {
  id: string;
  type: CanvasElementType;
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  style: Record<string, unknown>;
  data: Record<string, unknown>;
  zIndex: number;
  locked: boolean;
  groupId: string | null;
};

type CanvasContent = {
  elements: CanvasElement[];
  viewport: { zoom: number; panX: number; panY: number };
  metadata: { width: number; height: number; backgroundColor: string; gridEnabled: boolean };
};
```

### Dashboard Types

```ts
type WidgetType = "BAR_CHART" | "LINE_CHART" | "PIE_CHART" | "TABLE" | "STAT" | "HEATMAP" | "MARKDOWN";

type DashboardLayout = {
  columns: 12;
  gap: number;
  widgets: Array<{ widgetId: string; x: number; y: number; w: number; h: number }>;
};

type QueryResult = {
  columns: Array<{ name: string; type?: string }>;
  rows: unknown[][];
  meta: { totalRows: number; executedAt: string; warning?: string };
};
```

### Search Types

```ts
type SearchResult = {
  id: string;
  type: "canvas" | "comment" | "document" | "dashboard" | "template" | "user";
  workspaceId: string;
  title: string | null;
  content: string | null;
  tags: string[];
  authorId?: string;
  createdAt?: string;
  updatedAt?: string;
  highlights: Record<string, string[]>;
};

type SearchResponse = {
  results: SearchResult[];
  total: number;
  page: number;
  size: number;
  nextSearchAfter: string | null;
  facets: Record<string, Record<string, number>>;
};
```

### Realtime Types

```ts
type CanvasOperationMessage = {
  ops: unknown[];
  version: number;
  seq: number;
};

type AwarenessUpdate = {
  cursor: { x: number; y: number };
  selection: string[];
};
```

The implementation must replace `unknown` with validated operation types once the realtime protocol is finalized. Do not invent persisted fields outside the backend canvas contract.

## API Client Design

Every feature client should be scoped by a shared Gateway client:

```ts
interface GatewayClient {
  get<T>(path: string, options?: RequestOptions): Promise<T>;
  post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>;
  patch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>;
  delete<T>(path: string, options?: RequestOptions): Promise<T>;
}
```

Rules:

- Prefix backend routes through Gateway paths.
- Attach bearer auth through the auth provider only.
- Attach `x-request-id` when available.
- Never attach internal trusted headers.
- Abort stale requests on route changes, search input changes, and unmounted components.
- Normalize errors through `AppError`.
- Keep workspace id/slug in route state and request params consistently.

## Interaction Design

### Lattice Cockpit

The lattice cockpit is the signature overview. It must:

- Show connected nodes for recent canvas, dashboard, search, job, notification, and audit/service states.
- Support keyboard selection of nodes.
- Update an inspector panel when a node is selected.
- Use event pulses only to clarify relationships; respect reduced motion.
- Route to the underlying product object on activation.
- Show degraded, unread, unresolved, or in-progress state without relying on color alone.

### Canvas Editor

Core interactions:

- Select single/multiple elements.
- Pan and zoom viewport.
- Create shape/text/connector/freehand/image elements.
- Move, resize, rotate, duplicate, lock, group, ungroup, delete.
- Attach comment to canvas or element.
- Show active collaborators and selections.
- Create snapshot and restore from history with confirmation.
- Export/import through job flows.
- Show sync status and conflict state.

Keyboard minimum:

| Key | Behavior |
|---|---|
| Arrow keys | Move selected element by grid unit |
| Shift + Arrow | Move selected element by larger step |
| Delete/Backspace | Delete selected element with permission check |
| Enter | Open selected element inspector |
| Escape | Clear selection or close panel |
| C | Comment selected element when editor focus is active |
| Cmd/Ctrl + K | Open command palette |
| Cmd/Ctrl + S | Create or confirm save/snapshot action depending on mode |

### Dashboard Editor

- Drag/resize widgets with keyboard alternatives.
- Preserve 12-column coordinates and avoid overlapping widgets unless the backend supports overlap.
- Query execution is explicit for editor preview and automatic for viewer refresh.
- Each widget owns loading/stale/error states independently.

### Notifications and Jobs

- Notification toasts must not be the only source of information; inbox remains canonical.
- Job progress must remain visible after route changes through shell indicators or jobs page.
- Download actions must refresh expired signed URLs instead of reusing stale links.

## Visual Design Requirements

- Preserve `.ctx` palette roles and typography roles.
- The first logged-in screen should communicate the product thesis within five seconds: the workspace is a live lattice, not a file list.
- Use blueprint paper surfaces for canvas/editor work and graphite surfaces for system panels.
- Use utility monospace text for coordinates, events, endpoints, timestamps, and status chips.
- Use one signature motion pattern: event pulse through the lattice. Do not scatter unrelated animations.

## Accessibility Requirements

- WCAG AA contrast for text and controls.
- Visible keyboard focus on every interactive element.
- Dialogs trap focus and restore focus on close.
- Command palette supports keyboard navigation and Escape close.
- Canvas editor exposes selected element list and inspector controls for keyboard/screen-reader users.
- Toasts use polite live regions.
- Errors are associated with fields and forms.
- Reduced motion disables event pulses and non-essential transitions.

## Contract Preservation Matrix

| Backend contract | Frontend guardrail | Test fixture |
|---|---|---|
| Gateway-only access | API client rejects absolute internal service URLs | API client unit test |
| Trusted headers owned by Gateway | Header allow/block list test | API client unit test |
| Workspace scoping | Route and cache keys include workspace | Workspace route test |
| Canvas JSONB shape | Zod/type guard or explicit mapper | Canvas fixture test |
| Optimistic lock/version | Conflict UI on 409 | Canvas save test |
| Realtime room protocol | Socket adapter with typed events | Realtime mock test |
| Dashboard 12-column layout | Layout validator before save | Dashboard editor test |
| Widget enum values | Type-safe widget registry | Widget form test |
| Data source secrets | Secret fields write-only | Data source form test |
| Search facets/highlights | Response mapper and UI render test | Search fixture test |
| `search_after` pagination | Infinite list uses returned token | Search pagination test |
| Async jobs | Progress state machine | Job fixture test |
| Notifications preferences | Enum-preserving form | Preferences test |
| Audit read-only | No mutation controls in audit route | Audit route test |

## Build and Runtime

Frontend implementation should add:

- `frontend/Dockerfile` for production build and static serving or Node runtime.
- `frontend/package.json` with build/test/lint scripts.
- Local development script documented in `frontend/README.md`.
- Optional `compose.yaml` integration only when the frontend service is ready to run beside Gateway.
- Environment variables for Gateway base URL and WebSocket URL only; no backend service secrets.

## Verification

Minimum stage completion checks:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

When Compose integration exists:

```bash
docker compose config
docker compose build frontend
docker compose up -d frontend gateway core realtime search import-export notifications
docker compose ps
```

Smoke checks:

- Login screen renders.
- Authenticated workspace shell renders with mock or real Gateway data.
- Command palette opens with keyboard.
- Canvas editor route renders and can select an element.
- Realtime disconnected state is visible when socket is unavailable.
- Dashboard route renders widget loading/error/data states.
- Search page renders facets, highlights, empty state, and pagination token handling.
- Jobs page renders progress and failed/download states.
- Notifications page renders unread count and preferences.
- Audit page renders read-only event rows.
