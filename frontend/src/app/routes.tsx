import { Navigate, Route, Routes } from "react-router-dom";
import { RouteErrorBoundary } from "./error-boundaries/RouteErrorBoundary";
import { LoginRoute } from "../features/auth/LoginRoute";
import { ProtectedRoute } from "../features/auth/ProtectedRoute";
import { CanvasListRoute } from "../features/canvas/CanvasListRoute";
import { CanvasRoute } from "../features/canvas/CanvasRoute";
import { DashboardDetailRoute, DashboardListRoute } from "../features/dashboards/DashboardRoutes";
import { AppShell } from "../features/shell/AppShell";
import { AuditRoute, JobsRoute, NotificationsRoute, SearchRoute, WorkspaceHomeRoute, WorkspacesRoute } from "../features/routes/Placeholders";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<RouteErrorBoundary><LoginRoute /></RouteErrorBoundary>} />
      <Route path="/" element={<Navigate to="/workspaces" replace />} />
      <Route element={<RouteErrorBoundary><ProtectedRoute><AppShell /></ProtectedRoute></RouteErrorBoundary>}>
        <Route path="/workspaces" element={<WorkspacesRoute />} />
        <Route path="/w/:workspaceSlug" element={<WorkspaceHomeRoute />} />
        <Route path="/w/:workspaceSlug/c" element={<CanvasListRoute />} />
        <Route path="/w/:workspaceSlug/c/:canvasId" element={<CanvasRoute />} />
        <Route path="/w/:workspaceSlug/d" element={<DashboardListRoute />} />
        <Route path="/w/:workspaceSlug/d/:dashboardId" element={<DashboardDetailRoute />} />
        <Route path="/w/:workspaceSlug/search" element={<SearchRoute />} />
        <Route path="/w/:workspaceSlug/jobs" element={<JobsRoute />} />
        <Route path="/w/:workspaceSlug/notifications" element={<NotificationsRoute />} />
        <Route path="/w/:workspaceSlug/audit" element={<AuditRoute />} />
      </Route>
      <Route path="*" element={<Navigate to="/workspaces" replace />} />
    </Routes>
  );
}
