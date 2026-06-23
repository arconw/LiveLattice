# Stage 15D: Frontend Dashboards, Widgets, Data Sources, and Query States

## Objective

Implement dashboard and analytics frontend flows: dashboard list/detail, 12-column layout editing, widget CRUD, data source management, query preview, widget data loading, caching state, and per-widget failure handling.

## Required Context

Read these files before implementation:

- `.ctx/index.html`
- `.ctx/css/variables.css`
- `.ctx/css/styles.css`
- `docs/architect/frontend/frontend-architecture.md`
- `docs/techDesign/frontend/frontend-design.md`
- `docs/prompts/dashboard-analytics.md`
- `docs/techDesign/dashboard-analytics/dashboard-analytics-design.md`
- `docs/architect/data/data-architecture.md`
- `docs/architect/security/security-and-tenancy.md`

## Scope

1. Implement dashboard routes:
   - list dashboards
   - create dashboard
   - view dashboard
   - edit dashboard settings
   - duplicate dashboard
   - delete dashboard with confirmation
2. Implement 12-column dashboard grid:
   - render widgets using `{ widgetId, x, y, w, h }`
   - drag widgets
   - resize widgets
   - keyboard alternative for moving/resizing
   - collision/overlap prevention unless backend supports overlap
3. Implement widget components:
   - `BAR_CHART`
   - `LINE_CHART`
   - `PIE_CHART`
   - `TABLE`
   - `STAT`
   - `HEATMAP`
   - `MARKDOWN`
4. Implement widget editor:
   - title
   - type
   - data source
   - query DSL editor/form
   - display options
   - position
   - validation and preview
5. Implement dashboard time range control:
   - relative values `24h`, `7d`, `30d`
   - absolute `{ start, end }`
   - auto-refresh interval
   - disabled auto-refresh state
6. Implement dashboard data loading:
   - `GET /dashboards/:id/data`
   - `GET /dashboards/:id/widgets/:widgetId/data`
   - per-widget loading
   - per-widget stale state
   - per-widget error state
   - warning state for truncated results
7. Implement data source flows:
   - list data sources
   - create data source
   - edit non-secret metadata
   - delete data source with confirmation
   - test connection
   - secret write-only handling
8. Implement query result rendering:
   - unified columns/rows format
   - empty results
   - truncated results warning
   - execution timestamp
   - row count
9. Add tests for layout mapping, widget rendering, data source form secrecy, and error states.

## Dashboard Contracts

Dashboard layout must preserve backend shape:

```ts
type DashboardLayout = {
  columns: 12;
  gap: number;
  widgets: Array<{ widgetId: string; x: number; y: number; w: number; h: number }>;
};
```

Widget types must match backend enum values exactly:

```ts
type WidgetType = "BAR_CHART" | "LINE_CHART" | "PIE_CHART" | "TABLE" | "STAT" | "HEATMAP" | "MARKDOWN";
```

Query result shape:

```ts
type QueryResult = {
  columns: Array<{ name: string; type?: string }>;
  rows: unknown[][];
  meta: { totalRows: number; executedAt: string; warning?: string };
};
```

## Data Source Rules

- Data source secrets must be submitted only through create/update forms.
- Do not display previously stored secrets.
- Do not store secrets in long-lived frontend state.
- Do not execute database queries from the browser.
- All data source tests and widget queries go through Gateway/Core endpoints.
- Connection failures must show actionable error states.

## Contract Rules

- Dashboard routes use Gateway-protected Core endpoints.
- Dashboard and widget cache keys include workspace id, dashboard id, widget id, and time range.
- Widget errors do not break the whole dashboard route unless the dashboard itself cannot load.
- Time range values must preserve backend vocabulary and shape.
- Query DSL builders must not generate unsupported operators or field shapes without backend support.
- Large/truncated result warnings must be visible in the widget UI.
- Role restrictions must be enforced in UI but backend 403 remains authoritative.

## Required UI States

- Dashboard list loading.
- No dashboards yet.
- Dashboard not found.
- Dashboard permission denied.
- Dashboard view mode.
- Dashboard edit mode.
- Widget loading.
- Widget stale.
- Widget query failed.
- Widget empty result.
- Widget result truncated.
- Data source connection failed.
- Data source secret saved but hidden.
- Auto-refresh paused.

## Accessibility Requirements

- Dashboard grid editing has keyboard alternatives.
- Widget cards have accessible titles and status text.
- Chart data has a table or textual summary fallback.
- Data source forms have field labels and error associations.
- Delete actions require confirmation.
- Auto-refresh can be paused.
- Color is not the only indicator for thresholds or errors.

## Verification

```bash
cd frontend
npm run typecheck
npm run lint
npm test
npm run build
```

Optional Compose smoke:

```bash
docker compose up -d gateway core clickhouse redis postgres
```

Smoke checks:

- Dashboard list route renders.
- Empty dashboards state renders.
- Dashboard grid renders widgets from fixture layout.
- Widget editor preserves enum values and query DSL shape.
- Data source form does not reveal saved secret.
- Widget query failure only affects one widget.
- Time range control changes widget data cache key.

## Tests to Add

- Layout validator rejects non-12-column layout.
- Widget registry renders every backend widget enum.
- Query DSL form preserves metrics/dimensions/filters/order_by/limit shape.
- Data source form clears secret input after submit.
- Widget data mapper handles warnings and empty rows.
- Dashboard 403 renders permission state.
- Drag/resize updates widget position without losing unrelated widgets.
- Keyboard widget move updates position.

## Out of Scope

- Canvas editor implementation.
- Search page implementation.
- Import/export job page implementation.
- Notification inbox implementation.
- Audit page implementation.
- Backend query engine changes.

## Completion Criteria

The slice is complete when dashboard and data source UI can safely create/edit/view dashboards, render all widget types, preserve backend layout/query contracts, and show per-widget data states without exposing secrets.
