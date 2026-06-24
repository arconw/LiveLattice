import type { GatewayClient } from "./api-client";

export const digestFrequencies = ["instant", "hourly", "daily", "never"] as const;

export type DigestFrequency = (typeof digestFrequencies)[number];

export type NotificationTargetType = "canvas" | "dashboard" | "job" | "workspace" | "system" | "audit";

export type NotificationTarget = {
  type: NotificationTargetType;
  id: string;
  href: string;
};

export type NotificationItem = {
  id: string;
  workspaceId: string | null;
  type: string;
  title: string;
  body: string;
  readAt: string | null;
  createdAt: string;
  target: NotificationTarget;
};

export type NotificationListResponse = {
  notifications: NotificationItem[];
  total: number;
  unread: number;
};

export type WebhookEndpoint = {
  id: string;
  url: string;
  events: string[];
  createdAt: string;
  lastDeliveryStatus: "pending" | "delivered" | "failed" | "unknown";
};

export type NotificationPreferences = {
  emailDigest: DigestFrequency;
  mutedTypes: string[];
  webhooks: WebhookEndpoint[];
};

export type WebhookCreatePayload = {
  url: string;
  events: string[];
  secret: string;
};

export async function listNotifications(client: GatewayClient, workspaceId: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ workspace_id: workspaceId, size: "50" });
  const payload = await client.get<unknown>(`/api/notifications/notifications?${params.toString()}`, { signal });
  return mapNotificationList(payload);
}

export async function getUnreadNotificationCount(client: GatewayClient, workspaceId: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ workspace_id: workspaceId });
  const payload = await client.get<unknown>(`/api/notifications/notifications/unread-count?${params.toString()}`, { signal });
  const record = asRecord(payload);
  return toNumber(record.unread ?? record.count, 0);
}

export async function markNotificationRead(client: GatewayClient, notificationId: string, signal?: AbortSignal) {
  await client.patch<unknown>(`/api/notifications/notifications/${notificationId}/read`, {}, { signal });
}

export async function markAllNotificationsRead(client: GatewayClient, workspaceId: string, signal?: AbortSignal) {
  await client.post<unknown>("/api/notifications/notifications/read-all", { workspaceId }, { signal });
}

export async function getNotificationPreferences(client: GatewayClient, signal?: AbortSignal) {
  const payload = await client.get<unknown>("/api/notifications/notification-preferences", { signal });
  return mapNotificationPreferences(payload);
}

export async function updateNotificationPreferences(client: GatewayClient, preferences: NotificationPreferences, signal?: AbortSignal) {
  const payload = await client.patch<unknown>(
    "/api/notifications/notification-preferences",
    {
      emailDigest: preferences.emailDigest,
      mutedTypes: preferences.mutedTypes
    },
    { signal }
  );
  return mapNotificationPreferences(payload);
}

export async function addNotificationWebhook(client: GatewayClient, payload: WebhookCreatePayload, signal?: AbortSignal) {
  const response = await client.post<unknown>("/api/notifications/notification-preferences/webhooks", payload, { signal });
  return mapWebhookEndpoint(response);
}

export async function deleteNotificationWebhook(client: GatewayClient, webhookId: string, signal?: AbortSignal) {
  await client.delete<unknown>(`/api/notifications/notification-preferences/webhooks/${webhookId}`, { signal });
}

export function mapNotificationList(payload: unknown): NotificationListResponse {
  const record = asRecord(payload);
  const source = Array.isArray(payload) ? payload : Array.isArray(record.notifications) ? record.notifications : Array.isArray(record.items) ? record.items : [];
  const notifications = source.map(mapNotificationItem).filter((item): item is NotificationItem => item !== null);
  const unread = toNumber(record.unread, notifications.filter((notification) => notification.readAt === null).length);

  return {
    notifications,
    total: toNumber(record.total, notifications.length),
    unread
  };
}

export function mapNotificationPreferences(payload: unknown): NotificationPreferences {
  const record = asRecord(payload);
  const emailDigest = normalizeDigestFrequency(record.emailDigest ?? record.email_digest);
  const rawMutedTypes = record.mutedTypes ?? record.muted_types;
  const mutedTypes = Array.isArray(rawMutedTypes)
    ? rawMutedTypes.flatMap((item: unknown) => {
        const value = toString(item);
        return value ? [value] : [];
      })
    : [];
  const webhooks = Array.isArray(record.webhooks) ? record.webhooks.map(mapWebhookEndpoint).filter((item): item is WebhookEndpoint => item !== null) : [];

  return {
    emailDigest: emailDigest ?? "instant",
    mutedTypes,
    webhooks
  };
}

export function normalizeDigestFrequency(value: unknown): DigestFrequency | null {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.toLowerCase();
  return digestFrequencies.find((frequency) => frequency === normalized) ?? null;
}

export function notificationTargetHref(target: NotificationTarget, workspaceSlug: string) {
  if (target.href.startsWith("/")) {
    return target.href;
  }

  if (target.type === "canvas") {
    return `/w/${workspaceSlug}/c/${target.id}`;
  }

  if (target.type === "dashboard") {
    return `/w/${workspaceSlug}/d/${target.id}`;
  }

  if (target.type === "job") {
    return `/w/${workspaceSlug}/jobs`;
  }

  if (target.type === "audit") {
    return `/w/${workspaceSlug}/audit?target=${encodeURIComponent(target.id)}`;
  }

  return `/w/${workspaceSlug}`;
}

function mapNotificationItem(payload: unknown): NotificationItem | null {
  const record = asRecord(payload);
  const id = toString(record.id);
  const type = toString(record.type);
  const title = toString(record.title);

  if (!id || !type || !title) {
    return null;
  }

  return {
    id,
    workspaceId: toNullableString(record.workspaceId ?? record.workspace_id),
    type,
    title,
    body: toString(record.body),
    readAt: toNullableString(record.readAt ?? record.read_at),
    createdAt: toString(record.createdAt ?? record.created_at) || new Date(0).toISOString(),
    target: mapTarget(record.data ?? record.target)
  };
}

function mapTarget(payload: unknown): NotificationTarget {
  const record = asRecord(payload);
  const actionUrl = toString(record.actionUrl ?? record.action_url ?? record.href);

  if (toString(record.canvasId ?? record.canvas_id)) {
    return { type: "canvas", id: toString(record.canvasId ?? record.canvas_id), href: actionUrl };
  }

  if (toString(record.dashboardId ?? record.dashboard_id)) {
    return { type: "dashboard", id: toString(record.dashboardId ?? record.dashboard_id), href: actionUrl };
  }

  if (toString(record.jobId ?? record.job_id)) {
    return { type: "job", id: toString(record.jobId ?? record.job_id), href: actionUrl };
  }

  if (toString(record.auditId ?? record.audit_id)) {
    return { type: "audit", id: toString(record.auditId ?? record.audit_id), href: actionUrl };
  }

  return { type: actionUrl ? "system" : "workspace", id: toString(record.workspaceId ?? record.workspace_id) || "workspace", href: actionUrl };
}

function mapWebhookEndpoint(payload: unknown): WebhookEndpoint | null {
  const record = asRecord(payload);
  const id = toString(record.id);
  const url = toString(record.url);

  if (!id || !url) {
    return null;
  }

  return {
    id,
    url,
    events: Array.isArray(record.events) ? record.events.flatMap((event) => toString(event) ? [toString(event)] : []) : [],
    createdAt: toString(record.createdAt ?? record.created_at) || new Date(0).toISOString(),
    lastDeliveryStatus: normalizeDeliveryStatus(record.lastDeliveryStatus ?? record.last_delivery_status)
  };
}

function normalizeDeliveryStatus(value: unknown): WebhookEndpoint["lastDeliveryStatus"] {
  if (value === "pending" || value === "delivered" || value === "failed") {
    return value;
  }

  return "unknown";
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
