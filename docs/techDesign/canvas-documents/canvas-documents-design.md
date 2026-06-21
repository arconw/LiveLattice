# Canvas & Documents - Technical Design

## Responsibilities

- Canvas document model with CRDT-based content
- Version history and snapshot management
- Element manipulation (shapes, text, images, connectors)
- Comment/annotation system
- Template management

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline (persistence + query) + NestJS (collaboration)
- **Content storage**: PostgreSQL JSONB for current state + MinIO for snapshots
- **CRDT**: Yjs binary format stored in MinIO, metadata in PostgreSQL
- **Caching**: Redis for active document state (hot documents)
- **Events**: Kafka for content change events

## Canvas Data Model

```
Canvas
|-- id: UUID PK
|-- workspace_id: UUID FK
|-- title: VARCHAR(255)
|-- content: JSONB {
|     elements: [{
|       id: UUID,
|       type: "rectangle" | "circle" | "text" | "image" | "connector" | "arrow" | "freehand",
|       x, y, width, height: number,
|       rotation: number,
|       style: { fill, stroke, strokeWidth, opacity, fontFamily, fontSize, fontWeight },
|       data: { text, src, points, path } (type-dependent),
|       zIndex: number,
|       locked: boolean,
|       groupId: UUID | null
|     }],
|     viewport: { zoom, panX, panY },
|     metadata: { width, height, backgroundColor, gridEnabled }
|   }
|-- version: BIGINT (monotonic)
|-- lock_version: INT (optimistic locking)
|-- snapshot_version: BIGINT (last snapshot)
|-- created_by: UUID FK
|-- updated_by: UUID FK
|-- created_at: TIMESTAMPTZ
|-- updated_at: TIMESTAMPTZ
+-- deleted_at: TIMESTAMPTZ (soft delete)

CanvasSnapshot
|-- id: UUID PK
|-- canvas_id: UUID FK
|-- version: BIGINT
|-- content: JSONB (full state at snapshot)
|-- minio_path: VARCHAR(500) (path to binary Yjs snapshot in MinIO)
|-- snapshot_at: TIMESTAMPTZ
+-- UNIQUE(canvas_id, version)

Comment
|-- id: UUID PK
|-- canvas_id: UUID FK
|-- parent_id: UUID FK (null for top-level, thread replies)
|-- author_id: UUID FK
|-- content: TEXT
|-- resolved: BOOLEAN
|-- resolved_by: UUID FK
|-- resolved_at: TIMESTAMPTZ
|-- target_element_id: UUID (null for general comments)
|-- created_at: TIMESTAMPTZ
+-- updated_at: TIMESTAMPTZ

CanvasTemplate
|-- id: UUID PK
|-- workspace_id: UUID FK
|-- name: VARCHAR(255)
|-- category: VARCHAR(50)
|-- thumbnail: VARCHAR(500) (MinIO path)
|-- content: JSONB (same structure as Canvas.content)
+-- created_at: TIMESTAMPTZ
```

## Snapshot Lifecycle

```
Every 50 ops OR 30s:
  1. Serialize Y.Doc to binary via Yjs (snappy compressed)
  2. Upload to MinIO: snapshots/{ws_id}/{canvas_id}/{version}.yjs.snappy
  3. Insert record in canvas_snapshots table
  4. Update canvas.snapshot_version

On room join:
  1. Load latest snapshot from MinIO (or PostgreSQL for recent)
  2. Load all ops since snapshot_version from Kafka (compacted topic)
  3. Replay ops onto Y.Doc
  4. Serve current state to client
```

## REST API

```
GET    /canvases                -> List canvases (paginated, filterable)
POST   /canvases                -> Create canvas (with optional template)
GET    /canvases/:id            -> Get canvas (latest content)
PATCH  /canvases/:id            -> Update canvas metadata (title, etc.)
DELETE /canvases/:id            -> Soft delete canvas
POST   /canvases/:id/duplicate  -> Duplicate canvas
GET    /canvases/:id/history    -> List snapshots with timestamps
GET    /canvases/:id/history/:version -> Get content at specific version
POST   /canvases/:id/restore/:version -> Restore to version
GET    /canvases/:id/comments   -> List comments
POST   /canvases/:id/comments   -> Add comment
PATCH  /canvases/:id/comments/:commentId -> Edit/resolve comment
DELETE /canvases/:id/comments/:commentId -> Delete comment

GET    /templates               -> List templates
POST   /templates               -> Save canvas as template
```

## Performance Considerations

- **Content compression**: JSONB content compressed at application level with Snappy before storage
- **Large canvases**: Canvas with >10000 elements paginate element data; viewport-based loading
- **Snapshot retention**: Keep last 100 snapshots in PostgreSQL; older in MinIO only
- **Batch operations**: Element position updates batched in single PATCH (not per-element)
- **Template cache**: Frequently used templates cached in Redis with 1-hour TTL
- **Comment pagination**: Cursor-based with `(created_at, id)` composite index
