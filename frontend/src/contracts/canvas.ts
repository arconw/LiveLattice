import { AppError } from "./api-client";
import type { GatewayClient } from "./api-client";

export const canvasElementTypes = ["rectangle", "circle", "text", "image", "connector", "arrow", "freehand"] as const;

export type CanvasElementType = (typeof canvasElementTypes)[number];

export type CanvasPoint = {
  x: number;
  y: number;
};

export type CanvasElementStyle = {
  fill?: string;
  stroke?: string;
  strokeWidth?: number;
  opacity?: number;
  fontFamily?: string;
  fontSize?: number;
  fontWeight?: string | number;
  [key: string]: unknown;
};

export type CanvasElementData = {
  text?: string;
  src?: string;
  points?: CanvasPoint[];
  path?: string;
  start?: CanvasPoint;
  end?: CanvasPoint;
  [key: string]: unknown;
};

export type CanvasElement = {
  id: string;
  type: CanvasElementType;
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  style: CanvasElementStyle;
  data: CanvasElementData;
  zIndex: number;
  locked: boolean;
  groupId: string | null;
};

export type CanvasViewportState = {
  zoom: number;
  panX: number;
  panY: number;
};

export type CanvasMetadata = {
  width: number;
  height: number;
  backgroundColor: string;
  gridEnabled: boolean;
};

export type CanvasContent = {
  elements: CanvasElement[];
  viewport: CanvasViewportState;
  metadata: CanvasMetadata;
};

export type CanvasResponse = {
  id: string;
  workspaceId: string;
  title: string;
  content: CanvasContent;
  version: number;
  lockVersion: number;
  snapshotVersion: number | null;
  operationCountSinceSnapshot: number;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
};

export type RawCanvasResponse = Omit<CanvasResponse, "content"> & {
  content: unknown;
};

export type CreateCanvasPayload = {
  workspaceId: string;
  title: string;
  templateId?: string;
};

export type UpdateCanvasPayload = {
  title?: string;
  content?: CanvasContent;
  expectedVersion?: number;
  expectedLockVersion?: number;
};

export type CommentResponse = {
  id: string;
  canvasId: string;
  parentId: string | null;
  authorId: string;
  content: string;
  resolved: boolean;
  resolvedBy: string | null;
  resolvedAt: string | null;
  targetElementId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateCommentPayload = {
  content: string;
  parentId?: string | null;
  targetElementId?: string | null;
};

export type UpdateCommentPayload = {
  content?: string;
  resolved?: boolean;
};

export type SnapshotResponse = {
  id: string;
  canvasId: string;
  version: number;
  minioPath: string | null;
  createdBy: string;
  snapshotAt: string;
};

export type SnapshotContentResponse = {
  canvasId: string;
  version: number;
  content: CanvasContent;
  snapshotAt: string;
};

export type TemplateResponse = {
  id: string;
  workspaceId: string | null;
  name: string;
  category: string | null;
  thumbnail: string | null;
  content: CanvasContent;
  createdBy: string;
  createdAt: string;
};

export type RawTemplateResponse = Omit<TemplateResponse, "content"> & {
  content: unknown;
};

export type CreateTemplatePayload = {
  name: string;
  category?: string | null;
  thumbnail?: string | null;
  content?: CanvasContent;
  canvasId?: string;
};

export type ImportResponse = {
  canvasId: string | null;
  jobId: string | null;
  status: string;
  message: string;
};

export type ImportExportJobResponse = {
  jobId: string;
  type: string;
  status: string;
  progress: number;
  result: string | null;
  error: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type CanvasExportResult =
  | {
      kind: "download";
      blob: Blob;
      filename: string;
      contentType: string;
    }
  | {
      kind: "job";
      job: ImportExportJobResponse;
    };

export const defaultCanvasContent: CanvasContent = {
  elements: [],
  viewport: { zoom: 1, panX: 0, panY: 0 },
  metadata: { width: 2400, height: 1400, backgroundColor: "#eef2f5", gridEnabled: true }
};

export function normalizeCanvasResponse(response: RawCanvasResponse): CanvasResponse {
  return {
    ...response,
    snapshotVersion: response.snapshotVersion ?? null,
    content: normalizeCanvasContent(response.content)
  };
}

export function normalizeTemplateResponse(response: RawTemplateResponse): TemplateResponse {
  return {
    ...response,
    workspaceId: response.workspaceId ?? null,
    category: response.category ?? null,
    thumbnail: response.thumbnail ?? null,
    content: normalizeCanvasContent(response.content)
  };
}

export function normalizeSnapshotContent(response: Omit<SnapshotContentResponse, "content"> & { content: unknown }): SnapshotContentResponse {
  return {
    ...response,
    content: normalizeCanvasContent(response.content)
  };
}

export function normalizeCanvasContent(value: unknown): CanvasContent {
  const record = isRecord(value) ? value : {};
  const viewport = isRecord(record.viewport) ? record.viewport : {};
  const metadata = isRecord(record.metadata) ? record.metadata : {};
  const elements = Array.isArray(record.elements) ? record.elements : [];

  return {
    elements: elements.map(normalizeCanvasElement).filter((element): element is CanvasElement => element !== null).sort((a, b) => a.zIndex - b.zIndex),
    viewport: {
      zoom: clamp(numberValue(viewport.zoom, defaultCanvasContent.viewport.zoom), 0.1, 4),
      panX: numberValue(viewport.panX, defaultCanvasContent.viewport.panX),
      panY: numberValue(viewport.panY, defaultCanvasContent.viewport.panY)
    },
    metadata: {
      width: numberValue(metadata.width, defaultCanvasContent.metadata.width),
      height: numberValue(metadata.height, defaultCanvasContent.metadata.height),
      backgroundColor: stringValue(metadata.backgroundColor, defaultCanvasContent.metadata.backgroundColor),
      gridEnabled: booleanValue(metadata.gridEnabled, defaultCanvasContent.metadata.gridEnabled)
    }
  };
}

export function normalizeCanvasElement(value: unknown, index = 0): CanvasElement | null {
  if (!isRecord(value) || !isCanvasElementType(value.type)) {
    return null;
  }

  return {
    id: stringValue(value.id, `element-${index + 1}`),
    type: value.type,
    x: numberValue(value.x, 0),
    y: numberValue(value.y, 0),
    width: Math.max(1, numberValue(value.width, defaultSizeForType(value.type).width)),
    height: Math.max(1, numberValue(value.height, defaultSizeForType(value.type).height)),
    rotation: numberValue(value.rotation, 0),
    style: normalizeRecord(value.style, defaultStyleForType(value.type)),
    data: normalizeElementData(value.data),
    zIndex: numberValue(value.zIndex, index + 1),
    locked: booleanValue(value.locked, false),
    groupId: typeof value.groupId === "string" && value.groupId.length > 0 ? value.groupId : null
  };
}

export function isCanvasElementType(value: unknown): value is CanvasElementType {
  return typeof value === "string" && canvasElementTypes.includes(value as CanvasElementType);
}

export function stripElementUiState(element: CanvasElement): CanvasElement {
  return {
    id: element.id,
    type: element.type,
    x: element.x,
    y: element.y,
    width: element.width,
    height: element.height,
    rotation: element.rotation,
    style: { ...element.style },
    data: { ...element.data },
    zIndex: element.zIndex,
    locked: element.locked,
    groupId: element.groupId
  };
}

export function toPersistedCanvasContent(content: CanvasContent): CanvasContent {
  return {
    elements: content.elements.map(stripElementUiState),
    viewport: { ...content.viewport },
    metadata: { ...content.metadata }
  };
}

export async function getCanvas(client: GatewayClient, canvasId: string, signal?: AbortSignal) {
  return normalizeCanvasResponse(await client.get<RawCanvasResponse>(`/api/core/canvases/${encodeURIComponent(canvasId)}`, { signal }));
}

export async function listCanvases(client: GatewayClient, workspaceId: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ workspaceId, limit: "50", offset: "0" });
  const canvases = await client.get<RawCanvasResponse[]>(`/api/core/canvases?${params.toString()}`, { signal });
  return canvases.map(normalizeCanvasResponse);
}

export async function createCanvas(client: GatewayClient, payload: CreateCanvasPayload) {
  return normalizeCanvasResponse(await client.post<RawCanvasResponse>("/api/core/canvases", payload));
}

export async function updateCanvas(client: GatewayClient, canvasId: string, payload: UpdateCanvasPayload) {
  const nextPayload = payload.content ? { ...payload, content: toPersistedCanvasContent(payload.content) } : payload;
  return normalizeCanvasResponse(await client.patch<RawCanvasResponse>(`/api/core/canvases/${encodeURIComponent(canvasId)}`, nextPayload));
}

export async function listCanvasComments(client: GatewayClient, canvasId: string, signal?: AbortSignal) {
  return client.get<CommentResponse[]>(`/api/core/canvases/${encodeURIComponent(canvasId)}/comments?limit=100`, { signal });
}

export async function createCanvasComment(client: GatewayClient, canvasId: string, payload: CreateCommentPayload) {
  return client.post<CommentResponse>(`/api/core/canvases/${encodeURIComponent(canvasId)}/comments`, payload);
}

export async function updateCanvasComment(client: GatewayClient, canvasId: string, commentId: string, payload: UpdateCommentPayload) {
  return client.patch<CommentResponse>(`/api/core/canvases/${encodeURIComponent(canvasId)}/comments/${encodeURIComponent(commentId)}`, payload);
}

export async function deleteCanvasComment(client: GatewayClient, canvasId: string, commentId: string) {
  await client.delete<void>(`/api/core/canvases/${encodeURIComponent(canvasId)}/comments/${encodeURIComponent(commentId)}`);
}

export async function createCanvasSnapshot(client: GatewayClient, canvasId: string) {
  return client.post<SnapshotResponse>(`/api/core/canvases/${encodeURIComponent(canvasId)}/snapshot`);
}

export async function listCanvasSnapshots(client: GatewayClient, canvasId: string, signal?: AbortSignal) {
  return client.get<SnapshotResponse[]>(`/api/core/canvases/${encodeURIComponent(canvasId)}/history`, { signal });
}

export async function getCanvasSnapshotContent(client: GatewayClient, canvasId: string, version: number) {
  return normalizeSnapshotContent(await client.get<Omit<SnapshotContentResponse, "content"> & { content: unknown }>(`/api/core/canvases/${encodeURIComponent(canvasId)}/history/${version}`));
}

export async function restoreCanvasSnapshot(client: GatewayClient, canvasId: string, version: number) {
  return normalizeCanvasResponse(await client.post<RawCanvasResponse>(`/api/core/canvases/${encodeURIComponent(canvasId)}/restore/${version}`));
}

export async function listTemplates(client: GatewayClient, workspaceId: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ workspaceId });
  const templates = await client.get<RawTemplateResponse[]>(`/api/core/templates?${params.toString()}`, { signal });
  return templates.map(normalizeTemplateResponse);
}

export async function saveTemplate(client: GatewayClient, workspaceId: string, payload: CreateTemplatePayload) {
  const params = new URLSearchParams({ workspaceId });
  const nextPayload = payload.content ? { ...payload, content: toPersistedCanvasContent(payload.content) } : payload;
  return normalizeTemplateResponse(await client.post<RawTemplateResponse>(`/api/core/templates?${params.toString()}`, nextPayload));
}

export async function importCanvasFile(options: { accessToken: string | null; file: File; workspaceId: string; title: string; fetchImpl?: typeof fetch }) {
  const form = new FormData();
  form.set("file", options.file);
  form.set("options", JSON.stringify({ workspaceId: options.workspaceId, title: options.title }));
  const response = await (options.fetchImpl ?? fetch)("/api/import-export/import", {
    method: "POST",
    headers: bearerHeaders(options.accessToken),
    body: form
  });
  return readImportExportResponse<ImportResponse>(response);
}

export async function exportCanvasFile(options: { accessToken: string | null; canvasId: string; format: "svg" | "png" | "pdf" | "json"; fetchImpl?: typeof fetch }): Promise<CanvasExportResult> {
  const response = await (options.fetchImpl ?? fetch)(`/api/import-export/export/${encodeURIComponent(options.canvasId)}?format=${encodeURIComponent(options.format)}`, {
    method: "POST",
    headers: bearerHeaders(options.accessToken)
  });

  if (!response.ok) {
    throw await appErrorFromResponse(response);
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return {
      kind: "job",
      job: await response.json() as ImportExportJobResponse
    };
  }

  return {
    kind: "download",
    blob: await response.blob(),
    filename: filenameFromDisposition(response.headers.get("content-disposition")) ?? `canvas-${options.canvasId}.${options.format}`,
    contentType
  };
}

function defaultSizeForType(type: CanvasElementType) {
  if (type === "circle") {
    return { width: 120, height: 120 };
  }

  if (type === "text") {
    return { width: 180, height: 72 };
  }

  if (type === "connector" || type === "arrow" || type === "freehand") {
    return { width: 180, height: 80 };
  }

  return { width: 180, height: 96 };
}

function defaultStyleForType(type: CanvasElementType): CanvasElementStyle {
  if (type === "text") {
    return { fill: "transparent", stroke: "transparent", strokeWidth: 0, opacity: 1, fontFamily: "Inter", fontSize: 16, fontWeight: 700 };
  }

  if (type === "connector" || type === "arrow" || type === "freehand") {
    return { fill: "transparent", stroke: "#273142", strokeWidth: 3, opacity: 1 };
  }

  return { fill: "#ffffff", stroke: "#4d7cfe", strokeWidth: 2, opacity: 1 };
}

function normalizeElementData(value: unknown): CanvasElementData {
  const record = normalizeRecord(value, {});

  if (Array.isArray(record.points)) {
    return {
      ...record,
      points: record.points.flatMap((point) => {
        if (!isRecord(point)) {
          return [];
        }

        return [{ x: numberValue(point.x, 0), y: numberValue(point.y, 0) }];
      })
    };
  }

  return record;
}

function normalizeRecord(value: unknown, fallback: Record<string, unknown>): Record<string, unknown> {
  return isRecord(value) ? { ...value } : { ...fallback };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function numberValue(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function stringValue(value: unknown, fallback: string) {
  return typeof value === "string" && value.length > 0 ? value : fallback;
}

function booleanValue(value: unknown, fallback: boolean) {
  return typeof value === "boolean" ? value : fallback;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function bearerHeaders(accessToken: string | null): Headers {
  const headers = new Headers();
  if (accessToken) {
    headers.set("authorization", `Bearer ${accessToken}`);
  }
  return headers;
}

async function readImportExportResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await appErrorFromResponse(response);
  }

  return await response.json() as T;
}

async function appErrorFromResponse(response: Response) {
  let message = "The import/export request failed.";
  let code = "IMPORT_EXPORT_FAILED";

  try {
    const payload = await response.json() as { message?: string; error?: string };
    message = payload.message ?? payload.error ?? message;
    code = payload.error ?? code;
  } catch {
    return new AppError({ status: response.status, code, message, retryable: response.status >= 500 });
  }

  return new AppError({ status: response.status, code, message, retryable: response.status >= 500 });
}

function filenameFromDisposition(disposition: string | null) {
  if (!disposition) {
    return null;
  }

  const match = /filename="?([^";]+)"?/i.exec(disposition);
  return match?.[1] ?? null;
}
