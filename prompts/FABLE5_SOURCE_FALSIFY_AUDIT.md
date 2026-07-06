# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: SOURCE-READ FALSIFICATION AUDIT (Fable 5 lane)
# Scope: a NARROW adversarial source-read of the iDempiere posting + document-lifecycle Java — find invariants,
#        triggers, or circular accounting dependencies that our oracle-DIFF suite has NOT yet exercised. The output
#        is a DELTA against docs/ERP_COVERAGE_MATRIX.md, NOT a re-statement of what is already maxDiff=0c.
# WHY THIS CARD EXISTS: a Gemini-style "produce an iDempiere→serverless mapping spec" prompt would REGENERATE work
#        already banked at a deeper standard (HARDEN_MATRIX + 30+ poc_*.js witnesses, each oracle-diffed with a
#        load-bearing §FALSIFIER). The ONLY non-redundant slice is CONFLICT IDENTIFICATION read out of the Java
#        SOURCE — because everything proven so far is diff-against-OUTPUT, a source-read can surface an edge case the
#        diff has not hit. That, and only that, is this lane.
# NON-NEGOTIABLE: EXTRACT, DON'T INVENT. Every finding cites a Java file:line in ~/idempiere-dev-setup/idempiere
#        (live checkout) and/or an AD row in build/erp/ad_full.db. No finding is admitted without a concrete source
#        citation AND a proposed §FALSIFIER (the test that would prove/disprove it). Spec-first; a claim with no
#        falsifier is a note, not a finding.
# READ FIRST (take it ALL in, this order):
#   1. docs/ERP_COVERAGE_MATRIX.md   — the equivalence scoreboard. Anything already ✅ oracle-equivalent is OUT OF
#                                      SCOPE unless you can show the diff is incomplete (name the untested branch).
#   2. docs/ERP_MODEL_ARCHETYPE.md   — the denominator (MOrder archetype + ~25 deltas). Hunt in the deltas + the
#                                      cross-document seams, not the archetype (already walked).
#   3. prompts/HARDEN_MATRIX.md      — the oracle-diff discipline + what each W-* already covers. Do not redo a row.
#   4. The witnesses themselves: scripts/poc_fold_complete.js · poc_alloc_post.js · poc_alloc_fx.js ·
#                                 poc_matchinv.js · poc_morder_fsm.js · poc_reverse.js — these define the CURRENT
#                                 falsification frontier. Your job is to find what they DON'T assert.

---

## The three hunting grounds (the Gemini prompt's focus areas, narrowed to the unproven edge)

The Gemini prompt's three areas are already oracle-equivalent at the happy path. Hunt ONLY the hidden branches:

### 1 — Posting / combination-code edge cases NOT in the seed
Read `Doc_Order` · `Doc_Invoice` · `Doc_InOut` · `Doc_Allocation` · `Fact` / `FactLine` / `Doc.createFacts`.
Already proven: VAT tax-correction sub-cents (W-FOLD-ALLOC), multi-ccy + CurrencyBalancing (W-FOLD-ALLOC-FX),
IPV variance split on the on-hand cap (W-FOLD-MATCHINV). **Find the branches the seed never exercises:**
- Charge lines / service products (expense DR) — named-absent in seed; what posting rule fires?
- `GL Distribution` (`GL_Distribution` / `MDistribution`) — a single line fanning to N accounts by percentage. Present in AD? Diffable?
- Suspense / `SuspenseError` / `SuspenseBalancing` accounts (`C_AcctSchema_GL`) — when does iDempiere route to them, and would our fold silently diverge?
- Rounding accounts (`CurrencyBalancing` is proven; what about `Realized/UnrealizedGain/Loss`, `WriteOff` thresholds)?
- Doc reposting / `Posted='Y'`→repost path — does the op-log re-fold idempotently or double-post?

### 2 — Lifecycle invariants enforced by DB TRIGGER or cross-table check, not by Java beforeSave
This is the highest-value ground — our FSM walk diffs `DocumentEngine.java` (Java), but Postgres-side enforcement is invisible to it.
- Search the DDL / migration scripts (`migration/`, `org.adempiere.server-rolling`/`*.sql`) for actual `CREATE TRIGGER` / `CREATE RULE` — enumerate every one that touches a document or fact table.
- Period-control gate (`C_Period` / `C_PeriodControl` open/closed) — proven at the FSM legal-set level; is the POSTING-time period check a separate enforcement we don't fold?
- Partial shipment / partial invoicing reconciliation: `M_MatchPO` / `M_MatchInv` / `C_OrderLine.QtyDelivered`/`QtyInvoiced` running totals — what keeps them consistent under interleaved partial docs, and does our fold reconstruct the SAME running totals?
- Reversal/void constraints: what BLOCKS a `VO`/`RE` (allocated payment exists, period closed, already-matched) — are all the blockers in Java, or some in a trigger / `beforeDelete`?

### 3 — Data-dictionary fallbacks + rounding policy the seed leaves at default
- Default-fallback accounts: when a `C_BPartner`/`M_Product_Category`/`C_Tax` acct row is MISSING, what does iDempiere fall back to (schema default? error?) — `MAccount.get` / `getValidCombination`. Our resolver assumes the row exists; name the fallback chain.
- Rounding: `C_Currency.StdPrecision` vs `.CostingPrecision` — proven HALF_UP at 2dp; where does iDempiere use CostingPrecision (4dp) and would a 2dp fold drift? (W-FOLD-MOVEMENT-FX already caught one round-cost-first bug — are there siblings?)
- Mandatory cross-field validation (`AD_Field.IsMandatory` + `MandatoryLogic`) at COMPLETE time, not save time.

## DELIVERABLE — a delta file, nothing else
Write `docs/ERP_SOURCE_AUDIT_DELTAS.md`:
- **A. NEW edge cases found** — table: `finding · Java file:line (or trigger/DDL) · why our fold would diverge ·
  proposed §FALSIFIER · matrix row to add`. These become new HARDEN_MATRIX rows.
- **B. Confirmed-safe** — surfaces you checked and found ALREADY covered by an existing W-* diff (cite which). Short.
- **C. n/a-in-seed** — real iDempiere behaviour with no seed data to diff (stays ⛔, named, never synthesized).
Do NOT modify any engine file, any poc_*.js, or the published paper. Output is the delta doc + (optional) proposed
poc_* stubs as NEW files under build/erp/ that a follow-up lane runs. Read the §-log discipline in HARDEN_MATRIX —
a finding ships only with the test that would catch it.

## Falsifier on the whole lane
If after the audit every checked surface lands in bucket B (already covered), that is a VALID and valuable result —
it hardens the "indistinguishable from iDempiere" claim. Do not manufacture findings to look productive. The §FALSIFIER
for this card: a finding admitted with no reproducing source citation = invented = rejected.
