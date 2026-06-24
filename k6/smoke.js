import http from "k6/http";
import { profileOptions, smokeThresholds } from "./options.js";
import { checkStatus, exercisePublicHealth, serviceConfig, setExpectedResponseStatuses } from "./helpers.js";

setExpectedResponseStatuses();

export const options = profileOptions(__ENV.K6_PROFILE, smokeThresholds);

export default function () {
  const config = serviceConfig();
  exercisePublicHealth(config);
  checkStatus("gateway rejects unauthenticated core request", http.get(`${config.gateway}/api/core/workspaces`), [401]);
  checkStatus("gateway rejects unauthenticated search request", http.get(`${config.gateway}/api/search/search?q=k6`), [401]);
  checkStatus("gateway rejects unauthenticated notifications request", http.get(`${config.gateway}/api/notifications/notifications`), [401]);
  checkStatus("gateway rejects unauthenticated import-export request", http.post(`${config.gateway}/api/import-export/export/${testCanvasId()}`), [401]);
  checkStatus("gateway rejects unauthenticated audit-log request", http.get(`${config.gateway}/api/audit-log/audit-log`), [401]);
  checkStatus("gateway rejects unauthenticated background-jobs request", http.get(`${config.gateway}/api/background-jobs/jobs`), [401]);
  checkStatus("core rejects missing trusted identity", http.get(`${config.core}/workspaces`), [401]);
  checkStatus("search rejects missing trusted identity", http.get(`${config.search}/search?q=k6`), [401]);
  checkStatus("notifications rejects missing trusted identity", http.get(`${config.notifications}/notifications`), [401]);
  checkStatus("import-export rejects missing trusted identity", http.post(`${config.importExport}/export/${testCanvasId()}`), [401]);
  checkStatus("audit-log rejects missing trusted identity", http.get(`${config.auditLog}/audit-log`), [401]);
  checkStatus("background-jobs rejects missing trusted identity", http.get(`${config.backgroundJobs}/jobs`), [401]);
}

function testCanvasId() {
  return "00000000-0000-4000-8000-000000000001";
}
