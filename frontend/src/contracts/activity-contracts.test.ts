import { describe, expect, it } from "vitest";
import { buildAuditPath, mapAuditList } from "./audit";
import { coreFixtureIds } from "./fixture-ids";
import { auditEventsFixture, healthOverviewFixture, notificationPreferencesFixture, searchResponseFixture } from "./fixtures";
import { affectedFeaturesForService, mapHealthOverview } from "./health";
import { jobStateView, mapJob, validateImportUpload } from "./jobs";
import { mapNotificationPreferences, normalizeDigestFrequency } from "./notifications";
import { buildSearchPath, highlightToParts, mapSearchResponse } from "./search";

describe("activity frontend contracts", () => {
  it("maps search facets, highlights, result types, and search_after tokens", () => {
    const response = mapSearchResponse(searchResponseFixture);
    const path = buildSearchPath({
      q: "export",
      workspaceId: "workspace-1",
      types: ["canvas", "comment"],
      tags: ["rbac"],
      searchAfter: response.nextSearchAfter,
      size: 20
    });

    expect(response.facets.type.canvas).toBe(11);
    expect(response.results[0].type).toBe("canvas");
    expect(response.nextSearchAfter).toBe(`2026-06-23T11:58:00Z|${coreFixtureIds.dashboardOperations}`);
    expect(path).toContain(`search_after=2026-06-23T11%3A58%3A00Z%7C${coreFixtureIds.dashboardOperations}`);
    expect(highlightToParts("Gateway <em>export</em> boundary")).toEqual([
      { text: "Gateway ", highlighted: false },
      { text: "export", highlighted: true },
      { text: " boundary", highlighted: false }
    ]);
  });

  it("normalizes async job states into available UI actions", () => {
    const expired = mapJob(
      {
        id: "job-1",
        status: "SUCCESS",
        progress: 100,
        downloadUrl: "https://downloads.example.test/job-1",
        downloadExpiresAt: "2026-06-23T10:00:00Z"
      },
      "export"
    );
    const failed = mapJob({ id: "job-2", status: "FAILED", errorMessage: "OpenSearch timeout", retryCount: 1, maxRetries: 3 }, "background");

    expect(expired).not.toBeNull();
    expect(failed).not.toBeNull();
    expect(jobStateView(expired!, new Date("2026-06-23T12:00:00Z")).primaryAction).toBe("refresh-download");
    expect(jobStateView(failed!, new Date("2026-06-23T12:00:00Z")).primaryAction).toBe("retry");
  });

  it("validates import uploads before backend validation", () => {
    const badType = new File(["hello"], "notes.txt", { type: "text/plain" });
    const svg = new File(["<svg />"], "diagram.svg", { type: "image/svg+xml" });

    expect(validateImportUpload(badType)).toEqual({ ok: false, error: "Unsupported import type. Use draw.io XML, SVG, or JSON." });
    expect(validateImportUpload(svg)).toEqual({ ok: true, error: null });
  });

  it("preserves notification digest enum values and keeps webhook secrets write-only", () => {
    expect(normalizeDigestFrequency("daily")).toBe("daily");
    expect(normalizeDigestFrequency("DAILY")).toBe("daily");
    const preferences = mapNotificationPreferences({
      email_digest: notificationPreferencesFixture.emailDigest,
      muted_types: notificationPreferencesFixture.mutedTypes,
      webhooks: [{ ...notificationPreferencesFixture.webhooks[0], secret: "not-returned-to-ui" }]
    });

    expect(preferences.emailDigest).toBe("hourly");
    expect(preferences.mutedTypes).toEqual(["member.joined"]);
    expect(preferences.webhooks[0]).not.toHaveProperty("secret");
  });

  it("keeps audit queries workspace-scoped and maps read-only rows", () => {
    const path = buildAuditPath({ workspaceId: "workspace-1", actorId: "user-1", action: "canvas.update", targetType: "canvas" });
    const response = mapAuditList({ events: auditEventsFixture, total: auditEventsFixture.length });

    expect(path).toContain("workspace_id=workspace-1");
    expect(path).toContain("actor_id=user-1");
    expect(response.events[0].action).toBe("canvas.update");
    expect(response.events[0]).not.toHaveProperty("deleteUrl");
  });

  it("maps degraded health to affected feature hints", () => {
    const overview = mapHealthOverview({
      status: healthOverviewFixture.status,
      checks: {
        search: { status: "DEGRADED", details: { latencyMs: 310 } },
        "background-jobs": { status: "DOWN", details: {} }
      }
    });

    expect(overview.status).toBe("DEGRADED");
    expect(overview.services.find((service) => service.name === "search")?.affectedFeatures).toContain("workspace search");
    expect(affectedFeaturesForService("background-jobs")).toContain("job progress");
  });
});
