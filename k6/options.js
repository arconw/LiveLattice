const defaultSmokeScenarios = {
  smoke: {
    executor: "shared-iterations",
    vus: intEnv("K6_SMOKE_VUS", 1),
    iterations: intEnv("K6_SMOKE_ITERATIONS", 1),
    maxDuration: env("K6_SMOKE_MAX_DURATION", "2m")
  }
};

export const smokeThresholds = {
  checks: ["rate>0.99"],
  unexpected_response_rate: ["rate<0.01"],
  http_req_duration: [`p(95)<${intEnv("K6_HTTP_P95_MS", 1000)}`]
};

export const restThresholds = mergeThresholds(smokeThresholds, {
  http_req_duration: [`p(95)<${intEnv("K6_REST_P95_MS", 200)}`]
});

export const dashboardThresholds = mergeThresholds(smokeThresholds, {
  http_req_duration: [`p(95)<${intEnv("K6_DASHBOARD_P95_MS", 500)}`]
});

export const importExportThresholds = mergeThresholds(smokeThresholds, {
  http_req_duration: [`p(95)<${intEnv("K6_IMPORT_P95_MS", 30000)}`]
});

export const collaborationThresholds = mergeThresholds(smokeThresholds, {
  websocket_ack_latency: [`p(99)<${intEnv("K6_WS_ACK_P99_MS", 200)}`]
});

export const websocketThresholds = mergeThresholds(smokeThresholds, {
  websocket_reconnect_time: [`p(95)<${intEnv("K6_RECONNECT_P95_MS", 2000)}`]
});

export const kafkaLagThresholds = mergeThresholds(smokeThresholds, {
  kafka_consumer_lag_observed: [`max<${intEnv("K6_KAFKA_MAX_LAG", 1000)}`]
});

export function profileOptions(profile = env("K6_PROFILE", "smoke"), thresholds = smokeThresholds, scenariosByProfile = {}) {
  const scenarios = scenariosByProfile[profile] ?? defaultSmokeScenarios;
  return {
    scenarios,
    thresholds,
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"]
  };
}

export function restApiScenarios() {
  return {
    rest_api: {
      executor: "constant-arrival-rate",
      rate: intEnv("K6_REST_RATE", 1000),
      timeUnit: "1s",
      duration: env("K6_REST_DURATION", "5m"),
      preAllocatedVUs: intEnv("K6_REST_PREALLOCATED_VUS", 100),
      maxVUs: intEnv("K6_REST_MAX_VUS", 200)
    }
  };
}

export function collaborationScenarios() {
  return {
    canvas_collaboration: {
      executor: "constant-vus",
      vus: intEnv("K6_WS_VUS", 50),
      duration: env("K6_WS_DURATION", "3m")
    }
  };
}

export function dashboardScenarios() {
  return {
    dashboard_queries: {
      executor: "constant-vus",
      vus: intEnv("K6_DASHBOARD_VUS", 100),
      duration: env("K6_DASHBOARD_DURATION", "3m")
    }
  };
}

export function importScenarios() {
  return {
    import_export: {
      executor: "constant-vus",
      vus: intEnv("K6_IMPORT_VUS", 10),
      duration: env("K6_IMPORT_DURATION", "3m")
    }
  };
}

export function reconnectScenarios() {
  return {
    websocket_reconnect: {
      executor: "constant-vus",
      vus: intEnv("K6_RECONNECT_VUS", 100),
      duration: env("K6_RECONNECT_DURATION", "3m")
    }
  };
}

export function kafkaBurstScenarios() {
  return {
    kafka_lag: {
      executor: "constant-arrival-rate",
      rate: intEnv("K6_KAFKA_OPS_RATE", 5000),
      timeUnit: "1s",
      duration: env("K6_KAFKA_BURST_DURATION", "1m"),
      preAllocatedVUs: intEnv("K6_KAFKA_PREALLOCATED_VUS", 100),
      maxVUs: intEnv("K6_KAFKA_MAX_VUS", 300)
    }
  };
}

export function mergeThresholds(...sets) {
  return Object.assign({}, ...sets);
}

function env(name, fallback) {
  const current = __ENV[name];
  return current === undefined || current.length === 0 ? fallback : current;
}

function intEnv(name, fallback) {
  const parsed = Number(__ENV[name]);
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : fallback;
}
