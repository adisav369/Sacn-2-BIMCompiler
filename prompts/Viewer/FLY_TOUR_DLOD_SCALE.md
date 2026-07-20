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
day**: a viability test for a DIFFERENT, structural culling axis (storey occlusion) the user's own
domain pushback surfaced — see §5's "why this wasn't found earlier" note before assuming the
box-proxy design in §0-§4 is the only lever worth pulling.

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

**Recommended shape (not yet designed in full — this is a viability finding, not a spec):**
1. Storey-aware culling: query (or cache) which storey the camera currently occupies (Fly
   Tour/Cinema both already know their current stop's storey from the room-graph/dive-target data
   they compute for path-planning — reuse that, don't recompute), then treat any element whose
   `storey` differs as an occlusion candidate — box-proxy it (reuse §0-§2's existing box mechanism)
   or outright skip its draw, pending a design decision on which is cheaper to toggle live.
2. Exempt stairwell/atrium elements spanning the current storey and its immediate vertical
   neighbor (a name/class filter — `IfcStair`/`IfcStairFlight`/void-openings — not a full 3D
   containment test) from storey-based hiding, so vertical sightlines don't visibly break.
3. This is ADDITIVE to §0-§2's distance+frustum box-proxy, not a replacement — storey occlusion
   catches "wrong floor entirely" (the biggest single win per the numbers above); distance+frustum
   still matters for same-floor far/background elements.
4. Needs its own viability witness before implementation claims anything: confirm live on LTU that
   the current-storey lookup is cheap/already-available at the exact point `_dlodUpdateBoxes`-style
   code would need it, not just that the SQL partition looks good in isolation.
