import type { KafkaConfig } from "./config";
import type { BroadcastOp } from "./collaboration";
import type { KafkaProducerAdapter } from "./kafka-adapter";

export interface OpEnvelope {
  canvasId: string;
  ops: BroadcastOp[];
}

export class OpPersistenceService {
  private buffer: BroadcastOp[] = [];
  private flushTimer: ReturnType<typeof setInterval> | undefined;
  private readonly producer?: KafkaProducerAdapter;

  constructor(
    private readonly config: KafkaConfig,
    producer?: KafkaProducerAdapter
  ) {
    this.producer = producer;
  }

  start(onFlush?: (count: number) => void): void {
    if (!this.config.enabled || !this.producer) {
      return;
    }
    this.flushTimer = setInterval(() => {
      void this.flush(onFlush);
    }, this.config.flushIntervalMs);
    if (typeof this.flushTimer?.unref === "function") {
      this.flushTimer.unref();
    }
  }

  async push(op: BroadcastOp): Promise<void> {
    if (!this.config.enabled) {
      return;
    }
    this.buffer.push(op);
    if (this.buffer.length >= this.config.flushBatchSize) {
      await this.flush();
    }
  }

  async flush(onFlush?: (count: number) => void): Promise<number> {
    if (!this.config.enabled || !this.producer || this.buffer.length === 0) {
      const count = this.buffer.length;
      this.buffer = [];
      return count;
    }
    const batch = this.buffer;
    this.buffer = [];
    const byCanvas = new Map<string, BroadcastOp[]>();
    for (const op of batch) {
      const list = byCanvas.get(op.canvasId);
      if (list) {
        list.push(op);
      } else {
        byCanvas.set(op.canvasId, [op]);
      }
    }
    for (const [canvasId, ops] of byCanvas) {
      await this.producer.send({
        topic: this.config.canvasOpsTopic,
        messages: [
          {
            key: canvasId,
            value: JSON.stringify({ canvasId, ops })
          }
        ]
      });
    }
    if (onFlush) {
      onFlush(batch.length);
    }
    return batch.length;
  }

  async close(): Promise<void> {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
      this.flushTimer = undefined;
    }
    await this.flush();
    if (this.producer) {
      await this.producer.close();
    }
  }

  bufferedCount(): number {
    return this.buffer.length;
  }
}