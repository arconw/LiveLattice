import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import assert from "node:assert/strict";
import { generateKeyPair, exportJWK, SignJWT } from "jose";
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
  const config = loadConfig({ CORE_URL: `${"http"}://127.0.0.1:${port}`, RATE_LIMIT_MAX: "1000", AUTH_REQUIRED: "false" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "GET", url: "/api/core/projects?limit=1", headers: { "x-request-id": "test-request" } });
  assert.equal(response.statusCode, 200);
  assert.deepEqual(response.json(), { method: "GET", url: "/projects?limit=1", requestId: "test-request" });
});

test("protected core route rejects missing bearer token", async () => {
  const config = loadConfig({ AUTH_REQUIRED: "true", RATE_LIMIT_MAX: "1000" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "GET", url: "/api/core/workspaces" });
  assert.equal(response.statusCode, 401);
});

test("protected import-export route rejects missing bearer token", async () => {
  const config = loadConfig({ AUTH_REQUIRED: "true", RATE_LIMIT_MAX: "1000" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "GET", url: "/api/import-export/health" });
  assert.equal(response.statusCode, 401);
});

test("protected search route rejects missing bearer token", async () => {
  const config = loadConfig({ AUTH_REQUIRED: "true", RATE_LIMIT_MAX: "1000" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "GET", url: "/api/search/health" });
  assert.equal(response.statusCode, 401);
});

test("protected route matching does not overmatch sibling prefixes", async () => {
  const config = loadConfig({ AUTH_REQUIRED: "true", RATE_LIMIT_MAX: "1000" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const importExportResponse = await server.inject({ method: "GET", url: "/api/import-exporter/health" });
  const searchResponse = await server.inject({ method: "GET", url: "/api/searchable/health" });
  const auditLogResponse = await server.inject({ method: "GET", url: "/api/audit-logbook/health" });
  assert.equal(importExportResponse.statusCode, 404);
  assert.equal(searchResponse.statusCode, 404);
  assert.equal(auditLogResponse.statusCode, 404);
});

test("protected audit-log route rejects missing bearer token", async () => {
  const config = loadConfig({ AUTH_REQUIRED: "true", RATE_LIMIT_MAX: "1000" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "GET", url: "/api/audit-log/health" });
  assert.equal(response.statusCode, 401);
});

test("protected background-jobs route rejects missing bearer token", async () => {
  const config = loadConfig({ AUTH_REQUIRED: "true", RATE_LIMIT_MAX: "1000" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "GET", url: "/api/background-jobs/jobs" });
  assert.equal(response.statusCode, 401);
});

test("background-jobs route proxies valid bearer token with trusted internal headers", async () => {
  const issuer = "http://issuer.test/realms/livelattice";
  const { publicKey, privateKey } = await generateKeyPair("RS256");
  const jwk = await exportJWK(publicKey);
  jwk.kid = "test-key";
  const token = await new SignJWT({
    email: "owner@example.com",
    name: "LiveLattice Owner",
    realm_access: { roles: ["owner"] }
  })
    .setProtectedHeader({ alg: "RS256", kid: "test-key" })
    .setSubject("keycloak-subject")
    .setIssuer(issuer)
    .setIssuedAt()
    .setExpirationTime("15m")
    .sign(privateKey);
  const upstream = createServer((request, response) => {
    if (request.url === "/jwks") {
      response.setHeader("content-type", "application/json");
      response.end(JSON.stringify({ keys: [jwk] }));
      return;
    }
    response.setHeader("content-type", "application/json");
    response.end(JSON.stringify({
      method: request.method,
      url: request.url,
      internalToken: request.headers["x-internal-auth-token"],
      subject: request.headers["x-auth-subject"],
      email: request.headers["x-auth-email"],
      roles: request.headers["x-auth-roles"]
    }));
  });
  await new Promise<void>((resolve) => upstream.listen(0, resolve));
  after(async () => new Promise<void>((resolve) => upstream.close(() => resolve())));
  const port = (upstream.address() as AddressInfo).port;
  const config = loadConfig({
    AUTH_ISSUER: issuer,
    AUTH_JWKS_URI: `${"http"}://127.0.0.1:${port}/jwks`,
    BACKGROUND_JOBS_URL: `${"http"}://127.0.0.1:${port}`,
    RATE_LIMIT_MAX: "1000"
  });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({
    method: "GET",
    url: "/api/background-jobs/jobs",
    headers: { authorization: `Bearer ${token}`, "x-request-id": "bg-test" }
  });
  assert.equal(response.statusCode, 200);
  const json = response.json();
  assert.equal(json.method, "GET");
  assert.equal(json.url, "/jobs");
  assert.equal(json.internalToken, config.auth.internalSecret);
  assert.equal(json.subject, "keycloak-subject");
  assert.equal(json.email, "owner@example.com");
  assert.equal(json.roles, "owner");
});

test("api key requests pass through protected core route without bearer token", async () => {
  const upstream = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    response.end(JSON.stringify({ apiKey: request.headers["x-api-key"], userId: request.headers["x-user-id"] }));
  });
  await new Promise<void>((resolve) => upstream.listen(0, resolve));
  after(async () => new Promise<void>((resolve) => upstream.close(() => resolve())));
  const port = (upstream.address() as AddressInfo).port;
  const config = loadConfig({ CORE_URL: `${"http"}://127.0.0.1:${port}`, AUTH_REQUIRED: "true", RATE_LIMIT_MAX: "1000" });
  const app = await createApp({ config });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "GET", url: "/api/core/workspaces", headers: { "x-api-key": "secret", "x-user-id": "spoofed" } });
  assert.equal(response.statusCode, 200);
  assert.deepEqual(response.json(), { apiKey: "secret" });
});

test("login exchanges token, provisions user, and publishes auth audit event", async () => {
  const issuer = "http://issuer.test/realms/livelattice";
  const { publicKey, privateKey } = await generateKeyPair("RS256");
  const jwk = await exportJWK(publicKey);
  jwk.kid = "test-key";
  const token = await new SignJWT({
    email: "owner@example.com",
    name: "LiveLattice Owner",
    realm_access: { roles: ["owner"] }
  })
    .setProtectedHeader({ alg: "RS256", kid: "test-key" })
    .setSubject("keycloak-subject")
    .setIssuer(issuer)
    .setIssuedAt()
    .setExpirationTime("15m")
    .sign(privateKey);
  let provisionSubject = "";
  const sent: { topic: string; messages: { key: string; value: string }[] }[] = [];
  const upstream = createServer((request, response) => {
    if (request.url === "/token") {
      response.setHeader("content-type", "application/json");
      response.end(JSON.stringify({ access_token: token, refresh_token: "refresh", expires_in: 900, token_type: "Bearer" }));
      return;
    }
    if (request.url === "/jwks") {
      response.setHeader("content-type", "application/json");
      response.end(JSON.stringify({ keys: [jwk] }));
      return;
    }
    if (request.url === "/provision") {
      provisionSubject = String(request.headers["x-auth-subject"] ?? "");
      response.statusCode = 204;
      response.end();
      return;
    }
    response.statusCode = 404;
    response.end();
  });
  await new Promise<void>((resolve) => upstream.listen(0, resolve));
  after(async () => new Promise<void>((resolve) => upstream.close(() => resolve())));
  const port = (upstream.address() as AddressInfo).port;
  const base = `${"http"}://127.0.0.1:${port}`;
  const config = loadConfig({
    AUTH_ISSUER: issuer,
    AUTH_JWKS_URI: `${base}/jwks`,
    AUTH_TOKEN_ENDPOINT: `${base}/token`,
    CORE_PROVISION_URL: `${base}/provision`,
    KAFKA_ENABLED: "true",
    KAFKA_BROKERS: "localhost:9092",
    RATE_LIMIT_MAX: "1000"
  });
  const kafka = { send: async (batch: { topic: string; messages: { key: string; value: string }[] }) => sent.push(batch), close: async () => {} };
  const app = await createApp({ config, kafka });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "POST", url: "/auth/login", payload: { email: "owner@example.com", password: "owner123" } });
  assert.equal(response.statusCode, 200);
  assert.equal(response.json().user.subject, "keycloak-subject");
  assert.equal(provisionSubject, "keycloak-subject");
  assert.equal(sent.length, 1);
  assert.equal(sent[0].topic, "livelattice.audit.events");
  const auditEvent = JSON.parse(sent[0].messages[0].value);
  assert.equal(auditEvent.eventType, "auth.login");
  assert.equal(auditEvent.actorId, "keycloak-subject");
  assert.equal(auditEvent.targetId, "keycloak-subject");
  assert.notEqual(auditEvent.id, auditEvent.targetId);
});

test("logout and mfa auth audit events preserve authenticated target metadata", async () => {
  const issuer = "http://issuer.test/realms/livelattice";
  const { publicKey, privateKey } = await generateKeyPair("RS256");
  const jwk = await exportJWK(publicKey);
  jwk.kid = "test-key";
  const token = await new SignJWT({
    email: "owner@example.com",
    name: "LiveLattice Owner",
    realm_access: { roles: ["owner"] }
  })
    .setProtectedHeader({ alg: "RS256", kid: "test-key" })
    .setSubject("keycloak-subject")
    .setIssuer(issuer)
    .setIssuedAt()
    .setExpirationTime("15m")
    .sign(privateKey);
  const sent: { topic: string; messages: { key: string; value: string }[] }[] = [];
  const upstream = createServer((request, response) => {
    if (request.url === "/jwks") {
      response.setHeader("content-type", "application/json");
      response.end(JSON.stringify({ keys: [jwk] }));
      return;
    }
    if (request.url === "/logout") {
      response.statusCode = 204;
      response.end();
      return;
    }
    response.statusCode = 404;
    response.end();
  });
  await new Promise<void>((resolve) => upstream.listen(0, resolve));
  after(async () => new Promise<void>((resolve) => upstream.close(() => resolve())));
  const port = (upstream.address() as AddressInfo).port;
  const base = `${"http"}://127.0.0.1:${port}`;
  const config = loadConfig({
    AUTH_ISSUER: issuer,
    AUTH_JWKS_URI: `${base}/jwks`,
    AUTH_LOGOUT_ENDPOINT: `${base}/logout`,
    KAFKA_ENABLED: "true",
    KAFKA_BROKERS: "localhost:9092",
    RATE_LIMIT_MAX: "1000"
  });
  const kafka = { send: async (batch: { topic: string; messages: { key: string; value: string }[] }) => sent.push(batch), close: async () => {} };
  const app = await createApp({ config, kafka });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const headers = { authorization: `Bearer ${token}` };
  const logout = await server.inject({ method: "POST", url: "/auth/logout", headers, payload: { refreshToken: "refresh" } });
  const setup = await server.inject({ method: "POST", url: "/auth/mfa/setup", headers });
  const verify = await server.inject({ method: "POST", url: "/auth/mfa/verify", headers });
  assert.equal(logout.statusCode, 204);
  assert.equal(setup.statusCode, 200);
  assert.equal(verify.statusCode, 200);
  assert.equal(sent.length, 3);
  const events = sent.map((batch) => JSON.parse(batch.messages[0].value));
  assert.deepEqual(events.map((event) => event.eventType), ["auth.logout", "auth.mfa_enable", "auth.mfa_disable"]);
  for (const event of events) {
    assert.equal(event.actorId, "keycloak-subject");
    assert.equal(event.targetId, "keycloak-subject");
    assert.equal(event.workspaceId, "keycloak-subject");
    assert.notEqual(event.id, event.targetId);
    assert.equal(event.changes.email, "owner@example.com");
    assert.equal(event.metadata.source, "gateway");
  }
});

test("logout rejects missing bearer token before publishing audit event", async () => {
  const sent: { topic: string; messages: { key: string; value: string }[] }[] = [];
  const config = loadConfig({ AUTH_REQUIRED: "true", KAFKA_ENABLED: "true", RATE_LIMIT_MAX: "1000" });
  const kafka = { send: async (batch: { topic: string; messages: { key: string; value: string }[] }) => sent.push(batch), close: async () => {} };
  const app = await createApp({ config, kafka });
  after(async () => app.close());
  const server = app.getHttpAdapter().getInstance();
  const response = await server.inject({ method: "POST", url: "/auth/logout", payload: { refreshToken: "refresh" } });
  assert.equal(response.statusCode, 401);
  assert.equal(sent.length, 0);
});

test("fixed window rate limiter denies excess requests", () => {
  const limiter = new FixedWindowRateLimiter({ windowMs: 1000, max: 1 });
  assert.equal(limiter.check("client", 10).allowed, true);
  assert.equal(limiter.check("client", 20).allowed, false);
  assert.equal(limiter.check("client", 2000).allowed, true);
});
