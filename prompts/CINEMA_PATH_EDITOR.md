# ⚠ DO NOT REMOVE
**Scope:** ONE feature — a confirm dialog in the existing Alt+C gap that shows the computed cinema
path as a graph and lets the user OK it or adjust it. The "simplest fastest tour maker."
**Not in scope:** the §CINEMA_SPACE attic-pick default (own dev session — see §Out of scope below),
and anything that turns this into a viewport gizmo.
**Read the log after every run.** Verification on this project is `§`-tagged console output, not
screenshots — and for anything continuous (camera path, angles, Z) it is the NUMBERS, per CLAUDE.md's
FUNDAMENTAL LAW. Honour this block until this file is DONE.

---

# §CINEMA_PATH_EDITOR — the simplest fastest tour maker

## The one sentence
**Build §CINEMA_PATH_EDITOR as the SIMPLEST FASTEST TOUR MAKER — no new icon, no new panel: Alt+C
already computes the plan in ~50–100 ms, so put a DIALOG in that existing gap showing the path as a
graph (reuse the Find panel's route visual) with the per-beat camera angle/Z, and the user either
hits OK to proceed straight into the recording exactly as today, or drags a waypoint / fixes the
camera Z that currently dives into the attic first — persisting any edit as a `cinema_path` table in
the saved DB exactly the way `staffage_instances` already round-trips.**

## Origin
User, 2026-07-26 evening, *after* the foundation below was laid and landed:
> "once a path is done, the user can cancel or not, a new feature when pressed calls up a graph panel
> of the scene path and cam angle... That way this time the user can adjust the path points, Z level
> of the camera as now it goes for the attic."

then, scoping it:
> "it is to be the simplest fastest tour maker — perhaps a dialog box with the graph (similar to Find
> rooms path visually as idea) and user can OK to proceed or adjust it, thus no need extra icon."

## THE FOUNDATION — read these, do NOT re-derive them
This feature is only cheap because the substrate already exists and is proven. Referenced, not
restated; go to the source for detail.

| what | where | why it matters here |
|---|---|---|
| The plan itself | `viewer/effects.js` `_cinemaPathPlan()` → `{beats:{dive,spin,out,rise}, poseAt(t), pivot, exit, ...}` | Every pose is a pure function of `t`. **Both** consumers — live capture (`effects.js`) and the MaxQ bake (`cinema_maxq.js`) — read this ONE plan, so editing its inputs changes both for free. |
| The editable inputs | same file: `CINEMA_DIVE/SPIN/OUT/RISE_SEC`, `outWp` waypoints, `CINEMA_EYE_M`, `CINEMA_LOOKDOWN_DEG`, the orbit radius band | This is the actual edit surface. Nothing else needs to become editable. |
| The gap to put the dialog in | `§CINEMA_PLAN_MS` — Alt+C already pauses to compute (Duplex ~50–100 ms; Terminal/Hospital 500–750 ms) | No new entry point is needed or wanted. |
| The route renderer to REUSE | `viewer/navigate_find.js:1249 _drawPathHighlight`, `:3274 _renderPathResult` | The Find panel already draws a room route as a line through points; `outWp` is the same shape of data. |
| The persistence pattern to MIRROR | `staffage_instances` — `viewer/scene.js:577 _writeStaffageTable` / `:589 _exportBuildingDb`, `effects.js A._restoreStaffageInstances` | Full round-trip measured working 2026-07-26: `§STAFFAGE_SAVE rows=8` → `§STAFFAGE_RESTORE rows=8` → `restored=8`. See `prompts/STAFFAGE_WALKABLE_PLACEMENT.md §STAFFAGE_PERSIST`. |
| Recent path fixes that must not regress | `prompts/PHOTOREAL_STILL_RENDER.md` §CINEMA_TURN_SLERP (#1018) and §CINEMA_DAMPING_BLEED (#1020) | Their witnesses (`witness_cinema_exit_breathe.js`, `witness_cinema_damping_bleed.js`) must stay green after any change to the plan or its consumers. |

## Scope guardrails (the "simplest fastest" requirement is the spec, not a preference)
1. **No new icon, no new panel, no new keybinding.** It rides the Alt+C press that already exists.
2. **OK must behave exactly like today** — same film, same beats. The default cost of this feature is
   one click. If OK changes the output at all, the feature is wrong.
3. **Reuse the Find route renderer.** Do NOT build a second path-drawing system, and do NOT build an
   in-viewport 3D drag gizmo. A dialog with a 2D graph is the deliverable.
4. **An edited path is AUTHORED data, not derived.** Under the prime rule (EXTRACT OR COMPILE ONLY) it
   must be STORED, never re-guessed: a `cinema_path` table (beats + waypoints + eye height + tilt)
   written by `_exportBuildingDb()` and read on load. Reuse the staffage pattern; do not invent a
   second persistence mechanism.

## Witness claims (spec-first — write these before any implementation)
- **G1** OK-without-edit is a no-op: beats and sampled `poseAt(t)` are byte-identical to a run with
  the dialog bypassed. (The one gate that protects the zero-friction default.)
- **G2** a waypoint edit changes the sampled path at that waypoint and nowhere else.
- **G3** a camera-Z edit changes eye height by exactly the amount asked, on every affected beat.
- **G4** round-trip: edit → `_exportBuildingDb()` → `_openDbBytes()` → the SAME edited path is in
  force, proven by sampled poses, not by the table existing.
- **G5** control: on a DB with no `cinema_path` table the plan is the unedited derived one.
- **G6** §CINEMA_TURN_SLERP's and §CINEMA_DAMPING_BLEED's witnesses stay green.

## Out of scope — each has its own session, do not fold them in here
- **The attic pick.** §CINEMA_SPACE takes "largest space nearest the centre" and `_cinemaFloorY`
  raycasts a floor under it; on some buildings that resolves to an attic/roof void. **This editor
  makes it survivable and must not become the reason the default is never fixed** — user has agreed
  it goes through its own dev session.
- `§STAFFAGE_PAX_REJECT tried=72 placed=3` with all rejection counters 0 — 69 unattributed
  rejections, the reason only ~3 figures ever appear. `prompts/STAFFAGE_WALKABLE_PLACEMENT.md`.
- **Scene jumping on reopen** — NOT reproduced through `_openDbBytes()` (camera 0.00 m, target
  0.00 m). Needs the user's actual repro route first.
- **D2 walk-out corner whip** — 19.8°/frame on current main, printed by the witness, not gated.
- **JKR path-crossing** — `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md` §18.

## Two small already-diagnosed items to land alongside (both root-caused, neither started)
- **Restore staffage on LOAD**, not on the first Alt+P press. The round-trip works; the trigger is
  wrong. Details + measurements: `STAFFAGE_WALKABLE_PLACEMENT.md §STAFFAGE_PERSIST` (ANSWERED section).
- **MP4 loses one pixel row** on odd-height canvases: frames grabbed at `w×h`, encoder configured at
  `(w & ~1, h & ~1)`, drawn 1:1. `cinema_maxq.js` `_stitchMp4`.
