import assert from "node:assert/strict";
import { after, test } from "node:test";
import { MemoryStores } from "../src/memory-stores";
import { CollaborationEngine } from "../src/collaboration";
import { RoomManager } from "../src/room-manager";
import { SnapshotStore } from "../src/redis-stores";

function makeEngine(stores: SnapshotStore): CollaborationEngine {
  return new CollaborationEngine(
    { snapshotOpsThreshold: 50, snapshotIntervalMs: 30000 },
    stores,
    "rt:test"
  );
}

test("collaboration version increments per accepted operation batch", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const engine = makeEngine(stores);
  after(() => engine.close());
  const r1 = await engine.applyOperations(
    { canvasId: "c1", ops: [{ type: "add", id: "el-1", element: { kind: "rect" } }] },
    "origin-1"
  );
  assert.equal(r1.ack.version, 1);
  assert.equal(r1.broadcast.origin, "origin-1");
  const r2 = await engine.applyOperations(
    { canvasId: "c1", ops: [{ type: "add", id: "el-2" }, { type: "add", id: "el-3" }], seq: 7 },
    "origin-2"
  );
  assert.equal(r2.ack.version, 2);
  assert.equal(r2.ack.seq, 7);
  assert.equal(engine.getVersion("c1"), 2);
});

test("collaboration can force Core versions and keep trusted replay monotonic", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const engine = makeEngine(stores);
  after(() => engine.close());

  const coreAccepted = await engine.applyOperations(
    { canvasId: "c-core", ops: [{ type: "add", id: "el-core" }], version: 129, lockVersion: 5, seq: 1 },
    "origin-core",
    { trustVersion: true, forceVersion: true }
  );
  assert.equal(coreAccepted.ack.version, 129);
  assert.equal(coreAccepted.ack.lockVersion, 5);
  assert.equal(coreAccepted.broadcast.version, 129);
  assert.equal(coreAccepted.broadcast.lockVersion, 5);

  const replayedOlder = await engine.applyOperations(
    { canvasId: "c-core", ops: [{ type: "add", id: "el-older" }], version: 128 },
    "origin-replay",
    { trustVersion: true }
  );
  assert.equal(replayedOlder.ack.version, 129);
  assert.equal(engine.getVersion("c-core"), 129);
});

test("snapshot triggers after ops threshold", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const engine = new CollaborationEngine(
    { snapshotOpsThreshold: 3, snapshotIntervalMs: 60000 },
    stores,
    "rt:test"
  );
  after(() => engine.close());
  for (let i = 0; i < 3; i++) {
    await engine.applyOperations(
      { canvasId: "c2", ops: [{ type: "add", id: `el-${i}` }] },
      "origin"
    );
  }
  const snap = await stores.load("c2");
  assert.ok(snap, "snapshot should be persisted");
  assert.equal(snap.version, 3);
  assert.ok(snap.state.byteLength > 0);
});

test("snapshot triggers by time interval", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const engine = new CollaborationEngine(
    { snapshotOpsThreshold: 100, snapshotIntervalMs: 10 },
    stores,
    "rt:test"
  );
  after(() => engine.close());
  await engine.applyOperations(
    { canvasId: "c3", ops: [{ type: "add", id: "el-1" }] },
    "origin"
  );
  engine.forceSnapshotTimer("c3");
  await new Promise((resolve) => setTimeout(resolve, 60));
  const snap = await stores.load("c3");
  assert.ok(snap, "snapshot should be persisted by interval");
  assert.equal(snap.version, 1);
});

test("restore reuses in-memory doc and loads snapshot version", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const engine = makeEngine(stores);
  after(() => engine.close());
  await engine.applyOperations(
    { canvasId: "c4", ops: [{ type: "add", id: "el-1" }] },
    "origin"
  );
  await engine.snapshot(engine["docs"].get("c4")!, "c4");
  engine.removeDoc("c4");
  const restored = await engine.restore("c4");
  assert.equal(restored, 1);
});

test("room join/leave tracks membership and clears last member", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const rooms = new RoomManager(stores);
  await rooms.join("c5", "sock-1");
  await rooms.join("c5", "sock-2");
  let members = await rooms.members("c5");
  assert.equal(members.length, 2);
  await rooms.leave("c5", "sock-1");
  members = await rooms.members("c5");
  assert.equal(members.length, 1);
  await rooms.leave("c5", "sock-2");
  members = await rooms.members("c5");
  assert.equal(members.length, 0);
});
