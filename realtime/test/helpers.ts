import { randomUUID } from "node:crypto";
import type { AddressInfo } from "node:net";
import { io as ioClient, type Socket as ClientSocket } from "socket.io-client";
import { loadConfig, type RealtimeConfig } from "../src/config";
import { AuthService } from "../src/auth";
import { MemoryStores } from "../src/memory-stores";
import { RoomManager } from "../src/room-manager";
import { CollaborationEngine } from "../src/collaboration";
import { PresenceService } from "../src/presence";
import { BroadcastService } from "../src/broadcast";
import { OpPersistenceService } from "../src/op-persistence";
import { BackpressureTracker } from "../src/backpressure";
import { createRealtimeServer, type RealtimeServer, type RealtimeServerDeps } from "../src/realtime-server";
import { createHttpServer } from "../src/http-server";
import type { FastifyInstance } from "fastify";

export interface TestRuntime {
  server: RealtimeServer;
  stores: MemoryStores;
  collaboration: CollaborationEngine;
  deps: RealtimeServerDeps;
  baseUrl: string;
  close(): Promise<void>;
}

export async function createTestRuntime(overrides: Partial<RealtimeConfig> = {}): Promise<TestRuntime> {
  const config = loadConfig({
    PORT: "0",
    AUTH_DISABLED: "true",
    KAFKA_ENABLED: "false",
    REDIS_HOST: "memory",
    CORE_URL: "http://core:8080",
    INTERNAL_AUTH_SECRET: "internal-secret",
    ...overrides
  });
  const instanceId = `rt:test:${randomUUID()}`;
  const stores = new MemoryStores();
  const auth = new AuthService(config.auth, config.core, stores);
  const rooms = new RoomManager(stores);
  const collaboration = new CollaborationEngine(config.collaboration, stores, instanceId);
  const presence = new PresenceService(config.presence, stores);
  const broadcast = new BroadcastService(stores, instanceId);
  const backpressure = new BackpressureTracker(config.backpressure);
  const ops = new OpPersistenceService(config.kafka, undefined);
  const deps: RealtimeServerDeps = {
    auth,
    rooms,
    collaboration,
    presence,
    broadcast,
    ops,
    backpressure,
    membershipCache: stores,
    pubsub: stores,
    snapshots: stores,
    awareness: stores,
    stores
  };
  const http = createHttpServer(config, {
    redisReady: false,
    kafkaReady: false,
    authRequired: !config.auth.disabled
  });
  await http.ready();
  const httpServer = http.server;
  const server = createRealtimeServer({ config, deps, httpServer });
  await new Promise<void>((resolve) => httpServer.listen(0, "127.0.0.1", resolve));
  const port = (httpServer.address() as AddressInfo).port;
  return {
    server,
    stores,
    collaboration,
    deps,
    baseUrl: `http://127.0.0.1:${port}`,
    async close() {
      await server.close();
      await http.close();
      await stores.close();
    }
  };
}

export function connect(runtime: TestRuntime, workspaceId: string, auth?: { token?: string }): ClientSocket {
  return ioClient(`${runtime.baseUrl}/ws/${workspaceId}`, {
    transports: ["websocket"],
    auth: auth?.token ? { token: auth.token } : undefined,
    forceNew: true
  });
}