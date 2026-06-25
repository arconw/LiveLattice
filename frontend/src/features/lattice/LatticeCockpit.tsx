import { Activity, Bell, Boxes, FileSearch, Gauge, History, Import, Network } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { canvasListHref } from "../../contracts/fixture-ids";
import { Button, StatusChip } from "../../design-system/components";
import { usePrefersReducedMotion } from "./usePrefersReducedMotion";

type LatticeNode = {
  id: string;
  label: string;
  title: string;
  detail: string;
  owner: string;
  health: string;
  event: string;
  coordinate: string;
  tone: "canvas" | "dashboard" | "search" | "jobs" | "notifications" | "audit";
  state: string;
  href: (workspaceSlug: string) => string;
  icon: typeof Boxes;
};

const nodes: LatticeNode[] = [
  {
    id: "canvas",
    label: "Canvas",
    title: "Warehouse flow",
    detail: "39 elements, v128",
    owner: "Canvas team",
    health: "Editing live",
    event: "CanvasUpdated",
    coordinate: "x120 y84",
    tone: "canvas",
    state: "Live editing",
    href: canvasListHref,
    icon: Boxes
  },
  {
    id: "dashboard",
    label: "Dashboard",
    title: "Throughput board",
    detail: "12-column grid",
    owner: "Analytics",
    health: "Cached 22s",
    event: "QueryExecuted",
    coordinate: "x392 y72",
    tone: "dashboard",
    state: "Cache warm",
    href: (workspaceSlug) => `/w/${workspaceSlug}/d`,
    icon: Gauge
  },
  {
    id: "search",
    label: "Search",
    title: "Highlighted results",
    detail: "facets plus suggest",
    owner: "OpenSearch",
    health: "6 indexes ready",
    event: "BulkIndexed",
    coordinate: "x640 y146",
    tone: "search",
    state: "Indexed",
    href: (workspaceSlug) => `/w/${workspaceSlug}/search`,
    icon: FileSearch
  },
  {
    id: "jobs",
    label: "Import / export",
    title: "SVG to canvas",
    detail: "artifact streaming",
    owner: "MinIO worker",
    health: "86 percent parsed",
    event: "ExportProgressed",
    coordinate: "x226 y318",
    tone: "jobs",
    state: "In progress",
    href: (workspaceSlug) => `/w/${workspaceSlug}/jobs`,
    icon: Import
  },
  {
    id: "notifications",
    label: "Notify",
    title: "@mention thread",
    detail: "unresolved comment",
    owner: "Realtime",
    health: "Mention pending",
    event: "MentionCreated",
    coordinate: "x582 y322",
    tone: "notifications",
    state: "Unread",
    href: (workspaceSlug) => `/w/${workspaceSlug}/notifications`,
    icon: Bell
  },
  {
    id: "audit",
    label: "Audit",
    title: "Workspace trail",
    detail: "read-only events",
    owner: "Audit log",
    health: "Filtered by workspace",
    event: "AuditEventStored",
    coordinate: "x492 y214",
    tone: "audit",
    state: "Read-only",
    href: (workspaceSlug) => `/w/${workspaceSlug}/audit`,
    icon: History
  }
];

export function LatticeCockpit({ onNotify }: { onNotify?: (message: string) => void }) {
  const { workspaceSlug = "factory-floor" } = useParams();
  const [selectedNodeId, setSelectedNodeId] = useState("canvas");
  const prefersReducedMotion = usePrefersReducedMotion();
  const selectedNode = useMemo(() => nodes.find((node) => node.id === selectedNodeId) ?? nodes[0], [selectedNodeId]);
  const SelectedIcon = selectedNode.icon;

  function selectNode(node: LatticeNode) {
    setSelectedNodeId(node.id);
    onNotify?.(`${node.event} selected`);
  }

  return (
    <section className="lattice-cockpit" aria-label="Interactive lattice cockpit">
      <nav className="lattice-rail" aria-label="Workspace atlas">
        <div className="rail-title utility-text">Workspace atlas</div>
        <div className="nav-list">
          {nodes.map((node) => (
            <button
              className={`nav-link ${selectedNode.id === node.id ? "is-active" : ""}`}
              key={node.id}
              type="button"
              onClick={() => selectNode(node)}
              aria-pressed={selectedNode.id === node.id}
            >
              <span>{node.label}</span>
              <span className={`nav-signal nav-signal-${node.tone}`} aria-hidden="true" />
            </button>
          ))}
        </div>
      </nav>

      <div className="canvas-field">
        <div className="canvas-topline">
          <div className="canvas-title">
            <span className="coord">WS/{workspaceSlug} / canvases</span>
            <strong>Workspace canvas lattice</strong>
          </div>
          <div className="layer-controls" aria-label="Canvas layer toggles">
            <button className="layer-toggle is-on layer-events" type="button" aria-pressed="true">
              Events on
            </button>
            <button className="layer-toggle is-on layer-comments" type="button" aria-pressed="true">
              Comments on
            </button>
            <button className="layer-toggle is-on layer-presence" type="button" aria-pressed="true">
              Presence on
            </button>
          </div>
        </div>

        <div className="lattice-lines" aria-hidden="true">
          <svg viewBox="0 0 800 430" preserveAspectRatio="none">
            <path className="lattice-path" d="M80 120 C220 65 330 80 420 120 S615 230 725 160" />
            <path data-testid="event-pulse" className={prefersReducedMotion ? "lattice-path" : "lattice-path lattice-pulse"} d="M110 320 C230 250 315 260 410 190 S610 110 710 255" />
            <path className="lattice-path" d="M180 135 C210 250 260 310 380 320 S550 330 650 270" />
          </svg>
        </div>

        {nodes.map((node) => {
          const Icon = node.icon;
          const selected = selectedNode.id === node.id;

          return (
            <button
              className={`lattice-node lattice-node-${node.id} lattice-node-${node.tone} ${selected ? "is-selected" : ""}`}
              key={node.id}
              type="button"
              onClick={() => selectNode(node)}
              aria-pressed={selected}
            >
              <span className="node-label">
                <Icon aria-hidden="true" size={14} />
                {node.label}
              </span>
              <strong>{node.title}</strong>
              <small>{node.detail}</small>
              <span className="node-state">{node.state}</span>
            </button>
          );
        })}

        <span className="presence-cursor presence-a">SV</span>
        <span className="presence-cursor presence-b">AM</span>
        <button className="comment-pin" type="button" onClick={() => selectNode(nodes[4])} aria-label="Open unresolved notification thread">
          3
        </button>
      </div>

      <aside className="inspector" aria-label="Selected object inspector">
        <div className="inspector-title utility-text">Selection inspector</div>
        <div className="inspector-body">
          <div className="inspector-heading">
            <SelectedIcon aria-hidden="true" size={22} />
            <h2>{selectedNode.label === "Canvas" ? "Canvas document" : selectedNode.label === "Dashboard" ? "Dashboard widget" : selectedNode.label}</h2>
          </div>
          <p className="small-copy">
            {selectedNode.health}. Last event: {selectedNode.event}.
          </p>
          <div className="inspector-list">
            <div className="inspector-row">
              <span>Owner</span>
              <strong>{selectedNode.owner}</strong>
            </div>
            <div className="inspector-row">
              <span>Health</span>
              <strong>{selectedNode.health}</strong>
            </div>
            <div className="inspector-row">
              <span>Coordinate</span>
              <strong>{selectedNode.coordinate}</strong>
            </div>
          </div>
          <StatusChip tone={selectedNode.tone === "notifications" ? "danger" : selectedNode.tone === "jobs" || selectedNode.tone === "dashboard" ? "warning" : "healthy"}>
            {selectedNode.state}
          </StatusChip>
          <Button variant="secondary" icon={<Activity size={16} aria-hidden="true" />} onClick={() => onNotify?.(`Snapshot queued for ${selectedNode.title}`)}>
            Queue snapshot
          </Button>
          <Link className="button button-primary" to={selectedNode.href(workspaceSlug)}>
            <Network size={16} aria-hidden="true" />
            <span>Open route</span>
          </Link>
        </div>

        <div className="timeline" aria-label="Recent event stream">
          <div className="timeline-item">
            <span className="timeline-time">12:31</span>
            <span>CanvasUpdated published after commit.</span>
          </div>
          <div className="timeline-item">
            <span className="timeline-time">12:31</span>
            <span>OpenSearch bulk indexed canvas.</span>
          </div>
          <div className="timeline-item">
            <span className="timeline-time">12:30</span>
            <span>Gateway protected /api/search path.</span>
          </div>
        </div>
      </aside>
    </section>
  );
}
