# Realtime service

Realtime collaboration backend for LiveLattice.

## Runtime

- Node 24, TypeScript, Fastify, Socket.IO, Yjs, ioredis, kafkajs, jose.
- HTTP endpoints on the same Node HTTP server as Socket.IO:
  - `GET /health` -> `{ status, service, version }`
  - `GET /ready` -> `{ status, checks: { process, redis, kafka, authRequired } }`
- Socket.IO dynamic namespace: `/ws/{workspaceId}` (matched by `/^\/ws\/.+$/`).
- Local execution path: Docker Compose (`docker compose up -d realtime`).

## Authentication and authorization

- Socket.IO connections are authenticated in namespace middleware using a JWT.
- The JWT is read from `socket.handshake.auth.token` or `socket.handshake.query.token`.
- JWTs are verified with `jose` against the Keycloak JWKS endpoint using `AUTH_ISSUER`, `AUTH_JWKS_URI`, and optional `AUTH_AUDIENCE`.
- Extracted claims: `subject` (`sub`), `email`, `displayName` (`name`/`preferred_username`/`email`), realm roles (`realm_access.roles`).
- Workspace membership is verified by calling Core `GET /workspaces/{workspaceId}` with trusted internal identity headers:
  - `x-internal-auth-token`
  - `x-auth-subject`
  - `x-auth-email`
  - `x-auth-display-name`
- Membership results are cached in Redis (`realtime:membership:{workspaceId}:{subject}`) for `CORE_MEMBERSHIP_CACHE_TTL_SECONDS`.
- Missing token, invalid token, and non-member connections are rejected.
- `AUTH_DISABLED=true` bypasses JWT verification and Core membership checks. Intended only for tests and local test harnesses. Docker Compose defaults to `AUTH_DISABLED=false`.

## Room management

- `join:room` event with `{ canvasId }`:
  - joins Socket.IO room `canvas:{canvasId}`
  - adds the socket id to the Redis set `canvas:{canvasId}`
  - restores the Yjs document state from the latest Redis snapshot
  - ack returns `{ ok, canvasId, roomName, memberCount, version }`
- `leave:room` event with `{ canvasId }`:
  - leaves the Socket.IO room
  - removes the socket id from the Redis set
  - deletes the Redis room membership key when the last member leaves
  - removes the user's awareness entry
- On disconnect: all joined canvases are left, awareness is removed, and backpressure state is reset.

## Collaboration engine

- One in-memory `Y.Doc` per active canvas per service instance.
- `canvas:op` event with `{ canvasId, ops, version?, seq? }`:
  - requires the socket to have joined the canvas room first
  - applies each operation to the Yjs document (`add`, `update`, `remove`, `update-state`)
  - increments the server-side canvas version by 1 per accepted batch for normal clients
  - accepts an explicit `version` only in `trustVersion` mode, used for cross-instance fan-out so the local version jumps to the origin instance's version instead of double-incrementing
  - ack: `canvas:ack` payload `{ ok, canvasId, version, seq }`
  - broadcasts `canvas:op` to other members in the same Socket.IO room (excluding the sender)
  - publishes the accepted operation to Redis pub/sub channel `realtime:ops` for cross-instance fan-out
  - pushes the operation envelope to the Kafka buffer
- Cross-instance fan-out: Redis pub/sub messages carry an `instanceId`; an instance ignores messages it published itself. Received operations are applied to the local Yjs document (if loaded) and broadcast to the local Socket.IO room.
- Snapshots: Yjs state is encoded with `Y.encodeStateAsUpdate` and stored in Redis hash `canvas:snapshot:{canvasId}` every `SNAPSHOT_OPS_THRESHOLD` accepted operations (default 50) or every `SNAPSHOT_INTERVAL_MS` (default 30000ms), whichever comes first. On room join the latest snapshot is loaded and the Yjs document version is restored. MinIO snapshot persistence is not implemented in this stage.

## Presence and awareness

- `presence:update` event with `{ canvasId, cursor?, selection?, status?, state? }`:
  - requires the socket to have joined the canvas room first
  - throttled per user/canvas to `PRESENCE_THROTTLE_MS` (default 100ms); throttled updates are dropped with ack `{ ok: false, dropped: true, reason: "throttled" }`
  - stored ephemerally in Redis key `canvas:awareness:{canvasId}:{subject}` with TTL `PRESENCE_TTL_SECONDS` (default 60)
  - broadcast to other members in the same Socket.IO room via `presence:update`
  - not persisted to Kafka

## Operation persistence

- Kafka producer buffers accepted canvas operation batches.
- Topic: `canvas.ops` (`KAFKA_CANVAS_OPS_TOPIC`).
- Partition/key by `canvasId`.
- Flush by `KAFKA_FLUSH_BATCH_SIZE` (default 50) operations or `KAFKA_FLUSH_INTERVAL_MS` (default 50ms) window.
- `KAFKA_ENABLED=false` disables the producer; buffered count stays 0 and flush is a no-op. Used for unit/integration tests.

## Backpressure

- Per-socket message rate is tracked per 1-second window.
- When a socket exceeds `BACKPRESSURE_LIMIT` (default 100) messages per second:
  - emits `backpressure` event with `{ limit }`
  - drops non-critical presence/awareness updates with ack `{ ok: false, dropped: true, reason: "backpressure" }`
  - critical canvas operations continue to be processed

## Configuration

Environment variables (Docker Compose defaults in parentheses):

| Variable | Default | Description |
|---|---|---|
| `PORT` | 3002 | HTTP/Socket.IO listen port |
| `HOST` | 0.0.0.0 | Listen host |
| `AUTH_DISABLED` | false | Bypass JWT and membership checks (tests only) |
| `AUTH_ISSUER` | - | Keycloak issuer |
| `AUTH_JWKS_URI` | - | Keycloak JWKS URI |
| `AUTH_AUDIENCE` | - | Optional JWT audience |
| `JWKS_CACHE_TTL_SECONDS` | 3600 | JWKS cache TTL |
| `CORE_URL` | http://core:8080 | Core base URL for membership checks |
| `INTERNAL_AUTH_SECRET` | - | Trusted internal token sent to Core |
| `CORE_MEMBERSHIP_CACHE_TTL_SECONDS` | 60 | Membership cache TTL |
| `REDIS_HOST` | localhost | Redis host (`memory` selects in-memory stores for tests) |
| `REDIS_PORT` | 6379 | Redis port |
| `REDIS_PASSWORD` | - | Redis password |
| `KAFKA_ENABLED` | true | Enable Kafka producer |
| `KAFKA_BROKERS` | localhost:9092 | Comma-separated Kafka brokers |
| `KAFKA_CANVAS_OPS_TOPIC` | canvas.ops | Kafka topic for canvas operations |
| `KAFKA_FLUSH_BATCH_SIZE` | 50 | Operation buffer flush batch size |
| `KAFKA_FLUSH_INTERVAL_MS` | 50 | Operation buffer flush interval |
| `SNAPSHOT_OPS_THRESHOLD` | 50 | Operations between snapshots |
| `SNAPSHOT_INTERVAL_MS` | 30000 | Time between snapshots |
| `PRESENCE_THROTTLE_MS` | 100 | Presence throttle window |
| `PRESENCE_TTL_SECONDS` | 60 | Awareness TTL |
| `BACKPRESSURE_LIMIT` | 100 | Messages per second per socket |

## Develop

```bash
cd realtime
npm install
npm test
npm run build
```

## Docker Compose

```bash
docker compose config
docker compose build realtime
docker compose up -d redis kafka keycloak core realtime
curl -sf http://127.0.0.1:3002/health
curl -sf http://127.0.0.1:3002/ready
```

## Socket.IO smoke test

With `AUTH_DISABLED=true` (local test harness only):

```bash
node -e 'const {io}=require("socket.io-client"); const s=io("http://127.0.0.1:3002/ws/ws-1",{transports:["websocket"]}); s.on("connect",()=>{s.emit("join:room",{canvasId:"canvas-1"},(a)=>{console.log("join",a);s.emit("canvas:op",{canvasId:"canvas-1",ops:[{type:"add",id:"el-1"}],seq:1},(b)=>{console.log("ack",b);s.disconnect();process.exit(0)})})}); s.on("connect_error",e=>{console.error(e.message);process.exit(1)})'
```