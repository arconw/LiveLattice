export interface RateLimitOptions {
  windowMs: number;
  max: number;
}

export interface RateLimitDecision {
  allowed: boolean;
  limit: number;
  remaining: number;
  resetAt: number;
}

interface Bucket {
  startedAt: number;
  count: number;
}

export class FixedWindowRateLimiter {
  private readonly buckets = new Map<string, Bucket>();

  constructor(private readonly options: RateLimitOptions) {}

  check(key: string, now: number = Date.now()): RateLimitDecision {
    const existing = this.buckets.get(key);
    const bucket = existing && now - existing.startedAt < this.options.windowMs ? existing : { startedAt: now, count: 0 };
    bucket.count += 1;
    this.buckets.set(key, bucket);
    const resetAt = bucket.startedAt + this.options.windowMs;
    const remaining = Math.max(this.options.max - bucket.count, 0);
    return {
      allowed: bucket.count <= this.options.max,
      limit: this.options.max,
      remaining,
      resetAt
    };
  }

  size(): number {
    return this.buckets.size;
  }

  sweep(now: number = Date.now()): number {
    let removed = 0;
    for (const [key, bucket] of this.buckets.entries()) {
      if (now - bucket.startedAt >= this.options.windowMs) {
        this.buckets.delete(key);
        removed += 1;
      }
    }
    return removed;
  }
}
