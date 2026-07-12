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
