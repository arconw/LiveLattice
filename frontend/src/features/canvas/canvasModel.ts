import type { CanvasContent, CanvasElement, CanvasElementData, CanvasElementStyle, CanvasElementType, CanvasPoint, CanvasViewportState } from "../../contracts/canvas";
import { defaultCanvasContent, normalizeCanvasContent, toPersistedCanvasContent } from "../../contracts/canvas";

export type CanvasTool = "select" | "rectangle" | "circle" | "text" | "image" | "connector" | "arrow" | "freehand" | "comment";

export type CanvasOperation =
  | { type: "add"; id: string; element: CanvasElement }
  | { type: "update"; id: string; changes: Partial<CanvasElement> }
  | { type: "remove"; id: string }
  | { type: "update-state"; state: { viewport?: CanvasViewportState; metadata?: Partial<CanvasContent["metadata"]> } };

export type SelectionMode = "replace" | "toggle" | "append";

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

    return [{ type: "update" as const, id: element.id, changes: { x: element.x + dx, y: element.y + dy } }];
  });

  return { content: applyCanvasOperations(content, ops), ops };
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

    return [{ type: "update" as const, id: element.id, changes }];
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

export function fitViewportToContent(content: CanvasContent): CanvasViewportState {
  if (content.elements.length === 0) {
    return { zoom: 1, panX: 0, panY: 0 };
  }

  const bounds = content.elements.reduce(
    (current, element) => ({
      minX: Math.min(current.minX, element.x),
      minY: Math.min(current.minY, element.y),
      maxX: Math.max(current.maxX, element.x + element.width),
      maxY: Math.max(current.maxY, element.y + element.height)
    }),
    { minX: Number.POSITIVE_INFINITY, minY: Number.POSITIVE_INFINITY, maxX: Number.NEGATIVE_INFINITY, maxY: Number.NEGATIVE_INFINITY }
  );
  const contentWidth = Math.max(1, bounds.maxX - bounds.minX);
  const contentHeight = Math.max(1, bounds.maxY - bounds.minY);
  const zoom = Math.min(1.4, Math.max(0.35, Math.min(920 / contentWidth, 520 / contentHeight)));

  return {
    zoom,
    panX: Math.round(48 - bounds.minX * zoom),
    panY: Math.round(48 - bounds.minY * zoom)
  };
}

export function elementLabel(element: CanvasElement) {
  const text = typeof element.data.text === "string" && element.data.text.trim().length > 0 ? element.data.text.trim() : element.type;
  return `${text} ${element.locked ? "locked" : "editable"} at x ${Math.round(element.x)} y ${Math.round(element.y)}`;
}

export function elementCenter(element: CanvasElement): CanvasPoint {
  if (element.type === "connector" || element.type === "arrow") {
    const start = pointFromData(element.data.start, { x: element.x, y: element.y });
    const end = pointFromData(element.data.end, { x: element.x + element.width, y: element.y + element.height });
    return { x: (start.x + end.x) / 2, y: (start.y + end.y) / 2 };
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

  if (type === "connector" || type === "arrow" || type === "freehand") {
    return { width: 220, height: 96 };
  }

  return { width: 190, height: 104 };
}

function styleForType(type: CanvasElementType): CanvasElementStyle {
  if (type === "text") {
    return { fill: "transparent", stroke: "transparent", strokeWidth: 0, opacity: 1, fontFamily: "Inter", fontSize: 18, fontWeight: 700 };
  }

  if (type === "connector" || type === "arrow" || type === "freehand") {
    return { fill: "transparent", stroke: "#273142", strokeWidth: 3, opacity: 1 };
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

  if (type === "connector" || type === "arrow") {
    return { start: { x, y }, end: { x: x + 220, y: y + 96 } };
  }

  if (type === "freehand") {
    return { points: [{ x, y: y + 40 }, { x: x + 48, y }, { x: x + 104, y: y + 58 }, { x: x + 180, y: y + 20 }] };
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
    data: changes.data ? { ...element.data, ...changes.data } : element.data,
    groupId: changes.groupId === undefined ? element.groupId : changes.groupId
  };
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
