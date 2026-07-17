# ⚠ DO NOT REMOVE — Scope guard
# SCOPE: cross-session handoff for the "Functional Space Management" theme (room/space accuracy +
# walkability, bim-ootb Viewer + Modeller). REPLACES the 2026-07-14 (earlier) version — that one's
# §SHIPPED work is superseded by this session's much larger §HALLWAY-BACKBONE build-out. Read this
# file first, don't re-derive what's already here.
# 2026-07-15 UPDATE: §TOP PRIORITY (island-gap closure) DONE — see §ISLAND-BRIDGE-SHIPPED at the
# bottom of this file (new, read it first if picking this up next). PR #794 open (bim-ootb
# `fix/island-connectivity-bridge`), not yet merged.

# FUNCTIONAL_SPACE_MGMT_NEXT_SESSION — 2026-07-14 (§HALLWAY-BACKBONE session)

## §MOST IMPORTANT LESSON — READ THIS FIRST
**Every fix in this session passed every Node-based witness while being COMPLETELY INACTIVE in the
real browser**, for most of the session, because `common/hallway_backbone.js` was never added to
`viewer/main.js`'s dynamic module-load list (fixed at the very end — see §SHIPPED PR #788). Node's
`require()` resolves a file regardless of what any HTML/load-list references; the browser only
loads what it's told to. **Witnesses proved the LOGIC was correct — they never proved the browser
was actually RUNNING it.** Before declaring any browser-facing fix done next time: drive the real
dev server with a headless browser (Playwright — see §HOW-TO-TEST-LIVE below) and confirm the
new code's own console tag/log line actually appears, not just that the offline witness is green.
This was only caught because the user asked directly "have u tested?" — that question should be
asked of yourself before it's asked of you.

## §HOW-TO-TEST-LIVE (added this session, use every time from now on)
```bash
cd ~/bim-ootb && python3 -m http.server 8000 &
# Playwright is installed under ~/bim-ootb/tests/node_modules — require it from there:
node -e "const {chromium} = require('/home/red1/bim-ootb/tests/node_modules/playwright'); ..."
```
- Direct building URL only, never the Hub picker: `http://localhost:8000/viewer/viewer.html?db=buildings/{Name}_extracted.db&ghost=1`
  (see memory `feedback_localhost_full_building_url_testing.md` — Hub can silently fetch production for some buildings).
- Find panel: press `f` (not a visible button by default). Axis toggle: `#find-axis-toggle`
  (click cycles storey→disc→room→material→phase→parts). Sub-toggle (Storey/Type/Path): plain
  `<button>` with the label text, no id — use `button:has-text("Type") >> visible=true` (Playwright's
  bare `text=` selector matches hidden `<option>` elements first and hangs).
- `window.HallwayBackbone` / `window.RoomGraph` are checkable directly via `page.evaluate()`.
- **The live building's room compile does NOT match any static `.db` snapshot on disk.** HHS's
  live compile (via `RoomWalker.compileRooms()`+`writeRooms()`, run either by needle-inject or
  auto-recompute) produces **105 rooms**; the raw `spatial_structure` baked into the shipped
  `.db` file has only 14; a plain offline `RoomWalker.compileRooms()` call (no patches applied)
  gives yet a third number (71). **Always reproduce via the live compile path** (see the Node
  snippet pattern used throughout this session — `sql.js` + `RoomWalker.compileRooms`/`writeRooms`
  against the real `buildings/*_extracted.db`) before trusting any offline room count.

## §SHIPPED this session (bim-ootb, all merged to main, live) — chronological
1. **PR #782** — real corridor spine (`common/hallway_backbone.js`, new module): doorEdge →
   correlateDoorEdges → joinDoorways → growToWall → terminateAtStair → walkBackbone. E2 door-rescue
   switched from a single per-storey `CIRC` blob to the nearest real spine waypoint. Fixed a
   detour-legality gap (spine/circ missing from `_detourForChord` candidates) and gave each
   corridor bucket a real measured width (`bucketRect`) fed into `_pointWalkable`'s fallback.
2. **PR #783** — backward-propagation: `§CORRIDOR-ROOM-BACKPROP` injects a real `room` node for
   every backbone bucket with NO compiled room there (HHS: 12/15 buckets had none). Found+fixed a
   same-session regression (corridor rect swallowing a real room's own doors — real
   rect-overlap-AREA guard added, not just centroid-containment). Type-tree surfacing
   (`classifyCorridorRooms`) + needle-inject cache-bust fix (PR #785) shipped alongside.
3. **PR #784** — chord detour prefers LOCAL waypoints (chord bbox + 6m margin) over a legal-but-
   distant one first, falling back to the full storey search only if no local detour exists. Fixed
   a real "walks to the far end of a 13m room before turning back" artifact.
4. **PR #786** — stair waypoints (`loWp`/`hiWp`) now use each END's own real position (nearest
   flight row to that end's z), not one XY average across the whole stair — fixed a "vertical
   elevator-shaft drop" rendering artifact (real stairs have horizontal run; averaging collapsed it).
5. **PR #787** — `getStairGroups()` merges flight+assembly stair data instead of strict either/or.
   Root cause of Hospital's **total** cross-floor routing failure: 1 orphan `IfcStairFlight`
   (unrelated, 1.1m z-span) blocked the old either/or from ever reaching 61 real `IfcStair`
   assembly rows. Hospital: stairGroups 1→10, real E3 edges 0→6. Verified Duplex (flight-only
   building) triggers zero merge — no double-counting.
6. **PR #788 — THE CRITICAL ONE.** `common/hallway_backbone.js` was never added to
   `viewer/main.js`'s module list. Every feature in PRs #782-787 had been silently inert in every
   real browser session, all week, despite every witness passing. See §MOST IMPORTANT LESSON above.

**Net effect, live-verified (not just offline) after PR #788**: HHS's Type-tree went from ONE group
("INTERNAL_DOORPART 105") to TWO ("INTERNAL_DOORPART 74" + "Hall / Corridor 32") — screenshotted,
saved to `~/Pictures/Screenshots/proof_2026-07-14_0{1,2,3}_*.png`.

## §NEW BUG — corridor label false-positive rate — ✅ FIXED (2026-07-14, follow-up session)
**Root cause confirmed exactly as suspect #1 below, on BOTH ends of the same bug**: `bucketWidth()`
had no plausibility bounds on its flanking-wall search. Live dump (Clinic, better-sqlite3, no
browser needed — user steered "review the maths deeply, sandbox-test a formula set" instead of a
full live re-run): Second Floor's runCoord=45 bucket measured **halfWidthLo=halfWidthHi=0.00** — a
literal zero-thickness line, because the bucket's OWN door-hosting wall (same axis as the corridor,
so it always passes the "runs alongside" test) sits at ~0 offset from runCoord and the old
`Math.max(d,0)` clamp let that self-match win as "nearest". This is the exact user report:
"Second Floor Hall Corridor2 mis-IDs by taking the narrow wall next to it." Separately, on HHS,
the SAME unbounded search accepted implausible far walls (~7.8m half-width), ballooning rects
into false-positive matches (e.g. a 242m² room via centroid-inside alone, overlap-frac only 0.570).
**Fix** (`common/hallway_backbone.js`, branch `fix/corridor-width-bounds`, committed locally per
the standing push-pause — not pushed): `MIN_SIDE_OFFSET=0.5` / `MAX_SIDE_OFFSET=3.0` bound in
`bucketWidth()` (reject candidates outside a plausible corridor-half-width window, fall back to
`DEFAULT_HALF_WIDTH`); a new overlap-fraction guard in `classifyCorridorRooms()` (room's own area
must be >=50% inside the matched bucket, not just its centroid — mirrors room_graph.js's existing
CORRIDOR-ROOM-BACKPROP discipline). Also swapped `navigate_find.js`'s hardcoded 3.0m corridor-shell
ceiling placeholder for a 2.0m movement-clearance height (user steer: this box is for
path-of-movement, real ceiling height is Modeller's job later, "2 meter human height sufficient"
even under a tall foyer/atrium ceiling).
**Verified**: `sandbox_corridor_width.js` (new, synthetic geometry with known expected values —
host-wall self-match rejection, far-wall rejection, in-window acceptance, overlap-fraction
accept/reject) + all 3 existing witnesses (`witness_hallway_backbone.js`,
`witness_corridor_room_backprop.js`, `witness_corridor_type_label.js` — the last one's own G2/G3
ground-truth logic updated to check overlap-fraction instead of centroid-only, since that was the
behavior being intentionally changed) all green. Clinic Second Floor's 15 buckets now measure a
physically sane 1.6-4.2m width range (was 0.00 to unbounded). classifyCorridorRooms count on
Clinic moved 9→17 under a clean git-stash A/B (not just eyeballed) — net INCREASE despite the
stricter guard, because the width fix recovers real corridor-adjacent rooms that were previously
invisible behind zero-width buckets, while the overlap-fraction guard separately rejects
centroid-only false positives.
**Second increment, same session — user distilled a "common sense filter"**: a real
walkway/corridor (1) is not a room, (2) is not too wide, (3) is not floating in mid air, (4) is
always connected to doors/stairs/walls. (1)+(2) are the fix above; this increment adds (3)+(4) as
explicitly named, reusable verbs in `common/hallway_backbone.js` — matching the same shape
established BIM/indoor-routing tools use (IFC 2nd-level space-boundary generation, Revit
Room/Space resolution): a BOUNDED ray-cast to the nearest plausible surface, never an unbounded
nearest-match search.
- `wallLiesFlatAgainst(offset,min,max)` — the shared bounded-offset predicate, now used by BOTH
  `bucketWidth()` (side walls) and `growToWall()` (end caps) instead of two separate ad hoc bounds.
- `growToWall()` gets `MAX_END_REACH=8.0m` on its own end-cap search — same "nearest wall
  regardless of distance" flaw as the width bug, but along the corridor's own axis. Confirmed on
  real HHS data: two outlier end-caps (8.99m, 13.56m past the last door) now correctly left open;
  every real value (<=6.98m) elsewhere unaffected.
- `distanceToEnclosure()` + `buildingEnvelope()` — a per-storey wall-derived footprint check; a
  bucket whose rect corner sits outside its storey's real envelope is dropped. Literal "floating
  outside the building" backstop.
- `isGrounded()` — reject a bucket open on BOTH ends with no stair anchor on either side.
All four run in `buildBackbone()` right before chains are built, logging
`§COMMON_SENSE_FILTER droppedUngrounded=N droppedOutsideEnvelope=N` when either fires (never
silent). **Verified**: 12 new sandbox unit checks (`sandbox_corridor_width.js`, now 20 checks
total) covering each verb's boundary shape directly, plus all 3 existing witnesses — all green.
Zero buckets dropped on Clinic or HHS's CURRENT data (the filter is a verified-correct backstop
that hasn't needed to fire yet on these two buildings, not something visibly changing their
counts today).
**Third increment, same session — consolidated into a framework (user ask: "review, consolidate,
backward compatible")**. The two increments above left plausibility numbers scattered across 8
separate `var` constants and the grounding/envelope check as an inline ad hoc block in
`buildBackbone()`. Consolidated:
- `DEFAULT_PROFILE` — every tunable number (door count, width bounds, end-cap reach, stair
  clearance, overlap fraction, wall-cross slack, run-coord tolerance) now lives in ONE object.
  Every step function takes an OPTIONAL trailing `profile` arg defaulting to it — every existing
  call site omits it and reproduces today's exact numbers (verified: full witness re-run showed
  byte-identical stats before/after — buckets=113 joined=38 chains=15 classifiedRooms=17/118).
- `commonSenseFilter(buckets, envelopeByStorey, profile)` — the (3)+(4) gate (isGrounded +
  distanceToEnclosure) is now ONE named, exported, independently-testable step ("5b" in the verb
  chain), returning `{kept, dropped: [{bucket, reason}]}` — replacing the inline per-bucket filter
  block that used to live directly in `buildBackbone()`.
- Extension point (deliberately unused today, just placed): a future per-building-class corridor
  profile (matching this project's existing WalkerDoctrine building-class parameterization) is
  now `Object.assign({}, HallwayBackbone.DEFAULT_PROFILE, {...})` passed as `opts.profile` — a
  caller-side change, not a structural one.
Also dropped `terminateAtStair()`'s dead `storeyOf` param (grepped every call site first — never
passed anywhere). **Verified**: `sandbox_corridor_width.js` now 26 checks (added G1-G4 for
commonSenseFilter as one unit, H1-H2 proving the profile-override extension point actually works)
+ all 3 existing witnesses, all green, zero behavior drift.

**Fourth increment, same session — shape guard, user re-flagged**: "Type.Hall/Corridor are still
non corridors but mere rooms." Real: of the 17 rooms classified before this fix, 11 were small
near-square rooms (aspect ratio 1.08-1.44, area 4.7-11.4m², e.g. "First Floor R10" 2.4x2.2m) —
closets/small-offices, not corridors, small enough to clear 50% overlap regardless of shape. Added
`DEFAULT_PROFILE.minAspectRatio=1.8` + a new `unionAspectRatio(ownRects)` primitive (room's own
union bbox across §MULTI-RECT sub-rects — a shape property checked once per room, not per
candidate). `classifyCorridorRooms()` now requires BOTH the overlap-fraction bar AND this shape
bar. Clinic's classifiedRooms moved 17→6, keeping exactly the 6 genuinely elongated ones (aspect
1.88-3.86) and dropping exactly the 11 boxy ones. **Process note**: while diagnosing, an ad hoc
(uncommitted) debug script had its own off-by-one column bug that initially misidentified
"Second Floor R7" (real dims 16x4.6m, aspect 3.48 — a genuine corridor) as the offender — caught
before committing by re-deriving with corrected indices; production code was never affected.
Verified: sandbox grew to 30 checks (D3 + I1-I3), all witnesses green (witness_corridor_type_label
's own G2/G3 ground truth updated to require both bars, same pattern as the earlier overlap-
fraction fix).

**Still not done** (deliberately, scoped out, real follow-up): the user's ask for "doors facing
rooms along it" as a STRUCTURAL door-to-room-access validation (distinct from the grounding check
above, which only checks the bucket's END termination, not whether the doors ALONG it actually
serve rooms). `joinDoorways()` still only checks door COUNT (>=3). Re-open if a live re-check
(HHS or another building) still shows false positives after all four increments land.

## §NEW TOOL — RoomGraph.fullConnectivity() — "is every door reachable from every other, no gap?"
**User's own question, same session**: is there a simple test that every door can reach every
other door via a covered path? No such test existed — `components()` (pre-existing) is
deliberately room-only (E1/door edges, an honesty metric its own comment says not to extend); the
existing witnesses only sample random room-PAIRS via `shortestPath` and report a percentage.
**Added** `RoomGraph.fullConnectivity(graph)` (`common/room_graph.js`) — same union-find shape as
`components()`, but over the FULL adjacency (`shortestPath`'s own Dijkstra graph: all edge kinds
E1-E8). A door never has its own node (only ever edge metadata), so "every door reaches every
other, no gap" IS exactly "is this graph one connected component."
**Real bug found building it**: naively seeding the node universe from `graph.nodesByGuid`
produced ~200 phantom size-1 "islands" on Clinic — `'doorwp'`/`'stairwp'` entries are pure
position lookups for two unrelated on-demand systems (`_detourForChord`'s per-chord visibility
graph; `shortestPath`'s path-render waypoint substitution), most never wired into `graph.edges`
at all. Fixed: node universe = every room/circ/exit node (real destinations that MUST be
reachable) UNION every guid that actually appears as a real edge endpoint (still counts a
genuinely-doorless room as a real isolated island; still counts a bridging doorwp wired in via a
real E7 edge; excludes the untouched majority).
**Honest current answer** (not asserted as "should be 100%" — reported, per building):
Clinic 71.8% connected (12 genuinely isolated rooms, named by storey — First Floor
R26/R31/R40/R42/R45/R56/R58, Second Floor R25/R26/R30/R37/R43, TOF Footing R1); HHS 49.4% (raw
pre-patch data — see §HOW-TO-TEST-LIVE); Duplex's 3 isolated rooms are EXPECTED non-habitable
Roof/Foundation voids, not a bug. This directly quantifies, by name, the same gap already tracked
below (HHS connectivity well under 100%, Hospital R13→R46 no path) — now with a reusable tool,
not just a one-off sampled percentage.
**Verified**: `sandbox_full_connectivity.js` (10 checks — fully-connected case, a real doorless-
room gap correctly detected, the doorwp/stairwp phantom-island bug's exact shape reproduced then
fixed, an edge-wired doorwp still correctly counted) + `witness_full_connectivity.js` (real report
on Clinic/HHS/Duplex, pass bar = the function's own internal invariants hold, NOT "no gaps exist"
— per "tests expose issues," this test's job is to NAME the gap honestly, not assert it away).

## §DEFERRED TO A NEW SESSION — meticulous path-of-movement rendering ("green dots")
**User's own ask, same session, explicitly NOT for this session**: "the greendots... can they be
really following path of walking, not jumping/crossing into space... even at stairs, a dot [can
be] mid air of stairs. Should have more dot pairs, top and bottom of each flight." User's own
framing: spec it out thoroughly first, attempt in a new session. Do NOT implement yet — this is a
placeholder pointer, not a task to start.
**What's already known, so the next session doesn't re-derive it**:
- §OPEN #2 above (2026-07-14, earlier this session) already names the EXACT mechanism behind "a
  dot mid-air at stairs": `terminateAtStair`/E3 stair-chain code already computes BOTH a lower and
  upper real waypoint per flight (`loWp`/`hiWp` in `room_graph.js`'s `_e3Chain`) — the gap is in
  `shortestPath()`'s `path[]` RECONSTRUCTION, which only substitutes ONE waypoint (the arriving
  stair's) per intermediate node visit when two different stairs meet back-to-back at one floor,
  dropping the departing stair's own entry point. The waypoint DATA already exists; the rendering
  needs to surface both ends at every stair transfer, not just one per node.
- The user's other complaint ("jumping/crossing into space") is the SAME class of issue
  `_detourForChord`'s visibility-graph detour already targets (chord-legality + local-then-global
  waypoint search) — likely needs its own re-audit once path[] reconstruction is fixed, since a
  currently-"legal" chord might still not hug the corridor centerline even when it doesn't cross a
  wall.
- **Is this "done by others"?** Yes — this is a well-established pattern in indoor
  routing/wayfinding (e.g. IndoorGML's explicit multi-level "Anchor" transition nodes with distinct
  entry/exit points per level, or game-AI navmesh "off-mesh links" for stairs/elevators/ladders
  with two explicit endpoints). Not unusual or exotic — the existing loWp/hiWp data model already
  matches this pattern; it's the rendering/reconstruction layer that hasn't caught up to it yet.
- **Is it hard?** Not conceptually — the waypoint DATA is already measured and real (per the
  §STAIR-RUN-ENDS discipline). The work is a path[]-reconstruction change (surface both arrival AND
  departure waypoints at a shared intermediate node) plus a fresh visibility-graph audit — bounded,
  not a research problem, but real engineering worth its own spec'd session as the user asked.

## §NEW BUG (ORIGINAL WRITE-UP, kept for the diagnosis trail above) — corridor label false-positive rate

## §NEW BUG (ORIGINAL WRITE-UP, kept for the diagnosis trail above) — corridor label false-positive rate
**User's own live check, same session, right after PR #788 shipped**: of the 32 rooms classified
"Hall / Corridor" on HHS, only **1 is a genuine long corridor** — the other ~31 are false positives.
Not investigated yet — prime suspect below, verify before changing anything (don't guess-fix).

**Also very likely the SAME root cause as a separate visual report** ("half corridor... drawn not
along the corridor but perpendicular into mid air outside building", seen in
`proof_2026-07-14_03_Type_mode_HallCorridor.png` — a large box floating outside the building
envelope in the Room-lens shell view). An oversized/misoriented corridor rect would explain BOTH
symptoms at once: over-classification (rect too generous, catches unrelated small rooms whose
centroid falls inside it) AND a visibly-wrong shell box for whichever bucket has the worst
`growToWall`/`bucketWidth` result.

**Where to look first** (informed by this session's own data, not a fresh guess):
1. `HallwayBackbone.growToWall()` has **no maximum distance cap** when searching for a perpendicular
   capping wall — "any wall crossing this runCoord line, regardless of how far away" (see
   `common/hallway_backbone.js`). A coincidentally-Y-aligned wall on the FAR side of the building
   could become the chosen cap, stretching a bucket's span (and therefore `bucketRect()`'s box) far
   beyond the real corridor. This session's own data dump (see the raw `bucketRect` measurements
   taken mid-session) showed several HHS buckets with spans up to ~40-55m along one axis — plausible
   for a real long corridor, but NOT independently verified against real wall positions to rule out
   this exact failure mode. Re-run that same dump, then for the top 2-3 largest/most suspicious
   buckets, manually check whether the wall `growToWall` actually picked is a REAL corridor end-wall
   or a coincidental cross-building alignment.
2. `classifyCorridorRooms()`'s match test is **centroid-of-room-inside-bucketRect** only — no
   minimum-overlap-fraction requirement. A generously-sized (or wrongly-elongated) bucket rect can
   swallow many small unrelated rooms whose centroid merely happens to fall inside it, without those
   rooms actually BEING part of the corridor. Compare against the overlap-AREA guard already used
   for `§CORRIDOR-ROOM-BACKPROP` injection (`room_graph.js`, >0.5m² threshold) — that fix exists
   for exactly this class of "too permissive" bug in a sibling code path; `classifyCorridorRooms`
   may need the same discipline (e.g. required minimum overlap fraction of the ROOM's own area, not
   just a centroid point-test).
3. Once diagnosed, verify BOTH bugs (false-positive rate AND the floating-box render) close together
   with the SAME fix if they share a root cause — don't fix one and re-test the other separately if
   the data shows one bucket is responsible for both.

## §OPEN — real, unfinished, named plainly
1. **Hospital: R13(Level 1)->R46(Level 3) still returns no path**, even after PR #787's stair-merge
   fix (which DID enable real cross-floor connectivity elsewhere — 1/21 sampled cross-storey pairs
   now resolve, was 0 before). These two specific rooms are in different connected components,
   not near any of the 6 now-working stair bridges — a room-connectivity gap, not a stair-detection
   one. Needs the same measurement discipline already proven this session (real fresh compile,
   `RoomGraph.components()`, trace why R13/R46 specifically can't reach a spine/circ node).
2. **Two-stairs-meeting-at-one-floor waypoint drop** (found while fixing PR #786, NOT fixed): when a
   path passes through an intermediate CIRC node via two DIFFERENT stairs back-to-back (e.g.
   Level1→Level2 via stair A, immediately Level2→Level3 via stair B), only the ARRIVING stair's
   waypoint (`_publicHop`'s substitution) is exposed in the rendered `path[]` — the DEPARTING
   stair's own entry point is dropped. Not visible on HHS (the two real stairs there happen to sit
   0.08m apart) but will be a real "walks through a wall" artifact on any building where the two
   stairwells are genuinely apart. Needs a `path[]`-reconstruction change: surface BOTH the arrival
   and departure waypoint at a shared intermediate node, not just one substitution per node visit.
3. **HHS Level 1/2/3 connectivity is still well under 100%** even after all this session's fixes
   (was 17.6%→38.0% reachable room-pairs after PR #783's backprop; PR #787's stair-merge measured
   separately, not re-combined with #783's number — re-measure the CURRENT combined reachable-pair
   rate with all 6 PRs live, now that PR #788 makes them all actually active, before further work).
4. **The §NEW BUG above** (corridor false-positive labeling) is the actual next priority — it's
   what makes the whole feature currently look broken/untrustworthy to a user glancing at the Type
   tree, even though the underlying mechanism (spine, backprop, stair fixes) is real and measured.

## §LESSONS LEARNT (process, not code — carry these into every future session on this feature)
- **"Have you tested?" is a question to ask yourself unprompted.** An offline witness proves logic;
  it does not prove the browser runs that logic. This cost most of a session's worth of user-facing
  "still broken" reports that were actually "never loaded at all."
- **A live building's data does NOT match its shipped `.db` file.** Client-side compile
  (needle-inject / auto-recompute) can produce a wildly different room count than either the raw
  extraction OR a naive offline re-run of the same compile function without whatever patches the
  live session applies. Always reproduce via the SAME path the browser actually takes.
- **When a user reports something looks "like an old bug"**, take that framing seriously — it may
  be a pre-existing rendering issue made newly VISIBLE by a real data-coverage improvement (more
  rooms now shown = more chances to see an existing edge case), not a regression from the immediate
  fix. Don't assume it's new just because it was just reported.
- **Squash-merge auto-fires on green CI** in this repo — pushing more commits to an already-open PR
  after auto-merge has fired ORPHANS them silently (happened once this session, caught by chance
  when the local worktree's `getStairGroups()` output didn't match what had supposedly shipped).
  After opening a PR, check `gh pr view <n> --json state` before assuming later pushes are included;
  if state flips to MERGED mid-session, start the next chunk of work as a FRESH branch off the new
  `origin/main`, never keep pushing to the same (now-merged) branch.
- **`gh pr view`/`gh pr merge` must run from inside the target repo's own checkout** (`~/bim-ootb`),
  not from `bim-compiler` — `gh` resolves the PR against whatever repo the cwd's git remote points
  to; running it from the wrong repo silently fails to resolve the PR number.
- **`~/bim-ootb` (the shared, non-worktree checkout) is being actively edited by a concurrent
  session** all through this one (`common/history_tap.js` + `viewer/viewer.html`, a `HIST_VIEWNAV`
  guard + version bump, unrelated to this work) — every sync this session used
  `git stash push -u -m "concurrent session WIP"` → `git merge --ff-only` → `git stash pop`, never
  a plain merge/pull, to avoid clobbering it. Keep doing this every time `~/bim-ootb` needs sync.

## §TOP PRIORITY, NEXT SESSION — close the disconnected-island gaps (user, 2026-07-15)
**Push-pause LIFTED for this work specifically — user said "push" directly, then asked for the
PRs to be seen through to merge.** Status as of session end: `fix/corridor-width-bounds` (5
commits: width bounds, common-sense filter, framework consolidation, shape guard,
`fullConnectivity()`) → **PR #792, MERGED to `main`** (both CI checks green, auto-merge squash).
`fix/sfx-nan-guard` (1 commit, unrelated SFX crash fix found via a live user error report) →
**PR #793, MERGED to `main`**. Both feature branches + their `/tmp/wt-*` worktrees were pruned
(fully merged + clean) — **next session starts a FRESH worktree off updated `origin/main`**, do
NOT reuse or recreate either branch name (squash-merged history collides, see the standing
Concurrent-branches doctrine in `CLAUDE.md`). This does NOT reopen the rest of the standing
PUSH PAUSE in `CLAUDE.md` — that section wasn't edited, only this specific work got an explicit
go-ahead this turn; a fresh session should still default to local/localhost unless told
otherwise again.

**User's framing, verbatim intent**: the island gaps `fullConnectivity()` just named (Clinic
71.8% connected, HHS 49.4%) are a **show-stopper blocking real downstream value** (pathfinding,
escape-route, the whole occupant-graph story) — resolve them **right away**, next session, not
deferred. User's own hypothesis: it may be "just one more step or routine" — a pass that uses the
connectivity marker (`fullConnectivity()`'s `comp`/`sizes` output) to identify the islands and
bridge them.

**Grounded starting point (from this session's own data, not a fresh guess)** — the honest answer
is "maybe genuinely small, but verify before assuming":
1. **Two different shapes of island, likely two different fixes** — don't apply one blanket
   "bridge everything" heuristic to both:
   - **Size-1 islands** (Clinic: 12 of them — First Floor R26/R31/R40/R42/R45/R56/R58, Second
     Floor R25/R26/R30/R37/R43, TOF Footing R1) have LITERALLY ZERO edges — not even an E2
     lone-door rescue. First question: does each of these rooms have a REAL door in the IFC data
     at all? If yes, why didn't E1 (room-to-room) or E2 (lone-door-to-circ) match it — a
     threshold/matching gap in `buildGraph()`'s door-correlation logic (bounded, fixable, same
     shape as this session's other diagnoses). If NO real door exists, it's not a graph bug at
     all — some of these names ("TOF Footing" = Top-of-Footing, a structural/foundation space,
     same pattern as Duplex's expected-isolated "Roof"/"T/FDN" rooms) may be genuinely
     non-habitable and SHOULD stay excluded from the "must connect" universe, not bridged with an
     invented door. Check `room_habitability.js`'s existing non-habitable filter FIRST — it may
     already know which of these 12 don't belong in the connectivity requirement at all.
   - **Multi-node islands** (e.g. HHS's "Hall/Corridor N + spine + several doorwp", sizes 4-13)
     ARE internally connected corridor segments, just not bridged to the REST of the building's
     graph — likely a `walkBackbone()` crossing that should exist between two chains but wasn't
     detected (a T-junction the crossing-detection missed), or a segment near an exterior
     door/stair whose bridge into the main network never got wired. This is closer to "one more
     step" — likely a real, bounded gap in the SAME crossing/join logic this session already hardened.
2. **Do NOT invent a connection.** Per this project's Prime Directive (extract/compile, never
   invent), any "bridging pass" must be evidence-based — a real door, a real crossing, a real
   nearby stair — using the SAME discipline as `§CORRIDOR-ROOM-BACKPROP` (room_graph.js) and this
   session's own `commonSenseFilter`: only bridge where real geometry justifies it, and log
   `§ISLAND_BRIDGE` (or similar) with the evidence per bridge — never a silent "just connect
   nearest island" heuristic with no real basis, and never mark a genuinely non-habitable
   structural space as "fixed" by inventing a door for it.
3. **Method, same as this whole session**: for EACH size-1 island, dump its real door count/position
   from the DB directly (same `better-sqlite3` + real query pattern used throughout this session)
   BEFORE writing any fix — don't guess why it's isolated, measure it. Then decide per-room whether
   it's (a) a real matching-gap bug to fix, or (b) a legitimately non-habitable space to exclude
   from the connectivity requirement (not "fix" at all).
4. Re-run `witness_full_connectivity.js` after each change — the `fullConnectivity()`/`sizes`/`comp`
   output already exists precisely to prove whether an island closed for real reasons, not just to
   report the starting number.

Once (or alongside) this: the §OPEN items below (Hospital R13→R46, two-stairs-one-floor waypoint
drop, HHS connectivity re-measure) are the SAME underlying question at different granularity —
`fullConnectivity()` may subsume/answer several of them directly rather than needing separate
investigation.

## §ISLAND-BRIDGE-SHIPPED (2026-07-15, §TOP PRIORITY closed) — PR #794, open, not yet merged
Both island shapes named above were diagnosed by measurement (never guessed) and fixed in
`common/room_graph.js`, worktree `/tmp/wt-island-bridge`, branch `fix/island-connectivity-bridge`:
1. **Ambiguous-residual-rescue (E9)**: Clinic's 5 real-door-but-isolated rooms (R31/R40/R42/R45/
   R58) were each the 3rd candidate at a 3-way door junction — `buildGraph()` only ever wired the
   2 closest candidates, silently dropping every candidate beyond that even at sub-0.25m distances.
   Fix: every residual candidate now wires to the door's own real waypoint instead of being
   dropped. Recovers exactly the 5 predicted rooms, zero over-connection.
2. **Circ-per-chain bridge (E6)**: HHS's 3 multi-node islands (components sized 5/11/13) were each
   a real corridor chain (`hallway_backbone.js`'s own union-find grouping) on a storey that DOES
   have a real stair, but the old CIRC→spine bridge only ever picked the single globally-nearest
   chain — every other chain on the same floor stayed stranded. Fix: bridge one real-distance edge
   per distinct chain present on the storey, not just one overall.
**Verified**: `witness_full_connectivity.js` Clinic 71.8%→95.7%, HHS 49.4%→85.2%; all existing
hallway/corridor witnesses+sandboxes still green (byte-identical B3 Clinic cross-floor path modulo
a slightly shorter real route now available); **live Playwright check against the real Clinic
building** (not just Node) — `window.RoomGraph.fullConnectivity()` in-browser matches the offline
witness exactly (185 nodes, 9 components, 95.7%) — the exact class of "witness green but browser
inert" mistake from the PR #788 lesson above was checked for and did NOT recur here.
**Remaining gaps, deliberately NOT force-bridged** (measured, not guessed):
- **Clinic's 8 residual islands are real MEP/ACMV service voids**, confirmed by element-composition
  query (not just door-proximity): First Floor R26/Second Floor R25/26/30/37/43 are all
  ACMV-`IfcFlowSegment`-dominated duct risers (First Floor R56 is a duct chase with no live run,
  STR beam only); TOF Footing R1 is pure `IfcFooting`/wall — a structural void. Zero real door
  within 1.3-5m of any of them because there's genuinely nothing for a door to serve. This is NOT
  caught by `common/room_habitability.js`'s existing label-keyword exclusion (these carry generic
  "COMPILED INTERNAL" labels, not descriptive space names) — the real signal is element
  composition, not label text. **Open decision, asked of the user, not yet answered**: add an
  ACMV/footing-content-dominated + no-real-door signal to `room_habitability.js` as a new exclusion
  class (bounded, same file/pattern, just content- instead of label-based), or leave
  `fullConnectivity()`'s report as-is (already honest, needs a human reading it to know this
  context). Do this FIRST if picking this thread back up — don't re-measure, the composition query
  is in this session's PR #794 conversation history if needed again.
- **HHS's 2 remaining islands ("Unknown" storey Hall/Corridor 1 & 2) are the SAME storey='Unknown'
  landmine already fixed once elsewhere** (Modeller's `disc_walker.js`, see memory
  `project_discwalk_containment_utmost` / `RESUME_DISC_WALKER_ENVELOPE_BOUND.md` §STOREY-UNKNOWN).
  Measured: 17 doors tagged `storey='Unknown'` in HHS's `elements_meta` have their own real z
  spanning 1.23-8.43m — matching Level 1 (z 1.14-1.76), Level 2 (z 4.80-5.17), Level 3 (z 8.30-8.43)
  EXACTLY. A correct fix is a z-based storey reassignment for any 'Unknown'-tagged door/wall row
  (map to the nearest real storey by z, same real data already used elsewhere in this file) —
  bounded, but touches BOTH `common/hallway_backbone.js`'s doorRows/wallRows queries (wallRows
  currently doesn't even SELECT center_z — needs adding) AND `room_graph.js`'s own doorRows query,
  consistently, so a corridor bucket and its room-matching see the SAME reassigned storey. Not
  attempted this session (cross-cutting data-normalization change, wanted its own sandbox coverage
  before touching two already-heavily-tuned files) — named precisely so the next session doesn't
  need to re-derive the root cause, just implement + verify it.
- Duplex's Roof/T-FDN islands are the already-known expected-isolated structural voids (unchanged).
**Not yet done**: merge PR #794 (open, CI pending as of session end).
