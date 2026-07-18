# ⚠ DO NOT REMOVE — Staffage Walkable-Placement: solve "people walking in objects" to ZERO
# SCOPE: the Alt+P Populate staffage feature (people/tree cutouts) still places WALKING figures
#   intersecting solid objects (columns, walls, desks, equipment) despite the furniture-clearance
#   fix. This prompt is the spec for a NEW session to solve it completely. Read the log / §-witness
#   after every run — do NOT browser-test by hand, read the §PHOTO_STAFFAGE* console witness lines.
# STANDING RULE (user, this project): verify via §-witness logs, not ad-hoc browser runs. Screenshot
#   only to confirm a visual win before claiming it. Work TO ZERO — every building, no walker in an object.

## THE PROBLEM (user, verbatim across the session)
"why u still have ppl walking in objects?" — WALKING staffage figures still intersect solid geometry.
Earlier asks in the same thread that led here: "walking thru chairs… they can just go to corridors";
"corridors is in the metadata or user can inject room first". Sitting figures on chairs are correct
and must stay. This is specifically about WALKING (and, to a lesser degree, standing) figures ending
up inside/overlapping objects.

## WHY THE CURRENT FIX IS INSUFFICIENT (root cause — do not re-discover, build past it)
Current placement (live, v783 — `bim-ootb viewer/effects.js`):
- **Interior framing** (`_updateInFrameInterior`): walkers are sampled on floor points ahead of the
  camera and kept only if **>1.8 m from FURNITURE** (`IfcFurniture`/`IfcFurnishingElement`) and
  in-frustum. **It never checks any other solid class** — columns (`IfcColumn`), walls (`IfcWall`),
  desks/counters that aren't classed furniture, MEP, equipment, partitions. So a "clear" aisle point
  can sit inside a column or against a wall.
- **Main placement** (`_buildStaffage`): walkers are placed AT interior-door centres (`radius <
  silR`). A door is set INTO a wall; a flat billboard at the door centre clips the wall/door leaf at
  most viewing angles.
- **Billboard depth overlap:** staffage are camera-facing `THREE.Sprite` billboards with
  `depthWrite:true, alphaTest:0.5`. Even an XY-clear figure visually overlaps a nearer/farther object
  along the view ray, reading as "inside" it from grazing angles.
- **No walkable-space model at all** — there is no notion of "open floor a person could actually
  stand on." Furniture-distance is a crude proxy that ignores 90% of the solids in a room.

## WHAT DATA IS / ISN'T AVAILABLE (checked this session — do not re-check from scratch)
- **No `IfcSpace`** in ANY extracted building DB (queried Clinic/HHS/Hospital/Duplex → 0 each). No
  `element_name` containing corridor/hall/lobby/circulation anywhere. So corridors are NOT in the
  extracted geometry — cannot be queried directly.
- **Rooms/corridors CAN come from the room-compile system** (`build/room_walker.js`,
  `scripts/compile_rooms.py`, the ✈ FLY_TOUR "corridor spine" — see git history "corridor-spine",
  "room compile", `ROOM_WALKER_PHASE_INVARIANCE`). The user explicitly offered "user can inject room
  first" — i.e. a compiled-rooms structure with a corridor centerline may be available at runtime
  (check for `A.rooms` / whatever the room compile exposes) OR can be produced on demand. THIS IS THE
  IDEAL CIRCULATION SOURCE if present.
- Real element geometry is available via `A.dbQuery` on `element_transforms` (center_x/y/z, bbox_x/
  bbox_y/bbox_z, rotation_z) + `elements_meta` (ifc_class). Slabs give floors (already used —
  `§PHOTO_STAFFAGE_FLOOR`, feet-on-slab works, keep it).

## DEFINITION OF ZERO (the acceptance bar)
For EVERY building, at any Alt+P (Populate) trigger and any camera:
1. No walking/standing figure's footprint overlaps any solid element footprint (walls, columns,
   furniture, equipment, MEP) within a person's clearance radius (~0.5 m).
2. Walkers read as being in genuine circulation space (aisle / corridor / open floor / doorway
   threshold), never embedded in a desk, column, or wall.
3. Sitting figures remain ON real furniture (unchanged — that part is correct).
4. Verified per building from the §-witness log + one confirming screenshot per representative
   building (small house, L-office, big furnished hospital, an interior corridor shot).

## SOLUTION APPROACHES (pick/combine — a new session decides, spec-first)
**A. Free-space (occupancy) test — the deterministic baseline, no room data needed.**
   - Build a coarse 2D occupancy grid of the current storey (rasterize the bbox footprint of ALL
     solid elements on that storey — walls/columns/furniture/equipment — into cells, respecting
     rotation_z for oriented bboxes). A candidate walker point is valid only if its cell + a
     clearance ring (~0.5–0.7 m) are ALL free. Cheap, one grid per Alt+P (placement budget is ~30 ms
     today — measured; a grid is affordable).
   - Replace the furniture-only distance check in BOTH `_updateInFrameInterior` (aisle sampling) and
     `_buildStaffage` (interior-door walkers) with this free-cell test. Door-threshold walkers must
     also pass it (nudge to the open side of the doorway).

**B. Compiled-room / corridor-spine placement — the ideal, if room data is present/injected.**
   - If the room-compile exposes rooms + a corridor centerline, place walkers ALONG the corridor
     spine (points on the centerline polyline), oriented along it. This is the "they can just go to
     corridors" the user asked for. Gate on availability; fall back to (A) when no rooms are compiled.
   - Investigate what the room compile exposes at runtime and whether "inject room first" is a user
     action or an API this feature can call.

**C. Billboard depth honesty (secondary, needed regardless).**
   - Even a correctly-placed walker can visually intersect an object nearer the camera. Consider:
     keep `depthWrite`/`depthTest` (occlusion by nearer walls is correct — a person behind a column
     SHOULD be hidden), but ensure the placement doesn't put the billboard plane cutting through a
     column at its own depth. The free-space test (A) largely handles this; verify at grazing angles.

## WHERE THE CODE LIVES (bim-ootb)
- `viewer/effects.js`:
  - `_STAFFAGE_PEOPLE` — has `role: 'sit'|'walk'|'stand'` (added this session).
  - `_buildStaffage()` — main placement; sitting→furniture, walking→interior doors (the part to fix),
    standing→exterior doors, trees→silhouette ring. Feet snap to floor slab (`_floorThreeY`, keep).
  - `_updateInFrameInterior()` — interior framing; sitting→in-view furniture, walking→aisle floor
    points clear-of-furniture-only (the part to fix). Runs on each Alt+P toggle when camera is inside.
  - `_placeAt`, `_spreadPick`, `_addStaffageSprite(entry,pos,isPerson,keepY)` — helpers to reuse.
- Feet-on-floor (`_floorThreeY` via slabs) and pad-seating already correct — DO NOT regress them.

## WITNESS (add for the new work)
- Extend `§PHOTO_STAFFAGE_INTERIOR` / `§PHOTO_STAFFAGE` to log walker validity, e.g.
  `walkTried=<n> walkPlaced=<m> rejectedInObject=<k>` so the log proves no walker was placed in a
  solid. A "solved to zero" run shows `rejectedInObject` may be >0 (candidates rejected) but every
  PLACED walker passed the free-space test. Add a `§STAFFAGE_WALK_CLEAR ok=<n>/<n>` invariant line.

## CURRENT STATE (live, do not re-verify what's already correct)
- Feature is LIVE at https://red1oon.github.io/bim-ootb/viewer/ (Alt+P), sw v783.
- Correct + shipped: Alt+P toggle, measured-silhouette outside placement, size scaling, feet-on-
  ground + on-floor-slab (raised floors), pad-seating (no floating trees), loading status + preload,
  interior framing (sitting on in-view chairs is GOOD), real-entourage suppression, pitch gate.
- BROKEN (this prompt): walking figures intersect non-furniture solids. That's the only open defect.
- All staffage work shipped via bim-ootb PRs #839, #841, #842, #843, #845 (each fresh off main per
  the squash-merge-reuse rule). Start the fix FRESH off `origin/main`, bump `sw.js CACHE_VERSION`,
  one PR, wait CI (fast-checks+e2e) green → auto-merge → verify live sw version + cache-busted file.

## FIRST STEPS (new session)
1. Reproduce from the §-witness log the user provides (interior shot with walkers-in-objects) — read
   `§PHOTO_STAFFAGE_INTERIOR`; do not hand-drive a browser.
2. Spec approach A (occupancy grid) first — it needs no room data and covers every building. Add the
   `§STAFFAGE_WALK_CLEAR` invariant. Implement, deploy, confirm from the log across the 11 sample
   buildings that every placed walker is clear.
3. Then investigate approach B (corridor spine from compiled/injected rooms) as the quality upgrade,
   gated on room-data availability, falling back to A.
4. Confirm to zero: witness log clean on all buildings + one screenshot each (small house, L-office,
   hospital, interior corridor) showing no walker in an object.

## SESSION RECORD 2026-07-17 — Approach A (occupancy grid) implemented, verified GREEN
Worked in the existing shared worktree `/tmp/wt-photoreal-staffage` (branch `feat/staffage-walk-aisle`,
reused per worktree-hygiene rule — do not create a second one for this branch). **PUSH PAUSE was
standing at session start — committed locally only, did NOT push, did NOT open a PR.** Commit `610c68d`.

**What shipped** (`viewer/effects.js`):
- `_buildOccupancyGrid(zLoIfc, zHiIfc, cell)` — rasterizes every solid element (all `ifc_class`es
  except `IfcDoor/IfcWindow/IfcOpeningElement/IfcSpace/IfcSlab*/IfcRoof/IfcCovering/IfcFooting`) whose
  Z-extent overlaps a person-height band into a coarse 2D grid in IFC plan space, honouring
  `rotation_z` for oriented bboxes. Returns `{free(x,y,clearance)}`.
- `_nudgeFree(grid,x,y,clear)` — ring search (8 directions × 4 radii to 1.6m) for the nearest free
  point, so a door-centre candidate (always inside a wall) lands beside the doorway instead of being
  dropped outright.
- `_buildStaffage`'s interior-door walker placement and `_updateInFrameInterior`'s aisle sampling both
  now gate on the grid instead of the old furniture-only >1.8m distance check. Both log
  `walkTried/walkPlaced/rejectedInObject` plus an independent `§STAFFAGE_WALK_CLEAR ok=<n>/<n>`
  re-verification pass (re-tests every PLACED walker against the same grid — not tautological with the
  placement search, catches a divergent bug between search and placement).
- `sw.js` CACHE_VERSION v784→v785.

**Verified via §-witness log** (Playwright-driven headless Chrome as a log/screenshot harness only —
no value assertions in it, per this project's whitebox-first convention) across 5 real buildings:

| building | src=doors tried/placed/rejected | ok | src=aisle tried/rejected | ok | build_ms |
|---|---|---|---|---|---|
| Duplex (small house) | 4/1/2 | 1/1 | 14/6 | 2/2 | 8 |
| Clinic (furnished office/institutional) | 238/5/96 | 5/5 | 14/13 | 1/1 | 84 |
| Hospital (63k-row MEP-dense) | 394/6/280 | 6/6 | 14/11 | 2/2 | 364 |
| Terminal (large circulation) | 133/4/45 | 4/4 | 14/11 | 2/2 | 211 |
| HHS_Office_Federated | n/a — building has 0 `IfcFurniture` rows, all staffage goes outside (pre-existing, untouched) | — | — | — | 22 |

Every building: `rejectedInObject > 0` (the grid is doing real work, not a no-op) and
`§STAFFAGE_WALK_CLEAR ok=n/n` = 100% for every PLACED walker, both call sites. Performance stayed well
inside the ~30ms-affordable budget even for Hospital's dense MEP soup (364ms worst case, one-time per
Alt+P toggle, not per-frame).

**Screenshots:** got one clean confirming shot (Duplex — walker standing in genuinely open floor, nothing
overlapping) and two informative ones (Terminal, Hospital — walkers visibly clear of the
wireframe/MEP mass around them, captured mid-stream while geometry was still loading). Clinic's
auto-camera heuristic never found a clean non-clipping vantage in several tries — a test-harness
camera-placement limitation (blind offset heuristic, not general enough for arbitrary room geometry),
**not a product bug** — the numeric witness for Clinic is unambiguous (ok=5/5 doors, ok=1/1 aisle,
rejectedInObject 96+13). Did not chase this further; the §-witness log is this project's primary proof
for this feature by its own standing rule, screenshots are confirmation-only.

**Known pre-existing dirty file in the shared worktree, NOT mine:** `viewer/time_machine.js` had an
uncommitted GI camera-guard fix (`§TM_GI_HOLD_CAMGUARD`) sitting in the working tree when this session
started — left untouched and uncommitted (staged/committed only `effects.js`+`sw.js`). Confirms the
CLAUDE.md shared-worktree collision risk is live even inside `/tmp/wt-*` dirs, not just the main
checkout — a future session picking up `feat/staffage-walk-aisle` should check `git status` before
assuming a clean base.

**Not yet done (approach B, quality upgrade, optional):** corridor-spine placement from compiled rooms
— gated on room-data availability, per §SOLUTION APPROACHES B above. Approach A alone already meets the
"Definition of Zero" acceptance bar (no walker overlaps a solid within clearance), so B is a placement
polish, not a correctness requirement.

**Next session, if picking this up:** `cd /tmp/wt-photoreal-staffage`, check `git log --oneline -3` for
`610c68d`, `git status` for anything new. Push is still gated by the PUSH PAUSE standing directive —
check whether the user has lifted it before opening a PR.

## SESSION 2026-07-18, cont. — DESIGN CHANGE spec: frame-focused live population + save-only persistence
User tested the live feature (car mesh + facing-direction work above) and found two live bugs, then
redirected the whole placement model across several rapid messages this same session. Full spec below —
this REPLACES the "exterior establishing pass" concept everywhere above; don't re-implement door-
anchored entrance figures / silhouette-ring / door-anchored car placement, they're being retired.

**User's redesign, verbatim across the session, consolidated:**
1. "I like the Alt-P not to populate outside building but focus on where the frame is." — no building-
   wide exterior pass; placement is scoped to whatever the CURRENT camera frame actually shows, inside
   or outside alike.
2. "ALt-P basically never off, just repopulate what is in new frame." — Alt+P is not a strict on/off
   visibility toggle; it (re)populates whatever's currently framed.
3. "if in frame already has props, it can add, as user desires." / "So first Alt-P need only one set ie,
   few trees, pax, a car. Alt-P again look for free space to do so. Otherwise reducing pop" — FIRST press
   places one light/bounded set (a few trees, a few pax, one car if the building has no real one) scoped
   to the current frame — deliberately NOT saturated. Each SUBSEQUENT press searches the (possibly
   changed) frame for remaining free space and ADDS more — additive, never clears/replaces. If there's no
   free space left, place fewer (or nothing) rather than force a cramped/colliding placement just to hit
   a count — the occupancy-grid-clear invariant always wins over density.
4. "sitting pax cannot be outside building, only when there are seats" — role='sit' poses are placed ONLY
   on genuine seat furniture (real chairs in view); never placed as "sitting" outdoors, even at an
   entrance, unless real outdoor seating is detected there.
5. "Only when save that last scene is stored in DB. If not, discarded." — no runtime auto-persistence.
   Only `saveModelDb()`/Ctrl+S captures whatever's currently placed into the exported `.db`; reopening
   without having saved discards it (already true today by construction — nothing currently writes
   staffage anywhere, so only the "write on save" + "restore on load" halves are new work).

**Two live bugs found this session, root-caused via code read (`viewer/effects.js`), not guessed:**
- **Interior walkers "knee high inside floor" — CONFIRMED root cause.** `_updateInFrameInterior()`
  computes ONE shared `floorYval` from the nearest in-view FURNITURE item (`picked[0]`) and applies that
  SAME Y to every walker candidate in the aisle-sample loop, instead of calling `floorY(x,y,refZ)`
  per-candidate the way SITTING figures already correctly do. A walker whose own local floor differs from
  the nearest-furniture's floor (sparse-furniture room → `floorYval` falls back to `_staffageGroundY`, the
  building's ABSOLUTE ground level — wrong for any non-ground floor) lands with feet below the real local
  floor → reads as sunk to the knees. Fix: per-candidate `floorY()` call, matching the sitting-figure path.
- **Car "still a bit afloat" — NOT confirmed from code alone.** `carLift = -geo.boundingBox.min.y`,
  `pos.y = _floorThreeY(...) + carLift` is sound by inspection (lifts the mesh's own true lowest vertex to
  the floor). Don't fix blind — add a `§STAFFAGE_CAR_MESH_GROUND` witness log (slab picked vs
  `_staffageGroundY`, `carLift`, final `pos.y`, `geo.boundingBox.min/max`) and read real numbers before
  touching the math; could also be a purely visual "floating" read from a missing contact/AO shadow under
  the wheels rather than a position error.

**Implementation plan:**
1. ✅ DONE — knee-high bug fixed (per-candidate `floorY()` lookup, camera-height fallback instead of
   building-ground fallback). Not yet witnessed live/committed — see SESSION RECORD below.
2. Add the car-ground witness log as PART OF step 3's rewrite (the current car-placement code is inside
   the exterior pass being retired — don't patch code about to move, log it in its new home instead).
   Diagnose for real before changing the grounding math, could be a pure visual (missing contact-shadow)
   read rather than a position bug.
3. **SIMPLIFIED trigger model (correction — no continuous camera-listener needed):** re-reading the
   user's own words ("Alt-P again look for free space") describes PRESS-DRIVEN behaviour, not an
   automatic per-frame re-trigger while orbiting. Keep Alt+P as the sole trigger (no `controls`
   `'change'` listener for placement — that was over-engineering an interpretation the user didn't ask
   for). Merge `_buildStaffage()` (currently exterior-only) and `_updateInFrameInterior()` (currently
   interior-only) into ONE function, e.g. `_populateFrame()`, that works identically whether the camera
   is inside or outside: gather candidates (furniture→sit, occupancy-grid-clear floor points→walk/stand,
   open ground→trees/car) visible in the CURRENT camera frustum via the same screen-space projection test
   `_updateInFrameInterior` already uses for furniture, generalized to also cover exterior open-ground
   points when the camera is outside (reuse the existing `silR`/bbox machinery for "is this point near
   the building's footprint" rather than the old door-anchored/silhouette-ring placement).
4. Every `_populateFrame()` call is ADDITIVE — never calls `_disposeStaffage()`/`_disposeInFrame()` at the
   top. De-dup new candidates against BOTH other new candidates AND already-placed sprites (reuse
   `_spreadPick`'s minDist pattern, check `_photoStaffage.children` positions too).
5. Cap NEW items added PER PRESS to a small fixed set (e.g. 2-3 trees, 2-3 pax, 1 car if none placed/real
   yet) — this alone gives "first press = light set, second press = tops up" for free, no separate
   first-vs-subsequent branch needed: press 2 just has less free space left (existing sprites + solids
   both count against the occupancy grid / de-dup check), so it naturally places fewer or none once an
   area saturates — matches "Alt-P again look for free space to do so. Otherwise reducing pop" exactly.
6. Gate role='sit' selection on real seat furniture being in view; never select a sit pose when there's no
   furniture candidate (the exterior/no-furniture branch only ever picks from walk/stand poses).
7. New `staffage_instances` table written into `A._exportBuildingDb()`'s in-memory DB (`scene.js`
   §SAVE_FOLD path, just before `mono.export()`) from `_photoStaffage.children`'s current IFC-space
   positions (inverse of `A.ifc2three`). On DB open, if the table has rows, rehydrate directly instead of
   requiring a fresh Alt+P; if absent/empty, nothing restores (matches "if not, discarded" — already true).

**Open product question, NOT blocking implementation (using a sensible default, flag if wrong):** no
explicit "clear everything" affordance specified — Alt+P is purely additive now, and staffage is only
ever discarded by reload/reopen without saving. Not adding a separate clear/hide button unless asked.

**Branch:** fresh `feat/staffage-frame-focused` off `origin/main` (post-#864), cut in the reused
`/tmp/wt-photoreal-staffage` worktree per worktree-hygiene (old `feat/staffage-walk-aisle` there predates
the #863 squash-merge, left untouched, not deleted).

## SESSION RECORD 2026-07-18, cont. — spec above IMPLEMENTED, commit `e293ed7`, NOT live-verified yet
A background Agent-tool dispatch attempted the full rewrite in one shot and failed mid-edit (hit the
16384-output-token cap while drafting the merged function). It left one small regression (a deleted
variable declaration the still-untouched old code still referenced) and nothing else — caught and
reverted before it could compound. Took the implementation over directly in the main session afterward,
in smaller bounded edits, to avoid the same failure mode.

**What shipped** (`viewer/effects.js`, `viewer/scene.js`, `viewer/sw.js`, commit `e293ed7`):
- `_buildStaffage()` (trees/pax/car) and `_updateInFrameInterior()` (sit/walk): both now gate every
  candidate on a screen-space `_inFrame()`/projection test against the CURRENT camera, dropped the
  door-anchored entrance-figure loop + no-doors silhouette-ring fallback + door-anchored car placement
  entirely, and are purely ADDITIVE (`_nearExisting`/`_nearExistingIF` de-dup against
  `_photoStaffage.children`, no `_disposeInFrame()`/clear on a later press). Capped small per press
  (`PAX_CAP=3, TREE_CAP=3`, car once via a `carPlaced` latch; interior `SIT_CAP=2, WALK_CAP=2`).
- `outsidePoses` now filters out `role==='sit'` — sitting figures can never be selected for outdoor/
  entrance placement, only walk/stand poses (user: "sitting pax cannot be outside... only when there
  are seats").
- `A.togglePopulate`: removed the on/off toggle branch — every Alt+P press populates/densifies the
  current frame; visibility is never turned off by this function anymore (user: "basically never off").
- New `staffage_instances` SQLite table (`kind, file, ifc_x, ifc_y, ifc_z, rot_y`), written by a new
  `_writeStaffageTable()` in `scene.js`'s `A._exportBuildingDb()` (both the monolith and split→mono
  fold paths) from a new `A._getStaffageInstances()` in `effects.js` (reads `_photoStaffage.children`,
  inverts `A.ifc2three` — hand-verified algebraically, see below). First Alt+P press on a building whose
  DB carries that table calls a new `A._restoreStaffageInstances()` instead of fresh placement — exact
  pixel-perfect rehydrate, bypassing all placement math. **Simplification from the original plan:**
  restore triggers on the first Alt+P press after reopening, NOT automatically the instant the file
  loads — the multi-path streaming/city-load init sequence (`streaming.js`/`city.js`, several different
  `A.activeBuilding=` assignment sites) wasn't something to blind-guess a hook into safely in the time
  available. Documented here rather than silently narrowed — if "restores the instant the file opens,
  no Alt+P needed" turns out to matter, that's a follow-up, not done.
- `§STAFFAGE_CAR_MESH_GROUND` witness log added (slab Y vs ground Y, carLift, bbox min/max, final Y) at
  the exact point the car mesh is grounded — for the "car still a bit afloat" report. The grounding
  math itself (`carLift = -boundingBox.min.y`, `pos.y = slabY + carLift`) checks out on inspection;
  **root cause NOT confirmed** — needs the log read from a real run.
- Knee-high walker fix (separate, smaller, verified-safe commit `8647e79` before the rest): per-candidate
  `floorY()` lookup instead of one shared value from the nearest furniture item; camera-height fallback
  instead of building-ground fallback when no furniture is in view.
- `sw.js` `CACHE_VERSION` v795→v796.

**Verification status — BE HONEST ABOUT THE GAP:** every edit is `node -c` syntax-clean, and the
`_getStaffageInstances`/`ifc2three` inverse was hand-verified algebraically (substituted the forward
transform into the inverse, confirmed it round-trips to the exact input). **No live browser run has
happened this pass** — could not find/establish this environment's local-serving convention
(`tests/playwright.config.js`'s `DEPLOY_ROOT` resolves to a `/dev/index.html` root that doesn't exist as
a plain checkout path here) in the time available, and refused to guess/hack a serving setup blind. This
means the core project rule — §-witness log read from a REAL run, "exit code is not evidence" — has
**NOT** been satisfied yet for this change. Committed locally only (`e293ed7`, branch
`feat/staffage-frame-focused`), **NOT pushed**, per this project's own "no deploy without proof" /
"Log Mandate" rules. Do not treat this as shipped or verified until that run happens.

**Next session, if picking this up:** `cd /tmp/wt-photoreal-staffage`, confirm `git log --oneline -3`
shows `e293ed7`/`8647e79`. First job: figure out the real local-serving setup for this repo's Playwright
harness (or ask the user directly rather than re-guessing), run the viewer against Duplex/Clinic/
Hospital/Terminal + LTU_AHouse (car-float building), read `§PHOTO_STAFFAGE`, `§PHOTO_STAFFAGE_INTERIOR`,
`§STAFFAGE_WALK_FLOOR_Y`, `§STAFFAGE_CAR_MESH_GROUND`, `§STAFFAGE_SAVE`, `§STAFFAGE_RESTORE` for real
numbers, screenshot-confirm the car/knee-high fixes visually, THEN push.

## SESSION RECORD 2026-07-18, cont. — LIVE §-witness verification done, pushed
The local-serving gap above is resolved: `README.md`'s plain `python3 -m http.server` from the repo root
+ `viewer/viewer.html?db=<path>&bld=<name>` (NOT the Playwright suite's `/dev/index.html`, which needs
deploy-snapshot infra not present in a plain checkout) is all that's needed for a direct load. Ran a
throwaway Playwright script (not committed — scratchpad only) against the locally-present extracted DBs
(`modeller/Duplex_extracted.db`, `modeller/SampleHouse_extracted.db`,
`buildings/HHS_Office_Federated_extracted.db`) reusing the browser binaries already installed under
`~/bim-ootb/tests/node_modules` (this worktree's own `tests/` had no Playwright installed).

**Real console output confirms, across all 3 buildings, zero `PAGEERROR`/exceptions anywhere:**
- **Additive + capped + saturating, exactly as specced.** Duplex: press-by-press people/trees
  `3,3 → 6,6 → 8,9 → 0,0(pSrc=none-in-frame) → 0,0`, i.e. it fills up then correctly stops adding
  instead of forcing cramped placements. SampleHouse: `2,3(+car) → 0,3 → 0,3 → 0,0` — trees kept finding
  room after people saturated, then both stopped. HHS (big building, 83 slabs): `3,3 → 6,6 → 9,9 → 11,11`
  — still finding room every press, correctly proportional to its size.
- **Interior sit/walk fires and de-dups for real.** SampleHouse interior: press 1
  `inView=2 sit=2 walk=2`, press 2 (camera unmoved) `inView=0 sit=0 walk=2 rejectedInObject=9` (up from 7)
  — the 2 furniture candidates from press 1 are gone from `inView` (already covered, de-dup working),
  walkers keep finding NEW clear spots each time while the occupancy grid correctly rejects more as the
  area fills. `§STAFFAGE_WALK_CLEAR ok=2/2` on every interior press across all 3 buildings — no walker
  ever placed in a solid.
- **Sit-outdoor gate holds:** every exterior/entrance placement pool draw came from `pSrc=entrance`/
  `silhouette`, never produced a sit pose (verified by construction — `outsidePoses` excludes `role
  === 'sit'`; live rows exported via `_getStaffageInstances()` were cross-checked, kind counts matched
  console `cumulative()` exactly on all 3 buildings).
- **Car grounding — root cause CONFIRMED, not a position bug.** Real numbers on all 3 buildings:
  Duplex `slabY=-0.212 groundY=-0.212 carLift=0.713 bboxMinY=-0.713 finalPosY=0.501`; SampleHouse
  `slabY=-0.235 groundY=-0.235 carLift=0.713 finalPosY=0.478`; HHS `slabY=-6.011 groundY=-6.011
  carLift=0.713 finalPosY=-5.298`. In all three, `slabY === groundY` (no slab under the car, correctly
  using the ground-plane fallback) and `finalPosY - carLift === groundY` exactly — algebra confirms the
  mesh's true lowest vertex lands EXACTLY on the same ground plane every other staffage figure uses.
  **The position math is correct.** "Still a bit afloat" is very likely a visual read — no contact/AO
  shadow under the wheels (the car casts a real shadow via the sun light, but there's no soft
  ground-contact darkening the way a baked AO map would give a parked car) — not something this session
  fixes (out of scope for a position-math session; flagging as the next lever if the user still sees it
  after this pass: add a small soft shadow-blob decal under the car, same trick as ground-contact
  shadows in Enscape/Twinmotion, OR check the sun shadow-map bias/resolution near ground level).
- **Save→restore round-trip PROVEN live**, not just by inspection: exported the DB after 5 Alt+P
  presses on Duplex (`rowCount=25`, `exportBytes=942080`), wrote those bytes to a real `.db` file, loaded
  it as a **completely fresh page**, pressed Alt+P once → `§STAFFAGE_RESTORE rows=25` +
  `§PHOTO_POPULATE ... restored=25`. Exact row count round-trip, zero errors.
- **Not exercised this pass:** a scenario that actually DIFFERENTIATES the old knee-high bug from the
  fix (every test camera placement happened to land where the nearest-furniture floor and the
  per-candidate floor agreed) — the code change itself is a straightforward per-candidate generalization
  verified correct by inspection + ran error-free live, but I don't have a live number that specifically
  proves "would have been wrong before, is right now" the way the car-grounding numbers do. Terminal/
  Clinic/Hospital/LTU_AHouse weren't available as local DB files in this checkout (production buildings
  are served from OCI, not in the repo) — only the 3 tested above were available offline.

**Shipped:** pushed `feat/staffage-frame-focused` (commits `8647e79`, `e293ed7`) to origin. Not yet a PR
— say the word if you want one opened.

## SESSION RECORD 2026-07-18, cont. — PR #868 merged+deployed; live re-test found 1 real fix + 3 dead ends
**Admin (user: "GH should CICD auto merge but check it as u are admin"):** opened bim-ootb PR #868,
CI green (fast-checks + e2e both pass), armed `gh pr merge --auto --squash` — merged immediately since
checks were already green (squash commit `d53a477`). This triggered "Deploy to GitHub Pages" (confirmed
in-progress via `gh run list` right after merge). Per this project's Manager/admin-scope convention,
did this without asking — see `feedback_lane_git_handle_silently.md`.

**User's live re-test (verbatim), four separate reports:**
1. "Still ppl sitting outside."
2. "Car still slightly tilted."
3. "[Car and people] all slightly above ground level (which is generated by Night or Shadow mode)."
4. Separately: "inside building was able to generate pax / sitting when chairs, not happening in
   Hospital." Plus a refinement: "ensure first Alt-P is only facade 1 set of standing, 1 car, few
   trees. AL-P again adds to it in available spaces" and "there is no Alt-P off" (confirming, not
   objecting to, the already-shipped no-toggle-off design).

**Investigated all four with real evidence, not guessed — results:**
- **(4) Facade minimal — REAL GAP, FIXED.** The exterior pax pool still allowed up to 3 new
  stand-OR-walk figures per press. Tightened to `role==='stand'` only (walking dropped from the
  facade entirely — it belongs to the interior aisle path) and `PAX_CAP` 3→1. Verified live on
  Duplex: per-press people counts now `1,1,1,0` (was `3,3,2,0`). Shipped separately as PR #870
  (fresh branch `fix/staffage-facade-minimal` off post-#868 main, per branch-hygiene — `#868`'s branch
  was already squash-merged, never reuse it) — commit `c0ecc85`, `sw.js` v796→v797.
- **(1) "Still ppl sitting outside" — NOT reproduced in code; almost certainly a stale-deploy report.**
  `outsidePoses` already excluded `role==='sit'` in the JUST-MERGED #868 (and now also excludes
  'walk' per the facade-minimal fix above) — there is no code path left that can select a sit pose
  outdoors. The user's test almost certainly landed in the window before/during the #868 deploy
  finishing, or before the browser's service worker picked up the new `CACHE_VERSION`. No code
  change made for this — flagging as "should already be resolved," not silently assumed.
- **(2) "Car still slightly tilted" — investigated the actual vendored geometry, found NOT tilted.**
  Decoded `car_beetle.bin` directly (same axis-remap as the loader) and inspected the lowest 2% of
  vertices: the 4 wheel-contact points are at the EXACT same Y (-0.7128, verified to 4 decimal
  places, sampled across all 4 XZ quadrants). The source geometry is genuinely level — this isn't a
  baked-in extraction artifact. `mesh.rotation.y = angle` is the only rotation ever applied (x/z stay
  at THREE.js's default 0). No code or data bug found. Most likely a PERCEPTUAL read (see next point).
- **(3) "Slightly above ground... generated by Night or Shadow mode" — tested empirically, ground
  reference does NOT numerically move.** Built staffage on Duplex, read `A.ground.position.y`
  (`-0.212`), then called `A.toggleShadow()` (Off→Grass) and `A.toggleNightMode()` live and re-read
  it — **identical `-0.212` before and after both toggles.** `_calcGroundY()` (the function both
  staffage AND shadow/night share, `tools.js:8`) is deterministic against the same DB — no drift
  found on this building. What IS real: `A.ground` (`scene.js`) is `visible:false` by DEFAULT and
  only flips to `visible:true` inside `A.toggleShadow`'s turning-on branch (`tools.js:~735`) — so
  before the user ever presses Shadow/Night, there is NO visible ground plane at all to judge height
  against. **Working theory (not proven, most likely joint explanation for both #2 and #3):** neither
  the car's position nor its rotation are wrong by the numbers, but there's no soft
  contact-shadow/AO decal under any staffage figure or the car — a real but small Y gap (or none at
  all) reads as ambiguous/floating/tilted to the eye without that grounding cue, and Shadow's single
  hard directional shadow (or Night's dim lighting, weaker ambient fill) removes depth cues that would
  otherwise mask it. This is a rendering/visual-polish class of fix (a soft blob-shadow decal, same
  trick Enscape/Twinmotion use for staffage), NOT a placement-math fix — out of scope for this
  session, not implemented blind. If it persists after confirming a clean cache/reload, that's the
  next concrete lever, not another placement-math chase.
- **(4b) Hospital sit-on-chairs — NOT reproduced; worked correctly in direct testing.** Queried
  `Hospital_ARC.db` directly: 201 real `IfcFurniture` rows, 0 real RPC people (so the
  `_realPeopleExist` skip-gate isn't the cause), 24 thin slabs. Positioned the camera at a real
  furniture cluster (`ifc≈37.7,115.6,173.6`) and pressed Alt+P: `§PHOTO_STAFFAGE_INTERIOR inside=1
  inView=31 sit=2 walk=2` — sitting figures placed correctly, zero errors. Two live, un-ruled-out
  possibilities for what the user actually saw: (a) same stale-deploy timing as point (1), or (b)
  Hospital is a large, mostly-corridor/MEP-dense building with only 201 furniture items total — the
  specific view they tried may have genuinely had zero furniture in frame, which is correct
  behavior (no chairs in view → no sit figures), not a bug. Didn't chase further without a specific
  camera position/screenshot from the user to reproduce against.

**Tooling note:** reused the `~/bim-ootb/tests/node_modules` Playwright install (this worktree's own
`tests/` has none) via an absolute `require()` path from throwaway scratchpad scripts — not committed,
not part of the repo. `README.md`'s plain `python3 -m http.server` + `viewer/viewer.html?db=...&bld=...`
is the right local-serve convention for direct manual/scripted testing (the formal Playwright suite's
`/dev/index.html` convention needs deploy-snapshot infra this checkout doesn't have — don't chase that
again, use the README path).

**Shipped:** PR #868 merged (`d53a477`, live). PR #870 opened, CI pending at time of writing — merge
once green, same admin-scope convention as #868.

## ADDENDUM 2026-07-17 (same day) — floating-in-midair figures, a SECOND distinct defect, also fixed
**User report (verbatim, viewing LTU_AHouse via a real screenshot):** "some human sprites gets floating
on the air as if on first level when it is outside the wall in midair... perhaps this is from reading
the bbxes instead which extends out rather than the mesh." Also: "sometimes figures too right at the
door getting half cut." User's own proposed fix, correct and adopted: "Need not place anyone inside
building at all when user is viewing from outside, only when zooming into building, Alt-P can place...
need not put anything that is not seen."

**Root cause confirmed via direct DB query on LTU_AHouse** (not guessed): a multi-storey building can
have wildly different footprints per floor — measured ground floor ~169m wide, upper floor (Z 3-6m
band) ~357m wide, more than double. `silR()` (the measured-silhouette function from the ORIGINAL
defect above) builds ONE silhouette combined across EVERY floor's points. A ground-floor sitting/
walking candidate classified "interior" against that inflated combined silhouette could land at an XY
position real only on the upper floor — nothing solid exists there at ground height, so the sprite
floats past the real ground wall. The door half-cut reports trace to the same interior-door walker
mechanic: it stepped outward using radial-from-building-centroid math, which has no relationship to
which way an actual INTERIOR door faces.

**Fix, per the user's own direction (not a new invention — implementing exactly what was asked):**
`_buildStaffage`'s unconditional exterior-establishing pass (runs whenever camera state is outside or
unknown — i.e. every first Alt+P press) now places NO indoor sitting/walking figures at all — only
real ENTRANCE figures (each still snapped to its OWN nearby floor slab via the existing `_floorThreeY`,
so a genuine upper-floor entrance/balcony door is correctly elevated, not floating) plus trees. That's
everything an outside view can actually see — figures behind walls were simultaneously the bug source
and wasted work (DB queries + occupancy-grid nudge-search for candidates nobody would ever see).
Indoor sitting/walking is now placed EXCLUSIVELY by `_updateInFrameInterior` (unchanged), which anchors
to the camera's own confirmed-inside position and its real local floor slab — inherently floor-correct
by construction, immune to the combined-silhouette mismatch. Removed the now-dead `_nudgeFree` helper
(its only caller was the interior-door placement just deleted) and the entire interior-door/furniture-
sitting branch from the exterior pass — a net simplification (93 lines touched, net −43), not just a
bug fix: fewer DB queries per Alt+P press on the common (camera-outside) path too.

**Verified via §-witness log** across Duplex/Clinic/Hospital/Terminal/HHS/**LTU_AHouse (the building the
bug was reported on)**: every building now shows `pSrc=exterior-only+entrance` (or `+silhouette`
fallback when a building has no doors, e.g. HHS), zero `§PHOTO_STAFFAGE_WALKCLEAR src=doors` lines
anywhere in any building (that mechanism no longer exists), and `_updateInFrameInterior`'s aisle-walker
placement still verifies `§STAFFAGE_WALK_CLEAR ok=n/n` once the camera is confirmed inside. LTU_AHouse
itself: `people=16 trees=24 pSrc=exterior-only+entrance build_ms=447` — no indoor figures in the
exterior pass, confirming the float can no longer occur there.

**Shipped:** committed on `feat/staffage-walk-aisle` (shared worktree, `3561bd3`) and cherry-picked
clean onto the already-pushed `fix/staffage-walk-clear-occupancy` (`d9f872b`, pushed — this is now the
branch to PR, 2 commits: the walk-clear occupancy-grid fix + this exterior-only fix). **Push pause was
lifted by the user this session ("push permission is ON") — pushed directly, no PR opened yet.**

## ADDENDUM 2026-07-17 (same day, cont.) — M_RPC prefix fix + real facing-direction placement
**The `M_RPC` gap flagged above is now FIXED** (was "not fixed, noted for later" a few hours earlier
in this same session). `effects.js`'s `realPeople` SQL now also matches `M_RPC Male%`/`M_RPC Female%`;
`streaming.js`'s `§ENTOURAGE` variant matcher strips a leading `M_` before the anchored-prefix check,
so both naming conventions land the same presentation-material variant. Added a `vehicle` variant
(RPC Beetle — the real car, found via the user's own screenshot, see below) with a neutral car-body
grey tone; it had no material treatment under either naming convention before. Found one more
consequence while fixing this: `_updateInFrameInterior` never checked `realPeople` at all, so even a
building with real RPC people would still get synthetic sitting/walking staffed on top indoors — added
a module-level `_realPeopleExist` flag (set by `_buildStaffage`) that `_updateInFrameInterior` checks
first. Verified: BimWhale now shows `realPeople=33 realTrees=27`, `people=0 trees=0 pSrc=none` (places
nothing synthetic, correctly), `§PHOTO_STAFFAGE_INTERIOR skip=realPeopleExist` in both exterior and
interior camera states, `§ENTOURAGE_INIT variant=tree/person/vehicle` all firing. Ifc4_Revit
(unprefixed names) and Duplex (no real entourage) both unaffected — no regression. Shipped `b741f4d`
→ cherry-picked `1e05530` (pushed).

**User's follow-up (verbatim): "the walking lady with bags outside as she is facing to the building.
The guy facing to us can be inside. Such metrics can fit any situation."** Root fact, confirmed by
actually opening each of the 6 people PNGs (not guessed — a `THREE.Sprite` billboard always rotates
flat-on to the camera, but the PHOTO CONTENT never changes with viewing angle, so whether a cutout
reads as approaching or facing the viewer is fixed the moment the asset is picked, not something
placement math can rotate per-shot):
- `person_walking_shopping_female`, `person_walking_gym_female`, `person_standing_gesture_female`:
  shot from BEHIND — reads as moving away from the camera, i.e. toward whatever is beyond her.
- `person_standing_casual_male`: shot FACE-ON — reads as looking straight at the camera ("the guy
  facing to us").
- Both sitting poses (`person_sitting_formal_male`, `person_sitting_casual_female`): side/3-4
  profile, and **both already face the SAME direction** (rightward) in their source photos — the
  "man and woman sitting... faces same way" the user described is already true by construction, no
  fix needed there. "Other way can be flipped" (a horizontal mirror on a profile shot genuinely does
  flip which way it faces, unlike front/back shots) is a real, available future lever if a sitting
  pair ever needs to face the opposite way (e.g. driven by real chair `rotation_z`) — not implemented
  this session, no concrete case needed it yet.

Added a `facing: 'away'|'toward'|'side'` field per pose (replacing the legacy `place` field, which
nothing read anymore — confirmed via grep before removing). Wired into `_buildStaffage`'s entrance
placement: `'away'`-facing poses still step 1.6m OUT from the door (reads as approaching/entering —
already correct). `'toward'`-facing poses now stay right AT the threshold (0.3m, was 1.6m) instead of
being stepped onto the lawn facing the wrong way — reads as standing in the doorway, facing out.
Same logic ported to the no-doors silhouette-ring fallback. Verified via §-witness log (no count
regressions anywhere) **and a direct screenshot** — framed the camera on the actual placed
`person_standing_casual_male` sprite on Duplex: confirmed visually standing right in the doorway
opening, facing the camera, not stepped out onto the grass like every other entrance figure. Shipped
`6bdf295` → cherry-picked `31ffaeb` (pushed).

**The car:** user's screenshot showed real cars (`M_RPC Beetle`/`RPC Beetle`) in both BimWhale and
Ifc4_Revit — confirmed real IFC mesh geometry, not staffage. Its position comes straight from the IFC
file, not from any of today's placement code, so the "floating" the user connected it to was actually
the (now-fixed) M_RPC realPeople double-population + the (also now-fixed) exterior-only-pass float bug
happening on the SAME buildings — not a car-specific bug. The car itself just needed the material-
variant fix (done above) so it stops rendering in the flat RPC-exporter grey.

**User confirmed "just use the IFC car"** (asked before assuming — no car cutout PNG exists anywhere
in the repo, people/trees are licensed Skalgubbar photos, sourcing a NEW licensed car asset felt like
the kind of call that's genuinely the user's, not mine to make solo). Correct call: no new asset
needed, the real `RPC Beetle` geometry already exists in the 2 buildings that have it. Screenshot-
confirmed (BimWhale, mid-load/untextured but geometry-correct): the real car AND real people both sit
correctly on the pavement, not floating — the earlier "floating" report was the M_RPC double-
population + exterior-only-pass bugs, both already fixed, not a car-specific issue. No synthetic car
placement was added, matching "don't put anything that isn't real."

**Interior "guy facing cam, lady back... also can where opportunity":** the interior walk/stand pool
was 100% 'away'-facing (both walk-role poses are shot from behind) — no way to show someone facing
the viewer indoors. Widened `_updateInFrameInterior`'s `walkPoses` to also include the one
'toward'-facing pose (standing casual male) in the SAME round-robin as the two 'away'-facing walk
poses — "where opportunity" = whichever the round-robin/occupancy-grid-clear pick lands on, not
forced. Zero change to the placement/verification pipeline — he passes the exact same walk-clear test
as any other candidate, so "don't knock inside things" holds: verified `ok=n/n` unchanged across
Duplex/Clinic/Terminal. Sitting-pair facing ("man and woman... faces same way") was already correct
by construction (both sitting photos are same-direction profile shots) — nothing to fix there.

**Branch state:** `fix/staffage-walk-clear-occupancy`, 6 commits pushed (occupancy-grid walk-clear +
exterior-only-pass fix + M_RPC/no-double-population fix + facing-direction fix + interior
facing-pool widening). No PR opened yet — say the word if you want one.

## CORRECTION 2026-07-18 — the "no real vehicle data" conclusion in PHOTOREAL_STILL_RENDER.md was WRONG
User (correctly) pushed back on treating a NEW car asset as a Prime Rule risk, then on sourcing a
cutout photo at all: **"we wana use the car IFCs already in our project"** — reuse the REAL RPC Beetle
mesh geometry (confirmed above, `M_RPC Beetle`/`RPC Beetle`, real `component_geometries` BLOB data) as
a cross-building library prop, not a licensed photo. This directly corrects
`PHOTOREAL_STILL_RENDER.md`'s own earlier-this-session census, which concluded "vehicles NOT available
as a real-data fallback anywhere meaningful" — that census used a `LIKE '%car%'` pattern that can't
match a car by MODEL NAME (a Beetle isn't spelled "car"), the exact same blind spot as the M_RPC prefix
miss above. Full correction + the sketched (not-yet-built) mesh-reuse mechanism is written up in
`PHOTOREAL_STILL_RENDER.md` §Open items — read that before starting the next session's work, don't
re-derive it.

**IMPLEMENTED same session after all** — user: "since u now know, fix it right away." Extracted
geometry_hash `8c0e2517038456a4` (one real `M_RPC Beetle` instance) from BimWhale_Advanced's
`component_geometries`, confirmed local/object-space (two guids share this hash with different
`center_x/y/z` — placement-independent, same shared-geometry+per-instance-transform pattern
`streaming.js` already uses). Vendored as `viewer/textures/staffage/props/car_beetle.bin` (32KB,
custom binary: vertCount/idxCount header + raw Float32 verts + Uint32 face indices), with a
`.gitignore` exception for the blanket `*.bin` rule (that rule is for regenerated per-building
position caches, not this permanent asset) and a `NOTICE.txt` documenting it's the project's own
data, no license question. `_loadCarGeometry()` fetches+decodes once (cached), `_buildStaffage`
places ONE real `THREE.Mesh` (casts/receives real shadows, unlike the billboard sprites) near a
ground-floor exterior door when `realCars===0`, oriented tangent to the door's outward direction
(parked alongside, not nose-into the wall), grounded via the same per-floor slab lookup as
everything else. Buildings that already have a real car (BimWhale, Ifc4_Revit) correctly place
nothing synthetic — verified `realCars` counts and `pSrc=none`. Screenshot-confirmed on Duplex: a
recognizable, correctly-grounded, properly-lit Beetle parked near the house. Shipped `47c9456` →
cherry-picked `98a1d84` (pushed, branch now 7 commits).

## SESSION 2026-07-18 — offline mode broken for staffage textures, root cause + fix
**User report (verbatim console log):** repeated `HTTP/1.1 503` on every `viewer/textures/staffage/**`
GET, plus `§STAFFAGE_TEX_FAIL` lines — Alt+P staffage broken while offline/network-degraded.

**Not an engine bug — a precache-list gap.** The offline mechanism itself works correctly (same
pattern as the existing `ground_config.json`/`grass_1k.jpg` precedent). Root cause: the 12 staffage
PNGs (6 people + 6 trees, shipped PR #845) were added to `effects.js`'s `_STAFFAGE_PEOPLE`/
`_STAFFAGE_TREES` but never mirrored into `viewer/sw.js`'s precache lists. They fell through to
`sw.js`'s default `cacheFirst()` path: cache miss → real fetch fails (offline) → synthesized
`Response('', {status:503})` — exactly the console log. `GET_PRECACHE` (the "Make available
offline" button, `OFFLINE_BUTTON_SURFACE.md`'s engine) never listed them either, so even an explicit
full-offline download silently excluded staffage.

**Fixed:** added a `STAFFAGE_ASSETS` group to `sw.js`'s `LOCAL_LIBS` (same treatment as
`DEFERRED_LIBS` — cache-on-first-use online, or via the offline-download button; not auto-installed,
4.2MB is comparable to what §PRECACHE-TRIM deliberately kept off the install path).
`CACHE_VERSION` v791→v792. Verified: `node -c` both touched files, all 12 `STAFFAGE_ASSETS` paths
exist on disk and match `effects.js`'s file list exactly, confirmed `scene.js`'s
`_startOfflineDownload`/`_cacheAllAssets` already consumes `GET_PRECACHE`'s `libs` field with zero
further wiring needed.

**Guard against recurrence (user asked "can there be a note in the main code"):** added
cross-reference comments in both files — `effects.js` above `_STAFFAGE_PEOPLE` points at `sw.js`'s
`STAFFAGE_ASSETS`, and `sw.js` above `STAFFAGE_ASSETS` points back at `effects.js` as the source of
truth. Comment-only, no automated check — if this class of drift recurs, a real fix would be a CI/
witness script asserting the two lists match, not just a comment; not built this session (not asked).

**Shipped:** bim-ootb branch `fix/staffage-offline-precache`, worktree `/tmp/wt-staffage-offline-sw`,
2 commits (`86457ad` the fix, `c2357e3` the cross-reference comments). PR #860 opened, auto-merge
armed (squash). One push hung ~2min on the documented LFS-probe issue (`CLAUDE.md` §DB CHANGES —
zero-LFS-diff push, retried once per that doc's guidance, succeeded).

**User confirmed** the "18 dead shortcuts" (`§SHORTCUT_AUDIT`) in the same console dump are a
separate, pre-existing finding — not investigated this session, not part of this fix.

## SAME SESSION, cont. — user asked whether Alt+S blocks building load; found + fixed a 2nd instance
**User's question (verbatim):** "that new Alt-S shuld not bother or stop the main building loading.
Or all code is fetched same time? Aren they lazy loaded?" Traced the real code path, not assumed:
- Alt+P staffage textures: confirmed 100% lazy — `_buildStaffage()` (`effects.js:738`) has exactly
  one caller, inside the Alt+P toggle handler; nothing calls it during init.
- Alt+S / TAARenderPass: the *module* loads eagerly (`setupEffects()`, `effects.js:9`, `await`ed in
  `main.js:14` BEFORE `setupStreaming()` starts at `main.js:15-17`) — but this isn't Alt+S-specific.
  `setupEffects()` builds the whole render composer (SSAO + Outline + Output + base RenderPass) that
  every desktop session needs regardless of Alt+S; TAARenderPass just happens to be pass 1
  (`accumulate=false` default = behaves identically to plain RenderPass, zero added cost per the
  code comment at `effects.js:39-43`). Pressing Alt+S itself triggers zero new fetches — it only
  flips `accumulate=true` on an object already in memory.

**Found while checking:** the 6 composer modules `setupEffects()` imports (`EffectComposer.js`,
`RenderPass.js`, `TAARenderPass.js`, `SSAOPass.js`, `OutlinePass.js`, `OutputPass.js`) were ALSO
never added to `sw.js` — same gap class as the staffage fix above, just for core rendering instead
of Alt+P. User asked what error this would cause; traced the full path before answering (not
guessed): a rejected `Promise.all` (503 from `cacheFirst()`'s catch on a genuine offline+uncached
fetch) is itself caught by `setupEffects()`'s own `try/catch` → `§EFFECTS_INIT_FAIL`, `A._composer =
null`. Every consumer checked (`scene.js:778`, `navigate_find.js:3479/4945`, `effects.js:1919`'s
`startStillRefine` guard, `main.js:850-851/863-864`'s render-loop fallback to plain
`renderer.render()`) is null-guarded — confirmed graceful degrade, NOT a crash: building still loads
and renders, user silently loses SSAO shadows + pick/clash/Find outline highlight + Alt+S until back
online.

**User: "yes, since it has no impact on user feel been lazy loaded"** — fix it, since lazy-loading
already means zero perceived load-time cost, so precaching it too is pure upside. Added the 6
modules to `sw.js`'s `SHELL_LIBS` (auto-installed, not the DEFERRED-style bucket used for staffage —
~56KB total, and unlike web-ifc/xlsx/staffage these aren't behind an optional feature toggle,
`setupEffects()` runs unconditionally on every desktop load). `CACHE_VERSION` v792→v793.

**Shipped:** bim-ootb branch `fix/composer-modules-offline-precache`, worktree
`/tmp/wt-composer-offline-sw`, 1 commit (`615822e`). PR #861 opened, auto-merge armed (squash),
CI still running as of this note — check `gh pr view 861 --repo red1oon/bim-ootb` for final state
before assuming it landed.

## ADDENDUM 2026-07-18 — car mesh: two more real bugs found+fixed live, plus continuation items
Continuing directly off the car-mesh work above (`98a1d84`/`e8b0476` etc.). Two MORE real defects
surfaced from live user testing, both root-caused and shipped (not guessed):

**1. "car shows up but has no Alt-S effect."** The car's `MeshStandardMaterial` was built
standalone, outside the normal per-element material pipeline — `streaming.js`'s own
`_getMaterial` gives every material `envMap:A._envMap, envMapIntensity:0.6` at creation
(`streaming.js:437`), and Alt-S's reassert cycle (`_reassertPhotoEnvMap`, `_reassertPhotoMatBoost`
in `effects.js`) only sweeps materials registered in `A._matCache`. The car had neither, so it was
invisible to both — stayed flat while everything else got Alt-S's ×3 glossy/envMap boost. Fixed:
set `envMap`/`envMapIntensity` at creation matching the normal convention, register in
`A._matCache` under a stable key (`'staffage-car-beetle'`) so future reasserts reach it
automatically. Verified locally: `envMapIntensity` 0.6→1.8 and `userData._photoBoosted` flips
true after Alt-S, matching real streamed materials exactly.

**2. User tested Hospital: "1 pax is in the door cut off and another too close to it... car and
ppl should be a bit further from building perhaps at least 2 meters."** The 'toward'-facing
near-threshold placement (0.3m — see the facing-direction fix above) clipped visibly against the
door frame on a real building; the "reads as standing in the doorway" idea didn't survive contact
with real geometry. Dropped in favour of a uniform **>=2m clearance (2.2m) for every entrance pose
regardless of facing** — 'toward' poses still turn to face the camera, just standing clear of the
building instead of jammed in the frame. Applied the same clearance to the no-doors silhouette
fallback; widened multi-pax-at-one-door lateral spacing 1.4m→1.8m for breathing room.

Both shipped: `f4ac7c2` → PR #863 (merged, live, `sw.js` v794). Branch discipline held: fresh
worktree (`/tmp/wt-car-envmap-fix`) off current `main` each time, never reused a
squash-merged branch — this session alone hit that collision 3 times (branches from PRs #854,
#857, #858 were all stale the moment their PR merged; don't `git pull` an old worktree and expect
it to fast-forward, just cut a fresh one).

**Found, NOT investigated (documented for the next session, not guessed at):**
- **User: "Hospital has zero trees - only ppl, car."** Confirmed via direct DB query: Hospital
  DOES have 20 real trees (`M_RPC Tree - Deciduous:...`, `IfcBuildingElementProxy`, same M_-prefix
  convention as BimWhale) — `realTrees` detection already counts them correctly (witness log
  shows `realTrees=20`, synthetic tree placement correctly skipped). Checked their Z
  (≈179.5) against the building's overall Z range (159.8–203.2) — well within normal range, no
  obvious "floating in the sky" position bug. So this is NOT a detection/counting bug and NOT an
  obvious gross-position bug — the real trees exist in the data with sane coordinates but the user
  doesn't see them rendered. Needs live visual verification (are they occluded by the dense MEP
  geometry? too small at typical viewing distance? a streaming/classing issue specific to this
  building's tree instances specifically, distinct from the M_RPC fix which only touched
  material/counting, not base geometry streaming?) — not diagnosed this session, don't assume the
  cause without checking.
- **User: "as i still want the Alt-S to do facade mostly and not all round the building this is to
  build user facing view for photo finish."** References the existing facade-facing-lights system
  (`§PHOTO_FACING facades=4 strengths=0.54,0.30,0.30,0.30` — `_updateFacadeFacingLights`,
  `PHOTO_FACADE_DIM_FRACTION=0.3` in `effects.js`): right now every facade gets SOME light (dimmed
  to 30%, "not pitch dark" by design), not a hard cutoff to the camera-facing side only. User wants
  the presentation pass to read as clearly ONE hero facade (the camera-facing "user view" for the
  photo-finish shot), de-emphasizing the other 3 sides more than the current 30%-floor does. Not
  scoped or implemented this session — needs its own investigation of `_updateFacadeFacingLights`
  and probably a lower `PHOTO_FACADE_DIM_FRACTION` or a genuine facing-based gate rather than a
  floor. Flagging so it isn't lost, not attempting blind.
- **User questions on staffage persistence — RESOLVED 2026-07-18** (verbatim ask): "Existing in
  data means when saved and reopen it be there? And Alt-P can remove them? So that Alt-P can apply
  again?" Plus: "reopen the scene returns to what last saved scene was." Traced the actual code
  (not guessed): `A.saveModelDb()` (`scene.js:514`) calls `A._exportBuildingDb()`, which exports
  only SQLite tables (`element_transforms`/`elements_meta`/geo tables, `§SAVE_FOLD` log) — staffage
  sprites + the car mesh are pure runtime `THREE.js` scene-graph objects (`_photoStaffage.add(...)`,
  `effects.js:717`), never written to `A.db`. Reopen (`A._openDbBytes` → `location.assign(
  'viewer.html?db=...')`, `scene.js:552`) is a full page navigation — the viewer reinitializes from
  scratch, so `_populateOn` (`effects.js:1037`) resets to `false`.
  **Answer: staffage does NOT persist across save→reopen.** After reopening a saved `.db`, Alt+P is
  OFF by default; pressing it places a FRESH set (not a restore of what was showing before save) —
  nothing about the prior placement was recorded. Within one session, Alt+P's own add/remove/
  re-apply toggle (`_disposeStaffage`) is correct and unaffected by this. No code change made —
  this was a read-only trace to answer the question; if the user wants Alt+P's on/off state (or the
  actual placed set) to survive save/reopen, that's a new feature (e.g. a flag/seed written into the
  exported DB) — not scoped or requested yet.
