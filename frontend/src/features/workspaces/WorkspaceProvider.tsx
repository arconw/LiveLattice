import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { AppError } from "../../contracts/api-client";
import type { CreateWorkspacePayload, WorkspacePermission, WorkspaceView } from "../../contracts/workspaces";
import { canRole, createWorkspace as createWorkspaceRequest, currentRoleForUser, listWorkspaceMembers, listWorkspaces, workspaceMembersForView } from "../../contracts/workspaces";
import { useAuth } from "../auth/AuthProvider";

export type WorkspaceLoadStatus = "idle" | "loading" | "ready" | "empty" | "permission_denied" | "error";

export type RoleChangeState = {
  workspaceSlug: string;
  from: string;
  to: string;
} | null;

export type WorkspaceContextValue = {
  status: WorkspaceLoadStatus;
  workspaces: WorkspaceView[];
  error: AppError | null;
  cacheSerial: number;
  roleChange: RoleChangeState;
  reload: () => Promise<void>;
  createWorkspace: (payload: CreateWorkspacePayload) => Promise<WorkspaceView>;
  clearWorkspaceScopedCaches: () => void;
  acknowledgeRoleChange: () => void;
};

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const [status, setStatus] = useState<WorkspaceLoadStatus>("idle");
  const [workspaces, setWorkspaces] = useState<WorkspaceView[]>([]);
  const [error, setError] = useState<AppError | null>(null);
  const [cacheSerial, setCacheSerial] = useState(0);
  const [roleChange, setRoleChange] = useState<RoleChangeState>(null);
  const previousRolesRef = useRef(new Map<string, string>());

  const clearWorkspaceScopedCaches = useCallback(() => {
    setCacheSerial((current) => current + 1);
  }, []);

  const acknowledgeRoleChange = useCallback(() => {
    setRoleChange(null);
  }, []);

  const reload = useCallback(async () => {
    const currentUser = auth.user;

    if (auth.status !== "authenticated" || !currentUser) {
      setStatus("idle");
      setWorkspaces([]);
      setError(null);
      return;
    }

    setStatus("loading");
    setError(null);

    try {
      const workspaceResponses = await listWorkspaces(auth.client);

      if (workspaceResponses.length === 0) {
        setWorkspaces([]);
        setStatus("empty");
        return;
      }

      const views = await Promise.all(
        workspaceResponses.map(async (workspace) => {
          try {
            const members = workspaceMembersForView(await listWorkspaceMembers(auth.client, workspace.id));
            const currentRole = currentRoleForUser(members, currentUser);

            if (currentRole) {
              const previousRole = previousRolesRef.current.get(workspace.slug);

              if (previousRole && previousRole !== currentRole) {
                setRoleChange({ workspaceSlug: workspace.slug, from: previousRole, to: currentRole });
              }

              previousRolesRef.current.set(workspace.slug, currentRole);
            }

            return {
              ...workspace,
              currentRole,
              members,
              access: currentRole ? "active" as const : "revoked" as const
            };
          } catch (memberError) {
            if (memberError instanceof AppError && memberError.status === 403) {
              return {
                ...workspace,
                currentRole: null,
                members: [],
                access: "revoked" as const
              };
            }

            throw memberError;
          }
        })
      );

      setWorkspaces(views);
      setStatus("ready");
    } catch (loadError) {
      const appError = loadError instanceof AppError ? loadError : new AppError({ status: 0, code: "WORKSPACE_LOAD_FAILED", message: "Workspace data could not be loaded.", retryable: true });
      setError(appError);
      setStatus(appError.status === 403 ? "permission_denied" : "error");
    }
  }, [auth.client, auth.status, auth.user]);

  const createWorkspace = useCallback(
    async (payload: CreateWorkspacePayload) => {
      const created = await createWorkspaceRequest(auth.client, payload);
      const view: WorkspaceView = {
        ...created,
        currentRole: null,
        members: [],
        access: "active"
      };
      await reload();
      return view;
    },
    [auth.client, reload]
  );

  useEffect(() => {
    void reload();
  }, [reload]);

  const value = useMemo<WorkspaceContextValue>(
    () => ({
      status,
      workspaces,
      error,
      cacheSerial,
      roleChange,
      reload,
      createWorkspace,
      clearWorkspaceScopedCaches,
      acknowledgeRoleChange
    }),
    [acknowledgeRoleChange, cacheSerial, clearWorkspaceScopedCaches, createWorkspace, error, reload, roleChange, status, workspaces]
  );

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspaces() {
  const context = useContext(WorkspaceContext);

  if (!context) {
    throw new Error("useWorkspaces must be used within WorkspaceProvider");
  }

  return context;
}

export function useWorkspaceBySlug(workspaceSlug: string | undefined) {
  const workspaces = useWorkspaces();
  const activeWorkspace = workspaceSlug ? workspaces.workspaces.find((workspace) => workspace.slug === workspaceSlug) ?? null : null;

  return {
    ...workspaces,
    activeWorkspace,
    activeRole: activeWorkspace?.currentRole ?? null,
    can: (permission: WorkspacePermission) => canRole(activeWorkspace?.currentRole, permission)
  };
}
