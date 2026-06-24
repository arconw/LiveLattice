import { randomUUID } from "node:crypto";
import Fastify from "fastify";
import type { FastifyRequest } from "fastify";
import type { RealtimeConfig } from "./config";
import { createLogger } from "./logger";
import { RealtimeMetrics } from "./metrics";

export interface HealthDeps {
  redisReady: boolean;
  kafkaReady: boolean;
  authRequired: boolean;
}

const startedAtKey = Symbol("startedAt");

export function createHttpServer(config: RealtimeConfig, deps: HealthDeps, metrics = new RealtimeMetrics()) {
  const logger = createLogger(config.name);
  const app = Fastify({ logger: false });

  app.addHook("onRequest", async (request, reply) => {
    metrics.httpStart();
    Reflect.set(request, startedAtKey, process.hrtime.bigint());
    const requestId = headerValue(request.headers["x-request-id"]) ?? randomUUID();
    request.headers["x-request-id"] = requestId;
    reply.header("x-request-id", requestId);
  });

  app.addHook("onResponse", async (request, reply) => {
    const startedAt = Reflect.get(request, startedAtKey) as bigint | undefined;
    const durationSeconds = startedAt ? Number(process.hrtime.bigint() - startedAt) / 1_000_000_000 : 0;
    metrics.httpFinish(request.method, routeLabel(request), reply.statusCode, durationSeconds);
    logger.info({
      request_id: headerValue(request.headers["x-request-id"]),
      method: request.method,
      path: routeLabel(request),
      status: reply.statusCode,
      duration_ms: Number((durationSeconds * 1000).toFixed(3))
    }, "http request completed");
  });

  app.get("/health", async () => ({
    status: "UP",
    service: config.name,
    version: config.version,
    uptimeSeconds: Math.floor(process.uptime())
  }));

  app.get("/ready", async () => ({
    status: "UP",
    service: config.name,
    version: config.version,
    uptimeSeconds: Math.floor(process.uptime()),
    checks: {
      process: "UP",
      redis: deps.redisReady ? "UP" : "DOWN",
      kafka: deps.kafkaReady ? "UP" : "DOWN",
      authRequired: !config.auth.disabled
    },
    checkDetails: {
      process: { status: "healthy" },
      redis: { status: deps.redisReady ? "healthy" : "unhealthy" },
      kafka: { status: deps.kafkaReady ? "healthy" : "unhealthy" },
      auth: { status: "healthy", required: !config.auth.disabled }
    }
  }));

  app.get("/metrics", async (_request, reply) => {
    reply.header("content-type", "text/plain; version=0.0.4");
    return metrics.render();
  });

  return app;
}

function headerValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function routeLabel(request: FastifyRequest): string {
  return (request.raw.url ?? "/").split("?")[0] || "/";
}
