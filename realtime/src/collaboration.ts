import * as Y from "yjs";
import type { CollaborationConfig, RedisConfig } from "./config";
import type { SnapshotStore } from "./redis-stores";

export interface CanvasOperation {
  type: string;
  id?: string;
  [key: string]: unknown;
}

export interface CanvasOpPayload {
  canvasId: string;
  ops: CanvasOperation[];
  version?: number;
  lockVersion?: number;
  seq?: number;
}

export interface CanvasAck {
  canvasId: string;
  version: number;
  lockVersion?: number;
  seq?: number;
}

export interface BroadcastOp {
  canvasId: string;
  ops: CanvasOperation[];
  version: number;
  lockVersion?: number;
  seq?: number;
  origin: string;
}

export interface SnapshotResult {
  canvasId: string;
  version: number;
  bytes: number;
}

interface CanvasDoc {
  doc: Y.Doc;
  version: number;
  opsSinceSnapshot: number;
  lastSnapshotAt: number;
  snapshotTimer: ReturnType<typeof setInterval> | undefined;
}

export class CollaborationEngine {
  private readonly docs = new Map<string, CanvasDoc>();
  private readonly instanceId: string;

  constructor(
    private readonly config: CollaborationConfig,
    private readonly snapshots: SnapshotStore,
    instanceId?: string
  ) {
    this.instanceId = instanceId ?? `rt:${Math.random().toString(36).slice(2, 10)}`;
  }

  hasDoc(canvasId: string): boolean {
    return this.docs.has(canvasId);
  }

  getVersion(canvasId: string): number {
    return this.docs.get(canvasId)?.version ?? 0;
  }

  getDoc(canvasId: string): Y.Doc | undefined {
    return this.docs.get(canvasId)?.doc;
  }

  async applyOperations(payload: CanvasOpPayload, origin: string, options?: { trustVersion?: boolean; forceVersion?: boolean }): Promise<{ ack: CanvasAck; broadcast: BroadcastOp }> {
    const entry = this.ensureDoc(payload.canvasId);
    for (const op of payload.ops) {
      this.applyOp(entry.doc, op);
    }
    const trustVersion = options?.trustVersion ?? false;
    const trustedVersion = payload.version;
    const version = trustVersion && typeof trustedVersion === "number" ? (options?.forceVersion ? trustedVersion : Math.max(entry.version, trustedVersion)) : entry.version + 1;
    entry.version = version;
    entry.opsSinceSnapshot += payload.ops.length;
    const ack: CanvasAck = {
      canvasId: payload.canvasId,
      version: entry.version,
      lockVersion: payload.lockVersion,
      seq: payload.seq
    };
    const broadcast: BroadcastOp = {
      canvasId: payload.canvasId,
      ops: payload.ops,
      version: entry.version,
      lockVersion: payload.lockVersion,
      seq: payload.seq,
      origin
    };
    await this.maybeSnapshot(entry, payload.canvasId);
    return { ack, broadcast };
  }

  async maybeSnapshot(entry?: CanvasDoc, canvasId?: string): Promise<SnapshotResult | undefined> {
    const target = entry ?? (canvasId ? this.docs.get(canvasId) : undefined);
    if (!target || !canvasId) {
      return undefined;
    }
    const now = Date.now();
    const byOps = target.opsSinceSnapshot >= this.config.snapshotOpsThreshold;
    const byTime = now - target.lastSnapshotAt >= this.config.snapshotIntervalMs;
    if (!byOps && !byTime) {
      return undefined;
    }
    return this.snapshot(target, canvasId);
  }

  async snapshot(target: CanvasDoc, canvasId: string): Promise<SnapshotResult> {
    const state = Y.encodeStateAsUpdate(target.doc);
    await this.snapshots.save(canvasId, target.version, state);
    target.opsSinceSnapshot = 0;
    target.lastSnapshotAt = Date.now();
    return { canvasId, version: target.version, bytes: state.byteLength };
  }

  forceSnapshotTimer(canvasId: string): void {
    const entry = this.docs.get(canvasId);
    if (entry) {
      entry.lastSnapshotAt = 0;
    }
  }

  async restore(canvasId: string): Promise<number> {
    const existing = this.docs.get(canvasId);
    if (existing) {
      return existing.version;
    }
    const snap = await this.snapshots.load(canvasId);
    if (snap) {
      const entry = this.ensureDoc(canvasId);
      Y.applyUpdate(entry.doc, snap.state);
      entry.version = snap.version;
      entry.lastSnapshotAt = Date.now();
      return entry.version;
    }
    return 0;
  }

  removeDoc(canvasId: string): void {
    const entry = this.docs.get(canvasId);
    if (entry) {
      if (entry.snapshotTimer) {
        clearInterval(entry.snapshotTimer);
      }
      entry.doc.destroy();
      this.docs.delete(canvasId);
    }
  }

  close(): void {
    for (const [, entry] of this.docs) {
      if (entry.snapshotTimer) {
        clearInterval(entry.snapshotTimer);
      }
      entry.doc.destroy();
    }
    this.docs.clear();
  }

  private ensureDoc(canvasId: string): CanvasDoc {
    let entry = this.docs.get(canvasId);
    if (!entry) {
      const doc = new Y.Doc();
      entry = {
        doc,
        version: 0,
        opsSinceSnapshot: 0,
        lastSnapshotAt: Date.now(),
        snapshotTimer: undefined
      };
      this.docs.set(canvasId, entry);
      this.startSnapshotTimer(canvasId, entry);
    }
    return entry;
  }

  private startSnapshotTimer(canvasId: string, entry: CanvasDoc): void {
    entry.snapshotTimer = setInterval(() => {
      void this.maybeSnapshot(entry, canvasId);
    }, this.config.snapshotIntervalMs);
    if (typeof entry.snapshotTimer?.unref === "function") {
      entry.snapshotTimer.unref();
    }
  }

  private applyOp(doc: Y.Doc, op: CanvasOperation): void {
    const elements = doc.getMap("elements");
    if (op.type === "add" && op.id && typeof op.id === "string") {
      const ymap = new Y.Map();
      for (const [key, value] of Object.entries(op)) {
        if (key !== "type" && key !== "id") {
          ymap.set(key, value as unknown);
        }
      }
      elements.set(op.id, ymap);
      return;
    }
    if (op.type === "update" && op.id && typeof op.id === "string") {
      const existing = elements.get(op.id);
      if (existing instanceof Y.Map) {
        for (const [key, value] of Object.entries(op)) {
          if (key !== "type" && key !== "id") {
            existing.set(key, value as unknown);
          }
        }
      }
      return;
    }
    if (op.type === "remove" && op.id && typeof op.id === "string") {
      elements.delete(op.id);
      return;
    }
    if (op.type === "update-state" && op.state && typeof op.state === "object") {
      const state = doc.getMap("state");
      for (const [key, value] of Object.entries(op.state as Record<string, unknown>)) {
        state.set(key, value);
      }
    }
  }
}
