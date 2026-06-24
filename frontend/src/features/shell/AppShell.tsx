import { Activity, Bell, Command, Grid3X3, Home, Import, LayoutDashboard, LogOut, Search, ShieldCheck, UserRound } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate, useParams } from "react-router-dom";
import { AppError } from "../../contracts/api-client";
import { primaryCanvasHref } from "../../contracts/fixture-ids";
import { healthOverviewFixture, notificationsFixture } from "../../contracts/fixtures";
import { getGatewayReadiness } from "../../contracts/health";
import type { ServiceReadinessStatus } from "../../contracts/health";
import { getUnreadNotificationCount } from "../../contracts/notifications";
import type { WorkspacePermission, WorkspaceRole, WorkspaceView } from "../../contracts/workspaces";
import { canRole } from "../../contracts/workspaces";
import { Button, EmptyState, IconButton, LoadingState, Select, StatusChip, ToastRegion } from "../../design-system/components";
import { useAuth } from "../auth/AuthProvider";
import { CommandPalette } from "../command-palette/CommandPalette";
import { PermissionDeniedState, RouteAppErrorState, WorkspaceAccessRevokedState, WorkspaceNotFoundState } from "../workspaces/WorkspaceStates";
import { useWorkspaceBySlug } from "../workspaces/WorkspaceProvider";

export type ShellOutletContext = {
  pushToast: (message: string) => void;
  activeWorkspace: WorkspaceView | null;
  activeRole: WorkspaceRole | null;
  cacheSerial: number;
};

const navItems = [
  { label: "Cockpit", to: (workspaceSlug: string) => `/w/${workspaceSlug}`, icon: Home, permission: "canvas:view" },
  { label: "Canvas", to: primaryCanvasHref, icon: Grid3X3, permission: "canvas:view" },
  { label: "Dashboards", to: (workspaceSlug: string) => `/w/${workspaceSlug}/d`, icon: LayoutDashboard, permission: "dashboard:view" },
  { label: "Search", to: (workspaceSlug: string) => `/w/${workspaceSlug}/search`, icon: Search, permission: "canvas:view" },
  { label: "Jobs", to: (workspaceSlug: string) => `/w/${workspaceSlug}/jobs`, icon: Import, permission: "jobs:view" },
  { label: "Notify", to: (workspaceSlug: string) => `/w/${workspaceSlug}/notifications`, icon: Bell, permission: "canvas:view" },
  { label: "Audit", to: (workspaceSlug: string) => `/w/${workspaceSlug}/audit`, icon: ShieldCheck, permission: "audit:view" }
] satisfies Array<{ label: string; to: (workspaceSlug: string) => string; icon: typeof Home; permission: WorkspacePermission }>;

export function AppShell() {
  const params = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const auth = useAuth();
  const workspaceSlug = params.workspaceSlug;
  const workspaceState = useWorkspaceBySlug(workspaceSlug);
  const { activeWorkspace, activeRole, cacheSerial, workspaces, status, error, roleChange } = workspaceState;
  const selectedWorkspaceSlug = activeWorkspace?.slug ?? workspaceSlug ?? workspaces[0]?.slug ?? "";
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [toasts, setToasts] = useState<string[]>([]);
  const [unreadCount, setUnreadCount] = useState(() => notificationsFixture.filter((notification) => notification.readAt === null).length);
  const [healthStatus, setHealthStatus] = useState<ServiceReadinessStatus>(healthOverviewFixture.status);
  const commandButtonRef = useRef<HTMLButtonElement | null>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  const closePalette = useCallback(() => setPaletteOpen(false), []);

  const openPalette = useCallback(() => {
    returnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : commandButtonRef.current;
    setPaletteOpen(true);
  }, []);

  const pushToast = useCallback((message: string) => {
    setToasts((current) => [...current.slice(-2), message]);
  }, []);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        openPalette();
      }
    }

    document.addEventListener("keydown", handleKeyDown);

    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [openPalette]);

  useEffect(() => {
    setPaletteOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!activeWorkspace) {
      return undefined;
    }

    const controller = new AbortController();

    getUnreadNotificationCount(auth.client, activeWorkspace.id, controller.signal)
      .then(setUnreadCount)
      .catch((loadError) => {
        if (loadError instanceof AppError && loadError.code === "REQUEST_ABORTED") {
          return;
        }

        setUnreadCount(notificationsFixture.filter((notification) => notification.readAt === null).length);
      });

    getGatewayReadiness(auth.client, controller.signal)
      .then((overview) => setHealthStatus(overview.status))
      .catch((loadError) => {
        if (loadError instanceof AppError && loadError.code === "REQUEST_ABORTED") {
          return;
        }

        setHealthStatus("DEGRADED");
      });

    return () => controller.abort();
  }, [activeWorkspace, auth.client]);

  useEffect(() => {
    const heading = document.querySelector<HTMLElement>(".route-surface h1");

    if (heading) {
      heading.setAttribute("tabindex", "-1");
      heading.focus({ preventScroll: true });
    }
  }, [location.pathname]);

  const switchWorkspace = useCallback(
    (nextWorkspaceSlug: string) => {
      if (!nextWorkspaceSlug || nextWorkspaceSlug === selectedWorkspaceSlug) {
        return;
      }

      workspaceState.clearWorkspaceScopedCaches();
      navigate(`/w/${nextWorkspaceSlug}`);
    },
    [navigate, selectedWorkspaceSlug, workspaceState]
  );

  const logout = useCallback(async () => {
    await auth.logout();
    navigate("/login", { replace: true });
  }, [auth, navigate]);

  const outlet = renderWorkspaceOutlet({
    workspaceSlug,
    activeWorkspace,
    activeRole,
    cacheSerial,
    status,
    error,
    locationPathname: location.pathname,
    pushToast,
    reload: workspaceState.reload
  });

  return (
    <div className="app-shell">
      <header className="topbar" aria-label="LiveLattice shell">
        <NavLink className="brand" to={selectedWorkspaceSlug ? `/w/${selectedWorkspaceSlug}` : "/workspaces"} aria-label="LiveLattice workspace home">
          <span className="brand-mark" aria-hidden="true" />
          <span>
            <span className="brand-name">LiveLattice</span>
            <span className="brand-mode utility-text">Workspace graph</span>
          </span>
        </NavLink>

        <div className="workspace-switcher">
          <label className="field-label" htmlFor="workspace-switcher">
            Workspace
          </label>
          <Select id="workspace-switcher" value={selectedWorkspaceSlug} onChange={(event) => switchWorkspace(event.target.value)} aria-label="Workspace switcher" disabled={workspaces.length === 0 || status === "loading"}>
            {workspaces.length === 0 ? <option value="">No workspaces</option> : null}
            {workspaces.map((workspace) => (
              <option value={workspace.slug} key={workspace.id}>
                {workspace.slug}
              </option>
            ))}
          </Select>
        </div>

        <button className="command-trigger" type="button" ref={commandButtonRef} onClick={openPalette} aria-label="Open command palette">
          <span>
            <Command aria-hidden="true" size={18} />
            Search canvases, widgets, jobs
          </span>
          <kbd>Ctrl K</kbd>
        </button>

        <div className="topbar-actions" aria-label="Workspace status">
          <StatusChip tone={toneForRole(activeRole)}>{activeRole ? `${activeRole} role` : status === "loading" ? "workspace loading" : "no role"}</StatusChip>
          <StatusChip tone={toneForHealth(healthStatus)}>
            <Activity aria-hidden="true" size={14} />
            {healthStatus.toLowerCase()} health
          </StatusChip>
          <span className="cache-namespace utility-text" data-testid="cache-namespace">
            cache/{selectedWorkspaceSlug || "none"}/{cacheSerial}
          </span>
          <Link className="icon-button notification-link" to={selectedWorkspaceSlug ? `/w/${selectedWorkspaceSlug}/notifications` : "/workspaces"} aria-label={`Notifications, ${unreadCount} unread`}>
            <Bell aria-hidden="true" size={18} />
            <span className="notification-badge" aria-hidden="true">
              {unreadCount}
            </span>
          </Link>
          <IconButton label={auth.user ? `Account menu for ${auth.user.displayName}` : "Account menu"} icon={<UserRound aria-hidden="true" size={18} />} />
          <Button variant="ghost" icon={<LogOut aria-hidden="true" size={16} />} onClick={() => void logout()}>
            Logout
          </Button>
        </div>
      </header>

      <div className="shell-body">
        <aside className="primary-nav" aria-label="Primary navigation">
          <NavLink className={({ isActive }) => `primary-nav-link ${isActive ? "is-active" : ""}`} to="/workspaces">
            <Home aria-hidden="true" size={18} />
            <span>Workspaces</span>
          </NavLink>
          {navItems.filter((item) => canRole(activeRole, item.permission)).map((item) => {
            const Icon = item.icon;

            return (
              <NavLink className={({ isActive }) => `primary-nav-link ${isActive ? "is-active" : ""}`} end={item.label === "Cockpit"} key={item.label} to={item.to(selectedWorkspaceSlug)}>
                <Icon aria-hidden="true" size={18} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </aside>

        <main className="route-surface">
          {roleChange && (!workspaceSlug || roleChange.workspaceSlug === workspaceSlug) ? (
            <div className="role-change-banner" role="status">
              <span>
                Role changed from {roleChange.from} to {roleChange.to}.
              </span>
              <Button variant="ghost" onClick={workspaceState.acknowledgeRoleChange}>
                Dismiss
              </Button>
            </div>
          ) : null}
          {outlet}
        </main>
      </div>

      <CommandPalette open={paletteOpen} workspaceSlug={selectedWorkspaceSlug || "workspaces"} onClose={closePalette} returnFocusRef={returnFocusRef} />
      <ToastRegion messages={toasts} />
    </div>
  );
}

function renderWorkspaceOutlet({
  workspaceSlug,
  activeWorkspace,
  activeRole,
  cacheSerial,
  status,
  error,
  locationPathname,
  pushToast,
  reload
}: {
  workspaceSlug: string | undefined;
  activeWorkspace: WorkspaceView | null;
  activeRole: WorkspaceRole | null;
  cacheSerial: number;
  status: string;
  error: ReturnType<typeof useWorkspaceBySlug>["error"];
  locationPathname: string;
  pushToast: (message: string) => void;
  reload: () => Promise<void>;
}) {
  if (locationPathname === "/workspaces") {
    return <Outlet context={{ pushToast, activeWorkspace, activeRole, cacheSerial } satisfies ShellOutletContext} />;
  }

  if (status === "loading" || status === "idle") {
    return <LoadingState label="Workspace loading" />;
  }

  if (status === "empty") {
    return (
      <EmptyState
        title="No workspaces"
        copy="Create a workspace to establish a tenant boundary for canvases, dashboards, comments, jobs, notifications, and audit."
        action={
          <Link className="button button-primary" to="/workspaces">
            Open workspace setup
          </Link>
        }
      />
    );
  }

  if (status === "permission_denied") {
    return <PermissionDeniedState error={error} />;
  }

  if (status === "error" && error) {
    return <RouteAppErrorState error={error} onRetry={() => void reload()} />;
  }

  if (!workspaceSlug) {
    return <Outlet context={{ pushToast, activeWorkspace, activeRole, cacheSerial } satisfies ShellOutletContext} />;
  }

  if (!activeWorkspace) {
    return <WorkspaceNotFoundState workspaceSlug={workspaceSlug} />;
  }

  if (activeWorkspace.access === "revoked") {
    return <WorkspaceAccessRevokedState workspaceSlug={workspaceSlug} />;
  }

  return <Outlet context={{ pushToast, activeWorkspace, activeRole, cacheSerial } satisfies ShellOutletContext} />;
}

function toneForRole(role: WorkspaceRole | null | undefined) {
  if (role === "owner" || role === "admin") {
    return "healthy";
  }

  if (role === "editor") {
    return "info";
  }

  if (role === "viewer" || role === "commenter") {
    return "warning";
  }

  return "neutral";
}

function toneForHealth(status: ServiceReadinessStatus) {
  if (status === "UP") {
    return "healthy";
  }

  if (status === "DOWN") {
    return "danger";
  }

  return "warning";
}
