# ⚠ DO NOT REMOVE — FABLE 5 LANE / RESUME CARD: H-1 MOrder → ORACLE-EQUIVALENCE (the keystone)
# ⚠ THIS IS THE LIVE ASSIGNMENT — run it directly. (`prompts/FABLE5_PROJECT_REVIEW.md` is an OPTIONAL whole-project
#   review, DEFERRED by decision 2026-06-11: H-1 is concrete + falsifiable and forces enough project understanding on
#   its own — reading the matrix, the oracle, and MOrder.java IS the evaluation. Do the real task, not the meta-doc.)
# WHO THIS IS FOR: a Fable 5 session ONLY. This is the single highest-value, highest-difficulty reasoning task in
#   the ERP arc — the one place the premium model earns its 2× cost. Everything else (convention audit, roadmap,
#   NinjaExcel eval — `prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING` top block) is planning/housekeeping and runs on
#   Sonnet/Opus. Do NOT spend a Fable 5 session on those. This card is H-1 of `prompts/HARDEN_MATRIX.md`, lifted out
#   and fully specified so it can run start-to-finish in one well-scoped session.
# WHY FABLE 5: H-1 is long-horizon + correctness-critical (engine output must == real iDempiere output, 0c diff), and
#   the 1M context window holds MOrder.java + the ad_full.db schema + the captured oracle fixtures SIMULTANEOUSLY —
#   exactly the shape where the higher ceiling pays off. Give the FULL task spec up front (this card). Effort: default
#   `high`; escalate a single phase to `xhigh` ONLY if it genuinely struggles (xhigh = more depth + MORE tokens, not
#   faster — don't reach for it reflexively on top of Fable 5's 2× rate). Omit the `thinking` param to disable (an
#   explicit {type:"disabled"} 400s on Fable 5).
# NON-NEGOTIABLE (every turn): EXTRACT, DON'T INVENT — fixtures + oracle outputs come from the iDempiere checkout
#   (~/idempiere-dev-setup/idempiere) and build/erp/ad_full.db / glassbowl_data.db; NEVER hand-author an expected
#   output (a missing oracle is an honest ⬜, not a ✅). Spec-first; whitebox §-log FIRST (READ the log; exit code ≠
#   evidence); deterministic (recorded ids/ts, INTEGER CENTS, no Date.now/Math.random); keep the §0 SEPARATION seams
#   (AD-declaration / interpreter / log-fold never merge — `docs/ERP_BACKEND_SEPARATION.md`). MECHANISM not CORPUS.
#   Run every witness via `bash build/erp/run_witness.sh scripts/poc_X.js` (NOT tee — keeps the log off context).

# READ FIRST — in this order, take it ALL in before writing any code:
#   1. prompts/HARDEN_MATRIX.md         — the parent equivalence arc; this card IS its H-1 (lines 71-75) expanded.
#                                          §"THE ORACLE ALREADY EXISTS" + §"oracle harness" are load-bearing.
#   2. docs/ERP_MODEL_ARCHETYPE.md      — THE DENOMINATOR: the MOrder surface table is what H-1 turns GREEN by diff.
#   3. docs/ERP_BACKEND_SEPARATION.md   — the §0 seams the work must NOT violate.
#   4. ~/idempiere-dev-setup/idempiere/org.adempiere.base/src/org/compiere/model/MOrder.java — the oracle SOURCE:
#                                          beforeSave invariants + the DocAction methods (prepareIt/completeIt/
#                                          reverseCorrectIt/voidIt/closeIt) are the semantics to diff against.
#   5. scripts/poc_odoo_fold*.js + build/erp/odoo_oracle*.json — THE ORACLE-DIFF TEMPLATE (capture once → diff
#                                          deterministically, maxDiff=0c). Extend exactly this discipline.
#   6. scripts/extract_fact_acct.sh + build/erp/glassbowl_data.db — the EXISTING posting-oracle capture (real
#                                          GardenWorld fact_acct, client 11, balanced 46574.97). H-1.1 extends it.
#   Existing engine modules to diff (do NOT fork — consume via the seam): build/erp/post_resolver.js (posting),
#   build/erp/ad_modelval.js (beforeSave timing hooks), build/erp/ad_docfsm.js (DocAction FSM). Done baseline:
#   scripts/poc_fold_complete.js already proves completeIt(C_Order) Order→Ship→Invoice at AGGREGATE maxDiff=0c —
#   H-1 EXTENDS that to PER-DOCUMENT granularity + the save/FSM surfaces (see H-1.1 why the aggregate isn't enough).

---

# H-1 — Make the MOrder archetype GREEN by oracle-diff

## The goal (one sentence)
The MOrder surface table in `docs/ERP_MODEL_ARCHETYPE.md` goes from 🟡 (interpreted) to ✅ (oracle-equivalent) —
every MOrder behaviour (save-invariants, full DocAction set, per-document posting) shows engine-output ==
iDempiere-output with a `§HARDEN … diff=0` log line over K real fixtures. "Got MOrder, got the core."

## Why per-document, not the existing aggregate (the real gap — HARDEN_MATRIX §50-53)
`completeIt` posting is already proven at the AGGREGATE journal level (`poc_fold_complete.js`, ΣDR=ΣCR, maxDiff=0c).
But the captured `fact_acct` has **no `record_id`/`ad_table_id`/`line_id`** → it can't prove *per-document* derivation,
only the netted total. H-1 closes that: extend the capture, then diff `post_resolver`'s derived lines **per C_Order**
against the real per-document fact_acct lines.

## Phases — each ends at a NAMED witness; do not advance until its log shows diff=0

### H-1.1 — Extend the posting oracle to per-document granularity
- ENTRANCE: read `scripts/extract_fact_acct.sh` + confirm Docker Postgres `idempiere_test` is reachable (if not →
  `⛔ BLOCKED: bring up the idempiere_test docker instance` and STOP; do not synthesize).
- DO: add `record_id, ad_table_id, line_id` to the fact_acct SELECT; re-capture into `build/erp/glassbowl_data.db`
  (or a sibling table `fact_acct_doc`). Keep the existing aggregate capture intact (additive).
- EXIT: `bash build/erp/run_witness.sh scripts/poc_factacct_doc.js` exit 0 — `§HARDEN capture=fact_acct_doc rows=N
  has_record_id=Y` and the row-count reconciles to the existing aggregate.

### H-1.2 — Doc_Order posting equivalence, per document
- DO: drive `build/erp/post_resolver.js` to derive fact_acct lines for K real C_Order documents (client 11); diff
  each derived line vs the captured per-document real lines (account, DR/CR, amount in integer cents).
- EXIT: `bash build/erp/run_witness.sh scripts/poc_morder_post.js` exit 0 —
  `§HARDEN surface=MOrder.Doc_Order fixtures=K diff=0 oracle=iDempiere`. One load-bearing §FALSIFIER (corrupt one
  amount → diff≠0) proves the diff is real.

### H-1.3 — MOrder.beforeSave invariants
- DO: from `MOrder.java beforeSave()`, port the invariants (pricelist / warehouse / bpartner / credit-status) through
  `build/erp/ad_modelval.js`; diff the engine's accept/reject + derived values against the Java semantics for K
  (record, context) fixtures extracted from the seed.
- EXIT: `bash build/erp/run_witness.sh scripts/poc_morder_save.js` exit 0 —
  `§HARDEN surface=MOrder.beforeSave fixtures=K diff=0`. §FALSIFIER: a record that Java rejects must be rejected here.

### H-1.4 — MOrder FULL DocAction set (not just CO)
- DO: via `build/erp/ad_docfsm.js`, diff the legal next-status SET and the resulting status for EVERY MOrder
  DocAction (prepareIt / completeIt / reverseCorrectIt / voidIt / closeIt) against MOrder.java + the C_DocType FSM.
- EXIT: `bash build/erp/run_witness.sh scripts/poc_morder_fsm.js` exit 0 —
  `§HARDEN surface=MOrder.docaction fixtures=K diff=0`. §FALSIFIER: an illegal action from a status must be rejected.

### H-1.5 — Roll up the result
- DO: mark the MOrder surface table in `docs/ERP_MODEL_ARCHETYPE.md` GREEN (cite each witness); add/advance the
  Oracle column + "N of 40 oracle-equivalent" tally in `docs/ERP_COVERAGE_MATRIX.md` (additive — do not re-verdict
  the coverage column). Leave the ledger's other rows untouched.
- EXIT: every H-1 witness green; the archetype table cites them; the tally moved by exactly the MOrder surfaces proven.

## HONEST RESIDUALS / STOP CONDITION
- If the Docker oracle isn't reachable, a surface stays ⬜ — `⛔ BLOCKED: <one question>` and move to the next H-1
  sub-phase; never fake an oracle.
- Cost-valued GL on prod/inv is a KNOWN named-deferred (seed lacks component-cost+offset-acct) — out of H-1 scope.
- DONE when the MOrder archetype table is GREEN by diff and each surface has a `§HARDEN … diff=0` line in its log.
  H-1 establishes the reusable oracle-diff TEMPLATE — that is what unlocks the economy downstream. After H-1:
  H-2's ~5 DEEP deltas (MInOut/MPayment/MProduction/MInventory/MAllocationHdr — genuine new logic) are the NEXT Fable
  lane (separate card; do NOT start here); H-2's trade-pattern ISOMORPH tail (same template, different line table +
  Doc_* poster) is OPUS/SONNET replication, NOT Fable. Rule: Fable does the irreducible new reasoning, cheaper models
  replicate the proven pattern. Do not start H-2 in this session.

---

# DONE — H-1 COMPLETE (2026-06-11, Fable 5 lane). Every claim has its § line; READ THE LOGS.

- **H-1.1 ✅ W-FACTACCT-DOC** (`scripts/poc_factacct_doc.js` → `build/erp/poc_factacct_doc.log`, exit 0)
  `§HARDEN capture=fact_acct_doc rows=300 docs=42 has_record_id=Y has_table_id=Y has_line_id=180 rowDiff=0
  oracle=iDempiere(live-pg)` — the capture (already granular from the earlier extractor extension) proven
  ROW-IDENTICAL to live `idempiere_test` keyed by fact_acct_id; reconciles to the 46574.97 aggregate anchor;
  `§FALSIFIER drop=fact_acct_id:1000000 rowDiffs=1`. Extractor additively extended this session:
  `c_acctschema.commitmenttype` + `c_doctype.iscanbereactivated` + m_pricelist/c_doctype/c_paymentterm/
  c_bpartner_location/ad_user/ad_ref_list(131,135) (`§EXTRACT m_pricelist=4 c_doctype=52 …`); re-ran clean,
  all prior counts identical, `test_report_fin` anchor still ALL PASS.
- **H-1.2 ✅ W-MORDER-POST** (`scripts/poc_morder_post.js` → `build/erp/poc_morder_post.log`, exit 0)
  `§HARDEN surface=MOrder.Doc_Order fixtures=8 diff=0 oracle=iDempiere` — Doc_Order derives the oracle's ZERO
  set (8/8 `§HARDEN surface=MOrder.Doc_Order … gate=closed(N) derived=0 oracle=0 diff=0`), gated by REAL
  `commitmenttype='N'` (`§HARDEN_CONFIG`); `§FALSIFIER-A … commitmenttype=S derived=1 oracle=0` proves the gate.
  Chain at LINE granularity: 7/8 `§HARDEN surface=MOrder.chain … diff=0c keySet=match verdict=EQUIVALENT`
  (order 108 = named AMT-DRIFT, the doc-109 post-posting edit); receipts fold DR Asset/CR NIR @ qty×PO-price;
  `§FALSIFIER-B … maxDiff=1c`.
- **H-1.3 ✅ W-MORDER-SAVE** (`scripts/poc_morder_save.js` → `build/erp/poc_morder_save.log`, exit 0)
  `§HARDEN surface=MOrder.beforeSave fixtures=8 diff=0 oracle=iDempiere(stored-state+MOrder.java:1183-1396)` —
  NEW `ad_modelval.installMOrderSaveHooks(db)` (11 hooks, ADDITIVE; W-MODELVAL regression green). 8/8 stored
  ACCEPT, 0 contradictions; Bill/Currency re-derive stored 8/8; 4 cited rejects fire (`§FALSIFIER … verdict=
  REJECT hook=MOrder.{clientNotZero,warehouseMandatory,prepayNoCash,priceListImmutable}`); conjunctivity +
  ctx-fallback + foreign-location-CLEARED all asserted.
- **H-1.4 ✅ W-MORDER-FSM** (`scripts/poc_morder_fsm.js` → `build/erp/poc_morder_fsm.log`, exit 0)
  `§HARDEN surface=MOrder.docaction fixtures=43 diff=0 oracle=iDempiere(parsed-source+seed-replay)` — NEW
  `ad_docfsm.legalActionsOrder/transitionOrder/dispatchOrder` (ADDITIVE; W-DOCFSM + W-FOLD-REVERSE regressions
  green) diffed against an oracle PARSED AT RUNTIME from DocumentEngine.java/MOrder.java/DocAction.java
  (`§ORACLE_PARSED generic=… c_order=… outcomes=…`): 23 legal sets + 12 outcomes + 8/8 replays diff=0; the
  narrowing CO→[CL,VO,(RE)] (never RC/RA/PO) measured; RA proven NOT IMPLEMENTED on orders (:3042);
  `§FALSIFIER-A action=PR from=CO` rejected, `§FALSIFIER-B mutation=+RC@CO setEq=false`.
- **H-1.5 ✅ ROLLED UP** — `docs/ERP_MODEL_ARCHETYPE.md` MOrder table: ALL FIVE ROWS GREEN (the "✅ REACHED"
  block names the witnesses + residuals); `docs/ERP_COVERAGE_MATRIX.md` equivalence ledger: +4 rows, tally
  TWENTY → **TWENTY-FOUR oracle-equivalent**; the ⬜ row narrowed to ad_evaluator(render) + ad_workflow.
- **HONEST RESIDUALS (named, not faked):** commitment-ON posting + pricelist-version date gate + reverseAccrual
  date-shift = data-absent-in-seed (RA additionally proven unreachable on MOrder) · schema-200000 chain legs =
  the already-proven FX folds, not re-proven · ad_evaluator display-logic + ad_workflow stay ⬜.
- **NOT pushed/committed by this lane** (repo had pre-existing uncommitted work across many files — commit is
  the user's call). Files touched: scripts/{extract_fact_acct.sh, poc_factacct_doc.js, poc_morder_post.js,
  poc_morder_save.js, poc_morder_fsm.js} + build/erp/{ad_modelval.js, ad_docfsm.js, glassbowl_data.db,
  poc_*.log} + docs/{ERP_MODEL_ARCHETYPE.md, ERP_COVERAGE_MATRIX.md} + this card.
- **NEXT (separate card, per the stop condition): H-2 deepest-delta walk** — the template to replicate is now
  pinned: stored-state oracle + runtime source-parse + live-PG row-diff + load-bearing §FALSIFIER per surface.
