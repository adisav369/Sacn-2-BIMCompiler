# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: MULTI-LANE LAUNCH (one session, multi-agent, user-authorized 2026-06-11)
# Scope: run the three INDEPENDENT next-up lanes in PARALLEL sub-agents, then land them through ONE
#   serialized deploy train, then bank the matrix/backlog bookkeeping in ONE writer. This card IS the
#   user's explicit multi-agent opt-in ("use a workflow") — orchestrate with the Workflow/Agent tools.
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# HOUSE RULES the orchestration MUST encode (each one has bitten before — not advisory):
#   - bim-ootb edits ONLY in a /tmp/wt-* worktree off FRESH origin/main (PreToolUse hook blocks ~/bim-ootb).
#     ONE WORKTREE PER LANE; lanes never share a worktree or a branch.
#   - DEPLOYS SERIALIZE (the deploy train, Phase 2): only ONE bim-ootb PR in flight at a time.
#     sw.js CACHE_VERSION bumps once per landing; after each squash-merge VERIFY the squash actually
#     carries the diff (PR #138/#265 orphan trap — fired twice on 2026-06-11), then the NEXT lane
#     rebases its branch off the NEW origin/main before opening its PR.
#   - docs/ERP_COVERAGE_MATRIX.md + prompts/FRONTEND_LANE_MASTER.md have ONE writer: Phase 3 only.
#     Dev agents RETURN their re-verdict lines as data; they never edit shared docs.
#   - e2e flake: viewer S274 GP.2 goto-timeout is a KNOWN flake on ERP-only diffs → rerun the failed
#     job once before investigating.
#   - Witness-led, §-log first, EXTRACT don't invent, spec-first; Lucide-only pills; BigDecimal for money.
# POS IS NOT IN THIS SESSION: prompts/POS_LENS_SESSION.md is gated on the write-path being green
#   (CRUD lane below is part of that gate). It launches as its OWN next session once Lane B lands.

---

## WHY one session works (and where it would break)
Lanes A/B/C touch DISJOINT files (A: idempiere.html+idmp_session+ad_process sync · B: crud_overlay.js
glassbowl · C: bim-compiler only) — safe to DEVELOP in parallel. They collide only at (a) sw.js on
deploy and (b) the matrix/backlog docs — hence the serialized Phases 2/3. A flat fan-out without the
train re-creates today's orphaned-squash + sw-conflict churn; this DAG is the fix.

## ORCHESTRATION (the DAG a Workflow script should encode)
Phase 1 (parallel): Agent-A, Agent-B, Agent-C develop + witness in isolation; each RETURNS
  { branch?, witness §-lines, matrix re-verdict rows, residuals } — committed on its own branch
  (A/B) or on the bim-compiler branch (C). NO PR opened yet by A/B.
Phase 2 (serial, orchestrator or one agent): for A then B — fetch fresh origin/main, rebase/cherry-pick
  the lane branch, bump sw CACHE_VERSION (+ touched ?v=), re-run that lane's live witness against the
  worktree, push, PR, auto-merge, VERIFY SQUASH CARRIES THE DIFF, live-verify on Pages. Never two PRs open.
Phase 3 (serial, one agent): bank ALL matrix 🟡→✅ re-verdicts + FRONTEND_LANE_MASTER §OUTSTANDING
  updates + this card's # DONE appendix + memory, in one commit. Then session report: ✅ list + ⛔ questions.

---

## LANE A — B-5/C-5: live process dispatch (the seed-gate fell 2026-06-11; wiring only)
Sub-agent prompt (verbatim):
> Worktree: `git -C ~/bim-ootb worktree add /tmp/wt-procdispatch -b feat/proc-dispatch origin/main`.
> The full-width ad_seed.db (LIVE, sw v649) now ships ad_process(476)+ad_process_para(1208) — UI_UNPARK_RESUME.md
> B-5 was ⛔ ONLY on that. Spec refs: UI_UNPARK_RESUME.md B-5 · ERP_COVERAGE_MATRIX.md "AD_Process" row ·
> build/erp/ad_process.js (dispatch + registerHandler — the PROVEN spine, W-PROC).
> 1. SYNC ad_process.js (+ report_overlay.js if its handlers need it) from bim-compiler build/erp/ into
>    bim-ootb/erp — diff first, never blind-copy; the _b3 sql.js shim pattern from ad_docfsm (B-2) is the
>    adapter precedent (lowercased keys, absent-column conservative default).
> 2. Wire: menu P/R leaf click on idempiere.html → AdProcess.dispatch via the _b3 db; registered classname
>    runs (report procs → report_overlay folds, the 110/116/310 set); UNREGISTERED classname → the honest
>    "not available" card (the spine's explicit absent-handler result — never silent).
> 3. Param dialog: render AD_Process_Para fields via the EXISTING buildForm/AdEvaluator path (DisplayLogic
>    proven live in B-1); validate mandatory params via the spine's prepare gate (§PROC_PARAM_VALIDATE).
> 4. Witness scripts/poc_ad_process_live.js (ERP_ROOT pattern, mirrors poc_ad_docfsm_live.js): a REGISTERED
>    report proc dispatches + renders rows (`§AD-PROC-LIVE proc=<id> classname=<…> ok=Y rows=N`) · missing
>    mandatory param REJECTED · falsifier: unregistered classname shows the honest card, no silent no-op ·
>    menu-prune regression: GardenUser still sees 116/159 procs (B-4 untouched).
> 5. Commit on the branch. DO NOT open a PR / DO NOT touch sw.js (the deploy train does both).
> RETURN: branch name, witness §-lines, proposed matrix row deltas (AD_Process 🟡 residual shrinks;
> SvrProcess corpus stays 🟡 — 454 named-deferred is unchanged), residuals.

## LANE B — CRUD_EDIT_PERSIST residual: the docstatus-select bug (live-data corruption class)
Sub-agent prompt (verbatim):
> Worktree: /tmp/wt-docstatusfix -b fix/docstatus-select off origin/main. Resume card prompts/CRUD_EDIT_PERSIST.md;
> the bug was OBSERVED in UI_UNPARK_RESUME.md B-3 # DONE: the docstatus list widget doesn't mark the CURRENT
> value selected → editing a CO order can silently flip status to DR (`cols=docstatus` appears in the persist
> line on an edit that never touched status). VERIFY first which of the card's items v646 already closed
> (sw.js changelog: date-widget normDateValue + commitCrud signed persist BOTH shipped v646) — do not redo them.
> 1. Root-cause in crud_overlay.js buildForm/list-widget: the <select> must render with the record's CURRENT
>    docstatus selected; and the diff-detector must not emit a column whose value did not change.
> 2. Fix BOTH arms (render + no-op-diff suppression). PURE CORE seam if one exists (the v646 pattern:
>    CORE.normDateValue/tipValues are the precedent — add the helper beside them, witness it headless).
> 3. Witness scripts/poc_crud_docstatus.js (headless, real CORE like poc_crud_persist.js 16/16): open a CO
>    order → edit an unrelated column → persist line contains NO docstatus · the select shows CO selected ·
>    falsifier: an EXPLICIT status change still emits docstatus + routes through DOC_ACTION gating, not a
>    silent column write. Re-run poc_crud_persist.js + poc_crud_group.js regressions green.
> 4. Commit on the branch. NO PR, NO sw.js (deploy train).
> RETURN: branch, §-lines, whether the write-path gate (POS_LENS_SESSION GATE) is now green in your judgment
> + the one fact if not, residuals.

## LANE C — SPATIAL_PICKING §S-1: compile the GardenWorld warehouse (bim-compiler only, no deploy)
Sub-agent prompt (verbatim):
> Repo: bim-compiler working tree (NOT bim-ootb — no worktree needed; commit on the current branch).
> Spec: docs/SPATIAL_PICKING_SPEC.md §S-1 (read it whole first) + docs/BOMBasedCompilation.md (the recursion)
> + the no-cubes render gate (memory feedback_no_cubes_render_gate).
> 1. EXTRACT the locator topology: the 11 m_locator rows (build/erp/ad_seed_fullwidth.db) → write
>    config/warehouse_gardenworld.yaml — one entry per warehouse/aisle/rack/bin naming its M_Locator.Value +
>    m_locator_id; positions are COMPILED (TILE bins along racks, ROUTE for the walk order), never invented
>    coordinates (X/Y/Z in the ERP are TEXT labels — the spec says so).
> 2. Compile via the EXISTING BOM recursion path (building→floor→room→furniture == warehouse→aisle→rack→bin)
>    to build/erp/warehouse_gardenworld.db; stamp each bin mesh's element GUID = its m_locator_id (the
>    BIMtoERP linkage key, reversed).
> 3. Witness scripts/poc_wh_compile.js → W-WH-COMPILE: `§WH bins==m_locator rows mapped` (11/11) · W-BUFFER
>    space contract holds · falsifier: a yaml bin naming a locator absent from m_locator FAILS the compile ·
>    render gate: distinct-vertex check passes (no cubes) before the db is ever served.
> 4. Smoke the db in the viewer LOCALLY (localhost, the run_witness/§-log discipline) — do NOT deploy/upload.
> RETURN: §-lines, the db path + bytes, the yaml, what §S-2 (route) needs next, residuals.

## PHASE 2 — the deploy train (serial; encode in the workflow, never parallel)
For Lane A then Lane B (order: A first — bigger diff, B rebases over it cleanly):
fetch origin → rebase lane branch on origin/main → bump erp/sw.js CACHE_VERSION (+ ?v= of touched js)
with a changelog line → re-run the lane's live witness with ERP_ROOT=<worktree>/erp → push → PR →
`gh pr merge --auto --squash` → WAIT merged → `git show origin/main:<file>` proves the diff landed
(orphan check) → curl the Pages live-verify (sw version + one §-behavior probe). S274 flake → one rerun.

## PHASE 3 — single-writer bookkeeping (after the train)
ONE agent (or the orchestrator) commits in bim-compiler: matrix re-verdicts from Lanes A/B returns ·
FRONTEND_LANE_MASTER §OUTSTANDING flips · this card's # DONE appendix (every claim = a § line, Watchdog
rule) · memory updates (links-only index). Report = ✅ list + ⛔ questions, per WORK-TO-ZERO.

## DONE WHEN
Lanes A/B: live-verified on Pages with witnesses green + matrix banked. Lane C: W-WH-COMPILE green +
local render smoke. Card # DONE appendix written. POS session explicitly queued (its gate verdict from
Lane B's return recorded here). Anything user-blocked → `⛔ BLOCKED: <one question>`, move on.

## NEXT SESSIONS AFTER THIS CARD (not this session)
1. POS build — prompts/POS_LENS_SESSION.md + docs/POS_ADDON_SPEC.md (gate should be green post-Lane-B).
2. Spatial picking §S-2..§S-5 — route + walk + scan (rides Lane C's warehouse.db).
3. HARDEN_MATRIX ladder continuation (its own long arc — prompts/HARDEN_MATRIX.md).

---

# DONE — 2026-06-11 (orchestrated via Workflow: 3 parallel dev agents + serialized deploy train + single-writer bank)

## LANE A — B-5/C-5 live process dispatch ✅ (bim-ootb PR #267, sw v650, LIVE on Pages)
- Dispatch spine live on menu P/R leaves + `?process=` deep link (procSet-gated) —
  `§AD-PROC-LIVE proc=233 name="Verify Document Types" classname=org.compiere.process.DocumentTypeVerify dispatched=Y ok=Y rows=52`
  · `§AD-PROC-LIVE proc=110 name="Order Print" classname=report:c_order dispatched=Y ok=Y rows=1`
- Prepare gate rejects on-screen — `§PROC_PARAM_VALIDATE proc=310 missing=[C_AcctSchema_ID] badType=[] → REJECTED (no dispatch)`
- Falsifier: unregistered classname = honest card, never silent — `§AD-PROC-LIVE proc=333 name="Order Detail" classname=(blank) dispatched=N reason=absent-handler`
- Seed gap named honestly — `§AD-PROC-LIVE seed-gap (no such table: fact_acct) → empty set (honest)` (310 folds rows=0)
- B-4 pruning regression intact — `§IDMP-SESSION scopeMenu visibleWindows=163/332 visibleProcs=116/159 visibleForms=14/24 roots=15`
- All four live witnesses green: 🟢 W-AD-PROC-LIVE + W-AD-DOCFSM-LIVE + W-AD-DISPLAYLOGIC-LIVE + W-AD-MENU-PRF-LIVE
  (`build/erp/poc_ad_process_live.log` exit 0, re-run post-sw-bump by the train)
- Deploy train: sw v649→v650 + ad_process.js ADDED to PRECACHE_ASSETS; squash f8cbe1e orphan-checked
  (`git show origin/main:erp/sw.js` = v650 · `origin/main:erp/ad_process.js` carries the spine); Pages served v650 first poll.
- Matrix banked: **AD_Process 🟡→✅ → 7✅/32🟡/3⛔** (AD_Process_Para evidence added; SvrProcess corpus stays 🟡, 454 named-deferred).

## LANE B — docstatus-select bug (silent CO→DR flip) ✅ (bim-ootb PR #268, sw v651, LIVE on Pages)
- Render arm: select shows the record's CURRENT value — `§CRUD-LIST col=docstatus cur="CO" options=4 selected="CO"` (and VO/5)
- Diff arm: unrelated edit emits NO docstatus — `§CRUD-PERSIST key=c_order id=106 op=CRUD_UPDATE cols=description … verifyChain=ok`
- Falsifier: explicit change routes through DOC_ACTION gating, not a column write —
  `§CRUD-STATUS-SPLIT key=c_order docstatus CO→CL lane=DOC_ACTION fieldCols=description` ·
  `§CRUD-STATUS-SPLIT … DR→IP lane=DOC_ACTION outcome=in-progress unmet=c_bpartner_id,grandtotal`
- `§CRUD-DOCSTATUS PASS` (split-brain closed) + regressions `§CRUD-PERSIST PASS` + `§CRUDGROUP PASS` (logs read, 0 red)
- v646 items VERIFIED shipped (CORE.normDateValue + commitCrud present), NOT redone.
- Deploy train: sw v650→v651, glassbowl.html crud_overlay.js?v=2; squash ff0486e orphan-checked; Pages live-verified.
- **POS gate verdict: GREEN in substance** — caveat: lane-master D/E literal `§WRITE`/`§REFOLD` strings don't exist;
  equivalent evidence = `§SEAM-LIVE` + verifyChain=ok lines. Thin witness over the existing seam closes it if POS insists.

## LANE C — SPATIAL_PICKING §S-1 warehouse compile ✅ (bim-compiler a828258e, NO deploy by design)
- Topology EXTRACTED, bijective — `§WH_EXTRACT yaml bins=11 m_locator rows=11 bijection OK` ·
  `§WH bins=11 == m_locator rows mapped (11/11) guid==m_locator_id OK`
- Existing BOM recursion reused — `§BOM_WALK root=WH_SITE_GW leaves=11 subs=14 phantoms=15 ms=297`; BUFFER holds —
  `§WH_BUFFER sum-invariant=OK containment=OK pairs=25`
- Falsifier — `§WH_FALSIFIER ghost m_locator_id=99999 → compile FAILED as required (no invented bins)`
- Render gate — `§WH_RENDERGATE hashes=6 distinct_vertex_blobs=6 blob_miss=0 PASS (no cubes)`; local viewer smoke —
  `§WH_SMOKE_BINS loaded-db bins=11` + `§W-WH-SMOKE PASS`; db = `§WH_DB build/erp/warehouse_gardenworld.db bytes=61440` (git-ignored, regenerable)

## RESIDUALS (named, not lost)
- `fact_acct` absent from ad_seed.db → TrialBalance honest-empty; lighting it = seed-regen (export_ad.sh family), not wiring.
- Handler registry = 5 classnames; 454 SvrProcess named-deferred (corpus, unchanged). 110/116 reachable via `?process=` only (not menu leaves in this seed).
- bim-ootb report_overlay.js stays the 256-line CORE version (sufficient for registered handlers); the 908-line reporting-lane sync belongs to the reporting lane's residual.
- Glassbowl visual confirm of the docstatus fix (headless witness only; log≠visual proof). Footer "dry-run" copy stale (cosmetic).
- Lane C bins render INSIDE solid racks (occluded) — §S-3 ghost-depth reveal or shelf-frame recipe later; model is a compiled SCHEMATIC per spec.

## NEXT SESSIONS (queued per card §NEXT)
1. POS build — `prompts/POS_LENS_SESSION.md` + `docs/POS_ADDON_SPEC.md` (gate GREEN per Lane B verdict above).
2. Spatial §S-2..§S-5 — route rides `m_bom_line.ordinal` walk_seq already in warehouse_gardenworld.db + drafted M_Movement/M_InOut locator pairs.
3. HARDEN_MATRIX ladder (`prompts/HARDEN_MATRIX.md`).

---

# DONE — 2026-06-12 wave 2 (§NEXT queue executed: POS train · spatial §S-2..§S-5 dev+train · HARDEN B-1)

## LANE 1 — POS lens §P-1..§P-4 DEPLOYED ✅ (bim-ootb PR #269, sw v651→v652, LIVE on Pages)
- W-POS-LIVE re-run on the bumped tree (`build/erp/poc_pos_live.log` exit 0):
  `§POS-LIVE open station=100 tiles=16 priced=16 handAuthored=0` ·
  `§POS-SALE lines=2 dispatch=SALE newVerbs=[] chainOk=Y gid=e3607c9a-… ops=12 sealed=12` ·
  `§POS-DOC order=910001 completeIt ok (C_Order+M_InOut+C_Invoice CO in ONE group)` ·
  `§POS-LIVE-REPLENISH suggestions=8` · `🟢 W-POS-LIVE PASS`.
- Squash d8d3adf5 orphan-checked (`git show origin/main:erp/sw.js`=v652 · pos_lens/pos_core/erp_engine all in
  `git ls-tree origin/main erp/` · precache 3/3); Pages live-verified (v652 CI-minified — quote-agnostic greps);
  pages-build run 27368173617 success, fast-checks+e2e green. Full ledger: `prompts/POS_LENS_SESSION.md ## DEPLOY DONE`.

## LANE 2 — Spatial picking §S-2..§S-5 BUILT + DEPLOYED ✅ (bim-ootb PR #270, viewer sw v642→v643, LIVE on Pages)
- Route engine: `§W-WH-ROUTE PASS` (`build/erp/poc_wh_route.log` exit 0) — `§WH_ROUTE_ORDER input=[102,101,101]
  → route=[101,101,102] walk_seq=[1,1,7]` · `§WH_ROUTE_DET repeat=identical permuted=identical` ·
  `§WH_ROUTE_COVER steps=3/3 each-once=Y` · `§WH_FALSIFIER off-model locator=102 → unroutable=Y dropped=NONE` ·
  `§WH_DRAFT doc=M_Movement DR doctype=143 lines=3 qty=[4,4,4] newVerbs=[]` (buildDoc archetype).
- Walk lens on the phone viewport: `§W-WH-LIVE PASS` (`build/erp/poc_wh_walk_live.log` exit 0, 25 verdicts 🟢,
  390×844): `§WH PILL gate=on` / gate=off SampleHouse falsifier (`#pill-whwalk` absent on a plain building) ·
  `§WH TAP bin=50003 target=N step-held=1/3` · `§WH scan=50004 expected=101 via=typed REFUSED` ·
  `§WH PICK step=2/3 locator=101 qty=3 SHORT … chainOk=Y` · `§WH SKIP step=3/3 reason="bin blocked"` ·
  `§WH COMPLETE doc=wh-pick-1 status=CO via=dispatchFor(323) foldKeys=4 diffs=0 chainOk=Y` ·
  `§WH FOLD 123@101:-4 123@50000:4 127@101:-3 127@50000:3` · `§WH QR supported=N (honest fallback)`.
- Train: squash cb190f3 orphan-checked (origin/main viewer/sw.js v643 + wh_route/wh_walk precached;
  viewer.html panels.js?v=39 picking.js?v=27); erp/sw.js NOT touched (stays v652 — POS #269 already shipped
  the byte-identical erp/erp_engine.js, rebase deduped). DB `warehouse_gardenworld.db` → OCI COMMON bucket
  (per OCI_UPLOAD.md §RULES — the rule-card said dev bucket, the doc mandates COMMON for dbs; doc followed,
  conflict flagged), md5-verified. LIVE Pages behavior probe `§W-WH-LIVE-PAGES PASS`
  (`build/erp/poc_wh_live_pages.log` exit 0: §BBOX_CLEARED · no §BBOX_KEEP · no §BLOB_MISS · `§WH PILL gate=on`).
- §S-1 regression green this session: `§W-WH-COMPILE PASS` 11/11 + `§W-WH-SMOKE PASS` (`§WH_SMOKE_BINS bins=11
  guid==m_locator_id`). eslint no-undef total=0 on the 5 touched js files.

## LANE 3 — HARDEN B-1 logic-evaluator oracle-diff ✅ (bim-compiler only, no deploy needed)
- `§HARDEN surface=ad_evaluator fixtures=2751 diff=0 oracle=iDempiere-PG verdicts{T=985,F=1766} oracle_errors=0`
  (display 2211 / readonly 463 / mandatory 77 — each kind diff=0) vs the REAL compiled SimpleBooleanParser+
  EvaluationVisitor (`§HARDEN-ORACLE-BUILD` LogicOracle.java vs ANTLR 4.9.2 classes) over live-PG record context
  (`§HARDEN-CAPTURE candidates=2809 … fixtures=2751 skips{sql=3,context-var=542,no-pk=32,no-rows=236}`);
  expr md5-sets ours==PG (`§HARDEN-SRC` 754/124/25 setdiff=0); `§HARDEN-FALSIFIER` IsOwned Y→N flips BOTH sides.
  `✅ ALL VERDICTS PASS` — `build/erp/poc_logic_harden.log` exit 0. **Equivalence ledger 41 → 42**; ⬜ now =
  ad_workflow ONLY (B-2 entrance: no seed ad_wf_activity — do not synthesize).

## RESIDUALS (named, not lost)
- POS matrix row pends the live to-the-cent ring (posting-config data-gate → `prompts/MIGRATE_POSTING_CONFIG.md`).
- Spatial: camera QR unverified on a physical phone (BarcodeDetector absent headless, no Safari/iOS guarantee) ·
  walk op log in-memory only (no IDB persist, no viewer-page signer — offline rides §P-5 sync-FSM, v2) ·
  reservation semantics + short-pick remainder view = v2 · §S-6 put-away/cycle-count named v2 ·
  manifest.json landing card for warehouse_gardenworld.db = USER product choice (deep-link proven live) ·
  ad_seed.db (25MB) network-loaded at walk-open, not precached · sw v643 changelog says "26 verdicts" (actual 25,
  comment-only, fold into next bump) · `scripts/poc_wh_live_pages.js` still UNTRACKED (re-runnable live-Pages smoke; not in the named commit paths — commit/delete at owner's discretion).
- Pre-existing, untouched: audit_specs.js exit 1 (38-sh-dx-2d-runtime.spec.js, viewer lane) ·
  viewer/tests/test_pills_manifest.js fails on origin/main (expects absent viewer/pills.json).
- HARDEN honest skips: 3 `@SQL=` (parseSQLLogic, separate surface) · 542 window/login-context exprs (needs live
  session-context lane) · 236 zero-row tables · AD_Tab/AD_Field logic twins ride the same grammar, not separately
  fixtured · poc_*.log evidence untracked by design (gitignore /build/erp/*.log).

## NEXT SESSIONS (queued)
1. B-2 workflow oracle (entrance: real ad_wf_activity trace — else ⛔ n/a-in-seed, stop) / B-4 Track B substrate
   §H-7..§H-11 (`prompts/ERP_EXECUTION_ROADMAP.md`).
2. MIGRATE_POSTING_CONFIG — lights the POS + Posting-Preview + §S-5 to-the-cent rings (one data-gate, three rows).
3. Phase C UI wiring continuations (C-1..C-4, flips 🟡→✅).
