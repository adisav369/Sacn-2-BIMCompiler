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

## §CACHE-LANDMINE (found 2026-07-14, blocks §SHIPPED from actually reaching a browser) — READ FIRST
User reloaded HHS after PR #779 merged and did NOT see the claimed changes. Root cause confirmed from
`viewer/sw.js` source, not guesswork: `isNetworkFirst()` (line ~218-222) hard-codes `if (base.includes
('/lib/')) return false` — cache-first, no revalidation, EVER — for anything under `/lib/`. That rule
was meant for true vendor libs (three.js, sql-wasm, chart.js). But `viewer/lib/room_walker.js` — the
exact file PR #773/#776/#779 all changed — lives under `/lib/` by folder placement only, not because
it's immutable. A `CACHE_VERSION` bump wouldn't even have saved it; the `/lib/` check short-circuits
before that logic runs. Confirmed live: `CACHE_VERSION` also never bumped across #775→#779 (stayed
`'v748'`) — a second, separate omission. Live console evidence: `§ROOM_GRAPH edges=11 deadend=75
orphan=47` matches NEITHER the claimed before (16/73/44) nor after (27/66/40); `habitable=70` when
#779's own commit message says the raster was regenerated for a 71-room compile. **Fix: stop treating
`room_walker.js` as an immutable lib (move it out of `/lib/`, or carve a narrow exception in
`isNetworkFirst()`), bump `CACHE_VERSION`, verify a fresh load actually pulls new bytes, redeploy.**
**FIXED + MERGED + DEPLOYED 2026-07-14** — bim-ootb PR #780 (`fix/sw-lib-cache-room-walker`), merged
`8895234a9`, both CI checks green, `deploy-pages` run `29266972074` succeeded. Fix: exempted
`room_walker.js` explicitly from the `/lib/` blanket rule in `isNetworkFirst()` (network-first now,
same as any other project JS) + bumped `CACHE_VERSION` v748→v749. Verified pre-merge by extracting
`isNetworkFirst()` verbatim and unit-testing it in isolation (room_walker.js → network-first; real
vendor libs three.module.min.js/sql-wasm.wasm → unaffected, still cache-first) — a real browser
before/after wasn't run pre-merge, so **the user must still confirm with a genuinely clean load**
(DevTools → Application → Clear storage → Clear site data, or a private window) that: (a) the
`RoomOverSize.png` elongated `≈ Level 2 R9`-shaped defect (room box stretching outside the building
envelope) is gone, and (b) `§ROOM_GRAPH` now reads close to the claimed 27 edges/66 dead-ends/40
orphans, not the stale `edges=11/deadend=75/orphan=47` seen live 2026-07-14. This same pattern
(room_graph.js/CACHE_VERSION never bumped, fixes never reaching browsers) recurred once before —
see the stale, never-merged `fix/room-graph-cache-bust` branch — worth a five-minute check next
session that nothing else in `viewer/lib/` is a same-mistake project file mislabeled as vendor-immutable.

**SECOND, DEEPER BUG found right after — the sw.js fix alone was not enough.** User did a real full
storage clear + reload post-#780: HHS came back showing **105 raw rooms** (not ~71), console threw
`§HELPERS_QUERY_ERR no such column: room_guid` and `NEEDLE_INJECT ... rooms=0 rects=0`. Root cause:
`_needleInject()` (`viewer/navigate_find.js` ~line 892) set `source='patch'` — skipping the
`RoomWalker.walk()` compile fallback entirely — whenever the fetched patch SQL executed without
throwing. HHS's current patch is only 4 lines (regenerates `storey_walkable_raster`; PR #775 retired
the old patch that used to carry compiled room rows), so on a truly fresh DB `applied=true` but no
room was ever compiled: `spatial_structure` kept raw, uncompiled `IfcSpace` rows, no `room_guid`, none
of WALL-SNAP/SUSPECT-LARGE/§MULTI-RECT. `§NEEDLE_PERSIST` then wrote that regressed state back into
IDB, so every later reload reproduced it — this is what looked like "reverted to the old condition."
**FIXED + PUSHED 2026-07-14** — bim-ootb PR #781 (`fix/needle-inject-trusts-patch-without-compile`),
auto-merge armed, CI running at time of writing. Fix reuses the exact same-file precedent already
established at `_roomsFromSpatialStructure` (~line 1887, its own "§MULTI-RECT guard" comment,
itself a fix for this identical missing-column bug class): probe `PRAGMA table_info(spatial_structure)`
for a `room_guid` column as evidence rooms were actually compiled, don't trust a successful patch exec
alone. **MERGED + DEPLOYED 2026-07-14** — `09008f7166`, both CI checks green, `deploy-pages` run
`29267526459` succeeded. **Still not yet confirmed by a real post-merge browser check** — user must
re-verify with one more full storage clear + reload: rooms should come back to ~71 (not 105) with a
working `room_guid` column, the `RoomOverSize.png` elongated-room defect gone, and `§ROOM_GRAPH` near
`edges=27 deadend=66 orphan=40`.

Do this BEFORE trusting any of §SHIPPED is actually live for a real user, and before starting §OPEN #1
below — no point measuring Level 1/2 connectivity against code that isn't running.

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
   - **UX ask (user, 2026-07-14):** once corridor/hallway is detected (hallwayness/R-SPINE), surface it
     as a `Type` label on the room (Find panel / room card), not just an internal flag — so a corridor
     is filterable/findable by name, same as any other room type.
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
