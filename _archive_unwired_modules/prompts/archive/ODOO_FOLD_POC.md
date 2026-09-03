# ⚠ DO NOT REMOVE — Scope guard
# Scope: a SANDBOX, headless §-witness POC that tests the MIGRATION-SOLVENT claim on a REAL foreign ERP — Odoo.
#        Thesis under fire (docs/HolyGrail.md §"Abstracting the DocAction corpus — and why it is the migration
#        solvent"): a foreign ERP's document lifecycle folds into THIS engine via an ADAPTER (their (status,action,
#        transition) → the generic table; their schema → the 5-table bridge) and the EXISTING verb set reproduces
#        their executed output — with NO invented logic. This POC tries to FALSIFY that on Odoo's own O2C flow.
# NON-NEGOTIABLE: Spec-first; witness-led (each test NAMES the issue it proves/disproves); §-log first; the Log
#        Mandate (save the run, READ the log before conclusions); deterministic (ids/ts/amounts are recorded INPUTS
#        from the Odoo export — NO Date.now()/Math.random(); the oracle is STATIC, no live Odoo at replay time).
# HONEST FRAME: this is a FALSIFIER. A new verb that Odoo needs and iDempiere did not is a FINDING, named in the log,
#        not a failure to hide. "Folds with the existing verbs" and "needs verb X" are BOTH valid, reportable results.
# Read first: docs/HolyGrail.md (migration solvent + DocAction collapse) · docs/ERP.md §0.17 (the contained-set
#        thesis, diff-oracle) / §0.19 (long-tail ships matcher-free; matcher composes in at one seam) / §0.12 (the
#        STATIC oracle = executed rows, no live system) · scripts/diff_oracle.js + diff_oracle_cells.js (the
#        iDempiere oracle harness to MIRROR) · scripts/erp_engine.js / erp_kernel.js (the verb set to reuse) ·
#        scripts/poc_longtail.js (the iDempiere O2C vertical this mirrors on Odoo).

---

# Odoo Fold — Sandbox Migration POC (the migration-solvent falsifier)

## Why Odoo first
Odoo is the *fair* test: open-source and Python, so you get BOTH halves — the **oracle** (its executed demo rows)
AND the **source** (its state machine, readable, to build the adapter from). If the migration-solvent thesis is
true anywhere beyond iDempiere, it is true here first. If Odoo's O2C cannot be folded without inventing, the
"one verb set, behaviour-as-data" claim is **local to iDempiere** — and we must say so in HolyGrail. This is the
cheapest honest place to find that out.

## Step 0 — the oracle (gated; do this before any code)
Odoo runs on PostgreSQL and ships **demo data**. Obtain ONE static export of a completed sale-order lifecycle:
- Stand up Odoo demo (docker `odoo` + demo data) OR obtain a demo PG dump. Pick ONE real completed `sale.order`
  whose chain is fully executed: `sale.order` → `stock.picking` (delivery, `state=done`) → `account.move`
  (customer invoice, `state=posted`) → `account.payment` → reconciliation (`account.move.line.full_reconcile_id`
  / `account.partial.reconcile`).
- Export the rows of that chain to a static SQLite/JSON `build/erp/odoo_oracle.{db,json}` — the executed truth,
  exactly as `ad_full.db` rows ARE GardenWorld's executed output (§0.12). **No live Odoo at replay time.**
- Record in the log: `§ODOO-ORACLE so=<name> picking=<name> invoice=<name> payment=<name> reconcile=<id> rows=N`.
- (NOTE the coder VERIFIES the exact table/state names against the real export — these are the starting map:
  `sale.order.state` ∈ {draft,sent,sale,done,cancel}; `stock.picking.state` ∈ {…,assigned,done,cancel};
  `account.move.state` ∈ {draft,posted,cancel}, `move_type=out_invoice`; document flow via
  `sale.order.picking_ids` / `invoice_ids`. Do NOT trust this list — read the dump.)

## Step 1 — the adapter (the only new code; the engine does NOT change)
Write `scripts/odoo_adapter.js` — pure mapping, no business logic:
- **State-machine map:** Odoo `(model, state, action)` → the generic transition cell `{from,to,legal,handler}`.
  e.g. `sale.order: draft→sale` = COMPLETE_ORDER; `stock.picking: →done` = CREATE_SHIPMENT; `account.move:
  draft→posted` = CREATE_INVOICE + POST; reconciliation = ALLOCATE/MATCH.
- **Schema map:** Odoo tables → the 5-table bridge (`documents`/`document_lines`/`journal` + `containers`/`items`),
  `ad_table_map`-style. `sale.order`→documents(doc_type=SALES_ORDER); `account.move`→documents(doc_type=AR_INVOICE)
  + `account.move.line`→journal; `stock.move`→items movements.
- Output: the SAME op shape the kernel + CRUD layer use (`{op_type,id,parameters,ts}` INPUTS; op-GROUP for fan-out).

## Step 2 — fold + verify (mirror diff_oracle.js / poc_longtail.js)
Drive the adapted ops through the EXISTING kernel verbs (`completeOrder`/`createShipment`/`createInvoice`/`allocate`/
`match`). Diff the canonical projection against the Odoo oracle, the same effect-set multiset diff as `diff_oracle.js`.

## Witnesses (§-log first; the only evidence)
- `§ODOO-FOLD chain so→picking→invoice→payment→reconcile mapped=5/5 missing=0` — every hop the adapter maps to a cell.
- `§ODOO-FOLD verbs used=[completeOrder,createShipment,createInvoice,allocate,match] newVerbs=[…]` — **name any verb
  Odoo needs that iDempiere did not** (the headline finding — empty list = thesis holds; non-empty = thesis bounded).
- `§ODOO-FOLD diff matched=K missed=0 extra=0 vs odoo_oracle agree=Y` — canonical effects reproduce the executed
  Odoo result (delivered qty, invoice total, payment, reconciliation amount) to the cent.
- `§ODOO-FOLD replay-hash stable rebuildA==rebuildB agree=Y` — deterministic (no live system, holder-irrelevant).
- `§ODOO-FINDINGS …` — every divergence as a NAMED finding (e.g. anglo-saxon interim accounts; Odoo reconciliation
  is line-level partial vs iDempiere allocation header — refines §0.17/§0.19, does not hide).

## Acceptance / the falsifier
- **Thesis holds** iff `newVerbs=[]` AND `diff agree=Y` — Odoo's O2C folds with the existing verb set + a pure adapter.
- **Thesis bounded** (still a valid, important result) iff a small, NAMED set of new verbs/handlers is required —
  report exactly which, and whether they are *adapter-shaped* (data) or *genuinely new behaviour* (code). Update
  HolyGrail's migration section with the honest scope either way.
- The known watch-points to report on, not assume: **Odoo accounting model** (`account.move`/`account.move.line`,
  anglo-saxon vs continental, interim/COGS accounts) and **reconciliation** (partial/full) differ from iDempiere's
  `Fact_Acct`/allocation — expect findings here; that is the point.

## Guardrails
- Reuse the iDempiere verb set + `diff_oracle` harness; the ENGINE does not change — only `odoo_adapter.js` is new.
- Non-invent: every value is folded from the Odoo export; if the oracle lacks a value, report "absent in dataset",
  never synthesise (mirror the GL `fact_acct=0` honesty).
- Static oracle only; no live Odoo at replay; ids/ts/amounts are fixtures from the export.
- HANDS-OFF the live CRUD/glassbowl files. NO deploy.

## Status
- SPEC, 2026-06-01. The migration-solvent falsifier for Odoo. Sibling: `prompts/SAP_FOLD_POC.md` (the asymptote).
- Coder executes Step 0–2, produces `build/erp/odoo_fold.log` + a `# DONE` ledger (claim ↔ §ODOO line). No deploy.
