import { describe, expect, it, vi } from "vitest";
import errorFixtures from "./fixtures/gateway-errors.json";
import { TRUSTED_INTERNAL_HEADER_BLOCKLIST, createGatewayClient, createWorkspaceCacheKey } from "./api-client";

describe("Gateway API client", () => {
  it("strips trusted internal headers and keeps browser-safe headers", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({ ok: true }), { headers: { "content-type": "application/json" } }));
    const client = createGatewayClient({
      fetchImpl: fetchMock as typeof fetch,
      getAccessToken: () => "access-token",
      requestIdFactory: () => "request-1"
    });

    await client.post(
      "/api/core/workspaces",
      { name: "Factory floor" },
      {
        headers: {
          "x-user-id": "malicious-user",
          "x-auth-email": "malicious@example.test",
          "x-custom-client": "kept"
        }
      }
    );

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Headers;

    TRUSTED_INTERNAL_HEADER_BLOCKLIST.forEach((header) => {
      expect(headers.get(header)).toBeNull();
    });

    expect(headers.get("authorization")).toBe("Bearer access-token");
    expect(headers.get("x-request-id")).toBe("request-1");
    expect(headers.get("x-custom-client")).toBe("kept");
    expect(headers.get("content-type")).toBe("application/json");
  });

  it("refuses absolute internal service URLs", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({ ok: true })));
    const client = createGatewayClient({ fetchImpl: fetchMock as typeof fetch });

    await expect(client.get("http://core:8080/api/core/workspaces")).rejects.toMatchObject({
      status: 0,
      code: "GATEWAY_PATH_REQUIRED",
      retryable: false
    });

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("passes abort signals through to fetch", async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({ ok: true }), { headers: { "content-type": "application/json" } }));
    const client = createGatewayClient({ fetchImpl: fetchMock as typeof fetch });

    await client.get("/api/core/workspaces", { signal: controller.signal });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.signal).toBe(controller.signal);
  });

  it("refreshes once after a 401 and retries the protected request", async () => {
    let currentToken = "expired-token";
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ error: "unauthorized", message: "Expired" }), { status: 401, headers: { "content-type": "application/json" } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { headers: { "content-type": "application/json" } }));
    const refreshMock = vi.fn(async () => {
      currentToken = "fresh-token";
      return currentToken;
    });
    const client = createGatewayClient({
      fetchImpl: fetchMock as typeof fetch,
      getAccessToken: () => currentToken,
      refreshOnUnauthorized: refreshMock
    });

    await expect(client.get("/api/core/workspaces")).resolves.toEqual({ ok: true });

    expect(refreshMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((fetchMock.mock.calls[0][1] as RequestInit).headers).toBeInstanceOf(Headers);
    expect(((fetchMock.mock.calls[0][1] as RequestInit).headers as Headers).get("authorization")).toBe("Bearer expired-token");
    expect(((fetchMock.mock.calls[1][1] as RequestInit).headers as Headers).get("authorization")).toBe("Bearer fresh-token");
  });

  it("does not loop refresh attempts when the retried request is still unauthorized", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ error: "unauthorized", message: "Expired" }), { status: 401, headers: { "content-type": "application/json" } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ error: "unauthorized", message: "Still expired" }), { status: 401, headers: { "content-type": "application/json" } }));
    const refreshMock = vi.fn(async () => "fresh-token");
    const client = createGatewayClient({
      fetchImpl: fetchMock as typeof fetch,
      getAccessToken: () => "expired-token",
      refreshOnUnauthorized: refreshMock
    });

    await expect(client.get("/api/core/workspaces")).rejects.toMatchObject({
      status: 401,
      code: "unauthorized",
      message: "Still expired"
    });

    expect(refreshMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it.each([400, 401, 403, 404, 409, 429, 500])("normalizes %i Gateway errors", async (status) => {
    const payload = errorFixtures[String(status) as keyof typeof errorFixtures];
    const fetchMock = vi.fn(
      async () =>
        new Response(JSON.stringify(payload), {
          status,
          headers: {
            "content-type": "application/json",
            "x-request-id": `req-${status}`
          }
        })
    );
    const client = createGatewayClient({ fetchImpl: fetchMock as typeof fetch });

    await expect(client.get("/api/core/canvases")).rejects.toMatchObject({
      status,
      code: payload.code,
      message: payload.message,
      requestId: `req-${status}`
    });
  });

  it("keeps workspace context in cache keys", () => {
    expect(createWorkspaceCacheKey("factory-floor", "canvas", "incident-map")).toEqual(["workspace", "factory-floor", "canvas", "incident-map"]);
    expect(() => createWorkspaceCacheKey("")).toThrow("Workspace context is required");
  });
});
