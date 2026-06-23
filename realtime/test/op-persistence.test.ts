import assert from "node:assert/strict";
import { after, test } from "node:test";
import { OpPersistenceService } from "../src/op-persistence";
import { NoopKafkaProducer } from "../src/kafka-adapter";
import type { BroadcastOp } from "../src/collaboration";

function op(canvasId: string, version: number): BroadcastOp {
  return {
    canvasId,
    ops: [{ type: "add", id: `el-${version}` }],
    version,
    origin: "origin-1"
  };
}

test("Kafka disabled mode buffers nothing and flush is no-op", async () => {
  const ops = new OpPersistenceService(
    { enabled: false, brokers: [], canvasOpsTopic: "canvas.ops", flushBatchSize: 50, flushIntervalMs: 50 },
    undefined
  );
  after(async () => ops.close());
  await ops.push(op("c1", 1));
  assert.equal(ops.bufferedCount(), 0);
  const flushed = await ops.flush();
  assert.equal(flushed, 0);
});

test("Kafka enabled flushes batch to producer partitioned by canvasId", async () => {
  const producer = new NoopKafkaProducer();
  const ops = new OpPersistenceService(
    { enabled: true, brokers: ["localhost:9092"], canvasOpsTopic: "canvas.ops", flushBatchSize: 50, flushIntervalMs: 1000 },
    producer
  );
  after(async () => ops.close());
  await ops.push(op("c1", 1));
  await ops.push(op("c2", 1));
  assert.equal(ops.bufferedCount(), 2);
  const flushed = await ops.flush();
  assert.equal(flushed, 2);
  assert.equal(producer.sent.length, 2);
  const keys = producer.sent.map((b) => b.messages[0].key).sort();
  assert.deepEqual(keys, ["c1", "c2"]);
  for (const batch of producer.sent) {
    assert.equal(batch.topic, "canvas.ops");
  }
});

test("Kafka enabled auto-flushes when batch size reached", async () => {
  const producer = new NoopKafkaProducer();
  const ops = new OpPersistenceService(
    { enabled: true, brokers: ["localhost:9092"], canvasOpsTopic: "canvas.ops", flushBatchSize: 2, flushIntervalMs: 1000 },
    producer
  );
  after(async () => ops.close());
  await ops.push(op("c1", 1));
  assert.equal(producer.sent.length, 0);
  await ops.push(op("c1", 2));
  assert.equal(producer.sent.length, 1);
  assert.equal(ops.bufferedCount(), 0);
});