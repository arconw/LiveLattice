# k6 Performance Tests

The k6 suite runs from Docker Compose against the local backend stack. The default profile is smoke-level and safe for local use.

Start the backend and observability stacks first:

```bash
docker compose up -d
docker compose -f compose.observability.yaml up -d
```

Run all smoke scripts:

```bash
bash k6/run-all.sh
```

Run a subset:

```bash
K6_SCRIPTS="smoke.js search.js" bash k6/run-all.sh
```

Authenticated scenarios are enabled only when credentials or a token are supplied:

```bash
export K6_AUTH_USERNAME="owner@example.com"
read -rsp "K6 auth password: " K6_AUTH_PASSWORD
export K6_AUTH_PASSWORD
bash k6/run-all.sh
unset K6_AUTH_PASSWORD
```

The scripts never embed credentials or internal service secrets. If auth is not configured, they still verify public health/readiness and gateway or direct service auth-boundary failures. Reports are written under `k6/reports/`, with `k6/reports/summary.json` pointing at the latest profile run.

Available profiles:

| Profile | Purpose |
|---|---|
| `smoke` | One bounded iteration per script. |
| `baseline` | Script-specific load profile matching the Stage 14 prompt targets where practical. |

Endpoint variables default to Compose service DNS names inside the `livelattice_default` network and can be overridden with `BASE_URL`, `CORE_BASE_URL`, `REALTIME_BASE_URL`, `SEARCH_BASE_URL`, `NOTIFICATIONS_BASE_URL`, `IMPORT_EXPORT_BASE_URL`, `AUDIT_LOG_BASE_URL`, `BACKGROUND_JOBS_BASE_URL`, and `PROMETHEUS_BASE_URL`.
