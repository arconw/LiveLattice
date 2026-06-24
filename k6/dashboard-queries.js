import http from "k6/http";
import { dashboardScenarios, dashboardThresholds, profileOptions } from "./options.js";
import {
  bearerParams,
  checkStatus,
  serviceConfig,
  setExpectedResponseStatuses,
  setupCoreData,
  skipWhenMissing
} from "./helpers.js";

setExpectedResponseStatuses();

export const options = profileOptions(__ENV.K6_PROFILE, dashboardThresholds, {
  baseline: dashboardScenarios()
});

export function setup() {
  return setupCoreData({ dashboard: true, widget: true });
}

export default function (data) {
  const config = data.config ?? serviceConfig();
  checkStatus("core health succeeds for dashboard smoke", http.get(`${config.core}/health`), [200]);
  checkStatus("dashboard data rejects unauthenticated gateway request", http.get(`${config.gateway}/api/core/dashboards/00000000-0000-4000-8000-000000000001/data`), [401]);
  if (skipWhenMissing("authenticated dashboard query", data.auth && data.dashboard && data.widget)) {
    return;
  }
  checkStatus("dashboard data loads markdown widget data", http.get(`${config.gateway}/api/core/dashboards/${data.dashboard.id}/data`, bearerParams(data.auth)), [200]);
  checkStatus("widget data loads markdown widget data", http.get(`${config.gateway}/api/core/dashboards/${data.dashboard.id}/widgets/${data.widget.id}/data`, bearerParams(data.auth)), [200]);
  checkStatus("dashboard query validation rejects bad dashboard id", http.get(`${config.gateway}/api/core/dashboards/not-a-uuid/data`, bearerParams(data.auth)), [400]);
}
