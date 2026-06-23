import type { BackpressureConfig } from "./config";

export interface BackpressureState {
  count: number;
  windowStart: number;
  exceeded: boolean;
}

export class BackpressureTracker {
  private readonly states = new Map<string, BackpressureState>();

  constructor(private readonly config: BackpressureConfig) {}

  check(socketId: string): { exceeded: boolean; limit: number } {
    const now = Date.now();
    let state = this.states.get(socketId);
    if (!state || now - state.windowStart >= 1000) {
      state = { count: 0, windowStart: now, exceeded: false };
      this.states.set(socketId, state);
    }
    state.count += 1;
    const exceeded = state.count > this.config.messagesPerSecond;
    if (exceeded && !state.exceeded) {
      state.exceeded = true;
      return { exceeded: true, limit: this.config.messagesPerSecond };
    }
    return { exceeded, limit: this.config.messagesPerSecond };
  }

  isAwarenessDroppable(socketId: string): boolean {
    const state = this.states.get(socketId);
    return Boolean(state?.exceeded);
  }

  reset(socketId: string): void {
    this.states.delete(socketId);
  }

  close(): void {
    this.states.clear();
  }
}