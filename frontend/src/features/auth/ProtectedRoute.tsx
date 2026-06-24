import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { LoadingState } from "../../design-system/components";
import { useAuth } from "./AuthProvider";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const location = useLocation();

  if (auth.status === "restoring") {
    return (
      <main className="auth-state-route">
        <LoadingState label="Restoring Gateway session" />
      </main>
    );
  }

  if (auth.status !== "authenticated") {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search, expired: auth.expired }} />;
  }

  return children;
}
