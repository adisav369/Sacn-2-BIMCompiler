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
