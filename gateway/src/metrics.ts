export class GatewayMetrics {
  private activeRequests = 0;
  private readonly requestTotals = new Map<string, number>();
  private readonly durationMsTotals = new Map<string, number>();

  start(): void {
    this.activeRequests += 1;
  }

  finish(method: string, route: string, statusCode: number, durationSeconds: number): void {
    this.activeRequests = Math.max(this.activeRequests - 1, 0);
    const key = this.key(method, route, statusCode);
    this.requestTotals.set(key, (this.requestTotals.get(key) ?? 0) + 1);
    this.durationMsTotals.set(key, (this.durationMsTotals.get(key) ?? 0) + durationSeconds * 1000);
  }

  snapshot() {
    return {
      activeRequests: this.activeRequests,
      routes: this.requestTotals.size
    };
  }

  render(): string {
    const lines = [
      "# HELP service_info Service metadata.",
      "# TYPE service_info gauge",
      'service_info{service="gateway",version="0.1.0"} 1',
      "# HELP http_requests_active Active HTTP requests.",
      "# TYPE http_requests_active gauge",
      `http_requests_active{service="gateway"} ${this.activeRequests}`,
      "# HELP http_requests_total Total HTTP requests.",
      "# TYPE http_requests_total counter",
      ...this.renderTotals("http_requests_total"),
      "# HELP http_request_duration_ms_sum Total HTTP request duration in milliseconds.",
      "# TYPE http_request_duration_ms_sum counter",
      ...this.renderDurations("http_request_duration_ms_sum"),
      "# HELP http_request_duration_ms_count Count of timed HTTP requests.",
      "# TYPE http_request_duration_ms_count counter",
      ...this.renderTotals("http_request_duration_ms_count"),
      "# HELP livelattice_gateway_http_requests_active Active HTTP requests.",
      "# TYPE livelattice_gateway_http_requests_active gauge",
      `livelattice_gateway_http_requests_active ${this.activeRequests}`,
      "# HELP livelattice_gateway_http_requests_total Total HTTP requests.",
      "# TYPE livelattice_gateway_http_requests_total counter"
    ];
    for (const [key, count] of this.requestTotals.entries()) {
      lines.push(`livelattice_gateway_http_requests_total${key} ${count}`);
    }
    lines.push("# HELP livelattice_gateway_http_request_duration_ms_sum Total HTTP request duration in milliseconds.");
    lines.push("# TYPE livelattice_gateway_http_request_duration_ms_sum counter");
    for (const [key, ms] of this.durationMsTotals.entries()) {
      lines.push(`livelattice_gateway_http_request_duration_ms_sum${key} ${ms.toFixed(3)}`);
    }
    return `${lines.join("\n")}\n`;
  }

  private renderTotals(metric: string): string[] {
    return Array.from(this.requestTotals.entries()).map(([key, count]) => `${metric}${this.addServiceLabel(key)} ${count}`);
  }

  private renderDurations(metric: string): string[] {
    return Array.from(this.durationMsTotals.entries()).map(([key, ms]) => `${metric}${this.addServiceLabel(key)} ${ms.toFixed(3)}`);
  }

  private key(method: string, route: string, statusCode: number): string {
    return `{method="${this.escape(method)}",route="${this.escape(route)}",status="${statusCode}"}`;
  }

  private addServiceLabel(key: string): string {
    return key.replace("{", '{service="gateway",');
  }

  private escape(value: string): string {
    return value.replace(/\\/g, "\\\\").replace(/"/g, "\\\"");
  }
}
