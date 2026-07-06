# ⚠ DO NOT REMOVE — FABLE 5 LANE / RESUME CARD: H-2 DEEP DELTAS → ORACLE-EQUIVALENCE (the archetype walk)
# WHO THIS IS FOR: a Fable 5 session ONLY. This is HARDEN_MATRIX §H-2 lifted out, the sequel to the COMPLETED
#   H-1 (`prompts/FABLE5_MORDER_EQUIVALENCE.md` — read its `# DONE` appendix: the MOrder archetype is GREEN by
#   diff and the reusable TEMPLATE is pinned). H-2 = apply that template to the DEEP delta classes. Per the
#   allocation rule (memory `project_fable5_lane`): Fable does the deep deltas here; the trade-pattern ISOMORPH
#   tail (same template, different line table) is OPUS/SONNET replication — H-2.5 writes that handoff spec.
# THE PINNED TEMPLATE (proven by H-1; replicate, don't reinvent):
#   stored-state oracle (real client-11 rows were written THROUGH the Java being ported — accept 8/8, derive
#   defaults == stored) + RUNTIME SOURCE-PARSE oracle (parse DocumentEngine.java/M*.java in the witness, never
#   hand-transcribe — this broke the "needs a live JVM" blocker and caught real bugs in H-1) + live-PG row-diff
#   where SQL-grounded + ONE load-bearing §FALSIFIER per surface. Integer cents. Witnesses via
#   `bash build/erp/run_witness.sh scripts/poc_X.js` (NEVER tee to context). READ THE LOG; exit code ≠ evidence.
# NON-NEGOTIABLE: EXTRACT, DON'T INVENT — fixtures + oracles come from ~/idempiere-dev-setup/idempiere and
#   build/erp/glassbowl_data.db (the bundle; ad_full.db is the AD seed). A class with NO seed documents gets an
#   honest reduced scope (source-parse FSM yes, stored-replay n/a) or ⛔ — never a synthesized oracle.
#   Keep §0 seams (docs/ERP_BACKEND_SEPARATION.md): extend ad_modelval/ad_docfsm ADDITIVELY (H-1 pattern:
#   installMOrderSaveHooks / legalActionsOrder), regression-run the existing witnesses after every engine edit.

# READ FIRST — in this order:
#   1. prompts/FABLE5_MORDER_EQUIVALENCE.md `# DONE` appendix — what exists, the template, the residuals.
#   2. docs/ERP_MODEL_ARCHETYPE.md — the delta table (§"The document family") + the "✅ REACHED" block.
#   3. scripts/poc_morder_{save,post,fsm}.js + scripts/poc_factacct_doc.js — the four template witnesses.
#      poc_morder_fsm.js's parseBlock ALREADY parses every per-table region of DocumentEngine.getValidActions
#      (:1094-1230: InOut/Invoice/Payment/Journal/Allocation/Cash/BankStatement/Movement+Inventory) — H-2
#      generalizes the region slicing, it does not rewrite the parser.
#   4. The Java for each phase's class (org.adempiere.base/src/org/compiere/model/): MInOut.java · MInvoice.java
#      · MPayment.java (+ Doc_* in org/compiere/acct only where a posting gap remains — most posting is FOLDED).
#   5. scripts/extract_fact_acct.sh — extend ADDITIVELY per phase (the H-1 pattern: add columns/tables, re-run,
#      re-run poc_factacct_doc + test_report_fin to prove the bundle is unchanged where it matters).
# SEED FIXTURE REALITY (verified 2026-06-11 — scope each phase to this, no padding):
#   m_inout 9 docs (4 SO shipments + 5 receipts; fact_acct(319)=8 docs both schemas; isintransit captured) ·
#   c_invoice 8 (4 sales + 4 vendor; 318) · c_payment 2 (335) · m_movement 1 (323) · gl_journal 2 (224) ·
#   c_bankstatement 1 (392) · m_matchinv 18 (472) · MInventory/MProduction: NO seed documents (m_transaction
#   has no I±/P± rows) — FSM source-parse only, save-replay n/a, say so.

---

# H-2 — Walk the deep deltas, deepest first

Per class: (a) beforeSave invariants via NEW additive `ad_modelval.install<Class>SaveHooks(db)` — stored rows
accept + stripped defaults re-derive stored + cited reject mutations; (b) the per-table DocAction FSM via NEW
additive `ad_docfsm.legalActionsFor(adTableId, …)` (generalize legalActionsOrder; one function, table-keyed)
diffed against the runtime-parsed DocumentEngine block + the class's action-method deltas; (c) posting: ALREADY
oracle-folded for these classes (see matrix) — cite the witness, close LINE granularity only where the capture
has line_id and the existing fold was per-account (319 receipts/shipments are done in W-MORDER-POST).

### H-2.1 — MInOut (the in-transit/movement delta; richest fixtures: 9 stored docs)
- beforeSave (MInOut.java): movement-type/locator/org invariants; DocStatus replay for all 9 (CO).
- FSM: parsed InOut block (:1094-1104 — CO → RC gated periodOpen+isBackDateTrxAllowed, RA) + MInOut method
  outcomes. Witness `poc_minout_save.js` + `poc_minout_fsm.js`, each `§HARDEN surface=MInOut.<x> fixtures=K
  diff=0` + §FALSIFIER.
### H-2.2 — MInvoice (3632 LOC; 8 stored docs)
- beforeSave: paymentterm/pricelist/bpartner defaulting mirrors MOrder but invoice-flavored — port the DELTA,
  cite lines. FSM: parsed Invoice block (:1108-1123 — RE?+RC gated periodOpen, RA). `poc_minvoice_save.js` /
  `poc_minvoice_fsm.js`.
### H-2.3 — MPayment (allocation engine delta; 2 stored docs — K=2 is honest, state it)
- beforeSave: bank-account/doctype/amount invariants. FSM: parsed Payment block (:1127-1139). Posting + the
  allocation half already W-FOLD-PAYMENT/W-FOLD-ALLOC — cite. `poc_mpayment_save.js` / `poc_mpayment_fsm.js`.
### H-2.4 — MMovement + MInventory + MProduction (the inventory family edge)
- MMovement: 1 stored doc — replay + FSM block (:1200-1211). MInventory/MProduction: FSM source-parse diff
  ONLY (no seed docs — stored-replay n/a, beforeSave reduced to reject-mutations with cited lines, or an
  honest ⛔ if even that needs absent data). ONE witness `poc_minventory_family_fsm.js` is acceptable.
### H-2.5 — Roll up + the isomorph handoff
- Archetype delta table: mark each walked row GREEN-by-diff citing witnesses; matrix tally advances by the
  surfaces actually proven (additive Oracle axis, no coverage re-verdict).
- Write `prompts/H2_ISOMORPH_TAIL.md` (Opus/Sonnet card): the remaining trade-pattern classes (MRMA/MCash/
  MBankStatement/MBankTransfer/MDepositBatch/MJournalBatch/MRequisition/MProjectIssue/MTimeExpense + Fixed
  Assets) each = the SAME two witnesses with a different table/Java file — list per class: Java path, seed
  fixture count, DocumentEngine block lines, expected residuals. Fable writes the spec; cheap models run it.

## HONEST RESIDUALS / STOP CONDITION
- No seed doc → no stored-replay claim; data-absent config (cost-valued prod/inv GL, commitment-ON) stays named.
- DONE when H-2.1–H-2.4 witnesses are green with `§HARDEN … diff=0` lines, regressions pass (W-MODELVAL,
  W-DOCFSM, W-MORDER-* must stay green), H-2.5 docs + handoff card written. Do NOT start the isomorph tail.
- Update memory `project_fable5_lane` + this card's `# DONE` appendix (Watchdog: every claim = a § line).

---

# DONE — H-2 COMPLETE (2026-06-11, Fable 5 lane). Every claim has its § line; READ THE LOGS.

- **CAPTURE extended ADDITIVELY** (`scripts/extract_fact_acct.sh` → `build/erp/extract_fact_acct.log`):
  c_bpartner +so/po term/pricelist/rule · c_bpartner_location +isbillto/ispayfrom/isshipto · c_doctype
  +ad_org_id/isdefault · m_warehouse +isdisallownegativeinv · m_movement widened to doc columns · NEW
  c_bankaccount / ad_clientinfo / ad_sysconfig(CASH_AS_PAYMENT) — `§EXTRACT c_bankaccount=3 ad_clientinfo=2
  ad_sysconfig(CASH_AS_PAYMENT)=1 m_movement(doc-cols)=1`; all prior counts identical (`§EXTRACT fact_acct
  rows=300 … Dr=46574.97 Cr=46574.97`); bundle proven unchanged where it matters: **W-FACTACCT-DOC re-run
  `rowDiff=0` exit 0 + `test_report_fin` ALL PASS**.
- **ENGINE extended ADDITIVELY**: `ad_docfsm.js` + `DOC_FAMILY`/`legalActionsFor`/`transitionFor`/`dispatchFor`
  (table-keyed; un-walked tables THROW, never inherit the generic union) · `ad_modelval.js` +
  `installMInOutSaveHooks`(5)/`installMInvoiceSaveHooks`(8)/`installMPaymentSaveHooks`(10)/
  `installMMovementSaveHooks`(1), every hook citing its Java lines · NEW SHARED `scripts/docfsm_oracle.js`
  (the H-1 parser generalized with GATE tracking — periodOpen/backDate/canReact, braced + braceless ifs).
- **H-2.1 ✅ W-MINOUT-SAVE** (`build/erp/poc_minout_save.log`, exit 0) `§HARDEN surface=MInOut.beforeSave
  fixtures=9 diff=0 oracle=iDempiere(stored-state+MInOut.java:1304-1370)` — 9/9 ACCEPT 0-contradictions;
  MovementType←doctype 9/9 MUST + DeliveryRule 9/9 MUST; SalesRep←order = MEASURED source-evolution drift
  (stored NULL, derive == c_order.salesrep 7/7, named); `§FALSIFIER warehouse 104(org 12) … hook=
  MInOut.warehouseOrgConflict` + newRecord-gate accept + `§FALSIFIER … OrderOrRMA`.
- **H-2.1 ✅ W-MINOUT-FSM** (`build/erp/poc_minout_fsm.log`, exit 0) `§HARDEN surface=MInOut.docaction
  fixtures=154 diff=0 oracle=iDempiere(parsed-source+seed-replay)` — RC⊂periodOpen∧backDate, RA ungated,
  no VO/RE/PO at CO; reActivateIt NOT IMPLEMENTED (:2970); VO@CO→RE delegation (+DocumentEngine:616-618);
  9/9 replays; `§FALSIFIER-A action=VO from=CO` rejected, `§FALSIFIER-B mutation=+RC@CO(periodClosed)`.
- **H-2.2 ✅ W-MINVOICE-SAVE** (`build/erp/poc_minvoice_save.log`, exit 0) `§HARDEN surface=
  MInvoice.beforeSave fixtures=8 diff=0` — 8/8 ACCEPT 0-contradictions (:1257 processed-skip proven);
  location 8/8 MUST + term/pl/rule == c_bpartner masters; currency←pl 8/8 MUST; pl/doctype-target/term
  EXPLICIT-classified; `§FALSIFIER … CannotChangePlIn` + `§FALSIFIER EUR invoice 109 … FillMandatory
  CurrencyRate` (captured ad_clientinfo→schema hop).
- **H-2.2 ✅ W-MINVOICE-FSM** (`build/erp/poc_minvoice_fsm.log`, exit 0) `§HARDEN surface=
  MInvoice.docaction fixtures=153 diff=0` — the NESTED periodOpen frame parsed from source (RE/RC both die
  when period closes); reActivateIt IMPLEMENTED→IP; 8/8 replays; both falsifiers fire.
- **H-2.3 ✅ W-MPAYMENT-SAVE** (`build/erp/poc_mpayment_save.log`, exit 0) `§HARDEN surface=
  MPayment.beforeSave fixtures=2 diff=0` — K=2 stated; `§HARDEN_CONFIG ad_sysconfig.CASH_AS_PAYMENT=Y`
  (gate config-derived); doctype/dateacct MUST 2/2; org←bank both arms (ba 100 org-0 no-derive / ba 200000
  →11); 4 rejects incl. per-column PaymentAlreadyProcessed + the :721 cash exemption accept.
- **H-2.3 ✅ W-MPAYMENT-FSM** (`build/erp/poc_mpayment_fsm.log`, exit 0) `§HARDEN surface=
  MPayment.docaction fixtures=103 diff=0` — RC⊂periodOpen ONLY (≠InOut, measured cross-table); real
  doctype 119 = react=Y live; posting/allocation CITED (W-FOLD-PAYMENT/W-FOLD-ALLOC/-FX), not redone.
- **H-2.4 ✅ W-MINVENTORY-FAMILY-FSM** (`build/erp/poc_minventory_family_fsm.log`, exit 0) `§HARDEN
  surface=MInventoryFamily.docaction fixtures=161 diff=0 … where seed exists` — Movement+Inventory ONE
  source block + Production same shape (44 set-fixtures × 3 + 9 outcomes × 3); reActivateIt NOT IMPLEMENTED
  ×3; Movement 1/1 replay + doctype-default derive 143; **M_Inventory/M_Production stored-replay = ⛔
  no-seed, stated in `§HARDEN_RESIDUAL`, never synthesized**; both falsifiers fire.
- **REGRESSIONS all green after every engine edit** (final sweep, all exit 0): W-MODELVAL · W-DOCFSM ·
  W-MORDER-SAVE · W-MORDER-FSM · W-MORDER-POST · W-FACTACCT-DOC · W-FOLD-COMPLETE · W-POST-HARDEN ·
  poc_postings (ONE stale expectation fixed: its honesty-check awaited the §13.6 record-keyed re-extract
  that H-1 delivered — flipped to `factHasRecordKey===true`, `§POSTED-COVERAGE … recordKeyed=Y`) ·
  `test_report_fin` ALL PASS (TB 46574.97).
- **H-2.5 ✅ ROLLED UP** — `docs/ERP_MODEL_ARCHETYPE.md`: H-2 CONFIRMED-BY-DIFF block + 4 new oracle-folded
  rows (the three gate nestings + reActivate split = the measured family facts); `docs/ERP_COVERAGE_MATRIX.md`
  equivalence ledger: **+7 rows, tally TWENTY-FOUR → THIRTY-ONE oracle-equivalent** (additive — coverage
  column untouched); ⬜ row narrowed to evaluator/workflow + the isomorph tail; **`prompts/H2_ISOMORPH_TAIL.md`
  WRITTEN** (per-class Java path / table id / live-verified seed counts / block lines / residuals; 0-seed
  classes pre-marked ⛔-honest).
- **HONEST RESIDUALS (named, not faked):** M_Inventory/M_Production beforeSave stored-replay (no seed docs) ·
  RMA-doctype + shipper/freight (MInOut) · pricelist-version date twins (no m_pricelist_version) ·
  payment-term re-apply / documentno / credit-card encryption (write-path) · charge/reversal/prepay resets +
  on-bank-statement void (data-absent) · periodOpen/backDate are caller-context probes (diffed both branches
  as parameter dimensions) · posting for ALL walked classes pre-folded, cited.
- **NOT pushed/committed by this lane** (repo carries broad pre-existing uncommitted work — commit is the
  user's call). Files touched: scripts/{extract_fact_acct.sh, docfsm_oracle.js, poc_minout_save.js,
  poc_minout_fsm.js, poc_minvoice_save.js, poc_minvoice_fsm.js, poc_mpayment_save.js, poc_mpayment_fsm.js,
  poc_minventory_family_fsm.js, poc_postings.js} + build/erp/{ad_docfsm.js, ad_modelval.js,
  glassbowl_data.db, poc_*.log, extract_fact_acct.log} + docs/{ERP_MODEL_ARCHETYPE.md,
  ERP_COVERAGE_MATRIX.md} + prompts/{H2_ISOMORPH_TAIL.md, this card}.
- **NEXT (per the stop condition, NOT started):** the trade-pattern isomorph tail — run
  `prompts/H2_ISOMORPH_TAIL.md` on Opus/Sonnet.
