import type { Server as HttpServer } from "node:http";
import { Server as SocketIOServer, type Namespace, type Socket } from "socket.io";
import type { RealtimeConfig } from "./config";
import { AuthService, type AuthIdentity } from "./auth";
import type { RoomMembershipStore, PubSubStore, SnapshotStore, AwarenessStore, MembershipCacheStore, RealtimeStores } from "./redis-stores";
import { RoomManager } from "./room-manager";
import { CollaborationEngine, type BroadcastOp, type CanvasOpPayload } from "./collaboration";
import { PresenceService, type PresencePayload } from "./presence";
import { BroadcastService } from "./broadcast";
import { OpPersistenceService } from "./op-persistence";
import { BackpressureTracker } from "./backpressure";
import type { KafkaProducerAdapter } from "./kafka-adapter";
import type { RealtimeMetrics } from "./metrics";

export interface RealtimeServerDeps {
  auth: AuthService;
  rooms: RoomManager;
  collaboration: CollaborationEngine;
  presence: PresenceService;
  broadcast: BroadcastService;
  ops: OpPersistenceService;
  backpressure: BackpressureTracker;
  membershipCache: MembershipCacheStore;
  pubsub: PubSubStore;
  snapshots: SnapshotStore;
  awareness: AwarenessStore;
  stores: RealtimeStores;
  producer?: KafkaProducerAdapter;
  metrics?: RealtimeMetrics;
}

export interface CreateServerOptions {
  config: RealtimeConfig;
  deps: RealtimeServerDeps;
  httpServer?: HttpServer;
}

export interface RealtimeServer {
  io: SocketIOServer;
  namespace: Namespace;
  close(): Promise<void>;
}

interface SocketState {
  socketId: string;
  workspaceId: string;
  identity: AuthIdentity;
  joinedCanvases: Set<string>;
}

const WORKSPACE_PATTERN = /^\/ws\/([^/?#]+)/;

function matchWorkspace(name: string): string | undefined {
  const match = WORKSPACE_PATTERN.exec(name);
  return match ? decodeURIComponent(match[1]) : undefined;
}

export function createRealtimeServer(options: CreateServerOptions): RealtimeServer {
  const { config, deps, httpServer } = options;
  const io = new SocketIOServer(httpServer ?? 3002, {
    cors: { origin: "*" },
    transports: ["websocket", "polling"]
  });

  const namespace = io.of(/^\/ws\/.+$/);

  namespace.use(async (socket, next) => {
    try {
      const workspaceId = matchWorkspace(socket.nsp.name);
      if (!workspaceId) {
        next(new Error("Invalid namespace"));
        return;
      }
      const token = (socket.handshake.auth?.token as string | undefined) ?? (socket.handshake.query?.token as string | undefined);
      const result = await deps.auth.verifyToken(token);
      if (!result.allowed || !result.identity) {
        next(new Error(result.message ?? "Unauthorized"));
        return;
      }
      const isMember = await deps.auth.verifyWorkspaceMembership(workspaceId, result.identity);
      if (!isMember) {
        next(new Error("Not a workspace member"));
        return;
      }
      next();
    } catch (error) {
      next(error instanceof Error ? error : new Error("Unauthorized"));
    }
  });

  namespace.on("connection", (socket: Socket) => {
    deps.metrics?.socketConnected();
    void handleConnection(socket, config, deps);
  });

  void deps.broadcast.subscribeOps((op) => {
    void handleCrossInstanceOp(op, namespace, deps);
  });

  deps.ops.start();

  return {
    io,
    namespace,
    async close() {
      await deps.ops.close();
      await deps.broadcast.close();
      deps.collaboration.close();
      deps.presence.close();
      deps.backpressure.close();
      await deps.auth.close();
      io.close();
    }
  };
}

async function handleConnection(socket: Socket, _config: RealtimeConfig, deps: RealtimeServerDeps): Promise<void> {
  const workspaceId = matchWorkspace(socket.nsp.name);
  if (!workspaceId) {
    socket.disconnect(true);
    return;
  }
  const token = (socket.handshake.auth?.token as string | undefined) ?? (socket.handshake.query?.token as string | undefined);
  const result = await deps.auth.verifyToken(token);
  if (!result.allowed || !result.identity) {
    socket.disconnect(true);
    return;
  }
  const identity = result.identity;
  const state: SocketState = {
    socketId: socket.id,
    workspaceId,
    identity,
    joinedCanvases: new Set()
  };
  socket.data.state = state;
  const origin = `socket:${socket.id}`;

  socket.on("join:room", (payload: { canvasId?: string } | undefined, ack?: (res: unknown) => void) => {
    deps.metrics?.websocketMessage();
    void handleJoinRoom(socket, state, deps, payload, ack);
  });

  socket.on("leave:room", (payload: { canvasId?: string } | undefined, ack?: (res: unknown) => void) => {
    deps.metrics?.websocketMessage();
    void handleLeaveRoom(socket, state, deps, payload, ack);
  });

  socket.on("canvas:op", (payload: CanvasOpPayload | undefined, ack?: (res: unknown) => void) => {
    deps.metrics?.websocketMessage();
    void handleCanvasOp(socket, state, deps, payload, origin, ack);
  });

  socket.on("presence:update", (payload: PresencePayload | undefined, ack?: (res: unknown) => void) => {
    deps.metrics?.websocketMessage();
    void handlePresenceUpdate(socket, state, deps, payload, ack);
  });

  socket.on("disconnect", () => {
    void handleDisconnect(state, deps);
  });
}

async function handleJoinRoom(
  socket: Socket,
  state: SocketState,
  deps: RealtimeServerDeps,
  payload: { canvasId?: string } | undefined,
  ack?: (res: unknown) => void
): Promise<void> {
  const canvasId = payload?.canvasId;
  if (!canvasId || canvasId.length === 0) {
    ack?.({ ok: false, error: "canvasId is required" });
    return;
  }
  const roomName = deps.rooms.roomName(canvasId);
  await socket.join(roomName);
  const result = await deps.rooms.join(canvasId, socket.id);
  state.joinedCanvases.add(canvasId);
  await deps.collaboration.restore(canvasId);
  ack?.({
    ok: true,
    canvasId,
    roomName,
    memberCount: result.memberCount,
    version: deps.collaboration.getVersion(canvasId)
  });
}

async function handleLeaveRoom(
  socket: Socket,
  state: SocketState,
  deps: RealtimeServerDeps,
  payload: { canvasId?: string } | undefined,
  ack?: (res: unknown) => void
): Promise<void> {
  const canvasId = payload?.canvasId;
  if (!canvasId || canvasId.length === 0) {
    ack?.({ ok: false, error: "canvasId is required" });
    return;
  }
  const roomName = deps.rooms.roomName(canvasId);
  await socket.leave(roomName);
  await deps.rooms.leave(canvasId, socket.id);
  state.joinedCanvases.delete(canvasId);
  await deps.presence.remove(canvasId, state.identity.subject);
  ack?.({ ok: true, canvasId, roomName });
}

async function handleCanvasOp(
  socket: Socket,
  state: SocketState,
  deps: RealtimeServerDeps,
  payload: CanvasOpPayload | undefined,
  origin: string,
  ack?: (res: unknown) => void
): Promise<void> {
  if (!payload || !payload.canvasId || !Array.isArray(payload.ops)) {
    ack?.({ ok: false, error: "canvasId and ops are required" });
    return;
  }
  const canvasId = payload.canvasId;
  if (!state.joinedCanvases.has(canvasId)) {
    ack?.({ ok: false, error: "Join the canvas room before sending operations" });
    return;
  }
  const backpressure = deps.backpressure.check(socket.id);
  if (backpressure.exceeded) {
    socket.emit("backpressure", { limit: backpressure.limit });
  }
  const hasCoreVersion = typeof payload.version === "number" && typeof payload.lockVersion === "number";
  const { ack: ackPayload, broadcast } = await deps.collaboration.applyOperations(payload, origin, { trustVersion: hasCoreVersion, forceVersion: hasCoreVersion });
  deps.metrics?.canvasOperation();
  ack?.({ ok: true, ...ackPayload });
  const roomName = deps.rooms.roomName(canvasId);
  socket.to(roomName).emit("canvas:op", {
    canvasId: broadcast.canvasId,
    ops: broadcast.ops,
    version: broadcast.version,
    lockVersion: broadcast.lockVersion,
    seq: broadcast.seq,
    origin: broadcast.origin
  });
  await deps.broadcast.publishOp(broadcast);
  await deps.ops.push(broadcast);
}

async function handlePresenceUpdate(
  socket: Socket,
  state: SocketState,
  deps: RealtimeServerDeps,
  payload: PresencePayload | undefined,
  ack?: (res: unknown) => void
): Promise<void> {
  if (!payload || !payload.canvasId) {
    ack?.({ ok: false, error: "canvasId is required" });
    return;
  }
  const canvasId = payload.canvasId;
  if (!state.joinedCanvases.has(canvasId)) {
    ack?.({ ok: false, error: "Join the canvas room before presence updates" });
    return;
  }
  if (deps.backpressure.isAwarenessDroppable(socket.id)) {
    const backpressure = deps.backpressure.check(socket.id);
    if (backpressure.exceeded) {
      socket.emit("backpressure", { limit: backpressure.limit });
    }
    ack?.({ ok: false, dropped: true, reason: "backpressure" });
    return;
  }
  if (deps.presence.shouldThrottle(canvasId, state.identity.subject)) {
    ack?.({ ok: false, dropped: true, reason: "throttled" });
    return;
  }
  await deps.presence.apply({
    canvasId,
    subject: state.identity.subject,
    identity: state.identity,
    payload
  });
  const roomName = deps.rooms.roomName(canvasId);
  socket.to(roomName).emit("presence:update", {
    canvasId,
    subject: state.identity.subject,
    displayName: state.identity.displayName,
    payload
  });
  ack?.({ ok: true });
}

async function handleDisconnect(state: SocketState, deps: RealtimeServerDeps): Promise<void> {
  deps.metrics?.socketDisconnected();
  for (const canvasId of state.joinedCanvases) {
    await deps.rooms.leave(canvasId, state.socketId);
    await deps.presence.remove(canvasId, state.identity.subject);
  }
  deps.backpressure.reset(state.socketId);
}

async function handleCrossInstanceOp(op: BroadcastOp, namespace: Namespace, deps: RealtimeServerDeps): Promise<void> {
  if (!deps.collaboration.hasDoc(op.canvasId)) {
    return;
  }
  await deps.collaboration.applyOperations(
    { canvasId: op.canvasId, ops: op.ops, seq: op.seq, version: op.version, lockVersion: op.lockVersion },
    op.origin,
    { trustVersion: true }
  );
  const roomName = deps.rooms.roomName(op.canvasId);
  namespace.to(roomName).emit("canvas:op", {
    canvasId: op.canvasId,
    ops: op.ops,
    version: op.version,
    lockVersion: op.lockVersion,
    seq: op.seq,
    origin: op.origin
  });
}
