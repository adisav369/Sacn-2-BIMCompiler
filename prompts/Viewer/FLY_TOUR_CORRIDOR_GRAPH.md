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
