import type { CanvasContent, CanvasElement, CanvasPoint } from "../../contracts/canvas";
import { elementBounds } from "./canvasHitTest";
import { curvePointsFromElement, lineModeFromElement } from "./canvasModel";

export type CanvasRenderOptions = {
  width: number;
  height: number;
  selection: string[];
  imageCache?: Map<string, HTMLImageElement>;
  timestamp?: number;
};

type CurvePreset = "dashed" | "pulse" | "pulseReverse" | "signal" | "signalReverse" | "solid" | "dotted";

export const textElementVerticalInset = 8;
export const textElementLineHeightRatio = 1.45;

export function textElementFontSize(element: CanvasElement) {
  return numericStyle(element.style.fontSize, 18);
}

export function textElementLineHeight(element: CanvasElement) {
  return Math.max(16, textElementFontSize(element) * textElementLineHeightRatio);
}

export function textElementFont(element: CanvasElement) {
  return `${stringStyle(element.style.fontWeight, "700")} ${textElementFontSize(element)}px ${stringStyle(element.style.fontFamily, "Inter")}`;
}

export function textElementLines(ctx: CanvasRenderingContext2D, element: CanvasElement, text = String(element.data.text ?? "Text")) {
  return wrapTextLines(ctx, text, Math.max(1, element.width));
}

export function renderCanvasScene(ctx: CanvasRenderingContext2D, content: CanvasContent, options: CanvasRenderOptions) {
  ctx.clearRect(0, 0, options.width, options.height);
  ctx.fillStyle = stringStyle(content.metadata.backgroundColor, "#eef2f5");
  ctx.fillRect(0, 0, options.width, options.height);

  ctx.save();
  ctx.translate(content.viewport.panX, content.viewport.panY);
  ctx.scale(content.viewport.zoom, content.viewport.zoom);
  drawGrid(ctx, content, options);
  content.elements.forEach((element) => drawCanvasElement(ctx, element, options.imageCache, options.timestamp ?? 0));
  content.elements.filter((element) => options.selection.includes(element.id)).forEach((element) => drawSelection(ctx, element));
  ctx.restore();
}

export function drawCanvasElement(ctx: CanvasRenderingContext2D, element: CanvasElement, imageCache?: Map<string, HTMLImageElement>, timestamp = 0) {
  ctx.save();
  rotateElementContext(ctx, element);
  ctx.globalAlpha = numericStyle(element.style.opacity, 1);
  ctx.lineWidth = baseStrokeWidth(element);
  ctx.strokeStyle = stringStyle(element.style.stroke, "#273142");
  ctx.fillStyle = stringStyle(element.style.fill, "transparent");
  ctx.lineCap = "round";
  ctx.lineJoin = "round";

  if (element.type === "circle") {
    drawCircle(ctx, element);
  } else if (element.type === "text") {
    drawText(ctx, element);
  } else if (element.type === "image") {
    drawImageElement(ctx, element, imageCache);
  } else if (isLineElement(element)) {
    drawLineElement(ctx, element, timestamp);
  } else if (element.type === "freehand") {
    drawFreehand(ctx, element);
  } else {
    drawRectangle(ctx, element);
  }

  ctx.restore();
}

function drawGrid(ctx: CanvasRenderingContext2D, content: CanvasContent, options: CanvasRenderOptions) {
  if (!content.metadata.gridEnabled) {
    return;
  }

  const step = 32;
  const zoom = Math.max(0.1, content.viewport.zoom);
  const minX = -content.viewport.panX / zoom;
  const minY = -content.viewport.panY / zoom;
  const maxX = (options.width - content.viewport.panX) / zoom;
  const maxY = (options.height - content.viewport.panY) / zoom;
  const startX = Math.floor(minX / step) * step;
  const startY = Math.floor(minY / step) * step;

  ctx.beginPath();
  ctx.strokeStyle = "rgba(77, 124, 254, 0.22)";
  ctx.lineWidth = 1 / zoom;

  for (let x = startX; x <= maxX; x += step) {
    ctx.moveTo(x, minY);
    ctx.lineTo(x, maxY);
  }

  for (let y = startY; y <= maxY; y += step) {
    ctx.moveTo(minX, y);
    ctx.lineTo(maxX, y);
  }

  ctx.stroke();
}

function drawRectangle(ctx: CanvasRenderingContext2D, element: CanvasElement) {
  roundedRect(ctx, element.x, element.y, element.width, element.height, 8);
  fillAndStroke(ctx);
  drawCenteredLabel(ctx, element, String(element.data.text ?? "Rectangle"));
}

function drawCircle(ctx: CanvasRenderingContext2D, element: CanvasElement) {
  ctx.beginPath();
  ctx.ellipse(element.x + element.width / 2, element.y + element.height / 2, element.width / 2, element.height / 2, 0, 0, Math.PI * 2);
  fillAndStroke(ctx);
  drawCenteredLabel(ctx, element, String(element.data.text ?? "Circle"));
}

function drawText(ctx: CanvasRenderingContext2D, element: CanvasElement) {
  const lineHeight = textElementLineHeight(element);
  ctx.globalAlpha = numericStyle(element.style.opacity, 1);
  ctx.fillStyle = stringStyle(element.style.stroke, "#273142") === "transparent" ? "#273142" : stringStyle(element.style.stroke, "#273142");
  ctx.font = textElementFont(element);
  ctx.textBaseline = "top";
  textElementLines(ctx, element).forEach((line, index) => {
    ctx.fillText(line, element.x, element.y + textElementVerticalInset + index * lineHeight, Math.max(1, element.width));
  });
}

function wrapTextLines(ctx: CanvasRenderingContext2D, text: string, maxWidth: number) {
  const hardLines = text.split(/\r?\n/);
  return hardLines.flatMap((line) => wrapHardLine(ctx, line, maxWidth));
}

function wrapHardLine(ctx: CanvasRenderingContext2D, line: string, maxWidth: number) {
  if (line.length === 0) {
    return [""];
  }

  const parts = line.split(/(\s+)/).filter((part) => part.length > 0);
  const lines: string[] = [];
  let current = "";

  for (const part of parts) {
    const candidate = current + part;
    if (!current || ctx.measureText(candidate).width <= maxWidth) {
      current = candidate;
      continue;
    }

    lines.push(current.trimEnd());
    current = part.trimStart();
  }

  if (current || lines.length === 0) {
    lines.push(current.trimEnd());
  }

  return lines;
}

function drawImageElement(ctx: CanvasRenderingContext2D, element: CanvasElement, imageCache?: Map<string, HTMLImageElement>) {
  const src = typeof element.data.src === "string" ? element.data.src : null;
  const image = src ? imageCache?.get(src) : null;

  roundedRect(ctx, element.x, element.y, element.width, element.height, 8);
  ctx.fillStyle = "#ffffff";
  ctx.fill();
  ctx.stroke();

  if (image?.complete && image.naturalWidth > 0) {
    const bounds = imageMeetBounds(image, element);
    ctx.save();
    roundedRect(ctx, element.x, element.y, element.width, element.height, 8);
    ctx.clip();
    ctx.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height);
    ctx.restore();
    return;
  }

  ctx.beginPath();
  ctx.moveTo(element.x + 20, element.y + element.height - 24);
  ctx.lineTo(element.x + 72, element.y + element.height - 72);
  ctx.lineTo(element.x + 112, element.y + element.height - 32);
  ctx.strokeStyle = "#8a5cff";
  ctx.lineWidth = 4;
  ctx.stroke();
  ctx.fillStyle = "#273142";
  ctx.font = "700 14px Inter";
  ctx.textBaseline = "top";
  ctx.fillText(String(element.data.text ?? "Image placeholder"), element.x + 18, element.y + 18, Math.max(1, element.width - 36));
}

function imageMeetBounds(image: HTMLImageElement, element: CanvasElement) {
  const imageRatio = image.naturalWidth / image.naturalHeight;
  const elementRatio = element.width / element.height;

  if (imageRatio > elementRatio) {
    const height = element.width / imageRatio;
    return {
      x: element.x,
      y: element.y + (element.height - height) / 2,
      width: element.width,
      height
    };
  }

  const width = element.height * imageRatio;
  return {
    x: element.x + (element.width - width) / 2,
    y: element.y,
    width,
    height: element.height
  };
}

function drawLineElement(ctx: CanvasRenderingContext2D, element: CanvasElement, timestamp: number) {
  const points = curvePointsFromElement(element);
  const preset = curvePreset(element);

  if (preset === "pulse" || preset === "pulseReverse") {
    drawCurvePulse(ctx, points, element, timestamp, preset === "pulseReverse");
    drawLineArrowHead(ctx, element, points);
    return;
  }

  if (preset === "signal" || preset === "signalReverse") {
    ctx.save();
    ctx.globalAlpha *= 0.24;
    traceCurve(ctx, points);
    ctx.stroke();
    ctx.restore();
    drawCurvePulse(ctx, points, element, timestamp, preset === "signalReverse");
    drawLineArrowHead(ctx, element, points);
    return;
  }

  if (preset === "dotted") {
    ctx.setLineDash([2, 8]);
  } else if (preset === "dashed") {
    ctx.setLineDash([7, 8]);
  } else {
    ctx.setLineDash([]);
  }

  traceCurve(ctx, points);
  ctx.stroke();
  ctx.setLineDash([]);
  ctx.lineDashOffset = 0;
  drawLineArrowHead(ctx, element, points);
}

function traceCurve(ctx: CanvasRenderingContext2D, points: ReturnType<typeof curvePointsFromElement>) {
  ctx.beginPath();
  ctx.moveTo(points.start.x, points.start.y);
  ctx.bezierCurveTo(points.controlStart.x, points.controlStart.y, points.controlEnd.x, points.controlEnd.y, points.end.x, points.end.y);
}

function drawCurvePulse(ctx: CanvasRenderingContext2D, points: ReturnType<typeof curvePointsFromElement>, element: CanvasElement, timestamp: number, reverse = false) {
  const length = Math.max(48, curveLength(points));
  const segment = Math.min(34, Math.max(18, length * 0.12));
  const color = selectionColor(element);
  const offset = ((timestamp / 1800) % 1) * length;

  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = Math.max(2, baseStrokeWidth(element));
  ctx.shadowColor = shadowColor(color);
  ctx.shadowBlur = 10;
  ctx.setLineDash([segment, length]);
  ctx.lineDashOffset = reverse ? offset : -offset;
  traceCurve(ctx, points);
  ctx.stroke();
  ctx.restore();
  ctx.setLineDash([]);
  ctx.lineDashOffset = 0;
}

function curveLength(points: ReturnType<typeof curvePointsFromElement>) {
  const steps = 44;
  let length = 0;
  let previous = points.start;

  for (let index = 1; index <= steps; index += 1) {
    const next = bezierPoint(points, index / steps);
    length += Math.hypot(next.x - previous.x, next.y - previous.y);
    previous = next;
  }

  return length;
}

function bezierPoint(points: ReturnType<typeof curvePointsFromElement>, t: number) {
  const remaining = 1 - t;
  const remainingSquared = remaining * remaining;
  const tSquared = t * t;

  return {
    x: remainingSquared * remaining * points.start.x + 3 * remainingSquared * t * points.controlStart.x + 3 * remaining * tSquared * points.controlEnd.x + tSquared * t * points.end.x,
    y: remainingSquared * remaining * points.start.y + 3 * remainingSquared * t * points.controlStart.y + 3 * remaining * tSquared * points.controlEnd.y + tSquared * t * points.end.y
  };
}

function drawFreehand(ctx: CanvasRenderingContext2D, element: CanvasElement) {
  const points = Array.isArray(element.data.points) ? element.data.points : [];

  if (points.length === 0) {
    return;
  }

  ctx.beginPath();
  ctx.moveTo(points[0].x, points[0].y);
  points.slice(1).forEach((point) => ctx.lineTo(point.x, point.y));
  ctx.stroke();
}

function drawSelection(ctx: CanvasRenderingContext2D, element: CanvasElement) {
  ctx.save();
  rotateElementContext(ctx, element);
  if (element.type === "rectangle" || element.type === "circle" || element.type === "image") {
    drawShapeSelection(ctx, element);
    ctx.restore();
    return;
  }

  if (isLineElement(element)) {
    drawCurveSelection(ctx, element);
    ctx.restore();
    return;
  }

  const bounds = elementBounds(element, 6);
  ctx.strokeStyle = "#ff5f8f";
  ctx.lineWidth = 2;
  ctx.setLineDash([6, 5]);
  roundedRect(ctx, bounds.minX, bounds.minY, bounds.maxX - bounds.minX, bounds.maxY - bounds.minY, 8);
  ctx.stroke();
  ctx.setLineDash([]);

  ctx.restore();
}

function drawShapeSelection(ctx: CanvasRenderingContext2D, element: CanvasElement) {
  const color = selectionColor(element);

  ctx.strokeStyle = haloColor(color);
  ctx.lineWidth = 7;
  ctx.shadowColor = shadowColor(color);
  ctx.shadowBlur = 22;
  ctx.shadowOffsetY = 0;
  ctx.shadowOffsetX = 0;
  traceShapeSelection(ctx, element, 3);
  ctx.stroke();

  ctx.shadowColor = "transparent";
  ctx.shadowBlur = 0;
  ctx.shadowOffsetY = 0;
  ctx.shadowOffsetX = 0;
  ctx.strokeStyle = color;
  ctx.lineWidth = 2.2;
  traceShapeSelection(ctx, element, 0.6);
  ctx.stroke();
}

function traceShapeSelection(ctx: CanvasRenderingContext2D, element: CanvasElement, outset: number) {
  if (element.type === "circle") {
    ctx.beginPath();
    ctx.ellipse(element.x + element.width / 2, element.y + element.height / 2, element.width / 2 + outset, element.height / 2 + outset, 0, 0, Math.PI * 2);
  } else {
    roundedRect(ctx, element.x - outset, element.y - outset, element.width + outset * 2, element.height + outset * 2, 8);
  }
}

function drawCurveSelection(ctx: CanvasRenderingContext2D, element: CanvasElement) {
  const points = curvePointsFromElement(element);
  const color = selectionColor(element);
  const curveMode = lineModeFromElement(element) === "curve";

  if (curveMode) {
    ctx.strokeStyle = shadowColor(color);
    ctx.lineWidth = 1.4;
    ctx.setLineDash([4, 6]);
    ctx.beginPath();
    ctx.moveTo(points.start.x, points.start.y);
    ctx.lineTo(points.controlStart.x, points.controlStart.y);
    ctx.moveTo(points.end.x, points.end.y);
    ctx.lineTo(points.controlEnd.x, points.controlEnd.y);
    ctx.stroke();
    ctx.setLineDash([]);
  }

  ctx.strokeStyle = color;
  ctx.lineWidth = 2.2;
  ctx.shadowColor = shadowColor(color);
  ctx.shadowBlur = 14;
  ctx.beginPath();
  ctx.moveTo(points.start.x, points.start.y);
  ctx.bezierCurveTo(points.controlStart.x, points.controlStart.y, points.controlEnd.x, points.controlEnd.y, points.end.x, points.end.y);
  ctx.stroke();
  ctx.shadowColor = "transparent";
  ctx.shadowBlur = 0;

  drawHandle(ctx, points.start, color);
  drawHandle(ctx, points.end, color);
  if (curveMode) {
    drawHandle(ctx, points.controlStart, "#8b95a7");
    drawHandle(ctx, points.controlEnd, "#8b95a7");
  }
}

function drawLineArrowHead(ctx: CanvasRenderingContext2D, element: CanvasElement, points: ReturnType<typeof curvePointsFromElement>) {
  if (element.type !== "arrow") {
    return;
  }

  drawArrowHead(ctx, bezierPoint(points, 0.98), points.end, stringStyle(element.style.stroke, "#273142"));
}

function drawCenteredLabel(ctx: CanvasRenderingContext2D, element: CanvasElement, label: string) {
  ctx.fillStyle = "#273142";
  ctx.font = "700 15px Inter";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(label, element.x + element.width / 2, element.y + element.height / 2, Math.max(1, element.width - 16));
  ctx.textAlign = "start";
}

function drawArrowHead(ctx: CanvasRenderingContext2D, start: CanvasPoint, end: CanvasPoint, color: string) {
  const angle = Math.atan2(end.y - start.y, end.x - start.x);
  const size = 12;

  ctx.save();
  ctx.translate(end.x, end.y);
  ctx.rotate(angle);
  ctx.beginPath();
  ctx.moveTo(0, 0);
  ctx.lineTo(-size, -size / 2);
  ctx.lineTo(-size, size / 2);
  ctx.closePath();
  ctx.fillStyle = color;
  ctx.fill();
  ctx.restore();
}

function drawHandle(ctx: CanvasRenderingContext2D, point: CanvasPoint, color = "#ff5f8f") {
  ctx.beginPath();
  ctx.arc(point.x, point.y, 5, 0, Math.PI * 2);
  ctx.fillStyle = "#ffffff";
  ctx.fill();
  ctx.strokeStyle = color;
  ctx.lineWidth = 2;
  ctx.stroke();
}

function fillAndStroke(ctx: CanvasRenderingContext2D) {
  if (ctx.fillStyle !== "transparent") {
    ctx.fill();
  }

  if (ctx.lineWidth > 0) {
    ctx.stroke();
  }
}

function roundedRect(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  const nextRadius = Math.min(radius, width / 2, height / 2);
  ctx.beginPath();
  ctx.moveTo(x + nextRadius, y);
  ctx.lineTo(x + width - nextRadius, y);
  ctx.quadraticCurveTo(x + width, y, x + width, y + nextRadius);
  ctx.lineTo(x + width, y + height - nextRadius);
  ctx.quadraticCurveTo(x + width, y + height, x + width - nextRadius, y + height);
  ctx.lineTo(x + nextRadius, y + height);
  ctx.quadraticCurveTo(x, y + height, x, y + height - nextRadius);
  ctx.lineTo(x, y + nextRadius);
  ctx.quadraticCurveTo(x, y, x + nextRadius, y);
  ctx.closePath();
}

function rotateElementContext(ctx: CanvasRenderingContext2D, element: CanvasElement) {
  if (element.rotation === 0) {
    return;
  }

  const centerX = element.x + element.width / 2;
  const centerY = element.y + element.height / 2;
  ctx.translate(centerX, centerY);
  ctx.rotate(element.rotation * (Math.PI / 180));
  ctx.translate(-centerX, -centerY);
}

function stringStyle(value: unknown, fallback: string) {
  return typeof value === "string" && value.length > 0 ? value : fallback;
}

function numericStyle(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function baseStrokeWidth(element: CanvasElement) {
  const width = numericStyle(element.style.strokeWidth, 2);

  if (element.type === "rectangle" || element.type === "circle" || element.type === "image" || isLineElement(element)) {
    return width > 0 ? Math.max(2, width) : 0;
  }

  return width;
}

function selectionColor(element: CanvasElement) {
  const stroke = stringStyle(element.style.stroke, "#4d7cfe");
  return stroke === "transparent" ? "#4d7cfe" : stroke;
}

function curvePreset(element: CanvasElement): CurvePreset {
  const preset = element.data.curvePreset;
  if (preset === "dashed" || preset === "pulse" || preset === "pulseReverse" || preset === "signal" || preset === "signalReverse" || preset === "solid" || preset === "dotted") {
    return preset;
  }
  if (element.type === "arrow") {
    return "solid";
  }
  return "solid";
}

function isLineElement(element: CanvasElement) {
  return element.type === "connector" || element.type === "arrow" || element.type === "curve";
}

function shadowColor(color: string) {
  return colorToRgba(color, 0.34);
}

function haloColor(color: string) {
  return colorToRgba(color, 0.2);
}

function colorToRgba(color: string, alpha: number) {
  const normalized = color.trim();
  const hex = normalized.match(/^#([0-9a-f]{6})$/i);

  if (!hex) {
    return `rgba(77, 124, 254, ${alpha})`;
  }

  const value = Number.parseInt(hex[1], 16);
  const red = (value >> 16) & 255;
  const green = (value >> 8) & 255;
  const blue = value & 255;
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}
