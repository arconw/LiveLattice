import { DynamicModule, Module } from "@nestjs/common";
import type { GatewayConfig } from "./config";
import { HealthController } from "./health.controller";
import type { GatewayMetrics } from "./metrics";
import { GATEWAY_CONFIG, GATEWAY_METRICS } from "./tokens";

@Module({})
export class AppModule {
  static register(config: GatewayConfig, metrics: GatewayMetrics): DynamicModule {
    return {
      module: AppModule,
      controllers: [HealthController],
      providers: [
        { provide: GATEWAY_CONFIG, useValue: config },
        { provide: GATEWAY_METRICS, useValue: metrics }
      ]
    };
  }
}
