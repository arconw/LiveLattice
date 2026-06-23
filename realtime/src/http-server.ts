import Fastify from "fastify";
import type { RealtimeConfig } from "./config";

export interface HealthDeps {
  redisReady: boolean;
  kafkaReady: boolean;
  authRequired: boolean;
}

export function createHttpServer(config: RealtimeConfig, deps: HealthDeps) {
  const app = Fastify({ logger: false });

  app.get("/health", async () => ({
    status: "UP",
    service: config.name,
    version: config.version
  }));

  app.get("/ready", async () => ({
    status: "UP",
    checks: {
      process: "UP",
      redis: deps.redisReady ? "UP" : "DOWN",
      kafka: deps.kafkaReady ? "UP" : "DOWN",
      authRequired: !config.auth.disabled
    }
  }));

  return app;
}