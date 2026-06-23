# Stage 7: Realtime Collaboration

## Objective

Implement the WebSocket-based realtime collaboration service with CRDT conflict resolution, room management, presence tracking, and broadcast.

## Requirements

1. Initialize NestJS project in `realtime/` with Socket.IO
2. Implement `SocketGateway`:
   - Namespace: `/ws/{workspace_id}`
   - Connection auth via JWT query parameter `?token=JWT`
   - Extract `user_id`, `workspace_id`, `role` from JWT
   - Reject connection if user not workspace member
3. Implement `RoomManager`:
   - Redis-backed room membership (`redis> SMEMBERS canvas:{canvas_id}`)
   - Cross-instance room events via Redis pub/sub
   - Room lifecycle: create on first join, destroy on last leave
4. Implement `CollaborationEngine`:
   - CRDT using Yjs
   - In-memory `Y.Doc` per active canvas (shared across room members on same instance)
   - Lazy-load document state from latest MinIO snapshot + replay ops from Kafka
   - Apply operations, increment version, broadcast to room
   - Auto-snapshot: every 50 operations or 30s
5. Implement `PresenceService`:
   - Track cursor position, selection, online status
   - Throttle awareness updates to 100ms
   - Ephemeral (not persisted to Kafka)
6. Implement `BroadcastService`:
   - Fan-out operations to all room members
   - Cross-instance fan-out via Redis pub/sub
   - Acknowledge operations with version + sequence number
7. Implement `OpPersistenceService`:
   - Buffer incoming operations (50 ops batch / 50ms window)
   - Publish batch to Kafka topic `canvas.ops` (partitioned by `canvas_id`)
8. Implement backpressure:
   - Track message rate per connection
   - Send `backpressure` signal when rate > 100 msg/s
   - Drop non-critical messages (awareness) under load
9. Write unit tests (Jest) and integration test with Socket.IO client + Testcontainers Redis

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- Room state must survive single instance restart (Redis + Kafka recovery)
- Awareness updates must not be persisted to Kafka

## Verification

```bash
docker compose config
docker compose build realtime

docker compose up -d redis kafka keycloak core realtime
docker compose ps realtime

curl -sf http://127.0.0.1:3002/health
curl -sf http://127.0.0.1:3002/ready

cd realtime && npm ci && npm test && npm run build
```

Socket.IO smoke test with `AUTH_DISABLED=true` (local test harness only; Docker Compose default requires auth):

```bash
docker compose run --rm -e AUTH_DISABLED=true -e KAFKA_ENABLED=false realtime &
sleep 3
node -e 'const {io}=require("socket.io-client"); const s=io("http://127.0.0.1:3002/ws/ws-1",{transports:["websocket"]}); s.on("connect",()=>{s.emit("join:room",{canvasId:"canvas-1"},(a)=>{console.log("join",a);s.emit("canvas:op",{canvasId:"canvas-1",ops:[{type:"add",id:"el-1"}],seq:1},(b)=>{console.log("ack",b);s.disconnect();process.exit(0)})})}); s.on("connect_error",e=>{console.error(e.message);process.exit(1)})'
```

Expected ack shape: `{ ok: true, canvasId: "canvas-1", version: 1, seq: 1 }`.
