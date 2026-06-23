import assert from "node:assert/strict";
import { after, test } from "node:test";
import { MemoryStores } from "../src/memory-stores";
import { PresenceService } from "../src/presence";
import type { AuthIdentity } from "../src/auth";

const identity: AuthIdentity = {
  subject: "user-1",
  email: "user-1@example.com",
  displayName: "User One",
  roles: []
};

function makePresence(stores: MemoryStores): PresenceService {
  return new PresenceService({ throttleMs: 100, ttlSeconds: 60 }, stores);
}

test("presence throttle drops updates within 100ms window", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const presence = makePresence(stores);
  after(() => presence.close());
  assert.equal(presence.shouldThrottle("c1", identity.subject), false);
  assert.equal(presence.shouldThrottle("c1", identity.subject), true);
  await new Promise((resolve) => setTimeout(resolve, 110));
  assert.equal(presence.shouldThrottle("c1", identity.subject), false);
});

test("presence apply stores awareness in store", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const presence = makePresence(stores);
  after(() => presence.close());
  await presence.apply({
    canvasId: "c2",
    subject: identity.subject,
    identity,
    payload: { canvasId: "c2", cursor: { x: 10, y: 20 } }
  });
  const raw = await stores.getAwareness("c2", identity.subject);
  assert.ok(raw);
  const parsed = JSON.parse(raw);
  assert.equal(parsed.payload.cursor.x, 10);
});

test("presence remove clears awareness", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const presence = makePresence(stores);
  after(() => presence.close());
  await presence.apply({
    canvasId: "c3",
    subject: identity.subject,
    identity,
    payload: { canvasId: "c3" }
  });
  await presence.remove("c3", identity.subject);
  const raw = await stores.getAwareness("c3", identity.subject);
  assert.equal(raw, undefined);
});