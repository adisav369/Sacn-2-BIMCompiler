# Glassbowl Phase 2 — Lifecycle Chain + Right-Click Dossier (SPEC)

> **Status:** SPEC ONLY — to be built in a NEW session. Read-only (no editing yet; editing needs T3,
> the engine in the browser, `push=live`, explicit go). Built inside `bim-compiler`, served from this
> repo (same URL: https://red1oon.github.io/BIMCompiler/glassbowl.html). **Touches nothing in
> `bim-ootb`/live.** Prior art: `docs/GLASSBOWL.md` (Phase 1, the read-only map, shipped + proven).
>
> **PRIME RULE (unchanged):** EXTRACT OR COMPILE ONLY. Every node, edge, row, rule, and chain is
> read from `ad_full.db` / `erp_rules.db` / `kernel_ops` — never invented. A value with no source is
> a FINDING to log, not a guess.

## Why Phase 2 (the product thesis being proven)
Phase 1 proved the engine renders *itself* from data. Phase 2 proves the thing legacy iDempiere
structurally **cannot** do: **everything about an entity — its data, rules, validation, workflow,
access, accounting, and a real record's full lifecycle — in ONE place, no tab-switching, no second
login.** In iDempiere a power user opens 5–7 windows (and sometimes two logins) to assemble what one
right-click will show here. The §0.18b duality made literal: **Gardenworld** = the instance layer
(this invoice #200001), **Glassbowl** = the type layer (the `C_Invoice` cell) — Phase 2 lets you
move between them on one canvas.

## Interaction model (the redundant fixed panel goes away)
- **Left-click a bubble → a small FLOATING CARD anchored to it** — glanceable: friendly name, what
  the system does here, what it connects to. Follows the bubble; dismisses on click-away. Replaces
  the fixed right panel so the eye stays on the graph.
- **Right-click a bubble → the DOSSIER** — a larger movable/resizable panel, lazy tabs, "everything
  about this entity." The no-more-tab-switching payoff.
- **"Trace a record" (on a document bubble) → the LIFECYCLE CHAIN** (Phase 2a, below).
- God mode now: gate nothing (show all data/rules/orgs). Roles come later — and the access data is
  itself a dossier tab, so "who could see this" is visible to the god-mode user.

---

## Phase 2a — the LIFECYCLE CHAIN view (BUILD THIS FIRST: highest wow / lowest effort)
Pick a real GardenWorld document and light its **whole life** across the map as one connected path —
`Order → Shipment → Invoice → Payment → Allocation` — with a step-strip naming the actual documents.
This reuses lineage we already proved this session (`poc_longtail.js`, SO 101), so its oracle exists.

### Witness — W-LIFECYCLE
**Clicking "trace" on a document reconstructs a real record's full chain from data alone.** For sales
order **101** the chain is exactly the five documents `poc_longtail` drove (§0.19): `C_Order #80001 →
M_InOut #101 → C_Invoice #200001 → C_Payment #100 → C_AllocationLine (98.5)`. Proven when the lineage
extractor emits that ordered chain and the viewer lights exactly those type-bubbles + the derivation/
settlement edges between them, **0 hand-authored hops**. `§LIFECYCLE record=C_Order#101 hops=5
chain=[...] missing=0`. If a hop must be hand-coded, the claim fails (lineage is not in the data).

### The lineage path (extracted, deterministic — the derivation FK walk)
Forward-follow the document derivation edges (the green spine already classified in Phase 1):
1. `C_Order` —`c_order_id`→ `M_InOut` (shipment/receipt for that order)
2. `C_Order` —`c_order_id`→ `C_Invoice`
3. `C_Invoice` ←`c_invoice_id`— `C_Payment` (the payment that settles it)
4. `C_Payment` —`c_payment_id`→ `C_AllocationLine` (the applied amount, partial-aware)

Source of truth = `ad_full.db` rows (the static GardenWorld oracle, §0.12) for *existing* records;
for records *created in the engine*, the same chain is the `kernel_ops` lineage GUIDs
(`input_guids`/`output_guid`, §0.6) — the two must agree (a cross-check worth a §-log).

### Rendering
- Dim the whole map; highlight only the chain's nodes + connecting edges; subtle order→pay animation.
- A **step-strip** along the bottom: `Order #80001 ▸ Shipment #101 ▸ Invoice #200001 (100.70) ▸
  Payment #100 (98.50) ▸ Allocated 98.50` — each step the real document with its number/amount.
- Clicking a step opens that document's dossier (Phase 2b/c).
- A record picker (start with order 101; later a search box over real documents).

### Data sourcing
Phase 1 inlines only graph *metadata*. The chain needs *real rows* → load them in-browser via
`sql.js` + a small data bundle (a subset of `ad_full`/`ad_seed.db`), the EXACT pattern the ERP UI
already uses. Graph metadata stays inlined; rows + rule bodies lazy-load from the `.db` on first use.

---

## Phase 2b — the FLOATING CARD (left-click)
Replace the fixed right panel. Anchored near the bubble, lightweight, business language (reuse the
`FRIENDLY`/`LABEL`/`VERB` maps already in the viewer). Content: name · "what the system does here" ·
top connections · a "trace this record / open dossier" affordance.

## Phase 2c — the DOSSIER (right-click): the 5–7 iDempiere windows, fused
Lazy tabs, each EXTRACTED from data; each replaces an iDempiere window/tab:

| Dossier tab | Source (data) | iDempiere window it replaces |
|---|---|---|
| **Data** (real rows) | `ad_full` rows for the table, filterable | the document window itself |
| **Rules / Validation** | `erp_rules` `Validation` (raw SQL + plain-English gloss) | Validation Rule |
| **Callouts** | `erp_rules`/`handler_backlog` `callout` bound to fields | Callout (model) |
| **Workflow** | manifest `wfmc` transitions (DR→CO→VO…) drawn | Document Type / Workflow |
| **Access** | `ACCESS` rules (`document_action_access`) per role | Role / Document Action Access |
| **Columns** | `ad_column` (type, mandatory, default, FK target) | Table & Column |
| **Accounting** | `AccountingRule` (GL postings produced) | Accounting tab |

## Phase 2d+ — the high-value extras (backlog, prioritized)
1. **★ Reverse dependencies** — "what points TO this" (incoming FKs). One query over the 9,423 FKs;
   iDempiere has no easy equivalent.
2. **★ Audit / time-machine** — who/what/when, replayable, from `kernel_ops` lineage (§0.6, §18.8).
3. **★ Live rule-impact preview** — edit FIFO→LIFO, see affected matches flip *on the map* (the
   diff-oracle in-browser). **Needs the write loop → T3-gated; the read-only "preview without commit"
   is the bridge.**
4. **Status pulse** — counts by `DocStatus` per doctype, as bubble heat.
5. **Data-dictionary search** — type "tax" → jump to table/field/rule.
6. **Available reports** — housed `AD_PrintFormat` ("the set ones", §0.11).
7. **Plain-language labels** — `AD_Element` so the data view reads in business terms.

## Build order (each its own session + witness)
1. **2a Lifecycle chain** (W-LIFECYCLE) — START HERE. + the `sql.js` data bundle (the enabling step).
2. **2b Floating card** (replace fixed panel).
3. **2c Dossier tabs** (W-DOSSIER) — Data + Rules first, then Workflow/Access/Columns/Accounting.
4. **2d extras** — reverse-deps + audit first (both pure reads, both gasp-worthy).
5. **Editing / impact-preview** — LAST, T3-gated (engine in browser, `push=live`, explicit go).

## Non-goals for Phase 2
- **No editing / no writes** (God-mode *viewing* only; editing is T3-gated, a later phase).
- **No `bim-ootb` / no live viewer touch.** Stays in `bim-compiler`, same Pages URL.
- **No invented data.** Missing rows/rules/postings are logged as findings (e.g. GL `fact_acct=0`
  is dataless per §0.19 — the Accounting tab must say "not posted in this dataset", never fake it).

## Files (planned for the build session)
- A lineage extractor (extend `system_explorer.js` or a new `scripts/lineage.js`) emitting sample
  record chains + the `sql.js` data bundle; §LIFECYCLE witness in `build/erp/`.
- Viewer additions: floating card, right-click dossier, chain highlight + step-strip.
- `deploy/dev/tests/test_glassbowl.js` extended: trace lights exactly the 5-doc chain for order 101;
  dossier tabs populate from data; right-click wired.
