import { createLocalJWKSet, jwtVerify, type JSONWebKeySet } from "jose";
import type { AuthConfig, CoreConfig } from "./config";

export interface MembershipStore {
  getMembership(key: string): Promise<boolean | undefined>;
  setMembership(key: string, value: boolean, ttlSeconds: number): Promise<void>;
  close(): Promise<void>;
}

export interface AuthIdentity {
  subject: string;
  email: string;
  displayName: string;
  roles: string[];
}

export interface AuthResult {
  allowed: boolean;
  statusCode?: number;
  message?: string;
  identity?: AuthIdentity;
}

export class AuthService {
  private jwksCache: { value: JSONWebKeySet; expiresAt: number } | undefined;
  private jwksPromise: Promise<JSONWebKeySet> | undefined;

  constructor(
    private readonly auth: AuthConfig,
    private readonly core: CoreConfig,
    private readonly membership: MembershipStore
  ) {}

  async verifyToken(token: string | undefined): Promise<AuthResult> {
    if (this.auth.disabled) {
      return {
        allowed: true,
        identity: {
          subject: "anon-subject",
          email: "anon@livelattice.local",
          displayName: "Anonymous",
          roles: []
        }
      };
    }
    if (!token || token.length === 0) {
      return { allowed: false, statusCode: 401, message: "Missing token" };
    }
    if (!this.auth.jwksUri) {
      return { allowed: false, statusCode: 503, message: "JWKS URI is not configured" };
    }
    try {
      const jwks = await this.loadJwks();
      const result = await jwtVerify(token, createLocalJWKSet(jwks), {
        issuer: this.auth.issuer,
        audience: this.auth.audience
      });
      const payload = result.payload;
      const identity: AuthIdentity = {
        subject: String(payload.sub ?? ""),
        email: typeof payload.email === "string" ? payload.email : `${payload.sub ?? "anon"}@livelattice.local`,
        displayName: this.displayName(payload.name, payload.preferred_username, payload.email, payload.sub),
        roles: this.roles(payload.realm_access)
      };
      if (!identity.subject) {
        return { allowed: false, statusCode: 401, message: "Missing subject" };
      }
      return { allowed: true, identity };
    } catch {
      return { allowed: false, statusCode: 401, message: "Invalid token" };
    }
  }

  async verifyWorkspaceMembership(workspaceId: string, identity: AuthIdentity): Promise<boolean> {
    if (this.auth.disabled) {
      return true;
    }
    const cacheKey = `realtime:membership:${workspaceId}:${identity.subject}`;
    const cached = await this.membership.getMembership(cacheKey);
    if (cached !== undefined) {
      return cached;
    }
    const url = `${this.core.url.replace(/\/$/, "")}/workspaces/${encodeURIComponent(workspaceId)}`;
    let member = false;
    try {
      const response = await fetch(url, {
        method: "GET",
        headers: {
          "x-internal-auth-token": this.core.internalSecret,
          "x-auth-subject": identity.subject,
          "x-auth-email": identity.email,
          "x-auth-display-name": identity.displayName
        }
      });
      member = response.ok;
    } catch {
      member = false;
    }
    await this.membership.setMembership(cacheKey, member, this.core.membershipCacheTtlSeconds);
    return member;
  }

  async close(): Promise<void> {
    this.jwksCache = undefined;
    this.jwksPromise = undefined;
  }

  private async loadJwks(): Promise<JSONWebKeySet> {
    if (this.jwksCache && this.jwksCache.expiresAt > Date.now()) {
      return this.jwksCache.value;
    }
    if (this.jwksPromise) {
      return this.jwksPromise;
    }
    this.jwksPromise = this.fetchJwks();
    try {
      const jwks = await this.jwksPromise;
      this.jwksCache = { value: jwks, expiresAt: Date.now() + this.auth.jwksTtlSeconds * 1000 };
      return jwks;
    } finally {
      this.jwksPromise = undefined;
    }
  }

  private async fetchJwks(): Promise<JSONWebKeySet> {
    const response = await fetch(this.auth.jwksUri as string);
    if (!response.ok) {
      throw new Error("JWKS fetch failed");
    }
    return (await response.json()) as JSONWebKeySet;
  }

  private roles(value: unknown): string[] {
    if (typeof value !== "object" || value === null || !("roles" in value)) {
      return [];
    }
    const roles = (value as { roles?: unknown }).roles;
    return Array.isArray(roles) ? roles.filter((role): role is string => typeof role === "string") : [];
  }

  private displayName(...values: unknown[]): string {
    const found = values.find((value): value is string => typeof value === "string" && value.length > 0);
    return found ?? "Unknown";
  }
}