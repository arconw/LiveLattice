import { io } from "socket.io-client";
import type { CanvasPoint } from "../../contracts/canvas";
import type { CanvasOperation } from "./canvasModel";

export type RealtimeConnectionState = "idle" | "connecting" | "connected" | "reconnecting" | "offline" | "failed";

export type RealtimeStatus = {
  state: RealtimeConnectionState;
  label: string;
  version: number | null;
  memberCount: number | null;
  detail?: string;
};

export type RemotePresence = {
  id: string;
  name: string;
  cursor: CanvasPoint | null;
  selection: string[];
  status: "online" | "away";
};

export type RemoteOperationMessage = {
  canvasId: string;
  ops: CanvasOperation[];
  version: number;
  lockVersion?: number;
  seq?: number;
  origin?: string;
};

export type CanvasAck = {
  ok: boolean;
  canvasId?: string;
  version?: number;
  lockVersion?: number;
  seq?: number;
  error?: string;
};

export type PresenceAck = {
  ok: boolean;
  dropped?: boolean;
  reason?: string;
  error?: string;
};

type SocketListener = (...args: unknown[]) => void;

export type SocketLike = {
  connected: boolean;
  on: (event: string, listener: SocketListener) => SocketLike;
  off: (event: string, listener?: SocketListener) => SocketLike;
  emit: (event: string, payload?: unknown, ack?: (response: unknown) => void) => SocketLike;
  disconnect: () => SocketLike;
};

export type CanvasRealtimeAdapter = {
  connect: () => void;
  disconnect: () => void;
  sendOperations: (ops: CanvasOperation[], version: number, lockVersion: number, seq: number) => Promise<CanvasAck>;
  sendPresence: (payload: { cursor?: CanvasPoint; selection?: string[]; status?: "online" | "away" }) => Promise<PresenceAck>;
  onStatus: (listener: (status: RealtimeStatus) => void) => () => void;
  onRemoteOperation: (listener: (message: RemoteOperationMessage) => void) => () => void;
  onPresence: (listener: (presence: RemotePresence) => void) => () => void;
};

export type CanvasRealtimeAdapterOptions = {
  url: string;
  token: string | null;
  canvasId: string;
  socketFactory?: (url: string, options: { auth: { token?: string }; transports: string[]; reconnection: boolean }) => SocketLike;
};

export function resolveRealtimeUrl(workspaceId: string, configuredBase = import.meta.env.VITE_REALTIME_URL as string | undefined) {
  const base = configuredBase?.trim();
  if (!base) {
    return null;
  }

  return `${base.replace(/\/$/, "")}/ws/${encodeURIComponent(workspaceId)}`;
}

export function createCanvasRealtimeAdapter(options: CanvasRealtimeAdapterOptions): CanvasRealtimeAdapter {
  let socket: SocketLike | null = null;
  let status: RealtimeStatus = { state: "idle", label: "Realtime idle", version: null, memberCount: null };
  const statusListeners = new Set<(status: RealtimeStatus) => void>();
  const operationListeners = new Set<(message: RemoteOperationMessage) => void>();
  const presenceListeners = new Set<(presence: RemotePresence) => void>();

  function setStatus(next: RealtimeStatus) {
    status = next;
    statusListeners.forEach((listener) => listener(status));
  }

  function connect() {
    if (socket?.connected || status.state === "connecting") {
      return;
    }

    setStatus({ state: "connecting", label: "Realtime connecting", version: null, memberCount: null });
    const createSocket = options.socketFactory ?? ((url, socketOptions) => io(url, socketOptions) as unknown as SocketLike);
    socket = createSocket(options.url, {
      auth: options.token ? { token: options.token } : {},
      transports: ["websocket"],
      reconnection: true
    });
    socket.on("connect", handleConnect);
    socket.on("disconnect", handleDisconnect);
    socket.on("connect_error", handleConnectError);
    socket.on("reconnect_attempt", handleReconnectAttempt);
    socket.on("canvas:op", handleRemoteOperation);
    socket.on("presence:update", handlePresenceUpdate);
    socket.on("backpressure", handleBackpressure);
  }

  function disconnect() {
    if (!socket) {
      setStatus({ state: "idle", label: "Realtime idle", version: status.version, memberCount: status.memberCount });
      return;
    }

    socket.off("connect", handleConnect);
    socket.off("disconnect", handleDisconnect);
    socket.off("connect_error", handleConnectError);
    socket.off("reconnect_attempt", handleReconnectAttempt);
    socket.off("canvas:op", handleRemoteOperation);
    socket.off("presence:update", handlePresenceUpdate);
    socket.off("backpressure", handleBackpressure);
    socket.disconnect();
    socket = null;
    setStatus({ state: "offline", label: "Realtime offline", version: status.version, memberCount: status.memberCount });
  }

  async function sendOperations(ops: CanvasOperation[], version: number, lockVersion: number, seq: number) {
    return emitWithAck<CanvasAck>("canvas:op", { canvasId: options.canvasId, ops, version, lockVersion, seq });
  }

  async function sendPresence(payload: { cursor?: CanvasPoint; selection?: string[]; status?: "online" | "away" }) {
    return emitWithAck<PresenceAck>("presence:update", { canvasId: options.canvasId, ...payload });
  }

  async function handleConnect() {
    try {
      const ack = await emitWithAck<{ ok: boolean; version?: number; memberCount?: number; error?: string }>("join:room", { canvasId: options.canvasId });
      if (ack.ok) {
        setStatus({ state: "connected", label: "Realtime connected", version: ack.version ?? 0, memberCount: ack.memberCount ?? null });
        return;
      }

      setStatus({ state: "failed", label: "Realtime join failed", version: null, memberCount: null, detail: ack.error });
    } catch (error) {
      setStatus({ state: "failed", label: "Realtime join failed", version: null, memberCount: null, detail: error instanceof Error ? error.message : "Join failed" });
    }
  }

  function handleDisconnect() {
    setStatus({ state: "offline", label: "Realtime disconnected", version: status.version, memberCount: status.memberCount });
  }

  function handleConnectError(error: unknown) {
    setStatus({ state: "failed", label: "Realtime unavailable", version: status.version, memberCount: status.memberCount, detail: error instanceof Error ? error.message : "Connection failed" });
  }

  function handleReconnectAttempt() {
    setStatus({ state: "reconnecting", label: "Realtime reconnecting", version: status.version, memberCount: status.memberCount });
  }

  function handleRemoteOperation(payload: unknown) {
    const message = parseRemoteOperation(payload);
    if (!message || message.canvasId !== options.canvasId) {
      return;
    }

    setStatus({ state: status.state === "idle" ? "connected" : status.state, label: status.label, version: message.version, memberCount: status.memberCount });
    operationListeners.forEach((listener) => listener(message));
  }

  function handlePresenceUpdate(payload: unknown) {
    const presence = parsePresence(payload);
    if (!presence) {
      return;
    }

    presenceListeners.forEach((listener) => listener(presence));
  }

  function handleBackpressure(payload: unknown) {
    const limit = isRecord(payload) && typeof payload.limit === "number" ? payload.limit : null;
    setStatus({ state: "connected", label: "Realtime backpressure", version: status.version, memberCount: status.memberCount, detail: limit ? `Limit ${limit} messages per second` : "Awareness throttled" });
  }

  function emitWithAck<T>(event: string, payload: unknown): Promise<T> {
    if (!socket) {
      return Promise.resolve({ ok: false, error: "Realtime socket is not connected" } as T);
    }

    return new Promise((resolve) => {
      const timeout = window.setTimeout(() => {
        resolve({ ok: false, error: "Realtime acknowledgement timed out" } as T);
      }, 5000);
      socket?.emit(event, payload, (response: unknown) => {
        window.clearTimeout(timeout);
        resolve(response as T);
      });
    });
  }

  return {
    connect,
    disconnect,
    sendOperations,
    sendPresence,
    onStatus(listener) {
      statusListeners.add(listener);
      listener(status);
      return () => statusListeners.delete(listener);
    },
    onRemoteOperation(listener) {
      operationListeners.add(listener);
      return () => operationListeners.delete(listener);
    },
    onPresence(listener) {
      presenceListeners.add(listener);
      return () => presenceListeners.delete(listener);
    }
  };
}

function parseRemoteOperation(payload: unknown): RemoteOperationMessage | null {
  if (!isRecord(payload) || typeof payload.canvasId !== "string" || !Array.isArray(payload.ops) || typeof payload.version !== "number") {
    return null;
  }

  return {
    canvasId: payload.canvasId,
    ops: payload.ops.filter(isCanvasOperation),
    version: payload.version,
    lockVersion: typeof payload.lockVersion === "number" ? payload.lockVersion : undefined,
    seq: typeof payload.seq === "number" ? payload.seq : undefined,
    origin: typeof payload.origin === "string" ? payload.origin : undefined
  };
}

function parsePresence(payload: unknown): RemotePresence | null {
  if (!isRecord(payload) || typeof payload.subject !== "string") {
    return null;
  }

  const presencePayload = isRecord(payload.payload) ? payload.payload : {};
  const cursor = pointFromUnknown(presencePayload.cursor);
  const selection = Array.isArray(presencePayload.selection) ? presencePayload.selection.filter((item): item is string => typeof item === "string") : [];
  const status = presencePayload.status === "away" ? "away" : "online";

  return {
    id: payload.subject,
    name: typeof payload.displayName === "string" && payload.displayName.length > 0 ? payload.displayName : payload.subject,
    cursor,
    selection,
    status
  };
}

function isCanvasOperation(value: unknown): value is CanvasOperation {
  if (!isRecord(value) || typeof value.type !== "string") {
    return false;
  }

  return value.type === "add" || value.type === "update" || value.type === "remove" || value.type === "update-state";
}

function pointFromUnknown(value: unknown): CanvasPoint | null {
  if (!isRecord(value) || typeof value.x !== "number" || typeof value.y !== "number") {
    return null;
  }

  return { x: value.x, y: value.y };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
