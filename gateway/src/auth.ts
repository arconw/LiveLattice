import Redis from "ioredis";
import { createLocalJWKSet, jwtVerify } from "jose";
import type { AuthConfig } from "./config";

type JsonValue = Record<string, unknown>;

export interface AuthDecision {
  allowed: boolean;
  statusCode?: number;
  message?: string;
  subject?: string;
  email?: string;
  displayName?: string;
  roles?: string[];
}

export interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in?: number;
  refresh_expires_in?: number;
  token_type?: string;
  scope?: string;
}

export interface AuthUser {
  subject: string;
  email: string;
  displayName: string;
  roles: string[];
}

export interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresIn?: number;
  refreshExpiresIn?: number;
  tokenType?: string;
  scope?: string;
  user: AuthUser;
}

interface CacheStore {
  getJson<T extends JsonValue>(key: string): Promise<T | undefined>;
  setJson(key: string, value: JsonValue, ttlSeconds: number): Promise<void>;
  delete(key: string): Promise<void>;
  close(): Promise<void>;
}

class MemoryCacheStore implements CacheStore {
  private readonly values = new Map<string, { expiresAt: number; value: JsonValue }>();

  async getJson<T extends JsonValue>(key: string): Promise<T | undefined> {
    const current = this.values.get(key);
    if (!current || current.expiresAt <= Date.now()) {
      this.values.delete(key);
      return undefined;
    }
    return current.value as T;
  }

  async setJson(key: string, value: JsonValue, ttlSeconds: number): Promise<void> {
    this.values.set(key, { value, expiresAt: Date.now() + ttlSeconds * 1000 });
  }

  async delete(key: string): Promise<void> {
    this.values.delete(key);
  }

  async close(): Promise<void> {}
}

class RedisCacheStore implements CacheStore {
  private readonly redis: Redis;

  constructor(config: AuthConfig) {
    this.redis = new Redis({
      host: config.cache.host,
      port: config.cache.port,
      password: config.cache.password,
      lazyConnect: true,
      maxRetriesPerRequest: 1
    });
  }

  async getJson<T extends JsonValue>(key: string): Promise<T | undefined> {
    try {
      const raw = await this.redis.get(key);
      return raw ? JSON.parse(raw) as T : undefined;
    } catch {
      return undefined;
    }
  }

  async setJson(key: string, value: JsonValue, ttlSeconds: number): Promise<void> {
    try {
      await this.redis.set(key, JSON.stringify(value), "EX", ttlSeconds);
    } catch {}
  }

  async delete(key: string): Promise<void> {
    try {
      await this.redis.del(key);
    } catch {}
  }

  async close(): Promise<void> {
    this.redis.disconnect();
  }
}

export class AuthService {
  private readonly cache: CacheStore;

  constructor(private readonly config: AuthConfig, cache?: CacheStore) {
    this.cache = cache ?? (config.cache.enabled ? new RedisCacheStore(config) : new MemoryCacheStore());
  }

  async login(email: string, password: string): Promise<LoginResponse> {
    const tokens = await this.exchangeToken({
      grant_type: "password",
      username: email,
      password
    });
    const user = this.userFromDecision(await this.verifyAccessToken(tokens.access_token));
    await this.cacheSession(user);
    await this.provisionUser(user);
    return this.loginResponse(tokens, user);
  }

  async refresh(refreshToken: string): Promise<LoginResponse> {
    const tokens = await this.exchangeToken({
      grant_type: "refresh_token",
      refresh_token: refreshToken
    });
    const user = this.userFromDecision(await this.verifyAccessToken(tokens.access_token));
    await this.cacheSession(user);
    await this.provisionUser(user);
    return this.loginResponse(tokens, user);
  }

  async logout(refreshToken: string): Promise<void> {
    if (!this.config.logoutEndpoint) {
      return;
    }
    const response = await fetch(this.config.logoutEndpoint, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: this.clientBody({ refresh_token: refreshToken })
    });
    if (!response.ok) {
      throw new Error("Logout failed");
    }
  }

  socialLogin(provider: string, redirectUri: string): JsonValue {
    const base = this.config.issuer ?? "http://keycloak:8080/realms/livelattice";
    const normalized = provider.toLowerCase();
    return {
      provider: normalized,
      authorizationUrl: `${base}/broker/${encodeURIComponent(normalized)}/login?client_id=${encodeURIComponent(this.config.clientId)}&redirect_uri=${encodeURIComponent(redirectUri)}`
    };
  }

  mfaSetup(): JsonValue {
    return { status: "available", provider: "keycloak", type: "totp" };
  }

  mfaVerify(): JsonValue {
    return { status: "delegated", provider: "keycloak" };
  }

  async authorize(header: string | string[] | undefined): Promise<AuthDecision> {
    if (!this.config.required) {
      return { allowed: true };
    }
    const token = this.extractToken(header);
    if (!token) {
      return { allowed: false, statusCode: 401, message: "Missing bearer token" };
    }
    if (!this.config.jwksUri) {
      return { allowed: false, statusCode: 503, message: "JWKS URI is not configured" };
    }
    try {
      return await this.verifyAccessToken(token);
    } catch {
      return { allowed: false, statusCode: 401, message: "Invalid bearer token" };
    }
  }

  async close(): Promise<void> {
    await this.cache.close();
  }

  private async verifyAccessToken(token: string): Promise<AuthDecision> {
    const jwks = await this.loadJwks();
    const result = await jwtVerify(token, createLocalJWKSet(jwks), {
      issuer: this.config.issuer,
      audience: this.config.audience
    });
    const payload = result.payload;
    const roles = this.roles(payload.realm_access);
    return {
      allowed: true,
      subject: payload.sub,
      email: typeof payload.email === "string" ? payload.email : undefined,
      displayName: this.displayName(payload.name, payload.preferred_username, payload.email, payload.sub),
      roles
    };
  }

  private async loadJwks(): Promise<JsonValue> {
    const cacheKey = "auth:jwks:livelattice";
    const cached = await this.cache.getJson<JsonValue>(cacheKey);
    if (cached) {
      return cached;
    }
    const response = await fetch(this.config.jwksUri as string);
    if (!response.ok) {
      throw new Error("JWKS fetch failed");
    }
    const jwks = await response.json() as JsonValue;
    await this.cache.setJson(cacheKey, jwks, this.config.jwksTtlSeconds);
    return jwks;
  }

  private async exchangeToken(params: Record<string, string>): Promise<TokenResponse> {
    if (!this.config.tokenEndpoint) {
      throw new Error("Token endpoint is not configured");
    }
    const response = await fetch(this.config.tokenEndpoint, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: this.clientBody(params)
    });
    if (!response.ok) {
      const body = await response.text();
      throw new Error(body || "Token exchange failed");
    }
    return await response.json() as TokenResponse;
  }

  private clientBody(params: Record<string, string>): URLSearchParams {
    const body = new URLSearchParams({ client_id: this.config.clientId, ...params });
    if (this.config.clientSecret) {
      body.set("client_secret", this.config.clientSecret);
    }
    return body;
  }

  private async cacheSession(user: AuthUser): Promise<void> {
    await this.cache.setJson(`auth:session:${user.subject}`, {
      subject: user.subject,
      email: user.email,
      displayName: user.displayName,
      roles: user.roles
    }, this.config.sessionTtlSeconds);
  }

  private async provisionUser(user: AuthUser): Promise<void> {
    const response = await fetch(this.config.coreProvisionUrl, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-internal-auth-token": this.config.internalSecret,
        "x-auth-subject": user.subject,
        "x-auth-email": user.email,
        "x-auth-display-name": user.displayName
      },
      body: JSON.stringify({})
    });
    if (!response.ok) {
      throw new Error("User provisioning failed");
    }
  }

  private loginResponse(tokens: TokenResponse, user: AuthUser): LoginResponse {
    return {
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      idToken: tokens.id_token,
      expiresIn: tokens.expires_in,
      refreshExpiresIn: tokens.refresh_expires_in,
      tokenType: tokens.token_type,
      scope: tokens.scope,
      user
    };
  }

  private userFromDecision(decision: AuthDecision): AuthUser {
    if (!decision.allowed || !decision.subject) {
      throw new Error("Invalid token claims");
    }
    return {
      subject: decision.subject,
      email: decision.email ?? `${decision.subject}@livelattice.local`,
      displayName: decision.displayName ?? decision.email ?? decision.subject,
      roles: decision.roles ?? []
    };
  }

  private roles(value: unknown): string[] {
    if (typeof value !== "object" || value === null || !("roles" in value)) {
      return [];
    }
    const roles = (value as { roles?: unknown }).roles;
    return Array.isArray(roles) ? roles.filter((role): role is string => typeof role === "string") : [];
  }

  private displayName(...values: unknown[]): string | undefined {
    return values.find((value): value is string => typeof value === "string" && value.length > 0);
  }

  private extractToken(header: string | string[] | undefined): string | undefined {
    const current = Array.isArray(header) ? header[0] : header;
    if (!current) {
      return undefined;
    }
    const [scheme, token] = current.split(" ");
    return scheme?.toLowerCase() === "bearer" && token ? token : undefined;
  }
}
