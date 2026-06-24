import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../app/routes";
import { authSessionFixture, canvasCommentFixtures, canvasFixture, canvasSnapshotFixtures, canvasTemplateFixtures, workspaceFixtures, workspaceMemberFixtures } from "../contracts/fixtures";
import { coreFixtureIds, primaryCanvasHref } from "../contracts/fixture-ids";
import type { AuthTokenResponse } from "../contracts/auth";
import type { CanvasContent } from "../contracts/canvas";
import type { WorkspaceMemberResponse, WorkspaceResponse } from "../contracts/workspaces";
import { AuthProvider } from "../features/auth/AuthProvider";
import { WorkspaceProvider } from "../features/workspaces/WorkspaceProvider";

type TestRemoteOperation = {
  canvasId: string;
  ops: Array<{ type: string; id?: string; changes?: Record<string, unknown> }>;
  version: number;
  lockVersion?: number;
  seq?: number;
  origin?: string;
};

const realtimeMock = vi.hoisted(() => {
  const mock = {
    remoteOperationListener: undefined as undefined | ((message: TestRemoteOperation) => void),
    sendOperations: vi.fn(async () => ({ ok: true, canvasId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", version: 129, seq: 1 })),
    sendPresence: vi.fn(async () => ({ ok: true })),
    connect: vi.fn(),
    disconnect: vi.fn(),
    onStatus: vi.fn((listener: (status: { state: string; label: string; version: number; memberCount: number }) => void) => {
      listener({ state: "connected", label: "Realtime connected", version: 128, memberCount: 2 });
      return vi.fn();
    }),
    onRemoteOperation: vi.fn((listener: (message: TestRemoteOperation) => void) => {
      mock.remoteOperationListener = listener;
      return vi.fn(() => {
        mock.remoteOperationListener = undefined;
      });
    }),
    onPresence: vi.fn(() => vi.fn())
  };

  return mock;
});

vi.mock("../features/canvas/realtime", () => ({
  resolveRealtimeUrl: () => "http://realtime/ws/workspace",
  createCanvasRealtimeAdapter: () => realtimeMock
}));

type MemberFixtureMap = Record<string, WorkspaceMemberResponse[]>;
type CanvasFetchOptions = {
  workspaces?: WorkspaceResponse[];
  members?: MemberFixtureMap;
  patchCanvas?: (body: { content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }) => Response | Promise<Response>;
};

const canvasApiPath = `/api/core/canvases/${coreFixtureIds.canvasIncidentMap}`;

function renderCanvasApp(fetchMock = canvasFetch()) {
  vi.stubGlobal("fetch", fetchMock);
  return render(
    <MemoryRouter initialEntries={[primaryCanvasHref("factory-floor")]}>
      <AuthProvider initialSession={authSessionFixture as AuthTokenResponse}>
        <WorkspaceProvider>
          <AppRoutes />
        </WorkspaceProvider>
      </AuthProvider>
    </MemoryRouter>
  );
}

function canvasFetch({ workspaces = workspaceFixtures as WorkspaceResponse[], members = workspaceMemberFixtures as MemberFixtureMap, patchCanvas }: CanvasFetchOptions = {}) {
  return vi.fn<typeof fetch>(async (input, init) => {
    const path = String(input);

    if (path === "/api/core/workspaces") {
      return jsonResponse(workspaces);
    }

    const memberMatch = path.match(/^\/api\/core\/workspaces\/([^/]+)\/members$/);
    if (memberMatch) {
      return jsonResponse(members[memberMatch[1]] ?? []);
    }

    if (path === canvasApiPath && (!init?.method || init.method === "GET")) {
      return jsonResponse(canvasFixture);
    }

    if (path === canvasApiPath && init?.method === "PATCH") {
      const body = JSON.parse(String(init.body ?? "{}")) as { content?: CanvasContent };
      if (patchCanvas) {
        return patchCanvas(body);
      }

      return jsonResponse({
        ...canvasFixture,
        content: body.content ?? canvasFixture.content,
        version: canvasFixture.version + 1,
        lockVersion: canvasFixture.lockVersion + 1,
        updatedAt: "2026-06-23T18:45:00Z"
      });
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

describe("canvas route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    realtimeMock.sendOperations.mockClear();
    realtimeMock.sendPresence.mockClear();
    realtimeMock.connect.mockClear();
    realtimeMock.disconnect.mockClear();
    realtimeMock.remoteOperationListener = undefined;
  });

  it("loads a canvas, renders tools/elements/panels, and supports keyboard-accessible selection", async () => {
    const user = userEvent.setup();
    renderCanvasApp();

    expect(await screen.findByRole("heading", { name: /incident response lattice/i })).toBeInTheDocument();
    expect(screen.getByText(/version 128/i)).toBeInTheDocument();
    expect(await screen.findByText(/Realtime connected/i)).toBeInTheDocument();
    expect(screen.getByRole("application", { name: /canvas viewport/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /rectangle/i })).toBeEnabled();
    expect(screen.getByRole("button", { name: /import/i })).toBeEnabled();
    expect(screen.getByRole("button", { name: /^export$/i })).toBeEnabled();
    expect(screen.getAllByText(/REST Gateway/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Kafka/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Runbook image/i).length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: /REST Gateway editable at x 160 y 140/i }));
    const inspector = screen.getByRole("complementary", { name: /canvas workbench panels/i });
    expect(within(inspector).getByRole("heading", { name: /rectangle/i })).toBeInTheDocument();
    expect(within(inspector).getByLabelText(/^x$/i)).toHaveValue(160);

    await user.click(within(inspector).getByRole("button", { name: /duplicate/i }));
    await waitFor(() => expect(realtimeMock.sendOperations).toHaveBeenCalledTimes(1));
    expect(await screen.findByText(/version 129/i)).toBeInTheDocument();
    expect(realtimeMock.connect).toHaveBeenCalledTimes(1);
    expect(realtimeMock.disconnect).not.toHaveBeenCalled();
  });

  it("does not broadcast realtime operations when Core rejects a canvas save", async () => {
    const user = userEvent.setup();
    renderCanvasApp(canvasFetch({
      patchCanvas: () => jsonResponse({ code: "CANVAS_VERSION_CONFLICT", message: "Canvas version conflict" }, 409)
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("button", { name: /REST Gateway editable at x 160 y 140/i }));
    const inspector = screen.getByRole("complementary", { name: /canvas workbench panels/i });

    await user.click(within(inspector).getByRole("button", { name: /duplicate/i }));

    expect(await screen.findByText(/Canvas version conflict/i)).toBeInTheDocument();
    expect(realtimeMock.sendOperations).not.toHaveBeenCalled();
  });

  it("uses Core concurrency tokens from remote realtime operations for the next save", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        if (body.expectedVersion !== 129 || body.expectedLockVersion !== 5) {
          return jsonResponse({ code: "CANVAS_VERSION_CONFLICT", message: "Canvas version conflict" }, 409);
        }

        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: 130,
          lockVersion: 6,
          updatedAt: "2026-06-23T18:50:00Z"
        });
      }
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await waitFor(() => expect(realtimeMock.onRemoteOperation).toHaveBeenCalledTimes(1));

    act(() => {
      realtimeMock.remoteOperationListener?.({
        canvasId: coreFixtureIds.canvasIncidentMap,
        ops: [{ type: "update", id: "el-gateway", changes: { x: 190 } }],
        version: 129,
        lockVersion: 5,
        origin: "peer"
      });
    });

    await user.click(await screen.findByRole("button", { name: /REST Gateway editable at x 190 y 140/i }));
    const inspector = screen.getByRole("complementary", { name: /canvas workbench panels/i });
    await user.click(within(inspector).getByRole("button", { name: /duplicate/i }));

    await waitFor(() => expect(patchRequests).toHaveLength(1));
    expect(patchRequests[0]).toMatchObject({ expectedVersion: 129, expectedLockVersion: 5 });
    expect(await screen.findByText(/version 130/i)).toBeInTheDocument();
    expect(screen.queryByText(/Canvas version conflict/i)).not.toBeInTheDocument();
  });

  it("shows comments with missing targets and snapshot restore confirmation", async () => {
    const user = userEvent.setup();
    renderCanvasApp();

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("tab", { name: /comments/i }));
    expect(screen.getByText(/Missing target el-deleted/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /add comment/i })).toBeDisabled();

    await user.click(screen.getByRole("tab", { name: /snapshots/i }));
    await user.click(screen.getAllByRole("button", { name: /restore/i })[0]);
    expect(screen.getByRole("dialog", { name: /restore snapshot/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /confirm restore/i })).toBeInTheDocument();
  });
});
