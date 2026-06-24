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
  checkStatus("search health succeeds", http.get(`${config.search}/health`), [200]);
  checkStatus("search rejects unauthenticated gateway request", http.get(`${config.gateway}/api/search/search?q=k6`), [401]);
  if (skipWhenMissing("authenticated search", data.auth)) {
    return;
  }
  checkStatus("search returns authenticated results", http.get(`${config.gateway}/api/search/search?q=k6&size=5`, bearerParams(data.auth)), [200]);
  checkStatus("search validation rejects blank query", http.get(`${config.gateway}/api/search/search?q=&size=5`, bearerParams(data.auth)), [400]);
  checkStatus("search suggestion returns authenticated results", http.get(`${config.gateway}/api/search/search/suggest?q=k6`, bearerParams(data.auth)), [200]);
}
