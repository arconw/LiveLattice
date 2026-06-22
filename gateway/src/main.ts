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
import { FixedWindowRateLimiter } from "./rate-limit";

export interface CreateAppOptions {
  env?: NodeJS.ProcessEnv;
  config?: GatewayConfig;
}

const startedAtKey = Symbol("startedAt");

export async function createApp(options: CreateAppOptions = {}): Promise<NestFastifyApplication> {
  const config = options.config ?? loadConfig(options.env);
  const metrics = new GatewayMetrics();
  const limiter = new FixedWindowRateLimiter(config.rateLimit);
  const auth = new AuthService(config.auth);
  const proxy = new ProxyService(config.routes);
  const adapter = new FastifyAdapter({ logger: false, bodyLimit: config.bodyLimitBytes });
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
    if (isGatewayRoute(request)) {
      const decision = await auth.authorize(request.headers.authorization);
      if (!decision.allowed) {
        reply.code(decision.statusCode ?? 401).send({ error: decision.message ?? "Unauthorized" });
      }
    }
  });

  server.addHook("onResponse", async (request, reply) => {
    const startedAt = Reflect.get(request, startedAtKey) as bigint | undefined;
    const durationSeconds = startedAt ? Number(process.hrtime.bigint() - startedAt) / 1_000_000_000 : 0;
    metrics.finish(request.method, routeLabel(request), reply.statusCode, durationSeconds);
  });

  server.all("/api/:service", async (request, reply) => proxy.forward(request, reply));
  server.all("/api/:service/*", async (request, reply) => proxy.forward(request, reply));

  await app.init();
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

function isGatewayRoute(request: FastifyRequest): boolean {
  return (request.raw.url ?? "").startsWith("/api/");
}

function routeLabel(request: FastifyRequest): string {
  const raw = request.raw.url ?? "/";
  if (raw.startsWith("/api/")) {
    const [, , service] = raw.split("/");
    return `/api/${service ?? "unknown"}`;
  }
  return raw.split("?")[0] || "/";
}
