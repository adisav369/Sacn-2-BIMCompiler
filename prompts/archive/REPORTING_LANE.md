# ⚠ DO NOT REMOVE — Reporting Lane (look at iDempiere reporting AS A WHOLE, then fold it)
# SCOPE: own the REPORTING surface end-to-end — financial statements (`PA_Report`) + document/report layout
#   (`AD_PrintFormat`), folded deterministically from standard iDempiere metadata over the proven journal, rendered
#   in the browser. NO Jasper, NO server ReportEngine. The spec already exists — `docs/ReportingFold.md` — this lane
#   EXECUTES it (spec → witness → engine → matrix), and may refine the spec as evidence demands.
# NON-NEGOTIABLE (project Standing Rules):
#   - EXTRACT / NON-INVENT. Every account/line/source/format is READ from a real `PA_*`/`AD_PrintFormat` row or a
#     real `fact_acct` row; never synthesized. A definition absent in the seed is named-deferred, never faked.
#   - DETERMINISTIC, INTEGER-CENTS. Money/qty via `build/erp/bigdecimal.js` (proven == java.math.BigDecimal) or
#     BigInt cents — never raw JS `Number` for a posted/summed value. No `Date.now`/`Math.random` in any probe.
#   - LOG MANDATE. Every witness `tee`s to `build/erp/<name>.log`; READ the log before concluding — exit 0 is not
#     evidence. Each witness names the issue it proves and carries a LOAD-BEARING `§FALSIFIER`.
#   - SPEC-FIRST. No engine code before the witness + the spec section it implements are written. Cite the spec:
#     `// Implementing ReportingFold.md §X — Witness: W-…`.
#   - NINJAEXCEL = SEPARATE SESSION (do not touch here). `internal/NinjaExcel.md` (Excel-as-report-compiler) is now
#     assigned to its OWN lane/session. OUT OF SCOPE for this lane — do NOT reference, borrow from, or merge it into
#     PA_Report/AD_PrintFormat or the matrix. This lane focuses ONLY on the mainstream iDempiere fold + its integration.
#   Honour until the # DONE appendix gives every witness a verdict with a `§`-log line.

---

## 0 · THE GOAL (one sentence)
Make a **report a deterministic fold of the journal, driven by the standard iDempiere `PA_Report`/`AD_PrintFormat`
metadata the engine already carries as data** — so a financial statement (Balance Sheet / P&L / Cash Flows) and a
master-detail document print both reproduce iDempiere's output **to the cent**, rendered in the browser, Jasper deleted.

## 1 · WHAT ALREADY EXISTS (read these first — do not re-derive)
- **Spec:** `docs/ReportingFold.md` — the two verbs (`foldStatement`, `foldPrint`), the std-vs-abstract decision
  (both: std metadata = definition, on-the-fly fold = behaviour), the witnesses, the honesty boundary.
- **Proven base case (the anchor):** `scripts/test_report_fin.js` → `§REPORT-FIN` — the Trial Balance is a fold of
  the real `fact_acct` that balances to the cent (`ΣDr=ΣCr=46574.97`, `maxDiff=0c`), reconciled to an independent
  SQLite SUM. `report_overlay.js` already has `foldTrialBalance` / `foldPnL` / `foldReceipt` (PURE, host-injected).
- **The journal:** `build/erp/glassbowl_data.db` `fact_acct` (300 rows, 2 schemas, balanced 46574.97) — the SAME
  oracle the FOLD lane diffs to the cent.
- **The metadata (real, in `build/erp/ad_full.db`, mirrored in live `idempiere_test`):**
  `PA_Report` **3** = *100 Balance Sheet · 101 Income Statement · 102 Statement of Cash Flows* · `PA_ReportLine`
  **113** · `PA_ReportColumn` **17** · `PA_ReportSource` **93** · `AD_PrintFormat` **93** · `AD_PrintFormatItem`
  **2780** (Field 2711 / Text 28 / **`'P'`=27 master-detail** / Image 13; group-by 10 · sorted 114 · summarized 58).
- **The iDempiere engines you are folding** (`~/idempiere-dev-setup/idempiere/org.adempiere.base/src/org/compiere/`):
  `report.FinReport`/`FinStatement` (financial) · `print.DataEngine` (1529 LOC, builds the `PrintData` tree; `'P'`
  = the *Included print format* master-detail; `PrintDataGroup` = the break/function row engine) + `print.layout.
  LayoutEngine` (2170 LOC, Java2D paint — we replace with DOM).
- **Matrix surfaces (already added, status 🟡):** `ERP_COVERAGE_MATRIX.md` §B `PA_Report` + `AD_PrintFormat`.
- **Paper gap (already named):** `MigrateComparisonPaper.md` GAPS #8 — "do not claim reporting parity until
  W-PA-REPORT / W-PRINTFORMAT land `maxDiff=0c`."
- **Engine pattern to imitate:** `scripts/erp_engine.js` (pure verbs) + `scripts/post_resolver.js` (host glue) + any
  `scripts/poc_fold_*.js` witness (derive → oracle-diff → §FALSIFIER). The fold engine quality bar: `docs/FoldEngineQuality.md`.

## 2 · THE HONEST GAPS THIS LANE CLOSES (what is NOT done yet)
1. **`foldPnL` has no `fact_acct`-anchored witness** — it exists but is unproven. Anchor it.
2. **Statements are HARDCODED, not `PA_Report`-driven** — the engine does not yet read `PA_ReportLine`/`Source`/
   `Column` to fold an arbitrary statement. Build the generic `foldStatement`.
3. **`AD_PrintFormat` is not interpreted** — only `foldReceipt` (one fixed shape, via W-PROC) exists; the generic
   recursive `foldPrint` (master-detail `'P'` + the row engine) is unbuilt.

## 3 · BUILD ORDER (spec-first, witness-first, one bounded step at a time)
> **STATUS (2026-06-10):** Step 1 **✅ DONE** — `foldStatement` built in `build/erp/report_overlay.js` (3-pass: S-lines × cols →
> C-line calc tree → calc columns, BigDecimal, single HALF_UP at output). `W-PA-REPORT` GREEN: `pa_report` **100 Balance
> Sheet maxDiff=0c (108 cells)** + **101 Income Statement maxDiff=0c (148 cells)** vs an INDEPENDENT live `idempiere_test`
> re-derivation; §FALSIFIER fires (drop leaf 508 → cell 148.35→0.35); `§REPORT-FIN` base case unregressed. Key findings
> baked in: (a) `pa_reportsource` accounts are **tree-SUMMARY nodes** — fold MUST expand each source down the EV account
> tree to its leaves (migrated `MReportTree.getWhereClause`), else totals silently wrong; (b) `c_elementvalue.accountsign`
> IS present (all 379 = `'N'`) — natural-sign extracted, not assumed; (c) seed gap = no `dateacct`, bridged losslessly via
> `c_period_id` sets over the 360-period calendar. **102 Cash Flows folds without error** (53 lines) — its oracle-diff is
> the only foldStatement remainder. NOT committed/deployed; spec lives as the cited header block in `report_overlay.js`
> (mirror to `docs/ReportingFold.md §4b` when doc-edit unblocked).
>
> **STEP 3 (browser integration) ✅ DONE for BS+IS (2026-06-10):** `foldStatement` is now wired into the browser report
> overlay and proven cent-exact from the SERVED bundle alone. Three pieces landed:
> 1. **DATA GATE closed (reproducibly).** New `scripts/extract_pa_report.sh` (companion to `extract_fact_acct.sh`) pulls the
>    Financial-Report DEFINITION (`pa_report`/`line`/`column`/`source`, `c_period`/`c_year`, `ad_treenode` tree 101) + backfills
>    `c_elementvalue.accountsign` into `build/erp/glassbowl_data.db` from LIVE `idempiere_test`. Bundle regen recipe =
>    `extract_fact_acct.sh && extract_pa_report.sh`. (The served bundle had `fact_acct` but NOT the PA_* metadata — that was the
>    only blocker; the verb was already browser-safe.) NOTE: `glassbowl_data.db` + `ad_full.db` are untracked artifacts a
>    CONCURRENT terminal regenerates — sourcing from postgres (not `ad_full.db`) makes the extractor immune to that.
> 2. **ENGINE/UI wired.** `report_overlay.js` gains the host path `statementInputs(db,reportId)` (replicates the witness prep
>    against the bundle) + `statement(reportId)` + `renderStatement` (lines×cols matrix) + a **Financials picker bar** in the
>    report panel (tabs per `pa_report`), an `overlay:statement` intent, and `global.__report.statement(id)`.
> 3. **WITNESSED.** `scripts/poc_statement_browser.js` → `build/erp/poc_statement_browser.log` (exit 0): folds **100 BS (108 cells)
>    + 101 IS (148 cells) maxDiff=0c** reading EVERY input from `glassbowl_data.db` ALONE vs the independent live oracle;
>    `§PA-BROWSER-FALSIFIER dropped leaf 508 → 148.35→0.35` fires. This is the strong proof the in-app path == iDempiere.
>
> **iDempiere-CONFORMANCE AUDIT (2026-06-10) — "what qualifies":** audited `foldStatement` against the real engine
> (`FinReport.java` / `MReportTree.java` / `MReportColumn.java` / `FinReportPeriod.java`). VERIFIED correct & exercised by the
> seed: amount SELECT per AmountType B/S/C/D/Q/R (== `MReportColumn.getSelectClause`, incl. Q=raw `Qty` vs R=`acctBalance(Qty,0)`),
> natural-sign via `acctBalance` (oracle-diffed), summary→**non-summary-leaf** tree expansion (== `MReportTree.getWhereClause`),
> posting-type filter (only-if-set, NO 'A' default == `insertLine`), schema scope (acctschema-only, no AD_Client/GAAP == `MReport.getWhereClause`),
> line-AmountType-overrides-column, calc LINES A/S/R(getLineIDs range)/P. **FIXED a real divergence:** calc-COLUMN
> `calculationtype='R'` (report 101 col 50 "Budget Spent", cols 20→40) was folded as first-operand; now the inclusive
> column-position range sum (== `doCalculations` 992-997). **FIXED a vacuity defect:** default report period was "latest fact
> period" → landed in GardenWorld's emptiest fiscal year (8/300 rows) → Income Statement folded to ALL ZEROS (so its
> "maxDiff=0c" was a 0==0 tie, AND the app would render an empty IS). Now the report period = latest posted period of the
> MOST-ACTIVE fiscal year (data-driven stand-in for iDempiere's mandatory C_Period parameter); IS is now non-vacuous.
> Witness hardened with NON-VACUITY assertions + an INDEPENDENT calc-column cross-check (the guard that catches the R-bug
> class). NAME-DEFERRED (confirmed UNUSED in live 100/101/102 — not faked, not needed): period type **'N' Natural**, line-level
> period/posting overrides (0 lines set them; `pa_reportline` has no postingtype col), `IsAllowOppositeSign`, `Factor`/RoundFactor
> scaling, `GL_Budget_ID` filter (Budget col has none), `IsPrinted` line-deletion + blank-line('B') rendering (cosmetic).
> **EQUIVALENCE SCOPE (honest):** oracle-equivalent (cent-exact vs live `idempiere_test`) on **segment columns × S-lines** of
> BS+IS; calc lines/columns are NOT independently oracle-diffed (no stored `T_Report` oracle) but compose from the verified
> segment cells per the now-correct iDempiere operators + the witness cross-check. Re-witnessed: `build/erp/poc_statement_browser.log`
> (BS 108 + IS 148 cells 0c, BS 36 / IS 6 nonzero, calc-col R 0-mismatch, §FALSIFIER fires). **iDempiere MENU launcher ✅ DONE (2026-06-10) — "follow iDempiere, called from the menu":** the reports are now
> reached the iDempiere way — a "▤ Reports" pill opens `report_overlay.openMenu`, which renders the REAL AD_Menu branch
> **Performance Analysis and Accounting (278) → Financial Reporting (280) → Financial Report (281, a Window over PA_Report)**,
> data-driven from the bundle's `ad_menu`/`ad_treenodemm`/`ad_menu_table` (added to `extract_pa_report.sh` from live PG); the
> Financial Report node lists the PA_Report records, selecting one runs the fold (== iDempiere's "Create Report"). **Witness
> `scripts/poc_pa_menu.js` → `build/erp/poc_pa_menu.log` (W-PA-MENU, exit 0):** the path 278→280→281 (names/actions/parents),
> the 8 Financial-Reporting children (set+seqno order+names+actions), Financial-Report→PA_Report, and the 3 reachable reports
> ALL == live `idempiere_test` AD_Menu; §FALSIFIER (tampered node name) fires. **NEXT FOCUS:** (a) close 102 Cash Flows oracle-diff; (b) wire the other foldable menu leaves (Trial Balance 502 / Statement of Accounts 350 — folds exist)
> (intent/global exist — only a menu button is missing; the panel picker covers in-panel switching); (c) live in-browser visual
> confirm (no headless browser in this repo — runs in the bim-ootb Playwright suite); (d) Step 2 `foldPrint`. NOT committed/deployed.
1. **`foldStatement(PA_Report)` — financial statements** (highest value). Read `PA_ReportLineSet→Line`(+`Source`
   membership) × `PA_ReportColumnSet→Column` and fold over `fact_acct`, signed by account natural-balance.
   - **`W-PA-REPORT`** (`scripts/poc_pa_report.js` → `build/erp/poc_pa_report.log`): fold `pa_report` 100/101/102
     and diff vs the **independent** oracle — the live `idempiere_test` Financial-Report run (or its stored line
     totals). Target `maxDiff=0c`. §FALSIFIER: drop a `PA_ReportSource` → that line's cell diverges; flip a
     calculation operator → statement diverges. Start with Balance Sheet + P&L (Cash Flows = same engine, confirm its
     source→cashflow mapping is foldable from the same metadata; if not, name-defer that one honestly).
2. **`foldPrint(AD_PrintFormat)` — document/report layout** (the master-detail + row engine). **← THIS is the
   INDIVIDUAL-REPORT lane (resume here in a NEW session).** The general/financial report (PA_Report → Financial Report
   window → `foldStatement`) is DONE incl. the iDempiere AD_Menu launcher (W-PA-MENU). The INDIVIDUAL reports are the
   `action='R'` menu leaves — already rendered (dimmed) by `report_overlay.openMenu` under *Financial Reporting*
   (e.g. **350 Statement of Accounts**, **502 Trial Balance**), plus document prints (Invoice/Order). Each runs a
   specific report (`AD_Process` isReport → `AD_ReportView` SQL → `AD_PrintFormat` layout). `foldPrint` is the layout
   engine that makes those leaves ACTIONABLE (the menu wiring already exists — an actionable leaf just calls a
   `foldPrint`/report verb instead of being dimmed). Note `foldTrialBalance`/`foldReceipt` folds already exist and can
   back the simplest leaves first.
   - Recursive verb over `'P'` children (same shape as `explodeBOM`/`buildDoc` fan-out — N-level free); the row
     engine = composable pure reductions: `IsGroupBy`→partition, `IsSummarized`→sum, `IsAveraged`→avg,
     `IsCounted`→count, `SortNo`→comparator, `IsPageBreak`→a DOM marker. All in ONE integer-cents pass.
   - **`W-PRINTFORMAT`** (`scripts/poc_printformat.js` → `build/erp/poc_printformat.log`): reproduce iDempiere's
     **`PrintData` row-tree + break subtotals** for a real master-detail format (e.g. `Invoice Header → Invoice
     LineTax`) — **NOT pixels** (pixel-exact pagination is a stated non-goal; DOM/CSS is the render). Oracle = the
     `PrintData` tree + `IsSummarized`/`IsCounted` totals, re-runnable from `idempiere_test`. §FALSIFIER: drop a
     `'P'` child → detail missing; mis-sum a break → subtotal diverges; flatten recursion → multi-level rows lost.
3. **Wire into the browser report overlay** — render the manifest as DOM; flip the matrix surfaces 🟡→ as witnessed.

## 4 · DELIVERABLES + WHERE THEY GO
- Engine: new pure verbs in `build/erp/` (source of truth) — `foldStatement`, `foldPrint` (extend `report_overlay.js`
  or a sibling, keeping the host-injected-`query` purity so it runs under `sql.js` in the browser unchanged).
- Witnesses: `scripts/poc_pa_report.js`, `scripts/poc_printformat.js` → `build/erp/poc_*.log` (exit 0, §FALSIFIER fires).
- Docs (update as evidence lands, do NOT inflate ahead of a green witness):
  - `docs/ReportingFold.md` — mark each §/witness DONE with its `§`-log line.
  - `ERP_COVERAGE_MATRIX.md` — flip `PA_Report`/`AD_PrintFormat` from "spec'd/pending" toward the equivalence axis ONLY
    once `maxDiff=0c` is witnessed (a *fold-vs-independent-product* claim — the strong class, like the existing
    `fact_acct` rows; never the weaker config-read-back tier — see the audit's F-TIER-1 in `build/erp/AUDIT_EQUIVALENCE.md`).
  - `MigrateComparisonPaper.md` GAP #8 — narrow/close it as witnesses land.
- Do NOT deploy (mkdocs publish is another session's job) unless explicitly told.

## 5 · ORACLE ACCESS (how to diff independently)
- Live iDempiere Postgres: `docker exec postgres psql -U adempiere -d idempiere_test -t -A -c "<sql>"`. **`idempiere_test`
  is the posted-GL source of truth** (client 11 `fact_acct` = 300 rows / 46574.97); the `idempiere` DB has the AD
  dictionary but **0 `fact_acct`** — do NOT point a financial diff at it (a prior audit mistake — see AUDIT_EQUIVALENCE §4).
- For a Financial-Report oracle: run the iDempiere report (or read `PA_*` + sum `fact_acct` the way `FinReport` does)
  and capture the line totals; diff the fold against that. Quote the query in the witness log.

## 6 · STOP CONDITION
`W-PA-REPORT` and `W-PRINTFORMAT` each have a verdict backed by a re-run `§`-log line (`maxDiff=0c` or an honestly
named residual + a load-bearing §FALSIFIER); `ReportingFold.md` / matrix / GAP #8 updated to match the evidence (no
claim ahead of a green witness). Append a `# DONE` block: per-witness verdict +
`§`-line + the one residual each (if any). If a statement (likely Cash Flows) needs a fact that can't be EXTRACTED,
record `⛔ BLOCKED: <the one question>` and move on — never fake it.

---

# DONE (2026-06-10) — partial: first witness landed, lane refocused for a SEPARATE SESSION to resume

**✅ W-PA-REPORT — `foldStatement` oracle-equivalent for the two financial statements.**
- `§PA-REPORT report=100 "Balance Sheet" maxDiff=0c` (108 cells) · `report=101 "Income Statement" maxDiff=0c` (148 cells)
  vs an INDEPENDENT live `idempiere_test` re-derivation. `§PA-REPORT-FALSIFIER dropped leaf 508 → 148.35→0.35` fires.
  `§REPORT-FIN` base case still green. Log: `build/erp/poc_pa_report.log` (exit 0, deterministic).
- Engine: `build/erp/report_overlay.js` → new pure verb `foldStatement` (3-pass S-lines×cols → C-line calc tree → calc
  cols; BigDecimal; single HALF_UP at output). Witness: `scripts/poc_pa_report.js`. **UNCOMMITTED.**
- Load-bearing findings baked in: (a) `pa_reportsource` accounts are tree-**SUMMARY** nodes → fold MUST expand each
  source down the EV account tree to leaves (`MReportTree.getWhereClause`) or totals silently wrong; (b)
  `c_elementvalue.accountsign` IS present (all 379 = `'N'`) — natural-sign extracted not assumed; (c) seed gap = no
  `dateacct` → bridged losslessly via `c_period_id` sets over the 360-period calendar.

**Residuals / NEXT (resume here, in order):**
1. **102 Cash Flows oracle-diff** — it already folds through `foldStatement` (53 lines) WITHOUT error; only its
   independent oracle-diff is unrun. NOT blocked. Close it first.
2. **`foldPrint(AD_PrintFormat)` + W-PRINTFORMAT** — the `'P'` master-detail recursion (`explodeBOM`-shaped) + the
   row-engine reductions. Migration map for it is in the `report_overlay.js` header comment (study session output).
3. **Browser integration** — wire the manifests into the report overlay (the lane's integration goal).
4. **Doc mirror** — the `foldStatement` spec currently lives as a cited header comment in `report_overlay.js`; mirror it
   to `docs/ReportingFold.md §4b` (doc-edit now works). The `T_*` reporting-tier gap is already indexed in
   `docs/ERP_COVERAGE_MATRIX.md` (§B row + GAP item 13) + `docs/MigrateComparisonPaper.md §temp-tables / §gap-in-code`
   (the `T_Report`→`foldStatement` ✓ + the `T_Aging` fold-gap side-by-side). Flip the matrix `PA_Report` row toward the
   equivalence axis now that BS+IS are witnessed.
**NinjaExcel stays SEPARATE** — `internal/NinjaExcelAdaptation.md` (its own session); do not fold it in here.

---

# DONE (2026-06-11, session 4 — "single doc printformat") — Cash Flows closed + foldPrint/W-PRINTFORMAT landed + browser wired

**✅ 102 Statement of Cash Flows oracle-diff CLOSED (the last foldStatement remainder).**
- `§PA-REPORT report=102 "Statement of Cash Flows" maxDiff=0c` (140 segment cells) + `§PA-REPORT-NONVACUOUS
  CF-nonzero-cells=21` (not a 0==0 tie) — BOTH witnesses: engine-direct (`poc_pa_report.log`) and bundle-alone
  (`poc_statement_browser.log`, CF-nonzero=50). foldStatement now oracle-equivalent for ALL 3 PA_Reports.
- NOTE: `ad_full.db` was clobbered by a concurrent terminal (lost pa_*/ad_treenode) → `poc_pa_report.js` re-pointed
  at the immune bundle `glassbowl_data.db` (comment in file); + the browser witness's empty-id guard ported.

**✅ W-PRINTFORMAT — `foldPrint(AD_PrintFormat)`, the single-document print format (the session's main task).**
- Engine: `build/erp/report_overlay.js` → new pure verb `foldPrint` (recursive `'P'` master-detail — explodeBOM-shaped,
  N levels free — + the PrintDataGroup row engine: isorderby/sortno sort, isgroupby partition, issummarized Σ
  BigDecimal / iscounted / isaveraged, ispagebreak marker; ONE pass, no SQL-vs-Java split-brain).
- Data gate: NEW `scripts/extract_printformat.sh` → bundle carries ad_printformat (93) + ad_printformatitem (2780,
  26 'P' links) + the MATERIALIZED print views c_invoice_header_v (8) / c_invoice_linetax_v (40) — view SQL evaluated
  BY POSTGRES at extract time, never reimplemented (non-invent). `§PRINT-EXTRACT` log: `build/erp/extract_printformat.log`.
- Witness `scripts/poc_printformat.js` → `build/erp/poc_printformat.log` (exit 0): format 120 Invoice Header →'P'→
  121 Invoice LineTax, ALL 8 seed invoices — `§PRINTFORMAT invoices=8 diffedCells=48 maxDiff=0c`; oracle = LIVE
  base tables (c_invoiceline rows+order, c_invoicetax 999999 rows) **+ the stored c_invoice.grandtotal real iDempiere
  wrote** (Σ break(LineNetAmt) == grandtotal, the strong anchor). §FALSIFIERs A (drop 'P' → children=0),
  B (+1c → Σ diverges 1c), C (drop link → whole-view leak 40 rows) ALL fire. PrintData TREE + subtotals, NOT pixels.
- Honest: group-by/count/avg implemented per PrintDataGroup but UNEXERCISED by the seed format (no claim).

**✅ Browser wiring (Step 3 for prints + menu leaves item-b).**
- `report_overlay.js` host glue: `printModel`/`bundleRowsOf`/`formatForTable` (format resolved DATA-driven by
  tablename, prefers non-TEMPLATE) + `printDoc` + `renderPrint` (manifest→DOM: master fields, detail table, Σ break
  rows; `§PRINT` log) + `overlay:print` intent + `__report.print`. Receipt panel gains a DATA-GATED "⎙ iDempiere
  format" button (only when bundle carries the format + the doc's print view).
- AD_Menu leaf **Trial Balance (502, action='R') now ACTIONABLE** → proven `foldTrialBalance` + new in-panel render
  (`§TB` log); **Statement of Accounts (350) stays dimmed** — ⛔-class named-deferral: no oracle-anchored fold yet.
- Regression: `§REPORT-FIN` base + W-PA-MENU + both statement witnesses re-run GREEN after wiring (all logs exit 0).

**✅ Docs mirrored to evidence:** `ReportingFold.md` (status EXECUTED; §3 verdict column with §-lines; NEW §4b
foldStatement-as-built mirror + §4c foldPrint-as-built; §5 ticked) · `ERP_COVERAGE_MATRIX.md` (PA_Report +
AD_PrintFormat rows → ORACLE-EQUIVALENT wording, 🟡 headless ceiling held; T_Report member now BS+IS+CF; §B summary
updated) · `MigrateComparisonPaper.md` (§temp-tables proven-block now 3 statements + foldPrint; GAP #8 narrowed to
its tail: 13 remaining T_* members, unexercised break functions, pixels-non-goal).

**Residuals (named, not blocked):** (a) live in-browser VISUAL confirm = bim-ootb Playwright suite (no headless
browser in this repo); (b) Statement of Accounts 350 fold; (c) the other 13 `T_*` folds, each its own witness;
(d) a seed format exercising group-by/count/avg (e.g. 122 Shipment, 2 'P' items) for the row-engine's remaining
functions. NinjaExcel separate, untouched.
