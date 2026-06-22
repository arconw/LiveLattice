import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import assert from "node:assert/strict";
import { after, test } from "node:test";
import { createApp } from "../src/main";
import { loadConfig } from "../src/config";
import { FixedWindowRateLimiter } from "../src/rate-limit";

test("health and readiness endpoints", async () => {
  const app = await createApp({ env: { PORT: "3000", RATE_LIMIT_MAX: "1000" } });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const health = await server.inject({ method: "GET", url: "/health" });
  assert.equal(health.statusCode, 200);
  assert.equal(health.json().status, "UP");
  const ready = await server.inject({ method: "GET", url: "/ready" });
  assert.equal(ready.statusCode, 200);
  assert.ok(ready.json().checks.routes.includes("core"));
  const metrics = await server.inject({ method: "GET", url: "/metrics" });
  assert.equal(metrics.statusCode, 200);
  assert.match(metrics.body, /livelattice_gateway_http_requests_active/);
});

test("gateway proxies known service routes", async () => {
  const upstream = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    response.end(JSON.stringify({ method: request.method, url: request.url, requestId: request.headers["x-request-id"] }));
  });
  await new Promise<void>((resolve) => upstream.listen(0, resolve));
  after(async () => new Promise<void>((resolve) => upstream.close(() => resolve())));
  const port = (upstream.address() as AddressInfo).port;
  const config = loadConfig({ CORE_URL: `${"http"}://127.0.0.1:${port}`, RATE_LIMIT_MAX: "1000" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "GET", url: "/api/core/projects?limit=1", headers: { "x-request-id": "test-request" } });
  assert.equal(response.statusCode, 200);
  assert.deepEqual(response.json(), { method: "GET", url: "/projects?limit=1", requestId: "test-request" });
});

test("fixed window rate limiter denies excess requests", () => {
  const limiter = new FixedWindowRateLimiter({ windowMs: 1000, max: 1 });
  assert.equal(limiter.check("client", 10).allowed, true);
  assert.equal(limiter.check("client", 20).allowed, false);
  assert.equal(limiter.check("client", 2000).allowed, true);
});
