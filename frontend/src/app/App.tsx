import { AppRoutes } from "./routes";
import { AuthProvider } from "../features/auth/AuthProvider";
import { WorkspaceProvider } from "../features/workspaces/WorkspaceProvider";

export function App() {
  return (
    <AuthProvider>
      <WorkspaceProvider>
        <AppRoutes />
      </WorkspaceProvider>
    </AuthProvider>
  );
}
