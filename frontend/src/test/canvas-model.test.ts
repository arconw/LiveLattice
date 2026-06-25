import { describe, expect, it, vi } from "vitest";
import { normalizeCanvasContent, normalizeCanvasResponse, toPersistedCanvasContent } from "../contracts/canvas";
import { canvasFixture } from "../contracts/fixtures";
import { drawCanvasElement } from "../features/canvas/canvasRenderer";
import { applyCanvasOperations, createCanvasElement, deleteSelection, duplicateSelection, fitViewportToContent, moveSelection, normalizeFreehandPoints, setSelectionLocked, updateConnectorEndpoint, updateSelectionGeometry, updateSelectionStyle } from "../features/canvas/canvasModel";

describe("canvas content contracts and reducer", () => {
  it("normalizes Core canvas content and excludes UI-only element state from persistence", () => {
    const normalized = normalizeCanvasContent({
      elements: [
        {
          ...canvasFixture.content.elements[0],
          selected: true,
          hoverHandle: "resize-east"
        }
      ],
      viewport: { zoom: 9, panX: 12, panY: -4 },
      metadata: { width: 900, height: 700, backgroundColor: "#ffffff", gridEnabled: false }
    });

    expect(normalized.viewport.zoom).toBe(4);
    expect(normalized.elements[0]).not.toHaveProperty("selected");
    expect(normalized.elements[0]).not.toHaveProperty("hoverHandle");
    expect(toPersistedCanvasContent(normalized).elements[0]).toEqual({
      id: "el-gateway",
      type: "rectangle",
      x: 160,
      y: 140,
      width: 210,
      height: 104,
      rotation: 0,
      style: { fill: "#ffffff", stroke: "#4d7cfe", strokeWidth: 2, opacity: 1 },
      data: { text: "REST Gateway" },
      zIndex: 1,
      locked: false,
      groupId: null
    });
  });

  it("applies add, move, style, duplicate, and delete operations without mutating locked elements", () => {
    const canvas = normalizeCanvasResponse(canvasFixture);
    const created = createCanvasElement("rectangle", canvas.content.elements.length, { id: "el-new", x: 20, y: 30 });
    const withElement = applyCanvasOperations(canvas.content, [{ type: "add", id: created.id, element: created }]);

    expect(withElement.elements.find((element) => element.id === "el-new")?.x).toBe(20);

    const moved = moveSelection(withElement, ["el-new", "el-db"], 12, 8).content;
    expect(moved.elements.find((element) => element.id === "el-new")?.x).toBe(32);
    expect(moved.elements.find((element) => element.id === "el-db")?.x).toBe(260);

    const styled = updateSelectionStyle(moved, ["el-new"], { stroke: "#ff5f8f" }).content;
    expect(styled.elements.find((element) => element.id === "el-new")?.style.stroke).toBe("#ff5f8f");

    const duplicated = duplicateSelection(styled, ["el-new"]);
    expect(duplicated.selection).toHaveLength(1);
    expect(duplicated.content.elements).toHaveLength(styled.elements.length + 1);

    const deleted = deleteSelection(duplicated.content, duplicated.selection);
    expect(deleted.selection).toEqual([]);
    expect(deleted.content.elements).toHaveLength(styled.elements.length);
  });

  it("allows locked elements to be unlocked without accepting other locked updates", () => {
    const canvas = normalizeCanvasResponse(canvasFixture);
    const blockedStyle = updateSelectionStyle(canvas.content, ["el-db"], { stroke: "#ff5f8f" }).content;
    expect(blockedStyle.elements.find((element) => element.id === "el-db")?.style.stroke).toBe("#35d6a4");

    const unlocked = setSelectionLocked(blockedStyle, ["el-db"], false).content;
    expect(unlocked.elements.find((element) => element.id === "el-db")?.locked).toBe(false);

    const moved = moveSelection(unlocked, ["el-db"], 24, 0).content;
    expect(moved.elements.find((element) => element.id === "el-db")?.x).toBe(284);
  });

  it("fits content into the provided viewport and centers element bounds", () => {
    const canvas = normalizeCanvasResponse(canvasFixture);
    const viewport = fitViewportToContent(canvas.content, { width: 1200, height: 800 });
    const center = {
      x: (160 + 980) / 2,
      y: (130 + 514) / 2
    };

    expect(viewport.zoom).toBeCloseTo(1.17, 2);
    expect(center.x * viewport.zoom + viewport.panX).toBeCloseTo(600, 0);
    expect(center.y * viewport.zoom + viewport.panY).toBeCloseTo(400, 0);
  });

  it("normalizes and persists image element src data", () => {
    const src = "data:image/png;base64,cGl4ZWw=";
    const normalized = normalizeCanvasContent({
      ...canvasFixture.content,
      elements: [
        {
          ...canvasFixture.content.elements.find((element) => element.id === "el-image"),
          data: { text: "Runbook image", src }
        }
      ]
    });

    expect(normalized.elements[0]?.data.src).toBe(src);
    expect(toPersistedCanvasContent(normalized).elements[0]?.data.src).toBe(src);
  });

  it("draws image placeholders until a source image is loaded", () => {
    const placeholderElement = createCanvasElement("image", 0, { data: { text: "Image placeholder" } });
    const loadedElement = createCanvasElement("image", 0, {
      width: 200,
      height: 100,
      data: { text: "Loaded image", src: "https://example.com/image.png" }
    });
    const ctx = createMockCanvasContext();
    const image = Object.defineProperties({}, {
      complete: { value: true },
      naturalWidth: { value: 400 },
      naturalHeight: { value: 200 }
    }) as HTMLImageElement;

    drawCanvasElement(ctx as unknown as CanvasRenderingContext2D, placeholderElement);
    expect(ctx.fillText).toHaveBeenCalledWith("Image placeholder", expect.any(Number), expect.any(Number), expect.any(Number));

    ctx.fillText.mockClear();
    drawCanvasElement(ctx as unknown as CanvasRenderingContext2D, loadedElement, new Map([[String(loadedElement.data.src), image]]));

    expect(ctx.drawImage).toHaveBeenCalledWith(image, loadedElement.x, loadedElement.y, 200, 100);
    expect(ctx.fillText).not.toHaveBeenCalled();
  });

  it("wraps canvas text elements with consistent line spacing", () => {
    const element = createCanvasElement("text", 0, {
      width: 92,
      data: { text: "Alpha Beta\nGamma Delta" },
      style: { fontSize: 20, fontFamily: "Inter", fontWeight: 700, stroke: "#273142" }
    });
    const ctx = createMockCanvasContext();

    drawCanvasElement(ctx as unknown as CanvasRenderingContext2D, element);

    expect(ctx.fillText).toHaveBeenNthCalledWith(1, "Alpha", element.x, element.y + 8, 92);
    expect(ctx.fillText).toHaveBeenNthCalledWith(2, "Beta", element.x, element.y + 37, 92);
    expect(ctx.fillText).toHaveBeenNthCalledWith(3, "Gamma", element.x, element.y + 66, 92);
    expect(ctx.fillText).toHaveBeenNthCalledWith(4, "Delta", element.x, element.y + 95, 92);
  });

  it("moves line-like element geometry alongside x and y", () => {
    const canvas = normalizeCanvasResponse(canvasFixture);
    const moved = moveSelection(canvas.content, ["el-connector", "el-arrow", "el-curve", "el-freehand"], 12, 8).content;
    const connector = moved.elements.find((element) => element.id === "el-connector");
    const arrow = moved.elements.find((element) => element.id === "el-arrow");
    const curve = moved.elements.find((element) => element.id === "el-curve");
    const freehand = moved.elements.find((element) => element.id === "el-freehand");

    expect(connector?.x).toBe(382);
    expect(connector?.data.start).toEqual({ x: 382, y: 198 });
    expect(connector?.data.end).toEqual({ x: 492, y: 232 });
    expect(arrow?.x).toBe(622);
    expect(arrow?.data.start).toEqual({ x: 622, y: 228 });
    expect(arrow?.data.end).toEqual({ x: 752, y: 324 });
    expect(curve?.x).toBe(222);
    expect(curve?.data.start).toEqual({ x: 222, y: 378 });
    expect(curve?.data.controlStart).toEqual({ x: 296, y: 308 });
    expect(curve?.data.controlEnd).toEqual({ x: 404, y: 426 });
    expect(curve?.data.end).toEqual({ x: 482, y: 350 });
    expect(curve?.data.path).toBe("M 222 378 C 296 308 404 426 482 350");
    expect(freehand?.x).toBe(552);
    expect(freehand?.data.points?.[0]).toEqual({ x: 552, y: 478 });
    expect(freehand?.data.path).toContain("M 552 478");
  });

  it("normalizes freehand point bounds and path data", () => {
    const normalized = normalizeFreehandPoints([{ x: 220, y: 260 }, { x: 180, y: 200 }, { x: 240, y: 230 }]);

    expect(normalized).toEqual({
      x: 180,
      y: 200,
      width: 60,
      height: 60,
      data: {
        points: [{ x: 220, y: 260 }, { x: 180, y: 200 }, { x: 240, y: 230 }],
        path: "M 220 260 L 180 200 L 240 230"
      }
    });
  });

  it("moves arrow endpoints when generic geometry changes move the arrow origin", () => {
    const canvas = normalizeCanvasResponse(canvasFixture);
    const updated = updateSelectionGeometry(canvas.content, ["el-arrow"], { x: 650, y: 260 }).content;
    const arrow = updated.elements.find((element) => element.id === "el-arrow");

    expect(arrow?.x).toBe(650);
    expect(arrow?.y).toBe(260);
    expect(arrow?.data.start).toEqual({ x: 650, y: 260 });
    expect(arrow?.data.end).toEqual({ x: 780, y: 356 });
  });

  it("updates one connector endpoint and snaps it to nearby block boundaries", () => {
    const canvas = normalizeCanvasResponse(canvasFixture);
    const connector = canvas.content.elements.find((element) => element.id === "el-connector");

    expect(connector).toBeDefined();

    const updated = applyCanvasOperations(canvas.content, [{
      type: "update",
      id: "el-connector",
      changes: updateConnectorEndpoint(connector!, "start", { x: 369, y: 220 }, { snapElements: canvas.content.elements })
    }]);
    const nextConnector = updated.elements.find((element) => element.id === "el-connector");

    expect(nextConnector?.data.start).toEqual({ x: 370, y: 220 });
    expect(nextConnector?.data.end).toEqual({ x: 480, y: 224 });
    expect(nextConnector?.data.startElementId).toBe("el-gateway");
    expect(nextConnector?.x).toBe(370);
    expect(nextConnector?.y).toBe(220);
  });

  it("updates connector control points and keeps path data in sync", () => {
    const connector = createCanvasElement("connector", 0, { id: "el-connector-test" });
    const canvas = normalizeCanvasResponse({
      ...canvasFixture,
      content: {
        ...canvasFixture.content,
        elements: [connector]
      }
    });

    const updated = applyCanvasOperations(canvas.content, [{
      type: "update",
      id: "el-connector-test",
      changes: updateConnectorEndpoint(connector, "controlStart", { x: 120, y: 80 })
    }]);
    const nextConnector = updated.elements.find((element) => element.id === "el-connector-test");

    expect(nextConnector?.x).toBe(120);
    expect(nextConnector?.y).toBe(80);
    expect(nextConnector?.data.lineMode).toBe("curve");
    expect(nextConnector?.data.controlStart).toEqual({ x: 120, y: 80 });
    expect(nextConnector?.data.path).toBe("M 160 140 C 120 80 333.3333333333333 181.33333333333331 420 202");
  });

  it("draws an arrowhead at the updated arrow endpoint", () => {
    const canvas = normalizeCanvasResponse(canvasFixture);
    const moved = moveSelection(canvas.content, ["el-arrow"], 20, 12).content;
    const arrow = moved.elements.find((element) => element.id === "el-arrow");
    const ctx = createMockCanvasContext();

    expect(arrow).toBeDefined();

    drawCanvasElement(ctx as unknown as CanvasRenderingContext2D, arrow!);

    expect(ctx.translate).toHaveBeenCalledWith(760, 328);
    expect(ctx.rotate.mock.calls[0]?.[0]).toBeCloseTo(Math.atan2(96, 130));
    expect(ctx.fill).toHaveBeenCalled();
  });

  it("draws solid straight connectors by default", () => {
    const connector = createCanvasElement("connector", 0, { id: "el-connector-test" });
    const ctx = createMockCanvasContext();

    drawCanvasElement(ctx as unknown as CanvasRenderingContext2D, connector);

    expect(ctx.setLineDash).toHaveBeenCalledWith([]);
    expect(ctx.bezierCurveTo).toHaveBeenCalledWith(246.66666666666666, 160.66666666666666, 333.3333333333333, 181.33333333333331, 420, 202);
    expect(ctx.stroke).toHaveBeenCalled();
  });
});

function createMockCanvasContext() {
  return {
    beginPath: vi.fn(),
    bezierCurveTo: vi.fn(),
    clip: vi.fn(),
    closePath: vi.fn(),
    drawImage: vi.fn(),
    fill: vi.fn(),
    fillText: vi.fn(),
    lineTo: vi.fn(),
    moveTo: vi.fn(),
    quadraticCurveTo: vi.fn(),
    restore: vi.fn(),
    save: vi.fn(),
    setLineDash: vi.fn(),
    stroke: vi.fn(),
    translate: vi.fn(),
    rotate: vi.fn(),
    measureText: vi.fn((text: string) => ({ width: text.length * 10 })),
    globalAlpha: 1,
    lineCap: "butt",
    lineJoin: "miter",
    lineWidth: 1,
    fillStyle: "#000000",
    font: "10px sans-serif",
    strokeStyle: "#000000",
    textBaseline: "alphabetic"
  };
}
