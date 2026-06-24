import { Component } from "react";
import type { ReactNode } from "react";
import { AppError } from "../../contracts/api-client";
import { RouteAppErrorState } from "../../features/workspaces/WorkspaceStates";

type RouteErrorBoundaryState = {
  error: AppError | null;
};

export class RouteErrorBoundary extends Component<{ children: ReactNode }, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = {
    error: null
  };

  static getDerivedStateFromError(error: unknown): RouteErrorBoundaryState {
    if (error instanceof AppError) {
      return { error };
    }

    return {
      error: new AppError({
        status: 0,
        code: "ROUTE_RENDER_FAILED",
        message: "This route could not be rendered.",
        retryable: false
      })
    };
  }

  render() {
    if (this.state.error) {
      return <RouteAppErrorState error={this.state.error} />;
    }

    return this.props.children;
  }
}
