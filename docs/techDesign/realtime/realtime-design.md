# Realtime Service - Technical Design

## Responsibilities

- Manage WebSocket connections and room membership
- Orchestrate CRDT-based collaborative editing
- Track user presence (cursor, selection, online status)
- Fan-out canvas operations to all room subscribers
- Persist operations to Kafka for downstream consumption

## Technology Stack

- **Runtime**: Node.js 24
- **HTTP**: Fastify for `/health` and `/ready`
- **WebSocket**: Socket.IO 4 (dynamic namespace `/^\/ws\/.+$/`)
- **CRDT**: Yjs for in-memory per-canvas documents
- **Redis**: ioredis for room membership, snapshots, awareness, and cross-instance pub/sub
- **Persistence**: kafkajs producer for the canvas operation log
- **Auth**: jose for Keycloak JWT verification

## Key Modules

| Module | File | Responsibility |
|---|---|---|
| `config` | `src/config.ts` | Environment parsing |
| `AuthService` | `src/auth.ts` | JWT verification, identity extraction, Core membership check |
| `RedisStores` / `MemoryStores` | `src/redis-stores.ts`, `src/memory-stores.ts` | Room membership, snapshots, awareness, pub/sub, membership cache |
| `RoomManager` | `src/room-manager.ts` | Room join/leave, Redis set membership, room name mapping |
| `CollaborationEngine` | `src/collaboration.ts` | Yjs per-canvas docs, operation application, version increment, snapshots |
| `PresenceService` | `src/presence.ts` | Throttled awareness updates, ephemeral Redis storage |
| `BroadcastService` | `src/broadcast.ts` | Redis pub/sub fan-out, instance-id loopback suppression |
| `OpPersistenceService` | `src/op-persistence.ts` | Kafka operation buffer and flush |
| `BackpressureTracker` | `src/backpressure.ts` | Per-socket rate tracking, awareness dropping |
| `createRealtimeServer` | `src/realtime-server.ts` | Socket.IO server, namespace middleware, event handlers |
| `createHttpServer` | `src/http-server.ts` | Fastify `/health` and `/ready` |
| `main` / `start` | `src/main.ts`, `src/start.ts` | Composition root and process lifecycle |

## Room Architecture

```
Namespace: /ws/{workspaceId}  (matched by /^\/ws\/.+$/)
  Socket.IO room: canvas:{canvasId}
    Redis set: canvas:{canvasId}            -> {socketId,...}
    Redis hash: canvas:snapshot:{canvasId}  -> {version, state}
    Redis key:  canvas:awareness:{canvasId}:{subject}  (TTL 60s)
    Document: Y.Doc instance in memory (one per active canvas per instance)
    Operation buffer: in-memory -> flushed to Kafka canvas.ops every 50 ops or 50ms
```

## Authentication and Authorization

1. Client connects to `/ws/{workspaceId}` with `auth.token` or `?token=JWT`.
2. Namespace middleware verifies the JWT against the Keycloak JWKS (`AUTH_ISSUER`, `AUTH_JWKS_URI`, optional `AUTH_AUDIENCE`).
3. Extracted claims: `sub`, `email`, `displayName`, `realm_access.roles`.
4. Core is called at `GET /workspaces/{workspaceId}` with trusted headers `x-internal-auth-token`, `x-auth-subject`, `x-auth-email`, `x-auth-display-name`.
5. Membership is cached in Redis for `CORE_MEMBERSHIP_CACHE_TTL_SECONDS`.
6. Missing token, invalid token, and non-member connections are rejected.
7. `AUTH_DISABLED=true` bypasses both JWT and membership checks (tests only).

## Operation Lifecycle

1. Client emits `canvas:op` with `{ canvasId, ops, version?, seq? }`.
2. Server requires the socket to have joined `canvas:{canvasId}` first.
3. `CollaborationEngine.applyOperations` applies each op to the in-memory `Y.Doc` (`add`, `update`, `remove`, `update-state`) and increments the server-side canvas version by 1 for client-originated operations.
4. Ack `canvas:ack` returns `{ ok, canvasId, version, seq }`.
5. The accepted operation is broadcast to other members in the same Socket.IO room via `socket.to(roomName).emit("canvas:op", ...)`.
6. The operation is published to Redis pub/sub channel `realtime:ops` with an `instanceId` envelope for cross-instance fan-out; originating instances ignore their own messages.
7. Cross-instance received operations are applied in `trustVersion` mode: if the broadcast carries a higher version than the local document, the local version is advanced to the broadcast version so all instances converge to a single monotonic sequence without double-incrementing.
8. The operation is pushed to the Kafka buffer and flushed to `canvas.ops` (keyed by `canvasId`) every 50 ops or 50ms.

## Snapshot Strategy

- Snapshot = `Y.encodeStateAsUpdate(Y.Doc)` binary state.
- Stored in Redis hash `canvas:snapshot:{canvasId}` with `{ version, state }`.
- Triggered every `SNAPSHOT_OPS_THRESHOLD` accepted operations (default 50) or every `SNAPSHOT_INTERVAL_MS` (default 30000ms), whichever comes first.
- On room join, the latest snapshot is loaded and the Yjs document version is restored.
- MinIO snapshot persistence is **not** implemented in this stage. Downstream stages may add MinIO-backed snapshots and Kafka op replay.

## Presence and Awareness

- `presence:update` with `{ canvasId, cursor?, selection?, status?, state? }`.
- Throttled per user/canvas to `PRESENCE_THROTTLE_MS` (default 100ms).
- Stored in Redis key `canvas:awareness:{canvasId}:{subject}` with TTL `PRESENCE_TTL_SECONDS` (default 60).
- Broadcast to other room members via `presence:update`.
- Not persisted to Kafka.

## Backpressure

- Per-socket message count is tracked per 1-second window.
- Above `BACKPRESSURE_LIMIT` (default 100) messages/second:
  - emits `backpressure` event with `{ limit }`
  - drops non-critical presence/awareness updates with ack `{ ok: false, dropped: true, reason: "backpressure" }`
  - canvas operations continue to be processed

## Cross-Instance Broadcast

- Redis pub/sub channel `realtime:ops` carries `{ instanceId, op }` envelopes.
- An instance ignores messages it published itself (loopback suppression by `instanceId`).
- Received operations are applied to the local Yjs document (if loaded on that instance) and broadcast to the local Socket.IO room.
- The cross-instance handler passes `trustVersion: true` to `applyOperations`; local client handlers do not, so clients cannot force arbitrary version jumps.

## Performance Considerations

- In-memory `Y.Doc` per active canvas (not per connection), shared across room members on the same instance.
- Op batching: 50ms flush window or 50 ops batch, whichever first.
- Awareness updates: throttled to 100ms, not persisted to Kafka (ephemeral).
- Cross-instance traffic: Redis pub/sub (lower latency than Kafka).
- Membership check results cached in Redis to avoid repeated Core calls.