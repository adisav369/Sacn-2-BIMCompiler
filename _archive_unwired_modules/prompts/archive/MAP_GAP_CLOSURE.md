# ⚠ DO NOT REMOVE — MAP GAP CLOSURE (panel-bullet → driving-card MAP only)
# Scope: ONE narrow job — map each migration-honesty panel bullet (docs/migrate_status_panel.html) to the card
#        that drives it. NOTHING ELSE lives here now.
# ▶ THE OPERATIONAL BACKLOG MOVED: prompts/GAP_CLOSURE_LANE.md is the prioritized, oracle-grounded backlog for
#        closing ERP_COVERAGE_MATRIX gaps (T_Aging P1 → T_* report folds → inventory/analytic → declarative tail →
#        P4 Odoo extraction). Start work THERE, not here. This file is just the panel↔card lookup.
# Map source of truth: docs/migrate_status_panel.html (canonical) — edits go through prompts/MIGRATE_STATUS_PANEL.md.
# Equivalence backing: docs/ERP_COVERAGE_MATRIX.md (the scoreboard) · docs/GapClosureSpec.md (oracle protocol).
# NON-INVENT: a row is ✅ only by an oracle DIFF (maxDiff=0c + §FALSIFIER), never a claim. Read the log.

---

## The panel gap → driving-card index

| Band | Panel gap bullet | Driving card | Where in the backlog |
|---|---|---|---|
| 🟡→✅ | Live-UI axis (headless engines surfaced on-screen) | `done/UI_UNPARK_RESUME.md` | **✅ DRAINED 2026-06-11** — B-1→B-5 shipped (bim-ootb PR #264 sw v647; B-5 AD_Process PR #267 sw v650). Archived. NOT an open lane. |
| 🟠 | AR / AP aging | `GAP_CLOSURE_LANE.md` **P1.1 (T_Aging)** | **START HERE** — oracle CONFIRMED reachable (`rv_openitem`, 7 rows live). Was wrongly an ORPHAN here; it has a card now. |
| 🟠 | ~12 `T_*` report folds (TrialBalance · CashFlow · InventoryValue · InvoiceGL · …) | `GAP_CLOSURE_LANE.md` **P1.2** | live PG oracle each; one fold + witness + §FALSIFIER per table. |
| 🟠 | Master data · SO 26/27 · purchasing · money · inventory-state | `MIGRATE_INSTALL_TENANT.md` §RESUME | `GAP_CLOSURE_LANE.md` **P4** (Odoo extraction) — last priority (most work, least matrix movement). |
| 🔴 | Cost-valued inventory GL | `GAP_CLOSURE_LANE.md` **P2.3** | integrate into `postRecipe()` via the proven cost-selection rule; **rule-consistent** where seed lacks component-cost. |
| 🔴 | Analytic / cost-centre dimensions | `GAP_CLOSURE_LANE.md` **P2.4** | `{Project.Analytic}` token → `fact_acct`. |
| 🔴 | Posting edge-branches — charge lines · GL distribution · realized-FX | `FABLE5_SOURCE_FALSIFY_AUDIT.md` (C-1/C-3/C-4) | armed in AD, dormant in seed; **oracle-blocked** until a tenant exercises them — do NOT synthesize. |
| 🔴 | Server-action interp. (Odoo 64) · Odoo QWeb print mapping | **ORPHAN** (no card) | Odoo-side; needs a card before pursuing. |
| 🔴 | On-demand procs (454/476) | `GAP_CLOSURE_LANE.md` **P3.5** | on-demand MECHANISM only — do NOT pre-port the 454 corpus. |

## Audit bucket-A edges (kept so they aren't lost; GAP_CLOSURE_LANE defers to these)
| Edge | Card | State |
|---|---|---|
| A-1 qty-rollup (`QtyDelivered/QtyInvoiced`) | `ERP_SOURCE_AUDIT_DELTAS.md` §A-1 | **✅ DONE — W-MORDER-QTYROLLUP** (`maxDiff=0`); auto-wire into `completeOrder` = named next. |
| A-2 repost / FactAcctReset idempotency | `ERP_SOURCE_AUDIT_DELTAS.md` §A-2 | **STUB ready** (`build/erp/poc_repost_idempotent.js`) — property test, buildable now (`T_Fact_Acct_History` named in GAP_CLOSURE_LANE excludes). |

## STOP CONDITION
A panel bullet flips only with a witness diff (`maxDiff=0c` + §FALSIFIER) or a wired extraction. Update the bullet in
`docs/migrate_status_panel.html` via `MIGRATE_STATUS_PANEL.md` — never edit a band to look greener than the witness.
