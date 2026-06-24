# LiveLattice Frontend

React and TypeScript frontend shell for LiveLattice.

## Local Development

```bash
npm ci
npm run dev
```

The dev server listens on `0.0.0.0` through Vite. The browser app calls Gateway-relative REST routes only, so local backend integration should run it behind Gateway or another same-origin proxy.

## Quality Gate

```bash
npm run typecheck
npm run lint
npm test
npm run build
```

The test suite includes contract fixture checks, API mapper tests, route smoke tests, keyboard accessibility checks, critical axe accessibility checks, responsive smoke checks, reduced-motion checks, and rollback/failure state coverage.

## Environment

Set `VITE_REALTIME_URL` to the approved realtime Socket.IO base URL. The frontend appends `/ws/{workspaceId}`; for Docker Compose local runs this is usually `http://localhost:3002` when `REALTIME_PORT` is left at its default. If it is unset, the canvas editor shows realtime as not configured instead of falling back to an unverified same-origin websocket path.

The frontend build does not require backend service secrets. Do not put internal service URLs or trusted Gateway headers into `VITE_*` variables; only browser-safe runtime entrypoints belong there.

## Contract Fixtures

Fixtures live under `src/contracts/fixtures/`, `src/contracts/activity-fixtures.ts`, and `src/contracts/quality-fixtures.ts`. They cover auth/session, workspaces and roles, canvas content/comments/snapshots/templates, realtime messages, dashboards/widgets/data sources/data results, search, import/export and background jobs, notifications/preferences/webhooks, audit events, and health summaries.

Run the fixture and mapper checks with:

```bash
npm test -- quality-fixtures
```

## Docker

```bash
docker build -t livelattice-frontend ./frontend
```

The image builds static assets with Vite and serves them through nginx. The nginx runtime proxies `/auth/*`, `/api/*`, and `/ready` to the Compose `gateway` service so browser REST calls remain same-origin and Gateway-relative.

## Docker Compose

From the repository root:

```bash
docker compose config
docker compose build frontend
docker compose up -d frontend gateway
docker compose ps frontend gateway
```

The service is available on `http://localhost:${FRONTEND_PORT:-8088}`. Compose passes `FRONTEND_REALTIME_URL` to the frontend build as `VITE_REALTIME_URL`; by default it uses `http://localhost:3002`, which is a browser-visible realtime entrypoint rather than an internal container URL.
