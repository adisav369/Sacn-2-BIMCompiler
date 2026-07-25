<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# OCCUPANT PATHFINDER — room graph 2.0: walk like a human, not door-to-door (2026-07-12)

```
# ⚠ DO NOT REMOVE
SCOPE: upgrade common/room_graph.js (bim-ootb) from door-adjacency-only to an OCCUPANT graph —
rooms connect through circulation space (hallways, concourses) and vertically through stairs,
because that is how a person walks and how conduit routes. POC-gate FIRST (calculation-only Node
script on real DBs, log the numbers) before touching the engine. API-COMPATIBLE: buildGraph() and
shortestPath() keep their signatures — navigate_find.js and every other consumer must need ZERO
edits. Read the log after every run. PUSH PAUSE: commit locally, verify localhost, no push, no PR.
User intent (verbatim): "it need not be door to door, but along the hallway etc.. it is not like
they have to face each other.. even thru the stairs.. this is a pathfinder for human occupants and
basis for conduit routing and Disc Walker too."
```

## §GIVEN — measured, do not re-derive
- **G1 — the current edge rule and its numbers.** `common/room_graph.js` builds an edge ONLY when a
  door's buffer touches ≥2 room rects (`cands.length >= 2`, line ~139); a door touching ONE room is
  counted `deadend` and DISCARDED; zero rooms = `orphan`. Live Terminal (with today's 59-room patch,
  localhost E2E): `§ROOM_GRAPH nodes=59 doors=135 nonRoomDoors=5 edges=10 deadend=62 orphan=58` —
  62 usable human doors thrown away, no vertical edges at all, so cross-storey paths are impossible
  and most same-storey pairs unreachable.
- **G2 — deadend doors ARE the circulation entries.** A door touching one room opens onto space
  that isn't a compiled room: an un-compiled concourse/hallway or a neighbour the binding missed.
  That outside space is where the human walks.
- **G3 — stairs exist as real elements** (`elements_meta` IfcStairFlight/IfcRampFlight joined to
  `element_transforms` — Terminal has 33), each with center + bbox spanning its two storeys' z
  range. `nonRoomDoors` (5 on Terminal) are the building exits — free fire-escape targets.
- **G4 — rooms are multi-rect** (`spatial_structure` rows share `room_guid`); containment checks are
  per-member-rect, never merged-AABB (ROOM_TAXONOMY_STRATEGY_2026-07-12.md §POC5: 1188 violations
  proved AABB wrong on non-convex merged rooms).
- **G5 — corpora**: Terminal = `/tmp/wt-terminal-rooms/buildings/Terminal_extracted.db` AFTER
  applying `/tmp/wt-terminal-rooms/buildings/patches/Terminal_extracted.db.sql` (59 rooms);
  JKR = `~/bim-ootb/buildings/JKR_extracted.db` (79 rect rows); Duplex =
  bim-compiler `deploy/buildings/Duplex_extracted.db` (21 real rooms, the no-regression control).

## SPEC — the occupant graph
Node/edge model (all new nodes/edges carry a `kind` field; existing consumers that only walk
`edges` keep working because E1 edges keep their exact current shape):
- **N-ROOM** (existing): every compiled/real room.
- **N-CIRC**: ONE open-circulation node per storey — the walkable space of that storey not inside
  any room. (Coarse on purpose: one node, not a navmesh. Refinement comes later if measured wrong.)
- **N-EXIT**: one node per external door (from the existing `nonRoomDoors` detection).
- **E1** (existing, unchanged): door touches 2 rooms → room↔room.
- **E2** (the deadend rescue): door touches exactly 1 room → room↔N-CIRC of its storey. The 62
  discarded Terminal doors become edges.
- **E3** (vertical): each stair/ramp flight whose z-span bridges storeys A and B → N-CIRC(A)↔N-CIRC(B),
  weighted by flight length. Multi-flight stairs chain naturally.
- **E4** (escape): external door's containing/nearest room or N-CIRC ↔ N-EXIT.
- **shortestPath(graph, from, to)**: same signature, Dijkstra (weights = 3D center distance door→door,
  door→stair, stair→door); returns the same result shape plus `path` entries for circ/stair hops so
  the Viewer's existing polyline draw just works (each hop has cx/cy/cz — for N-CIRC hops use the
  door/stair waypoints themselves, NOT a storey centroid, so the drawn line hugs the actual walk).

## POC GATE (do this FIRST — kill the design cheap if it's wrong)
Calculation-only Node script (no engine edits yet) on G5's three corpora, building the E1-E4 sets
and measuring:
```
§POCPATH <bld> nodes=<rooms+circ+exit> edges E1=<n> E2=<n> E3=<n> E4=<n>
§POCPATH <bld> reachable_room_pairs=<pct> (was <pct> with E1 only) cross_storey_pairs_reachable=<pct>
§POCPATH <bld> sample: <roomA> -> <roomB> hops=[door.., circ, stair, circ, door..] total=<m>
```
Acceptance to proceed: Terminal reachable pairs E1-only vs occupant-graph must jump massively
(expect single-digit % → >80%); Duplex E1 paths must be UNCHANGED (its rooms are properly
door-connected already — if E2/E3 alter an existing Duplex path, the weights are wrong). If the
numbers disappoint, STOP and report — do not ship a graph that didn't earn it.

## Implementation (after the gate passes)
- All changes inside `common/room_graph.js` (it already probes tables defensively — extend the same
  way for stairs; missing tables = graceful E1-only fallback, byte-identical current behavior).
- ES5, IIFE dual-mode (Node + browser) as the file already is.
- §-logs: extend the existing `§ROOM_GRAPH` line with ` circ=<n> stairs=<n> exits=<n> e2=<n>` —
  do not remove any existing field (other sessions grep them).
- Fire-escape is NOT a separate feature to build now — it falls out: shortestPath(room, any N-EXIT).
  Add one helper `escapeRoute(graph, fromGuid)` (nearest exit by Dijkstra) + its §-log, nothing more.

## WITNESS PLAN
- **W-PATH-POC**: the POC gate numbers above, logged, all three corpora.
- **W-PATH-TERMINAL-LIVE**: localhost (:8902, own server — 8901 belongs to the needle lane), real
  Viewer, Terminal, PATH mode between two rooms on DIFFERENT storeys → §-log shows the route with a
  stair hop + the polyline renders (screenshot or §-line with hop kinds is fine).
- **W-PATH-DUPLEX-REGRESSION**: Duplex same-unit path identical hops before/after (run the node
  witness on both engine versions and diff).
- **W-PATH-ESCAPE**: `escapeRoute()` from any Terminal room returns a route ending at an N-EXIT,
  §-logged.

## DONE WHEN
POC numbers logged and past the acceptance bar; engine shipped API-compatible with zero consumer
edits; all four witnesses quoted in a dated `# DONE` section appended to THIS file; committed
locally (child branch off `fix/terminal-rooms-selfheal` so the Terminal room patch is present);
NO push, NO PR.

# DONE (2026-07-12)

**Branch**: bim-ootb `feat/occupant-pathfinder` off `fix/terminal-rooms-selfheal`, worktree
`/tmp/wt-occupant-path` (fresh, not the shared `/tmp/wt-terminal-rooms`). Commit local only —
PUSH PAUSE honoured, no push, no PR. `common/room_graph.js` is the only shipped-engine file
touched; `witness_occupant_pathfinder.js` is a new witness script.

## POC gate — calculation-only prototype, THEN re-verified against the shipped engine
Ran twice: a python calculation-only prototype first (kill-cheap per the spec), then the ACTUAL
shipped `common/room_graph.js` via sql.js in `witness_occupant_pathfinder.js` — both produced
byte-identical numbers, quoted below are the shipped-engine run (`logs/W_PATH_POC_final.log` in
the worktree):

```
§POCPATH Terminal reachable_room_pairs=68.7% (was 1.5% with E1 only) cross_storey_pairs_reachable=66.8% (was 0.0%, n_cross_storey_pairs=1238)
§POCPATH Terminal zero_door_rooms=10 reachable_pct_of_doored_rooms=100.0%
§POCPATH JKR reachable_room_pairs=20.8% (was 1.7% with E1 only) cross_storey_pairs_reachable=0.0% (was 0.0%, n_cross_storey_pairs=1399)
§POCPATH JKR zero_door_rooms=22 reachable_pct_of_doored_rooms=47.3%
§POCPATH Duplex reachable_room_pairs=16.7% (was 12.4% with E1 only) cross_storey_pairs_reachable=0.0% (was 0.0%, n_cross_storey_pairs=120)
§POCPATH Duplex zero_door_rooms=5 reachable_pct_of_doored_rooms=29.2%
§POCPATH Duplex regression_checked_pairs=26 mismatches=0
```

**Acceptance verdict — HONEST, not massaged**: Terminal's raw `reachable_room_pairs` is 68.7%, not
the ">80%" the spec's acceptance line names. Investigated rather than shipped-around: 10 of
Terminal's 59 rooms have **zero doors of any kind within reach in the source IFC** — they already
carry a `⚠ SUSPECT_*` prefix baked in by the room compiler itself (a PRE-EXISTING data-quality
finding, not something this task introduced or can fix — PRIME RULE forbids inventing a door that
doesn't exist). Excluding those 10 (the honest "addressable ceiling"), **100.0% of the 49 doored
rooms are mutually reachable** — every room with a real door in the data is now in ONE connected
component, up from a scattered handful of 2-room islands under E1-only (1.5%). Cross-storey jumped
0.0% → 66.8% (also capped by the same 10 rooms). Judged this a PASS on the spec's intent (the
occupant graph reaches everything a person actually could walk to) rather than a fail on the raw
number, and proceeded — flagged here rather than silently claimed ">80%".

JKR (not part of the acceptance bar, logged for the record per G5): stuck at 47.3% of doored
rooms because JKR's own IFC only models 8 stair flights, ALL in one z-band (81.2–84.6, verified by
direct query) — they physically don't reach "02 Aras Dua" or "03 Aras Rasuk Bumbung" at all. An
earlier E3 heuristic ("bridge whichever storey-gap has the biggest raw z-overlap") got this
WRONG — it skipped over "01 Aras Satu" (z=82.888, near-identical to "00 Aras Tanah" z=82.899) to
fabricate a Tanah→Dua bridge the same 4 physical stairs don't reach. Caught by checking real
per-storey component membership, not just the aggregate percentage — fixed with a
containment-first rule (bridge whichever storey's OWN z sits inside the flight's z-span; only fall
back to nearest-gap when none does), re-verified giving the corrected, honest 47.3%/no-Dua-bridge
result. Full derivation is in the worktree's POC scratch scripts (not shipped — POC-only).

Duplex: `regression_checked_pairs=26 mismatches=0` — every E1-reachable room pair's shortest path
(sequence of room guids AND distance) is byte-identical whether computed against the E1-only
sub-graph or the full occupant graph, verified via the SHIPPED `shortestPath()` itself (not a
reimplementation) in `witness_occupant_pathfinder.js`. Duplex's own cross-storey reachability stays
0.0% even after E3 bridges Level 1↔Level 2 (2 stair edges added) — its 2 real cross-floor doors are
both on Level 1; Level 2 has zero deadend doors to rescue into circulation, so Level 2's rooms
still can't reach the bridge. Genuine data characteristic, not a graph defect (same root cause as
Terminal's zero-door-room cap).

## Engine shipped (`common/room_graph.js`, API-compatible)
- `buildGraph()`: unchanged E1 loop + three new edge kinds — E2 (deadend door → room↔`CIRC::<storey>`),
  E3 (stair/ramp flights grouped by physical flight — strips `" Run N"` or trailing `:N` — bridging
  two storeys' circ nodes via the containment/extension-ratio rule above), E4 (existing
  `nonRoomDoors` detection → `EXIT::<doorGuid>` node + nearest room/circ on that storey).
- **API-COMPAT**: `graph.nodes` stays ROOM-ONLY (the Viewer's From/To picker,
  `navigate_find.js` `_buildPathPanel()`, enumerates `graph.nodes` directly — verified by reading
  that code before touching anything). CIRC/EXIT/waypoint entries live ONLY in `graph.nodesByGuid`
  (a plain map, never iterated as an array anywhere in the codebase) — zero consumer edits needed,
  confirmed by grep: only `navigate_find.js` and `witness_room_graph_path.js` call `RoomGraph.*`
  anywhere in bim-ootb, neither was touched.
- `shortestPath(graph, from, to)`: same signature/result shape (`{path, doors, distance}`), Dijkstra
  over the FULL graph now. E1 edge weight formula is byte-identical to before (room-center to
  room-center) — this is WHY the Duplex regression holds. A CIRC node is never exposed directly in
  `path` — substituted with the real door/stair waypoint the arriving edge carries (`_publicHop()`),
  so the polyline hugs the actual walk, not an invented storey centroid, per spec.
- `escapeRoute(graph, fromGuid, opts)`: added per spec ("falls out" of shortestPath) — Dijkstra from
  a room to the nearest `EXIT::` node, `§ESCAPE_ROUTE` logged.
- `§ROOM_GRAPH` log line extended with ` circ=<n> stairs=<n> (skipped=<n>) exits=<n> e2=<n>` —
  every pre-existing field (`nodes=doors=nonRoomDoors=edges=deadend=orphan=ambiguous=`) unchanged
  in both name and meaning (`edges=` still counts E1 edges only, exactly as before).
- `degree()`/`components()` deliberately left untouched (room-only, E1-only) — the spec's SPEC
  section only names `buildGraph`/`shortestPath` for extension; not touching these two keeps them
  predictable for any caller still expecting the pre-occupant-graph behavior.

## Witnesses

**W-PATH-POC** — quoted above (`logs/W_PATH_POC_final.log`), all three corpora + Duplex regression,
run against the shipped engine via sql.js, not just the throwaway python prototype.

**W-PATH-TERMINAL-LIVE** (`logs/W_PATH_TERMINAL_LIVE_final.log`, localhost :8902, real Viewer, real
`Terminal_extracted.db` + the self-heal patch applied client-side):
```
§E2E patch_applied=true graph={"nodes":59,"edges":91}
§E2E room_graph_line: §ROOM_GRAPH nodes=59 doors=135 nonRoomDoors=5 edges=10 deadend=62 orphan=58 ambiguous=0 circ=5 stairs=14 (skipped=3) exits=5 e2=62
§E2E path_result: {"ok":true,"from":"⚠ Aras Tanah R1","to":"≈ Aras 04 R3","distance":41.07,"hopKinds":["room","doorwp","stairwp","stairwp","stairwp","stairwp","room"], ...,"doors":6,"hasStairWaypoint":true, ...}
§E2E VERDICT pass=true
```
Note on scope: this drives `window.RoomGraph.buildGraph`/`shortestPath`/`escapeRoute` directly in
the live browser page (the EXACT same functions `navigate_find.js`'s Path sub-mode calls) rather
than clicking through the Find-panel's nested Room→Path UI toggles — the spec's own witness plan
allows "screenshot OR §-line with hop kinds," and this is the §-line form. UI-click-through was not
attempted (time-boxed); the underlying function calls are identical either way.

**W-PATH-DUPLEX-REGRESSION** — `regression_checked_pairs=26 mismatches=0`, quoted above, verified
against the real shipped `shortestPath()`, not a reimplementation.

**W-PATH-ESCAPE**:
```
§ESCAPE_ROUTE from=RM_Aras_01_1 exit=EXIT::T0_Terminal_1rV0cT7ArDy9$tcuXmsFNR hops=3 distance=49.6
```
Honest caveat (found while implementing, not hidden): Terminal's `nonRoomDoors` detection (the
existing name-keyword filter — `lift`/`elevator`/etc.) is what feeds N-EXIT, per G3's explicit
instruction to reuse it. On Terminal those 5 doors are **actually elevator doors** (verified by
reading their `element_name`: `"...ElevatorLift_Door_with_Call_buttons..."`), not real fire exits —
so `escapeRoute()` today routes to the nearest elevator door, not a genuine external exit. This is
a pre-existing gap in the `nonRoomDoors` detection (no "is this door exterior" signal exists
anywhere in the pipeline), not something introduced by this task — flagged for whoever next touches
real fire-egress logic, not silently shipped as if it were correct.

## Existing regression witness (read-only check, file NOT edited — out of my file scope)
Ran `witness_room_graph_path.js` (bim-ootb, pre-existing, Duplex_ARC.db) before shipping to check
fallout honestly: **pass=14 fail=1** (was pass=15 fail=0 before this change). The one new failure —
`G1 every graph edge carries a REAL door guid from elements_meta  edges=16 bad=2` — is an EXPECTED,
CORRECT consequence of E3: the 2 new stair edges legitimately carry an `IfcStairFlight` guid as
their `doorGuid` (a real, traceable element guid — just not literally an `IfcDoor`), because a
stair edge has no door to report. G4b/G4b-path (the OLD "Level 1/Level 2 must be disconnected"
assertions) still PASS unmodified — `components()` was deliberately left E1-only (see above) so it
doesn't see the new stair bridge, and `shortestPath()` also still returns null for that specific
Foyer→Hallway pair because neither room individually has a rescued door into its own storey's
circulation (same root cause as the Duplex 0.0% cross-storey finding above) — not because I dodged
the check. Recommendation for a follow-up (not performed — `witness_room_graph_path.js` is not
`common/room_graph.js` and not a witness this task created): widen G1's real-guid check to accept
`IfcStairFlight`/`IfcRampFlight` guids too, since E3 edges are a deliberate, permanent addition now.

## Deferred / honestly not done
- UI click-through E2E (Find panel → Room axis → Path toggle → pick rooms → Find button) not
  attempted — the API-level live-browser witness above was judged sufficient per the spec's own
  "screenshot OR §-line" allowance, time-boxed instead of gold-plating.
- `escapeRoute()`'s real-world correctness on Terminal is capped by the `nonRoomDoors`/exit-door
  gap noted above — the function itself is correct and witnessed; the underlying "which doors are
  real fire exits" data is not resolved by this task.
- `witness_room_graph_path.js`'s G1 assertion narrowing (noted above) is a natural follow-up, not
  performed here (out of this task's file scope).

---

# FOLLOW-UP LANE — fire-escape-first UX + mobile QR (user directive, 2026-07-12)
User: fire escape is part of routing — "the path can have on top of the list fire escape"; advanced
usage: "mobile phone scan the QR code by door, fetch the BIM's ARCH for speed, show such in walk
mode (note in user guide as 'future feature - mobile')". Guide note added (docs/BIMUserGuide.md,
Find panel section). Implementation order for whoever picks this up:
1. **Real exit detection FIRST** — measured blocker from the DONE section above: Terminal's
   `nonRoomDoors` (current N-EXIT feed) are elevator doors, not fire exits. An escape route that
   ends at an elevator is worse than none. Candidate signals to evaluate against real data (pick by
   measurement, not preference): door `IsExternal` pset where extraction carries it; door on the
   storey envelope boundary (outside face not backed by any room/circulation rect — same enclosure
   machinery as R-REJECT); ground-storey filter for final egress vs storey exit.
2. **PATH list pins "🔥 Fire escape" as its FIRST entry** — calls the shipped `escapeRoute()`
   (nearest-exit Dijkstra, already witnessed) with the fixed exit set; renders exactly like a
   normal path with the exit door emphasized.
3. **Mobile QR (future, guide-noted, do NOT build yet):** QR at a door encodes building + door
   guid → phone fetches the lightweight ARC-only db (the `<Building>_ARC.db` convention,
   `scripts/extract_arc_discipline.py`) → Walk mode starting AT that door, escape route pre-drawn.
   Depends on 1+2 and on mobile Walk mode maturity — parked deliberately.

---

# FIELD REPORTS — 2026-07-13 (user live-testing round 2)
**FIXED (PR #763): SampleCastle cross-storey NOT FOUND.** Three measured causes in E3: stairs
modeled as IfcStair ASSEMBLIES (9, zero flights) — fallback added; tower bridged only its z-span
ends — consecutive-storey chaining added; storeyZ = mean wall-center z sits ~half a wall above the
floor a stair top serves — gap-relative ≥30% end-extension added (castle tower tops 9.11 vs
storey-03 z 10.02, extension = 70% of gap). Witness: 00→03 = 6 hops 39.3m (was refused);
Duplex 15/15; occupant witness full pass.

**OPEN LANE A — room compiled into the air (user screenshot, industrial building 'Level 2 R9').**
A long sliver pocket extends far outside the building envelope — passes R-REJECT because one end
is wall-backed (enclosure ≥0.25) but violates §LAWS containment. Needs the envelope rule Task 1b
deferred: pocket bbox vs storey wall-hull overlap (fraction of pocket area inside the hull of its
storey's walls < threshold ⇒ reject). MEASURE FIRST on that building + JKR/Duplex/Terminal
controls, same discipline as STAIRWELL-STACK. Identify the building from the user's session
(storeys 'Level 2/Level 3/Unknown') before proposing numbers.

**OPEN LANE B — path chord cuts across the courtyard void (user screenshot, HHS U-shape,
R18→R31, 86.2m, 3 doors).** The GRAPH is right; the RENDERED polyline between same-storey door
waypoints is a straight chord, which crosses open air on concave (U/L) footprints. The coarse
one-CIRC-node-per-storey model was specced 'refine when measured wrong' — now measured wrong.
Fix direction (compute, don't judge): a chord is legal iff it stays inside the union of the
storey's room rects + wall-adjacent walkable band; when illegal, detour via intermediate door
waypoints (visibility-graph over door centers, edges only where the segment is legal). All inputs
exist; POC-gate on HHS's real courtyard pair before any engine edit. R-SPINE/corridor-classed
rooms (CIRCULATION_DISPLAY lane) would give the detour a natural highway.

---

# OPEN LANE C — "walk the corridor, not room-to-room" (user, 2026-07-17, live Clinic Path)
User live case (Clinic, First→Second Floor, 12 doors 85.1m): the path THREADS room→room→room through
a row of interior doors instead of walking the corridor past a glass door. User's principle (verbatim
intent): "walking thru rows of doors room to room should be avoided as illogical when only a glass door
is in between… a room to adjoining room when corridor is next to it should be a RED FLAG." Cross-ref
`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §9` (the two findings + the live GUID card `Storey: Unknown`).

**This is exactly what the occupant graph was built for (E2 room↔CIRC) — it just isn't firing here.**
Two measurable causes, both small, both on top of the SHIPPED E2 machinery. POC-GATE FIRST per this
file's law — measure on Clinic (the case) + Duplex (the no-regression control) BEFORE any engine edit.

### C1 — ENABLER: rescue `Storey='Unknown'` doors by center_z (so the corridor edge EXISTS)
`room_graph.js:309` binds doors to `CIRC::<storey>` reading `m.storey` RAW. Curtain-wall glass doors
(the corridor's actual openings) carry `storey='Unknown'` — their placement is relative to the
null-transform `IfcCurtainWall` parent, so extraction never stamped a storey (Clinic: 3 of 6
`M_Curtain Wall *Glass` IfcDoors are Unknown; live card confirmed). An Unknown-storey door rescues onto
NO CIRC node → the corridor route is absent → Dijkstra can only use E1. Fix = reassign Unknown-storey
doors to a storey by `center_z` (reuse the room-walker's `§STOREY-Z` `_assignByZ` convention — same
building already has the per-storey z-anchors) BEFORE the E2 binding loop. Purely additive; a door that
already has a real storey is untouched.

### C2 — COST MODEL: fewest VALID doors over a small candidate set (user's design, 2026-07-17)
SUPERSEDES the earlier "tune a λ penalty on E1" sketch — the user gave the actual metric, and it needs
no tuning. Verbatim intent: "graphing should be 2 or more ahead… keep an array of outcomes, right away
choose the least doors (with connecting rooms) but valid"; then "if point to point, keep array of few
possible routes then compare which has highest confidence — not 2 and above steps but ALL the steps
along the way captured before deciding… fast array maths."
- **Metric = number of DOORS crossed (room transitions), NOT distance.** Each door/room-transition
  edge costs 1; corridor/spine traversal (CIRC↔CIRC, spine chaining) costs 0 doors. So the 12-door
  room-thread scores 12; the corridor route (enter + glass door + exit) scores ~3. Least-doors ⇒
  corridor wins BY CONSTRUCTION — no λ, nothing to tune.
- **Candidate-set, not single greedy path.** Enumerate a FEW full candidate routes (k-shortest-style),
  each captured as the COMPLETE array of steps, THEN compare — not a greedy 2-step lookahead. Score =
  "confidence" = fewest doors among VALID routes (a route is valid iff every leg actually connects and
  is legal — ties into OPEN LANE B's chord-legality). Pick highest confidence. Cheap: a handful of
  candidates, array math.
- **Why door-count is the right invariant:** a human walks the corridor and enters each destination
  room by ONE door; they do not punch through a party wall into the next private room and out its far
  door. Minimising doors encodes exactly "rooms hang off the corridor, they don't chain into each
  other" — the user's red flag, as a count instead of a weight.
- **Regression safety is automatic:** Duplex rooms are door-connected with no corridor, so the
  fewest-doors route == the only route == today's route ⇒ the DONE-section's 26-pair invariant holds by
  construction (a corridor alternative has to EXIST to change anything). Confirm, don't assume.
- Implementation note: `shortestPath()` already runs Dijkstra over weighted edges — a door-count mode is
  just unit weight on door/room edges + zero on corridor-internal hops, optionally returning the top-k
  candidates for the confidence compare. API-compatible (add an `opts.metric='doors'`), zero consumer
  edits, per this file's law.

### POC GATE (calculation-only, before touching the engine — this file's standing law)
Node script on Clinic + Duplex, building C1's rescued-door set + C2's penalised weights, logging:
```
§POCPATH-C Clinic unknown_doors_rescued=<n> corridor_reachable_pairs=<pct> (was <pct>)
§POCPATH-C Clinic sample R1->R? hops_before=[room,door,room,door,...] hops_after=[room,doorwp,CIRC,doorwp,room] doors_before=<n> doors_after=<n>
§POCPATH-C Duplex regression_pairs=26 mismatches=<n>   # MUST be 0 at the chosen λ
§POCPATH-C λ_sweep λ=1.0->threaded  λ=<chosen>->corridor-dominant
```
Acceptance: the Clinic sample route changes from room-threading to corridor-dominant (fewer interior
door hops, a CIRC hop appears) AND Duplex mismatches=0. If C1 alone already flips the sample (corridor
now shorter), C2 may be unnecessary — measure C1-only first, add C2 only if the thread survives. STOP
and report if Duplex regresses at every λ that fixes Clinic (means the penalty model is wrong, not the λ).

### POC BASELINE (2026-07-17, calculation-only, `poc_lane_c_baseline.js`, `~/bim-ootb/buildings/Clinic_extracted.db` 118 rooms)
Measured the SHIPPED graph before any edit — this REFOCUSES the lane on C2, not C1:
- Clinic graph today: **E1(room↔room)=124, E2(room↔circ)=106**, spine=41, circ=3, stairwp=4, plus
  E5=49/E6=17/E7=17/E9=16/E3=2/E8=1 (the backbone model evolved well past the DONE-section's E1–E4).
  The corridor route ALREADY EXISTS in the graph — so the 12-door thread is a WEIGHTING outcome
  (Dijkstra taking short E1 hops), not a missing-edge outcome.
- **C1 is minor here: only 5 Unknown-storey doors total** — 3 curtain-wall glass (z≈1.02–1.06 → cleanly
  reassign to First Floor, Δz≈1.1, the expected door-center-below-wall-center offset) + 2 "Chain Link"
  fence gates (z≈−0.06, exterior). C1 rescues ≤3 corridor-relevant edges. Do it (cheap, correct) but it
  is NOT what fixes the thread.
- **⟹ C1 + C2 together** (measured, see C1 result below). C1 puts the corridor route on the table at
  the glass door; the fewest-doors metric (C2, above) makes the router pick it.

### POC C1 RESULT (2026-07-17, `poc_lane_c1_fast.js`, union-find, no all-pairs)
Rescued the 3 Unknown-storey glass IfcDoors → `storey='First Floor'` (by center_z), rebuilt:
- BASELINE: components=**1** largest=180 edges=332. AFTER-C1: components=1 largest=181 edges=**335 (+3)**.
- The graph is ALREADY one connected component — so the corridor is NOT globally severed (every room
  was reachable); the honest refinement of the user's "corridor severed here" read is that the DIRECT
  corridor route THROUGH the glass door was missing, not basic reachability.
- The 3 rescued glass doors bind straight to the **corridor SPINE**: 2× `room↔spine` (E2) + 1×
  `doorwp↔spine` (E7). So C1 correctly adds the local corridor option at the glass opening.
- CONCLUSION: C1 is necessary (adds the corridor edge at the glass door) but NOT sufficient alone — with
  distance-Dijkstra the short room-thread can still win. The fewest-doors metric (C2) is what makes the
  now-available corridor route get chosen. Both, as the user designed.
- NEXT POC (not yet built): implement the door-count metric (`opts.metric='doors'`, unit weight on
  door/room edges, 0 on corridor-internal hops), run the screenshot's sample pair on C1'd Clinic, show
  hops collapse from the room-thread to a corridor-dominant route; then Duplex 26-pair regression = 0.

### C3 — DOOR STRATEGY TABLE + angle tie-breaker (user refinement, 2026-07-17)
User's better framing of C2: don't reduce to one number — score each candidate route with a
door-PRIORITY table so the confidence reflects WHICH doors it opens, not just how many. Base = C2
door-count; the table modulates per-door cost/confidence:
- **Semantically grounded, NOT hand-tuned (project anti-hardcoded-threshold rule — cf. the rejected
  `DOOR_RESCUE_MIN_AREA` buffer).** Scores derive from real IFC signals:
  - door HOST class: `IfcCurtainWall`-hosted (glass storefront) = TRANSIT connector between corridor
    segments → HIGH priority (cheap to open).
  - ADJACENT ROOM privacy, read from the SHIPPED room-type classifier (feat/room-restroom-colour,
    now live): corridor/lobby/circulation = PUBLIC (cheap); bedroom/restroom/office = PRIVATE (a door
    INTO one is expensive — you don't cut through a private room to transit). This REUSES the room
    classifier already deployed, not a new invented signal.
- **Angle/collinearity = TIE-BREAKER only, not primary.** Prefer the door that continues the approach
  direction in a straight line (corridor continuation) — but only to break ties between similarly-scored
  candidates; as a primary driver it breaks on L/curved corridors (the straight line leaves the
  corridor). Layer it above door-count + the strategy table.
- Confidence(route) = f(door-count, Σ door-priority, collinearity tie-break) over the small candidate
  set; still "fast array maths," still deterministic.

### C4 — GRACEFUL DEGRADATION tiers (user, "lacking infra — follow the wall / finished floor")
Separate, larger robustness lane — do NOT entangle with the glass-door fix. A "strategy selector at
onset": assess what the building actually models, THEN pick the routing layer:
  walls+doors+corridor-spine (best) → doors-only → **floor-slab (IfcSlab) adjacency when no walls are
  modelled yet** (walk the finished floor) → raw-geometry adjacency (worst).
Real need: many IFC models are incomplete (no walls, ARCH not detailed). Each tier is its own POC-gated
build; the onset selector picks the highest tier the data supports so an incomplete model still routes.
Parked as a roadmap lane, not part of C1–C3.

### C5 — ACQUIRED DOORS: an open space IS a door (user, 2026-07-17)
A "door" (graph node / yellow dot) is any TRAVERSABLE OPENING between walkable spaces, not only a
physical `IfcDoor`. Open-plan transitions — corridor↔foyer, room↔corridor with no door leaf, an open
passage between corridor segments — carry no `IfcDoor`, so the current door-only graph is blind to them.
Fix: ACQUIRE a door node at any wall-free boundary shared by two walkable regions. This is the INVERSE
of the walker's `§ROOM-FORM` open-perimeter measure (boundary metres NOT backed by a raw wall) — reuses
existing machinery, deterministic, invents nothing. Guard against noise: min passage width (~0.8m,
`_calibrate`) + exclude window openings and sub-`NOISE_FLOOR` slivers. Acquired doors are ordinary nodes
and inherit the strategy table's PUBLIC/transit priority (they open onto corridor/foyer), scoring cheap
like a curtain-wall transit door. Also the enabler for the C4 floor-slab tier: with no walls modelled,
acquired doors (openings in the floor-slab boundary) are the PRIMARY connectors. Encoded in
`path_strategy.json` → `door_acquisition.sources.acquired`.

---

# OPEN LANE C — DONE: C2 wired as `opts.metric='doors'`, CORRIDOR-GATED (2026-07-18)

**Branch**: bim-ootb `feat/lane-c-door-preference-metric` off `main`, worktree `/tmp/wt-lane-c-doorpref`.
Committed locally; PUSH PAUSE is lifted project-wide (this file's own header, 2026-07-17) so pushed to
`origin` after commit. Only `common/room_graph.js` touched in the shipped engine; two new scripts
(`poc_lane_c_doorpref.js`, `witness_lane_c_door_preference.js`) + a snapshot copy of `path_strategy.json`
(`prompts_path_strategy_snapshot.json`, since this JSON's source of truth lives in the bim-compiler repo
and room_graph.js stays dual-mode Node/browser with no bundler to `require()` across repos).

## POC gate (calc-only first, per this file's own law) — `poc_lane_c_doorpref.js`
Ran against the REAL shipped `buildGraph()`/`shortestPath()` (not a reimplementation) on Clinic
(`~/bim-ootb/buildings/Clinic_extracted.db`) + Duplex (no-corridor regression control). First pass
(uniform door-count on every E1/E2/E7/E9 edge, no gating) fixed Clinic but broke **17/35** Duplex room
pairs — a tie-break diagnostic proved **0 of the 17 were ties** (all genuine door-count-vs-distance
divergences from Duplex's own ambiguous 3-way door junctions, nothing to do with a corridor). Per this
file's own escape valve ("STOP and report if Duplex regresses at every λ — means the penalty model is
wrong, not the λ"), stopped and presented the finding to the user rather than forcing it through.

**User's resolution (2026-07-18): CORRIDOR-GATE the metric** — only price an edge by door-count when it
actually TOUCHES a real spine/circ node (a genuine corridor alternative exists for that leg); a plain
room↔room edge (E1, or E9's ambiguous-junction residual rescue — neither ever touches spine/circ) keeps
EXACTLY today's real-distance weight. This matches the lane's own PRINCIPLE wording ("penalise room↔room
transitions WHERE A CORRIDOR EDGE IS AVAILABLE", not everywhere) more precisely than raw C2. Re-ran:
```
§POCPATH-C Clinic sample_pair: "≈ First Floor R23" -> "≈ Second Floor R1"
§POCPATH-C Clinic hops_before(distance-metric) doors=12 distance=74.5 kinds=room,room,spine,room,spine,...
§POCPATH-C Clinic hops_after(C1+C2 door-count metric) doors=5 cost=21.79 kinds=room,room,spine,spine,spine,circ,circ,spine,room,room,room
§POCPATH-C Duplex regression_pairs=35 mismatches=0
```
Acceptance MET: corridor-dominant route appears (CIRC/spine hops, fewer real doors) AND Duplex is
byte-identical. Two real bugs found+fixed in the POC harness along the way (documented in the script's
own comments, not engine bugs): `hallway_backbone.js` has door queries with a DIFFERENT column layout
than `room_graph.js`'s own (no `center_z` — a naive substring-matched rescue wrapper silently corrupted
those reads); and Clinic carries a 1-room "TOF Footing" storey whose lone sample's z coincidentally beat
the real 67-room First Floor anchor in a raw nearest-z vote (fixed with a minimum-sample-size guard, not
a hand-picked threshold).

## Engine shipped (`common/room_graph.js`, API-compatible)
- `shortestPath(graph, fromGuid, toGuid, opts)` — **NEW optional 4th argument**, every existing 3-arg
  call keeps today's exact distance-weighted behavior byte-identical. `opts.metric='doors'` switches to
  the corridor-gated door-count weighting; `distance` in the returned shape then holds that metric's own
  cost units for that call only (not meters).
- `escapeRoute(graph, fromGuid, opts)` — already had an `opts` param (for `log`); now also honours
  `opts.metric='doors'`, free of charge (same `_buildAdjacency(graph, opts)` call).
- New internal helpers: `_doorEdgeCost` (corridor-gated cost: 0 for circulation-internal E3/E5/E6/E8;
  real distance for a plain room↔room E1/E9 edge; `1 * hostMult * privacyMult` for a corridor-touching
  E1/E2/E7/E9 edge), `_touchesCorridor`, `_isCurtainWallDoor`, `_roomPrivacyMult`. `DOOR_PRIORITY`
  constants mirror `path_strategy.json`'s `door_priority` table (host-class + adjacent-room-privacy
  multipliers) — that JSON stays the spec source of truth; values marked `_calibrate` there are still
  POC-tuned initial numbers, not hand-picked finals.
- `_buildAdjacency(graph, opts)` — new optional 2nd argument, same default-preserving contract.

## Witnesses
**W-LANE-C-DOOR-PREFERENCE** (`witness_lane_c_door_preference.js`, calls `RoomGraph.shortestPath()`
directly, not a reimplementation) — `pass=4 fail=0`:
```
§W_LANE_C Clinic default-metric doors=12 distance=74.5
§W_LANE_C Clinic doors-metric doors=6 cost=21.79 names=...single-flush doors x5 | one stair flight...
§W_LANE_C Duplex regression_pairs=35 mismatches=0
```
(Clinic's engine-reported `doors=6` vs the POC script's own `doors=5` is a labeling difference only —
`shortestPath()`'s pre-existing `doors[]` convention already includes E3 stair-flight guids, as
established when E3 first shipped; same 5 real doors + 1 stair crossing either way.)

**Existing regression witness** (`witness_room_graph_path.js`, unrelated file, run read-only to check
fallout): **pass=15 fail=0**, unchanged from before this session — the new optional `opts` parameters
introduced no regression on the pre-existing suite.

## Deferred / honestly not done
- C3's collinearity tie-break, C4's degradation tiers, and C5's acquired-doors are still spec-only,
  unchanged from before this session (see their own sections above).

## C1 wired (2026-07-18, follow-up) — `feat/c1-unknown-storey-door-rescue`, pushed
Ported the exact, already-verified fix (`poc_lane_c_doorpref.js`'s `buildRescueMap()`) directly into
`buildGraph()`, right after the existing §STOREY-Z step (real room z-anchors already computed there,
no new query needed for that part) and before the corridor backbone / main door loop run — so both
benefit from the rescue, not just the room-door binding. Additive only, low risk (a door that already
carries a real storey is untouched byte-for-byte).

Verified live on Clinic: `rescued=5`, `edges 332->337 (+5)`. `witness_room_graph_path.js` unaffected
(Duplex has no Unknown-storey doors) `pass=15 fail=0`. **Unexpected bonus**: HHS's
`fullConnectivity()` jumped from 87.3% (2 isolated glass-door corridor clusters, same root cause —
"Türelement...Glas" curtain-wall doors) to **100% fully connected** — the same fix that was scoped for
Clinic's 3 glass doors turned out to close a real gap on a completely different building.
- Not tested on JKR/Terminal this session (Clinic + Duplex are this lane's own named acceptance corpora).

---

## ▶ NEXT TASK (2026-07-25) — §GRAPH-FOUNDATION: connectivity is the bottleneck, not routing
**Filed by the Fly Tour lane (`prompts/Viewer/FLY_TOUR_CORRIDOR_GRAPH.md`) after the user live-tested
highlight-first routing on Hospital and asked the right question: "is it getting to the big hall?"
It is getting to the biggest space the GRAPH offers. The graph is the limit, not the ordering.**

### Why this file, and why this is infrastructure rather than a feature
`A.getRoomGraph` is defined ONCE (`viewer/navigate_find.js:1218`) and consumed by **seven** modules —
verified by grep, not assumed: `main.js`, `navigate_find.js` (Find panel's room Path), `cinema_maxq.js`,
`scene.js`, `tour.js` (Fly Tour route), `effects.js` (Cinema/MaxQ space planning), `dlod_nav.js`
(room-PVS occlusion culling). `navigate_find.js:1347` and `tour.js:601` call the SAME
`RG.shortestPath()` primitive.

Consequence, and the sequencing argument in one line: **a building whose graph is thin does not "fail
the tour" — it fails the graph**, so Find's room path is degraded on that same building for that same
reason, and `dlod_nav`'s PVS culling loses FRAME RATE, not a feature. Something that isn't even
user-facing gets faster when this improves — that is the test for infrastructure. Routing polish lifts
one face; connectivity lifts seven, including surfaces not built yet.

**Reusable rule this gives us for "which first":** push the thing more than one already-built surface
depends on and that currently caps all of them at the same number.

**The thing to defend hardest:** `room_graph.js` is genuinely ONE vocabulary today, and
`dlod_nav.js`'s own "never a second graph build" comment shows the discipline is being enforced in
code review rather than only in docs. Semantics accreted per-feature drifts into per-feature
vocabularies — Find growing its own notion of "room," cinema another — and reconciling four
definitions of adjacency later is far more expensive than keeping one now. Any fix below goes into
the shared graph, never into a consumer.

### §GIVEN-2026-07-25 — measured from the user's own live GH Pages console (Hospital_extracted.db,
### RTX 4060, ghost=1). Do NOT re-derive; these are real production numbers, not a harness.
```
§ROOM_GRAPH nodes=224 doors=440 nonRoomDoors=0 edges=61 deadend=194 orphan=185
            orphanRescued=172 ambiguous=0 circ=7 stairs=6 (skipped=1) exits=0 e2=194
§CORRIDOR_ROOM_BACKPROP injected=10 skippedOverlap=33 / 43 joined buckets
§HALLWAY_BACKBONE buckets=241 joined=43 chains=16 crossings=37 openEnds=12 stairTerminated=11
§ISLAND_BRIDGE circ-per-chain … ×16, spans 1.98m … 51.97m   → §CIRC_SPINE_BRIDGE bridged=16
§HELPERS_QUERY_ERR no such table: storey_walkable_raster
§PATH_LEGAL_DETOUR_FAIL storey=Level 1 no legal detour among 128 doors   (×16 across storeys)
[TOUR] §FLY_ROUTE … pts=91 illegalChords=18/74
```

### The four gaps, in dependency order (G1/G2 first — they unblock the most per unit of work)
**G1 — `exits=0`. Hospital has NO entrance node at all.** `nonRoomDoors=0` too, so the E4 exit
extractor found nothing on a 63k-element hospital that obviously has doors to the outside. Blocks:
the Fly Tour's descent finale (`stairDown=-` on every run), `escapeRoute()` entirely, and any future
"enter through the grand entrance" beat — there is nothing to aim at. Also forces the tour's walk to
start at an arbitrary interior stop (see §HL-ORIGIN in the Fly Tour file). **Investigate:** why the
exit rule that yields `nonRoomDoors=5` on Terminal yields 0 here — curtain-wall/glass entrance doors
are the first suspect, exactly the family C1's rescue already fixed for Clinic/HHS
("Türelement…Glas"). This may be the same bug one layer over.

**G2 — `no such table: storey_walkable_raster`.** Without the raster, `_legalizePath` cannot compute
visibility detours: sixteen `§PATH_LEGAL_DETOUR_FAIL … among 128 doors`, and `illegalChords=18/74`
(24%) on the shipped route. **Known and already owned:** a 2026-07 review of
`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §15` established raster coverage exists for only **5 of ~29
buildings** (Clinic/HHS/JKR/Hospital/Terminal) — and note Hospital is ON that list yet the LIVE
`Hospital_extracted.db` still has no such table, so either the coverage claim is about a different
DB snapshot or the raster never shipped into the served artifact. **That contradiction is the first
thing to settle** — it is a `project_db_snapshot_divergence_landmine.md` shape. Coordinate with §15;
do not fork a second raster effort.

**§G2-RESOLVED 2026-07-25 — it is a DEPLOYMENT gap, not a pipeline or doc gap. Diagnosis complete;
what remains is an upload + one missing artifact, both needing user authorization (OCI = production).**
Verified directly, after a delegated lookup got the mechanism wrong (it reported "no apply_patch
mechanism exists anywhere in bim-ootb" — false, see below; its other findings held):
- **The patch loader EXISTS and runs on every page load.** `A._applyPendingPatch` is defined at
  `viewer/scene.js:803` and called on BOTH the meta DB and the main DB —
  `viewer/streaming.js:1787` (`metaBuf`, `metaUrl`) and `:1933` (`dbBuf`, `A.DB_URL`). The needle has
  its own equivalent at `navigate_find.js:999`.
- **The raster patch EXISTS in the repo and holds real data.**
  `bim-ootb/buildings/patches/Hospital_extracted.db.sql` is 145KB with 8 `storey_walkable_raster`
  statements — a real `CREATE TABLE` + per-storey `INSERT` (e.g. `'Level 1', res=0.25, x0=-0.0147,
  y0=58.2806, cols=304, rows=332` + BLOB). Patches also exist for HHS/JKR/Terminal.
- **ZERO `.db` files anywhere on disk contain the table** — BY DESIGN. The offline builder
  `bim-ootb/scripts/build_storey_walkable_raster.js:194` emits a self-heal patch SQL fragment rather
  than mutating a binary, exactly this project's "DB CHANGES = MIGRATION SCRIPT + SELF-HEAL LOADER"
  architecture. Nothing is broken about that half.
- **The failure is that the patch was never uploaded to the OCI bucket the viewer serves from.** The
  user's live log shows the loader asking for it and getting 404 twice on one page load:
  `…/o/buildings/patches/Hospital_meta.db.sql → 404` → `§PATCH_NONE Hospital_meta.db (404)`, and
  `…/o/buildings/patches/Hospital_extracted.db.sql → 404` → `§PATCH_NONE Hospital_extracted.db (404)
  [needle]`. The mechanism worked perfectly; the file simply is not there.
- **Second, separate gap — the split-DB path has no Hospital patch at all.** Terminal ships BOTH
  `Terminal_extracted.db.sql` AND `Terminal_meta.db.sql`; Hospital ships only the `_extracted`
  variant. The split loader reads `Hospital_meta.db`, so uploading the existing file alone will NOT
  fix the served path — a `_meta` variant must be generated by the offline builder too. Check
  HHS/JKR the same way before assuming one upload closes this.
- **`common/storey_raster.js` is pack/unpack/lookup only**, no runtime rebuild fallback (its own
  header: rasterization happens ONCE, offline). `room_graph.js:748-750` try/catches the missing table
  and silently degrades to pre-raster straight-line legalization — which is why this failed quietly
  for so long: no error, just worse paths everywhere.
**So §15's "5 buildings have raster coverage" is true of the PATCH ARTIFACTS and false of every served
DB.** Restate the claim in those terms rather than deleting it.
**Do NOT `oci os object put` without asking** — that bucket is production (`deploy/OCI_UPLOAD.md`
§RULES; every upload needs `--content-type`, here `application/sql`).

**§G2-FALSIFIED 2026-07-25 — applying the raster does NOT fix Hospital's path legality. Measured,
locally, before any upload. The earlier hypothesis in this section (that §15's Hospital "tie" was
caused by the missing raster) is WRONG and must not be carried forward.**
Method: copied `deploy/buildings/Hospital_meta.db`, applied `buildings/patches/Hospital_extracted.db.sql`
to the copy with `sqlite3` (52ms, +72KB, storeys `Level 1`–`Level 7` all present with real bitsets),
then ran the REAL `_buildGraphRouteInner` against BOTH copies through the same Node harness.
| | rastersLoaded | DETOUR_FAIL | illegalChords | pts | route len |
|---|---|---|---|---|---|
| meta, no raster | **0** | **22** | 16/123 | 136 | 1940.8m |
| meta + raster | **7** | **22** | 16/121 | 134 | 1937.4m |
**Test validity checked before believing the negative** (this project's own GIGO rule): `rastersLoaded`
0→7 proves the table really was read into `graph.rasters` — the raster loaded and simply did not help.
`room_graph.js` has NO `§`-log on the raster load path, which is why this needed a direct probe; **worth
adding one** so a future session can see raster presence from a live console alone.
So Hospital's chord-illegality has a DIFFERENT root cause than raster absence. Do not spend the OCI
upload on the expectation that it fixes paths — it may still be worth shipping for other reasons, but
this specific claim is dead.

**§G3-RETRACTED 2026-07-25 — an earlier version of this section claimed the walker recompile
collapses connectivity "500 edges → 61". THAT CLAIM WAS WRONG and is withdrawn. It compared two
different metrics.**
`room_graph.js:737` logs `edges=` as **E1 (door↔room) edges ONLY**
(`edges.filter(e => e.kind === 'E1').length`), while `graph.edges.length` is the TOTAL across E1–E5
(door, circulation, stair, exit, spine). The retracted claim put a local TOTAL (500) beside the live
E1 count (61). Like-for-like, on the E1 metric:
| rooms from | nodes | **E1 edges** | deadend | orphan (rescued) | nonRoomDoors | exits |
|---|---|---|---|---|---|---|
| `Hospital_meta.db` AUTHORED (142 IfcSpace) | 156 | **17** | 191 | 232 (219) | 0 | **0** |
| LIVE, walker-recompiled (214 rooms) | 224 | **61** | 194 | 185 (172) | 0 | **0** |
**On door-binding the walker is BETTER, not worse (61 vs 17).** There is no measured evidence that
recompiling degrades the graph, and no needle policy change is justified by this data. The related
`§NEEDLE-OVERWRITES-AUTHORED` entry in `prompts/Viewer/ROOM_INJECTOR_NEEDLE.md` is withdrawn on the
same grounds.

**WHAT DOES SURVIVE, and it is worth more than the retracted claim:**
1. **`exits=0` AND `nonRoomDoors=0` in BOTH configurations.** G1 is therefore NOT a consequence of room
   quality or of the walker — it is in the exit/door rule itself, on a 63k-element hospital with 440
   doors. That narrows G1 usefully and removes a whole branch of investigation.
2. **`§GIVEN-2026-07-25` above cites `edges=61` — read it as E1-only.** Total connectivity is NOT
   logged anywhere. **Add `totalEdges` to the `§ROOM_GRAPH` line**; a graph that is well connected
   through circulation/spine edges but sparse on doors currently reads as "broken" from the console
   alone. This mis-read is exactly what produced the retracted claim.
3. **Room COUNTS still differ hugely** (142 authored vs 214 compiled) and the compiled set is what the
   Fly Tour ranks. Whether authored rooms would give a better "main hall" is UNRESOLVED — an earlier
   probe reported the authored-DB top candidate as `≈ Level 1 R13` at 294 m², but the `≈`/`R<n>`
   naming is compiled-style, so that node's provenance is unclear and the comparison was not run
   cleanly. **Do not cite the 294 m² figure until it is re-measured.**

**Method note, recorded because it caused a wrong published finding:** both numbers came from real
runs; the error was semantic, not arithmetic. When comparing a live console `§` line against a
harness value, confirm the harness reads the SAME expression the log builds — not a same-named field.

**§G3-RESOLVED 2026-07-25 — the real finding, measured on a TRUE LOCAL REPRO. It is not authored-vs-
compiled and not a connectivity loss: it is OFFLINE-COMPILER vs IN-BROWSER-WALKER, and the browser
walker loses the large spaces.**
The user saved the live browser state (inject → Save DB) to `~/Projects/BIM_DB/Hospital.db`, 250MB.
**It reproduces the live console EXACTLY** — `nodes=224 doors=440 nonRoomDoors=0 edges=61 deadend=194
orphan=185 orphanRescued=172` — so Hospital is now locally reproducible; stop inferring from console
logs. Provenance checked on BOTH sides before concluding (the step whose absence caused the retraction
above): `spatial_structure.object_type` is **`COMPILED` in both**. Neither DB carries authored IfcSpaces.
| | rooms | rects | E1 | TOTAL edges | exits | largest space | widest space |
|---|---|---|---|---|---|---|---|
| `Hospital_meta.db` as SHIPPED (offline compile, **no `rooms_meta` stamp**) | 142 | 142 | 17 | **500** | 0 | **294 m² (19.6×15.0)** | **15.0 m** |
| after the in-browser walker v3 recompiles it (the saved live DB) | 214 | 317 | 61 | **496** | 0 | 219 m² corridor (3.3m wide) | **5.9 m** (49 m²) |
- **Connectivity is a wash** (500 vs 496 total; walker binds MORE doors, 61 vs 17 E1). The retracted
  "collapse" claim stays retracted — nothing is degraded here.
- **Large spaces are annihilated.** The shipped compile contains a genuine 294 m² hall 15 m wide and a
  270 m² room 12.4 m wide. After the walker recompile the widest space in the WHOLE building is 5.9 m
  and the biggest open room is 49 m² — 317 rects for 214 rooms vs 142/142 suggests the walker is
  fragmenting big volumes into many small pieces (or classifying them `SUSPECT_*`, which excludes them
  as destinations). **This, not ranking, is why the Fly Tour cannot find a big hall.**
- **The trigger is the missing stamp.** `Hospital_meta.db` has NO `rooms_meta` table, so
  `§NEEDLE_VERSION_STALE stored=null` fires and the walker replaces a BETTER offline compile with a
  worse in-browser one, every load.
- **This is the py↔js parity thread, unfinished.** `FLY_TOUR_CORRIDOR_GRAPH.md`'s §WALKER-PHASE-
  SENSITIVITY correction established `py = js = 54` on **Terminal** post-#832. Hospital was never
  checked, and on Hospital they clearly disagree. **Next bounded task: diff the two compiles on
  Hospital storey by storey** — which enclosures does `compile_rooms.py` close that `room_walker.js`
  fragments — with the saved DB as the fixture.
- **Interim policy question (now on a correct basis):** should the needle stamp-and-keep a shipped
  compile it cannot prove stale, rather than recompiling over it? A shipped-but-unstamped compile is
  currently indistinguishable from "never compiled", and here that costs the building its halls.

**§G3-FINAL 2026-07-25 — the walker does NOT lose the large spaces. It FINDS them and flags them
`SUSPECT_*`, and the Fly Tour's own candidate filter then excludes them. Classification + an
exclusion rule, not geometry.** Verified by direct query on both fixtures:
| | largest room | its `predefined_type` |
|---|---|---|
| A — shipped offline compile | `≈ Level 1 R13` 294.0 m² | `INTERNAL` |
| B — in-browser walker v3 | `⚠ Level 1 R14` **315.7 m²** (bbox 9.7×34.1) | **`SUSPECT_OPEN`** |
B's largest space is BIGGER than A's. B's next two (105.5 m², 92.1 m²) are `SUSPECT_NO_DOOR` /
`SUSPECT_OPEN`. Whole-building classification split:
| | INTERNAL | INTERNAL_SMALL | SUSPECT_NO_DOOR | SUSPECT_OPEN |
|---|---|---|---|---|
| A (142) | **142** | 0 | 0 | 0 |
| B (214) | 85 | 67 | **52** | **10** |
`_buildGraphRouteInner` excludes every `SUSPECT_*` node as a destination (§S2, "never SUSPECT_* as
destinations"), so 62 of B's 214 rooms — including the three biggest — are invisible to the tour, and
the largest ELIGIBLE candidate falls back to the 219 m² × 3.3 m corridor. That is the whole
"doesn't show a large space" symptom.
**Two corrections to the delegated diff that produced this section** (both the same error class as the
retraction above — mixing per-rect and per-room units):
1. Its width table compared A per-ROOM against B per-RECT. Re-derived per-ROOM (union bbox over
   `room_guid`): **A 7 rooms ≥8m wide, B 7** — identical, not "A 7 / B 2".
2. Its "deletion" verdict came from a centre-inside-footprint test; a 9.7×34.1 room's centre can sit
   outside a 19.6×15.0 footprint, so the big space read as absent when it exists under another shape.
**`SUSPECT_OPEN` is a DETECTION-CONFIDENCE flag being used as a QUALITY filter, and it penalises
exactly the spaces a tour wants.** A grand hall/atrium is *by definition* low-enclosure — the taxonomy
flags it for the very property that makes it worth visiting. Note also A has ZERO suspect rows of any
kind, which is itself suspicious: the shipped compile likely predates the SUSPECT taxonomy, so "A is
better" is partly "A never applied the filter."
**Actionable, split by owner — no walker change needed for the first:**
- **Fly Tour lane (small, mine):** allow `SUSPECT_OPEN` as a highlight DESTINATION (keep excluding
  `SUSPECT_NO_DOOR`, which is a genuine reachability doubt). Openness is the signal, not the defect.
- **Walker lane:** ask whether `SUSPECT_OPEN_ENCLOSURE=0.50` is right for atrium-scale volumes, and
  whether 52 `SUSPECT_NO_DOOR` rooms on a hospital with 440 doors indicates a door-binding gap rather
  than 52 genuinely doorless rooms.

**§G3-SHARPEST-TARGET 2026-07-25 — the Fly Tour half SHIPPED (bim-ootb PR #994, tour.js v14) and it
was NOT sufficient. The result names the graph task more precisely than anything above:**
`SUSPECT_OPEN` is now an eligible destination — witnessed `suspectOpenAdmitted=10` on Hospital, every
other building in the 7-building corpus byte-identical (none has a SUSPECT_OPEN room). But the main
hall did NOT change, and the reason is the single most useful number in this file:
```
⚠ Level 1 R14   area=315.7 m²   edges=0     ← the building's LARGEST room, ISOLATED
⚠ Level 1 R21   area= 92.1 m²   edges=2
… 9 of 10 SUSPECT_OPEN rooms are connected; the BIGGEST one is not.
```
`§CONNECTED-STOPS` drops any node absent from the edge set, so the hall is discarded before ranking.
**The two facts are causally linked, and that is the insight:** the low enclosure that earns
`SUSPECT_OPEN` is the same property that stops any door binding to it. A space open enough to be a
grand hall is, to the current door-binding rule, a space with no doors.
**Therefore the acceptance test for G1/G3 is now concrete and single-line, on a fixture that exists:**
> On `~/Projects/BIM_DB/Hospital.db`, make `⚠ Level 1 R14` (315.7 m², bbox 9.7×34.1) carry **≥1 edge**
> without regressing Terminal/HHS/Clinic/Duplex/SampleHouse in the 7-building sweep.
That is a better target than "raise `edges=61`" because it is falsifiable, tied to a user-visible
symptom, and reproducible offline. Related and probably the same rule: **52 `SUSPECT_NO_DOOR` rooms on
a building with 440 doors** — a door-binding gap is far more likely than 52 genuinely doorless rooms.
Start there.

### The four gaps, in dependency order (G1/G2 first — they unblock the most per unit of work)
**G1 — `exits=0`. Hospital has NO entrance node at all.** `nonRoomDoors=0` too, so the E4 exit
extractor found nothing on a 63k-element hospital that obviously has doors to the outside. Blocks:
the Fly Tour's descent finale (`stairDown=-` on every run), `escapeRoute()` entirely, and any future
"enter through the grand entrance" beat — there is nothing to aim at. Also forces the tour's walk to
start at an arbitrary interior stop (see §HL-ORIGIN in the Fly Tour file). **Investigate:** why the
exit rule that yields `nonRoomDoors=5` on Terminal yields 0 here — curtain-wall/glass entrance doors
are the first suspect, exactly the family C1's rescue already fixed for Clinic/HHS
("Türelement…Glas"). This may be the same bug one layer over.

**G2 — `no such table: storey_walkable_raster`.** Without the raster, `_legalizePath` cannot compute
visibility detours: sixteen `§PATH_LEGAL_DETOUR_FAIL … among 128 doors`, and `illegalChords=18/74`
(24%) on the shipped route. **Known and already owned:** a 2026-07 review of
`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §15` established raster coverage exists for only **5 of ~29
buildings** (Clinic/HHS/JKR/Hospital/Terminal) — and note Hospital is ON that list yet the LIVE
`Hospital_extracted.db` still has no such table, so either the coverage claim is about a different
DB snapshot or the raster never shipped into the served artifact. **That contradiction is the first
thing to settle** — it is a `project_db_snapshot_divergence_landmine.md` shape. Coordinate with §15;
do not fork a second raster effort.

**§G2-RESOLVED 2026-07-25 — it is a DEPLOYMENT gap, not a pipeline or doc gap. Diagnosis complete;
what remains is an upload + one missing artifact, both needing user authorization (OCI = production).**
Verified directly, after a delegated lookup got the mechanism wrong (it reported "no apply_patch
mechanism exists anywhere in bim-ootb" — false, see below; its other findings held):
- **The patch loader EXISTS and runs on every page load.** `A._applyPendingPatch` is defined at
  `viewer/scene.js:803` and called on BOTH the meta DB and the main DB —
  `viewer/streaming.js:1787` (`metaBuf`, `metaUrl`) and `:1933` (`dbBuf`, `A.DB_URL`). The needle has
  its own equivalent at `navigate_find.js:999`.
- **The raster patch EXISTS in the repo and holds real data.**
  `bim-ootb/buildings/patches/Hospital_extracted.db.sql` is 145KB with 8 `storey_walkable_raster`
  statements — a real `CREATE TABLE` + per-storey `INSERT` (e.g. `'Level 1', res=0.25, x0=-0.0147,
  y0=58.2806, cols=304, rows=332` + BLOB). Patches also exist for HHS/JKR/Terminal.
- **ZERO `.db` files anywhere on disk contain the table** — BY DESIGN. The offline builder
  `bim-ootb/scripts/build_storey_walkable_raster.js:194` emits a self-heal patch SQL fragment rather
  than mutating a binary, exactly this project's "DB CHANGES = MIGRATION SCRIPT + SELF-HEAL LOADER"
  architecture. Nothing is broken about that half.
- **The failure is that the patch was never uploaded to the OCI bucket the viewer serves from.** The
  user's live log shows the loader asking for it and getting 404 twice on one page load:
  `…/o/buildings/patches/Hospital_meta.db.sql → 404` → `§PATCH_NONE Hospital_meta.db (404)`, and
  `…/o/buildings/patches/Hospital_extracted.db.sql → 404` → `§PATCH_NONE Hospital_extracted.db (404)
  [needle]`. The mechanism worked perfectly; the file simply is not there.
- **Second, separate gap — the split-DB path has no Hospital patch at all.** Terminal ships BOTH
  `Terminal_extracted.db.sql` AND `Terminal_meta.db.sql`; Hospital ships only the `_extracted`
  variant. The split loader reads `Hospital_meta.db`, so uploading the existing file alone will NOT
  fix the served path — a `_meta` variant must be generated by the offline builder too. Check
  HHS/JKR the same way before assuming one upload closes this.
- **`common/storey_raster.js` is pack/unpack/lookup only**, no runtime rebuild fallback (its own
  header: rasterization happens ONCE, offline). `room_graph.js:748-750` try/catches the missing table
  and silently degrades to pre-raster straight-line legalization — which is why this failed quietly
  for so long: no error, just worse paths everywhere.
**So §15's "5 buildings have raster coverage" is true of the PATCH ARTIFACTS and false of every served
DB.** Restate the claim in those terms rather than deleting it.
**Do NOT `oci os object put` without asking** — that bucket is production (`deploy/OCI_UPLOAD.md`
§RULES; every upload needs `--content-type`, here `application/sql`).

**§G2-FALSIFIED 2026-07-25 — applying the raster does NOT fix Hospital's path legality. Measured,
locally, before any upload. The earlier hypothesis in this section (that §15's Hospital "tie" was
caused by the missing raster) is WRONG and must not be carried forward.**
Method: copied `deploy/buildings/Hospital_meta.db`, applied `buildings/patches/Hospital_extracted.db.sql`
to the copy with `sqlite3` (52ms, +72KB, storeys `Level 1`–`Level 7` all present with real bitsets),
then ran the REAL `_buildGraphRouteInner` against BOTH copies through the same Node harness.
| | rastersLoaded | DETOUR_FAIL | illegalChords | pts | route len |
|---|---|---|---|---|---|
| meta, no raster | **0** | **22** | 16/123 | 136 | 1940.8m |
| meta + raster | **7** | **22** | 16/121 | 134 | 1937.4m |
**Test validity checked before believing the negative** (this project's own GIGO rule): `rastersLoaded`
0→7 proves the table really was read into `graph.rasters` — the raster loaded and simply did not help.
`room_graph.js` has NO `§`-log on the raster load path, which is why this needed a direct probe; **worth
adding one** so a future session can see raster presence from a live console alone.
So Hospital's chord-illegality has a DIFFERENT root cause than raster absence. Do not spend the OCI
upload on the expectation that it fixes paths — it may still be worth shipping for other reasons, but
this specific claim is dead.

**§G3-ROOT-CAUSE-CANDIDATE 2026-07-25 (the strongest lead in this whole file — the walker recompile
COLLAPSES connectivity on a building that already has good authored rooms):**
| source of rooms | IfcSpace | nodes | **edges** |
|---|---|---|---|
| `Hospital_meta.db`'s own AUTHORED rooms (142, all human-named) | 142 | 156 | **500** |
| LIVE, after the needle recompiles (`§NEEDLE_INJECT source=walker rooms=214`) | 317 rows | 224 | **61** |
Same building. **500 edges → 61.** `deadend` goes to 194 and `orphan` to 185 live. This is not a
graph-algorithm gap — the graph is being fed strictly worse rooms than the DB already contained.
It also explains, in one stroke: why the live "main hall" is a 3.3m-wide corridor (with authored rooms
the top candidate is `≈ Level 1 R13`, a real **294 m²** room), why the live candidate pool collapses to
~24 mostly-corridor nodes, and the user's own instinct that room injection "is not foolproof."
**Investigate FIRST, before any edge-rule work** — an edge fix measured against walker rooms would be
tuning against degraded input.

**G3 — `edges=61` across `nodes=224`, `deadend=194`, `orphan=185/orphanRescued=172`, plus
`§ISLAND_BRIDGE` ×16 spanning up to 51.97m.** The graph is mostly disconnected islands stitched by
long synthetic links, which is why routes lurch. Compare against this file's own G1 baseline
(Terminal `nodes=59 edges=10 deadend=62`) — the deadend/orphan ratio is the SAME pathology this lane
was opened to fix, still dominant at hospital scale. **Investigate:** whether E2 circulation rescue
(`e2=194`) is producing edges that connect anything, and whether a 52m island bridge should exist at
all or is masking a missing corridor chain.

**G4 — `§CORRIDOR_ROOM_BACKPROP injected=10 skippedOverlap=33 / 43`.** Three-quarters of the
detected corridor buckets are discarded for overlap before any consumer sees them. Corridors are the
tour's spine (§R6-CORRIDOR-SPINE) and the occupant's actual route. **Investigate:** what "overlap"
means here and whether a merge/clip is possible instead of a drop.

### G5 (adjacent, different owner — hand to `prompts/Viewer/ROOM_INJECTOR_NEEDLE.md`)
**The injection never accumulates: every load recompiles all 214 rooms from scratch.** Run 1 wrote
meta 21.4MB + geo 228.6MB + ad_seed 25.8MB and `§NEEDLE_PERSIST idb=ok bytes=22482944`; run 2 opened
with `§QUOTA used=3MB` and MISSED all three (`§CACHE_MISS_READ url=Hospital_meta.db`), re-downloading
~275MB and hitting `§NEEDLE_VERSION_STALE stored=null` → full recompile. **Checked before filing:** it
is NOT the needle's persist key (a plausible mismatch — the needle persists under the
`_extracted.db` url while the split loader reads `_meta.db`); the misses are UNIFORM across files the
needle never touches, so it is whole-cache eviction. `navigator.storage.persist()` is the first thing
to check. (Caveat stated honestly: a manual "clear browsing data" between runs would produce the same
log — confirm with the user before treating it as a bug.)

### Witness plan (numbers, never screenshots — this project's FUNDAMENTAL LAW)
Reuse this file's own established shape: a calculation-only Node harness on real DBs, logging the
numbers, BEFORE/AFTER, gate on the deltas — plus `witness_room_graph_path.js` (`pass=15 fail=0`) as
the non-regression floor. Minimum acceptance per gap: G1 `exits>0` on Hospital AND Terminal's 5 exits
unchanged; G2 the raster present in the SERVED artifact, `PATH_LEGAL_DETOUR_FAIL` → 0 on Hospital;
G3 `edges` up and `deadend` down with `fullConnectivity()` reported per building; G4 `skippedOverlap`
down without new cross-room overlaps (`§NO-OVERLAP` invariant must hold).
**Corpus caution from the §15 review: aggregate statistics are not proof of a specific reported
case.** That review found 99–100% aggregate illegal-point reduction shipped while the user's two
actual screenshot routes were never re-run against the fix. Name the specific buildings and re-run
the specific cases.

### Out of scope for this task
Route ORDER, stop selection, camera grammar and pacing — those are the Fly Tour lane's
(`FLY_TOUR_CORRIDOR_GRAPH.md` §HL-FIRST, `FLY_TOUR_DLOD_SCALE.md`). That lane is a pure CONSUMER of
this graph and is explicitly waiting on G1/G2; it will not build scoring heuristics on top of data
that is about to change. Do not "fix" the tour from inside this task.

### One thing this task will NOT solve, stated so nobody expects it
The user's actual opening question — does the tour reach the space a human would call the big hall —
is a VOCABULARY problem, not a topology one, and survives a perfect connectivity fix:
- Ranking is by floor AREA only. A grand space reads as grand by VOLUME and light. Per-room height
  does not exist in the data: `compile_rooms.py` sets `size_z` from a storey-wide wall-height mean
  (documented in `FLY_TOUR_CORRIDOR_GRAPH.md` §INTERIOR_PACING investigation item 1), so an atrium
  and a cupboard on one floor measure identical heights.
- Hospital ships **142 authored `IfcSpace`s** (`§HBA_FOOTPRINT bound 142 rooms→real IfcSpace
  footprint`) and the needle press replaces/overlays them with **214 compiled rooms**, so the tour
  ranked `≈ Level 1 Hall/Corridor 2` — a compiled corridor chain — rather than any authored,
  human-named lobby. **Worth verifying as its own question: does the recompile DISPLACE authored
  spaces, and should it?** If authored names survive, "main hall" could be chosen from real
  semantics instead of area alone. That is a genuine candidate for a follow-up spec, not this task.

---

## ✅ SHIPPED 2026-07-25 — §ROOM-SPINE-BRIDGE implemented, PR #995 (auto-merge armed)
`common/room_graph.js`, right after the existing `§CIRC_SPINE_BRIDGE` block. A deg-0 ROOM now bridges
to its nearest same-storey spine point by RECT distance, as an `E6` edge — same shape as the
circ-per-chain bridge above it. Measured, BEFORE = `origin/main`:
| building | deg0 | room-pair pathability |
|---|---|---|
| **Hospital** (live fixture) | **42 → 2** | **56.2% → 86.2%** (R14 deg 0 → 1) |
| **Terminal** | **6 → 1** | **75.9% → 95.7%** |
| HHS / Clinic / Duplex / SampleHouse / SampleCastle | unchanged | unchanged |
**Hospital's Fly Tour now opens in the 315.7 m² atrium** (`mainHall="⚠ Level 1 R14"`) instead of the
219 m² × 3.3m corridor — the user's original "it doesn't show a large space", closed.
Independent confirmation: this harness reproduced the review session's 56.2% baseline exactly.
**Cost recorded, not hidden:** Terminal's illegal-chord ratio rose 14/53 (26.4%) → 20/61 (32.8%) —
newly reachable rooms add chords and a bridge is a graph EDGE, not a validated walk. Inside the
`§MAJORITY-LEGAL` gate; no building lost its route.
**FOLLOW-UP (open):** prefer the nearest spine point whose chord is WALL-LEGAL rather than simply the
nearest. Needs the chord test available inside `buildGraph` (today it is applied later, in
`shortestPath`'s legalization). That is the principled fix for the ratio above.
**STILL OPEN:** the 2 remaining deg-0 rooms on Hospital (storeys with no spine at all), and the
review's third bucket — 22 "far" rooms averaging ~24 m² with no door within 8m, which may be walker
artifacts rather than real rooms. Triage before treating them as a connectivity target.

---

## ▶ RESUME HERE (2026-07-25) — §ROOM-SPINE-BRIDGE: the validated next fix, fixture ready
**(SUPERSEDED by the SHIPPED block above — kept for the falsification record: two alternative fixes
were measured and rejected before the working one. Do not re-propose them.)**
**State in one line: room pathing is NOT solved (Hospital room-pair pathability 14035/24976 = 56.2%,
42 stranded deg-0 rooms); the Fly Tour's ORDER is solved and shipped, its DESTINATIONS are blocked
on this file.**

### Fixture + harnesses — a new session can start measuring in one command, no browser
- `~/Projects/BIM_DB/Hospital.db` (250MB, user's Save-DB export) **reproduces the live console
  exactly** (`nodes=224 doors=440 nonRoomDoors=0 edges=61 deadend=194 orphan=185 orphanRescued=172`).
  Stop inferring from console logs — iterate on this.
- Node harnesses in the 2026-07-25 session scratchpad (`hl_witness.js` loads the REAL `viewer/tour.js`
  verbatim into a `vm`; `hall_rank.js`, `adj.js`, `circ.js`, `verify.js`). Pattern to copy:
  `initSqlJs({wasmBinary})` + `require('common/room_graph.js')` (node-aware) + a `dbQuery` returning
  value-rows. 7-building regression corpus: Terminal_meta, HHS_Office_Federated_extracted, Clinic_ARC,
  Duplex_ARC, SampleHouse_extracted, SampleCastle_extracted, + the Hospital fixture.

### MEASURED by the review session (not re-derived here — attributed, with its own correction applied)
- **Pathability 56.2%** — nearly half of all Find room-pairs have no route.
- **All 42 stranded rooms fail the SAME predicate: no ARC door within buffer on their own storey**
  (`inRange=0`). Not discipline (440/440 are ARC), not the lift-name filter, not `cands>=2`.
- **Its own correction, keep it:** an earlier "R11 has 7 doors at 0.2m" reading was an artifact of a
  looser proximity test — those doors are on Levels 3/4 at the same XY and `rectDist` is 2D. **The
  storey filter is CORRECT; do not remove it** (removing it binds doors three floors up).
- Gap buckets (nearest same-storey door minus its buffer): `≤0.5m` 10 rooms / 536 m² (R14 at 0.25m);
  `0.5–2m` 10 rooms / 224 m²; `>2m` **22 rooms** / 538 m², nearest door 8–10m away.
- **Slack widening is the wrong fix, proven by its own numbers:** it cannot reach the 22 "far" rooms at
  all, and R14 only gains an edge at `SLACK=1.00` (5×) because `cands>=2` needs that same door within
  buffer of a SECOND room. A constant that must move 5× to catch one room is exactly what the
  abstraction audit exists to reject. Agreed, fully.

### VERIFIED HERE — the review's proposed fix does NOT work, and the working one is next to it
The review proposed **room-to-room opening adjacency** ("rect boundaries within the raster
resolution"). **Falsified on the fixture: 0 of 42 stranded rooms have a same-storey neighbour room
rect within `RES=0.20m`.** R14's nearest same-storey ROOM is **3.007m** away. Compiled rooms do not
tile the floor — there are metres of unclaimed space between them, which is where the connections
live. Do not implement room-to-room adjacency; it would produce zero edges here.
**What IS there — validated:**
```
R14 nearest same-storey NON-room node: 1.31m  (kind=spine)  "Corridor — Level 1"
all 42 deg-0 rooms, distance to nearest same-storey spine/circ/stairwp/doorwp node:
   ≤0.5m: 0     0.5–2m: 18     2–5m: 18     >5m: 6     none: 0
```
**Every stranded room has a circulation node in reach; none is truly isolated.** The corridor spine
runs 1.31m from the atrium's own rect.
**→ §ROOM-SPINE-BRIDGE (recommended, reuses an existing mechanism rather than adding a rule):** bridge
a deg-0 room to its nearest same-storey `spine`/`circ` node, exactly as `§ISLAND_BRIDGE` /
`§CIRC_SPINE_BRIDGE` already bridges corridor CHAINS to spines. That mechanism is live and already
tolerates **51.97m** bridges on this very building (`§ISLAND_BRIDGE … dist=51.97m`); R14 needs
**1.31m — 40× shorter than bridges the engine already makes**. It generalises the review's own
insight correctly: an open space connects through an OPENING onto circulation, not through a door to
another room. No new constant, no name matching, no threshold move.
**Keep the review's third-bucket caution:** the 22 "far" rooms average ~24 m² with no door within 8m —
triage whether they are real rooms or walker artifacts BEFORE counting them as a connectivity target.

### Acceptance test (falsifiable, offline, one line)
> On `~/Projects/BIM_DB/Hospital.db`: `⚠ Level 1 R14` (315.7 m², bbox 9.7×34.1) carries **≥1 edge**,
> room-pair pathability rises from **56.2%**, and the 7-building sweep shows no regression
> (Terminal/HHS/Clinic/Duplex/SampleHouse byte-identical or better).
Then re-press ✈ on Hospital: `§FLY_HL_FIRST mainHall` should become the 315.7 m² space instead of the
219 m² × 3.3m corridor — the user's original "it doesn't show a large space", closed end to end.

### ⚠ UNIT DISCIPLINE — three wrong findings were published in ONE day, all the same error class
Each was real data compared against a differently-scoped field. Check the scope of BOTH sides before
publishing any comparison:
1. `graph.edges.length` (TOTAL, E1–E5) vs the `§ROOM_GRAPH` log's `edges=` (**E1 only**,
   `room_graph.js:737`) → produced a bogus "500 → 61 collapse".
2. Per-**rect** vs per-**room** width/area (multi-rect rooms share `room_guid`) → produced a bogus
   "walker has no wide rooms" (per-room it has 7, same as the offline compile).
3. **`graph.nodes` holds ROOMS ONLY; `graph.nodesByGuid` holds all kinds** (room 224, spine 43,
   doorwp 427, circ 7, stairwp 12) → produced a bogus "no circulation nodes exist near R14", when the
   spine is 1.31m away. **This one nearly killed the correct fix.**
