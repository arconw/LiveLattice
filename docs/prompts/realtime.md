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
# Start infra + core
docker compose up -d

# Start realtime
cd realtime && npm install && npm run start:dev

# Connect via WebSocket client (use wscat or similar)
wscat -c "ws://localhost:3002/ws/ws-123?token=<valid_jwt>"

# Join room
> {"event":"join:room","data":{"canvasId":"canvas-123"}}

# Send operation
> {"event":"canvas:op","data":{"ops":[{"type":"add","element":{"id":"el-1","type":"rectangle","x":10,"y":10}}],"version":1,"seq":1}}

# Expect ack
< {"event":"canvas:ack","data":{"version":2,"seq":1}}

# Run tests
cd realtime && npm test
```
