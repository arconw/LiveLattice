import type { RealtimeStores } from "./redis-stores";

export class MemoryStores implements RealtimeStores {
  private readonly rooms = new Map<string, Set<string>>();
  private readonly snapshots = new Map<string, { version: number; state: Uint8Array }>();
  private readonly awareness = new Map<string, string>();
  private readonly subscriptions = new Map<string, Set<(message: string) => void>>();
  private readonly membership = new Map<string, { value: boolean; expiresAt: number }>();

  async join(canvasId: string, socketId: string): Promise<number> {
    let set = this.rooms.get(canvasId);
    if (!set) {
      set = new Set();
      this.rooms.set(canvasId, set);
    }
    const before = set.size;
    set.add(socketId);
    return set.size - before;
  }

  async leave(canvasId: string, socketId: string): Promise<number> {
    const set = this.rooms.get(canvasId);
    if (!set) {
      return 0;
    }
    const before = set.size;
    set.delete(socketId);
    if (set.size === 0) {
      this.rooms.delete(canvasId);
    }
    return before - set.size;
  }

  async members(canvasId: string): Promise<string[]> {
    const set = this.rooms.get(canvasId);
    return set ? Array.from(set) : [];
  }

  async save(canvasId: string, version: number, state: Uint8Array): Promise<void> {
    this.snapshots.set(canvasId, { version, state: new Uint8Array(state) });
  }

  async load(canvasId: string): Promise<{ version: number; state: Uint8Array } | undefined> {
    const snap = this.snapshots.get(canvasId);
    return snap ? { version: snap.version, state: new Uint8Array(snap.state) } : undefined;
  }

  async setAwareness(canvasId: string, subject: string, value: string, _ttlSeconds: number): Promise<void> {
    this.awareness.set(`${canvasId}:${subject}`, value);
  }

  async getAwareness(canvasId: string, subject: string): Promise<string | undefined> {
    return this.awareness.get(`${canvasId}:${subject}`);
  }

  async removeAwareness(canvasId: string, subject: string): Promise<void> {
    this.awareness.delete(`${canvasId}:${subject}`);
  }

  async publish(channel: string, message: string): Promise<void> {
    const set = this.subscriptions.get(channel);
    if (set) {
      for (const handler of set) {
        handler(message);
      }
    }
  }

  async subscribe(channel: string, handler: (message: string) => void): Promise<void> {
    let set = this.subscriptions.get(channel);
    if (!set) {
      set = new Set();
      this.subscriptions.set(channel, set);
    }
    set.add(handler);
  }

  async unsubscribe(channel: string): Promise<void> {
    this.subscriptions.delete(channel);
  }

  async getMembership(key: string): Promise<boolean | undefined> {
    const entry = this.membership.get(key);
    if (!entry || entry.expiresAt <= Date.now()) {
      this.membership.delete(key);
      return undefined;
    }
    return entry.value;
  }

  async setMembership(key: string, value: boolean, ttlSeconds: number): Promise<void> {
    this.membership.set(key, { value, expiresAt: Date.now() + ttlSeconds * 1000 });
  }

  async close(): Promise<void> {
    this.rooms.clear();
    this.snapshots.clear();
    this.awareness.clear();
    this.subscriptions.clear();
    this.membership.clear();
  }
}