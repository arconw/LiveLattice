import { describe, expect, it } from "vitest";
import { normalizeCanvasContent, normalizeCanvasResponse, toPersistedCanvasContent } from "../contracts/canvas";
import { canvasFixture } from "../contracts/fixtures";
import { applyCanvasOperations, createCanvasElement, deleteSelection, duplicateSelection, moveSelection, setSelectionLocked, updateSelectionStyle } from "../features/canvas/canvasModel";

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
});
