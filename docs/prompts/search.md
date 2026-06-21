# Stage 9: Search

## Objective

Implement full-text search over canvases and comments using OpenSearch with Kafka-based indexing, autocomplete suggestions, and faceted search.

## Requirements

1. Initialize Spring Boot project in `services/search/` with:
   - Spring Data Elasticsearch (OpenSearch client)
   - Kafka consumer for document change events
   - Redis for suggest cache
2. Configure OpenSearch index mappings:
   - `canvases` index with custom analyzer (edge ngram, snowball, stop words)
   - `comments` index with standard analyzer
   - ILM policy: hot 30d, warm 30d, delete 365d
3. Implement Kafka consumers:
   - Listen to `canvas.created`, `canvas.updated`, `canvas.deleted`
   - Listen to `comment.added`, `comment.deleted`
   - Batch index operations (bulk API, 1000 docs per request, flush every 5s)
4. Implement `SearchService`:
   - `GET /search?q=&type=&workspace_id=&tags=&from=&size=`
   - Multi-match query with field boosting: `title^3`, `content_text`, `tags^2`
   - Filter by `workspace_id`, `type` (canvas/comment), `tags`, date range
   - Highlight snippets in results
   - Faceted aggregation by `type`, `tags`
   - Pagination via `search_after` for deep pages
5. Implement `SuggestService`:
   - `GET /search/suggest?q=&workspace_id=`
   - OpenSearch completion suggester on `title` field
   - Cache suggestions in Redis (5 min TTL)
   - Return top 10 suggestions
6. Implement reindex endpoint:
   - `POST /search/reindex` - truncate and rebuild all indices from PostgreSQL
   - Admin-only, async
7. Write unit and integration tests with Testcontainers (OpenSearch module)

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Index updates must be consumed from Kafka (not direct DB queries)
- Search suggestions must be cached in Redis

## Verification

```bash
# Start infra + core + search
docker compose up -d

# Check index exists
curl http://localhost:9200/_cat/indices

# Search
curl "http://localhost:8081/search?q=diagram&workspace_id=ws-123&type=canvas"

# Suggest
curl "http://localhost:8081/search/suggest?q=diag&workspace_id=ws-123"

# Reindex
curl -X POST http://localhost:8081/search/reindex \
  -H "x-user-id: admin"

# Run tests
cd services/search && ./gradlew test
```
