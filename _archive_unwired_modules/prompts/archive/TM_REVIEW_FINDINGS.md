# Time Machine — Review Findings (2026-06-24)

# ⚠ DO NOT REMOVE
**Scope:** review of `viewer/time_machine.js` (~3.9k lines) + adjacent schedule modules, per
`prompts/RESUME_TM_REVIEW.md`. This is a MAP + findings doc — **no behaviour was changed**. Code read from a
worktree off fresh `origin/main` (d8cd81b). Whitebox/static; a live headless boot is the one remaining step
(offered, not yet run). Honour standing rules; Spec-First for any fix that follows.

## 1. What each TM control does (panel build ~time_machine.js:1824–2207)
| Button (id) | Handler | Does |
|---|---|---|
| ◄◄/◄/■/▶/►► transport | 2335 `startPlayback(dir)` / 2381 `stopPlayback` | jump start/end, play fwd/rev, stop |
| DAY/HR/MIN | 2241 `switchMode` | tick granularity; slider remaps in `configSlider` (2250) / `onSlide` (2272) |
| tm-share | 1985 | `?tm=play` link to clipboard |
| tm-sun | 1997 | day/night sun cycle over the cursor |
| tm-eye (drone) | 2025 | cinematic storyboard camera (`computeStoryboard`) |
| tm-gantt 📊 | 2105 → `drawGanttMini` (3006) | inline READ-ONLY playback gantt drawer |
| tm-author ✎ | 1934 → `ScheduleAuthorUI.toggle()` | authoring wizard (schedule_author_ui.js) |
| tm-whatif ⑂ | 1941 → `WhatIfPanel.open()` | ERP variance what-if (whatif.js, C_ProjectPhase — NOT 4D tables) |
| tm-editor ↗ | 1950 | opens `schedule_editor.html?db=…` in a new tab |
| tm-dash | 2119 → `drawDashboard` (3227) | time/cost donuts, phase bars, crews, S-curve |
| tm-var | 2133 → `drawVariance` (2898) | budget-vs-actual + EVM (CPI/EAC) at cursor |
| tm-close | 2204 → `deactivate` (3687) | restore scene |

## 2. Where the schedule data comes from (4 provenance paths)
`_cap` IIFE (time_machine.js:2437–2465) reads dated leaves:
`SELECT task_id,name,schedule_start,schedule_finish FROM tasks WHERE schedule_start/finish NOT NULL AND (is_summary IS NULL OR 0)`,
builds `{win:{task_id→{s,e,name}}, guidTask:{guid→earliest task_id}}`. An element whose GUID is in `guidTask`
is **captured** (yellow frame, real dates); otherwise **generative** (grey, `ScheduleGate.computeSchedule`
from Z/storey/IFC-class → `SEQUENCE_RULES`). The four sources the TM may meet:
1. **Rule-generated** — no dated `tasks`; in-memory timing only.
2. **Authored** — `schedule_id='SCH_AUTHORED'`, built by wizard (`materializeDefault`→`scheduleContiguous`/`applyDates`).
3. **Captured/imported** — Bonsai/Revit `IfcWorkSchedule` rows (import_db_builder.js DDL; import_worker fills `task_elements`).
4. **Blank/undated** — `materializeDefault(blank=true)`; phases exist, dates NULL until user originates them.

4D schema (import_db_builder.js:80–139): `schedules · tasks(wbs_parent,is_summary,schedule_*,early/late_*,float,is_critical) · task_elements(task_id,guid) · task_sequences(pred,succ,type,lag) · calendars`. Engine verbs all in schedule_author.js: `materializeDefault·assignElement·scheduleContiguous·foldCost·wbsTree·listDependencies·add/remove/updateDependency·wouldCycle·moveTask·computeCpm·activeSchedule`.

## 3. Playback math
`renderAtTime(cursor)` (577–1200): per op classify `placed`(end≤cursor) / `frontier`(installing, `progress=(cursor−start)/(end−start)`) / `recent`(fade-out) / `arrival`(first 15%, cyan); one unified traverse over single/Batched/Instanced meshes + shadow-promotion + drone follow. `playTick` (2392) advances `_cursor += dir*tickMs()` (DAY 3.2e6 / HR 5.2e4 / MIN 9e3 ms, twilight-slowed) and reschedules at adaptive `TICK_MS()` (140–220ms by element count). Bounds: `_projectStart` = 1ms before first op; `_projectEnd` = max end_ts.

## 4. Cross-surface (`bim_4d` BroadcastChannel)
Listener in **main.js:276**. Verified handlers: `4D_PING→PONG`, **`4D_SCHED_EDIT`** (replay via `ScheduleSync.applyOp` then TM re-fold), `4D_RESET/PLAY/PAUSE/RESUME/SEEK` (ghostglass), `4D_HIGHLIGHT(_ALL)`, `4D_RESOURCES(_HIDE)`, `4D_QTO_REQUEST→RESPONSE`, `4D_SCHEDULE_REQUEST→RESPONSE`. **Senders:** `schedule_editor_ui.js` (move/add/remove/retype/lag/cpm via `ScheduleSync` emit, echo-guarded by `tabId` at schedule_sync.js:53) and `boq_charts.html` (PING/RESET/HIGHLIGHT/QTO/SCHEDULE req). `applyOp` (schedule_sync.js:18–29) is a pure verb dispatch.

## 5. FINDINGS (grounded, ranked) — nothing changed yet

**F1 · `4D_SCHED_EDIT` re-fold is a fragile fixed 60ms toggle (main.js:298–300).** On an applied edit it does
`toggleTimeMachine()` (off) then `setTimeout(toggleTimeMachine, 60)` (on). But activate is **async**
(`_activateAsync`, time_machine.js:3577) — a hard 60ms can race the teardown/reload on big buildings (TM may
not re-enable, or re-enter mid-load). *Proposed:* re-fold via the activate completion callback, not a fixed
timer. **Needs a witness before any change.**

**F2 · Dead ghost-glass handlers — `4D_PAUSE / 4D_RESUME / 4D_SEEK` have NO in-repo sender** (grep: refs only
in ghostglass.js consumer + main.js listener). `4D_PLAY` likewise has no in-repo sender — comment claims it's
"injected externally," but nothing in this tree emits it (S254 stripped TM's senders). *Proposed:* either wire
the surface that should drive them or delete the handlers; right now `tm-eye`/ghost-glass play is reachable
only by an external/test poke. Confirm intent.

**F3 · `_cap` staleness — no live invalidation.** `_cap.guidTask` is built once at `injectGantt`. Editing
`task_elements` (assignElement) or dragging in the editor while the TM is OPEN does not refresh it; only a
manual off→on toggle rebuilds. The wizard works around this by toggling (schedule_author_ui.js ~180); an
external edit won't. *Proposed:* on `4D_SCHED_EDIT`/assign, invalidate `_cap` (ties into F1's re-fold).

**F4 · Minor / cosmetic:** (a) `tasks.predefined_type` ('CONSTRUCTION') and `tasks.resource` are written but
never read by `_cap`/`foldCost` (cost uses RATES×ifc_class). (b) `calendars` table created, never populated.
(c) `computeCpm` returns camelCase `freeFloat` but persists snake_case `free_float` — internally consistent,
style-only. (d) `time_machine.js:1791` `_savedInstanceMatrices` cleared but restore uses `_savedInstanceState`
— dead var, low risk. (e) BUG6 comment (line ~853) looks already-resolved; comment is stale.

## 6. Outcome — fixes shipped 2026-06-24 (all witnessed, whitebox §-log)
- **F1 + F3 FIXED** → PR #515 (W-TM-REFOLD 7/7). New `window.tmRefoldSchedule()` invalidates the stale gantt
  cache + kernel_ops places, then re-activates off the synchronous deactivate (no 60ms timer). main.js's
  4D_SCHED_EDIT consumer + the Author wizard's Apply now use it.
- **What-if ↔ authored schedule** (user, SampleCastle: ⑂ showed 1 phase vs 4) → PR #516 (W-WHATIF-AUTHORED-SYNC
  9/9). New `ProjFold.foldAuthoredPhases` mirrors authored phases into C_ProjectPhase (the OPFS store ⑂ reads);
  wizard Apply folds+persists+`WhatIfPanel.refresh()`. Also: "Generate" demotes to "Regenerate" once a schedule
  exists (no longer re-prompts after Apply).
- **Editor re-downloaded the whole DB** (user) → PR #517. Reuses the viewer's IndexedDB cache (bim_ootb_cache/dbs).
- **WBS deepen — add sub-task + break down by storey/type/discipline** (user) → PR #518 (W-SE-WBS 15/15).
  New `addTask` + `breakdownByAttribute` verbs + Editor UI + cross-tab replay; no `_cap` coverage lost.
- Plus the zoom-across 404 self-heal → PR #514 (W-DB-404-OCI-RETRY 12/12).

### Still open (lower-pri, not started)
- **F2** — `4D_PLAY/PAUSE/RESUME/SEEK` ghost-glass handlers have no in-repo sender: wire the driving surface or delete.
- **Live headless exercise** of the TM transport + ✎/⑂/↗ end-to-end (the engine halves are node-witnessed; a
  visual boot would be belt-and-suspenders).
- **F4** cosmetics (unused `predefined_type`/`resource`/`calendars`, stale BUG6 comment, dead `_savedInstanceMatrices`).
