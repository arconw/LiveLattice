import { Play, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { DashboardLayoutWidget, DashboardTimeRange, DashboardWidgetView, DataSourceView, QueryDsl, QueryResult, RelativeTimeRangeValue, WidgetPayload, WidgetType } from "../../contracts/dashboards";
import { defaultDashboardTimeRange, relativeTimeRangeValues, widgetTypes } from "../../contracts/dashboards";
import { Button, Input, Panel, Select, StatusChip } from "../../design-system/components";
import { QueryResultTable } from "./WidgetRegistry";

export function TimeRangeControl({ timeRange, autoRefresh, paused, canEdit, onPausedChange, onApply }: { timeRange: DashboardTimeRange; autoRefresh: number; paused: boolean; canEdit: boolean; onPausedChange: (paused: boolean) => void; onApply: (timeRange: DashboardTimeRange, autoRefresh: number) => Promise<void> }) {
  const [rangeType, setRangeType] = useState<DashboardTimeRange["type"]>(timeRange.type);
  const [relativeValue, setRelativeValue] = useState<RelativeTimeRangeValue>(timeRange.type === "relative" ? timeRange.value : "24h");
  const [start, setStart] = useState(timeRange.type === "absolute" ? timeRange.start : "");
  const [end, setEnd] = useState(timeRange.type === "absolute" ? timeRange.end : "");
  const [refresh, setRefresh] = useState(String(autoRefresh));
  const [status, setStatus] = useState("");

  useEffect(() => {
    setRangeType(timeRange.type);
    setRelativeValue(timeRange.type === "relative" ? timeRange.value : "24h");
    setStart(timeRange.type === "absolute" ? timeRange.start : "");
    setEnd(timeRange.type === "absolute" ? timeRange.end : "");
    setRefresh(String(autoRefresh));
  }, [autoRefresh, timeRange]);

  async function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextRefresh = Math.max(0, Number(refresh) || 0);
    const nextRange: DashboardTimeRange = rangeType === "absolute" && start && end ? { type: "absolute", value: null, start, end } : { type: "relative", value: relativeValue };
    await onApply(nextRange, nextRefresh);
    setStatus("Time range saved");
  }

  return (
    <Panel className="time-range-panel" as="section">
      <div className="panel-title-row">
        <div>
          <span className="kicker">Time range</span>
          <h2>Dashboard data window</h2>
        </div>
        {paused || autoRefresh === 0 ? <StatusChip tone="warning">auto-refresh paused</StatusChip> : <StatusChip tone="healthy">refresh {autoRefresh}s</StatusChip>}
      </div>
      <form className="time-range-form" onSubmit={apply}>
        <div className="segmented-control" role="group" aria-label="Time range mode">
          <button className={rangeType === "relative" ? "is-active" : ""} type="button" onClick={() => setRangeType("relative")}>
            Relative
          </button>
          <button className={rangeType === "absolute" ? "is-active" : ""} type="button" onClick={() => setRangeType("absolute")}>
            Absolute
          </button>
        </div>
        {rangeType === "relative" ? (
          <div className="form-field">
            <label className="field-label" htmlFor="dashboard-relative-range">
              Relative range
            </label>
            <Select id="dashboard-relative-range" value={relativeValue} onChange={(event) => setRelativeValue(event.target.value as RelativeTimeRangeValue)} disabled={!canEdit}>
              {relativeTimeRangeValues.map((value) => (
                <option value={value} key={value}>
                  {value}
                </option>
              ))}
            </Select>
          </div>
        ) : (
          <div className="form-field two-column-field">
            <span>
              <label className="field-label" htmlFor="dashboard-range-start">
                Start
              </label>
              <Input id="dashboard-range-start" type="datetime-local" value={start} onChange={(event) => setStart(event.target.value)} disabled={!canEdit} />
            </span>
            <span>
              <label className="field-label" htmlFor="dashboard-range-end">
                End
              </label>
              <Input id="dashboard-range-end" type="datetime-local" value={end} onChange={(event) => setEnd(event.target.value)} disabled={!canEdit} />
            </span>
          </div>
        )}
        <div className="form-field">
          <label className="field-label" htmlFor="dashboard-auto-refresh">
            Auto-refresh seconds
          </label>
          <Input id="dashboard-auto-refresh" inputMode="numeric" value={refresh} onChange={(event) => setRefresh(event.target.value)} disabled={!canEdit} />
        </div>
        <div className="form-action-row">
          <Button variant="primary" type="submit" disabled={!canEdit}>
            Apply
          </Button>
          <Button variant="ghost" onClick={() => onPausedChange(!paused)}>
            {paused ? "Resume" : "Pause"}
          </Button>
        </div>
        {status ? (
          <div className="widget-inline-status" role="status">
            {status}
          </div>
        ) : null}
      </form>
    </Panel>
  );
}

export function WidgetEditor({ widget, dataSources, canEdit, onSave, onDelete, onCancel, onPreview }: { widget: DashboardWidgetView | null; dataSources: DataSourceView[]; canEdit: boolean; onSave: (payload: WidgetPayload, widgetId?: string) => Promise<void>; onDelete: (widget: DashboardWidgetView) => Promise<void>; onCancel: () => void; onPreview: (widget: DashboardWidgetView) => Promise<QueryResult | null> }) {
  const [title, setTitle] = useState(widget?.title ?? "");
  const [type, setType] = useState<WidgetType>(widget?.type ?? "BAR_CHART");
  const [dataSourceId, setDataSourceId] = useState(widget?.dataSourceId ?? dataSources[0]?.id ?? "");
  const [queryText, setQueryText] = useState(formatJson(widget?.query ?? defaultQueryForType(widget?.type ?? "BAR_CHART")));
  const [optionsText, setOptionsText] = useState(formatJson(widget?.options ?? defaultOptionsForType(widget?.type ?? "BAR_CHART")));
  const [position, setPosition] = useState<DashboardLayoutWidget>(widget?.position ?? { widgetId: "new-widget", x: 0, y: 0, w: 4, h: 4 });
  const [error, setError] = useState("");
  const [preview, setPreview] = useState<QueryResult | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const editing = Boolean(widget);

  useEffect(() => {
    setTitle(widget?.title ?? "");
    setType(widget?.type ?? "BAR_CHART");
    setDataSourceId(widget?.dataSourceId ?? dataSources[0]?.id ?? "");
    setQueryText(formatJson(widget?.query ?? defaultQueryForType(widget?.type ?? "BAR_CHART")));
    setOptionsText(formatJson(widget?.options ?? defaultOptionsForType(widget?.type ?? "BAR_CHART")));
    setPosition(widget?.position ?? { widgetId: "new-widget", x: 0, y: 0, w: 4, h: 4 });
    setError("");
    setPreview(null);
  }, [dataSources, widget]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    if (!canEdit) {
      setError("Your role cannot edit widgets.");
      return;
    }

    if (!title.trim()) {
      setError("Title is required.");
      return;
    }

    if (type !== "MARKDOWN" && !dataSourceId) {
      setError("Data source is required for query-backed widgets.");
      return;
    }

    let query: QueryDsl;
    let options: Record<string, unknown>;

    try {
      query = JSON.parse(queryText) as QueryDsl;
      options = JSON.parse(optionsText) as Record<string, unknown>;
    } catch {
      setError("Query and options must be valid JSON.");
      return;
    }

    await onSave(
      {
        type,
        title: title.trim(),
        dataSourceId: type === "MARKDOWN" ? null : dataSourceId,
        query,
        options,
        position
      },
      widget?.id
    );
  }

  async function previewWidget() {
    if (!widget) {
      return;
    }

    setPreviewing(true);
    setError("");

    try {
      setPreview(await onPreview(widget));
    } catch (previewError) {
      setError(previewError instanceof Error ? previewError.message : "Preview failed.");
    } finally {
      setPreviewing(false);
    }
  }

  return (
    <Panel className="widget-editor-panel" as="section">
      <div className="panel-title-row">
        <div>
          <span className="kicker">{editing ? "Edit widget" : "Add widget"}</span>
          <h2>{editing ? widget?.title : "New widget"}</h2>
        </div>
        {editing ? <StatusChip tone="info">{widget?.type}</StatusChip> : null}
      </div>
      {error ? (
        <div className="form-alert form-alert-danger" role="alert">
          {error}
        </div>
      ) : null}
      <form className="widget-editor-form" onSubmit={save}>
        <div className="form-field">
          <label className="field-label" htmlFor="widget-title">
            Title
          </label>
          <Input id="widget-title" value={title} onChange={(event) => setTitle(event.target.value)} disabled={!canEdit} />
        </div>
        <div className="form-field two-column-field">
          <span>
            <label className="field-label" htmlFor="widget-type">
              Type
            </label>
            <Select id="widget-type" value={type} onChange={(event) => setType(event.target.value as WidgetType)} disabled={!canEdit}>
              {widgetTypes.map((candidate) => (
                <option value={candidate} key={candidate}>
                  {candidate}
                </option>
              ))}
            </Select>
          </span>
          <span>
            <label className="field-label" htmlFor="widget-data-source">
              Data source
            </label>
            <Select id="widget-data-source" value={dataSourceId} onChange={(event) => setDataSourceId(event.target.value)} disabled={!canEdit || type === "MARKDOWN"}>
              <option value="">No data source</option>
              {dataSources.map((dataSource) => (
                <option value={dataSource.id} key={dataSource.id}>
                  {dataSource.name}
                </option>
              ))}
            </Select>
          </span>
        </div>
        <fieldset className="position-fieldset">
          <legend className="field-label">Position</legend>
          {(["x", "y", "w", "h"] as const).map((field) => (
            <span key={field}>
              <label className="field-label" htmlFor={`widget-position-${field}`}>
                {field}
              </label>
              <Input id={`widget-position-${field}`} inputMode="numeric" value={position[field]} onChange={(event) => setPosition((current) => ({ ...current, [field]: Number(event.target.value) || 0 }))} disabled={!canEdit} />
            </span>
          ))}
        </fieldset>
        <div className="form-field">
          <label className="field-label" htmlFor="widget-query">
            Query DSL
          </label>
          <textarea id="widget-query" className="textarea" value={queryText} onChange={(event) => setQueryText(event.target.value)} disabled={!canEdit} />
        </div>
        <div className="form-field">
          <label className="field-label" htmlFor="widget-options">
            Display options
          </label>
          <textarea id="widget-options" className="textarea" value={optionsText} onChange={(event) => setOptionsText(event.target.value)} disabled={!canEdit} />
        </div>
        <div className="form-action-row">
          <Button variant="primary" type="submit" disabled={!canEdit}>
            Save widget
          </Button>
          <Button variant="secondary" icon={<Play aria-hidden="true" size={14} />} onClick={() => void previewWidget()} disabled={!widget}>
            {previewing ? "Previewing" : "Preview"}
          </Button>
          {widget ? (
            <Button variant="ghost" icon={<Trash2 aria-hidden="true" size={14} />} disabled={!canEdit} onClick={() => void onDelete(widget)}>
              Delete
            </Button>
          ) : null}
          <Button variant="ghost" onClick={onCancel}>
            Close
          </Button>
        </div>
      </form>
      {preview ? <QueryResultTable result={preview} label="Query preview result" /> : null}
    </Panel>
  );
}

export function dashboardTimeRangeOrDefault(value: DashboardTimeRange | null | undefined) {
  return value ?? defaultDashboardTimeRange();
}

function defaultQueryForType(type: WidgetType): QueryDsl {
  if (type === "MARKDOWN") {
    return {
      markdown: "### Notes"
    };
  }

  return {
    metrics: [{ expression: "count(*)", alias: "count" }],
    dimensions: [],
    filters: [],
    order_by: [{ field: "count", direction: "DESC" }],
    limit: 50
  };
}

function defaultOptionsForType(type: WidgetType): Record<string, unknown> {
  if (type === "STAT") {
    return {
      decimalPlaces: 0
    };
  }

  return {
    showLegend: type !== "TABLE" && type !== "MARKDOWN",
    colorScheme: "default"
  };
}

function formatJson(value: unknown) {
  return JSON.stringify(value, null, 2);
}
