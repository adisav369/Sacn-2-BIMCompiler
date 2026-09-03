# ⚠ DO NOT REMOVE — ODOO QWEB PRINT FOLD (red-band fold-gap closure #1)
# Paste-to-start: `proceed with prompts/ODOO_QWEB_PRINT_FOLD.md`
# Scope: the ODOO side of "Document print mapping" — map Odoo's QWeb report defs → the SAME print
#   tree the iDempiere side already folds (foldPrint / W-PRINTFORMAT, 8/8 invoices maxDiff=0c).
#   Closes one of the two genuine code gaps in the 🔴 fold-gap band of docs/migrate_status_panel.html.
# READ THE LOG after every run. ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# NON-NEGOTIABLE: spec-first · witness-led · NON-INVENT (every cell READ from a real Odoo row /
#   stored total, never synthesized) · deterministic integer-cents (build/erp/bigdecimal.js, never
#   raw Number for a summed value) · §FALSIFIER load-bearing in every witness · pixels are a NON-GOAL.

---

## 0 · THE GAP (one sentence)
iDempiere's `AD_PrintFormat` master-detail print is folded to the cent already (`foldPrint`,
W-PRINTFORMAT) — but Odoo's **41 QWeb report defs** have **no mapping** to that print tree, so a
migrated Odoo tenant can't render its own documents. This lane builds the Odoo→print-tree map.

## 1 · WHAT ALREADY EXISTS (read first — do NOT re-derive)
- **iDempiere side DONE:** `build/erp/report_overlay.js` `foldPrint` — recursive `'P'` master-detail
  reduction, ONE BigDecimal pass; W-PRINTFORMAT reproduces format 120 Invoice Header → 121 LineTax,
  ALL 8 seed invoices `§PRINTFORMAT diffedCells=48 maxDiff=0c` vs live base tables + stored
  `c_invoice.grandtotal`. `scripts/poc_printformat.js`, `docs/ReportingFold.md §4b/§4c`.
- **The print-tree shape** the Odoo side must produce = whatever `foldPrint` consumes (a `PrintData`
  row tree: header node → `'P'` child grid → break subtotals). REUSE it; do not invent a second shape.
- **Odoo migration generator:** `build/erp/gen_ad_odoo.js` (pulls trade + master into `12-odoo.db`).
  It does **NOT** pull QWeb today (verified 2026-06-14 — no qweb/ir_ui_view refs). The shard has no
  report templates.
- **Live Odoo 17 source** (the extraction origin): docker Postgres, db per `reference_idempiere_source`
  / `MIGRATE_INSTALL_TENANT.md`. QWeb report templates live in **`ir_ui_view`** (`type='qweb'`) bound
  to a model via **`ir_act_report_xml`** (report action → template + model + paperformat). The "41"
  count is from `MigrateComparisonPaper.md` (QWeb defs).

## 2 · SPEC (write this section BEFORE any engine code)
Add `docs/ReportingFold.md §5 — Odoo QWeb fold` (propose the heading first if structural):
- **Verb:** `foldQWeb(reportDef, record)` — PURE, host-injected db reader, no DOM. Maps a QWeb
  template's structure to the print-tree: `t-foreach` over a child relation = the `'P'` master-detail
  grid; `t-field`/`t-esc` = a cell bound to a column; `t-call`/sub-template = a nested node. Output =
  the SAME tree `renderPrint` already draws.
- **Honesty boundary (state it):** QWeb is a Turing-complete template engine. Fold ONLY the
  **declarative document-print subset** (header + line loop + field bindings + break sums — what an
  invoice/SO/PO/picking actually uses). Arbitrary `t-set`/Python expressions in a template = named-
  deferred (the iDempiere `'P'`-fold had the same boundary: group-by/count/avg implemented, exotic
  layout deferred). Count what you defer; never fake it.

## 3 · STEPS
1. **Extract** (extend the generator, don't fork): pull the report templates + their report actions
   from live Odoo Postgres into the shard — a `qweb_report` table (action id, model, template arch
   XML, paperformat) keyed to the migrated docs. Cite the SQL in the extractor. Log the count vs 41.
2. **Spec** §5 (above). Propose the heading, then write it.
3. **Engine** `foldQWeb` in `report_overlay.js` (or a sibling `report_qweb.js`, host-injected). Cite:
   `// Implementing ReportingFold.md §5 — Witness: W-ODOO-QWEB`.
4. **Witness** `scripts/poc_fold_qweb.js` → **W-ODOO-QWEB**: fold ONE real migrated Odoo invoice
   through `foldQWeb`, diff its money cells to the cent vs the **stored Odoo invoice total** (the
   oracle Odoo itself wrote — same discipline as the iDempiere stored-grandtotal oracle). `§FALSIFIER`:
   drop the line-loop (`t-foreach`) → no detail rows / total diverges (proves the loop is load-bearing).
5. **Matrix** — flip the `Document print / layout` row in `docs/ERP_COVERAGE_MATRIX.md` +
   `MigrateComparisonPaper.md` (Odoo QWeb side) from `· FOLD` (gap) to its new verdict; update the
   🔴 band of `docs/migrate_status_panel.html` (this item leaves the fold-gap band when proven). Deploy
   docs with `mkdocs gh-deploy` from bim-compiler.

## 4 · DONE WHEN
- `W-ODOO-QWEB` PASS (`maxDiff=0c`, falsifier fires), log read, not just exit 0.
- One real Odoo document renders in the browser from its own QWeb def via the shared print tree.
- Matrix + panel + paper updated and published; deferred subset named with a count.

## OUTSTANDING
- [ ] Extract QWeb report defs from live Odoo → shard `qweb_report` table (count vs 41)
- [ ] ReportingFold.md §5 spec (propose heading first)
- [ ] `foldQWeb` engine
- [ ] `poc_fold_qweb.js` W-ODOO-QWEB PASS + falsifier
- [ ] Matrix + panel + paper updated, `mkdocs gh-deploy`
