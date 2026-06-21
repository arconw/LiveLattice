# Search - Technical Design

## Responsibilities

- Full-text search across canvases, dashboards, comments, and workspace content
- Autocomplete/suggest endpoint
- Faceted search (filter by type, workspace, date, tags)
- Index management (create, update, delete documents)
- Re-index on demand

## Technology Stack

- **Runtime**: Java 21 / Spring Boot 4.x baseline; exact patch version is pinned during implementation
- **Search engine**: OpenSearch 2.x (fork of Elasticsearch)
- **Client**: OpenSearch Java Client with Spring Data Elasticsearch
- **Indexing**: Kafka consumer for document change events
- **Queue**: Kafka for async index updates
- **Caching**: Redis for frequent search suggestions (Ngram-based)

## Index Mapping

### Canvas Index

```json
{
  "index": "canvases",
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 2,
    "analysis": {
      "analyzer": {
        "canvas_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "stop", "snowball", "edge_ngram"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":          { "type": "keyword" },
      "workspace_id": { "type": "keyword" },
      "title":        { "type": "text", "analyzer": "canvas_analyzer", "fields": { "keyword": { "type": "keyword" } } },
      "content_text": { "type": "text", "analyzer": "canvas_analyzer" },
      "tags":         { "type": "keyword" },
      "created_by":   { "type": "keyword" },
      "created_at":   { "type": "date" },
      "updated_at":   { "type": "date" },
      "is_deleted":   { "type": "boolean" }
    }
  }
}
```

### Comment Index

```json
{
  "index": "comments",
  "mappings": {
    "properties": {
      "id":          { "type": "keyword" },
      "canvas_id":   { "type": "keyword" },
      "workspace_id": { "type": "keyword" },
      "content":     { "type": "text", "analyzer": "canvas_analyzer" },
      "author_id":   { "type": "keyword" },
      "created_at":  { "type": "date" },
      "resolved":    { "type": "boolean" }
    }
  }
}
```

## Document Sync (Change Data Capture)

```
Canvas Created   -> Kafka: canvas.created   -> Search consumer -> Index document
Canvas Updated   -> Kafka: canvas.updated   -> Search consumer -> Update document
Canvas Deleted   -> Kafka: canvas.deleted   -> Search consumer -> Soft delete (is_deleted=true)
Comment Added    -> Kafka: comment.added    -> Search consumer -> Index document
Comment Deleted  -> Kafka: comment.deleted  -> Search consumer -> Delete document
```

## Search Query Flow

```
1. Client GET /search?q=diagram&type=canvas&workspace_id=abc&page=1&size=20
2. Search controller:
   a. Build OpenSearch query:
      - must: multi_match query on title^3, content_text, tags^2
      - filter: workspace_id, type, date range, is_deleted=false
   b. Execute query (opensearch:9200/canvases,comments/_search)
   c. Parse hits -> SearchResult DTO
   d. Add highlight snippets
   e. Return paginated results
```

## Suggest/Autocomplete

```
GET /search/suggest?q=diag
-> Redis check: suggest:{workspace_id}:diag
-> Miss -> OpenSearch completion suggester on title field
-> Cache in Redis for 5 minutes
-> Return ["diagram", "diagram flow", "diagram mindmap", ...]
```

## Index Lifecycle Management (ILM)

```json
{
  "policy": {
    "phases": {
      "hot":  { "min_age": "0d", "actions": { "rollover": { "max_size": "50GB", "max_age": "30d" } } },
      "warm": { "min_age": "30d", "actions": { "replica": { "number_of_replicas": 1 } } },
      "delete": { "min_age": "365d", "actions": { "delete": {} } }
    }
  }
}
```

## API Endpoints

```
GET    /search?q=&type=&workspace_id=&tags=&from=&size=
       -> Full-text search with filters
GET    /search/suggest?q=&workspace_id=
       -> Autocomplete suggestions
POST   /search/reindex
       -> Trigger full re-index (admin only)
GET    /search/indices
       -> Index status, doc count, size
```

## Performance Considerations

- **Search-as-you-type**: Edge ngram filter for fast prefix matching (< 50ms)
- **Pagination**: Deep pagination via `search_after` (not `from`/`size`) for >10000 results
- **Highlighting**: Fast vector highlights for content snippets
- **Bulk indexing**: 1000 documents per bulk request, flushed every 5s
- **Shard strategy**: 3 shards per index, routed by `workspace_id` for query parallelism
- **Connection pooling**: OpenSearch client connection pool max 10 connections per route
- **Timeout**: Search timeout 5s, suggestion timeout 1s
