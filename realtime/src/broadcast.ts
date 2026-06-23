import type { PubSubStore } from "./redis-stores";
import type { BroadcastOp } from "./collaboration";

export interface BroadcastTarget {
  roomName: string;
  socketId: string;
  emit: (event: string, payload: unknown) => void;
}

export interface BroadcastChannels {
  ops: string;
}

export class BroadcastService {
  private readonly instanceId: string;
  private readonly channels: BroadcastChannels;

  constructor(
    private readonly pubsub: PubSubStore,
    instanceId: string
  ) {
    this.instanceId = instanceId;
    this.channels = { ops: "realtime:ops" };
  }

  opsChannel(): string {
    return this.channels.ops;
  }

  async publishOp(op: BroadcastOp): Promise<void> {
    const envelope = {
      instanceId: this.instanceId,
      op
    };
    await this.pubsub.publish(this.channels.ops, JSON.stringify(envelope));
  }

  async subscribeOps(handler: (op: BroadcastOp, originInstanceId: string) => void): Promise<void> {
    await this.pubsub.subscribe(this.channels.ops, (raw) => {
      try {
        const envelope = JSON.parse(raw) as { instanceId: string; op: BroadcastOp };
        if (envelope.instanceId === this.instanceId) {
          return;
        }
        handler(envelope.op, envelope.instanceId);
      } catch {}
    });
  }

  fanOutLocal(targets: BroadcastTarget[], op: BroadcastOp, originSocketId: string): number {
    let sent = 0;
    for (const target of targets) {
      if (target.socketId === originSocketId) {
        continue;
      }
      if (target.roomName !== `canvas:${op.canvasId}`) {
        continue;
      }
      target.emit("canvas:op", {
        canvasId: op.canvasId,
        ops: op.ops,
        version: op.version,
        seq: op.seq,
        origin: op.origin
      });
      sent += 1;
    }
    return sent;
  }

  async close(): Promise<void> {
    await this.pubsub.unsubscribe(this.channels.ops);
  }
}