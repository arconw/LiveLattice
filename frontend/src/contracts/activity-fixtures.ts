import type { AuditEvent } from "./audit";
import type { HealthOverview } from "./health";
import type { ActivityJob } from "./jobs";
import type { NotificationItem, NotificationPreferences } from "./notifications";
import type { SearchResponse, SearchSuggestion } from "./search";
import { canvasListHref, coreFixtureIds } from "./fixture-ids";

export const searchResponseFixture: SearchResponse = {
  results: [
    {
      id: coreFixtureIds.canvasIncidentMap,
      type: "canvas",
      workspaceId: coreFixtureIds.workspaceFactoryFloor,
      title: "Warehouse flow",
      content: "Gateway export boundary diagram with RBAC validation and MinIO artifact handling.",
      tags: ["export", "rbac", "gateway"],
      authorId: "keycloak-subject",
      createdAt: "2026-06-01T12:00:00Z",
      updatedAt: "2026-06-23T12:31:00Z",
      highlights: {
        title: ["Warehouse <em>flow</em>"],
        content: ["Gateway <em>export</em> boundary diagram with RBAC validation."]
      },
      targetUrl: canvasListHref("factory-floor")
    },
    {
      id: coreFixtureIds.commentExportBoundary,
      type: "comment",
      workspaceId: coreFixtureIds.workspaceFactoryFloor,
      title: "Export boundary comment",
      content: "Validate RBAC before export job creation and signed URL refresh.",
      tags: ["comment", "export"],
      authorId: "analytics-subject",
      createdAt: "2026-06-23T12:27:00Z",
      updatedAt: "2026-06-23T12:28:00Z",
      highlights: {
        content: ["Validate RBAC before <em>export</em> job creation."]
      },
      targetUrl: canvasListHref("factory-floor")
    },
    {
      id: coreFixtureIds.dashboardOperations,
      type: "dashboard",
      workspaceId: coreFixtureIds.workspaceFactoryFloor,
      title: "Throughput board",
      content: "Dashboard widget tracking import and export duration by workspace.",
      tags: ["dashboard", "jobs"],
      createdAt: "2026-06-20T09:30:00Z",
      updatedAt: "2026-06-23T11:58:00Z",
      highlights: {
        content: ["Dashboard widget tracking import and <em>export</em> duration."]
      },
      targetUrl: `/w/factory-floor/d/${coreFixtureIds.dashboardOperations}`
    }
  ],
  total: 27,
  page: 1,
  size: 3,
  nextSearchAfter: `2026-06-23T11:58:00Z|${coreFixtureIds.dashboardOperations}`,
  facets: {
    type: {
      canvas: 11,
      comment: 8,
      dashboard: 3,
      document: 2,
      template: 2,
      user: 1
    },
    tags: {
      export: 9,
      rbac: 5,
      gateway: 4,
      jobs: 3
    }
  }
};

export const searchSuggestionsFixture: SearchSuggestion[] = [
  { value: "export boundary", type: "comment", resultId: coreFixtureIds.commentExportBoundary },
  { value: "export job status", type: "document", resultId: "doc-export-status" },
  { value: "rbac export validation", type: "canvas", resultId: coreFixtureIds.canvasIncidentMap }
];

export const activityJobsFixture: ActivityJob[] = [
  {
    id: "job-svg-import",
    domain: "import",
    type: "IMPORT",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    ownerId: "keycloak-subject",
    status: "running",
    progress: 86,
    retryCount: 0,
    maxRetries: 3,
    failureReason: null,
    downloadUrl: null,
    downloadExpiresAt: null,
    createdAt: "2026-06-23T12:20:00Z",
    startedAt: "2026-06-23T12:21:00Z",
    completedAt: null
  },
  {
    id: "job-pdf-export",
    domain: "export",
    type: "EXPORT",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    ownerId: "analytics-subject",
    status: "succeeded",
    progress: 100,
    retryCount: 0,
    maxRetries: 3,
    failureReason: null,
    downloadUrl: "https://downloads.example.test/export/job-pdf-export",
    downloadExpiresAt: "2026-06-24T13:20:00Z",
    createdAt: "2026-06-23T12:12:00Z",
    startedAt: "2026-06-23T12:13:00Z",
    completedAt: "2026-06-23T12:18:00Z"
  },
  {
    id: "job-expired-export",
    domain: "export",
    type: "EXPORT",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    ownerId: "keycloak-subject",
    status: "succeeded",
    progress: 100,
    retryCount: 0,
    maxRetries: 3,
    failureReason: null,
    downloadUrl: "https://downloads.example.test/export/job-expired-export",
    downloadExpiresAt: "2026-06-23T10:00:00Z",
    createdAt: "2026-06-23T09:08:00Z",
    startedAt: "2026-06-23T09:09:00Z",
    completedAt: "2026-06-23T09:12:00Z"
  },
  {
    id: "job-index-sync",
    domain: "background",
    type: "IndexSync",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    ownerId: "system",
    status: "failed",
    progress: 42,
    retryCount: 1,
    maxRetries: 3,
    failureReason: "OpenSearch bulk request timed out.",
    downloadUrl: null,
    downloadExpiresAt: null,
    createdAt: "2026-06-23T11:55:00Z",
    startedAt: "2026-06-23T11:56:00Z",
    completedAt: "2026-06-23T11:57:00Z"
  },
  {
    id: "job-digest",
    domain: "background",
    type: "EmailDigest",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    ownerId: "system",
    status: "queued",
    progress: 0,
    retryCount: 0,
    maxRetries: 3,
    failureReason: null,
    downloadUrl: null,
    downloadExpiresAt: null,
    createdAt: "2026-06-23T12:00:00Z",
    startedAt: null,
    completedAt: null
  }
];

export const notificationsFixture: NotificationItem[] = [
  {
    id: "notif-mention",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    type: "canvas.@mention",
    title: "@mention on Warehouse flow",
    body: "Analytics asked you to validate the export connector.",
    readAt: null,
    createdAt: "2026-06-23T12:32:00Z",
    target: { type: "canvas", id: coreFixtureIds.canvasIncidentMap, href: canvasListHref("factory-floor") }
  },
  {
    id: "notif-export",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    type: "canvas.export.complete",
    title: "PDF export is ready",
    body: "The export artifact is available from the jobs page.",
    readAt: null,
    createdAt: "2026-06-23T12:18:00Z",
    target: { type: "job", id: "job-pdf-export", href: "/w/factory-floor/jobs" }
  },
  {
    id: "notif-quota",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    type: "workspace.quota.warning",
    title: "Workspace quota warning",
    body: "Factory floor reached 90 percent of its storage quota.",
    readAt: "2026-06-23T12:00:00Z",
    createdAt: "2026-06-23T11:44:00Z",
    target: { type: "workspace", id: coreFixtureIds.workspaceFactoryFloor, href: "/w/factory-floor" }
  }
];

export const notificationPreferencesFixture: NotificationPreferences = {
  emailDigest: "hourly",
  mutedTypes: ["member.joined"],
  webhooks: [
    {
      id: "webhook-ops",
      url: "https://hooks.example.test/livelattice",
      events: ["canvas.export.complete", "workspace.quota.warning"],
      createdAt: "2026-06-20T10:00:00Z",
      lastDeliveryStatus: "delivered"
    }
  ]
};

export const auditEventsFixture: AuditEvent[] = [
  {
    id: "audit-001",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    actorId: "keycloak-subject",
    actorDisplay: "LiveLattice Owner",
    action: "canvas.update",
    targetType: "canvas",
    targetId: coreFixtureIds.canvasIncidentMap,
    occurredAt: "2026-06-23T12:31:08Z",
    metadata: { requestId: "req-canvas-001", traceId: "trace-canvas-001" },
    previousHash: "0000000000000000000000000000000000000000000000000000000000000000",
    hash: "8adffb14fd8d6d9899bd6d47d7d909540b4c8ba9a0c192ff9cc0c2d1e2218029"
  },
  {
    id: "audit-002",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    actorId: "analytics-subject",
    actorDisplay: "Analytics",
    action: "canvas.export",
    targetType: "canvas",
    targetId: coreFixtureIds.canvasIncidentMap,
    occurredAt: "2026-06-23T12:18:20Z",
    metadata: { requestId: "req-export-002", format: "pdf" },
    previousHash: "8adffb14fd8d6d9899bd6d47d7d909540b4c8ba9a0c192ff9cc0c2d1e2218029",
    hash: "29d17d52915957eaef1c8bd4785e1e2a71e0a3797de091c53da8e124893e2f40"
  },
  {
    id: "audit-003",
    workspaceId: coreFixtureIds.workspaceFactoryFloor,
    actorId: "system",
    actorDisplay: "Background jobs",
    action: "search.bulk_indexed",
    targetType: "workspace",
    targetId: coreFixtureIds.workspaceFactoryFloor,
    occurredAt: "2026-06-23T12:17:45Z",
    metadata: { index: "canvases", count: 6 },
    previousHash: "29d17d52915957eaef1c8bd4785e1e2a71e0a3797de091c53da8e124893e2f40",
    hash: "eca7b282fd7774e2485fa756b7e025b529efdcdb938a5ec023b947d18d3b7d68"
  }
];

export const healthOverviewFixture: HealthOverview = {
  status: "DEGRADED",
  services: [
    { name: "gateway", status: "UP", latencyMs: 3, affectedFeatures: ["Routing", "authentication refresh"] },
    { name: "core", status: "UP", latencyMs: 8, affectedFeatures: ["workspaces", "canvases", "dashboards"] },
    { name: "search", status: "DEGRADED", latencyMs: 310, affectedFeatures: ["workspace search", "suggestions", "facets"] },
    { name: "notifications", status: "UP", latencyMs: 12, affectedFeatures: ["inbox", "unread badge", "webhooks"] },
    { name: "background-jobs", status: "DOWN", latencyMs: null, affectedFeatures: ["job progress", "retry state", "scheduled work"] }
  ]
};
