import http from "k6/http";
import { check, fail } from "k6";
import { Rate } from "k6/metrics";

export const unexpectedResponseRate = new Rate("unexpected_response_rate");
export const skippedScenarioRate = new Rate("skipped_scenario_rate");

export function setExpectedResponseStatuses() {
  http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 400, 401, 403, 404, 409, 415, 422));
}

export function serviceConfig() {
  const gateway = trimTrailingSlash(env("BASE_URL", env("GATEWAY_BASE_URL", "http://gateway:3000")));
  return {
    gateway,
    core: trimTrailingSlash(env("CORE_BASE_URL", "http://core:8080")),
    realtime: trimTrailingSlash(env("REALTIME_BASE_URL", "http://realtime:3002")),
    search: trimTrailingSlash(env("SEARCH_BASE_URL", "http://search:8081")),
    notifications: trimTrailingSlash(env("NOTIFICATIONS_BASE_URL", "http://notifications:8082")),
    importExport: trimTrailingSlash(env("IMPORT_EXPORT_BASE_URL", "http://import-export:8083")),
    auditLog: trimTrailingSlash(env("AUDIT_LOG_BASE_URL", "http://audit-log:8084")),
    backgroundJobs: trimTrailingSlash(env("BACKGROUND_JOBS_BASE_URL", "http://background-jobs:8085")),
    prometheus: trimTrailingSlash(env("PROMETHEUS_BASE_URL", "http://prometheus:9090")),
    authClientId: env("K6_AUTH_CLIENT_ID", "livelattice-web")
  };
}

export function publicServices(config = serviceConfig()) {
  return [
    ["gateway", config.gateway],
    ["core", config.core],
    ["realtime", config.realtime],
    ["search", config.search],
    ["notifications", config.notifications],
    ["import-export", config.importExport],
    ["audit-log", config.auditLog],
    ["background-jobs", config.backgroundJobs]
  ];
}

export function checkStatus(label, response, statuses) {
  const accepted = Array.isArray(statuses) ? statuses : [statuses];
  const ok = check(response, {
    [label]: (r) => accepted.includes(r.status)
  });
  unexpectedResponseRate.add(ok ? 0 : 1);
  return ok;
}

export function checkJsonField(label, response, field, expected) {
  const ok = check(response, {
    [label]: (r) => {
      const body = parseJson(r);
      return body !== null && body[field] === expected;
    }
  });
  unexpectedResponseRate.add(ok ? 0 : 1);
  return ok;
}

export function parseJson(response) {
  try {
    return response.json();
  } catch {
    return null;
  }
}

export function jsonParams(headers = {}) {
  return {
    headers: Object.assign({
      "content-type": "application/json",
      "x-request-id": requestId()
    }, headers)
  };
}

export function bearerParams(auth, headers = {}) {
  const token = typeof auth === "string" ? auth : auth?.accessToken;
  if (!token) {
    return jsonParams(headers);
  }
  return jsonParams(Object.assign({ authorization: `Bearer ${token}` }, headers));
}

export function bearerOnlyParams(auth, headers = {}) {
  const token = typeof auth === "string" ? auth : auth?.accessToken;
  const merged = Object.assign({ "x-request-id": requestId() }, headers);
  if (token) {
    merged.authorization = `Bearer ${token}`;
  }
  return { headers: merged };
}

export function apiKeyParams(apiKey, headers = {}) {
  return jsonParams(Object.assign({ "x-api-key": apiKey }, headers));
}

export function getJson(url, params = {}) {
  return http.get(url, params);
}

export function postJson(url, body, params = {}) {
  return http.post(url, JSON.stringify(body), mergeParams(jsonParams(), params));
}

export function patchJson(url, body, params = {}) {
  return http.patch(url, JSON.stringify(body), mergeParams(jsonParams(), params));
}

export function maybeLogin(config = serviceConfig()) {
  const providedToken = env("K6_AUTH_TOKEN", "");
  if (providedToken.length > 0) {
    return { accessToken: providedToken };
  }
  const username = env("K6_AUTH_USERNAME", "");
  const password = env("K6_AUTH_PASSWORD", "");
  if (username.length === 0 || password.length === 0) {
    skippedScenarioRate.add(1);
    return null;
  }
  const response = postJson(`${config.gateway}/auth/login`, { email: username, password }, jsonParams());
  if (!checkStatus("auth login returns token", response, [200])) {
    fail("auth login failed");
  }
  const body = parseJson(response);
  if (!body?.accessToken) {
    unexpectedResponseRate.add(1);
    fail("auth login response did not include accessToken");
  }
  return {
    accessToken: body.accessToken,
    refreshToken: body.refreshToken,
    user: body.user
  };
}

export function setupCoreData(flags = {}) {
  const config = serviceConfig();
  const auth = maybeLogin(config);
  const context = {
    config,
    auth,
    workspace: null,
    canvas: null,
    dashboard: null,
    widget: null,
    apiKey: null
  };
  if (!auth) {
    return context;
  }
  context.workspace = workspaceFromEnv() ?? createWorkspace(config, auth);
  if (context.workspace && flags.canvas) {
    context.canvas = createCanvas(config, auth, context.workspace.id);
  }
  if (context.workspace && flags.dashboard) {
    context.dashboard = createDashboard(config, auth, context.workspace.id);
  }
  if (context.dashboard && flags.widget) {
    context.widget = createMarkdownWidget(config, auth, context.dashboard.id);
  }
  if (context.workspace && flags.apiKey) {
    context.apiKey = createApiKey(config, auth, context.workspace.id);
  }
  return context;
}

export function createWorkspace(config, auth) {
  const suffix = randomSuffix();
  const response = postJson(`${config.gateway}/api/core/workspaces`, {
    name: `k6 workspace ${suffix}`,
    slug: `k6-${suffix}`
  }, bearerParams(auth));
  if (!checkStatus("core creates workspace", response, [201])) {
    return null;
  }
  return parseJson(response);
}

export function createCanvas(config, auth, workspaceId) {
  const response = postJson(`${config.gateway}/api/core/canvases`, {
    workspaceId,
    title: `k6 canvas ${randomSuffix()}`
  }, bearerParams(auth));
  if (!checkStatus("core creates canvas", response, [201])) {
    return null;
  }
  return parseJson(response);
}

export function createDashboard(config, auth, workspaceId) {
  const response = postJson(`${config.gateway}/api/core/dashboards`, {
    workspaceId,
    title: `k6 dashboard ${randomSuffix()}`,
    description: "k6 performance smoke",
    layout: { columns: 12, gap: 16, widgets: [] },
    timeRange: { type: "relative", value: "24h" },
    autoRefresh: 0
  }, bearerParams(auth));
  if (!checkStatus("core creates dashboard", response, [201])) {
    return null;
  }
  return parseJson(response);
}

export function createMarkdownWidget(config, auth, dashboardId) {
  const response = postJson(`${config.gateway}/api/core/dashboards/${dashboardId}/widgets`, {
    type: "MARKDOWN",
    title: `k6 widget ${randomSuffix()}`,
    query: { markdown: "k6" },
    options: { markdown: "k6" },
    position: { x: 0, y: 0, w: 4, h: 3 }
  }, bearerParams(auth));
  if (!checkStatus("core creates markdown widget", response, [201])) {
    return null;
  }
  return parseJson(response);
}

export function createApiKey(config, auth, workspaceId) {
  const response = postJson(`${config.gateway}/auth/keys`, {
    workspaceId,
    name: `k6 key ${randomSuffix()}`,
    permissions: ["workspace:read", "canvas:read", "dashboard:read", "widget:read"]
  }, bearerParams(auth));
  if (!checkStatus("core creates API key", response, [201])) {
    return null;
  }
  return parseJson(response);
}

export function exercisePublicHealth(config = serviceConfig()) {
  for (const [name, baseUrl] of publicServices(config)) {
    const health = getJson(`${baseUrl}/health`);
    checkStatus(`${name} health is public`, health, [200]);
    checkJsonField(`${name} health reports UP`, health, "status", "UP");
    const ready = getJson(`${baseUrl}/ready`);
    checkStatus(`${name} readiness is public`, ready, [200]);
  }
}

export function skipWhenMissing(label, value) {
  if (value) {
    return false;
  }
  skippedScenarioRate.add(1);
  check(null, { [`${label} skipped`]: () => true });
  return true;
}

export function randomSuffix() {
  return `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
}

export function testUuid(index = 0) {
  return `00000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`;
}

export function env(name, fallback = "") {
  const current = __ENV[name];
  return current === undefined || current.length === 0 ? fallback : current;
}

function workspaceFromEnv() {
  const workspaceId = env("K6_WORKSPACE_ID", "");
  return workspaceId.length > 0 ? { id: workspaceId } : null;
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, "");
}

function requestId() {
  return `k6-${randomSuffix()}`;
}

function mergeParams(base, extra) {
  return {
    headers: Object.assign({}, base.headers ?? {}, extra.headers ?? {}),
    tags: Object.assign({}, base.tags ?? {}, extra.tags ?? {})
  };
}
