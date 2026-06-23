import "reflect-metadata";
import { randomUUID } from "node:crypto";
import { NestFactory } from "@nestjs/core";
import { FastifyAdapter, NestFastifyApplication } from "@nestjs/platform-fastify";
import type { FastifyReply, FastifyRequest } from "fastify";
import { AppModule } from "./app.module";
import { AuthService } from "./auth";
import { GatewayConfig, loadConfig } from "./config";
import { GatewayMetrics } from "./metrics";
import { ProxyService } from "./proxy";
import type { IdentityHeaders } from "./proxy";
import { FixedWindowRateLimiter } from "./rate-limit";

export interface CreateAppOptions {
  env?: NodeJS.ProcessEnv;
  config?: GatewayConfig;
}

const startedAtKey = Symbol("startedAt");
const identityKey = Symbol("identity");

export async function createApp(options: CreateAppOptions = {}): Promise<NestFastifyApplication> {
  const config = options.config ?? loadConfig(options.env);
  const metrics = new GatewayMetrics();
  const limiter = new FixedWindowRateLimiter(config.rateLimit);
  const auth = new AuthService(config.auth);
  const proxy = new ProxyService(config.routes);
  const adapter = new FastifyAdapter({ logger: false, bodyLimit: config.bodyLimitBytes });
  (adapter as FastifyAdapter & { useRawBody?: boolean }).useRawBody = true;
  const app = await NestFactory.create<NestFastifyApplication>(AppModule.register(config, metrics), adapter, { bufferLogs: true });
  const server = adapter.getInstance();

  server.addHook("onRequest", async (request, reply) => {
    metrics.start();
    Reflect.set(request, startedAtKey, process.hrtime.bigint());
    const requestId = headerValue(request.headers["x-request-id"]) ?? randomUUID();
    request.headers["x-request-id"] = requestId;
    reply.header("x-request-id", requestId);
    const rate = limiter.check(clientKey(request));
    reply.header("x-ratelimit-limit", rate.limit);
    reply.header("x-ratelimit-remaining", rate.remaining);
    reply.header("x-ratelimit-reset", Math.ceil(rate.resetAt / 1000));
    if (!rate.allowed) {
      reply.code(429).send({ error: "Rate limit exceeded" });
      return;
    }
    if (isProtectedRoute(request)) {
      if (isApiKeyRequest(request)) {
        return;
      }
      const decision = await auth.authorize(request.headers.authorization);
      if (!decision.allowed) {
        reply.code(decision.statusCode ?? 401).send({ error: decision.message ?? "Unauthorized" });
        return;
      }
      if (decision.subject) {
        Reflect.set(request, identityKey, {
          subject: decision.subject,
          email: decision.email ?? `${decision.subject}@livelattice.local`,
          displayName: decision.displayName ?? decision.email ?? decision.subject,
          roles: decision.roles ?? [],
          internalSecret: config.auth.internalSecret
        } satisfies IdentityHeaders);
      }
    }
  });

  server.addContentTypeParser("multipart/form-data", { parseAs: "buffer" }, (_request, body, done) => {
    done(null, body);
  });

  server.addHook("onResponse", async (request, reply) => {
    const startedAt = Reflect.get(request, startedAtKey) as bigint | undefined;
    const durationSeconds = startedAt ? Number(process.hrtime.bigint() - startedAt) / 1_000_000_000 : 0;
    metrics.finish(request.method, routeLabel(request), reply.statusCode, durationSeconds);
  });

  server.post("/auth/login", async (request, reply) => handleLogin(auth, request, reply));
  server.post("/auth/refresh", async (request, reply) => handleRefresh(auth, request, reply));
  server.post("/auth/logout", async (request, reply) => handleLogout(auth, request, reply));
  server.post("/auth/social", async (request, reply) => handleSocial(auth, request, reply));
  server.post("/auth/mfa/setup", async (_request, reply) => reply.send(auth.mfaSetup()));
  server.post("/auth/mfa/verify", async (_request, reply) => reply.send(auth.mfaVerify()));
  server.all("/auth/keys", async (request, reply) => proxy.forwardPrefix(request, reply, config.routes.core, "/auth/keys", identity(request)));
  server.all("/auth/keys/*", async (request, reply) => proxy.forwardPrefix(request, reply, config.routes.core, "/auth/keys", identity(request)));
  server.all("/api/:service", async (request, reply) => proxy.forward(request, reply, identity(request)));
  server.all("/api/:service/*", async (request, reply) => proxy.forward(request, reply, identity(request)));

  await app.init();
  const originalClose = app.close.bind(app);
  app.close = async () => {
    await auth.close();
    await originalClose();
  };
  return app;
}

export async function start(): Promise<void> {
  const config = loadConfig();
  const app = await createApp({ config });
  await app.listen(config.port, config.host);
}

function headerValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function clientKey(request: FastifyRequest): string {
  const forwarded = headerValue(request.headers["x-forwarded-for"]);
  return forwarded?.split(",")[0]?.trim() || request.ip || "unknown";
}

function isProtectedRoute(request: FastifyRequest): boolean {
  const url = request.raw.url ?? "";
  return matchesRoutePrefix(url, "/api/core")
    || matchesRoutePrefix(url, "/api/search")
    || matchesRoutePrefix(url, "/api/import-export")
    || matchesRoutePrefix(url, "/auth/keys");
}

function isApiKeyRequest(request: FastifyRequest): boolean {
  const current = headerValue(request.headers["x-api-key"]);
  return current !== undefined && current.length > 0 && matchesRoutePrefix(request.raw.url ?? "", "/api/core");
}

function matchesRoutePrefix(url: string, prefix: string): boolean {
  return url === prefix || url.startsWith(`${prefix}/`) || url.startsWith(`${prefix}?`);
}

function identity(request: FastifyRequest): IdentityHeaders | undefined {
  return Reflect.get(request, identityKey) as IdentityHeaders | undefined;
}

function routeLabel(request: FastifyRequest): string {
  const raw = request.raw.url ?? "/";
  if (raw.startsWith("/api/")) {
    const [, , service] = raw.split("/");
    return `/api/${service ?? "unknown"}`;
  }
  return raw.split("?")[0] || "/";
}

async function handleLogin(auth: AuthService, request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const body = request.body as { email?: string; password?: string } | undefined;
  if (!body?.email || !body.password) {
    reply.code(400).send({ error: "email and password are required" });
    return;
  }
  try {
    reply.send(await auth.login(body.email, body.password));
  } catch (error) {
    reply.code(401).send({ error: error instanceof Error ? error.message : "Login failed" });
  }
}

async function handleRefresh(auth: AuthService, request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const body = request.body as { refreshToken?: string } | undefined;
  if (!body?.refreshToken) {
    reply.code(400).send({ error: "refreshToken is required" });
    return;
  }
  try {
    reply.send(await auth.refresh(body.refreshToken));
  } catch (error) {
    reply.code(401).send({ error: error instanceof Error ? error.message : "Refresh failed" });
  }
}

async function handleLogout(auth: AuthService, request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const body = request.body as { refreshToken?: string } | undefined;
  if (!body?.refreshToken) {
    reply.code(400).send({ error: "refreshToken is required" });
    return;
  }
  try {
    await auth.logout(body.refreshToken);
    reply.code(204).send();
  } catch (error) {
    reply.code(502).send({ error: error instanceof Error ? error.message : "Logout failed" });
  }
}

async function handleSocial(auth: AuthService, request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const body = request.body as { provider?: string; redirectUri?: string } | undefined;
  if (!body?.provider || !body.redirectUri) {
    reply.code(400).send({ error: "provider and redirectUri are required" });
    return;
  }
  reply.send(auth.socialLogin(body.provider, body.redirectUri));
}
