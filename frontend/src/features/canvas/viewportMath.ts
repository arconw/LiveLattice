import type { CanvasPoint, CanvasViewportState } from "../../contracts/canvas";

export type CanvasSize = {
  width: number;
  height: number;
};

type CanvasRect = Pick<DOMRect, "left" | "top"> & Partial<Pick<DOMRect, "width" | "height">>;

export function clientToViewport(client: CanvasPoint, rect: CanvasRect, size?: CanvasSize): CanvasPoint {
  const scaleX = scaleDimension(rect.width, size?.width);
  const scaleY = scaleDimension(rect.height, size?.height);

  return {
    x: (client.x - rect.left) * scaleX,
    y: (client.y - rect.top) * scaleY
  };
}

export function viewportToClient(point: CanvasPoint, rect: CanvasRect, size?: CanvasSize): CanvasPoint {
  const scaleX = scaleDimension(rect.width, size?.width);
  const scaleY = scaleDimension(rect.height, size?.height);

  return {
    x: rect.left + point.x / scaleX,
    y: rect.top + point.y / scaleY
  };
}

export function clientToWorld(client: CanvasPoint, rect: CanvasRect, viewport: CanvasViewportState, size?: CanvasSize): CanvasPoint {
  return viewportToWorld(clientToViewport(client, rect, size), viewport);
}

export function worldToClient(point: CanvasPoint, rect: CanvasRect, viewport: CanvasViewportState, size?: CanvasSize): CanvasPoint {
  return viewportToClient(worldToViewport(point, viewport), rect, size);
}

export function worldToViewport(point: CanvasPoint, viewport: CanvasViewportState): CanvasPoint {
  return {
    x: point.x * viewport.zoom + viewport.panX,
    y: point.y * viewport.zoom + viewport.panY
  };
}

export function viewportToWorld(point: CanvasPoint, viewport: CanvasViewportState): CanvasPoint {
  return {
    x: (point.x - viewport.panX) / viewport.zoom,
    y: (point.y - viewport.panY) / viewport.zoom
  };
}

export function panViewport(viewport: CanvasViewportState, dx: number, dy: number): CanvasViewportState {
  return {
    zoom: viewport.zoom,
    panX: viewport.panX + dx,
    panY: viewport.panY + dy
  };
}

export function zoomViewportAroundPoint(viewport: CanvasViewportState, anchor: CanvasPoint, nextZoom: number): CanvasViewportState {
  const zoom = clampZoom(nextZoom);
  const worldX = (anchor.x - viewport.panX) / viewport.zoom;
  const worldY = (anchor.y - viewport.panY) / viewport.zoom;

  return {
    zoom,
    panX: anchor.x - worldX * zoom,
    panY: anchor.y - worldY * zoom
  };
}

export function zoomViewportAroundCenter(viewport: CanvasViewportState, size: CanvasSize, nextZoom: number): CanvasViewportState {
  return zoomViewportAroundPoint(viewport, { x: size.width / 2, y: size.height / 2 }, nextZoom);
}

export function clampZoom(zoom: number) {
  return Math.min(4, Math.max(0.1, zoom));
}

function scaleDimension(rectSize: number | undefined, viewportSize: number | undefined) {
  if (!rectSize || !viewportSize) {
    return 1;
  }

  return viewportSize / rectSize;
}
