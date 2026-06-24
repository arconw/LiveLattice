import { Copy, LayoutDashboard, Plus, RotateCcw, Save, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useOutletContext, useParams } from "react-router-dom";
import { AppError } from "../../contracts/api-client";
import type { DashboardLayout, DashboardResponse, DashboardTimeRange, DashboardWidgetView, DataSourceView, QueryResult, WidgetDataView, WidgetPayload } from "../../contracts/dashboards";
import { addLayoutWidget, createDashboard, createDashboardCacheKey, createDataSource, createWidget, defaultDashboardLayout, defaultDashboardTimeRange, deleteDashboard, deleteDataSource, deleteWidget, duplicateDashboard, getDashboard, listDashboards, listDataSources, listWidgets, loadDashboardData, loadWidgetData, normalizeDashboardLayout, normalizeDataSourceResponse, normalizeQueryResult, normalizeTimeRange, normalizeWidgetResponse, testDataSourceConnection, updateDashboard, updateDataSource, updateWidget, widgetDataMap } from "../../contracts/dashboards";
import { canRole } from "../../contracts/workspaces";
import { Badge, Button, EmptyState, ErrorState, Input, LoadingState, Panel, PaperSurface, StatusChip } from "../../design-system/components";
import { useAuth } from "../auth/AuthProvider";
import type { ShellOutletContext } from "../shell/AppShell";
import { PermissionDeniedState, RouteAppErrorState } from "../workspaces/WorkspaceStates";
import { DashboardGrid } from "./DashboardGrid";
import { TimeRangeControl, WidgetEditor } from "./DashboardEditor";
import { DataSourceManager } from "./DataSourceManager";
import type { WidgetDataState } from "./WidgetRegistry";
import { WidgetTypeCoverage } from "./WidgetRegistry";

type DashboardView = Omit<DashboardResponse, "layout" | "timeRange"> & {
  layout: DashboardLayout;
  timeRange: DashboardTimeRange;
};

type RouteStatus = "idle" | "loading" | "ready" | "not_found" | "permission_denied" | "error";

export function DashboardListRoute() {
  const outlet = useOutletContext<ShellOutletContext>();
  const auth = useAuth();
  const client = auth.client;
  const navigate = useNavigate();
  const workspace = outlet.activeWorkspace;
  const canCreate = canRole(outlet.activeRole, "dashboard:create");
  const canManageDataSources = canRole(outlet.activeRole, "data_source:manage");
  const [status, setStatus] = useState<RouteStatus>("idle");
  const [error, setError] = useState<AppError | null>(null);
  const [dashboards, setDashboards] = useState<DashboardView[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceView[]>([]);

  const reload = useCallback(async () => {
    if (!workspace) {
      return;
    }

    setStatus("loading");
    setError(null);

    try {
      const [dashboardResponses, dataSourceResponses] = await Promise.all([listDashboards(client, workspace.id), listDataSources(client, workspace.id).catch(() => [])]);
      setDashboards(dashboardResponses.map(toDashboardView));
      setDataSources(dataSourceResponses.map(normalizeDataSourceResponse));
      setStatus("ready");
    } catch (loadError) {
      const appError = toAppError(loadError, "Dashboards could not be loaded.");
      setError(appError);
      setStatus(appError.status === 403 ? "permission_denied" : "error");
    }
  }, [client, workspace]);

  useEffect(() => {
    void reload();
  }, [reload, outlet.cacheSerial]);

  async function createDashboardFromForm(payload: { title: string; description: string }) {
    if (!workspace) {
      return;
    }

    const dashboard = await createDashboard(client, {
      workspaceId: workspace.id,
      title: payload.title,
      description: payload.description,
      layout: defaultDashboardLayout(),
      timeRange: defaultDashboardTimeRange(),
      autoRefresh: 0
    });
    outlet.pushToast("Dashboard created");
    navigate(`/w/${workspace.slug}/d/${dashboard.id}`);
  }

  async function createDataSourceFromPanel(payload: Parameters<typeof createDataSource>[1]) {
    await createDataSource(client, payload);
    outlet.pushToast("Data source secret saved and hidden");
    await reload();
  }

  async function updateDataSourceFromPanel(dataSourceId: string, payload: Parameters<typeof updateDataSource>[2]) {
    await updateDataSource(client, dataSourceId, payload);
    outlet.pushToast("Data source metadata updated");
    await reload();
  }

  async function deleteDataSourceFromPanel(dataSourceId: string) {
    await deleteDataSource(client, dataSourceId);
    outlet.pushToast("Data source deleted");
    await reload();
  }

  if (!workspace) {
    return <LoadingState label="Workspace dashboard context loading" />;
  }

  if (status === "loading" || status === "idle") {
    return <LoadingState label="Dashboard list loading" />;
  }

  if (status === "permission_denied") {
    return <PermissionDeniedState error={error} />;
  }

  if (status === "error" && error) {
    return <RouteAppErrorState error={error} onRetry={() => void reload()} />;
  }

  return (
    <section className="dashboard-route" aria-labelledby="dashboard-list-title">
      <div className="route-heading">
        <span className="kicker">Dashboards</span>
        <h1 id="dashboard-list-title">Analytics dashboards</h1>
        <p>Dashboard layouts stay pinned to the 12-column Core contract while widget data is loaded through Gateway-protected endpoints.</p>
      </div>

      <div className="dashboard-page-grid">
        <div className="dashboard-list-stack">
          <Panel className="dashboard-create-panel" as="section">
            <div className="panel-title-row">
              <div>
                <span className="kicker">Create dashboard</span>
                <h2>New 12-column board</h2>
              </div>
              <WidgetTypeCoverage />
            </div>
            <CreateDashboardForm canCreate={canCreate} onCreate={createDashboardFromForm} />
          </Panel>

          {dashboards.length === 0 ? (
            <EmptyState title="No dashboards yet" copy="Create a dashboard to add query-backed widgets, markdown notes, and time range controls." />
          ) : (
            <div className="dashboard-card-grid">
              {dashboards.map((dashboard) => (
                <PaperSurface className="dashboard-list-card" as="article" key={dashboard.id}>
                  <span className="coord">dashboard/{dashboard.id}</span>
                  <h2>{dashboard.title}</h2>
                  <p className="paper-copy">{dashboard.description || "No description"}</p>
                  <div className="dashboard-card-meta">
                    <Badge tone="info">{dashboard.layout.widgets.length} widgets</Badge>
                    <Badge tone={dashboard.autoRefresh > 0 ? "healthy" : "warning"}>{dashboard.autoRefresh > 0 ? `${dashboard.autoRefresh}s refresh` : "auto-refresh disabled"}</Badge>
                    <Badge tone="neutral">{dashboard.timeRange.type === "relative" ? dashboard.timeRange.value : "absolute"}</Badge>
                  </div>
                  <Link className="button button-primary" to={`/w/${workspace.slug}/d/${dashboard.id}`}>
                    <LayoutDashboard aria-hidden="true" size={16} />
                    <span>Open dashboard</span>
                  </Link>
                </PaperSurface>
              ))}
            </div>
          )}
        </div>

        <DataSourceManager workspaceId={workspace.id} dataSources={dataSources} canManage={canManageDataSources} onCreate={createDataSourceFromPanel} onUpdate={updateDataSourceFromPanel} onDelete={deleteDataSourceFromPanel} onTest={(dataSourceId) => testDataSourceConnection(client, dataSourceId)} />
      </div>
    </section>
  );
}

export function DashboardDetailRoute() {
  const outlet = useOutletContext<ShellOutletContext>();
  const auth = useAuth();
  const navigate = useNavigate();
  const { workspaceSlug = "", dashboardId = "" } = useParams();
  const workspace = outlet.activeWorkspace;
  const client = auth.client;
  const canEdit = canRole(outlet.activeRole, "dashboard:edit");
  const canCreate = canRole(outlet.activeRole, "dashboard:create");
  const canManageDataSources = canRole(outlet.activeRole, "data_source:manage");
  const [status, setStatus] = useState<RouteStatus>("idle");
  const [error, setError] = useState<AppError | null>(null);
  const [dashboard, setDashboard] = useState<DashboardView | null>(null);
  const [layout, setLayout] = useState<DashboardLayout>(defaultDashboardLayout());
  const [widgets, setWidgets] = useState<DashboardWidgetView[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceView[]>([]);
  const [widgetData, setWidgetData] = useState<Map<string, WidgetDataView>>(new Map());
  const [dataLoading, setDataLoading] = useState(false);
  const [dataError, setDataError] = useState<AppError | null>(null);
  const [mode, setMode] = useState<"view" | "edit">("view");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingWidget, setEditingWidget] = useState<DashboardWidgetView | null>(null);
  const [settingsTitle, setSettingsTitle] = useState("");
  const [settingsDescription, setSettingsDescription] = useState("");
  const [paused, setPaused] = useState(false);

  const refreshData = useCallback(async () => {
    if (!dashboardId) {
      return;
    }

    setDataLoading(true);
    setDataError(null);

    try {
      const response = await loadDashboardData(client, dashboardId);
      setWidgetData(widgetDataMap(response));
    } catch (loadError) {
      const appError = toAppError(loadError, "Dashboard data could not be loaded.");
      setDataError(appError);
    } finally {
      setDataLoading(false);
    }
  }, [client, dashboardId]);

  const reload = useCallback(async () => {
    if (!dashboardId) {
      return;
    }

    setStatus("loading");
    setError(null);

    try {
      const [dashboardResponse, widgetResponses, dataSourceResponses] = await Promise.all([getDashboard(client, dashboardId), listWidgets(client, dashboardId), workspace ? listDataSources(client, workspace.id).catch(() => []) : Promise.resolve([])]);
      const nextDashboard = toDashboardView(dashboardResponse);
      const nextWidgets = widgetResponses.map(normalizeWidgetResponse);
      setDashboard(nextDashboard);
      setLayout(layoutWithWidgetFallback(nextDashboard.layout, nextWidgets));
      setWidgets(nextWidgets);
      setDataSources(dataSourceResponses.map(normalizeDataSourceResponse));
      setSettingsTitle(nextDashboard.title);
      setSettingsDescription(nextDashboard.description ?? "");
      setStatus("ready");
      void refreshData();
    } catch (loadError) {
      const appError = toAppError(loadError, "Dashboard could not be loaded.");
      setError(appError);
      setStatus(appError.status === 404 ? "not_found" : appError.status === 403 ? "permission_denied" : "error");
    }
  }, [client, dashboardId, refreshData, workspace]);

  useEffect(() => {
    void reload();
  }, [reload, outlet.cacheSerial]);

  useEffect(() => {
    if (!dashboard || dashboard.autoRefresh <= 0 || paused) {
      return;
    }

    const interval = window.setInterval(() => {
      void refreshData();
    }, dashboard.autoRefresh * 1000);

    return () => window.clearInterval(interval);
  }, [dashboard, paused, refreshData]);

  const widgetStates = useMemo(() => {
    const states = new Map<string, WidgetDataState>();
    const timeRange = dashboard?.timeRange ?? defaultDashboardTimeRange();
    const workspaceId = workspace?.id ?? workspaceSlug;

    widgets.forEach((widget) => {
      const view = widgetData.get(widget.id);
      states.set(widget.id, {
        result: view?.result ?? null,
        error: view?.error ?? dataError?.message ?? null,
        loading: dataLoading && !view,
        stale: dataLoading && Boolean(view),
        cacheKey: createDashboardCacheKey(workspaceId, dashboardId, timeRange, widget.id)
      });
    });

    return states;
  }, [dashboard?.timeRange, dashboardId, dataError, dataLoading, widgetData, widgets, workspace?.id, workspaceSlug]);

  async function saveSettings(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!dashboard) {
      return;
    }

    const updated = toDashboardView(
      await updateDashboard(client, dashboard.id, {
        title: settingsTitle.trim(),
        description: settingsDescription.trim()
      })
    );
    setDashboard(updated);
    outlet.pushToast("Dashboard settings saved");
  }

  async function saveLayout() {
    if (!dashboard) {
      return;
    }

    const updated = toDashboardView(
      await updateDashboard(client, dashboard.id, {
        layout
      })
    );
    setDashboard(updated);
    setLayout(layoutWithWidgetFallback(updated.layout, widgets));
    outlet.pushToast("Dashboard layout saved");
  }

  async function saveTimeRange(timeRange: DashboardTimeRange, autoRefresh: number) {
    if (!dashboard) {
      return;
    }

    const updated = toDashboardView(
      await updateDashboard(client, dashboard.id, {
        timeRange,
        autoRefresh
      })
    );
    setDashboard(updated);
    setPaused(autoRefresh === 0 ? true : paused);
    await refreshData();
  }

  async function saveWidget(payload: WidgetPayload, widgetId?: string) {
    if (!dashboard) {
      return;
    }

    if (widgetId) {
      const updatedWidget = normalizeWidgetResponse(await updateWidget(client, dashboard.id, widgetId, payload));
      const nextWidgets = widgets.map((widget) => (widget.id === widgetId ? updatedWidget : widget));
      const nextLayout = addLayoutWidget(layout, { ...payload.position, widgetId });
      setWidgets(nextWidgets);
      setLayout(nextLayout);
      await updateDashboard(client, dashboard.id, { layout: nextLayout });
    } else {
      const createdWidget = normalizeWidgetResponse(await createWidget(client, dashboard.id, payload));
      const nextWidgets = [...widgets, createdWidget];
      const nextLayout = addLayoutWidget(layout, { ...payload.position, widgetId: createdWidget.id });
      setWidgets(nextWidgets);
      setLayout(nextLayout);
      await updateDashboard(client, dashboard.id, { layout: nextLayout });
    }

    setEditorOpen(false);
    setEditingWidget(null);
    outlet.pushToast("Widget saved");
    await refreshData();
  }

  async function removeWidget(widget: DashboardWidgetView) {
    if (!dashboard || !window.confirm(`Delete widget ${widget.title}?`)) {
      return;
    }

    await deleteWidget(client, dashboard.id, widget.id);
    const nextWidgets = widgets.filter((candidate) => candidate.id !== widget.id);
    const nextLayout = { ...layout, widgets: layout.widgets.filter((candidate) => candidate.widgetId !== widget.id) };
    setWidgets(nextWidgets);
    setLayout(nextLayout);
    await updateDashboard(client, dashboard.id, { layout: nextLayout });
    setEditorOpen(false);
    setEditingWidget(null);
    outlet.pushToast("Widget deleted");
  }

  async function duplicateCurrentDashboard() {
    if (!dashboard) {
      return;
    }

    const duplicated = await duplicateDashboard(client, dashboard.id);
    outlet.pushToast("Dashboard duplicated");
    navigate(`/w/${workspaceSlug}/d/${duplicated.id}`);
  }

  async function deleteCurrentDashboard() {
    if (!dashboard || !window.confirm(`Delete dashboard ${dashboard.title}?`)) {
      return;
    }

    await deleteDashboard(client, dashboard.id);
    outlet.pushToast("Dashboard deleted");
    navigate(`/w/${workspaceSlug}/d`);
  }

  async function refreshWidget(widget: DashboardWidgetView) {
    if (!dashboard) {
      return;
    }

    try {
      const response = await loadWidgetData(client, dashboard.id, widget.id);
      const nextMap = new Map(widgetData);
      const view = widgetDataMap(response).get(widget.id);

      if (view) {
        nextMap.set(widget.id, view);
      }

      setWidgetData(nextMap);
    } catch (refreshError) {
      const appError = toAppError(refreshError, "Widget data could not be loaded.");
      const nextMap = new Map(widgetData);
      nextMap.set(widget.id, { widgetId: widget.id, result: null, error: appError.message });
      setWidgetData(nextMap);
    }
  }

  async function previewWidget(widget: DashboardWidgetView): Promise<QueryResult | null> {
    if (!dashboard) {
      return null;
    }

    const response = await loadWidgetData(client, dashboard.id, widget.id);
    const view = widgetDataMap(response).get(widget.id);
    return view?.result ?? normalizeQueryResult(null);
  }

  async function createDataSourceFromPanel(payload: Parameters<typeof createDataSource>[1]) {
    await createDataSource(client, payload);
    outlet.pushToast("Data source secret saved and hidden");
    await reload();
  }

  async function updateDataSourceFromPanel(dataSourceId: string, payload: Parameters<typeof updateDataSource>[2]) {
    await updateDataSource(client, dataSourceId, payload);
    outlet.pushToast("Data source metadata updated");
    await reload();
  }

  async function deleteDataSourceFromPanel(dataSourceId: string) {
    await deleteDataSource(client, dataSourceId);
    outlet.pushToast("Data source deleted");
    await reload();
  }

  if (status === "loading" || status === "idle") {
    return <LoadingState label="Dashboard loading" />;
  }

  if (status === "permission_denied") {
    return <PermissionDeniedState error={error} />;
  }

  if (status === "not_found") {
    return <DashboardNotFoundState dashboardId={dashboardId} workspaceSlug={workspaceSlug} />;
  }

  if (status === "error" && error) {
    return <RouteAppErrorState error={error} onRetry={() => void reload()} />;
  }

  if (!dashboard || !workspace) {
    return <DashboardNotFoundState dashboardId={dashboardId} workspaceSlug={workspaceSlug} />;
  }

  return (
    <section className="dashboard-route" aria-labelledby="dashboard-detail-title">
      <div className="dashboard-detail-heading">
        <div className="route-heading compact">
          <span className="kicker">Dashboard detail</span>
          <h1 id="dashboard-detail-title">{dashboard.title}</h1>
          <p>{dashboard.description || "No description"}</p>
        </div>
        <div className="dashboard-toolbar" role="toolbar" aria-label="Dashboard actions">
          <Link className="button button-secondary" to={`/w/${workspace.slug}/d`}>
            All dashboards
          </Link>
          <Button variant={mode === "edit" ? "primary" : "secondary"} disabled={!canEdit} onClick={() => setMode(mode === "edit" ? "view" : "edit")}>
            {mode === "edit" ? "View mode" : "Edit mode"}
          </Button>
          <Button variant="secondary" icon={<RotateCcw aria-hidden="true" size={14} />} onClick={() => void refreshData()}>
            Refresh data
          </Button>
          <Button variant="secondary" icon={<Copy aria-hidden="true" size={14} />} disabled={!canCreate} onClick={() => void duplicateCurrentDashboard()}>
            Duplicate
          </Button>
          <Button variant="ghost" icon={<Trash2 aria-hidden="true" size={14} />} disabled={!canEdit} onClick={() => void deleteCurrentDashboard()}>
            Delete
          </Button>
        </div>
      </div>

      <div className="dashboard-page-grid">
        <div className="dashboard-main-stack">
          {dataError ? (
            <div className="form-alert form-alert-danger" role="status">
              Dashboard data endpoint failed. Existing dashboard metadata remains available and widgets show query errors individually.
            </div>
          ) : null}

          {widgets.length === 0 ? (
            <EmptyState
              title="No widgets yet"
              copy="Add a query-backed widget or markdown note to populate this dashboard."
              action={
                <Button
                  variant="primary"
                  icon={<Plus aria-hidden="true" size={16} />}
                  disabled={!canEdit}
                  onClick={() => {
                    setEditingWidget(null);
                    setEditorOpen(true);
                  }}
                >
                  Add widget
                </Button>
              }
            />
          ) : (
            <DashboardGrid
              layout={layout}
              widgets={widgets}
              widgetStates={widgetStates}
              editable={mode === "edit" && canEdit}
              onLayoutChange={setLayout}
              onEditWidget={(widget) => {
                setEditingWidget(widget);
                setEditorOpen(true);
              }}
              onRefreshWidget={(widget) => void refreshWidget(widget)}
            />
          )}
        </div>

        <aside className="dashboard-side-stack">
          {mode === "edit" ? (
            <Panel className="dashboard-settings-panel" as="section">
              <div className="panel-title-row">
                <div>
                  <span className="kicker">Settings</span>
                  <h2>Dashboard editor</h2>
                </div>
                <StatusChip tone={canEdit ? "healthy" : "warning"}>{canEdit ? "editable" : "viewer"}</StatusChip>
              </div>
              <form className="dashboard-settings-form" onSubmit={saveSettings}>
                <div className="form-field">
                  <label className="field-label" htmlFor="dashboard-title-input">
                    Title
                  </label>
                  <Input id="dashboard-title-input" value={settingsTitle} onChange={(event) => setSettingsTitle(event.target.value)} disabled={!canEdit} />
                </div>
                <div className="form-field">
                  <label className="field-label" htmlFor="dashboard-description-input">
                    Description
                  </label>
                  <textarea id="dashboard-description-input" className="textarea small-textarea" value={settingsDescription} onChange={(event) => setSettingsDescription(event.target.value)} disabled={!canEdit} />
                </div>
                <div className="form-action-row">
                  <Button variant="primary" type="submit" icon={<Save aria-hidden="true" size={14} />} disabled={!canEdit}>
                    Save settings
                  </Button>
                  <Button variant="secondary" icon={<Save aria-hidden="true" size={14} />} disabled={!canEdit} onClick={() => void saveLayout()}>
                    Save layout
                  </Button>
                </div>
              </form>
              <Button
                variant="secondary"
                icon={<Plus aria-hidden="true" size={14} />}
                disabled={!canEdit}
                onClick={() => {
                  setEditingWidget(null);
                  setEditorOpen(true);
                }}
              >
                Add widget
              </Button>
            </Panel>
          ) : null}

          {editorOpen ? <WidgetEditor widget={editingWidget} dataSources={dataSources} canEdit={canEdit} onSave={saveWidget} onDelete={removeWidget} onCancel={() => setEditorOpen(false)} onPreview={previewWidget} /> : null}

          <TimeRangeControl timeRange={dashboard.timeRange} autoRefresh={dashboard.autoRefresh} paused={paused} canEdit={canEdit} onPausedChange={setPaused} onApply={saveTimeRange} />

          <DataSourceManager workspaceId={workspace.id} dataSources={dataSources} canManage={canManageDataSources} onCreate={createDataSourceFromPanel} onUpdate={updateDataSourceFromPanel} onDelete={deleteDataSourceFromPanel} onTest={(dataSourceId) => testDataSourceConnection(client, dataSourceId)} />
        </aside>
      </div>
    </section>
  );
}

function CreateDashboardForm({ canCreate, onCreate }: { canCreate: boolean; onCreate: (payload: { title: string; description: string }) => Promise<void> }) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    if (!title.trim()) {
      setError("Title is required.");
      return;
    }

    setSubmitting(true);

    try {
      await onCreate({ title: title.trim(), description: description.trim() });
      setTitle("");
      setDescription("");
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Dashboard could not be created.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="dashboard-create-form" onSubmit={submit}>
      {error ? (
        <div className="form-alert form-alert-danger" role="alert">
          {error}
        </div>
      ) : null}
      <div className="form-field">
        <label className="field-label" htmlFor="dashboard-create-title">
          Title
        </label>
        <Input id="dashboard-create-title" value={title} onChange={(event) => setTitle(event.target.value)} disabled={!canCreate} />
      </div>
      <div className="form-field">
        <label className="field-label" htmlFor="dashboard-create-description">
          Description
        </label>
        <Input id="dashboard-create-description" value={description} onChange={(event) => setDescription(event.target.value)} disabled={!canCreate} />
      </div>
      <Button variant="primary" type="submit" icon={<Plus aria-hidden="true" size={14} />} disabled={!canCreate || submitting}>
        {submitting ? "Creating" : "Create dashboard"}
      </Button>
    </form>
  );
}

function DashboardNotFoundState({ dashboardId, workspaceSlug }: { dashboardId: string; workspaceSlug: string }) {
  return (
    <section className="route-state">
      <ErrorState title="Dashboard not found" copy={`No dashboard ${dashboardId} is available in workspace ${workspaceSlug}. It may have been deleted or you may have lost access.`} />
      <Link className="button button-secondary" to={`/w/${workspaceSlug}/d`}>
        Back to dashboards
      </Link>
    </section>
  );
}

function layoutWithWidgetFallback(layout: DashboardLayout, widgets: DashboardWidgetView[]) {
  return widgets.reduce((current, widget) => {
    if (current.widgets.some((item) => item.widgetId === widget.id)) {
      return current;
    }

    return addLayoutWidget(current, widget.position);
  }, layout);
}

function toDashboardView(response: DashboardResponse): DashboardView {
  return {
    ...response,
    layout: normalizeDashboardLayout(response.layout),
    timeRange: normalizeTimeRange(response.timeRange)
  };
}

function toAppError(error: unknown, fallbackMessage: string) {
  return error instanceof AppError
    ? error
    : new AppError({
        status: 0,
        code: "DASHBOARD_ROUTE_FAILED",
        message: fallbackMessage,
        retryable: true
      });
}
