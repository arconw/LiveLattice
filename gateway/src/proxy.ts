import type { FastifyReply, FastifyRequest } from "fastify";
import type { ServiceRouteMap } from "./config";

export interface IdentityHeaders {
  subject: string;
  email: string;
  displayName: string;
  roles: string[];
  internalSecret: string;
}

const hopByHopHeaders = new Set([
  "connection",
  "content-length",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade"
]);

const trustedHeaders = new Set([
  "x-internal-auth-token",
  "x-auth-subject",
  "x-auth-email",
  "x-auth-display-name",
  "x-auth-roles",
  "x-user-id"
]);

export class ProxyService {
  constructor(private readonly routes: ServiceRouteMap) {}

  async forward(request: FastifyRequest, reply: FastifyReply, identity?: IdentityHeaders): Promise<void> {
    const params = request.params as { service?: string };
    const service = params.service;
    if (!service || !this.routes[service]) {
      reply.code(404).send({ error: "Unknown upstream service", service });
      return;
    }
    const target = this.targetUrl(this.routes[service], service, request.raw.url ?? "");
    await this.forwardToTarget(request, reply, target, identity);
  }

  async forwardPrefix(request: FastifyRequest, reply: FastifyReply, baseUrl: string, prefix: string, identity?: IdentityHeaders): Promise<void> {
    const target = this.prefixedTargetUrl(baseUrl, prefix, request.raw.url ?? "");
    await this.forwardToTarget(request, reply, target, identity);
  }

  private async forwardToTarget(request: FastifyRequest, reply: FastifyReply, target: string, identity?: IdentityHeaders): Promise<void> {
    const response = await fetch(target, {
      method: request.method,
      headers: this.headers(request, identity),
      body: this.body(request),
      redirect: "manual"
    });
    reply.code(response.status);
    response.headers.forEach((value, key) => {
      if (!hopByHopHeaders.has(key.toLowerCase())) {
        reply.header(key, value);
      }
    });
    const data = Buffer.from(await response.arrayBuffer());
    reply.send(data);
  }

  private headers(request: FastifyRequest, identity?: IdentityHeaders): Headers {
    const headers = new Headers();
    for (const [key, value] of Object.entries(request.headers)) {
      const lowered = key.toLowerCase();
      if (hopByHopHeaders.has(lowered) || trustedHeaders.has(lowered) || value === undefined) {
        continue;
      }
      if (Array.isArray(value)) {
        for (const item of value) {
          headers.append(key, item);
        }
      } else {
        headers.set(key, String(value));
      }
    }
    const requestId = request.headers["x-request-id"];
    if (typeof requestId === "string") {
      headers.set("x-request-id", requestId);
    }
    if (!headers.has("content-type") && request.body && typeof request.body === "object" && !Buffer.isBuffer(request.body)) {
      headers.set("content-type", "application/json");
    }
    if (identity) {
      headers.set("x-internal-auth-token", identity.internalSecret);
      headers.set("x-auth-subject", identity.subject);
      headers.set("x-auth-email", identity.email);
      headers.set("x-auth-display-name", identity.displayName);
      headers.set("x-auth-roles", identity.roles.join(","));
      headers.set("x-user-id", identity.subject);
    }
    return headers;
  }

  private body(request: FastifyRequest): BodyInit | undefined {
    const method = request.method.toUpperCase();
    if (method === "GET" || method === "HEAD") {
      return undefined;
    }
    const current = request.body as unknown;
    if (current === undefined || current === null) {
      return undefined;
    }
    if (typeof current === "string" || current instanceof Uint8Array) {
      return current;
    }
    return JSON.stringify(current);
  }

  private targetUrl(baseUrl: string, service: string, rawUrl: string): string {
    const prefix = `/api/${service}`;
    const suffix = rawUrl.startsWith(prefix) ? rawUrl.slice(prefix.length) : "";
    const normalized = suffix.length === 0 ? "/" : suffix.startsWith("?") ? `/${suffix}` : suffix;
    return new URL(normalized, baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`).toString();
  }

  private prefixedTargetUrl(baseUrl: string, prefix: string, rawUrl: string): string {
    const suffix = rawUrl.startsWith(prefix) ? rawUrl.slice(prefix.length) : "";
    const normalized = suffix.length === 0 ? prefix : suffix.startsWith("?") ? `${prefix}${suffix}` : `${prefix}${suffix}`;
    return new URL(normalized, baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`).toString();
  }
}
