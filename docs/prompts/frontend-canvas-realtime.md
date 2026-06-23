# Stage 15C: Frontend Canvas Editor, Comments, Snapshots, Templates, and Realtime Collaboration

## Objective

Implement the core canvas experience: document loading, rendering, element interaction, comments, snapshots, templates, import/export entry points, and realtime presence/collaboration.

This is the product's primary interaction surface. Agents must start from the `.ctx` lattice cockpit and canvas workbench mock, then implement production components that preserve backend contracts.

## Required Context

Read these files before implementation:

- `.ctx/index.html`
- `.ctx/css/variables.css`
- `.ctx/css/styles.css`
- `docs/architect/frontend/frontend-architecture.md`
- `docs/techDesign/frontend/frontend-design.md`
- `docs/prompts/canvas-documents.md`
- `docs/techDesign/canvas-documents/canvas-documents-design.md`
- `docs/prompts/realtime.md`
- `docs/architect/realtime/realtime-collaboration.md`
- `docs/techDesign/realtime/realtime-design.md`
- `docs/prompts/import-export.md`
- `docs/techDesign/import-export/import-export-design.md`

## Scope

1. Implement canvas route:
   - load canvas by id
   - show title, version, workspace, updated state
   - handle not found/deleted/permission denied
2. Implement canvas rendering:
   - rectangle
   - circle
   - text
   - image placeholder
   - connector
   - arrow
   - freehand
3. Implement viewport behavior:
   - pan
   - zoom
   - fit to content
   - grid toggle
   - mini coordinate/status readout
4. Implement selection behavior:
   - single select
   - multi select
   - keyboard navigation
   - clear selection
   - locked element state
5. Implement element editing:
   - create basic shape/text/connector
   - move
   - resize
   - rotate where supported
   - duplicate
   - delete
   - edit style
   - update z-order
   - group/ungroup only if contract is preserved
6. Implement canvas inspector:
   - canvas metadata
   - selected element position/style/data
   - validation errors
   - permission state
7. Implement comments:
   - comment pins attached to `target_element_id`
   - general canvas comments
   - thread list
   - replies
   - edit/resolve/delete where permitted
   - missing target state when an element was deleted
8. Implement snapshots:
   - list history
   - create snapshot
   - inspect version metadata
   - restore with confirmation
9. Implement templates:
   - list templates
   - create canvas from template
   - save canvas as template where permitted
10. Implement import/export entry points:
    - import button opens upload flow or routes to import/export flow
    - export button starts canvas export job or sync download path according to backend response
    - large/async job responses link to jobs UI
11. Implement realtime client adapter:
    - connect to documented websocket entrypoint
    - join canvas room
    - send canvas operation messages
    - receive acknowledgements
    - receive remote operations
    - send awareness cursor/selection updates
    - receive awareness state
    - reconnect with visible state
12. Implement sync status:
    - saved
    - saving
    - offline
    - reconnecting
    - conflict
    - failed to save
13. Add tests for mappers, reducers, rendering, realtime adapter, and accessibility.

## Canvas Content Contract

Persisted canvas content must match backend `Canvas.content`:

```json
{
  "elements": [
    {
      "id": "uuid",
      "type": "rectangle|circle|text|image|connector|arrow|freehand",
      "x": 10,
      "y": 10,
      "width": 100,
      "height": 50,
      "rotation": 0,
      "style": { "fill": "#ffffff", "stroke": "#4d7cfe", "strokeWidth": 2, "opacity": 1 },
      "data": { "text": "type-specific data" },
      "zIndex": 1,
      "locked": false,
      "groupId": null
    }
  ],
  "viewport": { "zoom": 1, "panX": 0, "panY": 0 },
  "metadata": { "width": 2400, "height": 1400, "backgroundColor": "#eef2f5", "gridEnabled": true }
}
```

Do not persist UI-only state inside `content.elements[]` unless the backend contract is updated first.

## Realtime Contract

Use documented realtime messages:

| Direction | Event | Payload |
|---|---|---|
| Client -> Server | `canvas:op` | `{ ops: Op[], version: number, seq: number }` |
| Server -> Client | `canvas:ack` | `{ version: number, seq: number }` |
| Server -> Client | `canvas:op` | `{ ops: Op[], origin: string }` |
| Client -> Server | `awareness:update` | `{ cursor: { x, y }, selection: string[] }` |
| Server -> Client | `awareness:state` | `{ clients: [{ id, cursor, name }] }` |
| Client -> Server | `presence:away` | `{ status: "away" | "online" }` |
| Server -> Client | `room:join` / `room:leave` | `{ userId, userName }` |

If actual implementation differs, update docs and tests before coding against the difference.

## Contract Rules

- Load and save canvas through Gateway-protected Core routes.
- Connect realtime only through the documented Gateway/realtime entrypoint.
- Do not send trusted internal headers.
- Do not write comments through realtime only; persisted comments go through Core comment endpoints.
- Preserve optimistic lock/version behavior and show conflict state on 409.
- Do not assume the frontend has permission just because a control is visible.
- File import/export must use import-export service through Gateway and respect async job semantics.
- Signed download URLs must not be cached permanently.

## Required UI States

- Canvas loading.
- Canvas not found.
- Canvas deleted.
- Permission denied.
- Viewer mode.
- Commenter mode.
- Editor mode.
- Empty canvas.
- Selected element.
- Locked element.
- Missing comment target.
- Snapshot loading.
- Restore confirmation.
- Realtime disconnected.
- Reconnecting.
- Conflict.
- Save failed.
- Import rejected.
- Export job queued.

## Accessibility Requirements

- Canvas toolbar controls have labels and keyboard focus.
- Canvas elements are represented in a keyboard-accessible selection list or inspector list.
- Arrow keys can move selected elements.
- Delete/Backspace can delete selected elements where permitted.
- Escape clears selection or closes active panel.
- Comment pins are buttons with useful labels.
- Snapshot restore requires explicit confirmation.
- Realtime state is visible as text, not color alone.
- Reduced motion disables non-essential cursor/pulse animations.

## Verification

```bash
cd frontend
npm run typecheck
npm run lint
npm test
npm run build
```

Optional Compose smoke when backend services are available:

```bash
docker compose up -d gateway core realtime redis kafka postgres minio import-export
```

Smoke checks:

- Canvas route loads fixture data.
- Canvas renders all supported element types.
- Selecting an element updates inspector.
- Comment pin opens target thread.
- Snapshot list and restore confirmation render.
- Realtime unavailable state is visible.
- Import/export action transitions to job queued/download state.

## Tests to Add

- Canvas content mapper preserves backend shape.
- Invalid element type is rejected or rendered as safe unknown state.
- Element reducer handles move/resize/delete without mutating unrelated elements.
- Comment target lookup handles deleted/missing element.
- Realtime adapter sends documented join/op/awareness payloads.
- Realtime adapter deduplicates local echo or handles acknowledgements safely.
- 409 conflict renders conflict state.
- Permission denied disables editing actions and shows explanation.
- Keyboard selection and toolbar actions pass accessibility checks.

## Out of Scope

- Dashboard widget editor.
- Full import/export jobs page.
- Notification preferences page.
- Audit trail page.
- Backend API changes.

## Completion Criteria

The slice is complete when the canvas editor can load, render, select, edit, comment, snapshot, and show realtime states while preserving the persisted canvas JSONB contract and the documented realtime protocol.
