# ⚠ DO NOT REMOVE — Scope & Working Rules
**Scope:** make `viewer/time_machine.js` `renderAtTime()` cost proportional to WHAT CHANGED at the
cursor, instead of O(total elements) per tick. Nothing else. This is a pure performance refactor —
it must not change a single rendered pixel, and that is the acceptance bar.
**Read the log after every run.** Exit code is not evidence. The witness for this work is a NUMBER
(`§PERF_TRAVERSE`) plus a zero-mismatch equivalence check (`§INCR_VERIFY`), not "it looks fine."
**Status:** Phase 1 SHIPPED live (v821, 2026-07-20). Phases 2-3 below are OPEN. Read §4 Risks and
§8 (the shipped lessons) before touching code — several risks silently corrupt the scene, and one
already shipped a net regression that had to be reverted-in-effect.

---

## 0. SHIPPED + WHAT'S NEXT (2026-07-20) — read this first
**Phase 1 (DONE, live v821):** §PERF_INCR event-index skip + the waste-removal that actually moved
the needle. A user on real LTU (125k) confirmed "much faster." The honest post-mortem of WHAT helped:
- **Index thrash removed** — the biggest win, and it was fixing MY OWN regression. `_tmSceneSig()`
  first folded `scene.children.length` into the staleness key; that count changes every tick (spark
  sprites, SFX, stars, bloom add/remove children), so the 108ms event index rebuilt EVERY tick AND
  reset `_incrPrimed` → forced `mode=full` forever → ~158ms/tick, slower than no optimisation.
  Fixed by keying only on `A._metaGen`. **Lesson: never validate a perf change only headless — a
  software-WebGL rig showed a flat 60fps for both 6.9k and 125k and was blind to all of this.**
- **§WB_MAT logging gated** behind `window.__TM_WBDEBUG` (was ~40 console.logs/sec, real cost even
  console-closed; catastrophic with Firefox devtools open). Full traverse 50ms→23ms just from this.
- **25.8MB ad_seed.db reread** cached as a miss (`§PERF_NEG_CACHE`) — was re-deserialised from IDB
  EVERY tick on non-folded buildings (LTU). Verified: per-tick (30+) → 2 total.
- **The incremental SKIP itself contributed ~nothing** for this user, because it is gated OFF under
  shadows (`_incrOK = ... && !app._shadowOn`) and they run Shadow + Alt-G. `mode=full skipped=0` in
  their live log. Be honest about this: the felt win was waste-removal, not the headline skip.
- Diagnostic hooks shipped for real-hardware profiling: `window.__tmTrav` (last-traverse stats),
  `window.__tmStep(dms)` (drive a playback-like tick without the cinema UI).

**Phase 2 (NEXT, JS lever) — make the delta skip work UNDER SHADOWS.** Currently `_incrOK` forces
full whenever `app._shadowOn`, because the shadow-caster promotion pass needs the complete
`_placedMeshes` list each tick. Maintain that list incrementally (or decouple the shadow pass from
the traverse) so delta engages with Shadow/Alt-G on. **Expected: ~74% off the per-tick traverse
(23ms → ~6ms), high confidence** (identical path skips 9901/10841 on Hospital). JS-only; it does NOT
touch GPU cost. Gate remains equivalence (§INCR_VERIFY mismatch=0) WITH shadows on.

**Phase 3 (NEXT, the REAL scale lever) — DLOD / bbox-proxy in TM. See `TM_DLOD_SCALE.md` (to author).**
The per-tick JS is now ~23ms and not the bottleneck. What still renders every frame is all ~16k LTU
meshes at full geometry — GPU cost my JS work cannot touch. The proxy already exists and is proven
>50k in the Find Panel (`navigate_find.js _buildMergedGhost()` — per-discipline InstancedMesh unit
boxes from the DB `center_*`/`bbox_*` columns). Wire it in as TM's INACTIVE-SET renderer: real mesh
only on the active construction layer, boxes for the rest. **⚠ interaction with Phase 1:** a
bbox↔mesh swap bumps `A._metaGen` → rebuilds the event index (108ms). Fine for a user-driven layer
switch, fatal if it happens per playback tick — needs targeted per-mesh invalidation, not a global
`_metaGen` bump.

**MEASURE BEFORE ESTIMATING (hard rule this session learned twice).** The headless rig cannot see
GPU cost. Before quoting any Phase-3 %, get a real-hardware frame profile via `__tmTrav` + a
frame-time capture on the user's machine. The only number currently defensible is Phase 2's ~74%
traverse cut. Total-lag % is UNKNOWN until the GPU baseline exists — do not invent it.

## 0b. FOOTPRINT STRATEGY TO 1M — ingredients yes, proof no
Coherent on paper, unproven at 1M. Pieces: batching/instancing (LTU 125k = 8 draw calls, shipped) +
streaming/eviction (shipped) + O(changed) event index (shipped) + **DLOD bbox proxy (Phase 3, the
load-bearing piece)**. **Binding constraint = the ~2GB browser tab memory ceiling** (Autodesk's own
figure). At 1M the strategy only holds if the bbox proxy is the DEFAULT resident representation and
real mesh is streamed for the active window then evicted — memory is the wall nobody has measured.
Honest external phrasing: "architected for 1M via Pareto DLOD, demonstrated at 125k" — NOT "handles
1M." Next-session task: build Phase 3, then measure real memory + frame footprint at 250k/500k/1M to
find where the wall actually is.

---

## 1. The measured problem

Live on Hospital (63,416 elements scheduled, 16,115 ops), during playback:

```
§PERF_TRAVERSE ms=15.6–22.4  objs=10841  cand=3
```

16–22 ms of a ~31 ms tick is spent walking the entire scene graph to service 3 spark candidates.
Scrub benchmark, n=63 slider commits: mean **31.6 ms**, median 29.6 ms, p90 42 ms.

**There are TWO O(n) passes per tick, not one.** Both must be fixed or the win is halved:

| # | Pass | Cost driver | Location |
|---|---|---|---|
| A | **Ops scan** — rebuilds `placed{}` / `frontier{}` / `recent{}` / `arrival{}` from scratch | scans `_ops[0..cursor]`; near project end that is ~16,115 ops, and `placed{}` alone grows to ~16k keys **allocated fresh every tick** | `renderAtTime()`, the `for (var i = 0; i < _ops.length; i++)` loop |
| B | **Scene traverse** — `app.scene.traverse()` over every object | 10,841 objects on Hospital, independent of how many changed | `renderAtTime()`, `app.scene.traverse(function(obj) {...})` |

Pass A is pure allocation/GC churn; pass B is the measured 16–22 ms. A cursor step of one tick
typically changes the state of **single-digit** elements, so both passes are doing ~10⁴ work for
~10⁰ of change.

## 2. Target design — event-driven state with a full-rebuild fallback

### 2.1 Build once, on activate (not per tick)
- **`_evts`** — a flat, timestamp-sorted array of state transitions derived from `_ops`. Each element
  contributes at most three: `start_ts` → FRONTIER, `end_ts` → RECENT, `end_ts + lingerMs` → PLACED.
  (`lingerMs = tickMs() * 3` is cursor-independent, so the events are static once built.)
- **`_target`** — `guid → { obj, kind, slot }` where `kind ∈ {MESH, BATCHED, INSTANCED}`. This is the
  index that removes pass B entirely: given a changed guid, write directly to its mesh/slot instead
  of searching the graph for it. Build it in ONE traverse at activate, reusing the same branch
  structure the current per-tick traverse already has.
- Invalidate both when `_ops` is regenerated (schedule regen, cache-version bump, building switch).

### 2.2 Per tick — apply only the delta
Maintain live `placed` / `frontier` / `recent` sets ACROSS ticks. Moving the cursor `A → B`:
- **Forward:** apply every event in `(A, B]`.
- **Backward:** apply the inverse of every event in `(B, A]`. Scrubbing back is a first-class case,
  not an afterthought — the Time Machine is a scrub-driven player.
- **Frontier `t` values still update every tick** for elements already in the frontier set (progress
  is continuous, not an event). That set is small — this is the one unavoidable per-tick loop, and
  it is O(frontier), not O(total).

### 2.3 Fallback — this is a requirement, not a safety net
If `|events in the delta window| > _INCR_MAX_DELTA` (start at ~2,000, tune), do a FULL rebuild
instead. A scrub from 0% to 90% legitimately changes tens of thousands of elements, and applying
those one at a time is slower than the current code. The fallback is what makes the common case
(playback tick, small delta) fast without making the worst case (long jump) worse.
**`§PERF_INCR mode=delta|full n=<events>` on every tick** — if `mode=full` dominates in practice,
the refactor has failed and must be reported as such, not tuned until the log looks good.

## 3. What the traverse currently produces — every consumer must be preserved
The traverse is not just visibility. Anything below that regresses is a bug, and several are
invisible in a screenshot:

| Product | Consumer | Incremental equivalent |
|---|---|---|
| `obj.visible` / `setVisibleAt(slot)` / `setMatrixAt(zero)` | the actual render | write per changed guid via `_target` |
| `instanceMatrix.needsUpdate` | InstancedMesh upload | mark only meshes with ≥1 changed instance — **must not** be set blindly |
| `_frontierCentroids` | shadow promotion pass | maintain alongside the frontier set |
| `_frontierPositions`, `_guidPosMap` | camera follow / look-ahead | same; `_previewGuids` stays a bounded forward scan |
| `_placedMeshes` | shadow promotion (cap 500) | keep a maintained list, or leave this pass full-scan while `app._shadowOn` |
| `applyHighlight()` / `clearHighlight()` | single-mesh frontier tint | drive off frontier-set enter/exit |
| `_gspCollect()` | §GROUP_SPARK | already playback-gated; feed from the frontier/recent sets |
| `_wbMat()` counters | whitebox logging | cosmetic; may legitimately change frequency |
| `_sfxPhases` | sfx.js seam | rebuild from the frontier set, not the ops scan |

## 4. Risks — read before writing code
1. **Silent divergence.** Incremental state can drift from truth and the scene will look *plausible*
   while being wrong. This is the central risk and the reason §5's equivalence harness is mandatory.
2. **Backward scrub asymmetry.** Inverting a transition is not always the mirror of applying it
   (`recent` carries a fade computed from `cursor - end_ts`). Derive fades from the cursor, never
   from accumulated state.
3. **BatchedMesh/InstancedMesh slot aliasing.** `_target` assumes a stable guid→slot mapping. If
   streaming reassigns slots after activate, the index goes stale and writes hit the WRONG element.
   Confirm slot stability before relying on it — if it is not stable, `_target` needs invalidation
   hooks on the streaming path.
4. **`_ops` regeneration mid-session.** Schedule Author's "Apply to 4D" rewrites `_ops` while TM is
   active. Both caches must invalidate or the timeline silently renders the old schedule.
5. **Do NOT fold §IDLE-PARK into this.** Adding TM-playing to `_awake` in `main.js` was already
   investigated and REJECTED — the wake/park-per-tick pattern is correct; a continuous rAF chain
   between ~2/sec ticks reintroduces exactly the idle cost §IDLE-PARK removed. The stale
   "must stay 1" witness at `main.js:699` predates self-parking; fix the comment, not the loop.

## 5. Verification — equivalence first, speed second
**W-INCR-EQUIV (blocking).** A debug mode runs BOTH paths on the same cursor and diffs the resulting
visible-guid set plus per-slot visibility. `§INCR_VERIFY cursor=<t> mismatch=0` across a scripted
sweep — forward playback, backward playback, random scrubs, and jump-to-start/end — is the gate.
Any non-zero mismatch blocks the change. Speed is irrelevant until this is clean.

**W-INCR-PERF.** `§PERF_TRAVERSE` before/after on Hospital, same method as the baseline above
(n=63 slider commits, report mean/median/p90). Baseline to beat: **mean 31.6 ms, traverse 15.6–22.4 ms.**
Also capture a large building (Terminal) — Hospital may be too small to show the real win.

**W-INCR-PIXEL.** Rendered screenshots at matched cursors before/after must be visually identical.
The bar is "not a single changed pixel"; this is a perf refactor, so any visual delta is a defect.

**Non-witness:** "it feels smoother." Not evidence. Numbers or nothing.

## 6. Scope boundaries
- **In:** the two O(n) passes and the indices needed to remove them.
- **Out:** §GROUP_SPARK tuning, `_awake`/§IDLE-PARK, the ERP negative cache, console-log volume
  (all already handled or explicitly rejected in PR bim-ootb#891).
- **Out:** changing what the Time Machine *shows*. Zero behavioural change is the whole contract.

## 7. Provenance
Findings measured 2026-07-19 during PR bim-ootb#891 (§GROUP_SPARK). Full measurement record,
including three retracted perf hypotheses (`§RENDER_LOOP total`, per-tick `ad_seed.db` refetch, and
the spark-side micro-optimisations that benchmarked as no-ops), is in that PR's second commit
message and in `HOSPITAL_4D_SUPERSTRUCTURE_DURATION_ANOMALY.md` §GROUP_SPARK.
