# ⚠ DO NOT REMOVE — GAP-CLOSURE LANE (operational backlog; resume card)
# Paste-to-start (NEW SESSION): `proceed with prompts/GAP_CLOSURE_LANE.md`
# Scope: close every ERP_COVERAGE_MATRIX gap (⛔ not-built / 🟡 partial) → ✅ oracle-equivalent, by EXECUTING
#   the already-PROVEN oracle-diff harness on each enumerated rule. This is execution, NOT research — the method
#   went through PoC (the poc_*_harden.js live-PG diff template + the W-FOLD-* family already ship green).
# Prime Directive: EXTRACT OR COMPILE ONLY — non-invent. Oracle outputs come from the LIVE iDempiere Postgres or
#   the iDempiere checkout; NEVER hand-author an expected output. A row is ✅ only by an oracle DIFF, never a claim.
# Log Mandate: every poc_* run → build/erp/poc_<rule>.log; READ the log before any conclusion (exit code ≠ evidence).
# PARENT CARD: prompts/HARDEN_MATRIX.md (the WHY + the MOrder-archetype denominator + the §0 separation seams).
#   This card is its concrete, prioritized BACKLOG. Read HARDEN_MATRIX §"READ FIRST" before starting.

## GOVERNS
- **Enumeration (the scoreboard):** `docs/ERP_COVERAGE_MATRIX.md` — every surface, with verdict ⛔/🟡/✅.
- **Method/spec:** `docs/GapClosureSpec.md` — the oracle protocol, the witness template, the §FALSIFIER law,
  the gap taxonomy, and the per-gap Definition-of-Done. **READ THE SPEC BEFORE WRITING A WITNESS.**
- **Denominator/discipline:** `prompts/HARDEN_MATRIX.md` + `docs/ERP_MODEL_ARCHETYPE.md`.

## ✅ VERIFIED RECON (2026-06-13 — do NOT re-derive; this de-risks T_Aging and the whole lane)
- **Live iDempiere oracle is REACHABLE** (this was the feared blocker — it is NOT blocked):
  `docker exec postgres psql -U adempiere -d idempiere -tAc "SET search_path=adempiere; SELECT … FROM rv_openitem"`.
  Container `postgres` (image postgres:15), DB `idempiere` (+ `idempiere_test`), role `adempiere`, schema `adempiere`.
  `rv_openitem` (iDempiere's own open-item view — **`DaysDue` is computed by iDempiere**) returns **7 rows** live.
  `rv_openitemtodate` is the as-of variant (Aging uses it when StatementDate≠today).
- **Proven harness to REUSE (copy, don't reinvent):** `scripts/poc_logic_harden.js` / `poc_*_harden.js` — they shell
  to the live PG via `cp.execFileSync('docker',['exec',PG.container,'psql','-U',PG.user,'-d',PG.db,'-t','-A','-F',US,'-c',sql])`
  and diff engine-output vs PG-output to `diff=0`. Run via `bash build/erp/run_witness.sh scripts/poc_<rule>.js`.
- **Aging source (read these for the EXACT algorithm — non-invent):**
  `~/idempiere-dev-setup/idempiere/org.adempiere.base.process/src/org/compiere/process/Aging.java` (the open-items
  driver: source = `RV_OpenItem` / `RV_OpenItemToDate`, `WHERE oi.IsSOTrx=…`, one pass, `aging.add(DueDate,DaysDue,
  GrandTotal,OpenAmt)`) + `…/org.adempiere.base/src/org/compiere/model/MAging.java` (`add()` lines ~131-224 = the
  bucketing).
- **⚠ CORRECTION — the paper's `foldAging` stub is INCOMPLETE.** Migrate&Compare's "~12 LOC, Due0/Due0_7/Due31_60"
  candidate has only 3 buckets **and a flipped `daysDue` sign**. Real `MAging` has ~15 columns: `Due0, Due1_7,
  Due8_30, Due31_60, Due61_90, Due91_Plus` + aggregates `Due0_7, Due0_30, Due31_Plus, Due61_Plus` + the `PastDue*`
  mirror set. Fold the FULL set; let `rv_openitem.DaysDue` (iDempiere's sign) settle the sign convention.
- **Seed population:** `build/erp/ad_full.db` c_invoice open (IsPaid='N') = **6** → **4 PO (IsSOTrx='N') / 2 SO ('Y')**.
  Aging runs per `IsSOTrx`, so expect two small oracle sets. (Live PG `rv_openitem`=7 is the richer source.)
- **Verified `T_*` inventory in the AD (grounds Priority-1 item 2 — 21 tables, NOT all are report-folds):**
  REPORT/FINANCIAL scratch (fold targets): `T_Aging · T_TrialBalance · T_CashFlow · T_InventoryValue · T_InvoiceGL
  (+T_InvoiceGL_v) · T_Reconciliation (+T_RV_Reconciliation) · T_BankRegister · T_Report · T_ReportStatement ·
  T_DistributionRunDetail · T_Replenish · T_1099Extract`  ≈ **13 fold targets** (matches the brief).
  NOT report-folds (exclude / different concern): `T_BOMLine · T_BOMLine_Costs · T_BOM_Indented` (BOM costing temp),
  `T_MRP_CRP` (planning), `T_Spool` (print spool), `T_Transaction` (txn temp), `T_Fact_Acct_History` (repost archive,
  already named in ERP_SOURCE_AUDIT_DELTAS A-2).

## PRIORITY ORDER (work top-down; one rule → spec → fold → witness → matrix-flip → report → next)
**P1 — Core accounting completeness**
1. **`T_Aging` (AR/AP aging) — START HERE.** `build/erp/report_aging.js` `foldAging()` over `rv_openitem` open items;
   witness `scripts/poc_fold_aging.js` → `build/erp/poc_fold_aging.log`, **W-AGING**. Oracle protocol = §Spec "transient
   scratch table" (below + GapClosureSpec): the source rows + `DaysDue` are iDempiere's (live PG); diff the JS fold's
   per-(BPGroup/IsSOTrx) bucket totals against an INDEPENDENT SQL `CASE`-bucketer over the SAME `rv_openitem` →
   `maxDiff=0c`. Fold ALL ~15 buckets. §FALSIFIER: shift one boundary by a day → diff≠0.
2. **Remaining ~12 `T_*` report folds** (list above) — each its own `fold*()` + `poc_fold_*.js` + `W-<NAME>`.
**P2 — Inventory & costing**
3. **Cost-valued inventory GL** — `MInOut` COGS/Inventory value via the cost-selection rule already proven in
   `W-FOLD-MATCHINV`/`W-FOLD-MOVEMENT`; integrate into `postRecipe()`. (Was the named §FOLD-DEFERRED.)
4. **Analytic accounting** (`C_Project`, cost centres) — `{Project.Analytic}` token resolution into `fact_acct`.
**P3 — Declarative tail**
5. **454 SvrProcess procs — ON-DEMAND ONLY.** Build the *mechanism* that picks the actually-used ones
   (`AD_Process ⋈ AD_Process_Access ⋈ activity`) and folds on demand. **Do NOT pre-port 454.**
6. **Remaining ~19 `Doc_*` posting manifests** (beyond the proven set) — each a token manifest + witness.
**P4 — Extraction gaps (Odoo live instance)** — master data (38 partners/30 products/47 COA/8 journals/2 taxes),
   loop all confirmed SOs (today 1/N), wire buy-side adapter (POs + vendor bills + 3-way match).

## CONSTRAINTS (non-negotiable — from the brief + Prime Directive)
- Do NOT port architecture-deleted tiers (ZK UI · JDBC · OSGi · app server) — the 🔵 band / 34% bucket.
- Do NOT pre-port the 454 SvrProcess corpus — on-demand mechanism only (mechanism ≠ corpus).
- Do NOT synthesize an oracle where seed data is absent — state **rule-consistent**, not oracle-equivalent
  (e.g. MProduction GL with no component-cost/offset-acct in seed). See GapClosureSpec §Gap taxonomy.
- Every ✅ carries: a `poc_fold_<rule>.js` witness, a `poc_<rule>.log` showing `maxDiff=0c` vs the live oracle,
  and a load-bearing **§FALSIFIER** (corrupt the rule → diff≠0). No log line / no falsifier = NOT done.
- Keep the §0 separation seams (declaration / interpreter / log-fold never merge) — `docs/ERP_BACKEND_SEPARATION.md`.
- **Consume the NEW framework, never fork it:** every fold is a `build/erp/` engine verb reached through the
  `window.ERP` seam (browser files are UMD copies of `bim-compiler/scripts`/`build/erp`) — follow the existing
  reporting-fold pattern (`foldStatement`/`foldPrint`), NOT a standalone lookalike script. A new `report_*.js`/
  `fold*()` is added to the engine and the witness drives it through the seam; if you find yourself
  re-implementing logic the engine already has, STOP and consume it. (Migration target = the Fold Engine
  substrate; a fold that bypasses it is drift, even if its diff=0.)

## PER-GAP OUTPUT (report after each closure)
Rule closed · LOC added (engine + witness) · diff result (`maxDiff=0c` | rule-consistent) · new §FALSIFIER ·
updated gap count (e.g. "⛔ remaining: 12 → 11") + the matrix row flipped in `docs/ERP_COVERAGE_MATRIX.md`.

## RESUME STATE (where the next session starts)
**▶ ALL PRIORITIES COMPLETE (2026-06-14). P1+P2+P3+P4 ALL CLOSED.**

**P3.5 ✅ DONE 2026-06-14 (W-PROC-PICKER).** `ad_process.js:pickUsedProcesses` — the on-demand picker:
`AD_Process ⋈ AD_Process_Access ⋈ AD_WF_Node` returns the actually-used subset. Engine (SQLite ad_full.db)
== live-oracle (PG idempiere): byAccess=451/451, byWorkflow=9, union=451 (all active processes carry access
grants in this seed). §FALSIFIER: role=99999 → byAccess=0. Corpus (337 classnames) stays named-deferred.
`scripts/poc_proc_picker.js` → `build/erp/poc_proc_picker.log` exit 0. Matrix SvrProcess row updated.

**P3.6 ✅ DONE 2026-06-14 (W-FOLD-BANKSTMT).** `build/erp/report_bank_statement.js:foldBankStatement` —
Doc_BankStatement.createFacts port (CMB doc type). 5 new tokens in `post_resolver.js`: `{Bank.Asset}` ·
`{Bank.InTransit}` (was already present) · `{Charge.Expense}` · `{Bank.InterestExp}` ·
`{AcctSchema.CurrencyBalance}`. Oracle: fact_acct(392) record=100, 13 rows, 2 schemas (101 USD / 200000 EUR).
Both schemas `maxDiff=0c`: schema 101 ΣDR=ΣCR=148.50; schema 200000 ΣDR=ΣCR=126.23 incl. 0.01 DR currency
balancing line (usecurrencybalancing='Y'). Currency conversion: ROUND(amt×0.85, 2) HALF_UP exact.
§FALSIFIER: stmtAmt+100 → maxDiff=10000c > 0. `scripts/poc_fold_bank_statement.js` →
`build/erp/poc_fold_bank_statement.log` exit 0. Matrix Posting row updated (Doc_Bank now folded; all 8
seeded Doc_* table types are oracle-equivalent or ruled n/a-in-seed).

**P4 ✅ DONE 2026-06-14.**

**P4.1 ✅ DONE 2026-06-14 (W-P4-MASTERS).** `gen_ad_odoo.js` extended: ALL 38 `res.partner` → C_BPartner · ALL 47 `account.account` → c_elementvalue · purchase tax → c_tax (alongside sale; total=2) · 8 `account.journal` logged (no shard table; architecture boundary = DocTypes). `poc_p4_masters.js` diffs shard vs live `search_count`: C_BPartner=38/38 · c_elementvalue=47/47 · c_tax=2/2 · journal=8 (logged only). S00023 regression: C_Order 1200001 folds `coverage:complete basis=invoice balanced=Y`. §FALSIFIER: 1-partner shard < live → gap detected. `build/erp/poc_p4_masters.log` exit 0. (SO loop already proven: `poc_odoo_full_pull.js` 27/27 orders fold complete.)

**P4.2 ✅ DONE 2026-06-14 (W-P4-BUYSIDE-LIVE).** `poc_p4_buyside_live.js` — live buy-side fold: P00011 chain pulled from RUNNING odoodemo (PO P00011 → WH/IN/00006 receipt → BILL/2026/06/0002 → 3 GL lines), folded through `buildBuyEvents` + `erp_engine.match`: §LIVE-STATIC totals == `odoo_oracle_p2p.json` · §BUY-FOLD 5/5 hops (4 events + match) committed · §MATCH 3-way matcher genuinely invoked (2 calls; receipt↔bill + PO↔bill pairs == nLines=1) · §GL-BALANCE `ΣDR==ΣCR=6596.40 maxDiff=0c` vs live AML · §NEW-VERBS `newVerbs=[]` · §FALSIFIER corrupt partner → 0 pairs. `build/erp/poc_p4_buyside_live.log` exit 0. Upgrades `poc_odoo_fold_3way.js` (static oracle) to live connection.

**P1.1 `T_Aging` ✅ DONE 2026-06-13 (W-AGING).** `build/erp/report_aging.js` `foldAging()` (port of `Aging.doIt`
+ `MAging.add`, all ~21 buckets, integer cents) == an independent SQL CASE bucketer over the live `rv_openitem`,
`maxDiff=0c` over 88 cells (4 groups × 22 money cols), DaysDue + DueDate(earliest) also match; §FALSIFIER fires
(bent `PastDue61_Plus` boundary 61→8718, delta=713897c). Spec §5a added to `docs/GapClosureSpec.md`; matrix
"PROVEN for 3 members" (was 2). Witness `scripts/poc_fold_aging.js`, log `build/erp/poc_fold_aging.log`.
- Note: oracle is GapClosureSpec §3 option (b) — "grounded on `rv_openitem`", NOT a triggered-process `T_Aging`
  capture. The SET-echo gotcha (a `SET search_path;` prefix pollutes psql `-t -A` output with a literal `SET`
  line) → pass schema via `PGOPTIONS='-c search_path=adempiere'` on `docker exec`, never an inline `SET`.

**P1.2 `T_InventoryValue` ✅ DONE 2026-06-13 (W-INVVALUE).** `build/erp/report_inventory_value.js`
`foldInventoryValue()` (cost-valuation core of `InventoryValue.doIt`) == an independent SQL re-derivation over
the base tables (M_Cost ⋈ wh ⋈ clientinfo ⋈ acctschema ⋈ costelement, QtyOnHand=SUM(storage⋈locator),
amt=qty×cost), EXACT over 20 surviving rows (CostStandard, QtyOnHand, CostStandardAmt). §FALSIFIER fires
(product 123 cost+1 → amt 480→500, delta=qty=20). Spec §5b; matrix "PROVEN for 4 members". Witness
`scripts/poc_fold_inventory_value.js`, log `build/erp/poc_fold_inventory_value.log`.
- Scope note: price columns (PricePO/List/Std/Limit + *Amt) are PARAMETER-driven (need M_PriceList_Version_ID)
  → named, not folded. DateValue=today ⇒ 0 future txn ⇒ adjustment 0 (verified, not assumed). EXACT BigDecimal
  product (compareTo, scale-insensitive) — not integer cents, because costs carry up to 6dp.

**P1.2 `T_Replenish` ✅ DONE 2026-06-13 (W-REPLENISH).** `build/erp/report_replenish.js` `foldReplenish()`
(prepareTable corrections + fillTable planning core of `ReplenishReport.java`) == an independent SQL CTE
re-derivation, EXACT over 18 candidates (`{1:10, 2:8}`) / 8 survivors (the real T_Replenish). createPO/Requisition/
Movements/DO are the ReplenishmentCreate ACTION → out of scope. §FALSIFIER fires (product 127 Level_Max+1000 →
QtyToOrder 5→1005). Spec §5c; matrix "PROVEN for 5 members". Witness `scripts/poc_fold_replenish.js`, log
`build/erp/poc_fold_replenish.log`.
- Technique note: diff the FULL pre-delete candidate set (keepZero) so the type-1 branch (QtyToOrder=0 when
  stock sufficient) is itself oracle-confirmed, THEN match the post-delete survivor set. MOD/pack-rounding via
  BigDecimal `a.subtract(a.divide(b,0,DOWN).multiply(b))` == Postgres `MOD`.

**P1.4 `T_InvoiceGL` ✅ DONE 2026-06-13 (W-INVOICEGL).** `build/erp/report_invoice_gl.js` `foldInvoiceGL()` (report
core of `InvoiceNGL.doIt` — the INSERT…SELECT + diff/percent/proration UPDATEs; createGLJournal action out of
scope) == the live iDempiere currency engine (`idempiere_test`, its OWN plpgsql `currencyConvert`/`invoiceOpen`
re-run in SQL), `maxDiff=0c` over 88… 108 cells (12 rows × 9 money cols, both acct schemas), 6 cross-currency rows
exercised. JS independently reimplements currencyRate(flexible)/currencyRound(ROUND HALF_UP)/currencyConvert +
reval diffs + OpenAmt + Percent proration. §FALSIFIER fires (bend rate ×1.1 → inv 103 AmtRevalDr 136.95→150.65,
diverged). Spec §5d; matrix "PROVEN for 6 members". Witness `scripts/poc_fold_invoice_gl.js`, log
`build/erp/poc_fold_invoice_gl.log`.
- Notes: ORACLE DB = `idempiere_test` (fact_acct=300; default `idempiere` has fact_acct=0). EMU/Euro fixed-rate
  branches no-op (no currency IsEMUMember='Y'); only ONE active conversion rate per pair (the 2003 rows are
  isactive='N') ⇒ reval rate == posting rate ⇒ all reval diffs net to 0 in seed — the fold is still proven cell-
  exact and the falsifier perturbs the JS rate. Proration no-op (all invoices fully open, OpenAmt=GrandTotal).
  Base `c_invoice` == post-DISTINCT `c_invoice_v` driving set (invoice 109's payment-schedule split collapses).

**P1.5 `T_DistributionRunDetail` ◐ HYBRID 2026-06-13 (W-DISTRUN).** `build/erp/report_distribution_run.js`
`foldDistributionRun()` (planning core of `DistributionRun` — insertDetails ratio split + the allocation rounding
loop; createOrders action out of scope). TWO claims: **(a) rawSplit `ll.Ratio/RatioTotal*TotalQty` ORACLE-EQUIVALENT**
== iDempiere's own insertDetails SQL `maxDiff=0` (937.5/187.5/375 over 3 rows); **(b) integer-allocation loop
RULE-CONSISTENT only** — sum-exact under MinQty floors (run-line MinQty=200 floors BP117 187.5→200 ⇒ distribute-by-
ratio branch ⇒ 929/200/371), proven by INVARIANTS (sum==TotalQty, MinQty respected, deterministic, distribute-branch
binding), NOT a row diff. The loop is procedural with **no SQL/function oracle** — a true row oracle needs a live
process run (T_DistributionRunDetail is 0-row until DistributionRun executes), so per the Prime Directive it is
labelled rule-consistent, not oracle-equivalent. §FALSIFIER fires (bend listLine 50000 Ratio+25 → rawQty
937.5→1071.43). Spec §5e; matrix hybrid ◐. Witness `scripts/poc_fold_distribution_run.js`, log
`build/erp/poc_fold_distribution_run.log`.
- `T_1099Extract` = **n/a in seed** (0 C_1099Box, 0 extract rows → no oracle; do NOT synthesize one).

**P1.6 `T_CashFlow` ◐ HYBRID 2026-06-13 (W-CASHFLOW).** `build/erp/report_cashflow.js` (value cores of
`org.globalqss.process.CashFlow.doIt` — the 4 CashFlowSource feeds; `X_T_CashFlow.save()` action out of scope).
Verdicts per feed: **(1) InitialBalance ORACLE-EQUIVALENT** — `foldInitialBalance` = `Σ acctBalance(acct,Dr,Cr)` over
`Fact_Acct(A)` == the live `acctBalance()` plpgsql summed in SQL, `maxDiff=0c` over 21 accounts (both sign branches:
15 debit-natural A/E + 6 credit-natural L/R); **(2) CommitmentsOrders ORACLE-EQUIVALENT** — `open=GrandTotal×pending
−paid` rounded to currency prec, sign-flipped for PO, == independent SQL, EXACT (1 driving order #106 = −2160.00);
**(3) ActualDebtInvoices ORACLE-EQUIVALENT (thin)** — `RV_OpenItem.OpenAmt` IsSOTrx sign-flip == SQL, EXACT over 7 rows
(projection → weak falsifier); **(4) Plan verified-EMPTY no-op** (cashplan=0/cashplanline=0 → feed + 2 subtract-UPDATEs
+ delete-overplanned all no-ops, asserted not folded); **PROC RULE-CONSISTENT** — due-date insertion gate + pay-schedule
loop (procedural, no row oracle; invariants: gate passes, pay-schedule a verified no-op IsPayScheduleValid='N').
§FALSIFIER ×3 fire: flip natural-sign rule → 19/21 accounts diverge (acct 419 7802.38→−7802.38) · bend order pending
×1.1 → −2160.00→−2376.00 · drop IsSOTrx flip → 4 PO rows. ORACLE DB=`idempiere_test`. Spec §5f; matrix "PROVEN for 6
members" + 2 HYBRID. Witness `scripts/poc_fold_cashflow.js`, log `build/erp/poc_fold_cashflow.log`.

**P1.7 `T_BankRegister` ✦ THIN 2026-06-13 (W-BANKREGISTER) — last `T_*` member.** `build/erp/report_bank_register.js`
(port of `org.compiere.report.BankRegister` createBalanceLine + createDetailLines). ORACLE-EQUIVALENT but THIN:
load-bearing claim = the bank-account CONFIG-CHAIN join SELECTION (`fact_acct(AD_Table_ID=C_Payment) ⋈ C_Payment(CO/CL)
⋈ C_BankAccount ⋈ C_Bank ⋈ C_BankAccount_Acct ⋈ vc(B_InTransit OR B_Asset) ⋈ ev ⟕ bp WHERE fa.Account_ID=vc.Account_ID`),
NOT arithmetic. Detail `SELECT DISTINCT`=4 rows == live SQL; balance-line `SUM`=Dr 549.46/Cr 0.0/Bal 549.46 over 8 join
rows == live SQL (the DISTINCT-detail vs SUM-balance MULTIPLICITY QUIRK reproduced: 4 distinct → 8 join rows). `Cr=0`
throughout seed ⇒ `Dr−Cr` arithmetic UNTESTED (named, not claimed). §FALSIFIER load-bearing despite thin math: drop
`fa.Account_ID=vc.Account_ID` → join leaks 8→32 (Bal 549.46→0.00), engine == SQL on the leaked superset. ORACLE DB =
`idempiere_test`. Spec §5g; matrix "+1 THIN". Witness `scripts/poc_fold_bank_register.js`, log `build/erp/poc_fold_bank_register.log`.

**SESSION TALLY 2026-06-13: 4 full folds (`maxDiff=0c`) — T_Aging (W-AGING) · T_InventoryValue (W-INVVALUE) ·
T_Replenish (W-REPLENISH) · T_InvoiceGL (W-INVOICEGL) — + 2 HYBRID — T_DistributionRunDetail (W-DISTRUN) ·
T_CashFlow (W-CASHFLOW, 3 of 4 feeds oracle-equivalent, Plan no-op, proc rule-consistent) — + 1 THIN — T_BankRegister
(W-BANKREGISTER, join-selection oracle-equiv, arithmetic named). ⇒ THE `T_*` REPORT TIER IS DRAINED (only T_1099Extract
n/a). NOTE the procedural-loop ceiling: any report whose value comes from an iterative Java loop (not SQL/a function/a
view) has NO live oracle here → it can only reach rule-consistent without an app-server run.**

**NEXT: P2 — cost-valued inventory GL (item 3). §2 CLASSIFICATION for the closed CashFlow/BankRegister kept below for
reference (do NOT re-derive):**
- **`T_CashFlow` recon DONE 2026-06-13 — now CLOSED as P1.6 above; kept for reference:** seed in `idempiere_test` = cashplan=0 /
  cashplanline=0 (the C_CashPlan SOURCE is EMPTY → that branch is a no-op) · open C_Order (CO/CL)=8 · rv_openitem=7 ·
  t_cashflow=0 (process never run). So CashFlow is a LARGE multi-source fold: (1) opening balance = `SUM(acctBalance(
  Account_ID,AmtAcctDr,AmtAcctCr)) FROM Fact_Acct WHERE DateAcct<=…` (iDempiere fn `acctBalance` = SQL oracle) ·
  (2) open-orders pending = `SUM((QtyOrdered-QtyInvoiced)*PriceActual)/TotalLines` with `paymentTermDueDate()` due
  dates + partial-payment netting (`SUM(CASE IsReceipt…PayAmt) FROM C_Payment CO/CL`) · (3) RV_OpenItem "actual"
  source (7 rows) · (4) over-planned DELETE pass. `CashFlow.java` lines: sqlIni 99, sqlPlan 125, sqlOpenOrders 191,
  sqlActual 309, deletes 359-412. Oracle-backable parts: acctBalance opening + order-pending arithmetic + RV_OpenItem
  (all have SQL/fn forms); the per-order due-date/netting LOOP is procedural (rule-consistent like W-DISTRUN's loop).
  Plan it as cashplan(no-op, assert) → opening-balance fold → order-pending fold → openitem fold, each its own §-claim.
- `T_BankRegister` (`org.compiere.report.BankRegister`) is fact_acct-driven too but flagged **thin/tautological**
  (a join+projection, weak falsifier) — prefer CashFlow's math-bearing parts first. Both
  and the default `idempiere` DB has **fact_acct=0** (it's the config DB; GL oracle = `idempiere_test`, 300
  rows: 8 are C_Payment, 6 `c_bankaccount_acct` config rows present). So the witness must point psql at
  `idempiere_test` (like `poc_pa_report.js` does) and verify the bank-acct config chain: `fact_acct.Account_ID`
  = the bank's `B_InTransit_Acct` OR `B_Asset_Acct` (`c_bankaccount_acct` → `c_validcombination` →
  `c_elementvalue`), joined `fact_acct ⋈ C_Payment(CO/CL) ⋈ C_BankAccount ⋈ C_Bank`.
- ⚠ BankRegister is **thin fold-math** (a join + projection: Balance line = Σ prior `AmtAcctDr/Cr`; detail
  lines pass through `AmtAcctDr-AmtAcctCr` per payment). Equivalence is near-tautological (JS-join == SQL-join,
  no transform) → its falsifier is weak. Prefer the math-bearing remainder first (e.g. `T_InvoiceGL`,
  `T_DistributionRunDetail`, `T_1099Extract` — check each has a real aggregation/derivation, not just a SELECT).
- Recipe unchanged: classify §2 (count the driving table FIRST) → spec § → `report_<name>.js` → `poc_fold_<name>.js`
  → log → matrix flip. Harness shape: **PGOPTIONS schema (never inline `SET`)**; for fact_acct-driven folds use
  `-d idempiere_test`; independent SQL oracle; BigDecimal-exact diff; a load-bearing falsifier a real value crosses.
