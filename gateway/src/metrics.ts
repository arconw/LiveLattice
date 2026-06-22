export class GatewayMetrics {
  private activeRequests = 0;
  private readonly requestTotals = new Map<string, number>();
  private readonly durationTotals = new Map<string, number>();

  start(): void {
    this.activeRequests += 1;
  }

  finish(method: string, route: string, statusCode: number, durationSeconds: number): void {
    this.activeRequests = Math.max(this.activeRequests - 1, 0);
    const key = this.key(method, route, statusCode);
    this.requestTotals.set(key, (this.requestTotals.get(key) ?? 0) + 1);
    this.durationTotals.set(key, (this.durationTotals.get(key) ?? 0) + durationSeconds);
  }

  snapshot() {
    return {
      activeRequests: this.activeRequests,
      routes: this.requestTotals.size
    };
  }

  render(): string {
    const lines = [
      "# HELP livelattice_gateway_http_requests_active Active HTTP requests.",
      "# TYPE livelattice_gateway_http_requests_active gauge",
      `livelattice_gateway_http_requests_active ${this.activeRequests}`,
      "# HELP livelattice_gateway_http_requests_total Total HTTP requests.",
      "# TYPE livelattice_gateway_http_requests_total counter"
    ];
    for (const [key, count] of this.requestTotals.entries()) {
      lines.push(`livelattice_gateway_http_requests_total${key} ${count}`);
    }
    lines.push("# HELP livelattice_gateway_http_request_duration_seconds_sum Total HTTP request duration in seconds.");
    lines.push("# TYPE livelattice_gateway_http_request_duration_seconds_sum counter");
    for (const [key, seconds] of this.durationTotals.entries()) {
      lines.push(`livelattice_gateway_http_request_duration_seconds_sum${key} ${seconds.toFixed(6)}`);
    }
    return `${lines.join("\n")}\n`;
  }

  private key(method: string, route: string, statusCode: number): string {
    return `{method="${this.escape(method)}",route="${this.escape(route)}",status="${statusCode}"}`;
  }

  private escape(value: string): string {
    return value.replace(/\\/g, "\\\\").replace(/"/g, "\\\"");
  }
}
