import authSessionJson from "./fixtures/auth-session.json";
import canvasJson from "./fixtures/canvas.json";
import canvasCommentsJson from "./fixtures/canvas-comments.json";
import canvasSnapshotsJson from "./fixtures/canvas-snapshots.json";
import canvasTemplatesJson from "./fixtures/canvas-templates.json";
import workspaceMembersJson from "./fixtures/workspace-members.json";
import workspacesJson from "./fixtures/workspaces.json";
import type { AuthTokenResponse } from "./auth";
import type { CommentResponse, RawCanvasResponse, RawTemplateResponse, SnapshotResponse } from "./canvas";
import type { WorkspaceMemberResponse, WorkspaceResponse } from "./workspaces";
export {
  activityJobsFixture,
  auditEventsFixture,
  healthOverviewFixture,
  notificationPreferencesFixture,
  notificationsFixture,
  searchResponseFixture,
  searchSuggestionsFixture
} from "./activity-fixtures";

export const authSessionFixture = authSessionJson satisfies AuthTokenResponse;
export const canvasFixture = canvasJson satisfies RawCanvasResponse;
export const canvasCommentFixtures = canvasCommentsJson satisfies CommentResponse[];
export const canvasSnapshotFixtures = canvasSnapshotsJson satisfies SnapshotResponse[];
export const canvasTemplateFixtures = canvasTemplatesJson satisfies RawTemplateResponse[];
export const workspaceFixtures: WorkspaceResponse[] = workspacesJson.map((workspace) => ({
  ...workspace,
  tier: workspace.tier as WorkspaceResponse["tier"]
}));
export const workspaceMemberFixtures = workspaceMembersJson satisfies Record<string, WorkspaceMemberResponse[]>;
