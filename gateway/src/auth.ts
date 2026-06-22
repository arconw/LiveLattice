import { createRemoteJWKSet, jwtVerify } from "jose";
import type { AuthConfig } from "./config";

export interface AuthDecision {
  allowed: boolean;
  statusCode?: number;
  message?: string;
  subject?: string;
}

export class AuthService {
  private jwks?: ReturnType<typeof createRemoteJWKSet>;

  constructor(private readonly config: AuthConfig) {}

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
      const result = await jwtVerify(token, this.keys(), {
        issuer: this.config.issuer,
        audience: this.config.audience
      });
      return { allowed: true, subject: result.payload.sub };
    } catch {
      return { allowed: false, statusCode: 401, message: "Invalid bearer token" };
    }
  }

  private keys(): ReturnType<typeof createRemoteJWKSet> {
    if (!this.jwks) {
      this.jwks = createRemoteJWKSet(new URL(this.config.jwksUri as string));
    }
    return this.jwks;
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
