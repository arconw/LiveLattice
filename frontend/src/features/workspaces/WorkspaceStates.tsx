import { AlertTriangle, Ban, LockKeyhole, RotateCcw } from "lucide-react";
import { AppError } from "../../contracts/api-client";
import { Button, ErrorState } from "../../design-system/components";

export function PermissionDeniedState({ error }: { error?: AppError | null }) {
  return (
    <section className="route-state">
      <LockKeyhole aria-hidden="true" size={28} />
      <ErrorState title="Permission denied" copy={error?.message ?? "Your current workspace role does not allow this action. Ask a workspace owner or admin to change your access."} requestId={error?.requestId} />
    </section>
  );
}

export function QuotaReachedState({ error, onRetry }: { error?: AppError | null; onRetry?: () => void }) {
  const copy = error?.message ? `${error.message} Remove unused resources or ask an owner to update the workspace tier.` : "This workspace has reached its tier limit. Remove unused resources or ask an owner to update the workspace tier.";

  return (
    <section className="route-state">
      <AlertTriangle aria-hidden="true" size={28} />
      <ErrorState title="Quota reached" copy={copy} requestId={error?.requestId} />
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry} icon={<RotateCcw aria-hidden="true" size={16} />}>
          Retry
        </Button>
      ) : null}
    </section>
  );
}

export function WorkspaceAccessRevokedState({ workspaceSlug }: { workspaceSlug: string }) {
  return (
    <section className="route-state">
      <Ban aria-hidden="true" size={28} />
      <ErrorState title="Workspace access revoked" copy={`Your membership for ${workspaceSlug} is no longer active. Choose another workspace or ask an owner to restore access.`} />
    </section>
  );
}

export function WorkspaceNotFoundState({ workspaceSlug }: { workspaceSlug: string }) {
  return (
    <section className="route-state">
      <ErrorState title="Workspace not found" copy={`No workspace with slug ${workspaceSlug} is available to this session.`} />
    </section>
  );
}

export function RouteAppErrorState({ error, onRetry }: { error: AppError; onRetry?: () => void }) {
  if (error.status === 403) {
    return <PermissionDeniedState error={error} />;
  }

  if (isQuotaError(error)) {
    return <QuotaReachedState error={error} onRetry={onRetry} />;
  }

  if (error.status === 404) {
    return <WorkspaceNotFoundState workspaceSlug="requested route" />;
  }

  return (
    <section className="route-state">
      <ErrorState title="Route error" copy={error.message} requestId={error.requestId} />
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry} icon={<RotateCcw aria-hidden="true" size={16} />}>
          Retry
        </Button>
      ) : null}
    </section>
  );
}

export function isQuotaError(error: AppError | null | undefined) {
  return Boolean(error && (error.status === 422 || error.code.toLowerCase().includes("quota")));
}
