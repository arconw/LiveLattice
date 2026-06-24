import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../app/routes";
import {
  activityJobsFixture,
  auditEventsFixture,
  authSessionFixture,
  healthOverviewFixture,
  notificationPreferencesFixture,
  notificationsFixture,
  searchResponseFixture,
  searchSuggestionsFixture,
  workspaceFixtures,
  workspaceMemberFixtures
} from "../contracts/fixtures";
import { coreFixtureIds } from "../contracts/fixture-ids";
import type { AuthTokenResponse } from "../contracts/auth";
import type { WorkspaceMemberResponse, WorkspaceResponse } from "../contracts/workspaces";
import { AuthProvider } from "../features/auth/AuthProvider";
import { WorkspaceProvider } from "../features/workspaces/WorkspaceProvider";

type MemberFixtureMap = Record<string, WorkspaceMemberResponse[]>;

function renderAuthenticatedApp(initialEntry: string, fetchMock = activityFetch()) {
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

function activityFetch({
  failMarkRead = false,
  failPreferenceSave = false,
  failDownloadRefresh = false,
  workspaces = workspaceFixtures as WorkspaceResponse[],
  members = workspaceMemberFixtures as MemberFixtureMap
}: { failMarkRead?: boolean; failPreferenceSave?: boolean; failDownloadRefresh?: boolean; workspaces?: WorkspaceResponse[]; members?: MemberFixtureMap } = {}) {
  return vi.fn<typeof fetch>(async (input, init) => {
    const path = String(input);

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

    if (path.startsWith("/api/notifications/notifications/unread-count")) {
      return jsonResponse({ unread: notificationsFixture.filter((notification) => notification.readAt === null).length });
    }

    if (path.startsWith("/api/search/search/suggest")) {
      return jsonResponse({ suggestions: searchSuggestionsFixture });
    }

    if (path.startsWith("/api/search/search")) {
      return jsonResponse(searchResponseFixture);
    }

    if (path.startsWith("/api/import-export/export/jobs/job-expired-export/download")) {
      if (failDownloadRefresh) {
        return jsonResponse({ code: "DOWNLOAD_REFRESH_FAILED", message: "Refresh failed" }, 500);
      }

      return jsonResponse({ downloadUrl: "https://downloads.example.test/refreshed", downloadExpiresAt: "2026-06-24T12:00:00Z" });
    }

    if (path.startsWith("/api/import-export/export/jobs")) {
      return jsonResponse({ jobs: activityJobsFixture.filter((job) => job.domain !== "background"), total: 3 });
    }

    if (path.startsWith("/api/background-jobs/jobs")) {
      return jsonResponse({ jobs: activityJobsFixture.filter((job) => job.domain === "background"), total: 2 });
    }

    if (path.startsWith("/api/notifications/notifications/") && path.endsWith("/read")) {
      return failMarkRead ? jsonResponse({ code: "READ_FAILED", message: "Read failed" }, 500) : jsonResponse({}, 204);
    }

    if (path === "/api/notifications/notifications/read-all") {
      return jsonResponse({}, 204);
    }

    if (path.startsWith("/api/notifications/notifications")) {
      return jsonResponse({ notifications: notificationsFixture, total: notificationsFixture.length, unread: 2 });
    }

    if (path === "/api/notifications/notification-preferences" && init?.method === "PATCH") {
      if (failPreferenceSave) {
        return jsonResponse({ code: "PREFERENCES_FAILED", message: "Preferences failed" }, 500);
      }

      return jsonResponse(notificationPreferencesFixture);
    }

    if (path === "/api/notifications/notification-preferences") {
      return jsonResponse(notificationPreferencesFixture);
    }

    if (path === "/api/notifications/notification-preferences/webhooks" && init?.method === "POST") {
      return jsonResponse({
        id: "webhook-new",
        url: "https://hooks.example.test/new",
        events: ["canvas.export.complete"],
        createdAt: "2026-06-23T12:40:00Z",
        lastDeliveryStatus: "pending"
      });
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

describe("activity routes", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("renders search highlights, facets, and the next search_after token", async () => {
    renderAuthenticatedApp("/w/factory-floor/search?q=export");

    expect(await screen.findByRole("heading", { name: /workspace search/i })).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: /warehouse flow/i })).toBeInTheDocument();
    expect(screen.getByText("nextSearchAfter")).toBeInTheDocument();
    expect(screen.getByText(`2026-06-23T11:58:00Z|${coreFixtureIds.dashboardOperations}`)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /canvas/i })).toBeInTheDocument();
    expect(screen.getAllByText("export").length).toBeGreaterThan(0);
  });

  it("renders job states and validates rejected uploads", async () => {
    renderAuthenticatedApp("/w/factory-floor/jobs");

    expect(await screen.findByRole("heading", { name: /job activity/i })).toBeInTheDocument();
    expect(await screen.findByText(/Download URL expired/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /refresh link/i })).toBeInTheDocument();
    expect(screen.getAllByText(/OpenSearch bulk request timed out/i).length).toBeGreaterThan(0);

    const oversizedSvg = new File(["<svg />"], "oversized.svg", { type: "image/svg+xml" });
    Object.defineProperty(oversizedSvg, "size", { value: 101 * 1024 * 1024 });
    fireEvent.change(screen.getByLabelText(/import file/i), { target: { files: [oversizedSvg] } });

    expect(await screen.findByText(/larger than the 100 MB/i)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /service readiness/i })).toBeInTheDocument();
  });

  it("keeps expired download links retryable when refresh fails", async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp("/w/factory-floor/jobs", activityFetch({ failDownloadRefresh: true }));

    expect(await screen.findByText(/Download URL expired/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /refresh link/i }));

    expect(await screen.findByText(/The expired link was kept so you can retry/i)).toBeInTheDocument();
    expect(screen.getByText(/Download URL expired/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /refresh link/i })).toBeInTheDocument();
  });

  it("rolls notification read state back on failure and keeps webhook secrets write-only", async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp("/w/factory-floor/notifications", activityFetch({ failMarkRead: true }));

    expect(await screen.findByRole("heading", { name: /^notifications$/i })).toBeInTheDocument();
    await user.click((await screen.findAllByRole("button", { name: /mark read/i }))[0]);

    expect(await screen.findByText(/rolled back/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText(/^url$/i), "https://hooks.example.test/new");
    await user.type(screen.getByLabelText(/^secret$/i), "super-secret-value");
    await user.click(screen.getByRole("button", { name: /add webhook/i }));

    await waitFor(() => expect(screen.queryByDisplayValue("super-secret-value")).not.toBeInTheDocument());
    expect(screen.queryByText("super-secret-value")).not.toBeInTheDocument();
    expect(screen.getAllByText(/secret is write-only/i).length).toBeGreaterThan(0);
  });

  it("rolls notification preferences back when save fails", async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp("/w/factory-floor/notifications", activityFetch({ failPreferenceSave: true }));

    expect(await screen.findByRole("heading", { name: /^notifications$/i })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText(/digest frequency/i), "daily");
    await user.click(screen.getByRole("button", { name: /save preferences/i }));

    expect(await screen.findByText(/Preference save failed and was rolled back/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText(/digest frequency/i)).toHaveValue("hourly"));
    expect(screen.queryByText(/^saved$/i)).not.toBeInTheDocument();
  });

  it("renders audit rows without mutation controls", async () => {
    renderAuthenticatedApp("/w/factory-floor/audit?action=canvas.update");

    expect(await screen.findByRole("heading", { name: /audit trail/i })).toBeInTheDocument();
    expect(await screen.findByLabelText(/canvas.update by LiveLattice Owner/i)).toBeInTheDocument();

    const timeline = document.querySelector(".audit-event-list");
    expect(timeline).toBeInstanceOf(HTMLElement);
    expect(within(timeline as HTMLElement).queryByRole("button", { name: /delete|edit|remove/i })).not.toBeInTheDocument();
    expect(document.querySelectorAll(".audit-event-list button")).toHaveLength(0);
  });
});
