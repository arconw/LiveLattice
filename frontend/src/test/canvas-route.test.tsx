import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../app/routes";
import { authSessionFixture, canvasCommentFixtures, canvasFixture, canvasSnapshotFixtures, canvasTemplateFixtures, workspaceFixtures, workspaceMemberFixtures } from "../contracts/fixtures";
import { coreFixtureIds, primaryCanvasHref } from "../contracts/fixture-ids";
import type { AuthTokenResponse } from "../contracts/auth";
import type { CanvasContent, CommentResponse, RawCanvasResponse, UpdateCommentPayload } from "../contracts/canvas";
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
  canvas?: RawCanvasResponse;
  patchCanvas?: (body: { content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }) => Response | Promise<Response>;
  comments?: CommentResponse[];
  patchComment?: (commentId: string, body: UpdateCommentPayload) => Response | Promise<Response>;
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

function canvasFetch({ workspaces = workspaceFixtures as WorkspaceResponse[], members = workspaceMemberFixtures as MemberFixtureMap, canvas = canvasFixture, patchCanvas, comments = canvasCommentFixtures as CommentResponse[], patchComment }: CanvasFetchOptions = {}) {
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
      return jsonResponse(canvas);
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
      return jsonResponse(comments);
    }

    const commentMatch = path.match(new RegExp(`^${canvasApiPath}/comments/([^/]+)$`));
    if (commentMatch && init?.method === "PATCH") {
      const body = JSON.parse(String(init.body ?? "{}")) as UpdateCommentPayload;
      if (patchComment) {
        return patchComment(commentMatch[1], body);
      }

      const existing = comments.find((comment) => comment.id === commentMatch[1]);
      return jsonResponse({
        ...existing,
        ...body,
        id: existing?.id ?? commentMatch[1],
        canvasId: coreFixtureIds.canvasIncidentMap,
        updatedAt: "2026-06-23T18:45:00Z"
      });
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

function deferredResponse() {
  let resolve!: (response: Response) => void;
  const promise = new Promise<Response>((promiseResolve) => {
    resolve = promiseResolve;
  });

  return { promise, resolve };
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

  it("toggles in-page canvas fullscreen and exits with Escape", async () => {
    const user = userEvent.setup();
    renderCanvasApp();

    await screen.findByRole("heading", { name: /incident response lattice/i });
    const fullscreenButton = screen.getByRole("button", { name: /canvas fullscreen/i });
    const editorShell = fullscreenButton.closest(".canvas-editor-shell");

    expect(editorShell).not.toHaveClass("is-fullscreen");

    await user.click(fullscreenButton);

    expect(editorShell).toHaveClass("is-fullscreen");
    expect(screen.getByRole("button", { name: /exit fullscreen/i })).toHaveAttribute("aria-pressed", "true");
    expect(document.body.style.overflow).toBe("hidden");

    fireEvent.keyDown(window, { key: "Escape" });

    await waitFor(() => expect(editorShell).not.toHaveClass("is-fullscreen"));
    expect(screen.getByRole("button", { name: /canvas fullscreen/i })).toHaveAttribute("aria-pressed", "false");
    expect(document.body.style.overflow).toBe("");
  });

  it("rejects a canvas that belongs to a different workspace than the route", async () => {
    renderCanvasApp(canvasFetch({
      canvas: {
        ...canvasFixture,
        workspaceId: workspaceFixtures[1].id
      }
    }));

    expect(await screen.findByRole("heading", { name: /canvas not found/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /back to canvases/i })).toHaveAttribute("href", "/w/factory-floor/c");
    expect(realtimeMock.connect).not.toHaveBeenCalled();
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

  it("serializes queued canvas saves while keeping optimistic local edits", async () => {
    const user = userEvent.setup();
    const firstSave = deferredResponse();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);

        if (patchRequests.length === 1) {
          return firstSave.promise;
        }

        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: 130,
          lockVersion: 6,
          updatedAt: "2026-06-23T18:51:00Z"
        });
      }
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("button", { name: /^rectangle$/i }));
    await waitFor(() => expect(patchRequests).toHaveLength(1));
    await user.click(screen.getByRole("button", { name: /^circle$/i }));

    expect(patchRequests).toHaveLength(1);

    act(() => {
      firstSave.resolve(jsonResponse({
        ...canvasFixture,
        content: patchRequests[0].content ?? canvasFixture.content,
        version: 129,
        lockVersion: 5,
        updatedAt: "2026-06-23T18:50:00Z"
      }));
    });

    await waitFor(() => expect(patchRequests).toHaveLength(2));
    expect(patchRequests[0]).toMatchObject({ expectedVersion: 128, expectedLockVersion: 4 });
    expect(patchRequests[1]).toMatchObject({ expectedVersion: 129, expectedLockVersion: 5 });
    expect(patchRequests[1].content?.elements).toHaveLength(canvasFixture.content.elements.length + 2);
    expect(await screen.findByText(/version 130/i)).toBeInTheDocument();
  });

  it("keeps rapid toolbar additions while the first save is still pending", async () => {
    const firstSave = deferredResponse();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);

        if (patchRequests.length === 1) {
          return firstSave.promise;
        }

        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: 130,
          lockVersion: 6,
          updatedAt: "2026-06-23T18:51:00Z"
        });
      }
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });

    fireEvent.click(screen.getByRole("button", { name: /^rectangle$/i }));
    fireEvent.click(screen.getByRole("button", { name: /^circle$/i }));
    fireEvent.click(screen.getByRole("button", { name: /^text$/i }));

    await waitFor(() => expect(patchRequests).toHaveLength(1));
    expect(await screen.findByRole("img", { name: /incident response lattice canvas with 12 elements/i })).toBeInTheDocument();

    act(() => {
      firstSave.resolve(jsonResponse({
        ...canvasFixture,
        content: patchRequests[0].content ?? canvasFixture.content,
        version: 129,
        lockVersion: 5,
        updatedAt: "2026-06-23T18:50:00Z"
      }));
    });

    await waitFor(() => expect(patchRequests).toHaveLength(2));
    expect(patchRequests[1]).toMatchObject({ expectedVersion: 129, expectedLockVersion: 5 });
    expect(patchRequests[1].content?.elements).toHaveLength(canvasFixture.content.elements.length + 3);
  });

  it("does not add an element when the freehand toolbar button is selected", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + 1,
          lockVersion: canvasFixture.lockVersion + 1,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("button", { name: /^freehand$/i }));

    expect(screen.getByRole("button", { name: /^freehand$/i })).toHaveAttribute("aria-pressed", "true");
    expect(await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i })).toBeInTheDocument();
    expect(patchRequests).toHaveLength(0);
  });

  it("deletes selected elements from the tool rail and keyboard", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + patchRequests.length,
          lockVersion: canvasFixture.lockVersion + patchRequests.length,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });
    const deleteSelectionButton = screen.getByRole("button", { name: /delete selection/i });
    expect(deleteSelectionButton).toBeDisabled();

    await user.click(screen.getByRole("button", { name: /REST Gateway editable at x 160 y 140/i }));
    expect(deleteSelectionButton).toBeEnabled();
    await user.click(deleteSelectionButton);

    await waitFor(() => expect(patchRequests).toHaveLength(1));
    expect(patchRequests[0].content?.elements.some((element) => element.id === "el-gateway")).toBe(false);
    expect(deleteSelectionButton).toBeDisabled();

    await user.click(screen.getByRole("button", { name: /Kafka editable at x 480 y 160/i }));
    await user.keyboard("{Delete}");

    await waitFor(() => expect(patchRequests).toHaveLength(2));
    expect(patchRequests[1].content?.elements.some((element) => element.id === "el-kafka")).toBe(false);
  });

  it("keeps a selected group while dragging one selected element", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + 1,
          lockVersion: canvasFixture.lockVersion + 1,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const surface = await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i });
    Object.defineProperty(surface, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 960, height: 672, right: 960, bottom: 672, x: 0, y: 0, toJSON: () => ({}) })
    });

    await user.click(screen.getByRole("button", { name: /box select/i }));
    fireEvent.pointerDown(surface, { pointerId: 21, button: 0, clientX: 140, clientY: 130 });
    fireEvent.pointerMove(surface, { pointerId: 21, button: 0, clientX: 610, clientY: 290 });
    fireEvent.pointerUp(surface, { pointerId: 21, button: 0, clientX: 610, clientY: 290 });
    await user.click(screen.getByRole("button", { name: /^select$/i }));

    fireEvent.pointerDown(surface, { pointerId: 22, button: 0, clientX: 200, clientY: 170 });
    fireEvent.pointerMove(surface, { pointerId: 22, button: 0, clientX: 220, clientY: 190 });
    fireEvent.pointerUp(surface, { pointerId: 22, button: 0, clientX: 220, clientY: 190 });

    await waitFor(() => expect(patchRequests).toHaveLength(1));
    expect(patchRequests[0].content?.elements.find((element) => element.id === "el-gateway")).toMatchObject({ x: 180, y: 160 });
    expect(patchRequests[0].content?.elements.find((element) => element.id === "el-kafka")).toMatchObject({ x: 500, y: 180 });
  });

  it("creates a freehand element from pointer drawing at zoomed and panned coordinates", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      canvas: {
        ...canvasFixture,
        content: {
          ...canvasFixture.content,
          viewport: { zoom: 2, panX: 40, panY: -30 }
        }
      },
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + patchRequests.length,
          lockVersion: canvasFixture.lockVersion + patchRequests.length,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const surface = await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i });
    Object.defineProperty(surface, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 960, height: 672, right: 960, bottom: 672, x: 0, y: 0, toJSON: () => ({}) })
    });

    await user.click(screen.getByRole("button", { name: /^freehand$/i }));
    fireEvent.pointerDown(surface, { pointerId: 11, button: 0, clientX: 260, clientY: 230 });
    fireEvent.pointerMove(surface, { pointerId: 11, button: 0, clientX: 280, clientY: 250 });
    fireEvent.pointerMove(surface, { pointerId: 11, button: 0, clientX: 340, clientY: 310 });
    fireEvent.pointerUp(surface, { pointerId: 11, button: 0, clientX: 380, clientY: 350 });

    await waitFor(() => expect(patchRequests).toHaveLength(1));
    const added = patchRequests[0].content?.elements.find((element) => element.type === "freehand" && element.id !== "el-freehand");

    expect(added).toMatchObject({ type: "freehand", x: 110, y: 130, width: 60, height: 60 });
    expect(added?.data.points).toEqual([{ x: 110, y: 130 }, { x: 120, y: 140 }, { x: 150, y: 170 }, { x: 170, y: 190 }]);
    expect(added?.data.path).toBe("M 110 130 L 120 140 L 150 170 L 170 190");
    expect(await screen.findByRole("img", { name: /incident response lattice canvas with 10 elements/i })).toBeInTheDocument();
  });

  it("opens a direct editor for new text elements and persists edited text", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      canvas: {
        ...canvasFixture,
        content: {
          ...canvasFixture.content,
          viewport: { zoom: 1.5, panX: 20, panY: -10 }
        }
      },
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + patchRequests.length,
          lockVersion: canvasFixture.lockVersion + patchRequests.length,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("button", { name: /^text$/i }));

    const editor = await screen.findByRole("textbox", { name: /edit text element/i });
    expect(editor).toHaveValue("New text");
    expect(Number.parseFloat(editor.style.left)).toBeCloseTo(315.5);
    expect(Number.parseFloat(editor.style.top)).toBeCloseTo(291.5);

    await user.clear(editor);
    await user.type(editor, "Direct canvas note{Enter}second line");
    fireEvent.blur(editor);

    await waitFor(() => expect(patchRequests.some((request) => request.content?.elements.some((element) => element.type === "text" && element.data.text === "Direct canvas note\nsecond line"))).toBe(true));
    expect(screen.queryByRole("textbox", { name: /edit text element/i })).not.toBeInTheDocument();
  });

  it("isolates text editor keyboard input from canvas shortcuts", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + patchRequests.length,
          lockVersion: canvasFixture.lockVersion + patchRequests.length,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const surface = await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i });
    fireEvent.doubleClick(surface, { clientX: 720, clientY: 140 });

    const editor = await screen.findByRole("textbox", { name: /edit text element/i });
    expect(editor).toHaveValue("Validate RBAC before export");

    await user.keyboard("{Backspace}");

    expect(editor).toHaveValue("Validate RBAC before expor");
    expect(patchRequests).toHaveLength(0);
    expect(await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i })).toBeInTheDocument();

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("textbox", { name: /edit text element/i })).not.toBeInTheDocument();
    expect(patchRequests).toHaveLength(0);
    expect(within(screen.getByRole("complementary", { name: /canvas workbench panels/i })).getByRole("heading", { name: /text/i })).toBeInTheDocument();
  });

  it("rebases and retries once after a Core version conflict", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);

        if (patchRequests.length === 1) {
          return jsonResponse({ code: "CANVAS_VERSION_CONFLICT", message: "Canvas version conflict" }, 409);
        }

        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: 129,
          lockVersion: 5,
          updatedAt: "2026-06-23T18:50:00Z"
        });
      }
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("button", { name: /REST Gateway editable at x 160 y 140/i }));
    const inspector = screen.getByRole("complementary", { name: /canvas workbench panels/i });
    await user.click(within(inspector).getByRole("button", { name: /duplicate/i }));

    await waitFor(() => expect(patchRequests).toHaveLength(2));
    expect(realtimeMock.sendOperations).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(/Canvas version conflict/i)).not.toBeInTheDocument();
    expect(await screen.findByText(/version 129/i)).toBeInTheDocument();
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

  it("supports canvas hit selection, drag commit, and local wheel zoom", async () => {
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + 1,
          lockVersion: canvasFixture.lockVersion + 1,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const surface = await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i });
    Object.defineProperty(surface, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 960, height: 672, right: 960, bottom: 672, x: 0, y: 0, toJSON: () => ({}) })
    });

    fireEvent.pointerDown(surface, { pointerId: 1, button: 0, clientX: 200, clientY: 170 });
    fireEvent.pointerMove(surface, { pointerId: 1, button: 0, clientX: 220, clientY: 190 });
    expect(patchRequests).toHaveLength(0);
    fireEvent.pointerUp(surface, { pointerId: 1, button: 0, clientX: 220, clientY: 190 });

    await waitFor(() => expect(patchRequests).toHaveLength(1));
    expect(patchRequests[0].content?.elements.find((element) => element.id === "el-gateway")).toMatchObject({ x: 180, y: 160 });

    fireEvent.wheel(surface, { deltaY: -100, clientX: 320, clientY: 240 });
    expect(await screen.findByText("110%")).toBeInTheDocument();
    expect(patchRequests).toHaveLength(1);
  });

  it("drags connector endpoints at zoomed and panned coordinates", async () => {
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      canvas: {
        ...canvasFixture,
        content: {
          ...canvasFixture.content,
          viewport: { zoom: 2, panX: 40, panY: -30 }
        }
      },
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + 1,
          lockVersion: canvasFixture.lockVersion + 1,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const surface = await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i });
    Object.defineProperty(surface, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 960, height: 672, right: 960, bottom: 672, x: 0, y: 0, toJSON: () => ({}) })
    });

    fireEvent.pointerDown(surface, { pointerId: 5, button: 0, clientX: 780, clientY: 350 });
    fireEvent.pointerMove(surface, { pointerId: 5, button: 0, clientX: 780, clientY: 410 });
    fireEvent.pointerUp(surface, { pointerId: 5, button: 0, clientX: 780, clientY: 410 });

    await waitFor(() => expect(patchRequests).toHaveLength(1));
    const connector = patchRequests[0].content?.elements.find((element) => element.id === "el-connector");

    expect(connector?.data.start).toEqual({ x: 370, y: 220 });
    expect(connector?.data.end).toEqual({ x: 480, y: 224 });
    expect(connector?.data.startElementId).toBe("el-gateway");
    expect(screen.getByLabelText(/start y/i)).toHaveValue(220);
  });

  it("drags arrow bodies and endpoints at zoomed and panned coordinates", async () => {
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      canvas: {
        ...canvasFixture,
        content: {
          ...canvasFixture.content,
          viewport: { zoom: 2, panX: 40, panY: -30 }
        }
      },
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + patchRequests.length,
          lockVersion: canvasFixture.lockVersion + patchRequests.length,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const surface = await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i });
    Object.defineProperty(surface, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 1800, height: 900, right: 1800, bottom: 900, x: 0, y: 0, toJSON: () => ({}) })
    });

    fireEvent.pointerDown(surface, { pointerId: 7, button: 0, clientX: 1390, clientY: 506 });
    fireEvent.pointerMove(surface, { pointerId: 7, button: 0, clientX: 1450, clientY: 546 });
    fireEvent.pointerUp(surface, { pointerId: 7, button: 0, clientX: 1450, clientY: 546 });

    await waitFor(() => expect(patchRequests).toHaveLength(1));
    const movedArrow = patchRequests[0].content?.elements.find((element) => element.id === "el-arrow");

    expect(movedArrow).toMatchObject({ x: 640, y: 240 });
    expect(movedArrow?.data.start).toEqual({ x: 640, y: 240 });
    expect(movedArrow?.data.end).toEqual({ x: 770, y: 336 });
    expect(within(screen.getByRole("complementary", { name: /canvas workbench panels/i })).getByRole("heading", { name: /arrow/i })).toBeInTheDocument();

    fireEvent.pointerDown(surface, { pointerId: 8, button: 0, clientX: 1580, clientY: 642 });
    fireEvent.pointerMove(surface, { pointerId: 8, button: 0, clientX: 1660, clientY: 630 });
    fireEvent.pointerUp(surface, { pointerId: 8, button: 0, clientX: 1660, clientY: 630 });

    await waitFor(() => expect(patchRequests).toHaveLength(2));
    const adjustedArrow = patchRequests[1].content?.elements.find((element) => element.id === "el-arrow");

    expect(adjustedArrow).toMatchObject({ x: 640, y: 240, width: 170, height: 90 });
    expect(adjustedArrow?.data.start).toEqual({ x: 640, y: 240 });
    expect(adjustedArrow?.data.end).toEqual({ x: 810, y: 330 });
    expect(screen.getByLabelText(/end x/i)).toHaveValue(810);
  });

  it("zooms toolbar controls around the viewport center without issuing a canvas PATCH", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + 1,
          lockVersion: canvasFixture.lockVersion + 1,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const viewport = await screen.findByRole("application", { name: /canvas viewport/i });
    Object.defineProperty(viewport, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 1000, height: 600, right: 1000, bottom: 600, x: 0, y: 0, toJSON: () => ({}) })
    });
    const commentPin = await screen.findByRole("button", { name: /open comment on arrow/i });

    await user.click(screen.getByRole("button", { name: /zoom in/i }));

    expect(await screen.findByText("110%")).toBeInTheDocument();
    expect(Number.parseFloat(commentPin.style.left)).toBeCloseTo(692.5);
    expect(Number.parseFloat(commentPin.style.top)).toBeCloseTo(264.8);
    expect(patchRequests).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: /zoom out/i }));

    expect(await screen.findByText("100%")).toBeInTheDocument();
    expect(Number.parseFloat(commentPin.style.left)).toBeCloseTo(675);
    expect(Number.parseFloat(commentPin.style.top)).toBeCloseTo(268);
    expect(patchRequests).toHaveLength(0);
  });

  it("keeps overlay pins and hit testing aligned after panning the camera", async () => {
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + 1,
          lockVersion: canvasFixture.lockVersion + 1,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const viewport = await screen.findByRole("application", { name: /canvas viewport/i });
    const surface = await screen.findByRole("img", { name: /incident response lattice canvas with 9 elements/i });
    const viewportRect = { left: 0, top: 0, width: 1000, height: 600, right: 1000, bottom: 600, x: 0, y: 0, toJSON: () => ({}) };
    Object.defineProperty(viewport, "getBoundingClientRect", {
      configurable: true,
      value: () => viewportRect
    });
    Object.defineProperty(surface, "getBoundingClientRect", {
      configurable: true,
      value: () => viewportRect
    });
    const commentPin = await screen.findByRole("button", { name: /open comment on arrow/i });

    fireEvent.pointerDown(surface, { pointerId: 31, button: 0, clientX: 900, clientY: 500 });
    fireEvent.pointerMove(surface, { pointerId: 31, button: 0, clientX: 950, clientY: 530 });
    fireEvent.pointerUp(surface, { pointerId: 31, button: 0, clientX: 950, clientY: 530 });

    await waitFor(() => expect(Number.parseFloat(commentPin.style.left)).toBeCloseTo(725));
    expect(Number.parseFloat(commentPin.style.top)).toBeCloseTo(298);
    expect(patchRequests).toHaveLength(0);

    fireEvent.pointerDown(surface, { pointerId: 32, button: 0, clientX: 315, clientY: 222 });
    fireEvent.pointerUp(surface, { pointerId: 32, button: 0, clientX: 315, clientY: 222 });

    expect(within(screen.getByRole("complementary", { name: /canvas workbench panels/i })).getByRole("heading", { name: /rectangle/i })).toBeInTheDocument();
  });

  it("opens readable comment pins from the canvas overlay", async () => {
    const user = userEvent.setup();
    renderCanvasApp();

    const commentPin = await screen.findByRole("button", { name: /open comment on general canvas: open. snapshot before changing the database branch/i });

    expect(commentPin).toHaveAccessibleDescription(/Snapshot before changing the database branch/i);
    expect(within(commentPin).getByRole("tooltip")).toHaveTextContent(/General canvas/i);

    await user.click(commentPin);

    const panel = screen.getByRole("complementary", { name: /canvas workbench panels/i });
    expect(within(panel).getByRole("tab", { name: /comments/i })).toHaveAttribute("aria-selected", "true");
    expect(within(panel).getByText("Snapshot before changing the database branch.")).toBeInTheDocument();
  });

  it("drags comment pins and persists canvas coordinates", async () => {
    const patchRequests: Array<{ commentId: string; body: UpdateCommentPayload }> = [];
    renderCanvasApp(canvasFetch({
      patchComment: (commentId, body) => {
        patchRequests.push({ commentId, body });
        const existing = (canvasCommentFixtures as CommentResponse[]).find((comment) => comment.id === commentId);
        return jsonResponse({
          ...existing,
          ...body,
          id: commentId,
          canvasId: coreFixtureIds.canvasIncidentMap,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    const viewport = await screen.findByRole("application", { name: /canvas viewport/i });
    Object.defineProperty(viewport, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 1000, height: 600, right: 1000, bottom: 600, x: 0, y: 0, toJSON: () => ({}) })
    });
    const commentPin = await screen.findByRole("button", { name: /open comment on general canvas/i });

    fireEvent.pointerDown(commentPin, { pointerId: 22, button: 0, clientX: 112, clientY: 96 });
    fireEvent.pointerMove(commentPin, { pointerId: 22, buttons: 1, clientX: 180, clientY: 140 });
    fireEvent.pointerUp(commentPin, { pointerId: 22, button: 0, clientX: 180, clientY: 140 });

    await waitFor(() => expect(patchRequests).toEqual([
      {
        commentId: coreFixtureIds.commentGeneral,
        body: { position: { x: 180, y: 140 } }
      }
    ]));
    expect(Number.parseFloat(commentPin.style.left)).toBeCloseTo(180);
    expect(Number.parseFloat(commentPin.style.top)).toBeCloseTo(140);
  });

  it("does not pass transparent values to inspector color inputs", async () => {
    const user = userEvent.setup();
    renderCanvasApp();

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("button", { name: /Validate RBAC before export editable at x 710 y 130/i }));
    const inspector = screen.getByRole("complementary", { name: /canvas workbench panels/i });
    const colorInputs = Array.from(inspector.querySelectorAll<HTMLInputElement>("input[type='color']"));

    expect(colorInputs).toHaveLength(2);
    expect(colorInputs.every((input) => /^#[\da-f]{6}$/i.test(input.value))).toBe(true);
    expect(colorInputs.map((input) => input.value)).not.toContain("transparent");
    expect(within(inspector).getByLabelText(/transparent fill/i)).toBeChecked();
  });

  it("replaces, updates, and removes image element sources from the inspector", async () => {
    const user = userEvent.setup();
    const patchRequests: Array<{ content?: CanvasContent; expectedVersion?: number; expectedLockVersion?: number }> = [];
    renderCanvasApp(canvasFetch({
      patchCanvas: (body) => {
        patchRequests.push(body);
        return jsonResponse({
          ...canvasFixture,
          content: body.content ?? canvasFixture.content,
          version: canvasFixture.version + patchRequests.length,
          lockVersion: canvasFixture.lockVersion + patchRequests.length,
          updatedAt: "2026-06-23T18:45:00Z"
        });
      }
    }));

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("button", { name: /Runbook image editable at x 770 y 360/i }));
    const inspector = screen.getByRole("complementary", { name: /canvas workbench panels/i });
    const uploadInput = within(inspector).getByLabelText(/upload image file/i);

    await user.upload(uploadInput, new File([new Uint8Array(2 * 1024 * 1024 + 1)], "huge.png", { type: "image/png" }));

    expect(await within(inspector).findByRole("alert")).toHaveTextContent(/2 MB or smaller/i);
    expect(patchRequests).toHaveLength(0);

    await user.upload(uploadInput, new File(["pixel"], "pixel.png", { type: "image/png" }));

    await waitFor(() => expect(patchRequests.some((request) => typeof request.content?.elements.find((element) => element.id === "el-image")?.data.src === "string")).toBe(true));
    expect(patchRequests.at(-1)?.content?.elements.find((element) => element.id === "el-image")?.data.src).toMatch(/^data:image\/png;base64,/);

    await user.type(within(inspector).getByLabelText(/image url/i), "https://example.com/replacement.png");
    fireEvent.blur(within(inspector).getByLabelText(/image url/i));

    await waitFor(() => expect(patchRequests.at(-1)?.content?.elements.find((element) => element.id === "el-image")?.data.src).toBe("https://example.com/replacement.png"));
    const requestCountBeforeRemove = patchRequests.length;
    const removeButton = within(inspector).getByRole("button", { name: /remove image/i });

    await waitFor(() => expect(removeButton).toBeEnabled());
    fireEvent.click(removeButton);

    await waitFor(() => expect(patchRequests.length).toBeGreaterThan(requestCountBeforeRemove));
    await waitFor(() => expect(patchRequests.at(-1)?.content?.elements.find((element) => element.id === "el-image")?.data).not.toHaveProperty("src"));
  });

  it("shows comments with missing targets and snapshot restore confirmation", async () => {
    const user = userEvent.setup();
    renderCanvasApp();

    await screen.findByRole("heading", { name: /incident response lattice/i });
    await user.click(screen.getByRole("tab", { name: /comments/i }));
    const panel = screen.getByRole("complementary", { name: /canvas workbench panels/i });
    expect(within(panel).getByText(/Missing target el-deleted/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /add comment/i })).toBeDisabled();

    await user.click(screen.getByRole("tab", { name: /snapshots/i }));
    await user.click(screen.getAllByRole("button", { name: /restore/i })[0]);
    expect(screen.getByRole("dialog", { name: /restore snapshot/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /confirm restore/i })).toBeInTheDocument();
  });
});
