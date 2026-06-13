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

## PER-GAP OUTPUT (report after each closure)
Rule closed · LOC added (engine + witness) · diff result (`maxDiff=0c` | rule-consistent) · new §FALSIFIER ·
updated gap count (e.g. "⛔ remaining: 12 → 11") + the matrix row flipped in `docs/ERP_COVERAGE_MATRIX.md`.

## RESUME STATE (where the next session starts)
Nothing folded yet this lane. **First action: implement `T_Aging` (P1.1) per the recon above + GapClosureSpec.**
The oracle is confirmed reachable; the algorithm files + full bucket set are named; the harness template is named.
No discovery is owed before T_Aging — go straight to spec → `report_aging.js` → `poc_fold_aging.js` → log → flip.
