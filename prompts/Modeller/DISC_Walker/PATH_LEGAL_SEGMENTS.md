<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# PATH LEGAL SEGMENTS — no chord through the void (2026-07-13)

```
# ⚠ DO NOT REMOVE
SCOPE: fix the RENDERED path taking straight-line shortcuts across open air on concave buildings
(user screenshot: HHS U-shape, R18→R31, the chord crosses the courtyard). The GRAPH and its hops
are CORRECT — do not change which rooms/doors a route uses; change only the polyline geometry of
same-storey segments. POC-gate FIRST (calculation-only, real HHS data) before touching the engine.
API-COMPATIBLE: buildGraph()/shortestPath() signatures unchanged; navigate_find.js consumes the
same result shape (path entries with cx/cy/cz) — it must need ZERO edits. Every claim needs a
§-log line. Read the log after every run. Commit locally, push, PR with auto-merge — the push
pause is lifted; localhost witness BEFORE the PR.
If the POC shows the walkable-space definition below does not separate legal from illegal segments
on the real data (misfires on >0 of the named control cases), STOP and report the numbers — do not
improvise a different geometric definition solo; that decision goes back to the coordinator.
```

## §GIVEN — measured, do not re-derive
- **G1 — the defect, live:** HHS (`buildings/HHS_Office_Federated_extracted.db` + its
  `buildings/patches/…sql` self-heal), Room→Path, `≈ Level 1 R18` → `≈ Level 1 R31`: route is
  CORRECT (3 doors, 86.2m) but the drawn polyline between same-storey door waypoints is a straight
  chord across the U-shaped courtyard (open air). Screenshot in the session record, 2026-07-13.
- **G2 — where polylines are assembled:** `common/room_graph.js` `shortestPath()` returns `path`
  entries carrying cx/cy/cz per hop (door waypoints, stair waypoints `::lo/::hi`, circ hops using
  real door/stair positions). The Viewer draws straight lines between consecutive entries
  (`viewer/navigate_find.js` `_drawPathHighlight`, no interpolation). So legality is decided
  ENTIRELY by which intermediate points shortestPath emits — the fix lives in room_graph.js only.
- **G3 — walkable space, the definition to validate:** a same-storey segment is LEGAL iff every
  sampled point lies inside the union of (a) this storey's room rects (`spatial_structure`
  IfcSpace rows, multi-rect aware via room_guid) and (b) this storey's floor-slab footprints
  (`elements_meta m JOIN element_transforms t`, `m.ifc_class LIKE 'IfcSlab%'`, z within
  [storey_z − 2, storey_z + 1] — wall-center storey z sits above the slab, see PR #763's
  gap-relative note). Intuition: you can walk where there is floor. The courtyard void has no
  upper-storey slab; the wings do.
- **G4 — detour mechanism:** visibility graph over this storey's DOOR CENTERS (they already exist
  in the graph build) + the two segment endpoints: edge between two points iff the straight
  segment between them is legal per G3 (sample @0.25m). Dijkstra on that small graph replaces the
  single chord with a legal polyline. Doors are natural corridors' waypoints; no new geometry is
  invented.
- **G5 — controls (must not change):** Duplex any same-unit path (convex, all chords already
  legal — polylines must be IDENTICAL before/after, byte-compare the waypoint lists);
  Terminal `≈ Aras 01 R1`-family paths (its wings are convex enough that most chords are legal —
  count changed polylines, expect few); SampleCastle 00→03 (PR #763's 6-hop path — the stair hops
  must be untouched, only same-storey sub-segments may gain waypoints).
- **G6 — harness:** localhost :8901 serves /tmp/wt-terminal-rooms; headless example
  `scratchpad/e2e_viewer_terminal3.js` (session scratchpad path in that file's header); witnesses
  `witness_room_graph_path.js` (15/15) + `witness_occupant_pathfinder.js` (full pass) must stay
  green.

## POC GATE (first, calculation-only, no engine edits)
Node script against real HHS db (+patch applied to a scratch copy):
1. Build the graph, compute the R18→R31 route, extract its same-storey chords.
2. For each chord: sample @0.25m, classify each point inside/outside G3's walkable union; log
   `§POCLEG chord=<a>-><b> len=<m> illegal_pts=<n>/<N>`. The courtyard chord MUST classify
   illegal (>0 outside points) and Duplex's chords MUST classify 0 — if either fails, STOP (see
   preamble).
3. Build G4's visibility detour for the illegal chord; log the detour waypoint count + length
   `§POCLEG detour hops=<n> len=<m> illegal_pts=0`. Expect longer than 86.2m — that is correct,
   the chord was a lie.

## Implementation (after the gate)
- All inside `common/room_graph.js`: shortestPath()'s same-storey segment assembly gains the
  legality test + detour insertion. ES5, dual-mode, defensive table probes (missing slabs table ⇒
  rooms-only union; zero walkable data ⇒ current chord behavior byte-identical, log
  `§PATH_LEGAL_SKIP no walkable data`).
- Extend the existing §-line: ` legalized=<n_segments> detoured=<n>` — never remove fields.
- Cache-bust: `viewer/main.js` room_graph.js `?v=` +1 (check current value first, another PR may
  have bumped it).

## WITNESS PLAN
- **W-LEG-POC**: the gate numbers above.
- **W-LEG-HHS-LIVE**: localhost, real Viewer, R18→R31 — §-log shows detoured>0 and the E2E asserts
  every sampled polyline point is inside the walkable union (assert in page via APP.dbQuery — no
  screenshot judgment needed).
- **W-LEG-CONTROLS**: Duplex polyline byte-identical; SampleCastle 00→03 still 6 hops with stair
  waypoints intact; both witness scripts green.
- Append a dated `# DONE` with quoted §-lines to THIS file; commit here too.

## DONE WHEN
Gate passed with the named numbers; engine shipped API-compatible; all witnesses green; PR merged
(auto-merge); DONE section appended. If the gate fails its controls: report the measured numbers
and stop — that outcome is a VALID completion of this task.

# DONE (2026-07-13) — GATE_FAIL, per preamble: STOP, report numbers, no engine edit

**W-LEG-POC ran against real data (bim-ootb `poc_path_legal.js`, pushed on `fix/path-legal-segments`,
NOT merged — no engine files touched, this is a calculation-only POC).**

- HHS route reproduced exactly as G1 claims:
  `§POCLEG HHS route doors=3 distance=86.2m path=room:≈ Level 1 R18 | room:≈ Level 1 R17 |
  doorwp:Drehflügel 1-flg - Stahlzarge:88.5 x 2.26:88.5 x 2.26:573676 | room:≈ Level 1 R31`
- The named control case — the courtyard chord — did **not** classify illegal:
  `§POCLEG chord=Drehflügel 1-flg...573676->≈ Level 1 R31 len=48.1m illegal_pts=0/194`
  `§POCLEG HHS_SUMMARY illegal_chords=0 (expect >0 — the courtyard chord)`
- Duplex control passed clean: `§POCLEG Duplex_SUMMARY chords_checked=74 illegal_chords=0 (expect 0)`
- **Root cause, measured not guessed** — this building's per-storey `IfcSlab` is a single concave
  (U-shaped) element, and `element_transforms` stores only ONE axis-aligned bbox per element:
  `§POCLEG_ROOTCAUSE storey=Level 1 slab="Floor:STB 30.0:573302" bbox=65.8x53.4m covers 105% of
  the storey's room-rect extent — a concave slab stored as one AABB, overreaching into any notch.`
  Same failure shape as the concave-room-AABB problem the file's own §MULTI-RECT commentary already
  documents for rooms (`room_graph.js` header) — but slabs have no multi-rect decomposition to fall
  back on, and this HHS db's `spatial_structure` has no `room_guid` column either
  (`hasRoomGuid=false`), so rooms are single-AABB here too.
- **Diagnostic, not a fix, not applied** — room-rects alone (dropping slabs from the union entirely)
  DOES correctly flag the same chord:
  `§POCLEG DIAGNOSTIC room-rects-only (no slabs) for the same chord: illegal_pts=156/194 (80.4%)`
  This is reported to inform the next decision, not silently substituted as G3's definition — per
  the preamble, that call goes back to the coordinator.

**Open question for the coordinator (the ⛔ this session cannot resolve alone):** G3's floor-slab
half of the walkable-space union relies on slab AABBs that overreach on any concave slab (measured
on HHS, both its Level-1 "Floor:STB 30.0" AND "Floor:FB 15.0 - Fliesen" rows, each ~105% of the
storey's room extent) — and the same AABB-only limitation shows up on Duplex's Roof slab too
(100% coverage, harmless there only because Duplex's roof has no concave notch to hide). Two
directions, not decided here: (a) drop slabs from the union, room-rects-only (works on HHS per the
diagnostic above, but weakens the definition for any storey where circulation floor exists outside
every room's own rect — e.g. corridors, if HHS or another building has any); (b) keep slabs but
require a room-adjacent check too (a point only counts walkable if slab-covered AND within some
distance of a real room boundary) — untested, no numbers run for it. Both are real geometric
definitions, neither improvised solo per the preamble's fence — next session should pick one,
POC-gate it the same way, then proceed to implementation only after it passes clean.
