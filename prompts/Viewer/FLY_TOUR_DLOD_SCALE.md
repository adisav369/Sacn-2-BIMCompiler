# ⚠ DO NOT REMOVE — Scope & Working Rules
**Scope:** extend the already-shipped view-based DLOD box-proxy mechanism (`prompts/done/TM_DLOD_SCALE.md`
§9 — real mesh near-camera/in-frustum, wireframe box otherwise) from Time-Machine-only to GENERAL
navigation (Fly Tour, free orbit/pan, plain viewing) so buildings AT OR ABOVE LTU_AHouse's scale
(122k elements, the current largest tested fixture) stay smooth without needing Time Machine active.
Nothing else — no new box-rendering system, no touching the retracted S261 geometry-swap DLOD, no
touching `dlod.js`'s independent frustum-culling (S262). **This is a NEW spec (filed 2026-07-20), not
a re-open of a regression** — MEASURE BEFORE ESTIMATING (this project's standing doctrine): get real
frame numbers on real hardware before any % target is claimed anywhere in a PR or this file.
**Read the log after every run.** Witnesses (§4) all need a `§DLOD_NAV_*` or `§PERF_TRAVERSE` log
line — no claim without one. Target session: **Fable** (execution), NOT Sonnet — this file is
self-contained per `feedback_model_allocation_mastermind_vs_execution.md`; do not rediscover, do not
re-litigate `TM_DLOD_SCALE.md` §1's settled DLOD-naming history.
**Status:** OPEN, filed 2026-07-20. Triggered by a live user question ("how to get true DLOD to
scale LTU above sizes") asked immediately after `prompts/done/TOUR_ROUTE_CACHE.md` §4's fix — the
user's own framing tied the two together ("perhaps it is just the FlyTour isolated case... delve
into its initial"), i.e. Fly Tour is the first real-world path this should cover. **§5 added same
day, then self-corrected same day**: a viability test for a structural culling axis (storey
occlusion) the user's own domain pushback surfaced — the FIRST version of §5 (blanket storey-
partition) was itself RETRACTED after two more rounds of user pushback found it breaks aerial views
and barely helps interior ones; §5's final shape is a two-track split (box-proxy for aerial, room-
level occlusion blocked on data for interior). **§6 added same day**: real-code impact check on
Find Panel + Clash — a required exemption clause (§6's last paragraph) is now part of the design,
not optional, before any DLOD extension ships. **§7 added same day**: real math found the §0-§4
distance-radius test itself gets occlusion backwards (facade-far-but-visible boxed, next-room-near-
but-hidden stays real) — line-of-sight via an existing unused R-tree index is the corrected axis,
not yet designed. **§8 added same day, HARD GATE — read before touching ANY DLOD code in this
repo:** this exact problem class (distance/geometry-threshold visibility swap) has failed FOUR
times already (S258, S259, S261, S262) on edge-on flicker + TM visibility conflicts; hysteresis was
already tried and was insufficient alone. §8 authorizes Fable to INVESTIGATE a cross-fade mitigation
in an isolated prototype and REPORT BACK ONLY — no implementation, no live wiring, no PR, until the
user reviews the findings and explicitly says to proceed.

## 0. What already exists — reuse, do not reinvent (all shipped, all proven)
- **`viewer/time_machine.js`'s DLOD proxy** (`_dlodEngaged`, `_dlodBuildBoxes`, `_dlodUpdateBoxes`,
  `_dlodBoxIndex[guid].pos/.radius`, `_dlodFrustum`/`_dlodPSM`/`_dlodSphere` reused scratch objects):
  the FINAL shipped rule (`TM_DLOD_SCALE.md` §9) is view-based — FRONTIER/RECENT (TM concepts) always
  real; everything else PLACED renders real only if `distance ≤ 50m AND in-frustum`, else a wireframe
  box (`MeshBasicMaterial`, opacity 0.4, matching `_drawBboxPlaceholders`' established look).
  **Only the "FRONTIER/RECENT always real" clause is TM-specific** — outside TM there is no
  construction cursor, so that clause simply doesn't apply; the distance+frustum test underneath is
  already building-agnostic.
- **Engage gate today:** `_dlodEngaged = toggle && _isLargeBuilding && !app.streaming`
  (`time_machine.js:501`) — hard-gated on TM being the active mode. This is the ONE line that makes
  the whole mechanism TM-only; extending it is centrally this line's job, not a rewrite.
- **`LARGE_BUILDING = 50000`** (`time_machine.js:471`) is the existing size threshold — LTU (122k) is
  ~2.4× over it. "Above LTU sizes" has no shipped meaning yet; §1 below is where that gets defined
  with a real number, not invented here.
- **Pick-exclusion:** `isBboxPlaceholder` (checked `picking.js:257`) + the debug tag `isDlodTmProxy`
  (`TM_DLOD_SCALE.md` §9's last note — use this to tell DLOD boxes apart from ordinary load-time
  placeholders in any future audit; they share geometry/material and are otherwise indistinguishable).
- **`TOUR_ROUTE_CACHE.md`** (just refixed, §4) already solves the ROUTE-PLANNING cost of Fly Tour —
  this spec is about RENDER cost during the tour/navigation itself, a different bottleneck. Do not
  conflate the two; a slow Fly Tour after this spec's fix should point back at `TOUR_ROUTE_CACHE.md`.
- **`TM_DLOD_SCALE.md` §7 already named this as a non-goal for v1**, verbatim: "No Fly-Tour/walk-mode
  integration yet: the tour benefits automatically only via whatever TM window is active. A follow-up
  MAY render far-storey boxes during tours using the same proxy — separate spec, after W-DLOD-PERF
  numbers exist." **W-DLOD-PERF (TM_DLOD_SCALE.md §6) was never actually run** (§8 admits it — no
  browser/GPU access from that CLI session); this spec's §4 W-DLOD-NAV-PERF is the first time real
  numbers for this mechanism exist at all, TM or not. Get those numbers before extending further.

## 1. What "above LTU sizes" concretely means — measure before designing
LTU_AHouse (122k elements) is the largest fixture currently proven end-to-end in this repo (mesh
extraction + room graph + streaming + render, all witnessed elsewhere). Before writing any code:
1. Confirm whether a real fixture bigger than LTU already exists (`grep -c` element counts across
   `deploy/buildings/*_extracted.db` or ask — do not invent a number).
2. If none exists, the cheapest real stress test this codebase already supports without new data is
   a **city view**: `A.buildingsRendered.size > 1` (multiple buildings loaded together — Fly Tour
   already has city-tour code for this, `viewer/tour.js` city-orbit block) — e.g. LTU + LTU again (or
   + Hospital/Terminal) pushes total resident elements well past 122k using only existing DBs. Use
   this to get a first real "above LTU" data point instead of waiting for a bigger single-building DB.
3. Log the actual triangle/draw-call/element counts from whichever path is used — this is the
   `MEASURE BEFORE ESTIMATING` step `TM_INCREMENTAL_RENDER_PERF.md §0` established the hard way; do
   not reuse that file's retracted "8 draw calls" number (`TM_DLOD_SCALE.md` §1 already flagged it
   uncorroborated — real measured LTU healthy state is ~15-16K draw calls, S280c).

## 2. Design shape — generalize the engage gate, not the mechanism
- Add a navigation-scope engage condition alongside the existing TM one:
  `_dlodEngagedNav = navToggle && (elCount > DLOD_NAV_MIN_ELEMENTS) && !app.streaming` — independent
  of whether TM is active. `DLOD_NAV_MIN_ELEMENTS` starts as `LARGE_BUILDING` (50000, proven) unless
  §1's measurement says otherwise for the general-nav case (TM's threshold was tuned for TM's own
  cost profile, not necessarily the right number for free-camera panning).
- Drive the ACTIVE set from resident/placed elements directly (there is no construction cursor
  outside TM — no FRONTIER/RECENT distinction applies), reusing the exact same distance+frustum test
  and cached `_dlodBoxIndex` from `TM_DLOD_SCALE.md` §9. The box/real partition becomes purely
  "in-view real, out-of-view box" everywhere, TM or not.
- Own pill/toggle for nav-scope DLOD, separate from TM's `◧ LOD` pill (different lifecycle — this one
  should be live during Fly Tour and plain orbit, not gated on TM panel visibility). Default OFF,
  same as TM's v1 (`TM_DLOD_SCALE.md` §4/§7 — "No default-ON... default flips only after the user's
  own hardware numbers say so").
- **Landmines, already known, do not re-discover:**
  1. `_metaGen`/`_batchMeta` index-rebuild bump — box InstancedMeshes must NEVER be TM-tracked meshes
     (`TM_DLOD_SCALE.md` §5.1). The still-OPEN `TM_STREAM_REBUILD_COALESCE.md` item (per-batch index
     rebuild during streaming, 50-159ms×10+ on LTU) is a DIFFERENT, already-filed bug — do not fold it
     into this spec, but be aware it's live if profiling shows rebuild cost during a load-then-fly test.
  2. Never touch `_useDlodPath`/`setGeometryAt` (S261, retracted, stays retracted) or couple with
     `dlod.js`'s frustum-culling (S262 doctrine: independent systems, do not merge).
  3. `feedback_no_fake_lod_unbreakable.md`: boxes must always read as obviously-proxy — reuse the
     wireframe look verbatim, no "solid" regression (TM_DLOD_SCALE.md §9 already learned this the
     hard way from a live user correction — do not re-litigate, just reuse the wireframe material).
  4. Fly Tour specifically streams/promotes geometry as it moves (`§FLY_STREAM_WAIT` doctrine,
     `TOUR_ROUTE_CACHE.md` §2) — box proxies must not fight the promote pipeline; gate nav-DLOD off
     during `A.streaming`, same as TM's v1 does.

## 3. Non-goals (v1, mirrors TM_DLOD_SCALE.md §7)
- No camera-distance mesh-swap (`setGeometryAt`), no memory-residency/eviction work — this reduces
  DRAW cost only, not resident GPU memory (say so honestly in any report, per `TM_DLOD_SCALE.md` §1's
  correction of an earlier overstated claim).
- No default-ON, no auto-engage without an explicit user toggle, until §4's numbers justify it.
- Not a fix for `TM_STREAM_REBUILD_COALESCE.md`'s open per-batch rebuild cost — separate, already-filed.

### SCOPE DECISION — 2026-07-21, USER-DICTATED (engage surfaces, settled — do not re-litigate)
Nav-scope DLOD engages ONLY during: free orbit/pan/plain viewing + Fly Tour. Excluded, per the
user's explicit direction ("But not Find Panel and Alt-C movie orbit"):
- **Find Panel isolation active → nav-DLOD disengages ENTIRELY** (stronger and simpler than §6's
  minimum per-GUID exemption, which stays documented below as the fallback shape). Rationale:
  isolation already hides most of the building — there is no draw cost left for DLOD to save, only
  the §6 revert-flicker risk. Re-engage (if the pill is on) when the isolation clears.
- **Alt+C Cinema Orbit: excluded** — visual-quality mode; a wireframe proxy box must never appear
  in a movie frame. Same rationale extends to Alt+P photoreal stills (session note: extended by the
  assistant on the same logic, flagged to the user in-conversation, not independently dictated).
  Accepted tradeoff, on record: Cinema pays full render cost on LTU-scale buildings — if a Cinema
  run is slow there, that is this decision working as intended, not a bug.

## 4. Witnesses (blocking; headless is blind to GPU per TM_DLOD_SCALE.md §6 — real numbers need the
user's machine, `window.__tmTrav`-style stats or an equivalent nav-scope hook)
- **W-DLOD-NAV-EQUIV:** toggle OFF → scene identical to today's Fly Tour / free-orbit rendering,
  byte-for-byte visible-guid set, across a scripted camera sweep. `mismatch=0`.
- **W-DLOD-NAV-PROXY:** toggle ON → in-view real-mesh set matches the distance+frustum test exactly;
  boxed set is the disjoint remainder; log `§DLOD_NAV active=<n> boxed=<n> mode=on|off`.
- **W-DLOD-NAV-PERF:** on LTU (and the §1 city-view stress case if no bigger single fixture exists),
  compare draw calls / triangle count / frame time during an actual Fly Tour run and during free-orbit
  panning, proxy OFF vs ON. This is the FIRST real number for this mechanism outside TM — report it
  plainly, don't extrapolate from TM's (also never formally witnessed) numbers.
- **W-DLOD-NAV-NO-REBUILD:** confirm zero `§PERF_INCR_INDEX` (or equivalent) rebuilds fire from DLOD
  box visibility flips alone during a sustained tour/orbit session (post-streaming).

## 5. Storey occlusion — a SEPARATE, structural culling axis (viability tested 2026-07-20)

**Why this is here and why it wasn't found weeks ago (read before designing more box-proxy work):**
every perf phase to date — TM delta-rendering, box-proxy distance+frustum, plain `dlod.js` frustum
culling, the retracted S261 geometry-swap — asks a CAMERA-RELATIVE question (distance, frustum,
time-window). None ever asked whether the BUILDING'S OWN STRUCTURE already proves an element can't
be seen. That's not a data gap — `elements_meta.storey` has been 100%-populated the whole time — it's
an analysis blind spot: each phase was a reactive fix to a specific live complaint, built by reusing
whatever mechanism already existed (correctly, per this project's reuse-first doctrine), which means
a mechanism that had never been built (structural occlusion) never entered the reuse pool. It surfaced
this session only because the user pushed back on the "wide view = mostly facade" assumption with
"behind the walls can't they be culled" — a domain intuition the camera-relative framing above never
reached for on its own. Both Fly Tour AND Alt+C Cinema Orbit are affected — `effects.js`'s MaxQ film
always "dives" into the largest interior space at floor level (`§CINEMA_SIMPLE decision 3`), so
Cinema is NOT purely exterior/aerial either; occlusion here has to be room/storey-aware for BOTH
entry points, not envelope-only (a naive "hide everything behind the outer shell" rule would go dark
the instant either camera is inside its own dive/walk target — see the conversation this file's git
history sits under for the fuller reasoning).

**Viability test run 2026-07-20 (pure SQL against the real DB, no browser/GPU needed — this only
tests spatial partition, not render cost):** `buildings/LTU_AHouse_extracted.db`, 125,698 elements.
```
SELECT storey, COUNT(*), 125698-COUNT(*), ROUND(100.0*(125698-COUNT(*))/125698,1)
FROM elements_meta GROUP BY storey ORDER BY 2 DESC;
```
| storey (top 5 of 19 distinct labels) | on this storey | elsewhere | % elsewhere |
|---|---|---|---|
| Plan 1 | 44,374 | 81,324 | 64.7% |
| Plan 2 | 28,542 | 97,156 | 77.3% |
| Plan 3 | 18,815 | 106,883 | 85.0% |
| Plan 4 | 13,522 | 112,176 | 89.2% |
| VÅNING 3 | 2,218 | 123,480 | 98.2% |

Even parked on the single biggest floor, **65% of the building is on a different storey** —
opaque floor/ceiling slabs make that a strong occlusion candidate (exception: stairwells/atria with
genuine vertical sightlines — a small exempted set, not a dent in the ceiling). Most storeys clear
90%+. **`storey` coverage is complete** — this is buildable today with no new extraction.

**Same-floor room-level occlusion (walls blocking lateral sightlines) is a SEPARATE, currently
BLOCKED question** — checked `rel_contained_in_space` (element→IfcSpace containment): only 1,608 of
125,698 elements (1.3%) have a room assignment, across 181 spaces. Not a logic problem, a data
problem — do not attempt room-level occlusion until containment extraction coverage is fixed
(separate task, not this spec).

**RETRACTED same day — blanket storey-partition is NOT a good design, don't build it as originally
proposed above.** Two follow-up questions from the user exposed real cracks, confirmed with more
SQL (not hand-waved):
1. **Aerial/exterior views break it.** The tour's own action log (both Fly Tour and Alt+C) mixes an
   opening `orbit`, interior `flyPath` legs at floor height, and a closing `Bird's eye`/`Final` shot.
   A blanket "hide anything not on camera's current storey" rule is only even valid while the camera
   is deep inside ONE floor — during the aerial/orbit legs there is no single "current storey," and
   other floors' facade/roof genuinely ARE visible from outside. The naive rule would punch holes in
   the building during exactly the establishing/closing shots — the same "go dark" failure as
   envelope-only occlusion (§ intro), just triggered from the opposite direction.
2. **Even where valid (camera genuinely inside one floor), storey-level is too coarse to matter
   much.** Queried real room-level containment: the 181 rooms with data average **8.9 elements per
   room** (`rel_contained_in_space`, 1,608 rows / 181 rooms). Compare to Plan 1's 44,374 elements.
   Storey-partition only ever addressed the VERTICAL axis (other floors); the bigger win was always
   the HORIZONTAL axis (other rooms on the SAME floor, 8.9 vs 44,374) — which storey-partition
   cannot touch at all, and which needs room-level containment, currently blocked on the same
   1.3%-coverage data gap named above.

**Corrected shape — two separate tracks, not one storey-partition mechanism:**
1. **Aerial/exterior legs:** use §0-§2's existing distance+frustum box-proxy (already-proven,
   TM_DLOD_SCALE.md §9) — the correct lever for "camera can see a lot at once," not storey partition.
2. **Interior/room legs:** the real win is room-level occlusion (8.9 vs 44k elements), not storey-
   level — but this is gated on a PREREQUISITE, non-optional fix: `rel_contained_in_space` coverage
   needs to go from 1.3% to something usable BEFORE any room-level culling logic is worth designing,
   let alone building. That containment fix is its own task (extraction/compile work, not a render
   change) and belongs in a separate spec — note it here, don't scope-creep it into this file.
3. Storey membership itself is NOT thrown away — it's just not the culling axis. It remains useful
   as an aerial/interior MODE SIGNAL (is the camera's current stop associated with one storey via
   the room-graph/dive-target data both Fly Tour and Cinema already compute for path-planning, or is
   it an orbit/bird's-eye action with no single storey?) — a cheap way to pick which of the two
   tracks above applies at a given tour stop, reusing data already in memory.

## 6. Cross-system impact — Find Panel, Clash (checked 2026-07-20, real code, not assumed)

Any DLOD extension (nav-scope box-proxy from §0-§4, or a future room-level mechanism from §5) writes
to the SAME per-slot visibility state (`_instanceMeta`/`_batchMeta`, `setMatrixAt`/`setVisibleAt`)
that other features already use. Two were checked directly — do not assume the rest of the app is
unaffected without checking each on its own terms; these two aren't a template that automatically
covers everything else.

- **Find Panel's element-precise highlight** (`navigate_find.js` §C, `_hlOverlay`): draws its OWN
  `InstancedMesh` of unit boxes from `element_transforms`, entirely independent of the base scene's
  render state. **Not affected** by DLOD — same architectural pattern as Clash below.
- **Find Panel's isolate mode** (`A.filterByGuids`, `panels.js:839`; also used by `A.isolateRoom`,
  `panels.js:870`, itself limited by the SAME `rel_contained_in_space` 1.3% coverage gap named in
  §5 — not a new finding, the same data gap blocks BOTH room-level occlusion and today's shipped
  room-isolate feature): manipulates the real mesh's per-slot visibility directly via
  `A.collectMeshes`+`A.filterInstancedMesh`/`filterBatchedMesh` (`helpers.js:36-73`) — the exact
  same state DLOD's per-tick traverse (`time_machine.js:1264`, `hideForProxy`) re-asserts every
  tick with zero awareness of Find's isolation. **Confirmed real conflict, not hypothetical:** Find
  isolates a GUID → `filterByGuids` force-shows the real mesh this instant → the very next DLOD
  tick, if that element independently reads as "placed, not frontier/recent, out-of-view," DLOD
  zeroes it back out and shows its box instead. The isolated element visibly reverts. `filterInstancedMesh`/
  `filterBatchedMesh` DO silently no-op on DLOD's box `InstancedMesh` itself (`if (!meta) return`,
  since box meshes are deliberately never registered in `_instanceMeta`/`_batchMeta` per
  `TM_DLOD_SCALE.md` §5.1) — so Find's filter can't corrupt or crash on a box mesh, it just can't
  see or protect a real mesh that DLOD independently decides to hide.
- **Clash** (`measure.js:681-728`, both the full-opacity and clipped-overlap highlight meshes):
  built fresh from `component_geometries` blobs into a separate `A.measureGroup`, not the streamed
  base scene. **Architecturally immune** — always renders true geometry regardless of DLOD state.
  Clash pair detection itself is DB-driven (`A._currentClashRules`), not scene-state-dependent.
- **Picking** (`isBboxPlaceholder`, proven pick-exclusion, `picking.js:257`): clicking a DLOD box
  today correctly no-ops by design — already-shipped, already-correct behavior. Net effect worth
  stating explicitly, not just inherited silently: an element currently rendered as a box is
  unclickable/unselectable until it promotes back to real mesh. Acceptable for TM today; carry the
  same tradeoff into nav-scope DLOD rather than treating it as a new gap to close.

**Required design addition, not optional:** any DLOD engage-condition (§2's `hideForProxy`-equivalent
for nav-scope, and any future room-level mechanism) needs an explicit exemption clause — same shape
as TM's existing FRONTIER/RECENT-always-real rule — that also covers "GUID is currently part of an
active Find isolation set" (`A.activeGuidFilter`, set by `filterByGuids` at `panels.js:840`, already
resident in memory — check that, not a new lookup). Without it, isolate-then-fly on a large building
will visibly flicker/revert the moment nav-scope DLOD engages. Clash needs no equivalent change.

## 7. Distance is the wrong axis — line-of-sight (occlusion) is the right one (2026-07-20)

Real math (SQL against LTU_AHouse_extracted.db) exposed that §0-§2's 50m-radius rule, reused
verbatim from `TM_DLOD_SCALE.md` §9, gets BOTH directions wrong: at a central interior point,
**54.5%** of ALL 125,698 elements (every storey — the building is only 17m tall but 425m×286m wide,
so 50m trivially reaches vertically, it's a horizontal-only constraint in practice) fall within the
"stays real" radius, including elements in adjacent rooms that ARE occluded by a wall 5m away. At
the tour's own logged real orbit radius (`r=255`, from an actual `[WALK] Orbit: r=255 from 27°` log
line earlier this session), **0% of elements** fall within 50m — the entire building would render
as boxes during the tour's own opening orbit / closing bird's-eye shot. Distance is not a proxy for
occlusion; it gets facade-at-255m (visible, nothing occluding it) and next-room-at-5m (invisible,
a wall in the way) backwards.

**Correct axis: line-of-sight / occlusion, not distance.** This codebase already has the
infrastructure for a CPU-side version — `docs/internal/CINEMATIC_RENDERING.md` names an existing
R-tree spatial index (built for clash detection + pick-proximity, explicitly "not a render
optimization" today) that could drive a camera→element raycast test against nearby opaque geometry,
without the GPU occlusion-query latency/driver-inconsistency/per-object overhead concerns raised
earlier in this file's history (see the conversation this file's git history sits under). This
would supersede, not sit alongside, §0-§2's distance-only test — it directly answers "is a wall in
the way," which distance never could.

**Not yet designed or witnessed — two prerequisites before any implementation:**
1. CPU cost of ~125k R-tree-accelerated raycasts per frame/tick is UNVERIFIED — needs its own
   measurement, same MEASURE BEFORE ESTIMATING discipline as everything else in this file.
2. The pop/flicker risk of any hard visibility toggle — see §8, gated, do not build ahead of it.

## 8. Cross-fade/hysteresis investigation — GATED, Fable to REPORT ONLY, no implementation without
explicit user sign-off (filed 2026-07-20)

**This section exists because this exact PROBLEM CLASS has already failed four times in this
codebase** (`prompts/done/S259_BATCHEDMESH.md` §"Why DLOD broke before (S258 disable reasons)",
`prompts/done/S261_DLOD_MILLION.md`, `prompts/done/S262_DLOD_REENABLE.md` — all Three.js/bim-ootb,
not the unrelated Blender-side DLOD lineage in `prompts/done/S179_dlod_rtree_handoff.md`/
`S193_dlod_auto_linker.md`, a DIFFERENT tool with its own separate history, cited here only as a
secondary cross-check, not conflated with this one):
- **S258**: DLOD disabled. Named causes: "wrong-angle onset — thin elements viewed edge-on flicker
  between LOD levels" + "Hourglass conflicts — DLOD and Time Machine both control visibility, they
  fight" (`S259_BATCHEDMESH.md` line 61-63).
- **S259**: proposed BatchedMesh `setGeometryIdAt` as the fix for S258's flicker.
- **S261**: fully built distance-threshold geometry swap (bbox↔real, `setGeometryAt`), INCLUDING a
  **hysteresis band already** (promote at 50m, demote at 80m, explicitly "prevents flicker at
  boundary" — `S261_DLOD_MILLION.md` line 95). Still retracted — hysteresis alone did not fix the
  edge-on flicker or the TM-visibility-fight problem (`TM_DLOD_SCALE.md` §1: "Died on: edge-on
  flicker on thin elements, TM visibility fights, pick broken until promotion completes").
- **S262**: tried to re-enable it, argued "breaks pick" was overstated. Still disabled today
  (`_useDlodPath = false`, hard-set, guarded by whitebox test `dlod_visibility_only`/`§WB_DLOD_VIS`
  asserting `noSwapPath && noPromote` — **do not flip this guard**).
- **Cross-fade/opacity-blend specifically was NAMED but never built or tested** —
  `S261_DLOD_PBR_MILLION.md` lists "Bbox → full mesh fade transition" as a future-steps item; the
  whole lineage was retracted before reaching it. Distinguish clearly from hysteresis (tried,
  proven insufficient alone) — cross-fade is a genuinely untested idea, not a repeat of a known
  failure, but also not a known success.
- **One real difference worth testing on its own merits, not assumed identical:** S258-S262's
  flicker trigger was DISTANCE-threshold crossing (thin element flips LOD at 50m/80m). §7's
  line-of-sight idea triggers on OCCLUSION-boundary crossing (camera rounds a corner) — a different
  geometric event. Same general risk CLASS (any hard per-frame visibility toggle can pop), not
  guaranteed the same failure mode.

**Fable's task — INVESTIGATE AND REPORT, explicitly NOT implement into any shipped path:**
1. Build a small, isolated prototype (scratch/throwaway, not wired into the live viewer) that
   reproduces the S258/S261 edge-on-flicker condition on a thin element (a wall or beam, viewed
   near edge-on, crossing a hard visibility/geometry threshold) — confirm the failure reproduces
   before testing any fix, per this project's `feedback_verify_checker_before_code_under_test.md`
   doctrine (know the failure is real before claiming a fix addresses it).
2. Add an opacity cross-fade (N frames, box and real mesh both rendered at complementary opacity
   during the transition) across that SAME reproduced case. Measure and report: does the flicker
   actually go away, does the transition read as smooth, what N (frame count/duration) was needed,
   any new artifact introduced (z-fighting between the two overlapping representations during the
   fade, since both are visible simultaneously — a risk unique to cross-fade that hysteresis never
   had, since hysteresis never draws both at once).
3. Report findings in this file (§8, dated entry) — reproduction confirmed y/n, cross-fade result,
   concrete numbers, any new artifact found. **STOP THERE.** Do not wire it into `dlod.js`,
   `streaming.js`, or any live path. Do not touch `_useDlodPath` or the `§WB_DLOD_VIS` guard test.
   Do not open a PR. The user reviews the findings and decides whether to proceed — this is a
   report-back task, not an implementation task, given four prior attempts in this exact problem
   class all ended in retraction.
4. **Not authorized without this report:** any live code change to DLOD/box-proxy visibility
   mechanics in `viewer/dlod.js`, `viewer/streaming.js`, or `viewer/time_machine.js`'s DLOD
   sections. §0-§4's nav-scope box-proxy extension and §7's R-tree line-of-sight idea both wait on
   this investigation's outcome before any implementation work starts.

### §8 FINDINGS — 2026-07-21 (Fable, isolated prototype, REPORT ONLY — no live code touched)

**Prototype:** scratchpad-only (`xfade_proto/proto.html` + playwright runner, session scratchpad —
throwaway per task 1; key § lines quoted below are the durable evidence). Thin wall 6m×3m×0.08m
viewed 3° off edge-on (projects a ~4px sliver — S258's condition), real mesh ↔ wireframe bbox proxy
using the shipped §9 look (`MeshBasicMaterial` wireframe, opacity 0.4), hard 50m threshold.
Fully deterministic: frame-index-driven camera, 640×480, SwiftShader software GL, full-canvas
`gl.readPixels` every frame. Metrics are numeric per the FUNDAMENTAL LAW — no screenshots:
`changedPx` (pixels with any-channel delta>10 vs prev frame) and `sumAbsK` (total abs pixel delta,
kilo-units); a CTRL run (always-real, same path) gives the camera-motion baseline (max 7K/frame,
mean 6-8 changedPx). Two camera paths: T = genuine traverse 90→30→90m (2 real crossings);
O = oscillation 50±2.5m + ±0.5° yaw wobble (the flicker trigger). 17 runs total.

**1. Reproduction: CONFIRMED.** Hard toggle on the oscillating path flips 14×/400 frames
(`§XFADE_RUN path=O mode=HARD flips=14 ... maxChangedPx=194`), each flip a 36K single-frame delta
vs 7K motion baseline — a 5× pop repeating every ~30 frames = the recorded edge-on flicker. On the
traverse path each genuine crossing pops 29K in one frame (`§XFADE_POPSUM path=T mode=HARD
worstFrameSumExcessK=29@f134 ctrlMaxFrameSumK=7`).

**2. Hysteresis alone (S261's 50/80 band): exactly matches its historical "insufficient" record.**
Kills oscillation completely (O-path flips 14→0) but the pop at a genuine crossing is BYTE-IDENTICAL
to hard toggle (29K@f134, same frame, same magnitude) — hysteresis fixes decision instability, does
nothing for the swap itself. The prototype independently re-derives why S261 retracted.

**3. Cross-fade alone: NOT a fix either — two new findings.**
(a) **First-frame depth-occlusion step, N-independent:** the first fade frame carries HALF the hard
pop (15K for N=10, 17K for N=20 — same regardless of N) because the real mesh, even at 5-10%
opacity, still writes depth and instantly occludes the proxy's back wireframe lines. A hard
structural pop hiding inside the "smooth" fade.
(b) **On an oscillating decision, fade converts binary flicker into sustained shimmer:** O-path
FADE10 still flips 14×, opacity never settles, mean churn 47 changedPx/frame — WORSE than hard
toggle's 24 (`§XFADE_RUN path=O mode=FADE10 ... meanChangedPx=47`).

**4. The working combination — hysteresis + cross-fade N=10 + depthWrite:false while mid-fade:**
depthWrite-off during the transition removes finding 3a entirely: worst per-frame excess drops
29K (hard) → 11K (naive fade) → **7K — indistinguishable from the camera-motion baseline itself**
(`§XFADE_POPSUM path=T mode=FADE10DW worstFrameSumExcessK=7@f143 ctrlMaxFrameSumK=7`; same for
FADEHYST10DW). Hysteresis supplies decision stability (0 oscillation flips, O-path identical to
CTRL), fade+DW supplies a pop-free transition. N=10 frames sufficed; N=5 and N=20 were both
slightly worse pre-DW (12K/13K) — no reason found to go longer than 10.

**5. Z-fighting (the risk unique to cross-fade): STABLE in this environment.** Worst case tested —
box-shaped wall means proxy and real mesh are EXACTLY coplanar — 58 static frames with both drawn
at 50/50: zero changed pixels across all three variants (`§XFADE_ZFIGHT mode=ZSTATIC ...
totalChangedPx=0 verdict=STABLE`, ditto ZSTATICDW). Caveat: SwiftShader is a deterministic software
rasterizer; a real-GPU spot check on the user's machine is a cheap prerequisite before any go.

**6. Costs/limits found:** draw calls double per transitioning element for N frames (returns to 1
after — transient, bounded); both representations must render in the transparent pass mid-fade
(sorting cost at scale untested). **Biggest implementation-reality gap, untested here:** the
prototype fades a standalone `Mesh` material — the live viewer's real geometry is
InstancedMesh/BatchedMesh with SHARED materials, so per-element opacity needs per-instance alpha
(shader patch) or temporarily hoisting transitioning elements into an overlay mesh; neither is
trivial and neither was prototyped. Also: cross-fade addresses ONLY the pop — it does nothing for
S258's second named cause (TM/Find visibility-ownership fights); that remains §6's exemption-clause
design, a separate mandatory piece of any implementation.

**Verdict for user review:** flicker mechanism reproduced and killed in isolation by
hysteresis + N=10 cross-fade + mid-fade depthWrite-off — worst-frame delta equal to normal camera
motion, zero oscillation churn, zero static-overlap instability (software GL). Open before any
implementation decision: real-GPU z-fight/artifact spot check, and the batched/instanced
per-element-opacity question in finding 6. STOPPED HERE per gate — no live wiring, no PR,
`_useDlodPath`/`§WB_DLOD_VIS` untouched.

### §8 FINDINGS ADDENDUM — 2026-07-21 same day (user: "test against all assumptions before Go")
Both open items from the entry above are now POC'd — same isolated-prototype discipline
(`xfade_proto/proto2.html`), this time mirroring the LIVE stack exactly: the viewer's own three.js
r185 lib copy, BatchedMesh + InstancedMesh + shared `MeshStandardMaterial` per streaming.js §S280d
routing, directional+ambient lighting. Every test ran TWICE — SwiftShader AND this machine's real
GPU (`§P0_GL renderer=ANGLE (NVIDIA... RTX 4060 Laptop GPU ... OpenGL 4.5.0)`, via system Chrome
headless-new + `--use-angle=gl`). Verdicts identical across both renderers except one 1-pixel
nuance noted below.

**A. Real-GPU check: CLOSED, no longer needs the user's machine.** The full proto1 suite rerun on
the RTX 4060 reproduces every SwiftShader conclusion: hard pop 33-34K vs 10K motion baseline,
hysteresis byte-identical pop (33K), fade 12-13K, fade+depthWrite-off **8K — below the 10K
baseline**; z-fight static-overlap STABLE (0 changed px, 58 frames, all three variants) on real
NVIDIA hardware (`proto_gpu.log`).

**B. Per-element opacity on the real mesh types — one NO, two YES:**
- **BatchedMesh built-in alpha: FAILS** (`§P1_VERDICT BATCH_ALPHA=FAILS`, both renderers). r185's
  per-instance `_colorsTexture` is RGBA `Float32Array`, but poking the alpha texel has ZERO pixel
  effect — the batching shader multiplies RGB only. No supported per-element fade inside
  BatchedMesh without patching the batching shader chunk itself (deeper surgery, untested,
  unnecessary given the next finding).
- **Overlay-hoist (Find-Panel `_hlOverlay` pattern): PIXEL-IDENTICAL through the whole cycle**
  (`§P3_VERDICT OVERLAY_SWAP=PIXEL_IDENTICAL swapChg=0`, both renderers). `setVisibleAt(slot,
  false)` + same-frame standalone `Mesh` with same geometry/matrix/material = 0 changed pixels;
  `material.clone()` swap = 0; fading the clone leaves the neighbor element byte-stable (0 changed
  px in its region across all fade steps); restore = 0 vs original baseline. **This is the
  BatchedMesh fade path** — and it never perturbs the shared material at all.
- **InstancedMesh per-instance alpha via onBeforeCompile attribute patch: WORKS**
  (`§P2_VERDICT INST_ALPHA=WORKS`, both renderers): faded instance changes monotonically, sibling
  instance byte-stable, outside region byte-stable, and the transparent-flip itself is 0-delta.
- **Transparent-pass flip at alpha=1** (`§P4`): 0-delta on SwiftShader; on real GPU, 1 (one) changed
  pixel at sub-measurable magnitude with overlapping instances — effectively safe, but the overlay
  approach avoids even that by never flipping the base mesh's material.

**Remaining scale consideration (named, not POC'd — needs the real viewer, i.e. implementation):**
overlay-hoist costs 1 extra draw call per concurrently-fading element for N frames. A camera
rounding a corner could start hundreds of fades in one tick; cap/stagger concurrent fades (or an
InstancedMesh overlay for the fading cohort) is the obvious shape, but measuring that belongs to
the implementation phase this gate still guards. All assumptions testable in isolation are now
tested; still no live wiring, no PR, `_useDlodPath`/`§WB_DLOD_VIS` untouched.

## 9. IMPLEMENTATION DESIGN — v1 (2026-07-21, user said "proceed"; §8 gate satisfied by the two
dated findings entries above; this section is the Spec-First artifact the code cites)

**Module:** new `viewer/dlod_nav.js` (IIFE-wrapped per `feedback_browser_iife_wrap_engines`),
loaded after `time_machine.js` in `viewer.html`. TM's DLOD internals are NOT modified — the four
prior retractions all involved coupling visibility systems; nav-scope is a sibling that reuses the
PATTERN (box-build query, distance+frustum test, wireframe look, pick-exclusion flags) with
mutual exclusion instead of shared state. `_useDlodPath`/`§WB_DLOD_VIS` untouched.

**Engage gate (all must hold, checked every tick — any failure = full disengage+restore):**
`_navPillOn && app.activeBuildingTotal > 50000 && !app.streaming && !app._tmOn (TM owns visibility
when open) && !A.activeGuidFilter (§3 scope decision: Find isolation = full disengage) &&
!A.activeStoreyFilter && A.hiddenDiscs.size === 0 (storey/disc filters own per-element visibility
— same conflict class as Find, same remedy) && !cinema (A._cinemaOrbitActive, new 3-line public
mirror of effects.js's private _cinemaActive) && !A._maxqActive && !A._stillRefineActive (§3:
Alt+C/Alt+P excluded)`.

**Decision rule (per element, §8 combo):** hysteresis two-state — promote to real when
`dist ≤ 50m AND bounding-sphere in exact frustum`; demote to box when `dist > 80m OR sphere+5m
margin outside frustum` (the +5m frustum margin is the angular analogue of the 50/80 distance
band — rotation jitter at the frustum edge must not re-trigger, same reason S261 added 50/80).
Between bands: state holds. Transitions run a 10-frame opacity cross-fade with `depthWrite:false`
on the fading materials (§8 FINDINGS #4).

**Transition mechanics — overlay-hoist for ALL three mesh types (§8 ADDENDUM B: BatchedMesh has
no per-instance alpha; overlay-hoist measured pixel-identical; used uniformly for simplicity):**
demote = hide real slot (Mesh.visible / InstancedMesh zero-scale caching `_origMatrix`, same
convention as `A.filterInstancedMesh` / BatchedMesh `setVisibleAt(false)`) + same-frame standalone
real-copy Mesh (geometry: own for Mesh; shared geo + `getMatrixAt` for InstancedMesh; for
BatchedMesh a new `bm.userData.slotGeo[slotId] = geo` reference recorded at flush — one additive
line in streaming.js, contract structures untouched) + standalone box-copy (wireframe clone);
real-copy fades 1→0 while box-copy fades 0→0.4; on completion overlays are disposed and the box
instance in the nav box-set goes visible. Promote = exact inverse. **Concurrency cap 128:**
transitions beyond the cap in one tick SNAP (today's shipped TM-DLOD behavior — graceful
degradation, not a new failure mode). Fades cancel+snap instantly on disengage.

**Box set:** own per-discipline wireframe `InstancedMesh` set, built with the same
`element_transforms JOIN elements_meta` query + `isBboxPlaceholder` (pick-exclusion, picking.js:257)
+ new `isDlodNavProxy` marker; never registered in `_instanceMeta`/`_batchMeta` (landmine 1);
disposed on pill-off/building switch.

**Driver:** own rAF loop, alive ONLY while the pill is ON (pill OFF = zero listeners, zero per-
frame work — W-DLOD-NAV-EQUIV is structural). Per frame: guard check (flag reads), camera pose
signature compare (TM camguard's string shape); evaluate transitions only on pose change or active
fades.

**Pill:** panels.js tool-registry entry (sibling of 'fly'), same box glyph as `tm-lod`, default
OFF, `isActive` highlight; on <50k buildings the toggle no-ops with a toast + `§DLOD_NAV_GATE`
log (registry is static; TM-style dynamic hiding not available there — accepted v1 tradeoff).

**Witness hooks:** `window.__dlodNav` = {mutations, active, boxed, fades, snaps} + `§DLOD_NAV_*`
log lines; `window.__dlodNavAudit()` recomputes the wanted partition from scratch and returns
mismatch count (drives W-DLOD-NAV-PROXY). W-DLOD-NAV-PERF runs on THIS machine's real GPU
(headless hardware-GL path proven in §8 ADDENDUM) on LTU + a city-view case, OFF vs ON, draw
calls + frame time. W-DLOD-NAV-NO-REBUILD asserts zero `§PERF_INCR_INDEX` and zero
`_instanceMeta`/`_batchMeta` registrations from nav-DLOD during a sustained orbit.

### §9 addition — USER-DICTATED mid-implementation (2026-07-21): "bboxes must not shine thru"
TM's shipped DLOD has an X-ray artifact: wireframe boxes write no face depth, so a boxed region
shows EVERY box through every other box (and through boxed facades). Nav-scope v1 must improve on
this: each per-disc wireframe box InstancedMesh gets a PAIRED depth-only InstancedMesh
(`colorWrite:false, depthWrite:true`, opaque pass ⇒ renders before the transparent wireframes;
same instance matrices, same zero-scale hiding; `isBboxPlaceholder` pick-excluded, marker
`isDlodNavDepth`). Effect: boxed masses self-occlude — only the visible outer shell's wireframes
render. Wireframe look itself unchanged (feedback_no_fake_lod_unbreakable). Fading overlay boxes
(10-frame transitions) deliberately skip the depth pass — a mid-fade element must not pop
neighbors' occlusion; 10-frame inconsistency accepted. TM's own pill keeps its current behavior
(out of scope here; port later if the user asks).

## 10. SHIPPED — 2026-07-21 (PR #935, auto-merge armed)
§9 v1 implemented as `viewer/dlod_nav.js` + pill (panels.js, sibling of Fly Tour) + slotGeo record
(streaming.js) + sw v836. All §4 witnesses PASS on real GPU (RTX 4060, LTU 122k, headless
hardware-GL — `w_nav_run4.log`):
- **W-DLOD-NAV-EQUIV PASS** — pill OFF: `mutations=0` across a 240-frame scripted sweep.
- **W-DLOD-NAV-PERF** (first real numbers for this mechanism, MEASURE-BEFORE-ESTIMATING satisfied):
  OFF baseline wide orbit **112.7ms/frame, 15,675 draw calls** (LTU free-orbit truly runs ~9fps
  today). ON, fully boxed: **17.3ms, 16 draw calls (6.5×)**. Close-in quarter-orbit at 25m with
  29,815 promoted real elements: **52.1ms (2.2×)**, 2,304 cross-fades executed.
- **W-DLOD-NAV-PROXY PASS** — `__dlodNavAudit()` mismatch=0 at settled pose after the promote leg.
- **W-DLOD-NAV-NO-REBUILD PASS** — 0 `§PERF_INCR_INDEX` post-engage; 16 box meshes, 0 registered
  in `_instanceMeta`/`_batchMeta`.
- **Restore PASS** — pill off: 0 nav meshes left in scene, `mutations` symmetric (521,022 total).
**Two mid-implementation corrections that ARE the perf story (recorded for future DLOD work):**
(1) per-slot hiding alone (zero-scale/setVisibleAt) cut only 15,675→13,639 calls — LTU's scene is
~16K SMALL MESH OBJECTS (meshAggs=16104), so the real lever is mesh-LEVEL `visible=false` when
every slot of a mesh is boxed (same `anyVisible` lever as `A.filterInstancedMesh`); (2) first
witness run was vacuous-GIGO (`REALIDX entries=0` — placeholders-only scene from a bad symlink;
8 draw calls should have been the tell vs the known-healthy 15-16K) — caught by reading the log,
not the verdicts. **Known honest costs:** eval spike 25-50ms per 150ms-throttled re-evaluation
while the camera moves (122k index; amortized mean stays 17ms); close-in p95 88.9ms; §7's
line-of-sight axis and any eval chunking remain future work, separate go.

### §10 live-user confirmation — 2026-07-21, Firefox on LTU (user: "working.. aesthetics ok, no
hangs.. slightly improved speeds")
Field log during a Fly Tour: `§DLOD_NAV active≈19-22k boxed≈100-103k eval_ms=9-18` — eval cost on
the user's machine is 9-18ms (vs 25-50ms in the headless witness runs), well inside the 150ms
throttle. Fades pinned at the 128 cap every eval with started=500-1500 (continuous camera motion →
constant transition churn; cap+snap behaving as designed). Depth-pass aesthetics approved.
**Why only "slightly improved" there:** an INTERIOR tour keeps ~20k elements real across most of
the ~16k mesh objects, so mesh-level visible=false rarely triggers — the 6.5× win is the WIDE-orbit
/aerial regime (everything boxed → 16 draw calls); interior legs are bounded by in-view real
geometry, and Firefox's missing WEBGL_multi_draw raises its per-instance BatchedMesh cost baseline
regardless. Expected shape, stated in §5 all along (aerial=box-proxy; interior's real lever is
room-level occlusion, still data-blocked). Remaining named lever for the tour experience: the
TOUR_ROUTE_CACHE.md §5 re-opened IDB fix (planning hang), separate go.
**Same-day field addendum:** user ran Night (N) + Alt+G GI simultaneously with nav-DLOD — "i am
fine with it." Compatible by design (lighting/post-processing modes don't touch per-element
visibility; not gate conditions). Their log also captured the largest live transition wave yet:
`started=47817 eval_ms=34` — one eval absorbed a ~48k-element wave (128 faded, rest snapped via
FADE_CAP) in 34ms, no hang: the cap's graceful-degradation path confirmed at full scale.

### §10 addendum — 2026-07-21 later same day: #942 re-measure + in-flight smoothness (PR #943)
**PR bim-ootb#942 (peer session's idle-deferred rooms warm-up in `toggleDlodNav`) re-measured on
the RTX 4060 as requested:** §DLOD_NAV_ROOMS wall-clock = **952-1188ms** across three runs (their
SwiftShader-confounded 50-100s estimate confirmed as a ~50-100× software-rendering contention
artifact). No W-DLOD-NAV-PERF regression (ON sweep 19.9-21.9ms across runs, same 3245 draw calls).
The change honors the room-blind review conditions (ON-branch only, fire-and-forget, engage
machinery untouched).
**User "still lagging in flight" root-caused with a 600-frame real-tour-flight profile:** the
150ms-cadence monolithic eval cost 42ms/hit (28% duty cycle). **PR #943: scan chunked to ~16k
elements/rAF tick (1.3-1.7ms/chunk, pass ≈8 frames)** → flight mean 96.1→79.5ms, frames>50ms
466→300/600, ON-sweep p95 53.8→37.3. **Negative result, recorded in-code and here — do not
re-try without new numbers:** a per-frame TRANSITION_BUDGET (384) measured WORSE (throttles the
engage/demote wave, scene stays half-real, sweep mean 19.9→92.5ms, draw calls 3245→7984);
transitions are NOT the flight bottleneck. **Honest remainder:** flight p50 ~114ms is genuine
in-view interior geometry cost — §5 track 2 (room-level occlusion, blocked on 1.3% containment
coverage) is the only lever left for interior legs; distance-based DLOD is done squeezing there.

**Prerequisite CLEARED 2026-07-21** — `prompts/done/CONTAINMENT_LTU_STOREY_ALIAS.md` (bim-compiler
PR #55): the 1.3% figure was a storey-naming mismatch (ARC/STR/MEP each spelled the same floor
differently), not a source-data gap. `compile_rooms.py` fix raises LTU_AHouse coverage to 24.2%
(314→30,409 rows), MEP now included.
**Correction — no OCI redistribution needed, ported to the client-side engine instead
(2026-07-21):** rooms aren't baked into a per-building DB and shipped as a static OCI artifact —
`A.ensureRooms()` compiles them client-side, on demand, from whatever DB a user (including one
bringing their own IFC building this codebase has never seen) has loaded, via `room_walker.js`
(the JS port of `compile_rooms.py`). That file had the IDENTICAL bug. bim-ootb PR #950 ports the
fix verbatim (parity witness confirms byte-identical numbers to the Python result) and bumps
`ROOM_WALKER_V` v2→v3, so the existing stage-3 version-stamp self-heal (`§NEEDLE_VERSION_STALE`,
`TOUR_ROUTE_CACHE.md` §6's cache-bust included) recompiles any already-loaded building once and
picks up the wider containment automatically — no DB redistribution, no per-building action at all.
Room-level occlusion (this file's track 2) is unblocked but still NOT implemented — that remains
the next session's job.

## 11. Room-level occlusion — STUDY TASK (2026-07-21, dispatch-ready — study before implementing,
per user directive: "It can be studied by the session first before doing")

**Do not implement from this section alone.** It names the mechanism to reuse, the real numbers
that motivate it, and the open design questions that must be resolved WITH CODE/MEASUREMENT before
any implementation PR — same discipline as §8's "GATED, report only" and §9's "IMPLEMENTATION
DESIGN" that came after it. First pass on this task = answer §11.2 with citations + numbers, write
the answers into this file, THEN a separate go for implementation.

### 11.1 GIVEN — what already exists to reuse (verified in code, not assumed)
- **Containment data is wide enough to build on:** `rel_contained_in_space` now covers 24.2% of
  LTU_AHouse (30,409 rows, all disciplines) post-`CONTAINMENT_LTU_STOREY_ALIAS.md`; self-heals to
  any other building automatically via the version stamp (§10 above).
- **The box-proxy state machine already exists and is the right mechanism to extend, not replace:**
  `dlod_nav.js` maintains `_boxIndex[guid] = {mesh, idx, matrix, pos, radius, state:'real'|'box'}`,
  promoting/demoting per-element on a `PROMOTE_DIST`/`DEMOTE_DIST` + frustum test, with a 10-frame
  cross-fade and a `FADE_CAP` snap-fallback at scale — all of this is DISTANCE/FRUSTUM-based and
  untouched by containment. Room-level occlusion is a SECOND criterion for the same promote/demote
  decision (element's room ≠ camera's current room ⇒ eligible to box), not a parallel system.
- **The `>50k` engage gate (`NAV_MIN_ELEMENTS`, `dlod_nav.js:26`) means dlod_nav today NEVER
  engages on Terminal (48,428 — confirmed live by the user this session, pressing 'o' correctly did
  nothing) or any building under that line** — room-level occlusion, if wired only into
  `dlod_nav.js`, would inherit the same size-blindness unless deliberately generalized. Whether
  that's correct (rooms only matter at scale) or wrong (a Duplex-scale building could still want its
  own interior-vs-exterior split during Fly Tour, cheaply) is 11.2 Q1.
- **Fly Tour ('L' key, `toggleFlyAround` → `_prepareGraphTour` → `A.ensureRooms()`) has NO size
  gate at all** — unlike `dlod_nav`'s 'o' key, it warms rooms on every building regardless of scale
  (confirmed this session: 'L' fires the NEEDLE chain on Terminal, sub-50k, where 'o' does not).
  Room occlusion tied to Fly Tour specifically would not inherit the 50k blindness.
- **Room-per-camera-position is already computed, but only at PLANNING time, not per-frame:** the
  Fly Tour route planner (`tour.js` §FLY_ROUTE / RoomGraph) already knows which room each `flyPts[i]`
  stop belongs to (seen live this session: `{"name":"⚠ VÅNING 1 R95"}` etc. in a real `§TOUR_PATH`
  dump) — this is a PATH-INDEXED lookup (which stop am I nearest, in the planned sequence), not a
  live point-in-room-rect test against the camera's actual current XY. Whether the planned-path
  index is accurate enough for occlusion (camera stays close to its planned path) or a live
  point-in-rect test is needed (same rect data the containment join already uses) is 11.2 Q2.

### 11.2 OPEN DESIGN QUESTIONS — resolve with code + numbers before implementing
1. **Scope by building size or not?** Measure room-count and elements/room on a below-50k building
   (Terminal: 48,428 elements — real per-discipline containment numbers already in
   `CONTAINMENT_LTU_STOREY_ALIAS.md`'s regression set) vs LTU (122k). Is the per-room element count
   (8.9 avg on LTU) similarly worth culling on a smaller building, or does `dlod_nav`'s existing 50k
   gate already mark the point below which this isn't worth the engineering? Answer with the actual
   numbers, not a guess.
2. **Current-room detection: reuse the planned-path index, or a live point-in-rect test?** The
   planned-path index is free (already computed) but only as accurate as "camera follows its planned
   route" — measure how far the ACTUAL camera position (not the plan) drifts from the nearest
   planned stop during a real flight (same `§FLYPATH_INIT`/camera-position log lines already used
   for other Fly Tour proofs this session) before assuming it's good enough. A live test would reuse
   the exact rect-containment math `compile_rooms.py`/`room_walker.js` already do (`abs(x-cx)<=sx/2
   && abs(y-cy)<=sy/2`), just evaluated against the CAMERA position instead of an element's, at
   whatever per-frame or per-N-frame cadence `dlod_nav`'s existing 150ms eval throttle suggests.
3. **Aerial/orbit legs must not lose the room test's own §5-track-1 lesson:** §5 already found that
   a blanket "hide anything not on camera's current storey" rule breaks aerial/orbit shots (no
   single current storey then). Room-level occlusion inherits the same failure mode one level finer
   — during an orbit/aerial `TOUR_PATH` action, there is no "current room" either. Reuse §5's own
   fix shape: gate room-occlusion to INTERIOR legs only (same signal §5.3 already named — storey/
   room membership from the path-planning data marks which actions are interior vs aerial).
4. **Interaction with the existing distance/frustum promote-demote, not a second box mesh set:**
   should room-mismatch be a THIRD demote condition alongside `DEMOTE_DIST`/frustum-margin in the
   SAME `_boxIndex` state machine (an element can be demoted for EITHER reason), or does it need its
   own hysteresis tuning (a room boundary is a hard cut, not a soft distance band — does the same
   10-frame cross-fade look right for "camera walked through a doorway," or does that need to be
   instant)? Needs a real interior-flight video/log comparison, not assumed to be identical.
5. **Below-50k buildings without `dlod_nav` engaged at all:** if Q1 concludes small buildings ARE
   worth it, room occlusion there has no existing box-proxy host to plug into — would need either
   generalizing `dlod_nav`'s engage gate (room-mismatch alone, no size floor) or a separate minimal
   mechanism. Do not build a third DLOD engine (`time_machine.js`'s own is already a cautionary
   precedent per its `assignStoreyByZ` mirroring note in `ROOM_INJECTOR_NEEDLE.md`) — extend
   `dlod_nav.js`'s existing gate/state machine if this is needed, per that doc's needle-sharing
   recommendation applied to this mechanism too.

### §11.2 FINDINGS — 2026-07-21 (STUDY dispatch: measured, not guessed; no implementation code
touched, per the §8 "report only" discipline)

**Method:** Q1 via the CURRENT walker (`lib/room_walker.js` v3 `§CONTAINMENT-ALIAS`, run in Node
against fresh copies of both extracted DBs — harness validated by reproducing §11.1's exact LTU
numbers: 30,409 rel rows / 24.2%). Q2/Q4 via a real Fly Tour flight on LTU in headless
hardware-GL Chrome (`§HARNESS_GL renderer=ANGLE (NVIDIA ... RTX 4060 Laptop GPU ... OpenGL
4.5.0)`), 122,330 elements loaded, CINE-GRAPH route (`§FLY_ROUTE storeys=4 stops=13/14 pts=97
illegalChords=0/92`), **12,628 frames recorded at 60.0fps (16.7ms mean)** of which **11,168 were
interior `flyPath` frames (~186s)**; per-frame camera position + walkActionIdx sampled via rAF,
analyzed offline (numeric log evidence only — no screenshots). Q3 by code reading of the
`df5def1` checkout. Code moved since §11.1 was written: `NAV_MIN_ELEMENTS` is now
`dlod_nav.js:28` (was :26), and #943's `EVAL_CHUNK=16384` chunked eval (`dlod_nav.js:39`,
`_evalChunk` ~:307) landed on top.

**Q1 — Scope by size: YES, keep the >50k gate. Room occlusion below it buys ≤4.2%.**
- LTU (125,698 meta rows): **422 rooms** (301 non-empty), contained **30,409 = 24.2%**, avg
  **72.1**/compiled room, **101.0**/non-empty, median 52, max 962.
- Terminal (48,428): **54 rooms** (40 non-empty), contained **2,045 = 4.2%**, avg 51.1/non-empty,
  median 34, max 176.
- **Correction to Q1's own prose:** "8.9 avg on LTU" was the PRE-alias number (§5's 1,608 rows /
  181 rooms at 1.3% coverage). Post-alias reality is 72–101 elements/room.
- Verdict: Terminal's theoretical max cull is ≤2,045 elements (4.2% of the building) — not worth
  the engineering, and sub-50k buildings render at the healthy baseline without dlod_nav anyway
  (§12). The existing 50k gate marks the right cutoff.
- Parity debt noted (not this task's scope): `scripts/compile_rooms.py --write` yields only
  **314** LTU rel rows — the `§CONTAINMENT-ALIAS` fix lives in the JS walker only (ported there
  per bim-ootb PR #950); the Python source of truth has not received it back.

**Q2 — Planned-path index DISPROVEN; live point-in-rect test required.**
- Measured camera drift from the nearest planned `flyPt` during interior legs: **mean 4.14m /
  median 3.60m / p95 10.37m / max 14.19m** (3D; XY-only mean 3.57m, max 13.56m). The drift is
  by-design: CatmullRom(0.5) smoothing (`tour.js:1160`) + adaptive SMOOTH damping
  (`tour.js:1204-1211`) keep the camera off its stops continuously.
- Direct verdict number: nearest-planned-stop room attribution agrees with a live point-in-rect
  test on only **39.3%** of interior frames (3,731 match + 656 both-null of 11,168). Median drift
  3.6m exceeds the half-width of LTU's typical rooms.
- The live test is cheap: one camera point vs 529 rect rows per eval (same
  `abs(x-cx)<=sx/2 && abs(y-cy)<=sy/2` math, `room_walker.js:1356`) — trivial next to dlod_nav's
  150ms eval throttle. ⚠ It must reuse the walker's own floor-anchor Z resolution
  (`_joinKey`/`floorAnchors`, `room_walker.js:1311-1337`): the harness's naive nearest-cz matching
  produced observable cross-floor picks (VÅNING_2 → VÅNING_1) at rect overlaps.
- Design fact for the null policy: the camera was inside a compiled room **83.7%** of interior
  frames; **16.3%** in no room (doorways/circulation) — "no current room ⇒ no room-based demote"
  needs to be an explicit state, not an edge case.

**Q3 — Interior-vs-aerial signal EXISTS at action granularity (confirmed, with one correction).**
- Interior = `act.type === 'flyPath'` (points are graph room/door/stair nodes at eye height,
  `tour.js:602-604`, actions assembled `:869-870`). Aerial/exterior = `orbit` (`:757-758`),
  `moveTo` Entrance (`:761`), Bird's eye (`:882`), Final (`:894`), and the closing `lookAround`
  — distinguishable from interior look-around beats because only the finale carries
  `lookAtX/lookAtY` (`:900-901`); the interior beats between flyPath segments (`:871-875`) hold
  camera position, so interior-ness persists through them.
- **Correction to §11.1's claim:** the planner carries room display NAMES per point
  (`act.names`, filled only for room/exit/stairwp nodes at `:603`, `''` for doorwp/circ/spine) —
  per-point room GUIDs and storeys (`ifcTrail`, `:576-605`) are computed then DROPPED (not in
  `_buildGraphRouteInner`'s return, `:642`). So the leg-level flag is free today; per-point room
  membership is not — moot given Q2's verdict anyway (live test wins).

**Q4 — Neither instant cut nor bare fade: same 10-frame fade machinery + a ~10-15-frame
membership-stability window.**
- Same flight: **236 room-membership changes** across 11,168 interior frames (**1.27/s** avg);
  gap between changes **median 19 frames (317ms)**, mean 49.3 (822ms), p95 257; 160 non-null room
  visits, median dwell 500ms; **17 A→B→A flap sequences returning within ≤10 frames**.
- Reading: a hard cut at 1.27 changes/s with ~100-1000 elements flipping per room (avg 101
  non-empty, max 962) would be constant popping. A bare 10-frame fade (167ms @60fps) is ~half the
  median transition gap, and the 17 flaps land INSIDE one fade duration — churn, not smoothing.
- dlod_nav's existing machinery already fits: state owned at fade START (`dlod_nav.js:254`),
  `FADE_FRAMES=10` (`:32`), snap beyond `FADE_CAP=128` (`:33`, `:235`) — a room switch is mostly
  snap territory by size, and the cap's graceful path is proven at 48k/34ms (§10). Recommendation:
  reuse it unchanged, gate the room-mismatch criterion on membership being STABLE for ~10-15
  frames first — filters all 17 observed flaps at the cost of ~0.2s switch latency, well under
  the 500ms median room dwell.

**Q5 — Not needed (per Q1).** Terminal's 4.2% / ≤2,045-element ceiling doesn't justify any
mechanism below the gate. If sub-50k containment coverage ever materially improves, the named
(NOT built) generalization is: split `dlod_nav.js`'s single engage gate (`NAV_MIN_ELEMENTS`,
`:28`, checked at `:68`/`:411`) into per-criterion gates — size floor stays for distance/frustum
boxing; the room-mismatch criterion would gate on containment coverage (contained-fraction ×
element count), not raw size. No third DLOD engine, per `ROOM_INJECTOR_NEEDLE.md`.

**§11.3 framing note:** PR bim-ootb #954 (landed `df5def1`) shipped the "Find Panel Room lens
calls `A.ensureRooms()` itself" follow-up — §11.3's second non-goal is now DONE upstream, so it
drops out of the non-goal list naturally; nothing folded into this scope.

**VERDICT: READY for an IMPLEMENTATION DESIGN section (§8→§9 pattern) — no further data needed.**
The design is pinned by the numbers above: (a) live per-eval point-in-rect current-room test with
the walker's floor-anchor Z-join, never the planned-path index (39.3% agreement kills it);
(b) room-occlusion active only during `flyPath` legs (signal exists today, Q3); (c) room-mismatch
as an additional demote criterion inside the existing `_boxIndex` state machine, using the
existing fade/snap path plus a ~10-15-frame membership-stability window (Q4); (d) explicit
"camera in no room ⇒ no room-demote" state (16.3% of interior frames); (e) scope stays >50k /
dlod_nav-hosted (Q1/Q5). Implementation is a separate go, gated on user review of these findings.

### 11.3 Non-goals (v1, same discipline as §3/§7)
- Time Machine occlusion — separate feature, separate go, once §11 settles the mechanism shape here
  (`ROOM_INJECTOR_NEEDLE.md`'s recommendation: share `A.ensureRooms()`, not the box-proxy engine
  itself, which is Fly-Tour/nav-specific machinery TM would need its own engage-gate story for).
- Find Panel's `isolateRoom` warming its own rooms (separate, smaller, already-named follow-up in
  `ROOM_INJECTOR_NEEDLE.md`) — unrelated to this occlusion mechanism, don't fold it in here.

## 12. Perf-history correction — the "R185/BatchedMesh was great, then it got worse" arc (2026-07-21,
user pushed back on a first-pass wrong theory; corrected trace below, cite this before re-asking)

**User's memory, verbatim shape:** first r185+BatchedMesh+DLOD pass felt great on LTU, then perf
"took a turn" during continuous testing since — **without ever pressing N (night) or H (shadow)**.
That last fact rules out a rendering-pipeline/feature-toggle explanation (a first-pass theory blaming
S277's Cinematic Rendering/night-mode lighting cost was wrong and is retracted here, in writing, so a
future session doesn't re-propose it). The real cause traces to the MACHINE, not the app:

1. **2026-05-26/27 — driver update, no reboot (`prompts/done/S280c_PERF_VERIFY.md`).** Two days after
   S276 (r184 BatchedMesh, "LTU 122K verified smooth", `45a393b62`), an NVIDIA driver bump (595.71)
   landed but the machine was never rebooted onto the matching kernel. Firefox's WebGL context
   silently fell back to the **Intel iGPU**, and LTU rendered at **83K draw calls** instead of its
   healthy count. Root-caused and fixed the next day (S280c, `47a69ee`): reboot + threshold/
   consolidation/render-gate fixes restored the real baseline — **~16K draw calls** (2500
   BatchedMesh @ ~87K slots + 13600 InstancedMesh @ ~35K instances).
   **Correction (2026-07-22, user pushback — "how could it run fast in FF for a while" if Firefox
   never had multi_draw?):** the ORIGINAL framing of this point ("no `WEBGL_multi_draw` there")
   wrongly implied Firefox had multi_draw before the iGPU fallback and lost it. Re-verified live,
   empirically, on THIS machine (WebDriver/geckodriver session against real system Firefox, real
   NVIDIA driver 595.71.05 active): `WEBGL_multi_draw` is **absent in Firefox regardless of which
   GPU it's bound to** — confirmed just now, not assumed from old docs. The real mechanism
   (`viewer/scene.js:47-54`): Three.js's `BatchedMesh` doesn't fail without `multi_draw` — it
   silently falls back to **one GL draw call per instance instead of one combined multi-draw call
   per bucket**. Firefox has ALWAYS been on that slower per-draw path on this GPU, including during
   the "fast" period right after S276 — nothing about multi_draw ever changed in Firefox. What
   actually changed was which PHYSICAL GPU was doing the (always-fallback) rendering: a discrete
   RTX 4060 has enough raw throughput to absorb the extra per-draw-call overhead and stay smooth;
   the Intel iGPU does not. The iGPU swap broke it, not a multi_draw regression.
2. **2026-05-24 → broke later → fixed 2026-07-13, i.e. 8 days before this conversation** (memory:
   `project_machine_chrome_firefox_gpu_launchers.md`). The SAME DAY as S276's r185 WebGPU test,
   `~/.local/share/applications/google-chrome-gpu.desktop` was created with PRIME render-offload +
   Vulkan/WebGPU flags, silently shadowing the normal Chrome icon. "Worked for a while, then broke"
   (a later driver bump or Chrome auto-update likely flipped it) — every load started failing WebGL
   context creation outright. Sat broken, invisible from inside the viewer, until 8 days ago.
3. **Permanent, not a regression — see point 1's correction above for the live-verified detail.**
   Firefox on this GPU never supports `WEBGL_multi_draw`, on any GPU it's bound to; BatchedMesh's
   per-instance-draw-call fallback is Firefox's constant state here, not something that broke.
4. **Today's §10 baseline (112.7ms/frame, 15,675 draw calls, wide-orbit OFF) matches S280c's
   "healthy" ~15-16K number almost exactly.** The system is in its normal state today, not a degraded
   one — §9/§10's `dlod_nav.js` box-proxy is a NEW layer stacked on top of that honest baseline, not a
   restoration of lost multi_draw throughput. The felt "took a turn" was most likely one or both
   machine incidents above landing inside the same continuous-testing window, silently, then getting
   fixed without the app itself ever telling the user why.

**Forward-looking question, answered:** if/when this machine's GPU/driver state improves further
(e.g. multi_draw reliably active, no more launcher/driver flukes), does that ADD ON TO today's
`dlod_nav` win, or regress it? **Adds on — the two layers are orthogonal, not competing.**
`dlod_nav`'s win comes from cutting the NUMBER of elements considered "real" at all (distance+frustum
demote to a ~16-draw-call box set); a faster/healthier BatchedMesh path underneath speeds up exactly
the promoted/real subset the same way it always did, and the boxed floor (~16 draw calls, wireframe)
is already near-free regardless of multi_draw. `W-DLOD-NAV-EQUIV` (pill OFF ⇒ byte-identical to
today's ordinary rendering) is the structural guarantee: OFF mode automatically inherits whatever the
underlying baseline is, good or bad, and ON mode multiplies a cut on top of it. **One honest caveat:**
the measured multipliers (6.5× wide-orbit, 2.2× close-orbit) are multipliers against TODAY's ~15-16K
baseline — if that baseline itself gets cheaper, there is less headroom to cut, so the *relative*
multiplier will likely shrink even though the *absolute* frame time keeps improving. Re-measure
(§4 witnesses), don't assume the 6.5×/2.2× numbers carry over to a faster baseline unchanged.

## 13. IMPLEMENTATION DESIGN v1 — room-mismatch demote, STEP 1 ONLY (2026-07-22, user: "spec and
implement a step first here so i can test incremental perf change"; §11.2 FINDINGS is the gate this
satisfies, same §8→§9 pattern)

**Scope of this step, deliberately narrow:** wire ONE new demote criterion (camera's current room ≠
element's contained room) into the existing `dlod_nav.js` `_boxIndex` state machine, live-toggleable
from the console with zero reload, so a real before/after frame-time number can be pulled on THIS
machine before any UI/pill work is done. Full UX polish (a proper pill, a user-facing toggle, default-
on decision) is explicitly deferred to a follow-up step once this number is in hand — this is the
smallest slice that produces a measurable, comparable perf delta.

**Module:** extend `viewer/dlod_nav.js` in place (no new engine — §11.1/§11.2(c) already ruled out a
parallel system). New code additive only; existing distance/frustum demote path untouched when the
new criterion is disabled.

**Current-room detection (§11.2 Q2's verdict — live test, not the planned-path index):**
- Per eval chunk (reuse the existing `EVAL_CHUNK`/`_evalChunk` cadence, `dlod_nav.js:39`/~:307 —
  do NOT add a second timer), compute the camera's current room via the SAME point-in-rect test
  `room_walker.js:1356` already uses (`abs(x-cx)<=sx/2 && abs(y-cy)<=sy/2`), reusing its floor-anchor
  Z-join (`_joinKey`/`floorAnchors`, `room_walker.js:1311-1337`) — the study's harness got cross-floor
  mispicks from skipping this; do not repeat that.
- **Explicit no-room state:** camera outside all compiled rects (measured 16.3% of interior frames)
  ⇒ room-criterion contributes nothing that tick; distance/frustum alone still governs. Never treat
  "no room" as "any room" or "eligible to demote everything."
- **Membership-stability gate (§11.2 Q4):** only update "camera's current room" after N consecutive
  evals agree (N=10-15 frames — the study measured 17 A→B→A flaps inside a single fade window without
  this; a bare per-tick room read would reproduce that churn). Track a small pending-room counter
  alongside the existing per-tick state, reset on disagreement.
- **Gate to interior legs only (§11.2 Q3):** room-criterion active only while the current tour action
  is `flyPath` (`tour.js:602-604`/`:869-870`) or, outside a tour, free navigation (no cheap "am I on
  an orbit/moveTo leg" signal exists outside the tour context — treat non-tour free-nav as interior by
  default, since there is no aerial/orbit leg concept there; only tour-driven orbit/moveTo/Bird's-
  eye/Final legs (`tour.js:757-758,761,882,894`) explicitly disable it).

**Demote/promote integration — SAME state machine, one more OR'd condition:**
- Demote condition becomes: `dist > DEMOTE_DIST OR frustum-margin-exceeded OR (roomOcclEnabled AND
  hasCurrentRoom AND element.containedRoom !== currentRoom)`. Promote is the inverse AND-of-NOTs.
  This is additive to the existing hysteresis (`dlod_nav.js` promote/demote bands) — do not fork a
  parallel demote path.
- Reuse the exact same fade/snap mechanics already shipped: 10-frame cross-fade
  (`FADE_FRAMES`, `:32`), state owned at fade start (`:254`), `FADE_CAP=128` snap fallback (`:33`,
  `:235`). No new transition code.
- Elements with no compiled containment row (75.8% of LTU, per §11.2 Q1) are simply never eligible
  for the room-criterion — distance/frustum still apply to them unchanged. Do not treat "uncontained"
  as its own bucket.

**Step-1 testing lever (the actual deliverable for "test incremental perf change"), NOT a UI toggle:**
`window.__dlodNav.roomOcclEnabled` — a plain boolean, default `false`, flippable live from DevTools
console on an already-loaded, already-flying scene (no reload needed to A/B). When `false`, behavior
and performance must be BIT-IDENTICAL to today's shipped `dlod_nav.js` (this IS the v1 "equivalence"
witness — see below). A real pill/pref-panel toggle is out of scope for this step; note it as the
obvious next step once a real number justifies shipping it broadly.

**Witnesses (blocking, real GPU — headless hardware-GL per every prior witness in this file):**
- **W-ROOM-OCCL-EQUIV:** `roomOcclEnabled=false` ⇒ scene/mutations byte-identical to current shipped
  behavior across a scripted sweep (mirrors `W-DLOD-NAV-EQUIV`'s method exactly).
- **W-ROOM-OCCL-PROXY:** `roomOcclEnabled=true` ⇒ boxed set matches a from-scratch recompute of
  (distance-eligible OR frustum-eligible OR room-mismatch-eligible), `mismatch=0`, same audit-fn
  pattern as `__dlodNavAudit()`.
- **W-ROOM-OCCL-PERF (the actual deliverable):** on the SAME interior LTU flight profile the study
  used (flyPath legs, real GPU, `§HARNESS_GL` real RTX 4060 log line required), report frame time and
  draw calls `roomOcclEnabled=false` vs `=true`, back to back, same route, same machine state. This is
  the first real number for whether room-mismatch demote helps interior legs at all — §10's own
  honest remainder said interior legs are "bounded by in-view real geometry" and named this exact
  mechanism as the only remaining lever; this witness is what confirms or falsifies that, no more
  hand-waving. Log with a `§ROOM_OCCL_*` tag; read the log before any conclusion, per this project's
  Log Mandate.
- **W-ROOM-OCCL-STABILITY:** confirm the flap sequences the study found (17 A→B→A within ≤10 frames on
  the same real flight) no longer trigger a room-state change, using the stability-gate counter.

**Non-goals (this step):** no pill/UI, no default-on decision, no Time Machine port, no Find Panel
fold-in — all already named out of scope by §11.3, unchanged here. If W-ROOM-OCCL-PERF comes back
negative (no measurable win on interior legs), that is a valid, reportable outcome — say so plainly,
do not keep tuning to force a positive number.

### §13 RESULT — 2026-07-22, real RTX 4060, `bim-ootb` branch `feat/room-occlusion-step1` @ `9b5d9dc`
(committed locally only — worker/Watchdog role separation, not yet pushed/PR'd; pending user review)

Implemented exactly as specced above: `viewer/dlod_nav.js`'s `_wantedReal()` gets a third OR'd
demote condition (`roomMis`), a lazy building-keyed camera-room index + per-element room stamp
(`room_walker.js`'s new `buildCameraRoomIndex()`, hoisting the exact `_joinKey` floor-anchor math
`writeRooms()` already used, so the camera-side test can't drift from the containment-side one), a
12-frame membership-stability gate, and interior-leg gating via `app.walkActions[idx].type`. Console
lever `window.__dlodNav.roomOcclEnabled` (default false), zero pill/UI as scoped.

**All 4 witnesses ran on this machine's real RTX 4060 (headless hardware-GL, `§HARNESS_GL` confirmed
each run):**
- **W-ROOM-OCCL-EQUIV: PASS.** `roomOcclEnabled=false` byte-identical to `origin/main`'s shipped
  `dlod_nav.js` across 8 deterministic poses (4 rooms + 4 orbit points) — same hash, same
  real/boxed/mismatch at every pose. (First run raced streaming and showed a spurious 125,698 vs
  122,330 total mismatch; re-ran clean and it resolved — a harness timing artifact, not a code
  difference, logged here so it isn't re-investigated as a regression later.)
- **W-ROOM-OCCL-PROXY: PASS.** Boxed set matches a from-scratch recompute at 3 spots: inside a small
  room (`roomOnlyBoxed=17`), inside a large room spanning most of a floor (`roomOnlyBoxed=4165`), and
  outside all compiled rooms (`roomOnlyBoxed=0, active=false` — explicit no-room state holds).
- **W-ROOM-OCCL-STABILITY: PASS.** A synthetic 24× 5-frame A↔B room flap (faster than any of the
  study's 17 measured real flaps) produced **zero** room-state changes; a genuine hold was still
  accepted in 12 frames both directions — the stability gate filters churn without deadlocking.
- **W-ROOM-OCCL-PERF: small draw-call win, frame-time a wash — honest result, not a dramatic one.**
  Real interior-flight A/B on LTU (122k elements), `roomOcclEnabled` false vs true, same CINE-GRAPH
  route both runs: **OFF** frame_ms mean=106.16 median=113.0 p95=228.6, drawCalls mean=3328. **ON**
  frame_ms mean=105.63 median=117.0 p95=237.5, drawCalls mean=3229, roomChanges=30 (confirms the
  criterion engaged continuously, not a no-op run). ~3% fewer draw calls, essentially no frame-time
  change (p95 marginally worse in this one sample). Matches §10's own prior prediction exactly:
  interior legs are "bounded by in-view real geometry," and room-mismatch only additionally boxes
  elements already inside the distance/frustum-eligible set but in a different room — for a typical
  interior camera position that set is small relative to the ~110k already-boxed baseline, so the
  extra cut is real but modest. **Caveat: only one clean OFF/ON pair, not the full interleaved
  off/on/on/off design** — the headless Chrome session crashed (`Target page, context or browser has
  been closed`) mid-run on the 3rd of 4 planned legs, on two separate attempts, at the same point
  both times. A second independent OFF sample from the first crashed attempt (mean=108.70,
  drawCalls=3317) is consistent with this run's OFF (mean=106.16, drawCalls=3328), giving some
  confidence in the baseline despite the incomplete interleave. The repeated same-point crash is
  itself worth a follow-up look (possible resource accumulation across sequential in-page tour
  restarts) — not chased down as part of this step.

**Where this leaves step 1:** mechanism works exactly as designed and is provably safe (EQUIV/PROXY/
STABILITY all clean), but the perf payoff on THIS building/route is small, not the dramatic win §10
hoped for. Open call for the user: ship this modest win as-is (PR + pill/UI as a follow-up step), hold
for a bigger-payoff scenario (denser multi-room interiors, lower-end hardware where every draw call
counts more), or treat the STUDY's mechanism as validated-but-not-yet-worth-shipping broadly.

**RESOLVED 2026-07-22 — user: "that is what we been waiting for."** Pushed + PR bim-ootb#962 opened,
honest result stated plainly in the PR description (not oversold). This closes step 1.

## 14. GPU capability warning — spec (2026-07-22, user: "u may launch an agent to do now")

**Problem this solves (§12's own root-cause finding):** both real machine incidents that degraded
this app's perf on this machine were **100% silent** — no in-app indication, ever. The iGPU fallback
(§12 point 1) and the broken Chrome launcher (§12 point 2) were each discovered only via felt
slowness over days/weeks, not anything the app told the user. The app already computes renderer
capability at load (`§RENDERER_CAPS` log line, `viewer/scene.js:47-54`) — it just never surfaces
that to the user or compares it against anything. This spec closes that gap.

**Design:**
- At the SAME point `§RENDERER_CAPS` already logs (`scene.js`, right after line 54), read a stored
  "last known good" renderer signature from `localStorage` (key `bim_gpu_lastgood`:
  `{renderer, multiDraw, ts}`).
- **No stored baseline (first-ever run on this browser/profile):** just store the current
  signature. No warning — nothing to compare against yet.
- **Compare current vs stored:** flag DEGRADED if `multiDraw` went `true→false`, OR the renderer
  string looks like it moved from a discrete GPU to an integrated one (simple, documented
  heuristic: previous string matched `/nvidia|amd|radeon/i` and current matches `/intel|uhd|iris/i`
  — do not over-engineer a full GPU classifier here, this heuristic only needs to catch the two
  real incidents already seen, not every conceivable GPU swap).
- **DEGRADED:** show one dismissible toast/banner ("Rendering fell back to a slower GPU (<renderer>)
  — if this seems wrong, check GPU drivers or reboot.") + a `§GPU_DEGRADED_WARN` console line. Do
  NOT nag every load once dismissed for the SAME degraded state — only re-show if the signature
  changes again (e.g. degrades further, or recovers then degrades again).
- **Same or IMPROVED:** silently update the stored baseline, no toast. An improvement (e.g. driver
  fixed, back on the dGPU) should refresh what "good" means going forward, not stay pinned to a
  stale weaker baseline forever.
- Cheap by construction: one `localStorage` read + conditional write, zero additional GPU queries
  beyond what `§RENDERER_CAPS` already does today.

**Witnesses (can run headless, no real degraded hardware needed — this is pure JS logic driven off
a mocked localStorage baseline, not a live GPU-swap):**
- **W-GPU-WARN-FIRSTRUN:** no stored baseline → no toast, baseline gets written.
- **W-GPU-WARN-DEGRADED:** stored baseline = discrete GPU + multiDraw=true; current = integrated +
  multiDraw=false → toast appears, `§GPU_DEGRADED_WARN` logs, baseline is NOT silently overwritten
  by the degraded reading (so the warning would still be meaningful if checked again without a
  session in between — confirm the actual persistence semantics chosen and pin them here).
- **W-GPU-WARN-RECOVERED:** stored baseline = degraded state; current = discrete GPU + multiDraw=true
  → no toast (improvement), baseline updates to the better state.
- **W-GPU-WARN-NONAG:** same degraded signature on two consecutive loads → toast shows once, not
  twice (confirm the exact "don't nag" mechanism, e.g. a session-only dismissed-flag vs a persisted
  one — pin whichever is chosen with a citation here).

**Non-goals:** no attempt to detect WHY the GPU changed (driver/reboot/launcher-flags) — this is a
symptom detector, not a diagnostic tool. No auto-fix action (reboot prompts, driver links) — just
visibility. No change to `§RENDERER_CAPS`'s existing log line itself.

## 15. Interior-lag POCs — GATED, REPORT ONLY, no implementation (2026-07-22, user: "wont it be
better to run POCs on all these? Once we get the formula right, then we know" — same discipline as
§8's cross-fade investigation: measure first, no live wiring, no PR, until reviewed)

**Why this is here:** discussing the "why does Fly still lag with only ~20k 'active' elements"
puzzle surfaced two live, UNMEASURED hypotheses — (1) per-triangle/vertex cost on top of element
count, (2) the `CINEMATIC_RENDERING.md` S277 FPS-notes estimate that "interior views: 60-70% of
in-frustum geometry is occluded" was never actually measured, just estimated back in May. Both are
worth a real number before anyone designs an implementation around them.

### POC-A: Triangle/vertex complexity of the "active" (real, non-boxed) set during interior flight
**Question:** is frame cost dominated by draw-call/CPU overhead (element count), or by raw
vertex/fragment cost (polygon complexity), or split roughly evenly?
- Extend the existing perf-harness pattern (`witness/harness.js`/`w_perf.js` from the room-occlusion
  task, or a fresh copy) to also sample `renderer.info.render.triangles` alongside `.calls`, per
  interior frame, same real-GPU interior LTU flight already used for W-ROOM-OCCL-PERF.
- Report: mean/median/p95 triangles-per-frame alongside the already-known draw-calls-per-frame
  numbers (mean=3328 OFF baseline). Cross-reference against the ~20k "active" element count to get
  an average triangles/element figure — compare that against a few known element classes (a plain
  wall panel vs a railing/stair/MEP run) to judge whether complexity is concentrated in a few
  high-poly classes or evenly spread.
- Cheap, quick — no new infrastructure, just an added sample point on an existing harness.

### POC-B: Real occlusion percentage — replace the untested "60-70%" estimate with a measured number
**Question:** of the ~20k elements distance/frustum currently calls "active" (real) during an
interior leg, how many are ACTUALLY visible (clear line of sight to camera) vs genuinely occluded
(a wall/floor/slab blocks the sightline) — right now, TODAY, on real LTU data, not a May guess?

**Stage 0 — REQUIRED FIRST, sandbox correctness proof (user: "i believe it is just algorithm maths,
in a sandbox, setup of test elements, cam moving to and fro, witnessing of values" — 2026-07-22):**
before this touches the real 122k-element LTU building at all, prove the line-of-sight math itself
is correct against a small, KNOWN-ground-truth synthetic scene — same methodology this file already
used for `§CAMIDX_SELF`/`§CAMIDX_XFLOOR` (room_walker's floor-anchor join proven against
self-consistent synthetic checks before trusting it on real data).
- Build a tiny synthetic scene (plain Node + `three` + `three-mesh-bvh`, no browser/GPU needed — this
  is pure geometry math): a handful of test elements with HAND-PICKED, known positions — e.g. one
  blocking wall plane between camera and a target box (expected: occluded), one target box with clear
  line of sight (expected: visible), one target partially behind a wall edge (expected: boundary case
  — decide and document which way it should classify).
- Move the camera through a small SCRIPTED sweep (to-and-fro along a line, not a full flight) —
  several fixed positions where the expected clear/occluded verdict for each test element is known
  in advance BY CONSTRUCTION (you built the geometry, you know the answer), not estimated.
- At each camera position, run the SAME raycast-occlusion function the real POC-B measurement below
  will use, and witness its output against the hand-computed expected verdict per test element per
  camera position — `mismatch` must be 0. This is the correctness gate: if the algorithm gets the
  synthetic case wrong, fix the math here BEFORE spending any time on the real building.
- Only once this passes cleanly does Stage 1 (below, real LTU data) proceed.

**Stage 1 — apply the proven algorithm to real LTU data:**
- **Reuse, don't rebuild:** `viewer/loader.js` already monkey-patches `THREE.Mesh.prototype.raycast`
  via `three-mesh-bvh`'s `acceleratedRaycast`, and `viewer/streaming.js` (~line 737-752) already
  computes a BVH bounds tree for every mesh geometry AND every BatchedMesh bucket
  (`computeBatchedBoundsTree`) during normal streaming — full-scene accelerated raycasting already
  exists, confirmed by direct grep, not assumed. A line-of-sight occlusion POC is a raycast from the
  camera to each candidate element's center (or bbox corners for a stricter test), checking whether
  anything else in the scene intersects that ray BEFORE reaching the target — reusing this exact
  existing acceleration structure, not building a new one.
- **Method:** during the same real interior LTU flight (headless hardware-GL, real GPU, same route
  already used for W-ROOM-OCCL-PERF), at a sampled cadence (every N frames, not every frame — this
  is a report-only cost measurement, keep the POC's OWN overhead from confounding the frame-time
  numbers being investigated), cast one ray per "active" element from camera position to element
  center. Classify: **clear** (ray reaches target unobstructed) vs **occluded** (something blocks
  it first). Report the real occluded fraction, compared directly against the "60-70%" estimate.
- **Known landmine to test for, not assume away:** a raycast can validly hit the TARGET element's
  own geometry first (self-intersection) — exclude the target mesh/instance itself from the
  occlusion test, the same way picking code already has to (`picking.js`'s existing self-exclusion
  pattern, if one exists — check before writing a new one).
- **From the measured occluded fraction, compute the CEILING, not a promise:** if X% of the active
  set is genuinely occluded, and (per §10's own finding) draw-call savings only materialize when a
  whole mesh/BatchedMesh bucket empties, report BOTH the raw occluded-element percentage AND a
  more honest bucket-level estimate (how many of LTU's ~16,104 mesh objects would have EVERY one of
  their visible instances occluded, vs how many have a mix of occluded+visible instances and
  therefore wouldn't save a draw call even with perfect occlusion culling). This second number is
  the real ceiling on what real occlusion culling could buy — report it even if it's much smaller
  than the raw occluded-element percentage.

### Deliverable
Write findings into this file (dated, cited, real numbers — same format as §11.2 FINDINGS/§13
RESULT) with an overall verdict: is real occlusion culling worth designing as a follow-up spec (§16
IMPLEMENTATION DESIGN, same §8→§9/§11→§13 pattern), not worth it, or is the honest bucket-level
ceiling too small to bother — same "negative result is a valid, reportable outcome" discipline as
every prior POC in this file. **No implementation code in this pass** — raycasting/sampling scripts
for the POC itself are throwaway measurement tools, not the eventual occlusion-culling mechanism.

### §15 FINDINGS — 2026-07-22 (Fable, REPORT ONLY — no shipped file touched; scripts + logs
committed locally in `/tmp/wt-occl-poc` worktree, branch `poc/occlusion-culling-study` off
`e84a079`, NOT pushed/PR'd per dispatch)

**Method + Stage 0 gate (per the user's "prove the algorithm maths in a sandbox first"):** the
line-of-sight classifier lives in ONE file (`witness/occl_classify.js` — center-ray, guid-based
self-exclusion mirroring picking.js's hit→guid resolution, non-element hits ignored, non-finite
distances guarded) and is used VERBATIM by both stages. Stage 0 (`witness/w_occl_stage0.mjs`, plain
Node + `three@0.184` + `three-mesh-bvh@0.8.0`, no browser): hand-placed AABB scene (wall 4×3×0.2 +
3 targets incl. a wall-edge boundary case), 21-pose to-and-fro sweep, expectations from an
INDEPENDENT segment-vs-AABB slab oracle + a 6-row hand-derived literal table.
**`§OCCL0_RESULT checks=63 mismatch=0 handChecks=6 handMismatch=0 selfFirstSeen=35 verdict=PASS`** —
boundary rule pinned: verdict follows the CENTER ray only (a half-exposed element with blocked
center classifies occluded). Stage 1 ran only after this gate. Both Stage 1 runs on real RTX 4060
(`§HARNESS_GL renderer=ANGLE (NVIDIA ... RTX 4060 Laptop GPU ... OpenGL 4.5.0)`), LTU 122,330
elements, DLOD-nav ON, `roomOcclEnabled` default false (shipped state), CINE-GRAPH tour route.

**POC-A RESULT — triangle load is trivial for the GPU; neither triangles nor draw calls explain
interior frame-time variance (`witness/w_occl_poca.log`):**
- 3,000 interior flyPath frames: frame_ms mean=109.03 median=119.4 p95=247.9 (cross-checks §13's
  OFF mean=106.16); triangles/frame mean=3,365,410 median=3,418,136 p95=4,181,646 max=4,704,151;
  drawCalls mean=3,262 median=3,333 p95=4,757; dlodActive mean=19,987 boxed mean=102,343 →
  **168.4 tris/active element, 1,032 tris/draw call** (`§POCA_TRIS`/`§POCA_PER_ELEMENT`).
- Correlations (`§POCA_CORR`): r(frame_ms,triangles)=**0.197**, r(frame_ms,drawCalls)=**0.190**,
  r(triangles,drawCalls)=0.950 (collinear — can't separate observationally). ~3.4M tris is nothing
  for an RTX 4060, yet frames average 109ms → per-triangle GPU cost is NOT the bottleneck; the cost
  rides on object/submission count + per-frame CPU work, consistent with dlod_nav.js's own prior
  W-DLOD-NAV-PERF finding ("the scene is ~15K small mesh objects, the object-level flag is the
  lever", dlod_nav.js ~:175) and §13's 3%-fewer-calls ⇒ frame-time-wash result.
- Census (`§POCA_CENSUS`, all 122,330 elements = 12,955,180 tris): mean 105.9 tris/el, median 92,
  p95 232, p99 828, max 10,726 — complexity is mostly FLAT, not concentrated. Biggest classes by
  triangle share: IfcFlowFitting 24.0% + IfcFlowSegment 20.1% (74,210 MEP elements, avg 62-97
  tris/el — share comes from COUNT, not per-element weight); concentration exists only in IfcDoor
  (606 els, avg 2,302 tris, 10.8%) and IfcFurnishingElement (242 els, avg 2,074 tris, 3.9%) —
  LOD-simplification candidates, but only ~15% of total tris combined. **Verdict: element/object
  count is the axis that matters; per-element polygon complexity is not LTU's problem.**

**POC-B RESULT — the May-2026 "60-70% of in-frustum geometry is occluded" estimate
(`docs/internal/CINEMATIC_RENDERING.md:91`, never measured) was a large UNDERestimate. Measured:
~99% (`witness/w_occl_stage1.log` + `w_occl_stage1_run1_nofrustum.log`):**
- Method: 8 camera poses captured DURING the live tour at 350-interior-frame cadence, then frozen
  and classified after it (full active set per pose, one center-ray per element vs all visible
  element geometry — keeps POC cost out of frame timing; POC-A owns timing). Self-exclusion path
  exercised for real (selfFirst up to 776/pose); `noCenter=0` — full coverage.
- **Run 2 (frustum-split, the honest one), `§POCB_SUMMARY`: occluded fraction of the ACTIVE set
  mean=99.4% median=100.0% min=95.9% max=100.0%; of the IN-FRUSTUM active subset (the estimate's
  own phrasing) mean=99.6% min=97.8% max=100.0%.** Active sets 19,983-36,720/pose; in-frustum
  14,186-36,457; clear-LOS elements were literally 1-826 per pose — an interior camera in LTU truly
  sees a few hundred elements; the ceilings/coverings (24,321 IfcCovering) wall off the 74k MEP
  elements above them.
- **Bucket-level ceiling — the number §15 said matters more — is NOT much smaller: objects whose
  EVERY active element is occluded = mean 98.5% (all-active rule) / 99.1% (in-frustum rule)**
  (`§POCB_BUCKET`, objWithActive 3,291-5,812/pose vs rendererCalls 3,755-5,885 — same magnitude, so
  bucket-emptying maps ~1:1 onto real draw calls). Run 1 (pre-frustum-split, higher-mismatch
  settle) independently agrees: raw 99.8%/bucket 99.5% — the conclusion is insensitive to the
  settle imperfection (audit mismatch was still 21-18,009 at pose freeze even with forced-dirty
  settle; both runs bracket it and agree).
- **Caveats, stated plainly:** (1) the POC's per-element CPU raycast took **91-279 s PER POSE** —
  this measurement method is ~4 orders of magnitude off real-time and is NOT the mechanism; any
  implementation needs GPU occlusion queries (the doc's own WebGL2 suggestion) temporally amortized,
  or a precomputed room/portal visibility structure — never per-frame CPU rays. (2) Center-ray
  granularity can misclassify a partially-visible element as occluded (Stage-0-pinned rule), so the
  raw % is a shade optimistic — but the margin over 60-70% is ~30 points; the conclusion survives
  any plausible correction. (3) One-ray-per-element ≠ pixel truth; bbox-corner rays would tighten
  it (documented option, not needed to answer the gate question). (4) Incidental code finding:
  `streaming.js:748-752` calls `window.computeBatchedBoundsTree` but NOTHING ever defines it — the
  BatchedMesh-BVH step in §15's premise has been a silent no-op guard all along (Mesh BVH via
  `loader.js:203` is real; batched raycast used three r184's native per-instance-visible path,
  which is what made this POC correct w.r.t. hidden slots anyway).

**OVERALL VERDICT: YES — real occlusion culling merits a §16 IMPLEMENTATION DESIGN spec.** The
measured ceiling is ~99% of active elements / ~98.5-99.1% of draw-call buckets during interior
legs — not the 60-70% guessed, and nothing like §13's ~3% room-mismatch cut (room-occlusion only
catches OTHER-room elements; this measures true line-of-sight). Emptying ~98% of buckets would take
interior frames from ~3,300-5,900 draw calls to a few hundred at most — a regime change POC-A says
attacks the RIGHT axis (object/submission count, not triangles). Honesty requirement for §16: §13
proved a 3% call cut moved nothing, so the spec's witness must A/B the actual frame time, not just
the call count — the win is plausible but only the implementation's own W-perf run can prove the
~99% visibility ceiling converts to frame-time. §16 must also pick a real-time mechanism up front
(GPU occlusion query w/ N-frame reuse, or compiled-room portal visibility) — the POC's raycast
method is measurement-only by construction.

## 16. IMPLEMENTATION DESIGN — real occlusion culling (2026-07-22, design only per §8→§9/§11→§13
discipline; no shipped code in this pass, a separate go implements it)

### 16.1 Why §13's mechanism undercounts the §15 ceiling — the gap this design closes
§13's room-mismatch demote (`viewer/dlod_nav.js` `_wantedReal`, shipped on branch `feat/room-
occlusion-step1` @ `e84a079`, NOT yet on `main` — confirmed live, `main`@`df5def1` still has only
the distance/frustum test) only boxes an element when `e.room !== _roomCur` (strict equality,
`dlod_nav.js:394` on that branch) — camera's current room vs the element's OWN room, nothing about
what's actually visible FROM the current room. Two things this misses that §15's ~99% figure
captures: (a) an element in a DIRECTLY-adjacent room through an open doorway is "mismatched" and
gets demoted even though it may be genuinely visible (a false demote §13's own 3-spot PROXY witness
didn't test for, because it only checked "inside a room" / "outside all rooms", never "in the next
room over"); (b) most of what §13 measured as only a 3% additional cut is because distance/frustum
had ALREADY boxed the majority of other-room/other-floor elements — the residual 3% is the SMALL
set that survived distance/frustum but failed room-equality. §15's true line-of-sight test, run on
the SAME building/route, found the ACTIVE set (already distance/frustum-real) is ~99% occluded —
meaning most of what distance+frustum call "real" is other-room/other-floor geometry seen through
open frustum cones but blocked by walls/slabs that room-EQUALITY never flagged because §13 never
asked "can the current room see that room," only "is it the same room."

### 16.2 Mechanism choice — portal/PVS on the compiled room graph, GPU occlusion query as fallback
**Primary: precomputed room-to-room Potentially-Visible-Set (PVS), not a per-frame GPU query.**
Reasons, weighed against the two options this file's own history already put on the table:
- **§7 already flagged GPU occlusion queries' "latency/driver-inconsistency/per-object overhead"
  as a reason to prefer a CPU/precomputed approach** over live hardware queries — that concern
  wasn't resolved, just deferred to when a real design was needed. It's needed now.
- **§15 Stage 1 already proved per-frame CPU raycasting is 4 orders of magnitude too slow**
  (91-279s/pose) — ruling out a live-raycast mechanism too. What's left is something computed
  ONCE (at compile/graph-build time) and looked up cheaply at runtime.
- **The exact data a portal/PVS needs already exists, unbuilt-on:** `common/room_graph.js`'s
  `buildGraph()` already emits `E1` edges (`{a:roomGuidA, b:roomGuidB, doorGuid, storey, kind:'E1'}`
  — a REAL door directly connecting two rooms, `room_graph.js:407-408`), `E2` (room↔CIRC/hallway),
  `E5` (corridor-junction↔junction), `E8` (corridor-room↔junction), and `E3` (stair groups bridging
  storeys ONLY at the stair's own footprint, `:518`, WalkerDoctrine's one trusted stair extractor).
  These edges ARE portals — a doorway or open corridor junction is exactly the "can room A see
  through to room B" relationship a PVS needs, already measured from real geometry, not invented
  for this task.
- **This is the SAME "prepared vocabulary, not real-time logic" principle
  `FLY_TOUR_CORRIDOR_GRAPH.md §VOCABULARY_NOT_REALTIME` already commits this codebase to** — extend
  the compiled graph (a new derived artifact: room→visible-room-set) rather than add a live
  geometric computation per feature. GPU occlusion query would be exactly the kind of "grow its own
  real-time... logic" that section warns against when a compiled alternative exists and is cheap.
- **GPU occlusion query is kept as a named fallback, not discarded**, for the specific case portal/
  PVS cannot handle by construction: open-plan/atrium spaces where room compilation itself is weak
  or absent (§10's storey-occlusion track already named atria/stairwells as the exception to
  opaque-floor culling), or if 16.4's Stage-0 validation shows the 1-hop portal assumption is too
  coarse. Spec for that fallback is in 16.5 — build it ONLY if 16.4 falsifies portal/PVS.

### 16.3 Portal/PVS design
- **Cells** = compiled rooms already in `spatial_structure` (RM_ guids), identical to what
  `buildCameraRoomIndex()` (`viewer/lib/room_walker.js:1285`, `e84a079`) already resolves a camera
  position to — reuse that function verbatim for "what room is the camera in now," unchanged from
  §13's step 1 (including its `_makeJoinKey` floor-anchor Z-join, `:1260` — the exact fix for the
  VÅNING_2→VÅNING_1 cross-floor mispick §11.2 Q2 found).
- **Portals** = graph edges of kind `E1`/`E2`/`E5`/`E8` on the SAME storey (a doorway or open
  corridor junction transmits visibility); `E3` (stairs) transmits ONLY between the two rooms/
  landings the stair group itself directly touches, never "the whole floor above is visible" — this
  mirrors §5's own atrium/stairwell carve-out for storey-occlusion, applied here at room grain.
  Door open/closed state is NOT modeled (no per-frame IFC operation-state exists) — a portal is
  always "open" for this purpose. This is a deliberate CONSERVATIVE bias: it can show a few elements
  through an actually-closed door (safe — a false "still real" costs a few extra draw calls, never a
  popping/missing-geometry bug), never hides something genuinely visible.
- **visibleRoomsFrom(roomGuid)** = `{roomGuid}` ∪ every room reachable via ONE portal hop (depth=1,
  default — see 16.4 for why this needs validating before trusting it, not assuming it). Computed
  once per room via a shallow BFS over the already-built graph's edge list, cached as
  `Map<roomGuid, Set<roomGuid>>` alongside the existing `_pathGraphCache`/`A.getRoomGraph()` (never
  a second graph cache — `FLY_TOUR_CORRIDOR_GRAPH.md` R2's "reuse one cache" rule applies here too).
  Trivial cost: room counts are in the 10s-100s (LTU 422, Terminal 54, §11.2 Q1) — a full BFS over
  every room is microseconds, done once per building load/graph rebuild, not per frame.
- **Runtime integration is a ONE-LINE change to §13's already-shipped-and-witnessed mechanism**,
  not a new state machine: `dlod_nav.js`'s `roomMis` (`:394` on `e84a079`) changes from
  `e.room !== _roomCur` to `!visibleRoomsFrom(_roomCur).has(e.room)` — the exact same OR'd
  criterion slot, the exact same `_roomActive`/interior-leg gating (`:385`), the exact same
  `ROOM_STABLE_N=12`-frame membership-stability gate (`:55`), the exact same fade/snap machinery
  (`FADE_FRAMES`/`FADE_CAP`, unchanged), the exact same explicit no-room state (`_roomActive =
  (_roomCur !== null)`, `:385`). Everything §13 already proved safe (EQUIV/PROXY/STABILITY) stays
  proved safe by construction — only the SET an element is checked against grows from "one room" to
  "one room's visible set."
- **No new UI/pill** (unchanged non-goal from §11.3/§13), no Time Machine port (unchanged non-goal).

### 16.4 Stage 0 — REQUIRED validation before trusting depth=1, same discipline as §15's own Stage 0
**Do not implement 16.3 with depth=1 assumed correct — measure it first**, exactly as §15 refused
to trust its own raycast classifier on real data before a synthetic ground-truth gate.
- §15's own POC scripts (`/tmp/wt-occl-poc`, branch `poc/occlusion-culling-study` @ `6d95ac1`,
  `witness/w_occl_stage1.js`) already compute a per-element CLEAR/OCCLUDED verdict per pose but only
  persist AGGREGATES (`witness/w_occl_stage1_poses.json` — confirmed by direct read: `activeTotal`/
  `occluded`/`clear` counts only, no per-guid rows) — the per-element verdict + guid exists
  transiently in that script's loop (`w_occl_stage1.js:180-194`) and is thrown away. The cheapest
  next step is NOT a new POC: add one line to dump `{guid, verdict, room: e.room}` per classified
  element in that same loop, re-run on the SAME already-captured 8 poses.
- **Validation question:** of the elements classified `clear` (genuinely visible), what fraction
  have `room === cameraRoom` (depth 0) vs `room` reachable via one portal hop from cameraRoom
  (depth 1) vs neither (depth 2+ or no portal path at all — a PVS miss)? Report this distribution
  per pose. If depth ≤1 covers e.g. ≥95% of `clear` elements, depth=1 is the right default. If a
  material fraction of `clear` elements sit at depth 2+ (e.g. visible down a straight double-door
  corridor two rooms over), extend BFS to depth=2 for `E2`/`E5`/`E8` (corridor/junction) edges only
  — corridors are architecturally the case where sightlines legitimately travel multiple hops,
  rooms behind closed doors are not.
- **Report the inverse too:** of elements the PVS at the chosen depth would mark visible (in the
  visible-set), what fraction the ground-truth raycast actually classified `occluded`? This is the
  "false real" rate — the cost of the conservative bias in 16.3. Must stay small enough that it
  doesn't erase §15's ~99% ceiling (a PVS that's visible-set-too-generous just reduces the win, it
  never causes a correctness bug, per 16.3's "safe direction" framing — but report the number rather
  than assume it's small).
- Only once this passes with a stated, defensible depth does 16.3 proceed to implementation.

### 16.5 GPU occlusion-query fallback — spec only, build IF 16.4 falsifies portal/PVS
Kept minimal, per §7's own caution, and scoped to the residual set portal/PVS leaves ambiguous —
never a full per-element per-frame sweep:
- **Per-BUCKET, not per-element:** issue `EXT_occlusion_query_boolean`/`ANY_SAMPLES_PASSED_
  CONSERVATIVE` (WebGL2) queries against each BatchedMesh bucket's own combined AABB proxy — buckets
  are already the draw-call unit (`§10`/`§13`'s own framing: emptying a BUCKET is what saves a
  call), and there are only ~2,500 of them (§0-2 baseline) vs ~110k+ elements, directly answering
  §7's "per-object overhead" concern by construction.
- **Only query buckets portal/PVS left UNRESOLVED** (i.e. contains a mix of visible-set and
  not-visible-set elements per 16.3 — a bucket portal/PVS can already fully resolve either way needs
  no query at all). This bounds query count to the genuinely ambiguous cases.
- **N-frame temporal reuse**, reusing the SAME `ROOM_STABLE_N`-scale window already tuned by §11.2
  Q4/§13's stability gate (~10-15 frames) rather than inventing a new constant — query issued, result
  read back ASYNCHRONOUSLY on a LATER frame (never a synchronous `getParameter` stall, the classic
  occlusion-query correctness bug), reused for the window, re-issued after.
- Non-goal unless 16.4 requires it: do not build this speculatively. Portal/PVS alone, if 16.4
  validates it, is the whole mechanism — this section exists so a future session doesn't have to
  re-derive the shape from scratch if 16.4 comes back negative.

### 16.6 Witness plan — frame-time A/B is the gate, not draw-call count (the session's own mandate)
Same EQUIV/CORRECT/STABILITY/PERF pattern §13 already ran, extended:
- **W-PVS-EQUIV:** mechanism flag off (default) ⇒ byte-identical to §13's shipped `e84a079`
  behavior across the same 8 deterministic poses (regression floor: this design must not silently
  change §13's OWN behavior when the new flag is unset).
- **W-PVS-CORRECT:** `visibleRoomsFrom()` at the chosen depth, computed fresh, matches a from-
  scratch BFS recompute; cross-checked against 16.4's ground-truth clear/occluded split (report
  agreement %, not just "it ran").
- **W-PVS-STABILITY:** reuse §13's exact flap-resistance test (24×5-frame A↔B flap ⇒ zero room-
  state changes) — the stability gate is unchanged, only what's being gated changed.
- **W-PVS-PERF — the decisive witness, per this session's charter:** real interior-flight A/B on the
  SAME machine/GPU/route already used for §13/§15 (LTU CINE-GRAPH tour), mechanism OFF vs ON,
  reporting frame_ms mean/median/p95 AND draw calls AND a NEW number — count of BatchedMesh buckets
  with zero active instances this frame (the thing that actually removes a draw call) — per frame,
  both runs. **Draw-call count alone is not sufficient evidence, per §13's own lesson** (3% fewer
  calls, frame-time unmoved) — report frame_ms honestly even if it doesn't move, and if it doesn't,
  investigate WHETHER the bottleneck is CPU submission overhead that persists even for skipped
  buckets (e.g. `renderer.render()`'s own per-object traversal cost) before concluding occlusion
  culling "doesn't work" — that would itself be a valuable, reportable finding, not a reason to keep
  silently retuning until a number looks good (this file's own standing discipline, §13/§15 both).
- **Non-goals (unchanged):** no pill/UI, no default-on, no Time Machine port, no cross-building sweep
  beyond LTU (+ Terminal as the small-building regression check, matching §13's own pattern).

### 16.7 Open questions for user review before the implementation go
1. Depth=1 vs depth=2-for-corridors default, pending 16.4's actual measured distribution (not
   guessed here).
2. Whether the "false real through an always-open portal" bias (16.4's inverse check) is small
   enough to ship without also modeling door open/closed state — and if not, whether that state is
   even extractable from source IFC data (`IfcDoor.OperationType` is static, not a live position).
3. Whether 16.5's GPU-query fallback is worth building at all if 16.4 validates portal/PVS cleanly,
   or should stay a documented-not-built option indefinitely (matching §15's "throwaway measurement
   tools, not the eventual mechanism" framing for its own raycaster).

### §16 RESULT — 2026-07-22, real RTX 4060, `bim-ootb` branch `feat/room-occlusion-pvs`
(worktree `/tmp/wt-occl-pvs` off `origin/main`@`be8f122`, committed locally only — same worker/
review-later split §13 used, not yet pushed/PR'd)

Implemented exactly as specced: `common/room_graph.js`'s `buildRoomPVS(graph, opts)` (0-1 BFS —
E1/E3 "door crossing" edges cost 1, all circulation edges free, `maxDoorCrossings` default 1) +
`viewer/dlod_nav.js`'s `pvsEnabled` console lever (default false), replacing §13's plain
`e.room !== _roomCur` equality with PVS set-membership inside the SAME `_roomMismatch()` helper
now shared by `_wantedReal` and `__dlodNavAudit` — a genuinely one-line semantic change at the
call site, gated behind its own default-false flag so §13's shipped behavior is provably untouched
when it's off.

**W-PVS-EQUIV: PASS.** `pvsEnabled=false` (with `roomOcclEnabled=true`, §13's mechanism actually
engaged, not both flags dormant) produced a byte-identical 4-pose fingerprint/audit record against
`origin/main`'s shipped `dlod_nav.js` swapped onto disk in its place (the harness's own established
swap method) — `witness/w_pvs_equiv_pvs-off.json` == `witness/w_pvs_equiv_shipped-main.json`,
diff clean.

**W-PVS-CORRECT: PASS.** Pure-Node sql.js run (`witness/w_pvs_correct.js`, no browser) against real
LTU_AHouse (371 rooms) and Terminal (55 rooms): shipped `buildRoomPVS` agreed guid-for-guid with an
INDEPENDENTLY-written reference 0-1 BFS on every room (`mismatchRooms=0` both buildings), every
room's own set contained itself (`selfMissing=0`). Sane, non-degenerate size distribution: LTU
avgVisible=34.4/371 rooms (min 1, max 110), Terminal avgVisible=18.0/55 (min 1, max 33) — the PVS
is neither "just yourself" nor "everything," consistent with corridor chains genuinely linking many
rooms for free while doors still cost a crossing.

**W-PVS-STABILITY: PASS.** Same 24×5-frame synthetic A↔B flap as §13's own test, `pvsEnabled=true`
this time: zero room-state changes during the flap, genuine holds still accepted in 12 frames, and
`§ROOM_PVS_BUILD` logged exactly once (not rebuilt per flap/room-change) — the PVS build is
correctly a per-building one-time cost, not coupled to room-membership churn.

**W-PVS-PERF: two attempts, both incomplete (same recurring crash — §16.8), but together enough to
call the direction.** The 4-run interleaved harness (`witness/w_pvs_perf.js`) crashed on the 3rd
leg both times with the identical `Target page, context or browser has been closed` failure §13's
own PERF run first hit (now seen 3×, see §16.8) — never got a full off1/on1/on2/off2 interleave
either time. Two independent attempts, reported together rather than cherry-picking the friendlier
one:
- **Attempt 1** (off1 only, on1 only): OFF frame_ms=86.26 drawCalls=3205 boxed=102849 | ON
  frame_ms=91.22 drawCalls=3244 boxed=102237 → delta +4.96ms, +39 calls.
- **Attempt 2** (got 3 of 4 legs — off1, on1, on2): OFF frame_ms=83.47 drawCalls=3216 boxed=102709
  | ON (on1+on2 avg) frame_ms=83.40 drawCalls=3223 boxed=102316 → delta **−0.08ms**, +7 calls.
- **Cross-attempt noise floor:** the SAME "off" condition measured 86.26ms in attempt 1 vs 83.47ms
  in attempt 2 — a 2.79ms spread with `pvsEnabled` never touched. Both attempts' on/off deltas
  (+4.96ms, −0.08ms) sit inside or near that noise band. **Honest read: frame_ms shows no
  consistent, distinguishable direction across two independent measurements — a wash, not a
  win and not a measurable regression either, matching §13's own PERF finding almost exactly**
  (a draw-call-adjacent change that doesn't move frame time).
- **What IS consistent across all 3 "on" samples vs both "off" samples: boxed count reliably
  DROPS** (−612, −394) — confirming the structural fact (PVS's visible-set is a strict superset of
  §13's same-room-only test) independent of the noisy frame-time reading. This costs nothing
  measurable and buys nothing measurable on THIS metric — it is a correctness change with a
  performance side-effect too small to detect at this noise floor, not a performance mechanism.

**W-PVS-STAGE0-VALIDATE (added beyond the original plan, per §16.4's own "required before
trusting depth=1" gate): informative but incomplete — real cost measured, not just assumed
small.** Same recurring crash, this time on pose 7/8, in the POC worktree
(`/tmp/wt-occl-poc/witness/w_pvs_stage0_validate.js`, PVS injected verbatim into that worktree's
page context — no files edited there, per §15's own "throwaway measurement, not shipped code"
convention). 6 of 7 completed poses landed `camRoom=none` (this tour's flyPath frames spent little
time inside a compiled room rect vs. doorways/circulation — a route/sampling artifact, not
necessarily representative of §11.2's own 83.7%-in-room figure from a different flight). The one
pose with usable data (`camRoom=RM_VÅNING_1_100`) gave a real, reportable number on the side that
matters: of 6,005 OCCLUDED elements with room data, the PVS still called **1,863 (31.0%)
"visible"** — the documented conservative-bias cost, and it is not small. n=1 pose is not enough to
generalize the exact percentage, but combined with W-PVS-PERF's direction, the shape is consistent
and not encouraging for a performance win from this mechanism alone.

**§16 VERDICT: portal-PVS is a CORRECTNESS fix with a performance effect too small to measure at
this harness's noise floor — confirmed across two independent attempts, not assumed.** It
demonstrably stops §13 from incorrectly boxing elements genuinely visible through an open doorway
(the failure mode §16.1 named), and does so safely (EQUIV/CORRECT/STABILITY all clean). It does
NOT reach §15's ~99% ceiling — the boxed-count direction is consistently down (fewer culled, as the
superset design predicts) but frame_ms shows no consistent direction across two measurements
against a ~3ms cross-run noise floor, and the Stage-0 cross-tab's 31% false-positive rate (n=1
pose, real but not yet generalized) shows the conservative bias has a real, non-trivial cost on the
occluded side. That ceiling is dominated by occlusion INSIDE what a room-adjacency graph already
calls "visible" — same-room partitions, furniture, ceilings hiding MEP directly overhead, floor
slabs — nothing a room graph can see, no matter how it's tuned. Closing that gap needs a mechanism
that judges visibility at object/region grain, not room grain. §17 below specs that mechanism.

**Open call for the user:** ship §16 as a modest correctness fix (console-lever only, as scoped, no
default-on decision per §11.3/§13's own non-goals — it costs nothing measurable to leave available),
or hold it pending §17's outcome and ship both together. Either is consistent with this file's own
"negative-but-real result is reportable, not a failure" discipline (§13, §15).

### §16.8 Standing infrastructure finding — recurring headless-Chrome crash on long real-GPU runs
Both of today's long real-GPU witness runs (§16's own PVS-PERF, and the Stage-0 validation) hit the
identical `Target page, context or browser has been closed` failure that §13's own PERF run first
reported ("the repeated same-point crash is itself worth a follow-up look... not chased down as
part of this step"). Now observed a 3rd time, always on a long-running (minutes, not seconds) real-
hardware-GL headless session doing sustained heavy work (either a multi-leg tour flight or repeated
multi-second CPU raycasts). Still not root-caused (possible resource accumulation across sequential
in-page tour restarts, or a headless-Chrome memory/handle ceiling under sustained real-GL load) —
flagging again, explicitly, so a THIRD recurrence doesn't get re-discovered as a surprise. Named,
not fixed; a real follow-up if long real-GPU witness runs are going to keep being this file's
standard method (they should — screenshots/estimates are exactly what this file's own "math
discipline, not screenshots" rule exists to replace).

## 17. IMPLEMENTATION DESIGN — hierarchical GPU occlusion culling (2026-07-22, design only, per
§8→§9/§11→§13/§15→§16 discipline; motivated by §16's own verdict that portal-PVS cannot reach the
~99% ceiling, and by a live side-investigation into whether this codebase's existing R-tree
(clash detection) or MeshBVH (raycasting) structures could drive it for free)

### 17.1 Why neither existing hierarchical structure is directly usable — verified, not assumed
Prompted externally (a pasted second-AI suggestion) that the project's existing spatial R-tree is
"precisely the hierarchical bounding structure needed" for GPU occlusion queries. Checked directly,
empirically, rather than accepted on description:
- **The SQLite `elements_rtree` (`viewer/measure.js:143`, clash detection/pick-proximity) DOES
  maintain a genuine multi-level internal hierarchy** — confirmed by building the real 122,330-
  element R-tree in Node/sql.js and querying its `_node`/`_parent` shadow tables directly:
  **3,555 internal nodes, real parent→child rows** (e.g. `[2,373]`, `[3,55]`). The hierarchy is
  not hand-wavy; it is real and SQLite maintains it whether or not anything ever reads it.
- **But it is not walkable for this purpose as shipped.** Each `_node` row's bounding box is an
  OPAQUE PACKED BINARY BLOB (SQLite's internal R*-tree cell format) — not queryable columns. The
  R-tree's two proven wins in this project (fast Bonsai preview, fast clash detection) both go
  through its SUPPORTED interface — a flat SQL range query (`WHERE minX<=? AND maxX>=?...`) that
  never requires touching node internals; SQLite walks its own tree invisibly and just returns
  rows. Getting a specific node's actual extent for GPU-query traversal would need a from-scratch
  binary decoder for that cell format — real, bounded, documented work, but not "already there."
- **`three-mesh-bvh`'s `MeshBVH` (already loaded for raycasting, §15) is real and directly
  walkable**, but scoped PER MESH/BATCHEDMESH-BUCKET over TRIANGLES, not across buckets. Draw-call
  buckets in this codebase group by DISCIPLINE (`dlod_nav.js`'s own `_buildBoxes`: `byDisc[d] =
  byDisc[d] || []`), not spatial locality — a single bucket's own bounding box typically spans the
  whole building, so "is this bucket's box occluded" would almost never return true. MeshBVH is the
  right tool for a LATER step (once a spatial region is known-hidden, toggle per-instance
  visibility for whatever of that bucket's instances fall inside it — the SAME per-instance
  mechanism `dlod_nav.js`/BatchedMesh's `setVisibleAt` already use today), not for discovering which
  regions are hidden in the first place.
- **The piece that is actually missing: a hierarchy over ELEMENT POSITIONS, cutting across
  disciplines/buckets, with real (non-opaque) traversable nodes.** Neither existing structure gives
  this. Building one needs NO new query and NO new data: `dlod_nav.js` already holds every
  element's `{pos, radius}` resident in memory (`_boxIndex`, built once per building load from
  `element_transforms`) — a small in-memory spatial tree over that already-resident data is the
  actual missing piece, and it is cheap (see 17.2).

### 17.2 The index — build once per building, over already-resident element data
- **Input:** `_boxIndex`'s existing `{pos, radius}` per element (already in memory, zero new SQL).
  A real AABB (not just a bounding sphere) is preferable for tighter culling — `_buildBoxes` already
  queries `bbox_x/y/z` per element (`dlod_nav.js` ~line 101) and could stash it alongside `pos`/
  `radius` at the same build step, at negligible extra memory cost (3 more floats/element).
- **Structure:** a plain median-split BVH (or an existing small library, e.g. the same family as
  `rbush` — real traversable JS objects with `{minX,minY,minZ,maxX,maxY,maxZ,children|leaf}`, never
  an opaque blob), built ONCE per building load/graph-rebuild event (same cadence as `_roomIdxEnsure`/
  `_pvsEnsure`'s own building-keyed cache). Cost estimate: building a comparable SQLite R-tree over
  the real 122,330-row set took 2.65s wall-clock in-process (measured this session) — a pure-JS
  in-memory median-split build over the same row count, with no DB round-trip, should be
  comparable or faster; MEASURE this directly before committing to it as cheap, not asserted here.
- **Coordinate frame — a named landmine, learned from this project's own history:** build the index
  in the SAME space `dlod_nav.js`'s existing frustum/sphere tests already use (Three.js world
  space, via `camPos`/`e.pos` — NOT raw IFC coordinates). This project has hit a translation/frame
  mismatch bug class before (`FLY_TOUR_CORRIDOR_GRAPH.md`'s §WALKER-PHASE-SENSITIVITY /
  §PATCH-FRAME-GUARD sagas) — do not reintroduce it here by building the tree in a different frame
  than the one it will be queried against.

### 17.3 Traversal + GPU occlusion query design
- **Query target:** WebGL2 `EXT_occlusion_query_boolean` (`ANY_SAMPLES_PASSED_CONSERVATIVE`,
  cheaper/less precise than exact — appropriate here, a coarse ancestor-node test doesn't need
  pixel-perfect answers). One query per VISITED internal node, against a proxy (the node's own AABB
  rendered as a simple invisible box, depth-tested against the already-rendered frame).
- **Top-down, early-out:** start at the tree's top internal nodes (not the root singleton — query
  its children directly, same shape Gemini's own diagram sketched). If a node's query result comes
  back "hidden" (0 samples), mark EVERY element in its subtree as occlusion-hidden this cycle and do
  **not** descend further or query any of its children — this is where the big wins come from (one
  query result culling potentially thousands of elements across many different discipline-buckets
  at once, unlike MeshBVH's per-bucket ceiling). If "visible" (or the query hasn't resolved yet),
  descend to its children on a LATER cycle (17.4).
- **Integration point — reuse §13/§16's own machinery, do not build a second demote pipeline:** a
  new `occlMis` criterion, OR'd into `_wantedReal`'s existing decision alongside `roomMis`, using
  the SAME fade/snap state machine (`FADE_FRAMES`/`FADE_CAP`), the SAME "only demote, never a new
  visual system" discipline §8/§9 already established for this file. An occlusion-hidden element is
  just another reason to want `box`, structurally identical to a distance/frustum/room-mismatch
  demote from the engine's point of view.
- **Async by construction — never block the main thread on a query result.** Issue queries one
  frame, poll `getQueryParameter(query, GL.QUERY_RESULT_AVAILABLE)` on later frames, only read
  `GL.QUERY_RESULT` once available. This is the single most common way hierarchical occlusion
  culling gets implemented WRONG (a synchronous stall) — call it out explicitly in the eventual
  code's own comments, not just here.

### 17.4 Temporal amortization — do not re-traverse/re-query every frame
- **Round-robin the tree across frames**, same amortization shape `dlod_nav.js`'s own `EVAL_CHUNK`
  chunked scan already uses for its distance/frustum pass (§FLY_SMOOTH) — query a rolling subset of
  nodes per frame rather than the whole tree at once, spreading cost instead of bursting it.
  Reuse each node's last query result for a HOLD window before re-querying — start from the SAME
  `FADE_FRAMES=10`-scale constant already tuned for this codebase's own hysteresis rather than
  inventing a new number, and revise only if a witness shows it's wrong for this specific case.
- **Camera-motion-aware re-query priority (not required for v1, name it for later):** nodes nearer
  the camera's current view direction change visibility more often than distant/peripheral ones —
  a v2 lever, not needed to get a first correct-and-safe version working.

### 17.5 Correctness pitfalls to test explicitly, not assume away
1. A parent node's proxy box can be VISIBLE even when every real element inside it happens to be
   individually occluded by DIFFERENT things — this is fine (conservative, same safe-direction bias
   §16.3 already established for portals) and just means "descend further," never a correctness bug.
2. The reverse must never happen by construction: if a query says a node is hidden, everything
   inside it must ACTUALLY be behind that same occluding geometry — verify this against §15's own
   ground-truth classifier (17.6), not assumed from the query semantics alone.
3. Query result staleness across a moving camera — the HOLD window (17.4) trades a few stale
   frames of over-conservatism (kept real one cycle too long) for avoiding query-storm cost; verify
   this doesn't reintroduce the "pop/flicker" failure class §8 was gated against.

### 17.6 Witness plan (same rigor as §13/§16 — frame-time A/B is still the only real bar)
- **W-OCCL-BVH-EQUIV:** mechanism off ⇒ byte-identical to §16's own shipped behavior (same pattern:
  a pure superset lever, never implied by `pvsEnabled`/`roomOcclEnabled` alone).
- **W-OCCL-BVH-CORRECT:** THE important one — cross-check the traversal's hide/show verdict against
  §15's own PROVEN ground-truth classifier (`occl_classify.js`, Stage-0-gated, `mismatch=0`) at the
  SAME captured poses, not a fresh, unproven ground truth. Report false-hide rate (must be ~0 — a
  real correctness bug if nonzero) separately from false-show rate (the acceptable conservative
  residual, report honestly, same as §16.4's 31%).
- **W-OCCL-BVH-STABILITY:** temporal-hold window doesn't flicker under the same synthetic flap
  method §13/§16 already use.
- **W-OCCL-BVH-PERF — the decisive witness:** real interior-flight A/B, same machine/route/method as
  §13/§16's own PERF runs, reporting frame_ms AND draw calls AND bucket-empty count. Given §13 and
  §16 have NOW BOTH shown a draw-call change without a frame-time change, this witness's own
  finding is not assumed — if frame_ms still doesn't move despite emptying far more buckets than
  §13/§16 could, that means the bottleneck is elsewhere (JS-side traversal/query overhead itself,
  or a CPU submission cost independent of bucket occupancy) and must be reported as its own finding,
  not chased by retuning until a number looks good.

### 17.7 Non-goals (v1)
No pill/UI, no default-on, no Time Machine port (unchanged from §11.3/§13/§16). No v2 camera-
motion-aware re-query priority (17.4). No new occluder types beyond what's already in the scene
(no synthetic coarse "room shell" proxies — the real geometry already rendered each frame IS the
occluder, same as §15's own POC used).

### 17.8 Open questions for user review before implementation
1. Is the AABB-tree build cost (17.2, estimated ~2-3s from the SQLite comparison, not yet measured
   for the actual JS in-memory version) acceptable as a per-building-load cost, or does it need to
   be incremental/idle-deferred like `ensureRooms()`'s own pattern?
2. Should 17.1's finding be acted on for `elements_rtree` too (write the binary node-blob decoder,
   since the hierarchy genuinely exists on disk) as an alternative to a fresh in-memory tree, or is
   a fresh JS tree simply less code for the same result? Leaning fresh-tree (this doc's own
   recommendation) but flagged as an explicit choice, not a foregone one.
3. Whether §16 (portal-PVS) and §17 (hierarchical occlusion) should compose (PVS as a first, near-
   free filter; the BVH/GPU-query pass only for whatever PVS didn't already resolve) or whether §17
   alone supersedes §16 entirely — compose is the more consistent choice with this file's own
   "extend, don't discard" pattern (§9→§13 kept both criteria OR'd), tentatively recommended, not
   decided here.

### 17.9 §17.8 DECIDED (2026-07-22, user reviewed, proceed)
1. **Build cost:** not decided up front — MEASURE the real in-memory JS build directly (standalone
   timing script over the actual resident element set) before choosing sync-at-load vs
   idle-deferred. The 2-3s number is an SQLite-round-trip estimate, not the JS structure's own cost,
   and is cheap to pin down first.
2. **Fresh JS tree, not the `elements_rtree` blob decoder.** Decided, closed. The R-tree's cell
   format isn't a supported SQLite interface (reverse-engineering internals for a one-off), and it's
   built in a different coordinate frame (IFC/raw) than 17.2 needs (Three.js world space) — the
   frame conversion alone is most of the work a fresh tree needs anyway.
3. **Compose.** PVS (§16) as a first free filter; BVH/GPU-query only on whatever PVS leaves visible.
   Consistent with §9→§13's own "extend, don't discard." Also reduces live GPU-query volume per
   cycle (already-PVS-culled rooms need no query at all).

### 17.10 CHEAP POC — spec (before full §17 build; de-risks the causal claim, not the engineering)
**Issue this POC proves/disproves, isolated from implementation cost:** §13 and §16 BOTH already
found draw-call reduction with NO measurable frame_ms change. Before spending real effort on the
BVH + async GPU-query mechanism (17.2-17.4), test whether object-grain occlusion demotion moves
frame_ms AT ALL, using a PERFECT oracle in place of the real-time mechanism — decouples "does the
idea work" from "can we build a fast enough approximation of it."
- **Oracle source:** §15's own proven classifier, `witness/occl_classify.js` (Stage-0-gated,
  `mismatch=0`), reused verbatim — same discipline as §16's own witnesses. Extend §15 Stage 1's
  existing per-pose capture (`witness/w_occl_stage1.js`: freeze camera at a captured interior pose,
  enumerate the full active set, classify each) to additionally emit a **guid→occluded boolean map**
  per pose (Stage 1 today only aggregates counts — this POC needs the per-guid verdict list to
  actually demote by).
- **Wiring — smallest possible addition to `dlod_nav.js`, mirrors `_roomMismatch` exactly:** a new
  `_occlOracle` guid-map + `_stats.occlOracleEnabled` console-only lever (default false, inert), a
  `_occlMismatch(e)` test (`occlOracleEnabled === true && _occlOracle && _occlOracle[e.guid] ===
  true`), OR'd into `_wantedReal` alongside `roomMis`. `window.__dlodNav.setOcclOracle(map)` setter
  to inject a pose's hide-map from the witness script. No BVH, no GPU query, no temporal
  amortization — the oracle map itself stands in for all of 17.2-17.4 for this POC only.
- **Measurement — static-pose hold, not a live flight (cheaper, and the oracle is only valid AT the
  pose it was classified for):** at each of Stage 1's captured poses, freeze the camera, sample
  frame_ms over a short window with the oracle OFF then ON then ON then OFF (same off1/on1/on2/off2
  interleave §16's W-PVS-PERF used, to cancel warm-up/GC drift), aggregate delta_ms across poses.
- **What it does NOT test:** build cost, async query correctness/staleness, temporal hold-window
  tuning, or a moving-camera flight — all deferred to the real §17 build IF this POC shows frame_ms
  moves. A negative result here (no delta despite a perfect, zero-cost oracle) is itself a
  reportable, valid finding per this file's own §13/§15 discipline — it would mean the bottleneck is
  JS-side/CPU-submission cost, not draw-call/bucket-occupancy, and §17's real build should NOT
  proceed.
- **Where:** new branch `poc/occl-bvh-oracle` off `feat/room-occlusion-pvs` tip (86b096b) in
  `bim-ootb`, worktree `/tmp/wt-occl-oracle-poc` (reusing the existing worktree-hygiene discipline —
  checked `git worktree list` first, no duplicate). Local only, not pushed — same convention as
  `poc/occlusion-culling-study` (§15) and `feat/room-occlusion-pvs` (§16) itself, both currently
  local-only pending the user's own promote decision.

### 17.11 RESULT — 2026-07-22, real RTX 4060, `bim-ootb` branch `poc/occl-bvh-oracle`
**W-OCCL-ORACLE-PERF ran twice.** First run's boxed/real counts were BIT-IDENTICAL between
oracle-off and oracle-on at every pose despite the audit's own `mismatch` jumping into 5 figures —
a contradiction, caught before trusting it: `dlod_nav.js`'s chunked scan (`_evalChunk`) only re-arms
on a camera-POSE change (`_lastCamSig`), and a frozen-pose A/B never moves the camera, so the oracle
lever toggle never actually triggered a state transition. Fixed by re-arming the scan on an
oracle-lever change too (`_lastOracleEnabled`/`_lastOracleSet`, mirrors the existing room-change
re-arm at line ~374). Reran clean — `mismatch` converges to 0 every run, boxed/real genuinely differ
oracle-off vs oracle-on.

**4 frozen interior poses, off1/on1/on2/off2 interleave (same method as §16's W-PVS-PERF):**

| pose | OFF frame_ms | ON frame_ms | delta_ms | drawCalls Δ | boxed Δ |
|---|---|---|---|---|---|
| 0 | 27.24 | 21.54 | -5.69 (-21%) | -994 | +3810 |
| 1 | 34.05 | 16.69 | -17.36 (-51%) | -3403 | +21255 |
| 2 | 41.64 | 16.69 | -24.94 (-60%) | -4087 | +23049 |
| 3 | 16.69 | 16.68 | -0.01 (n/a — already 98.6% boxed pre-oracle, no headroom) | -337 | +1463 |

`mean_delta_ms=-12.00 mean_delta_calls=-2205 mean_delta_boxed=12394 meanOccludedPct=94.1%`.
Full log: `witness/w_occl_oracle_perf.log` / `witness/w_occl_oracle_perf.json`.

**Two honesty flags, not glossed over:**
1. Poses 1-3 all land at exactly 16.6-16.7ms once demoted (=1/60s) — reads as a vsync/rAF frame cap
   being hit once GPU submission cost drops low enough, meaning the true win at those poses is more
   likely UNDERSTATED by this measurement than overstated.
2. This used a zero-cost precomputed oracle at 4 FROZEN poses, not a live moving flight, and no
   BVH/query/traversal overhead of its own — it isolates the causal claim only, per 17.10's own
   design. The real mechanism (17.2-17.4) still has to prove its own overhead doesn't eat the win —
   that is what W-OCCL-BVH-PERF (17.6) is for, unchanged.

**§17.11 VERDICT: the cheap gate PASSED, decisively.** Unlike §13 (frustum/distance) and §16
(room-PVS), both of which found draw-call reduction with NO measurable frame_ms movement at the
harness's noise floor, object-grain demotion here moved frame_ms in lockstep with draw calls, by as
much as 60% at some poses. The bottleneck this codebase's PERF harness kept finding "elsewhere" is
NOT JS-traversal/CPU-submission-bound at these interior poses — it is genuinely bucket-occupancy-
bound, and finer-than-room-grain culling has real, large headroom to exploit. **§17's real
BVH+GPU-occlusion-query build (17.2-17.4) is justified to proceed** — the POC's job (decide whether
the engineering is worth doing BEFORE doing it) is done.

### 17.12 REAL BUILD — implemented, EQUIV passed, CORRECT FAILED (2026-07-22/23, real RTX 4060,
`bim-ootb` branch `feat/occl-bvh-gpu-query`, off `feat/room-occlusion-pvs` tip)
**Implementation:** `viewer/dlod_nav.js` +397 lines — median-split BVH over `_boxIndex`'s AABBs
(measured 418ms build, 122,330 elements, confirms §17.9-1's 439ms estimate), async WebGL2
`ANY_SAMPLES_PASSED_CONSERVATIVE` occlusion queries per visited node (poll-only, never blocks),
temporal HOLD window at `FADE_FRAMES`-scale, composed with §16's PVS as a further OR'd
`_occlBvhMismatch` criterion. `window.__dlodNav.occlBvhEnabled` (default false).

**W-OCCL-BVH-EQUIV: PASS**, independently re-verified — off-path byte-identical to shipped §13+§16
(`diff` of both JSON runs empty, `mismatch=0` at all 4 poses, zero nodes/queries built while
disabled).

**W-OCCL-BVH-CORRECT: FAILS, two independently-confirmed bugs, not one.**

1. **Off→on re-enable is permanently inert (root-caused in code, not patched).** `_occlDisable()`
   empties the cut and nulls the hide-set; `_bvhEnsure()` early-returns once `_bvh` already exists
   for the current building (line ~493), and cut-reseeding/hide-set-reset only live in the BUILD
   path (lines ~497, ~513-515) — so a simple re-enable within the same building never issues another
   query. First surfaced as "poses 1-3 completely dead" in a same-browser, multi-pose run 1; ruled
   OUT as a witness-harness artifact by running again with a fresh browser per pose (removing the
   off→on re-enable from the test entirely) — confirmed as a real code bug, not a test artifact.

2. **Nonzero, large false-hide rate — the correctness bar itself fails.** Fresh-browser-per-pose
   run: `meanFalseHideRate=31.48%` (per-pose 18.46%/8.48%/82.32%/16.67%, worst case 135 of 164
   actually-visible elements wrongly hidden). The spec's own bar (17.6) is "~0 — a real correctness
   bug if nonzero"; this is decisively over that bar, not a rounding residual. The hide-set also
   never stabilizes — `hidden` oscillates by TENS OF THOUSANDS between 5s samples at a FROZEN
   camera (pose 1: 81,454↔117,721; pose 3: 75,917↔122,078) — every pose reports `settled=timeout`,
   and at pose 3 the applied partition collapsed to `real=103` for the entire 122,330-element
   building.

**Root-cause hypothesis (plausible, consistent with the numbers, NOT separately proven):** occlusion
queries depth-test against "whatever the renderer last drew" (17.3's own design, matching real
occluder discipline, §17.7) — but what's actually drawn includes the ~100k+ nav-DLOD wireframe BOX
PROXIES for already-demoted elements (EQUIV logged `boxVis=121953`), not just real solid mesh. A box
proxy is a rendering STAND-IN, not real occlusion evidence — using it as an occluder plausibly
creates a SELF-AMPLIFYING FEEDBACK LOOP: hiding an element adds another box proxy to the depth
buffer → that proxy occludes MORE real geometry in later queries → more elements hide → more
proxies → runaway, matching the observed 5-figure oscillation and the pose-3 near-total collapse.
If correct, this is a design conflict between the existing box-proxy DLOD system and reusing "last
drawn frame" as the occlusion source (17.7 named this the correct occluder in principle — real
scene geometry — but did not anticipate the box PROXIES contaminating that same depth buffer).

**Status: NOT proceeding to STABILITY/PERF.** This is now a real architectural question, not a
tuning pass — likely fix shape is excluding box-proxy geometry from whatever depth buffer occlusion
queries test against (e.g. a depth-only pre-pass over real/solid meshes only), which is real,
non-trivial engineering, not a quick patch. Open call for the user: invest in that redesign, or stop
here and report §17 as a proven-opportunity/failed-first-implementation result (the causal claim
from 17.10-17.11 stands — object-grain occlusion demonstrably moves frame_ms when correctly
identified — this implementation just doesn't correctly identify it yet), same "negative-but-real
result is reportable" discipline already used for §13/§15/§16, and ship §16's PVS alone as the
practical stopping point.

## 18. nav-DLOD root-cause + real-frame_ms wins on LTU_AHouse (2026-07-23, separate from §17's occl-bvh work — this is `dlod_nav.js`, the older distance-based box-proxy system §9/§10 shipped, not §16/§17's occlusion work)

**Starting observation (user):** flying LTU_AHouse (122,330 elements) with `'o'` (nav-DLOD) on, solid
display and bbox-cycle display felt the SAME speed — read at first as "occlusion has hit the bbxes
floor." That reading was wrong; root cause below.

**Root cause, code-confirmed:** Alt+Z's bbox/ghost display cycle (`navigate_find.js` `toggleMergedGhost`)
calls `A.filterByGuids(new Set())` to hide solids for the ghost — an EMPTY Set, but still truthy in JS.
`dlod_nav.js`'s `_gateBlockReason` (`if (app.activeGuidFilter) return 'find-isolation';`) unconditionally
blocks nav-DLOD while any guid filter is set, including this empty one. Pressing `'o'` while in bbox mode
(or with a stale filter left over from Find/isolation) silently logged `on=true` and never engaged — no
error, no `§DLOD_NAV_ENGAGE`, indistinguishable from working. That's what made "solid vs bbox" look like
the same speed: bbox mode was never actually running nav-DLOD's box-proxy system at all — it's a fully
separate mechanism (`§GHOST_XRAY`'s own 28,569-box `SHELL_GHOST_BBOX`, vs nav-DLOD's 122,330-element
index).

**Three PRs shipped (bim-ootb), in order:**
- **#973** `feat/fps-mode-log` — throttled `§FPS_MODE mean=.. max=.. n=.. dlod=.. disp=.. fly=.. orbit=..`
  frame_ms sampler (only counts frames that did real work, post idle-park gate).
- **#974** `feat/dlod-nav-gate-toast` — `toggleDlodNav` now checks `_gateBlockReason` synchronously and
  logs/toasts `§DLOD_NAV_TOGGLE on=true blocked=<reason>` instead of a false `on=true` when a gate
  condition (bbox/ghost isolation, `streaming`, `find-isolation`, etc.) already holds. Diagnostic only,
  zero behavior change to when nav-DLOD actually engages.
- **#975** `fix/dlod-nav-restore-chunk` — two fixes found via #973's real frame_ms data:
  1. `_restoreAll` (disengage path) was a single synchronous loop over the whole 122,330-element index —
     measured **2.5-3.6s frame_ms spike** on every `'o'`-off. Chunked the same way `_evalChunk` already is
     (`_restoreAllNow`, `EVAL_CHUNK` per rAF tick); a re-engage mid-drain force-flushes the pending restore
     synchronously first (`_restoreFlush`) so `_buildBoxes` never races a stale in-flight drain.
  2. `§FPS_MODE`'s `dlod=` tag read pill-intent (`_dlodNavOn`), not real engagement (`_engaged`) — a
     gate-blocked press tagged frames `dlod=on` while nav-DLOD did nothing, which would have silently
     poisoned the exact on/off comparison the sampler exists to make. Now reads `window._dlodNavEngaged`.

**Confirmed live, same session, post-merge — real numbers, not inference:**
- **Disengage freeze is gone.** `'o'` toggled off mid-flight: `§FPS_MODE mean=116 max=207.7 dlod=off` —
  unremarkable, in line with surrounding frames. Pre-#975 this exact moment was 2.5-3.6s.
- **#974's blocked-toast fires correctly in the wild**, twice, different reasons: `blocked=streaming`
  (pressed `'o'` before streaming finished) and `blocked=find-isolation` (pressed `'o'` while a
  pick/ghost isolation filter was still set).
- **First trustworthy `dlod=on` vs `dlod=off` comparison during actual flight** (previously impossible —
  the tag itself lied): clean `dlod=off` sample right before engaging, `mean=219.8 max=285.6`; after the
  one-time engage burst settles (`mean=299.1` for the burst itself, matching the cold-engage-burst finding
  below), sustained `dlod=on` flight: `mean=100-150` across dozens of 2s windows. **Roughly 45-55% mean
  frame-time reduction, real and reproducible**, not a screenshot/feel — this is the first number-backed
  answer to whether nav-DLOD helps on a building this size.
- **New, slightly counter-intuitive finding, not yet acted on:** full ghost/xray mode (nav-DLOD gated off
  the whole stretch, confirmed via `blocked=find-isolation`) measured **~70-90ms mean** during flight —
  FASTER than solid+nav-DLOD-on's ~100-150ms. Expected once stated: nav-DLOD still keeps roughly
  15-30% of elements as full real geometry at any moment (`active=22366 boxed=99964` typical), while
  full-ghost hides essentially all real solids. nav-DLOD beats full-solid, but doesn't reach full-hide.

**Confounds identified, do not misattribute:**
- Cold-engage burst (`active=0 boxed=122330 started=122330`, `mean≈300ms`) is real and reproducible on
  every fresh engage — expected from `_evalChunk`'s design (whole-building reclassification), not a bug.
- `three-mesh-bvh`'s incremental build (`§BVH_DEFERRED`, seen at 6.9s/13.3s/30.4s across runs — highly
  variable, likely CPU-contention-dependent) can coincide with nav-DLOD/bbox testing and inflate frame_ms
  spikes that aren't nav-DLOD's fault.
- The camera-teleport reclassification jank named in an earlier pass of this session turned out to be
  **general, not nav-DLOD-specific** — the same tour-start jank (`mean=550.7 max=1533.1`) recurred with
  `dlod=off`. Likely three.js's own frustum-culling/visibility churn on a large instant camera jump. Don't
  scope a nav-DLOD-only fix at it.
- Do not re-litigate the "R185/BatchedMesh was great, then got worse" arc against this session's data —
  already root-caused to machine/driver state in §12, not app code; this session's slower-than-usual
  module bootstrap (`§UPGRADE_THREE_DONE ms=1057` vs the usual `ms=22-48`) was a cold service-worker cache
  after a hard reset, unrelated to §12's history and unrelated to nav-DLOD.

**Open, not yet done (two real perf candidates, ranked):**
1. Investigate whether nav-DLOD's DEMOTE/PROMOTE distance thresholds can be tightened for buildings this
   size, to box more aggressively and close the ~30-60ms gap to full-ghost speed while still keeping
   nearby real geometry visible. Not started.
2. The general (non-nav-DLOD) camera-teleport jank above — separate investigation, not scoped here.
