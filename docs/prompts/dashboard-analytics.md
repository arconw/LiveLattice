# Stage 6: Core Domain - Dashboard & Analytics

## Objective

Implement dashboard CRUD, widget management, data source configuration, and query execution engine against ClickHouse.

## Requirements

1. Add Flyway migration for `dashboards`, `widgets`, and `data_sources` tables
2. Implement `DashboardService`:
   - CRUD with JSONB layout (12-column grid system)
   - Time range configuration (relative: `24h`, `7d`, `30d`; absolute: `{start, end}`)
   - Auto-refresh interval (per dashboard, in seconds)
   - Duplicate dashboard with all widgets
3. Implement `WidgetService`:
   - CRUD with type, query DSL, display options, position
   - Widget types: BAR_CHART, LINE_CHART, PIE_CHART, TABLE, STAT, HEATMAP, MARKDOWN
4. Implement `DataSourceService`:
   - CRUD with encrypted config storage (AES-256-GCM)
   - Types: CLICKHOUSE, POSTGRESQL, PROMETHEUS, REST_API, CSV
   - Test connection endpoint
5. Implement `QueryEngine`:
   - Resolve data source config (decrypt)
   - Transform widget query JSON to target DSL (ClickHouse SQL, PromQL, etc.)
   - Execute via connection pool (HikariCP for JDBC, OkHttp for REST)
   - Cache results in Redis (30s TTL default)
   - Parallel execution of widget queries (max 10 concurrent)
6. Implement result transformation:
   - Unified format: `{ columns: [...], rows: [...], meta: { totalRows, executedAt } }`
   - Truncate results > 10000 rows with warning
7. Implement dashboard data endpoint:
   - `GET /dashboards/:id/data` - execute all widget queries in parallel
   - `GET /dashboards/:id/widgets/:widgetId/data` - single widget
8. Add ClickHouse initialization SQL in `compose.yaml` (create `canvas_events` and `dashboard_queries` tables)
9. Write unit and integration tests with Testcontainers (ClickHouse module)

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Data source passwords must be encrypted at rest
- Widget query results must be cached in Redis, not recomputed on every request

## Verification

```bash
# Create data source
curl -X POST http://localhost:8080/data-sources \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"workspaceId":"ws-123","name":"Canvas Events","type":"CLICKHOUSE","config":{"host":"clickhouse","port":8123,"database":"default","table":"canvas_events"}}'

# Create dashboard
curl -X POST http://localhost:8080/dashboards \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"workspaceId":"ws-123","title":"Analytics","layout":{"columns":12,"widgets":[]}}'

# Add widget
curl -X POST http://localhost:8080/dashboards/dash-123/widgets \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"type":"BAR_CHART","title":"Events by Type","dataSourceId":"ds-123","query":{"metrics":[{"expression":"count(*)","alias":"count"}],"dimensions":[{"field":"event_type","alias":"type"}],"limit":10}}'

# Get dashboard data
curl http://localhost:8080/dashboards/dash-123/data

# Run tests
cd core && ./gradlew test --tests *DashboardService*
```
