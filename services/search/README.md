# Search

Spring Boot search service for LiveLattice.

## Endpoints

- `GET /health`
- `GET /ready`
- `GET /search?q=&type=&workspace_id=&tags=&from=&to=&page=&size=&search_after=`
- `GET /search/suggest?q=&workspace_id=`
- `POST /search/reindex`
- `GET /search/indices`

## Runtime

Configuration is environment-driven:

- `OPENSEARCH_URL`, default `http://localhost:9200`
- `REDIS_HOST`, `REDIS_PORT`
- `KAFKA_BROKERS`
- `SEARCH_KAFKA_TOPICS`
- `INTERNAL_AUTH_SECRET`
- `SEARCH_REINDEX_ADMIN_ROLE`, default `admin`
- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`

The service creates indexes for canvases, documents, dashboards, comments, templates, and users, and attaches the configured OpenSearch ISM lifecycle policy. Kafka indexing events are batched up to `SEARCH_BULK_BATCH_SIZE` and flushed every `SEARCH_BULK_FLUSH_INTERVAL`.

`/search/reindex` recreates the indexes asynchronously and rebuilds available Core-backed search documents from PostgreSQL. Normal document changes are still consumed from Kafka.

## Verification

```bash
docker build -t livelattice-search services/search
```

Integration tests are tagged `integration` and are excluded by the Docker build path.
