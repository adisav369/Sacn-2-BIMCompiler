# ⚠ DO NOT REMOVE — Scope guard
# SCOPE: cross-session handoff for the "Functional Space Management" theme (room/space accuracy +
# walkability, bim-ootb Viewer + Modeller). REPLACES the 2026-07-13 version — that one's work is
# done and shipped (see §SHIPPED below). Read this file first, don't re-derive what's already here.

# FUNCTIONAL_SPACE_MGMT_NEXT_SESSION — 2026-07-14

## §SHIPPED this session (bim-ootb, all merged to main, live)
1. **PR #773/#776** — `viewer/lib/room_walker.js` (needle-button copy) synced with bim-compiler's
   already-proven room fixes (SUSPECT_ELONGATED, §DOOR-PARTITION-EXT-EXCLUDE, §STAIRWELL-STACK,
   no-overlap guard). Root lesson: the needle-button JS was a SEPARATE, silently-stale copy of the
   canonical source — see memory `feedback_verify_runtime_copy_not_just_canonical_fix`.
2. **PR #775** — retired the stale static `buildings/patches/HHS_Office_Federated_extracted.db.sql`
   room data (105 pre-fix rows) that was shadowing the fixed code on every load/needle-press.
3. **PR #776** — §WALL-SNAP: compiled room rects were systematically short of their real wall face
   by up to 0.6m (0/208 sampled sides ever overshot — pure undersize, not overlap). Fixed in both
   `scripts/compile_rooms.py` and `build/room_walker.js`.
4. **PR #777** — path routing: (a) stair waypoint nodes never carried a `storey` field at all, so
   `_legalizePath`'s same-storey gate silently skipped almost every chord touching a stair, not just
   the genuinely cross-floor one — fleet-wide fix, any building with stairs; (b) HHS's real
   mesh-derived `storey_walkable_raster` (accidentally dropped by #775) regenerated.
5. **PR #778** — Find-panel room selection is now `room_guid`-aware. A §MULTI-RECT logical room (one
   room split across N `spatial_structure` rows, e.g. L-shaped) was listed N times under the same
   name, and clicking any entry bound to ONE sub-rect instead of the room's real extent — this is
   what looked like "still too small" / "shifted into a wall." Fixed via one shared
   `_roomUnionBBox()`, reused by both the list-builder and the selector (WalkerDoctrine §10).
6. **PR #779** — §SUSPECT-LARGE: `MAX_AREA_ABS=150m²` used to silently DROP any compiled pocket
   bigger than that — no row, no flag, no log — calibrated to residential room sizes, predating the
   ext-exclusion leak fix. HHS's real 456m² Level-3 corridor was being discarded this way; every door
   off it had only 1 room neighbor instead of 2, which is why the room graph measured 73 dead-ends/44
   orphans out of 70 rooms. Repurposed: still compiles, flagged `SUSPECT_LARGE` for review, never
   vanishes. Fleet-measured: every building gained rooms or stayed unchanged, none regressed.

**Net effect on HHS, measured, not assumed:** room-graph edges 16→27, dead-ends 73→66, orphans 44→40.
**This is a real step, not the finish line** — see §OPEN below.

## §OPEN — real, unfinished, named plainly
1. **HHS Level 1/2 connectivity is still weak** (66 dead-ends, 40 orphans remain) and NOT yet
   root-caused the way Level 3's corridor was. Hypothesis, unverified: smaller, fragmented gaps
   rather than one big dropped pocket — needs the same measurement discipline (raw flood-fill
   component sizes, walkable-raster-vs-room-coverage diff) applied to Level 1/2 specifically before
   touching any code.
2. **The 3-signal room-type/bucket system (user's own framing, 2026-07-14, "we need to think
   together") — spec'd in conversation, NOT built:**
   - **Size-bucket clustering** (per floor, not one global ratio): cluster a storey's own room areas;
     a tight cluster of similar-sized rooms = one functional type (e.g. offices); an outlier that's
     large AND long AND touches many rooms = hallway signature. Supersedes the single global
     area-ratio test already tried once and rejected as too crude (`ROOM_INTELLIGENCE_SCOREBOARD.md`
     — Clinic's real 93m² hall got flagged by a naive global ratio; a per-floor cluster test wouldn't).
   - **Hallwayness + R-SPINE** — ALREADY SPEC'D, not built: `ROOM_TAXONOMY_STRATEGY_2026-07-12.md`
     Task 4's `hallwayness(R) = min(aspect/2.697,1) * min(area/10.415,1)` (measured from real Duplex
     hallway ground truth) + `spine(storey) = connected subgraph of rooms with hallwayness >= 0.5`.
     Read that file's Task 4 spec before building this — don't re-derive.
   - **Z-band container consistency** (new, 2026-07-14) — a room's Z-placement should sit inside its
     OWN storey's measured slab-to-slab band (`§STOREY-ZBAND`, built earlier for a different bug — a
     disc-walker placement issue, not rooms). A room that doesn't fit its storey's real Z-band is
     mis-assigned to the wrong floor container, independent of XY size/shape. Not yet connected to
     room compilation at all — worth checking whether `§STOREY-ZBAND`'s existing measured-band code
     can be reused directly (WalkerDoctrine §10 — one function, not a new one) before writing fresh math.
   - **Sequencing note (settled in conversation):** none of these 3 signals can classify a room that
     was never compiled — §SUSPECT-LARGE (shipped) was the prerequisite doorway, not a substitute for
     this system.
3. **HHS curtain-wall glass elements are unclickable in the Viewer** (`§PICK ... g=?`, real bug,
   confirmed cause: all 33 `IfcCurtainWall` PARENT elements have zero `element_transforms` — only
   their `IfcMember`/`IfcPlate` children carry real positions). **Deliberately deprioritized as noise
   by the user** for this round — not the same bug as room-wall snap accuracy (`compile_rooms.py`
   already has a documented workaround using the children, not the untransformed parent). Revisit only
   if curtain-wall-bounded room sides are later measured to snap worse than solid-wall sides — that
   specific check was raised but never run.
4. **DISC-walk `hostBind()` two-level room+wall surface sensing** — user's own idea (2026-07-13):
   walk the room list first (even in a connecting/adjacency sense), THEN secure the exact real ARCH
   wall; best-effort recognize secondary aligned surfaces (perpendicular partitions, fixture/sink
   rows — real classes found: `IfcFurnishingElement`/`IfcFurniture`, matched on real Clinic/Duplex/
   Hospital data) as valid mount candidates too. Investigation started, real ground-truth evidence
   already gathered (Duplex: storey-wide nearest-wall picks the WRONG room's wall in 3/18 real cases,
   16.7%) — but implementation was interrupted before any code was written. Needs: `_roomWallsFor()`-
   style room-scoped candidate narrowing in `modeller/disc_walker.js`'s `hostBind()` SIDE branch,
   opt-in (never default-on without a witness), respecting the settled `spacesOf()` compiled-room-
   never-drives-placement boundary — using room data to pick WHICH wall, not WHERE to place, is the
   same "weaker, more defensible use" §SPACE-GATE already established as acceptable. Not started.

## Where to start
Pick #1 (Level 1/2 connectivity) if continuing THIS thread directly — same measurement method already
proven on Level 3. Pick #2 if the user wants the bigger bucket-system built. Pick #4 if redirected back
to DISC-walk. Don't re-derive any of the above — it's all measured or spec'd already.
