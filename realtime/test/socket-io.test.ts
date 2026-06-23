import assert from "node:assert/strict";
import { after, test } from "node:test";
import { createTestRuntime, connect, type TestRuntime } from "./helpers";
import type { ClientSocket } from "socket.io-client";

test("Socket.IO integration: join, canvas:op ack with version, and broadcast to peer", async () => {
  const runtime = await createTestRuntime();
  after(async () => runtime.close());
  const ws = "ws-1";
  const canvasId = "canvas-1";

  const clientA = connect(runtime, ws);
  const clientB = connect(runtime, ws);
  after(async () => {
    clientA.disconnect();
    clientB.disconnect();
  });
  await Promise.all([waitForConnect(clientA), waitForConnect(clientB)]);

  const joinAckA = await emitWithAck(clientA, "join:room", { canvasId });
  assert.equal(joinAckA.ok, true);
  assert.equal(joinAckA.canvasId, canvasId);
  assert.equal(joinAckA.roomName, "canvas:canvas-1");

  const joinAckB = await emitWithAck(clientB, "join:room", { canvasId });
  assert.equal(joinAckB.ok, true);
  assert.equal(joinAckB.memberCount, 2);

  const receivedOp = new Promise<unknown>((resolve) => {
    clientB.once("canvas:op", (payload: unknown) => resolve(payload));
  });

  const opAck = await emitWithAck(clientA, "canvas:op", {
    canvasId,
    ops: [{ type: "add", id: "el-1", element: { kind: "rect" } }],
    version: 1,
    seq: 1
  });
  assert.equal(opAck.ok, true);
  assert.equal(opAck.canvasId, canvasId);
  assert.equal(opAck.version, 1);
  assert.equal(opAck.seq, 1);

  const broadcast = (await receivedOp) as { canvasId: string; ops: unknown[]; version: number; seq: number };
  assert.equal(broadcast.canvasId, canvasId);
  assert.equal(broadcast.version, 1);
  assert.equal(broadcast.seq, 1);

  const opAck2 = await emitWithAck(clientA, "canvas:op", {
    canvasId,
    ops: [{ type: "add", id: "el-2" }],
    seq: 2
  });
  assert.equal(opAck2.version, 2);

  assert.equal(runtime.collaboration.getVersion(canvasId), 2);
});

test("Socket.IO integration: leave:room removes membership", async () => {
  const runtime = await createTestRuntime();
  after(async () => runtime.close());
  const client = connect(runtime, "ws-2");
  after(() => client.disconnect());
  await waitForConnect(client);
  const joinAck = await emitWithAck(client, "join:room", { canvasId: "c-leave" });
  assert.equal(joinAck.ok, true);
  const members = await runtime.stores.members("c-leave");
  assert.equal(members.length, 1);
  const leaveAck = await emitWithAck(client, "leave:room", { canvasId: "c-leave" });
  assert.equal(leaveAck.ok, true);
  const membersAfter = await runtime.stores.members("c-leave");
  assert.equal(membersAfter.length, 0);
});

test("Socket.IO integration: canvas:op rejected before join:room", async () => {
  const runtime = await createTestRuntime();
  after(async () => runtime.close());
  const client = connect(runtime, "ws-3");
  after(() => client.disconnect());
  await waitForConnect(client);
  const ack = await emitWithAck(client, "canvas:op", {
    canvasId: "c-rejected",
    ops: [{ type: "add", id: "el-1" }]
  });
  assert.equal(ack.ok, false);
  assert.ok(ack.error);
});

test("Socket.IO integration: presence:update broadcasts to peers", async () => {
  const runtime = await createTestRuntime();
  after(async () => runtime.close());
  const clientA = connect(runtime, "ws-4");
  const clientB = connect(runtime, "ws-4");
  after(() => {
    clientA.disconnect();
    clientB.disconnect();
  });
  await Promise.all([waitForConnect(clientA), waitForConnect(clientB)]);
  await emitWithAck(clientA, "join:room", { canvasId: "c-pres" });
  await emitWithAck(clientB, "join:room", { canvasId: "c-pres" });

  const received = new Promise<unknown>((resolve) => {
    clientB.once("presence:update", (payload: unknown) => resolve(payload));
  });
  const ack = await emitWithAck(clientA, "presence:update", {
    canvasId: "c-pres",
    cursor: { x: 5, y: 7 }
  });
  assert.equal(ack.ok, true);
  const presence = (await received) as { subject: string; payload: { cursor: { x: number; y: number } } };
  assert.equal(presence.payload.cursor.x, 5);
  assert.equal(presence.payload.cursor.y, 7);
});

test("Socket.IO integration: /health and /ready endpoints", async () => {
  const runtime = await createTestRuntime();
  after(async () => runtime.close());
  const health = await fetch(`${runtime.baseUrl}/health`);
  assert.equal(health.status, 200);
  const healthBody = await health.json();
  assert.equal(healthBody.status, "UP");

  const ready = await fetch(`${runtime.baseUrl}/ready`);
  assert.equal(ready.status, 200);
  const readyBody = await ready.json();
  assert.equal(readyBody.status, "UP");
  assert.equal(readyBody.checks.authRequired, false);
});

test("Socket.IO integration: disconnect cleans up room membership", async () => {
  const runtime = await createTestRuntime();
  after(async () => runtime.close());
  const client = connect(runtime, "ws-5");
  await waitForConnect(client);
  await emitWithAck(client, "join:room", { canvasId: "c-disconnect" });
  let members = await runtime.stores.members("c-disconnect");
  assert.equal(members.length, 1);
  client.disconnect();
  await new Promise((resolve) => setTimeout(resolve, 100));
  members = await runtime.stores.members("c-disconnect");
  assert.equal(members.length, 0);
});

function waitForConnect(socket: ClientSocket): Promise<void> {
  return new Promise((resolve, reject) => {
    socket.once("connect", resolve);
    socket.once("connect_error", reject);
  });
}

function emitWithAck(socket: ClientSocket, event: string, payload: unknown): Promise<any> {
  return new Promise((resolve, reject) => {
    socket.timeout(2000).emit(event, payload, (err: Error | undefined, response: unknown) => {
      if (err) {
        reject(err);
      } else {
        resolve(response);
      }
    });
  });
}