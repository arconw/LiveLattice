import type { GatewayClient } from "./api-client";

export const workspaceRoles = ["owner", "admin", "editor", "viewer", "commenter"] as const;

export type WorkspaceRole = (typeof workspaceRoles)[number];

export type AuthUser = {
  subject: string;
  email: string;
  displayName: string;
  roles: string[];
};

export type AuthTokenResponse = {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresIn?: number;
  refreshExpiresIn?: number;
  tokenType?: string;
  scope?: string;
  user: AuthUser;
};

export type LoginCredentials = {
  email: string;
  password: string;
};

export function normalizeWorkspaceRole(role: string): WorkspaceRole | null {
  const normalized = role.toLowerCase();
  return isWorkspaceRole(normalized) ? normalized : null;
}

export function isWorkspaceRole(role: string): role is WorkspaceRole {
  return workspaceRoles.includes(role as WorkspaceRole);
}

export function userWorkspaceRoles(user: AuthUser): WorkspaceRole[] {
  return user.roles.map(normalizeWorkspaceRole).filter((role): role is WorkspaceRole => role !== null);
}

export async function loginWithGateway(client: GatewayClient, credentials: LoginCredentials) {
  return client.post<AuthTokenResponse>("/auth/login", credentials);
}

export async function refreshGatewaySession(client: GatewayClient, refreshToken: string) {
  return client.post<AuthTokenResponse>("/auth/refresh", { refreshToken });
}

export async function logoutGatewaySession(client: GatewayClient, refreshToken: string) {
  await client.post<void>("/auth/logout", { refreshToken });
}
