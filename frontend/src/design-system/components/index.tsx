import { forwardRef, useEffect, useRef } from "react";
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

type ClassNameProp = {
  className?: string;
};

function cx(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(" ");
}

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost";
  icon?: ReactNode;
};

export function Button({ variant = "secondary", icon, className, children, type = "button", ...props }: ButtonProps) {
  return (
    <button className={cx("button", `button-${variant}`, className)} type={type} {...props}>
      {icon}
      <span>{children}</span>
    </button>
  );
}

export type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  label: string;
  icon: ReactNode;
};

export function IconButton({ label, icon, className, type = "button", ...props }: IconButtonProps) {
  return (
    <button className={cx("icon-button", className)} aria-label={label} title={label} type={type} {...props}>
      {icon}
    </button>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & ClassNameProp>(function Input({ className, ...props }, ref) {
  return <input className={cx("input", className)} ref={ref} {...props} />;
});

export function Select({ className, children, ...props }: SelectHTMLAttributes<HTMLSelectElement> & ClassNameProp) {
  return (
    <select className={cx("select", className)} {...props}>
      {children}
    </select>
  );
}

export type BadgeTone = "neutral" | "healthy" | "warning" | "info" | "danger";

export function StatusChip({ tone = "neutral", children }: { tone?: BadgeTone; children: ReactNode }) {
  return (
    <span className={cx("status-chip", `status-chip-${tone}`)}>
      <span className="status-dot" aria-hidden="true" />
      <span>{children}</span>
    </span>
  );
}

export function Badge({ tone = "neutral", children }: { tone?: BadgeTone; children: ReactNode }) {
  return <span className={cx("badge", `badge-${tone}`)}>{children}</span>;
}

export function Panel({ className, children, as: Component = "section" }: ClassNameProp & { children: ReactNode; as?: "section" | "article" | "aside" | "div" }) {
  return <Component className={cx("panel", className)}>{children}</Component>;
}

export function PaperSurface({ className, children, as: Component = "section" }: ClassNameProp & { children: ReactNode; as?: "section" | "article" | "aside" | "div" }) {
  return <Component className={cx("paper-surface", className)}>{children}</Component>;
}

export function Dialog({ open, title, children, onClose }: { open: boolean; title: string; children: ReactNode; onClose: () => void }) {
  const panelRef = useRef<HTMLElement | null>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    returnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const panel = panelRef.current;
    const focusableElements = focusableElementsIn(panel);
    (focusableElements[0] ?? panel)?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }

      if (event.key === "Tab") {
        trapFocus(event, panel);
      }
    }

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      returnFocusRef.current?.focus();
    };
  }, [onClose, open]);

  if (!open) {
    return null;
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <section className="dialog-panel" role="dialog" aria-modal="true" aria-label={title} tabIndex={-1} ref={panelRef} onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog-title-row">
          <h2>{title}</h2>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
        </div>
        {children}
      </section>
    </div>
  );
}

function trapFocus(event: KeyboardEvent, panel: HTMLElement | null) {
  const focusableElements = focusableElementsIn(panel);
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

function focusableElementsIn(panel: HTMLElement | null) {
  if (!panel) {
    return [];
  }

  return Array.from(panel.querySelectorAll<HTMLElement>("button, input, select, textarea, a[href], [tabindex]:not([tabindex='-1'])")).filter((element) => !element.hasAttribute("disabled") && element.getAttribute("aria-hidden") !== "true");
}

export function ToastRegion({ messages }: { messages: string[] }) {
  return (
    <div className="toast-region" aria-live="polite" aria-atomic="true">
      {messages.map((message, index) => (
        <div className="toast" key={`${message}-${index}`}>
          {message}
        </div>
      ))}
    </div>
  );
}

export function EmptyState({ title, copy, action }: { title: string; copy: string; action?: ReactNode }) {
  return (
    <section className="state state-empty">
      <span className="kicker">Empty state</span>
      <h2>{title}</h2>
      <p>{copy}</p>
      {action}
    </section>
  );
}

export function ErrorState({ title, copy, requestId }: { title: string; copy: string; requestId?: string }) {
  return (
    <section className="state state-error">
      <span className="kicker">Error state</span>
      <h2>{title}</h2>
      <p>{copy}</p>
      {requestId ? <span className="utility-text">request {requestId}</span> : null}
    </section>
  );
}

export function LoadingState({ label = "Loading workspace graph" }: { label?: string }) {
  return (
    <section className="state state-loading" aria-busy="true">
      <span className="loading-mark" aria-hidden="true" />
      <span>{label}</span>
    </section>
  );
}
