import {
  ArrowRight,
  Camera,
  Check,
  Circle,
  Copy,
  Download,
  GitBranch,
  Grid3X3,
  History,
  Image,
  Layers,
  LayoutTemplate,
  Lock,
  Maximize2,
  MessageSquare,
  Minimize2,
  MousePointer2,
  Pencil,
  ScanSearch,
  Send,
  Square,
  SquareDashedMousePointer,
  Trash2,
  Type,
  Unlock,
  Upload,
  X,
  ZoomIn,
  ZoomOut
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, ChangeEvent, FormEvent, KeyboardEvent, PointerEvent as ReactPointerEvent, ReactNode } from "react";
import { Link, useNavigate, useOutletContext, useParams } from "react-router-dom";
import { AppError, createWorkspaceCacheKey } from "../../contracts/api-client";
import type { CanvasContent, CanvasElement, CanvasElementType, CanvasPoint, CanvasResponse, CommentResponse, SnapshotResponse, TemplateResponse } from "../../contracts/canvas";
import {
  createCanvas,
  createCanvasComment,
  createCanvasSnapshot,
  deleteCanvasComment,
  exportCanvasFile,
  getCanvas,
  importCanvasFile,
  listCanvasComments,
  listCanvasSnapshots,
  listTemplates,
  restoreCanvasSnapshot,
  saveTemplate,
  updateCanvas,
  updateCanvasComment
} from "../../contracts/canvas";
import { canRole } from "../../contracts/workspaces";
import { Badge, Button, Dialog, EmptyState, ErrorState, IconButton, Input, LoadingState, Panel, PaperSurface, Select, StatusChip } from "../../design-system/components";
import { useAuth } from "../auth/AuthProvider";
import type { ShellOutletContext } from "../shell/AppShell";
import { PermissionDeniedState, RouteAppErrorState } from "../workspaces/WorkspaceStates";
import {
  applyCanvasOperations,
  createCanvasElement,
  curvePointsFromElement,
  deleteSelection,
  duplicateSelection,
  elementCenter,
  elementLabel,
  fitViewportToContent,
  lineModeFromElement,
  moveSelection,
  pointFromData,
  selectElement,
  setSelectionLocked,
  translateCanvasElement,
  updateConnectorEndpoint,
  updateSelectionGeometry,
  updateSelectionStyle,
  updateViewport,
  updateZOrder
} from "./canvasModel";
import type { CanvasOperation, CanvasTool, ConnectorEndpoint } from "./canvasModel";
import { CanvasSurface } from "./CanvasSurface";
import { createCanvasRealtimeAdapter, resolveRealtimeUrl } from "./realtime";
import type { CanvasRealtimeAdapter, RealtimeStatus, RemotePresence } from "./realtime";
import { zoomViewportAroundCenter } from "./viewportMath";

type LoadStatus = "loading" | "ready" | "not_found" | "deleted" | "permission_denied" | "error";
type SyncState = "saved" | "saving" | "offline" | "reconnecting" | "conflict" | "failed";
type ActivePanel = "inspector" | "comments" | "snapshots" | "templates";

const maxInlineImageBytes = 2 * 1024 * 1024;
const supportedImageTypes = new Set(["image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml"]);
const curvePresetOptions = [
  { value: "dashed", label: "Dashed" },
  { value: "pulse", label: "Pulse forward" },
  { value: "pulseReverse", label: "Pulse reverse" },
  { value: "signal", label: "Signal forward" },
  { value: "signalReverse", label: "Signal reverse" },
  { value: "solid", label: "Solid" },
  { value: "dotted", label: "Dotted" }
] as const;

type CanvasToolButton = { tool: CanvasTool; label: string; icon: ReactNode; permission: "edit" | "comment"; spawn?: false } | { tool: CanvasElementType; label: string; icon: ReactNode; permission: "edit"; spawn: true };

const selectionToolButtons: CanvasToolButton[] = [
  { tool: "select", label: "Select", icon: <MousePointer2 size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "marquee", label: "Box select", icon: <SquareDashedMousePointer size={16} aria-hidden="true" />, permission: "edit" }
];

const spawnToolButtons: CanvasToolButton[] = [
  { tool: "rectangle", label: "Rectangle", icon: <Square size={16} aria-hidden="true" />, permission: "edit", spawn: true },
  { tool: "circle", label: "Circle", icon: <Circle size={16} aria-hidden="true" />, permission: "edit", spawn: true },
  { tool: "text", label: "Text", icon: <Type size={16} aria-hidden="true" />, permission: "edit", spawn: true },
  { tool: "image", label: "Image", icon: <Image size={16} aria-hidden="true" />, permission: "edit", spawn: true },
  { tool: "connector", label: "Connector", icon: <GitBranch size={16} aria-hidden="true" />, permission: "edit", spawn: true },
  { tool: "arrow", label: "Arrow", icon: <ArrowRight size={16} aria-hidden="true" />, permission: "edit", spawn: true },
  { tool: "freehand", label: "Freehand", icon: <Pencil size={16} aria-hidden="true" />, permission: "edit" }
];

const commentToolButtons: CanvasToolButton[] = [
  { tool: "comment", label: "Comment", icon: <MessageSquare size={16} aria-hidden="true" />, permission: "comment" }
];

const canvasToolGroups = [
  { label: "Selection tools", items: selectionToolButtons },
  { label: "Create elements", items: spawnToolButtons },
  { label: "Canvas notes", items: commentToolButtons }
] as const;

export function CanvasRoute() {
  const { workspaceSlug = "", canvasId = "" } = useParams();
  const outlet = useOutletContext<ShellOutletContext>();
  const auth = useAuth();
  const navigate = useNavigate();
  const activeWorkspace = outlet.activeWorkspace;
  const [loadStatus, setLoadStatus] = useState<LoadStatus>("loading");
  const [loadError, setLoadError] = useState<AppError | null>(null);
  const [canvas, setCanvas] = useState<CanvasResponse | null>(null);
  const [content, setContent] = useState<CanvasContent | null>(null);
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [snapshots, setSnapshots] = useState<SnapshotResponse[]>([]);
  const [templates, setTemplates] = useState<TemplateResponse[]>([]);
  const [selection, setSelection] = useState<string[]>([]);
  const [tool, setTool] = useState<CanvasTool>("select");
  const [activePanel, setActivePanel] = useState<ActivePanel>("inspector");
  const [syncState, setSyncState] = useState<SyncState>("offline");
  const [syncError, setSyncError] = useState<AppError | null>(null);
  const [realtimeStatus, setRealtimeStatus] = useState<RealtimeStatus>({ state: "idle", label: "Realtime idle", version: null, memberCount: null });
  const [presence, setPresence] = useState<RemotePresence[]>([]);
  const [coordinate, setCoordinate] = useState({ x: 0, y: 0 });
  const [commentDraft, setCommentDraft] = useState("");
  const [replyDraft, setReplyDraft] = useState("");
  const [editingCommentId, setEditingCommentId] = useState<string | null>(null);
  const [editingDraft, setEditingDraft] = useState("");
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [restoreTarget, setRestoreTarget] = useState<SnapshotResponse | null>(null);
  const [templateName, setTemplateName] = useState("");
  const [exportFormat, setExportFormat] = useState<"svg" | "png" | "pdf" | "json">("svg");
  const [jobNotice, setJobNotice] = useState<string | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [fullscreen, setFullscreen] = useState(false);
  const [editingTextElementId, setEditingTextElementId] = useState<string | null>(null);
  const realtimeRef = useRef<CanvasRealtimeAdapter | null>(null);
  const contentRef = useRef<CanvasContent | null>(null);
  const canvasRef = useRef<CanvasResponse | null>(null);
  const realtimeStatusRef = useRef(realtimeStatus);
  const saveQueueRef = useRef<CanvasOperation[]>([]);
  const saveLoopRunningRef = useRef(false);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const previousBodyOverflowRef = useRef("");
  const seqRef = useRef(0);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const canEditCanvas = canRole(outlet.activeRole, "canvas:edit");
  const canComment = canRole(outlet.activeRole, "canvas:comment");
  const canViewCanvas = canRole(outlet.activeRole, "canvas:view");
  const mode = canEditCanvas ? "Editor mode" : canComment ? "Commenter mode" : "Viewer mode";
  const cacheKey = createWorkspaceCacheKey(workspaceSlug || "unknown", "canvas", canvasId || "missing");

  useEffect(() => {
    contentRef.current = content;
  }, [content]);

  useEffect(() => {
    canvasRef.current = canvas;
  }, [canvas]);

  useEffect(() => {
    realtimeStatusRef.current = realtimeStatus;
  }, [realtimeStatus]);

  useEffect(() => {
    if (!fullscreen) {
      return;
    }

    previousBodyOverflowRef.current = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    viewportRef.current?.focus();

    return () => {
      document.body.style.overflow = previousBodyOverflowRef.current;
      previousFocusRef.current?.focus();
      previousFocusRef.current = null;
    };
  }, [fullscreen]);

  useEffect(() => {
    if (!fullscreen) {
      return;
    }

    function exitOnEscape(event: globalThis.KeyboardEvent) {
      if (event.key !== "Escape") {
        return;
      }

      event.preventDefault();
      setFullscreen(false);
    }

    window.addEventListener("keydown", exitOnEscape);
    return () => window.removeEventListener("keydown", exitOnEscape);
  }, [fullscreen]);

  const loadCanvas = useCallback(async (signal?: AbortSignal) => {
    if (!canvasId) {
      setLoadStatus("not_found");
      return;
    }

    setLoadStatus("loading");
    setLoadError(null);
    setSyncError(null);

    try {
      const loadedCanvas = await getCanvas(auth.client, canvasId, signal);
      if (activeWorkspace?.id && loadedCanvas.workspaceId !== activeWorkspace.id) {
        setCanvas(null);
        setContent(null);
        setComments([]);
        setSnapshots([]);
        setTemplates([]);
        setLoadStatus("not_found");
        return;
      }

      const [loadedComments, loadedSnapshots, loadedTemplates] = await Promise.all([
        listCanvasComments(auth.client, loadedCanvas.id, signal),
        listCanvasSnapshots(auth.client, loadedCanvas.id, signal),
        activeWorkspace?.id ? listTemplates(auth.client, activeWorkspace.id, signal) : Promise.resolve([])
      ]);

      setCanvas(loadedCanvas);
      canvasRef.current = loadedCanvas;
      setContent(loadedCanvas.content);
      contentRef.current = loadedCanvas.content;
      saveQueueRef.current = [];
      setComments(loadedComments);
      setSnapshots(loadedSnapshots);
      setTemplates(loadedTemplates);
      setSelection([]);
      setActiveThreadId(null);
      setLoadStatus("ready");
      setSyncState("saved");
    } catch (error) {
      if (error instanceof AppError && error.code === "REQUEST_ABORTED") {
        return;
      }

      const appError = error instanceof AppError ? error : new AppError({ status: 0, code: "CANVAS_LOAD_FAILED", message: "Canvas could not be loaded.", retryable: true });
      setLoadError(appError);
      setLoadStatus(loadStatusFromError(appError));
    }
  }, [activeWorkspace?.id, auth.client, canvasId]);

  useEffect(() => {
    const controller = new AbortController();
    void loadCanvas(controller.signal);
    return () => controller.abort();
  }, [loadCanvas, outlet.cacheSerial]);

  useEffect(() => {
    if (!canvas || canvas.id !== canvasId || !activeWorkspace?.id || !auth.accessToken) {
      setRealtimeStatus({ state: "offline", label: "Realtime offline", version: null, memberCount: null });
      setSyncState((current) => (current === "saved" ? "offline" : current));
      return;
    }

    const realtimeUrl = resolveRealtimeUrl(activeWorkspace.id);
    if (!realtimeUrl) {
      setRealtimeStatus({ state: "failed", label: "Realtime not configured", version: null, memberCount: null, detail: "Set VITE_REALTIME_URL to the approved realtime entrypoint." });
      setSyncState((current) => (current === "saved" ? "offline" : current));
      return;
    }

    const adapter = createCanvasRealtimeAdapter({
      url: realtimeUrl,
      token: auth.accessToken,
      canvasId: canvas.id
    });
    realtimeRef.current = adapter;
    const unsubscribeStatus = adapter.onStatus((status) => {
      setRealtimeStatus(status);
      setSyncState((current) => {
        if (current === "conflict" || current === "failed" || current === "saving") {
          return current;
        }

        if (status.state === "connected") {
          return "saved";
        }

        if (status.state === "reconnecting" || status.state === "connecting") {
          return "reconnecting";
        }

        return "offline";
      });
    });
    const unsubscribeOperations = adapter.onRemoteOperation((message) => {
      setContent((current) => {
        const localViewport = contentRef.current?.viewport ?? current?.viewport;
        const nextContent = current && localViewport ? withViewport(applyCanvasOperations(current, message.ops), localViewport) : current;
        contentRef.current = nextContent;
        return nextContent;
      });
      setCanvas((current) => {
        const nextCanvas = current ? { ...current, content: contentRef.current ?? current.content, version: Math.max(current.version, message.version), lockVersion: Math.max(current.lockVersion, message.lockVersion ?? current.lockVersion) } : current;
        canvasRef.current = nextCanvas;
        return nextCanvas;
      });
      setSyncState("saved");
    });
    const unsubscribePresence = adapter.onPresence((remotePresence) => {
      setPresence((current) => [remotePresence, ...current.filter((item) => item.id !== remotePresence.id)].slice(0, 8));
    });
    adapter.connect();

    return () => {
      unsubscribeStatus();
      unsubscribeOperations();
      unsubscribePresence();
      adapter.disconnect();
      realtimeRef.current = null;
    };
  }, [activeWorkspace?.id, auth.accessToken, canvas?.id, canvasId]);

  const validation = useMemo(() => (content ? validateCanvas(content, comments) : []), [comments, content]);
  const selectedElements = useMemo(() => {
    if (!content) {
      return [];
    }

    const selected = new Set(selection);
    return content.elements.filter((element) => selected.has(element.id));
  }, [content, selection]);
  const selectedElement = selectedElements.length === 1 ? selectedElements[0] : null;
  const topLevelComments = useMemo(() => comments.filter((comment) => !comment.parentId), [comments]);
  const activeThread = useMemo(() => comments.find((comment) => comment.id === activeThreadId) ?? topLevelComments[0] ?? null, [activeThreadId, comments, topLevelComments]);

  async function flushCanvasSaveQueue() {
    if (saveLoopRunningRef.current) {
      return;
    }

    saveLoopRunningRef.current = true;
    let conflictRetryUsed = false;
    let restartQueuedWork = true;

    try {
      while (saveQueueRef.current.length > 0) {
        const currentCanvas = canvasRef.current;
        const currentContent = contentRef.current;

        if (!currentCanvas || !currentContent || !canEditCanvas) {
          saveQueueRef.current = [];
          return;
        }

        const ops = saveQueueRef.current;
        saveQueueRef.current = [];

        try {
          const updated = await updateCanvas(auth.client, currentCanvas.id, {
            content: currentContent,
            expectedVersion: currentCanvas.version,
            expectedLockVersion: currentCanvas.lockVersion
          });
          const latestContent = contentRef.current ?? updated.content;
          const nextCanvas = { ...updated, content: latestContent };
          const realtime = realtimeRef.current;

          canvasRef.current = nextCanvas;
          setCanvas(nextCanvas);
          setSyncError(null);
          conflictRetryUsed = false;

          if (realtime) {
            const seq = seqRef.current + 1;
            seqRef.current = seq;
            void realtime.sendOperations(ops, updated.version, updated.lockVersion, seq);
          }

          setSyncState(saveQueueRef.current.length > 0 ? "saving" : realtimeStatusRef.current.state === "connected" ? "saved" : "offline");
        } catch (error) {
          const appError = error instanceof AppError ? error : new AppError({ status: 0, code: "CANVAS_SAVE_FAILED", message: "Canvas changes could not be saved.", retryable: true });

          if (appError.status === 409 && !conflictRetryUsed) {
            conflictRetryUsed = true;

            try {
              const latestCanvas = await getCanvas(auth.client, currentCanvas.id);
              const pendingOps = [...ops, ...saveQueueRef.current];
              const rebasedContent = withViewport(applyCanvasOperations(latestCanvas.content, pendingOps), currentContent.viewport);
              const nextCanvas = { ...latestCanvas, content: rebasedContent };

              saveQueueRef.current = pendingOps;
              canvasRef.current = nextCanvas;
              contentRef.current = rebasedContent;
              setCanvas(nextCanvas);
              setContent(rebasedContent);
              setSyncError(null);
              setSyncState("saving");
              continue;
            } catch (reloadError) {
              const reloadAppError = reloadError instanceof AppError ? reloadError : appError;
              saveQueueRef.current = [...ops, ...saveQueueRef.current];
              setSyncError(reloadAppError);
              setSyncState("conflict");
              restartQueuedWork = false;
              return;
            }
          }

          saveQueueRef.current = [...ops, ...saveQueueRef.current];
          setSyncError(appError);
          setSyncState(appError.status === 409 ? "conflict" : "failed");
          restartQueuedWork = false;
          return;
        }
      }

      setSyncState(realtimeStatusRef.current.state === "connected" ? "saved" : "offline");
    } finally {
      saveLoopRunningRef.current = false;
      if (restartQueuedWork && saveQueueRef.current.length > 0) {
        void flushCanvasSaveQueue();
      }
    }
  }

  function applyLocalCanvasOperations(ops: CanvasOperation[]) {
    const baseContent = contentRef.current;

    if (!baseContent || ops.length === 0) {
      return baseContent;
    }

    const nextContent = applyCanvasOperations(baseContent, ops);
    contentRef.current = nextContent;
    setContent((current) => {
      if (!current) {
        return current;
      }

      if (current === baseContent) {
        return nextContent;
      }

      const updatedContent = withViewport(applyCanvasOperations(current, ops), nextContent.viewport);
      contentRef.current = updatedContent;
      return updatedContent;
    });

    return nextContent;
  }

  function commitCanvasOperations(ops: CanvasOperation[], nextSelection = selection) {
    const currentCanvas = canvasRef.current;
    const nextContent = applyLocalCanvasOperations(ops);

    setSelection(nextSelection);

    if (!currentCanvas || !nextContent || !canEditCanvas || ops.length === 0) {
      return;
    }

    saveQueueRef.current = [...saveQueueRef.current, ...ops];
    setSyncState("saving");
    setSyncError(null);
    void flushCanvasSaveQueue();
    window.setTimeout(() => {
      if (!saveLoopRunningRef.current && saveQueueRef.current.length > 0) {
        void flushCanvasSaveQueue();
      }
    }, 0);
  }

  function addElement(type: CanvasElementType) {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const baseElement = createCanvasElement(type, currentContent.elements.length, { zIndex: currentContent.elements.length + 1 });
    const element = centerElementInViewport(baseElement, currentContent, currentViewportSize());
    const op: CanvasOperation = { type: "add", id: element.id, element };
    void commitCanvasOperations([op], [element.id]);

    if (type === "text") {
      setEditingTextElementId(element.id);
    }
  }

  function handleCanvasToolButtonClick(item: CanvasToolButton) {
    if (item.spawn) {
      addElement(item.tool);
      setTool("select");
      return;
    }

    setTool(item.tool);

    if (item.tool === "comment") {
      setActivePanel("comments");
    }
  }

  function selectCanvasElement(elementId: string, mode: "replace" | "toggle" | "append" = "replace") {
    setSelection((current) => selectElement(current, elementId, mode));
    void realtimeRef.current?.sendPresence({ cursor: coordinate, selection: selectElement(selection, elementId, mode), status: "online" });
  }

  function clearSelection() {
    setSelection([]);
    void realtimeRef.current?.sendPresence({ cursor: coordinate, selection: [], status: "online" });
  }

  function applyMove(dx: number, dy: number) {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const result = moveSelection(currentContent, selection, dx, dy);
    void commitCanvasOperations(result.ops);
  }

  function duplicateSelected() {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const result = duplicateSelection(currentContent, selection);
    void commitCanvasOperations(result.ops, result.selection);
  }

  function deleteSelected() {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const result = deleteSelection(currentContent, selection);
    void commitCanvasOperations(result.ops, result.selection);
  }

  function setLocked(locked: boolean) {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const result = setSelectionLocked(currentContent, selection, locked);
    void commitCanvasOperations(result.ops);
  }

  function updateSelectedStyle(style: Parameters<typeof updateSelectionStyle>[2]) {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const result = updateSelectionStyle(currentContent, selection, style);
    void commitCanvasOperations(result.ops);
  }

  function updateSelectedGeometry(changes: Parameters<typeof updateSelectionGeometry>[2]) {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const result = updateSelectionGeometry(currentContent, selection, changes);
    void commitCanvasOperations(result.ops);
  }

  function updateSelectedData(data: Partial<CanvasElement["data"]>) {
    const currentContent = contentRef.current;
    const currentSelectedElement = currentContent?.elements.find((element) => element.id === selectedElement?.id);

    if (!currentContent || !currentSelectedElement || !canEditCanvas) {
      return;
    }

    const nextData = Object.entries(data).reduce<CanvasElement["data"]>((current, [key, value]) => {
      if (value === undefined) {
        delete current[key];
        return current;
      }

      current[key] = value;
      return current;
    }, { ...currentSelectedElement.data });
    const op: CanvasOperation = { type: "update", id: currentSelectedElement.id, changes: { data: nextData } };
    void commitCanvasOperations([op]);
  }

  function updateSelectedConnectorEndpoint(handle: ConnectorEndpoint, changes: Partial<CanvasPoint>) {
    const currentContent = contentRef.current;
    const currentSelectedElement = currentContent?.elements.find((element) => element.id === selectedElement?.id);

    if (!currentContent || !currentSelectedElement || !canEditCanvas || currentSelectedElement.locked || !isLineElement(currentSelectedElement)) {
      return;
    }

    const currentPoint = pointFromData(currentSelectedElement.data[handle], endpointFallback(currentSelectedElement, handle));
    const op: CanvasOperation = {
      type: "update",
      id: currentSelectedElement.id,
      changes: updateConnectorEndpoint(currentSelectedElement, handle, { ...currentPoint, ...changes })
    };

    void commitCanvasOperations([op]);
  }

  function moveZOrder(direction: "front" | "back") {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const result = updateZOrder(currentContent, selection, direction);
    void commitCanvasOperations(result.ops);
  }

  function setGroup(grouped: boolean) {
    const currentContent = contentRef.current;

    if (!currentContent || !canEditCanvas) {
      return;
    }

    const groupId = grouped ? `group-${Date.now()}` : null;
    const selected = new Set(selection);
    const ops = currentContent.elements.flatMap((element) => selected.has(element.id) && !element.locked ? [{ type: "update" as const, id: element.id, changes: { groupId } }] : []);
    void commitCanvasOperations(ops);
  }

  function changeViewport(nextViewport: CanvasContent["viewport"]) {
    applyViewportChange(nextViewport, false);
  }

  function toggleFullscreen() {
    setFullscreen((current) => {
      if (!current) {
        previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      }

      return !current;
    });
  }

  function currentViewportSize() {
    const viewportRect = viewportRef.current?.getBoundingClientRect();
    return {
      width: viewportRect?.width || viewportRef.current?.clientWidth || 960,
      height: viewportRect?.height || viewportRef.current?.clientHeight || 672
    };
  }

  function zoomToolbar(delta: number) {
    const currentContent = contentRef.current;

    if (!currentContent) {
      return;
    }

    changeViewport(zoomViewportAroundCenter(
      currentContent.viewport,
      currentViewportSize(),
      currentContent.viewport.zoom + delta
    ));
  }

  function applyViewportChange(nextViewport: CanvasContent["viewport"], persist: boolean) {
    const currentContent = contentRef.current;

    if (!currentContent) {
      return;
    }

    const result = updateViewport(currentContent, nextViewport);

    if (persist) {
      void commitCanvasOperations(result.ops);
      return;
    }

    applyLocalCanvasOperations(result.ops);
  }

  function commitCanvasSurfaceOperations(ops: CanvasOperation[], nextSelection = selection) {
    const currentContent = contentRef.current;

    if (!currentContent || ops.length === 0) {
      return;
    }

    void commitCanvasOperations(ops, nextSelection);
  }

  function updateCanvasCoordinate(nextCoordinate: { x: number; y: number }) {
    setCoordinate(nextCoordinate);
    void realtimeRef.current?.sendPresence({ cursor: nextCoordinate, selection, status: "online" });
  }

  function updateCanvasSelection(nextSelection: string[]) {
    setSelection(nextSelection);
    void realtimeRef.current?.sendPresence({ cursor: coordinate, selection: nextSelection, status: "online" });
  }

  function toggleGrid() {
    const currentContent = contentRef.current;

    if (!currentContent) {
      return;
    }

    const op: CanvasOperation = { type: "update-state", state: { metadata: { gridEnabled: !currentContent.metadata.gridEnabled } } };
    const nextContent = applyCanvasOperations(currentContent, [op]);
    contentRef.current = nextContent;
    setContent(nextContent);
  }

  function keyboardMove(event: KeyboardEvent<HTMLElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      clearSelection();
      return;
    }

    if ((event.key === "Delete" || event.key === "Backspace") && selection.length > 0) {
      event.preventDefault();
      deleteSelected();
      return;
    }

    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "s") {
      event.preventDefault();
      void createSnapshot();
      return;
    }

    if (event.key.toLowerCase() === "c" && canComment) {
      event.preventDefault();
      setActivePanel("comments");
      setTool("comment");
      return;
    }

    const step = event.shiftKey ? 32 : 8;
    const moves: Record<string, [number, number]> = {
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0]
    };
    const move = moves[event.key];

    if (move && selection.length > 0) {
      event.preventDefault();
      applyMove(move[0], move[1]);
    }
  }

  useEffect(() => {
    function handleGlobalDelete(event: globalThis.KeyboardEvent) {
      if (event.defaultPrevented || editingTextElementId || isEditableKeyboardTarget(event.target)) {
        return;
      }

      if ((event.key === "Delete" || event.key === "Backspace") && selection.length > 0 && canEditCanvas) {
        event.preventDefault();
        deleteSelected();
      }
    }

    window.addEventListener("keydown", handleGlobalDelete);
    return () => window.removeEventListener("keydown", handleGlobalDelete);
  }, [canEditCanvas, editingTextElementId, selection]);

  async function submitComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!canvas || !canComment || !commentDraft.trim()) {
      return;
    }

    const targetElementId = selection.length === 1 ? selection[0] : null;
    try {
      const created = await createCanvasComment(auth.client, canvas.id, { content: commentDraft.trim(), targetElementId });
      setComments((current) => [created, ...current]);
      setCommentDraft("");
      setActiveThreadId(created.id);
      outlet.pushToast("Comment saved");
    } catch (error) {
      setSyncError(error instanceof AppError ? error : new AppError({ status: 0, code: "COMMENT_SAVE_FAILED", message: "Comment could not be saved.", retryable: true }));
    }
  }

  async function submitReply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!canvas || !activeThread || !canComment || !replyDraft.trim()) {
      return;
    }

    const created = await createCanvasComment(auth.client, canvas.id, { content: replyDraft.trim(), parentId: activeThread.id, targetElementId: activeThread.targetElementId });
    setComments((current) => [...current, created]);
    setReplyDraft("");
  }

  async function saveCommentEdit(comment: CommentResponse) {
    if (!canvas || !editingDraft.trim()) {
      return;
    }

    const updated = await updateCanvasComment(auth.client, canvas.id, comment.id, { content: editingDraft.trim() });
    setComments((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    setEditingCommentId(null);
    setEditingDraft("");
  }

  async function resolveComment(comment: CommentResponse, resolved: boolean) {
    if (!canvas) {
      return;
    }

    const updated = await updateCanvasComment(auth.client, canvas.id, comment.id, { resolved });
    setComments((current) => current.map((item) => (item.id === updated.id ? updated : item)));
  }

  async function moveCommentPin(commentId: string, position: CanvasPoint) {
    if (!canvas || !canComment) {
      return;
    }

    const nextPosition = { x: Math.round(position.x * 10) / 10, y: Math.round(position.y * 10) / 10 };
    setComments((current) => current.map((item) => (item.id === commentId ? { ...item, position: nextPosition } : item)));

    try {
      const updated = await updateCanvasComment(auth.client, canvas.id, commentId, { position: nextPosition });
      setComments((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (error) {
      setSyncError(error instanceof AppError ? error : new AppError({ status: 0, code: "COMMENT_MOVE_FAILED", message: "Comment pin position could not be saved.", retryable: true }));
    }
  }

  async function removeComment(comment: CommentResponse) {
    if (!canvas) {
      return;
    }

    await deleteCanvasComment(auth.client, canvas.id, comment.id);
    setComments((current) => current.filter((item) => item.id !== comment.id && item.parentId !== comment.id));
    if (activeThreadId === comment.id) {
      setActiveThreadId(null);
    }
  }

  async function createSnapshot() {
    if (!canvas || !canEditCanvas) {
      return;
    }

    const snapshot = await createCanvasSnapshot(auth.client, canvas.id);
    setSnapshots((current) => [snapshot, ...current.filter((item) => item.id !== snapshot.id)]);
    outlet.pushToast(`Snapshot v${snapshot.version} created`);
  }

  async function restoreSnapshot() {
    if (!canvas || !restoreTarget || !canEditCanvas) {
      return;
    }

    const restored = await restoreCanvasSnapshot(auth.client, canvas.id, restoreTarget.version);
    setCanvas(restored);
    setContent(restored.content);
    setSelection([]);
    setRestoreTarget(null);
    setSyncState("saved");
    outlet.pushToast(`Restored snapshot v${restoreTarget.version}`);
  }

  async function submitTemplate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!canvas || !content || !activeWorkspace?.id || !canEditCanvas || !templateName.trim()) {
      return;
    }

    const template = await saveTemplate(auth.client, activeWorkspace.id, {
      name: templateName.trim(),
      category: "workspace",
      content,
      canvasId: canvas.id
    });
    setTemplates((current) => [template, ...current.filter((item) => item.id !== template.id)]);
    setTemplateName("");
  }

  async function createFromTemplate(template: TemplateResponse) {
    if (!activeWorkspace?.id || !canEditCanvas) {
      return;
    }

    const created = await createCanvas(auth.client, {
      workspaceId: activeWorkspace.id,
      title: `${template.name} canvas`,
      templateId: template.id
    });
    navigate(`/w/${workspaceSlug}/c/${created.id}`);
  }

  async function handleImport(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    setImportError(null);

    if (!file || !activeWorkspace?.id) {
      return;
    }

    if (file.size > 100 * 1024 * 1024) {
      setImportError("Import rejected: file exceeds 100 MB.");
      return;
    }

    try {
      const response = await importCanvasFile({ accessToken: auth.accessToken, file, workspaceId: activeWorkspace.id, title: file.name.replace(/\.[^.]+$/, "") || "Imported canvas" });

      if (response.canvasId) {
        navigate(`/w/${workspaceSlug}/c/${response.canvasId}`);
        return;
      }

      if (response.jobId) {
        setJobNotice(`Import job queued: ${response.jobId}`);
      }
    } catch (error) {
      const appError = error instanceof AppError ? error : new AppError({ status: 0, code: "IMPORT_FAILED", message: "Import failed.", retryable: true });
      setImportError(appError.status === 413 ? "Import rejected: the file is too large." : appError.message);
    }
  }

  async function handleExport() {
    if (!canvas) {
      return;
    }

    try {
      const result = await exportCanvasFile({ accessToken: auth.accessToken, canvasId: canvas.id, format: exportFormat });

      if (result.kind === "job") {
        setJobNotice(`Export job queued: ${result.job.jobId}`);
        return;
      }

      const url = URL.createObjectURL(result.blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = result.filename;
      link.click();
      URL.revokeObjectURL(url);
      setJobNotice(`Export ready: ${result.filename}`);
    } catch (error) {
      const appError = error instanceof AppError ? error : new AppError({ status: 0, code: "EXPORT_FAILED", message: "Export failed.", retryable: true });
      setJobNotice(appError.message);
    }
  }

  if (!canViewCanvas) {
    return <PermissionDeniedState error={new AppError({ status: 403, code: "PERMISSION_DENIED", message: "Your workspace role cannot view this canvas.", retryable: false })} />;
  }

  if (loadStatus === "loading") {
    return <LoadingState label="Canvas loading" />;
  }

  if (loadStatus === "permission_denied") {
    return <PermissionDeniedState error={loadError} />;
  }

  if (loadStatus === "not_found") {
    return (
      <EmptyState
        title="Canvas not found"
        copy="The canvas id in this route does not match an accessible canvas in the current workspace."
        action={
          <Link className="button button-secondary" to={`/w/${workspaceSlug}/c`}>
            Back to canvases
          </Link>
        }
      />
    );
  }

  if (loadStatus === "deleted") {
    return <ErrorState title="Canvas deleted" copy="This canvas was deleted and cannot be edited from the live workbench." requestId={loadError?.requestId} />;
  }

  if (loadStatus === "error" && loadError) {
    return <RouteAppErrorState error={loadError} onRetry={() => void loadCanvas()} />;
  }

  if (!canvas || !content) {
    return <ErrorState title="Canvas unavailable" copy="Canvas state could not be prepared for rendering." />;
  }

  return (
    <section className="canvas-route" aria-labelledby="canvas-title">
      <div className={`canvas-workbench ${fullscreen ? "is-fullscreen" : ""}`}>
        <div className="canvas-workbench-header">
          <div>
            <span className="kicker">Canvas workbench</span>
            <h1 id="canvas-title">{canvas.title}</h1>
            <p>
              Workspace {workspaceSlug} · canvas/{canvas.id} · version {canvas.version} · updated {formatDate(canvas.updatedAt)}
            </p>
          </div>
          <div className="canvas-header-right">
            <div className="canvas-header-actions" aria-label="Canvas actions">
              <Button variant="secondary" icon={<Camera size={16} aria-hidden="true" />} disabled={!canEditCanvas} onClick={() => void createSnapshot()}>
                Snapshot
              </Button>
              <input ref={fileInputRef} className="visually-hidden" type="file" accept=".svg,.drawio,.xml,.json" aria-label="Import canvas file" onChange={(event) => void handleImport(event)} />
              <Button variant="secondary" icon={<Upload size={16} aria-hidden="true" />} disabled={!canEditCanvas} onClick={() => fileInputRef.current?.click()}>
                Import
              </Button>
              <Select className="export-format-select" value={exportFormat} onChange={(event) => setExportFormat(event.target.value as typeof exportFormat)} aria-label="Export format">
                <option value="svg">SVG</option>
                <option value="png">PNG</option>
                <option value="pdf">PDF</option>
                <option value="json">JSON</option>
              </Select>
              <Button variant="secondary" icon={<Download size={16} aria-hidden="true" />} onClick={() => void handleExport()}>
                Export
              </Button>
            </div>
            <div className="canvas-status-strip canvas-status-row" aria-label="Canvas status">
              <StatusChip tone={syncTone(syncState)}>{syncLabel(syncState)}</StatusChip>
              <StatusChip tone={realtimeStatus.state === "connected" ? "healthy" : realtimeStatus.state === "failed" ? "danger" : "warning"}>{realtimeStatus.label}</StatusChip>
              <StatusChip tone={canEditCanvas ? "healthy" : canComment ? "warning" : "neutral"}>{mode}</StatusChip>
            </div>
          </div>
        </div>

        <div className="canvas-main-grid">
          <PaperSurface className={`canvas-editor-shell ${fullscreen ? "is-fullscreen" : ""}`} as="article">
            <div className="canvas-viewport-wrap" ref={viewportRef} tabIndex={0} role="application" aria-label="Canvas viewport" onKeyDown={keyboardMove}>
              {content.elements.length === 0 ? <EmptyCanvasOverlay canEdit={canEditCanvas} onCreate={() => {
                addElement("rectangle");
                setTool("select");
              }} /> : null}
              <div className="canvas-tool-rail" aria-label="Canvas tools">
                {canvasToolGroups.map((group) => (
                  <div className="canvas-tool-group" key={group.label} aria-label={group.label}>
                    {group.items.map((item) => {
                      const enabled = item.permission === "edit" ? canEditCanvas : canComment;
                      return (
                        <IconButton
                          className={`canvas-tool-button ${tool === item.tool ? "is-active" : ""}`}
                          key={item.tool}
                          label={item.label}
                          icon={item.icon}
                          disabled={!enabled}
                          aria-pressed={tool === item.tool}
                          onClick={() => handleCanvasToolButtonClick(item)}
                        />
                      );
                    })}
                    {group.label === "Selection tools" ? (
                      <IconButton
                        className="canvas-tool-button canvas-tool-delete"
                        label="Delete selection"
                        icon={<Trash2 size={16} aria-hidden="true" />}
                        disabled={!canEditCanvas || selection.length === 0}
                        onClick={deleteSelected}
                      />
                    ) : null}
                  </div>
                ))}
              </div>
              <div className="canvas-camera-controls" aria-label="Canvas camera controls">
                <IconButton className="canvas-camera-button" label="Zoom out" icon={<ZoomOut size={16} aria-hidden="true" />} onClick={() => zoomToolbar(-0.1)} />
                <IconButton className="canvas-camera-button" label="Zoom in" icon={<ZoomIn size={16} aria-hidden="true" />} onClick={() => zoomToolbar(0.1)} />
                <IconButton className="canvas-camera-button" label="Fit to content" icon={<ScanSearch size={16} aria-hidden="true" />} onClick={() => changeViewport(fitViewportToContent(content, currentViewportSize()))} />
                <IconButton className="canvas-camera-button" label={content.metadata.gridEnabled ? "Hide grid" : "Show grid"} icon={<Grid3X3 size={16} aria-hidden="true" />} aria-pressed={content.metadata.gridEnabled} onClick={toggleGrid} />
                <IconButton className="canvas-camera-button canvas-fullscreen-button" label={fullscreen ? "Exit fullscreen" : "Canvas fullscreen"} icon={fullscreen ? <Minimize2 size={16} aria-hidden="true" /> : <Maximize2 size={16} aria-hidden="true" />} aria-pressed={fullscreen} onClick={toggleFullscreen} />
              </div>
              <CanvasSurface
                content={content}
                selection={selection}
                title={canvas.title}
                canEdit={canEditCanvas}
                tool={tool}
                onCoordinateChange={updateCanvasCoordinate}
                onSelectionChange={updateCanvasSelection}
                onCommitOperations={commitCanvasSurfaceOperations}
                onViewportChange={applyViewportChange}
                editingTextElementId={editingTextElementId}
                onTextEditingChange={setEditingTextElementId}
              />
              <CanvasOverlays content={content} comments={topLevelComments} presence={presence} selection={selection} activeThreadId={activeThreadId} canMoveComments={canComment} onMoveComment={(commentId, position) => void moveCommentPin(commentId, position)} onOpenThread={(commentId) => {
                setActiveThreadId(commentId);
                setActivePanel("comments");
              }} />
              <div className="canvas-readout" role="status">
                <span>x {coordinate.x} y {coordinate.y}</span>
                <span>{Math.round(content.viewport.zoom * 100)}%</span>
                <span>{content.elements.length} elements</span>
                <span>{cacheKey.join(" / ")}</span>
              </div>
            </div>

            <aside className="canvas-side-panel" aria-label="Canvas workbench panels">
              <div className="panel-tabs" role="tablist" aria-label="Canvas panels">
                {(["inspector", "comments", "snapshots", "templates"] as ActivePanel[]).map((panel) => (
                  <button className={activePanel === panel ? "is-active" : ""} key={panel} type="button" role="tab" aria-selected={activePanel === panel} onClick={() => setActivePanel(panel)}>
                    {panel}
                  </button>
                ))}
              </div>
              {activePanel === "inspector" ? (
                <InspectorPanel
                  canvas={canvas}
                  content={content}
                  selectedElements={selectedElements}
                  canEdit={canEditCanvas}
                  validation={validation}
                  syncError={syncError}
                  onClear={clearSelection}
                  onDuplicate={duplicateSelected}
                  onDelete={deleteSelected}
                  onLock={() => setLocked(true)}
                  onUnlock={() => setLocked(false)}
                  onFront={() => moveZOrder("front")}
                  onBack={() => moveZOrder("back")}
                  onGroup={() => setGroup(true)}
                  onUngroup={() => setGroup(false)}
                  onStyle={updateSelectedStyle}
                  onGeometry={updateSelectedGeometry}
                  onData={updateSelectedData}
                  onEndpoint={updateSelectedConnectorEndpoint}
                  onSelectElement={(elementId) => selectCanvasElement(elementId)}
                />
              ) : null}
              {activePanel === "comments" ? (
                <CommentsPanel
                  comments={comments}
                  content={content}
                  activeThread={activeThread}
                  canComment={canComment}
                  selectedElementId={selection.length === 1 ? selection[0] : null}
                  commentDraft={commentDraft}
                  replyDraft={replyDraft}
                  editingCommentId={editingCommentId}
                  editingDraft={editingDraft}
                  onDraft={setCommentDraft}
                  onReplyDraft={setReplyDraft}
                  onSubmit={submitComment}
                  onSubmitReply={submitReply}
                  onOpenThread={setActiveThreadId}
                  onStartEdit={(comment) => {
                    setEditingCommentId(comment.id);
                    setEditingDraft(comment.content);
                  }}
                  onEditDraft={setEditingDraft}
                  onSaveEdit={(comment) => void saveCommentEdit(comment)}
                  onCancelEdit={() => {
                    setEditingCommentId(null);
                    setEditingDraft("");
                  }}
                  onResolve={(comment) => void resolveComment(comment, true)}
                  onReopen={(comment) => void resolveComment(comment, false)}
                  onDelete={(comment) => void removeComment(comment)}
                />
              ) : null}
              {activePanel === "snapshots" ? <SnapshotsPanel snapshots={snapshots} canEdit={canEditCanvas} onCreate={() => void createSnapshot()} onRestore={setRestoreTarget} /> : null}
              {activePanel === "templates" ? (
                <TemplatesPanel templates={templates} canEdit={canEditCanvas} templateName={templateName} onTemplateName={setTemplateName} onSubmit={submitTemplate} onCreateFromTemplate={(template) => void createFromTemplate(template)} />
              ) : null}
            </aside>
          </PaperSurface>
        </div>

        {importError ? <div className="canvas-alert danger" role="alert">{importError}</div> : null}
        {jobNotice ? (
          <div className="canvas-alert" role="status">
            <span>{jobNotice}</span>
            <Link to={`/w/${workspaceSlug}/jobs`}>Open jobs</Link>
          </div>
        ) : null}
      </div>

      <Dialog open={Boolean(restoreTarget)} title="Restore snapshot" onClose={() => setRestoreTarget(null)}>
        <div className="dialog-content-grid">
          <p>Restore version {restoreTarget?.version}. Current canvas content will be replaced through the Core restore endpoint.</p>
          <div className="dialog-actions">
            <Button variant="ghost" onClick={() => setRestoreTarget(null)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={() => void restoreSnapshot()}>
              Confirm restore
            </Button>
          </div>
        </div>
      </Dialog>
    </section>
  );
}

type CommentDragState = {
  commentId: string;
  pointerId: number;
  position: CanvasPoint;
  offset: CanvasPoint;
  moved: boolean;
};

function CanvasOverlays({ content, comments, presence, selection, activeThreadId, canMoveComments, onMoveComment, onOpenThread }: { content: CanvasContent; comments: CommentResponse[]; presence: RemotePresence[]; selection: string[]; activeThreadId: string | null; canMoveComments: boolean; onMoveComment: (commentId: string, position: CanvasPoint) => void; onOpenThread: (commentId: string) => void }) {
  const [dragState, setDragState] = useState<CommentDragState | null>(null);
  const suppressedClickRef = useRef<string | null>(null);
  const elementMap = new Map(content.elements.map((element) => [element.id, element]));

  function eventCanvasPosition(event: ReactPointerEvent<HTMLElement>) {
    const viewport = event.currentTarget.closest<HTMLElement>(".canvas-viewport-wrap");
    const rect = viewport?.getBoundingClientRect();
    if (!rect) {
      return null;
    }
    return {
      x: (event.clientX - rect.left - content.viewport.panX) / content.viewport.zoom,
      y: (event.clientY - rect.top - content.viewport.panY) / content.viewport.zoom
    };
  }

  function startCommentDrag(event: ReactPointerEvent<HTMLButtonElement>, commentId: string, position: CanvasPoint) {
    if (!canMoveComments || event.button !== 0) {
      return;
    }

    const pointerPosition = eventCanvasPosition(event);
    if (!pointerPosition) {
      return;
    }

    event.currentTarget.setPointerCapture?.(event.pointerId);
    setDragState({
      commentId,
      pointerId: event.pointerId,
      position,
      offset: { x: pointerPosition.x - position.x, y: pointerPosition.y - position.y },
      moved: false
    });
  }

  function updateCommentDrag(event: ReactPointerEvent<HTMLButtonElement>) {
    if (!dragState || dragState.pointerId !== event.pointerId) {
      return;
    }

    const position = eventCanvasPosition(event);
    if (!position) {
      return;
    }

    event.preventDefault();
    setDragState((current) => current && current.pointerId === event.pointerId ? { ...current, position: { x: position.x - current.offset.x, y: position.y - current.offset.y }, moved: true } : current);
  }

  function finishCommentDrag(event: ReactPointerEvent<HTMLButtonElement>) {
    if (!dragState || dragState.pointerId !== event.pointerId) {
      return;
    }

    const nextDragState = dragState;
    setDragState(null);
    event.currentTarget.releasePointerCapture?.(event.pointerId);
    if (nextDragState.moved) {
      suppressedClickRef.current = nextDragState.commentId;
      event.preventDefault();
      onMoveComment(nextDragState.commentId, nextDragState.position);
    }
  }

  function cancelCommentDrag(event: ReactPointerEvent<HTMLButtonElement>) {
    if (dragState?.pointerId === event.pointerId) {
      setDragState(null);
    }
  }

  return (
    <div className="canvas-overlay-layer" aria-hidden="false">
      {comments.map((comment) => {
        const target = comment.targetElementId ? elementMap.get(comment.targetElementId) : null;
        const center = dragState?.commentId === comment.id ? dragState.position : comment.position ?? (target ? elementCenter(target) : { x: 24, y: 24 + comments.indexOf(comment) * 42 });
        const left = center.x * content.viewport.zoom + content.viewport.panX;
        const top = center.y * content.viewport.zoom + content.viewport.panY;
        const targetLabel = target ? `on ${target.type}` : comment.targetElementId ? `with missing target ${comment.targetElementId}` : "on general canvas";
        const status = comment.resolved ? "Resolved" : "Open";
        const excerpt = commentExcerpt(comment.content);
        const descriptionId = `comment-pin-${comment.id}`;
        const isDragging = dragState?.commentId === comment.id;
        return (
          <button
            className={`canvas-comment-pin ${activeThreadId === comment.id ? "is-active" : ""} ${target || !comment.targetElementId ? "" : "is-missing-target"} ${isDragging ? "is-dragging" : ""}`}
            key={comment.id}
            type="button"
            style={{ left, top }}
            aria-label={`Open comment ${targetLabel}: ${status}. ${excerpt}`}
            aria-describedby={descriptionId}
            onPointerDown={(event) => startCommentDrag(event, comment.id, center)}
            onPointerMove={updateCommentDrag}
            onPointerUp={finishCommentDrag}
            onPointerCancel={cancelCommentDrag}
            onClick={() => {
              if (suppressedClickRef.current === comment.id) {
                suppressedClickRef.current = null;
                return;
              }
              onOpenThread(comment.id);
            }}
          >
            <MessageSquare size={14} aria-hidden="true" />
            <span className="canvas-comment-pin-preview" id={descriptionId} role="tooltip">
              <strong>{status} comment</strong>
              <span>{excerpt}</span>
              <small>{target ? `Target ${target.type}` : comment.targetElementId ? `Missing target ${comment.targetElementId}` : "General canvas"}</small>
            </span>
          </button>
        );
      })}
      {presence.map((remotePresence, index) => {
        if (!remotePresence.cursor) {
          return null;
        }

        const left = remotePresence.cursor.x * content.viewport.zoom + content.viewport.panX;
        const top = remotePresence.cursor.y * content.viewport.zoom + content.viewport.panY;
        return (
          <div className="canvas-presence-cursor" key={remotePresence.id} style={{ left, top, "--cursor-color": cursorColor(index) } as CSSProperties}>
            <span>{initials(remotePresence.name)}</span>
            <strong>{remotePresence.name}</strong>
          </div>
        );
      })}
      {selection.length > 1 ? <span className="multi-select-count">{selection.length} selected</span> : null}
    </div>
  );
}

function commentExcerpt(content: string) {
  const normalized = content.replace(/\s+/g, " ").trim();
  return normalized.length > 96 ? `${normalized.slice(0, 93)}...` : normalized;
}

function InspectorPanel({ canvas, content, selectedElements, canEdit, validation, syncError, onClear, onDuplicate, onDelete, onLock, onUnlock, onFront, onBack, onGroup, onUngroup, onStyle, onGeometry, onData, onEndpoint, onSelectElement }: {
  canvas: CanvasResponse;
  content: CanvasContent;
  selectedElements: CanvasElement[];
  canEdit: boolean;
  validation: string[];
  syncError: AppError | null;
  onClear: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
  onLock: () => void;
  onUnlock: () => void;
  onFront: () => void;
  onBack: () => void;
  onGroup: () => void;
  onUngroup: () => void;
  onStyle: (style: Parameters<typeof updateSelectionStyle>[2]) => void;
  onGeometry: (changes: Parameters<typeof updateSelectionGeometry>[2]) => void;
  onData: (data: Partial<CanvasElement["data"]>) => void;
  onEndpoint: (handle: ConnectorEndpoint, changes: Partial<CanvasPoint>) => void;
  onSelectElement: (elementId: string) => void;
}) {
  const selected = selectedElements.length === 1 ? selectedElements[0] : null;
  const fillIsTransparent = isTransparentStyle(selected?.style.fill);
  const strokeIsTransparent = isTransparentStyle(selected?.style.stroke);
  const fillColor = colorInputValue(selected?.style.fill, "#ffffff");
  const strokeColor = colorInputValue(selected?.style.stroke, "#4d7cfe");
  const selectedLineMode = selected && isLineElement(selected) ? lineModeFromElement(selected) : null;
  const endpointStart = selected && isLineElement(selected) ? pointFromData(selected.data.start, endpointFallback(selected, "start")) : null;
  const endpointEnd = selected && isLineElement(selected) ? pointFromData(selected.data.end, endpointFallback(selected, "end")) : null;
  const controlStart = selected && selectedLineMode === "curve" ? pointFromData(selected.data.controlStart, endpointFallback(selected, "controlStart")) : null;
  const controlEnd = selected && selectedLineMode === "curve" ? pointFromData(selected.data.controlEnd, endpointFallback(selected, "controlEnd")) : null;

  return (
    <Panel className="canvas-panel-section" as="section">
      <span className="kicker">Inspector</span>
      {syncError ? <ErrorState title={syncError.status === 409 ? "Conflict" : "Save failed"} copy={syncError.message} requestId={syncError.requestId} /> : null}
      {validation.length > 0 ? (
        <div className="validation-list" role="status">
          <span className="kicker">Validation</span>
          {validation.map((message) => <p key={message}>{message}</p>)}
        </div>
      ) : <StatusChip tone="healthy">content valid</StatusChip>}

      <div className="inspector-list">
        <KeyValue label="Canvas id" value={canvas.id} />
        <KeyValue label="Version" value={`${canvas.version} / lock ${canvas.lockVersion}`} />
        <KeyValue label="Grid" value={content.metadata.gridEnabled ? "visible" : "hidden"} />
        <KeyValue label="Permission" value={canEdit ? "edit allowed" : "read only"} />
      </div>

      {selectedElements.length === 0 ? (
        <div className="element-list" aria-label="Keyboard accessible element selection list">
          {content.elements.map((element) => <button key={element.id} type="button" onClick={() => onSelectElement(element.id)}>{elementLabel(element)}</button>)}
        </div>
      ) : null}

      {selected ? (
        <div className="selected-editor" key={selected.id}>
          <div className="selected-title-row">
            <h2>{selected.type}</h2>
            {selected.locked ? <Badge tone="warning">locked</Badge> : <Badge tone="info">selected</Badge>}
          </div>
          {selected.type === "text" || selected.type === "rectangle" || selected.type === "circle" ? (
            <label className="form-field">
              <span className="field-label">Text data</span>
              <Input defaultValue={String(selected.data.text ?? "")} disabled={!canEdit || selected.locked} onBlur={(event) => onData({ text: event.target.value })} />
            </label>
          ) : null}
          <div className="geometry-grid">
            <NumberField label="X" value={selected.x} disabled={!canEdit || selected.locked} onCommit={(value) => onGeometry({ x: value })} />
            <NumberField label="Y" value={selected.y} disabled={!canEdit || selected.locked} onCommit={(value) => onGeometry({ y: value })} />
            <NumberField label="W" value={selected.width} disabled={!canEdit || selected.locked} onCommit={(value) => onGeometry({ width: value })} />
            <NumberField label="H" value={selected.height} disabled={!canEdit || selected.locked} onCommit={(value) => onGeometry({ height: value })} />
            <NumberField label="Rotation" value={selected.rotation} disabled={!canEdit || selected.locked} onCommit={(value) => onGeometry({ rotation: value })} />
          </div>
          <div className="style-grid">
            <label>
              Fill
              <input type="color" value={fillColor} disabled={!canEdit || selected.locked || fillIsTransparent} onChange={(event) => onStyle({ fill: event.target.value })} />
            </label>
            <label>
              Transparent fill
              <input type="checkbox" checked={fillIsTransparent} disabled={!canEdit || selected.locked} onChange={(event) => onStyle({ fill: event.target.checked ? "transparent" : fillColor })} />
            </label>
            <label>
              Stroke
              <input type="color" value={strokeColor} disabled={!canEdit || selected.locked || strokeIsTransparent} onChange={(event) => onStyle({ stroke: event.target.value })} />
            </label>
            <label>
              Transparent stroke
              <input type="checkbox" checked={strokeIsTransparent} disabled={!canEdit || selected.locked} onChange={(event) => onStyle({ stroke: event.target.checked ? "transparent" : strokeColor })} />
            </label>
            <NumberField label="Stroke width" value={numberStyle(selected.style.strokeWidth, 2)} disabled={!canEdit || selected.locked} onCommit={(value) => onStyle({ strokeWidth: value })} />
            <NumberField label="Opacity" value={numberStyle(selected.style.opacity, 1)} disabled={!canEdit || selected.locked} step={0.1} onCommit={(value) => onStyle({ opacity: Math.max(0, Math.min(1, value)) })} />
          </div>
          {selected.type === "image" ? <ImageDataEditor selected={selected} canEdit={canEdit} onData={onData} /> : null}
          {isLineElement(selected) ? <CurveDataEditor selected={selected} canEdit={canEdit} onData={onData} /> : null}
          {endpointStart && endpointEnd ? (
            <div className="connector-endpoint-grid" aria-label="Connector endpoint coordinates">
              <NumberField label="Start X" value={endpointStart.x} disabled={!canEdit || selected.locked} onCommit={(value) => onEndpoint("start", { x: value })} />
              <NumberField label="Start Y" value={endpointStart.y} disabled={!canEdit || selected.locked} onCommit={(value) => onEndpoint("start", { y: value })} />
              <NumberField label="End X" value={endpointEnd.x} disabled={!canEdit || selected.locked} onCommit={(value) => onEndpoint("end", { x: value })} />
              <NumberField label="End Y" value={endpointEnd.y} disabled={!canEdit || selected.locked} onCommit={(value) => onEndpoint("end", { y: value })} />
              {controlStart && controlEnd ? (
                <>
                  <NumberField label="Control 1 X" value={controlStart.x} disabled={!canEdit || selected.locked} onCommit={(value) => onEndpoint("controlStart", { x: value })} />
                  <NumberField label="Control 1 Y" value={controlStart.y} disabled={!canEdit || selected.locked} onCommit={(value) => onEndpoint("controlStart", { y: value })} />
                  <NumberField label="Control 2 X" value={controlEnd.x} disabled={!canEdit || selected.locked} onCommit={(value) => onEndpoint("controlEnd", { x: value })} />
                  <NumberField label="Control 2 Y" value={controlEnd.y} disabled={!canEdit || selected.locked} onCommit={(value) => onEndpoint("controlEnd", { y: value })} />
                </>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}

      {selectedElements.length > 1 ? <p className="small-copy">{selectedElements.length} elements selected. Inspector actions apply only to editable, unlocked elements.</p> : null}

      <div className="inspector-actions">
        <Button variant="secondary" icon={<X size={16} aria-hidden="true" />} onClick={onClear}>Clear</Button>
        <Button variant="secondary" icon={<Copy size={16} aria-hidden="true" />} disabled={!canEdit || selectedElements.length === 0} onClick={onDuplicate}>Duplicate</Button>
        <Button variant="secondary" icon={<Trash2 size={16} aria-hidden="true" />} disabled={!canEdit || selectedElements.length === 0} onClick={onDelete}>Delete</Button>
        <Button variant="secondary" icon={<Lock size={16} aria-hidden="true" />} disabled={!canEdit || selectedElements.length === 0} onClick={onLock}>Lock</Button>
        <Button variant="secondary" icon={<Unlock size={16} aria-hidden="true" />} disabled={!canEdit || selectedElements.length === 0} onClick={onUnlock}>Unlock</Button>
        <Button variant="secondary" icon={<Layers size={16} aria-hidden="true" />} disabled={!canEdit || selectedElements.length === 0} onClick={onFront}>Front</Button>
        <Button variant="secondary" icon={<Layers size={16} aria-hidden="true" />} disabled={!canEdit || selectedElements.length === 0} onClick={onBack}>Back</Button>
        <Button variant="secondary" icon={<Check size={16} aria-hidden="true" />} disabled={!canEdit || selectedElements.length < 2} onClick={onGroup}>Group</Button>
        <Button variant="secondary" icon={<X size={16} aria-hidden="true" />} disabled={!canEdit || selectedElements.length === 0} onClick={onUngroup}>Ungroup</Button>
      </div>
    </Panel>
  );
}

function CurveDataEditor({ selected, canEdit, onData }: { selected: CanvasElement; canEdit: boolean; onData: (data: Partial<CanvasElement["data"]>) => void }) {
  const disabled = !canEdit || selected.locked;
  const value = typeof selected.data.curvePreset === "string" ? selected.data.curvePreset : selected.type === "curve" ? "dashed" : "solid";

  return (
    <label className="form-field">
      <span className="field-label">Line preset</span>
      <Select value={value} disabled={disabled} onChange={(event) => onData({ curvePreset: event.target.value })}>
        {curvePresetOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </Select>
    </label>
  );
}

function ImageDataEditor({ selected, canEdit, onData }: { selected: CanvasElement; canEdit: boolean; onData: (data: Partial<CanvasElement["data"]>) => void }) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const removedOnPointerRef = useRef(false);
  const skipNextUrlCommitRef = useRef(false);
  const [imageError, setImageError] = useState<string | null>(null);
  const disabled = !canEdit || selected.locked;
  const src = typeof selected.data.src === "string" ? selected.data.src : "";
  const urlDefaultValue = src.startsWith("data:") ? "" : src;

  async function handleImageFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    event.target.value = "";

    if (!file) {
      return;
    }

    if (!supportedImageTypes.has(file.type)) {
      setImageError("Use a PNG, JPEG, GIF, WebP, or SVG image.");
      return;
    }

    if (file.size > maxInlineImageBytes) {
      setImageError("Images must be 2 MB or smaller.");
      return;
    }

    try {
      const dataUrl = await readFileAsDataUrl(file);
      setImageError(null);
      onData({ src: dataUrl, text: selected.data.text ?? file.name });
    } catch {
      setImageError("The image could not be read.");
    }
  }

  function commitImageUrl(value: string) {
    const nextUrl = value.trim();

    if (skipNextUrlCommitRef.current) {
      skipNextUrlCommitRef.current = false;
      return;
    }

    if (nextUrl === urlDefaultValue) {
      setImageError(null);
      return;
    }

    if (!nextUrl) {
      if (!src.startsWith("data:")) {
        onData({ src: undefined });
      }
      setImageError(null);
      return;
    }

    if (!isSupportedImageUrl(nextUrl)) {
      setImageError("Use an http, https, or data:image URL.");
      return;
    }

    setImageError(null);
    onData({ src: nextUrl });
  }

  function removeImage(skipUrlCommit = false) {
    skipNextUrlCommitRef.current = skipUrlCommit;
    setImageError(null);
    onData({ src: undefined });
  }

  return (
    <div className="image-data-editor">
      <input ref={fileInputRef} className="visually-hidden" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/svg+xml" disabled={disabled} aria-label="Upload image file" onChange={(event) => void handleImageFile(event)} />
      <div className="image-action-row">
        <Button variant="secondary" icon={<Upload size={16} aria-hidden="true" />} disabled={disabled} onClick={() => fileInputRef.current?.click()}>
          Upload/Replace image
        </Button>
        <Button variant="ghost" icon={<X size={16} aria-hidden="true" />} disabled={disabled || !src} onPointerDown={(event) => {
          event.preventDefault();
          removedOnPointerRef.current = true;
          removeImage(true);
        }} onMouseDown={(event) => {
          event.preventDefault();
          if (removedOnPointerRef.current) {
            return;
          }

          removedOnPointerRef.current = true;
          removeImage(true);
        }} onClick={() => {
          if (removedOnPointerRef.current) {
            removedOnPointerRef.current = false;
            window.setTimeout(() => {
              skipNextUrlCommitRef.current = false;
            }, 0);
            return;
          }

          removeImage();
        }}>
          Remove image
        </Button>
      </div>
      <label className="form-field">
        <span className="field-label">Image URL</span>
        <Input defaultValue={urlDefaultValue} disabled={disabled} placeholder={src.startsWith("data:") ? "Uploaded image stored in canvas" : "https://example.com/image.png"} onBlur={(event) => commitImageUrl(event.target.value)} />
      </label>
      <label className="form-field">
        <span className="field-label">Image label</span>
        <Input defaultValue={String(selected.data.text ?? "")} disabled={disabled} onBlur={(event) => onData({ text: event.target.value })} />
      </label>
      {imageError ? <p className="canvas-field-error" role="alert">{imageError}</p> : null}
    </div>
  );
}

function CommentsPanel(props: {
  comments: CommentResponse[];
  content: CanvasContent;
  activeThread: CommentResponse | null;
  canComment: boolean;
  selectedElementId: string | null;
  commentDraft: string;
  replyDraft: string;
  editingCommentId: string | null;
  editingDraft: string;
  onDraft: (value: string) => void;
  onReplyDraft: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onSubmitReply: (event: FormEvent<HTMLFormElement>) => void;
  onOpenThread: (commentId: string) => void;
  onStartEdit: (comment: CommentResponse) => void;
  onEditDraft: (value: string) => void;
  onSaveEdit: (comment: CommentResponse) => void;
  onCancelEdit: () => void;
  onResolve: (comment: CommentResponse) => void;
  onReopen: (comment: CommentResponse) => void;
  onDelete: (comment: CommentResponse) => void;
}) {
  const topLevel = props.comments.filter((comment) => !comment.parentId);
  const replies = props.activeThread ? props.comments.filter((comment) => comment.parentId === props.activeThread?.id) : [];
  const elementIds = new Set(props.content.elements.map((element) => element.id));

  return (
    <Panel className="canvas-panel-section" as="section">
      <span className="kicker">Comments</span>
      <form className="comment-form" onSubmit={props.onSubmit}>
        <label className="form-field">
          <span className="field-label">{props.selectedElementId ? `Target ${props.selectedElementId}` : "General canvas comment"}</span>
          <textarea value={props.commentDraft} disabled={!props.canComment} onChange={(event) => props.onDraft(event.target.value)} />
        </label>
        <Button variant="primary" icon={<Send size={16} aria-hidden="true" />} disabled={!props.canComment || !props.commentDraft.trim()} type="submit">Add comment</Button>
      </form>
      <div className="thread-list" aria-label="Comment threads">
        {topLevel.length === 0 ? <EmptyState title="No comments" copy="Canvas comments and element pins are persisted through Core comment endpoints." /> : null}
        {topLevel.map((comment) => {
          const missingTarget = Boolean(comment.targetElementId && !elementIds.has(comment.targetElementId));
          return (
            <button className={props.activeThread?.id === comment.id ? "thread-button is-active" : "thread-button"} key={comment.id} type="button" onClick={() => props.onOpenThread(comment.id)}>
              <strong>{comment.resolved ? "Resolved" : "Open"} thread</strong>
              <span>{comment.targetElementId ? missingTarget ? `Missing target ${comment.targetElementId}` : `Target ${comment.targetElementId}` : "General canvas"}</span>
            </button>
          );
        })}
      </div>
      {props.activeThread ? (
        <div className="active-thread">
          <CommentItem comment={props.activeThread} canComment={props.canComment} editingCommentId={props.editingCommentId} editingDraft={props.editingDraft} onStartEdit={props.onStartEdit} onEditDraft={props.onEditDraft} onSaveEdit={props.onSaveEdit} onCancelEdit={props.onCancelEdit} onResolve={props.onResolve} onReopen={props.onReopen} onDelete={props.onDelete} />
          {replies.map((reply) => <CommentItem key={reply.id} comment={reply} canComment={props.canComment} editingCommentId={props.editingCommentId} editingDraft={props.editingDraft} onStartEdit={props.onStartEdit} onEditDraft={props.onEditDraft} onSaveEdit={props.onSaveEdit} onCancelEdit={props.onCancelEdit} onResolve={props.onResolve} onReopen={props.onReopen} onDelete={props.onDelete} />)}
          <form className="comment-form" onSubmit={props.onSubmitReply}>
            <textarea value={props.replyDraft} disabled={!props.canComment} aria-label="Reply to active thread" onChange={(event) => props.onReplyDraft(event.target.value)} />
            <Button variant="secondary" disabled={!props.canComment || !props.replyDraft.trim()} type="submit">Reply</Button>
          </form>
        </div>
      ) : null}
    </Panel>
  );
}

function CommentItem({ comment, canComment, editingCommentId, editingDraft, onStartEdit, onEditDraft, onSaveEdit, onCancelEdit, onResolve, onReopen, onDelete }: {
  comment: CommentResponse;
  canComment: boolean;
  editingCommentId: string | null;
  editingDraft: string;
  onStartEdit: (comment: CommentResponse) => void;
  onEditDraft: (value: string) => void;
  onSaveEdit: (comment: CommentResponse) => void;
  onCancelEdit: () => void;
  onResolve: (comment: CommentResponse) => void;
  onReopen: (comment: CommentResponse) => void;
  onDelete: (comment: CommentResponse) => void;
}) {
  const editing = editingCommentId === comment.id;
  return (
    <article className="comment-item">
      <span className="coord">{formatDate(comment.updatedAt)}</span>
      {editing ? (
        <div className="comment-edit">
          <textarea value={editingDraft} onChange={(event) => onEditDraft(event.target.value)} />
          <div className="comment-actions">
            <Button variant="primary" onClick={() => onSaveEdit(comment)}>Save</Button>
            <Button variant="ghost" onClick={onCancelEdit}>Cancel</Button>
          </div>
        </div>
      ) : (
        <>
          <p>{comment.content}</p>
          <div className="comment-actions">
            <Button variant="ghost" disabled={!canComment} onClick={() => onStartEdit(comment)}>Edit</Button>
            <Button variant="ghost" disabled={!canComment} onClick={() => comment.resolved ? onReopen(comment) : onResolve(comment)}>{comment.resolved ? "Reopen" : "Resolve"}</Button>
            <Button variant="ghost" disabled={!canComment} onClick={() => onDelete(comment)}>Delete</Button>
          </div>
        </>
      )}
    </article>
  );
}

function SnapshotsPanel({ snapshots, canEdit, onCreate, onRestore }: { snapshots: SnapshotResponse[]; canEdit: boolean; onCreate: () => void; onRestore: (snapshot: SnapshotResponse) => void }) {
  return (
    <Panel className="canvas-panel-section" as="section">
      <div className="panel-title-row">
        <span className="kicker">Snapshots</span>
        <Button variant="secondary" icon={<Camera size={16} aria-hidden="true" />} disabled={!canEdit} onClick={onCreate}>Create</Button>
      </div>
      <div className="snapshot-list">
        {snapshots.length === 0 ? <EmptyState title="No snapshots" copy="Create a snapshot to capture a restorable canvas version." /> : null}
        {snapshots.map((snapshot) => (
          <article className="snapshot-row" key={snapshot.id}>
            <History size={16} aria-hidden="true" />
            <div>
              <strong>Version {snapshot.version}</strong>
              <span>{formatDate(snapshot.snapshotAt)}</span>
            </div>
            <Button variant="ghost" disabled={!canEdit} onClick={() => onRestore(snapshot)}>Restore</Button>
          </article>
        ))}
      </div>
    </Panel>
  );
}

function TemplatesPanel({ templates, canEdit, templateName, onTemplateName, onSubmit, onCreateFromTemplate }: { templates: TemplateResponse[]; canEdit: boolean; templateName: string; onTemplateName: (value: string) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; onCreateFromTemplate: (template: TemplateResponse) => void }) {
  return (
    <Panel className="canvas-panel-section" as="section">
      <span className="kicker">Templates</span>
      <form className="template-form" onSubmit={onSubmit}>
        <Input value={templateName} disabled={!canEdit} placeholder="Template name" aria-label="Template name" onChange={(event) => onTemplateName(event.target.value)} />
        <Button variant="primary" icon={<LayoutTemplate size={16} aria-hidden="true" />} disabled={!canEdit || !templateName.trim()} type="submit">Save template</Button>
      </form>
      <div className="template-list">
        {templates.length === 0 ? <EmptyState title="No templates" copy="Save this canvas as a template or create from workspace templates when available." /> : null}
        {templates.map((template) => (
          <article className="template-row" key={template.id}>
            <LayoutTemplate size={16} aria-hidden="true" />
            <div>
              <strong>{template.name}</strong>
              <span>{template.category ?? "workspace"} · {template.content.elements.length} elements</span>
            </div>
            <Button variant="ghost" disabled={!canEdit} onClick={() => onCreateFromTemplate(template)}>Use</Button>
          </article>
        ))}
      </div>
    </Panel>
  );
}

function EmptyCanvasOverlay({ canEdit, onCreate }: { canEdit: boolean; onCreate: () => void }) {
  return (
    <div className="empty-canvas-overlay">
      <EmptyState title="Empty canvas" copy="Add a shape, text block, connector, image placeholder, arrow, or freehand mark to start the document." action={<Button variant="primary" disabled={!canEdit} onClick={onCreate}>Add rectangle</Button>} />
    </div>
  );
}

function NumberField({ label, value, disabled, step = 1, onCommit }: { label: string; value: number; disabled?: boolean; step?: number; onCommit: (value: number) => void }) {
  const [draft, setDraft] = useState(String(value));

  useEffect(() => {
    setDraft(String(value));
  }, [value]);

  return (
    <label>
      {label}
      <input type="number" step={step} value={draft} disabled={disabled} onChange={(event) => setDraft(event.target.value)} onBlur={() => {
        const next = Number(draft);
        if (Number.isFinite(next) && next !== value) {
          onCommit(next);
        }
      }} />
    </label>
  );
}

function KeyValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="key-value">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function centerElementInViewport(element: CanvasElement, content: CanvasContent, size: { width: number; height: number }): CanvasElement {
  const viewportCenter = {
    x: (size.width / 2 - content.viewport.panX) / content.viewport.zoom,
    y: (size.height / 2 - content.viewport.panY) / content.viewport.zoom
  };
  const currentCenter = elementCenter(element);
  const changes = translateCanvasElement(element, Math.round(viewportCenter.x - currentCenter.x), Math.round(viewportCenter.y - currentCenter.y));

  return {
    ...element,
    ...changes,
    data: {
      ...element.data,
      ...changes.data
    }
  };
}

function withViewport(content: CanvasContent, viewport: CanvasContent["viewport"]): CanvasContent {
  return {
    ...content,
    viewport
  };
}

function validateCanvas(content: CanvasContent, comments: CommentResponse[]) {
  const messages: string[] = [];
  const ids = new Set<string>();

  content.elements.forEach((element) => {
    if (ids.has(element.id)) {
      messages.push(`Duplicate element id ${element.id}`);
    }
    ids.add(element.id);

    if (element.width <= 0 || element.height <= 0) {
      messages.push(`Invalid dimensions for ${element.id}`);
    }
  });

  comments.forEach((comment) => {
    if (comment.targetElementId && !ids.has(comment.targetElementId)) {
      messages.push(`Comment ${comment.id} targets a missing element`);
    }
  });

  return messages;
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => typeof reader.result === "string" ? resolve(reader.result) : reject(new Error("Invalid image data"));
    reader.onerror = () => reject(reader.error ?? new Error("Image read failed"));
    reader.readAsDataURL(file);
  });
}

function isSupportedImageUrl(value: string) {
  if (value.startsWith("data:image/")) {
    return true;
  }

  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function loadStatusFromError(error: AppError): LoadStatus {
  if (error.status === 403) {
    return "permission_denied";
  }

  if (error.status === 404) {
    return error.message.toLowerCase().includes("deleted") ? "deleted" : "not_found";
  }

  return "error";
}

function syncLabel(syncState: SyncState) {
  const labels: Record<SyncState, string> = {
    saved: "Saved",
    saving: "Saving",
    offline: "Offline",
    reconnecting: "Reconnecting",
    conflict: "Conflict",
    failed: "Failed to save"
  };

  return labels[syncState];
}

function syncTone(syncState: SyncState) {
  if (syncState === "saved") {
    return "healthy";
  }

  if (syncState === "conflict" || syncState === "failed") {
    return "danger";
  }

  return "warning";
}

function stringStyle(value: unknown, fallback: string) {
  return typeof value === "string" && value.length > 0 ? value : fallback;
}

function colorInputValue(value: unknown, fallback: string) {
  const color = stringStyle(value, fallback);
  return /^#[\da-f]{6}$/i.test(color) ? color : fallback;
}

function endpointFallback(element: CanvasElement, handle: ConnectorEndpoint): CanvasPoint {
  if (isLineElement(element)) {
    return curvePointsFromElement(element)[handle];
  }

  return handle === "end" ? { x: element.x + element.width, y: element.y + element.height } : { x: element.x, y: element.y };
}

function isLineElement(element: CanvasElement) {
  return element.type === "connector" || element.type === "arrow" || element.type === "curve";
}

function isTransparentStyle(value: unknown) {
  return typeof value === "string" && value.toLowerCase() === "transparent";
}

function numberStyle(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function isEditableKeyboardTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false;
  }

  const tagName = target.tagName.toLowerCase();
  return target.isContentEditable || tagName === "input" || tagName === "textarea" || tagName === "select";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "RT";
}

function cursorColor(index: number) {
  return ["#4d7cfe", "#ff5f8f", "#35d6a4", "#8a5cff", "#f3b44e"][index % 5];
}
