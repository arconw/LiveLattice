import http from "k6/http";
import { profileOptions, smokeThresholds } from "./options.js";
import { bearerParams, checkStatus, parseJson, serviceConfig, setExpectedResponseStatuses, setupCoreData, skipWhenMissing } from "./helpers.js";

setExpectedResponseStatuses();

export const options = profileOptions(__ENV.K6_PROFILE, smokeThresholds);

export function setup() {
  return setupCoreData({});
}

export default function (data) {
  const config = data.config ?? serviceConfig();
  checkStatus("background-jobs health succeeds", http.get(`${config.backgroundJobs}/health`), [200]);
  checkStatus("background-jobs rejects unauthenticated gateway request", http.get(`${config.gateway}/api/background-jobs/jobs`), [401]);
  if (skipWhenMissing("authenticated background-jobs", data.auth)) {
    return;
  }
  checkStatus("background-jobs lists own jobs", http.get(`${config.gateway}/api/background-jobs/jobs?page=0&size=5`, bearerParams(data.auth)), [200]);
  const createResponse = http.post(`${config.gateway}/api/background-jobs/jobs`, JSON.stringify({
    type: "NOOP",
    payload: { source: "k6" },
    priority: 50,
    maxRetries: 0
  }), bearerParams(data.auth));
  if (checkStatus("background-jobs enqueues noop job", createResponse, [202])) {
    const job = parseJson(createResponse);
    if (job?.id) {
      checkStatus("background-jobs reads noop job", http.get(`${config.gateway}/api/background-jobs/jobs/${job.id}`, bearerParams(data.auth)), [200]);
    }
  }
  checkStatus("background-jobs rejects unsupported job type", http.post(`${config.gateway}/api/background-jobs/jobs`, JSON.stringify({
    type: "UNSUPPORTED_K6_JOB",
    payload: {}
  }), bearerParams(data.auth)), [400]);
}
