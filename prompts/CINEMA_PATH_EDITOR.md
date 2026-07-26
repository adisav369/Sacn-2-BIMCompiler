# ⚠ DO NOT REMOVE
**Scope:** ONE feature — a waypoint editor that opens after the Alt+C preview, lists the cinema path's
waypoints with their camera info, and lets the user key or drag them before the bake. The "simplest
fastest tour maker."
**Not in scope:** the §CINEMA_SPACE attic-pick default — **owned by another session as of 2026-07-26,
do not touch `_cinemaPathPlan`'s §CINEMA_SPACE block (~L3486-3610)**; see §Out of scope below.
**Read the log after every run.** Verification on this project is `§`-tagged console output, not
screenshots — and for anything continuous (camera path, angles, Z) it is the NUMBERS, per CLAUDE.md's
FUNDAMENTAL LAW. Honour this block until this file is DONE.
**⚠ The interaction model below (§CINEMA_PATH_EDITOR_MODEL, 2026-07-26) SUPERSEDES the
"graph dialog" framing in the original sections — read it FIRST.**

---

# §CINEMA_PATH_EDITOR — the simplest fastest tour maker

## The one sentence
**Build §CINEMA_PATH_EDITOR as the SIMPLEST FASTEST TOUR MAKER — no new icon, no new panel: Alt+C
already computes the plan in ~50–100 ms, so put a DIALOG in that existing gap showing the path as a
graph (reuse the Find panel's route visual) with the per-beat camera angle/Z, and the user either
hits OK to proceed straight into the recording exactly as today, or drags a waypoint / fixes the
camera Z that currently dives into the attic first — with an explicit **"Save this path"** action
that stores the edit as a `cinema_path` table in the building DB exactly the way
`staffage_instances` already round-trips.**

## Origin
User, 2026-07-26 evening, *after* the foundation below was laid and landed:
> "once a path is done, the user can cancel or not, a new feature when pressed calls up a graph panel
> of the scene path and cam angle... That way this time the user can adjust the path points, Z level
> of the camera as now it goes for the attic."

then, scoping it:
> "it is to be the simplest fastest tour maker — perhaps a dialog box with the graph (similar to Find
> rooms path visually as idea) and user can OK to proceed or adjust it, thus no need extra icon."

## §CINEMA_PATH_EDITOR_MODEL — the settled interaction model (user, 2026-07-26, in discussion)
**This section is the spec. Where it disagrees with the "graph dialog" wording elsewhere in this
file, this wins.** Settled point by point with the user; nothing here is inferred.

### The data model — one authored thing, everything else derived
1. **Waypoints are the ONLY authored data.** A waypoint is a position plus a camera height. Nothing
   else is stored.
2. **Camera angle is NEVER authored.** It is LOS — the look direction at waypoint *n* is toward
   waypoint *n+1*. This is why the table carries **one extra final row** (the stopping waypoint): it
   exists to give the last real leg something to look at.
3. **Therefore "adjust the POV" is not a separate gesture.** Dragging waypoint *n+1* is *how* you
   change waypoint *n*'s camera angle. Falls out of (2) for free.
4. **Consequence, load-bearing:** no authored yaw/pitch anywhere → `cinema_path` stores positions and
   times only → **§CINEMA_TURN_SLERP keeps deriving headings from the path exactly as today.** Its
   witness (G6) stays green without being weakened to accommodate this feature. An earlier
   per-waypoint-authored-aim design was considered and dropped for precisely this reason.

### Geometry — no sharp corners can exist
5. **The waypoints are CONTROL points, not corners.** The flown path is smooth everywhere by
   construction, so "sharp corner" is not a state the path can reach. User's own words: *"that means
   there are no 'sharp' corners."*
6. **The curve CUTS INSIDE the corner** (user directive: *"yes cut inside"*) — it does not pass exactly
   through the placed point. The turn is eaten by geometry, not by slowing down.
7. **The cut is BOUNDED BY MEASURED CLEARANCE, not a constant.** `A.cinemaFan` (exposed
   `effects.js:4079`) is already this project's single source for real measured clearance-to-nearest-
   surface — §CINEMA_SPACE calls it "the ONLY where-is-open-space source". The rounding radius at each
   waypoint comes from it. Tight room → tight curve; open hall → wide graceful arc. **Do not invent a
   fixed fillet radius** — that would be exactly the kind of guessed constant the prime rule forbids.
8. **This retires an ungated defect.** "D2 walk-out corner whip — 19.8°/frame, printed by the witness,
   not gated" (listed under §Out of scope) is the user's reported *"about 2 jerks, fast jump at least a
   frame."* It was tolerable only because the derived route is tame. Once waypoints are user-draggable,
   sharp input becomes trivial to create — so smoothing moves IN SCOPE here and gets a real gate (G7).

### Timing — constant speed, editable total
9. **Constant speed. Path length sets the clock.** Drag a point further out and the total rises from
   24s. The displayed total is the truth about what was authored, not a fixed budget.
10. **The total is then itself an editable field.** Key 20s and the whole clip speeds up uniformly;
    key 40s and it slows. Uniform scale over the whole film, not per-leg.
11. **Cost that sits behind that field:** total × fps = frames to bake. 24s = 576 frames; 40s = 960,
    i.e. ~1.7× the render time. Surface it, don't hide it.

### Interaction — reuse, nothing invented
12. **The editor opens AFTER the 10s preview**, with the camera already returned to its initial pose
    (`cinema_maxq.js` already saves/restores it — `camSave`, ~L429).
13. **Click a row (or its waypoint) to HOLD it.** The scene camera backs up so that waypoint is framed.
    `A.controls.enabled = false` while held — the established pattern here, not a new one
    (`grid_drag.js:568/650`, `city.js:993/1013`, `doc_canvas.js:1450/1502`).
14. **Held-point verbs mirror the viewer's own navigation verbs** (user: *"isn't that intuitive?"*):
    - **drag** → move the waypoint on the horizontal plane through its own height (x, z)
    - **ctrl+drag** → move it vertically (camera height — the original complaint)
    - **wheel** → still scene zoom, NOT point depth. Releasing just to look closer would defeat the
      point of holding; x/z/height are already fully covered by the two drags.
15. **Double-click empty canvas to RELEASE** (user directive — single click was rejected as too easy to
    trigger and lose focus). Canvas `dblclick` is claimed only by measure mode (`picking.js:150`), so
    this collides with nothing. Touch has no `dblclick`: reuse the existing 350ms double-tap detector
    at `picking.js:174`, do not write a second one.
16. **While a point is held, single click does nothing** — it must not run element picking, or mid-edit
    clicks select the walls behind the waypoint.
17. **The held state must be unmistakable** — pulsing waypoint + highlighted row + a status line saying
    a point is held and double-click releases. A frozen-but-unexplained canvas reads as a hang.
18. **The pulse reuses §SELECT-PULSE** (`navigate_find.js:2160-2227`): generation counter + rAF +
    `markDirty`, and critically `depthTest:false`/`renderOrder` so the marker **shines through walls** —
    load-bearing, since the camera is outside the building when the editor opens.
19. **Two-way binding is required.** Key a number in the table → the canvas moves. Drag in the canvas →
    the number updates. Same state, two views.

### One defect this design exposes (fix as part of the work)
20. `cinema_maxq.js:357-360` claims `A._maxqActive`, `_wakeAcquire()` and `_dampHold()` **before** the
    plan/preview. `A._maxqActive` makes `dlod_nav.js:307` report `'cinema'` and fully disengage DLOD.
    An editor opening after the preview inherits all three — so a user editing for five minutes holds a
    screen wake-lock and runs Terminal/Hospital at full detail with no LOD the whole time. **The editor
    must release all three while open and re-claim them on OK.**

### Amendment on record — scope guardrail 3 is void
Guardrail 3 below says *"do NOT build an in-viewport 3D drag gizmo."* The user directed exactly that on
2026-07-26 (*"as we click on each row, appears a cam-eye 3d icon... user can adjust... and the canvas
reflects that changed path"*, then the freeze/drag/double-click model above). **Recorded as a deliberate
user amendment, not drift.** Guardrails 1, 2, 4 and 5 stand unchanged — in particular **guardrail 2: OK
without an edit must still be byte-identical to today.**

### Witness claims for this model (in addition to G1-G6 below)
- **G7** no-sharp-corners: on a path with a deliberately sharp authored corner, peak angular rate stays
  under a stated °/frame cap — the same measurement that today prints 19.8°/frame ungated. This is what
  makes "graceful" a NUMBER rather than a look.
- **G8** clearance-bounded rounding: the flown curve's maximum deviation from each authored waypoint is
  ≤ the `A.cinemaFan` clearance measured at that waypoint. Proves the cut-inside is bounded by measured
  space, never by a guessed constant.
- **G9** constant speed + uniform scale: doubling a leg's length raises the total proportionally at
  unchanged m/s; then setting the total to T re-times every leg by the same factor (sampled speeds
  ratio-identical).
- **G10** LOS: sampled look direction at each waypoint points at the next waypoint (within the smoothing
  tolerance), and moving waypoint *n+1* changes waypoint *n*'s aim — the (3) claim, measured.
- **G11** lock release: while the editor is open `A._maxqActive` is false and the wake-lock is released;
  both are re-claimed on OK. Proves defect (20) is actually fixed rather than described.

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
5. **Persistence is an EXPLICIT action — "Save this path" (user directive 2026-07-26).** Adjusting and
   proceeding is *ephemeral*: this film, this press, nothing written. Only "Save this path" marks the
   edit as the building's stored path. Mechanically this mirrors staffage exactly — the action stages
   the path into the in-memory DB and the user's normal `Ctrl+S` carries it to the file, the same way
   `_writeStaffageTable()` runs at `_exportBuildingDb()` time. Do NOT add a second save route, and do
   NOT auto-persist on adjust: a user experimenting with waypoints must be able to walk away without
   having changed the building.
   Dialog affordances, therefore: **OK** (proceed, unchanged) · **Save this path** (stage the edit) ·
   **Cancel** (abandon, no recording).

## Witness claims (spec-first — write these before any implementation)
- **G1** OK-without-edit is a no-op: beats and sampled `poseAt(t)` are byte-identical to a run with
  the dialog bypassed. (The one gate that protects the zero-friction default.)
- **G2** a waypoint edit changes the sampled path at that waypoint and nowhere else.
- **G3** a camera-Z edit changes eye height by exactly the amount asked, on every affected beat.
- **G4** round-trip: edit → **Save this path** → `_exportBuildingDb()` → `_openDbBytes()` → the SAME
  edited path is in force, proven by SAMPLED POSES, not by the table merely existing.
- **G4b** ephemerality: edit → proceed WITHOUT "Save this path" → export/reopen → the plan is the
  unedited derived one. Adjusting must not silently mutate the building.
- **G5** control: on a DB with no `cinema_path` table the plan is the unedited derived one.
- **G6** §CINEMA_TURN_SLERP's and §CINEMA_DAMPING_BLEED's witnesses stay green.

## §CINEMA_ATTIC_PICK — the dive target ⛔ REASSIGNED, NOT THIS SESSION'S (user, 2026-07-26)
> **"ignore the default attic issue — as another session is handling."**
>
> The "do this FIRST" ordering below is therefore VOID for this file's lane, and the §CINEMA_SPACE block
> in `_cinemaPathPlan` (~L3486-3610) is **another session's working set** — the path-editor work must not
> edit it. Kept below only as the reference record of what that other lane owns. The editor is designed
> so it does not care which room the picker chooses: the settle point is just waypoint 1, draggable.

<details><summary>original folded-in brief (now another session's)</summary>

**Do this FIRST, before the dialog.** Not a priority argument to re-run — the reasoning is simply that
this editor's value is inversely proportional to how often it is needed: a good default makes the
dialog a one-click OK (the differentiator), a bad default makes editing mandatory (just a worse
conventional tool). Fixing the default first also gives the dialog's G1 no-op gate something worth
being a no-op about.

**Symptom:** the dive "goes for the attic" — the camera settles in a top-level/roof void instead of a
real occupiable space.

**What the picker actually does today** (`viewer/effects.js` §CINEMA_SPACE, ~line 3386): ranks ALL
room-graph nodes with rects by `area / (1 + dCtr / (envelope*0.5))` — "largest space nearest the
centre" — takes the single top-ranked one, applies ONE enclosure sanity check, and falls to
bbox-centre if that fails. Live JKR evidence from the user's own log:
```
§CINEMA_SPACE cand=0pNy6pOyf7JPmXRLgxs3sW area=135.2 enclosed=0%   mep=0% chosen=false
§CINEMA_SPACE cand=0BTBFw6f90Nfh9rP1dlXr2 area=27.7  enclosed=100% mep=0% chosen=true (rank=2, skipped 1 disqualified above)
```
So the enclosure gate DOES fire and DOES skip candidates — meaning an attic that reads `enclosed=100%`
will win on area alone. Start by capturing the same `§CINEMA_SPACE` lines on a building that actually
exhibits the attic dive; do not theorise from JKR, which chose a 27.7 m² room.

**⚠ ANTI-DRIFT — do NOT "fix" this by adding floor-level weighting.** That was explicitly ruled out by
the user on 2026-07-20 and the ruling is quoted in the code's own comment: *"abandon the 'next largest
room' idea... It is disastrous for sure. Just back to original 'go to largest space within 4 sec'."*
There is deliberately **no** floor-level weighting and no multi-candidate search. A fix must come from
a different angle — e.g. a stronger occupiability probe (headroom, real floor beneath, reachability
from the room graph) — not from re-ranking by storey. Re-read that comment block before touching it.

</details>

## Out of scope — each has its own session, do not fold them in here
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
