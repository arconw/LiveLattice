import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import type { CSSProperties, KeyboardEvent, MouseEvent, PointerEvent } from "react";
import { Minus, Spline } from "lucide-react";
import type { CanvasContent, CanvasElement, CanvasPoint, CanvasViewportState } from "../../contracts/canvas";
import { applyCanvasOperations, createCanvasElement, curvePointsFromElement, curveToPath, lineModeFromElement, normalizeFreehandPoints, pointsToPath, selectElement, translateCanvasElement, updateConnectorEndpoint } from "./canvasModel";
import type { CanvasOperation, CanvasTool, ConnectorEndpoint, LineMode } from "./canvasModel";
import { elementBounds, hitTestElements } from "./canvasHitTest";
import type { CanvasBounds } from "./canvasHitTest";
import { renderCanvasScene, textElementFontSize, textElementLineHeight, textElementVerticalInset } from "./canvasRenderer";
import { clampZoom, clientToViewport, clientToWorld, panViewport, worldToViewport, zoomViewportAroundCenter, zoomViewportAroundPoint } from "./viewportMath";

type CanvasSurfaceProps = {
  content: CanvasContent;
  selection: string[];
  title: string;
  canEdit: boolean;
  tool: CanvasTool;
  onCoordinateChange: (coordinate: CanvasPoint) => void;
  onSelectionChange: (selection: string[]) => void;
  onCommitOperations: (ops: CanvasOperation[], nextSelection?: string[]) => void;
  onViewportChange: (viewport: CanvasViewportState, persist: boolean) => void;
  editingTextElementId: string | null;
  onTextEditingChange: (elementId: string | null) => void;
};

type SurfaceSize = {
  width: number;
  height: number;
};

type ViewportRect = {
  left: number;
  top: number;
  width: number;
  height: number;
};

type SurfaceRect = Pick<DOMRect, "left" | "top"> & Partial<Pick<DOMRect, "width" | "height">>;

type DragState =
  | {
      type: "move";
      pointerId: number;
      startWorld: CanvasPoint;
      originalContent: CanvasContent;
      elementIds: string[];
    }
  | {
      type: "endpoint";
      pointerId: number;
      handle: ConnectorEndpoint;
      element: CanvasElement;
      originalContent: CanvasContent;
    }
  | {
      type: "pan";
      pointerId: number;
      startViewport: CanvasPoint;
      viewport: CanvasViewportState;
    }
  | {
      type: "marquee";
      pointerId: number;
      startWorld: CanvasPoint;
      currentWorld: CanvasPoint;
      originalContent: CanvasContent;
      originalSelection: string[];
      append: boolean;
    }
  | {
      type: "freehand";
      pointerId: number;
      element: CanvasElement;
      originalContent: CanvasContent;
      points: CanvasPoint[];
    };

const fallbackSize = { width: 960, height: 672 };
const maxDpr = 2;
const freehandPointDistance = 2;

export function CanvasSurface({ content, selection, title, canEdit, tool, onCoordinateChange, onSelectionChange, onCommitOperations, onViewportChange, editingTextElementId, onTextEditingChange }: CanvasSurfaceProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const textEditorRef = useRef<HTMLTextAreaElement | null>(null);
  const contentRef = useRef(content);
  const selectionRef = useRef(selection);
  const previewContentRef = useRef<CanvasContent | null>(null);
  const dragRef = useRef<DragState | null>(null);
  const rafRef = useRef<number | null>(null);
  const sizeRef = useRef<SurfaceSize>(fallbackSize);
  const imageCacheRef = useRef(new Map<string, HTMLImageElement>());
  const [size, setSize] = useState<SurfaceSize>(fallbackSize);
  const [textDraft, setTextDraft] = useState("");
  const [marqueeRect, setMarqueeRect] = useState<ViewportRect | null>(null);
  const editingTextElement = editingTextElementId ? content.elements.find((element) => element.id === editingTextElementId && element.type === "text" && !element.locked) ?? null : null;
  const selectedLineElement = canEdit && selection.length === 1 ? content.elements.find((element) => element.id === selection[0] && isLineElement(element) && !element.locked) ?? null : null;
  const renderDpr = typeof window === "undefined" ? 1 : Math.min(maxDpr, Math.max(1, window.devicePixelRatio || 1));

  const draw = useCallback(() => {
    rafRef.current = null;
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");

    if (!canvas || !ctx) {
      return;
    }

    const renderSize = syncSurfaceSize();
    const dpr = typeof window === "undefined" ? 1 : Math.min(maxDpr, Math.max(1, window.devicePixelRatio || 1));
    const backingWidth = Math.max(1, Math.round(renderSize.width * dpr));
    const backingHeight = Math.max(1, Math.round(renderSize.height * dpr));

    if (canvas.width !== backingWidth || canvas.height !== backingHeight) {
      canvas.width = backingWidth;
      canvas.height = backingHeight;
    }

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const renderedContent = previewContentRef.current ?? contentRef.current;
    renderCanvasScene(ctx, renderedContent, {
      width: renderSize.width,
      height: renderSize.height,
      selection: selectionRef.current,
      imageCache: imageCacheRef.current,
      timestamp: performance.now()
    });

    if (hasAnimatedCurves(renderedContent)) {
      const requestFrame = window.requestAnimationFrame ?? ((callback: FrameRequestCallback) => window.setTimeout(() => callback(performance.now()), 16));
      rafRef.current = requestFrame(draw);
    }
  }, []);

  const requestDraw = useCallback(() => {
    if (rafRef.current !== null) {
      return;
    }

    const requestFrame = window.requestAnimationFrame ?? ((callback: FrameRequestCallback) => window.setTimeout(() => callback(performance.now()), 16));
    rafRef.current = requestFrame(draw);
  }, [draw]);

  useLayoutEffect(() => {
    contentRef.current = content;
    previewContentRef.current = null;
    requestDraw();
  }, [content, requestDraw]);

  useLayoutEffect(() => {
    selectionRef.current = selection;
    requestDraw();
  }, [requestDraw, selection]);

  useEffect(() => {
    if (!editingTextElementId) {
      setTextDraft("");
      return;
    }

    const element = contentRef.current.elements.find((item) => item.id === editingTextElementId);

    if (!element || element.type !== "text" || element.locked) {
      onTextEditingChange(null);
      return;
    }

    setTextDraft(String(element.data.text ?? ""));
  }, [editingTextElementId, onTextEditingChange]);

  useEffect(() => {
    if (!editingTextElement) {
      return;
    }

    const editor = textEditorRef.current;
    editor?.focus();
    editor?.select();
  }, [editingTextElement?.id]);

  useEffect(() => {
    const editor = textEditorRef.current;

    if (!editor || !editingTextElement) {
      return;
    }

    const minHeight = Math.max(36, (editingTextElement.height - textElementVerticalInset * 2) * content.viewport.zoom);
    editor.style.height = "0px";
    editor.style.height = `${Math.max(minHeight, editor.scrollHeight)}px`;
  }, [content.viewport.zoom, editingTextElement, textDraft]);

  useLayoutEffect(() => {
    requestDraw();
  }, [requestDraw, size]);

  useEffect(() => {
    return () => {
      if (rafRef.current !== null) {
        if (window.cancelAnimationFrame) {
          window.cancelAnimationFrame(rafRef.current);
        } else {
          window.clearTimeout(rafRef.current);
        }
      }
    };
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    const parent = canvas?.parentElement;

    if (!canvas || !parent) {
      return;
    }

    const updateSize = () => {
      syncSurfaceSize();
      requestDraw();
    };

    updateSize();

    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", updateSize);
      return () => window.removeEventListener("resize", updateSize);
    }

    const observer = new ResizeObserver(updateSize);
    observer.observe(canvas);
    observer.observe(parent);
    return () => observer.disconnect();
  }, [requestDraw]);

  useEffect(() => {
    const sources = content.elements.flatMap((element) => {
      const src = element.type === "image" && typeof element.data.src === "string" ? element.data.src : null;
      return src ? [src] : [];
    });

    sources.forEach((src) => {
      if (imageCacheRef.current.has(src)) {
        return;
      }

      const image = new Image();
      image.crossOrigin = "anonymous";
      image.onload = requestDraw;
      image.onerror = requestDraw;
      image.src = src;
      imageCacheRef.current.set(src, image);
    });
  }, [content.elements, requestDraw]);

  function surfaceRect() {
    const parentRect = canvasRef.current?.parentElement?.getBoundingClientRect();

    if (parentRect && parentRect.width > 0 && parentRect.height > 0) {
      return parentRect;
    }

    return canvasRef.current?.getBoundingClientRect() ?? ({ left: 0, top: 0 } as DOMRect);
  }

  function currentSurfaceSize() {
    return syncSurfaceSize();
  }

  function syncSurfaceSize() {
    const rect = surfaceRect();
    const nextSize = {
      width: rect.width > 0 ? rect.width : sizeRef.current.width || fallbackSize.width,
      height: rect.height > 0 ? rect.height : sizeRef.current.height || fallbackSize.height
    };

    if (Math.abs(nextSize.width - sizeRef.current.width) > 0.5 || Math.abs(nextSize.height - sizeRef.current.height) > 0.5) {
      sizeRef.current = nextSize;
      setSize(nextSize);
      return nextSize;
    }

    sizeRef.current = nextSize;
    return nextSize;
  }

  function applyViewportLocally(viewport: CanvasViewportState) {
    contentRef.current = applyViewportLocallyToContent(contentRef.current, viewport);
    requestDraw();
  }

  function worldPoint(event: MouseEvent<HTMLCanvasElement> | PointerEvent<HTMLCanvasElement>) {
    return clientToWorld({ x: event.clientX, y: event.clientY }, surfaceRect(), (previewContentRef.current ?? contentRef.current).viewport, currentSurfaceSize());
  }

  function handlePointerDown(event: PointerEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current;
    const currentContent = contentRef.current;
    const point = worldPoint(event);
    onCoordinateChange({ x: Math.round(point.x), y: Math.round(point.y) });

    if ((event.button === 1 || event.altKey) && tool === "select") {
      event.preventDefault();
      if (canvas?.setPointerCapture) {
        canvas.setPointerCapture(event.pointerId);
      }
      dragRef.current = {
        type: "pan",
        pointerId: event.pointerId,
        startViewport: clientToViewport({ x: event.clientX, y: event.clientY }, surfaceRect(), currentSurfaceSize()),
        viewport: currentContent.viewport
      };
      return;
    }

    if (canEdit && tool === "marquee" && event.button === 0) {
      event.preventDefault();
      if (canvas?.setPointerCapture) {
        canvas.setPointerCapture(event.pointerId);
      }

      dragRef.current = {
        type: "marquee",
        pointerId: event.pointerId,
        startWorld: point,
        currentWorld: point,
        originalContent: currentContent,
        originalSelection: selectionRef.current,
        append: event.shiftKey
      };
      setMarqueeRect(viewportRectFromWorldPoints(point, point, currentContent.viewport));

      if (!event.shiftKey) {
        onSelectionChange([]);
      }

      return;
    }

    if (canEdit && tool === "freehand" && event.button === 0) {
      event.preventDefault();
      if (canvas?.setPointerCapture) {
        canvas.setPointerCapture(event.pointerId);
      }

      const points = [point];
      const element = freehandElementFromPoints(currentContent, points);
      dragRef.current = {
        type: "freehand",
        pointerId: event.pointerId,
        element,
        originalContent: currentContent,
        points
      };
      previewContentRef.current = applyCanvasOperations(currentContent, [{ type: "add", id: element.id, element }]);
      onSelectionChange([]);
      requestDraw();
      return;
    }

    const hit = hitTestElements(currentContent.elements, point);

    if (!hit) {
      if (tool === "select" && event.button === 0) {
        event.preventDefault();
        if (canvas?.setPointerCapture) {
          canvas.setPointerCapture(event.pointerId);
        }
        if (canvas) {
          canvas.style.cursor = "grabbing";
        }
        dragRef.current = {
          type: "pan",
          pointerId: event.pointerId,
          startViewport: clientToViewport({ x: event.clientX, y: event.clientY }, surfaceRect(), currentSurfaceSize()),
          viewport: currentContent.viewport
        };
      }

      onSelectionChange([]);
      return;
    }

    const currentSelection = selectionRef.current;
    const keepSelectedGroup = !event.shiftKey && tool === "select" && currentSelection.length > 1 && currentSelection.includes(hit.element.id);
    const nextSelection = keepSelectedGroup ? currentSelection : selectElement(currentSelection, hit.element.id, event.shiftKey ? "toggle" : "replace");
    onSelectionChange(nextSelection);

    if (!canEdit || tool !== "select" || hit.element.locked) {
      return;
    }

    event.preventDefault();
    if (canvas?.setPointerCapture) {
      canvas.setPointerCapture(event.pointerId);
    }

    if (hit.type === "handle") {
      dragRef.current = {
        type: "endpoint",
        pointerId: event.pointerId,
        handle: hit.handle,
        element: hit.element,
        originalContent: currentContent
      };
      return;
    }

    dragRef.current = {
      type: "move",
      pointerId: event.pointerId,
      startWorld: point,
      originalContent: currentContent,
      elementIds: nextSelection.includes(hit.element.id) ? nextSelection : [hit.element.id]
    };
  }

  function handlePointerMove(event: PointerEvent<HTMLCanvasElement>) {
    const point = worldPoint(event);
    onCoordinateChange({ x: Math.round(point.x), y: Math.round(point.y) });

    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) {
      updateSurfaceCursor(point);
      return;
    }

    if (drag.type === "pan") {
      const nextViewport = viewportFromPanEvent(event, drag, surfaceRect(), currentSurfaceSize());
      applyViewportLocally(nextViewport);
      onViewportChange(nextViewport, false);
      return;
    }

    if (drag.type === "marquee") {
      const nextPoint = clientToWorld({ x: event.clientX, y: event.clientY }, surfaceRect(), drag.originalContent.viewport, currentSurfaceSize());
      drag.currentWorld = nextPoint;
      setMarqueeRect(viewportRectFromWorldPoints(drag.startWorld, nextPoint, drag.originalContent.viewport));
      return;
    }

    if (drag.type === "endpoint") {
      const nextPoint = clientToWorld({ x: event.clientX, y: event.clientY }, surfaceRect(), drag.originalContent.viewport, currentSurfaceSize());
      const changes = updateConnectorEndpoint(drag.element, drag.handle, nextPoint, { snapElements: drag.originalContent.elements });
      previewContentRef.current = applyCanvasOperations(drag.originalContent, [{ type: "update", id: drag.element.id, changes }]);
      requestDraw();
      return;
    }

    if (drag.type === "freehand") {
      const nextPoint = clientToWorld({ x: event.clientX, y: event.clientY }, surfaceRect(), drag.originalContent.viewport, currentSurfaceSize());
      const nextPoints = appendFreehandPoint(drag.points, nextPoint, freehandPointDistance);

      if (nextPoints === drag.points) {
        return;
      }

      drag.points = nextPoints;
      drag.element = freehandElementFromPoints(drag.originalContent, nextPoints, drag.element);
      previewContentRef.current = applyCanvasOperations(drag.originalContent, [{ type: "add", id: drag.element.id, element: drag.element }]);
      requestDraw();
      return;
    }

    const nextPoint = clientToWorld({ x: event.clientX, y: event.clientY }, surfaceRect(), drag.originalContent.viewport, currentSurfaceSize());
    const dx = nextPoint.x - drag.startWorld.x;
    const dy = nextPoint.y - drag.startWorld.y;
    const selected = new Set(drag.elementIds);
    const ops = drag.originalContent.elements.flatMap((element) => selected.has(element.id) && !element.locked ? [{ type: "update" as const, id: element.id, changes: translateCanvasElement(element, dx, dy) }] : []);
    previewContentRef.current = applyCanvasOperations(drag.originalContent, ops);
    requestDraw();
  }

  function updateSurfaceCursor(point: CanvasPoint) {
    const canvas = canvasRef.current;

    if (!canvas || dragRef.current) {
      if (canvas && !dragRef.current) {
        canvas.style.cursor = "default";
      }
      return;
    }

    canvas.style.cursor = hitTestElements(contentRef.current.elements, point) ? "pointer" : "default";
  }

  function handlePointerLeave() {
    if (!dragRef.current && canvasRef.current) {
      canvasRef.current.style.cursor = "default";
    }
  }

  function handlePointerUp(event: PointerEvent<HTMLCanvasElement>) {
    const drag = dragRef.current;

    if (!drag || drag.pointerId !== event.pointerId) {
      return;
    }

    if (canvasRef.current?.releasePointerCapture) {
      canvasRef.current.releasePointerCapture(event.pointerId);
    }
    if (canvasRef.current) {
      canvasRef.current.style.cursor = "default";
    }
    dragRef.current = null;

    if (drag.type === "pan") {
      const nextViewport = viewportFromPanEvent(event, drag, surfaceRect(), currentSurfaceSize());
      previewContentRef.current = null;
      applyViewportLocally(nextViewport);
      onViewportChange(nextViewport, false);
      return;
    }

    if (drag.type === "endpoint") {
      const nextElement = previewContentRef.current?.elements.find((element) => element.id === drag.element.id);
      previewContentRef.current = null;

      if (nextElement) {
        onCommitOperations([{ type: "update", id: nextElement.id, changes: endpointChangesFromElement(nextElement) }]);
      }

      requestDraw();
      return;
    }

    if (drag.type === "marquee") {
      const finalPoint = clientToWorld({ x: event.clientX, y: event.clientY }, surfaceRect(), drag.originalContent.viewport, currentSurfaceSize());
      const selectedIds = selectedElementIdsInBounds(drag.originalContent.elements, worldBoundsFromPoints(drag.startWorld, finalPoint));
      const nextSelection = drag.append ? mergeSelections(drag.originalSelection, selectedIds) : selectedIds;
      setMarqueeRect(null);
      onSelectionChange(nextSelection);
      requestDraw();
      return;
    }

    if (drag.type === "freehand") {
      const finalPoint = clientToWorld({ x: event.clientX, y: event.clientY }, surfaceRect(), drag.originalContent.viewport, currentSurfaceSize());
      const points = appendFreehandPoint(drag.points, finalPoint, 0.5);
      previewContentRef.current = null;

      if (points.length >= 2) {
        const element = freehandElementFromPoints(drag.originalContent, points, drag.element);
        onCommitOperations([{ type: "add", id: element.id, element }], [element.id]);
      } else {
        onSelectionChange([]);
      }

      requestDraw();
      return;
    }

    const nextContent = previewContentRef.current;
    previewContentRef.current = null;

    if (!nextContent) {
      requestDraw();
      return;
    }

    const selected = new Set(drag.elementIds);
    const ops = nextContent.elements.flatMap((element) => selected.has(element.id) ? [{ type: "update" as const, id: element.id, changes: endpointChangesFromElement(element) }] : []);
    onCommitOperations(ops, drag.elementIds);
    requestDraw();
  }

  useEffect(() => {
    const canvas = canvasRef.current;

    if (!canvas) {
      return undefined;
    }

    function handleNativeWheel(event: globalThis.WheelEvent) {
      event.preventDefault();
      const rect = surfaceRect();
      const anchor = clientToViewport({ x: event.clientX, y: event.clientY }, rect, currentSurfaceSize());
      const nextZoom = contentRef.current.viewport.zoom * (event.deltaY > 0 ? 0.9 : 1.1);
      const nextViewport = zoomViewportAroundPoint(contentRef.current.viewport, anchor, nextZoom);
      applyViewportLocally(nextViewport);
      onViewportChange(nextViewport, false);
    }

    canvas.addEventListener("wheel", handleNativeWheel, { passive: false });
    return () => canvas.removeEventListener("wheel", handleNativeWheel);
  }, [onViewportChange]);

  function handleDoubleClick(event: MouseEvent<HTMLCanvasElement>) {
    const currentContent = contentRef.current;
    const point = worldPoint(event);
    const hit = hitTestElements(currentContent.elements, point);

    if (hit?.type === "element" && hit.element.type === "text" && canEdit && !hit.element.locked) {
      event.preventDefault();
      event.stopPropagation();
      onSelectionChange([hit.element.id]);
      onTextEditingChange(hit.element.id);
      return;
    }

    const nextViewport = zoomViewportAroundCenter(currentContent.viewport, currentSurfaceSize(), clampZoom(currentContent.viewport.zoom * 1.2));
    applyViewportLocally(nextViewport);
    onViewportChange(nextViewport, false);
  }

  function commitTextEdit() {
    const elementId = editingTextElementId;
    const element = elementId ? contentRef.current.elements.find((item) => item.id === elementId && item.type === "text") : null;

    onTextEditingChange(null);

    if (!element || element.locked) {
      return;
    }

    const currentText = String(element.data.text ?? "");

    if (textDraft === currentText) {
      return;
    }

    const editor = textEditorRef.current;
    const textHeight = editor ? editor.scrollHeight / contentRef.current.viewport.zoom : Math.max(textElementLineHeight(element), textDraft.split(/\r?\n/).length * textElementLineHeight(element));
    const nextHeight = Math.ceil(textElementVerticalInset * 2 + textHeight);

    const op: CanvasOperation = { type: "update", id: element.id, changes: { data: { ...element.data, text: textDraft }, height: Math.max(element.height, nextHeight) } };
    contentRef.current = applyCanvasOperations(contentRef.current, [op]);
    requestDraw();
    onCommitOperations([op], [element.id]);
  }

  function cancelTextEdit() {
    onTextEditingChange(null);
  }

  function handleTextKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    event.stopPropagation();
    event.nativeEvent.stopImmediatePropagation();

    if (event.key === "Escape") {
      event.preventDefault();
      cancelTextEdit();
      return;
    }

    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      commitTextEdit();
    }
  }

  function handleLineModeChange(elementId: string, mode: LineMode) {
    const element = contentRef.current.elements.find((item) => item.id === elementId);

    if (!canEdit || !element || element.locked || !isLineElement(element) || lineModeFromElement(element) === mode) {
      return;
    }

    const op: CanvasOperation = { type: "update", id: element.id, changes: lineModeChangesForElement(element, mode) };
    previewContentRef.current = null;
    contentRef.current = applyCanvasOperations(contentRef.current, [op]);
    requestDraw();
    onCommitOperations([op], [element.id]);
  }

  function textEditorStyle(element: CanvasElement): CSSProperties {
    const viewportPoint = worldToViewport({ x: element.x, y: element.y + textElementVerticalInset }, content.viewport);
    const fontSize = textElementFontSize(element) * content.viewport.zoom;
    const lineHeight = textElementLineHeight(element) * content.viewport.zoom;

    return {
      left: viewportPoint.x,
      top: viewportPoint.y,
      width: Math.max(64, element.width * content.viewport.zoom),
      minHeight: Math.max(36, (element.height - textElementVerticalInset * 2) * content.viewport.zoom),
      color: textColor(element),
      fontFamily: stringStyle(element.style.fontFamily, "Inter"),
      fontSize: Math.max(12, fontSize),
      fontWeight: stringStyle(element.style.fontWeight, "700"),
      lineHeight: `${lineHeight}px`,
      transform: element.rotation === 0 ? undefined : `rotate(${element.rotation}deg)`
    };
  }

  return (
    <>
      <canvas
        ref={canvasRef}
        className="canvas-surface"
        role="img"
        aria-label={`${title} canvas with ${content.elements.length} elements`}
        width={Math.max(1, Math.round(size.width * renderDpr))}
        height={Math.max(1, Math.round(size.height * renderDpr))}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onPointerLeave={handlePointerLeave}
        onDoubleClick={handleDoubleClick}
      />
      {marqueeRect ? <div className="canvas-marquee" style={marqueeRectStyle(marqueeRect)} /> : null}
      {selectedLineElement ? (
        <div className="canvas-line-mode-toolbar" style={lineModeToolbarStyle(selectedLineElement, content.viewport)} onPointerDown={(event) => event.stopPropagation()}>
          <button type="button" aria-label="Line mode" aria-pressed={lineModeFromElement(selectedLineElement) === "line"} onClick={() => handleLineModeChange(selectedLineElement.id, "line")}>
            <Minus size={10} aria-hidden="true" />
            <span>Line</span>
          </button>
          <button type="button" aria-label="Curve mode" aria-pressed={lineModeFromElement(selectedLineElement) === "curve"} onClick={() => handleLineModeChange(selectedLineElement.id, "curve")}>
            <Spline size={10} aria-hidden="true" />
            <span>Curve</span>
          </button>
        </div>
      ) : null}
      {editingTextElement ? (
        <textarea
          ref={textEditorRef}
          className="canvas-text-editor"
          aria-label="Edit text element"
          value={textDraft}
          style={textEditorStyle(editingTextElement)}
          rows={Math.max(2, textDraft.split(/\r?\n/).length)}
          onBlur={commitTextEdit}
          onChange={(event) => setTextDraft(event.target.value)}
          onKeyDown={handleTextKeyDown}
          onPointerDown={(event) => event.stopPropagation()}
        />
      ) : null}
    </>
  );
}

function marqueeRectStyle(rect: ViewportRect): CSSProperties {
  return {
    left: rect.left,
    top: rect.top,
    width: rect.width,
    height: rect.height
  };
}

function viewportRectFromWorldPoints(start: CanvasPoint, end: CanvasPoint, viewport: CanvasViewportState): ViewportRect {
  const startViewport = worldToViewport(start, viewport);
  const endViewport = worldToViewport(end, viewport);

  return {
    left: Math.min(startViewport.x, endViewport.x),
    top: Math.min(startViewport.y, endViewport.y),
    width: Math.abs(endViewport.x - startViewport.x),
    height: Math.abs(endViewport.y - startViewport.y)
  };
}

function viewportFromPanEvent(event: PointerEvent<HTMLCanvasElement>, drag: Extract<DragState, { type: "pan" }>, rect: SurfaceRect, size: SurfaceSize) {
  const nextViewportPoint = clientToViewport({ x: event.clientX, y: event.clientY }, rect, size);
  return panViewport(drag.viewport, nextViewportPoint.x - drag.startViewport.x, nextViewportPoint.y - drag.startViewport.y);
}

function applyViewportLocallyToContent(content: CanvasContent, viewport: CanvasViewportState): CanvasContent {
  return {
    ...content,
    viewport
  };
}

function worldBoundsFromPoints(start: CanvasPoint, end: CanvasPoint): CanvasBounds {
  return {
    minX: Math.min(start.x, end.x),
    minY: Math.min(start.y, end.y),
    maxX: Math.max(start.x, end.x),
    maxY: Math.max(start.y, end.y)
  };
}

function selectedElementIdsInBounds(elements: CanvasElement[], bounds: CanvasBounds) {
  if (bounds.maxX - bounds.minX < 2 && bounds.maxY - bounds.minY < 2) {
    return [];
  }

  return elements.filter((element) => boundsIntersect(elementBounds(element, 4), bounds)).map((element) => element.id);
}

function boundsIntersect(first: CanvasBounds, second: CanvasBounds) {
  return first.maxX >= second.minX && first.minX <= second.maxX && first.maxY >= second.minY && first.minY <= second.maxY;
}

function mergeSelections(current: string[], next: string[]) {
  return [...current, ...next.filter((id) => !current.includes(id))];
}

function freehandElementFromPoints(content: CanvasContent, points: CanvasPoint[], current?: CanvasElement): CanvasElement {
  const normalized = normalizeFreehandPoints(points);

  return createCanvasElement("freehand", content.elements.length, {
    ...current,
    ...normalized,
    id: current?.id,
    zIndex: current?.zIndex ?? content.elements.length + 1,
    data: {
      ...current?.data,
      ...normalized.data
    }
  });
}

function appendFreehandPoint(points: CanvasPoint[], point: CanvasPoint, minDistance: number) {
  const last = points[points.length - 1];

  if (!last || pointDistance(last, point) >= minDistance) {
    return [...points, point];
  }

  return points;
}

function pointDistance(a: CanvasPoint, b: CanvasPoint) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function hasAnimatedCurves(content: CanvasContent) {
  return content.elements.some((element) => isLineElement(element) && (element.data.curvePreset === "pulse" || element.data.curvePreset === "pulseReverse" || element.data.curvePreset === "signal" || element.data.curvePreset === "signalReverse"));
}

function endpointChangesFromElement(element: CanvasElement): Partial<CanvasElement> {
  if (isLineElement(element)) {
    const lineMode = lineModeFromElement(element);
    const points = lineMode === "curve" ? {
      start: pointFromElement(element, "start"),
      controlStart: pointFromElement(element, "controlStart"),
      controlEnd: pointFromElement(element, "controlEnd"),
      end: pointFromElement(element, "end")
    } : straightLinePoints(pointFromElement(element, "start"), pointFromElement(element, "end"));
    const data = {
      ...element.data,
      start: points.start,
      end: points.end,
      lineMode,
      path: curveToPath(points)
    };

    if (lineMode === "curve") {
      data.controlStart = points.controlStart;
      data.controlEnd = points.controlEnd;
    } else {
      delete data.controlStart;
      delete data.controlEnd;
    }

    return {
      x: element.x,
      y: element.y,
      width: element.width,
      height: element.height,
      data
    };
  }

  if (element.type === "freehand" && Array.isArray(element.data.points)) {
    return {
      x: element.x,
      y: element.y,
      data: {
        ...element.data,
        points: element.data.points,
        path: pointsToPath(element.data.points)
      }
    };
  }

  return {
    x: Math.round(element.x),
    y: Math.round(element.y),
    data: element.data
  };
}

function pointFromElement(element: CanvasElement, handle: ConnectorEndpoint): CanvasPoint {
  const fallback = curvePointsFromElement(element)[handle];
  const value = element.data[handle];

  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return fallback;
  }

  const record = value as Record<string, unknown>;
  return {
    x: typeof record.x === "number" ? record.x : fallback.x,
    y: typeof record.y === "number" ? record.y : fallback.y
  };
}

function lineModeChangesForElement(element: CanvasElement, mode: LineMode): Partial<CanvasElement> {
  const current = curvePointsFromElement(element);
  const points = mode === "curve" ? current : straightLinePoints(current.start, current.end);
  const bounds = boundsFromPoints(Object.values(points));
  const data = {
    ...element.data,
    start: points.start,
    end: points.end,
    lineMode: mode,
    path: curveToPath(points)
  };

  if (mode === "curve") {
    data.controlStart = points.controlStart;
    data.controlEnd = points.controlEnd;
  } else {
    delete data.controlStart;
    delete data.controlEnd;
  }

  return {
    x: bounds.minX,
    y: bounds.minY,
    width: Math.max(1, bounds.maxX - bounds.minX),
    height: Math.max(1, bounds.maxY - bounds.minY),
    data
  };
}

function lineModeToolbarStyle(element: CanvasElement, viewport: CanvasViewportState): CSSProperties {
  const bounds = elementBounds(element, 12);
  const point = worldToViewport({ x: bounds.maxX + 10, y: bounds.minY - 36 }, viewport);

  return {
    left: Math.max(8, Math.round(point.x)),
    top: Math.max(8, Math.round(point.y))
  };
}

function straightLinePoints(start: CanvasPoint, end: CanvasPoint) {
  return {
    start,
    controlStart: {
      x: start.x + (end.x - start.x) / 3,
      y: start.y + (end.y - start.y) / 3
    },
    controlEnd: {
      x: start.x + (end.x - start.x) * 2 / 3,
      y: start.y + (end.y - start.y) * 2 / 3
    },
    end
  };
}

function boundsFromPoints(points: CanvasPoint[]): CanvasBounds {
  return points.reduce(
    (current, point) => ({
      minX: Math.min(current.minX, point.x),
      minY: Math.min(current.minY, point.y),
      maxX: Math.max(current.maxX, point.x),
      maxY: Math.max(current.maxY, point.y)
    }),
    { minX: Number.POSITIVE_INFINITY, minY: Number.POSITIVE_INFINITY, maxX: Number.NEGATIVE_INFINITY, maxY: Number.NEGATIVE_INFINITY }
  );
}

function isLineElement(element: CanvasElement) {
  return element.type === "connector" || element.type === "arrow" || element.type === "curve";
}

function textColor(element: CanvasElement) {
  const stroke = stringStyle(element.style.stroke, "#273142");
  return stroke === "transparent" ? "#273142" : stroke;
}

function stringStyle(value: unknown, fallback: string) {
  if (typeof value === "number") {
    return String(value);
  }

  return typeof value === "string" && value.length > 0 ? value : fallback;
}

function numericStyle(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}
