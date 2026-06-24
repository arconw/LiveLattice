import { GripHorizontal, Maximize2 } from "lucide-react";
import { useMemo, useRef } from "react";
import type { CSSProperties, KeyboardEvent, PointerEvent } from "react";
import type { DashboardLayout, DashboardLayoutWidget, DashboardWidgetView } from "../../contracts/dashboards";
import { moveLayoutWidget, replaceLayoutWidget, resizeLayoutWidget } from "../../contracts/dashboards";
import { Panel } from "../../design-system/components";
import { WidgetCard } from "./WidgetRegistry";
import type { WidgetDataState } from "./WidgetRegistry";

type DashboardGridProps = {
  layout: DashboardLayout;
  widgets: DashboardWidgetView[];
  widgetStates: Map<string, WidgetDataState>;
  editable: boolean;
  onLayoutChange: (layout: DashboardLayout) => void;
  onEditWidget: (widget: DashboardWidgetView) => void;
  onRefreshWidget: (widget: DashboardWidgetView) => void;
};

type DragState = {
  mode: "move" | "resize";
  startX: number;
  startY: number;
  widget: DashboardLayoutWidget;
  layout: DashboardLayout;
};

export function DashboardGrid({ layout, widgets, widgetStates, editable, onLayoutChange, onEditWidget, onRefreshWidget }: DashboardGridProps) {
  const gridRef = useRef<HTMLDivElement | null>(null);
  const dragRef = useRef<DragState | null>(null);
  const widgetsById = useMemo(() => new Map(widgets.map((widget) => [widget.id, widget])), [widgets]);
  const placedIds = useMemo(() => new Set(layout.widgets.map((widget) => widget.widgetId)), [layout.widgets]);
  const unplacedWidgets = widgets.filter((widget) => !placedIds.has(widget.id));
  const sortedLayout = [...layout.widgets].sort((first, second) => first.y - second.y || first.x - second.x);

  function startPointer(event: PointerEvent<HTMLButtonElement>, item: DashboardLayoutWidget, mode: DragState["mode"]) {
    if (!editable) {
      return;
    }

    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      mode,
      startX: event.clientX,
      startY: event.clientY,
      widget: item,
      layout
    };
  }

  function handlePointerMove(event: PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;

    if (!drag || !editable) {
      return;
    }

    const rect = gridRef.current?.getBoundingClientRect();
    const columnWidth = rect && rect.width > 0 ? rect.width / layout.columns : 64;
    const rowHeight = 64;
    const deltaX = Math.round((event.clientX - drag.startX) / columnWidth);
    const deltaY = Math.round((event.clientY - drag.startY) / rowHeight);

    if (deltaX === 0 && deltaY === 0) {
      return;
    }

    const nextWidget =
      drag.mode === "move"
        ? {
            ...drag.widget,
            x: drag.widget.x + deltaX,
            y: drag.widget.y + deltaY
          }
        : {
            ...drag.widget,
            w: drag.widget.w + deltaX,
            h: drag.widget.h + deltaY
          };

    onLayoutChange(replaceLayoutWidget(drag.layout, nextWidget));
  }

  function handlePointerUp(event: PointerEvent<HTMLButtonElement>) {
    if (dragRef.current) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }

    dragRef.current = null;
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>, item: DashboardLayoutWidget) {
    if (!editable || !["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)) {
      return;
    }

    event.preventDefault();
    const horizontal = event.key === "ArrowLeft" ? -1 : event.key === "ArrowRight" ? 1 : 0;
    const vertical = event.key === "ArrowUp" ? -1 : event.key === "ArrowDown" ? 1 : 0;
    const nextLayout = event.shiftKey ? resizeLayoutWidget(layout, item.widgetId, horizontal, vertical) : moveLayoutWidget(layout, item.widgetId, horizontal, vertical);
    onLayoutChange(nextLayout);
  }

  return (
    <div className="dashboard-grid-stack">
      <div className="dashboard-layout-grid" ref={gridRef} style={{ "--dashboard-grid-gap": `${layout.gap}px` } as CSSProperties} aria-label="Dashboard 12-column grid">
        {sortedLayout.map((item) => {
          const widget = widgetsById.get(item.widgetId);
          const state = widgetStates.get(item.widgetId);

          if (!widget || !state) {
            return (
              <article className="dashboard-grid-item dashboard-widget-card dashboard-widget-missing" key={item.widgetId} style={gridItemStyle(item)}>
                <span className="coord">widget/{item.widgetId}</span>
                <h2>Widget metadata unavailable</h2>
                <p className="small-copy">The dashboard layout references a widget that was not returned by Core.</p>
              </article>
            );
          }

          return (
            <div className="dashboard-grid-item" key={item.widgetId} style={gridItemStyle(item)} onKeyDown={(event) => handleKeyDown(event, item)} tabIndex={editable ? 0 : -1} aria-label={`${widget.title} grid item`} aria-keyshortcuts="ArrowLeft ArrowRight ArrowUp ArrowDown Shift+ArrowLeft Shift+ArrowRight Shift+ArrowUp Shift+ArrowDown">
              {editable ? (
                <div className="widget-grid-controls">
                  <button className="widget-grid-button" type="button" title={`Move ${widget.title}`} aria-label={`Move ${widget.title}`} onPointerDown={(event) => startPointer(event, item, "move")} onPointerMove={handlePointerMove} onPointerUp={handlePointerUp}>
                    <GripHorizontal aria-hidden="true" size={16} />
                  </button>
                  <button className="widget-grid-button" type="button" title={`Resize ${widget.title}`} aria-label={`Resize ${widget.title}`} onPointerDown={(event) => startPointer(event, item, "resize")} onPointerMove={handlePointerMove} onPointerUp={handlePointerUp}>
                    <Maximize2 aria-hidden="true" size={16} />
                  </button>
                </div>
              ) : null}
              <WidgetCard widget={widget} state={state} editable={editable} onEdit={() => onEditWidget(widget)} onRefresh={() => onRefreshWidget(widget)} />
            </div>
          );
        })}
      </div>

      {unplacedWidgets.length > 0 ? (
        <Panel className="unplaced-widget-panel" as="aside">
          <span className="kicker">Unplaced widgets</span>
          {unplacedWidgets.map((widget) => (
            <div className="result-line" key={widget.id}>
              <strong>{widget.title}</strong>
              <span>{widget.type}</span>
            </div>
          ))}
        </Panel>
      ) : null}
    </div>
  );
}

function gridItemStyle(item: DashboardLayoutWidget) {
  return {
    gridColumn: `${item.x + 1} / span ${item.w}`,
    gridRow: `${item.y + 1} / span ${item.h}`,
    "--widget-row-span": item.h
  } as CSSProperties;
}
