import type { CanvasElement, CanvasPoint } from "../../contracts/canvas";
import { curvePointsFromElement, lineModeFromElement } from "./canvasModel";
import type { ConnectorEndpoint } from "./canvasModel";

export type CanvasHitTarget =
  | { type: "element"; element: CanvasElement }
  | { type: "handle"; element: CanvasElement; handle: ConnectorEndpoint };

export type CanvasBounds = {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
};

export function hitTestElements(elements: CanvasElement[], point: CanvasPoint): CanvasHitTarget | null {
  const ordered = [...elements].sort((a, b) => b.zIndex - a.zIndex);

  for (const element of ordered) {
    const handle = hitTestEndpointHandle(element, point);
    if (handle) {
      return { type: "handle", element, handle };
    }

    if (hitTestElement(element, point)) {
      return { type: "element", element };
    }
  }

  return null;
}

export function hitTestElement(element: CanvasElement, point: CanvasPoint) {
  const localPoint = rotatePoint(point, elementCenterPoint(element), -element.rotation);

  if (element.type === "circle") {
    const rx = element.width / 2;
    const ry = element.height / 2;
    const cx = element.x + rx;
    const cy = element.y + ry;

    if (rx <= 0 || ry <= 0) {
      return false;
    }

    const normalized = ((localPoint.x - cx) ** 2) / (rx ** 2) + ((localPoint.y - cy) ** 2) / (ry ** 2);
    return normalized <= 1;
  }

  if (isLineElement(element)) {
    const points = curvePointsFromElement(element);
    return distanceToCurve(localPoint, points.start, points.controlStart, points.controlEnd, points.end) <= Math.max(8, numericStyle(element.style.strokeWidth, 2) + 6);
  }

  if (element.type === "freehand") {
    const points = Array.isArray(element.data.points) ? element.data.points : [];
    if (points.length < 2) {
      return pointInBounds(localPoint, elementBounds(element, 8));
    }

    return points.some((pointItem, index) => {
      const next = points[index + 1];
      return Boolean(next && distanceToSegment(localPoint, pointItem, next) <= Math.max(8, numericStyle(element.style.strokeWidth, 3) + 6));
    });
  }

  return pointInBounds(localPoint, elementBounds(element));
}

export function hitTestEndpointHandle(element: CanvasElement, point: CanvasPoint): ConnectorEndpoint | null {
  if (!isLineElement(element)) {
    return null;
  }

  const localPoint = rotatePoint(point, elementCenterPoint(element), -element.rotation);
  const points = curvePointsFromElement(element);
  const handles: ConnectorEndpoint[] = lineModeFromElement(element) === "curve" ? ["start", "end", "controlStart", "controlEnd"] : ["start", "end"];
  return handles.find((handle) => distance(localPoint, points[handle]) <= 10) ?? null;
}

export function elementBounds(element: CanvasElement, padding = 0): CanvasBounds {
  if (isLineElement(element)) {
    const points = curvePointsFromElement(element);
    return boundsFromPoints(Object.values(points), Math.max(padding, numericStyle(element.style.strokeWidth, 2)));
  }

  if (element.type === "freehand") {
    const points = Array.isArray(element.data.points) ? element.data.points : [];
    if (points.length > 0) {
      return boundsFromPoints(points, Math.max(padding, numericStyle(element.style.strokeWidth, 3)));
    }
  }

  return {
    minX: element.x - padding,
    minY: element.y - padding,
    maxX: element.x + element.width + padding,
    maxY: element.y + element.height + padding
  };
}

export function distanceToSegment(point: CanvasPoint, start: CanvasPoint, end: CanvasPoint) {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const lengthSquared = dx * dx + dy * dy;

  if (lengthSquared === 0) {
    return distance(point, start);
  }

  const t = Math.max(0, Math.min(1, ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared));
  return distance(point, { x: start.x + t * dx, y: start.y + t * dy });
}

function distanceToCurve(point: CanvasPoint, start: CanvasPoint, controlStart: CanvasPoint, controlEnd: CanvasPoint, end: CanvasPoint) {
  const steps = 36;
  let previous = start;
  let closest = Number.POSITIVE_INFINITY;

  for (let index = 1; index <= steps; index += 1) {
    const next = bezierPoint(start, controlStart, controlEnd, end, index / steps);
    closest = Math.min(closest, distanceToSegment(point, previous, next));
    previous = next;
  }

  return closest;
}

function bezierPoint(start: CanvasPoint, controlStart: CanvasPoint, controlEnd: CanvasPoint, end: CanvasPoint, t: number): CanvasPoint {
  const remaining = 1 - t;
  const remainingSquared = remaining * remaining;
  const tSquared = t * t;

  return {
    x: remainingSquared * remaining * start.x + 3 * remainingSquared * t * controlStart.x + 3 * remaining * tSquared * controlEnd.x + tSquared * t * end.x,
    y: remainingSquared * remaining * start.y + 3 * remainingSquared * t * controlStart.y + 3 * remaining * tSquared * controlEnd.y + tSquared * t * end.y
  };
}

export function pointInBounds(point: CanvasPoint, bounds: CanvasBounds) {
  return point.x >= bounds.minX && point.x <= bounds.maxX && point.y >= bounds.minY && point.y <= bounds.maxY;
}

function boundsFromPoints(points: CanvasPoint[], padding: number): CanvasBounds {
  const bounds = points.reduce(
    (current, point) => ({
      minX: Math.min(current.minX, point.x),
      minY: Math.min(current.minY, point.y),
      maxX: Math.max(current.maxX, point.x),
      maxY: Math.max(current.maxY, point.y)
    }),
    { minX: Number.POSITIVE_INFINITY, minY: Number.POSITIVE_INFINITY, maxX: Number.NEGATIVE_INFINITY, maxY: Number.NEGATIVE_INFINITY }
  );

  return {
    minX: bounds.minX - padding,
    minY: bounds.minY - padding,
    maxX: bounds.maxX + padding,
    maxY: bounds.maxY + padding
  };
}

function elementCenterPoint(element: CanvasElement): CanvasPoint {
  return {
    x: element.x + element.width / 2,
    y: element.y + element.height / 2
  };
}

function rotatePoint(point: CanvasPoint, center: CanvasPoint, degrees: number): CanvasPoint {
  if (degrees === 0) {
    return point;
  }

  const radians = degrees * (Math.PI / 180);
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  const dx = point.x - center.x;
  const dy = point.y - center.y;

  return {
    x: center.x + dx * cos - dy * sin,
    y: center.y + dx * sin + dy * cos
  };
}

function distance(a: CanvasPoint, b: CanvasPoint) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function numericStyle(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function isLineElement(element: CanvasElement) {
  return element.type === "connector" || element.type === "arrow" || element.type === "curve";
}
