import errorFixtures from "./fixtures/gateway-errors.json";
import { workspaceRoles } from "./auth";
import type { CreateDataSourcePayload, DashboardDataResponse, DashboardResponse, DataSourceResponse, WidgetResponse } from "./dashboards";
import { dataSourceTypes, widgetTypes } from "./dashboards";
import { defaultCanvasContent } from "./canvas";
import { coreFixtureIds, dashboardWidgetFixtureIds, dataSourceFixtureIds } from "./fixture-ids";
import {
  activityJobsFixture,
  auditEventsFixture,
  authSessionFixture,
  canvasCommentFixtures,
  canvasFixture,
  canvasSnapshotFixtures,
  canvasTemplateFixtures,
  healthOverviewFixture,
  notificationPreferencesFixture,
  notificationsFixture,
  searchResponseFixture,
  searchSuggestionsFixture,
  workspaceFixtures,
  workspaceMemberFixtures
} from "./fixtures";

const workspaceId = workspaceFixtures[0].id;
const dashboardId = coreFixtureIds.dashboardOperations;

const widgetPositions = [
  { x: 0, y: 0, w: 4, h: 3 },
  { x: 4, y: 0, w: 4, h: 3 },
  { x: 8, y: 0, w: 4, h: 3 },
  { x: 0, y: 3, w: 4, h: 3 },
  { x: 4, y: 3, w: 4, h: 3 },
  { x: 8, y: 3, w: 4, h: 3 },
  { x: 0, y: 6, w: 4, h: 3 }
] as const;

export const dashboardWidgetFixtures: WidgetResponse[] = widgetTypes.map((type, index) => ({
  id: dashboardWidgetFixtureIds[type],
  dashboardId,
  type,
  title: type,
  dataSourceId: type === "MARKDOWN" ? null : dataSourceFixtureIds.CLICKHOUSE,
  query:
    type === "MARKDOWN"
      ? { markdown: "Release notes and operator handoff" }
      : {
          metrics: [{ expression: "count(*)", alias: "events", aggregation: "SUM" }],
          dimensions: [{ field: "event_type", alias: "type" }],
          filters: [{ field: "workspace_id", operator: "EQ", value: "{workspace_id}" }],
          order_by: [{ field: "events", direction: "DESC" }],
          limit: 50
        },
  options: { palette: "livelattice" },
  position: widgetPositions[index],
  createdAt: "2026-06-23T12:00:00Z",
  updatedAt: "2026-06-23T12:15:00Z"
}));

export const dashboardFixtures: DashboardResponse[] = [
  {
    id: dashboardId,
    workspaceId,
    title: "Operations board",
    description: "12-column dashboard fixture covering every backend widget enum.",
    layout: {
      columns: 12,
      gap: 16,
      widgets: dashboardWidgetFixtures.map((widget) => ({
        widgetId: widget.id,
        ...(widget.position as { x: number; y: number; w: number; h: number })
      }))
    },
    timeRange: {
      type: "relative",
      value: "24h"
    },
    autoRefresh: 30,
    isPublic: false,
    createdBy: "keycloak-subject",
    createdAt: "2026-06-23T12:00:00Z",
    updatedAt: "2026-06-23T12:15:00Z"
  }
];

export const dashboardDataFixture: DashboardDataResponse = {
  dashboardId,
  widgets: dashboardWidgetFixtures.map((widget, index) => ({
    widgetId: widget.id,
    error: widget.type === "HEATMAP" ? "ClickHouse timeout" : null,
    data:
      widget.type === "HEATMAP"
        ? null
        : {
            columns: [
              { name: "type", type: "String" },
              { name: "count", type: "UInt64" }
            ],
            rows: [
              [`${widget.type.toLowerCase()}.updated`, 42 + index],
              [`${widget.type.toLowerCase()}.created`, 17 + index]
            ],
            meta: {
              totalRows: 2,
              executedAt: "2026-06-23T12:16:00Z",
              warning: widget.type === "TABLE" ? "Large result truncated after 10,000 rows." : undefined
            }
          }
  }))
};

export const dataSourceFixtures: DataSourceResponse[] = dataSourceTypes.map((type, index) => ({
  id: dataSourceFixtureIds[type],
  workspaceId,
  name: `${type} workspace source`,
  type,
  createdBy: "keycloak-subject",
  createdAt: `2026-06-2${index}T10:00:00Z`,
  updatedAt: "2026-06-23T12:10:00Z"
}));

export const dataSourceCreateFixture: CreateDataSourcePayload = {
  workspaceId,
  name: "ClickHouse canvas events",
  type: "CLICKHOUSE",
  config: {
    host: "clickhouse",
    database: "livelattice",
    password: "fixture-write-only-value"
  }
};

export const realtimeMessageFixtures = {
  join: {
    event: "join:room",
    payload: { canvasId: canvasFixture.id },
    ack: { ok: true, version: canvasFixture.version, memberCount: 2 }
  },
  operation: {
    event: "canvas:op",
    payload: {
      canvasId: canvasFixture.id,
      ops: [{ type: "update", id: "el-gateway", changes: { x: 184 } }],
      version: canvasFixture.version + 1,
      lockVersion: canvasFixture.lockVersion + 1,
      seq: 42,
      origin: "keycloak-subject"
    },
    ack: { ok: true, canvasId: canvasFixture.id, version: canvasFixture.version + 1, lockVersion: canvasFixture.lockVersion + 1, seq: 42 }
  },
  awareness: {
    event: "presence:update",
    payload: {
      subject: "analytics-subject",
      displayName: "Analytics",
      payload: { cursor: { x: 320, y: 240 }, selection: ["el-gateway"], status: "online" }
    },
    ack: { ok: true }
  },
  disconnect: {
    event: "disconnect",
    status: { state: "offline", label: "Realtime disconnected", version: canvasFixture.version, memberCount: 1 }
  }
} as const;

export const frontendContractFixtureMatrix = {
  auth: {
    session: authSessionFixture,
    loginFailure: errorFixtures["401"],
    refreshFailure: errorFixtures["401"],
    logout: { status: 204 }
  },
  workspaces: {
    workspaces: workspaceFixtures,
    members: workspaceMemberFixtures,
    roles: workspaceRoles
  },
  canvas: {
    canvas: canvasFixture,
    emptyContent: defaultCanvasContent,
    comments: canvasCommentFixtures,
    snapshots: canvasSnapshotFixtures,
    templates: canvasTemplateFixtures
  },
  realtime: realtimeMessageFixtures,
  dashboards: {
    dashboards: dashboardFixtures,
    widgets: dashboardWidgetFixtures,
    data: dashboardDataFixture,
    dataSources: dataSourceFixtures,
    createDataSource: dataSourceCreateFixture
  },
  search: {
    response: searchResponseFixture,
    suggestions: searchSuggestionsFixture
  },
  jobs: {
    jobs: activityJobsFixture
  },
  notifications: {
    notifications: notificationsFixture,
    preferences: notificationPreferencesFixture
  },
  audit: {
    events: auditEventsFixture
  },
  health: {
    overview: healthOverviewFixture
  }
} as const;
