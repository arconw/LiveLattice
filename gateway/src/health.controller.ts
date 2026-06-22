import { Controller, Get, Header, Inject } from "@nestjs/common";
import type { GatewayConfig } from "./config";
import type { GatewayMetrics } from "./metrics";
import { GATEWAY_CONFIG, GATEWAY_METRICS } from "./tokens";

@Controller()
export class HealthController {
  constructor(
    @Inject(GATEWAY_CONFIG) private readonly config: GatewayConfig,
    @Inject(GATEWAY_METRICS) private readonly metrics: GatewayMetrics
  ) {}

  @Get("health")
  health() {
    return {
      status: "UP",
      service: this.config.name,
      version: this.config.version
    };
  }

  @Get("ready")
  ready() {
    return {
      status: "UP",
      checks: {
        process: "UP",
        routes: Object.keys(this.config.routes).sort(),
        authRequired: this.config.auth.required
      }
    };
  }

  @Get("metrics")
  @Header("content-type", "text/plain; version=0.0.4")
  metricsText() {
    return this.metrics.render();
  }
}
