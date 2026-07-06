# ⚠ DO NOT REMOVE — ISOMORPH TAIL / REPLICATION CARD: the trade-pattern document classes → oracle-equivalence
# WHO THIS IS FOR: an OPUS or SONNET session (per the allocation rule, memory `project_fable5_lane`: Fable did
#   the irreducible deep deltas — H-1 MOrder + H-2 MInOut/MInvoice/MPayment/inventory-family; the tail is the
#   SAME two witnesses with a different table + Java file. Replicate the proven pattern; invent NOTHING).
# READ THE LOG after every run (`bash build/erp/run_witness.sh scripts/poc_X.js` → build/erp/poc_X.log);
#   exit code is not evidence. Honour this preamble until the card is DONE.
#
# THE TEMPLATE (pinned by H-1, replicated 4× by H-2 — copy these, don't redesign):
#   · FSM witness     → scripts/poc_minout_fsm.js is the canonical shape. Shared parser =
#     scripts/docfsm_oracle.js (gate-aware RUNTIME parse of DocumentEngine.getValidActions + the class's
#     action methods — NEVER hand-transcribe an expected set; the §FALSIFIER must show the diff can fire).
#   · save witness    → scripts/poc_minout_save.js / poc_mpayment_save.js are the canonical shapes
#     (stored rows ACCEPT w/ zero contradictions → strip→re-derive == stored/master → cited reject mutations).
#   · engine          → ADDITIVE ONLY: ad_docfsm.DOC_FAMILY + the legalActionsFor() per-table switch
#     (build/erp/ad_docfsm.js — add the new table id + its Completed-block arm, cite the DocumentEngine
#     lines) · ad_modelval.install<Class>SaveHooks (build/erp/ad_modelval.js — one installer per class,
#     every hook cites its M*.java lines). After EVERY engine edit re-run the regressions:
#     poc_modelval, poc_docfsm, poc_morder_save, poc_morder_fsm, poc_morder_post, poc_minout_save,
#     poc_minout_fsm, poc_minvoice_save, poc_minvoice_fsm, poc_mpayment_save, poc_mpayment_fsm,
#     poc_minventory_family_fsm — ALL must stay green.
#   · capture         → fixtures live in build/erp/glassbowl_data.db. A class whose header/master rows are
#     not yet captured needs an ADDITIVE block in scripts/extract_fact_acct.sh (the H-2 pattern: add
#     columns/tables, re-run, then poc_factacct_doc + test_report_fin MUST still pass — proves the bundle
#     is unchanged where it matters). Docker `postgres`/`idempiere_test` must be up.
# NON-NEGOTIABLE: EXTRACT, DON'T INVENT — a class with NO seed documents gets source-parse FSM only +
#   an honest ⛔ on stored-replay (the W-MINVENTORY-FAMILY-FSM precedent), NEVER a synthesized oracle.
#   Posting for several tail classes is ALREADY oracle-folded — CITE the witness, do not redo it.

---

# The tail, class by class (seed counts VERIFIED live 2026-06-11; table IDs from ad_full.db ad_table)

Java root: `~/idempiere-dev-setup/idempiere/org.adempiere.base/src/org/compiere/model/`.
DocumentEngine per-table blocks (already parsed by `docfsm_oracle.sliceRegions` — the keys exist):
`MJournal` (:1143-1157, shared with MJournalBatch — Payment-style nesting: RC⊂periodOpen, RE⊂periodOpen∧canReact, RA) ·
`MAllocationHdr` (:1159-1172 — RC⊂periodOpen, RA; NO RE) · `MCash` (:1174-1183 — CO→VO only) ·
`MBankStatement` (:1185-1198 — periodOpen frame: RE if canReact, VO). Classes with NO block = generic-only
(getValidActions falls through) — their `legalActionsFor` arm is just the generic set; say so in the witness.

| Class (Java) | ad_table_id | Seed docs (client 11) | DocumentEngine block | Notes / expected residuals |
|---|---|---|---|---|
| `MJournal.java` | 224 | **2** (`gl_journal`, already captured) | Journal :1143-1157 | posting CITED (W-FOLD-GLJOURNAL, both schemas); beforeSave = period/rate derives |
| `MJournalBatch.java` | 225 | **1** (`gl_journalbatch` — capture additively) | same block :1143 | batch=header-of-headers; ControlAmt warning path |
| `MAllocationHdr.java` | 735 | **2** (`c_allocationhdr`, captured — header cols only, widen additively for save) | Allocation :1159-1172 | posting CITED (W-FOLD-ALLOC/-FX); headerless delta already noted in the archetype |
| `MCash.java` | 407 | **3** (`c_cash` — capture additively; `c_cashline` partial capture exists) | Cash :1174-1183 | CO→VO only — the SMALLEST block; beforeSave derives cashbook/name from date |
| `MBankStatement.java` | 392 | **2** (`c_bankstatement` — capture additively) | BankStatement :1185-1198 | RE⊂periodOpen∧canReact + VO⊂periodOpen — a FOURTH nesting; statement-line BeginningBalance derive |
| `MRMA.java` | 661 | **1** (`m_rma` — capture additively) | none (generic) | unlocks the named MInOut RMA residuals (:1333 doctype derive, OrderOrRMA pair) |
| `MRequisition.java` | 702 | **1** (`m_requisition`) | none (generic) | pre-order; beforeSave = warehouse/pricelist derives |
| `MTimeExpense.java` | 486 | **1** (`s_timeexpense`) | none (generic) | report-style doc |
| `MBankTransfer.java` | 200246 | **0** | none (generic) | ⛔ no-seed: FSM source-parse only, stored-replay n/a — state it |
| `MDepositBatch.java` | 200056 | **0** | none (generic) | ⛔ no-seed, same rule |
| `MProjectIssue.java` | 623 | **0** | none (generic) | ⛔ no-seed, same rule |
| Fixed Assets (`MAssetAddition` 53137 · `MAssetDisposed` 53127 · `MAssetReval` 53275 · `MAssetTransfer` 53128 · `MDepreciationEntry` 53121) | — | **0 each** (all `a_*` doc tables empty) | none (generic) | ⛔ no-seed family: source-parse FSM only; depreciation batch = [DepreciationPerf](../docs/DepreciationPerf.md), separate lane |

Per class, the two witnesses (named `poc_<class>_save.js` / `poc_<class>_fsm.js`, or one combined family
witness where classes share a block — the W-MINVENTORY-FAMILY-FSM precedent):
1. **FSM**: extend `sliceRegions`' marker list ONLY if a new block is needed (Journal/Allocation/Cash/
   BankStatement markers already exist); diff legal sets across 11 statuses × periodOpen × backDate ×
   react-Y/N doctypes; parse the class's prepareIt/completeIt last-returns + reActivateIt
   implemented-or-not + voidIt delegation; replay every stored doc (DR-CO→stored status; stored docaction
   legal-or-None); vocabulary ⊂ ad_ref_list 135/131; TWO falsifiers (illegal action + injected-action set-diff).
2. **save**: port beforeSave as `install<Class>SaveHooks` citing lines; stored rows ACCEPT with zero
   contradictions; strip→re-derive each defaulted column (MUST where the stored value is the derive,
   EXPLICIT-classified where Java only defaults a zero column); reject mutations per cited conditions.
   K=1 or K=2 is honest — STATE the count, never pad.

DONE when: every class above has its witnesses green with `§HARDEN … diff=0` lines (or an honest ⛔
stored-replay note for the 0-seed rows), the regressions all still pass, the equivalence ledger in
`docs/ERP_COVERAGE_MATRIX.md` gains one row per witness (additive — never re-verdict coverage), and the
archetype family table in `docs/ERP_MODEL_ARCHETYPE.md` cites them. Update this card's `# DONE` appendix
with a § line per claim (Watchdog rule).

---

# DONE (2026-06-11, Fable 5 session — every claim has its § line; logs under build/erp/)

**The tail is DRAINED: 10 witnesses green, every DocumentEngine per-table block + every generic-block
document class walked; the 12 listed regressions + the fixture-dependent folds all stay green.**

- **Capture (additive)** — `extract_fact_acct.sh` widened gl_journal (doc cols + processedon) /
  c_allocationhdr (doc cols) / c_cashline (c_cash_id, amount) / c_bankaccount (currentbalance) /
  c_doctype (gl_category_id + overwrite flags); NEW gl_journalbatch, c_cash, c_cashbook, c_bankstatement(+lines),
  m_rma, m_requisition, s_timeexpense.
  § `§EXTRACT tail gl_journalbatch=1 c_cash=3 c_cashbook=2 c_bankstatement=2 c_bankstatementline=2 m_rma=1 m_requisition=1 s_timeexpense=1` (extract_fact_acct.log)
  § bundle unchanged where it matters: `§EXTRACT fact_acct rows=300 … Dr=46574.97 Cr=46574.97 diff=0.0` + poc_factacct_doc exit 0 (`§HARDEN capture=fact_acct_doc rows=300 … rowDiff=0`) + test_report_fin `=== RESULT: ALL PASS ===`.
- **Engine (additive)** — `ad_docfsm.js` DOC_FAMILY +16 tables (224/225 journal · 735 alloc · 407 cash ·
  392 bankstmt · 661/702/486 generic · 200246/200056/623 + 5 FA zero-seed) with per-table `block`/`vo`/`rc`/`ra`
  parsed flags; `legalActionsFor` gains the journal/alloc/cash/bankstmt arms (cited :1143-1198);
  `ad_modelval.js` +7 installers (MJournal 8 hooks · MJournalBatch 3 · MAllocationHdr 1 · MCash 2 ·
  MBankStatement 3 · MRMA 5 · MRequisition 1), every hook citing its M*.java lines.
- **W-MJOURNAL-FSM** ✅ § `§HARDEN surface=GLJournalFamily.docaction fixtures=207 diff=0` (poc_mjournal_fsm.log) — shared block RC⊂periodOpen/RE⊂periodOpen∧canReact/RA; journal VO@DR/IN-only, batch VO-never; K=2+1 replayed; §FALSIFIER-A/-B fire.
- **W-MJOURNAL-SAVE** ✅ § `§HARDEN surface=GLJournalFamily.beforeSave fixtures=3 diff=0` (poc_mjournal_save.log) — period 155 / category 108 / schema 101 / convtype 114 MUST; ParentComplete reject on the REAL processed batch; frozen-gate flag-N accept arm on REAL ProcessedOn (flag-Y reject arm seed-absent, named).
- **W-MALLOCHDR-FSM** ✅ § `§HARDEN surface=MAllocationHdr.docaction fixtures=100 diff=0` (poc_mallochdr_fsm.log) — no RE arm even react-Y; VO@CO→RE delegation; K=2 replayed.
- **W-MALLOCHDR-SAVE** ✅ § `§HARDEN surface=MAllocationHdr.beforeSave fixtures=2 diff=0` (poc_mallochdr_save.log) — the ONE IsActive guard, K=1 stated; N→Y rejected, Y→N + new-record accepted.
- **W-MCASH-FSM** ✅ § `§HARDEN surface=MCash.docaction fixtures=34 diff=0` (poc_mcash_fsm.log) — CO→VO only; **VO lands RE even from DR** (reverseIt sets Reversed :758 + DocumentEngine:603); RA never; K=3 replayed (2 CO + 1 DR).
- **W-MCASH-SAVE** ✅ § `§HARDEN surface=MCash.beforeSave fixtures=3 diff=0` (poc_mcash_save.log) — org←cashbook MUST 3/3; ending=beginning+stmtdiff cent-exact 3/3; stored stmtdiff == Σ(c_cashline.amount) 3/3.
- **W-MBANKSTMT-FSM** ✅ § `§HARDEN surface=MBankStatement.docaction fixtures=99 diff=0` (poc_mbankstatement_fsm.log) — the FOURTH nesting (periodOpen frames RE AND VO); VO@CO→VO no-delegation; RC/RA never; K=2 replayed.
- **W-MBANKSTMT-SAVE** ✅ § `§HARDEN surface=MBankStatement.beforeSave fixtures=2 diff=0` (poc_mbankstatement_save.log) — CMB doctype 146 MUST; the draft statement's beginning-balance derive STATE-DEPENDENT, diffed vs the captured master (148) and classified, nothing skipped; both :265 gate arms falsified.
- **W-GENERIC-TAIL-FSM** ✅ § `§HARDEN surface=GenericTail.docaction fixtures=333 diff=0` (poc_generic_tail_fsm.log) — 11 generic-block classes == the parsed generic region; RMA(IP via DR-PR)/Requisition/TimeExpense replayed; § `⛔ C_BankTransfer/C_DepositBatch/M_ProjectIssue/A_* stored-replay=N/A (0 seed documents each)` stated in-log; MProjectIssue RC/RA-callables + MBankTransfer return-false quirk parsed.
- **W-GENERIC-TAIL-SAVE** ✅ § `§HARDEN surface=GenericTail.beforeSave fixtures=3 diff=0` (poc_generic_tail_save.log) — RMA shipment-derives MUST + IsSOTrx-flip reject; Requisition pricelist FALLBACK arm proven; `§HARDEN surface=MTimeExpense.beforeSave override=ABSENT hooks=0 diff=0`.
- **Regressions** ✅ all exit 0 (logs re-read): poc_modelval · poc_docfsm · poc_morder_{save,fsm,post} ·
  poc_minout_{save,fsm} · poc_minvoice_{save,fsm} · poc_mpayment_{save,fsm} · poc_minventory_family_fsm ·
  poc_alloc_{post,fx} · poc_gljournal · poc_money_post · poc_reverse · poc_sales_to_ship · poc_factacct_doc ·
  test_report_fin (fold logs grepped: 0 reds).
- **Ledger** — `docs/ERP_COVERAGE_MATRIX.md` +10 oracle-equivalent rows (tally THIRTY-ONE → **FORTY-ONE**;
  ⬜ row no longer lists the FSM tail); `docs/ERP_MODEL_ARCHETYPE.md` isomorph-tail confirmation block +
  6 witness-table rows + score line updated.
- **Honest ⛔ ledger** — 0-seed stored-replay n/a (8 classes, source-parse only) · journal frozen-gate
  flag-Y reject arm seed-absent · MJournal DateDoc←today arm needs the clock · posting for Cash/BankStatement/
  RMA/Requisition/TimeExpense/FA has 0 fact_acct rows to diff (NOT an FSM/save gap — a posting-oracle gap).
