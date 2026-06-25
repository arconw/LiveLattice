import type { CanvasContent, CanvasElement, CanvasElementData, CanvasElementStyle, CanvasElementType, CanvasPoint, CanvasViewportState } from "../../contracts/canvas";
import { defaultCanvasContent, normalizeCanvasContent, toPersistedCanvasContent } from "../../contracts/canvas";

export type CanvasTool = "select" | "marquee" | "rectangle" | "circle" | "text" | "image" | "connector" | "arrow" | "curve" | "freehand" | "comment";

export type CanvasOperation =
  | { type: "add"; id: string; element: CanvasElement }
  | { type: "update"; id: string; changes: Partial<CanvasElement> }
  | { type: "remove"; id: string }
  | { type: "update-state"; state: { viewport?: CanvasViewportState; metadata?: Partial<CanvasContent["metadata"]> } };

export type SelectionMode = "replace" | "toggle" | "append";
export type ConnectorEndpoint = "start" | "end" | "controlStart" | "controlEnd";
export type LineMode = "line" | "curve";

export type CurvePoints = {
  start: CanvasPoint;
  controlStart: CanvasPoint;
  controlEnd: CanvasPoint;
  end: CanvasPoint;
};

type ConnectorEndpointOptions = {
  snapElements?: CanvasElement[];
  snapThreshold?: number;
};

export function createCanvasElement(type: CanvasElementType, index: number, overrides: Partial<CanvasElement> = {}): CanvasElement {
  const id = overrides.id ?? createId("el");
  const base = baseElementForType(type, index);

  return {
    ...base,
    ...overrides,
    id,
    type,
    style: {
      ...base.style,
      ...overrides.style
    },
    data: {
      ...base.data,
      ...overrides.data
    },
    groupId: overrides.groupId ?? null
  };
}

export function applyCanvasOperations(content: CanvasContent, operations: CanvasOperation[]): CanvasContent {
  return normalizeCanvasContent(
    operations.reduce<CanvasContent>((current, operation) => {
      if (operation.type === "add") {
        return {
          ...current,
          elements: replaceOrAppendElement(current.elements, operation.element)
        };
      }

      if (operation.type === "update") {
        return {
          ...current,
          elements: current.elements.map((element) => {
            if (element.id !== operation.id || !canApplyElementUpdate(element, operation.changes)) {
              return element;
            }

            return mergeElementChanges(element, operation.changes);
          })
        };
      }

      if (operation.type === "remove") {
        return {
          ...current,
          elements: current.elements.filter((element) => element.id !== operation.id || element.locked)
        };
      }

      return {
        ...current,
        viewport: operation.state.viewport ? { ...operation.state.viewport } : current.viewport,
        metadata: operation.state.metadata ? { ...current.metadata, ...operation.state.metadata } : current.metadata
      };
    }, content)
  );
}

export function selectElement(current: string[], elementId: string, mode: SelectionMode): string[] {
  if (mode === "replace") {
    return [elementId];
  }

  if (mode === "append") {
    return current.includes(elementId) ? current : [...current, elementId];
  }

  return current.includes(elementId) ? current.filter((id) => id !== elementId) : [...current, elementId];
}

export function moveSelection(content: CanvasContent, selection: string[], dx: number, dy: number): { content: CanvasContent; ops: CanvasOperation[] } {
  const selected = new Set(selection);
  const ops = content.elements.flatMap((element) => {
    if (!selected.has(element.id) || element.locked) {
      return [];
    }

    return [{ type: "update" as const, id: element.id, changes: translateCanvasElement(element, dx, dy) }];
  });

  return { content: applyCanvasOperations(content, ops), ops };
}

export function translateCanvasElement(element: CanvasElement, dx: number, dy: number): Partial<CanvasElement> {
  const nextData: CanvasElementData = { ...element.data };

  if (isLineElement(element)) {
    const points = curvePointsFromElement(element);
    const nextPoints = {
      start: translatePoint(points.start, dx, dy),
      controlStart: translatePoint(points.controlStart, dx, dy),
      controlEnd: translatePoint(points.controlEnd, dx, dy),
      end: translatePoint(points.end, dx, dy)
    };
    nextData.start = nextPoints.start;
    nextData.end = nextPoints.end;
    if (lineModeFromElement(element) === "curve") {
      nextData.controlStart = nextPoints.controlStart;
      nextData.controlEnd = nextPoints.controlEnd;
      nextData.path = curveToPath(nextPoints);
    } else {
      delete nextData.controlStart;
      delete nextData.controlEnd;
      nextData.path = curveToPath(straightCurvePoints(nextPoints.start, nextPoints.end));
    }
  }

  if (element.type === "freehand" && Array.isArray(element.data.points)) {
    nextData.points = element.data.points.map((point) => ({ x: point.x + dx, y: point.y + dy }));
    nextData.path = pointsToPath(nextData.points);
  }

  return {
    x: element.x + dx,
    y: element.y + dy,
    data: nextData
  };
}

export function updateConnectorEndpoint(element: CanvasElement, handle: ConnectorEndpoint, point: CanvasPoint, options: ConnectorEndpointOptions = {}): Partial<CanvasElement> {
  if (!isLineElement(element)) {
    return {};
  }

  const nextLineMode = handle === "controlStart" || handle === "controlEnd" ? "curve" : lineModeFromElement(element);
  const snap = (handle === "start" || handle === "end") && options.snapElements ? snapConnectorEndpoint(point, options.snapElements, element.id, options.snapThreshold ?? 18) : null;
  const nextPoint = snap?.point ?? point;
  const currentPoints = curvePointsFromElement(element);
  const points: CurvePoints = {
    start: handle === "start" ? nextPoint : currentPoints.start,
    controlStart: handle === "controlStart" ? nextPoint : currentPoints.controlStart,
    controlEnd: handle === "controlEnd" ? nextPoint : currentPoints.controlEnd,
    end: handle === "end" ? nextPoint : currentPoints.end
  };

  if (nextLineMode === "line" || ((handle === "start" || handle === "end") && !isPointData(element.data.controlStart))) {
    points.controlStart = straightControlPoint(points.start, points.end, 1 / 3);
  }

  if (nextLineMode === "line" || ((handle === "start" || handle === "end") && !isPointData(element.data.controlEnd))) {
    points.controlEnd = straightControlPoint(points.start, points.end, 2 / 3);
  }

  const bounds = boundsFromCanvasPoints(Object.values(points));
  const nextData: CanvasElementData = {
    ...element.data,
    start: points.start,
    end: points.end,
    lineMode: nextLineMode,
    path: curveToPath(points)
  };

  if (nextLineMode === "curve") {
    nextData.controlStart = points.controlStart;
    nextData.controlEnd = points.controlEnd;
  } else {
    delete nextData.controlStart;
    delete nextData.controlEnd;
  }

  if (handle === "start" || handle === "end") {
    if (snap) {
      nextData[`${handle}ElementId`] = snap.element.id;
      nextData[`${handle}Anchor`] = {
        x: snap.element.width === 0 ? 0 : (nextPoint.x - snap.element.x) / snap.element.width,
        y: snap.element.height === 0 ? 0 : (nextPoint.y - snap.element.y) / snap.element.height
      };
    } else {
      delete nextData[`${handle}ElementId`];
      delete nextData[`${handle}Anchor`];
    }
  }

  return {
    x: bounds.minX,
    y: bounds.minY,
    width: Math.max(1, bounds.maxX - bounds.minX),
    height: Math.max(1, bounds.maxY - bounds.minY),
    data: nextData
  };
}

export function updateSelectionStyle(content: CanvasContent, selection: string[], style: CanvasElementStyle): { content: CanvasContent; ops: CanvasOperation[] } {
  const selected = new Set(selection);
  const ops = content.elements.flatMap((element) => {
    if (!selected.has(element.id) || element.locked) {
      return [];
    }

    return [{ type: "update" as const, id: element.id, changes: { style: { ...element.style, ...style } } }];
  });

  return { content: applyCanvasOperations(content, ops), ops };
}

export function updateSelectionGeometry(content: CanvasContent, selection: string[], changes: Partial<Pick<CanvasElement, "x" | "y" | "width" | "height" | "rotation">>): { content: CanvasContent; ops: CanvasOperation[] } {
  const selected = new Set(selection);
  const ops = content.elements.flatMap((element) => {
    if (!selected.has(element.id) || element.locked) {
      return [];
    }

    return [{ type: "update" as const, id: element.id, changes: geometryChangesForElement(element, changes) }];
  });

  return { content: applyCanvasOperations(content, ops), ops };
}

export function duplicateSelection(content: CanvasContent, selection: string[]): { content: CanvasContent; selection: string[]; ops: CanvasOperation[] } {
  const selected = new Set(selection);
  const nextElements = content.elements.filter((element) => selected.has(element.id) && !element.locked).map((element, index) => ({
    ...toPersistedCanvasContent({ ...defaultCanvasContent, elements: [element] }).elements[0],
    id: createId("el"),
    x: element.x + 32,
    y: element.y + 32,
    zIndex: highestZIndex(content.elements) + index + 1
  }));
  const ops = nextElements.map((element) => ({ type: "add" as const, id: element.id, element }));

  return {
    content: applyCanvasOperations(content, ops),
    selection: nextElements.map((element) => element.id),
    ops
  };
}

export function deleteSelection(content: CanvasContent, selection: string[]): { content: CanvasContent; selection: string[]; ops: CanvasOperation[] } {
  const selected = new Set(selection);
  const ops = content.elements.flatMap((element) => {
    if (!selected.has(element.id) || element.locked) {
      return [];
    }

    return [{ type: "remove" as const, id: element.id }];
  });

  return {
    content: applyCanvasOperations(content, ops),
    selection: [],
    ops
  };
}

export function setSelectionLocked(content: CanvasContent, selection: string[], locked: boolean): { content: CanvasContent; ops: CanvasOperation[] } {
  const selected = new Set(selection);
  const ops = content.elements.flatMap((element) => {
    if (!selected.has(element.id)) {
      return [];
    }

    return [{ type: "update" as const, id: element.id, changes: { locked } }];
  });

  return { content: applyCanvasOperations(content, ops), ops };
}

export function updateZOrder(content: CanvasContent, selection: string[], direction: "front" | "back"): { content: CanvasContent; ops: CanvasOperation[] } {
  const selected = new Set(selection);
  const highest = highestZIndex(content.elements);
  const lowest = lowestZIndex(content.elements);
  const ops = content.elements.flatMap((element, index) => {
    if (!selected.has(element.id) || element.locked) {
      return [];
    }

    return [{ type: "update" as const, id: element.id, changes: { zIndex: direction === "front" ? highest + index + 1 : lowest - index - 1 } }];
  });

  return { content: applyCanvasOperations(content, ops), ops };
}

export function updateViewport(content: CanvasContent, viewport: CanvasViewportState): { content: CanvasContent; ops: CanvasOperation[] } {
  const nextViewport = {
    zoom: Math.min(4, Math.max(0.1, viewport.zoom)),
    panX: viewport.panX,
    panY: viewport.panY
  };
  const ops: CanvasOperation[] = [{ type: "update-state", state: { viewport: nextViewport } }];

  return { content: applyCanvasOperations(content, ops), ops };
}

export function fitViewportToContent(content: CanvasContent, size = { width: 960, height: 672 }): CanvasViewportState {
  if (content.elements.length === 0) {
    return { zoom: 1, panX: 0, panY: 0 };
  }

  const bounds = content.elements.map(contentElementBounds).reduce(
    (current, boundsItem) => ({
      minX: Math.min(current.minX, boundsItem.minX),
      minY: Math.min(current.minY, boundsItem.minY),
      maxX: Math.max(current.maxX, boundsItem.maxX),
      maxY: Math.max(current.maxY, boundsItem.maxY)
    }),
    { minX: Number.POSITIVE_INFINITY, minY: Number.POSITIVE_INFINITY, maxX: Number.NEGATIVE_INFINITY, maxY: Number.NEGATIVE_INFINITY }
  );
  const contentWidth = Math.max(1, bounds.maxX - bounds.minX);
  const contentHeight = Math.max(1, bounds.maxY - bounds.minY);
  const paddingX = Math.max(48, size.width * 0.1);
  const paddingY = Math.max(48, size.height * 0.1);
  const availableWidth = Math.max(1, size.width - paddingX * 2);
  const availableHeight = Math.max(1, size.height - paddingY * 2);
  const zoom = Math.min(1.6, Math.max(0.1, Math.min(availableWidth / contentWidth, availableHeight / contentHeight)));

  return {
    zoom,
    panX: Math.round((size.width - contentWidth * zoom) / 2 - bounds.minX * zoom),
    panY: Math.round((size.height - contentHeight * zoom) / 2 - bounds.minY * zoom)
  };
}

function contentElementBounds(element: CanvasElement) {
  if (isLineElement(element)) {
    const points = curvePointsFromElement(element);
    return boundsFromCanvasPoints(Object.values(points));
  }

  if (element.type === "freehand" && Array.isArray(element.data.points) && element.data.points.length > 0) {
    return element.data.points.reduce(
      (current, point) => ({
        minX: Math.min(current.minX, point.x),
        minY: Math.min(current.minY, point.y),
        maxX: Math.max(current.maxX, point.x),
        maxY: Math.max(current.maxY, point.y)
      }),
      { minX: Number.POSITIVE_INFINITY, minY: Number.POSITIVE_INFINITY, maxX: Number.NEGATIVE_INFINITY, maxY: Number.NEGATIVE_INFINITY }
    );
  }

  return {
    minX: element.x,
    minY: element.y,
    maxX: element.x + element.width,
    maxY: element.y + element.height
  };
}

export function elementLabel(element: CanvasElement) {
  const text = typeof element.data.text === "string" && element.data.text.trim().length > 0 ? element.data.text.trim() : element.type;
  return `${text} ${element.locked ? "locked" : "editable"} at x ${Math.round(element.x)} y ${Math.round(element.y)}`;
}

export function elementCenter(element: CanvasElement): CanvasPoint {
  if (isLineElement(element)) {
    const bounds = boundsFromCanvasPoints(Object.values(curvePointsFromElement(element)));
    return { x: (bounds.minX + bounds.maxX) / 2, y: (bounds.minY + bounds.maxY) / 2 };
  }

  return { x: element.x + element.width / 2, y: element.y + element.height / 2 };
}

export function pointFromData(value: unknown, fallback: CanvasPoint): CanvasPoint {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return fallback;
  }

  const record = value as Record<string, unknown>;
  return {
    x: typeof record.x === "number" ? record.x : fallback.x,
    y: typeof record.y === "number" ? record.y : fallback.y
  };
}

export function pointsToPath(points: CanvasPoint[]) {
  if (points.length === 0) {
    return "";
  }

  const [first, ...rest] = points;
  return rest.reduce((path, point) => `${path} L ${point.x} ${point.y}`, `M ${first.x} ${first.y}`);
}

export function curvePointsFromElement(element: CanvasElement): CurvePoints {
  const start = pointFromData(element.data.start, { x: element.x, y: element.type === "curve" ? element.y + element.height * 0.7 : element.y });
  const end = pointFromData(element.data.end, { x: element.x + element.width, y: element.type === "curve" ? element.y + element.height * 0.35 : element.y + element.height });

  if (element.type === "connector" || element.type === "arrow") {
    if (lineModeFromElement(element) === "line") {
      return straightCurvePoints(start, end);
    }

    return {
      start,
      controlStart: pointFromData(element.data.controlStart, straightControlPoint(start, end, 1 / 3)),
      controlEnd: pointFromData(element.data.controlEnd, straightControlPoint(start, end, 2 / 3)),
      end
    };
  }

  return {
    start,
    controlStart: pointFromData(element.data.controlStart, { x: element.x + element.width * 0.25, y: element.y }),
    controlEnd: pointFromData(element.data.controlEnd, { x: element.x + element.width * 0.75, y: element.y + element.height }),
    end
  };
}

export function curveToPath(points: CurvePoints) {
  return `M ${points.start.x} ${points.start.y} C ${points.controlStart.x} ${points.controlStart.y} ${points.controlEnd.x} ${points.controlEnd.y} ${points.end.x} ${points.end.y}`;
}

export function lineModeFromElement(element: CanvasElement): LineMode {
  return element.data.lineMode === "curve" || element.type === "curve" ? "curve" : "line";
}

function isLineElement(element: CanvasElement) {
  return element.type === "connector" || element.type === "arrow" || element.type === "curve";
}

function isPointData(value: unknown): value is CanvasPoint {
  return typeof value === "object" && value !== null && !Array.isArray(value) && typeof (value as Record<string, unknown>).x === "number" && typeof (value as Record<string, unknown>).y === "number";
}

function straightControlPoint(start: CanvasPoint, end: CanvasPoint, ratio: number): CanvasPoint {
  return {
    x: start.x + (end.x - start.x) * ratio,
    y: start.y + (end.y - start.y) * ratio
  };
}

function straightCurvePoints(start: CanvasPoint, end: CanvasPoint): CurvePoints {
  return {
    start,
    controlStart: straightControlPoint(start, end, 1 / 3),
    controlEnd: straightControlPoint(start, end, 2 / 3),
    end
  };
}

export function normalizeFreehandPoints(points: CanvasPoint[]): Pick<CanvasElement, "x" | "y" | "width" | "height"> & { data: Pick<CanvasElementData, "points" | "path"> } {
  const normalizedPoints = points.flatMap((point) => Number.isFinite(point.x) && Number.isFinite(point.y) ? [{ x: roundCoordinate(point.x), y: roundCoordinate(point.y) }] : []);

  if (normalizedPoints.length === 0) {
    return { x: 0, y: 0, width: 1, height: 1, data: { points: [], path: "" } };
  }

  const bounds = normalizedPoints.reduce(
    (current, point) => ({
      minX: Math.min(current.minX, point.x),
      minY: Math.min(current.minY, point.y),
      maxX: Math.max(current.maxX, point.x),
      maxY: Math.max(current.maxY, point.y)
    }),
    { minX: Number.POSITIVE_INFINITY, minY: Number.POSITIVE_INFINITY, maxX: Number.NEGATIVE_INFINITY, maxY: Number.NEGATIVE_INFINITY }
  );

  return {
    x: bounds.minX,
    y: bounds.minY,
    width: Math.max(1, bounds.maxX - bounds.minX),
    height: Math.max(1, bounds.maxY - bounds.minY),
    data: {
      points: normalizedPoints,
      path: pointsToPath(normalizedPoints)
    }
  };
}

function baseElementForType(type: CanvasElementType, index: number): Omit<CanvasElement, "id"> {
  const x = 160 + (index % 4) * 56;
  const y = 140 + (index % 5) * 48;
  const style = styleForType(type);
  const data = dataForType(type, x, y);
  const size = sizeForType(type);

  return {
    type,
    x,
    y,
    width: size.width,
    height: size.height,
    rotation: 0,
    style,
    data,
    zIndex: index + 1,
    locked: false,
    groupId: null
  };
}

function sizeForType(type: CanvasElementType) {
  if (type === "circle") {
    return { width: 128, height: 128 };
  }

  if (type === "text") {
    return { width: 220, height: 76 };
  }

  if (type === "arrow" || type === "freehand") {
    return { width: 220, height: 96 };
  }

  if (type === "connector" || type === "curve") {
    return { width: 260, height: 148 };
  }

  return { width: 190, height: 104 };
}

function styleForType(type: CanvasElementType): CanvasElementStyle {
  if (type === "text") {
    return { fill: "transparent", stroke: "transparent", strokeWidth: 0, opacity: 1, fontFamily: "Inter", fontSize: 18, fontWeight: 700 };
  }

  if (type === "arrow") {
    return { fill: "transparent", stroke: "#273142", strokeWidth: 2, opacity: 1 };
  }

  if (type === "freehand") {
    return { fill: "transparent", stroke: "#273142", strokeWidth: 3, opacity: 1 };
  }

  if (type === "connector" || type === "curve") {
    return { fill: "transparent", stroke: "#273142", strokeWidth: 2, opacity: 1 };
  }

  if (type === "circle") {
    return { fill: "#ffffff", stroke: "#8a5cff", strokeWidth: 2, opacity: 1 };
  }

  return { fill: "#ffffff", stroke: "#4d7cfe", strokeWidth: 2, opacity: 1 };
}

function dataForType(type: CanvasElementType, x: number, y: number): CanvasElementData {
  if (type === "text") {
    return { text: "New text" };
  }

  if (type === "image") {
    return { text: "Image placeholder" };
  }

  if (type === "connector") {
    const start = { x, y };
    const end = { x: x + 260, y: y + 62 };
    const points = straightCurvePoints(start, end);
    return { start, end, lineMode: "line", path: curveToPath(points), curvePreset: "solid" };
  }

  if (type === "arrow") {
    const start = { x, y };
    const end = { x: x + 220, y: y + 96 };
    const points: CurvePoints = {
      start,
      controlStart: straightControlPoint(start, end, 1 / 3),
      controlEnd: straightControlPoint(start, end, 2 / 3),
      end
    };
    return { start, end, lineMode: "line", path: curveToPath(points), curvePreset: "solid" };
  }

  if (type === "curve") {
    const points: CurvePoints = {
      start: { x, y: y + 86 },
      controlStart: { x: x + 72, y: y + 8 },
      controlEnd: { x: x + 188, y: y + 148 },
      end: { x: x + 260, y: y + 62 }
    };
    return { ...points, path: curveToPath(points), curvePreset: "dashed" };
  }

  if (type === "freehand") {
    return { points: [], path: "" };
  }

  return { text: type === "rectangle" ? "New rectangle" : "New circle" };
}

function replaceOrAppendElement(elements: CanvasElement[], nextElement: CanvasElement) {
  const found = elements.some((element) => element.id === nextElement.id);
  const next = found ? elements.map((element) => (element.id === nextElement.id ? nextElement : element)) : [...elements, nextElement];
  return next.sort((a, b) => a.zIndex - b.zIndex);
}

function canApplyElementUpdate(element: CanvasElement, changes: Partial<CanvasElement>) {
  if (!element.locked) {
    return true;
  }

  const changedKeys = Object.keys(changes);
  return changedKeys.length === 1 && changedKeys[0] === "locked" && changes.locked === false;
}

function mergeElementChanges(element: CanvasElement, changes: Partial<CanvasElement>): CanvasElement {
  return {
    ...element,
    ...changes,
    width: changes.width === undefined ? element.width : Math.max(1, changes.width),
    height: changes.height === undefined ? element.height : Math.max(1, changes.height),
    style: changes.style ? { ...element.style, ...changes.style } : element.style,
    data: changes.data ? { ...changes.data } : element.data,
    groupId: changes.groupId === undefined ? element.groupId : changes.groupId
  };
}

function geometryChangesForElement(element: CanvasElement, changes: Partial<Pick<CanvasElement, "x" | "y" | "width" | "height" | "rotation">>): Partial<CanvasElement> {
  if (element.type !== "connector" && element.type !== "arrow" && element.type !== "curve" && element.type !== "freehand") {
    return changes;
  }

  const dx = changes.x === undefined ? 0 : changes.x - element.x;
  const dy = changes.y === undefined ? 0 : changes.y - element.y;
  const translated = dx !== 0 || dy !== 0 ? translateCanvasElement(element, dx, dy) : {};

  return {
    ...translated,
    ...(changes.width === undefined ? {} : { width: changes.width }),
    ...(changes.height === undefined ? {} : { height: changes.height }),
    ...(changes.rotation === undefined ? {} : { rotation: changes.rotation })
  };
}

function translatePoint(point: CanvasPoint, dx: number, dy: number): CanvasPoint {
  return { x: point.x + dx, y: point.y + dy };
}

function boundsFromCanvasPoints(points: CanvasPoint[]) {
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

function highestZIndex(elements: CanvasElement[]) {
  return elements.reduce((highest, element) => Math.max(highest, element.zIndex), 0);
}

function lowestZIndex(elements: CanvasElement[]) {
  return elements.reduce((lowest, element) => Math.min(lowest, element.zIndex), 0);
}

function createId(prefix: string) {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `${prefix}-${crypto.randomUUID()}`;
  }

  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function roundCoordinate(value: number) {
  return Math.round(value * 100) / 100;
}

function snapConnectorEndpoint(point: CanvasPoint, elements: CanvasElement[], excludeId: string, threshold: number) {
  return elements.reduce<{ element: CanvasElement; point: CanvasPoint; distance: number } | null>((closest, element) => {
    if (element.id === excludeId || element.type === "connector" || element.type === "arrow" || element.type === "curve" || element.type === "freehand") {
      return closest;
    }

    const boundaryPoint = nearestElementBoundaryPoint(element, point);
    const distance = Math.hypot(point.x - boundaryPoint.x, point.y - boundaryPoint.y);

    if (distance > threshold || (closest && closest.distance <= distance)) {
      return closest;
    }

    return { element, point: boundaryPoint, distance };
  }, null);
}

function nearestElementBoundaryPoint(element: CanvasElement, point: CanvasPoint): CanvasPoint {
  if (element.type === "circle") {
    return nearestEllipseBoundaryPoint(element, point);
  }

  return nearestRectBoundaryPoint(element, point);
}

function nearestRectBoundaryPoint(element: CanvasElement, point: CanvasPoint): CanvasPoint {
  const minX = element.x;
  const minY = element.y;
  const maxX = element.x + element.width;
  const maxY = element.y + element.height;
  const insideX = point.x >= minX && point.x <= maxX;
  const insideY = point.y >= minY && point.y <= maxY;

  if (insideX && insideY) {
    const distances = [
      { edge: "left", value: point.x - minX },
      { edge: "right", value: maxX - point.x },
      { edge: "top", value: point.y - minY },
      { edge: "bottom", value: maxY - point.y }
    ].sort((a, b) => a.value - b.value);
    const edge = distances[0]?.edge;

    if (edge === "left") {
      return { x: minX, y: point.y };
    }

    if (edge === "right") {
      return { x: maxX, y: point.y };
    }

    if (edge === "top") {
      return { x: point.x, y: minY };
    }

    return { x: point.x, y: maxY };
  }

  if (insideX) {
    return { x: point.x, y: point.y < minY ? minY : maxY };
  }

  if (insideY) {
    return { x: point.x < minX ? minX : maxX, y: point.y };
  }

  return {
    x: Math.min(maxX, Math.max(minX, point.x)),
    y: Math.min(maxY, Math.max(minY, point.y))
  };
}

function nearestEllipseBoundaryPoint(element: CanvasElement, point: CanvasPoint): CanvasPoint {
  const rx = element.width / 2;
  const ry = element.height / 2;
  const cx = element.x + rx;
  const cy = element.y + ry;
  const dx = point.x - cx;
  const dy = point.y - cy;

  if (rx <= 0 || ry <= 0 || (dx === 0 && dy === 0)) {
    return { x: cx + rx, y: cy };
  }

  const scale = 1 / Math.sqrt((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry));
  return {
    x: cx + dx * scale,
    y: cy + dy * scale
  };
}
