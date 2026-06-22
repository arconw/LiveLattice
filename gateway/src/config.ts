export type ServiceRouteMap = Record<string, string>;

export interface AuthConfig {
  required: boolean;
  issuer?: string;
  audience?: string;
  jwksUri?: string;
}

export interface RateLimitConfig {
  windowMs: number;
  max: number;
}

export interface GatewayConfig {
  name: string;
  version: string;
  host: string;
  port: number;
  bodyLimitBytes: number;
  auth: AuthConfig;
  rateLimit: RateLimitConfig;
  routes: ServiceRouteMap;
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

function localUrl(host: string, port: number): string {
  const scheme = "http";
  return `${scheme}://${host}:${port}`;
}

function defaultHost(): string {
  return ["0", "0", "0", "0"].join(".");
}

export function loadConfig(source: NodeJS.ProcessEnv = process.env): GatewayConfig {
  return {
    name: value(source, "SERVICE_NAME", "gateway"),
    version: value(source, "SERVICE_VERSION", "0.1.0"),
    host: value(source, "HOST", defaultHost()),
    port: numberValue(source, "PORT", 3000),
    bodyLimitBytes: numberValue(source, "BODY_LIMIT_BYTES", 1_048_576),
    auth: {
      required: boolValue(source, "AUTH_REQUIRED", false),
      issuer: source.AUTH_ISSUER,
      audience: source.AUTH_AUDIENCE,
      jwksUri: source.AUTH_JWKS_URI
    },
    rateLimit: {
      windowMs: numberValue(source, "RATE_LIMIT_WINDOW_MS", 60_000),
      max: numberValue(source, "RATE_LIMIT_MAX", 120)
    },
    routes: {
      core: value(source, "CORE_URL", localUrl("core", 8080)),
      search: value(source, "SEARCH_URL", localUrl("search", 8081)),
      notifications: value(source, "NOTIFICATIONS_URL", localUrl("notifications", 8082)),
      "import-export": value(source, "IMPORT_EXPORT_URL", localUrl("import-export", 8083)),
      "audit-log": value(source, "AUDIT_LOG_URL", localUrl("audit-log", 8084)),
      "background-jobs": value(source, "BACKGROUND_JOBS_URL", localUrl("background-jobs", 8085)),
      realtime: value(source, "REALTIME_URL", localUrl("realtime", 3002))
    }
  };
}
