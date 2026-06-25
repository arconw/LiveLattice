import type { GatewayClient } from "./api-client";

export const jobStatuses = ["queued", "running", "succeeded", "failed", "cancelled"] as const;
export const jobDomains = ["import", "export", "background"] as const;

export type JobStatus = (typeof jobStatuses)[number];
export type JobDomain = (typeof jobDomains)[number];

export type ActivityJob = {
  id: string;
  domain: JobDomain;
  type: string;
  workspaceId: string | null;
  ownerId: string;
  status: JobStatus;
  progress: number;
  retryCount: number;
  maxRetries: number;
  failureReason: string | null;
  downloadUrl: string | null;
  downloadExpiresAt: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
};

export type JobListResponse = {
  jobs: ActivityJob[];
  total: number;
};

export type JobStateView = {
  label: string;
  tone: "neutral" | "healthy" | "warning" | "info" | "danger";
  progressText: string;
  primaryAction: "cancel" | "download" | "refresh-download" | "retry" | "none";
};

export type JobListFilters = {
  workspaceId: string;
  status?: JobStatus;
  type?: string;
};

export type UploadValidation = {
  ok: boolean;
  error: string | null;
};

const allowedImportExtensions = [".drawio", ".xml", ".svg", ".json"] as const;
const maxUploadBytes = 100 * 1024 * 1024;

export function buildImportExportJobsPath(filters: JobListFilters) {
  const params = new URLSearchParams({ workspace_id: filters.workspaceId });

  if (filters.status) {
    params.set("status", filters.status);
  }

  if (filters.type) {
    params.set("type", filters.type);
  }

  return `/api/import-export/export/jobs?${params.toString()}`;
}

export function buildBackgroundJobsPath(filters: JobListFilters) {
  const params = new URLSearchParams({ workspace_id: filters.workspaceId });

  if (filters.status) {
    params.set("status", filters.status);
  }

  if (filters.type) {
    params.set("type", filters.type);
  }

  return `/api/background-jobs/jobs?${params.toString()}`;
}

export async function listImportExportJobs(client: GatewayClient, filters: JobListFilters, signal?: AbortSignal) {
  const payload = await client.get<unknown>(buildImportExportJobsPath(filters), { signal });
  return mapJobListResponse(payload, "export");
}

export async function listBackgroundJobs(client: GatewayClient, filters: JobListFilters, signal?: AbortSignal) {
  const payload = await client.get<unknown>(buildBackgroundJobsPath(filters), { signal });
  return mapJobListResponse(payload, "background");
}

export async function refreshJobDownloadUrl(client: GatewayClient, job: ActivityJob, signal?: AbortSignal) {
  const base = job.domain === "import" ? "/api/import-export/import/jobs" : "/api/import-export/export/jobs";
  const payload = await client.get<unknown>(`${base}/${job.id}/download`, { signal });
  const record = asRecord(payload);

  return {
    downloadUrl: toNullableString(record.downloadUrl ?? record.url),
    downloadExpiresAt: toNullableString(record.downloadExpiresAt ?? record.expiresAt ?? record.expires_at)
  };
}

export function mapJobListResponse(payload: unknown, fallbackDomain: JobDomain): JobListResponse {
  if (Array.isArray(payload)) {
    const jobs = payload.map((item) => mapJob(item, fallbackDomain)).filter((job): job is ActivityJob => job !== null);
    return { jobs, total: jobs.length };
  }

  const record = asRecord(payload);
  const sourceJobs = Array.isArray(record.jobs) ? record.jobs : Array.isArray(record.items) ? record.items : [];
  const jobs = sourceJobs.map((item) => mapJob(item, fallbackDomain)).filter((job): job is ActivityJob => job !== null);

  return {
    jobs,
    total: toNumber(record.total, jobs.length)
  };
}

export function mapJob(payload: unknown, fallbackDomain: JobDomain): ActivityJob | null {
  const record = asRecord(payload);
  const id = toString(record.id ?? record.jobId ?? record.job_id);
  const status = normalizeJobStatus(record.status);

  if (!id || !status) {
    return null;
  }

  return {
    id,
    domain: normalizeJobDomain(record.domain ?? record.kind) ?? fallbackDomain,
    type: toString(record.type ?? record.jobType ?? record.job_type) || "EXPORT",
    workspaceId: toNullableString(record.workspaceId ?? record.workspace_id),
    ownerId: toString(record.ownerId ?? record.owner_id ?? record.createdBy ?? record.created_by) || "unknown",
    status,
    progress: clampProgress(record.progress),
    retryCount: toNumber(record.retryCount ?? record.retry_count ?? record.retries, 0),
    maxRetries: toNumber(record.maxRetries ?? record.max_retries, 3),
    failureReason: toNullableString(record.failureReason ?? record.errorMessage ?? record.error_message),
    downloadUrl: toNullableString(record.downloadUrl ?? record.download_url),
    downloadExpiresAt: toNullableString(record.downloadExpiresAt ?? record.download_expires_at),
    createdAt: toString(record.createdAt ?? record.created_at) || new Date(0).toISOString(),
    startedAt: toNullableString(record.startedAt ?? record.started_at),
    completedAt: toNullableString(record.completedAt ?? record.completed_at)
  };
}

export function jobStateView(job: ActivityJob, now = new Date()) {
  const downloadExpired = Boolean(job.downloadExpiresAt && Date.parse(job.downloadExpiresAt) <= now.getTime());
  const progressText = progressTextFor(job);

  if (job.status === "queued") {
    return { label: "Queued", tone: "neutral", progressText, primaryAction: "cancel" } satisfies JobStateView;
  }

  if (job.status === "running") {
    return { label: "Running", tone: "info", progressText, primaryAction: "none" } satisfies JobStateView;
  }

  if (job.status === "succeeded" && downloadExpired) {
    return { label: "Download URL expired", tone: "warning", progressText: "Artifact ready, link expired", primaryAction: "refresh-download" } satisfies JobStateView;
  }

  if (job.status === "succeeded") {
    return { label: "Succeeded", tone: "healthy", progressText: "Artifact ready", primaryAction: job.downloadUrl ? "download" : "none" } satisfies JobStateView;
  }

  if (job.status === "failed") {
    return { label: "Failed", tone: "danger", progressText: `Failed after ${job.retryCount}/${job.maxRetries} retries`, primaryAction: job.retryCount < job.maxRetries ? "retry" : "none" } satisfies JobStateView;
  }

  return { label: "Cancelled", tone: "warning", progressText: "Stopped before completion", primaryAction: "none" } satisfies JobStateView;
}

export function validateImportUpload(file: File): UploadValidation {
  if (file.size > maxUploadBytes) {
    return { ok: false, error: "File is larger than the 100 MB import limit." };
  }

  const lowerName = file.name.toLowerCase();
  const allowed = allowedImportExtensions.some((extension) => lowerName.endsWith(extension));

  if (!allowed) {
    return { ok: false, error: "Unsupported import type. Use draw.io XML, SVG, or JSON." };
  }

  return { ok: true, error: null };
}

function progressTextFor(job: ActivityJob) {
  if (job.status === "queued") {
    return "Waiting for a worker";
  }

  if (job.status === "running") {
    return `${job.progress}% complete`;
  }

  return `${job.progress}% recorded`;
}

function normalizeJobStatus(value: unknown): JobStatus | null {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.toLowerCase();
  if (normalized === "pending") {
    return "queued";
  }

  if (normalized === "success" || normalized === "completed" || normalized === "complete") {
    return "succeeded";
  }

  if (normalized === "processing" || normalized === "in_progress") {
    return "running";
  }

  return jobStatuses.find((status) => status === normalized) ?? null;
}

function normalizeJobDomain(value: unknown): JobDomain | null {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.toLowerCase();
  return jobDomains.find((domain) => domain === normalized) ?? null;
}

function clampProgress(value: unknown) {
  const progress = toNumber(value, 0);
  return Math.min(100, Math.max(0, Math.round(progress)));
}

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function toNumber(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function toString(value: unknown) {
  return typeof value === "string" ? value : "";
}

function toNullableString(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : null;
}
