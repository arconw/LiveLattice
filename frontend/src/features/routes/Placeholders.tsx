import { Grid3X3 } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useOutletContext, useParams } from "react-router-dom";
import { AppError, createWorkspaceCacheKey } from "../../contracts/api-client";
import { coreFixtureIds } from "../../contracts/fixture-ids";
import { canRole, workspaceDisplayTier } from "../../contracts/workspaces";
import { Button, EmptyState, ErrorState, LoadingState, Panel, PaperSurface, StatusChip, Input } from "../../design-system/components";
import type { ShellOutletContext } from "../shell/AppShell";
import { LatticeCockpit } from "../lattice/LatticeCockpit";
import { PermissionDeniedState, QuotaReachedState, RouteAppErrorState, isQuotaError } from "../workspaces/WorkspaceStates";
import { useWorkspaces } from "../workspaces/WorkspaceProvider";
export { AuditRoute } from "../audit/AuditRoute";
export { JobsRoute } from "../jobs/JobsRoute";
export { NotificationsRoute } from "../notifications/NotificationsRoute";
export { SearchRoute } from "../search/SearchRoute";

export function WorkspacesRoute() {
  const workspaceState = useWorkspaces();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [createError, setCreateError] = useState<AppError | null>(null);
  const [fieldError, setFieldError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const sortedWorkspaces = useMemo(() => [...workspaceState.workspaces].sort((a, b) => a.name.localeCompare(b.name)), [workspaceState.workspaces]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError(null);
    setFieldError("");

    if (!name.trim() || !slug.trim()) {
      setFieldError("Workspace name and slug are required.");
      return;
    }

    setSubmitting(true);

    try {
      const created = await workspaceState.createWorkspace({ name: name.trim(), slug: slug.trim() });
      navigate(`/w/${created.slug}`);
    } catch (error) {
      setCreateError(error instanceof AppError ? error : new AppError({ status: 0, code: "WORKSPACE_CREATE_FAILED", message: "Workspace could not be created.", retryable: true }));
    } finally {
      setSubmitting(false);
    }
  }

  if (workspaceState.status === "loading") {
    return <LoadingState label="Workspace loading" />;
  }

  if (workspaceState.status === "permission_denied") {
    return <PermissionDeniedState error={workspaceState.error} />;
  }

  if (workspaceState.status === "error" && workspaceState.error) {
    return <RouteAppErrorState error={workspaceState.error} onRetry={() => void workspaceState.reload()} />;
  }

  return (
    <section className="workspace-route" aria-labelledby="workspaces-title">
      <div className="route-heading">
        <span className="kicker">Tenant boundary</span>
        <h1 id="workspaces-title">Workspaces</h1>
        <p>Select or create a workspace. Switching route context clears workspace-scoped cache keys.</p>
      </div>

      <div className="workspace-grid">
        <div className="workspace-list">
          {workspaceState.status === "empty" ? (
            <EmptyState title="No workspaces" copy="Create the first workspace to establish membership, roles, quotas, and route-scoped data." />
          ) : null}

          {sortedWorkspaces.map((workspace) => (
            <PaperSurface as="article" key={workspace.id}>
              <span className="coord">workspace/{workspace.slug}</span>
              <h2>{workspace.name}</h2>
              <p className="paper-copy">
                {workspace.currentRole ? `${workspace.currentRole} role` : "membership unavailable"}, {workspaceDisplayTier(workspace.tier)} tier, {workspace.members.length} members
              </p>
              <div className="workspace-card-actions">
                <Link className="button button-primary" to={`/w/${workspace.slug}`}>
                  Open workspace
                </Link>
                {workspace.access === "revoked" ? <StatusChip tone="danger">access revoked</StatusChip> : <StatusChip tone={workspace.currentRole === "owner" || workspace.currentRole === "admin" ? "healthy" : "info"}>{workspace.currentRole}</StatusChip>}
              </div>
            </PaperSurface>
          ))}
        </div>

        <Panel className="workspace-create-panel" as="aside">
          <span className="kicker">Create workspace</span>
          <h2>New tenant boundary</h2>
          {createError && isQuotaError(createError) ? <QuotaReachedState error={createError} onRetry={() => setCreateError(null)} /> : null}
          {createError && !isQuotaError(createError) ? <ErrorState title="Workspace create failed" copy={createError.message} requestId={createError.requestId} /> : null}
          {fieldError ? (
            <div className="form-alert form-alert-danger" role="alert">
              {fieldError}
            </div>
          ) : null}
          <form className="workspace-form" onSubmit={submit}>
            <div className="form-field">
              <label className="field-label" htmlFor="workspace-name">
                Name
              </label>
              <Input id="workspace-name" value={name} onChange={(event) => setName(event.target.value)} autoComplete="off" />
            </div>
            <div className="form-field">
              <label className="field-label" htmlFor="workspace-slug">
                Slug
              </label>
              <Input id="workspace-slug" value={slug} onChange={(event) => setSlug(event.target.value)} autoComplete="off" />
            </div>
            <Button variant="primary" type="submit" disabled={submitting}>
              {submitting ? "Creating" : "Create workspace"}
            </Button>
          </form>
          <div className="member-state-list">
            <StatusChip tone="warning">Member invite pending</StatusChip>
            <p className="small-copy">Pending invitations remain visible until Core membership confirms the joined role.</p>
          </div>
        </Panel>
      </div>
    </section>
  );
}

export function WorkspaceHomeRoute() {
  const outlet = useOutletContext<ShellOutletContext>();
  const activeWorkspace = outlet.activeWorkspace;
  const canManageMembers = canRole(outlet.activeRole, "workspace:manage_members");
  const canCreateCanvas = canRole(outlet.activeRole, "canvas:create");

  return (
    <section className="workspace-home" aria-labelledby="workspace-home-title">
      <div className="route-heading compact">
        <span className="kicker">Not a file manager</span>
        <h1 id="workspace-home-title">Workspace cockpit</h1>
        <p>The first workspace surface keeps canvases, dashboards, search, jobs, notifications, and audit in one live lattice.</p>
      </div>
      <div className="workspace-role-strip" aria-label="Workspace role and permission summary">
        <Panel as="section">
          <span className="kicker">Current role</span>
          <h2>{outlet.activeRole ?? "No active role"}</h2>
          <p className="small-copy">{activeWorkspace ? `${activeWorkspace.name} is scoped by slug ${activeWorkspace.slug} and cache namespace ${outlet.cacheSerial}.` : "Workspace membership is not loaded."}</p>
        </Panel>
        <Panel as="section">
          <span className="kicker">Role-aware actions</span>
          <div className="role-action-row">
            <Button variant="secondary" disabled={!canCreateCanvas}>
              New canvas
            </Button>
            <Button variant="secondary" disabled={!canManageMembers}>
              Invite member
            </Button>
          </div>
          <p className="small-copy">{canManageMembers ? "Owners and admins can manage members. Backend 403 responses still remain authoritative." : "Member management is unavailable for this role."}</p>
        </Panel>
      </div>
      <LatticeCockpit onNotify={outlet.pushToast} />
    </section>
  );
}

export function CanvasRoute() {
  const { workspaceSlug = "factory-floor", canvasId = coreFixtureIds.canvasIncidentMap } = useParams();
  const outlet = useOutletContext<ShellOutletContext>();
  const cacheKey = createWorkspaceCacheKey(workspaceSlug, "canvas", canvasId);
  const canEditCanvas = canRole(outlet.activeRole, "canvas:edit");
  const canComment = canRole(outlet.activeRole, "canvas:comment");

  return (
    <section className="feature-route" aria-labelledby="canvas-workbench-title">
      <RouteHeading headingId="canvas-workbench-title" kicker="Canvas editor placeholder" title="Canvas workbench" copy={`Route /w/${workspaceSlug}/c/${canvasId} keeps canvas loading, comments, snapshots, imports, and realtime room state scoped to the workspace.`} />
      <div className="feature-grid">
        <PaperSurface className="canvas-placeholder" as="article">
          <div className="editor-toolbar" aria-label="Canvas tools">
            <Button variant="secondary" icon={<Grid3X3 size={16} aria-hidden="true" />} disabled={!canEditCanvas}>
              Select
            </Button>
            <Button variant="secondary" disabled={!canEditCanvas}>Shape</Button>
            <Button variant="secondary" disabled={!canEditCanvas}>Connector</Button>
            <Button variant="secondary" disabled={!canComment}>Comment</Button>
            <Button variant="secondary" disabled={!canEditCanvas}>Snapshot</Button>
            <Button variant="secondary" disabled={!canEditCanvas}>Export</Button>
          </div>
          <div className="editor-surface" aria-label="Canvas placeholder surface">
            <span className="shape shape-rect">REST Gateway</span>
            <span className="shape shape-diamond">Kafka</span>
            <span className="shape shape-note">Comment target el-42</span>
            <span className="shape shape-db">PostgreSQL JSONB</span>
            <span className="connector-line connector-a" />
            <span className="connector-line connector-b" />
            <span className="connector-line connector-c" />
          </div>
        </PaperSurface>
        <Panel className="route-inspector" as="aside">
          <StatusChip tone="warning">Realtime disconnected state planned</StatusChip>
          {!canEditCanvas ? <PermissionDeniedState error={new AppError({ status: 403, code: "PERMISSION_DENIED", message: "This role can view the canvas but cannot edit canvas content.", retryable: false })} /> : null}
          <KeyValue label="Cache key" value={cacheKey.join(" / ")} />
          <KeyValue label="Canvas id" value={canvasId} />
          <KeyValue label="Persisted content" value="Canvas.content JSONB shape" />
          <ErrorState title="Conflict placeholder" copy="A 409 response will show optimistic lock recovery instead of a generic error." requestId="req_mock_409" />
        </Panel>
      </div>
    </section>
  );
}

export function DashboardRoute() {
  const { workspaceSlug = "factory-floor", dashboardId = coreFixtureIds.dashboardOperations } = useParams();
  const outlet = useOutletContext<ShellOutletContext>();
  const cacheKey = createWorkspaceCacheKey(workspaceSlug, "dashboard", dashboardId, "24h");
  const canEditDashboard = canRole(outlet.activeRole, "dashboard:edit");

  return (
    <section className="feature-route" aria-labelledby="dashboard-route-title">
      <RouteHeading headingId="dashboard-route-title" kicker="Dashboard placeholder" title="12-column dashboard" copy="Widgets stay query-backed and isolated, with stale/loading/error states per widget." />
      <div className="dashboard-grid">
        <PaperSurface className="widget widget-wide" as="article">
          <span className="coord">dashboard/{dashboardId} / range/24h</span>
          <h2>Canvas events by type</h2>
          <div className="chart-bars" aria-label="Mock chart bars">
            <span className="bar bar-a" />
            <span className="bar bar-b" />
            <span className="bar bar-c" />
            <span className="bar bar-d" />
          </div>
        </PaperSurface>
        <PaperSurface className="widget" as="article">
          <LoadingState label="Widget query loading" />
        </PaperSurface>
        <Panel className="widget dark-widget" as="article">
          <StatusChip tone={canEditDashboard ? "healthy" : "warning"}>{canEditDashboard ? "editor controls available" : "viewer controls only"}</StatusChip>
          <p>{cacheKey.join(" / ")}</p>
        </Panel>
      </div>
    </section>
  );
}

function RouteHeading({ headingId, kicker, title, copy }: { headingId: string; kicker: string; title: string; copy: string }) {
  return (
    <div className="route-heading">
      <span className="kicker">{kicker}</span>
      <h1 id={headingId}>{title}</h1>
      <p>{copy}</p>
    </div>
  );
}

function KeyValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="key-value">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
