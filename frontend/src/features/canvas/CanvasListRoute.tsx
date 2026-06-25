import { Grid3X3, Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useOutletContext } from "react-router-dom";
import { AppError } from "../../contracts/api-client";
import type { CanvasResponse } from "../../contracts/canvas";
import { createCanvas, listCanvases } from "../../contracts/canvas";
import { canRole } from "../../contracts/workspaces";
import { Badge, Button, EmptyState, ErrorState, Input, LoadingState, Panel, PaperSurface } from "../../design-system/components";
import { useAuth } from "../auth/AuthProvider";
import type { ShellOutletContext } from "../shell/AppShell";
import { PermissionDeniedState, RouteAppErrorState } from "../workspaces/WorkspaceStates";

type RouteStatus = "idle" | "loading" | "ready" | "permission_denied" | "error";

export function CanvasListRoute() {
  const outlet = useOutletContext<ShellOutletContext>();
  const auth = useAuth();
  const navigate = useNavigate();
  const workspace = outlet.activeWorkspace;
  const canView = canRole(outlet.activeRole, "canvas:view");
  const canCreate = canRole(outlet.activeRole, "canvas:create");
  const [status, setStatus] = useState<RouteStatus>("idle");
  const [error, setError] = useState<AppError | null>(null);
  const [canvases, setCanvases] = useState<CanvasResponse[]>([]);
  const [title, setTitle] = useState("");
  const [createError, setCreateError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const latestCanvas = canvases[0] ?? null;
  const canvasCountLabel = useMemo(() => `${canvases.length} ${canvases.length === 1 ? "canvas" : "canvases"}`, [canvases.length]);

  const reload = useCallback(async (signal?: AbortSignal) => {
    if (!workspace) {
      return;
    }

    setStatus("loading");
    setError(null);

    try {
      setCanvases(await listCanvases(auth.client, workspace.id, signal));
      setStatus("ready");
    } catch (loadError) {
      if (loadError instanceof AppError && loadError.code === "REQUEST_ABORTED") {
        return;
      }

      const appError = loadError instanceof AppError ? loadError : new AppError({ status: 0, code: "CANVAS_LIST_FAILED", message: "Canvas list could not be loaded.", retryable: true });
      setError(appError);
      setStatus(appError.status === 403 ? "permission_denied" : "error");
    }
  }, [auth.client, workspace]);

  useEffect(() => {
    const controller = new AbortController();
    void reload(controller.signal);
    return () => controller.abort();
  }, [reload, outlet.cacheSerial]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError("");

    if (!workspace) {
      return;
    }

    if (!title.trim()) {
      setCreateError("Title is required.");
      return;
    }

    setSubmitting(true);

    try {
      const created = await createCanvas(auth.client, { workspaceId: workspace.id, title: title.trim() });
      outlet.pushToast("Canvas created");
      navigate(`/w/${workspace.slug}/c/${created.id}`);
    } catch (createFailed) {
      const appError = createFailed instanceof AppError ? createFailed : new AppError({ status: 0, code: "CANVAS_CREATE_FAILED", message: "Canvas could not be created.", retryable: true });
      setCreateError(appError.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (!workspace) {
    return <LoadingState label="Workspace canvas context loading" />;
  }

  if (!canView) {
    return <PermissionDeniedState error={new AppError({ status: 403, code: "PERMISSION_DENIED", message: "Your workspace role cannot view canvases.", retryable: false })} />;
  }

  if (status === "loading" || status === "idle") {
    return <LoadingState label="Canvas list loading" />;
  }

  if (status === "permission_denied") {
    return <PermissionDeniedState error={error} />;
  }

  if (status === "error" && error) {
    return <RouteAppErrorState error={error} onRetry={() => void reload()} />;
  }

  return (
    <section className="canvas-list-route" aria-labelledby="canvas-list-title">
      <div className="route-heading">
        <span className="kicker">Canvases</span>
        <h1 id="canvas-list-title">Canvas workbench</h1>
        <p>Create, open, and review canvas documents for the active workspace.</p>
      </div>

      <div className="canvas-list-grid">
        <div className="canvas-list-stack">
          {latestCanvas ? (
            <PaperSurface className="canvas-feature-card" as="article">
              <span className="coord">latest canvas/{latestCanvas.id}</span>
              <div>
                <h2>{latestCanvas.title}</h2>
                <p className="paper-copy">Version {latestCanvas.version}, {latestCanvas.content.elements.length} elements, snapshot {latestCanvas.snapshotVersion ?? "none"}.</p>
              </div>
              <Link className="button button-primary" to={`/w/${workspace.slug}/c/${latestCanvas.id}`}>
                <Grid3X3 aria-hidden="true" size={16} />
                <span>Open latest canvas</span>
              </Link>
            </PaperSurface>
          ) : (
            <EmptyState title="No canvases yet" copy="Create a canvas in this workspace before opening the editor route." />
          )}

          {canvases.length > 0 ? (
            <div className="canvas-card-grid">
              {canvases.map((canvas) => (
                <PaperSurface className="canvas-list-card" as="article" key={canvas.id}>
                  <span className="coord">canvas/{canvas.id}</span>
                  <h2>{canvas.title}</h2>
                  <p className="paper-copy">Updated {formatDateTime(canvas.updatedAt)}</p>
                  <div className="canvas-card-meta">
                    <Badge tone="info">v{canvas.version}</Badge>
                    <Badge tone={canvas.content.elements.length > 0 ? "healthy" : "neutral"}>{canvas.content.elements.length} elements</Badge>
                    <Badge tone={canvas.snapshotVersion ? "warning" : "neutral"}>{canvas.snapshotVersion ? `snapshot ${canvas.snapshotVersion}` : "no snapshot"}</Badge>
                  </div>
                  <Link className="button button-secondary" to={`/w/${workspace.slug}/c/${canvas.id}`}>
                    <Grid3X3 aria-hidden="true" size={16} />
                    <span>Open canvas</span>
                  </Link>
                </PaperSurface>
              ))}
            </div>
          ) : null}
        </div>

        <Panel className="canvas-create-panel" as="aside">
          <div className="panel-title-row">
            <div>
              <span className="kicker">Create canvas</span>
              <h2>New document</h2>
            </div>
            <Badge tone="neutral">{canvasCountLabel}</Badge>
          </div>
          {createError ? (
            <div className="form-alert form-alert-danger" role="alert">
              {createError}
            </div>
          ) : null}
          <form className="canvas-create-form" onSubmit={submit}>
            <div className="form-field">
              <label className="field-label" htmlFor="canvas-create-title">
                Title
              </label>
              <Input id="canvas-create-title" value={title} onChange={(event) => setTitle(event.target.value)} disabled={!canCreate} />
            </div>
            <Button variant="primary" type="submit" icon={<Plus aria-hidden="true" size={14} />} disabled={!canCreate || submitting}>
              {submitting ? "Creating" : "Create canvas"}
            </Button>
          </form>
          {!canCreate ? <ErrorState title="Create unavailable" copy="Your current workspace role can view canvases but cannot create a new document." /> : null}
        </Panel>
      </div>
    </section>
  );
}

function formatDateTime(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}
