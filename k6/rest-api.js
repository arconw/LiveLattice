import http from "k6/http";
import { profileOptions, restApiScenarios, restThresholds } from "./options.js";
import {
  apiKeyParams,
  bearerParams,
  checkStatus,
  serviceConfig,
  setExpectedResponseStatuses,
  setupCoreData,
  skipWhenMissing
} from "./helpers.js";

setExpectedResponseStatuses();

export const options = profileOptions(__ENV.K6_PROFILE, restThresholds, {
  baseline: restApiScenarios()
});

export function setup() {
  return setupCoreData({ canvas: true, dashboard: true, widget: true, apiKey: true });
}

export default function (data) {
  const config = data.config ?? serviceConfig();
  checkStatus("gateway health succeeds", http.get(`${config.gateway}/health`), [200]);
  checkStatus("core validation fails without auth before validation", http.post(`${config.gateway}/api/core/workspaces`, JSON.stringify({}), {
    headers: { "content-type": "application/json" }
  }), [401]);
  if (skipWhenMissing("authenticated REST", data.auth && data.workspace)) {
    return;
  }
  checkStatus("core lists authenticated workspaces", http.get(`${config.gateway}/api/core/workspaces`, bearerParams(data.auth)), [200]);
  checkStatus("core reads created workspace", http.get(`${config.gateway}/api/core/workspaces/${data.workspace.id}`, bearerParams(data.auth)), [200]);
  checkStatus("core rejects invalid workspace payload", http.post(`${config.gateway}/api/core/workspaces`, JSON.stringify({ name: "" }), bearerParams(data.auth)), [400]);
  if (data.canvas) {
    checkStatus("core reads created canvas", http.get(`${config.gateway}/api/core/canvases/${data.canvas.id}`, bearerParams(data.auth)), [200]);
  }
  if (data.dashboard) {
    checkStatus("core reads created dashboard", http.get(`${config.gateway}/api/core/dashboards/${data.dashboard.id}`, bearerParams(data.auth)), [200]);
  }
  if (data.widget && data.dashboard) {
    checkStatus("core lists dashboard widgets", http.get(`${config.gateway}/api/core/dashboards/${data.dashboard.id}/widgets`, bearerParams(data.auth)), [200]);
  }
  if (data.apiKey?.token && data.workspace) {
    checkStatus("core API key reads workspace", http.get(`${config.gateway}/api/core/workspaces/${data.workspace.id}`, apiKeyParams(data.apiKey.token)), [200]);
    checkStatus("core rejects invalid API key", http.get(`${config.gateway}/api/core/workspaces/${data.workspace.id}`, apiKeyParams("ll.invalid.invalid")), [401]);
  }
}
