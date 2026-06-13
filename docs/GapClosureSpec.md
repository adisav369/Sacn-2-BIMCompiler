# Gap-Closure Spec — how a coverage gap becomes oracle-equivalent

**Governs:** `prompts/GAP_CLOSURE_LANE.md` (the operational backlog, repo-local) closing gaps in
[`ERP_COVERAGE_MATRIX.md`](ERP_COVERAGE_MATRIX.md). **Parent discipline:** `prompts/HARDEN_MATRIX.md`
(the equivalence arc) + [`ERP_MODEL_ARCHETYPE.md`](ERP_MODEL_ARCHETYPE.md) (the denominator). **Method is PoC-proven**
— the oracle-diff harness (`scripts/poc_*_harden.js`, the `W-FOLD-*` family) already ships green; this lane *executes*
it across the remaining gaps. Spec-first: **no witness without a spec section naming the rule it proves.**

## 1. Prime law
**A row is ✅ only by an oracle DIFF, never by a claim.** Oracle output is *extracted* from the live iDempiere
Postgres or the iDempiere checkout — never hand-authored. Every closure carries a load-bearing **§FALSIFIER**:
corrupt the rule and the diff *must* go non-zero, or the test proves nothing (a pass that can't fail is a tautology).

## 2. Gap taxonomy — classify the gap BEFORE folding (it sets the achievable verdict)
| Class | What it is | Achievable verdict | Oracle source |
|---|---|---|---|
| **Foldable-now** | logic + seed + oracle all present | ✅ **oracle-equivalent** (`maxDiff=0c`) | live PG / captured `fact_acct` |
| **Data-blocked** | logic known, but seed doesn't exercise it (no oracle to diff) | 🟡 + named-deferred — **not** ✅ | none yet — say so, don't fake it |
| **Rule-consistent** | enacted path with no seed oracle (e.g. MProduction GL) | **rule-consistent** (vs proven sub-rules + balance + falsifier) — explicitly **not** `== iDempiere` | derived sub-rules |
| **Deleted-by-architecture** | ZK/JDBC/OSGi/app-server tier | 🔵 — **no port**, leaves the denominator | n/a |
| **On-demand corpus** | infinite/long-tail (454 SvrProcess) | mechanism ✅, corpus named-deferred | per-case when used |

Do not promote a data-blocked or rule-consistent gap to ✅. Honesty about the verdict *is* the deliverable.

## 3. Oracle protocol — how `maxDiff=0c` is established, by gap shape
- **Persisted output** (e.g. `fact_acct`): diff engine output vs the real stored rows, per (account, record, line, side),
  cents. The `W-FOLD-*` template.
- **Live declarative** (membership/verdict — val-rule, reference, access, callout, logic): diff engine vs **live PG**
  via `docker exec postgres psql -U adempiere -d idempiere` (schema `adempiere`). The `poc_*_harden.js` template.
- **Transient scratch table** (the `T_*` reports — `T_Aging`, `T_TrialBalance`, …): the table is per-run, *not* a stored
  oracle. Two valid oracles, in preference order: **(a)** trigger the real iDempiere process and capture its `T_*` rows
  (strongest); **(b)** if (a) is impractical, take iDempiere's **own source view** (e.g. `rv_openitem`, whose `DaysDue`
  iDempiere computes) as the SOURCE, and diff the engine's fold against an **independently-coded SQL bucketer over the
  same view** — the two-independent-paths pattern of `W-FOLD-QTYONHAND`. Non-tautological iff the two bucketers are
  genuinely independent. **Label which oracle was used; (b) is "grounded on iDempiere's source view," not "== T_Aging".**
- **No oracle in seed**: stop — verdict is rule-consistent or data-blocked (§2), never a synthesized number.

## 4. Witness template (one per gap)
1. `build/erp/<rule>.js` — the engine fold (pure verb; INTEGER CENTS / BigInt HALF_UP for money; no `Date.now`/`random`).
2. `scripts/poc_fold_<rule>.js` — drives the fold, pulls the oracle (live PG or capture), diffs, prints `§<RULE> …
   maxDiff=…` and a `§FALSIFIER` line; run via `bash build/erp/run_witness.sh scripts/poc_fold_<rule>.js`.
3. `build/erp/poc_<rule>.log` — the saved run; **READ it** (exit code ≠ evidence).
4. `docs/ERP_COVERAGE_MATRIX.md` — flip the row's verdict only after the log shows `maxDiff=0c` (or label rule-consistent).

## 5. Definition of Done (per gap)
✅ requires ALL of: spec section · engine fold · `poc_fold_<rule>.js` · `poc_<rule>.log` with `maxDiff=0c` vs a real
oracle · a §FALSIFIER that fires · the matrix row flipped. Missing any one → it is **not** done; report it as 🟡/⛔ with
the one blocking fact. Per-gap report: rule · LOC (engine+witness) · diff result · new falsifier · gap-count delta.

## 5a. Gap: `T_Aging` (AR/AP invoice aging) — W-AGING  [P1.1, closed 2026-06-13]
**Rule it proves:** `build/erp/report_aging.js` `foldAging()` reproduces iDempiere's `org.compiere.process.Aging`
+ `MAging.add()` bucketing — the full ~21-column set (`Due0, Due0_7, Due0_30, Due1_7, Due8_30, Due31_60,
Due31_Plus, Due61_90, Due61_Plus, Due91_Plus, DueAmt` + the `PastDue*` mirror set + `InvoicedAmt, OpenAmt`),
grouped per (IsSOTrx, C_BPartner, C_Currency) for the default (non-list-invoices) report.
**Source (non-invent):** `DaysDue` is iDempiere's own — read verbatim from `rv_openitem` (live PG). With
`StatementDate=today`, `m_statementOffset=0`, so `DaysDue = rv_openitem.DaysDue`. Bucket conditions ported
verbatim from `MAging.java:157-235`; bucket amount `amt = OpenAmt` for every bucket; `InvoicedAmt += GrandTotal`.
**Oracle (gap-shape = transient scratch, §3 option b):** grounded on iDempiere's source view `rv_openitem` —
diff the JS fold against an **independently-coded SQL `CASE` bucketer** over the SAME `rv_openitem`, per group,
all 21 cells, INTEGER CENTS → `maxDiff=0c`. Labelled "grounded on `rv_openitem`," **not** "== `T_Aging`".
**§FALSIFIER:** corrupt ONE engine bucket boundary so a real row crosses it (GardenWorld demo invoices are all
~8200-8900 days overdue, so a literal ±1-day shift cannot move a row — the falsifier shifts a `PastDue*_Plus`
threshold to a value the real `DaysDue` population straddles); the engine-vs-SQL diff must go non-zero.

## 5b. Gap: `T_InventoryValue` (inventory valuation) — W-INVVALUE  [P1.2, closed 2026-06-13]
**Rule it proves:** `build/erp/report_inventory_value.js` `foldInventoryValue()` reproduces the COST-VALUATION
core of `org.compiere.process.InventoryValue.doIt()` — seed rows from `M_Cost` (CostingMethod='S',
CostElementType='M') per active warehouse (sql1); `QtyOnHand = SUM(M_StorageOnHand.QtyOnHand)` via Locator→
Warehouse (sql6, ASI=0 path); valuation-date adjustment subtracting `M_Transaction` after DateValue (sql8);
`DELETE` rows with QtyOnHand=0/NULL (sql9); `CostStandardAmt = QtyOnHand * CostStandard` (dbeux, EXACT product).
**Scope (honest, bounded):** the inventory-VALUE columns `CostStandard, QtyOnHand, CostStandardAmt` — the lane's
named "cost × qtyOnHand". The price columns `PricePO/PriceList/PriceStd/PriceLimit` (+ `*Amt`) are PARAMETER-
driven (sql10 needs a user-chosen `M_PriceList_Version_ID`; default param=0 → NULL) and are **out of this
witness** — decoration on the value core, named not folded.
**Source (non-invent):** every value READ from a real `M_Cost`/`M_StorageOnHand`/`M_Locator`/`M_Warehouse` row.
DateValue=today over a historical seed ⇒ 0 future transactions ⇒ adjustment is 0 (verified in-witness, not
assumed). Money EXACT via BigDecimal (qty×cost is an un-rounded product, matching the SQL UPDATE).
**Oracle (transient scratch, §3 option b):** `T_InventoryValue` holds 0 captured rows → oracle = a HAND-WRITTEN
SQL re-derivation over the SAME base tables (joins + `SUM` + multiply in Postgres). The engine does the same
joins/aggregation/multiply in JS — two independent paths (the `W-FOLD-QTYONHAND` pattern). Labelled "grounded
on the base tables," **not** "== `T_InventoryValue`". 20 surviving rows, EXACT (cost, qty, value), `maxDiff=0`.
**§FALSIFIER:** perturb ONE surviving product's `CostStandard` by +1 → `CostStandardAmt` must shift by exactly
its `QtyOnHand` (proven: product 123 wh 103, 480→500, qty=20) → the value diff goes non-zero.

## 5c. Gap: `T_Replenish` (replenishment planning) — W-REPLENISH  [P1.2, closed 2026-06-13]
**Rule it proves:** `build/erp/report_replenish.js` `foldReplenish()` reproduces the PLANNING core of
`org.compiere.process.ReplenishReport` (`prepareTable` + `fillTable`) — the in-memory corrections (Level_Max≥
Level_Min; Order_Min/Pack≥1; IsCurrentVendor sole-vendor/multi-vendor), the two-phase candidate insert (current-
vendor PO join + the C_BPartner=0 fallback), `QtyOnHand`/`QtyReserved`/`QtyOrdered` rollups, the `QtyToOrder`
min/max formula (type'1' reorder-below-min, type'2' maintain-max), Min-order-qty, Pack rounding (`MOD`), and the
delete-`<1`. The document-creation actions (createPO/Requisition/Movements/DO) are the `ReplenishmentCreate`
ACTION, not the report — **out of scope**.
**Source (non-invent):** every value READ from a real `M_Replenish`/`M_Product_PO`/`M_StorageOnHand`/
`M_StorageReservation` row. EXACT BigDecimal (`MOD` via truncating integer-quotient, matching Postgres `MOD`).
Source-warehouse + custom-`ReplenishmentClass` branches are no-ops in this seed (asserted in-witness).
**Oracle (transient scratch, §3 option b):** `T_Replenish` holds 0 captured rows → oracle = a HAND-WRITTEN SQL
CTE re-derivation over the SAME base tables. The engine does the same in JS — two independent paths. The diff is
taken over the FULL pre-delete candidate set (so the type-1 branch, whose QtyToOrder is legitimately 0 when
stock is sufficient, is itself oracle-confirmed), then the post-delete survivor set is matched too. 18
candidates EXACT (`{1:10, 2:8}`), 8 survivors == the oracle. Labelled "grounded on the base tables," **not** "==
`T_Replenish`".
**§FALSIFIER:** bend ONE surviving product's `Level_Max` by +1000 → its `QtyToOrder` must diverge (proven:
product 127, 5→1005) → the diff goes non-zero.

## 5d. Gap: `T_InvoiceGL` (Invoice Not-realized Gain/Loss) — W-INVOICEGL  [P1.2, closed 2026-06-13]
**Rule it proves:** `build/erp/report_invoice_gl.js` `foldInvoiceGL()` reproduces the REPORT core of
`org.compiere.process.InvoiceNGL.doIt()` — the `INSERT…SELECT` that fills `T_InvoiceGL` plus the three post-
`UPDATE`s. Per open invoice (`IsPaid='N'`) joined to its `Fact_Acct` row (`AD_Table_ID=318`, `GrandTotal` matching
`AmtSourceDr` or `AmtSourceCr`) on an Asset/Liability account, for ONE acct schema: `AmtSourceBalance`/`AmtAcctBalance`,
the revaluation amounts `AmtRevalDr/Cr = currencyConvert(AmtSource…, invCcy, schemaCcy, DateReval, ConvTypeReval)`,
the reval diffs `AmtRevalDrDiff/CrDiff = AmtReval… − AmtAcct…`, `OpenAmt = invoiceOpen()`, and `Percent`
(`100` if `GrandTotal=OpenAmt` else `ROUND(OpenAmt*100/GrandTotal,6)`) with the `Percent≠100` proration of the four
amounts. The `createGLJournal()` document-creation step (writes `MJournal`/`MJournalLine`) is the ACTION, not the
report — **out of scope** (cf. ReplenishReport's createPO).
**Source (non-invent):** every value READ from a real `Fact_Acct`/`C_Invoice`/`C_Conversion_Rate`/`C_Currency` row.
The currency engine (`currencyRate` flexible lookup + `currencyRound` `ROUND(amt*rate, StdPrecision)` HALF_UP +
`currencyConvert`) is reimplemented in JS as a path INDEPENDENT of iDempiere's plpgsql. EMU/Euro fixed-rate branches +
allocation/credit-memo/payment-schedule corrections of `invoiceOpen` are NO-OPS in this seed (no currency has
`IsEMUMember='Y'`; open invoices carry no active allocations; all `Multiplier=1.0`) — **asserted in-witness, not
assumed**.
**Oracle (live iDempiere, §3 option a):** the ORACLE DB is `idempiere_test` (`fact_acct=300`; the default
`idempiere` config DB has `fact_acct=0`). The oracle re-runs the EXACT `InvoiceNGL` `INSERT…SELECT` logic in SQL
using iDempiere's OWN `currencyConvert()` + `invoiceOpen()` plpgsql functions + the diff/percent/proration. The engine
folds the SAME base rows in JS — two independent implementations of the currency math. `maxDiff=0c` over **108 cells**
(12 rows × 9 money cols) across both acct schemas; **6 cross-currency rows** make the revaluation actually exercised.
Labelled "== live iDempiere currency engine".
**§FALSIFIER:** bend the engine's looked-up conversion rate ×1.1 → `AmtRevalDr` on a cross-currency row must diverge
from the live oracle (proven: invoice 103, 136.95→150.65) → the diff goes non-zero.

## 5e. Gap: `T_DistributionRunDetail` (distribution planning) — W-DISTRUN  [P1.2, closed 2026-06-13 — hybrid]
**Rule it proves:** `build/erp/report_distribution_run.js` `foldDistributionRun()` reproduces the PLANNING core of
`org.compiere.process.DistributionRun` — `insertDetails()` (the ratio split) + the allocation rounding loop
(`addAllocations`/`isAllocationEqTotal`/`adjustAllocation` with `MDistributionRunDetail.round`/`getActualAllocation`/
`adjustQty` + `MDistributionRunLine.*`). The `createOrders()`/`distributionOrders()` document-creation steps are the
ACTION, not the report — **out of scope** (cf. ReplenishReport's createPO).
**This is a HYBRID closure — two distinct claims:**
- **(a) rawSplit `= ll.Ratio/l.RatioTotal*rl.TotalQty` is ORACLE-EQUIVALENT** — diffed against iDempiere's OWN
  `insertDetails` `INSERT…SELECT` formula run as a `SELECT` on the live `idempiere_test`, `maxDiff=0` over 3 rows
  (937.5/187.5/375).
- **(b) the allocation loop is RULE-CONSISTENT only** — it rounds rawSplit to UOM precision then iterates to force
  `Σ ActualAllocation == TotalQty` under the `MinQty` floors (here `MinQty=200` floors BP117's 187.5→200, forcing the
  *distribute-by-ratio* branch → final **929/200/371**). This loop is **procedural with no SQL/function oracle** in the
  live DB; a true row oracle would require RUNNING the process (app server, unavailable here). So the witness proves the
  loop's INVARIANTS — sum-exact, MinQty respected, deterministic/replay-stable, distribute-branch exercised — **not** a
  row diff. Per the Prime Directive ("NEVER hand-author an expected output") this is labelled rule-consistent, **not**
  oracle-equivalent — distinct from the `maxDiff=0c` members.
**Source (non-invent):** every value READ from a real `M_DistributionRun(Line)`/`M_DistributionList(Line)`/`C_UOM` row.
EXACT BigDecimal; `round`=`setScale(UOMprec, HALF_UP)`; distribute `diffRatio=ratio*diff/ratioTotal` divided at the
dividend's scale (`BigDecimal.divide(div, HALF_UP)`), matching the Java source.
**§FALSIFIER:** bend one list line's `Ratio` (+25) → its rawSplit must diverge from the live iDempiere formula (proven:
listLine 50000, 937.5→1071.43) → the oracle-backed diff goes non-zero.

## 5f. Gap: `T_CashFlow` (cash-flow report) — W-CASHFLOW  [P1.2, closed 2026-06-13 — hybrid]
**Rule it proves:** `build/erp/report_cashflow.js` reproduces the VALUE cores of `org.globalqss.process.CashFlow.doIt()`
— the four `CashFlowSource` feeds it materializes into `T_CashFlow`. `X_T_CashFlow.save()` row-writes are the
document-creation ACTION, not the report — **out of scope** (cf. ReplenishReport's createPO).
**This is a HYBRID closure — four distinct verdicts, one per feed:**
- **(1) `InitialBalance` ORACLE-EQUIVALENT** — `foldInitialBalance` = `SUM(acctBalance(Account_ID,AmtAcctDr,AmtAcctCr))`
  over `Fact_Acct(PostingType='A', DateAcct≤asOf)` per posting account == the live iDempiere `acctBalance()` plpgsql
  summed in SQL, `maxDiff=0c` over **21 accounts** (300 fact rows). `acctBalance` is reimplemented in JS as a path
  INDEPENDENT of the plpgsql (the natural-sign rule: Asset/Expense→Debit else Credit; Credit-sign flips `cr−dr`).
  **Both** sign branches exercised (15 debit-natural A/E + 6 credit-natural L/R accounts) ⇒ the sign rule is load-bearing.
- **(2) `CommitmentsOrders` ORACLE-EQUIVALENT** — `foldOrderCommitment` = `open = GrandTotal × (Σ((QtyOrdered−
  QtyInvoiced)·PriceActual)/TotalLines) − paid`, rounded to currency `StdPrecision`, sign-flipped for a PO ==
  an INDEPENDENT SQL re-derivation, EXACT (1 driving open order in seed — #106, `−2160.00`). Thin (one row) but real arithmetic.
- **(3) `ActualDebtInvoices` ORACLE-EQUIVALENT (thin)** — `foldActual` = `RV_OpenItem.OpenAmt` sign-flipped by `IsSOTrx`,
  gated `DueDate≤DateTo`, == SQL over the same view, EXACT over **7 rows**. A projection + sign flip → near-tautological,
  WEAK falsifier (the rule still bites: dropping the flip diverges the 4 PO rows).
- **(4) `Plan` — verified-EMPTY no-op (data-blocked).** `C_CashPlan`=0 / `C_CashPlanLine`=0 in seed ⇒ the Plan feed +
  the two subtract-from-plan UPDATEs + the delete-overplanned pass are all no-ops → asserted, **NOT folded, NOT synthesized**.
- **PROC — RULE-CONSISTENT.** The per-order `DueDate≤DateTo` insertion gate and the `MOrderPaySchedule` accumulation loop
  are procedural with no SQL/function oracle (a true row oracle needs an app-server run). Proven by INVARIANTS — every
  driving order passes the gate; the pay-schedule loop is a verified no-op (`IsPayScheduleValid='N'` for all driving orders).
**Source (non-invent):** every value READ from a real `Fact_Acct`/`C_Order`/`C_OrderLine`/`C_Payment`/`RV_OpenItem` row.
ORACLE DB = `idempiere_test` (`fact_acct=300`; default `idempiere` config DB has `fact_acct=0`). EXACT BigDecimal;
`DateTo`/`asOf` = fixed literal (no `Date.now`), far-future ⇒ admits the full seed population identically to `p_dateFrom=today`.
**§FALSIFIER (three, one per value-feed):** (1) flip the natural-sign rule → 19/21 account balances diverge (e.g. acct 419,
`7802.38 → −7802.38`); (2) bend order 106 `pending ×1.1` → `LineTotalAmt −2160.00 → −2376.00`; (3) drop the `IsSOTrx`
sign flip → 4 PO rows diverge. Each oracle-backed diff goes non-zero.

## 5g. Gap: `T_BankRegister` (bank register report) — W-BANKREGISTER  [P1.2, closed 2026-06-13 — thin/last `T_*`]
**Rule it proves:** `build/erp/report_bank_register.js` reproduces `org.compiere.report.BankRegister` —
`createBalanceLine()` + `createDetailLines()`. This is the LAST `T_*` member and is THIN / near-tautological (the
recon flagged it): its only real logic is the bank-account CONFIG-CHAIN join that SELECTS the right `Fact_Acct`
rows, plus a DISTINCT-vs-SUM multiplicity quirk; `Balance = AmtAcctDr − AmtAcctCr` merely passes through.
**Verdict: ORACLE-EQUIVALENT but THIN — load-bearing claim = the JOIN SELECTION, NOT arithmetic.**
The fold reproduces the literal join `Fact_Acct(AD_Table_ID=C_Payment) ⋈ C_Payment(CO/CL) ⋈ C_BankAccount ⋈ C_Bank
⋈ C_BankAccount_Acct ⋈ C_ValidCombination(vc.id = B_InTransit_Acct OR B_Asset_Acct) ⋈ C_ElementValue ⟕ C_BPartner`
`WHERE fa.Account_ID = vc.Account_ID`, then **createDetailLines** = `SELECT DISTINCT … DateAcct BETWEEN from/to`
and **createBalanceLine** = `SUM(Dr), SUM(Cr), SUM(Dr−Cr) … DateAcct < from`.
**The reproduced QUIRK (non-invent):** the `baa`/`vc` join MULTIPLIES each fact row (×2 in seed: 4 distinct rows →
8 join rows); the detail's `SELECT DISTINCT` collapses the duplicates (→ 4 rows) but the balance line's `SUM` does
NOT (→ counts all 8). Both matched against the live SQL — warts and all.
**Source (non-invent):** every value READ from a real `Fact_Acct`/`C_Payment`/`C_BankAccount(_Acct)`/
`C_ValidCombination` row. ORACLE DB = `idempiere_test`. Detail = 4 DISTINCT rows == live SQL; balance line (date >
all postings) = `Dr 549.46 / Cr 0.0 / Bal 549.46` over 8 join rows == live SQL. **`Cr=0` throughout the seed ⇒ the
`Dr−Cr` arithmetic is UNTESTED — NAMED, not claimed** (an honest limit of this thin report).
**§FALSIFIER (load-bearing despite thin arithmetic):** drop the `fa.Account_ID = vc.Account_ID` bank-account
selection → the join LEAKS rows (8 → 32, `Bal 549.46 → 0.00`); engine and SQL agree on the leaked superset, proving
the config-chain selection is the real load-bearing logic (a `Dr−Cr → Dr+Cr` falsifier would be vacuous here, `Cr=0`).
**With this the `T_*` tier is DRAINED** — `T_1099Extract` is the only remainder (0-row seed, n/a).

## 5h. Gap: cost-valued inventory GL (`Doc_InOut`) — W-FOLD-INOUTGL  [P2 item 3, closed 2026-06-13]
**Rule it proves:** `build/erp/report_inout_gl.js` `foldInOutGL()` reproduces the value core of
`org.compiere.acct.Doc_InOut` for the two movement polarities in the GardenWorld seed, **as a reusable ENGINE
VERB** (lifting the witness-local `deriveShipmentForOrder` of W-FOLD-COMPLETE into `build/erp/`, per
GAP_CLOSURE_LANE §6 "consume the engine, never fork it"). Accounts RESOLVED through the injected `deps.resolve`
(post_resolver — the `window.ERP` seam), never invented. **BOTH polarities ORACLE-EQUIVALENT — `maxDiff=0c`:**
- **C- (Customer Shipment, COGS).** `DR {Product.Cogs} / CR {Product.Asset}`, amount = the POSTED `Σ|M_CostDetail.amt|`
  per line (the cost iDempiere relieved inventory at); a non-costed line has no detail → no COGS posting (matches
  the oracle). `maxDiff=0c` vs `fact_acct(319)` (327.50, accounts 430/742).
- **V+ (Vendor Receipt).** `DR {Product.Asset} / CR {BPGroup.NotInvoicedReceipts}`, amount = `round(movementqty ×
  C_OrderLine.PriceActual, 2)` per line — the Not-Invoiced-Receipts accrual at the PURCHASE price (the SAME PO-price
  basis Doc_MatchInv uses for its NIR leg, W-FOLD-MATCHINV). `maxDiff=0c` vs `fact_acct(319)` (9209.00, accounts
  742/587), every line `oracle_dr == qty×PriceActual` exactly. *(The cost-selection rule — current m_cost — was the
  WRONG basis here: it gives 9161.10, a 47.90 drift; the stored receipts post at PO price, which is exact.)*
**Source (non-invent):** every value READ from a real `M_InOutLine`/`M_CostDetail`/`C_OrderLine` row.
ORACLE = `glassbowl_data.db` `fact_acct(319)` (client 11, schema 101 — iDempiere's own stored postings). Integer cents.
**§FALSIFIER (three):** (A) drop the C- COGS DR line → C- `maxDiff=32750c`; (B) bend `C_OrderLine.PriceActual` →
V+ `maxDiff≠0` (the PO-price basis is load-bearing); (C) swap DR/CR accounts → C- `maxDiff=32750c` (account
resolution load-bearing).

## 5i. Gap: analytic accounting (`{Project.Analytic}` → `fact_acct`) — DATA-BLOCKED  [P2 item 4, 2026-06-13]
**Verdict: 🟡 data-blocked, named-deferred (NOT ✅, NOT synthesized).** The analytic-accounting fold would resolve
a posting's analytic dimensions (`C_Project`, `C_Activity`, `C_Campaign`, `User1/User2` cost centres) into the
`fact_acct` row. But the GardenWorld seed has **NO analytic-tagged postings**: all 300 `fact_acct` rows in
`idempiere_test` carry `C_Project_ID=C_Activity_ID=C_Campaign_ID=User1_ID=User2_ID = 0` (2 unused `c_project`
rows, 0 `c_activity`, 0 `c_projectphase`); `glassbowl_data.db` `fact_acct` has no `c_project_id` column at all.
There is **no oracle to diff against** → per §2 (Data-blocked) and the Prime Directive ("no synthesized oracle"),
this stays 🟡 until a seed exercises a project-costed document. The token machinery (`post_resolver`) would extend
trivially (a `{Project.*}` token family) once such data exists — the BLOCKER is data, not logic.

## 6. Constraints (mirror of the lane card)
No porting of architecture-deleted tiers (🔵) · no pre-porting the 454 SvrProcess corpus (on-demand mechanism only) ·
no synthesized oracles · keep the `docs/ERP_BACKEND_SEPARATION.md` §0 seams (declaration / interpreter / log-fold never
merge). The objective is **per-deployment equivalence** — a tenant's *used* surface folds to the cent — not a 496-class
sweep. See [`MigrateComparisonPaper.md`](MigrateComparisonPaper.md) for the substrate-and-method (not feature-parity) frame.
