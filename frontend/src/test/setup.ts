import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";

Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn()
  }))
});

const canvasContext = {
  arc: vi.fn(),
  bezierCurveTo: vi.fn(),
  beginPath: vi.fn(),
  clearRect: vi.fn(),
  clip: vi.fn(),
  closePath: vi.fn(),
  drawImage: vi.fn(),
  ellipse: vi.fn(),
  fill: vi.fn(),
  fillRect: vi.fn(),
  fillText: vi.fn(),
  lineTo: vi.fn(),
  measureText: vi.fn((text: string) => ({ width: text.length * 8 })),
  moveTo: vi.fn(),
  quadraticCurveTo: vi.fn(),
  restore: vi.fn(),
  rotate: vi.fn(),
  save: vi.fn(),
  scale: vi.fn(),
  setLineDash: vi.fn(),
  setTransform: vi.fn(),
  stroke: vi.fn(),
  translate: vi.fn(),
  globalAlpha: 1,
  lineCap: "butt",
  lineJoin: "miter",
  lineWidth: 1,
  fillStyle: "#000000",
  font: "10px sans-serif",
  strokeStyle: "#000000",
  textAlign: "start",
  textBaseline: "alphabetic"
};

Object.defineProperty(HTMLCanvasElement.prototype, "getContext", {
  writable: true,
  value: vi.fn(() => canvasContext)
});
