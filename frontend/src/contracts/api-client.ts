export type AppErrorShape = {
  status: number;
  code: string;
  message: string;
  fieldErrors?: Record<string, string[]>;
  requestId?: string;
  retryable: boolean;
};

export class AppError extends Error implements AppErrorShape {
  status: number;
  code: string;
  fieldErrors?: Record<string, string[]>;
  requestId?: string;
  retryable: boolean;

  constructor(error: AppErrorShape) {
    super(error.message);
    this.name = "AppError";
    this.status = error.status;
    this.code = error.code;
    this.fieldErrors = error.fieldErrors;
    this.requestId = error.requestId;
    this.retryable = error.retryable;
  }
}

export const TRUSTED_INTERNAL_HEADER_BLOCKLIST = [
  "x-internal-auth-token",
  "x-auth-subject",
  "x-auth-email",
  "x-auth-display-name",
  "x-auth-roles",
  "x-user-id"
] as const;

export type RequestOptions = {
  headers?: HeadersInit;
  signal?: AbortSignal;
  requestId?: string;
};

export type GatewayClient = {
  get<T>(path: string, options?: RequestOptions): Promise<T>;
  post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>;
  patch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>;
  delete<T>(path: string, options?: RequestOptions): Promise<T>;
};

export type GatewayClientOptions = {
  fetchImpl?: typeof fetch;
  getAccessToken?: () => string | null | Promise<string | null>;
  refreshOnUnauthorized?: () => Promise<string | null>;
  requestIdFactory?: () => string;
};

const retryableStatuses = new Set([408, 409, 429, 500, 502, 503, 504]);

export function createWorkspaceCacheKey(workspaceSlug: string, ...parts: Array<string | number | boolean | null | undefined>) {
  if (!workspaceSlug.trim()) {
    throw new AppError({
      status: 0,
      code: "WORKSPACE_CONTEXT_REQUIRED",
      message: "Workspace context is required for workspace-scoped cache keys.",
      retryable: false
    });
  }

  return ["workspace", workspaceSlug, ...parts.filter((part) => part !== undefined)] as const;
}

export function createGatewayClient(options: GatewayClientOptions = {}): GatewayClient {
  const fetchImpl = options.fetchImpl ?? fetch;

  async function request<T>(method: string, path: string, body?: unknown, requestOptions: RequestOptions = {}, retryAttempted = false, tokenOverride?: string) {
    assertGatewayPath(path);

    const headers = sanitizeHeaders(requestOptions.headers);
    const token = tokenOverride ?? await options.getAccessToken?.();

    if (token) {
      headers.set("authorization", `Bearer ${token}`);
    }

    const requestId = requestOptions.requestId ?? options.requestIdFactory?.();

    if (requestId) {
      headers.set("x-request-id", requestId);
    }

    const init: RequestInit = {
      method,
      headers,
      signal: requestOptions.signal
    };

    if (body !== undefined) {
      headers.set("content-type", "application/json");
      init.body = JSON.stringify(body);
    }

    let response: Response;

    try {
      response = await fetchImpl(path, init);
    } catch (error) {
      throw normalizeNetworkError(error);
    }

    if (!response.ok) {
      if (response.status === 401 && !retryAttempted && options.refreshOnUnauthorized) {
        let refreshedToken: string | null;

        try {
          refreshedToken = await options.refreshOnUnauthorized();
        } catch {
          refreshedToken = null;
        }

        if (refreshedToken) {
          return request<T>(method, path, body, requestOptions, true, refreshedToken);
        }
      }

      throw await normalizeResponseError(response);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    const contentType = response.headers.get("content-type") ?? "";

    if (contentType.includes("application/json")) {
      return (await response.json()) as T;
    }

    return (await response.text()) as T;
  }

  return {
    get: <T,>(path: string, requestOptions?: RequestOptions) => request<T>("GET", path, undefined, requestOptions),
    post: <T,>(path: string, body?: unknown, requestOptions?: RequestOptions) => request<T>("POST", path, body, requestOptions),
    patch: <T,>(path: string, body?: unknown, requestOptions?: RequestOptions) => request<T>("PATCH", path, body, requestOptions),
    delete: <T,>(path: string, requestOptions?: RequestOptions) => request<T>("DELETE", path, undefined, requestOptions)
  };
}

function assertGatewayPath(path: string) {
  if (!path.startsWith("/") || path.startsWith("//") || /^[a-z][a-z0-9+.-]*:\/\//i.test(path)) {
    throw new AppError({
      status: 0,
      code: "GATEWAY_PATH_REQUIRED",
      message: "Frontend requests must use Gateway-relative paths.",
      retryable: false
    });
  }
}

function sanitizeHeaders(headers: HeadersInit | undefined) {
  const sanitized = new Headers(headers);

  TRUSTED_INTERNAL_HEADER_BLOCKLIST.forEach((header) => {
    sanitized.delete(header);
  });

  return sanitized;
}

async function normalizeResponseError(response: Response) {
  const requestId = response.headers.get("x-request-id") ?? undefined;
  const payload = await readErrorPayload(response);
  const code = payload.code ?? defaultCodeForStatus(response.status);
  const message = payload.message ?? defaultMessageForStatus(response.status);

  return new AppError({
    status: response.status,
    code,
    message,
    fieldErrors: payload.fieldErrors,
    requestId,
    retryable: retryableStatuses.has(response.status) || response.status >= 500
  });
}

async function readErrorPayload(response: Response): Promise<Partial<AppErrorShape>> {
  const contentType = response.headers.get("content-type") ?? "";

  if (!contentType.includes("application/json")) {
    return {};
  }

  try {
    const payload = (await response.json()) as unknown;

    if (isErrorPayload(payload)) {
      return normalizeErrorPayload(payload);
    }
  } catch {
    return {};
  }

  return {};
}

function isErrorPayload(payload: unknown): payload is Partial<AppErrorShape> & { error?: unknown } {
  return typeof payload === "object" && payload !== null;
}

function normalizeErrorPayload(payload: Partial<AppErrorShape> & { error?: unknown }) {
  const code = typeof payload.code === "string" ? payload.code : typeof payload.error === "string" ? payload.error : undefined;
  const message = typeof payload.message === "string" ? payload.message : typeof payload.error === "string" ? payload.error : undefined;

  return {
    ...payload,
    code,
    message
  };
}

function normalizeNetworkError(error: unknown) {
  if (error instanceof AppError) {
    return error;
  }

  if (error instanceof DOMException && error.name === "AbortError") {
    return new AppError({
      status: 0,
      code: "REQUEST_ABORTED",
      message: "The request was cancelled.",
      retryable: false
    });
  }

  return new AppError({
    status: 0,
    code: "NETWORK_ERROR",
    message: "The Gateway request could not be completed.",
    retryable: true
  });
}

function defaultCodeForStatus(status: number) {
  const codes: Record<number, string> = {
    400: "VALIDATION_ERROR",
    401: "AUTHENTICATION_REQUIRED",
    403: "PERMISSION_DENIED",
    404: "NOT_FOUND",
    409: "CONFLICT",
    413: "PAYLOAD_TOO_LARGE",
    429: "RATE_LIMITED"
  };

  return codes[status] ?? (status >= 500 ? "SERVICE_ERROR" : "REQUEST_FAILED");
}

function defaultMessageForStatus(status: number) {
  const messages: Record<number, string> = {
    400: "The request could not be validated.",
    401: "Authentication is required.",
    403: "You do not have permission to perform this action.",
    404: "The requested resource was not found.",
    409: "The request conflicts with the current server version.",
    413: "The upload is too large.",
    429: "Too many requests. Try again shortly."
  };

  return messages[status] ?? (status >= 500 ? "The service is temporarily unavailable." : "The request failed.");
}
