import { AlertTriangle, RefreshCw } from "lucide-react";
import type { CSSProperties, ReactNode } from "react";
import type { DashboardWidgetView, QueryColumn, QueryResult, WidgetType } from "../../contracts/dashboards";
import { emptyQueryResult, widgetTypes } from "../../contracts/dashboards";
import { Badge, Button, LoadingState, StatusChip } from "../../design-system/components";

export type WidgetDataState = {
  result: QueryResult | null;
  loading: boolean;
  stale: boolean;
  error: string | null;
  cacheKey: readonly unknown[];
};

export type WidgetRenderProps = {
  widget: DashboardWidgetView;
  result: QueryResult;
};

export const widgetRegistry: Record<WidgetType, (props: WidgetRenderProps) => ReactNode> = {
  BAR_CHART: BarChartWidget,
  LINE_CHART: LineChartWidget,
  PIE_CHART: PieChartWidget,
  TABLE: TableWidget,
  STAT: StatWidget,
  HEATMAP: HeatmapWidget,
  MARKDOWN: MarkdownWidget
};

export function WidgetCard({ widget, state, editable, onEdit, onRefresh }: { widget: DashboardWidgetView; state: WidgetDataState; editable: boolean; onEdit: () => void; onRefresh: () => void }) {
  const titleId = `widget-${widget.id}-title`;
  const result = state.result ?? emptyQueryResult();
  const warning = state.result?.meta.warning;

  return (
    <article className="dashboard-widget-card" aria-labelledby={titleId}>
      <div className="widget-card-header">
        <div>
          <span className="coord">widget/{widget.id}</span>
          <h2 id={titleId}>{widget.title}</h2>
        </div>
        <div className="widget-card-actions">
          <Badge tone="info">{widget.type}</Badge>
          {state.stale ? <StatusChip tone="warning">stale</StatusChip> : null}
          {warning ? <StatusChip tone="warning">truncated</StatusChip> : null}
          {state.error ? <StatusChip tone="danger">query failed</StatusChip> : null}
        </div>
      </div>

      <div className="widget-card-body">
        {state.loading && !state.result ? <LoadingState label="Widget loading" /> : null}
        {state.loading && state.result ? (
          <div className="widget-inline-status" role="status">
            Refreshing cached data
          </div>
        ) : null}
        {state.error ? <WidgetError message={state.error} onRefresh={onRefresh} /> : null}
        {!state.loading && !state.error ? widgetRegistry[widget.type]({ widget, result }) : null}
        {warning ? (
          <div className="widget-warning" role="status">
            <AlertTriangle aria-hidden="true" size={16} />
            <span>{warning}</span>
          </div>
        ) : null}
      </div>

      <div className="widget-card-footer">
        <span className="utility-text">rows {result.meta.totalRows}</span>
        <span className="utility-text">{result.meta.executedAt ? `executed ${formatDateTime(result.meta.executedAt)}` : "not executed"}</span>
        <span className="utility-text">{state.cacheKey.join(" / ")}</span>
        <Button variant="ghost" icon={<RefreshCw aria-hidden="true" size={14} />} onClick={onRefresh}>
          Refresh
        </Button>
        <Button variant="ghost" onClick={onEdit} disabled={!editable}>
          Edit
        </Button>
      </div>
    </article>
  );
}

export function QueryResultTable({ result, label = "Query result" }: { result: QueryResult; label?: string }) {
  if (result.rows.length === 0) {
    return (
      <div className="query-empty-state" role="status">
        Empty result
      </div>
    );
  }

  const columns: QueryColumn[] = result.columns.length > 0 ? result.columns : result.rows[0].map((_, index) => ({ name: `column_${index + 1}` }));

  return (
    <div className="query-table-wrap">
      <table className="query-result-table">
        <caption>{label}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th scope="col" key={column.name}>
                {column.name}
                {column.type ? <span>{column.type}</span> : null}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.slice(0, 50).map((row, rowIndex) => (
            <tr key={rowIndex}>
              {columns.map((column, columnIndex) => (
                <td key={`${rowIndex}-${column.name}`}>{formatCell(row[columnIndex])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function WidgetTypeCoverage() {
  return (
    <div className="widget-type-coverage">
      {widgetTypes.map((type) => (
        <Badge tone="info" key={type}>
          {type}
        </Badge>
      ))}
    </div>
  );
}

function WidgetError({ message, onRefresh }: { message: string; onRefresh: () => void }) {
  return (
    <div className="widget-error-state" role="alert">
      <AlertTriangle aria-hidden="true" size={18} />
      <div>
        <strong>Widget query failed</strong>
        <p>{message}</p>
      </div>
      <Button variant="secondary" icon={<RefreshCw aria-hidden="true" size={14} />} onClick={onRefresh}>
        Retry
      </Button>
    </div>
  );
}

function BarChartWidget({ result }: WidgetRenderProps) {
  const rows = numericRows(result);

  if (rows.length === 0) {
    return <QueryResultTable result={result} label="Bar chart source data" />;
  }

  const max = Math.max(...rows.map((row) => row.value), 1);

  return (
    <div className="chart-with-summary">
      <div className="dashboard-bar-chart" aria-hidden="true">
        {rows.map((row) => (
          <span style={{ height: `${Math.max(8, (row.value / max) * 100)}%` }} key={row.label} />
        ))}
      </div>
      <QueryResultTable result={result} label="Bar chart data table" />
    </div>
  );
}

function LineChartWidget({ result }: WidgetRenderProps) {
  const rows = numericRows(result);

  if (rows.length < 2) {
    return <QueryResultTable result={result} label="Line chart source data" />;
  }

  return (
    <div className="chart-with-summary">
      <svg className="dashboard-line-chart" viewBox="0 0 320 120" role="img" aria-label="Line chart preview">
        <path d={linePath(rows.map((row) => row.value))} />
      </svg>
      <QueryResultTable result={result} label="Line chart data table" />
    </div>
  );
}

function PieChartWidget({ result }: WidgetRenderProps) {
  const rows = numericRows(result);
  const total = rows.reduce((sum, row) => sum + row.value, 0);

  if (total <= 0) {
    return <QueryResultTable result={result} label="Pie chart source data" />;
  }

  let cursor = 0;
  const colors = ["var(--color-crdt-blue)", "var(--color-event-amber)", "var(--color-snapshot-mint)", "var(--color-index-violet)", "var(--color-conflict-pink)"];
  const segments = rows.slice(0, 6).map((row, index) => {
    const start = cursor;
    const end = cursor + (row.value / total) * 100;
    cursor = end;
    return `${colors[index % colors.length]} ${start}% ${end}%`;
  });

  return (
    <div className="dashboard-pie-layout">
      <span className="dashboard-pie" style={{ "--pie-segments": segments.join(", ") } as CSSProperties} aria-hidden="true" />
      <QueryResultTable result={result} label="Pie chart data table" />
    </div>
  );
}

function TableWidget({ result }: WidgetRenderProps) {
  return <QueryResultTable result={result} label="Table widget data" />;
}

function StatWidget({ result, widget }: WidgetRenderProps) {
  const first = numericRows(result)[0];
  const decimalPlaces = typeof widget.options.decimalPlaces === "number" ? widget.options.decimalPlaces : 0;
  const value = first ? first.value.toLocaleString(undefined, { maximumFractionDigits: decimalPlaces, minimumFractionDigits: decimalPlaces }) : "0";

  return (
    <div className="dashboard-stat-widget">
      <strong>{value}</strong>
      <span>{first?.label ?? "current value"}</span>
      <QueryResultTable result={result} label="Stat widget data table" />
    </div>
  );
}

function HeatmapWidget({ result }: WidgetRenderProps) {
  const rows = numericRows(result);
  const max = Math.max(...rows.map((row) => row.value), 1);

  if (rows.length === 0) {
    return <QueryResultTable result={result} label="Heatmap source data" />;
  }

  return (
    <div className="chart-with-summary">
      <div className="dashboard-heatmap" aria-hidden="true">
        {rows.slice(0, 48).map((row) => (
          <span style={{ opacity: Math.max(0.22, row.value / max) }} key={row.label} />
        ))}
      </div>
      <QueryResultTable result={result} label="Heatmap data table" />
    </div>
  );
}

function MarkdownWidget({ widget }: WidgetRenderProps) {
  const text = typeof widget.query.markdown === "string" ? widget.query.markdown : typeof widget.options.markdown === "string" ? widget.options.markdown : "Markdown widget";

  return <div className="dashboard-markdown-widget">{text}</div>;
}

function numericRows(result: QueryResult) {
  return result.rows.flatMap((row, index) => {
    const valueIndex = row.findIndex((cell) => typeof cell === "number");

    if (valueIndex === -1) {
      return [];
    }

    return [
      {
        label: labelForRow(result, row, index, valueIndex),
        value: Number(row[valueIndex])
      }
    ];
  });
}

function labelForRow(result: QueryResult, row: unknown[], index: number, valueIndex: number) {
  const labelValue = row.find((cell, cellIndex) => cellIndex !== valueIndex && (typeof cell === "string" || typeof cell === "number"));

  if (labelValue !== undefined) {
    return String(labelValue);
  }

  return result.columns[index]?.name ?? `row ${index + 1}`;
}

function linePath(values: number[]) {
  const max = Math.max(...values, 1);
  const min = Math.min(...values, 0);
  const range = Math.max(max - min, 1);
  const points = values.map((value, index) => {
    const x = values.length === 1 ? 160 : (index / (values.length - 1)) * 304 + 8;
    const y = 112 - ((value - min) / range) * 96;
    return `${x},${y}`;
  });

  return `M${points.join(" L")}`;
}

function formatCell(value: unknown) {
  if (value === null || value === undefined) {
    return "";
  }

  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }

  return JSON.stringify(value);
}

function formatDateTime(value: string) {
  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString();
}
