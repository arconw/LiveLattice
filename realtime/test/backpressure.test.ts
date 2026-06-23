import assert from "node:assert/strict";
import { after, test } from "node:test";
import { BackpressureTracker } from "../src/backpressure";

test("backpressure emits exceeded flag above 100 messages per second", () => {
  const tracker = new BackpressureTracker({ messagesPerSecond: 100 });
  after(() => tracker.close());
  let exceededReported = false;
  for (let i = 0; i < 100; i++) {
    const r = tracker.check("sock-1");
    if (r.exceeded) {
      exceededReported = true;
    }
  }
  assert.equal(exceededReported, false);
  const r = tracker.check("sock-1");
  assert.equal(r.exceeded, true);
  assert.equal(r.limit, 100);
});

test("backpressure drops awareness for exceeded sockets", () => {
  const tracker = new BackpressureTracker({ messagesPerSecond: 5 });
  after(() => tracker.close());
  for (let i = 0; i < 6; i++) {
    tracker.check("sock-2");
  }
  assert.equal(tracker.isAwarenessDroppable("sock-2"), true);
  assert.equal(tracker.isAwarenessDroppable("sock-3"), false);
});

test("backpressure resets on socket id reset", () => {
  const tracker = new BackpressureTracker({ messagesPerSecond: 5 });
  after(() => tracker.close());
  for (let i = 0; i < 6; i++) {
    tracker.check("sock-4");
  }
  assert.equal(tracker.isAwarenessDroppable("sock-4"), true);
  tracker.reset("sock-4");
  assert.equal(tracker.isAwarenessDroppable("sock-4"), false);
});

test("backpressure window resets after one second", async () => {
  const tracker = new BackpressureTracker({ messagesPerSecond: 5 });
  after(() => tracker.close());
  for (let i = 0; i < 6; i++) {
    tracker.check("sock-5");
  }
  assert.equal(tracker.isAwarenessDroppable("sock-5"), true);
  await new Promise((resolve) => setTimeout(resolve, 1010));
  const r = tracker.check("sock-5");
  assert.equal(r.exceeded, false);
});