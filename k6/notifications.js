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
  checkStatus("notifications health succeeds", http.get(`${config.notifications}/health`), [200]);
  checkStatus("notifications rejects unauthenticated gateway request", http.get(`${config.gateway}/api/notifications/notifications`), [401]);
  if (skipWhenMissing("authenticated notifications", data.auth)) {
    return;
  }
  checkStatus("notifications unread count succeeds", http.get(`${config.gateway}/api/notifications/notifications/unread-count`, bearerParams(data.auth)), [200]);
  checkStatus("notifications creation enforces validation or role", http.post(`${config.gateway}/api/notifications/notifications`, JSON.stringify({
    recipientIds: [],
    type: "SYSTEM_ANNOUNCEMENT",
    channels: []
  }), bearerParams(data.auth)), [400, 403]);
}
