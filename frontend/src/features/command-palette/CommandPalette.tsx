import { Bell, FileSearch, Import, LayoutDashboard, ListChecks, MessageCircle, Network, Search, ShieldCheck, UserRound } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent, MutableRefObject } from "react";
import { useNavigate } from "react-router-dom";
import { exportBoundaryCommentHref, primaryCanvasHref } from "../../contracts/fixture-ids";
import { Input } from "../../design-system/components";

export type CommandPaletteProps = {
  open: boolean;
  workspaceSlug: string;
  onClose: () => void;
  returnFocusRef: MutableRefObject<HTMLElement | null>;
};

export function CommandPalette({ open, workspaceSlug, onClose, returnFocusRef }: CommandPaletteProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const commands = useMemo(
    () => {
      const canvasHref = primaryCanvasHref(workspaceSlug);

      return [
        { label: "Open workspace lattice", kind: "workspace", href: `/w/${workspaceSlug}`, icon: Network },
        { label: "Open Warehouse flow", kind: "canvas", href: canvasHref, icon: ListChecks },
        { label: "Open dashboards", kind: "dashboard", href: `/w/${workspaceSlug}/d`, icon: LayoutDashboard },
        { label: "Search highlighted comments", kind: "search", href: `/w/${workspaceSlug}/search?q=diagram+RBAC+export`, icon: FileSearch },
        { label: "Open export boundary comment", kind: "comment", href: exportBoundaryCommentHref(workspaceSlug), icon: MessageCircle },
        { label: "Review import and export jobs", kind: "jobs", href: `/w/${workspaceSlug}/jobs`, icon: Import },
        { label: "Open notification inbox", kind: "notifications", href: `/w/${workspaceSlug}/notifications`, icon: Bell },
        { label: "Find workspace people", kind: "people", href: `/w/${workspaceSlug}/search?type=user&q=owner`, icon: UserRound },
        { label: "Review audit trail", kind: "audit", href: `/w/${workspaceSlug}/audit`, icon: ShieldCheck }
      ];
    },
    [workspaceSlug]
  );
  const visibleCommands = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();

    if (!normalizedQuery) {
      return commands;
    }

    return commands.filter((command) => `${command.label} ${command.kind}`.toLowerCase().includes(normalizedQuery));
  }, [commands, query]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    inputRef.current?.focus();

    function handleKeyDown(event: globalThis.KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        returnFocusRef.current?.focus();
      }

      if (event.key === "Tab") {
        trapFocus(event, panelRef.current);
      }
    }

    document.addEventListener("keydown", handleKeyDown);

    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose, open, returnFocusRef]);

  if (!open) {
    return null;
  }

  function chooseCommand(href: string) {
    navigate(href);
    onClose();
    returnFocusRef.current?.focus();
  }

  function handlePanelKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((current) => Math.min(current + 1, visibleCommands.length - 1));
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((current) => Math.max(current - 1, 0));
    }

    if (event.key === "Enter" && visibleCommands[activeIndex]) {
      event.preventDefault();
      chooseCommand(visibleCommands[activeIndex].href);
    }
  }

  return (
    <div className="command-palette" role="presentation" onMouseDown={onClose}>
      <div className="palette-panel" role="dialog" aria-modal="true" aria-labelledby="command-palette-title" ref={panelRef} onMouseDown={(event) => event.stopPropagation()} onKeyDown={handlePanelKeyDown}>
        <h2 id="command-palette-title" className="visually-hidden">
          Command palette
        </h2>
        <div className="palette-input-row">
          <Search aria-hidden="true" size={20} />
          <Input ref={inputRef} aria-label="Command search" value={query} onChange={(event) => setQuery(event.target.value)} />
        </div>
        <div className="palette-options" role="listbox" aria-label="Command results">
          {visibleCommands.length === 0 ? <div className="palette-empty small-copy">No commands match</div> : null}
          {visibleCommands.map((command, index) => {
            const Icon = command.icon;

            return (
              <button className={`palette-option ${index === activeIndex ? "is-active" : ""}`} type="button" role="option" aria-selected={index === activeIndex} key={command.href} onMouseEnter={() => setActiveIndex(index)} onClick={() => chooseCommand(command.href)}>
                <span>
                  <Icon aria-hidden="true" size={17} />
                  {command.label}
                </span>
                <span className="coord">{command.kind}</span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function trapFocus(event: globalThis.KeyboardEvent, panel: HTMLDivElement | null) {
  if (!panel) {
    return;
  }

  const focusableElements = Array.from(panel.querySelectorAll<HTMLElement>("button, input, select, textarea, a[href], [tabindex]:not([tabindex='-1'])")).filter((element) => !element.hasAttribute("disabled"));
  const first = focusableElements[0];
  const last = focusableElements[focusableElements.length - 1];

  if (!first || !last) {
    return;
  }

  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  }

  if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
