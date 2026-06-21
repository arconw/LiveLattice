# System Architecture

## High-Level Diagram

```mermaid
graph TB
    subgraph Client["Client Layer"]
        WEB["Web App<br/>(React)"]
        MOBILE["Mobile App<br/>(React Native)"]
        API_CLIENT["External API Clients"]
    end

    subgraph Gateway["API Gateway (NestJS)"]
        REST["REST API<br/>Port 3000"]
        WS["WebSocket<br/>Port 3001"]
        GATEWAY_AUTH["Auth Guard"]
        GATEWAY_RATE["Rate Limiter"]
        GATEWAY_ROUTER["Service Router"]
    end

    subgraph Realtime["Realtime (NestJS)"]
        ROOM_MGR["Room Manager"]
        PRESENCE["Presence Service"]
        COLLAB["Collaboration Engine<br/>CRDT/OT"]
        BROADCAST["Broadcast Bus"]
    end

    subgraph Core["Core Domain (Spring Boot)"]
        API["REST Controllers"]
        CMD["Command Handlers"]
        QUERY["Query Handlers"]
        EVENT["Event Publishers"]
        DOMAIN["Domain Services"]
        REPO["Repositories"]
    end

    subgraph Integrations["Integration Layer"]
        SEARCH["Search (Spring Boot)"]
        NOTIFY["Notifications"]
        IMPORT["Import/Export"]
        AUDIT["Audit Log"]
        JOBS["Background Jobs"]
    end

    subgraph Messaging["Message Bus"]
        KAFKA["Kafka / RabbitMQ"]
    end

    subgraph Cache["Cache"]
        REDIS["Redis"]
    end

    subgraph Storage["Data Stores"]
        PG[("PostgreSQL<br/>Relational + JSONB")]
        CH[("ClickHouse / TimescaleDB<br/>Time Series")]
        OS[("OpenSearch<br/>Full Text")]
        MINIO[("MinIO<br/>Object Store")]
    end

    subgraph Observability["Observability Stack"]
        OTLP["OpenTelemetry Collector"]
        PROM["Prometheus"]
        GRAF["Grafana"]
        LOKI["Loki"]
        TEMPO["Tempo"]
        JAEGER["Jaeger"]
    end

    WEB --> REST
    WEB --> WS
    MOBILE --> REST
    MOBILE --> WS
    API_CLIENT --> REST

    REST --> GATEWAY_AUTH
    WS --> GATEWAY_AUTH
    GATEWAY_AUTH --> GATEWAY_RATE
    GATEWAY_RATE --> GATEWAY_ROUTER

    GATEWAY_ROUTER --> API
    WS --> ROOM_MGR
    ROOM_MGR --> PRESENCE
    ROOM_MGR --> COLLAB
    COLLAB --> BROADCAST
    BROADCAST --> WS

    API --> CMD
    API --> QUERY
    CMD --> DOMAIN
    DOMAIN --> REPO
    DOMAIN --> EVENT
    EVENT --> KAFKA
    QUERY --> REPO

    REPO --> PG
    REPO --> REDIS
    QUERY --> CH

    SEARCH --> OS
    SEARCH --> KAFKA
    NOTIFY --> KAFKA
    IMPORT --> KAFKA
    AUDIT --> KAFKA
    JOBS --> KAFKA

    REPO -.-> CH
    DOMAIN -.-> REDIS

    GATEWAY_ROUTER -.-> SEARCH
    GATEWAY_ROUTER -.-> NOTIFY
    GATEWAY_ROUTER -.-> IMPORT
    GATEWAY_ROUTER -.-> AUDIT
    GATEWAY_ROUTER -.-> JOBS

    PG --> OTLP
    CH --> OTLP
    OS --> OTLP
    KAFKA --> OTLP
    REDIS --> OTLP
    OTLP --> PROM
    OTLP --> TEMPO
    OTLP --> JAEGER
    PROM --> GRAF
    LOKI --> GRAF
    TEMPO --> GRAF
```

## Deployment Topology

```
Internet -> Load Balancer -> API Gateway (NestJS, 2+ replicas)
                              |-- Realtime (NestJS, collocated or separate)
                              |-- Core Domain (Spring Boot, 3+ replicas)
                              |-- Search (Spring Boot, 2+ replicas)
                              `-- Integrations (Spring Boot, 2+ replicas)

Internal only:
    Kafka / RabbitMQ cluster (3+ nodes)
    Redis cluster (sentinel/raft)
    PostgreSQL primary + replicas
    ClickHouse shards
    OpenSearch cluster
    MinIO (standalone or HA)
    OpenTelemetry Collector (daemonset)
```

## Request Flow (Example: Save Canvas)

1. Client sends PATCH via WebSocket or REST -> Gateway authenticates JWT
2. Gateway routes to Core Domain REST endpoint or Realtime room
3. Core Domain validates, applies OT/CRDT transform, persists to PostgreSQL
4. Domain publishes `CanvasUpdated` event to Kafka
5. Search consumer re-indexes; Audit consumer logs; Broadcast pushes to room
6. Response returns to client with updated version vector

## Key Design Decisions

- **NestJS for gateway/realtime**: native WebSocket support, RxJS for streaming, decorator-based guards
- **Spring Boot for core**: rich transaction management, mature ORM, integration ecosystem
- **Kafka over RabbitMQ as primary event bus**: stronger ordering guarantees, log compaction for audit, replayability
- **CRDTs for canvas collaboration**: conflict-free merging without central server authority
- **ClickHouse for analytics**: columnar storage, sub-second aggregation queries on large datasets
- **OpenSearch for search**: full-text search with rich query DSL, highlight, suggest
- **MinIO for assets**: S3-compatible, self-hosted, no egress costs
