# Realtime Service - Technical Design

## Responsibilities

- Manage WebSocket connections and room membership
- Orchestrate CRDT-based collaborative editing
- Track user presence (cursor, selection, online status)
- Fan-out canvas operations to all room subscribers
- Persist operations to Kafka for downstream consumption

## Technology Stack

- **Runtime**: Node.js 24 LTS
- **Framework**: NestJS 11 with `@nestjs/platform-socket.io` (Socket.IO)
- **CRDT**: Yjs (y-websocket integration) for conflict-free data types
- **Broadcast**: Redis pub/sub for cross-instance room messages
- **Presence**: Redis sorted sets + pub/sub
- **Persistence**: Kafka producer for operation log

## Key Modules

| Module | Responsibility |
|---|---|
| `SocketGateway` | Connection lifecycle, auth, namespace routing |
| `RoomManager` | Redis-backed room membership, instance affinity |
| `PresenceService` | Cursor/selection/online state per room |
| `CollaborationEngine` | CRDT op application, version tracking, snapshots |
| `BroadcastService` | Fan-out to room subscribers (same + cross-instance) |
| `OpPersistenceService` | Batch-write operations to Kafka topic `canvas.ops` |

## Room Architecture

```
Global namespace: /ws/{workspace_id}
  Room: canvas:{canvas_id}
    Members: Set<userId>
    Awareness: Map<userId, {cursor, selection, name, color}>
    Document: Y.Doc instance in memory (lazy-loaded from PG/Redis)
    Operation log: in-memory buffer -> flushed to Kafka every 50ms
```

## Operation Lifecycle

1. Client sends `canvas:op` with batch of operations + version + seq
2. Gateway validates op schema, checks user has edit permission (via Redis cache of workspace roles)
3. CollaborationEngine applies ops to in-memory Y.Doc, increments version
4. BroadcastService sends `canvas:op` to all other room members (including cross-instance via Redis)
5. OpPersistenceService batches ops and publishes to Kafka `canvas.ops`
6. Response: `canvas:ack` with new version and seq

## Snapshot Strategy

- Automatic snapshot every 50 operations or 30 seconds (whichever first)
- Snapshot = Yjs binary encoding of Y.Doc state
- Stored in MinIO as `snapshots/{workspace_id}/{canvas_id}/{version}.yjs`
- Snapshot metadata in PostgreSQL `canvas_snapshots` table
- On room join: load latest snapshot + replay ops since that version

## Performance Considerations

- In-memory Y.Doc per active canvas (not per connection) - shared across room members on same instance
- Op batching: 50ms flush window or 50 ops batch, whichever first
- Awareness updates: throttled to 100ms, not persisted to Kafka (ephemeral)
- Cross-instance traffic: Redis pub/sub, not Kafka (lower latency)
- Snapshot compression: Snappy before MinIO upload
- Instance affinity: `consistent-hashing(canvas_id) % instances` for room assignment
