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
