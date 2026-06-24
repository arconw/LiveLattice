import http from "k6/http";
import { importExportThresholds, importScenarios, profileOptions } from "./options.js";
import {
  bearerOnlyParams,
  checkStatus,
  serviceConfig,
  setExpectedResponseStatuses,
  setupCoreData,
  skipWhenMissing
} from "./helpers.js";

setExpectedResponseStatuses();

export const options = profileOptions(__ENV.K6_PROFILE, importExportThresholds, {
  baseline: importScenarios()
});

export function setup() {
  return setupCoreData({});
}

export default function (data) {
  const config = data.config ?? serviceConfig();
  checkStatus("import-export health succeeds", http.get(`${config.importExport}/health`), [200]);
  checkStatus("import-export rejects unauthenticated gateway request", http.post(`${config.gateway}/api/import-export/export/00000000-0000-4000-8000-000000000001`), [401]);
  if (skipWhenMissing("authenticated import-export", data.auth && data.workspace)) {
    return;
  }
  const options = JSON.stringify({ workspaceId: data.workspace.id, title: `k6 import ${Date.now()}` });
  const validPayload = {
    file: http.file("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"></svg>", "k6.svg", "image/svg+xml"),
    options
  };
  checkStatus("import-export imports small svg", http.post(`${config.gateway}/api/import-export/import`, validPayload, bearerOnlyParams(data.auth)), [200, 202]);
  const invalidPayload = {
    file: http.file("not an importable canvas", "k6.txt", "text/plain"),
    options
  };
  checkStatus("import-export rejects invalid import file", http.post(`${config.gateway}/api/import-export/import`, invalidPayload, bearerOnlyParams(data.auth)), [400, 415, 422]);
}
