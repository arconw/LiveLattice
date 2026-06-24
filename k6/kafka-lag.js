import http from "k6/http";
import { Trend } from "k6/metrics";
import { kafkaBurstScenarios, kafkaLagThresholds, profileOptions } from "./options.js";
import { checkStatus, env, parseJson, serviceConfig, setExpectedResponseStatuses, skippedScenarioRate } from "./helpers.js";

export const kafkaConsumerLagObserved = new Trend("kafka_consumer_lag_observed");

setExpectedResponseStatuses();

export const options = profileOptions(__ENV.K6_PROFILE, kafkaLagThresholds, {
  baseline: kafkaBurstScenarios()
});

export default function () {
  const config = serviceConfig();
  checkStatus("gateway health succeeds before kafka lag smoke", http.get(`${config.gateway}/health`), [200]);
  if (env("K6_SKIP_PROMETHEUS", "false") === "true") {
    skippedScenarioRate.add(1);
    kafkaConsumerLagObserved.add(0);
    return;
  }
  const health = http.get(`${config.prometheus}/-/healthy`);
  if (!checkStatus("prometheus health succeeds", health, [200])) {
    kafkaConsumerLagObserved.add(0);
    return;
  }
  const query = encodeURIComponent("max(kafka_consumer_lag) or vector(0)");
  const response = http.get(`${config.prometheus}/api/v1/query?query=${query}`);
  if (!checkStatus("prometheus kafka lag query succeeds", response, [200])) {
    kafkaConsumerLagObserved.add(0);
    return;
  }
  const body = parseJson(response);
  const value = Number(body?.data?.result?.[0]?.value?.[1] ?? 0);
  kafkaConsumerLagObserved.add(Number.isFinite(value) ? value : 0);
}
