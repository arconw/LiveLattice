import { describe, expect, it, vi } from "vitest";
import { createCanvasRealtimeAdapter, resolveRealtimeUrl } from "../features/canvas/realtime";
import type { SocketLike } from "../features/canvas/realtime";

type Listener = (...args: unknown[]) => void;

class FakeSocket implements SocketLike {
  connected = false;
  readonly emits: Array<{ event: string; payload: unknown }> = [];
  private readonly listeners = new Map<string, Set<Listener>>();

  on(event: string, listener: Listener) {
    const listeners = this.listeners.get(event) ?? new Set<Listener>();
    listeners.add(listener);
    this.listeners.set(event, listeners);
    return this;
  }

  off(event: string, listener?: Listener) {
    if (!listener) {
      this.listeners.delete(event);
      return this;
    }

    this.listeners.get(event)?.delete(listener);
    return this;
  }

  emit(event: string, payload?: unknown, ack?: (response: unknown) => void) {
    this.emits.push({ event, payload });

    if (event === "join:room") {
      ack?.({ ok: true, canvasId: "canvas-1", roomName: "canvas:canvas-1", memberCount: 2, version: 7 });
    }

    if (event === "canvas:op") {
      ack?.({ ok: true, canvasId: "canvas-1", version: 8, seq: 2 });
    }

    if (event === "presence:update") {
      ack?.({ ok: true });
    }

    return this;
  }

  disconnect() {
    this.connected = false;
    return this;
  }

  trigger(event: string, payload?: unknown) {
    if (event === "connect") {
      this.connected = true;
    }

    this.listeners.get(event)?.forEach((listener) => listener(payload));
  }
}

describe("canvas realtime adapter", () => {
  it("requires an explicit realtime base URL", () => {
    expect(resolveRealtimeUrl("workspace 1", undefined)).toBeNull();
    expect(resolveRealtimeUrl("workspace 1", "http://localhost:3002/")).toBe("http://localhost:3002/ws/workspace%201");
  });

  it("joins a canvas room, sends ops, and maps remote presence", async () => {
    const socket = new FakeSocket();
    const statuses: string[] = [];
    const remoteOps: unknown[] = [];
    const presences: unknown[] = [];
    const adapter = createCanvasRealtimeAdapter({
      url: "http://realtime/ws/workspace-1",
      token: "fixture-token",
      canvasId: "canvas-1",
      socketFactory: vi.fn(() => socket)
    });

    adapter.onStatus((status) => statuses.push(status.label));
    adapter.onRemoteOperation((message) => remoteOps.push(message));
    adapter.onPresence((presence) => presences.push(presence));

    adapter.connect();
    socket.trigger("connect");
    await Promise.resolve();

    expect(statuses).toContain("Realtime connected");
    expect(socket.emits[0]).toMatchObject({ event: "join:room", payload: { canvasId: "canvas-1" } });

    const ack = await adapter.sendOperations([{ type: "update", id: "el-1", changes: { x: 12 } }], 7, 3, 2);
    expect(ack).toMatchObject({ ok: true, version: 8, seq: 2 });
    expect(socket.emits[1]).toMatchObject({ event: "canvas:op", payload: { canvasId: "canvas-1", version: 7, lockVersion: 3, seq: 2 } });

    socket.trigger("canvas:op", { canvasId: "canvas-1", ops: [{ type: "remove", id: "el-2" }], version: 9, lockVersion: 4, origin: "peer" });
    expect(remoteOps).toMatchObject([{ canvasId: "canvas-1", version: 9, lockVersion: 4, origin: "peer" }]);

    socket.trigger("presence:update", { subject: "peer-1", displayName: "Peer User", payload: { cursor: { x: 4, y: 9 }, selection: ["el-1"], status: "online" } });
    expect(presences).toEqual([{ id: "peer-1", name: "Peer User", cursor: { x: 4, y: 9 }, selection: ["el-1"], status: "online" }]);
  });
});
