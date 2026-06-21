# Core Domain - Technical Design

## Responsibilities

- Canonical source of truth for workspace, canvas, dashboard, widget, data source entities
- Command-Query Responsibility Segregation (CQRS) within the same service
- Publish domain events to Kafka for downstream consumers
- Enforce business rules, validation, and authorization

## Technology Stack

- **Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 4.x baseline with Spring Web MVC; exact patch version is pinned during implementation
- **ORM**: Spring Data JPA + Hibernate 6 with PostgreSQL dialect
- **CQRS**: Custom command/query bus with `@CommandHandler` / `@QueryHandler` annotations
- **Validation**: Jakarta Bean Validation 3.0 + `@Validated`
- **Mapping**: MapStruct for entity <-> DTO mapping
- **Events**: Spring ApplicationEvent -> Kafka producer (via `@EventListener` + `KafkaTemplate`)
- **Caching**: Spring Cache Abstraction with Redis (cache-aside)
- **Migration**: Flyway with versioned SQL migrations

## Domain Model

```
Workspace
|-- WorkspaceMember (User, Role)
|-- Canvas (content: JSONB, version, snapshots)
|-- Dashboard (layout: JSONB)
|   +-- Widget (type, dataSource, query, position)
|-- DataSource (type, config: encrypted JSONB)
+-- AuditEvent (immutable, partitioned)
```

## CQRS Architecture

```
                         +--------------+
                         |   Gateway     |
                         +------+-------+
                                |
                   +------------+------------+
                   |                         |
            +------v------+          +------v------+
            |  CommandBus |          |   QueryBus  |
            | (write path)|          | (read path) |
            +------+------+          +------+------+
                   |                         |
            +------v------+          +------v------+
            |  Command    |          |    Query    |
            |  Handlers   |          |   Handlers  |
            +------+------+          +------+------+
                   |                         |
            +------v------+          +------v------+
            |  Domain     |          | Repository  |
            |  Services   |          | (read-only) |
            +------+------+          +------+------+
                   |                         |
            +------v------+          +------v------+
            |  Repository |          |   PostgreSQL|
            |  (write)    |          |  (query opt)|
            +------+------+          +-------------+
                   |
            +------v------+
            |  Event Bus  |
            |  (Kafka)    |
            +-------------+
```

## Key Commands

| Command | Event Published |
|---|---|
| `CreateWorkspace` | `WorkspaceCreated` |
| `UpdateWorkspaceSettings` | `WorkspaceSettingsUpdated` |
| `InviteWorkspaceMember` | `WorkspaceMemberInvited` |
| `RemoveWorkspaceMember` | `WorkspaceMemberRemoved` |
| `CreateCanvas` | `CanvasCreated` |
| `UpdateCanvasContent` | `CanvasUpdated` |
| `DeleteCanvas` | `CanvasDeleted` |
| `CreateDashboard` | `DashboardCreated` |
| `UpdateDashboardLayout` | `DashboardLayoutUpdated` |
| `CreateWidget` | `WidgetCreated` |
| `UpdateWidgetQuery` | `WidgetQueryUpdated` |
| `DeleteWidget` | `WidgetDeleted` |
| `CreateDataSource` | `DataSourceCreated` |
| `UpdateDataSourceConfig` | `DataSourceConfigUpdated` |

## Performance Considerations

- **Batch operations**: `saveAll` in repository, flush at transaction commit
- **Read replicas**: `@Transactional(readOnly = true)` routed to read replica via `RoutingDataSource`
- **Optimistic locking**: `@Version` on Canvas entity to prevent concurrent overwrite conflicts
- **Pessimistic locking**: `PESSIMISTIC_WRITE` on workspace member operations
- **JSONB indexes**: GIN index on `canvas.content` and `dashboard.layout` for JSON path queries
- **Connection pool**: HikariCP, max 40 connections per instance, 5s timeout
- **Query logging**: `datasource-proxy` for debug logging of slow queries (>100ms)
