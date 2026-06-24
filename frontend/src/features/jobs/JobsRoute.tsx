import { Download, FileWarning, RefreshCcw, RotateCw, Upload, XCircle } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";
import { useOutletContext, useParams, useSearchParams } from "react-router-dom";
import { AppError, createWorkspaceCacheKey } from "../../contracts/api-client";
import { activityJobsFixture } from "../../contracts/fixtures";
import { jobStateView, listBackgroundJobs, listImportExportJobs, refreshJobDownloadUrl, validateImportUpload } from "../../contracts/jobs";
import type { ActivityJob, JobStatus } from "../../contracts/jobs";
import { canRole } from "../../contracts/workspaces";
import { Badge, Button, EmptyState, ErrorState, Input, LoadingState, Panel, Select, StatusChip } from "../../design-system/components";
import { ActivityOverview } from "../activity/ActivityOverview";
import { useAuth } from "../auth/AuthProvider";
import type { ShellOutletContext } from "../shell/AppShell";
import { PermissionDeniedState } from "../workspaces/WorkspaceStates";

type JobLoadStatus = "loading" | "ready" | "empty" | "error";

export function JobsRoute() {
  const { workspaceSlug = "factory-floor" } = useParams();
  const outlet = useOutletContext<ShellOutletContext>();
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const workspaceId = outlet.activeWorkspace?.id ?? activityJobsFixture[0]?.workspaceId ?? workspaceSlug;
  const [jobs, setJobs] = useState<ActivityJob[]>(activityJobsFixture);
  const [status, setStatus] = useState<JobLoadStatus>("ready");
  const [error, setError] = useState<AppError | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadAccepted, setUploadAccepted] = useState<string | null>(null);
  const [downloadRefreshError, setDownloadRefreshError] = useState<string | null>(null);
  const selectedStatus = normalizeStatusParam(searchParams.get("status"));
  const selectedType = searchParams.get("type") ?? "";
  const cacheKey = createWorkspaceCacheKey(workspaceSlug, "jobs", selectedStatus, selectedType);
  const canViewJobs = canRole(outlet.activeRole, "jobs:view");

  useEffect(() => {
    if (!canViewJobs) {
      return undefined;
    }

    const controller = new AbortController();
    setStatus("loading");

    Promise.all([
      listImportExportJobs(auth.client, { workspaceId, status: selectedStatus ?? undefined, type: selectedType || undefined }, controller.signal),
      listBackgroundJobs(auth.client, { workspaceId, status: selectedStatus ?? undefined, type: selectedType || undefined }, controller.signal)
    ])
      .then(([importExportJobs, backgroundJobs]) => {
        const nextJobs = [...importExportJobs.jobs, ...backgroundJobs.jobs];
        setJobs(nextJobs);
        setStatus(nextJobs.length > 0 ? "ready" : "empty");
        setError(null);
      })
      .catch((loadError) => {
        if (loadError instanceof AppError && loadError.code === "REQUEST_ABORTED") {
          return;
        }

        setError(loadError instanceof AppError ? loadError : new AppError({ status: 0, code: "JOB_LOAD_FAILED", message: "Job activity could not be loaded.", retryable: true }));
        setStatus("error");
      });

    return () => controller.abort();
  }, [auth.client, canViewJobs, selectedStatus, selectedType, workspaceId]);

  const visibleJobs = useMemo(
    () =>
      jobs.filter((job) => {
        const statusMatches = selectedStatus ? job.status === selectedStatus : true;
        const typeMatches = selectedType ? job.type.toLowerCase().includes(selectedType.toLowerCase()) : true;
        return statusMatches && typeMatches;
      }),
    [jobs, selectedStatus, selectedType]
  );

  function updateFilters(nextStatus: string, nextType: string) {
    const next = new URLSearchParams(searchParams);
    if (nextStatus) {
      next.set("status", nextStatus);
    } else {
      next.delete("status");
    }

    if (nextType) {
      next.set("type", nextType);
    } else {
      next.delete("type");
    }
    setSearchParams(next);
  }

  function validateUpload(fileList: FileList | null) {
    const file = fileList?.[0];

    if (!file) {
      setUploadAccepted(null);
      setUploadError(null);
      return;
    }

    const validation = validateImportUpload(file);

    if (!validation.ok) {
      setUploadAccepted(null);
      setUploadError(validation.error);
      return;
    }

    setUploadError(null);
    setUploadAccepted(`${file.name} accepted for backend validation`);
  }

  async function refreshDownload(job: ActivityJob) {
    setDownloadRefreshError(null);

    try {
      const refreshed = await refreshJobDownloadUrl(auth.client, job);
      setJobs((current) =>
        current.map((item) =>
          item.id === job.id
            ? {
                ...item,
                downloadUrl: refreshed.downloadUrl ?? item.downloadUrl,
                downloadExpiresAt: refreshed.downloadExpiresAt ?? new Date(Date.now() + 60 * 60 * 1000).toISOString()
              }
            : item
        )
      );
      outlet.pushToast("Download URL refreshed");
    } catch {
      setDownloadRefreshError("Download URL refresh failed. The expired link was kept so you can retry.");
      outlet.pushToast("Download URL refresh failed");
    }
  }

  if (!canViewJobs) {
    return <PermissionDeniedState error={new AppError({ status: 403, code: "PERMISSION_DENIED", message: "Your workspace role cannot access job activity.", retryable: false })} />;
  }

  return (
    <section className="feature-route jobs-route" aria-labelledby="jobs-route-title">
      <div className="route-heading">
        <span className="kicker">Import, export, and background jobs</span>
        <h1 id="jobs-route-title">Job activity</h1>
        <p>Async jobs remain visible across routes with queued, running, succeeded, failed, and cancelled state-machine actions.</p>
      </div>

      <div className="feature-grid jobs-layout">
        <Panel className="job-controls-panel" as="aside">
          <span className="kicker">Filters and upload</span>
          <div className="form-field">
            <label className="field-label" htmlFor="job-status">
              Status
            </label>
            <Select id="job-status" value={selectedStatus ?? ""} onChange={(event) => updateFilters(event.target.value, selectedType)}>
              <option value="">All statuses</option>
              <option value="queued">queued</option>
              <option value="running">running</option>
              <option value="succeeded">succeeded</option>
              <option value="failed">failed</option>
              <option value="cancelled">cancelled</option>
            </Select>
          </div>
          <div className="form-field">
            <label className="field-label" htmlFor="job-type">
              Job type
            </label>
            <Input id="job-type" value={selectedType} onChange={(event) => updateFilters(selectedStatus ?? "", event.target.value)} placeholder="EXPORT, IMPORT, IndexSync" />
          </div>
          <div className="upload-dropzone">
            <Upload size={22} aria-hidden="true" />
            <label className="field-label" htmlFor="import-file">
              Import file
            </label>
            <Input id="import-file" type="file" accept=".drawio,.xml,.svg,.json" onChange={(event) => validateUpload(event.target.files)} />
            {uploadError ? (
              <div className="form-alert form-alert-danger" role="alert">
                {uploadError}
              </div>
            ) : null}
            {uploadAccepted ? (
              <div className="form-alert" role="status">
                {uploadAccepted}
              </div>
            ) : null}
            <p className="small-copy">Frontend validation is early feedback only. Backend magic-byte and quota validation remain authoritative.</p>
          </div>
          <div className="key-value">
            <span>Cache key</span>
            <strong>{cacheKey.join(" / ")}</strong>
          </div>
        </Panel>

        <Panel className="job-list-panel" as="section">
          <div className="panel-heading-row">
            <div>
              <span className="kicker">Visible jobs</span>
              <h2>Progress state machine</h2>
            </div>
            <StatusChip tone="info">{visibleJobs.length} shown</StatusChip>
          </div>
          {status === "loading" ? <LoadingState label="Loading job activity" /> : null}
          {status === "error" && error ? <ErrorState title="Job service unavailable" copy={error.message} requestId={error.requestId} /> : null}
          {downloadRefreshError ? (
            <div className="form-alert form-alert-danger" role="alert">
              {downloadRefreshError}
            </div>
          ) : null}
          {status === "empty" || visibleJobs.length === 0 ? <EmptyState title="No jobs match" copy="Import, export, and background jobs will appear here without blocking route navigation." /> : null}
          <div className="job-card-list">
            {visibleJobs.map((job) => (
              <JobCard job={job} key={job.id} onRefreshDownload={() => void refreshDownload(job)} />
            ))}
          </div>
        </Panel>
      </div>

      <ActivityOverview />
    </section>
  );
}

function JobCard({ job, onRefreshDownload }: { job: ActivityJob; onRefreshDownload: () => void }) {
  const view = jobStateView(job);

  return (
    <article className="job-card">
      <div className="job-card-heading">
        <div>
          <Badge tone={job.domain === "background" ? "warning" : job.domain === "import" ? "info" : "healthy"}>{job.domain}</Badge>
          <h3>{job.type}</h3>
        </div>
        <StatusChip tone={view.tone}>{view.label}</StatusChip>
      </div>
      <div className="progress-track" aria-label={`${job.type} progress ${job.progress} percent`}>
        <span className="progress-fill" style={{ "--progress": `${job.progress}%` } as CSSProperties} />
      </div>
      <p className="small-copy">{view.progressText}</p>
      <dl className="job-meta-grid">
        <div>
          <dt>Owner</dt>
          <dd>{job.ownerId}</dd>
        </div>
        <div>
          <dt>Retry</dt>
          <dd>
            {job.retryCount}/{job.maxRetries}
          </dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>{formatDate(job.createdAt)}</dd>
        </div>
      </dl>
      {job.failureReason ? (
        <div className="form-alert form-alert-danger">
          <FileWarning size={16} aria-hidden="true" />
          {job.failureReason}
        </div>
      ) : null}
      <div className="job-action-row">
        {view.primaryAction === "download" && job.downloadUrl ? (
          <a className="button button-primary" href={job.downloadUrl}>
            <Download size={16} aria-hidden="true" />
            <span>Download</span>
          </a>
        ) : null}
        {view.primaryAction === "refresh-download" ? (
          <Button variant="secondary" icon={<RefreshCcw size={16} aria-hidden="true" />} onClick={onRefreshDownload}>
            Refresh link
          </Button>
        ) : null}
        {view.primaryAction === "retry" ? (
          <Button variant="secondary" icon={<RotateCw size={16} aria-hidden="true" />}>
            Retry
          </Button>
        ) : null}
        {view.primaryAction === "cancel" ? (
          <Button variant="secondary" icon={<XCircle size={16} aria-hidden="true" />}>
            Cancel
          </Button>
        ) : null}
      </div>
    </article>
  );
}

function normalizeStatusParam(value: string | null): JobStatus | null {
  return value === "queued" || value === "running" || value === "succeeded" || value === "failed" || value === "cancelled" ? value : null;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}
