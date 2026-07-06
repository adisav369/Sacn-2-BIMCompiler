# IMPORTED PLAN — compose-checks vs WBS-deepen (#518) + What-if (#516)

# ⚠ DO NOT REMOVE
**Scope:** prove that an **adopted P6/MS Project schedule behaves like any authored one** through the
two capabilities that landed after the §SE arc — **#518 WBS-deepen** (`addTask`/`breakdownByAttribute`
+ `schedule_sync.applyOp`) and **#516 What-if** (authored→what-if, no Generate re-prompt). The engine
already supports both; this lane WITNESSES them *through the import path* so we can claim it, not assume
it. **Read the §-log after every run.** Spec-first; every slice names its witness.

## §WHY
`prompts/XER_IMPORT_P6_ADOPT_LANE.md §SYNC` requires the imported plan to compose with #518/#516, but
`W-FGN` only proved adopt→CPM→bind→5D. An imported schedule is a *captured* schedule (`activeSchedule`
`captured=true`) — the same class #502/#516 handle — so it SHOULD just work; unwitnessed, it's a guess.
The risk is real: imported tasks carry P6 ids (`A:A1010`) and a P6 WBS depth, not our `materializeDefault`
shape — a deepen/what-if path that assumed the authored shape could break on them.

## §SLICES (spec-first; each names its witness)

### §C1 — deepen an imported WBS (#518)
`erp/tests/foreign_compose_witness.js`, node, real Hospital DB + the demo XER (later: the real fixture).
Adopt → then operate the shipped #518 verbs on the imported tasks:
- **W-IMPORT-DEEPEN**:
  - `breakdownByAttribute(db, schedId, '<imported phase>', 'storey')` on a BOUND imported phase →
    children created, parent→summary, **zero `_cap` coverage loss** (every element still on a
    non-summary leaf — count before == count after across leaves), children inherit the phase window.
  - `addTask(db, schedId, {name, wbsParent:'<imported WBS node>'})` → new leaf under the imported WBS,
    inherits parent window, parent NOT forced summary, deterministic id.
  - both ops **replay via `schedule_sync.applyOp` on a peer db → byte-identical convergence** (the
    cross-tab guarantee holds for imported ids too).
  - **5D re-folds** after breakdown: the parent's cost redistributes onto the new children, project
    total invariant (breakdown moves cost, doesn't mint it).

### §C2 — what-if on an imported plan (#516)
- **W-IMPORT-WHATIF**:
  - an adopted (captured) imported schedule reaches What-if as "authored-from-import" and **does NOT
    re-prompt Generate** (the #516 captured-aware guard fires for import too).
  - a what-if **slip of an imported phase ripples through the imported FS/SS/FF/SF logic** (the blue
    branch recomputes off `task_sequences` we wrote from the file) — assert a downstream phase's date
    moves by the slipped amount honouring the imported lag; **accept** persists, **discard** reverts.

### §C3 — headless compose smoke (wiring, secondary)
- **§COMPOSE-SMOKE**: in the live editor, import the demo file → break a phase down by storey (the #518
  `break by…` select) → bars re-render as sub-tasks → Compute CPM still lights a path; zero page errors.

### §C4 — the auto-bind tie-in (forward link)
If `prompts/AUTOBIND_BY_CONVENTION.md` ships, add: import with selector tokens auto-binds coarse (all
of a class) → `breakdownByAttribute` by level splits the bound phase into per-level sub-tasks **each
keeping its element slice** → granular 4D with no manual picking. **W-AUTOBIND-DEEPEN** (lives in that
spec; cross-checked here). This is the payoff composition — see [[project_foreign_schedule_import]].

## §LOG
- 2026-06-25 — Spec opened from XER_IMPORT_P6_ADOPT_LANE §SYNC. Engine verbs (#518 `addTask`/
  `breakdownByAttribute`/`applyOp`, #516 captured-aware what-if) are LIVE on main; this lane only adds
  witnesses through the import path. No new engine code expected unless a deepen/what-if path trips on
  imported ids — if so, fix at the seam, never special-case import. Cross-ref [[feedback_whitebox_deduce_not_browser]].
- 2026-06-25 — **§C1·§C3·§C4 ✅ DONE; §C2 ✅ DONE (editor/CPM layer) + ⛔ partial (ERP blue-branch gated
  on parked fold)** (bim-ootb PR #523 off fresh `origin/main`, auto-merge SQUASH armed; tests-only, NO
  engine change — the shipped verbs handle imported `A:Axxxx` ids + P6 WBS depth as-is, confirming the
  spec's prediction). `erp/tests/foreign_compose_witness.js` **W-COMPOSE 16/16** on real `Hospital_meta.db`:
  - §C1 **W-IMPORT-DEEPEN** — `breakdownByAttribute`/`addTask` on imported ids = ZERO `_cap` loss (17,987
    leaf elements before==after); both replay via `schedule_sync.applyOp` → byte-identical peer
    convergence (23,986,176 bytes); 5D re-folds total-invariant ($30,654,056). (addTask deterministic id
    keeps CASE → `TASK_Precast_columns`; WBS-summary parents carry no window so window-inherit only from a
    DATED leaf — both pinned.)
  - §C4 **W-AUTOBIND-DEEPEN** (the payoff) — autoBind coarse-binds all 255 columns to one task → break by
    storey → 4 children, each a PURE single-storey slice (COUNT(DISTINCT storey)=1), zero loss.
  - §C2 **W-IMPORT-WHATIF** — imported schedule is `captured=true` (Generate guard fires, no re-prompt);
    slip = lag edit on the imported FS link `A2010→A2020` → `computeCpm` ripples (A2020 ES 55→65, project
    300→310d, honours FS+lag); discard restores 300d. **HONEST BOUNDARY (don't invent engine):** `computeCpm`
    derives ES/EF from the dependency graph, so the "slip" is a lag/duration edit, not a `moveTask` (which
    only re-anchors a single task). The ERP `C_ProjectPhase` blue-branch what-if (`whatif.js`, #516) acts on
    a DIFFERENT table; a freshly-imported tasks-table plan reaches it only after the PARKED "fold authored
    sched→ERP C_Project" step → that half stays ⛔ until the fold ships. Proven at the schedule-editor/CPM
    layer where the imported plan actually lives.
  - §C3 **§COMPOSE-SMOKE 7/7** (`foreign_compose_smoke.js`, headless Chromium, real Hospital): import
    tokened demo + auto-bind → break Columns by storey via the #518 select → per-storey sub-tasks render →
    Compute CPM reruns; zero page errors. Cross-ref [[feedback_whitebox_deduce_not_browser]],
    AUTOBIND_BY_CONVENTION §C4.
