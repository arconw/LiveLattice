import { describe, expect, it } from "vitest";
import { normalizeCanvasResponse } from "../contracts/canvas";
import { canvasFixture } from "../contracts/fixtures";
import { hitTestElement, hitTestElements } from "../features/canvas/canvasHitTest";
import { clientToViewport, clientToWorld, viewportToClient, viewportToWorld, worldToClient, worldToViewport, zoomViewportAroundPoint } from "../features/canvas/viewportMath";

describe("canvas viewport math", () => {
  it("converts client and world coordinates through rendered canvas scaling", () => {
    const viewport = { zoom: 2, panX: 40, panY: -30 };
    const rect = { left: 40, top: 20, width: 400, height: 200 };
    const size = { width: 800, height: 600 };
    const client = { x: 140, y: 70 };
    const viewportPoint = clientToViewport(client, rect, size);
    const world = clientToWorld(client, rect, viewport, size);

    expect(viewportPoint).toEqual({ x: 200, y: 150 });
    expect(viewportToClient(viewportPoint, rect, size)).toEqual(client);
    expect(world).toEqual({ x: 80, y: 90 });
    expect(worldToClient(world, rect, viewport, size)).toEqual(client);
    expect(viewportToWorld(worldToViewport({ x: 32, y: 48 }, viewport), viewport)).toEqual({ x: 32, y: 48 });
  });

  it("zooms around the provided viewport anchor", () => {
    const viewport = { zoom: 1, panX: 100, panY: 50 };
    const anchor = { x: 300, y: 250 };
    const before = viewportToWorld(anchor, viewport);
    const next = zoomViewportAroundPoint(viewport, anchor, 2);
    const clampedMin = zoomViewportAroundPoint(viewport, anchor, 0.01);
    const clampedMax = zoomViewportAroundPoint(viewport, anchor, 8);

    expect(next.zoom).toBe(2);
    expect(viewportToWorld(anchor, next)).toEqual(before);
    expect(clampedMin.zoom).toBe(0.1);
    expect(viewportToWorld(anchor, clampedMin)).toEqual(before);
    expect(clampedMax.zoom).toBe(4);
    expect(viewportToWorld(anchor, clampedMax)).toEqual(before);
  });
});

describe("canvas retained hit testing", () => {
  it("hits filled shapes and text/image bounds", () => {
    const content = normalizeCanvasResponse(canvasFixture).content;
    const rectangle = content.elements.find((element) => element.id === "el-gateway");
    const text = content.elements.find((element) => element.type === "text");
    const image = content.elements.find((element) => element.type === "image");

    expect(rectangle && hitTestElement(rectangle, { x: 200, y: 170 })).toBe(true);
    expect(text && hitTestElement(text, { x: text.x + 10, y: text.y + 10 })).toBe(true);
    expect(image && hitTestElement(image, { x: image.x + 20, y: image.y + 20 })).toBe(true);
  });

  it("hits wide line targets, endpoint handles, and freehand paths", () => {
    const content = normalizeCanvasResponse(canvasFixture).content;
    const connector = content.elements.find((element) => element.type === "connector");
    const arrow = content.elements.find((element) => element.type === "arrow");
    const formerCurve = content.elements.find((element) => element.id === "el-curve");
    const freehand = content.elements.find((element) => element.type === "freehand");

    expect(connector).toBeDefined();
    expect(hitTestElements(content.elements, connector?.data.start ?? { x: 0, y: 0 })).toMatchObject({ type: "handle", element: { id: connector?.id }, handle: "start" });
    expect(connector && hitTestElement(connector, { x: 425, y: 215 })).toBe(true);
    expect(arrow && hitTestElement(arrow, { x: arrow.x + arrow.width / 2, y: arrow.y + arrow.height / 2 })).toBe(true);
    expect(formerCurve?.type).toBe("connector");
    expect(formerCurve && hitTestElements(content.elements, formerCurve.data.controlStart ?? { x: 0, y: 0 })).toMatchObject({ type: "handle", element: { id: formerCurve?.id }, handle: "controlStart" });
    expect(formerCurve && hitTestElement(formerCurve, { x: 337, y: 360 })).toBe(true);
    expect(freehand && hitTestElement(freehand, freehand.data.points?.[1] ?? { x: 0, y: 0 })).toBe(true);
  });
});
