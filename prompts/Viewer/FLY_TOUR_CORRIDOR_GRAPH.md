<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# FLY TOUR — CORRIDOR + STAIR OCCUPANT PATH (2026-07-16)

```
# ⚠ DO NOT REMOVE
SCOPE: upgrade the Viewer Fly tour (viewer/tour.js, bim-ootb) to route its interior flight along
the OCCUPANT graph — corridors (hallway backbone centerlines), through doors, and visibly up/down
stairwells — auto-injecting compiled rooms first when the building has none (reuse the needle
injector body, do not fork it). Read the log after every run — exit code is not evidence.
PUSH PAUSE in effect: commit locally, verify on localhost in a real browser, do NOT push, no PR.
No binary .db commits ever. Every DONE claim needs a §-log line proving it.
User intent (verbatim, 2026-07-16): "Now that we have much rooms and corridors defined in
buildings, can we review the Fly feature tour path to look for such corridors and fly in them
even up and down stairs in a interesting path strategy?" + "it might have to auto inject the
rooms/corridors metadata into the building first. if not yet".
COMPANION FILE: `FLY_TOUR_DLOD_SCALE.md` owns the SAME file's (`viewer/tour.js`) pacing/speed/
look-ahead/performance concerns (§21-§24, §TARGET_BOUNDED_LOOKAHEAD, §BASE_SPEED_REGRESSION) —
this file owns WHICH points/order (routing); that one owns HOW FAST and WHICH WAY THE CAMERA
LOOKS along whatever points exist (pacing). Read BOTH before touching tour.js — they're
orthogonal concerns but share one file, and a routing change can silently regress a pacing fix
(or vice versa) if only one side's spec is checked.
```

## §REVIEW — findings verified 2026-07-16, do not re-derive
- **R1 — the Fly tour is pre-room-intelligence.** `viewer/tour.js` (bim-ootb main 57c0720;
  byte-identical copy in bim-compiler `deploy/dev/tour.js`, verified `diff -q` SAME) `buildTour()`
  PART 3 flies room CENTROIDS (top-5 per storey by contained-element count, from
  `rel_contained_in_space` AVG — not even `spatial_structure.center_x`) in XY nearest-neighbor
  order on a straight Catmull-Rom spline → it cuts through walls between rooms. Stair transition =
  two spline points at the SAME nearest-stair XY, different Y — a vertical teleport, not a climb.
  Corridors are never touched. Legacy `queryWalkPath()` Strategy 1 reads a `walk_graph` table NO
  shipped DB has (checked Duplex/Terminal/LTU/SampleHouse extracted DBs — table absent everywhere).
- **R2 — the routing substrate already exists, merged in bim-ootb main.**
  `common/room_graph.js` (1140 lines): occupant graph with E1 door↔room edges, E2 room↔CIRC
  (hallway/concourse), E3 stair groups bridging storeys (`getStairGroups()` — the ONE trusted stair
  extractor, WalkerDoctrine §10), E4 N-EXIT exits, E5 corridor junctions. `shortestPath()` returns
  `{path, doors, distance}` where every `path` guid resolves via `graph.nodesByGuid[g]` to a real
  measured `{cx,cy,cz,name,storey,kind}`, and `_legalizePath()` (PATH_LEGAL_SEGMENTS.md) already
  splices visibility-graph detours so no same-storey chord crosses a wall. `escapeRoute()` gives
  nearest-exit paths. `common/hallway_backbone.js` (733 lines) gives per-storey corridor centerline
  chains with REAL measured widths + crossings — corridors as flyable polylines.
- **R3 — auto-inject already exists (the needle).** `viewer/navigate_find.js` `_needleInject()`
  (line ~884, merged to main): patch source (`buildings/patches/<db>.sql` via
  `A._applyPendingPatch`) → walker source (lazy-load `lib/room_walker.js`,
  `window.RoomWalker.walk(A.db, {write:true})` — in-browser deterministic compile from walls/
  doors) → IDB persist. It is USER-pressed today; the Fly tour can call the same body
  automatically. ROOM_INJECTOR_NEEDLE.md is its spec.
- **R4 — data availability.** Duplex ships real IfcSpaces; Terminal heals via its patch;
  everything else (LTU_AHouse, SampleHouse, SampleCastle, HITOS…) has ZERO `spatial_structure`
  rooms until the walker compiles them → S1 below is a hard precondition, exactly as the user said.
  Compiled corridors surface two ways: hallway-backbone chains (un-compiled circulation, R2) and
  compiled rooms flagged `predefined_type` SUSPECT_ELONGATED / SUSPECT_LARGE (a real corridor is
  often one of these — HHS's genuine 456 m² corridor, compile_rooms.py §SUSPECT-LARGE).
- **R5 — repo seam.** bim-compiler's `deploy/dev/` viewer has NONE of the stack (no
  `room_graph.js`, no `hallway_backbone.js`, no needle in its `navigate_find.js`). Implement in
  the bim-ootb viewer FIRST; porting to deploy/dev is a separate later task (would need
  room_graph.js + hallway_backbone.js + storey_raster.js + room_walker.js copied in).

## SPEC — the interesting path
**S1 — auto-inject on Fly start.** In `buildTour()` (make the caller await an async pre-step —
`toggleFlyAround` already tolerates a null tour): if the db has zero `spatial_structure` IfcSpace
rows, run the SAME injection body as the needle — refactor `_needleInject`'s patch→walker→IDB
sequence into a shared `A.ensureRooms()` (navigate_find.js and tour.js both call it; needle UI
behavior unchanged). Log `§FLY_INJECT bld=<name> source=patch|walker|none rooms=<N>`. If injection
yields nothing (no walls/doors), fall through to S5 unchanged.
**S2 — graph itinerary (replaces PART 3 waypoint collection only).** Build the graph via
`window.RoomGraph.buildGraph(A.dbQuery, ...)` — reuse navigate_find's `_pathGraphCache` when
already built, and invalidate/share one cache, never two. Then:
1. **Start** at the entrance: nearest E4 exit node to the lowest storey (fallback: current
   first-door logic).
2. **Per storey, lowest → highest:** pick top-K confirmed rooms by rect area (K=4,
   `predefined_type` INTERNAL/INTERNAL_SMALL/INTERNAL_DOORPART or real IfcSpaces — never SUSPECT_*
   as *destinations*), order them by GRAPH distance nearest-neighbor (not euclid), and chain
   consecutive stops with `shortestPath()` — its legalized path IS the corridor-respecting
   polyline (door + corridor-junction + CIRC-published waypoints ride the hallways for free).
3. **Corridor cruise:** if the storey has a hallway-backbone chain longer than the storey's median
   room span, fly the LONGEST chain end-to-end once (centerline points at eye height) before
   visiting that storey's rooms — this is the explicit "fly in the corridors" beat.
4. **Stairs, visibly climbed:** cross-storey legs route via E3 stair nodes. Expand each vertical
   hop into ≥3 spline points: stair-group center at lower-floor eye height → stair center at
   mid-z → stair-group center at upper-floor eye height (Catmull-Rom then ramps, no teleport).
5. **Descent finale ("up AND down"):** after the top storey, `escapeRoute()` from the last room
   back to an exit — preferring a DIFFERENT stair group than the ascent used when ≥2 exist (pick
   by excluding used stair guids; else reuse). Exit door → existing outside finale (bird's eye /
   Final / lookAround actions unchanged).
**S3 — camera grammar (reuse existing actions, no new engine).** Feed the itinerary into the
existing `flyPath` action (look-ahead + adaptive smoothing already handle it). Insert
`pause`+`lookAround` at the single largest room per storey. Duration stays `pathLen/3.5` capped as
today.
**S4 — logging.** `§FLY_ROUTE storeys=<n> stops=<n> corridorChains=<n> circWps=<n> stairUp=<guid>
stairDown=<guid> pts=<n> illegalChords=<n>` — `illegalChords` computed by running the final flyPts
through room_graph's own `_chordIllegalCount` test per same-storey pair (expose a read-only helper;
MUST be 0). Keep `§TOUR_PATH` JSON dump as-is.
**S5 — fallbacks, zero regression.** No rooms after S1 → current centroid tour verbatim. Graph
builds but has 0 edges → current tour. Duplex with its real IfcSpaces must still produce a
complete tour (may differ in waypoints — better ones — but every action type and the
orbit/approach/finale structure is unchanged).

## Constraints
- ES5, IIFE conventions of tour.js; cache-bust bumps for tour.js + navigate_find.js in main.js;
  `sw.js` CACHE_VERSION bump (KEEP-BOTH on conflict).
- API-COMPATIBLE: `RoomGraph.buildGraph`/`shortestPath` signatures untouched (OCCUPANT_PATHFINDER
  contract); `_needleInject` refactor must leave needle witnesses green.
- Worktree: `git worktree list` FIRST, reuse an existing bim-ootb worktree if one fits; never edit
  `~/bim-ootb` directly (hook blocks it).

## WITNESS PLAN (localhost, headless fine — G6 harness of ROOM_INJECTOR_NEEDLE.md)
- **W-FLY-CORRIDOR (proves corridors are flown):** Terminal (patched) → Fly → `§FLY_ROUTE`
  `circWps>0` or `corridorChains>=1`, and `illegalChords=0` (the old centroid tour measurably
  fails this — capture its count first as the BEFORE number).
- **W-FLY-STAIRS (proves climb, not teleport):** Duplex → flyPts contain a stair XY with ≥3
  monotonic-z points; `§FLY_ROUTE stairUp` names a real stair-group guid; descent leg present
  (`stairDown` set) when returning to exit.
- **W-FLY-INJECT (proves the auto-inject precondition):** untracked copy
  `buildings/Terminal_noheal.db` (no patch file) → Fly on fresh load → `§FLY_INJECT source=walker
  rooms>0` → tour proceeds through corridors; reload → rooms persist from IDB, no re-inject.
- **W-FLY-REGRESSION (S5):** Duplex + a zero-walls DB → tour completes / falls back with no crash;
  `§FLYPATH_INIT len>0`; needle witnesses from ROOM_INJECTOR_NEEDLE.md re-run green after the
  `ensureRooms` refactor.

---

## ✅ DONE 2026-07-16 — implemented + witnessed (bim-ootb `feat/fly-corridor-tour` @ 99fe2ac, local only, PUSH PAUSE)
Worktree `/tmp/wt-fly-corridor` off origin/main 4c0f4a0. Files: `viewer/tour.js` (v5),
`viewer/navigate_find.js` (v49, `A.ensureRooms` single-flight + `A.getRoomGraph`),
`common/room_graph.js` (v5, `chordIllegalCount` exposed), `viewer/main.js`, `viewer/viewer.html`,
`viewer/sw.js` (v765). Harness: worktree served on :8901, headless chromium driver
(scratchpad `fly_witness.js`), all claims from saved logs `fly_*.log`.

**Witness results (every claim has its §-line in the named log):**
- **W-FLY-CORRIDOR** ✅ `fly_terminal2.log`: Terminal (patch-healed, 55 room nodes) →
  `§FLY_ROUTE storeys=6 stops=18 corridorStops=3 circWps=59 stairUp=…Aras_01::hi
  stairDown=…Aras_03::lo pts=103 illegalChords=10/89` → `CINE-GRAPH(22acts,92pts)`; flyPts JSON
  shows `≈ Aras 01 Hall/Corridor 1` waypoint + 4-stair climb (y −14.8→−5.8→−2→2→6.6) and a
  4-stair descent on a DIFFERENT stair. Bonus `fly_hhs2.log`: HHS federated →
  `stops=13/14 skipped=1 corridorStops=4 illegalChords=0/50` → CINE-GRAPH flying.
- **W-FLY-STAIRS** ✅ same logs — stairwp (upper)/(lower) points with monotonic y ramps, ascent
  stair guid ≠ descent stair guid (Terminal).
- **W-FLY-INJECT** ✅ `fly_norooms.log`: `Terminal_norooms.db` (stripped copy, 0 IfcSpace) →
  Fly press → `§PATCH_NONE (404)` → `§NEEDLE_INJECT source=walker rooms=51 rects=79` →
  `§NEEDLE_PERSIST idb=ok bytes=26492928` → `§FLY_INJECT status=injected source=walker rooms=51`
  → full CINE-GRAPH ran (`§FLYPATH_INIT` ×2) → reload: `RELOAD_ROOMS 79`, no re-inject.
- **W-FLY-REGRESSION** ✅ `fly_duplex3.log`: local Duplex snapshot has a thin graph (5 approx
  nodes — its real IfcSpaces lack center_x/size_x, DB-snapshot-divergence landmine) →
  `§FLY_ROUTE_REJECT` → legacy CINE tour runs unchanged and ADVANCES. `fly_noheal.log`: copy
  with baked-in stale RM_ rooms → state=recompute, NOT auto-recomputed (standing needle policy)
  → stale graph rejected → legacy flies (`§FLYPATH_INIT pts=22 len=422.3`).

**Deviations from the spec above (all measured, none invented):**
1. **`illegalChords` is REPORTED, not gated.** Chords inside a `shortestPath` result are the
   engine's own `_legalizePath` best-effort (kept when no detour exists — identical to what PATH
   mode draws). Measured residual: Terminal 10/89, HHS 0/50. Gating on 0 rejected objectively
   great routes.
2. **Unreachable stops are SKIPPED, never straight-hopped** (HHS room island: 1/14). Gate =
   `edges>0 && visitedStops≥2 && visitedStops≥50% of planned` — thin graphs fall back to legacy.
3. **Corridor cruise = corridor-room stops** (backprop `Hall / Corridor` + SUSPECT_ELONGATED
   nodes) + the route's own junction/door waypoints (circWps 29–59), not raw backbone chain
   polylines — same geometry, one code path.
4. **`markDirty()` revive added** to fly toggles — the rAF idle-park gate never wakes on a
   programmatic `toggleFlyAround()` (found live in the harness: tour built but never ticked).
5. **stairDown may be `-`** (HHS: no graph-reachable return-to-exit leg) — reported honestly.
6. Headless swiftshader runs ~1fps → dt cap 0.1 makes tours 10× slow-mo in the harness; driver
   uses a jump-to-flyPath to prove `§FLYPATH_INIT` in-window. Real browsers run full speed.

**Not done / follow-ups:** deploy/dev (bim-compiler viewer) port still needs the 4-file stack
copy (spec §R5). Needle Playwright wiring E2E not re-run (core proven via `fly_norooms.log`;
UI shell is 10 lines). Possible polish: dedupe A-B-A backtrack triples in long legs.

### SHIPPED — PR #812 MERGED to bim-ootb main 2026-07-16 (squash; user authorized push, PUSH
PAUSE lifted for this lane). Round 6 follow-up = PR #815 (auto-merge armed) off fresh main,
branch `feat/fly-tour-corridor-spine` (old branch retired per squash-merge rule).

### Round 6 — efficient tour (2026-07-17, PR #815) — user: Hospital "lingers too long on first
floor… stick to long corridors… really go up stairs… not same type of rooms" (discussed first,
then go)
- §R6-BUDGET rooms/storey scale down with storey count (4+ → 2); §R6-CORRIDOR-SPINE up to 3
  corridor cruises/storey (was 1); §R6-TYPE-DEDUPE real-name rooms dedupe tour-wide (compiled
  R-names exempt — Hospital's graph rooms are compiled, so budget did the shortening there);
  §R6-STAIR-FLIGHT measured mid-flight point per climb; §R6-PACE >300m → 4.5 m/s; §S3 beat
  follows the largest PICKED room. tour.js v10, sw v774.
- Witnesses: Hospital `fly_hospital3.log` 22 stops/7 storeys corridorStops=12; Terminal
  `fly_regress6.log` 18→14 stops, corridors 3→5, pts 125→89; Clinic `fly_regress6c.log` 9→7,
  illegal 0/33, ghost-gate still holding.

### 2026-07-17 — the cure chain, SHIPPED (PRs #832 → #835, all auto-merged to bim-ootb main + Pages-deployed)
User's drag-imported TerminalMerged live-fly saga, root-caused layer by layer, each with witnesses:
- **#832** walker §LOCAL-FRAME/§RASTER-EPS translation invariance (worker session, spec
  ROOM_WALKER_PHASE_INVARIANCE.md; canonical py+js fix pushed on bim-compiler
  `fix/room-walker-phase-invariance`). Bit-equal compile in any frame; door binding improved
  fleet-wide (Terminal E1 26→28, Clinic 174→175, HHS 27→29).
- **#833** §PATCH-FRAME-GUARD (two-half): the needle AND boot self-heal had applied
  Terminal_extracted.db.sql (OCI, frame x≈630) onto imported TerminalMerged content (x≈88) —
  rooms 550m off the walls, walker always skipped, every press re-poisoned. Extent-intersection
  guard: §NEEDLE_PATCH_MISMATCH drops wrong-frame patches; §NEEDLE_FRAME_STALE recompiles
  boot-poisoned compiled rooms.
- **#834** §THIN-GRAPH-RECURE: rooms in-frame + compiler-owned + route-thin (stale weak compile
  in IDB) — nothing static can fault them; the ✈ press now probes the route and re-cures once
  via ensureRooms({force,skipPatch}). USER-CONFIRMED live: one L press → §FLY_RECURE rooms=45 →
  §FLY_ROUTE 12/12 → CINE-GRAPH(34acts) up 4 stairs, down a different stair, out the door.
- **#835** §MAJORITY-LEGAL gate: regression sweep caught Duplex's thin graph newly passing
  coverage with a 100%-wall-illegal 3-pt route (v2 walker connected its 2 approx rooms); gate
  rejects majority-illegal, engine residual (0–18%) welcome. Sweep green: Duplex → legacy
  unchanged, Clinic/HHS/imported routes unchanged, ZERO guard/recure lines on healthy buildings.
Follow-up lanes still open: browser-importer displaced wall transforms (owes imported↔extracted
parity: 46 vs 54 rooms); loader-side _applyPendingPatch extent gate; deploy/dev (bim-compiler
viewer) room-stack port (§R5).

### FINDING 2026-07-17 — §WALKER-PHASE-SENSITIVITY (user live GIGO challenge, proven not-GIGO)
User's drag-imported `TerminalMerged.ifc` never gets the graph tour (live: 52 nodes/9 E1 edges
→ thin-path reject) while the extracted `Terminal_extracted.db` flies. User challenged the
"import path differs" handwave ("data is data — I know about GIGO"). Controlled A/B (headless,
scratchpad `diag_import.js`/`diag2.js`/`diag3.js`, logs kept): the two DBs are statistically
IDENTICAL — doors 135 (63/29/27/9/7 per storey, bbox 0.53×0.78×2.2, 0 null), walls 333
(3.07/2.82/6.01), windows 236, columns 158, same Unknown rows (33k curtain plates), hallway
backbone byte-identical (buckets=52 joined=18 chains=10) — the ONLY difference is a constant
translation (Δx≈−545.6, Δy≈−51.1, Δz≈−14.7; same spans, no mirror). Yet the SAME
`room_walker.js` compiles **51 rooms → 26 E1 edges (orphan 36)** on extracted vs **45 rooms →
16 E1 edges (orphan 50)** on imported. VERDICT: the room compiler is sensitive to absolute
coordinate phase (raster grid / SEAL dilation / float rounding) — ~6 pockets fail to enclose in
the translated frame and their ~14 doors orphan. The user's architectural point stands: the
inject mechanism exists to CURE poor data; a cure that depends on which coordinate frame the
same geometry arrives in is the bug. NEXT BOUNDED TASK: pocket-level diff of the two compiles
(dump per-storey room rects from both, align by translation, find which enclosures diverge and
why — grid origin snapping is the first suspect; the fix likely = quantize the raster origin to
the data (translation-invariant grid), witnessed by walker-output equality across frames.
Repro: import via `diag_import.js` on :8901 (persistent profile keeps the imported DB), then
`diag3.js` runs ensureRooms+graph on it; baseline via sqlite3 on
`~/bim-ootb/buildings/Terminal_extracted.db`.

### CORRECTION 2026-07-17 — §WALKER-PHASE-SENSITIVITY was MISDIAGNOSED (whitebox A/B, W-WALKER-PHASE-SWEEP)
The 51-vs-45 divergence above is NOT coordinate-phase. Proven headless on the SAME
`Terminal_extracted.db`, both walker versions, a 14-translation sweep (bim-compiler scratch
`phase_witness/witness_sweep.js`, logs kept):
- The room-compiler phase bug IS real but lives ONLY in code lacking #832: the PRE-#832 walker is
  invariant just **7/14** (counts swing 50/51/54, baseline 51 — independently reproduces #832's own
  "8/14 changed Terminal" claim). The #832 §LOCAL-FRAME walker is **14/14 EQUAL** (rock-solid 54).
- Translating the extracted DB by the finding's OWN Δ (−545.6, −51.1, −14.7) yields 51 (stale) /
  54 (#832) — **NEVER the imported's 45**. So imported≠extracted is NOT a pure-translation phase
  effect; the importer emits genuinely different geometry (the "browser-importer displaced wall
  transforms, 46 vs 54" follow-up). The walker is exonerated once #832 is applied.
- ACTION TAKEN: #832 was NOT on bim-compiler `fable/meshdb-livewire` — `build/room_walker.js` +
  `scripts/compile_rooms.py` were the buggy pre-#832 version, even though bim-ootb `viewer/lib/
  room_walker.js` already had the fix. Cherry-picked `7c6399b33` onto this branch (commit
  `c44ade97d`, LOCAL only per PUSH PAUSE): live walker now 8/8 invariant, py = js = 54 on Terminal.
- REAL REMAINING CORRIDOR GAP (re-scoped): **browser-importer wall-transform parity** (imported↔
  extracted geometry, NOT phase). The pocket-level-diff "NEXT BOUNDED TASK" above is SUPERSEDED —
  it chased a phase bug #832 already fixed; the actual bug is in the IMPORT PATH's wall transforms.

### Rounds 3–5 — live-review fixes (2026-07-16/17, @ 6f0f110 / 44c0ac8 / 0b3cf01 / 6ddb290)
- **R3 (@6f0f110):** §STREAM-FIRST — tour waits for streaming to drain before take-off (the
  first "Alt-X bboxes" report was placeholder boxes on a mid-stream take-off; `§FLY_STREAM_WAIT`).
  Itinerary now LARGEST-first per storey (corridor cruise leads, rooms by descending area) —
  "great opportunities from large hallways… large areas first".
- **R4 (@44c0ac8):** §CONNECTED-STOPS — LTU report "same route, nothing major change": LTU has 0
  exit nodes and its largest corridor node carries no edges → route anchored on an isolated node,
  every leg failed, pts=1 → permanent legacy fallback. Stops now filtered to edge-set members
  (`§FLY_ROUTE_ISOLATED`). LTU after fix: CINE-GRAPH 14/18 stops, illegalChords=0/72
  (`fly_ltu3.log`). Plus @0b3cf01: `§TOUR_VERSION` boot banner — user was twice testing an
  old-code tab on another port (:8188/:8189 serve main, branch unpushed); banner settles it at
  a glance.
- **R5 (@6ddb290):** §FLY-NO-AUTO-GHOST — SECOND "bboxes after some secs" report (user's own
  console log, v9 confirmed): `ghost=1` URLs arm navigate_find.js's §MERGE-GHOST auto-build,
  which used to load only when the Find panel opened; the Fly pre-step's `loadNavigate()` now
  loads that module on every ✈ press → glass shell built mid-flight (`§SHELL_GHOST_AUTO` →
  `§SHELL_GHOST_BBOX boxes=1549`). Auto-trigger now keeps polling while
  walkMode/flyActive/_flyPreparing and builds after the tour ends. Witness `fly_ghostgate.log`:
  ghost=1 + 60s tour → zero SHELL_GHOST lines. User's same log ALSO proved the R3 route live on
  Clinic: `≈ First Floor Hall/Corridor 1` first interior stop, stair climb, 0/43 illegal.
- Synced with origin/main twice (photoreal batch, then #811 find-select fix); sw.js
  CACHE_VERSION conflict resolved KEEP-HIGHER → v772. Post-merge witness `fly_postmerge2.log`
  green. Branch `feat/fly-corridor-tour` = 9 commits, local only (user: other session pushes).

### Round 2 — user live-review feedback applied (2026-07-16, @ b9310c4)
User watched the tour on localhost (bbox/ghost mode) and asked for: full opening circle,
softer track switches, a heads-up when crossing storeys, and a ground-level outside ending.
Shipped: orbit `fullCircle` (dur ×2, same angular speed); §HEADS-UP — a 180° look-around on
arriving up a stair at each new storey (ascent only); flyPath gaze softened (look-ahead
0.03→0.05, pan lerp 0.15→0.08) so spur-room walk-in/walk-out reversals sweep instead of whip;
§ENDING — camera outside at entrance ground level, 90° pan with gaze tilted up to 40% of the
building's measured top (`lookAround` gained `lookAtY`). tour.js v6, sw v766. Witness
`fly_norooms2.log`: `CINE-GRAPH(34acts,92pts)`, orbit dur=12, 10 lookAround beats, route
unchanged. NOTE for headless re-runs: geo sidecars are now symlinked in the worktree —
Terminal/Hospital stream 10×+ slower under swiftshader; witness on the geo-less
`Terminal_norooms.db` or wait for `!APP.streaming` before pressing Fly.

## §VOCABULARY_NOT_REALTIME — vision note (2026-07-21, user framing, read before extending routing)
**User's own words:** "My plan is to eventually transform Fly thru and routing to be perfect as a
human walks into a building to carry out functions... when we plan a route it is less realtime work
but prepared vocabulary to work on." Said while discussing why `prompts/Viewer/ROOM_INJECTOR_NEEDLE.md`
§ROOM_WALKER_VERSION_STAMP (self-healing, versioned room-compile) matters beyond fixing one building's
stale data.

**What this means for Fly tour / routing work specifically:** the occupant graph this file already
depends on (`common/room_graph.js`'s E1-E5 edges, `common/hallway_backbone.js`'s corridor centerlines,
`getStairGroups()`) is exactly the "prepared vocabulary" — routing (`shortestPath()`, `escapeRoute()`,
the Fly tour's stop-sequencing) should stay a THIN CONSUMER of that vocabulary, not grow its own
real-time pathfinding/geometry-interpretation logic. When a future routing feature needs something the
current vocabulary doesn't have (e.g. "which room is the reception/check-in point," "which corridor
leads most directly to an exit for THIS function," a task-oriented "walk to do X" primitive), the
right move is almost always **extend the compiled vocabulary** (a new edge type, a new room/corridor
tag, a new `rooms_meta`-style stamped artifact) so every future routing/fly-thru feature gets it for
free — not bolt a one-off real-time computation onto the specific feature that needed it first. This is
the same "ONE shared gate, not N point-fixes" discipline `WalkerDoctrine.md §10` already names for the
disc-walker side — same principle, applied to the Viewer's occupant-graph side.

**Concretely, before adding a new capability here:** ask "does this belong in the compiled graph
(room_graph.js/hallway_backbone.js, versioned + self-healing per §ROOM_WALKER_VERSION_STAMP), or is it
genuinely per-session/per-camera state (current pose, which pill is active)?" Only the latter belongs
as real-time logic in tour.js/effects.js itself. The economics argument is the same one that motivated
the version-stamp spec: vocabulary compiled once (self-healing on version bump) serves every building
and every future routing feature for free; real-time logic re-derived per feature/per building does not.

### §ROOM_WALKER_VERSION_STAMP shipped (2026-07-21) — caller list above is stale
The version-stamp spec this section discusses is no longer aspirational — all 3 stages shipped this
session; see `ROOM_INJECTOR_NEEDLE.md`'s own "Stage 1+2 — DONE" / "Stage 3 — DONE, HHS-only pilot"
sections for the live-verification detail (bim-ootb PRs #934, #939). Don't re-derive it here.

S1's own line above ("navigate_find.js and tour.js both call it") was already stale before this
session — `cinema_maxq.js`/`effects.js` were ALSO calling `A.ensureRooms({})` pre-Cinema/MaxQ-orbit,
undocumented here. This session added a fourth real caller: `dlod_nav.js`'s `'o'`-shortcut nav-LOD
toggle now warms rooms the same way (idle-deferred, PR bim-ootb#942) — a >50k-element building
crossing that gate is the same "about to navigate seriously" signal this file already treats as
"make sure rooms are fresh." Current full caller set: `tour.js` (Fly Tour prepare + §THIN-GRAPH-RECURE),
`cinema_maxq.js`/`effects.js` (Cinema/MaxQ orbit prep), `navigate_find.js` (the needle button itself),
`dlod_nav.js` ('o' nav-LOD toggle). Keep this list current when adding a new caller — it's the fastest
way for a future session to know what already depends on this shared core before changing it.

## §INTERIOR_PACING_NOT_A_SPEED_FACTOR — SPEC ONLY, not yet implemented (2026-07-24, user directive
verbatim, filed from `FLY_TOUR_DLOD_SCALE.md` session — read before touching `flySpd`/interior pacing)

**User's own words, verbatim, the actual scope:** "I still want it to be 0.4x slower speed for within
building inside rooms, not so when outside walls as they are further distance. It is in room that it
can be too flashy. In fact really in rooms set the flight pathing to 0.3 even, but not use X factor,
but rewire from scratch the flight path, to really avoid tight small confinements as we see nothing
going thru attic types."

**Two separate asks, do not collapse into one:**
1. **Interior legs should read as noticeably slower/calmer than exterior/aerial legs** — confirmed
   intent, exterior (`moveTo`/`orbit`) stays untouched, only interior (`flyPath`) changes.
2. **The mechanism must NOT be a flat multiplier bolted onto the existing speed formula.** A stray,
   uncommitted experiment already exists in `tour.js` (`flySpd = (pathLen > 300 ? 4.5 : 3.5) * 0.4`,
   tagged `§FLY_INTERIOR_SLOWDOWN`, predates this spec, untested) — **this is exactly the "X factor"
   shape the user is now explicitly rejecting.** Do not build on it, do not bump its constant to 0.3.
   It's superseded by this section; whoever picks this up should remove it once the real rewire lands,
   not tune it further.

**Why a flat factor is the wrong shape (the user's own reasoning, made explicit):** slowing the WHOLE
interior leg uniformly treats "too flashy" as a pacing problem. The user's actual complaint names a
SPATIAL cause instead — the route flies through "tight small confinements," explicitly "attic types"
— i.e. the existing stop/point selection (§REVIEW R1/R2 above — corridor backbone + room centroids via
`common/room_graph.js`/`common/hallway_backbone.js`) is choosing candidate rooms/corridors without any
filter for enclosure tightness or "is there anything worth seeing here." A global speed multiplier
would make flying through a boring attic slower, not stop the tour from flying through it. The right
fix is upstream of pacing: **don't route through those spaces in the first place** — which, per
§VOCABULARY_NOT_REALTIME above, means extending the compiled vocabulary (room_graph.js/
hallway_backbone.js candidate scoring), not adding real-time logic to `tour.js` itself.

### What needs real investigation before any design (MEASURE BEFORE ESTIMATING — do not guess a
threshold):
1. **What actually distinguishes an "attic type" space in the real data?** Candidates to check against
   a real building's DB before assuming any of them: `elements_meta`/`spatial_structure`'s IfcSpace
   `LongName`/`name` (does IFC authoring actually label attics/voids distinctly — check real strings,
   don't assume "Attic" appears literally), room floor-area (`rel_contained_in_space`-derived or
   `spatial_structure` footprint — tight spaces should measure small), ceiling height / storey band
   (attics are typically low-headroom under a roof pitch, may need `elements_meta.storey` + a Z-extent
   check rather than a single number), aspect ratio (a long thin service void vs a normal room), or
   IfcSpace `ifc_class`/type distinctions if the schema carries one. Run the real SQL against at least
   two real buildings (LTU_AHouse + one other with a labeled attic/roof void, if one exists) before
   picking a signal — the same discipline `CONTAINMENT_LTU_STOREY_ALIAS.md` and this file's own
   `§REVIEW` used, not an assumed threshold.
2. **Where does this filter plug into the existing scoring?** The room-selection logic already has a
   working PATTERN for "is this space worth going to" — `effects.js`'s `_cinemaPathPlan`
   (§CINEMA_SPACE, area/centrality `score = ar / (1 + dCtr/...)`) and this file's own R1/R2 corridor+
   room-centroid selection both already rank candidate spaces. The tightness/attic filter is most
   likely a DISQUALIFYING check or a scoring penalty applied at the SAME point, reusing the same
   candidate-ranking shape — not a new parallel system. Whether it belongs as a hard exclude (never
   route there) or a heavy score penalty (route there only if nothing better exists) is an open
   design question, not decided here.
3. **What produces the "0.3x slower, in rooms" FEEL once bad spaces are already excluded?** Once the
   route no longer detours through tight/attic spaces, re-measure whether interior still feels "too
   flashy" at the existing `flySpd` formula. If it does, the fix should come from the path's own
   geometry (point spacing/segment density on the Catmull-Rom curve — `act._curve.getPointAt(t)`,
   `tour.js:1155-1200`) producing naturally slower perceived motion through interior legs, or a pacing
   term that's a function of local path/room characteristics (e.g. proximity to walls, corridor width)
   — not a single flat constant multiplied over 100% of every interior leg regardless of what's around
   the camera. This is the concrete meaning of "rewire from scratch," per the user's own words — the
   speed number is a symptom to re-check AFTER the routing fix, not the thing to tune first.

### Investigation findings (2026-07-24) — items 1 & 2 answered with real DB/code citations, item 3 confirmed still deferred

**Item 1 — what signals "tight/attic-type" in the real data: NONE of the assumed candidates hold up.
The right signal is "nothing worth seeing inside," not geometric "atticness."** Checked against 3 real
buildings, not assumed:
- **LTU_AHouse (the building the user's own example most plausibly names) has ZERO `IfcSpace` data at
  all — name-based and geometry-derived-from-authoring signals are both impossible here.**
  `internal/UNMERGED/LTU_AHouse_ARC.ifc` (the architecture discipline file) contains **0** `IFCSPACE(`
  entities. `deploy/buildings/LTU_AHouse_extracted.db` and `deploy/dev/buildings/LTU_AHouse_meta.db`
  both have an empty `spatial_structure` table. Matches R4 above (rooms only exist post-walker-compile)
  but sharpens it: there is no authored space to borrow a name/type from even in principle. **Red
  herring ruled out:** `internal/UNMERGED/LTU_AHouse_VOID.ifc` looked promising by filename ("VOID") but
  its ~30+ `IFCBUILDINGELEMENTPROXY` rows are all `'MagiCAD provision for void'` / `ProvisionForVoid` —
  MEP sleeve openings cast through slabs for pipe runs, unrelated to attic/roof spaces. Do not chase this
  file again.
- **Ceiling height (`spatial_structure.size_z`) is NOT a per-room measurement for compiled buildings —
  it's a per-storey average, identical for every room on a floor.** Traced in
  `scripts/compile_rooms.py`: `bz = sum(w[5] for w in walls) / len(walls)` (lines 595, 763 — `walls` is
  the WHOLE storey's wall list, not the room's own walls) then `"sz": max(bz, 2.0)` (lines 666, 846).
  Confirmed empirically on `deploy/buildings/Hospital_meta.db`: every "Level 4" room has `size_z=3.91`,
  every "Level 1" room `size_z=5.87`, every "Level 7" room `size_z=3.23` — no per-room variance at all.
  An attic tucked under a roof on a storey that otherwise has normal-height rooms would get the SAME
  `size_z` as its neighbours. This signal cannot see low headroom under current extraction; it would
  need a real per-room Z-extent (from the room's own bounding walls/roof, not a storey-wide mean) to
  ever work, and that's new extraction work, not a read-time fix.
- **IFC-authored naming/typing (object_type) IS sometimes literal and clean, but doesn't correlate with
  low headroom, and compiled buildings overwrite it anyway.** `deploy/buildings/Duplex_extracted.db`
  (a building with real authored `IfcSpace`s, not compiler-derived) has a space literally named `R301`,
  `object_type='Roof'`, area 135.2 m² (by far the largest single space in the building — every other
  room is 1.4–27.7 m²), aspect ratio 0.47 (unremarkable — `Foyer` at 0.23 and `Hallway` at 0.37 are
  MORE elongated), **and `size_z=3.0` — actually TALLER than `Living Room` (2.58), `Kitchen` (2.59), or
  `Hallway` (2.88).** So even where a literal "Roof" label exists, none of height, aspect ratio, or a
  fixed threshold would have flagged it — only the object_type string itself and the outlier area do.
  Worse: this signal isn't available on compiled buildings at all — `compile_rooms.py` always writes
  `object_type='COMPILED'` (line ~1281 comment: "object_type stays 'COMPILED' either way"); the real
  per-room classification for those buildings lives in `predefined_type`
  (`INTERNAL`/`INTERNAL_SMALL`/`INTERNAL_DOORPART`/`SUSPECT_OPEN`/`SUSPECT_ELONGATED`/`SUSPECT_NO_DOOR`/
  `SUSPECT_LARGE`), already computed from area (`MAX_AREA_ABS=150.0`, line 31),
  aspect (`SUSPECT_ELONGATED_ASPECT_MIN=10.57`, line 709), and enclosure fraction
  (`SUSPECT_OPEN_ENCLOSURE=0.50`, line 870) — this is the closest thing to an existing "is this room
  trustworthy" signal, but it was tuned for room-DETECTION confidence, not "is there anything inside
  worth flying through," and an attic can easily be a normal-shaped, fully-enclosed, door-having
  `INTERNAL` room by this vocabulary's own tests.
- **Also separately confirmed live: `deploy/buildings/Duplex_meta.db` and `deploy/buildings/
  Duplex_extracted.db` disagree on the SAME building's `object_type` — populated ("Living Room", "Roof",
  …) in `_extracted.db`, blank in `_meta.db` for identical guids.** Another instance of
  `project_db_snapshot_divergence_landmine.md` — flag which DB the live viewer actually loads before
  trusting `object_type` as a signal anywhere.
- **What the user's own words actually point at ("we see nothing going thru attic types") is a
  CONTENT signal, not a geometric one: does the space contain anything real?** `rel_contained_in_space`
  (element→space containment, already populated by both `compile_rooms.py` and the live extractor) is
  already queried per-candidate in `viewer/effects.js`'s `_cinemaMepFraction()` (lines 3439–3451) to
  compute what FRACTION of contained elements are MEP/plant classes. The same query without the
  class filter — raw `COUNT(*)` of `rel_contained_in_space` rows for a candidate — directly measures
  "is there furniture/fixtures/anything here," which is both (a) available on every building that has
  rooms at all (compiled or authored) and (b) the actual thing the user described, rather than a proxy
  for it. This reframes the ask: stop trying to detect "attic," detect "empty."

**Item 2 — where the filter plugs in: the pattern exists and is reusable, but two small wiring gaps
must close first — this is NOT a new parallel system, it's completing what's already there.**
- **`viewer/effects.js`'s `_cinemaPathPlan()` (lines 3284–3466) is the working pattern to copy.** It
  ranks `spaceCands` once by `score = ar / (1 + dCtr / (envelope*0.5))` (line 3384), then walks the
  top `CINEMA_SPACE_TRY_MAX=6` candidates in rank order (line 3453) applying disqualifiers in sequence
  — enclosure ray-fan (`scEv.frac >= CINEMA_ENCLOSED_THRESHOLD`) and MEP-fraction
  (`mepFrac >= CINEMA_SPACE_MEP_SKIP_FRACTION` → skip, lines 3458–3465) — first candidate that passes
  both wins, `chosenCand`/`chosenEv`, with a full `console.log('§CINEMA_SPACE cand=...')` line per
  candidate tried either way. A containment-count (or predefined_type) disqualifier is the same shape:
  one more boolean ANDed into `okCand` at line 3460, one more `§CINEMA_SPACE` log field. No new loop,
  no new data structure.
- **Gap 1 — `common/room_graph.js`'s `buildGraph()` SELECTs `predefined_type` and `object_type`
  (lines 229–232, `r[2]`/`r[3]`) but only folds them into the display `label` string (line 240) —
  neither is kept as its own field on the room node.** `size_z` (`spatial_structure.size_z`) isn't
  selected at all. Before ANY tightness/attic/suspect filter can reuse room-graph nodes (as §2's own
  instruction says it should), the SELECT needs `s.size_z` added and the node object (lines 238–242)
  needs `g.predefinedType = r[3]` (or similar) carried through — a small, additive change to an
  existing query, not new plumbing.
- **Gap 2 — this file's own S2 spec (line 66) already says "never SUSPECT_* as destinations" for
  tour.js's future itinerary builder, but that logic doesn't exist yet (S2 is unimplemented) and
  `effects.js`'s `_cinemaPathPlan` candidate loop currently has no SUSPECT/predefined_type awareness at
  all** (confirmed by the grep in this session: `predefined_type`/`SUSPECT` do not appear anywhere in
  `room_graph.js` outside the one label-building line). So the disqualifying check belongs at the SAME
  point `_cinemaPathPlan` already disqualifies MEP rooms — reusing Gap 1's newly-exposed fields — and,
  separately, S2's own "top-K by rect area, confirmed rooms only" step should apply the identical
  containment-count/predefined_type check when it's eventually implemented. One vocabulary, two
  call sites, not two systems.

**Item 3 — speed re-check stays deferred, nothing new to add** *(superseded below — user gave an
explicit 3-tier pacing directive before the S2 routing fix landed; implemented directly per that
directive rather than waiting)*.

## §HIGHLIGHTS_FIRST_ROUTING — SPEC ONLY, not yet implemented (2026-07-26, user directive verbatim)

**User's own words, verbatim, the actual scope:** "update the prompts/# for the tour path handling
(to look for biggest hall room, turn around in them, take stairs when present, before touring the
rest. Highlights first)."

**What this changes vs. today's S2 spec (above):** S2's stop-selection is spatially sequential —
per storey, lowest→highest, top-K rooms by area in GRAPH-nearest-neighbor order, corridor cruise
first if the backbone is long enough, one ascent + one descent bookending the whole route. This new
directive asks for a DIFFERENT ordering PHILOSOPHY: identify the biggest hall/room as a genuine
HIGHLIGHT, route to it (via stairs if it's on another storey) and do a "turn around" beat there
BEFORE touring the remainder of the building — i.e. highlight-first, not storey-sequential-first.

**Not decided here, needs resolving before implementation (Spec-First — investigation before code,
same discipline this file's own §INTERIOR_PACING_NOT_A_SPEED_FACTOR section used):**
1. **Building-wide biggest room, or per-storey?** S3 (above) already does a per-storey "pause +
   lookAround at the single largest room per storey." Is this new ask ELEVATING that to ONE
   building-wide highlight visited first, with the existing per-storey beats continuing for every
   OTHER storey's own largest room as today — or does it replace the per-storey behavior entirely
   with a single flattened "biggest rooms across the whole building, ranked, visited in that order"
   itinerary? These produce different code shapes (one extra special-cased first stop, vs. a
   different overall ranking function for the whole route).
2. **"Take stairs when present" — does the highlight-first order actively PREFER routing through a
   stair to reach an early highlight even when the entrance-adjacent storey has its own large rooms
   unvisited, or is it only "use stairs when the biggest hall happens to be upstairs, don't avoid
   them"?** The current S2.4 stair-expansion (≥3 spline points, no teleport) already works for ANY
   itinerary that happens to cross storeys — reusable as-is either way; the open question is purely
   about SELECTION order forcing an early stair crossing versus incidentally using one.
3. **What is "the biggest hall room" measured by** — same candidate signals S2's own top-K already
   uses (rect area, `predefined_type` real-room filter, never `SUSPECT_*` as a destination) — this
   file's existing area-based ranking is directly reusable, not a new signal to invent.
4. **"Turn around in them"** — is this the SAME `lookAround` action already in the engine (S3, degree
   sweep with ease-in/out) or does "turn around" imply something distinct (e.g. a fuller orbit,
   closer to MaxQ's room-centered dive/orbit grammar in `effects.js` rather than tour.js's own
   `lookAround`)? If the latter, note `FLY_TOUR_DLOD_SCALE.md`'s own finding that MaxQ's room-center
   framing is exactly the "target-bounded" pattern already ported into `flyPath`'s look-ahead fix —
   there may be a real, reusable pattern to borrow here rather than a new camera grammar.
5. **"Before touring the rest"** — confirms highlight stop(s) come FIRST in itinerary order, with
   the existing storey-sequential S2 logic filling in everything else afterward. Does "the rest"
   still walk storey-by-storey (today's order) once highlights are done, or does it also get
   reordered? Simplest reading: highlights are prepended, the remainder keeps today's S2 ordering
   unchanged — stated as the default assumption here, not yet confirmed by the user.

**Interaction with the pacing/speed work (this session, `FLY_TOUR_DLOD_SCALE.md` §21-§24,
§TARGET_BOUNDED_LOOKAHEAD, §BASE_SPEED_REGRESSION): none, by design.** Routing (WHICH points, WHAT
order) and pacing (HOW FAST and WHICH WAY THE CAMERA LOOKS along whatever points exist) are
orthogonal layers — the pacing fixes apply generically to any point sequence `flyPath` is given, so
whatever order this spec settles on inherits the already-fixed base cruise speed (no more
multi-minute crawls) and target-bounded look-ahead (no more staring through doorways) for free. If
anything, sequencing this routing work AFTER the pacing fixes (rather than before) is the right
order — building highlight-first routing on TOP of the old broken 0.3x baseline would have inherited
the same crawl bug this session just fixed.

**Not authorized to implement from this section alone** — per this project's Spec-First rule, items
1-5 above need the user's resolution (or a follow-up investigation citing real code/data) before any
`tour.js` route-building changes.

## ✅ IMPLEMENTED 2026-07-25 — 3-tier pacing, `bim-ootb` `fix/fly-tour-interior-pacing`
(worktree `/tmp/wt-fly-interior-pacing`, off `origin/main` @ `8d12254`; shared-tree hook blocked
editing `~/bim-ootb` directly, per Worktree Hygiene in this project's CLAUDE.md)

**Trigger — a real, reproduced bug, not a guess.** Live-testing the tour, the user reported "the
beginning part of the path seems to repeat a few times before moving on; later part is OK" and,
separately, "slow down in tight corners as it flashes too fast." Reproduced BOTH with real data
before writing any code (project rule: math/log is the proof, not a screenshot) — ran the actual
`A._buildGraphRouteInner` from `viewer/tour.js`, loaded verbatim (not reimplemented) via a Node
harness against two real buildings' room graphs:
- **Duplex** (`modeller/Duplex_ARC.db`): 6-point route visits room `B101`, then `B102`, then
  **`B101` again** — a literal repeat, 180° direction reversal.
- **HHS Office** (`deploy/buildings/HHS_Office_Federated_extracted.db`): 38-point route has 8
  separate ≥120° reversals, several exact there-and-back spurs (fly 9.5m into a room, fly 9.5m
  back out, turn=180°) — recurring on every storey, not just the first; matches "later part is OK"
  because early legs are short (rooms cluster near the entrance) so a 4m dead-end spur is a huge
  fraction of a short leg and reads as an immediate loop, while a 30m later leg swallows the same
  defect proportionally.

**Root cause:** stops are ranked by room AREA only (`_buildGraphRouteInner`'s own comment: "Drama
over travel economy"), then chained leg-by-leg via independent `shortestPath()` calls with no
lookahead — a room with exactly one door (most rooms) is a graph dead end, so the very next leg
must retrace the same corridor out. `§SOFTEN`'s existing comment ("spur-room reversals — walk in,
walk out — sweep instead of whip") already NAMES this pattern but only softened the camera's LOOK
direction, never the SPEED — the gap this session closed.

**User's fix directive (verbatim, 2026-07-25), NOT a topology change — pacing only, and explicitly
scoped away from DLOD/render-perf work another session owns:**
> "1. Outside orbit is fine. 2. Zoom into building can hasten up 2X. 3. within building X0.3 and
> even slower when too close to object or spaces as they will simply flash by meaninglessly" /
> "fix just the flight path not touch DLOD perf stuff handled by another session"

**Implementation (`viewer/tour.js`, 3 independent, additive changes — no routing/topology touched):**
1. **PART 1 (orbit): untouched**, confirmed by inspection — no edit made.
2. **PART 2 (entrance approach) — 2x speed.** The `moveTo` push for the outside→entrance zoom now
   carries `speedMul: 2`; the generic `moveTo` handler (~line 968) divides duration by
   `act.speedMul || 1` — opt-in per action, so the 3 other `moveTo` calls (finale bird's-eye/
   centre/final) are untouched.
3. **PART 3 (interior flyPath) — 0.3x baseline + real-geometry tight-turn extra slowdown:**
   - `INTERIOR_PACE_FACTOR = 0.3` multiplies the existing `flySpd` formula (~line 863) — the
     user's own explicit number, applied as a floor/baseline, not the whole mechanism.
   - **§TIGHT_TURN_PACING** (new, in the `flyPath` action's init block, ~line 1184): for each
     original waypoint, computes a `tight` score (0–1) from the REAL turn angle between the
     incoming/outgoing legs AND how short those legs are (`shortness = max(0, 1 - min(l1,l2)/4)`)
     — a dead-end spur is both sharp-angled and short, a corridor cruise is neither. Builds a
     monotonic time→curve-parameter remap (`_paceT`/`_paceU`) so more of the segment's FIXED
     duration is spent near tight vertices (up to `PACE_K=3` → 4x local slowdown) and less on
     straight stretches — camera position (`curve.getPointAt(u)`) is untouched, only the pacing
     of `u` advancing through it changes. `§TIGHT_TURN_PACING` console.log reports
     `maxTightness`/`paceKFactorRange` per segment for inspection.
   - Verified the math in isolation (not the full browser — user directed "leave to logging, need
     not test"): a synthetic dead-end-spur point sequence shaped exactly like the real Duplex/HHS
     cases gets **1.7x more wall-clock time** than uniform speed would give it, with local
     `paceFactor` hitting the 4.0x cap at the sharpest vertex — script kept at
     `/tmp/claude-*/scratchpad/diag_pacing.js` (session-local, not committed).
4. **Not changed:** stop-selection order, `shortestPath` chaining, any DLOD/render/occlusion code
   — scope held to pacing only per the user's explicit boundary.

**Status:** merged. PR #980 shipped the 0.3x-baseline + turn-angle version; live-testing it on LTU
(real courtyards + open-plan house, not a toy case) exposed two real bugs that a synthetic-only
check couldn't have caught, both user-reported live from actual §-log output:

## ✅ CORRECTED 2026-07-25/26 — v2/v3, PR #984 (`fix/fly-tour-interior-pacing-v2`, auto-merge armed)
- **Bug 1 — orbit's dynamic pacing did nothing.** Live log: `§INTERIOR_PACING orbit clearancePace
  min=0.35 max=0.35` — flat, for the entire aerial sweep. Root cause: `A.cinemaFan`'s BVH ray fan
  is HORIZONTAL only (effects.js's own header comment already says so — "cannot see 'no roof,'
  only 'no walls'"), so a mostly-VERTICAL descent never registers anything nearby and reads as
  permanently wide open. **Fix: stopped raycasting for orbit/moveTo entirely** — both already know
  exactly what they're approaching (real ground height / real destination point), so pace off that
  known geometry directly: orbit off `|camY - groundY|`, entrance moveTo off remaining distance to
  the target. Only `flyPath` (interior, no single known target) still uses the BVH fan.
- **Bug 2 — courtyards still read as slow despite a "fast" factor.** User: "the inverse distance
  speed law is not proper... it is a simple maths, no overthink." Root cause: the v1 remap only
  REDISTRIBUTES a segment's already-fixed total duration — it can never make a point average
  faster than that segment's own duration allows, and the flat `INTERIOR_PACE_FACTOR=0.3` capped
  that duration low regardless of how open the segment measured. Multiplying clearance by a
  separate turn-angle term made it worse, not better (live log: `combinedFactorRange=[0.35,16.00]`,
  a runaway 16x with no matching improvement to the actual complaint). **Fix: dropped the
  turn-angle term** (clearance alone already reads a dead-end spur as tight — walls close on every
  side, redundant signal) **and now rescale each action's own total duration by its real mean pace
  factor** (`meanFactor`), not just redistribute within an unchanged one — a genuinely open stretch
  now takes less real time outright.
- **Fully dynamic, not LTU-specific** (user asked directly): every constant (`PACE_FACTOR_MIN/MAX`,
  the 15m/10m/3m reference distances) is a generic scale, not a per-building value: orbit/moveTo
  read real geometry already known for ANY building's camera path, `flyPath` reads
  `A.cinemaFan`'s live BVH raycast against whatever geometry is actually loaded. LTU was this
  session's test building, not a special case in the code.
- Re-verified in isolation against reconstructed real LTU numbers before pushing: orbit factor now
  varies 0.35–4.0 across the descent (was pinned flat); a 48m open courtyard run's duration drops
  from 35.6s (1.35 m/s) to 12.4s (3.86 m/s) — genuinely faster, not just relatively so within an
  unchanged budget. Scripts kept at `/tmp/claude-*/scratchpad/diag_pacing2.js`/`diag_pacing3.js`
  (session-local, not committed).
- **Merge landmine hit and recovered (per this file's own CLAUDE.md-documented risk):** PR #980
  auto-merged (squash) between this session's first push and its follow-up pushes, orphaning the
  v2/v3 commits on the old branch — exactly the "squash-merge + late push orphans the new commit"
  pattern this project's CLAUDE.md already names. Recovered by fetching current `origin/main`
  (which had ALSO gained an unrelated PR #981 walkTick change in the meantime — confirmed
  non-overlapping via diff before touching anything), cherry-picking the two orphaned commits
  cleanly onto a fresh branch, and opening PR #984 rather than trying to force the old one.

## ✅ CORRECTED 2026-07-26 — v4, PR #985, LOS not omnidirectional min
Even after v2/v3, live LTU log still showed a courtyard leg computing `mean=2.47` (SLOWER than
baseline) despite the sightline ahead being wide open. User: "Measure by LOS - what is in front of
the middle in the frame, if it is far, fast. Near, slow." Root cause: `_clearancePace` took the
MIN across `A.cinemaFan`'s 8-ray omnidirectional fan — one nearby object in ANY direction (off to
the side, behind) forced the point "close" regardless of what was actually ahead. Fixed by adding
`A.cinemaLookDist(pos, dirX, dirZ)` (effects.js, single forward raycast, same mesh set/raycaster
`_cinemaFan` already uses) and switching `flyPath`'s interior pacing to measure LOS toward the
NEXT waypoint instead of the fan's min — an open sightline ahead now genuinely hastens even with
clutter off to the side. Orbit/moveTo untouched (already correct per #984's live log — height/
distance to a known target, not a raycast at all). PR #985, auto-merge armed.

**Not fully closed — handed off.** User: "another session will smoothen out the path for reason
of tour speeding up calculation" — further path-smoothing/speed-calc work is a known follow-on,
not done in this session. Don't re-litigate the LOS-vs-fan decision above without re-reading this
entry; it was arrived at from 3 rounds of live-log-verified correction, not a guess.

## ✅ IMPLEMENTED 2026-07-26 — §TARGET_BOUNDED_LOOKAHEAD, `bim-ootb` `fix/flypath-target-bounded-lookahead`
(worktree `/tmp/wt-fly-target-framing`, off `fix/fly-tour-los-pacing` (PR #985) @ `78b6046`, current
with `origin/main`; this is the "another session will smoothen out the path" handoff v4 named above)

**Trigger — cross-referenced from `FLY_TOUR_DLOD_SCALE.md` §21-§24, not this file's own thread.**
That file's investigation into why MaxQ's live preview (`cinema_maxq.js` `pvStep`) and Clash's
fly-to (`measure.js` `_flyToClash`) both read smooth while Fly Tour's interior `flyPath` doesn't —
after ruling out draw-call count, scene-object count, and render-pipeline settings (DPR/shadows/
composer/FOV, all checked, none differ in the causal direction) — found a real, code-grounded
mechanism difference:

| | target computed from | distance/framing |
|---|---|---|
| MaxQ dive (`effects.js` `_cinemaPathPlan`) | largest room's center/extent | scaled to that room's size |
| Clash fly-to (`measure.js:736-742`) | overlap-bbox center of the two clashing elements | `max(overlapMax*3, 2)` — scaled to the clash's own extent |
| Fly Tour `flyPath` (pre-fix, `tour.js` old line ~1334) | none | fixed **fraction** (0.05) of the curve's own total arc length |

MaxQ/Clash both bound their gaze to something of room/clash SCALE (a few meters); their distance is
DERIVED from that bounded target, never a flat fraction of an unrelated total. Fly Tour's `flyPath`
look-at used `getPointAt(t+0.05)` — 0.05 of the ACTION'S OWN arc length, which for a short MaxQ-style
beat (settle-to-door, effects.js `§CINEMA_TIMING_672` uses the same 0.05-of-path-style fraction) stays
a few meters ahead, but for `flyPath`'s multi-room, whole-storey route (100+m total, per §22's own
"38-point route" scale) the SAME 0.05 fraction put the look-at target **tens of meters ahead** —
aiming the gaze through doorways/down corridors into geometry the camera hadn't reached yet, well
before arriving.

**Fix (`viewer/tour.js`, 2 lines of real logic + 1 log line, `§TARGET_BOUNDED_LOOKAHEAD`):**
`LOOKAHEAD_M = 5` (constant, same scale as the pacing work's own 3m/15m reference distances).
`act._lookAheadFrac = totalLen>0 ? min(0.05, LOOKAHEAD_M/totalLen) : 0.05` — caps the lookahead to an
ABSOLUTE arc-length distance instead of a fixed fraction, independent of the action's total length.
Short flyPath actions (already ≤5m at 0.05) are unaffected; long multi-room actions get clamped. The
existing §SOFTEN 0.08 lerp-smoothing (spur-room reversals sweep, not whip) is untouched — this only
changes WHERE the raw lookahead point sits before smoothing, not the smoothing itself.

**Verified live, LTU_AHouse, real RTX 4060 browser (not synthetic, not screenshot-judged):** drove
`streamTick()`/`walkTick()` directly (bypassing the rAF idle-park gate and a hidden-automation-tab
rAF-throttle landmine hit during this session — both required manual pumping to get real streamed
geometry and a built tour without a 2+ minute stall) to reach the first interior `flyPath` action
(23 pts, `totalLen=555.7m`). Sampled 25 points along its curve; at each point, rendered TWICE — once
with the new capped look-at, once with the old `t+0.05` look-at — same camera position both times,
only orientation differs, then read `renderer.info.render.triangles` after a forced re-render:
- **Mean triangles across the 25 samples: 5,105,832 (old) → 4,179,055 (new), an 18% reduction**
  (`ratio=1.22`), with 9/25 points showing a real (>10%) drop, 2/25 slightly worse, 14/25 near-parity
  (points where a wall/turn was already within 5m under the old lookahead too).
- **Worst single-point divergence: t=0.14, old aimed 14.3m ahead vs new's capped 5.1m —
  9,412,644 → 1,403,138 triangles, a 6.7x reduction**, from an orientation change alone (identical
  camera position). Several other points showed 2-4x drops (t=0.34: 8.30M→2.25M; t=0.54: 5.51M→2.66M).
- Confirms §23/§24's hypothesis directly: the old lookahead frequently pointed down open corridor/
  doorway sightlines exposing far-room geometry; capping it to human/room-scale distance measurably
  cuts what's in frustum, independent of the object-count/draw-call fixes §21/§22 already exhausted.

**What this does NOT fix, stated plainly:** this is an ORIENTATION fix, not a geometry-reduction one
— the remaining triangle cost when the capped lookahead still faces open geometry (14/25 sample
points, near-parity) is real GPU/rasterization cost, same domain `FLY_TOUR_DLOD_SCALE.md` §17's
room-occlusion work already targets. This and that work are complementary, same as §22 noted for its
own object-count fix. Also NOT re-tested: whether the tighter/lower-average look-at changes perceived
"feel" (does the shorter gaze read as staring at the wrong thing on approach) — the pacing sessions
already covered SPEED feel; this fix's FEEL (not just its triangle count) has not been live-watched
by the user yet. Confirm that before calling this fully closed.

**Housekeeping note for whoever continues:** the test worktree hit two real environment landmines
worth knowing about before repeating this kind of live capture: (1) the streaming/render loop is
fully idle-parked (`main.js` `_startLoop`/`_rafId`) and ALSO genuinely killed by the browser's own
`visibilitychange` handler when the automation tab isn't the foreground/visible tab — `A.streamTick()`
and `A.walkTick()` are both safely callable directly (bypassing rAF) for this reason, confirmed
working; (2) a plain `python3 -m http.server` does not serve HTTP Range requests (LTU's 71MB
extracted.db needs 206 Partial Content or the app's own fetch wrapper treats it as a failure and
falls back to slow OCI) — use `npx http-server -c-1 --cors` or equivalent instead.

### Non-goals (this spec)
- Exterior/aerial (`moveTo`/`orbit`) pacing: untouched, out of scope, already correctly separated in
  the current code (own `WALK_SPEED` constant, not `flySpd`).
- Not a DLOD/render-cost fix — unrelated to `FLY_TOUR_DLOD_SCALE.md`'s occlusion/frame-cost work,
  filed here specifically because this is a routing/pathing concern, not a rendering one.
- Not authorized to implement from this section alone — per this project's Spec-First rule, the
  investigation in the numbered items above needs real data/citations added to this file before any
  code changes to `tour.js`'s interior route-building or pacing.

## §TOUR_TIMELINE_SCRUB — SPEC ONLY, not yet implemented (2026-07-26, user directive verbatim)

**User's own words, verbatim, the actual scope:** "introduce a new feature which is the tour
timeline where it can be scrubbed... It is separate from the history/timeline which is dots of
events. This tour timeline be smooth to play forward backward while maintaining the overlay ie
n,Alt-G/o."

**Explicitly NOT the existing History Bar** (`common/history_bar.js`/`universal_history.js`/
`history_tap.js`) — that system is a discrete EVENT log (`§HIST_LIST`/`§HIST_PUSH`, dots per
recorded `§act`: "Opened LTU_AHouse", "Alt+G → GI preview", etc.), navigable by jumping between
event dots, not a continuous playback position. This new feature is a CONTINUOUS scrubber over the
Fly Tour's own playback time — drag a handle, camera position/gaze update live, both directions —
closer to a video seek bar than an event timeline. Two different mechanisms, two different files;
this one belongs in `tour.js`, not the history system.

**What the engine already has, real code citations, not assumed:**
- The tour is `A.walkActions[]` (22 actions this session: orbit, moveTo, flyPath×6, pause,
  lookAround, interleaved) — each with its own `duration`/`_duration`, known once `buildTour()`
  completes. A GLOBAL scrub position is naturally "cumulative time across all actions in order,"
  computable once (prefix-sum the durations) — no new data needed, just a derived table.
- Per-action pose-from-progress is MOSTLY already a pure function of a 0-1 fraction: `flyPath` uses
  `act._curve.getPointAt(t)` (real arc-length parameterized spline — already random-access, not
  iterative); `orbit` computes position from `angTt`/`radius`/height formulas at a given `tt`
  (`tour.js` orbit block, also already pose-from-fraction); `moveTo` lerps `_startPos`→destination
  by a t fraction. **This is good news for scrubbing** — the POSITION side of every action type
  already supports "give me the pose at fraction t," which is exactly what a scrubber needs.

**What does NOT already support random access — the real open design question, not decided here:**
- **Look-at smoothing is frame-history-dependent, not pure-from-t.** `flyPath`'s gaze
  (`act._prevLook.lerp(lookPt, 0.08)`, `tour.js` ~line 1354) and `lookAround`'s pan both blend from
  the PREVIOUS frame's target, not compute fresh from `t` alone — correct for forward playback
  (smooths track-switches, §SOFTEN), but undefined for an arbitrary scrub jump (there is no "previous
  frame" to lerp from at a fresh seek position). Needs a decision: snap `_prevLook` directly to the
  raw target on a hard/large jump, keep the lerp only for small in-place drag deltas (the user's own
  "smooth to play forward backward" wording suggests the drag-scrub case specifically wants the
  smoothing kept, just not broken by big jumps) — a threshold-based hybrid, not a full redesign.
- **Per-action lazy-init runs once at `A.walkActionT === 0`** (`flyPath` builds `_curve`/
  `_paceRemap`; `moveTo` captures `_startPos`/`_startTarget`; `orbit` similar) — a scrubber jumping
  INTO the middle of an action that hasn't run its init yet would read `undefined` curve/remap
  objects. Needs either: precompute/cache every action's init eagerly once at tour-build time (all
  the real geometry/points needed already exist then — nothing here depends on live camera state
  except `moveTo`'s `_startPos`, which is only used for the FIRST `moveTo`; every OTHER action's
  init uses already-known waypoints, not live camera position, so this is likely cheap and safe to
  precompute for all 22 actions up front, not lazily) — or a small per-action "ensure inited" guard
  called from the scrub-seek path, reusing the SAME init code forward-playback already uses.
- **DLOD/streaming systems tick incrementally, not from-scratch per seek** (`§DLOD_NAV_BUDGET`
  ramps up over many ticks, `§ROOM_OCCL_INDEX`/`§ROOM_OCCL_ROOM` tracks room transitions with
  `stableN` hysteresis) — a large scrub jump changes camera position discontinuously, which these
  systems don't currently expect (they assume gradual, tick-by-tick movement). Needs checking
  whether a big scrub jump should force an immediate DLOD re-evaluation at the new position (most
  correct) vs. just letting the existing budget ramp catch up over the next few frames (cheaper,
  possibly a visible pop). Not decided here — a real perf/correctness tradeoff, not a UI detail.

**"Maintaining the overlay ie n, Alt-G/o" — stated as an INVARIANT, not new work.** Night mode
(`toggleNightMode`), GI preview (`Alt+G`, `N8AO` composer toggle), and DLOD nav (`toggleDlodNav`)
are independent scene/render-state toggles, orthogonal to tour playback today — scrubbing must not
call anything that resets them. This is a regression risk to explicitly TEST once built (drag the
scrubber with each toggle on, confirm it survives), not a mechanism to build — the scrub
implementation should touch camera/gaze/DLOD-state only, never re-run whatever code path those
toggles live in.

**Not authorized to implement from this section alone** — per this project's Spec-First rule, the
three open items above (look-at smoothing at arbitrary seek, eager vs. lazy per-action init, DLOD
re-evaluation on discontinuous jumps) need resolving — by further investigation citing real code, or
by the user's own call on the tradeoffs — before any `tour.js`/UI changes. Likely a real, if small,
new UI element too (a draggable scrub bar) — not specified here, follows once the engine-side
seek() mechanism above is settled.

**"Similar to TM" (2026-07-26, user's own framing) — real, confirmed precedent, not a loose
analogy, and it likely makes this feature cheaper than the open items above suggest on their own.**
`viewer/time_machine.js` already ships exactly this shape of control, proven and shipping:
- A time cursor (`_cursor`, ms into the project timeline) that state/rendering is a function OF,
  plus a real HTML range-input scrub bar (`#tm-slider`, `time_machine.js:2523`) wired on the `input`
  event (`onSlide`, `:2595`) — the UI chrome, drag mechanics, and slider styling this feature needs
  are not new, they already exist and work.
- A progress bar synced to cursor fraction (`pbar.style.width`, `:2335-2336`) — the same "where in
  the sequence am I" display the tour scrubber needs.
- **TM already has cursor-driven CINEMATIC CAMERA behavior, not just element-visibility toggling**
  — `_cineTransitFrom`/`_cineTransitTo` (`:1567-1742`) move the camera smoothly as `_cursor` scrubs,
  including obstruction-peeling and distance-based easing. This is the closest existing precedent to
  Fly Tour's own camera-pose-from-position problem — study THIS code before assuming Fly Tour needs
  a bespoke mechanism (the walkTick-replay-via-virtual-clock idea floated earlier in this same
  session is a reasonable fallback, but TM's own approach should be checked first — it may already
  solve the "smooth scrub, either direction" problem in a way that's directly portable).
- **Concretely, next session's first move:** read `time_machine.js`'s cursor→state→camera pipeline
  end to end (not just the citations above), then decide whether Fly Tour's scrubber wraps THAT
  same kernel abstraction (cursor→pose function, shared UI slider pattern) rather than building a
  parallel one. If BIM Compiler's kernel already has one general "scrub a cursor across time, drive
  visible state and camera from it" concept serving BOTH 4D construction playback and now the Fly
  Tour, that's a real, reusable, and (per the user's own words) "free" architectural asset — worth
  stating plainly in whatever session picks this up, since it changes the scope from "build a
  scrubber" to "wire Fly Tour into the existing scrub kernel."

**Two more real design points added post-close (2026-07-26, user, before actually ending):**

1. **Discovery/reveal interaction, user's own words:** "a movie record button icon flashing when
   it is playing, and when pressed it pauses and a timeline appears with the control knobs." I.e.
   the scrub UI is HIDDEN by default during normal cinematic playback (keeps the view clean) —
   a single recognizable icon (record-dot style, flashing while playing) is the only visible
   control; pressing it pauses the tour AND reveals the full scrub bar/knobs. Not "always-on
   timeline UI" — a two-state reveal, discoverable via one familiar icon language (record/pause),
   not a persistent control bar competing with the cinematic view.
2. **Confirmed, real architectural distinction — "ours quite on the fly" vs. baked (user's own
   framing, verified accurate, not just asserted):** this codebase already has a genuinely BAKED
   system to contrast against — `cinema_maxq.js`'s own cinema export pipes the canvas through
   `MediaRecorder`/`captureStream()` to produce an actual `.webm` file (real wall-clock pre-render
   time proportional to length, nothing scrubbable until that finishes). The Fly Tour scrubber is
   the opposite: every seek recomputes the LIVE 3D scene at that exact pose in real time — no
   pre-render step, any window size, and it keeps respecting whatever's toggled on screen (night
   mode/GI/DLOD nav) because it's a real render, not a video-frame lookup. Worth stating in the
   eventual implementation's own comments, not just here — it's the reason a live-replay-based seek
   (§ above) is the right shape, not "bake then let the user scrub the recording."

**Session closed here (2026-07-26, user: "clean close new session on that").** Spec above is
sufficient for a fresh session to start from a running start — investigate `time_machine.js`'s
cursor/camera pipeline first, decide reuse-vs-bespoke, then implement. Not started this session.
