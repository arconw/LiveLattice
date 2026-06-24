import type { GatewayClient } from "./api-client";

export type AuditEvent = {
  id: string;
  workspaceId: string;
  actorId: string;
  actorDisplay: string;
  action: string;
  targetType: string;
  targetId: string;
  occurredAt: string;
  metadata: Record<string, unknown>;
  hash: string | null;
  previousHash: string | null;
};

export type AuditFilters = {
  workspaceId: string;
  actorId?: string;
  action?: string;
  targetType?: string;
  targetId?: string;
  from?: string;
  to?: string;
};

export type AuditListResponse = {
  events: AuditEvent[];
  total: number;
};

export function buildAuditPath(filters: AuditFilters) {
  const params = new URLSearchParams({ workspace_id: filters.workspaceId, size: "50" });

  if (filters.actorId) {
    params.set("actor_id", filters.actorId);
  }

  if (filters.action) {
    params.set("action", filters.action);
  }

  if (filters.targetType) {
    params.set("target_type", filters.targetType);
  }

  if (filters.targetId) {
    params.set("target_id", filters.targetId);
  }

  if (filters.from) {
    params.set("from", filters.from);
  }

  if (filters.to) {
    params.set("to", filters.to);
  }

  return `/api/audit-log/audit-log?${params.toString()}`;
}

export async function listAuditEvents(client: GatewayClient, filters: AuditFilters, signal?: AbortSignal) {
  const payload = await client.get<unknown>(buildAuditPath(filters), { signal });
  return mapAuditList(payload);
}

export function mapAuditList(payload: unknown): AuditListResponse {
  if (Array.isArray(payload)) {
    const events = payload.map(mapAuditEvent).filter((event): event is AuditEvent => event !== null);
    return { events, total: events.length };
  }

  const record = asRecord(payload);
  const source = Array.isArray(record.events) ? record.events : Array.isArray(record.items) ? record.items : [];
  const events = source.map(mapAuditEvent).filter((event): event is AuditEvent => event !== null);

  return {
    events,
    total: toNumber(record.total, events.length)
  };
}

export function mapAuditEvent(payload: unknown): AuditEvent | null {
  const record = asRecord(payload);
  const id = toString(record.id);
  const workspaceId = toString(record.workspaceId ?? record.workspace_id);
  const action = toString(record.action);
  const actorId = toString(record.actorId ?? record.actor_id);
  const targetType = toString(record.targetType ?? record.target_type);
  const targetId = toString(record.targetId ?? record.target_id);

  if (!id || !workspaceId || !action || !actorId || !targetType || !targetId) {
    return null;
  }

  return {
    id,
    workspaceId,
    actorId,
    actorDisplay: toString(record.actorDisplay ?? record.actor_display) || actorId,
    action,
    targetType,
    targetId,
    occurredAt: toString(record.occurredAt ?? record.occurred_at) || new Date(0).toISOString(),
    metadata: asRecord(record.metadata),
    hash: toNullableString(record.hash),
    previousHash: toNullableString(record.previousHash ?? record.previous_hash)
  };
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function toNumber(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function toString(value: unknown) {
  return typeof value === "string" ? value : "";
}

function toNullableString(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : null;
}
