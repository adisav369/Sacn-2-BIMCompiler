# ⚠ DO NOT REMOVE — Scope guard
# Scope: add R(eport) as the 5th verb in the glassbowl CRUD-P ring — the iDempiere CORE REPORTING phase.
#        TWO pure-read folds: (1) a per-document RECEIPT, (2) a PA Financial Report (Trial Balance / P&L).
#        PURE READ — no T3 write-gate; ships on the proven op-log foundation. The report DEFINITION is AD
#        data (ad_printformat / PA_Report), rendered as definition-as-data — NOT a hardcoded layout.
# NON-NEGOTIABLE: Spec-first; witness-led (each test NAMES the issue it proves); §-log first (save the run,
#        READ the log before any conclusion); deterministic / non-invent (every number is a FOLD over rows,
#        never asserted). EXPLICIT GO before any deploy (Glassbowl-way; bump sw CACHE_VERSION).
# Read first: docs/ERP.md §0.17/§0.19 (contained-set, diff-oracle) · prompts/CRUD_OVERLAY.md (the ring + Process
#        seam) · build/erp/crud_overlay.js (op shape, the ring) · build/erp/glassbowl_data.db (what a Receipt
#        folds from) · build/erp/ad_full.db (fact_acct, ad_printformat, PA_Report — the Financial source + defs).

---

# CRUD-P-R — the Report verb (core reporting)

## Why now
The CRUD-P ring writes (Process = signed SET_STATUS, DONE). An ERP is read-heavy: Report is the *read face* of
the same op-log. In this model a report is just a fold — Receipt folds one document; the Financial Report folds
the journal. Both are PURE READ, so they ship on the foundation the falsifiers already proved. This is the
"core reporting ready" early phase before pills surface it and the BIM fold sources it.

## What exists (verified)
- `glassbowl_data.db` carries `c_order`, `c_orderline`, `m_product`, `c_bpartner`, `c_invoice(line)`, `c_payment`,
  `c_allocation*`, `m_inout(line)` → a **Receipt folds today**.
- `fact_acct` is NOT in the bundle (only in `ad_full.db`) → the **Financial Report needs fact_acct bundled**
  (copy GardenWorld's posted facts whole, same non-invent move as the 11 lifecycle tables).
- `ad_printformat` / `ad_printformatitem` present; `PA_Report*` is the financial-report definition (verify rows).

## Tasks (each names its witness; nothing deploys without GO)
### R1 — Receipt (per-document, folds from the bundle)
- `report_overlay.js`: a `▤ Report` verb in the ring → renders a receipt for the focused document by folding
  `c_order`+`c_orderline`(+`m_product`,`c_bpartner`) from the bundle. Honest "not carried" for non-bundle tables.
- **Witness:** `§REPORT-RECEIPT doc=C_Order#101 lines=N subtotal=… total=… folds-from=bundle handAuthored=0`
  (the rendered totals == a re-fold of the rows; no hardcoded numbers).

### R2 — PA Financial Report (Trial Balance / P&L, folds from fact_acct)
- Bundle `fact_acct` (+ the minimal account/period rows it needs) into `glassbowl_data.db` (non-invent — they are
  GardenWorld's executed facts). Fold a Trial Balance and a P&L.
- **Witness:** `§REPORT-FIN trial-balance Dr=… Cr=… balanced=Y maxDiff=0c folds-from=fact_acct rows=N` — folded
  balances reconcile to the `fact_acct` sums TO THE CENT (same discipline as the checkpoint POC).

### R3 — Definition-as-data (no report-writer code)
- Render R1/R2 through the AD definition: `ad_printformat`/`ad_printformatitem` for the Receipt, `PA_Report`
  structure for the Financial Report. The layout is DATA, not a literal template.
- **Witness:** `§REPORT-DEF receipt=ad_printformat#X fin=PA_Report#Y handAuthoredLayout=0` (rows drive the layout).

## Honest gap (name it, don't hide it)
Today Process writes only the docstatus, NOT journal facts. So R2 reflects **pre-posted GardenWorld facts**
("the books as loaded"), not an order you just Processed. Wiring Process→`Fact_Acct` (the `poc_showstopper`
fan-out) so a fresh order hits the P&L is **Phase 3-adjacent** (`BIM_ERP_FOLD.md`), not this prompt.

## Discipline
- §-log under `build/erp/`; READ before concluding. Pre-flight cite the spec. HANDS-OFF the live write-loop files
  except to ADD the read-only report verb. Deploy = Glassbowl-way, bump sw CACHE_VERSION, EXPLICIT GO, fetch-back-verify.
