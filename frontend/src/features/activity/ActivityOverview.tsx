import { Activity, AlertTriangle, CheckCircle2 } from "lucide-react";
import { useEffect, useState } from "react";
import { AppError } from "../../contracts/api-client";
import { getGatewayReadiness } from "../../contracts/health";
import type { HealthOverview } from "../../contracts/health";
import { healthOverviewFixture } from "../../contracts/fixtures";
import { Badge, ErrorState, Panel, StatusChip } from "../../design-system/components";
import { useAuth } from "../auth/AuthProvider";

export function ActivityOverview() {
  const auth = useAuth();
  const [health, setHealth] = useState<HealthOverview>(healthOverviewFixture);
  const [error, setError] = useState<AppError | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    getGatewayReadiness(auth.client, controller.signal)
      .then((overview) => {
        setHealth(overview.services.length > 0 ? overview : healthOverviewFixture);
        setError(null);
      })
      .catch((loadError) => {
        if (loadError instanceof AppError && loadError.code === "REQUEST_ABORTED") {
          return;
        }

        setError(loadError instanceof AppError ? loadError : new AppError({ status: 0, code: "HEALTH_LOAD_FAILED", message: "Health overview could not be loaded.", retryable: true }));
      });

    return () => controller.abort();
  }, [auth.client]);

  return (
    <Panel className="activity-overview" as="section">
      <div className="panel-heading-row">
        <div>
          <span className="kicker">Health and activity</span>
          <h2>Service readiness</h2>
        </div>
        <StatusChip tone={health.status === "UP" ? "healthy" : health.status === "DOWN" ? "danger" : "warning"}>{health.status.toLowerCase()}</StatusChip>
      </div>
      {error ? <ErrorState title="Health degraded" copy="Gateway readiness is unavailable. Showing last known affected feature mapping." requestId={error.requestId} /> : null}
      <div className="service-card-grid">
        {health.services.map((service) => (
          <article className="service-card" key={service.name}>
            <div className="service-card-title">
              {service.status === "UP" ? <CheckCircle2 size={18} aria-hidden="true" /> : service.status === "DOWN" ? <AlertTriangle size={18} aria-hidden="true" /> : <Activity size={18} aria-hidden="true" />}
              <strong>{service.name}</strong>
              <Badge tone={service.status === "UP" ? "healthy" : service.status === "DOWN" ? "danger" : "warning"}>{service.status}</Badge>
            </div>
            <p className="small-copy">{service.latencyMs === null ? "No latency sample" : `${service.latencyMs} ms readiness latency`}</p>
            <p className="small-copy">Affected: {service.affectedFeatures.join(", ")}</p>
          </article>
        ))}
      </div>
    </Panel>
  );
}
