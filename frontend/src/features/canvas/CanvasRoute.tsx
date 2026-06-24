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
  MousePointer2,
  Move,
  Pencil,
  Send,
  Square,
  Trash2,
  Type,
  Unlock,
  Upload,
  X,
  ZoomIn,
  ZoomOut
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, ChangeEvent, FormEvent, KeyboardEvent, PointerEvent, ReactNode } from "react";
import { Link, useNavigate, useOutletContext, useParams } from "react-router-dom";
import { AppError, createWorkspaceCacheKey } from "../../contracts/api-client";
import type { CanvasContent, CanvasElement, CanvasElementType, CanvasResponse, CommentResponse, SnapshotResponse, TemplateResponse } from "../../contracts/canvas";
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
  deleteSelection,
  duplicateSelection,
  elementCenter,
  elementLabel,
  fitViewportToContent,
  moveSelection,
  pointsToPath,
  pointFromData,
  selectElement,
  setSelectionLocked,
  updateSelectionGeometry,
  updateSelectionStyle,
  updateViewport,
  updateZOrder
} from "./canvasModel";
import type { CanvasOperation, CanvasTool } from "./canvasModel";
import { createCanvasRealtimeAdapter, resolveRealtimeUrl } from "./realtime";
import type { CanvasRealtimeAdapter, RealtimeStatus, RemotePresence } from "./realtime";

type LoadStatus = "loading" | "ready" | "not_found" | "deleted" | "permission_denied" | "error";
type SyncState = "saved" | "saving" | "offline" | "reconnecting" | "conflict" | "failed";
type ActivePanel = "inspector" | "comments" | "snapshots" | "templates";

type DragState = {
  elementId: string;
  startClientX: number;
  startClientY: number;
  startX: number;
  startY: number;
};

const toolButtons: Array<{ tool: CanvasTool; label: string; icon: ReactNode; permission: "edit" | "comment" }> = [
  { tool: "select", label: "Select", icon: <MousePointer2 size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "rectangle", label: "Rectangle", icon: <Square size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "circle", label: "Circle", icon: <Circle size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "text", label: "Text", icon: <Type size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "image", label: "Image", icon: <Image size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "connector", label: "Connector", icon: <GitBranch size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "arrow", label: "Arrow", icon: <ArrowRight size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "freehand", label: "Freehand", icon: <Pencil size={16} aria-hidden="true" />, permission: "edit" },
  { tool: "comment", label: "Comment", icon: <MessageSquare size={16} aria-hidden="true" />, permission: "comment" }
];

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
  const [dragState, setDragState] = useState<DragState | null>(null);
  const realtimeRef = useRef<CanvasRealtimeAdapter | null>(null);
  const contentRef = useRef<CanvasContent | null>(null);
  const canvasRef = useRef<CanvasResponse | null>(null);
  const realtimeStatusRef = useRef(realtimeStatus);
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
      const [loadedComments, loadedSnapshots, loadedTemplates] = await Promise.all([
        listCanvasComments(auth.client, loadedCanvas.id, signal),
        listCanvasSnapshots(auth.client, loadedCanvas.id, signal),
        activeWorkspace?.id ? listTemplates(auth.client, activeWorkspace.id, signal) : Promise.resolve([])
      ]);

      setCanvas(loadedCanvas);
      setContent(loadedCanvas.content);
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
      setContent((current) => current ? applyCanvasOperations(current, message.ops) : current);
      setCanvas((current) => current ? { ...current, version: Math.max(current.version, message.version), lockVersion: Math.max(current.lockVersion, message.lockVersion ?? current.lockVersion) } : current);
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

  async function commitContent(nextContent: CanvasContent, ops: CanvasOperation[], nextSelection = selection) {
    const currentCanvas = canvasRef.current;

    if (!currentCanvas || !canEditCanvas || ops.length === 0) {
      setContent(nextContent);
      setSelection(nextSelection);
      return;
    }

    setContent(nextContent);
    setSelection(nextSelection);
    setSyncState("saving");
    setSyncError(null);

    try {
      const updated = await updateCanvas(auth.client, currentCanvas.id, {
        content: nextContent,
        expectedVersion: currentCanvas.version,
        expectedLockVersion: currentCanvas.lockVersion
      });
      const realtime = realtimeRef.current;
      if (realtime) {
        const seq = seqRef.current + 1;
        seqRef.current = seq;
        void realtime.sendOperations(ops, updated.version, updated.lockVersion, seq);
      }
      setCanvas(updated);
      setContent(updated.content);
      setSyncState(realtimeStatusRef.current.state === "connected" ? "saved" : "offline");
    } catch (error) {
      const appError = error instanceof AppError ? error : new AppError({ status: 0, code: "CANVAS_SAVE_FAILED", message: "Canvas changes could not be saved.", retryable: true });
      setSyncError(appError);
      setSyncState(appError.status === 409 ? "conflict" : "failed");
    }
  }

  function addElement(type: CanvasElementType) {
    if (!content || !canEditCanvas) {
      return;
    }

    const element = createCanvasElement(type, content.elements.length, { zIndex: content.elements.length + 1 });
    const op: CanvasOperation = { type: "add", id: element.id, element };
    void commitContent(applyCanvasOperations(content, [op]), [op], [element.id]);
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
    if (!content || !canEditCanvas) {
      return;
    }

    const result = moveSelection(content, selection, dx, dy);
    void commitContent(result.content, result.ops);
  }

  function duplicateSelected() {
    if (!content || !canEditCanvas) {
      return;
    }

    const result = duplicateSelection(content, selection);
    void commitContent(result.content, result.ops, result.selection);
  }

  function deleteSelected() {
    if (!content || !canEditCanvas) {
      return;
    }

    const result = deleteSelection(content, selection);
    void commitContent(result.content, result.ops, result.selection);
  }

  function setLocked(locked: boolean) {
    if (!content || !canEditCanvas) {
      return;
    }

    const result = setSelectionLocked(content, selection, locked);
    void commitContent(result.content, result.ops);
  }

  function updateSelectedStyle(style: Parameters<typeof updateSelectionStyle>[2]) {
    if (!content || !canEditCanvas) {
      return;
    }

    const result = updateSelectionStyle(content, selection, style);
    void commitContent(result.content, result.ops);
  }

  function updateSelectedGeometry(changes: Parameters<typeof updateSelectionGeometry>[2]) {
    if (!content || !canEditCanvas) {
      return;
    }

    const result = updateSelectionGeometry(content, selection, changes);
    void commitContent(result.content, result.ops);
  }

  function updateSelectedData(data: Partial<CanvasElement["data"]>) {
    if (!content || !selectedElement || !canEditCanvas) {
      return;
    }

    const op: CanvasOperation = { type: "update", id: selectedElement.id, changes: { data: { ...selectedElement.data, ...data } } };
    void commitContent(applyCanvasOperations(content, [op]), [op]);
  }

  function moveZOrder(direction: "front" | "back") {
    if (!content || !canEditCanvas) {
      return;
    }

    const result = updateZOrder(content, selection, direction);
    void commitContent(result.content, result.ops);
  }

  function setGroup(grouped: boolean) {
    if (!content || !canEditCanvas) {
      return;
    }

    const groupId = grouped ? `group-${Date.now()}` : null;
    const selected = new Set(selection);
    const ops = content.elements.flatMap((element) => selected.has(element.id) && !element.locked ? [{ type: "update" as const, id: element.id, changes: { groupId } }] : []);
    void commitContent(applyCanvasOperations(content, ops), ops);
  }

  function changeViewport(nextViewport: CanvasContent["viewport"]) {
    if (!content) {
      return;
    }

    const result = updateViewport(content, nextViewport);
    void commitContent(result.content, result.ops);
  }

  function toggleGrid() {
    if (!content) {
      return;
    }

    const op: CanvasOperation = { type: "update-state", state: { metadata: { gridEnabled: !content.metadata.gridEnabled } } };
    void commitContent(applyCanvasOperations(content, [op]), [op]);
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

  function pointerCoordinate(event: PointerEvent<HTMLElement | SVGSVGElement>) {
    const rect = viewportRef.current?.getBoundingClientRect();
    const currentContent = contentRef.current;

    if (!rect || !currentContent) {
      return { x: 0, y: 0 };
    }

    return {
      x: Math.round((event.clientX - rect.left - currentContent.viewport.panX) / currentContent.viewport.zoom),
      y: Math.round((event.clientY - rect.top - currentContent.viewport.panY) / currentContent.viewport.zoom)
    };
  }

  function updatePointerCoordinate(event: PointerEvent<HTMLElement | SVGSVGElement>) {
    const nextCoordinate = pointerCoordinate(event);
    setCoordinate(nextCoordinate);
    void realtimeRef.current?.sendPresence({ cursor: nextCoordinate, selection, status: "online" });

    if (dragState && contentRef.current) {
      const dx = (event.clientX - dragState.startClientX) / contentRef.current.viewport.zoom;
      const dy = (event.clientY - dragState.startClientY) / contentRef.current.viewport.zoom;
      const op: CanvasOperation = { type: "update", id: dragState.elementId, changes: { x: Math.round(dragState.startX + dx), y: Math.round(dragState.startY + dy) } };
      setContent(applyCanvasOperations(contentRef.current, [op]));
    }
  }

  function startDrag(event: PointerEvent<SVGGElement>, element: CanvasElement) {
    if (!canEditCanvas || element.locked || tool !== "select") {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    setDragState({ elementId: element.id, startClientX: event.clientX, startClientY: event.clientY, startX: element.x, startY: element.y });
    selectCanvasElement(element.id, event.shiftKey ? "toggle" : "replace");
  }

  function finishDrag() {
    if (!dragState || !contentRef.current || !canEditCanvas) {
      setDragState(null);
      return;
    }

    const dragged = contentRef.current.elements.find((element) => element.id === dragState.elementId);
    setDragState(null);

    if (!dragged) {
      return;
    }

    const op: CanvasOperation = { type: "update", id: dragged.id, changes: { x: dragged.x, y: dragged.y } };
    void commitContent(contentRef.current, [op]);
  }

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
    return <EmptyState title="Canvas not found" copy="The canvas id in this route does not match an accessible canvas in the current workspace." />;
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
      <div className="canvas-workbench">
        <div className="canvas-workbench-header">
          <div>
            <span className="kicker">Canvas workbench</span>
            <h1 id="canvas-title">{canvas.title}</h1>
            <p>
              Workspace {workspaceSlug} · canvas/{canvas.id} · version {canvas.version} · updated {formatDate(canvas.updatedAt)}
            </p>
          </div>
          <div className="canvas-status-strip" aria-label="Canvas status">
            <StatusChip tone={syncTone(syncState)}>{syncLabel(syncState)}</StatusChip>
            <StatusChip tone={realtimeStatus.state === "connected" ? "healthy" : realtimeStatus.state === "failed" ? "danger" : "warning"}>{realtimeStatus.label}</StatusChip>
            <StatusChip tone={canEditCanvas ? "healthy" : canComment ? "warning" : "neutral"}>{mode}</StatusChip>
          </div>
        </div>

        <div className="canvas-main-grid">
          <PaperSurface className="canvas-editor-shell" as="article">
            <div className="canvas-toolbar" aria-label="Canvas tools">
              {toolButtons.map((item) => {
                const enabled = item.permission === "edit" ? canEditCanvas : canComment;
                return (
                  <Button className={tool === item.tool ? "is-active" : ""} key={item.tool} variant="secondary" icon={item.icon} disabled={!enabled} aria-pressed={tool === item.tool} onClick={() => {
                    setTool(item.tool);
                    if (item.tool !== "select" && item.tool !== "comment") {
                      addElement(item.tool);
                    } else if (item.tool === "comment") {
                      setActivePanel("comments");
                    }
                  }}>
                    {item.label}
                  </Button>
                );
              })}
              <div className="toolbar-divider" />
              <IconButton label="Zoom out" icon={<ZoomOut size={16} aria-hidden="true" />} onClick={() => changeViewport({ ...content.viewport, zoom: content.viewport.zoom - 0.1 })} />
              <IconButton label="Zoom in" icon={<ZoomIn size={16} aria-hidden="true" />} onClick={() => changeViewport({ ...content.viewport, zoom: content.viewport.zoom + 0.1 })} />
              <IconButton label="Fit to content" icon={<Maximize2 size={16} aria-hidden="true" />} onClick={() => changeViewport(fitViewportToContent(content))} />
              <IconButton label={content.metadata.gridEnabled ? "Hide grid" : "Show grid"} icon={<Grid3X3 size={16} aria-hidden="true" />} onClick={toggleGrid} />
              <IconButton label="Pan canvas right" icon={<Move size={16} aria-hidden="true" />} onClick={() => changeViewport({ ...content.viewport, panX: content.viewport.panX + 32 })} />
              <div className="toolbar-divider" />
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

            <div className="canvas-viewport-wrap" ref={viewportRef} tabIndex={0} role="application" aria-label="Canvas viewport" onKeyDown={keyboardMove} onPointerMove={updatePointerCoordinate} onPointerUp={finishDrag} onPointerLeave={finishDrag}>
              {content.elements.length === 0 ? <EmptyCanvasOverlay canEdit={canEditCanvas} onCreate={() => addElement("rectangle")} /> : null}
              <svg className="canvas-svg" viewBox={`0 0 ${content.metadata.width} ${content.metadata.height}`} role="img" aria-label={`${canvas.title} canvas with ${content.elements.length} elements`} onPointerDown={() => clearSelection()}>
                <defs>
                  <marker id="canvas-arrowhead" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                    <path d="M 0 0 L 8 4 L 0 8 z" fill="#273142" />
                  </marker>
                  {content.metadata.gridEnabled ? (
                    <pattern id="canvas-grid" width="32" height="32" patternUnits="userSpaceOnUse">
                      <path d="M 32 0 L 0 0 0 32" fill="none" stroke="rgba(77, 124, 254, 0.22)" strokeWidth="1" />
                    </pattern>
                  ) : null}
                </defs>
                <rect width={content.metadata.width} height={content.metadata.height} fill={content.metadata.backgroundColor} />
                {content.metadata.gridEnabled ? <rect width={content.metadata.width} height={content.metadata.height} fill="url(#canvas-grid)" /> : null}
                <g transform={`translate(${content.viewport.panX} ${content.viewport.panY}) scale(${content.viewport.zoom})`}>
                  {content.elements.map((element) => (
                    <CanvasElementView key={element.id} element={element} selected={selection.includes(element.id)} onPointerDown={(event) => startDrag(event, element)} onSelect={(append) => selectCanvasElement(element.id, append ? "toggle" : "replace")} />
                  ))}
                </g>
              </svg>
              <CanvasOverlays content={content} comments={topLevelComments} presence={presence} selection={selection} activeThreadId={activeThreadId} onOpenThread={(commentId) => {
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
          </PaperSurface>

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

function CanvasElementView({ element, selected, onPointerDown, onSelect }: { element: CanvasElement; selected: boolean; onPointerDown: (event: PointerEvent<SVGGElement>) => void; onSelect: (append: boolean) => void }) {
  const stroke = stringStyle(element.style.stroke, "#273142");
  const fill = stringStyle(element.style.fill, "transparent");
  const strokeWidth = numberStyle(element.style.strokeWidth, 2);
  const opacity = numberStyle(element.style.opacity, 1);
  const transform = `rotate(${element.rotation} ${element.x + element.width / 2} ${element.y + element.height / 2})`;

  return (
    <g className={`canvas-element ${selected ? "is-selected" : ""} ${element.locked ? "is-locked" : ""}`} transform={transform} onPointerDown={onPointerDown} onClick={(event) => {
      event.stopPropagation();
      onSelect(event.shiftKey);
    }}>
      {renderElementShape(element, fill, stroke, strokeWidth, opacity)}
      {selected ? <rect className="selection-outline" x={element.x - 6} y={element.y - 6} width={element.width + 12} height={element.height + 12} rx="8" /> : null}
    </g>
  );
}

function renderElementShape(element: CanvasElement, fill: string, stroke: string, strokeWidth: number, opacity: number) {
  if (element.type === "circle") {
    return (
      <g>
        <ellipse cx={element.x + element.width / 2} cy={element.y + element.height / 2} rx={element.width / 2} ry={element.height / 2} fill={fill} stroke={stroke} strokeWidth={strokeWidth} opacity={opacity} />
        <text x={element.x + element.width / 2} y={element.y + element.height / 2 + 5} textAnchor="middle" fill="#273142" fontSize="15" fontWeight="700">{String(element.data.text ?? "Circle")}</text>
      </g>
    );
  }

  if (element.type === "text") {
    return (
      <text x={element.x} y={element.y + 28} fill={stroke === "transparent" ? "#273142" : stroke} fontFamily={stringStyle(element.style.fontFamily, "Inter")} fontSize={numberStyle(element.style.fontSize, 18)} fontWeight={String(element.style.fontWeight ?? 700)} opacity={opacity}>
        {String(element.data.text ?? "Text")}
      </text>
    );
  }

  if (element.type === "image") {
    return (
      <g>
        <rect x={element.x} y={element.y} width={element.width} height={element.height} rx="8" fill="#ffffff" stroke={stroke} strokeWidth={strokeWidth} opacity={opacity} />
        <path d={`M ${element.x + 20} ${element.y + element.height - 24} L ${element.x + 72} ${element.y + element.height - 72} L ${element.x + 112} ${element.y + element.height - 32}`} fill="none" stroke="#8a5cff" strokeWidth="4" />
        <text x={element.x + 18} y={element.y + 28} fill="#273142" fontSize="14" fontWeight="700">{String(element.data.text ?? "Image placeholder")}</text>
      </g>
    );
  }

  if (element.type === "connector" || element.type === "arrow") {
    const start = pointFromData(element.data.start, { x: element.x, y: element.y });
    const end = pointFromData(element.data.end, { x: element.x + element.width, y: element.y + element.height });
    return <line x1={start.x} y1={start.y} x2={end.x} y2={end.y} stroke={stroke} strokeWidth={strokeWidth} opacity={opacity} markerEnd={element.type === "arrow" ? "url(#canvas-arrowhead)" : undefined} />;
  }

  if (element.type === "freehand") {
    const points = Array.isArray(element.data.points) ? element.data.points : [];
    const path = typeof element.data.path === "string" ? element.data.path : pointsToPath(points);
    return <path d={path} fill="none" stroke={stroke} strokeWidth={strokeWidth} opacity={opacity} strokeLinecap="round" strokeLinejoin="round" />;
  }

  return (
    <g>
      <rect x={element.x} y={element.y} width={element.width} height={element.height} rx="8" fill={fill} stroke={stroke} strokeWidth={strokeWidth} opacity={opacity} />
      <text x={element.x + element.width / 2} y={element.y + element.height / 2 + 5} textAnchor="middle" fill="#273142" fontSize="15" fontWeight="700">{String(element.data.text ?? "Rectangle")}</text>
    </g>
  );
}

function CanvasOverlays({ content, comments, presence, selection, activeThreadId, onOpenThread }: { content: CanvasContent; comments: CommentResponse[]; presence: RemotePresence[]; selection: string[]; activeThreadId: string | null; onOpenThread: (commentId: string) => void }) {
  const elementMap = new Map(content.elements.map((element) => [element.id, element]));
  return (
    <div className="canvas-overlay-layer" aria-hidden="false">
      {comments.map((comment) => {
        const target = comment.targetElementId ? elementMap.get(comment.targetElementId) : null;
        const center = target ? elementCenter(target) : { x: 24, y: 24 + comments.indexOf(comment) * 42 };
        const left = center.x * content.viewport.zoom + content.viewport.panX;
        const top = center.y * content.viewport.zoom + content.viewport.panY;
        return (
          <button className={`canvas-comment-pin ${activeThreadId === comment.id ? "is-active" : ""} ${target || !comment.targetElementId ? "" : "is-missing-target"}`} key={comment.id} type="button" style={{ left, top }} aria-label={target ? `Open comment on ${target.type}` : comment.targetElementId ? `Open comment with missing target ${comment.targetElementId}` : "Open general canvas comment"} onClick={() => onOpenThread(comment.id)}>
            <MessageSquare size={14} aria-hidden="true" />
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

function InspectorPanel({ canvas, content, selectedElements, canEdit, validation, syncError, onClear, onDuplicate, onDelete, onLock, onUnlock, onFront, onBack, onGroup, onUngroup, onStyle, onGeometry, onData, onSelectElement }: {
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
  onSelectElement: (elementId: string) => void;
}) {
  const selected = selectedElements.length === 1 ? selectedElements[0] : null;

  return (
    <Panel className="canvas-panel-section" as="section">
      <span className="kicker">Inspector</span>
      <div className="inspector-list">
        <KeyValue label="Canvas id" value={canvas.id} />
        <KeyValue label="Version" value={`${canvas.version} / lock ${canvas.lockVersion}`} />
        <KeyValue label="Paper" value={`${content.metadata.width} x ${content.metadata.height}`} />
        <KeyValue label="Permission" value={canEdit ? "edit allowed" : "read only"} />
      </div>

      {syncError ? <ErrorState title={syncError.status === 409 ? "Conflict" : "Save failed"} copy={syncError.message} requestId={syncError.requestId} /> : null}
      {validation.length > 0 ? (
        <div className="validation-list" role="status">
          <span className="kicker">Validation</span>
          {validation.map((message) => <p key={message}>{message}</p>)}
        </div>
      ) : <StatusChip tone="healthy">content valid</StatusChip>}

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
              <input type="color" value={stringStyle(selected.style.fill, "#ffffff")} disabled={!canEdit || selected.locked} onChange={(event) => onStyle({ fill: event.target.value })} />
            </label>
            <label>
              Stroke
              <input type="color" value={stringStyle(selected.style.stroke, "#4d7cfe")} disabled={!canEdit || selected.locked} onChange={(event) => onStyle({ stroke: event.target.value })} />
            </label>
            <NumberField label="Stroke width" value={numberStyle(selected.style.strokeWidth, 2)} disabled={!canEdit || selected.locked} onCommit={(value) => onStyle({ strokeWidth: value })} />
            <NumberField label="Opacity" value={numberStyle(selected.style.opacity, 1)} disabled={!canEdit || selected.locked} step={0.1} onCommit={(value) => onStyle({ opacity: Math.max(0, Math.min(1, value)) })} />
          </div>
          {selected.type === "text" || selected.type === "rectangle" || selected.type === "circle" || selected.type === "image" ? (
            <label className="form-field">
              <span className="field-label">Text data</span>
              <Input defaultValue={String(selected.data.text ?? "")} disabled={!canEdit || selected.locked} onBlur={(event) => onData({ text: event.target.value })} />
            </label>
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
  return (
    <label>
      {label}
      <input type="number" step={step} defaultValue={value} disabled={disabled} onBlur={(event) => {
        const next = Number(event.target.value);
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

function numberStyle(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
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
