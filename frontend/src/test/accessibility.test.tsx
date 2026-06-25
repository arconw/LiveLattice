import axe from "axe-core";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../app/routes";
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
} from "../contracts/fixtures";
import { canvasListHref, coreFixtureIds, primaryCanvasHref } from "../contracts/fixture-ids";
import type { AuthTokenResponse } from "../contracts/auth";
import { dashboardDataFixture, dashboardFixtures, dashboardWidgetFixtures, dataSourceFixtures } from "../contracts/quality-fixtures";
import type { CanvasContent } from "../contracts/canvas";
import type { WorkspaceMemberResponse, WorkspaceResponse } from "../contracts/workspaces";
import { AuthProvider } from "../features/auth/AuthProvider";
import { WorkspaceProvider } from "../features/workspaces/WorkspaceProvider";

type MemberFixtureMap = Record<string, WorkspaceMemberResponse[]>;

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  window.sessionStorage.clear();
});

beforeEach(() => {
  setViewport(1280, 900);
});

const canvasRoute = primaryCanvasHref("factory-floor");
const canvasListRoute = canvasListHref("factory-floor");
const dashboardRoute = `/w/factory-floor/d/${coreFixtureIds.dashboardOperations}`;
const canvasApiPath = `/api/core/canvases/${coreFixtureIds.canvasIncidentMap}`;
const dashboardApiPath = `/api/core/dashboards/${coreFixtureIds.dashboardOperations}`;

describe("frontend accessibility gate", () => {
  it("has no critical automated accessibility violations on the login form and reaches the primary action by keyboard", async () => {
    const user = userEvent.setup();
    const { container } = renderApp("/login", false);

    expect(await screen.findByRole("heading", { name: /livelattice/i })).toBeInTheDocument();

    await user.tab();
    expect(screen.getByLabelText(/email/i)).toHaveFocus();
    await user.tab();
    expect(screen.getByLabelText(/password/i)).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: /sign in/i })).toHaveFocus();

    await expectNoCriticalA11yViolations(container);
  });

  it.each([
    ["/w/factory-floor", /workspace cockpit/i],
    [canvasListRoute, /canvas workbench/i],
    [canvasRoute, /incident response lattice/i],
    ["/w/factory-floor/d", /analytics dashboards/i],
    [dashboardRoute, /operations board/i],
    ["/w/factory-floor/search?q=export", /workspace search/i],
    ["/w/factory-floor/jobs", /job activity/i],
    ["/w/factory-floor/notifications", /^notifications$/i],
    ["/w/factory-floor/audit", /audit trail/i]
  ])("has no critical automated accessibility violations for %s", async (route, heading) => {
    const { container } = renderApp(route);

    expect(await screen.findByRole("heading", { name: heading })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryAllByText(/loading/i).length).toBeLessThan(3));

    await expectNoCriticalA11yViolations(container);
  });

  it("keeps command palette focus trapped and restores the trigger", async () => {
    const user = userEvent.setup();
    renderApp("/w/factory-floor");

    expect(await screen.findByRole("heading", { name: /workspace cockpit/i })).toBeInTheDocument();
    const trigger = screen.getByRole("button", { name: /open command palette/i });
    trigger.focus();

    await user.keyboard("{Enter}");
    expect(screen.getByRole("dialog", { name: /command palette/i })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /command search/i })).toHaveFocus();

    await user.keyboard("{Shift>}{Tab}{/Shift}");
    expect(screen.getByRole("option", { name: /review audit trail/i })).toHaveFocus();
    await user.keyboard("{Tab}");
    expect(screen.getByRole("textbox", { name: /command search/i })).toHaveFocus();
    await user.keyboard("{Escape}");

    expect(trigger).toHaveFocus();
  });

  it("opens the primary canvas route by keyboard and reaches the editor toolbar", async () => {
    const user = userEvent.setup();
    renderApp("/w/factory-floor");

    expect(await screen.findByRole("heading", { name: /workspace cockpit/i })).toBeInTheDocument();
    const canvasLink = screen.getByRole("link", { name: /^canvas$/i });
    expect(canvasLink).toHaveAttribute("href", canvasListRoute);

    canvasLink.focus();
    await user.keyboard("{Enter}");

    expect(await screen.findByRole("heading", { name: /canvas workbench/i })).toBeInTheDocument();
    const openLatestCanvas = screen.getByRole("link", { name: /open latest canvas/i });
    openLatestCanvas.focus();
    await user.keyboard("{Enter}");

    expect(await screen.findByRole("heading", { name: /incident response lattice/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /rectangle/i })).toBeEnabled();
    expect(screen.getByRole("complementary", { name: /canvas workbench panels/i })).toBeInTheDocument();
  });

  it("keeps canvas toolbar and inspector paths keyboard-operable", async () => {
    const user = userEvent.setup();
    renderApp(canvasRoute);

    expect(await screen.findByRole("heading", { name: /incident response lattice/i })).toBeInTheDocument();

    const elementListButton = screen.getByRole("button", { name: /REST Gateway editable at x 160 y 140/i });
    elementListButton.focus();
    await user.keyboard("{Enter}");

    const inspector = screen.getByRole("complementary", { name: /canvas workbench panels/i });
    expect(within(inspector).getByRole("heading", { name: /rectangle/i })).toBeInTheDocument();

    const xField = within(inspector).getByLabelText(/^x$/i);
    xField.focus();
    await user.clear(xField);
    await user.type(xField, "168");
    await user.tab();

    await waitFor(() => expect(xField).toHaveValue(168));
    within(inspector).getByRole("button", { name: /clear/i }).focus();
    await user.keyboard("{Enter}");
    await waitFor(() => expect(screen.getByRole("button", { name: /REST Gateway editable at x 168 y 140/i })).toBeInTheDocument());

    const rectangleTool = screen.getByRole("button", { name: /rectangle/i });
    rectangleTool.focus();
    await user.keyboard("{Enter}");
    await screen.findByText(/version 129/i);
  });

  it("exposes comment pin previews to keyboard and assistive technology", async () => {
    renderApp(canvasRoute);

    expect(await screen.findByRole("heading", { name: /incident response lattice/i })).toBeInTheDocument();
    const commentPin = screen.getByRole("button", { name: /open comment on general canvas: open. snapshot before changing the database branch/i });

    commentPin.focus();

    expect(commentPin).toHaveFocus();
    expect(commentPin).toHaveAccessibleDescription(/Snapshot before changing the database branch/i);
    expect(within(commentPin).getByRole("tooltip")).toHaveTextContent(/General canvas/i);
  });

  it("traps and restores focus for dialogs opened from the canvas snapshot flow", async () => {
    const user = userEvent.setup();
    renderApp(canvasRoute);

    expect(await screen.findByRole("heading", { name: /incident response lattice/i })).toBeInTheDocument();
    const snapshotsTab = screen.getByRole("tab", { name: /snapshots/i });
    snapshotsTab.focus();
    await user.keyboard("{Enter}");

    const restoreButton = screen.getAllByRole("button", { name: /restore/i })[0];
    restoreButton.focus();
    await user.keyboard("{Enter}");

    expect(screen.getByRole("dialog", { name: /restore snapshot/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /close/i })).toHaveFocus();

    await user.keyboard("{Shift>}{Tab}{/Shift}");
    expect(screen.getByRole("button", { name: /confirm restore/i })).toHaveFocus();
    await user.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("dialog", { name: /restore snapshot/i })).not.toBeInTheDocument());
    expect(restoreButton).toHaveFocus();
  });
});

describe("frontend responsive and reduced-motion smoke", () => {
  it.each([
    [375, 812, "/w/factory-floor", /workspace cockpit/i],
    [390, 844, canvasRoute, /incident response lattice/i],
    [768, 1024, dashboardRoute, /operations board/i],
    [414, 896, "/w/factory-floor/search?q=export", /workspace search/i],
    [430, 932, "/w/factory-floor/jobs", /job activity/i],
    [430, 932, "/w/factory-floor/notifications", /^notifications$/i],
    [430, 932, "/w/factory-floor/audit", /audit trail/i]
  ])("renders %s x %s responsive smoke route %s", async (width, height, route, heading) => {
    setViewport(width, height);
    const { container } = renderApp(route);

    expect(await screen.findByRole("heading", { name: heading })).toBeInTheDocument();
    expect(container.querySelector(".app-shell")).toBeInTheDocument();
  });

  it("disables the lattice event pulse when reduced motion is preferred", async () => {
    vi.stubGlobal(
      "matchMedia",
      vi.fn().mockImplementation((query: string) => ({
        matches: query.includes("prefers-reduced-motion"),
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn()
      }))
    );

    renderApp("/w/factory-floor");

    expect(await screen.findByRole("heading", { name: /workspace cockpit/i })).toBeInTheDocument();
    expect(screen.getByTestId("event-pulse")).not.toHaveClass("lattice-pulse");
  });
});

function renderApp(initialEntry: string, authenticated = true) {
  vi.stubGlobal("fetch", gatewayFetch());

  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider initialSession={authenticated ? (authSessionFixture as AuthTokenResponse) : undefined}>
        <WorkspaceProvider>
          <AppRoutes />
        </WorkspaceProvider>
      </AuthProvider>
    </MemoryRouter>
  );
}

function gatewayFetch({ workspaces = workspaceFixtures as WorkspaceResponse[], members = workspaceMemberFixtures as MemberFixtureMap }: { workspaces?: WorkspaceResponse[]; members?: MemberFixtureMap } = {}) {
  return vi.fn<typeof fetch>(async (input, init) => {
    const path = String(input);

    if (path === "/auth/login") {
      return jsonResponse(authSessionFixture);
    }

    if (path === "/api/core/workspaces") {
      return jsonResponse(workspaces);
    }

    const memberMatch = path.match(/^\/api\/core\/workspaces\/([^/]+)\/members$/);
    if (memberMatch) {
      return jsonResponse(members[memberMatch[1]] ?? []);
    }

    if (path === "/ready") {
      return jsonResponse({
        status: healthOverviewFixture.status,
        checks: {
          gateway: { status: "UP", details: { latencyMs: 3 } },
          search: { status: "DEGRADED", details: { latencyMs: 310 } },
          "background-jobs": { status: "DOWN", details: {} }
        }
      });
    }

    if (path.startsWith("/api/core/canvases?")) {
      return jsonResponse([canvasFixture]);
    }

    if (path === canvasApiPath && init?.method === "PATCH") {
      const body = JSON.parse(String(init.body ?? "{}")) as { content?: CanvasContent };
      return jsonResponse({
        ...canvasFixture,
        content: body.content ?? canvasFixture.content,
        version: canvasFixture.version + 1,
        lockVersion: canvasFixture.lockVersion + 1,
        updatedAt: "2026-06-23T18:45:00Z"
      });
    }

    if (path === canvasApiPath) {
      return jsonResponse(canvasFixture);
    }

    if (path === `${canvasApiPath}/comments?limit=100`) {
      return jsonResponse(canvasCommentFixtures);
    }

    if (path === `${canvasApiPath}/history`) {
      return jsonResponse(canvasSnapshotFixtures);
    }

    if (path.startsWith("/api/core/templates?")) {
      return jsonResponse(canvasTemplateFixtures);
    }

    if (path === `${canvasApiPath}/snapshot` && init?.method === "POST") {
      return jsonResponse({ ...canvasSnapshotFixtures[0], id: coreFixtureIds.snapshot129, version: 129 }, 201);
    }

    if (path === `${dashboardApiPath}/data` || path.match(new RegExp(`^/api/core/dashboards/${coreFixtureIds.dashboardOperations}/widgets/[^/]+/data$`))) {
      return jsonResponse(dashboardDataFixture);
    }

    if (path === `${dashboardApiPath}/widgets`) {
      return jsonResponse(dashboardWidgetFixtures);
    }

    if (path === dashboardApiPath) {
      return jsonResponse(dashboardFixtures[0]);
    }

    if (path.startsWith("/api/core/dashboards?")) {
      return jsonResponse(dashboardFixtures);
    }

    if (path.startsWith("/api/core/data-sources?")) {
      return jsonResponse(dataSourceFixtures);
    }

    if (path.startsWith("/api/notifications/notifications/unread-count")) {
      return jsonResponse({ unread: notificationsFixture.filter((notification) => notification.readAt === null).length });
    }

    if (path.startsWith("/api/search/search/suggest")) {
      return jsonResponse({ suggestions: searchSuggestionsFixture });
    }

    if (path.startsWith("/api/search/search")) {
      return jsonResponse(searchResponseFixture);
    }

    if (path.startsWith("/api/import-export/export/jobs")) {
      return jsonResponse({ jobs: activityJobsFixture.filter((job) => job.domain !== "background"), total: 3 });
    }

    if (path.startsWith("/api/background-jobs/jobs")) {
      return jsonResponse({ jobs: activityJobsFixture.filter((job) => job.domain === "background"), total: 2 });
    }

    if (path.startsWith("/api/notifications/notifications")) {
      return jsonResponse({ notifications: notificationsFixture, total: notificationsFixture.length, unread: 2 });
    }

    if (path === "/api/notifications/notification-preferences") {
      return jsonResponse(notificationPreferencesFixture);
    }

    if (path.startsWith("/api/audit-log/audit-log")) {
      return jsonResponse({ events: auditEventsFixture, total: auditEventsFixture.length });
    }

    return jsonResponse({ error: "not_found", message: "Not found" }, 404);
  });
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json"
    }
  });
}

async function expectNoCriticalA11yViolations(container: HTMLElement) {
  const results = await axe.run(container, { rules: { "color-contrast": { enabled: false } } });
  const critical = results.violations.filter((violation) => violation.impact === "critical");

  expect(critical.map((violation) => ({ id: violation.id, nodes: violation.nodes.map((node) => node.target.join(" ")) }))).toEqual([]);
}

function setViewport(width: number, height: number) {
  Object.defineProperty(window, "innerWidth", { configurable: true, writable: true, value: width });
  Object.defineProperty(window, "innerHeight", { configurable: true, writable: true, value: height });
  window.dispatchEvent(new Event("resize"));
}
