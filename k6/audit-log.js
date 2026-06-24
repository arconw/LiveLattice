import http from "k6/http";
import { profileOptions, smokeThresholds } from "./options.js";
import { bearerParams, checkStatus, serviceConfig, setExpectedResponseStatuses, setupCoreData, skipWhenMissing } from "./helpers.js";

setExpectedResponseStatuses();

export const options = profileOptions(__ENV.K6_PROFILE, smokeThresholds);

export function setup() {
  return setupCoreData({});
}

export default function (data) {
  const config = data.config ?? serviceConfig();
  checkStatus("audit-log health succeeds", http.get(`${config.auditLog}/health`), [200]);
  checkStatus("audit-log rejects unauthenticated gateway request", http.get(`${config.gateway}/api/audit-log/audit-log`), [401]);
  if (skipWhenMissing("authenticated audit-log", data.auth && data.workspace)) {
    return;
  }
  checkStatus("audit-log scoped query succeeds", http.get(`${config.gateway}/api/audit-log/audit-log?workspace_id=${data.workspace.id}&page=1&size=5`, bearerParams(data.auth)), [200]);
  checkStatus("audit-log global query requires scope or admin", http.get(`${config.gateway}/api/audit-log/audit-log?page=1&size=5`, bearerParams(data.auth)), [403]);
}
