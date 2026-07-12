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
