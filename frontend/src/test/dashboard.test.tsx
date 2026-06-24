import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../app/routes";
import { authSessionFixture, workspaceFixtures, workspaceMemberFixtures } from "../contracts/fixtures";
import type { AuthTokenResponse } from "../contracts/auth";
import type { DashboardLayout, DashboardWidgetView, QueryResult } from "../contracts/dashboards";
import { normalizeDashboardLayout, normalizeQueryDsl, widgetDataMap, widgetTypes } from "../contracts/dashboards";
import type { WorkspaceMemberResponse, WorkspaceResponse } from "../contracts/workspaces";
import { AuthProvider } from "../features/auth/AuthProvider";
import { DashboardGrid } from "../features/dashboards/DashboardGrid";
import { DataSourceForm } from "../features/dashboards/DataSourceManager";
import { WidgetCard } from "../features/dashboards/WidgetRegistry";
import type { WidgetDataState } from "../features/dashboards/WidgetRegistry";
import { WorkspaceProvider } from "../features/workspaces/WorkspaceProvider";

type MemberFixtureMap = Record<string, WorkspaceMemberResponse[]>;

const queryResult: QueryResult = {
  columns: [
    { name: "type", type: "String" },
    { name: "count", type: "UInt64" }
  ],
  rows: [
    ["canvas.updated", 42],
    ["comment.created", 17]
  ],
  meta: {
    totalRows: 2,
    executedAt: "2026-06-23T12:00:00Z"
  }
};

beforeEach(() => {
  window.sessionStorage.clear();
  vi.restoreAllMocks();
  Object.defineProperty(HTMLElement.prototype, "setPointerCapture", { configurable: true, value: vi.fn() });
  Object.defineProperty(HTMLElement.prototype, "releasePointerCapture", { configurable: true, value: vi.fn() });
});

describe("dashboard contracts", () => {
  it("rejects layouts that do not preserve 12 columns", () => {
    expect(() => normalizeDashboardLayout({ columns: 10, gap: 16, widgets: [] })).toThrow(/columns: 12/i);
  });

  it("preserves query DSL metrics dimensions filters order_by and limit shape", () => {
    const query = normalizeQueryDsl({
      metrics: [{ expression: "count(*)", alias: "events", aggregation: "SUM" }],
      dimensions: [{ field: "event_type", alias: "type" }],
      filters: [{ field: "workspace_id", operator: "EQ", value: "{workspace_id}" }],
      order_by: [{ field: "events", direction: "DESC" }],
      limit: 50
    });

    expect(query).toMatchObject({
      metrics: [{ expression: "count(*)", alias: "events", aggregation: "SUM" }],
      dimensions: [{ field: "event_type", alias: "type" }],
      filters: [{ field: "workspace_id", operator: "EQ", value: "{workspace_id}" }],
      order_by: [{ field: "events", direction: "DESC" }],
      limit: 50
    });
  });

  it("maps widget data warnings and empty rows", () => {
    const mapped = widgetDataMap({
      dashboardId: "dash-1",
      widgets: [
        {
          widgetId: "widget-1",
          error: null,
          data: {
            columns: [],
            rows: [],
            meta: {
              totalRows: 0,
              executedAt: "2026-06-23T12:00:00Z",
              warning: "Large result truncated after 10,000 rows."
            }
          }
        }
      ]
    });

    expect(mapped.get("widget-1")?.result?.rows).toEqual([]);
    expect(mapped.get("widget-1")?.result?.meta.warning).toMatch(/truncated/i);
  });
});

describe("dashboard widgets", () => {
  it("renders every backend widget enum through the registry", () => {
    widgetTypes.forEach((type) => {
      const { unmount } = render(<WidgetCard widget={widgetFixture(type)} state={stateFixture()} editable={false} onEdit={vi.fn()} onRefresh={vi.fn()} />);

      expect(screen.getByRole("heading", { name: type })).toBeInTheDocument();

      unmount();
    });
  });

  it("isolates a widget query error in the widget card", () => {
    render(<WidgetCard widget={widgetFixture("TABLE")} state={{ ...stateFixture(), result: null, error: "ClickHouse timeout" }} editable={true} onEdit={vi.fn()} onRefresh={vi.fn()} />);

    expect(screen.getByRole("alert")).toHaveTextContent(/widget query failed/i);
    expect(screen.getByText(/clickhouse timeout/i)).toBeInTheDocument();
  });
});

describe("data source form secrecy", () => {
  it("clears secret inputs after submit", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<DataSourceForm workspaceId="workspace-1" onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/^Name$/i), "Canvas Events");
    await user.type(screen.getByLabelText(/host/i), "clickhouse");
    await user.type(screen.getByLabelText(/password/i), "write-only-secret");
    await user.click(screen.getByRole("button", { name: /create data source/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(screen.getByLabelText(/password/i)).toHaveValue("");
  });
});

describe("dashboard grid editing", () => {
  it("moves a widget with the keyboard without losing unrelated widgets", () => {
    render(<GridHarness />);

    fireEvent.keyDown(screen.getByLabelText(/events grid item/i), { key: "ArrowRight" });

    expect(screen.getByTestId("layout-json")).toHaveTextContent('"widgetId":"events","x":1');
    expect(screen.getByTestId("layout-json")).toHaveTextContent('"widgetId":"latency","x":8');
  });

  it("resizes a widget with the pointer handle without losing unrelated widgets", () => {
    render(<GridHarness />);

    const resizeHandle = screen.getByRole("button", { name: /resize events/i });
    fireEvent.pointerDown(resizeHandle, { clientX: 0, clientY: 0, pointerId: 1 });
    fireEvent.pointerMove(resizeHandle, { clientX: 80, clientY: 0, pointerId: 1 });
    fireEvent.pointerUp(resizeHandle, { clientX: 80, clientY: 0, pointerId: 1 });

    expect(screen.getByTestId("layout-json")).toHaveTextContent('"widgetId":"events","x":0,"y":0,"w":5');
    expect(screen.getByTestId("layout-json")).toHaveTextContent('"widgetId":"latency","x":8');
  });
});

describe("dashboard route states", () => {
  it("renders dashboard permission denied on a Core 403", async () => {
    vi.stubGlobal("fetch", gatewayFetch({ dashboardStatus: 403 }));

    render(
      <MemoryRouter initialEntries={["/w/factory-floor/d"]}>
        <AuthProvider initialSession={authSessionFixture as AuthTokenResponse}>
          <WorkspaceProvider>
            <AppRoutes />
          </WorkspaceProvider>
        </AuthProvider>
      </MemoryRouter>
    );

    expect(await screen.findByRole("heading", { name: /permission denied/i })).toBeInTheDocument();
  });
});

function GridHarness() {
  const [layout, setLayout] = useGridLayout();
  const widgets = [widgetFixture("BAR_CHART", "events", "Events"), widgetFixture("LINE_CHART", "latency", "Latency")];
  const states = new Map(widgets.map((widget) => [widget.id, stateFixture()]));

  return (
    <>
      <DashboardGrid layout={layout} widgets={widgets} widgetStates={states} editable={true} onLayoutChange={setLayout} onEditWidget={vi.fn()} onRefreshWidget={vi.fn()} />
      <output data-testid="layout-json">{JSON.stringify(layout.widgets)}</output>
    </>
  );
}

function useGridLayout() {
  return useState<DashboardLayout>({
    columns: 12,
    gap: 16,
    widgets: [
      { widgetId: "events", x: 0, y: 0, w: 4, h: 3 },
      { widgetId: "latency", x: 8, y: 0, w: 4, h: 3 }
    ]
  });
}

function widgetFixture(type: DashboardWidgetView["type"], id: string = type, title: string = type): DashboardWidgetView {
  return {
    id,
    dashboardId: "dashboard-1",
    type,
    title,
    dataSourceId: type === "MARKDOWN" ? null : "data-source-1",
    query: type === "MARKDOWN" ? { markdown: "Notes" } : { metrics: [{ expression: "count(*)", alias: "count" }], order_by: [{ field: "count", direction: "DESC" }], limit: 50 },
    options: {},
    position: { widgetId: id, x: id === "latency" ? 8 : 0, y: 0, w: 4, h: 3 },
    createdAt: "2026-06-23T12:00:00Z",
    updatedAt: "2026-06-23T12:00:00Z"
  };
}

function stateFixture(): WidgetDataState {
  return {
    result: queryResult,
    loading: false,
    stale: false,
    error: null,
    cacheKey: ["workspace", "workspace-1", "dashboard", "dashboard-1", "widget"]
  };
}

function gatewayFetch({ workspaces = workspaceFixtures as WorkspaceResponse[], members = workspaceMemberFixtures as MemberFixtureMap, dashboardStatus = 200 }: { workspaces?: WorkspaceResponse[]; members?: MemberFixtureMap; dashboardStatus?: number } = {}) {
  return vi.fn<typeof fetch>(async (input) => {
    const path = String(input);

    if (path === "/api/core/workspaces") {
      return jsonResponse(workspaces);
    }

    const memberMatch = path.match(/^\/api\/core\/workspaces\/([^/]+)\/members$/);

    if (memberMatch) {
      return jsonResponse(members[memberMatch[1]] ?? []);
    }

    if (path.startsWith("/api/core/dashboards")) {
      return dashboardStatus === 403 ? jsonResponse({ error: "forbidden", message: "Dashboard access denied" }, 403) : jsonResponse([]);
    }

    if (path.startsWith("/api/core/data-sources")) {
      return jsonResponse([]);
    }

    return jsonResponse({ error: "not_found", message: "Not found" }, 404);
  });
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json"
    }
  });
}
