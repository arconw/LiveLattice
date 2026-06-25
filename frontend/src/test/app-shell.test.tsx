import { readFileSync } from "node:fs";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../app/routes";
import { authSessionFixture, canvasFixture, workspaceFixtures, workspaceMemberFixtures } from "../contracts/fixtures";
import { coreFixtureIds } from "../contracts/fixture-ids";
import type { AuthTokenResponse } from "../contracts/auth";
import type { RawCanvasResponse } from "../contracts/canvas";
import type { WorkspaceMemberResponse, WorkspaceResponse } from "../contracts/workspaces";
import { AuthProvider } from "../features/auth/AuthProvider";
import { WorkspaceProvider } from "../features/workspaces/WorkspaceProvider";

type MemberFixtureMap = Record<string, WorkspaceMemberResponse[]>;
type GatewayFetchOptions = {
  workspaces?: WorkspaceResponse[];
  members?: MemberFixtureMap;
  createStatus?: number;
  canvases?: RawCanvasResponse[];
};

function renderAuthenticatedApp(initialEntry = "/w/factory-floor", fetchMock = gatewayFetch()) {
  vi.stubGlobal("fetch", fetchMock);

  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider initialSession={authSessionFixture as AuthTokenResponse}>
        <WorkspaceProvider>
          <AppRoutes />
        </WorkspaceProvider>
      </AuthProvider>
    </MemoryRouter>
  );
}

function renderUnauthenticatedApp(initialEntry = "/w/factory-floor", fetchMock = gatewayFetch()) {
  window.sessionStorage.clear();
  vi.stubGlobal("fetch", fetchMock);

  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <WorkspaceProvider>
          <AppRoutes />
        </WorkspaceProvider>
      </AuthProvider>
    </MemoryRouter>
  );
}

function gatewayFetch({ workspaces = workspaceFixtures as WorkspaceResponse[], members = workspaceMemberFixtures as MemberFixtureMap, createStatus = 201, canvases = [canvasFixture] }: GatewayFetchOptions = {}) {
  return vi.fn<typeof fetch>(async (input, init) => {
    const path = String(input);

    if (path === "/auth/login") {
      return jsonResponse(authSessionFixture);
    }

    if (path === "/api/core/workspaces" && init?.method === "POST") {
      if (createStatus === 422) {
        return jsonResponse({ error: "quota_exceeded", message: "Workspace limit reached for the current tier." }, 422);
      }

      return jsonResponse({ ...workspaces[0], id: "44444444-4444-4444-8444-444444444444", name: "Launch room", slug: "launch-room" }, 201);
    }

    if (path === "/api/core/workspaces") {
      return jsonResponse(workspaces);
    }

    if (path.startsWith("/api/core/canvases?")) {
      return jsonResponse(canvases);
    }

    const memberMatch = path.match(/^\/api\/core\/workspaces\/([^/]+)\/members$/);

    if (memberMatch) {
      return jsonResponse(members[memberMatch[1]] ?? []);
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

describe("app shell auth and workspace flows", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it("opens the command palette, focuses input, closes with Escape, and restores focus", async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp();
    await screen.findByRole("heading", { name: /workspace cockpit/i });

    const trigger = screen.getByRole("button", { name: /open command palette/i });
    await user.click(trigger);

    const input = screen.getByRole("textbox", { name: /command search/i });
    expect(input).toHaveFocus();

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("textbox", { name: /command search/i })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("toggles the primary sidebar between collapsed and expanded modes", async () => {
    const user = userEvent.setup();
    const { container } = renderAuthenticatedApp();
    await screen.findByRole("heading", { name: /workspace cockpit/i });

    const shell = container.querySelector(".app-shell");
    const expandButton = screen.getByRole("button", { name: /expand primary navigation/i });

    expect(shell).toHaveClass("is-sidebar-collapsed");
    expect(expandButton).toHaveAttribute("aria-expanded", "false");

    await user.click(expandButton);

    expect(shell).toHaveClass("is-sidebar-open");
    expect(screen.getByRole("button", { name: /collapse primary navigation/i })).toHaveAttribute("aria-expanded", "true");
    expect(window.localStorage.getItem("livelattice:primary-sidebar")).toBe("open");
  });

  it("updates the lattice inspector when a node is selected", async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp();
    await screen.findByRole("heading", { name: /workspace cockpit/i });

    const cockpit = screen.getByRole("region", { name: /interactive lattice cockpit/i });
    await user.click(within(cockpit).getByRole("button", { name: /dashboard.*throughput board/i }));

    const inspector = screen.getByRole("complementary", { name: /selected object inspector/i });
    expect(within(inspector).getByRole("heading", { name: /dashboard widget/i })).toBeInTheDocument();
    expect(within(inspector).getByText(/QueryExecuted/i)).toBeInTheDocument();
    expect(within(inspector).getByText(/Cache warm/i)).toBeInTheDocument();
  });

  it("does not add the event pulse class when reduced motion is preferred", async () => {
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

    renderAuthenticatedApp();
    await screen.findByRole("heading", { name: /workspace cockpit/i });

    expect(screen.getByTestId("event-pulse")).not.toHaveClass("lattice-pulse");
  });

  it("renders route placeholders with workspace-scoped cache keys", async () => {
    renderAuthenticatedApp("/w/factory-floor/search");

    expect(await screen.findByRole("heading", { name: /workspace search/i })).toBeInTheDocument();
    expect(screen.getByText(/workspace \/ factory-floor \/ search/i)).toBeInTheDocument();
    expect(screen.getByText(/nextSearchAfter/i)).toBeInTheDocument();
  });

  it("redirects unauthenticated users to login and preserves the intended destination", async () => {
    const user = userEvent.setup();
    renderUnauthenticatedApp("/w/factory-floor/search");

    expect(await screen.findByRole("heading", { name: /livelattice/i })).toBeInTheDocument();

    await user.type(screen.getByLabelText(/email/i), "owner@example.com");
    await user.type(screen.getByLabelText(/password/i), "owner123");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("heading", { name: /workspace search/i })).toBeInTheDocument();
  });

  it("updates the route workspace and cache namespace when switching workspaces", async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp();
    await screen.findByRole("heading", { name: /workspace cockpit/i });

    expect(screen.getByTestId("cache-namespace")).toHaveTextContent("cache/factory-floor/0");

    await user.selectOptions(screen.getByLabelText(/workspace switcher/i), "platform-ops");

    await waitFor(() => expect(screen.getByTestId("cache-namespace")).toHaveTextContent("cache/platform-ops/1"));
  });

  it("opens the workspace canvas list from primary navigation without a fixture canvas id", async () => {
    const user = userEvent.setup();
    const fetchMock = gatewayFetch({ canvases: [] });
    renderAuthenticatedApp("/w/factory-floor", fetchMock);
    await screen.findByRole("heading", { name: /workspace cockpit/i });

    await user.click(screen.getByRole("link", { name: /^canvas$/i }));

    expect(await screen.findByRole("heading", { name: /canvas workbench/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /no canvases yet/i })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes(coreFixtureIds.canvasIncidentMap))).toBe(false);
  });

  it("renders readable canvas list open actions on paper surfaces", async () => {
    renderAuthenticatedApp("/w/factory-floor/c");

    expect(await screen.findByRole("heading", { name: /canvas workbench/i })).toBeInTheDocument();

    const latestAction = screen.getByRole("link", { name: /open latest canvas/i });
    const canvasAction = screen.getByRole("link", { name: /^open canvas$/i });

    expect(latestAction).toHaveClass("button", "button-primary");
    expect(canvasAction).toHaveClass("button", "button-secondary");
    expect(latestAction.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(canvasAction.querySelector("svg")).toHaveAttribute("aria-hidden", "true");

    const styles = readFileSync("src/styles.css", "utf8");
    expect(styles).toMatch(/\.canvas-feature-card \.button-primary,\n\.canvas-list-card \.button-secondary \{\n\s+color: var\(--color-blueprint-ink\);/);
    expect(styles).toMatch(/\.canvas-list-card \.button-secondary \{\n\s+background: rgba\(77, 124, 254, 0\.12\);/);
    expect(styles).toContain(".canvas-feature-card .button-primary:hover,\n.canvas-list-card .button-secondary:hover");
    expect(styles).toContain(".canvas-list-card .button-secondary[aria-disabled=\"true\"]");
  });

  it("hides restricted navigation and disables restricted actions for viewers", async () => {
    const viewerMembers: MemberFixtureMap = {
      ...(workspaceMemberFixtures as MemberFixtureMap),
      [workspaceFixtures[0].id]: [
        {
          userId: "keycloak-subject",
          role: "VIEWER",
          joinedAt: "2026-06-23T10:00:00Z"
        }
      ]
    };

    renderAuthenticatedApp("/w/factory-floor", gatewayFetch({ members: viewerMembers }));

    expect(await screen.findByRole("heading", { name: /workspace cockpit/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /audit/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /new canvas/i })).toBeDisabled();
  });

  it("renders permission denied and quota states with actionable copy", async () => {
    const user = userEvent.setup();
    const viewerMembers: MemberFixtureMap = {
      ...(workspaceMemberFixtures as MemberFixtureMap),
      [workspaceFixtures[0].id]: [
        {
          userId: "keycloak-subject",
          role: "VIEWER",
          joinedAt: "2026-06-23T10:00:00Z"
        }
      ]
    };

    renderAuthenticatedApp("/w/factory-floor/jobs", gatewayFetch({ members: viewerMembers, createStatus: 422 }));

    expect(await screen.findByRole("heading", { name: /permission denied/i })).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: /workspaces/i }));
    await user.type(await screen.findByLabelText(/name/i), "Launch room");
    await user.type(screen.getByLabelText(/slug/i), "launch-room");
    await user.click(screen.getByRole("button", { name: /create workspace/i }));

    expect(await screen.findByRole("heading", { name: /quota reached/i })).toBeInTheDocument();
    expect(screen.getByText(/remove unused resources/i)).toBeInTheDocument();
  });
});
