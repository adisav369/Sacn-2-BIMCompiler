# ERP Source-Read Falsification Audit — Deltas

**Lane:** `prompts/FABLE5_SOURCE_FALSIFY_AUDIT.md` (model-agnostic). **Date:** 2026-06-13.
**Companion to:** [Coverage Matrix](ERP_COVERAGE_MATRIX.md) (the equivalence scoreboard, 43 oracle-equivalent rows) ·
[Model Archetype](ERP_MODEL_ARCHETYPE.md) (the denominator) · [Harden Matrix](../prompts/HARDEN_MATRIX.md) (the oracle-diff discipline).

**Method.** A NARROW adversarial *source-read* of the iDempiere posting + document-lifecycle Java
(`~/idempiere-dev-setup/idempiere`, master @ `87968daa73`, 199 behind origin — stable for this read) to find
branches/invariants the oracle-DIFF suite (30+ `poc_*.js`, each `maxDiff=0c`) has **not** exercised. This is a
**delta against the matrix**, not a restatement of what is already proven. Every finding cites a Java `file:line`
**and/or** an AD row in `build/erp/ad_full.db` / a seed row in `build/erp/glassbowl_data.db`, plus a proposed
**§FALSIFIER** — the test that would catch it. No citation = rejected as invented.

**Oracle status (Session 0).** Docker `postgres` container UP (7 days); both `idempiere` and `idempiere_test`
GardenWorld DBs live → live-diff is available for any bucket-A surface. No surface stalled on a down oracle.

**Headline (honest).** Of the surfaces hunted, **2 are genuinely NEW diffable edges (bucket A)** — **A-1 is now
CLOSED** (W-MORDER-QTYROLLUP, `maxDiff=0`, 2026-06-13; A-2 remains a stub); the rest are
already covered (**B**) or armed-but-dormant in this seed (**C**). Per this lane's own falsifier, a small A-count is
the *expected* result — it hardens the "indistinguishable from iDempiere" claim rather than weakening it. Findings
were **not manufactured** to look productive; every C surface is a real iDempiere behaviour with **no seed journal
to diff**, named so it stays ⛔ and never gets synthesized.

---

## A · NEW edge cases — propose new HARDEN_MATRIX rows

These have **real seed data (or a property to test)** and **no existing witness asserts them**.

| # | Finding | Citation (Java + AD/seed) | Why our fold would diverge | Proposed §FALSIFIER | Status / matrix row |
|---|---|---|---|---|---|
| **A-1** | **Partial-fulfillment running-total writeback** — `completeIt` on a shipment/invoice updates the *parent* `C_OrderLine.QtyDelivered` / `QtyInvoiced` (a `completeIt` SIDE-EFFECT, not a `beforeSave` invariant). The H-2 walk diffed `beforeSave` hooks and the FSM; it never asserted this afterSave cascade onto the order line. | `MInOut.java:1981/1983` `oLine.setQtyDelivered(…±Qty)` · `MInvoice.java:2121` `ol.setQtyInvoiced(…+Qty)` · `MOrderLine.java:518-525` `beforeDelete` guards (`QtyDelivered≠0`/`QtyInvoiced≠0` block delete). **Seed:** order **106** (`documentno=800002`, `IsSOTrx='N'`), lines 121/122/123 = `QtyDelivered=30/12/15`, `QtyInvoiced=0` (delivered, never invoiced) — the **only** partial-state order, and a **PO**, so W-FOLD-COMPLETE's `IsSOTrx='Y'` filter excludes it entirely. | W-FOLD-COMPLETE checks ship/invoice *lines == oracle docs*; it does **not** write back the parent order-line running totals, and never sees a deliver-but-don't-invoice order. A fold that regenerates documents independently could land `QtyInvoiced≠0` on order 106 or mis-sum under interleaved partials. | Fold order 106's shipment → assert `C_OrderLine.QtyDelivered == {30,12,15}` and `QtyInvoiced == 0` == the stored oracle row (cents/units, `diff=0`). §FALSIFIER-A: over-deliver (Qty>Ordered) must be representable / flagged. §FALSIFIER-B: an invoice fan-out must *increment* `QtyInvoiced` by exactly the invoiced qty, not overwrite. | **✅ RESOLVED 2026-06-13 — W-MORDER-QTYROLLUP** (`scripts/poc_partial_fulfill.js` → `build/erp/poc_partial_fulfill.log`, PASS): new pure verb `erp_engine.qtyRollup` folds the fan-out into `UPDATE_FIELD(QtyDelivered/QtyInvoiced)`; order 106 = `{30,12,15}` delivered / `0` invoiced == stored oracle, `maxDiff=0`; §FALSIFIER-A force-invoice→30, §FALSIFIER-B +1→1 both fire. Additive verb (completeOrder unchanged; poc_fold_complete/invoice_complete/opgroup regression-green). Auto-wiring into completeOrder = named next step. |
| **A-2** | **Posting repost / `FactAcctReset` idempotency** — re-posting a `Posted='Y'` doc **DELETEs** the prior `Fact_Acct` rows (archived to `T_Fact_Acct_History`) *before* re-deriving, so a re-fold must NOT double-post; into a **closed** period it refuses (`PeriodClosed`); a non-repost re-attempt returns `AlreadyPosted`. Our op-log fold has no asserted "delete prior facts before re-derive" step. | `Doc.java:625` `if (isPosted() && !isPeriodOpen())` · `:633` `deleteAcct()` · `:640` `return "AlreadyPosted"` · `:768-802` `deleteAcct()` → `INSERT INTO T_Fact_Acct_History … ; UPDATE … ; DELETE FROM Fact_Acct`. **AD:** `AD_Process 175` `FactAcctReset` is registered (matrix W-PROC, doc-action handler) — the *trigger* exists, the *delete-then-rederive* contract is untested. | The FSM blocks re-`completeIt` (CO∉legal), but **reposting is a separate action**. `post_resolver` derives a fresh balanced set each call with no idempotency assertion — a naive replay of FactAcctReset→repost would *append* a second journal, doubling balances. | Property test (no new oracle needed): derive a doc's posting twice through the FactAcctReset→repost path; assert the net `Fact_Acct` after repost == after first post (`maxDiff=0c`), i.e. the second derive *replaces* not *adds*. §FALSIFIER: skip the delete-prior step → balances double (residual≠0). | **`Doc.repost-idempotent` (Posted=Y→repost)** — Oracle col: ⬜→ target ✅ (property) |

---

## B · Confirmed-safe — already covered, cite the witness

| Surface checked | Verdict | Covered by |
|---|---|---|
| **DB triggers / rules on document or fact tables** | **NONE EXIST.** `grep -rin "CREATE TRIGGER\|CREATE RULE" --include=*.sql` over the checkout + `bim-compiler/migration/` hits only admin blobs (`delete_ad_{archive,attachment,image}_binary.sql`, `dbreplicasyncverifier.sql`) — zero on `c_order`/`c_invoice`/`m_inout`/`c_payment`/`c_allocationhdr`/`fact_acct`. | **The whole of ground 2 collapses to Java**, which the FSM + `beforeSave` walk already diffs (W-MORDER/MINOUT/MINVOICE/MPAYMENT/…-SAVE/-FSM). No hidden Postgres-side enforcement. *This hardens the claim.* |
| **Suspense balancing on a multi-currency doc** | `Fact.java:300` `if (!isSuspenseBalancing() \|\| isMultiCurrency())` — suspense balancing is **skipped** for multi-ccy; the doc routes to **currency-balancing** instead. | **W-FOLD-ALLOC-FX** (currency-balancing line → acct 724, `maxDiff=0c`). The schema-200000 path is exactly this branch. |
| **Inter-org segment balancing** (`c_acctschema_element.elementtype='OO' isbalanced='Y'`, both schemas) | The only balanced segment is Org → Due-To/From bridge. | **W-FOLD-MOVEMENT** (Intercompany Due-To/From, acct 600/741) + **W-FOLD-GLJOURNAL** (per-org balancing, `fact_acct(224)`). |
| **COMPLETE-time validation** (`MOrder.java:2157` BEFORE_COMPLETE, `:2217` AFTER_COMPLETE `fireDocValidate`) | ModelValidator fires at complete, can reject. | **W-MODELVAL** (BEFORE/AFTER_COMPLETE timing hooks; 0-line order BLOCKED before complete). |
| **Period-closed gating at the FSM** (`MInOut/MInvoice/MPayment/MAllocationHdr.voidIt` `MPeriod.testPeriodOpen`) | All in Java DocAction methods, none in `beforeDelete`. | The **per-table FSM rows** (W-*-FSM) already diff the gate-aware legal sets (RC/RA ⊂ periodOpen, the three distinct nestings). |
| **CostingPrecision (4dp) vs StdPrecision (2dp)** (`MCost.java:321/1022` 4dp; `FactLine.java:342` 2dp) | A 4dp cost rounded line-first vs cost-first drifts. | **W-FOLD-MOVEMENT-FX** *already caught* the round-cost-first 1c bug exposed by the schema-200000 4dp cost. Siblings (`MProduction`/`MInventory` cost-valued GL) are **named-deferred** (component cost absent in seed) — see C below. |
| **Product→Category account fallback** (`ProductCost.java:238` product→category→null) | Resolver must climb master→category→schema-default. | **post_resolver / W-POST-DERIVE** does override→`c_acctschema_default` fallback; the product→category hop is the resolved `{Product.Revenue}` path. (The *fully-missing* case is C-5.) |
| **Document-level / summary tax posting** (`c_tax 104 IsDocumentLevel='Y'`, `108 IsSummary='Y'` GST/PST; `MInvoiceTax.java:359` round-once) | Tax *calculation* rounding (doc-level sum-then-round vs per-line). | The happy-path invoice/alloc diffs **read the stored `c_invoicetax.taxamt`** (the real posted value) → posting equivalence holds regardless of how it was calculated. Tax *derivation* (POS new-line path) is a named residual, not a posting gap. |

---

## C · n/a-in-seed — real iDempiere behaviour, no seed journal to diff (stays ⛔, named, never synthesized)

These are **armed in the AD** (valid + active) but **no captured GardenWorld journal exercises them**, so there is
no oracle to diff. They are NOT gaps in the proven slice; they would bite a *different* tenant. Named so a future
seed/lane can promote them, and so the "indistinguishable" claim is scoped honestly.

| # | Surface | Citation | Why n/a here (the dormancy proof) |
|---|---|---|---|
| **C-1** | **GL_Distribution fan-out** — `Fact.distribute()` replaces one FactLine with N distribution lines by percentage. Our `Fact`-fold has **no `distribute()`**. | `Fact.java:689` `distribute()` / `:700` `MDistribution.get` / `:727` delete-source-line. **AD:** `gl_distribution 100` "Distribute Salaries" `IsValid='Y' IsActive='Y'`, schema 101, source `account_id=451`, 2 `gl_distributionline` (incomplete: blank target accts, 50%+0%). | Source **account 451 never appears in the captured `fact_acct`** (0 rows) → the distribution never fires in any GardenWorld journal. Definition is dormant/demo. If a seed posts to 451, our fold diverges (it would keep 451; iDempiere fans it out). |
| **C-2** | **Suspense-balancing fallback** (single-currency) — when a doc's source amounts don't balance, iDempiere posts the residual to the Suspense-Balancing acct; Suspense-Error catches unpostable lines. Our `balanceAccounting` only does currency- + inter-org-balancing. | `Fact.java:300/314` `isSuspenseBalancing()` → `getSuspenseBalancing_Acct()`. **AD:** `c_acctschema_gl` `UseSuspenseBalancing='Y'` (acct **219** schema 101 / **200019** schema 200000); `UseSuspenseError='Y'` (acct **220** schema 101). | Every GardenWorld document balances at source → the suspense path is **armed but never fired**. No unbalanced doc in seed to diff. (Ties to C-5: a *skipped* missing-acct line is exactly what would push a doc unbalanced into suspense.) |
| **C-3** | **Realized FX gain/loss on settlement** — settling a foreign invoice at a rate ≠ the invoice rate posts the difference to RealizedGain/Loss (a **different** account from CurrencyBalancing). | `Doc_AllocationHdr.java:892` `createInvoiceGainLoss` / `:975-976` `getRealizedGain_Acct`/`getRealizedLoss_Acct`. **AD:** `c_acctschema_default` schema 200000 → RealizedGain=**200027**, RealizedLoss=**200028**. | The schema-200000 alloc legs in `fact_acct(735)` are accounts **{427,511,516,518,724,765}** — **200027/200028 absent** → the seed allocation settles at the invoice rate, no G/L leg. **Doc note:** `poc_alloc_post.js`'s §DEFERRED says the FX witness "closed" this delta, but W-FOLD-ALLOC-FX closed only **currency-balancing** (724); realized G/L is genuinely **unproven**. §FALSIFIER for a future fixture: invoice-rate ≠ settlement-rate → a 200027/200028 leg appears. |
| **C-4** | **Charge-line / service-product posting** — a line with `C_Charge_ID` (no `M_Product_ID`) posts to the charge expense acct, not a product acct. | `DocLine.java:450` `if (M_Product_ID==0 && C_Charge_ID!=0)` → `:485` `getChargeAccount` → `MCharge.getAccount` (`ACCTTYPE_Charge`). **Seed:** `c_invoiceline`/`c_orderline` with `c_charge_id≠0` = **0 rows**. | No charge lines in any seed document. `poc_reverse.js:91` (`deriveInvoiceApFwd`) explicitly pushes a charge line to `absent[]` — our engine has **no charge-account resolution**, correctly named as a gap. |
| **C-5** | **Missing-account skip-line semantics** — when an account is *fully* missing (not even a schema default), iDempiere logs and **skips the FactLine** (returns null), continuing the post (which may then hit suspense, C-2). Our resolver treats `absent[]` as a hard fail. | `Fact.java:116-121` (null acct → log + `return null`, line not posted) · `Doc.java:1586-1591` (`getValidCombination_ID` → `return 0`, no exception) · `MCharge.java:59` / `DocTax.java:117`. | Every seed account resolves (all witnesses pass with `absent.length==0`). The *divergent* behaviour (skip-and-continue vs error) has no seed case to diff. Behaviourally relevant only with incomplete acct config (the C-2 suspense partner). |
| **C-6** | **Negative IPV** (invoice price > PO price → DR-side variance) in `Doc_MatchInv`. | `Doc_MatchInv.java:438` `if (ipv.signum()==0) return;` (sign handled generically by `Fact.createLine`); the avg-cost split rides current stock. | `poc_matchinv.js` already names "variance<0 (DR) not in seed" — all 18 seed matches are PO≤invoice (CR-side). No DR-variance match to diff. |
| **C-7** | **reverseAccrual booked-date / posting-time period check** — `reverseAccrual` books the negation in the *next open period*; `Doc.post()` re-checks period at posting time (separate from the FSM gate). | `Doc.java:625` posting-time period check; reverseAccrual date = next-open-period. | `poc_reverse.js` already names this: the `fact_acct` extract carries **no `c_period`/calendar table**, so the next-period date is unverifiable. The posting *negation* is proven (oracle-anchored); only the booked **date** is deferred. |

---

## Proposed follow-up stubs (optional, NOT run this lane)

- **A-1 — BUILT ✅** `scripts/poc_partial_fulfill.js` (W-MORDER-QTYROLLUP, green; promoted from the stub) + new
  pure verb `erp_engine.qtyRollup`. Oracle = stored GardenWorld `c_orderline` (no live `idempiere_test` needed).
- **A-2 — STUB** `build/erp/poc_repost_idempotent.js` — derive a doc's posting through FactAcctReset→repost twice,
  assert net `Fact_Acct` unchanged; §FALSIFIER skip-delete → balances double. Property test, no new oracle; not yet built.

## Falsifier on this lane
The §FALSIFIER for the card: *a finding admitted with no reproducing source citation = invented = rejected.* Every
A/C row above carries a verified `file:line` (greps re-run against the checkout — e.g. `MOrderLine.beforeDelete`
corrected to the real `:518-525`, not the first-pass `:941/947`) **and** a real AD/seed row. No row rests on memory.
