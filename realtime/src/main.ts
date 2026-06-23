import { randomUUID } from "node:crypto";
import { loadConfig, type RealtimeConfig } from "./config";
import { AuthService } from "./auth";
import { RedisStores } from "./redis-stores";
import { MemoryStores } from "./memory-stores";
import { RoomManager } from "./room-manager";
import { CollaborationEngine } from "./collaboration";
import { PresenceService } from "./presence";
import { BroadcastService } from "./broadcast";
import { OpPersistenceService } from "./op-persistence";
import { BackpressureTracker } from "./backpressure";
import { createProducer } from "./kafka-adapter";
import { createRealtimeServer, type RealtimeServer, type RealtimeServerDeps } from "./realtime-server";
import { createHttpServer } from "./http-server";
import type { KafkaProducerAdapter } from "./kafka-adapter";

export interface RealtimeRuntime {
  config: RealtimeConfig;
  server: RealtimeServer;
  close(): Promise<void>;
}

export async function createRuntime(config: RealtimeConfig = loadConfig()): Promise<RealtimeRuntime> {
  const instanceId = `rt:${randomUUID()}`;
  const useRedis = !config.redis.host.endsWith("memory");
  const stores = useRedis ? new RedisStores(config.redis) : new MemoryStores();
  if (stores instanceof RedisStores) {
    await stores.connect();
  }

  const auth = new AuthService(config.auth, config.core, stores);
  const rooms = new RoomManager(stores);
  const collaboration = new CollaborationEngine(config.collaboration, stores, instanceId);
  const presence = new PresenceService(config.presence, stores);
  const broadcast = new BroadcastService(stores, instanceId);
  const backpressure = new BackpressureTracker(config.backpressure);

  const producer = await createProducer(config.kafka);
  const ops = new OpPersistenceService(config.kafka, producer);

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
    stores,
    producer
  };

  const http = createHttpServer(config, {
    redisReady: useRedis,
    kafkaReady: config.kafka.enabled,
    authRequired: !config.auth.disabled
  });
  await http.ready();

  const httpServer = http.server;
  const server = createRealtimeServer({ config, deps, httpServer });

  return {
    config,
    server,
    async close() {
      await server.close();
      await http.close();
      await stores.close();
    }
  };
}

export async function start(): Promise<void> {
  const config = loadConfig();
  const runtime = await createRuntime(config);
  const httpServer = runtime.server.io.httpServer;
  if (httpServer && typeof (httpServer as { listen?: Function }).listen === "function" && !httpServer.listening) {
    (httpServer as { listen: Function }).listen(config.port, config.host, () => {
      console.log(`realtime listening on ${config.host}:${config.port}`);
    });
  }
  const shutdown = async () => {
    await runtime.close();
    process.exit(0);
  };
  process.on("SIGTERM", shutdown);
  process.on("SIGINT", shutdown);
}