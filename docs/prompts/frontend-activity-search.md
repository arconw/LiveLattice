# Stage 15E: Frontend Search, Jobs, Notifications, Audit, and Activity Surfaces

## Objective

Implement the frontend surfaces that make background and cross-cutting systems visible: workspace search, suggestions, facets, import/export jobs, background job status, notifications inbox/preferences, audit trail, and health/activity indicators.

## Required Context

Read before implementation:

- `.ctx/index.html`
- `.ctx/css/variables.css`
- `.ctx/css/styles.css`
- `docs/architect/frontend/frontend-architecture.md`
- `docs/techDesign/frontend/frontend-design.md`
- `docs/prompts/search.md`
- `docs/techDesign/search/search-design.md`
- `docs/prompts/import-export.md`
- `docs/techDesign/import-export/import-export-design.md`
- `docs/prompts/notifications.md`
- `docs/techDesign/notifications/notifications-design.md`
- `docs/prompts/audit-log.md`
- `docs/techDesign/audit-log/audit-log-design.md`
- `docs/prompts/background-jobs.md`
- `docs/techDesign/background-jobs/background-jobs-design.md`
- `docs/prompts/health-observability.md`
- `docs/techDesign/health-observability/health-observability-design.md`

## Scope

1. Implement global/workspace search with query, suggestions, type filters, workspace filter, tags, date range, facets, highlighted snippets, `search_after` pagination, empty states, and errors.
2. Implement command palette integration for jumping to canvases, dashboards, comments, jobs, notifications, people, and audit contexts.
3. Implement import/export job surfaces with queued/running/succeeded/failed/cancelled states, progress, failure reason, download action, and expired signed URL handling.
4. Implement background job surfaces with job type, owner, workspace scope, retry state, failure reason, and timestamps.
5. Implement notifications with unread badge, inbox, target links, mark read, mark all read, empty state, preferences, muted types, digest frequency, and webhook management.
6. Implement audit trail with read-only rows, actor/action/target filters, date range, and workspace scoping.
7. Implement health/activity overview with service health cards, degraded states, and affected feature hints.
8. Add tests for search mappers, pagination tokens, job states, notification preferences, webhook secrecy, audit read-only behavior, and health mapping.

## Search Contract

Search response shape:

```ts
type SearchResponse = {
  results: SearchResult[];
  total: number;
  page: number;
  size: number;
  nextSearchAfter: string | null;
  facets: Record<string, Record<string, number>>;
};
```

Search result type values:

```ts
type SearchResultType = "canvas" | "comment" | "document" | "dashboard" | "template" | "user";
```

Rules:

- Use `search_after` for deep pagination.
- Debounce suggestions.
- Cancel stale suggestion and search requests.
- Render highlight snippets safely.
- Preserve facet counts and selected filters in URL state.

## Job Contract Rules

- Async import/export responses create visible job states.
- Progress is a state machine, not just a number.
- Download links can expire and must be refreshed or re-requested.
- Upload UI validates early but backend validation remains authoritative.
- File type and size errors must be specific.
- Large file jobs must not block route navigation.

## Notification Contract Rules

- Inbox is canonical; toasts are transient hints only.
- Notification target links route to canvas, dashboard, job, workspace, or system context.
- Preferences preserve backend values exactly: instant, hourly, daily, never.
- Muted types preserve backend notification type identifiers.
- Webhook secrets are write-only.
- Read state changes may be optimistic but must roll back on failure.

## Audit Contract Rules

- Audit UI is read-only.
- Do not add mutation controls to audit rows.
- Preserve actor/action/target/workspace/time terminology.
- Audit filters must be URL-addressable.
- Audit data remains workspace-scoped.

## Required UI States

- Search idle/loading/no results/failed.
- Suggestions loading/empty.
- Facets selected.
- Job queued/running/succeeded/failed.
- Download URL expired.
- Upload rejected.
- Notifications empty/read/unread.
- Preferences saved.
- Webhook add failed.
- Audit empty.
- Audit service unavailable.
- Health degraded.

## Accessibility Requirements

- Search input has a label and keyboard-accessible suggestions.
- Facets are keyboard reachable and not color-only.
- Progress indicators have text equivalents.
- Notification unread state is not color-only.
- Audit filters have labels and preserve focus after applying.
- Command palette supports keyboard navigation and Escape close.
- Toasts use a polite live region.

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
docker compose up -d gateway search import-export notifications audit-log background-jobs core redis kafka postgres opensearch minio
```

Smoke checks:

- Search renders highlights and facets from fixture.
- Search uses `nextSearchAfter` for next page.
- Suggestions debounce and cancel stale requests.
- Job list renders queued/running/succeeded/failed states.
- Notification unread count updates after mark read.
- Preferences preserve digest enum values.
- Webhook secret is not displayed after save.
- Audit page has no mutation controls.
- Health degraded state explains affected features.

## Tests to Add

- Search mapper handles result types and highlights.
- Search pagination uses `nextSearchAfter` and does not exceed backend deep page limit.
- Suggest client cancels stale requests.
- Job state machine renders correct action for each state.
- Expired download URL shows refresh/retry path.
- Notification read optimistic update rolls back on failure.
- Preferences form preserves exact enum strings.
- Webhook secret is write-only.
- Audit route renders read-only rows and filters.
- Health overview maps service readiness to affected UI features.

## Out of Scope

- Canvas editor implementation.
- Dashboard widget editor implementation.
- Auth/workspace foundation implementation.
- Backend API changes.

## Completion Criteria

The slice is complete when search, jobs, notifications, audit, and health/activity surfaces are implemented with typed contracts, explicit failure states, accessible controls, and no direct internal service calls.
