# Glassbowl — the engine as a navigable surface (read-only MVP)

> **Status:** SPEC + read-only prototype, built and proven inside `bim-compiler` (served from this
> repo until maturity, then a proper sub-repo). **Touches nothing in `bim-ootb`/live.** The main
> client will eventually route to two views: **Glassbowl** (this — the engine) and **Gardenworld**
> (the existing data/instances view). The old view is *not deleted* — just not called.

## Why this exists
Today the only way to know what the ERP engine does is to read specs + code. But the engine was
built as **data + an op-log, not imperative code** (§0.14, §0.6): the FK relationships are data
(`ad_column` refs + `ad_ref_table`), the behaviour is data (`erp_rules.db`), the history is data
(`kernel_ops`). So the engine can *render itself* — this is §0.7 self-graphing pointed at the dev.
A glass bowl over iDempiere's Java would be a decompiler; a glass bowl over **this** is just
rendering the metamodel the system already holds.

## Witness claim (the issue this proves)
**W-GLASSBOWL — the engine is introspectable from DATA ALONE.** Its FK graph, its cells (verbs /
guards / policy / matcher), its gravity (hot/cold), and its cold backlog all render with **zero
hand-authored structure** — every node, edge, and annotation is *extracted* from `ad_full.db` +
`erp_rules.db` + `kernel_ops`. Proven when `scripts/system_explorer.js` emits `system_graph.json`
with §-logged counts (FK edges, spine classification, written cells, 155 cold cells, gravity rank)
and a self-contained `glassbowl.html` renders them. If any node/edge had to be hand-coded, the
claim fails (the engine is *not* introspectable as data) — that is the issue under test.

## The duality (one graph, two altitudes)
- **Gardenworld** = the graph at the **instance** layer (this invoice, this order).
- **Glassbowl** = the *same* graph zoomed out to the **type/metamodel** layer (the `C_Invoice`
  cell, its FK to `C_Order`). AD already models the type layer as data, so it is the same renderer
  at a different altitude — not a separate subsystem.

## Layers (read-only MVP scope)
- **Layer 1 — FK graph.** Nodes = tables/DocTypes in the core document world; edges = FKs,
  **classified by spine** (the deterministic rule below). Lets you *see* the §0.13 two spines
  separate: the BOM tree (containment + derivation) vs the settlement lattice (multi-parent DAG).
- **Layer 2 — cells.** Each written `(DocType, action)` cell annotated with its verbs, guard
  count, policy flags, and whether the matcher is invoked — extracted from the cell registry +
  `erp_rules.db`, authoritative engine facts from a `diff_oracle` run (not guessed).
- **Layer 3 — gravity overlay.** Node size/heat = `kernel_ops` use (§14 weighting). Hot cells
  glow; the **155 cold `handler_backlog` cells** render as dim ghosts — the gravity backlog made
  visible. "What's actually alive" — something no static ER tool gives you.

## Edge classification rule (deterministic, extracted — never invented)
Each FK edge `(fromTable.column → toTable)` is one of four kinds, decided by data only:
1. **containment** — `ad_column.isparent = 'Y'` (iDempiere's own parent-FK marker).
2. **settlement** — `fromTable` or `toTable` ∈ the match/allocation set
   {`m_matchpo`, `m_matchinv`, `c_allocationhdr`, `c_allocationline`} (§0.13 "must-agree" edges).
3. **derivation** — non-parent FK between two lifecycle documents
   (`m_inout`/`m_inoutline`/`c_invoice`/`c_invoiceline`/`c_payment` → `c_order`/`c_orderline`/
   `m_inout`/`m_inoutline`/`c_invoice`/`c_invoiceline`) — the order→ship→invoice→pay chain.
4. **reference** — everything else (FK to master data: `c_bpartner`, `m_product`, UOM, …).
FK target resolution: `ad_reference_value_id` present → `ad_ref_table.ad_table_id` → `tablename`;
else strip trailing `_ID` from the column name. Edges whose target is not a real `ad_table` are
dropped. The table-membership sets above are themselves stated data, not magic constants.

## Out of scope for the MVP (deliberately)
- **Editing.** Click-to-edit a policy flag is the SystemAdmin console — it needs the engine *in
  the browser* (the kernel write loop), i.e. **T3 relocation to `bim-ootb`, which is parked and
  needs an explicit go** (`push=live`). The MVP is a read-only glass bowl; editing is the next gate.
- **The full 925-table schema.** The MVP scopes to the core document world so the bowl is about
  the *engine*, not a 9,423-edge schema dump (that would be a boring ER diagram, the failure mode).

## Files
- `scripts/system_explorer.js` — the generator (deterministic backbone, §-logged witnesses).
- `build/erp/system_graph.json` — the emitted engine graph (the data layer).
- `build/erp/glassbowl.html` — self-contained viewer (graph inlined; opens via `file://`).
