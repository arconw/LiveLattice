import type { GatewayClient } from "./api-client";

export type ServiceReadinessStatus = "UP" | "DOWN" | "DEGRADED" | "UNKNOWN";

export type ServiceReadiness = {
  name: string;
  status: ServiceReadinessStatus;
  latencyMs: number | null;
  affectedFeatures: string[];
};

export type HealthOverview = {
  status: ServiceReadinessStatus;
  services: ServiceReadiness[];
};

const featureHints: Record<string, string[]> = {
  gateway: ["Routing", "authentication refresh"],
  core: ["workspaces", "canvases", "dashboards"],
  search: ["workspace search", "suggestions", "facets"],
  notifications: ["inbox", "unread badge", "webhooks"],
  "import-export": ["imports", "exports", "artifact downloads"],
  "background-jobs": ["job progress", "retry state", "scheduled work"],
  "audit-log": ["audit trail", "compliance exports"],
  realtime: ["presence", "comments", "canvas collaboration"],
  opensearch: ["workspace search", "suggestions"],
  redis: ["suggestion cache", "job progress", "notification stream"],
  kafka: ["index updates", "notifications", "audit ingestion"],
  postgresql: ["workspace data", "audit records"],
  minio: ["snapshots", "imports", "exports"]
};

export async function getGatewayReadiness(client: GatewayClient, signal?: AbortSignal) {
  const payload = await client.get<unknown>("/ready", { signal });
  return mapHealthOverview(payload);
}

export function mapHealthOverview(payload: unknown): HealthOverview {
  const record = asRecord(payload);
  const checks = asRecord(record.checks);
  const services = mapReadinessChecks(checks);
  const status = normalizeStatus(record.status) ?? aggregateStatus(services);

  return {
    status,
    services
  };
}

export function affectedFeaturesForService(serviceName: string) {
  return featureHints[serviceName] ?? ["workspace activity"];
}

function mapReadinessChecks(checks: Record<string, unknown>) {
  const services: ServiceReadiness[] = [];

  Object.entries(checks).forEach(([name, value]) => {
    if (Array.isArray(value)) {
      value.forEach((service) => {
        if (typeof service === "string") {
          services.push({ name: service, status: "UP", latencyMs: null, affectedFeatures: affectedFeaturesForService(service) });
        }
      });
      return;
    }

    const record = asRecord(value);
    const details = asRecord(record.details);
    const status = normalizeStatus(record.status) ?? "UNKNOWN";

    services.push({
      name,
      status,
      latencyMs: toNullableNumber(details.latencyMs ?? details.latency_ms),
      affectedFeatures: affectedFeaturesForService(name)
    });
  });

  return services;
}

function aggregateStatus(services: ServiceReadiness[]): ServiceReadinessStatus {
  if (services.some((service) => service.status === "DOWN")) {
    return "DOWN";
  }

  if (services.some((service) => service.status === "DEGRADED" || service.status === "UNKNOWN")) {
    return "DEGRADED";
  }

  return "UP";
}

function normalizeStatus(value: unknown): ServiceReadinessStatus | null {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.toUpperCase();

  if (normalized === "UP" || normalized === "DOWN" || normalized === "DEGRADED" || normalized === "UNKNOWN") {
    return normalized;
  }

  return null;
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function toNullableNumber(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
