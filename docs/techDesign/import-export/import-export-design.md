# Import & Export - Technical Design

## Responsibilities

- Import canvases from various formats (draw.io, Figma, Miro, Visio, SVG)
- Export canvases to PNG, SVG, PDF, JSON
- Export dashboard data to CSV, Excel, JSON
- Batch import/export operations
- File validation and size enforcement

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline; exact patch version is pinned during implementation
- **File storage**: MinIO (staging + final)
- **Processing**: Apache POI (Excel), Batik (SVG), iText (PDF), javax.imageio (PNG)
- **Async**: Kafka for large import/export jobs
- **Queue**: Redis-backed job queue (Bull-compatible via `@EnableScheduling` + Redis)
- **Validation**: Apache Tika for file type detection + ClamAV for malware scanning

## Key Modules

| Module | Responsibility |
|---|---|
| `ImportController` | Accept multipart uploads, validate, initiate processing |
| `ExportController` | Accept export requests, stream or generate async |
| `FormatTransformer` | Convert between formats (draw.io <-> canvas JSON, etc.) |
| `FileValidator` | Size, type (magic bytes), malware scan |
| `BatchJobManager` | Queue, track, and report batch import/export jobs |
| `StreamingService` | Chunked streaming for large file downloads |

## Import Pipeline

```
1. Client POST /import (multipart: file + options)
2. Gateway validates file size (max 100MB) and auth
3. Core receives file:
   a. Write to MinIO staging bucket: imports/{workspace_id}/{job_id}/original
   b. Parse file type from magic bytes (not extension)
   c. If unknown type -> reject (422)
   d. Transform format -> Canvas JSON content
   e. Validate transformed content (schema, constraints)
   f. Create Canvas in PostgreSQL
   g. Link any assets (images) to MinIO: assets/{workspace_id}/{canvas_id}/{filename}
   h. Move original file to archive: imports/archive/{job_id}
   i. Return Canvas ID
4. If > 10MB -> process async via Kafka job
   a. Job completes -> notification sent via Notifications service
```

## Export Pipeline

```
1. Client POST /export/{canvasId}?format=svg&quality=high
2. Core loads canvas content from PostgreSQL
3. FormatTransformer converts to target format:
   - SVG: direct XML rendering of canvas elements
   - PNG: Batik rasterizer (SVG -> PNG)
   - JSON: raw canvas content dump
4. Stream response with correct Content-Type header
5. For large exports (> 10MB) -> async job:
   a. Generate file, upload to MinIO
   b. Return { jobId, status: "processing" }
   c. Client polls GET /export/jobs/{jobId}
   d. When complete -> download URL (signed MinIO URL, 1hr expiry)
```

## Format Mapping

| Source Format | Target Format | Notes |
|---|---|---|
| draw.io XML | Canvas JSON | Parse mxGraph model -> element mapping |
| Figma API JSON | Canvas JSON | Via Figma plugin webhook |
| Miro JSON | Canvas JSON | Miro REST API export format |
| Visio VSDX | Canvas JSON | Apache POI -> element mapping |
| SVG | Canvas JSON | Basic shapes only |
| PNG | Canvas (image) | Upload as image element |

| Source Format | Export Format | Notes |
|---|---|---|
| Canvas JSON | SVG | Full vector fidelity |
| Canvas JSON | PNG | Rasterized at configurable DPI (72-300) |
| Canvas JSON | JSON (debug) | Raw content with metadata |
| Canvas JSON | PDF | Print layout, multi-page if large |
| Dashboard data | CSV | Flattened widget data |
| Dashboard data | XLSX | Multi-sheet Excel workbook |

## API Endpoints

```
POST   /import                    -> Upload file (multipart)
GET    /import/jobs/:jobId        -> Check import job status

POST   /export/:canvasId          -> Export canvas (sync if small, async if large)
POST   /export/dashboard/:dashId  -> Export dashboard data
GET    /export/jobs/:jobId        -> Check export job status
GET    /export/jobs/:jobId/download -> Download exported file (signed URL)

POST   /batch/import              -> Multiple file import (zip)
POST   /batch/export              -> Multiple canvas export (zip)
```

## Performance Considerations

- **Streaming**: Large file downloads use `StreamingResponseBody` chunked transfer
- **Async processing**: Files >10MB processed via Kafka; client polls for completion
- **File validation**: Magic byte check before full processing; reject within 100ms
- **Concurrent imports**: Max 5 per user, 20 per workspace (configurable)
- **Temp storage**: Files deleted from staging after 24 hours via scheduled cleanup job
- **Rasterization**: PNG rendering at 150 DPI by default; configurable up to 300 DPI
- **Batch exports**: Zipped via `ZipOutputStream` streaming (no in-memory buffer)
