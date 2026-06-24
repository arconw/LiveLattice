import http from "k6/http";
import { collaborationScenarios, collaborationThresholds, profileOptions } from "./options.js";
import { Trend } from "k6/metrics";
import { checkStatus, serviceConfig, setExpectedResponseStatuses } from "./helpers.js";

export const websocketAckLatency = new Trend("websocket_ack_latency");

setExpectedResponseStatuses();

export const options = profileOptions(__ENV.K6_PROFILE, collaborationThresholds, {
  baseline: collaborationScenarios()
});

export default function () {
  const config = serviceConfig();
  const started = Date.now();
  checkStatus("realtime health succeeds before collaboration smoke", http.get(`${config.realtime}/health`), [200]);
  checkStatus("realtime socket.io polling endpoint is reachable", http.get(`${config.realtime}/socket.io/?EIO=4&transport=polling&t=${Date.now()}`), [200, 400]);
  websocketAckLatency.add(Date.now() - started);
}
