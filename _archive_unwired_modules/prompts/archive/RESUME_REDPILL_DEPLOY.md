# ⚠ DO NOT REMOVE — Session Resume Card (handoff to a fresh Opus session, 2026-06-13)

> ## ✅✅ LANE DRAINED 2026-06-13 — DO NOT RE-PURSUE. All 6 items closed.
> - **1. B2 G8-GOVERNANCE gate** ✅ — `viewer/tests/{redpill_gate,poc_redpill_rosetta}.js` (W-REDPILL-ROSETTA 6/6, gate proven able to FAIL). Run on Opus per user.
> - **2. Red Pill B1+B2 deploy** ✅ — bim-ootb **PR #294 merged**, viewer **sw v652**.
> - **3. Reporting engine + R-T2** ✅ — bim-ootb **PR #295 merged**, erp **sw v669**; **#253 superseded** (absorbed via canonical source-of-truth sync, left open + commented for fold-back).
> - **4. R-T3 idempiere reporting** ✅ — reachability witnessed (W-AD-PROC-LIVE); period-windowed fall-through is glassbowl-statement-specific (idempiere TB = all-periods, correct) → enhancement, not regression.
> - **5. Lane A (A1-A3)** ✅ — `docs/TestArchitecture.md §Truth Model/§CI`, `CLAUDE.md` fix, `scripts/system_is_real.sh`, `.github/workflows/ci.yml` (bim-compiler `de59bcb8`).
> - **6. Housekeeping** ✅ — wt-socrud removed · `glassbowl_data.db` patched + `extract_fact_acct.sh` fixed (`c8be94cc`) · MEMORY.md trimmed + [[project_redpill_rosetta]] pointer added.
> - **NON-BLOCKING follow-ups** (no ✅ depends on them): idempiere.html:1237 stale comment · idempiere period-scoped fall-through · A3 Red Pill CI step needs a PAT for cross-repo checkout · served bim-ootb `glassbowl_data.db` fidelity patch (deployed reporting degrades gracefully without it).
> - **The parent lane `prompts/TRUTH_MODEL_REDPILL_ROSETTA.md` is now also fully drained (A1-A3 + B0-B2 all ✅).**


**Scope:** Finish the Red Pill RosettaStone lane (B2 = the verification gate) and drain the deploy +
backlog the prior session left green-but-unshipped. **Work-to-zero**: take the priority item → do it
witness-first → mark ✅/⛔ → next. Stop only on user interrupt or a genuine EXTRACT-blocker.
**Read first:** `docs/RedPillRosetta.md` (the B0/B1 spec + §7b), `prompts/TRUTH_MODEL_REDPILL_ROSETTA.md`
(Lane A + Lane B), `prompts/REPORTING_UI_DEFAULT_FALLTHROUGH.md`, `prompts/SO_FULL_CRUD_GAP.md`.
**Protocol:** source-of-truth = `build/erp/` (ERP) / bim-ootb `viewer/` (viewer); witness-first;
READ the `.log` after any run (exit code ≠ evidence); NON-INVENT; deploy to bim-ootb = `/tmp/wt-*`
worktree + PR (the shared tree is hook-blocked; bg agents need the dir in `.claude/settings.local.json`
`permissions.additionalDirectories`). Branch in play: `feat/erp-substrate-phase012` (bim-compiler).

---

## STATE SNAPSHOT (what's done, where it lives)
- **SO full document CRUD — SHIPPED.** bim-ootb PR **#292 merged** (sw **v667**): Complete fans out
  ship+invoice+status as one sealed `commitGroup` w/ group Z-fold-back; CREATE/DELETE list visibility;
  owner-gate/CAS reject; mounted on BOTH glassbowl + idempiere. Source committed on
  `feat/erp-substrate-phase012` (`5da2b4e4`/`f70b7403`). Witnesses W-SO-COMPLETE-UI / W-CRUD-LIST /
  W-CRUD-GATE green. **Live-visual eyeball still owed** (headless proved values; live DOM probe NOT faked).
- **Reporting default fall-through — GREEN IN SOURCE, NOT DEPLOYED.** Commit `f536c305`. Unconfigured
  report now lands on populated Fact_Acct history (period 160 / Jan-03, 2002-03 band) instead of a
  vacuous matrix; `coverage:default→history` + banner; folds byte-identical. Witnesses
  `scripts/poc_report_{open,default_fallthrough}.js` PASS. R-T1 was already-wired (▤ ring fab, verified).
- **Red Pill B0 (spec) — DONE.** `docs/RedPillRosetta.md` (+ §7b B1 spec). 7 design Qs user-APPROVED:
  G8-GOVERNANCE · 1.0mm tol · event-log = the History-timeline op-log · **ungoverned=0 for anything
  MOVED** (non-BOM elems may exist but must be STATIC) · persist-first · delta-vol predicted-vs-actual
  ±0.1% · ALL classes w/ named exclusions. PLAYBACK unified: one op-log/timeline, no double.
- **Red Pill B1 — DONE & WITNESSED, NOT DEPLOYED.** Worktree `/tmp/wt-redpill-b1`, branch
  `feat/redpill-b1-bom-attach`, commit **`8c337fb`** (based off bim-ootb #291+#292). Replaced the
  proximity heuristic with BOM-governed attach; governed moves persist as the existing **`GRID_MOVE`**
  op (rides the #291 HB session tree, folds back via `KernelOps.undoOp`); grid Ctrl+Z/Y routed through
  `UniversalHistory` (double killed). Witness `viewer/tests/poc_redpill_governed_drag.js`:
  `§BOM_RECOMPOSE … governed=882/882 ungoverned=0 … foldedBack reverted=882/882`, exit 0;
  `poc_session_recall.js` still green. Real SampleCastle W022 DB staged at
  `/tmp/wt-redpill-b1/viewer/tests/fixtures/SampleCastle_extracted.db`.
  **Data nuance:** in this export `element_ref` holds element-NAME patterns (joined name+storey), NOT
  guids — B2 must expect name-keyed binding here.

---

## OUTSTANDING — work top-to-bottom

### 1. ⏭ PRIORITY — **B2: the G8-GOVERNANCE verification gate** (Lane B, `TRUTH_MODEL_REDPILL_ROSETTA.md`)
The RosettaStone comparison that turns B1's governed moves into a *gate*. Per `docs/RedPillRosetta.md`:
- **Identity round-trip:** grammar→grid→materialized `NewIFC.db` == reference, reusing the Java gate
  maths (G1 exact COUNT w/ named exclusions, G2 volume ±0.1%, G3 SpatialDigest, centroid ≤1.0mm —
  recover from `RosettaStoneGateTest.java` / `SpatialDigest.java`, NON-INVENT no new numbers).
- **Governed-delta:** pure predictor `P(BOM, grid′)` vs persisted actual; classify GOVERNED/STATIC/
  UNGOVERNED; **ungoverned=0 for anything moved** = PASS. `G8-GOVERNANCE` = "every delta is
  BOM-explained" (the dynamic peer of G5-PROVENANCE).
- Build on B1's worktree (`/tmp/wt-redpill-b1`); the SampleCastle DB is staged there; expect name-keyed
  `element_ref`. Witness emits `§REDPILL-RS mode=identity … verdict=PASS` and `mode=delta governed=N/N
  ungoverned=0 verdict=PASS` + per-offender `§REDPILL-RS-FAIL`. The gate MUST be able to FAIL (prove it).
- **Model note:** prior session reserved B2 for **Fable** (equivalence rigor; quota reset ~10:30am KL).
  Running on Opus is acceptable — it's witness-gated. **Ask the user which** before launching if unsure.

### 2. Deploy Red Pill (B1, then B2) to bim-ootb — **as a verified unit** (don't ship B1 alone)
Once B2 is green: sync `feat/redpill-b1-bom-attach` viewer hunks → bim-ootb `viewer/` (line-level,
worktree), bump viewer `sw.js` CACHE_VERSION (KEEP-BOTH precache / take-higher on conflict), PR, verify
auto-merge landed (squash + late push can orphan — re-check). Coordinate with in-flight viewer PR #291.

### 3. Deploy reporting fall-through to bim-ootb — **coordinate with open PR #253**
`#253 report/print-confirm` already syncs `report_overlay.js` (256→908 lines). My R-T2 is an additive
`resolveScope`/`periodsOf`/`statementInputs`/`renderStatement`/CSS hunk ON TOP of the 908-line engine.
Cleanest: let #253 land, then add my hunk; OR supersede #253 (ask user). Don't blind-conflict.

### 4. R-T3 — reporting reachability + default fall-through on the **iDempiere** surface
The reporting work was Glassbowl-only. Verify ▤ report + `default→history` fall-through work on
`idempiere.html` (crud_overlay + report_overlay are already mounted there post-#292). Witness it.

### 5. Lane A — TestArchitecture truth-model + CI smoke gate (`TRUTH_MODEL_REDPILL_ROSETTA.md` Lane A)
A1 re-anchor `TestArchitecture.md` for browser-first reality + fix the FALSE "RosettaStoneGateTest
changes break CI" claim (no CI runs it) — **propose the doc outline first** ([[feedback_propose_before_editing_docs]]).
A2 `scripts/system_is_real.sh` single runner. A3 minimal `.github/workflows/ci.yml` smoke gate. Standard coder.

### 6. Housekeeping
- **Bundle data-drift:** served `build/erp/glassbowl_data.db` lost `c_elementvalue.accountsign` +
  `c_bpartner.name` → 3 pre-existing reporting witnesses (`poc_pa_report`, `test_report_overlay`,
  `poc_statement_browser`) fail at HEAD (NOT from our work). Regen the bundle so the full statement set lights up.
- `git worktree remove /tmp/wt-socrud` (PR #292 merged). Keep `/tmp/wt-redpill-b1` until B1/B2 deploy.
- MEMORY.md is over its size limit — trim + add a Red Pill RosettaStone pointer at session close.

---

## DO-NOT
- Don't ship B1 without B2 (Red Pill ships verified-as-a-unit).
- Don't edit `migrate_status_panel.html` counts — this session's work was delivery/UX + a separate
  geometry axis; no cent-fold count changed (it's an ERP-migration-scoped honesty panel).
- Don't touch the Java RosettaStone gates — B2 EXTENDS the model to the dynamic editor, doesn't replace it.
