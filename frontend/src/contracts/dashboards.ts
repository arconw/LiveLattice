import { AppError, createWorkspaceCacheKey } from "./api-client";
import type { GatewayClient } from "./api-client";

export const widgetTypes = ["BAR_CHART", "LINE_CHART", "PIE_CHART", "TABLE", "STAT", "HEATMAP", "MARKDOWN"] as const;
export const dataSourceTypes = ["CLICKHOUSE", "POSTGRESQL", "PROMETHEUS", "REST_API", "CSV"] as const;
export const relativeTimeRangeValues = ["24h", "7d", "30d"] as const;

export type WidgetType = (typeof widgetTypes)[number];
export type DataSourceType = (typeof dataSourceTypes)[number];
export type RelativeTimeRangeValue = (typeof relativeTimeRangeValues)[number];

export type DashboardLayoutWidget = {
  widgetId: string;
  x: number;
  y: number;
  w: number;
  h: number;
};

export type DashboardLayout = {
  columns: 12;
  gap: number;
  widgets: DashboardLayoutWidget[];
};

export type DashboardTimeRange = { type: "relative"; value: RelativeTimeRangeValue; start?: null; end?: null } | { type: "absolute"; value?: null; start: string; end: string };

export type QueryColumn = {
  name: string;
  type?: string;
};

export type QueryResult = {
  columns: QueryColumn[];
  rows: unknown[][];
  meta: {
    totalRows: number;
    executedAt: string;
    warning?: string;
  };
};

export type QueryMetric = {
  expression: string;
  alias?: string;
  aggregation?: string;
};

export type QueryDimension = {
  field: string;
  alias?: string;
};

export type QueryFilter = {
  field: string;
  operator: string;
  value: unknown;
};

export type QueryOrder = {
  field: string;
  direction: "ASC" | "DESC";
};

export type QueryDsl = {
  metrics?: QueryMetric[];
  dimensions?: QueryDimension[];
  filters?: QueryFilter[];
  order_by?: QueryOrder[];
  limit?: number;
  markdown?: string;
};

export type DashboardResponse = {
  id: string;
  workspaceId: string;
  title: string;
  description: string | null;
  layout: Record<string, unknown>;
  timeRange: Record<string, unknown> | null;
  autoRefresh: number;
  isPublic: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type WidgetResponse = {
  id: string;
  dashboardId: string;
  type: string;
  title: string;
  dataSourceId: string | null;
  query: Record<string, unknown>;
  options: Record<string, unknown>;
  position: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export type DataSourceResponse = {
  id: string;
  workspaceId: string;
  name: string;
  type: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type DashboardWidgetView = Omit<WidgetResponse, "type" | "position" | "query"> & {
  type: WidgetType;
  position: DashboardLayoutWidget;
  query: QueryDsl;
};

export type DataSourceView = Omit<DataSourceResponse, "type"> & {
  type: DataSourceType;
};

export type DashboardWidgetDataResponse = {
  widgetId: string;
  data: Record<string, unknown> | null;
  error: string | null;
};

export type DashboardDataResponse = {
  dashboardId: string;
  widgets: DashboardWidgetDataResponse[];
};

export type CreateDashboardPayload = {
  workspaceId: string;
  title: string;
  description?: string | null;
  layout: DashboardLayout;
  timeRange?: DashboardTimeRange;
  autoRefresh?: number;
};

export type UpdateDashboardPayload = Partial<{
  title: string;
  description: string | null;
  layout: DashboardLayout;
  timeRange: DashboardTimeRange;
  autoRefresh: number;
  isPublic: boolean;
}>;

export type WidgetPayload = {
  type: WidgetType;
  title: string;
  dataSourceId: string | null;
  query: QueryDsl;
  options: Record<string, unknown>;
  position: DashboardLayoutWidget;
};

export type CreateDataSourcePayload = {
  workspaceId: string;
  name: string;
  type: DataSourceType;
  config: Record<string, unknown>;
};

export type UpdateDataSourcePayload = {
  name?: string;
  config?: Record<string, unknown>;
};

export type WidgetDataView = {
  widgetId: string;
  result: QueryResult | null;
  error: string | null;
};

export function isWidgetType(value: string): value is WidgetType {
  return widgetTypes.includes(value as WidgetType);
}

export function isDataSourceType(value: string): value is DataSourceType {
  return dataSourceTypes.includes(value as DataSourceType);
}

export function defaultDashboardLayout(): DashboardLayout {
  return {
    columns: 12,
    gap: 16,
    widgets: []
  };
}

export function defaultDashboardTimeRange(): DashboardTimeRange {
  return {
    type: "relative",
    value: "24h"
  };
}

export function normalizeDashboardLayout(value: unknown): DashboardLayout {
  if (!isRecord(value)) {
    throw contractError("Dashboard layout must be an object.");
  }

  if (value.columns !== 12) {
    throw contractError("Dashboard layout must preserve columns: 12.");
  }

  const gap = numberOr(value.gap, 16);
  const rawWidgets = Array.isArray(value.widgets) ? value.widgets : [];
  const widgets = rawWidgets.map(normalizeLayoutWidget);

  if (hasLayoutCollision(widgets)) {
    throw contractError("Dashboard layout contains overlapping widgets.");
  }

  return {
    columns: 12,
    gap,
    widgets
  };
}

export function normalizeWidgetResponse(widget: WidgetResponse): DashboardWidgetView {
  if (!isWidgetType(widget.type)) {
    throw contractError(`Unsupported widget type ${widget.type}.`);
  }

  return {
    ...widget,
    type: widget.type,
    position: normalizeLayoutWidget({ ...widget.position, widgetId: widget.id }),
    query: normalizeQueryDsl(widget.query)
  };
}

export function normalizeDataSourceResponse(dataSource: DataSourceResponse): DataSourceView {
  if (!isDataSourceType(dataSource.type)) {
    throw contractError(`Unsupported data source type ${dataSource.type}.`);
  }

  return {
    ...dataSource,
    type: dataSource.type
  };
}

export function normalizeTimeRange(value: unknown): DashboardTimeRange {
  if (!isRecord(value)) {
    return defaultDashboardTimeRange();
  }

  if (value.type === "absolute" && typeof value.start === "string" && typeof value.end === "string") {
    return {
      type: "absolute",
      value: null,
      start: value.start,
      end: value.end
    };
  }

  if (value.type === "relative" && typeof value.value === "string" && relativeTimeRangeValues.includes(value.value as RelativeTimeRangeValue)) {
    return {
      type: "relative",
      value: value.value as RelativeTimeRangeValue
    };
  }

  return defaultDashboardTimeRange();
}

export function normalizeQueryDsl(value: unknown): QueryDsl {
  if (!isRecord(value)) {
    return {};
  }

  return {
    metrics: arrayOfRecords(value.metrics).map((metric) => ({
      expression: stringOr(metric.expression),
      alias: optionalString(metric.alias),
      aggregation: optionalString(metric.aggregation)
    })),
    dimensions: arrayOfRecords(value.dimensions).map((dimension) => ({
      field: stringOr(dimension.field),
      alias: optionalString(dimension.alias)
    })),
    filters: arrayOfRecords(value.filters).map((filter) => ({
      field: stringOr(filter.field),
      operator: stringOr(filter.operator),
      value: filter.value
    })),
    order_by: arrayOfRecords(value.order_by).map((order) => ({
      field: stringOr(order.field),
      direction: order.direction === "ASC" ? "ASC" : "DESC"
    })),
    limit: typeof value.limit === "number" && Number.isFinite(value.limit) ? value.limit : undefined,
    markdown: optionalString(value.markdown)
  };
}

export function normalizeQueryResult(data: unknown): QueryResult {
  if (!isRecord(data)) {
    return emptyQueryResult();
  }

  const columns = Array.isArray(data.columns)
    ? data.columns.flatMap((column) => {
        if (typeof column === "string") {
          return [{ name: column }];
        }

        if (isRecord(column) && typeof column.name === "string") {
          return [
            {
              name: column.name,
              type: typeof column.type === "string" ? column.type : undefined
            }
          ];
        }

        return [];
      })
    : [];

  const rows = Array.isArray(data.rows)
    ? data.rows.map((row) => {
        if (Array.isArray(row)) {
          return row as unknown[];
        }

        return [row];
      })
    : [];

  const meta = isRecord(data.meta) ? data.meta : {};

  return {
    columns,
    rows,
    meta: {
      totalRows: typeof meta.totalRows === "number" && Number.isFinite(meta.totalRows) ? meta.totalRows : rows.length,
      executedAt: typeof meta.executedAt === "string" ? meta.executedAt : "",
      warning: typeof meta.warning === "string" ? meta.warning : undefined
    }
  };
}

export function emptyQueryResult(): QueryResult {
  return {
    columns: [],
    rows: [],
    meta: {
      totalRows: 0,
      executedAt: ""
    }
  };
}

export function widgetDataViews(response: DashboardDataResponse): WidgetDataView[] {
  return response.widgets.map((widget) => ({
    widgetId: widget.widgetId,
    result: widget.data ? normalizeQueryResult(widget.data) : null,
    error: widget.error
  }));
}

export function widgetDataMap(response: DashboardDataResponse): Map<string, WidgetDataView> {
  return new Map(widgetDataViews(response).map((widget) => [widget.widgetId, widget]));
}

export function createDashboardCacheKey(workspaceId: string, dashboardId: string, timeRange: DashboardTimeRange, widgetId?: string) {
  return createWorkspaceCacheKey(workspaceId, "dashboard", dashboardId, widgetId ?? "all-widgets", timeRangeCachePart(timeRange));
}

export function moveLayoutWidget(layout: DashboardLayout, widgetId: string, deltaX: number, deltaY: number): DashboardLayout {
  const widget = layout.widgets.find((candidate) => candidate.widgetId === widgetId);

  if (!widget) {
    return layout;
  }

  return replaceLayoutWidget(layout, {
    ...widget,
    x: widget.x + deltaX,
    y: widget.y + deltaY
  });
}

export function resizeLayoutWidget(layout: DashboardLayout, widgetId: string, deltaW: number, deltaH: number): DashboardLayout {
  const widget = layout.widgets.find((candidate) => candidate.widgetId === widgetId);

  if (!widget) {
    return layout;
  }

  return replaceLayoutWidget(layout, {
    ...widget,
    w: widget.w + deltaW,
    h: widget.h + deltaH
  });
}

export function addLayoutWidget(layout: DashboardLayout, widget: DashboardLayoutWidget): DashboardLayout {
  const nextWidget = clampLayoutWidget(widget);

  if (layout.widgets.some((candidate) => candidate.widgetId === nextWidget.widgetId)) {
    return replaceLayoutWidget(layout, nextWidget);
  }

  const nextWidgets = [...layout.widgets, nextWidget];

  if (hasLayoutCollision(nextWidgets)) {
    return layout;
  }

  return {
    ...layout,
    widgets: nextWidgets
  };
}

export function replaceLayoutWidget(layout: DashboardLayout, widget: DashboardLayoutWidget): DashboardLayout {
  const nextWidget = clampLayoutWidget(widget);
  const nextWidgets = layout.widgets.map((candidate) => (candidate.widgetId === widget.widgetId ? nextWidget : candidate));

  if (hasLayoutCollision(nextWidgets)) {
    return layout;
  }

  return {
    ...layout,
    widgets: nextWidgets
  };
}

export function layoutWidgetsOverlap(first: DashboardLayoutWidget, second: DashboardLayoutWidget) {
  return first.x < second.x + second.w && first.x + first.w > second.x && first.y < second.y + second.h && first.y + first.h > second.y;
}

export function hasLayoutCollision(widgets: DashboardLayoutWidget[]) {
  return widgets.some((widget, index) => widgets.slice(index + 1).some((other) => layoutWidgetsOverlap(widget, other)));
}

export async function listDashboards(client: GatewayClient, workspaceId: string, signal?: AbortSignal) {
  return client.get<DashboardResponse[]>(`/api/core/dashboards?workspaceId=${encodeURIComponent(workspaceId)}`, { signal });
}

export async function createDashboard(client: GatewayClient, payload: CreateDashboardPayload) {
  return client.post<DashboardResponse>("/api/core/dashboards", payload);
}

export async function getDashboard(client: GatewayClient, dashboardId: string, signal?: AbortSignal) {
  return client.get<DashboardResponse>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}`, { signal });
}

export async function updateDashboard(client: GatewayClient, dashboardId: string, payload: UpdateDashboardPayload) {
  return client.patch<DashboardResponse>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}`, payload);
}

export async function deleteDashboard(client: GatewayClient, dashboardId: string) {
  await client.delete<void>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}`);
}

export async function duplicateDashboard(client: GatewayClient, dashboardId: string) {
  return client.post<DashboardResponse>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}/duplicate`);
}

export async function listWidgets(client: GatewayClient, dashboardId: string, signal?: AbortSignal) {
  return client.get<WidgetResponse[]>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}/widgets`, { signal });
}

export async function createWidget(client: GatewayClient, dashboardId: string, payload: WidgetPayload) {
  return client.post<WidgetResponse>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}/widgets`, widgetPayloadForRequest(payload));
}

export async function updateWidget(client: GatewayClient, dashboardId: string, widgetId: string, payload: Partial<WidgetPayload>) {
  return client.patch<WidgetResponse>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}/widgets/${encodeURIComponent(widgetId)}`, widgetPayloadForRequest(payload));
}

export async function deleteWidget(client: GatewayClient, dashboardId: string, widgetId: string) {
  await client.delete<void>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}/widgets/${encodeURIComponent(widgetId)}`);
}

export async function loadDashboardData(client: GatewayClient, dashboardId: string, signal?: AbortSignal) {
  return client.get<DashboardDataResponse>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}/data`, { signal });
}

export async function loadWidgetData(client: GatewayClient, dashboardId: string, widgetId: string, signal?: AbortSignal) {
  return client.get<DashboardDataResponse>(`/api/core/dashboards/${encodeURIComponent(dashboardId)}/widgets/${encodeURIComponent(widgetId)}/data`, { signal });
}

export async function listDataSources(client: GatewayClient, workspaceId: string, signal?: AbortSignal) {
  return client.get<DataSourceResponse[]>(`/api/core/data-sources?workspaceId=${encodeURIComponent(workspaceId)}`, { signal });
}

export async function createDataSource(client: GatewayClient, payload: CreateDataSourcePayload) {
  return client.post<DataSourceResponse>("/api/core/data-sources", payload);
}

export async function updateDataSource(client: GatewayClient, dataSourceId: string, payload: UpdateDataSourcePayload) {
  return client.patch<DataSourceResponse>(`/api/core/data-sources/${encodeURIComponent(dataSourceId)}`, payload);
}

export async function deleteDataSource(client: GatewayClient, dataSourceId: string) {
  await client.delete<void>(`/api/core/data-sources/${encodeURIComponent(dataSourceId)}`);
}

export async function testDataSourceConnection(client: GatewayClient, dataSourceId: string) {
  return client.post<boolean>(`/api/core/data-sources/${encodeURIComponent(dataSourceId)}/test`);
}

function widgetPayloadForRequest(payload: Partial<WidgetPayload>) {
  return {
    ...payload,
    dataSourceId: payload.type === "MARKDOWN" ? null : payload.dataSourceId,
    position: payload.position ? layoutWidgetPosition(payload.position) : undefined
  };
}

function normalizeLayoutWidget(value: unknown): DashboardLayoutWidget {
  if (!isRecord(value)) {
    throw contractError("Dashboard layout widget must be an object.");
  }

  const widgetId = stringOr(value.widgetId);

  if (!widgetId) {
    throw contractError("Dashboard layout widget must include widgetId.");
  }

  return clampLayoutWidget({
    widgetId,
    x: numberOr(value.x, 0),
    y: numberOr(value.y, 0),
    w: numberOr(value.w, 4),
    h: numberOr(value.h, 4)
  });
}

function clampLayoutWidget(widget: DashboardLayoutWidget): DashboardLayoutWidget {
  const width = clamp(Math.round(widget.w), 1, 12);
  const height = clamp(Math.round(widget.h), 1, 24);
  const x = clamp(Math.round(widget.x), 0, 12 - width);
  const y = Math.max(0, Math.round(widget.y));

  return {
    widgetId: widget.widgetId,
    x,
    y,
    w: width,
    h: height
  };
}

function layoutWidgetPosition(widget: DashboardLayoutWidget) {
  return {
    x: widget.x,
    y: widget.y,
    w: widget.w,
    h: widget.h
  };
}

function timeRangeCachePart(timeRange: DashboardTimeRange) {
  if (timeRange.type === "absolute") {
    return `absolute:${timeRange.start}:${timeRange.end}`;
  }

  return `relative:${timeRange.value}`;
}

function arrayOfRecords(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter(isRecord) : [];
}

function stringOr(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function optionalString(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function numberOr(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function contractError(message: string) {
  return new AppError({
    status: 0,
    code: "DASHBOARD_CONTRACT_INVALID",
    message,
    retryable: false
  });
}
