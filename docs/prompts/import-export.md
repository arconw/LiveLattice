# Stage 8: Import & Export

## Objective

Implement file import (draw.io, SVG) and export (SVG, PNG, PDF, CSV) with format transformation, async processing for large files, and batch operations.

## Requirements

1. Initialize Spring Boot project in `services/import-export/` with:
   - Spring Web, Kafka, MinIO client, Redis
   - Apache Batik (SVG), iText (PDF), Apache POI (Excel)
2. Implement `ImportController`:
   - `POST /import` - multipart file upload with options
   - File validation: magic bytes (not extension), max 100MB, malware scan (ClamAV integration optional placeholder)
   - Small files (<10MB): sync processing, return Canvas ID
   - Large files (>10MB): async via Kafka, return job ID
3. Implement `FormatTransformer`:
   - draw.io XML -> Canvas JSON (parse mxGraph model)
   - SVG -> Canvas JSON (basic shapes)
   - Canvas JSON -> SVG (full fidelity)
   - Canvas JSON -> PNG (Batik rasterizer, 150 DPI default)
   - Canvas JSON -> PDF (iText, multi-page if large)
   - Dashboard data -> CSV
   - Dashboard data -> XLSX (multi-sheet)
4. Implement `ExportController`:
   - `POST /export/:canvasId?format=svg` - sync or async
   - `POST /export/dashboard/:dashId` - export dashboard data
   - File streaming via `StreamingResponseBody` (chunked transfer)
   - Signed MinIO URLs for async job downloads (1hr expiry)
5. Implement `BatchJobManager`:
   - `POST /batch/import` - zip of multiple files
   - `POST /batch/export` - zip of multiple canvases
   - Progress tracking via Redis
   - Notification on completion (via Kafka)
6. Implement job polling endpoints:
   - `GET /export/jobs/:jobId` - status + progress
   - `GET /export/jobs/:jobId/download` - download link
7. Write unit and integration tests with Testcontainers (MinIO)

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- File type detection must use magic bytes, not file extension
- Files >10MB must be processed async (do not block request thread)
- Exports must stream results without buffering entire file in memory

## Verification

```bash
# Create a test SVG file
echo '<svg xmlns="http://www.w3.org/2000/svg"><rect x="10" y="10" width="100" height="50"/></svg>' > test.svg

# Import SVG
curl -X POST http://localhost:8083/import \
  -F "file=@test.svg" \
  -F 'options={"workspaceId":"ws-123","title":"Imported SVG"}' \
  -H "x-user-id: user-123"

# Export canvas as SVG
curl -X POST "http://localhost:8083/export/canvas-123?format=svg" \
  -H "x-user-id: user-123" \
  --output output.svg

# Export as PNG
curl -X POST "http://localhost:8083/export/canvas-123?format=png&quality=high" \
  -H "x-user-id: user-123" \
  --output output.png

# Run tests
cd services/import-export && ./gradlew test
```
