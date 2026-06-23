import type { PresenceConfig } from "./config";
import type { AwarenessStore } from "./redis-stores";
import type { AuthIdentity } from "./auth";

export interface PresencePayload {
  canvasId: string;
  cursor?: { x: number; y: number };
  selection?: unknown;
  status?: string;
  state?: unknown;
}

export interface PresenceUpdate {
  canvasId: string;
  subject: string;
  identity: AuthIdentity;
  payload: PresencePayload;
}

export class PresenceService {
  private readonly lastSentAt = new Map<string, number>();
  private readonly pending = new Map<string, PresenceUpdate>();

  constructor(
    private readonly config: PresenceConfig,
    private readonly awareness: AwarenessStore
  ) {}

  key(canvasId: string, subject: string): string {
    return `${canvasId}:${subject}`;
  }

  shouldThrottle(canvasId: string, subject: string): boolean {
    const key = this.key(canvasId, subject);
    const last = this.lastSentAt.get(key);
    const now = Date.now();
    if (last && now - last < this.config.throttleMs) {
      this.pending.set(key, {
        canvasId,
        subject,
        identity: { subject, email: "", displayName: "", roles: [] },
        payload: { canvasId }
      });
      return true;
    }
    this.lastSentAt.set(key, now);
    return false;
  }

  async apply(update: PresenceUpdate): Promise<void> {
    const key = this.key(update.canvasId, update.subject);
    this.lastSentAt.set(key, Date.now());
    await this.awareness.setAwareness(update.canvasId, update.subject, JSON.stringify({
      subject: update.subject,
      displayName: update.identity.displayName,
      email: update.identity.email,
      payload: update.payload
    }), this.config.ttlSeconds);
  }

  async remove(canvasId: string, subject: string): Promise<void> {
    const key = this.key(canvasId, subject);
    this.lastSentAt.delete(key);
    this.pending.delete(key);
    await this.awareness.removeAwareness(canvasId, subject);
  }

  close(): void {
    this.lastSentAt.clear();
    this.pending.clear();
  }
}