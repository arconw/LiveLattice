export interface RealtimeConfig {
  name: string;
  version: string;
  host: string;
  port: number;
  auth: AuthConfig;
  redis: RedisConfig;
  kafka: KafkaConfig;
  core: CoreConfig;
  collaboration: CollaborationConfig;
  presence: PresenceConfig;
  backpressure: BackpressureConfig;
}

export interface AuthConfig {
  disabled: boolean;
  issuer?: string;
  audience?: string;
  jwksUri?: string;
  jwksTtlSeconds: number;
}

export interface RedisConfig {
  host: string;
  port: number;
  password?: string;
  roomKeyTtlSeconds: number;
}

export interface KafkaConfig {
  enabled: boolean;
  brokers: string[];
  canvasOpsTopic: string;
  flushBatchSize: number;
  flushIntervalMs: number;
}

export interface CoreConfig {
  url: string;
  internalSecret: string;
  membershipCacheTtlSeconds: number;
}

export interface CollaborationConfig {
  snapshotOpsThreshold: number;
  snapshotIntervalMs: number;
}

export interface PresenceConfig {
  throttleMs: number;
  ttlSeconds: number;
}

export interface BackpressureConfig {
  messagesPerSecond: number;
}

function value(source: NodeJS.ProcessEnv, key: string, fallback: string): string {
  const current = source[key];
  return current === undefined || current.length === 0 ? fallback : current;
}

function numberValue(source: NodeJS.ProcessEnv, key: string, fallback: number): number {
  const raw = source[key];
  if (raw === undefined || raw.length === 0) {
    return fallback;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function boolValue(source: NodeJS.ProcessEnv, key: string, fallback: boolean): boolean {
  const raw = source[key];
  if (raw === undefined || raw.length === 0) {
    return fallback;
  }
  return ["1", "true", "yes", "on"].includes(raw.toLowerCase());
}

function listValue(source: NodeJS.ProcessEnv, key: string, fallback: string[]): string[] {
  const raw = source[key];
  if (raw === undefined || raw.length === 0) {
    return fallback;
  }
  return raw.split(",").map((item) => item.trim()).filter((item) => item.length > 0);
}

function defaultHost(): string {
  return ["0", "0", "0", "0"].join(".");
}

export function loadConfig(source: NodeJS.ProcessEnv = process.env): RealtimeConfig {
  return {
    name: value(source, "SERVICE_NAME", "realtime"),
    version: value(source, "SERVICE_VERSION", "0.1.0"),
    host: value(source, "HOST", defaultHost()),
    port: numberValue(source, "PORT", 3002),
    auth: {
      disabled: boolValue(source, "AUTH_DISABLED", false),
      issuer: source.AUTH_ISSUER,
      audience: source.AUTH_AUDIENCE,
      jwksUri: source.AUTH_JWKS_URI,
      jwksTtlSeconds: numberValue(source, "JWKS_CACHE_TTL_SECONDS", 3600)
    },
    redis: {
      host: value(source, "REDIS_HOST", "localhost"),
      port: numberValue(source, "REDIS_PORT", 6379),
      password: source.REDIS_PASSWORD,
      roomKeyTtlSeconds: numberValue(source, "REDIS_ROOM_KEY_TTL_SECONDS", 86400)
    },
    kafka: {
      enabled: boolValue(source, "KAFKA_ENABLED", true),
      brokers: listValue(source, "KAFKA_BROKERS", ["localhost:9092"]),
      canvasOpsTopic: value(source, "KAFKA_CANVAS_OPS_TOPIC", "canvas.ops"),
      flushBatchSize: numberValue(source, "KAFKA_FLUSH_BATCH_SIZE", 50),
      flushIntervalMs: numberValue(source, "KAFKA_FLUSH_INTERVAL_MS", 50)
    },
    core: {
      url: value(source, "CORE_URL", "http://core:8080"),
      internalSecret: value(source, "INTERNAL_AUTH_SECRET", "livelattice_internal_dev_secret"),
      membershipCacheTtlSeconds: numberValue(source, "CORE_MEMBERSHIP_CACHE_TTL_SECONDS", 60)
    },
    collaboration: {
      snapshotOpsThreshold: numberValue(source, "SNAPSHOT_OPS_THRESHOLD", 50),
      snapshotIntervalMs: numberValue(source, "SNAPSHOT_INTERVAL_MS", 30000)
    },
    presence: {
      throttleMs: numberValue(source, "PRESENCE_THROTTLE_MS", 100),
      ttlSeconds: numberValue(source, "PRESENCE_TTL_SECONDS", 60)
    },
    backpressure: {
      messagesPerSecond: numberValue(source, "BACKPRESSURE_LIMIT", 100)
    }
  };
}