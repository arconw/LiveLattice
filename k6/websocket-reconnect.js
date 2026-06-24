import ws from "k6/ws";
import { check } from "k6";
import { Trend } from "k6/metrics";
import { profileOptions, reconnectScenarios, websocketThresholds } from "./options.js";
import { skippedScenarioRate, unexpectedResponseRate, serviceConfig } from "./helpers.js";

export const websocketReconnectTime = new Trend("websocket_reconnect_time");

export const options = profileOptions(__ENV.K6_PROFILE, websocketThresholds, {
  baseline: reconnectScenarios()
});

export default function () {
  const config = serviceConfig();
  const wsUrl = config.realtime.replace(/^http/, "ws");
  const started = Date.now();
  const result = ws.connect(`${wsUrl}/socket.io/?EIO=4&transport=websocket`, {}, (socket) => {
    socket.on("open", () => {
      websocketReconnectTime.add(Date.now() - started);
      socket.send("40/ws/00000000-0000-4000-8000-000000000001,{\"token\":\"k6-invalid-token\"}");
      socket.setTimeout(() => socket.close(), 500);
    });
    socket.on("message", () => {});
    socket.on("error", () => {});
    socket.setTimeout(() => socket.close(), 1500);
  });
  const ok = check(result, {
    "realtime websocket endpoint accepts or rejects invalid socket auth predictably": (r) => r && [101, 400, 401].includes(r.status)
  });
  if (!result || result.status !== 101) {
    websocketReconnectTime.add(Date.now() - started);
  }
  unexpectedResponseRate.add(ok ? 0 : 1);
  if (!result) {
    skippedScenarioRate.add(1);
  }
}
