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

## §HALLWAY-BACKBONE (2026-07-14, investigation session, NOTHING COMMITTED — read before touching #2)
User's ask: cheaply find ALL long hallways fleet-wide, chained with stairs into one continuous
structure ("main entrance till all doors"), for Modeller + path routing (+ incidentally, a flythrough
camera path — same structure serves both, keep the eventual output an ORDERED path, not just a graph).
Everything below ran in scratch scripts (`node`, `sql.js`, local `deploy/buildings/*.db`) and was
DELETED after each run per housekeeping — **none of this is committed code yet**, it's a verified
algorithm sketch. Next session's job (if picking this up) is to actually build it as real code.

**Dead end, correctly ruled out — don't re-try:** column-fragmented-corridor theory. `WALL_LIKE`
(`compile_rooms.py`/`room_walker.js`) does treat `IfcColumn` as a wall obstacle, so the mechanism is
real, but Hospital's 604 columns + 10 proximity-based "chains" turned out, on rigorous check (does a
real column actually sit in the touch-gap?), to be real individually-walled small rooms (bathrooms,
real `IfcDoor`+`IfcWallStandardCase` partitions) — only 18 genuinely column-verified touching pairs
exist in the whole building, none chaining ≥3. Proximity alone is not corridor evidence.

**Live lead, not yet acted on:** `rejectRooms()` (`compile_rooms.py`/`room_walker.js`) silently DROPS
any pocket with `enclosure < REJECT_ENCLOSURE` — no row, no flag, same shape as the `SUSPECT_LARGE`
bug §SUSPECT-LARGE already fixed, except this gate is enclosure-based not size-based and is still live.
Hospital dropped 7 pockets this way. A wide-open corridor/atrium (long, barely enclosed, few doors) is
exactly the shape that would score low enclosure and vanish. Worth instrumenting `rejectRooms` to
report what it drops (size/shape/enclosure score) before deciding whether to repurpose it
compile-but-flag, same treatment as `SUSPECT_LARGE`.

**Working result — door+wall+crossing backbone, verified on Clinic:**
1. `doorEdge(door)` — wall-run-axis from the door's OWN bbox aspect ratio (wide-in-X ⇒ its wall runs
   along X ⇒ cluster by shared Y). `rotation_z` is unusable — always 0 in this extracted data, checked
   before relying on it.
2. `correlateDoorEdges(edges)` — bucket-matrix keyed by `(storey, axis, roundedRunCoord)` — the
   "matrix array, harnessed by the door wall" the user specified.
3. `joinDoorways(buckets)` — buckets with ≥3 aligned doors = hallway-candidate ("join with other
   doorways forms it").
4. `growToWall(bucket)` — extend the bucket's span along its axis, both directions, until a REAL wall
   (`IfcWall%` only — columns/beams deliberately excluded per user, "ignore supporting columns/beams
   for convenience") caps it.
5. `terminateAtStair(bucket)` — an un-capped (open) end near a stair = a connecting space, not a
   dead-end. **Stair detection is the unresolved piece** — see below.
6. `walkBackbone()` — union-find merge of buckets whose grown spans cross (an x-run bucket's runCoord
   falls inside a y-run bucket's span and vice versa = a T-junction/crossing).

**Result on Clinic:** 41 joined buckets → 9 chains after crossing-merge. The two big ones: First Floor
24 segments/116 door-touches merged into ONE connected backbone; Second Floor 10 segments/58
door-touches into another. Cross-validated three independent ways without pointing one at the other:
graph door-degree (R36 deg=10, top of Clinic), raw-coordinate clustering, and this wall-harnessed
version all converge on the same rooms (R36, R96, R41, R34, R9, R59) — R36/R96 each appear in *two*
different axis-buckets, correctly reflecting real corridor T-junctions. Separately, door-degree was
also validated against REAL ground truth: Duplex's rooms are literally named "Hallway A201"/"Hallway
B201" in the source IFC, and rank #1 by door-degree with zero shape-based flagging.

**Unresolved: stair-termination, 0/41 buckets connected to a stair.** Root-caused TWO layers deep, not
just tolerance-tuned:
- First bug (fixed in the scratch check): stair elements report `storey='Unknown'` — same landmine
  class as the existing `§STOREY-UNKNOWN` fix elsewhere in this codebase, unaddressed for Clinic's
  stairs specifically. Dropped the storey-string filter, matched by real XY instead.
- Second bug (fixed): was using point-to-centroid distance; switched to real contour (bbox-rect)
  distance with a ~2m movement-clearance tolerance, per user's steer ("stairs movement space can be of
  say 2 meter height flow onto the stairs contour").
- Even after both fixes: still 0. The 2 actually-open bucket-ends are 11-18m from the nearest stair —
  not a near-miss. `IfcRailing` was tried as a corroborating signal (stairs themselves may be
  unclickable/ungeometried, same "unclickable parent" shape as the HHS curtain-wall bug — use the
  reliable neighbor instead) — but every railing cluster sits on a stair I'd ALREADY found, so it
  didn't reveal anything new. The 2 open ends genuinely aren't stair-adjacent by any signal tried.
- Separately flagged by the user and NOT yet resolved: three different ad-hoc `ifc_class LIKE
  'IfcStair%'` queries in this session returned 7, 8, and 13 — the trusted, shipped answer (Building
  Parts Taxonomy's `Part.Stairs` in the live Viewer) is **4**. Any real implementation MUST reuse the
  existing trusted stair extractor (`BUILDING_PARTS_TAXONOMY.md`'s `STAIRWAY` logic and/or
  `room_graph.js`'s own `§STAIR-CLASS-FALLBACK`), not a fresh ad-hoc query — WalkerDoctrine §10, one
  function not a new one. This is very likely why stair-termination hasn't matched anything: it may
  simply be counting/locating the wrong 7 "stairs" instead of the real 4.
- Next concrete step if resumed: swap in the trusted stair extractor, re-run `terminateAtStair`, THEN
  decide whether the 2 open ends are a real remaining gap or resolve themselves.

**Parked, not needed given the above works:** floor-slab/`IfcCovering` footprint as an alternative
"complete base for the space" signal (Clinic has 16 `IfcSlab` + 250 `IfcCovering`) — raised from a
screenshot showing one continuous unbroken floor surface spanning a stair-flanked landing. Not tested
before the session converged on the door+wall approach instead. Also parked: per-area ceiling-height
as a join signal — checked, currently NON-discriminating (`room_walker.js` assigns one flattened
`center_z`/`size_z` per whole storey, all 168 First-Floor Clinic rooms report the identical value) —
would need real per-area slab/covering Z pulled directly, not the compiled room's flattened value.

**Design principle (user, 2026-07-14):** the correlation matrix (`correlateDoorEdges`) shouldn't be
hard-wired to doors only — ANY element that supports/corroborates a space's identity (railings, stairs,
walls, doors) should be roll-in-able to the same bucket/array-set arbitrarily. `IfcRailing`-as-stair-
corroborator above is one instance of this, not a one-off — when building for real, keep the bucket
keyed generically (storey, axis, runCoord) and let multiple element types contribute edges into it,
rather than writing a separate parallel structure per element type.

**Verbs, for whoever builds this for real:** checked the actual Java `BIM_COBOL` verb catalog (77
verbs) for a precedent — `JoinVerb` (MEP connector, not spatial), `FollowVerb` (MEP pipe crawl via a
`CrawlRouter`/`CrawlState` engine — closest structural analog to "grow until stopped"),
`RouteSprinklersVerb` (per-room only), `WalkThruVerb` (BOM-tree walk, unrelated domain). None fit —
this is JS-side (`room_walker.js`/`room_graph.js` territory, where the live per-browser compile
already runs), not a BIM_COBOL/Java concern. Suggested names, verb-styled per that catalog's
discipline even though not literally Java: `doorEdge`, `correlateDoorEdges`, `joinDoorways`,
`growToWall`, `terminateAtStair`, `walkBackbone` — matches steps 1-6 above 1:1.

**Also raised, genuinely minor, parked:** some Clinic rooms' compiled Z-extent runs up through a
raised/soft ceiling's ACMV plenum void instead of stopping at the real ceiling line. User: "small, not
urgent... only if room doesn't clearly cross a boarded ceiling." No action taken.

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
   - **Hallwayness + R-SPINE — SUPERSEDED by §HALLWAY-BACKBONE above (2026-07-14).** The room-shape-
     based `hallwayness()` formula undercounts badly (missed Clinic and Hospital's real corridors
     entirely — see §HALLWAY-BACKBONE's dead-end note). The door+wall+crossing backbone approach is
     the validated replacement direction — read §HALLWAY-BACKBONE in full before building this, don't
     start from the old formula.
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
