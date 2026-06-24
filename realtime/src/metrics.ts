import { monitorEventLoopDelay } from "node:perf_hooks";

export class RealtimeMetrics {
  private activeHttpRequests = 0;
  private activeWsConnections = 0;
  private canvasOpsTotal = 0;
  private wsMessagesTotal = 0;
  private readonly requestTotals = new Map<string, number>();
  private readonly durationMsTotals = new Map<string, number>();
  private readonly eventLoopDelay = monitorEventLoopDelay({ resolution: 20 });

  constructor() {
    this.eventLoopDelay.enable();
  }

  httpStart(): void {
    this.activeHttpRequests += 1;
  }

  httpFinish(method: string, route: string, statusCode: number, durationSeconds: number): void {
    this.activeHttpRequests = Math.max(this.activeHttpRequests - 1, 0);
    const key = this.key(method, route, statusCode);
    this.requestTotals.set(key, (this.requestTotals.get(key) ?? 0) + 1);
    this.durationMsTotals.set(key, (this.durationMsTotals.get(key) ?? 0) + durationSeconds * 1000);
  }

  socketConnected(): void {
    this.activeWsConnections += 1;
  }

  socketDisconnected(): void {
    this.activeWsConnections = Math.max(this.activeWsConnections - 1, 0);
  }

  websocketMessage(): void {
    this.wsMessagesTotal += 1;
  }

  canvasOperation(): void {
    this.canvasOpsTotal += 1;
  }

  render(): string {
    const eventLoopLagSeconds = Number.isFinite(this.eventLoopDelay.mean) ? this.eventLoopDelay.mean / 1_000_000_000 : 0;
    const lines = [
      "# HELP service_info Service metadata.",
      "# TYPE service_info gauge",
      'service_info{service="realtime",version="0.1.0"} 1',
      "# HELP http_requests_active Active HTTP requests.",
      "# TYPE http_requests_active gauge",
      `http_requests_active{service="realtime"} ${this.activeHttpRequests}`,
      "# HELP http_requests_total Total HTTP requests.",
      "# TYPE http_requests_total counter",
      ...this.renderTotals("http_requests_total"),
      "# HELP http_request_duration_ms_sum Total HTTP request duration in milliseconds.",
      "# TYPE http_request_duration_ms_sum counter",
      ...this.renderDurations("http_request_duration_ms_sum"),
      "# HELP http_request_duration_ms_count Count of timed HTTP requests.",
      "# TYPE http_request_duration_ms_count counter",
      ...this.renderTotals("http_request_duration_ms_count"),
      "# HELP node_event_loop_lag_seconds Mean Node.js event loop lag.",
      "# TYPE node_event_loop_lag_seconds gauge",
      `node_event_loop_lag_seconds{service="realtime"} ${eventLoopLagSeconds.toFixed(9)}`,
      "# HELP active_ws_connections Active WebSocket connections.",
      "# TYPE active_ws_connections gauge",
      `active_ws_connections{service="realtime"} ${this.activeWsConnections}`,
      "# HELP ws_connections_active Active WebSocket connections.",
      "# TYPE ws_connections_active gauge",
      `ws_connections_active{service="realtime"} ${this.activeWsConnections}`,
      "# HELP canvas_ops_total Canvas operation messages accepted.",
      "# TYPE canvas_ops_total counter",
      `canvas_ops_total{service="realtime"} ${this.canvasOpsTotal}`,
      "# HELP ws_messages_total WebSocket messages handled.",
      "# TYPE ws_messages_total counter",
      `ws_messages_total{service="realtime"} ${this.wsMessagesTotal}`
    ];
    return `${lines.join("\n")}\n`;
  }

  private renderTotals(metric: string): string[] {
    return Array.from(this.requestTotals.entries()).map(([key, count]) => `${metric}${key} ${count}`);
  }

  private renderDurations(metric: string): string[] {
    return Array.from(this.durationMsTotals.entries()).map(([key, ms]) => `${metric}${key} ${ms.toFixed(3)}`);
  }

  private key(method: string, route: string, statusCode: number): string {
    return `{service="realtime",method="${this.escape(method)}",route="${this.escape(route)}",status="${statusCode}"}`;
  }

  private escape(value: string): string {
    return value.replace(/\\/g, "\\\\").replace(/"/g, "\\\"");
  }
}
