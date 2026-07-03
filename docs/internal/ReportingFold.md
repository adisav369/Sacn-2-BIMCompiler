# Reporting as a Fold — standard iDempiere `PA_Report` + `AD_PrintFormat`, no Jasper

> **Spec status:** EXECUTED — both witnesses GREEN (2026-06-10/11): **W-PA-REPORT** `maxDiff=0c` for all 3 statements
> (BS 108 + IS 148 + **CF 140** segment cells, engine + bundle-alone paths) and **W-PRINTFORMAT** `maxDiff=0c`
> (8/8 seed invoices, 48 cells, 3 falsifiers). See §3 for the §-log lines, §4b/§4c for the as-built notes.
> **Branch:** `feat/erp-substrate-phase012` · 2026-06-10.
> **Thesis:** a report is a **deterministic fold of the journal**, defined by **standard iDempiere metadata** the engine already carries as data — interpreted on-the-fly in the browser. The Java `ReportEngine` + Jasper stack (~38K LOC) stays deleted; nothing is reimplemented, the existing AD definitions are *interpreted*, exactly as the engine already interprets `AD_Window`/`AD_Column`/`AD_Val_Rule`.

---

## 0 · Why this is interpretation, not reinvention (the non-invent rule)

iDempiere stores **both** reporting layers as metadata. We read them, we don't author a new format:

| Layer | Standard iDempiere metadata | In the seed (`ad_full.db`, verified) | Defines |
|---|---|---|---|
| **Financial statements** | `PA_Report` + `PA_ReportLineSet`→`PA_ReportLine` + `PA_ReportColumnSet`→`PA_ReportColumn` + `PA_ReportSource` (*Financial Report*, Window 216) | **3** reports · **113** lines · **17** columns · **93** sources · 3 line-sets · 3 col-sets | Balance Sheet, P&L, Cash Flows as data |
| **Document / report layout** | `AD_PrintFormat` + `AD_PrintFormatItem` | **93** formats · **2,780** items | how a document/report renders |

The 3 real GardenWorld statements (client 11) are the fold targets:

```
pa_report 100  Balance Sheet Current Month
pa_report 101  Income Statement Current Month
pa_report 102  Statement of Cash Flows
```

The journal they fold over is the **already-proven** `fact_acct` (300 rows, both schemas, `ΣDr=ΣCr=46574.97`, `maxDiff=0c` — `test_report_fin.js` §REPORT-FIN). The live oracle `idempiere_test` carries the identical `PA_*` rows (3/113/93), so a real Financial-Report run is diffable.

---

## 1 · The fold model

A financial statement is a **matrix** = (lines × columns) of summed journal amounts:

```
cell(line L, column C) = Σ  signed(fact.account, L)  × fact.amtacct
                         over fact_acct rows where
                           fact.account_id ∈ sources(L)        -- PA_ReportSource membership
                           AND fact.dateacct ∈ period(C)       -- PA_ReportColumn period/amount-type
                           AND fact.c_acctschema_id = schema
```

- **Lines** (`PA_ReportLine`, ordered by `SeqNo`) — a statement row. Two kinds (iDempiere `FinReport`): a **segment-value line** (sums the accounts named by its `PA_ReportSource` rows) or a **calculation line** (`Oper_1`/`Oper_2` + `CalculationType` ∈ {Add, Subtract, Range, Percent} over other lines' results). `PA_ReportSource` carries the element membership (Account, ranges, includes/excludes).
- **Columns** (`PA_ReportColumn`, ordered by `SeqNo`) — a period offset / amount-type (`AmountType` = period vs YTD vs balance; `ColumnType` = relative-period, budget-vs-actual). Degenerate first cut: one "current period" actual column.
- **Sign** — natural balance by account type (the `foldPnL`/`foldTrialBalance` rule already shipped): expense/asset DR-natural, revenue/liability CR-natural; sourced from the account element, never hard-typed.

This **generalises the two hardcoded folds** (`report_overlay.foldTrialBalance`, `foldPnL`) into ONE metadata-driven verb — the same "MOrder + deltas → one verb" compression applied to reporting: a new statement is **a `PA_Report` row, not a new function**.

### Pure verb (host-injected query, browser-portable — the engine pattern)
```
foldStatement(report, lineSet, colSet, sources, factRows, accounts) → cells[line][col]
```
No DB, no clock, no DOM in the verb; integer-cents via `bigdecimal.js` (proven `== java.math.BigDecimal`); the host injects `query()`/the `PA_*` rows. Same shape as `erp_engine` + the `poc_*` witnesses.

### Document layout — `AD_PrintFormat` (Master-Detail + the row engine)

**What iDempiere does** (verified in `org.adempiere.base/src/org/compiere/print/`): two server engines totalling **≈4.6K LOC** (before Jasper + the `layout/` subpackage):
- **`DataEngine`** (1,529 LOC) — generates SQL from the format and builds a `PrintData` tree. **Master-Detail** = a `PrintFormatType='P'` item (`DataEngine.java:322`, the *"Included print format"* of `:72`): it recursively `loadPrintData(pd, childFormat)` (`:252`) and nests the child query under each master row (an N+1). The **row engine** is `PrintDataGroup m_group` — *"Break & Column Functions"* (`:128-129`) — driven by `IsGroupBy` (SQL `GROUP BY`), `IsSummarized`/`IsAveraged`/`IsCounted` (sum/avg/count breaks), `SortNo` (sort), `IsPageBreak` (`:298-300`). Subtotals are computed **split-brain** — some in SQL `GROUP BY`, some in Java `PrintDataGroup` after the query.
- **`LayoutEngine`** (2,170 LOC) — paints the tree via Java2D / Jasper.

**What we do — one recursive fold replaces both:**
```
foldPrint(format, items, rowsOf) → manifest{ section, fields[], rows[ foldPrint(child, …) ], breaks[] }
```
- **Master-Detail = recursion.** A `'P'` item folds its child format per master row — the *same verb* as `explodeBOM`/`buildDoc` fan-out. iDempiere nests one level and special-cases multi-section; our recursion does **N levels free** (the seed's `Manufacturing_Order_Header → BOMLine → component`, `Workflow_Header → Node → Node_Product`).
- **The row engine = four composable pure reductions**, all in ONE pass (no SQL-vs-Java split-brain), integer-cents via `bigdecimal.js`:
  - `IsGroupBy` → **partition**(rows by key)
  - `IsSummarized` → **sum** · `IsAveraged` → **avg** · `IsCounted` → **count** (the break/subtotal rows)
  - `SortNo` → **comparator** · `IsPageBreak` → a manifest marker (DOM page-break, not pixels)
- `foldReceipt` (already dispatched via W-PROC for Order/Invoice print) is the degenerate one-master-one-detail case.

**The browser renders the manifest as DOM/CSS — this is the `ReportEngine`/Jasper replacement** (~4.6K + Jasper LOC deleted): layout is data, render is the browser, 0 round-trips.

**Honest boundary (it sets the witness):** we reproduce the **`PrintData` *data* tree + break subtotals to the cent** — **not** the Java2D *pixels*. Pixel-exact pagination is a deliberate non-goal (DOM/CSS is the better render); the meaningful equivalence is the row tree and the numbers, and those fold deterministically.

---

## 2 · Std vs. abstract-on-the-fly — the decision

**Both, no tension.** Use the *standard* `PA_Report`/`AD_PrintFormat` rows as the **definition** (non-invent — they are real AD data in the seed) and **fold them on-the-fly** at render time, browser-side, no server / no Jasper. Definition = data; behaviour = deterministic fold. This is precisely how the engine already treats every other AD surface.

---

## 3 · Witnesses (spec-first — name the issue each proves)

| Witness | Proves | Oracle | Falsifier | Verdict |
|---|---|---|---|---|
| **`W-PA-REPORT`** (`poc_pa_report.js`) | `foldStatement` reproduces iDempiere's Financial Report for `pa_report` 100/101/102 over real `fact_acct` to the cent | live `idempiere_test` Financial-Report run (or its stored line totals) — an **independent product**, like `fact_acct` | drop one `PA_ReportSource` → that line's cell diverges (`diff≠0`); flip a calc operator → statement diverges | **✅ GREEN** — `§PA-REPORT report=100 maxDiff=0c` (108 cells) · `101 maxDiff=0c` (148) · `102 maxDiff=0c` (140, `§PA-REPORT-NONVACUOUS CF-nonzero-cells=21`); `§PA-REPORT-FALSIFIER dropped leaf=508 → 148.35→0.35` fires. Bundle-alone twin `poc_statement_browser.js`: same 3 reports `maxDiff=0c` + calc-col cross-check. Logs: `build/erp/poc_pa_report.log` / `poc_statement_browser.log` |
| **`W-PRINTFORMAT`** (`poc_printformat.js`) | `foldPrint` reproduces iDempiere's `PrintData` **data tree + break subtotals** for a real Master-Detail format (e.g. `Invoice Header → Invoice LineTax`) to the cent — **NOT pixels** | the `PrintData` master/detail row tree + `IsSummarized`/`IsCounted` break totals, independently re-runnable from `idempiere_test` (an independent product) | drop a `'P'` child → detail section missing; mis-sum a break → subtotal diverges; flatten recursion → multi-level rows lost | **✅ GREEN** — `§PRINTFORMAT invoices=8 diffedCells=48 maxDiff=0c` (format 120→`'P'`→121, bundle-alone inputs; oracle = live base tables **+ the stored `c_invoice.grandtotal` real iDempiere wrote**); falsifiers A (drop `'P'` → children=0), B (+1c → Σ diverges 1c), C (drop link → whole-view leak) ALL fire. Log: `build/erp/poc_printformat.log` |

Both: deterministic, integer-cents, read-only, no `Date.now`/`Math.random`; `§`-logged; read the log (Log Mandate). `maxDiff=0c` = oracle-equivalent, and it must carry a **load-bearing §FALSIFIER** so a passing diff isn't a tautology.

---

## 4 · Coverage-matrix placement (currently untracked — name the gap)

Add two surfaces to `ERP_COVERAGE_MATRIX.md`:
- **`PA_Report` (Financial Report, W-216)** — 🟡 until `W-PA-REPORT` lands, then the statement fold joins the oracle-equivalent axis (it's a *fold vs independent product*, the strong class — not config read-back).
- **`AD_PrintFormat`** — 🟡 (document print folds via W-PROC today; the generic interpreter is the abstraction).

The matrix's existing "Trial-balance / posting-read ✅ oracle-equivalent" row (`test_report_fin.js`, `ΣDr=ΣCr=46574.97`) is the **already-proven base case** of `foldStatement` — `W-PA-REPORT` promotes it from hardcoded to `PA_Report`-driven.

## 4b · `foldStatement` — as built (mirror of the authoritative header block in `build/erp/report_overlay.js`)

The normative spec lives as the cited header comment above `foldStatement` in `build/erp/report_overlay.js` (it was
written there while this doc was edit-blocked; the engine header remains authoritative). Key semantics, mirrored:

- **Three passes, one integer-cents fold, HALF_UP only at output:** (1) S-lines × segment columns = Σ `amtExpr` over
  scoped facts; (2) C-lines in SeqNo order (`A`/`S`/`R`-range-by-SeqNo/`P`, == `FinReport.doCalculations`);
  (3) calc COLUMNS — `R` = **inclusive column-position range sum** (== `doCalculations:992-997`), not first-operand.
- **AmountType B/S/C/D/Q/R** == `MReportColumn.getSelectClause` verbatim; natural sign via `acctBalance` semantics
  (`accountsign='N'` → A/E debit-natural else credit-natural); a LINE's `paamounttype` overrides the column's.
- **Sources are tree-SUMMARY nodes** — each `pa_reportsource` account expands down the EV tree (`ad_treenode` 101)
  to non-summary leaves (== `MReportTree.getWhereClause`) or totals silently wrong.
- **Period windows** resolved host-side to `c_period_id` sets (`'P'`/`'Y'`/`'T'` == `FinReportPeriod`); the bundle's
  `fact_acct` has no `dateacct` — bridged losslessly via the 360-period calendar. Report period = latest posted
  period of the MOST-ACTIVE fiscal year (the data-driven stand-in for the mandatory C_Period parameter; the naive
  "latest fact period" folded the IS all-zero — the vacuity bug class the witness now guards with NON-VACUITY asserts).
- **Named-deferred (confirmed unused in live 100/101/102):** period type 'N', line-level period/posting overrides,
  `IsAllowOppositeSign`, `Factor`/RoundFactor, `GL_Budget_ID` filter, `IsPrinted` deletion + blank-line render.
- **Honest scope:** oracle-equivalent on segment columns × S-lines (BS+IS+CF, both metadata paths); calc lines/cols
  compose from verified cells per the correct operators + an independent calc-column cross-check in the witness.

## 4c · `foldPrint` — as built

One recursive pure verb (`build/erp/report_overlay.js`, cited `// Implementing ReportingFold.md §1`) replaces
`DataEngine`+`PrintDataGroup`; `LayoutEngine`/Jasper is replaced by `renderPrint` DOM. As-built notes:

- **Inputs are all data:** `ad_printformat` + `ad_printformatitem` + the **materialized print views**
  (`c_invoice_header_v`/`c_invoice_linetax_v`) extracted by `scripts/extract_printformat.sh` — the view SQL is
  evaluated **by Postgres at extract time**, never reimplemented (non-invent). `rowsOf(format, link)` is host-injected.
- **Semantics** (== `DataEngine`/`PrintDataGroup`): sort = `isorderby` items by `sortno`; fields = printed non-`'P'`
  items in SeqNo order; `'P'` = recursion per master row via the item's `columnname` link (N levels free); breaks =
  `isgroupby` partition + `issummarized` Σ (BigDecimal) / `iscounted` non-null count / `isaveraged` Σ/n HALF_UP —
  per group and grand, ONE pass, no SQL-vs-Java split-brain; `ispagebreak` = a manifest marker.
- **The strong anchor:** for the seed's invoice tree (lines + the view's `999998` separator + `999999` tax rows),
  the `IsSummarized(LineNetAmt)` grand break **== `c_invoice.grandtotal`** — a stored total written by real
  iDempiere (`MInvoice`), to the cent, all 8 invoices.
- **Exercised vs implemented:** the seed format exercises sort + `'P'` + `IsSummarized`; `IsGroupBy`/`IsCounted`/
  `IsAveraged` are implemented per `PrintDataGroup` but not yet exercised by a seed format — honestly noted, not claimed.
- **Browser wiring:** receipt panel gains a data-gated "⎙ iDempiere format" button (format resolved from
  `ad_printformat` by tablename, never hardcoded; `§PRINT` log); menu leaf **Trial Balance (502, action='R')** is now
  actionable via the proven `foldTrialBalance` (`§TB` log); **Statement of Accounts (350) stays dimmed** —
  no oracle-anchored fold yet (named-deferred). Intents: `overlay:print`, `global.__report.print/trialBalance`.

---

## 5 · Build order (after acceptance)

1. ✅ `foldStatement(PA_Report)` — the financial-statement half (BS + IS + **Cash Flows**, same engine).
2. ✅ `W-PA-REPORT` against `idempiere_test` → `maxDiff=0c` for all 3 statements (+ bundle-alone twin).
3. ✅ `foldPrint(AD_PrintFormat)` — the document/report layout half; `W-PRINTFORMAT` `maxDiff=0c`, 3 falsifiers.
4. ✅ Wire both into the browser report overlay (statement picker + AD_Menu launcher + `renderPrint` + Trial-Balance
   leaf); matrix rows updated to the witnessed state (🟡 ceiling holds — ✅ needs the live-UI bridge, parked).
   Residual: live in-browser **visual** confirm runs in the bim-ootb Playwright suite (no headless browser here);
   Statement of Accounts (350) named-deferred; the other 13 `T_*` members each await their own witness.

*Reporting becomes one more fold over the op-log: lose the report, keep the journal + the `PA_Report` definition, refold it exactly — server-less, Jasper-free, definition-as-data.*
