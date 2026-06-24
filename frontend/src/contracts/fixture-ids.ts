export const coreFixtureIds = {
  workspaceFactoryFloor: "11111111-1111-4111-8111-111111111111",
  canvasIncidentMap: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  commentExportBoundary: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1",
  commentExportReply: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2",
  commentGeneral: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3",
  commentMissingTarget: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb4",
  snapshot126: "cccccccc-cccc-4ccc-8ccc-cccccccc0126",
  snapshot128: "cccccccc-cccc-4ccc-8ccc-cccccccc0128",
  snapshot129: "cccccccc-cccc-4ccc-8ccc-cccccccc0129",
  templateIncident: "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
  dashboardOperations: "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
} as const;

export const dashboardWidgetFixtureIds = {
  BAR_CHART: "77777777-7777-4777-8777-777777777001",
  LINE_CHART: "77777777-7777-4777-8777-777777777002",
  PIE_CHART: "77777777-7777-4777-8777-777777777003",
  TABLE: "77777777-7777-4777-8777-777777777004",
  STAT: "77777777-7777-4777-8777-777777777005",
  HEATMAP: "77777777-7777-4777-8777-777777777006",
  MARKDOWN: "77777777-7777-4777-8777-777777777007"
} as const;

export const dataSourceFixtureIds = {
  CLICKHOUSE: "88888888-8888-4888-8888-888888888001",
  POSTGRESQL: "88888888-8888-4888-8888-888888888002",
  PROMETHEUS: "88888888-8888-4888-8888-888888888003",
  REST_API: "88888888-8888-4888-8888-888888888004",
  CSV: "88888888-8888-4888-8888-888888888005"
} as const;

export function primaryCanvasHref(workspaceSlug: string) {
  return `/w/${workspaceSlug}/c/${coreFixtureIds.canvasIncidentMap}`;
}

export function exportBoundaryCommentHref(workspaceSlug: string) {
  return `${primaryCanvasHref(workspaceSlug)}?comment=${coreFixtureIds.commentExportBoundary}`;
}
