import { describe, expect, it } from "vitest";
import { canvasElementTypes, normalizeCanvasContent, toPersistedCanvasContent } from "./canvas";
import { dataSourceTypes, normalizeDashboardLayout, normalizeDataSourceResponse, normalizeWidgetResponse, widgetDataMap, widgetTypes } from "./dashboards";
import { jobStatuses } from "./jobs";
import { digestFrequencies } from "./notifications";
import { workspaceRoles } from "./auth";
import { canvasListHref, coreFixtureIds, dashboardWidgetFixtureIds, dataSourceFixtureIds } from "./fixture-ids";
import {
  dashboardDataFixture,
  dashboardFixtures,
  dashboardWidgetFixtures,
  dataSourceCreateFixture,
  dataSourceFixtures,
  frontendContractFixtureMatrix,
  realtimeMessageFixtures
} from "./quality-fixtures";

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

describe("Stage 15F contract fixtures", () => {
  it("covers every required frontend contract domain", () => {
    expect(Object.keys(frontendContractFixtureMatrix).sort()).toEqual(["audit", "auth", "canvas", "dashboards", "health", "jobs", "notifications", "realtime", "search", "workspaces"]);
  });

  it("covers auth, refresh failure, workspace roles, and RBAC member fixtures", () => {
    const roles = Object.values(frontendContractFixtureMatrix.workspaces.members).flatMap((members) => members.map((member) => member.role.toLowerCase()));

    expect(frontendContractFixtureMatrix.auth.session.user.subject).toBe("keycloak-subject");
    expect(frontendContractFixtureMatrix.auth.refreshFailure.code).toBe("AUTHENTICATION_REQUIRED");
    expect(new Set(roles)).toEqual(new Set(workspaceRoles));
  });

  it("preserves the canvas JSONB shape, all element types, comments, snapshots, templates, and empty canvas state", () => {
    const content = normalizeCanvasContent(frontendContractFixtureMatrix.canvas.canvas.content);
    const elementTypes = new Set(content.elements.map((element) => element.type));
    const persisted = toPersistedCanvasContent(content);

    expect(elementTypes).toEqual(new Set(canvasElementTypes));
    expect(persisted.elements[0]).toEqual(expect.objectContaining({ id: "el-gateway", groupId: null, locked: false }));
    expect(frontendContractFixtureMatrix.canvas.emptyContent.elements).toHaveLength(0);
    expect(frontendContractFixtureMatrix.canvas.comments.some((comment) => comment.parentId)).toBe(true);
    expect(frontendContractFixtureMatrix.canvas.comments.some((comment) => comment.targetElementId === "el-deleted")).toBe(true);
    expect(frontendContractFixtureMatrix.canvas.snapshots.length).toBeGreaterThan(0);
    expect(frontendContractFixtureMatrix.canvas.templates.length).toBeGreaterThan(0);
  });

  it("covers realtime join, operation, acknowledgement, awareness, and disconnect contracts", () => {
    expect(realtimeMessageFixtures.join).toMatchObject({ event: "join:room", payload: { canvasId: coreFixtureIds.canvasIncidentMap } });
    expect(realtimeMessageFixtures.operation.payload).toMatchObject({ version: 129, lockVersion: 5, seq: 42 });
    expect(realtimeMessageFixtures.operation.ack).toMatchObject({ ok: true, canvasId: coreFixtureIds.canvasIncidentMap });
    expect(realtimeMessageFixtures.awareness.payload.payload.selection).toEqual(["el-gateway"]);
    expect(realtimeMessageFixtures.disconnect.status.state).toBe("offline");
  });

  it("covers dashboards, widget enums, 12-column layout, data results, and write-only data source secrets", () => {
    const layout = normalizeDashboardLayout(dashboardFixtures[0].layout);
    const widgets = dashboardWidgetFixtures.map(normalizeWidgetResponse);
    const dataSources = dataSourceFixtures.map(normalizeDataSourceResponse);
    const data = widgetDataMap(dashboardDataFixture);

    expect(layout.columns).toBe(12);
    expect(new Set(widgets.map((widget) => widget.type))).toEqual(new Set(widgetTypes));
    expect(new Set(dataSources.map((dataSource) => dataSource.type))).toEqual(new Set(dataSourceTypes));
    expect(data.get(dashboardWidgetFixtureIds.TABLE)?.result?.meta.warning).toMatch(/truncated/i);
    expect(data.get(dashboardWidgetFixtureIds.HEATMAP)?.error).toMatch(/timeout/i);
    expect(dataSourceCreateFixture.config).toHaveProperty("password");
    dataSourceFixtures.forEach((dataSource) => {
      expect(dataSource).not.toHaveProperty("config");
      expect(JSON.stringify(dataSource)).not.toMatch(/password|token|secret/i);
    });
  });

  it("covers search, jobs, notifications, audit, and health activity contracts", () => {
    const jobStateSet = new Set(frontendContractFixtureMatrix.jobs.jobs.map((job) => job.status));

    expect(frontendContractFixtureMatrix.search.response.nextSearchAfter).toBeTruthy();
    expect(frontendContractFixtureMatrix.search.response.facets.type.canvas).toBeGreaterThan(0);
    expect(jobStatuses.every((status) => jobStateSet.has(status) || status === "cancelled")).toBe(true);
    expect(frontendContractFixtureMatrix.jobs.jobs.some((job) => job.downloadExpiresAt && Date.parse(job.downloadExpiresAt) < Date.parse("2026-06-23T12:00:00Z"))).toBe(true);
    expect(digestFrequencies).toContain(frontendContractFixtureMatrix.notifications.preferences.emailDigest);
    expect(frontendContractFixtureMatrix.notifications.preferences.webhooks[0]).not.toHaveProperty("secret");
    expect(frontendContractFixtureMatrix.audit.events[0]).not.toHaveProperty("deleteUrl");
    expect(frontendContractFixtureMatrix.health.overview.services.some((service) => service.status === "DOWN")).toBe(true);
  });

  it("uses UUID-shaped IDs for Core-backed resource contracts and navigable targets", () => {
    const canvas = frontendContractFixtureMatrix.canvas.canvas;
    const comments = frontendContractFixtureMatrix.canvas.comments;
    const snapshots = frontendContractFixtureMatrix.canvas.snapshots;
    const templates = frontendContractFixtureMatrix.canvas.templates;
    const dashboards = frontendContractFixtureMatrix.dashboards.dashboards;
    const widgets = frontendContractFixtureMatrix.dashboards.widgets;
    const dataSources = frontendContractFixtureMatrix.dashboards.dataSources;
    const searchResults = frontendContractFixtureMatrix.search.response.results;
    const notifications = frontendContractFixtureMatrix.notifications.notifications;
    const auditEvents = frontendContractFixtureMatrix.audit.events;

    expectUuid(canvas.id);
    comments.forEach((comment) => {
      expectUuid(comment.id);
      expect(comment.canvasId).toBe(canvas.id);
      if (comment.parentId) {
        expectUuid(comment.parentId);
      }
    });
    snapshots.forEach((snapshot) => {
      expectUuid(snapshot.id);
      expect(snapshot.canvasId).toBe(canvas.id);
    });
    templates.forEach((template) => expectUuid(template.id));
    dashboards.forEach((dashboard) => expectUuid(dashboard.id));
    widgets.forEach((widget) => {
      expectUuid(widget.id);
      expect(widget.dashboardId).toBe(coreFixtureIds.dashboardOperations);
      if (widget.dataSourceId) {
        expectUuid(widget.dataSourceId);
      }
    });
    dataSources.forEach((dataSource) => expectUuid(dataSource.id));
    expect(new Set(dataSources.map((dataSource) => dataSource.id))).toEqual(new Set(Object.values(dataSourceFixtureIds)));
    frontendContractFixtureMatrix.dashboards.data.widgets.forEach((widgetData) => expectUuid(widgetData.widgetId));
    searchResults.filter((result) => result.type === "canvas" || result.type === "comment" || result.type === "dashboard").forEach((result) => {
      expectUuid(result.id);
      expect(result.targetUrl).toContain(result.type === "dashboard" ? coreFixtureIds.dashboardOperations : canvasListHref("factory-floor"));
    });
    notifications.filter((notification) => notification.target.type === "canvas").forEach((notification) => {
      expectUuid(notification.target.id);
      expect(notification.target.href).toBe(canvasListHref("factory-floor"));
    });
    auditEvents.filter((event) => event.targetType === "canvas" || event.targetType === "workspace").forEach((event) => expectUuid(event.targetId));
  });
});

function expectUuid(value: string) {
  expect(value).toMatch(uuidPattern);
}
