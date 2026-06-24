import { History, ShieldCheck } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { useOutletContext, useParams, useSearchParams } from "react-router-dom";
import { AppError, createWorkspaceCacheKey } from "../../contracts/api-client";
import { auditEventsFixture } from "../../contracts/fixtures";
import { listAuditEvents } from "../../contracts/audit";
import type { AuditEvent } from "../../contracts/audit";
import { canRole } from "../../contracts/workspaces";
import { Badge, Button, EmptyState, ErrorState, Input, LoadingState, Panel, StatusChip } from "../../design-system/components";
import type { ShellOutletContext } from "../shell/AppShell";
import { useAuth } from "../auth/AuthProvider";
import { PermissionDeniedState } from "../workspaces/WorkspaceStates";

type AuditLoadStatus = "loading" | "ready" | "empty" | "error";

export function AuditRoute() {
  const { workspaceSlug = "factory-floor" } = useParams();
  const outlet = useOutletContext<ShellOutletContext>();
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const workspaceId = outlet.activeWorkspace?.id ?? auditEventsFixture[0]?.workspaceId ?? workspaceSlug;
  const [events, setEvents] = useState<AuditEvent[]>(auditEventsFixture);
  const [status, setStatus] = useState<AuditLoadStatus>("ready");
  const [error, setError] = useState<AppError | null>(null);
  const filters = {
    actorId: searchParams.get("actor") ?? "",
    action: searchParams.get("action") ?? "",
    targetType: searchParams.get("target_type") ?? "",
    targetId: searchParams.get("target") ?? "",
    from: searchParams.get("from") ?? "",
    to: searchParams.get("to") ?? ""
  };
  const cacheKey = createWorkspaceCacheKey(workspaceSlug, "audit", filters.actorId, filters.action, filters.targetType, filters.targetId, filters.from, filters.to);
  const canViewAudit = canRole(outlet.activeRole, "audit:view");

  useEffect(() => {
    if (!canViewAudit) {
      return undefined;
    }

    const controller = new AbortController();
    setStatus("loading");

    listAuditEvents(
      auth.client,
      {
        workspaceId,
        actorId: filters.actorId || undefined,
        action: filters.action || undefined,
        targetType: filters.targetType || undefined,
        targetId: filters.targetId || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined
      },
      controller.signal
    )
      .then((response) => {
        setEvents(response.events);
        setStatus(response.events.length > 0 ? "ready" : "empty");
        setError(null);
      })
      .catch((loadError) => {
        if (loadError instanceof AppError && loadError.code === "REQUEST_ABORTED") {
          return;
        }

        setError(loadError instanceof AppError ? loadError : new AppError({ status: 0, code: "AUDIT_LOAD_FAILED", message: "Audit events could not be loaded.", retryable: true }));
        setStatus("error");
      });

    return () => controller.abort();
  }, [auth.client, canViewAudit, filters.action, filters.actorId, filters.from, filters.targetId, filters.targetType, filters.to, workspaceId]);

  const filteredEvents = useMemo(
    () =>
      events.filter((event) => {
        const actorMatches = filters.actorId ? event.actorId.includes(filters.actorId) || event.actorDisplay.toLowerCase().includes(filters.actorId.toLowerCase()) : true;
        const actionMatches = filters.action ? event.action.includes(filters.action) : true;
        const targetTypeMatches = filters.targetType ? event.targetType === filters.targetType : true;
        const targetMatches = filters.targetId ? event.targetId.includes(filters.targetId) : true;
        return actorMatches && actionMatches && targetTypeMatches && targetMatches;
      }),
    [events, filters.action, filters.actorId, filters.targetId, filters.targetType]
  );

  function applyFilters(nextFilters: typeof filters) {
    const next = new URLSearchParams(searchParams);
    setSearchParam(next, "actor", nextFilters.actorId);
    setSearchParam(next, "action", nextFilters.action);
    setSearchParam(next, "target_type", nextFilters.targetType);
    setSearchParam(next, "target", nextFilters.targetId);
    setSearchParam(next, "from", nextFilters.from);
    setSearchParam(next, "to", nextFilters.to);
    setSearchParams(next);
  }

  if (!canViewAudit) {
    return <PermissionDeniedState error={new AppError({ status: 403, code: "PERMISSION_DENIED", message: "Your workspace role cannot access audit events.", retryable: false })} />;
  }

  return (
    <section className="feature-route audit-route" aria-labelledby="audit-route-title">
      <div className="route-heading">
        <span className="kicker">Immutable workspace trail</span>
        <h1 id="audit-route-title">Audit trail</h1>
        <p>Audit events are read-only, workspace-scoped, and filterable by actor, action, target, and time.</p>
      </div>

      <div className="feature-grid audit-layout">
        <Panel className="audit-filter-panel" as="aside">
          <AuditFilters initialFilters={filters} onApply={applyFilters} />
          <div className="key-value">
            <span>Workspace</span>
            <strong>{workspaceSlug}</strong>
          </div>
          <div className="key-value">
            <span>Cache key</span>
            <strong>{cacheKey.join(" / ")}</strong>
          </div>
          <StatusChip tone="healthy">read-only</StatusChip>
        </Panel>

        <Panel className="audit-timeline-panel" as="section">
          <div className="panel-heading-row">
            <div>
              <span className="kicker">Timeline</span>
              <h2>Workspace-scoped events</h2>
            </div>
            <StatusChip tone="info">{filteredEvents.length} events</StatusChip>
          </div>
          {status === "loading" ? <LoadingState label="Loading audit events" /> : null}
          {status === "error" && error ? <ErrorState title="Audit service unavailable" copy={error.message} requestId={error.requestId} /> : null}
          {status === "empty" || filteredEvents.length === 0 ? <EmptyState title="No audit events" copy="No events match the current actor, action, target, and date filters." /> : null}
          <div className="audit-event-list">
            {filteredEvents.map((event) => (
              <AuditEventRow event={event} key={event.id} />
            ))}
          </div>
        </Panel>
      </div>
    </section>
  );
}

function setSearchParam(params: URLSearchParams, key: string, value: string) {
  if (value) {
    params.set(key, value);
    return;
  }

  params.delete(key);
}

function AuditFilters({ initialFilters, onApply }: { initialFilters: { actorId: string; action: string; targetType: string; targetId: string; from: string; to: string }; onApply: (filters: { actorId: string; action: string; targetType: string; targetId: string; from: string; to: string }) => void }) {
  const [filters, setFilters] = useState(initialFilters);

  useEffect(() => {
    setFilters(initialFilters);
  }, [initialFilters]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply(filters);
  }

  return (
    <form className="audit-filter-form" onSubmit={submit}>
      <div className="panel-heading-row">
        <div>
          <span className="kicker">Filters</span>
          <h2>Actor, action, target</h2>
        </div>
        <ShieldCheck size={20} aria-hidden="true" />
      </div>
      <label className="form-field" htmlFor="audit-actor">
        <span className="field-label">Actor</span>
        <Input id="audit-actor" value={filters.actorId} onChange={(event) => setFilters((current) => ({ ...current, actorId: event.target.value }))} />
      </label>
      <label className="form-field" htmlFor="audit-action">
        <span className="field-label">Action</span>
        <Input id="audit-action" value={filters.action} onChange={(event) => setFilters((current) => ({ ...current, action: event.target.value }))} placeholder="canvas.update" />
      </label>
      <label className="form-field" htmlFor="audit-target-type">
        <span className="field-label">Target type</span>
        <Input id="audit-target-type" value={filters.targetType} onChange={(event) => setFilters((current) => ({ ...current, targetType: event.target.value }))} placeholder="canvas" />
      </label>
      <label className="form-field" htmlFor="audit-target">
        <span className="field-label">Target</span>
        <Input id="audit-target" value={filters.targetId} onChange={(event) => setFilters((current) => ({ ...current, targetId: event.target.value }))} />
      </label>
      <label className="form-field" htmlFor="audit-from">
        <span className="field-label">From</span>
        <Input id="audit-from" type="date" value={filters.from} onChange={(event) => setFilters((current) => ({ ...current, from: event.target.value }))} />
      </label>
      <label className="form-field" htmlFor="audit-to">
        <span className="field-label">To</span>
        <Input id="audit-to" type="date" value={filters.to} onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))} />
      </label>
      <Button variant="primary" type="submit">
        Apply filters
      </Button>
    </form>
  );
}

function AuditEventRow({ event }: { event: AuditEvent }) {
  return (
    <article className="audit-event-row" aria-label={`${event.action} by ${event.actorDisplay}`}>
      <div className="audit-row-icon" aria-hidden="true">
        <History size={16} />
      </div>
      <div className="audit-row-body">
        <div className="audit-row-heading">
          <Badge tone={event.action.includes("delete") ? "danger" : event.action.includes("export") ? "warning" : "info"}>{event.action}</Badge>
          <strong>{event.targetType}/{event.targetId}</strong>
          <span className="timeline-time">{formatDate(event.occurredAt)}</span>
        </div>
        <dl className="audit-meta-grid">
          <div>
            <dt>Actor</dt>
            <dd>{event.actorDisplay}</dd>
          </div>
          <div>
            <dt>Workspace</dt>
            <dd>{event.workspaceId}</dd>
          </div>
          <div>
            <dt>Hash</dt>
            <dd>{event.hash ? `${event.hash.slice(0, 10)}...` : "not provided"}</dd>
          </div>
        </dl>
      </div>
    </article>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}
