# ⚠ DO NOT REMOVE — Scope & Working Rules
**Scope:** TM Phase 3 — cap the GPU frame cost of Time Machine playback on large buildings (LTU
122k+) by rendering the INACTIVE construction set as bbox proxies and real mesh only for the active
window. This file is the spec `TM_INCREMENTAL_RENDER_PERF.md` §0 references as "TM_DLOD_SCALE.md
(to author)". Implementation target: a Sonnet session; everything needed is named here — do not
rediscover, do not re-litigate §1's settled history.
**Read the log after every run.** Witnesses in §6; every claim needs its § line. Exit code is not
evidence. The acceptance bar for the OFF state is ZERO behavioural change (same as Phases 1-2).
**Status:** SPEC ONLY (2026-07-20, authored from the two-repo large-model survey). Nothing
implemented. Read §2 (what already exists — you are wiring, not inventing) and §5 (landmines,
two of which already shipped regressions in earlier phases).

## 1. Settled history — why this design and not the other one (do NOT re-litigate)
Three different things have been called "DLOD" in this codebase. Be precise:
1. **Geometry-swap DLOD (S261 lineage — RETRACTED, stays retracted).** All elements resident as
   bboxes, `BatchedMesh.setGeometryAt()` swaps real geometry near camera, LRU eviction. Died on:
   edge-on flicker on thin elements, TM visibility fights, pick broken until promotion completes.
   `viewer/streaming.js:19,109,158` hard-set `A._useDlodPath=false`; the whitebox test
   `dlod_visibility_only` (`deploy/dev/tests/whitebox_regression.js` ~645, `§WB_DLOD_VIS`) asserts
   `noSwapPath && noPromote`. **This spec does NOT touch `setGeometryAt` and does NOT re-enable
   `_useDlodPath` — the guard test must stay green unmodified.**
2. **Frustum visibility culling (S262/S274 — SHIPPED, `viewer/dlod.js`).** Skips off-frustum slots.
   Useless for wide views (TM playback, aerial tours see the whole building in frustum). Keep as-is;
   independent of this spec (S262 doctrine: TM controls visibility, DLOD-culling controls its own —
   do not couple them; `_dlodPaused` is a dead field, leave it).
3. **THIS SPEC: representation-by-activity.** Built-but-inactive elements render as discipline
   boxes; the active construction window renders real mesh. Swap axis = construction time (cursor
   window), not camera distance → coarse, user-paced, no per-frame promotion churn. No geometry
   swap: real meshes stay resident with per-slot visibility OFF; boxes are SEPARATE InstancedMesh
   objects. Everything S261 died of is structurally absent.

**Corrections this survey established (recorded 2026-07-20, fix citations when touched):**
- `TM_INCREMENTAL_RENDER_PERF.md` "LTU 125k = 8 draw calls" is UNCORROBORATED — the measured healthy
  state is ~15-16K draw calls (S280c `§S280c` logs, S263). Do not design against "8".
- Its §0b "streaming/eviction (shipped)" overstates: only dispose-on-building-switch exists. True
  memory-bounded eviction died with S261. At 122k, FULL geometry for the whole building is resident
  in GPU memory today; this spec reduces DRAW cost, not resident memory — say so honestly in any
  external claim. Memory-bounded residency is a separate future phase (§7).

## 2. Existing mechanisms to reuse (wiring inventory — all shipped, all proven)
- **Box proxy renderer:** `viewer/navigate_find.js:1413` `_buildMergedGhost()` — per-discipline
  `THREE.InstancedMesh` unit boxes scaled from DB `center_*`/`bbox_*` columns; proven >50k (Alt+X).
  Reuse the BUILD PATTERN (per-discipline instanced boxes from elements_meta), not the literal
  wireframe ghost material — Phase-3 boxes should be solid, discipline-colored, slightly emissive
  (match `_drawBboxPlaceholders`' look in `viewer/streaming.js` ~187, the load-time placeholder
  system users already understand as "not yet detailed").
- **Per-slot visibility:** TM already drives `setVisibleAt(slot)` (BatchedMesh) and zero-scale
  `setMatrixAt` (InstancedMesh) per element — `viewer/time_machine.js` `renderAtTime()` branches.
  The inactive set is hidden by EXACTLY this mechanism; no new hide path.
- **The event index** (`_tmBuildEventIndex`, `_evMesh`) already knows which meshes have events in
  any cursor window — the active-window membership test is a lookup, not a new scan.
- **Activity sets:** `renderAtTime` already maintains `frontier{}` / `recent{}` / `placed{}` per
  tick — the active window IS frontier ∪ recent (+ optional lookahead, §4). No new bookkeeping.

## 3. Definition — the active window
- ACTIVE (real mesh): elements in `frontier{}` or `recent{}` at the cursor, plus a lookahead band:
  elements whose `start_ts` falls within `LOOKAHEAD_TICKS * tickMs()` after the cursor (default 6 —
  what's about to build is what the eye tracks; tune with a witness number, not by feel).
- INACTIVE-BUILT (box): in `placed{}` but not ACTIVE.
- UNBUILT: hidden (exactly as today).
- **Engage gate:** proxy mode engages only when `elements_meta count > DLOD_TM_MIN_ELEMENTS`
  (default 50000) AND the user has the toggle ON (§4). Hospital/Duplex never see it; LTU does.

## 4. UX contract
- A TM-panel pill toggle "◧ LOD" (OFF by default in v1). OFF = today's rendering, bit-identical
  (§6 W-DLOD-EQUIV gates this). ON = boxes for INACTIVE-BUILT.
- Toggling is user-paced — it MAY do one full-cost rebuild (like a shadow toggle edge; reuse the
  `_lastShadowOn` edge-detection pattern from Phase 2, `time_machine.js` ~892).
- Boxes must NOT respond to picking as real elements (the S261 pick trap): reuse the ghost meshes'
  existing pick-exclusion (`_buildMergedGhost` instances are already pick-transparent — verify and
  cite the mechanism in the PR).
- `feedback_no_fake_lod_unbreakable.md` (UNBREAKABLE: no non-LOD400 shown as real geometry): boxes
  must read as obviously-proxy (placeholder styling, §2), never as modelled mass.

## 5. Landmines (each one already bit an earlier phase — read before coding)
1. **`_metaGen` bump = 50-159ms index rebuild** (measured live, LTU). The box InstancedMeshes are
   NOT TM-tracked meshes: never register them in `_batchMeta`/`_instanceMeta`, never bump
   `A._metaGen` when flipping box instance visibility. If the index rebuilds during playback, the
   design has failed (`§PERF_INCR_INDEX` count is a witness gate, §6).
2. **Cursor calling convention** (#912, #916): any new render entry point takes the target cursor
   as an ARGUMENT; zero direct `_cursor` assignments (audit: `grep '_cursor = ' viewer/time_machine.js`
   must return only the declaration + `renderAtTime` internal line 974).
3. **Equivalence witnesses don't test call sites** (#912 lesson): W-DLOD-EQUIV must drive the REAL
   UI (pill clicks, ▶, ⏮, slider), not only `__tmSetCursor`.
4. **Streaming interplay:** while `APP.streaming`, batches rebuild meshes and bump `_metaGen` (the
   documented, still-unfixed rebuild-per-batch cost — `TOUR_WALKMODE_IDLE_PARK_STUCK.md` §5). v1:
   proxy mode simply refuses to engage until streaming drains (same doctrine as Fly Tour's
   `§FLY_STREAM_WAIT`). Do not try to solve the streaming-rebuild coalescing here.
5. **Double-draw:** an element must never show box AND real mesh. W-DLOD-PROXY diffs the union.

## 6. Witnesses (all blocking; Hospital for logic, LTU for scale; headless is blind to GPU — frame
numbers come from the user's machine via `window.__tmTrav` + a `§DLOD_TM` stats line)
- **W-DLOD-EQUIV:** toggle OFF → visible-guid set + per-slot visibility identical to pre-change
  build across the #909 sweep (19 cursors, fwd/back/scrub/jumps, shadows on). `mismatch=0`.
- **W-DLOD-PROXY:** toggle ON → real-mesh visible set == ACTIVE set exactly; box instance count ==
  |INACTIVE-BUILT|; union disjoint. Log `§DLOD_TM active=<n> boxed=<n> unbuilt=<n> mode=on|off`.
- **W-DLOD-NO-REBUILD:** `§PERF_INCR_INDEX` fires ZERO times during 60 ticks of proxy-mode playback
  (post-streaming). One rebuild allowed at toggle edge.
- **W-DLOD-PERF (user hardware):** `§PERF_TRAVERSE` + renderer.info draw-call/triangle counts at
  cursor=95% with proxy OFF vs ON on LTU. The claim to beat: near-end heaviness (user report
  2026-07-20). No % target invented — measure first (MEASURE BEFORE ESTIMATING, TM file §0).

## 7. Explicit non-goals (v1)
- No camera-distance promotion, no `setGeometryAt`, no eviction/memory-residency work (S261 stays
  dead; memory ceiling is its own future spec — measure 250k/500k footprint AFTER this ships).
- No Fly-Tour/walk-mode integration yet: the tour benefits automatically only via whatever TM
  window is active. A follow-up MAY render far-storey boxes during tours using the same proxy —
  separate spec, after W-DLOD-PERF numbers exist (route-planning cost is already solved by
  `TOUR_ROUTE_CACHE.md`).
- No default-ON. Default flips only after the user's own hardware numbers say so.
