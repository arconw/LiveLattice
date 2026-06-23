import Redis from "ioredis";
import type { RedisConfig } from "./config";

export interface RoomMembershipStore {
  join(canvasId: string, socketId: string): Promise<number>;
  leave(canvasId: string, socketId: string): Promise<number>;
  members(canvasId: string): Promise<string[]>;
}

export interface SnapshotStore {
  save(canvasId: string, version: number, state: Uint8Array): Promise<void>;
  load(canvasId: string): Promise<{ version: number; state: Uint8Array } | undefined>;
}

export interface AwarenessStore {
  setAwareness(canvasId: string, subject: string, value: string, ttlSeconds: number): Promise<void>;
  getAwareness(canvasId: string, subject: string): Promise<string | undefined>;
  removeAwareness(canvasId: string, subject: string): Promise<void>;
}

export interface PubSubStore {
  publish(channel: string, message: string): Promise<void>;
  subscribe(channel: string, handler: (message: string) => void): Promise<void>;
  unsubscribe(channel: string): Promise<void>;
  close(): Promise<void>;
}

export interface MembershipCacheStore {
  getMembership(key: string): Promise<boolean | undefined>;
  setMembership(key: string, value: boolean, ttlSeconds: number): Promise<void>;
  close(): Promise<void>;
}

export interface RealtimeStores extends RoomMembershipStore, SnapshotStore, AwarenessStore, PubSubStore, MembershipCacheStore {}

export class RedisStores implements RealtimeStores {
  private readonly client: Redis;
  private readonly subscriber: Redis;
  private readonly handlers = new Map<string, Set<(message: string) => void>>();

  constructor(config: RedisConfig) {
    const opts = {
      host: config.host,
      port: config.port,
      password: config.password,
      lazyConnect: true,
      maxRetriesPerRequest: 1
    };
    this.client = new Redis(opts);
    this.subscriber = new Redis(opts);
  }

  async connect(): Promise<void> {
    await Promise.all([this.client.connect(), this.subscriber.connect()]);
    this.subscriber.on("message", (channel, message) => {
      const set = this.handlers.get(channel);
      if (set) {
        for (const handler of set) {
          handler(message);
        }
      }
    });
  }

  async join(canvasId: string, socketId: string): Promise<number> {
    const key = `canvas:${canvasId}`;
    const added = await this.client.sadd(key, socketId);
    await this.client.expire(key, 86400);
    return added;
  }

  async leave(canvasId: string, socketId: string): Promise<number> {
    const key = `canvas:${canvasId}`;
    const removed = await this.client.srem(key, socketId);
    const remaining = await this.client.scard(key);
    if (remaining === 0) {
      await this.client.del(key);
    }
    return removed;
  }

  async members(canvasId: string): Promise<string[]> {
    return this.client.smembers(`canvas:${canvasId}`);
  }

  async save(canvasId: string, version: number, state: Uint8Array): Promise<void> {
    const key = `canvas:snapshot:${canvasId}`;
    await this.client.hset(key, {
      version: String(version),
      state: Buffer.from(state)
    });
  }

  async load(canvasId: string): Promise<{ version: number; state: Uint8Array } | undefined> {
    const key = `canvas:snapshot:${canvasId}`;
    const [versionRaw, stateRaw] = await this.client.hmgetBuffer(key, "version", "state");
    if (!versionRaw || !stateRaw) {
      return undefined;
    }
    return { version: Number(versionRaw.toString("utf8")), state: stateRaw };
  }

  async setAwareness(canvasId: string, subject: string, value: string, ttlSeconds: number): Promise<void> {
    const key = `canvas:awareness:${canvasId}:${subject}`;
    await this.client.set(key, value, "EX", ttlSeconds);
  }

  async getAwareness(canvasId: string, subject: string): Promise<string | undefined> {
    const raw = await this.client.get(`canvas:awareness:${canvasId}:${subject}`);
    return raw ?? undefined;
  }

  async removeAwareness(canvasId: string, subject: string): Promise<void> {
    await this.client.del(`canvas:awareness:${canvasId}:${subject}`);
  }

  async publish(channel: string, message: string): Promise<void> {
    await this.client.publish(channel, message);
  }

  async subscribe(channel: string, handler: (message: string) => void): Promise<void> {
    let set = this.handlers.get(channel);
    if (!set) {
      set = new Set();
      this.handlers.set(channel, set);
      await this.subscriber.subscribe(channel);
    }
    set.add(handler);
  }

  async unsubscribe(channel: string): Promise<void> {
    const set = this.handlers.get(channel);
    if (!set) {
      return;
    }
    this.handlers.delete(channel);
    await this.subscriber.unsubscribe(channel);
  }

  async getMembership(key: string): Promise<boolean | undefined> {
    const raw = await this.client.get(key);
    if (raw === null) {
      return undefined;
    }
    return raw === "1";
  }

  async setMembership(key: string, value: boolean, ttlSeconds: number): Promise<void> {
    await this.client.set(key, value ? "1" : "0", "EX", ttlSeconds);
  }

  async close(): Promise<void> {
    this.handlers.clear();
    this.client.disconnect();
    this.subscriber.disconnect();
  }
}
