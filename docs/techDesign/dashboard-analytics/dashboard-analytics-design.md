# Dashboard & Analytics - Technical Design

## Responsibilities

- Dashboard CRUD with flexible grid layout
- Widget types: chart (bar, line, pie, area, scatter), table, stat, heatmap, markdown
- Data sources: CSV upload, SQL query (ClickHouse), REST API, Prometheus
- Query execution and result caching
- Time range selection and auto-refresh
- Dashboard sharing and embedding

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline; exact patch version is pinned during implementation
- **Analytics DB**: ClickHouse (primary) / TimescaleDB alternative
- **Cache**: Redis for query results
- **Visualization**: Server-side data transformation; chart rendering done client-side
- **Events**: Kafka for dashboard/widget change events
- **DSL**: Custom JSON-based query DSL for data sources

## Dashboard Data Model

```
Dashboard
|-- id: UUID PK
|-- workspace_id: UUID FK
|-- title: VARCHAR(255)
|-- description: TEXT
|-- layout: JSONB {
|     columns: 12,
|     gap: number,
|     widgets: [{ widgetId, x, y, w, h }]
|   }
|-- time_range: JSONB { type: "relative"|"absolute", value: "24h"|"7d"|null, start: null, end: null }
|-- auto_refresh: INT (seconds, 0 = disabled)
|-- is_public: BOOLEAN (for sharing)
|-- created_by: UUID FK
|-- created_at: TIMESTAMPTZ
+-- updated_at: TIMESTAMPTZ

Widget
|-- id: UUID PK
|-- dashboard_id: UUID FK
|-- type: WidgetType { BAR_CHART, LINE_CHART, PIE_CHART, TABLE, STAT, HEATMAP, MARKDOWN }
|-- title: VARCHAR(255)
|-- data_source_id: UUID FK (nullable for markdown)
|-- query: JSONB {
|     metrics: [{ expression, alias, aggregation }],
|     dimensions: [{ field, alias }],
|     filters: [{ field, operator, value }],
|     order_by: [{ field, direction }],
|     limit: number
|   }
|-- options: JSONB {
|     { showLegend, stacked, smooth, colorScheme, decimalPlaces, thresholds }
|   }
|-- position: JSONB { x, y, w, h }
|-- created_at: TIMESTAMPTZ
+-- updated_at: TIMESTAMPTZ

DataSource
|-- id: UUID PK
|-- workspace_id: UUID FK
|-- name: VARCHAR(255)
|-- type: DataSourceType { CLICKHOUSE, POSTGRESQL, PROMETHEUS, REST_API, CSV }
|-- config: JSONB (encrypted) {
|     { host, port, database, table, query, url, headers, authType }
|   }
|-- created_by: UUID FK
|-- created_at: TIMESTAMPTZ
+-- updated_at: TIMESTAMPTZ
```

## Query Execution Pipeline

```
1. Client requests dashboard load (or auto-refresh)
2. For each widget:
   a. Check Redis for cached result (key: query:{widget_id}:{time_range_hash})
   b. Cache hit -> return cached data
   c. Cache miss:
      - Resolve data source config (decrypt sensitive fields)
      - Transform widget query to target DSL (ClickHouse SQL, PromQL, etc.)
      - Execute query via connection pool
      - Transform result to unified format: { columns: [...], rows: [...], meta: { totalRows, executedAt } }
      - Cache in Redis with TTL (30s default, configurable)
      - Return result
3. Aggregate all widget results into { widgets: [{ widgetId, data, error? }] }
4. Return to client
```

## Query DSL Example

```json
{
  "metrics": [
    { "expression": "count(*)", "alias": "events", "aggregation": "SUM" }
  ],
  "dimensions": [
    { "field": "event_type", "alias": "type" }
  ],
  "filters": [
    { "field": "timestamp", "operator": "BETWEEN", "value": ["{from}", "{to}"] },
    { "field": "workspace_id", "operator": "EQ", "value": "{workspace_id}" }
  ],
  "order_by": [{ "field": "events", "direction": "DESC" }],
  "limit": 50
}
```

## ClickHouse Query Template (rendered from DSL)

```sql
SELECT
    event_type AS "type",
    count(*) AS "events"
FROM canvas_events
WHERE timestamp BETWEEN {from:DateTime64} AND {to:DateTime64}
  AND workspace_id = {workspace_id:UUID}
GROUP BY event_type
ORDER BY events DESC
LIMIT 50
```

## API Endpoints

```
GET    /dashboards                -> List dashboards
POST   /dashboards                -> Create dashboard
GET    /dashboards/:id            -> Get dashboard with widget metadata
PATCH  /dashboards/:id            -> Update dashboard layout/settings
DELETE /dashboards/:id            -> Delete dashboard and widgets
POST   /dashboards/:id/duplicate  -> Duplicate dashboard

GET    /dashboards/:id/data       -> Execute all widget queries -> full data
GET    /dashboards/:id/widgets/:widgetId/data -> Execute single widget query

POST   /dashboards/:id/widgets    -> Add widget
PATCH  /dashboards/:id/widgets/:widgetId -> Update widget
DELETE /dashboards/:id/widgets/:widgetId -> Remove widget

GET    /data-sources              -> List data sources
POST   /data-sources              -> Create data source
PATCH  /data-sources/:id          -> Update data source
DELETE /data-sources/:id          -> Delete data source
POST   /data-sources/:id/test    -> Test connection
```

## Performance Considerations

- **Query result cache**: Redis with per-widget TTL; invalidated on data source change
- **Parallel execution**: Widget queries executed in parallel via `CompletableFuture` (max 10 concurrent)
- **ClickHouse speed**: Columnar storage, materialized views for common aggregations, TTL-based partitioning
- **Large results**: Paginated query results (>10000 rows truncated); client-side aggregation for large datasets
- **Empty results**: Cached with short TTL (5s) to avoid thundering herd on empty time ranges
- **Connection pooling**: HikariCP for ClickHouse JDBC; max 20 connections per instance
