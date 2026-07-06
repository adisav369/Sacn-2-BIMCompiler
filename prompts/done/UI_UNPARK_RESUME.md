# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: UI BRIDGE (the live-UI ✅ axis) — REFRESHED 2026-06-11
# Lane: wire the PROVEN headless AD engines into the LIVE ERP screens so matrix surfaces flip 🟡→✅ on the
#       third (live-UI) axis. The backend evidence has OUTRUN what anyone can see — this lane closes that gap.
# READ THE LOG after every run; §-tagged console.log = the value proof, Playwright = wiring only
#       (CLAUDE.md §Browser testing). Honour this preamble until the card is DONE.
# HOUSE RULES: bim-ootb edits ONLY via a /tmp/wt-* worktree (PreToolUse hook blocks ~/bim-ootb/). Engines'
#       source-of-truth = bim-compiler `build/erp/` — SYNC (diff first), never blind-copy either direction.
#       Every deploy bumps sw.js (conflict magnet: keep BOTH precache hunks, take the HIGHER version).
#       Test ON-SCREEN before claiming done — a wired-but-inert deploy is the failure mode that bit §1-v613
#       (engine loads ≠ surface reacts). Spec refs: prompts/AD_BEHAVIOR_HANDOFF.md + AD_RENDER_HANDOFF.md.

---

# RESUME — START HERE (2026-06-11)

## WHAT CHANGED SINCE THE LAST UI SESSION (2026-06-10) — the ground is much firmer
- **The FOLD/matrix lane is DRAINED and COMMITTED** (c204fc88 + 45db28ee on `feat/erp-substrate-phase012`,
  pushed; paper + matrix published). `docs/ERP_COVERAGE_MATRIX.md` is NO LONGER CONTENDED — this lane may
  bank its own 🟡→✅ re-verdicts directly (the old "HOLD for FOLD" rule is DEAD).
- **The equivalence ledger is 41 oracle-equivalent**, and the NEW assets matter here: `ad_docfsm.js` now
  carries `legalActionsFor/transitionFor/dispatchFor` for **EVERY DocAction table** (22 tables: Order,
  InOut, Invoice, Payment, Movement, Inventory, Production, GL_Journal+Batch, Allocation, Cash,
  BankStatement, RMA, Requisition, TimeExpense + 0-seed/FA) and `ad_modelval.js` carries **12 install*SaveHooks
  installers** (MOrder…MRequisition — every beforeSave port, hooks cite their M*.java lines). Both proven
  against the runtime-parsed iDempiere source (W-*-FSM/W-*-SAVE witnesses, all green).
- These two engines are NOT yet in bim-ootb/erp/ (only ad_evaluator/ad_ui/idmp_session/crud_overlay are) —
  syncing them in IS part of this lane.

## SURFACES (work top-to-bottom; each = wire → §-log witness → on-screen verify → deploy → matrix 🟡→✅)

### B-1 Bank what already works (cheap, do first)
- §2 role-gates-menu is LIVE+WITNESSED: `build/erp/poc_ad_access_live.js` (W-AD-ACCESS-LIVE — Admin 294/332 ·
  User 163/332 · WebSvc 0/332 by REAL grants) — file exists, UNTRACKED. Commit it; re-verdict the access rows
  (Window_Access et al) 🟡→✅ in the matrix WITH the witness § lines.
- §1 DisplayLogic: live behavior works via the legacy `ADParser.evaluateDisplayLogic` (ad_ui.js:2207), but the
  PROVEN engine `ad_evaluator.js` (W-LOGIC-EVAL, 3044/3044) is still UNUSED on-screen. Either swap the call
  site to `window.AdEvaluator` (one seam) and re-verdict honestly, or document the legacy path as the live
  implementation and re-verdict on BEHAVIOR with the witness — decide by reading both parsers' coverage, do
  not double-implement.

### B-2 Doc-action buttons ← ad_docfsm (the NEW bridge, highest visible win)
- Today the renderer's doc-action affordances are not gated by the walked FSM. Wire: record drawer/status
  pill on `idempiere.html` consults `AdDocFsm.legalActionsFor(db, ad_table_id, {docStatus, doctypeId,
  processing, periodOpen, isBackDateTrxAllowed})` → render ONLY the legal actions for the open document;
  dispatch via `dispatchFor` → status chip updates per `transitionFor`.
- periodOpen probe: derive from `c_period` on dateacct (the W-MJOURNAL-SAVE periodOf pattern); backDate: start
  with `true` + name it.
- Witness `§AD-DOCFSM-LIVE table=<id> record=<id> status=CO legal=[CL,RC,RA] clicked=RC to=RE` on at least:
  a completed C_Order (CL/VO/RE only), a completed M_InOut (no VO!), a completed GL_Journal (RC/RE period-
  gated), a C_Cash (VO→RE even from DR). Falsifier: a button for an illegal action must NOT render.
- Matrix row: "C_DocType FSM" + the per-class FSM rows gain live-UI ✅.

### B-3 Save-path validation ← ad_modelval (ties into CRUD_EDIT_PERSIST)
- `prompts/CRUD_EDIT_PERSIST.md` is OPEN: CRUD_UPDATE is still dry-run + the date widget rejects `00:00:00`.
  When closing it, route the save through `AdModelVal.fireHooks('BEFORE_SAVE', …)` with the table's installer
  loaded: derived values fill the form (the `info.derived` seam), a reject blocks the save with the hook's
  error string. Start with C_Order (11 hooks) — the deepest proven set.
- Witness `§AD-MODELVAL-LIVE table=C_Order hook=MOrder.prepayNoCash verdict=REJECT` + a derive case
  (`m_pricelist_id` filled from default). On-screen: the rejected save shows the error, the derive appears.
- Matrix rows: beforeSave/afterSave + AD_ModelValidator gain live-UI ✅.

### B-4 §2 residual — P/R/F menu leaves (named in idmp_session.js §3b.1)
- Add `accessibleProcesses` (ad_process_access) / `accessibleForms` (ad_form_access) to `buildContext` →
  extend `scopeMenu` to prune P/R leaves ∉ procSet, F/X ∉ formSet. FIRST verify `ADParser.getMenuTree`
  carries `ad_process_id`/`ad_form_id` on non-W nodes (it only used `windowId` for W).

### B-5 §3 process-dispatch — ⛔ SEED-GATED (unchanged)
- Needs `ad_process`/`ad_process_para` in bim-ootb `ad_seed.db` (ABSENT — a seed-regen/gen_ad job, not ours).
  When present: load `ad_process.js`, route Process leaves → `AdProcess.dispatch`, unregistered classname →
  honest "not available". `§AD-PROC-LIVE` witness. Do NOT block on this — mark ⛔ and move on.

## KEY FACTS (don't relearn)
- erp.html = lean globe (ad_ui.js, NO login/role). idempiere.html = full renderer (login, `_session.role`,
  menu, kanban). Posting-Preview pill is ALREADY live there (PR #232, data-gated on `?db=preview_demo.db`).
- Engines consumed FROM `build/erp/`: ad_evaluator (synced) · ad_docfsm/ad_modelval (NOT synced yet) ·
  ad_access/ad_valrule/ad_callout/ad_process (check before assuming).
- The browser AD db is `ad_seed.db` (`?db=ad_seed.db`); it is NOT glassbowl_data.db — doc tables/doctypes
  exist but VERIFY a column exists before wiring (the §3 lesson). `c_period` presence in ad_seed.db: CHECK
  before B-2's periodOpen probe; absent → periodOpen=true + §-named.
- Driving headless: serve local http over `erp/`, use the `ADUI._test`/`IdmpSession` seams + direct in-page
  calls (see poc_ad_access_live.js). Playwright at `~/bim-ootb/tests/node_modules/playwright`.
- Deploy = git push from the worktree branch → PR → auto-merge; VERIFY the squash actually landed
  (orphaned-commit trap, PR #138). ERP sw version bumps on every deploy.
- Matrix edits are now SAFE (fold lane drained) — bank re-verdicts in the SAME session as their witness.

## DONE WHEN
Each B-item is ✅ (witness § lines + on-screen verify + deployed + matrix re-verdict) or ⛔ with the one
blocking fact (B-5 is pre-marked). Update this card's # DONE appendix with a § line per claim (Watchdog rule).

---

# DONE (2026-06-11) — lane worked B-1→B-5; deployed bim-ootb PR #264 (squash 8229cc6 VERIFIED on main), sw v647

> **2026-06-11 LATER (IDMP_FULLWIDTH_SEED lane): the §-named seed residuals BELOW ARE DISSOLVED** by the
> FULL-WIDTH ad_seed.db (PR #265, sw v648, IDB key v14): c_doctype now carries iscanbereactivated/docsubtypeso
> (doc-action bar legally offers RE — Order 135/GLJ 115/ARR 119 canReact=Y; W-AD-DOCFSM-LIVE re-derived, 6
> cases) · M_InOut.MovementType present → window 169 scopes rows, the no-VO-on-InOut falsifier restored ·
> **B-5's entrance is SATISFIED** (ad_process 476 + ad_process_para 1208 in the seed) — the C-5 dispatch wiring
> itself remains this lane's open item. All five live witnesses re-run green on the new seed.

- ✅ **B-1a access witness banked** — `scripts/poc_ad_access_live.js` (moved from build/erp/, committed) re-run green:
  `§AD-ACCESS-LIVE … role=102 grants=413 visibleWindows=294/332 · role=103 163/332 · role=50004 0/332` →
  `🟢 W-AD-ACCESS-LIVE PASS` (`build/erp/poc_ad_access_live.log`). Matrix: AD_Role + AD_Window_Access ✅.
- ✅ **B-1b DisplayLogic seam DECIDED = swap to the proven engine, no double-implement** — findings: erp.html's
  grid/record-card path is RETIRED (§IDEMPIERE-ROUTE routes to idempiere.html) and idempiere.html had NO
  DisplayLogic at all. Wired `buildForm` → `window.AdEvaluator` (+ swapped the dead-path ad_ui.js:2126 legacy
  call). Witness `scripts/poc_ad_displaylogic_live.js`: `§AD-DISPLAYLOGIC-LIVE table=C_Order shown=33 hidden=27`
  + falsifier (no-logic DocumentNo renders) → `🟢 W-AD-DISPLAYLOGIC-LIVE PASS`. Matrix: AD_Field·DisplayLogic ✅
  (named residual: window-context vars unpopulated; tab-level render).
- ✅ **B-2 doc-action bar ← ad_docfsm** — ad_docfsm.js+ad_modelval.js SYNCED into bim-ootb/erp; `_b3` shim
  (better-sqlite3 API over sql.js, LOWERCASES result keys — sql.js returns stored-case keys, engines proven on
  lowercase ad_full.db; absent seed column → no-row conservative default, §-named). periodOpen = REAL
  MPeriod.isOpen port (c_period 360 + c_periodcontrol 10464 rows — c_period VERIFIED present per the card's trap).
  Witness `scripts/poc_ad_docfsm_live.js` 5 cases: `§AD-DOCFSM-LIVE table=224(GL_Journal) record=100 status=CO
  legal=[CL,RC,RA] periodOpen=true(period=155 GLJ=O)` · `clicked=RC from=CO to=RE` (chip→Reversed) · C_Order CO
  `legal=[CL,VO]` clicked=VO→VO · falsifiers: completed Invoice/Journal/Payment render NO VO button, CL order 102
  renders ZERO buttons → `🟢 W-AD-DOCFSM-LIVE PASS`. On-screen: `build/erp/figs/b2_docfsm_journal.png`.
  SEED-GAPS named: c_doctype lacks iscanbereactivated/docsubtypeso → conservative no-RE; M_InOut form-unreachable
  (all 4 windows filter on absent MovementType) → C_Invoice carries the no-VO falsifier. Matrix: C_DocType FSM ✅.
- ✅ **B-3 save-path ← ad_modelval** — glassbowl `crud_overlay.saveForm` fires `AdModelVal.fireHooks('BEFORE_SAVE')`
  (lazy per-table installer; C_Order = the 11-hook W-MORDER-SAVE set); crud_ops.json c_order adds m_pricelist_id +
  bill_bpartner_id (real AD columns) so reject+derive are drivable. Witness `scripts/poc_ad_modelval_live.js`:
  REJECT `§AD-MODELVAL-LIVE table=c_order hook=MOrder.priceListImmutable verdict=REJECT error="CannotChangePl"`
  (real c_orderline count; on-screen error, form stays open, NO commit) · DERIVE `verdict=OK
  derived={"bill_bpartner_id":112,…} fired=11` then signed commit `§CRUD-PERSIST … verifyChain=ok` →
  `🟢 W-AD-MODELVAL-LIVE PASS`. Screenshot `build/erp/figs/b3_modelval_reject.png`. Matrix: beforeSave row
  updated (live wiring noted; corpus stays 🟡). NOTE prepayNoCash/m_pricelist-derive of the card's example need
  master tables ABSENT from glassbowl_data.db — priceListImmutable/billDefaults are the same proven hook set on
  REAL data (honest swap, §-named). OBSERVED pre-existing bug (not this lane): the docstatus list widget doesn't
  mark the current value selected → editing a CO order can silently flip status to DR (`cols=docstatus` in the
  persist line) — backlog candidate.
- ✅ **B-4 P/R/F menu pruning** — getMenuTree now carries AD_Process_ID/AD_Form_ID (verified present in ad_menu);
  idmp_session gains accessibleProcesses/accessibleForms + scopeMenu(,,procSet,formSet); buildMenu passes the
  session sets. Witness `scripts/poc_ad_menu_prf_live.js`: `§IDMP-SESSION scopeMenu visibleWindows=294/332
  visibleProcs=137/159 visibleForms=21/24` (Admin) vs `163/332 · 116/159 · 14/24` (User) vs `0 · 1/159 · 0` (WebSvc);
  live DOM: Reset Accounting + Import File Loader present for GardenAdmin, ABSENT for GardenUser (falsifier) →
  `🟢 W-AD-MENU-PRF-LIVE PASS`. 'F' (workflow)/I/T leaves stay unscoped — ad_menu has no workflow/task id column
  in this seed (named residual). Matrix: AD_Process_Access + AD_Form_Access ✅.
- ⛔ **B-5 process dispatch** — CONFIRMED seed-gated: `no such table: ad_process` in bim-ootb ad_seed.db
  (python PRAGMA probe). Unchanged; needs a seed-regen/gen_ad job.
- **Deploy** — bim-ootb PR #264 squash-merged (8229cc6, verified on origin/main — not orphaned), sw v647
  (both new engines precached). Matrix headline now **6✅ / 33🟡 / 3⛔ of 42**.
