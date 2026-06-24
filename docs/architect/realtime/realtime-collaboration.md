# Realtime Collaboration

## Architecture

```mermaid
graph LR
    subgraph Client["WebSocket Client"]
        WS_CLIENT["Socket.IO / native WS"]
        LOCAL_CRDT["Local CRDT copy"]
    end

    subgraph Realtime["Realtime Service (NestJS)"]
        WS_GATEWAY["WebSocket Gateway"]
        ROOM_MGR["Room Manager<br/>(Redis adapter)"]
        PRESENCE["Presence Tracker"]
        COLLAB["Collaboration Engine"]
        AWARENESS["Awareness (cursors)"]
        BROADCAST["Broadcast"]
    end

    subgraph Bus["Event Bus"]
        KAFKA["Kafka"]
    end

    subgraph Cache["Cache"]
        REDIS["Redis Pub/Sub + Store"]
    end

    subgraph Storage["Persistence"]
        PG[("PostgreSQL")]
    end

    WS_CLIENT <--> WS_GATEWAY
    WS_GATEWAY --> ROOM_MGR
    ROOM_MGR --> PRESENCE
    ROOM_MGR --> COLLAB
    COLLAB --> AWARENESS
    COLLAB --> BROADCAST
    BROADCAST --> WS_GATEWAY
    COLLAB --> KAFKA
    KAFKA --> PG
    REDIS <--> ROOM_MGR
```

## Connection Lifecycle

1. Client connects to `/ws?token=JWT` -> Gateway validates, extracts `workspace_id`
2. Client sends `join:room` with `canvas_id` -> Room Manager adds to Redis-backed room
3. Presence announces user online; awareness broadcasts cursor position
4. On disconnect -> presence marks away (30s grace), then removes from room

## Collaboration Model

- **Conflict Resolution**: CRDT (Yjs-based) for canvas element operations
  - Each operation is a unique, idempotent delta
  - No central merge server - peers exchange operations via room broadcast
  - Periodic snapshots persisted to PostgreSQL + MinIO
- **Operation format**: JSON patch (RFC 6902) with Lamport timestamp + client ID
- **Rate of sync**: operations batched every 50ms or 50 operations, whichever comes first

## Message Protocol

| Direction | Event | Payload |
|---|---|---|
| Client -> Server | `canvas:op` | `{ ops: Op[], version: number, lockVersion: number, seq: number }` |
| Server -> Client | `canvas:ack` | `{ version: number, lockVersion: number, seq: number }` |
| Server -> Client | `canvas:op` | `{ ops: Op[], version: number, lockVersion: number, origin: string }` |
| Client -> Server | `presence:update` | `{ canvasId, cursor: { x, y }, selection: string[], status: 'away' | 'online' }` |
| Server -> Client | `presence:update` | `{ canvasId, subject, displayName, payload: { cursor, selection, status } }` |
| Server -> Client | `room:join` / `room:leave` | `{ userId, userName }` |

## Conversation Events (comments/chat)

- Messages are sent via REST or WebSocket, stored in PostgreSQL via Core Domain
- Realtime service receives `Canvas.CommentAdded` event from Kafka and broadcasts to room

## Scalability

- **Horizontal scaling**: Multiple NestJS realtime instances; Redis pub/sub relays messages between instances
- **Room sharding**: `canvas_id` hash determines instance affinity; sticky via Redis
- **Backpressure**: If Kafka consumer falls behind, non-critical events (awareness) are dropped; canvas ops still processed
- **Connection limits**: 10k concurrent per instance; 50k per room via fan-out

## Fault Tolerance

- Client reconnects with exponential backoff (100ms -> 5s), sends last known version
- Server responds with missing operations since that version
- If server crashes, another instance picks up the room via Redis heartbeat; state recovered from PostgreSQL snapshot
