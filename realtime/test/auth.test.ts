import assert from "node:assert/strict";
import { after, test } from "node:test";
import { generateKeyPair, exportJWK, SignJWT } from "jose";
import { AuthService } from "../src/auth";
import { MemoryStores } from "../src/memory-stores";

test("auth disabled returns anonymous identity", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const auth = new AuthService(
    { disabled: true, jwksTtlSeconds: 60 },
    { url: "http://core:8080", internalSecret: "secret", membershipCacheTtlSeconds: 60 },
    stores
  );
  after(async () => auth.close());
  const result = await auth.verifyToken(undefined);
  assert.equal(result.allowed, true);
  assert.equal(result.identity?.subject, "anon-subject");
});

test("auth missing token is rejected when not disabled", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const auth = new AuthService(
    { disabled: false, issuer: "iss", jwksUri: "http://example/jwks", jwksTtlSeconds: 60 },
    { url: "http://core:8080", internalSecret: "secret", membershipCacheTtlSeconds: 60 },
    stores
  );
  after(async () => auth.close());
  const result = await auth.verifyToken(undefined);
  assert.equal(result.allowed, false);
  assert.equal(result.statusCode, 401);
});

test("auth verifies valid Keycloak JWT and extracts subject, email, displayName, roles", async () => {
  const issuer = "http://issuer.test/realms/livelattice";
  const { publicKey, privateKey } = await generateKeyPair("RS256");
  const jwk = await exportJWK(publicKey);
  jwk.kid = "test-key";
  const token = await new SignJWT({
    email: "owner@example.com",
    name: "LiveLattice Owner",
    preferred_username: "owner",
    realm_access: { roles: ["owner", "user"] }
  })
    .setProtectedHeader({ alg: "RS256", kid: "test-key" })
    .setSubject("keycloak-subject")
    .setIssuer(issuer)
    .setIssuedAt()
    .setExpirationTime("15m")
    .sign(privateKey);

  let jwksRequests = 0;
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: URL | RequestInfo, init?: RequestInit) => {
    const url = typeof input === "string" ? input : (input as URL).toString();
    if (url.endsWith("/jwks")) {
      jwksRequests += 1;
      return new Response(JSON.stringify({ keys: [jwk] }), { status: 200, headers: { "content-type": "application/json" } });
    }
    return new Response("", { status: 404 });
  }) as typeof globalThis.fetch;
  after(() => {
    globalThis.fetch = originalFetch;
  });

  const stores = new MemoryStores();
  after(async () => stores.close());
  const auth = new AuthService(
    { disabled: false, issuer, jwksUri: "http://example/jwks", jwksTtlSeconds: 60 },
    { url: "http://core:8080", internalSecret: "secret", membershipCacheTtlSeconds: 60 },
    stores
  );
  after(async () => auth.close());
  const result = await auth.verifyToken(token);
  assert.equal(result.allowed, true);
  assert.equal(result.identity?.subject, "keycloak-subject");
  assert.equal(result.identity?.email, "owner@example.com");
  assert.equal(result.identity?.displayName, "LiveLattice Owner");
  assert.deepEqual(result.identity?.roles, ["owner", "user"]);
});

test("auth rejects invalid token", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const auth = new AuthService(
    { disabled: false, issuer: "iss", jwksUri: "http://example/jwks", jwksTtlSeconds: 60 },
    { url: "http://core:8080", internalSecret: "secret", membershipCacheTtlSeconds: 60 },
    stores
  );
  after(async () => auth.close());
  const result = await auth.verifyToken("not-a-jwt");
  assert.equal(result.allowed, false);
  assert.equal(result.statusCode, 401);
});

test("auth workspace membership caches and calls Core with trusted headers", async () => {
  const stores = new MemoryStores();
  after(async () => stores.close());
  const auth = new AuthService(
    { disabled: false, jwksTtlSeconds: 60 },
    { url: "http://core:8080", internalSecret: "internal-secret", membershipCacheTtlSeconds: 60 },
    stores
  );
  after(async () => auth.close());
  let lastHeaders: Record<string, string> = {};
  let coreCalls = 0;
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: URL | RequestInfo, init?: RequestInit) => {
    const url = typeof input === "string" ? input : (input as URL).toString();
    if (url.includes("/workspaces/ws-1")) {
      coreCalls += 1;
      lastHeaders = (init?.headers as Record<string, string>) ?? {};
      return new Response(JSON.stringify({ id: "ws-1" }), { status: 200 });
    }
    return new Response("", { status: 404 });
  }) as typeof globalThis.fetch;
  after(() => {
    globalThis.fetch = originalFetch;
  });

  const identity = { subject: "sub-1", email: "sub@example.com", displayName: "Sub", roles: [] };
  const r1 = await auth.verifyWorkspaceMembership("ws-1", identity);
  assert.equal(r1, true);
  assert.equal(coreCalls, 1);
  assert.equal(lastHeaders["x-internal-auth-token"], "internal-secret");
  assert.equal(lastHeaders["x-auth-subject"], "sub-1");
  assert.equal(lastHeaders["x-auth-email"], "sub@example.com");
  assert.equal(lastHeaders["x-auth-display-name"], "Sub");
  const r2 = await auth.verifyWorkspaceMembership("ws-1", identity);
  assert.equal(r2, true);
  assert.equal(coreCalls, 1, "second call should hit cache");
});