import type { AuthUser, WorkspaceRole } from "./auth";
import { normalizeWorkspaceRole } from "./auth";
import type { GatewayClient } from "./api-client";

export type { WorkspaceRole };

export type WorkspaceTier = "FREE" | "PRO" | "ENTERPRISE";

export type WorkspaceResponse = {
  id: string;
  name: string;
  slug: string;
  tier: WorkspaceTier;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
};

export type WorkspaceMemberResponse = {
  userId: string;
  role: string;
  joinedAt: string;
};

export type CreateWorkspacePayload = {
  name: string;
  slug: string;
};

export type WorkspaceMemberView = WorkspaceMemberResponse & {
  role: WorkspaceRole;
  inviteStatus: "joined" | "pending";
};

export type WorkspaceView = WorkspaceResponse & {
  currentRole: WorkspaceRole | null;
  members: WorkspaceMemberView[];
  access: "active" | "revoked";
};

export const rolePermissions = {
  owner: ["workspace:delete", "workspace:update_settings", "workspace:manage_members", "canvas:create", "canvas:edit", "canvas:view", "canvas:comment", "dashboard:create", "dashboard:edit", "dashboard:view", "data_source:manage", "jobs:view", "audit:view"],
  admin: ["workspace:update_settings", "workspace:manage_members", "canvas:create", "canvas:edit", "canvas:view", "canvas:comment", "dashboard:create", "dashboard:edit", "dashboard:view", "data_source:manage", "jobs:view", "audit:view"],
  editor: ["canvas:create", "canvas:edit", "canvas:view", "canvas:comment", "dashboard:create", "dashboard:edit", "dashboard:view", "data_source:manage", "jobs:view"],
  viewer: ["canvas:view", "dashboard:view"],
  commenter: ["canvas:view", "canvas:comment", "dashboard:view"]
} satisfies Record<WorkspaceRole, string[]>;

export type WorkspacePermission = (typeof rolePermissions)[WorkspaceRole][number];

export function canRole(role: WorkspaceRole | null | undefined, permission: WorkspacePermission) {
  return Boolean(role && rolePermissions[role].includes(permission));
}

export async function listWorkspaces(client: GatewayClient, signal?: AbortSignal) {
  return client.get<WorkspaceResponse[]>("/api/core/workspaces", { signal });
}

export async function createWorkspace(client: GatewayClient, payload: CreateWorkspacePayload) {
  return client.post<WorkspaceResponse>("/api/core/workspaces", payload);
}

export async function listWorkspaceMembers(client: GatewayClient, workspaceId: string, signal?: AbortSignal) {
  return client.get<WorkspaceMemberResponse[]>(`/api/core/workspaces/${workspaceId}/members`, { signal });
}

export function workspaceMembersForView(members: WorkspaceMemberResponse[]): WorkspaceMemberView[] {
  return members.flatMap((member) => {
    const role = normalizeWorkspaceRole(member.role);
    return role ? [{ ...member, role, inviteStatus: "joined" as const }] : [];
  });
}

export function currentRoleForUser(members: WorkspaceMemberView[], user: AuthUser) {
  return members.find((member) => member.userId === user.subject)?.role ?? null;
}

export function workspaceDisplayTier(tier: WorkspaceTier) {
  return tier.toLowerCase();
}
