# ⚠ DO NOT REMOVE — scope + rules

Refactor SEAMS survey for the long scripts (viewer + ERP). Written 2026-08-21 by the watchdog session
while the 4D dev session rested. **Specs only — nothing here is built.** Read the log after every run.
Rule applied throughout: **low witness coverage RAISES risk and DISQUALIFIES a candidate** — extracting
from code nothing can prove is how a silent regression ships. Precedent for every spec: viewer/gantt_model.js (PR #1446).

§S58 = support_sweep.js extraction spec (4D lane — fold into 4D_GANTT_TM_REFACTOR.md when that file is free).
§S59 = viewer fleet table. §S60 = ERP cluster table.

---

# S58 — SPEC: Extract support-order physics from `viewer/time_machine.js` into `viewer/support_sweep.js`

**Status: SPEC ONLY — no product code, no PR, no git writes.**
Precedent: `viewer/gantt_model.js` (206 lines, PR #1446) — pure functions, dual-mode export, no state/DOM; parent keeps thin wrappers owning state + `§` logs.

## TODO
- [x] 1. Membership table (function, file:line span, MOVE/STAY, why; flag module-var/app-object readers)
- [x] 2. Module contract (exports, signatures, in-place mutation note, §LOAD_FAIL posture)
- [x] 3. Consumer impact (witness/probe slices, per-function call-site changes, before/after slice counts)
- [x] 4. Preservation landmines (text-marker/indentation slicing, ~:3945 warning comment)
- [x] 5. Proof protocol (exact commands, pass=49 fail=0, normalized diff, ≥3 perturbations w/ FAIL ids, baselines)
- [x] 6. Wiring (viewer.html script tag order, sw.js PRECACHE + CACHE_VERSION v1063→v1064 same commit, audits)
- [x] 7. Risk (silent breakage, catches, functions that stay behind)

---

## 1. Membership table

All `file:line` spans read directly from the sandbox copy of `viewer/time_machine.js` (origin/main, 9,194 lines). Function-end lines computed by brace counting, not estimated. Correction to the candidate list: **`_ogCellsQueryTop` (:4051) and `_ogCellsQueryTopFar` (:4008) are NOT standalone functions** — they are `var`-bound closures local to `_ogSupportSweep`'s body (as are `_ogCellsFor` :4023, `_ogCellsBuild` :4041, `_ogCellsQuery` :4048, `_ogXY` :4049). They move as interior bytes of their parent, never as separate exports. The file wins over the candidate list here.

| Function | Span (time_machine.js) | Lines | Verdict | Why (one line) | Global/state reads |
|---|---|---|---|---|---|
| `_ogSupportSweep(_allScheduled, taskWin)` | 3953–4167 (+banner 3940–3952) | 215 (+13) | **MOVE** | Param-complete physics pass; mutates its array arg in place (incl. bz-ascending sort at :4046), returns `{pushed, sweeps}`; only external reads are guarded | `ScheduleGate.CELL` (guarded `typeof`, `\|\| 4` fallback, :3970); `console.log` :4164 → hoist to wrapper |
| `_cjpJudgeParity(items, taskWin)` | 4199–4243 (+doc 4169–4198) | 45 (+30) | **MOVE** | Window-bounded judge-parity repair; pure over params + `_contactGraph`; returns `{pushed, sweeps, floating, windowBlocked}` | `performance`/`Date` (guarded, :4201); `console.log` :4232 → hoist; `maxShift`+`ms` are log-only locals → return shape must grow (see §2) |
| `_contactGraph(items)` | 4528–4566 (+§MIDAIR_REPAIR doctrine 4478–4522, header 4523–4527) | 39 (+50) | **MOVE** | "The one place the physical world is derived" (:4523); pure, no log, no state; self-guards with `{ok:false}` when gate absent (:4530) | `ScheduleGate.CELL/EPS/GAP` (guarded `typeof`, :4529–4531) |
| `_designatedSupport(items, G)` | 4576–4614 (+header 4568–4575) | 39 (+8) | **MOVE** | Pure support election, mirrors cpm_schedule.js `designatedSupport` under §CPM_PARITY; no log, no state | `ScheduleGate.EPS/GAP/supportPool` (:4578–4579 — note `SG.EPS` deref is UNGUARDED against `SG===null`; safe today only because every caller goes through `_contactGraph`'s `G.ok` gate first; module must keep that call-order contract) |
| `_midairAudit(items)` | 4633–4646 (+header 4616–4632) | 14 (+17) | **MOVE** | Pure judge composition over the two functions above; no log, no state | none beyond its two callees |
| `_capWindowRescale(_allScheduled, _win)` | 5537–5621 (+body-doc §GANTT_GAP_CLAMP_SPREAD 5470–5518) | 85 (+49) | **MOVE (hoist)** | Nested inside `injectGantt` but PARAM-COMPLETE — body reads zero closure variables (verified line-by-line: only params, locals, `Math`); identity/affine/gap-clamp rescale, mutates arg in place | `console.log` §CAP_RESCALE_IDENTITY :5620 → hoist; currently returns `undefined` → must return `{skipped, rescaled}` (see §2) |
| `_displayTimeline(items)` | 4268–4351 | 84 | **STAY** | Owns the one-shot cross-consumer cache `_displayTimeline._last`/`._lastCell` (:4275, 4336, 4338), reads `window.LABOR_RATES` (:4311), `CpmSchedule.run`, emits 3 `§` logs + a `console.error` — state+logging is the parent's job by contract | function-property state, globals, logs |
| `_displayTimelineRemember(items, stragglerOf)` | 4359–4366 | 8 | **STAY** | Writes `_displayTimeline._last` — pure state plumbing | module state |
| `_tukeyBound(arr, lowSide)` | 4399–4401 | 3 | **STAY** | Already a thin delegate to `window.GanttModel.tukeyBound` (§S53/F3) — precedent's own wrapper pattern, nothing to extract | `window.GanttModel` |
| `_tmDisplayRemap(elements, schedule)` | 4402–4467 | 66 | **STAY** | Writes module state `_rawScheduleRemember` (:4388, assigned :4410), calls `_displayTimeline` (stateful), logs §ZONE_WINDOW_DAGWINS_CLIP (:4464) — it is the displayRemap HOOK, i.e. wiring, not physics | module state, `ScheduleGate.collapsePhase`, logs |
| `verifyGanttIntegrity()` | 4665–4717 | 53 | **STAY** | Reads app state: `_buildXrayElements()` (:4670), `_ops` (:4673), `_lockBaseline` (:4712); its physics core is `_midairAudit` + `ScheduleGate.auditFloating`, both already behind clean seams — becomes a consumer of the moved module | module state, DOM-adjacent app object |
| `captureLockBaseline()` | 4724–4730 | 7 | **STAY** | Mutates `_lockBaseline`, logs §GANTT_LOCK_BASELINE — pure state+log | module state, log |
| `var _CPM_DISPLAY` | 4267 | 1 | **STAY** | Kept as a named module var specifically so witnesses can inject `var _CPM_DISPLAY = true;` ahead of sliced copies (:4260–4264) — moving it breaks that convention for zero physics gain | witness-slice convention |
| `var _lockBaseline`, `var _rawScheduleRemember` | 4723, 4388 | 2 | **STAY** | Module state by definition | — |

**Flagged module-var/app-object readers (per task instruction):** `verifyGanttIntegrity` (`_ops`, `_buildXrayElements`, `_lockBaseline`), `captureLockBaseline` (`_lockBaseline`), `_displayTimeline` (own function-property cache + `window.LABOR_RATES`), `_tmDisplayRemap` (`_rawScheduleRemember`), `_displayTimelineRemember` (cache). None of the six MOVE functions reads a time_machine module variable or the app object `A()` — verified by reading each body in full. The only cross-file global the movers touch is `ScheduleGate` (already a separate script, `viewer/schedule_gate.js`), read via guarded `typeof` except the `_designatedSupport` note above.

**Extraction size:** moved code 215+45+39+39+14+85 = **437 code lines**, plus migrating comment blocks 13+30+50+8+17+49 = **167 comment lines** ≈ **604 lines out of time_machine.js** (9,194 → ≈8,590, −6.6%), landing in a new `viewer/support_sweep.js` of ≈640 lines incl. IIFE/export boilerplate (~25 lines, copied from `gantt_model.js:26–28, 200–206` pattern). Call-site framing comments that stay in time_machine.js: §GANTT_TASK_WINDOW_FIDELITY 5450–5469 (explains WHY rescale is called there), §CAP_RESCALE_SKIP 5519–5526 and §OG_SWEEP_SKIP 5624–5638 (DB-flag branching around the calls — that IS call-site logic).

## 2. Module contract — `viewer/support_sweep.js`

**Shape (copied from `gantt_model.js`, the shipped precedent):** one IIFE `(function (global) { 'use strict'; ... })(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this));` (gantt_model.js:26, :206), ending with dual-mode export (gantt_model.js:203–205):

```js
var API = { ogSupportSweep, cjpJudgeParity, contactGraph, designatedSupport, midairAudit, capWindowRescale };
global.SupportSweep = API;
if (typeof module !== 'undefined' && module.exports) module.exports = API;
```

**Header contract line, verbatim adaptation of gantt_model.js:17–19:** pure functions only — no module state, no TM variable, no DOM, **no console side effects**. time_machine.js owns state assignment and every `§` log line. NOTHING HERE IS NEW: every rule and every comment moves verbatim; the WHY stays attached to the rule (gantt_model.js:21–23).

**Exports and signatures** (public names drop the `_` prefix; time_machine.js keeps thin wrappers under the ORIGINAL private names so all internal call sites read unchanged — same pattern as the shipped `_tukeyBound` delegate, time_machine.js:4399–4401):

| Export | Signature | Mutation / notes |
|---|---|---|
| `ogSupportSweep(allScheduled, taskWin)` | → `{pushed, sweeps}` | **MUTATES `allScheduled` IN PLACE**: re-sorts it ascending by `bz` (:4046 in today's body — the sort persists after return and callers see the sorted array) and moves `item.s`/`item.e` LATER only, duration preserved (banner :3951–3952). `console.log` at :4164 is REMOVED from the body; the wrapper logs from the return value. |
| `cjpJudgeParity(items, taskWin)` | → `{pushed, sweeps, floating, windowBlocked, maxShiftMs, ms, ok}` | Mutates `items[i].s/.e` in place (monotone-later, window-bounded). Return grows ADDITIVELY: today `maxShift` and the elapsed `ms` are log-only locals (:4232–4236) — the module keeps its internal `t0` timing span byte-identical and RETURNS both so the wrapper can print the same numbers. The early bail at :4203 (`!G.ok`) returns `{pushed:0, sweeps:0, ok:false}` — wrapper logs NOTHING on that path, exactly as today (no log fires there now). |
| `contactGraph(items)` | → `{ok, contacts, grounded, orphans, groundedN}` | Pure. Self-guards: `{ok:false,...}` when `ScheduleGate`/`CELL` absent (:4529–4530). |
| `designatedSupport(items, G)` | → `Int32Array` (index of designated support per item, −1 = none) | Pure. **Documented precondition: `G.ok === true`** — its `SG.EPS` deref (:4578–4579) is unguarded against a missing gate; today that is safe only because every caller reaches it through `contactGraph`'s `ok` gate. The precondition moves into the migrated header comment. |
| `midairAudit(items)` | → `{midair, orphans, guids[≤20], ok}` | Pure judge, no mutation (:4633–4646). |
| `capWindowRescale(allScheduled, win)` | → `{skipped, rescaled}` | **MUTATES in place** (per-task identity check, affine re-base, gap-clamp re-space). Today returns `undefined` and logs at :5620 — log removed, return added so the wrapper prints §CAP_RESCALE_IDENTITY from it. Hoisted from nested scope (injectGantt) to module scope — legal because it is param-complete (verified §1). |

**Wrapper obligations in time_machine.js** (the parent owns state + `§` lines):
- `_ogSupportSweep(a, w)` wrapper: call module, then `if (r.pushed)` print the BYTE-IDENTICAL text of :4164–4165 — `'§PHASE_OVERLAP_SUPPORT_GUARD pushed=' + r.pushed + '/' + a.length + ' (sweeps=' + r.sweeps + ', bearing+hang) elements later than their §PHASE_OVERLAP_BAND window to stay after their real support'`. The historical §PHASE_OVERLAP_BAND wording is load-bearing for log-diff proof (§5) — do not reword.
- `_cjpJudgeParity(items, w)` wrapper: if `r.ok !== false`, print the :4232–4236 line verbatim, sourcing `maxShiftDays` from `(r.maxShiftMs/86400000).toFixed(1)` and `ms=` from `r.ms`.
- `_capWindowRescale(a, w)` wrapper: print the :5620 line verbatim from `r.skipped`/`r.rescaled`.
- `_contactGraph`/`_designatedSupport`/`_midairAudit` wrappers: bare delegates (no logs exist today; none added).

**ScheduleGate coupling:** the module reads the bare identifier `ScheduleGate` via guarded `typeof` AT CALL TIME, verbatim from today's bodies — no load-time dependency on `schedule_gate.js`, and in Node the lookup resolves through the global scope, so witnesses set `global.ScheduleGate` (or pass-through synthetic constants) before calling, exactly as their sliced copies already require. No new indirection is introduced (byte preservation beats dependency injection here — see §4).

**§LOAD_FAIL posture, copied from the gantt_model.js precedent** (viewer.html:943–945): a `<!-- -->` comment stating the must-load-before relationship, then
`<script src="support_sweep.js?v=1" onerror="console.warn('§LOAD_FAIL support_sweep.js — support-order physics unavailable (schedule repair, judge parity, lock-gate midair audit)')"></script>`
and the wrappers call `SupportSweep.*` UNGUARDED — same as `_tukeyBound`'s unguarded `window.GanttModel.tukeyBound` (:4400): a missing module fails loud and early at first use, never a silent degraded schedule.

## 3. Consumer impact

**Measured census** (`cd /home/red1/bim-ootb && git grep -n "sliceFn(tmSrc\|tmSrc\." origin/main -- viewer/tests scripts`): **107 matches across 28 files** — the task brief said "~139 slices, 28 files"; file count confirmed, match count is 107 by this exact pattern. The file wins: 107.

Of those 28 files, **15 execute or slice the MOVED physics** (list below); the other 13 slice only STAY functions (`witness_s50_cell_engine.js`, `witness_dlod_vf_camguard.js`, `witness_tm_erp_twin_guard.js`, `witness_tm_geo_order_cycles.js`, `witness_tm_panel_resize.js`, `witness_gantt_native_generate.js`, `witness_big_element_support_coverage.js`, `witness_gantt_bars_in_rect.js`, `witness_gantt_edit_lock.js`, `witness_gantt_edit_undo.js`, `witness_gantt_group_move.js`, `witness_gantt_refold_yield.js`, `witness_gantt_ruler_shift_lock.js` — slice targets verified one by one: `_buildXrayElements`, `_promoteRoofLoadPath`, `_classify*`, `_zone*`, `_dlod*`, `_loadTwin`, `wirePanelResize`, `generateGanttSchedule` etc.) — **zero edits** for those 13.

**Two slicing mechanisms exist** (both read and verified):
- **Named-function slice** — `sliceFn(src, name)` finds `'function ' + name + '('` and brace-counts (e.g. `witness_midair_zero.js:108–125`). Robust to indentation, coupled only to the function NAME existing in `time_machine.js`.
- **Raw text-marker slice** — only `witness_og_guard_bearing_bound.js:48–53` and `witness_gantt_og_grid_perf.js:39–46`: `startMark = 'var _ogCELL = '`, `endMark` = the ENTIRE two-line `§PHASE_OVERLAP_SUPPORT_GUARD pushed=` console.log statement INCLUDING its `\n        ` 8-space continuation indentation. See §4.

### Per-moved-function consumer map (before → after)

| Moved fn | Sliced/executed by (`file:line`) | What each call site becomes |
|---|---|---|
| `_ogSupportSweep` | `witness_zone_display_authoring.js:93`, `scripts/probe_captured_floating.js:32` (named); `witness_og_guard_bearing_bound.js:48–53`, `witness_gantt_og_grid_perf.js:39–46` (marker) | Named slices → prelude alias `var _ogSupportSweep = SupportSweep.ogSupportSweep;` with `SupportSweep = require('../support_sweep.js')` injected into the vm sandbox / `Function` params. Marker slices: see §4. |
| `_cjpJudgeParity` | `witness_crosstask_judge_parity.js:65`, `witness_zone_display_authoring.js:93`, `scripts/probe_captured_floating.js:33`, `scripts/probe_cpm_display_path.js:34` | Same alias pattern; the paired `sliceFn(tmSrc,'_contactGraph')` inside each `buildFn([...])` composite drops (the module resolves its own `contactGraph`). `buildFn` grows a second param: `new Function('ScheduleGate','SupportSweep', ...)`. |
| `_contactGraph` | 13 files: `witness_midair_zero.js:143`, `witness_zone_display_authoring.js:92`, `witness_hosted_before_host.js:184`, `witness_crosstask_judge_parity.js:63,65`, `witness_curtain_wall_opening.js:215`, `witness_kernel_ops_sched_version.js:109`, `witness_gantt_lock_integrity.js:128` (conditional `_names.unshift`), `scripts/probe_captured_floating.js:30,33`, `probe_cpm_display_path.js:32,34`, `probe_cpm_schedule.js:39`, `probe_e3_synthetic.js:34`, `probe_proxy_carrier_classes.js:25`, `probe_schedule_engine.js:33` | `var _contactGraph = SupportSweep.contactGraph;` alias. `probe_e3_synthetic.js:34`'s `new Function('ScheduleGate', slice + 'return _contactGraph;')(ScheduleGate)` becomes simply `SupportSweep.contactGraph` (module reads `ScheduleGate` as a global at call time — the probe sets `global.ScheduleGate` before calling, or keeps the Function-param form with `SupportSweep` passed in). |
| `_designatedSupport` | 11 files (same lines as above minus `probe_e3_synthetic`/`probe_proxy_carrier_classes`, plus `probe_captured_floating.js:31`, `probe_cpm_display_path.js:33`, `probe_cpm_schedule.js:40`, `probe_schedule_engine.js:34`, `witness_crosstask_judge_parity.js:64`) | `var _designatedSupport = SupportSweep.designatedSupport;` — note `probe_cpm_schedule.js` diffs this pair contact-for-contact against `cpm_schedule.js`'s own copy (§CPM_PARITY, `time_machine.js:4568–4575`); after the move it diffs MODULE vs `cpm_schedule.js` — the parity witness gets STRONGER (it now checks the one real copy, not a text snapshot). |
| `_midairAudit` | 6 files: `witness_midair_zero.js:143`, `witness_zone_display_authoring.js:94`, `witness_hosted_before_host.js:184`, `witness_curtain_wall_opening.js:215`, `witness_kernel_ops_sched_version.js:109`, `witness_gantt_lock_integrity.js:128` | `var _midairAudit = SupportSweep.midairAudit;` alias — required because the STAY functions these harnesses also slice (`_displayTimeline` calls `_midairAudit(` at :4298/:4319; `verifyGanttIntegrity` at :4701) resolve it as a bare identifier at run time. |
| `_capWindowRescale` | 2 files: `witness_zone_display_authoring.js:94`, `scripts/probe_cpm_display_path.js:36` | `var _capWindowRescale = SupportSweep.capWindowRescale;` — and because the hoist moves the function OUT of `injectGantt`, `sliceFn(tmSrc,'_capWindowRescale')` would otherwise stop resolving; the alias removes the dependency entirely. |

### The six task-named witnesses, specifically
- **`witness_midair_zero.js`** (572 lines): 9 `tmSrc` refs; 3 are moved-fn slices (:143, one line). Change: drop those 3 from the `sliced` join array (:138–144), add the 3 aliases + `SupportSweep` to the sandbox at :360–368 — the EXACT pattern the sandbox already uses for `GanttModel` post-§S53 (`:364 — "§S53: sliced code's delegates read window.GanttModel"`). Its own `census()` (:297+) is an INDEPENDENT judge by design (comment :296) — untouched. The `verifyGanttIntegrity`-body text assert (:168–170, `_vgiBody.indexOf('_midairAudit(') > 0`) SURVIVES because the stay-behind `verifyGanttIntegrity` keeps calling the wrapper by that name.
- **`witness_zone_display_authoring.js`** (260 lines): 10 refs; 6 moved-fn slices (:92–94) → aliases; the CALL-SITE regex asserts at :58–62 (`else { _ogSupportSweep(_allScheduled, _cap.win);` and `_cjpJudgeParity(...)` within 2600 chars of `_cjpDisplayAuthored`) SURVIVE UNEDITED because call sites keep name, text, and indentation (§2 wrapper rule).
- **`witness_hosted_before_host.js`** (299 lines): 3 moved-fn slices (:184) → aliases; sandbox at :201–202 gains `SupportSweep`.
- **`witness_crosstask_judge_parity.js`** (219 lines): 4 moved-fn slices (:63–65) → module refs; wiring asserts at :94–96 (`sweepCall`/`parityCall` indexOf order, `< 300` chars apart) SURVIVE — call sites at `time_machine.js:5640/5642` unchanged.
- **`witness_og_guard_bearing_bound.js`** (207 lines): the marker-slice consumer — see §4 for its migration (named slice of `support_sweep.js` source; its text-perturbation variant machinery keeps working because interior bytes move verbatim).
- **`witness_gantt_og_grid_perf.js`** (184 lines): marker slice + vm execution (`vm.runInContext(block, sandbox)` :147/:168 region, reads `sandbox._ogPushed`/`_ogSweeps` from the block's leaked vars) → direct call `SupportSweep.ogSupportSweep(_allScheduled, undefined)` with `global.ScheduleGate = { CELL: 4 }` set first; reads `r.pushed`/`r.sweeps` from the return value. The O(n²) `bruteForcePush` reference (:77–126) and the <15s Terminal perf gate stay as-is. Its brace-balance assert (block `depth === 0`, :48–50) retires with the marker slice.

### Slice counts
- **Before:** 41 named-function slices of moved physics (sum over the 15 files: mz 3, zda 6, hbh 3, cjp 4, cwo 3, kos 3, gli 3-conditional, pcf 5, pcd 5, pcs 2, pe3 1, ppc 1, pse 2) + 2 raw text-marker block slices = **43 slices of the moved region**.
- **After:** **1** (witness_og_guard_bearing_bound's named-function slice of `support_sweep.js`, kept because its perturbation variants need source text to mutate). **42 slices retired.** `witness_gantt_lock_integrity.js`'s presence gate (`tmSrc.indexOf('function _midairAudit(') >= 0`, :127) stays true via the wrapper, so its "both-commits pattern" keeps working on older revisions too.
- Everything in the other 13 files (66 of the 107 grep hits): untouched.

## 4. Preservation landmines

**The warning comment, read at `time_machine.js:3944–3950`** (inside the §PHASE_OVERLAP_SUPPORT_GUARD banner 3940–3952): "The block's interior bytes and ORIGINAL INDENTATION are deliberately preserved: two witnesses (witness_og_guard_bearing_bound.js, witness_gantt_og_grid_perf.js) slice it by text markers (the `_ogCELL` declaration → the §PHASE_OVERLAP_SUPPORT_GUARD log statement, whose historical §PHASE_OVERLAP_BAND wording is part of the end-mark bytes) and execute it against synthetic `_allScheduled` arrays — re-indenting, rewording the log, or renaming variables would rot both (that exact rot killed witness_gantt_og_grid_perf once already, 2026-08-07..11)."

**Verified mechanism** (`witness_gantt_og_grid_perf.js:39–46`, identical marks in `witness_og_guard_bearing_bound.js:47–53`): `startMark = 'var _ogCELL = '` (matches `time_machine.js:3970`); `endMark` is the ENTIRE two-line console.log statement at :4164–4165 as a byte string, INCLUDING the `\n` + exactly 8 spaces of continuation indentation. The perf witness's own comment at :40 records the prior kill: "Current guard summary line (reworded by §4D_LAYER_TRUTH 2026-08-07 — the rot that killed v1)."

**What the extraction does to those bytes:** hoisting the log to the wrapper (§2) REMOVES the endMark bytes from `time_machine.js`; moving the body removes the startMark too. Both witnesses then `throw new Error('... mark not found — has the block been renamed/moved?')` — loud, at slice time, on the first run. So the two witness edits are **part of the extraction commit, not a follow-up** (same one-commit discipline as sw.js in §6).

**How the extraction retires this rot class instead of repeating it:**
1. **No raw text markers survive.** `witness_gantt_og_grid_perf.js` stops slicing entirely — it calls `SupportSweep.ogSupportSweep(_allScheduled, undefined)` directly (its sandbox already only injected `{ScheduleGate:{CELL:4}, console, Math}` — those become `global.ScheduleGate` + the return value replaces the leaked `sandbox._ogPushed`/`_ogSweeps` reads). `witness_og_guard_bearing_bound.js` still needs SOURCE TEXT (it builds reference variants by substring substitution — e.g. its :101 check `block.indexOf('var _ogTopBound = T.tz + _ogGAP')`), so it re-points `fs.readFileSync` at `viewer/support_sweep.js` and slices with the standard **named-function `sliceFn`** (brace-counting from `'function _ogSupportSweep('`) — immune to indentation, log wording, and everything except the function's NAME, which is also what every other consumer already depends on.
2. **Interior bytes move verbatim — this is a hard rule of the extraction, not a preference.** The six function bodies (declaration line, parameter names `_allScheduled`/`taskWin`/`items`/`G`/`_win`, every local `_og*`/`_cjp*` name, every comment, current indentation) are copy-paste moves. The ONLY interior deltas allowed are the ones §2 names: (a) delete the 3 console.log statements (`:4164–4165`, `:4232–4236`, `:5620`), (b) the additive return-shape fields (`maxShiftMs`/`ms`/`ok` in `_cjpJudgeParity`, `{skipped, rescaled}` in `_capWindowRescale`). This is what keeps `witness_og_guard_bearing_bound`'s substitution targets (`var _ogTopBound = T.tz + _ogGAP`, :4066) matching unchanged.
3. **Amendment to §2 (byte preservation beats naming cosmetics):** inside `support_sweep.js` the declarations KEEP their original private names (`function _ogSupportSweep(...)`, `_cjpJudgeParity`, `_contactGraph`, `_designatedSupport`, `_midairAudit`, `_capWindowRescale`) so the internal call graph (`_cjpJudgeParity`→`_contactGraph` :4202, `_midairAudit`→`_contactGraph`/`_designatedSupport` :4636/:4639) moves with zero edits; the API map exports them under the public names: `var API = { ogSupportSweep: _ogSupportSweep, cjpJudgeParity: _cjpJudgeParity, ... }`. (`gantt_model.js` renamed on move — it could afford to, nothing text-slices it; this region cannot.)
4. **The banner comment migrates AND gets its slicing paragraph rewritten** to describe the new reality (named-fn slice by `witness_og_guard_bearing_bound.js` only; everyone else calls the module). Leaving the old marker description in place would plant the next landmine — a future session "preserving" markers that no longer exist. The log-wording sentence is REPLACED by: the `§PHASE_OVERLAP_SUPPORT_GUARD` line now lives in the time_machine.js wrapper and is pinned by the §5 log-diff — still do not reword it.
5. **Call-site text in `time_machine.js` is frozen byte-for-byte** (`_capWindowRescale(_allScheduled, _cap.win);` :5535, `_ogSupportSweep(_allScheduled, _cap.win);` :5640, `_cjpJudgeParity(_allScheduled, _cap.win);` + trailing comment :5642, and the `else {` shapes around them) — four surviving text assertions depend on it: `witness_zone_display_authoring.js:58–62` (regex incl. the else-branch newline/indentation), `witness_crosstask_judge_parity.js:94–96` (indexOf order + `<300` chars apart), `witness_midair_zero.js:168–170`, `witness_gantt_lock_integrity.js:127`.

## 5. Proof protocol

All commands run from a bim-ootb worktree with the extraction applied; `BLD_DIR=$HOME/bim-ootb/buildings` (real fleet DBs). Save every run to a log file and read the log (Log Mandate) — exit code is not evidence.

**P0 — control baseline (ALREADY RUN for this spec, origin/main sandbox, 2026-08-21):**
```
cd <tree>/viewer/tests && BLD_DIR=$HOME/bim-ootb/buildings node witness_midair_zero.js > mz_before.log 2>&1
```
Measured result on origin/main (log `scratchpad/mz.log`, 174 lines): exit 0, **`§MIDAIR_ZERO_SUMMARY pass=49 fail=0`**, 49 PASS / 0 FAIL lines. This is the required before-number; after the extraction the same command must again read **`pass=49 fail=0`**.

**P1 — normalized log diff (the primary identity gate):**
```
node witness_midair_zero.js > mz_after.log 2>&1
norm() { sed -E 's/ms=\{[^}]*\}/ms={NORM}/g; s/(compileMs|ms)=[0-9.]+/\1=NORM/g' "$1"; }
diff <(norm mz_before.log) <(norm mz_after.log)
```
Must be EMPTY. The only volatile fields observed in the real control log are timings — both the scalar form (`compileMs=288`, `ms=...`) and the composite form (`ms={contact:231,axis+solve:468,total:699}` on §CELL_RUN/§CPM_RUN lines) — the normalizer above covers both. Every count, guid, baseline and § line must be byte-identical.

**P2 — the rest of the executor fleet, before AND after, same normalized-diff rule:**
```
for w in witness_zone_display_authoring witness_hosted_before_host witness_crosstask_judge_parity \
         witness_og_guard_bearing_bound witness_gantt_og_grid_perf witness_gantt_lock_integrity \
         witness_curtain_wall_opening witness_kernel_ops_sched_version; do
  BLD_DIR=$HOME/bim-ootb/buildings node viewer/tests/$w.js > /tmp/$w.after.log 2>&1; echo "$w exit=$?"; done
node deploy/dev/tests/audit_specs.js   # only if Playwright specs were touched (none should be)
```
Each must exit 0 with its own summary line all-pass; `witness_gantt_og_grid_perf`'s §OG_GRID_PERF `ms=` and `witness_gantt_lock_integrity`'s `ms=` normalize away, its Terminal `<15000ms` assert must still PASS. The 6 `scripts/probe_*.js` executors are probes, not gates — run `probe_cpm_schedule.js` at minimum (it is the §CPM_PARITY diff that now compares module-vs-cpm_schedule.js contact-for-contact).

**P3 — baselines that must not move:** `viewer/tests/baselines/midair.json` (read in full for this spec) — all three groups, seven buildings each, byte-identical:
- `float_after_cpm`: Terminal 554, Hospital 935, Duplex 44, HHS_Office_Federated 889, Clinic 324, LTU_AHouse 5023, JKR 1222
- `midair`: Terminal 684, Hospital 218, Duplex 0, HHS_Office_Federated 0, Clinic 422, LTU_AHouse 0, JKR 0
- `orphans`: Terminal 25, Hospital 35, Duplex 1, HHS_Office_Federated 36, Clinic 27, LTU_AHouse 865, JKR 1
The extraction commit must NOT touch this file; any FAIL against it is a real behavior change, i.e. the move was not verbatim.

**P4 — perturbation runs (each must produce its specific FAIL, proving the witnesses still bite AFTER re-pointing).** Apply to a scratch copy, run, confirm the named FAIL id, revert:
1. **In `support_sweep.js` `_ogSupportSweep`, change tier-2 gating `if (!lastEnd && envEnd) lastEnd = envEnd;` (moved from :4104) to unconditional `if (envEnd) lastEnd = envEnd;`** → `witness_og_guard_bearing_bound.js` must FAIL its bearing-bound assert (W-OGB family — the reference-variant comparison detects the enveloping-carrier gate regressing to always-on) AND `witness_gantt_og_grid_perf.js` must FAIL its brute-force-match assert (`Duplex: grid-based push decisions match the O(n^2) brute-force reference ... mismatches=0` goes non-zero) — proves both re-pointed witnesses still execute the REAL module, not a stale copy.
2. **In `support_sweep.js` `_cjpJudgeParity`, drop the day-rounding tolerance: `if (first + dur > w.e + _CJP_DAY_TOL)` (moved from :4218) → `if (first + dur > w.e)`** → `witness_crosstask_judge_parity.js` must FAIL (its W-CJP relative asserts: strictly-reduces / WINDOW_BLOCKED accounting shifts) — proves the module path feeds the parity witness.
3. **In `support_sweep.js` `_midairAudit`, weaken the judge: `if (items[sIdx].s > items[i].s + 1)` (moved from :4642) → `+ 86400000`** → `witness_midair_zero.js` must FAIL W-MZ-2 on the cell-path buildings (locked midair: Terminal 684, Hospital 218, Clinic 422 would collapse toward 0 — a LOWER number is still a baseline mismatch, that is the point of a lock) — proves the harness's aliased `SupportSweep.midairAudit` is the judge being locked. (W-MZ-7, the witness's own can-go-red probe, must also flip.)
4. **Wrapper-side control: reword the `§PHASE_OVERLAP_SUPPORT_GUARD` log line in the time_machine.js wrapper** (e.g. drop the `§PHASE_OVERLAP_BAND` token) → P1's normalized diff goes NON-empty on any building where `pushed>0`. Proves the log-diff instrument pins wording even now that no text marker does.

**P5 — zero-slice residue check:** `git grep -n "startMark\|endMark" -- viewer/tests` must return hits only in files that no longer reference `time_machine.js` for them (i.e. none); `git grep -n "sliceFn(tmSrc, '_ogSupportSweep'\|sliceFn(tmSrc, '_cjpJudgeParity'\|sliceFn(tmSrc, '_contactGraph'\|sliceFn(tmSrc, '_designatedSupport'\|sliceFn(tmSrc, '_midairAudit'\|sliceFn(tmSrc, '_capWindowRescale'" -- viewer/tests scripts` must return ZERO rows.

## 6. Wiring

**viewer.html** (read at sandbox `viewer/viewer.html`): the schedule stack loads at :934–948 — `schedule_gate.js?v=3` (:934) → `cpm_schedule.js?v=2` (:942) → `gantt_model.js?v=1` (:945, with its two-line "Must load BEFORE time_machine.js" comment at :943–944) → `time_machine.js?v=71` (:946) → `schedule_author.js?v=14` (:948). **New tag goes between :945 and :946**, copying the precedent comment + §LOAD_FAIL warn (§2):
```html
<!-- §S58: support-order physics (sweep/judge/rescale), extracted out of time_machine.js.
     Must load BEFORE time_machine.js — its _ogSupportSweep/_cjpJudgeParity/_contactGraph/
     _designatedSupport/_midairAudit/_capWindowRescale wrappers call it. -->
<script src="support_sweep.js?v=1" onerror="console.warn('§LOAD_FAIL support_sweep.js — support-order physics unavailable (schedule repair, judge parity, lock-gate midair audit)')"></script>
```
`time_machine.js`'s own tag bumps `?v=71` → `?v=72` (its content changes).

**sw.js** (read at sandbox `viewer/sw.js`) — ALL in the SAME commit as the code change (the missed-bump on PR #1409 cost a full round-trip; standing MANDATORY rule):
- `PRECACHE_ASSETS` (:298): add `'support_sweep.js',` next to its family — `'schedule_gate.js'` :390, `'cpm_schedule.js'` :393, `'gantt_model.js'` :394 (which carries the §S53 extraction comment to copy), `'time_machine.js'` :395 — with a `// §S58 — support-order physics, extracted from time_machine.js` comment.
- `CACHE_VERSION` (:11): `'v1063'` → `'v1064'`, plus a `// v1064 (date) 4D_GANTT_TM_REFACTOR.md §S58: support-order physics extracted time_machine.js → NEW support_sweep.js (precached, viewer.html loads it first); behavior-identical (witness_midair_zero pass=49 fail=0 before/after, normalized log diff empty)` changelog line in the header block — same format as the v1063 entry at :12–27.
- **`_GANTT_CACHE_VERSION` is NOT bumped** — the sw.js header bumps it only when "the schedule shape changes" (v1062 comment, :22/:27); this extraction is proven shape-identical by §5, so regenerating every user's pre-stamped kernel_ops would be pure waste.

**Audits — corrected paths (the task brief said `viewer/tests/…`; the tree says otherwise, the file wins):** both live at repo root `tests/` (verified `git ls-tree origin/main`):
- `node tests/audit_sw_precache.js` — verifies every `PRECACHE_ASSETS` entry exists on disk (header: "Missing precache file → offline user gets blank page"). Must exit green with the new entry.
- `node tests/audit_script_tags.js` — verifies every `<script src>` in viewer.html resolves to a real file (header: "Typo in script tag → silent 404 → feature silently missing"). Must exit green with the new tag.
Note their real coverage direction: both verify LISTED things EXIST — neither can detect an OMITTED precache entry (see §7).

## 7. Risk

**What breaks silently, and what catches it:**
1. **Non-verbatim move (a reflowed predicate, a "tidied" comment, a renamed local):** caught three ways — P1's empty normalized log diff, P3's locked `baselines/midair.json` (any drift in the three 7-building groups), and `witness_gantt_og_grid_perf`'s O(n²) brute-force parity on Duplex. Also `witness_og_guard_bearing_bound`'s substitution targets stop matching → loud throw.
2. **sw.js precache omission or CACHE_VERSION not bumped — THE RISKIEST ITEM.** Nothing in the witness fleet runs offline; `tests/audit_sw_precache.js` checks listed-entries-exist, NOT needed-entries-are-listed, so an omitted `'support_sweep.js'` passes every audit and every witness while existing offline users get a Time Machine whose schedule repair, judge parity and lock gate all throw `ReferenceError: SupportSweep is not defined` on first use. Catch: the §6 same-commit checklist is the only guard — treat it as part of the definition of done, exactly like the `_writeScheduledChunked` deliverable rule ("patch AND loader together"). The failure is at least LOUD in console when it happens (unguarded wrapper calls, by design, §2).
3. **`witness_gantt_lock_integrity`'s presence-conditional slice (`:127 tmSrc.indexOf('function _midairAudit(') >= 0`):** if a later cleanup deletes or renames the wrappers, the condition silently goes FALSE and the witness keeps passing while no longer exercising the midair judge in the lock gate — a green witness testing less. Guard: the wrapper names are declared FROZEN (§4 point 5), and the migrated witness should convert this from a conditional to an unconditional alias so absence throws instead of skipping.
4. **A consumer left un-migrated** (most likely one of the 6 rarely-run `scripts/probe_*.js`): its `sliceFn(tmSrc,'_x')` now slices a thin wrapper; executing it in a sandbox without `SupportSweep` throws `ReferenceError` — loud on next run, and P5's zero-residue grep catches it textually at extraction time without waiting for that run.
5. **Load-order mistake** (tag after time_machine.js): harmless at load (wrappers bind at call time), and the first `injectGantt`/`verifyGanttIntegrity` call would still find the module loaded; a 404/typo is the real hazard and `tests/audit_script_tags.js` + `§LOAD_FAIL` cover it.
6. **Future physics edits landing in the wrong file** (someone "fixes" the wrapper instead of the module): the migrated banner's rewritten pointer (§4 point 4) plus the fact that wrappers are 3-line delegates makes the wrong-file edit obvious in review; the log-diff instrument catches any wrapper-side wording drift (P4.4).

**Stays behind (cannot move cleanly, and why — full detail in §1):** `verifyGanttIntegrity` + `captureLockBaseline` (app/module state: `_ops`, `_buildXrayElements`, `_lockBaseline`), `_displayTimeline` + `_displayTimelineRemember` (the one-shot cross-consumer schedule cache), `_tmDisplayRemap` (writes `_rawScheduleRemember`, is the displayRemap hook), `_tukeyBound` (already a GanttModel delegate), `_CPM_DISPLAY` (witness-injection convention). None of these is physics; all become consumers of the module. No candidate function was found unmovable-but-desired: all six physics functions are param-complete (the one surprise — `_capWindowRescale` — was verified param-complete despite being nested).

---

## Summary (return values)
- **Extraction size:** 6 functions, 437 code lines + 167 migrating comment lines ≈ **604 lines out of `time_machine.js`** (9,194 → ≈8,590), into a new ≈640-line `viewer/support_sweep.js`.
- **Slices retired:** **42 of 43** slices of the moved region (41 named-function + 2 raw text-marker, across 15 of the 28 tmSrc-consuming files; measured census 107 grep hits — not the ~139 the brief estimated). The 1 survivor is `witness_og_guard_bearing_bound.js`'s named-function slice of `support_sweep.js`, kept for its text-perturbation variants; the indentation/log-wording marker coupling is retired entirely.
- **Riskiest item:** the sw.js `PRECACHE_ASSETS` + `CACHE_VERSION` (v1063→v1064) same-commit bump — the only breakage class no witness or audit can catch (audits verify listed-exists, not needed-is-listed), and it fails only for offline users of the previous cache.
- **Real control baseline already banked:** `witness_midair_zero.js` on origin/main against the real fleet = `pass=49 fail=0`, exit 0 (log at `scratchpad/mz.log`; volatile fields are only `ms=`/`compileMs=`/`ms={...}`).

---

# S59 — Viewer Fleet Refactor Survey (seams table)

Survey of 14 viewer files at origin/main (sandbox: scratchpad/wdog2/viewer/).
No product code, no PR. Priority 1 = extract first, 5 = leave alone.
PROTECTED LANE (user ruling): effects.js, cinema_path_editor.js, cinema_maxq.js — camera beats/pacing/gaze/orbit off-limits; only pure query/util helper extraction may be suggested.
NOTE: viewer/time_machine.js excluded — owned by another agent.

| File | Lines | What it actually does | Witness coverage (tests referencing) | Priority | Reason |
|---|---|---|---|---|---|
| effects.js | 8803 | Photoreal staging: staffage (people/cars/sprites, occupancy-grid + raycast placement, effects.js:996-1728), photo props/skyline/sparkles/glow/ember (395-536, 4124-4560), puddle+HDRI shaders (3052-3260), still-refine AO passes (3642-4838), CPE reveal visual/caption/qty-cost helpers (5181-5365), legacy cinema orbit (8581) | **0 files** | 5 | PROTECTED LANE + zero witnesses — nothing can prove an extraction safe. Only permissible seam: pure DB/bbox helpers `_bboxZFenced` (578-602), `_buildingBBoxArc` (613-616), `cpeRevealDiscQtyCost` (5293-5310) — but no witness would catch a regression, so even that is disqualified now |
| navigate_find.js | 5452 | Find panel: one giant `init(A,nav,...)` closure (navigate_find.js:17) holding accordion UI/CSS (19-349), view history (350-541), room tree + room-graph pathfinding (634-679, 1227-1415), merged-ghost lens (1544-1657), 5D selection cost + ERP push/fold (1688-1994), room cuboid/shell/category reveal (2089-2967), material/phase/storey/disc trees (2992-3628, 4215-4325), isolate/drill (3966-4065), voice mic (4326+) | **9 files**: witness_disc_friendly_labels, witness_shakeout_2026-07-06, witness_isolate_zoom, witness_find_close_no_leak, witness_find_panel_hidden_onload, poc_zoom_scope_live, witness_corridor_reveal_shell, witness_room_box_purple, witness_room_select_door | 2 | Best-covered big file; already an extraction from navigate.js. Clear seams: 5D cost block (1700-1994, mostly pure rate/DB math) and `_findRoomPath` graph search (1362-1415, pure). Cost: all closure-scoped, so moves need explicit param plumbing |
| cinema_path_editor.js | 3668 | CPE: interactive fly-path editor — rigid bands (tangent segments) + hose pulls + sticks on the cinema pipe (cinema_path_editor.js:6-15, 313-449), replan/preview (454-506), undo/redo snapshots (944-1006), panel + scrub bar + viewfinder pose (796-1443) | **1 file**: witness_dlod_vf_camguard.js | 5 | PROTECTED LANE (bands/aim/pacing ARE the file) and only 1 witness. The few pure helpers (`_norm` 240, `_arcFractions` 715, `_fmtMMSS` 1268) are too small to justify a move; no witness would lock them |
| scene.js | 3064 | App hub: sky/fog/envmap/lensflare (scene.js:129-449), ifc↔three transforms (468-481), IDB cache DB (531-658), Save/Open building .db + §SCENE_MERGE fold (660-1378), chunked SQL/ghost compose/blob→geometry (1379-1618), home framing (1619-1830), shortcuts + command palette + keyboard handler incl. Alt+C route (2031-2289, 2769-2962; Alt+C confirmed in 2780-2800 window), PWA offline install/update (2290-2647), panel registry (2688-2767), GPU baseline (3029) | **13 files**: poc_db_cache_key_live, connect_harness.html, witness_undo_dot_spawn, witness_room_cycle_home, connect_modeller_live, witness_class_outline_live, witness_db_cache_key, connect_peer.html, witness_scene_merge, witness_pill_drawer_followup, poc_about_help_restore, witness_db_404_oci_retry, witness_panel_abstraction | 1 | Best witness coverage in the fleet and least cohesive file (7+ unrelated domains). Save/Open+merge block (660-1378) and cache-DB block (531-658) each have DEDICATED witnesses (witness_scene_merge, witness_db_cache_key) that lock behaviour through an extraction |
| streaming.js | 2733 | Geometry streaming pipeline: stream start/building pick (streaming.js:63-211), bbox placeholders (233-327), material factory w/ class+STD_MAT+triplanar shader rules (365-883), streamTick fetch sync/httpvfs-range (978-1292), flush to Batched/Instanced/Merged mesh + merged raycast (1293-2006), consolidate (2007), three-phase split-DB load positions.bin→meta.db→geo.db (2221-2443), URL hash (2630) | **4 files**: witness_real_placement_resolver, witness_big_element_support_coverage, witness_hba_iot_lod_device_meshes, witness_class_outline_live | 3 | Cohesive perf-critical pipeline — big but single-purpose, so size alone is no reason. The one real seam is the ~518-line material factory (365-883); only witness_class_outline_live/witness_hba_iot_lod_device_meshes partially touch material behaviour, so coverage is thin for that move |
| panels.js | 2458 | Panel/pill chrome + filter engine: ICONS registry + icon/panel factories + autoplace (panels.js:7-305), Sunglass panel incl. cinema row (307-497; legacy `A.startCinemaOrbit()` fallback confirmed at ~431), list key-nav (498-642), storey/disc/role filters + `filterDiscs`/`_applyDiscVisibility`/guid filter (700-895), building list/HUD (897-961), pill drawers + panel actions (962-1558) | **10 files**: witness_role_filter, witness_hba_pill_desync_fix, witness_disc_friendly_labels, witness_class_outline_live, witness_pill_drawer_followup, witness_find_panel_hidden_onload, poc_hba_mobile_stack, witness_pill_drawer_mobile_position, test_pills_manifest, witness_panel_abstraction | 3 | Well-witnessed, but the file is mostly one domain (panel/pill UI). The genuine seam is the visibility/filter engine (700-895) which navigate_find/effects both call; witness_role_filter + witness_class_outline_live lock it. Worth doing only after scene.js |
| tour.js | 2281 | Fly-around / walk-through tour engine: fly toggle + route decide (tour.js:8-179), IDB route cache + bust/prune (180-306), corridor-graph route build w/ A* (465-814), tour build + nn-sort (815-1102), pacing remap `_invPace`/`_losPace`/`_paceLookup` (1143-1209), action pose interpolation + seek/beat + walkTick smoothing (1216-1600+) | **0 files** | 5 | Cohesive single-domain motion engine with ZERO witnesses — any extraction is unprovable, and pacing/pose code is exactly the class of behaviour the protected-lane ruling exists to shield elsewhere. Leave alone; flagged under dangerous coverage below |
| doc_canvas.js | 2115 | Doc-pill canvas: envelope wireframe + fresh 2D grid (AABBCC bubbles/dimensions, doc_canvas.js:508-759), BOM-driven phase loader + Gantt-step materialize (760-924), HUD/camera-fit (926-984), Rosetta grid-calibration mode + drag (996-1199), per-discipline rules + double-click grid add/remove (1222-1320+) | **0 files** | 4 | Zero witnesses (its stated witness W-DOC-CANVAS lives outside viewer/tests — nothing here proves it) and a self-declared "clean start, no legacy baggage" module (doc_canvas.js:9). Cohesive; not a refactor target, and extraction would be unprovable anyway |
| dlod_nav.js | 2029 | DLOD navigation state machine: instanced proxy boxes vs real meshes (dlod_nav.js:343-500), cross-fade transitions (502-569), room-occlusion demote criterion + PVS via room graph (570-660), occlusion BVH build + GPU depth-query culling (669-1183; occl-BVH known-disabled per project memory) | **0 files** (basename grep; its W-DLOD-NAV-* witnesses are named in the header dlod_nav.js:6-8 but no viewer/tests file references the file) | 5 | Perf-critical single-domain state machine, zero in-repo test references — extraction here is exactly "code nothing can prove". Leave alone; flagged under dangerous coverage below |
| grid_overlay.js | 1932 | 3D grid-overlay mode: storey-aware cut plane (grid_overlay.js:57-111), grid bubbles/lines from GridDims detection (191-527), view presets + contour rendering (528-712), dim chains delegated to DimChains (713-726), saved sections in localStorage+DB (727-1092), auto cards + panel + click-to-zoom (1093-1414+) | **0 files** | 4 | Zero in-tests references; already delegates to GridViews/DimChains/GridDims modules (528, 713, header:12) — the extraction pattern was applied here once and stalled. Finishing it (saved-sections block 727-1092 is DB/localStorage logic, no scene semantics) is only safe once a witness exists; today nothing would catch a regression |
| measure.js | 1901 | Two domains in one file: (a) clash-detection engine — rules load, R-tree indexes, pair queries, severity, status persist, fly-to, list/matrix panels, deep-links (measure.js:72-1118); (b) the actual measure tool — two-point distance, mesh area/volume (1119-1343+); plus the fleet-shared `A._makeDraggable` panel helper (13-71) | **1 file**: poc_z_events.js | 4 | Cleanest domain split in the fleet (clash engine ≠ measuring), but effectively unwitnessed — the single referencing test is a z-event poc, not clash math. Per the rules, extraction is disqualified until the clash queries get a witness |
| schedule_author.js | 1703 | Pure DOM-free 4D authoring engine (self-declared "Pure, DOM-free, node-testable", schedule_author.js:9): rule-match + install-secs (17-104), zone/default materialize into schedules/tasks/task_elements (382-711), contiguous scheduling + cost fold (792-931), WBS tree + dependency CRUD with cycle guard (932-1073), bounded CPM + constraint-aware move/cascade/resize (1074-1343), baseline/variance (1344-1401), add/reparent/breakdown + IDB persist (1492-1600+) | **22 files** (witness_gantt_native_generate, witness_s50_cell_engine, witness_tm_refold, witness_crew_demand, witness_gantt_baseline, witness_crosstask_judge_parity, ... full list from grep) | 5 | Nothing to do — this file is the fleet's model OUTPUT of a good extraction: already pure/headless and the most-witnessed file per line in scope (22 tests). Refactoring it would only put settled 4D-lane behaviour at risk |
| tools.js | 1693 | Viewer toolbox: ground-Y calc + ground shader/texture (tools.js:8-290), wireframe/x-ray/bbox-ghost cycle (291-382), section cut (383-473), 4D5D export/screenshot/fullscreen/theme (474-557), sunglass 100-step ambience palettes + recolor (558-762), lighting/shadow/background (763-1007), night-mode light colors/glow (1008-1060+) | **1 file**: poc_z_events.js | 4 | A deliberate toolbox (header tools.js:3) of many small independent verbs — no single seam is big enough to earn a move, and with one incidental witness nothing would prove one safe |
| cinema_maxq.js | 1582 | Max-Quality Orbit bake (`A.startMaxQualityOrbit`, attached cinema_maxq.js:1553): frame-by-frame offline render + MediaRecorder capture (689-958), work-paced 4D buildup cursor + topout remap (95-190), ghost-ground curve (191-460), IDB frame store (542-606), deterministic Math.random freeze (607-612), visibility/wake handling (629-688), pose/stick-approach interpolation (959-1015) | **0 files** by basename (pure fns are exported onto APP "for the witness" at 1560-1578 — buildupTAt, ghostGroundDebugState, maxqStatusDayRoomSegs — so some witnesses may call them without naming the file) | 5 | PROTECTED LANE (pacing/buildup/orbit IS this file) + zero basename references. Its pure mappings are ALREADY exposed as one-implementation-two-callers exports (1556-1578) — the extraction this survey would suggest has effectively been done in place |

## Top 2 candidates

### 1. scene.js — extract the Save/Open + §SCENE_MERGE building-IO block (priority 1)
Move, as one unit (the db_resolve.js precedent: `cacheKey` was already extracted from scene.js into a pure module and its witness `require`s it verbatim — witness_db_cache_key.js:12-13):
- Export writers: `_writeStaffageTable` scene.js:668, `_writeCinemaPathTable` scene.js:694, `_writeSceneStateTable` scene.js:731, `A._exportBuildingDb` scene.js:752-816
- Merge: `A._showMergeModal` scene.js:824-875, `_mergeCols` scene.js:876, `_mergeTable` scene.js:888, `_georefPin` scene.js:914, §SM-7.1 fold steps scene.js:946-1036, `A._mergeStreamNext` scene.js:1037-1049
- Open path incl. IDB cache rekey/write scene.js:1050-1378
**Witness lock**: `witness_scene_merge_2026-07-30.js` (W-SCENE-MERGE) drives the REAL browser through Open A → Merge B → re-merge dedup → Esc/New, asserting from §-log + live object state (its header, lines 1-18) — a regression in export/merge/open behaviour after extraction fails it. The cache-DB half is additionally locked by `witness_db_cache_key.js` + `poc_db_cache_key_live.js` and `witness_db_404_oci_retry.js`.

### 2. navigate_find.js — extract the 5D cost + ERP push block (priority 2)
Move (all closure-scoped inside `init` at navigate_find.js:17, so each needs its deps passed explicitly — the real cost of this move):
- `_rates`/`_cur`/`_pack` navigate_find.js:1700-1702, `_selectionPriced` :1705, `_selectionCost` :1736, `_updateSelCost` :1743
- `_ensureErpDb` :1773, `_persistErpDb` :1781, `_money` :1798, `_foldClassTwin` :1800, `_surfaceExistingOrder` :1824, `_surfaceConstructionLink` :1858, `_showClassCost` :1878-1918, `_pushToErp` :1925-1994
**Witness lock**: `poc_find_erp_link_live.js` (does not name the file but exercises exactly this path end-to-end on the real viewer: no-selection reject leg with §PROJ_PUSH_AUDIO id=erp_reject, then select → push folds a Project Order → deep-link record id equals the created C_Project_ID, §PROJ_PUSH_LINK — its header, lines 1-11). `poc_construction_link_live.js` covers the construction-link surface. The pure-math half (`_selectionPriced`/`_selectionCost`) has NO direct witness — extract it into a require-able module and gate it the witness_db_cache_key way as part of the same task, or leave it inline.

Runner-up considered and rejected: streaming.js material factory (365-883) — only witness_class_outline_live/witness_hba_iot_lod_device_meshes graze material behaviour; coverage too thin to prove the move.

## Dangerously low witness coverage for size
Zero files in viewer/tests reference these (basename grep, 102 test files scanned):
- **effects.js — 8803 lines, 0 references.** The single largest file in the fleet is invisible to the test suite. Protected lane anyway, but even its pure DB helpers can't be moved provably today.
- **tour.js — 2281 lines, 0.** A full camera-motion engine (pacing, pose interpolation, A* routing) with nothing locking it.
- **doc_canvas.js — 2115 lines, 0.** (Header cites W-DOC-CANVAS, but nothing in viewer/tests carries it.)
- **dlod_nav.js — 2029 lines, 0.** Header cites 8+ W-DLOD/W-ROOM-OCCL/W-PVS witnesses (dlod_nav.js:6-17); none exist under viewer/tests by name.
- **grid_overlay.js — 1932 lines, 0.**
- **cinema_maxq.js — 1582 lines, 0 by basename** — partially mitigated: pure mappings are exported on APP explicitly "for the witness" (cinema_maxq.js:1560-1578), so coverage may exist under other names.
Near-zero for size: measure.js (1901) and tools.js (1693) each have only the incidental `poc_z_events.js`.

---

# S60 — ERP Cluster Refactor Survey Table

Scope: 5 files at bim-ootb `origin/main`. Extraction precedent: `viewer/gantt_model.js` (PR #1446).
Status: COMPLETE — all 5 rows analysed, top-2 candidates named, coverage flags written. 2026-08-21.

| File | Lines | What it actually does | Consumers | Test coverage | Priority (1-5) + reason |
|---|---|---|---|---|---|
| erp/ad_ui.js | 3361 (verified wc -l) | AD-driven ERP UI renderer (`window.ADUI`): one IIFE holding all screen state (`_db`, `_currentScreen`, `_currentRecords` etc., ad_ui.js:9-29) and rendering home menu/KPI cards (:37,:65), home graph+heatmap (:1434,:1603), window list+card screens (:1866,:1896,:2114), accordion record panel/table overlay with inline cell CRUD (:168,:351,:742-805), keyboard/swipe/client-switch nav (:2359-2506), help panel (:2803), share-URL context (:2657, §AD_UI buildShareUrl log :3326). Mostly DOM+state; pure-ish islands: `_caseGet` :1251, `_resolveDisplay` :1261, `_REF_TYPES`+`_getFieldsForTable` :1275-1348, `_escHtml` :1424 | 18 non-test consumers incl. erp/erp.html, erp/idempiere.html, erp/ad_graph←(ad_ui.js loads it), erp/accts_posted.js, erp/ninja_pill.js, erp/sw.js | erp/tests/: 5 (poc_accts_posted, poc_ad_displaylogic, poc_init_instant, poc_init_reburst, poc_single_burst). viewer/tests/: 0. Also 5 root tests/ (test_ad_ui, test_s259_accordion, test_s259_globe_ux, test_s259_table_overlay, test_s262_crud) — outside the erp/tests scope but real | **3** — biggest file and it exposes a deliberate `_test` whitebox seam (ad_ui.js:3350-3356) with 10 test files around it, but ~90% of the mass is DOM+state the gantt_model precedent says stays in the parent; the extractable pure islands (field-model helpers :1251-1363) are small, so payoff is modest, not urgent |
| erp/crud_overlay.js | 2849 (verified wc -l) | CRUD "ring of fire" overlay + a large PURE core: validation/AD-logic (`effectiveFlags` :80, `validate` :143), doc-action FSM (`legalDocActions` :184, `buildOp` :221, `buildDocActionGroup` :286), op-log tip reads (`tipDocs` :345, `listTip` :371, `readTip` :536, `tipValues` :571), Z-fold (`foldBackGroup` :497), AD-folded spec (`foldCrudSpec` :680), change trail (`changeLog` :2166, `recordInfo` :2213, `fieldLineage` :2253), draft buffer (`draftPut` etc. :2307-2370). Already dual-mode: `var CORE = {...}` :716, node gets `module.exports = CORE; return;` :733 — DOM overlay (ring/forms/inline edit, `global.__crud` :2826-2848) only mounts in browser | 17 non-test consumers incl. erp/idempiere.html, erp/glassbowl.html, erp/ad_process.js, erp/kernel_ops.js, erp/pos_core.js, erp/report_overlay.js, erp/idmp_history.js, erp/sw.js | erp/tests/: 7 (poc_a_grail, poc_ad_folded_crud, poc_critic_odoo_process_live, poc_critic_process_signed_live, poc_doc_dots, poc_draft_restore_live, witness_t7_incremental). viewer/tests/: 0. Two of them require the file directly as pure CORE (poc_ad_folded_crud.js:10 `const CORE = require('../crud_overlay.js')`, poc_doc_dots.js:21) | **5** — the strongest candidate: the pure/DOM seam ALREADY exists in-file (CORE :716 + node early-return :733, exactly the gantt_model contract) but ~210 pure lines (`changeLog`/`recordInfo`/`fieldLineage`/`draft*` :2166-2370) physically sit inside the DOM half, hoisted across the node `return` — an edit there can silently capture browser state. 7 erp/tests lock CORE behavior today, so a physical split is provably safe |
| erp/ad_graph.js | 1918 (verified wc -l) | "Data Globe" — Canvas-2D rotating sphere of DB records: sphere/perspective math (`_project` :143, `_orbitPosition` :472), node building from live tables (`_buildHomeNodes` :175, `_buildEntityNodes` :325), record classification (`_classifyRecord` :263, `_scanDates` :312), drill/expand FK traversal (`_expandTable` :504, gateways :581), drag/momentum/fly-to animation state (:27-55), `window.ADGraph` + dual-mode export :1914-1915 with `_debug` whitebox seam :1903-1911 | 8 non-test consumers incl. erp/erp.html, erp/ad_ui.js, erp/accts_posted.js, erp/sw.js, scripts/minify_pages.js | erp/tests/: 2 but both INCIDENTAL (poc_init_instant measures its script-load timing :56; poc_single_burst mentions its resize fix in a comment :6 — neither exercises globe logic). viewer/tests/: 0. Real coverage is root tests/: test_globe_search.js requires it headlessly and asserts discoverChildren/getBubbleWeight/focusNode/collapseAll (:79-237); test_s259_globe_ux.js drives showEntity + `_debug` seam (:73-339) | **2** — cohesive single-purpose visualization (size alone is not a reason); its model functions are ALREADY node-testable through the mock-canvas harness + `_debug` seam, so a physical extraction adds little; the strictly in-scope (erp/tests) coverage is incidental-only, which further argues against churning it |
| common/room_graph.js | 1865 (verified wc -l) | Room-to-room adjacency graph + occupant pathfinding, pure computation end to end: E1 door edges + E2 circ-space + E3 stair-flight bridging (`buildGraph` :226-950), connectivity analysis (`components` :963, `fullConnectivity` :991), Dijkstra + waypoint substitution (:1064-1158), walkable-raster path legalization + A* grid + LOS simplify (:1240-1622), `shortestPath` :1713, `escapeRoute` :1746, portal PVS `buildRoomPVS` :1806. No DOM, no module UI state; dual-mode export `ROOT.RoomGraph`/`module.exports` :1863-1864 incl. read-only witness helpers (`chordIllegalCount`, `astarHop` :1848-1859) | 15+ non-test consumers incl. viewer/scene.js, viewer/tour.js, viewer/dlod_nav.js, viewer/main.js, viewer/navigate_find.js, common/hallway_backbone.js, common/storey_raster.js, scripts/build_storey_walkable_raster.js | erp/tests/: 0. viewer/tests/: 0. BUT ~20 root-level witness_*.js exercise it (witness_room_graph_path, witness_room_graph_utility_penalty, witness_full_connectivity, witness_occupant_pathfinder, witness_*_walkable_raster x5, etc.) — the repo's witness convention, heavy real node-side coverage | **1** — it already IS the target state of the gantt_model precedent: a pure, dual-mode, witness-covered model library with deliberate read-only test seams; big but cohesive (size alone is not a reason). Its consumers are viewer/ files owned by others, so API churn here is doubly wrong. Leave it alone |
| erp/pos_lens.js | 1552 (verified wc -l) | POS Lens, a "dumb terminal by construction" (header :5-10): renders tiles/cart/scan/receipt/import overlays and sends signed op groups — all pricing/tax/inventory/doc logic lives in POSCore/ERPEngine/kernel_ops, not here. One giant DOM closure `open(cfg)` :406-1543 (~1140 lines: tile grid :489, pay panel :562, replenishment stage :746, QR scan :942, import/snap/scan :1038-1433, pay/deliver-later commits :1435-1531). Pure shard-aware folds `nextIds` :257 / `logMovements` :268 / `pendingInbound` :293 exported as node seam `_t7` :1548-1550; `suggestAll` :322 is pure too but NOT in the seam | 10 non-test consumers incl. erp/idempiere.html, erp/pos_core.js, erp/kitchen_core.js, erp/kitchen_lens.js, erp/crud_overlay.js, erp/erp_shard.js, viewer/wh_walk.js, erp/sw.js | erp/tests/: 4 + 1 fixture (poc_pos_close_leak, witness_cross_tab_persist, witness_pos_pillbar, witness_t7_incremental, fixtures/pos_host.html). viewer/tests/: 0 | **2** — the extraction already happened at the architecture level: logic was pushed into POSCore/kernel_ops and the pure shard folds already have their node seam (`_t7`) locked by witness_t7_incremental; what remains is the DOM closure the precedent says the parent keeps ("open() stays DOM-bound and untestable headless by design", :1546-1547). Only real nit: `suggestAll` :322 is pure but unexported/unlocked |

## Top 2 candidates

### 1. erp/crud_overlay.js — physically split the already-designed pure CORE out (priority 5)
The file already implements the gantt_model contract internally (`var CORE` :716-731; node early-return `module.exports = CORE; return;` :733) — the refactor is to make the physical layout match the logical seam, because ~210 pure lines are stranded inside the DOM half and only reach node via function hoisting across that `return`.

Functions to move (to e.g. `erp/crud_core.js`, dual-mode export; `crud_overlay.js` re-exports CORE so `require('../crud_overlay.js')` in tests keeps working unchanged):
- crud_overlay.js:14-731 — the contiguous pure head: `_getTableCols` :16, `_fmtKernelTs` :37, store helpers :50-72, AD-logic eval `effectiveFlags` :80 / `validateField` :104 / `validate` :143 / `coerce` :155 / `cleanVals` :165, doc-action FSM `legalDocActions` :184 / `docActionOutcome` :198 / `buildOp` :221 / `kernelParamsFor` :268 / `buildDocActionGroup` :286 / `docPolicyFor` :304, tip reads `tipDocs` :345 / `listTip` :371 / `gateOp` :460, Z-fold `foldBackGroup` :497 / `foldForwardGroup` :514, `readTip` :536 / `normDateValue` :559 / `tipValues` :571 / `listOptions` :595 / `splitStatusChange` :611 / `docLabel` :634, AD-folded spec `mapRefDisplayType` :657 / `mapRefType` :671 / `foldCrudSpec` :680, plus the CORE object :716-731.
- The stranded pure block: `changeLog` :2166-2212, `recordInfo` :2213-2252, `fieldLineage` :2253-2305, `draftChangedCols` :2307 / `draftDirty` :2317 / `draftPut` :2324 / `draftGet` :2336 / `draftClear` :2340 / `draftList` :2346 / `draftDrift` :2363.

Locks (existing, red-capable): erp/tests/poc_ad_folded_crud.js:10 (`const CORE = require('../crud_overlay.js')` — foldCrudSpec/validate), erp/tests/poc_doc_dots.js:21 (tip/doc-status via CORE), erp/tests/poc_draft_restore_live.js (draft* seam), erp/tests/witness_t7_incremental.js, erp/tests/poc_critic_odoo_process_live.js + poc_critic_process_signed_live.js (doc-action lane), erp/tests/poc_a_grail.js (fold-via-scrub). These call the exact functions being moved through the exact export being preserved — a behavior change goes red.

Caveat to verify at implementation time: `_getTableCols` reads `globalThis.__idmpDb` (typeof-guarded, :22-23) — fine in node, keep the guard verbatim; comments move with their functions per the precedent.

### 2. erp/ad_ui.js — extract the field-metadata/DisplayLogic model, narrowly scoped (priority 3)
Functions to move: `_REF_TYPES` + `_getFieldsForTable` ad_ui.js:1275-1347, `_fallbackFields` :1348-1363, and the DisplayLogic gating currently inlined in `_openAccordionPanel` (AdEvaluator resolution, :184-185 region). This is precisely the gantt_model motivation #2 situation: tests/test_s259_table_overlay.js:355-357 asserts by SOURCE-TEXT grep (`adUiSrc.indexOf('drillCallback:')`), and tests/test_s259_accordion.js:180-209 openly gave up ("Extract _openAccordionPanel function body — too fragile") — the extraction is the remedy that lets a witness call the rule instead of slicing it.

Locks: erp/tests/poc_ad_displaylogic.js (live playwright, drives `ADUI._test.openAccordion` :17-18 and asserts hidden/shown fields — catches a DisplayLogic-column or field-list regression); tests/test_ad_ui.js FIELD-1/FIELD-2 :234-237 lock ADParser's field typing, NOT these helpers.

Said plainly: `_caseGet` :1251-1258 and `_resolveDisplay` :1261-1270 have NO existing test that would go red on a regression (test_ad_ui's 158 checks assert navigation state, not display resolution) — that slice is disqualified from a first cut by the survey's own rule. Move only the `_getFieldsForTable`/DisplayLogic slice that poc_ad_displaylogic locks; add a witness for `_resolveDisplay` before ever moving it.

## Dangerously low test coverage (for size)
- **None of the five is truly untested**, but two regions qualify as dangerous:
- **erp/ad_ui.js accordion/table-overlay region (~:161-1198, ~1040 lines)** — locked only by one playwright poc (poc_ad_displaylogic) plus source-text greps (test_s259_table_overlay.js:355-357) and a test that explicitly gave up on reaching the internals (test_s259_accordion.js:209). Biggest file of the five, weakest per-line lock. This is the risk that caps its priority at 3 despite being the largest file.
- **erp/ad_graph.js under the strict scope definition** — its 2 erp/tests references are incidental (load-timing measurement, a comment); zero erp/tests or viewer/tests exercise globe logic. Mitigated in practice by root tests/test_globe_search.js (exact-value asserts on getBubbleWeight/discoverChildren :129-237) and tests/test_s259_globe_ux.js (`_debug` seam :73-339) — real coverage, wrong directory for the scope definition.
- common/room_graph.js has zero erp/tests|viewer/tests but ~20 root witness_*.js — not dangerous, it is the best-covered file of the five under the repo's witness convention.

## Method note
All five files copied verbatim from `origin/main` (git show) to scratch; line counts verified by `wc -l` and all match the tasked list. All cited `file:line` values were read directly from those copies. Consumers from `git grep -l "<basename>" origin/main` in /home/red1/bim-ootb (read-only).

---

# §S61 — the harness, audited (2026-08-21, watchdog session)

**Why this section is in this file:** §S59/§S60 disqualify candidate after candidate with the rule
*"low witness coverage RAISES risk"*. §S61 turns that rule on the harness itself. Two gaps were
claimed. **One was real and is fixed (§S61.2, shipped as bim-ootb PR #1452). The other was a
measurement error and is RETRACTED below (§S61.1) — the harness gates fine.**

⚠ **Neither is a precondition for `support_sweep.js`.** The original framing said they were; that
followed from the §S61.1 error and does not survive it. §S58 can be taken whenever dev reaches it.

## §S61.1 — RETRACTED: the "65 of 112" figure was a regex artifact. The real number is 3.

**This section originally claimed "65 of 112 probe/test files print `FAIL` and exit 0" and called
the suite ungated. That was WRONG and is corrected here.** Caught by the agent dispatched to execute
it, then re-measured independently before this rewrite — the execution pass is what disproved the
plan it was given, which is the correct outcome, not a failed task.

**The error:** the original count grepped for the literal strings `process.exit(1)` / `exitCode = 1`.
This codebase's convention is a COMPUTED exit — `process.exit(fail ? 1 : 0)`,
`process.exit(fail === 0 ? 0 : 1)`, `process.exit(passed === results.length ? 0 : 1)` — none of which
contain that literal. Every file using the house style was counted as ungated.

**Re-measured on `origin/main` @ `a98b62c`**, classifying each FAIL-printing file by whether it has
any `process.exit` and whether that exit is conditional:

| | n | |
|---|---|---|
| already gate (conditional exit or literal `exit(1)`) | **101** | the suite works as intended |
| no `process.exit` anywhere | **1** | `viewer/tests/redpill_gate.js` — **also a false positive**: it is a LIBRARY (`module.exports` of `identityGate`/`governedDeltaGate`/`digest`, `:151-157`), not a runnable test. Its "FAIL" strings are verdict NAMES in comments (`:115-116`). A module correctly has no exit. |
| ends in an unconditional `process.exit(0)` | **2** | `scripts/probe_gantt_drag_outliers.js`, `scripts/probe_gantt_stagger.js` — instruments by design; a header line saying so is the whole fix |

**Final count: ZERO test files fail to gate.** The two probes are instruments, and the third "hole"
was a library. The original figure was off by 65.

**What survives of the original plan:** the instrument-vs-gate DISTINCTION is still worth stating once
— a `probe_*` reporting by `§`-log is not a gate, and the two unconditional-exit probes should say so
in a header rather than look like broken witnesses. And `redpill_gate.js` is a real (single) hole.
**What does NOT survive:** any claim that the harness is broadly unable to fail, and any framing of
this as a precondition blocking `§S58`. It is not. `support_sweep.js` was never gated behind it.

**Standing lesson, worth more than the finding was:** a count derived from one grep pattern is a
hypothesis, not a measurement. The house idiom beat the pattern. Before a count drives a decision,
classify a handful of hits by hand and confirm the pattern matches the convention — the same
verify-before-load-bearing rule this project already applies to code claims.

## §S61.2 — both precache/script audits check only one direction

Read in full: `tests/audit_sw_precache.js` (41 lines) and `tests/audit_script_tags.js` (35 lines).
Note they live at **repo-root `tests/`**, not `viewer/tests/`.

| audit | asks | does NOT ask |
|---|---|---|
| `audit_sw_precache.js:23-37` | for each entry in `sw.js` `PRECACHE_ASSETS`, does the file exist on disk? | is every file the viewer actually loads listed in `PRECACHE_ASSETS`? |
| `audit_script_tags.js:16-31` | for each `<script src>` in `viewer.html`, does the file exist on disk? | is that script precached? |

Both answer "is this listed thing real?" Neither answers "is every needed thing listed." **A new
viewer file added to `viewer.html` but omitted from `PRECACHE_ASSETS` passes both audits, passes every
witness, and breaks only offline users** — silently, and only for people already holding an old cache.
This is the recurring `sw.js` cache-version bug class this project has been bitten by before; the
`CACHE_VERSION`-bump rule in CLAUDE.md is the discipline that exists because the check does not.

### SPEC — the missing invariant, one check, written before any code
**Claim (W-SW-UNLISTED):** every non-`lib/`, non-CDN `<script src="...">` in `viewer.html` appears in
`sw.js` `PRECACHE_ASSETS`.

- **Where:** extend `tests/audit_sw_precache.js` (it already parses `PRECACHE_ASSETS`; reuse the
  `viewer.html` script-tag regex from `audit_script_tags.js:13` verbatim rather than writing a second
  one — one parser, two callers).
- **Skips, matching the existing audits' own rules:** `http`/`//` prefixes (`audit_script_tags.js:21`)
  and `lib/` (`:23`, third-party/dynamically loaded).
- **Output:** one `§SW_AUDIT_UNLISTED <file>` line per miss, folded into `§SW_AUDIT_SUMMARY`; exit 1
  if any, same as the existing `fail > 0` gate at `:40`.
- **Pre-existing misses:** run it FIRST and read the log before wiring the exit. If the current tree
  is already clean, it ships green and protects every future file. If not, each miss goes in a
  `KNOWN_UNLISTED` list **with a stated reason** — the same shape as `KNOWN_MISSING` at `:20` — never
  a bare skip.
- **Proof it can go red (mandatory, per the project's own test rule):** delete one known-precached
  entry (e.g. `gantt_model.js`) from a scratch copy of `sw.js` and confirm the audit fails naming that
  file; restore. An audit that has never been seen red proves nothing.

**Cost:** small and self-contained. **Value:** it protects every file added from here on, including
every module the `support_sweep.js` / `scene.js` / `crud_overlay.js` extractions would create — each
of which adds exactly the kind of new file this check exists to catch.


---

# §S61.3 — RAN THE WHOLE WITNESS SUITE (2026-08-21) — 22 of 63 are not green

The §S61.1 retraction left one real question: if every witness gates correctly, **what happens when
you actually run them all?** Nobody does — each is run standalone by the session that touched it.
Run here against `origin/main` @ `a98b62c`, real fleet DBs, 150s timeout each.

| | n |
|---|---|
| green (exit 0) | **39** |
| nonzero exit | **22** |
| timeout at 150s | **2** (`witness_class_outline_live.js`, `witness_panel_abstraction.js`) |

**The gates all worked.** Not one of these is a gating failure — every red exited nonzero exactly as
it should. They are pre-existing failures that nothing surfaced because the suite is never run as a
suite. Three distinct causes, do NOT treat them as one queue:

## A. Stale-slice crashes — the §S53.5 bug class, still live, now named
```
ReferenceError: _zoneIndex is not defined   at evalmachine.<anonymous>:104
```
- `witness_big_element_support_coverage.js` — crashes AFTER its first PASS line (W-BIGSUP-0), so it
  looked alive
- `witness_tm_geo_order_cycles.js` — same, after printing `§TMREPRO_SLICE state=post-refactor`

Identical shape to `witness_zone_display_authoring.js`'s `_tukeyBound is not defined` (dead 4 days,
§S53.5): a source-text slice of `time_machine.js` calls a module-scope helper the sandbox was never
given. **This is the failure class `§S58`'s `support_sweep.js` extraction retires permanently** —
42 of 43 slices go away, and a `require()` cannot half-import a function. That raises §S58 from
housekeeping to a fix for a recurring, currently-live defect.

## B. Hardcoded absolute path
- `witness_zone_index.js` — `ENOENT: no such file or directory, open '/tmp/vw/time_machine.js'`.
  Same class as `38-offline-pwa.spec.js`'s hardcoded `VIEWER_URL` (CLAUDE.md, GH_DEPLOY_ISSUES Issue 4).
  One-line fix: resolve relative to `__dirname` like every other witness.

## C. Real reds in browser witnesses that DID run
`witness_isolate_zoom_2026-07-12.js` reached a live browser, loaded Duplex, and reported
`🔴 Parts axis reachable via toggle — axis=disc`. That is a genuine product red, not an environment
problem. Several others in the 22 print `🔴` rather than the string `FAIL`, which is why a
FAIL-grep classifier misses them — **the same lesson as §S61.1: one pattern is not a measurement.**
Some in this bucket genuinely need a server that was not up (`witness_shakeout_2026-07-06.js`:
`page.goto: Timeout 30000ms`). **Each of the remaining 22 needs individual triage** — this section
does not claim to have classified them all, and no red was fixed.

## The actual finding
Not "tests can't fail" (that was §S61.1, wrong). It is: **nothing runs the suite, so reds accumulate
silently and get discovered by accident** — §S53.5 found one by auditing consumers of a moved
function, and this run found more the same way. A suite runner that executes all witnesses and
prints one summary is worth more than any single extraction on this list. Logs for every run:
`scratchpad/s61run/*.log`.
---

# ▶ RESUME HERE (2026-08-22, watchdog session end)

**Everything below is MERGED and verified ancestor-of-main. Nothing is in flight, no unpushed work,
worktrees pruned.** `origin/main` carries it all; `CACHE_VERSION` **v1068**.

## What shipped this session
| PR | what | proof |
|---|---|---|
| #1452 / #1454 | `audit_sw_precache.js` gained the OTHER direction (needed-is-listed) + the 18 scripts it exposed are now precached | 141 found/0 missing · 114 precached/0 unlisted · proven red both ways |
| #1453 / #1454 | `tests/run_witness_suite.js` — one command runs every witness | headless 42 GATE, browser 21 report-only (three sweeps proved the browser set is not deterministic) |
| #1455 | **§S58** `viewer/support_sweep.js` — 6 functions, 604 lines out | `witness_midair_zero` 49/49 before AND after, normalized log diff EMPTY; 43 source-text slices → 0 |
| #1457 | code-side ⛔ pointers for the midair judge divergence | refs §S58.5 |
| #1458 | `tests/audit_section_refs.js` — every `prompts/*.md` pointer must resolve | 100 resolved · 25 archived · 13 known-gone · 0 dead |
| #1459 | **§S62** `viewer/zone_index.js` — 63 lines out, retired a dead witness | suite green=34→35, known_red 8→7 |
| #1460 | **§S56** `§TM_BAKE_LOCK` — the Gantt refuses edits while the film records | `witness_tm_bake_lock` 10/10, proven red |
| #1464 | **§S57** `witness_bake_plays_schedule` — the film plays the REAL schedule | 16/16 on Hospital+Clinic, headless and numeric, proven red |

`time_machine.js` **9,217 → 8,673**. Suite: **green=37 known_red=7 new_red=0**, 44 witnesses.
All three demo gates (§S55 identity, §S56 lock, §S57 bake) are CLOSED.

## Pick up here, in this order
1. ✅ **DONE (witness) 2026-08-22 — `witness_tm_geo_order_cycles`.** Bisected (18 runs), isolated to
   ONE file, cause measured, re-locked at 12 + composition. Full trail: §S63 below.
2. ✅ **DONE (witness) 2026-08-22 — `witness_zone_index.js`.** NOT the one-line fix predicted here:
   `OLD` is a prior REVISION, not a sibling file. See §S63.
3. ✅ **DONE (witness) 2026-08-22 — W-ZDA-4a per-building baseline lock.** Duplex 22→37, HHS 894→1839
   recorded in `viewer/tests/baselines/midair.json`. No threshold, exactly as specified. See §S63.

Then the next extraction, by the rule both of this session's extractions obeyed: **take a seam
because it retires a named failure, never because of a line count.** Honest candidates left in
`time_machine.js`: the 1,131-line `§GANTT_DRAG`/`§GANTT_RETIME` block, and ~870 lines of
sparks/smoke + day-night sky that are not 4D at all.

## Standing lessons this session paid for
- **One pattern is not a measurement.** "65 of 112 files never gate" was a regex artifact; the real
  number is 0 (see §S61.1, RETRACTED). Classify a handful of hits by hand before a count drives work.
- **One observation is not a verdict.** Three sweeps of the same commit gave three different browser
  red-sets; a red is only believed when it reproduces (`--retries`).
- **CI's lint gate sees what nothing local does.** `node --check`, four audits, four physics
  witnesses and the whole suite were green while 6 undefined `SupportSweep` references sat in
  `time_machine.js`. Cross-file identifier resolution has exactly one guard.
- **A sliced function cannot state its own dependencies.** That single fact caused the dead witness
  (§S53.5), both `_zoneIndex` crashes (§S62), and the two witnesses my own §S56 guard broke. It is
  the argument for every extraction on this list.

---

# §S63 — the three handover items, all closed (2026-08-22)

Measurement + witness work only. **Zero product-code lines changed** — no `viewer/*.js` outside
`tests/`, so no `sw.js` `CACHE_VERSION` bump applies (nothing a user's browser caches moved).

## 1. `witness_tm_geo_order_cycles` — 8 vs 12 (the only real number-vs-lock disagreement)
Reproduced first (floating=12, cycles=0, n=48428), then **bisected**: the witness held at HEAD, only
`viewer/{schedule_gate,time_machine}.js` + `rates/sequence_rules.json` swapped across every commit that
touched `schedule_gate.js` since the lock was set. 18 runs, ~3 s each. Constant **8** through
`2463ff1`, constant **12** from `a2c30ee` on — one step, no flapping. Then **isolated**: over the
`2463ff1` tree, `a2c30ee`'s `schedule_gate.js` ALONE gives 12; its `time_machine.js` alone gives 8.

`a2c30ee` = #1345 §STAIR_FLIGHT_GRID_VISIBILITY. The 4 added floaters are all `IfcStairFlight`, and the
mechanism is measured to the day: the flight is now a scheduling support for the landing members above
it (carrier start day 1.1083 == flight end day 1.1083; pre-#1345 those carriers finished day 0.25),
while `auditFloating` — deliberately unchanged by #1345 — still audits the flight as *hanging from*
those same members. Deficit **0.01 d (~14 min)**. Re-locked at 12 plus a new `W-TMREPRO-5b` on the class
composition, so a swap that keeps the count still reddens. Also measured: the mirror #1345 reverted
(flights into `auditFloating`'s `structGrid`) does **not** fix it — still 12, same composition.

Full trail, with the per-element numbers and the ⛔ open item it leaves behind:
`prompts/4D_SCHEDULE_PERFECTION.md` §S63.

## 2. `witness_zone_index.js` — the "one-line fix" was wrong about the bug
§S61.3 classified this as class B, "hardcodes `/tmp/vw/time_machine.js`, resolve from `__dirname` like
every sibling." **That fix would not have worked.** `OLD` is not a sibling FILE, it is a prior
**REVISION** — the last commit before §ZONE_INDEX (#1313) consolidated the two inline banding copies.
No such file exists anywhere in the tree to resolve to. The original run staged it by hand at
`/tmp/vw/`, which is exactly why it ENOENT-crashed for everyone after.

Fixed properly: derive the baseline from git at `475373b^`, cached under the OS temp dir, `OLD=` /
`OLD_REV=` still overriding, and **skip cleanly (exit 0, `§ZONE_INDEX_SKIP`) when the revision cannot be
produced** instead of crashing. Two undeclared slice dependencies §S62 had exposed were fixed in the
same pass (`ZoneIndex` module in the sandbox; `_classifyRule`/`_classifyNameOverride` in the slice set)
— the same "a sliced function cannot state its own dependencies" class, third instance.

Result: **5/5 gates, 7 buildings, 267,274 elements compared guid-by-guid, mismatch=0** — so the
§ZONE_INDEX consolidation is *still* provably a no-op today, which is a live gate again rather than a
dead file. `medianZ` ties: 0 everywhere except LTU_AHouse=2. Proven red by reversing the band sort in
`zone_index.js` (G-ZONE-BANDS FAIL); skip path proven by `OLD_REV=deadbeef1234` (exit 0, no crash).

**Lesson for the §S61.3 table: a red's one-line classification is a hypothesis, not a diagnosis.**
Two of that table's three named causes were right; this one named the symptom and guessed the fix.

## 3. W-ZDA-4a — an inequality that was permanently false
`assert(nextFloat <= baseFloat)` had shipped RED on every run since the display-authoring path landed,
because the display path IS worse: Duplex 22→37, HHS 894→1839. An assertion that is false on every run
gates nothing — it only teaches readers to skip the file. Replaced with an exact per-building lock in
`viewer/tests/baselines/midair.json` (new `zda_display_float` group, same file + same discipline as
`witness_midair_zero.js`'s baselines: data in the JSON, the WHY in the witness). No threshold — that
judge transfer was tried and retracted, as the handover said.

Proven red three ways: lock 36 vs measured 37 → FAIL naming both; building absent from the group →
FAIL telling you to record it; group absent → loud throw, never a silent pass. Green: 18/0.

**The trade itself is still open, not blessed by being locked** — locking makes a CHANGE visible, it
does not endorse 1839.

## Housekeeping done in the same PR
- `tests/run_witness_suite.js` KNOWN_RED drained of all three (the runner's own contract: drain in the
  PR that fixes it). Remaining known reds: `witness_gantt_lock_integrity` (self-declared),
  `witness_door_window_host_wall` (untriaged), `witness_tm_stream_index_defer` + `witness_xray_cache_memo`
  (reproducible, cause not established).
- `witness_big_element_support_coverage.js`'s floating commentary was stale (said Terminal=8, HHS=0).
  Re-measured from its own green run and dated: Terminal=12 Hospital=0 Duplex=0 HHS=9 Clinic=1
  LTU_AHouse=360 JKR=80. That witness asserts `unchecked`, never floating — the column is commentary,
  now honest commentary. Its Terminal=12 is an INDEPENDENT confirmation of §S63's number.

## §S63 CLOSE — shipped
**bim-ootb PR #1470**, auto-merge armed. Suite after: **green=40 new_red=0 known_red=4 flaky=0,
total=44** (was green=37 known_red=7). Remaining known reds, all pre-existing and untouched here:
`witness_gantt_lock_integrity` (self-declared bug in its own assert), `witness_door_window_host_wall`
(untriaged), `witness_tm_stream_index_defer` + `witness_xray_cache_memo` (reproducible, cause not
established). Logs: `scratchpad/geoc/*.log` (bisect_*, probe*, p1–p6, py1–py3, suite).

---

# ▶ RESUME HERE (2026-08-22, §S63 session end)

**Nothing in flight.** PR #1470 pushed with auto-merge armed; zero unpushed commits. The three items
the previous RESUME block handed over are all `✅ DONE (witness)`.

## Open, in the order I'd take them
1. **⛔ The scheduler/audit support asymmetry** (`4D_SCHEDULE_PERFECTION.md` §S63's open item). This is
   the only item on this list where a real physical question is unanswered, not just untidy:
   `geoGate`'s `below` has no top-proximity bound while `auditFloating`'s bearing test requires
   `S.top_z >= T.base_z - GAP`, so one element can be a *scheduling* support for something it is not an
   *audit* support for — and be audited as hanging from that same thing. Terminal's 4 stair flights are
   the measured instance (0.01 d deficit). Any fix must be measured on all 7 buildings and re-lock
   `W-TMREPRO-5`/`5b`. **Do not start by mirroring flights into `auditFloating` — that was measured this
   session and does nothing** (still 12, same composition).
2. **The 4 remaining KNOWN_RED**, cheapest first. Two have never been triaged at all
   (`witness_door_window_host_wall`, and `witness_tm_stream_index_defer`/`witness_xray_cache_memo`
   are labelled "cause NOT established" — that label is honest, not a diagnosis). §S63's own lesson
   applies: **a red's one-line classification is a hypothesis** — §S61.3 named `witness_zone_index`'s
   cause correctly and its FIX wrongly, and the fix that was written down would not have worked.
3. **The duplicate lower-half `contained` rule** — `geoGate`'s inline clause (`schedule_gate.js:540`)
   and `edgeContained` (`:793`) are the same rule written twice; only the second feeds the reported
   cycle count. They agree today, so this is not a defect, but it is the exact shape §S26.2 lifted
   `supportPool()` out of, and it is why perturbing one of them cannot redden `W-TMREPRO-4`.

Then the next extraction, by the rule every extraction on this list has obeyed: **take a seam because
it retires a named failure, never because of a line count.** Honest candidates left in
`time_machine.js` (now 8,673 lines): the 1,131-line `§GANTT_DRAG`/`§GANTT_RETIME` block, and ~870 lines
of sparks/smoke + day-night sky that are not 4D at all.

## Standing lessons this session paid for
- **Bisect before you judge.** "The lock is stale" and "something regressed" look identical from one
  run. 18 three-second runs turned a nine-day-old mystery into one commit, one file, one predicate.
- **A one-line classification of a red is a hypothesis, not a diagnosis** (see item 2 above).
- **An assertion that is false on every run gates nothing.** `W-ZDA-4a` shipped red for weeks and
  taught readers to skip the file. If a bar cannot currently be met, lock the measured number and name
  the trade — never leave an inequality that only ever prints FAIL.
- **Lock the composition, not just the count.** `floating=12` alone would let four stair flights be
  swapped for four columns silently; `W-TMREPRO-5b` is one line and closes that.
- **`git checkout HEAD -- <file>` destroys uncommitted work.** Used it to restore a perturbation and
  wiped the new `baselines/midair.json` group in the same stroke. Back up to the scratchpad, or commit
  first, before using checkout as an undo during perturbation testing.
