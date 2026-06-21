# Stage 5: Core Domain - Canvas & Documents

## Objective

Implement canvas document CRUD with JSONB content storage, version history, snapshot management, and comment system.

## Requirements

1. Add Flyway migration for `canvases`, `canvas_snapshots`, and `comments` tables
2. Implement `CanvasService`:
   - `CreateCanvas` command with optional template cloning
   - `UpdateCanvasContent` with optimistic locking (`@Version`)
   - `UpdateCanvasMetadata` (title only, lightweight)
   - `DeleteCanvas` (soft delete)
   - `DuplicateCanvas` (deep copy content)
3. Implement `SnapshotManager`:
   - Create snapshot on demand (`POST /canvases/:id/snapshot`)
   - List snapshots with timestamps (`GET /canvases/:id/history`)
   - Get content at specific version (`GET /canvases/:id/history/:version`)
   - Restore to version (`POST /canvases/:id/restore/:version`)
   - Automatic snapshot trigger (background job, 50 ops threshold)
   - Store full snapshot content in MinIO, metadata in PostgreSQL
4. Implement `CommentService`:
   - Create comment (top-level or reply via `parent_id`)
   - Edit comment, resolve comment, delete comment
   - Target comments to specific canvas elements (`target_element_id`)
5. Implement `TemplateService`:
   - Save canvas as template
   - List templates with thumbnail
   - Create canvas from template
6. Implement REST controllers with pagination and cursor-based comment listing
7. Publish events: `CanvasCreated`, `CanvasUpdated`, `CanvasDeleted`, `CommentAdded`
8. Add GIN index on `canvases.content` for JSON path queries
9. Write unit and integration tests

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Canvas content stored as JSONB in PostgreSQL (not separate element table)
- Snapshot retention: keep last 100 in PostgreSQL, older in MinIO only

## Verification

```bash
# Ensure Stage 4 is complete (workspaces exist)

# Create canvas
curl -X POST http://localhost:8080/canvases \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"workspaceId":"ws-123","title":"My Diagram"}'

# Update content
curl -X PATCH http://localhost:8080/canvases/canvas-123 \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"content":{"elements":[{"id":"el-1","type":"rectangle","x":10,"y":10,"width":100,"height":50}]}}'

# Add comment
curl -X POST http://localhost:8080/canvases/canvas-123/comments \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"content":"Great diagram!","targetElementId":"el-1"}'

# List history
curl http://localhost:8080/canvases/canvas-123/history

# Restore version 1
curl -X POST http://localhost:8080/canvases/canvas-123/restore/1

# Run tests
cd core && ./gradlew test --tests *CanvasService*
```
