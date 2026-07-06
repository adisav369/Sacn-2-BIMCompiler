# ⚠ DO NOT REMOVE — Session handoff: Odoo master-data FULL pull + ShowMe icon content + MigrateComparisonPaper→ERPUserGuide link
# STATUS: PREPARED 2026-06-16 (handoff from the install/migrate-triage session). 3 work items, item 1 is the big one.
# DOCTRINE: Spec-first · §-log first · NON-INVENT (every migrated row traces to a real Odoo record;
#   the proof is per-model `records == live search_count`) · delegate-to-install (browser never touches Odoo).
# EDIT RULE: ~/bim-ootb is hook-blocked → work in a `/tmp/wt-*` worktree off `origin/main`. ERP deploy = PR→main (GH Pages).
#   SW changelog is DEPRECATED (#332, 2026-06-16): CACHE_VERSION = bare bump + one-line pointer, NO changelog. Git is the record.

## ◀ WHAT THIS SESSION ALREADY SHIPPED (context, don't redo)
- **Viewer MEP + 4D/5D offline DB load** — `fix/mep-boq-offline-cache` (PR #330): peek IDB cache (zero network)
  before any HEAD probe; fall back to sibling DB; "Load the building first" on no-cached-DB. Witness
  `tests/witness_offline_db_select.js` 6/6. viewer sw v659→v660.
- **Tenant delete trashcan visibility** — `fix/tenant-del-visible` (PR #333, auto-merge): the login switcher's
  delete affordance was `opacity:0` (hover-only) → invisible at a glance + unreachable on touch. Now faint
  always-on (`opacity:.4`, brighten on hover). Gating logic was already correct. erp sw v695→v696.
- SW changelog drop landed concurrently (#332).

## ▶ ITEM 1 (BIG) — Odoo migrate: extend the agent from ONE chain → FULL master-data pull
**Governing prompt:** `prompts/MIGRATE_INSTALL_TENANT.md` (its RESUME/NEXT #2,#3). Gap inventory (authoritative):
`build/erp/odoo_survey.json` (`.gap_inventory.areas` + `.counts` = live search_count per model).

**`odoodemo` IS THE TEST BENCH, NOT THE TARGET.** The shipped agent is generic + env-parameterized
(`ODOO_HOST/ODOO_DB/ODOO_LOGIN/ODOO_PASSWORD`) and the USER runs it against THEIR OWN Odoo on THEIR machine —
that is the whole point ("run it → migrates YOUR master data over"). We develop+witness against `odoodemo`
only because it's a real Odoo 17 with KNOWN counts, so `records == search_count` is a meaningful non-invent
proof (can't prove completeness against a black box). The pull targets STANDARD Odoo models (res.partner,
product.template, account.account, …) present in any Odoo 17 — nothing odoodemo-specific.
**Live bench is UP RIGHT NOW:** docker `odoo`+`odoo-db` (up 5d), `http://localhost:8069` returns 200, JSON-RPC
`/jsonrpc`, login `admin/admin`, db `odoodemo`. (If down: `docker start odoo-db odoo`; poll
`curl -s -o/dev/null -w '%{http_code}' localhost:8069/web/login` until 200.)

**Current state — the agent pulls almost nothing as master data:**
- `~/bim-ootb/erp/odoo_agent/agent.js` → `odoo_chain.json`: ONLY the S00023 sell-side O2C chain (1 SO →
  delivery → 1 invoice GL). No master data, no other docs. (buy-side `buildBuyEvents` exists in
  `odoo_adapter.js` but agent.js never runs it.)
- `~/bim-ootb/erp/odoo_agent/extract_model.js` → `odoo_model.json`: the DICTIONARY (menus/fields + ≤500
  display records) for renderer #2 — NOT a migrate master-data set.
- Bundle = `~/bim-ootb/erp/odoo_agent/{agent.js, odoo_adapter.js, erp_kernel.js, extract_model.js,
  package.json, README.md}` zipped to `~/bim-ootb/erp/odoo_agent.zip` (served; `npm install && node agent.js`).

**THE GAP (from odoo_survey.json — every number is a live search_count):**
- Masters: res.partner **38** · res.company **2** · res.currency **1** · product.template/product.product **30/35**
  · product.category **9** · uom.uom **28** · account.account **47** · account.journal **8** · account.tax **2**
  · account.payment.term **11** · account.fiscal.position 0.
- Documents (later legs): sale.order 27 · purchase.order 13 · stock.picking 31 · stock.move/line 59/51 ·
  stock.quant 43 · account.move out_invoice 34 / in_invoice 6 · account.move.line 99 · account.payment 3 · etc.
- Fold-gaps (DO NOT extract — route to `docs/ERP_COVERAGE_MATRIX.md` / FOLD lane): account.report, QWeb defs,
  ir.actions.server (64, all imperative), aging wizard.

**BUILD (this leg = MASTERS + witness; the prompt's NEXT #2/#3):**
1. New `extract_masters.js` in the odoo_agent bundle (model on `agent.js` rpc/auth: JSON-RPC `execute_kw`,
   `uid` from `common.authenticate`). For EACH master model above: `search_count` THEN `search_read` the fields
   needed, and ASSERT `rows.length === count` → emit `§ODOO-MASTERS model=<m> rows=N count=N PASS` (the
   NON-INVENT proof). Write `./odoo_masters.json` (one section per model, raw pulled rows; m2o `[id,label]`
   kept as-is for the fold to resolve).
2. Run it against LIVE Odoo, capture the log, confirm EVERY model PASS (rows==count). This is the witness —
   it is the whole deliverable of this leg (do not claim done without the per-model PASS lines).
3. Rebuild `odoo_agent.zip` from the source folder (the served artifact must carry the new extractor). Update
   `README.md` to mention the masters pass.
4. **Fold-into-tenant (NEXT leg, can be its own PR):** the browser Odoo flow (`erp/erp_picker.js` `_foldChain`)
   must consume `odoo_masters.json` and merge the masters into the **Client-12** shard so a migrated tenant has
   its 38 BPs / 35 products / 47 accounts resident — not just the 1 chain. Acceptance bar (from
   MIGRATE_INSTALL_TENANT.md): Client 12 fits MIGRATE_FULL_MODEL_FRAME items 1–4 and the AD-gated GL view
   renders `coverage:complete` + balanced. Posting config dependency: `prompts/MIGRATE_POSTING_CONFIG.md`.
**GUARDRAIL:** never synthesize a row to "fill" the tenant. Pullable-but-not-pulled = extraction-gap (extend
the agent). Engine-can't-fold = fold-gap (route to the matrix, NOT the extractor).
**Worktree:** `feat/odoo-master-pull` exists at `/tmp/wt-odoo-pull` (off origin/main, empty) — reuse or recreate.

## ▶ ITEM 2 — Update the ShowMe icon content
- ShowMe lives in `~/bim-ootb/erp/help_overlay.js` (spec `READSHOWME_DYNAMIC_SPEC.md` / build/erp
  `READSHOWME_DYNAMIC_SPEC.html`; D3). The overlay reads a per-step HELP store → `STEPS` (key + `readmeAnchor`
  → `Read more →` into `red1oon.github.io/BIMCompiler/`), with a `▶ ShowMe` button (`#hcShow`) that drives a
  type-aware coach plan. Icons = Lucide via `icons.js` (per `feedback_pill_icon_consistency` — clean line
  icons only, no unicode).
- **ASK THE USER what "icon content" means here** before editing: (a) the ShowMe step text/anchors,
  (b) the icon glyph used for the ShowMe pill, or (c) the coach-plan steps. Then update accordingly, §-log
  witness (`§READSHOWME step=… key=… para=…`), keep icons Lucide-consistent.

## ▶ ITEM 3 — MigrateComparisonPaper: link ERPUserGuide prominently
- Files: `docs/MigrateComparisonPaper.md` (117KB, the GAP ANALYSIS / 3-col capability map) + target
  `docs/ERPUserGuide.md` (37KB, 2026-06-15). Published site = `red1oon.github.io/BIMCompiler/` (mkdocs).
- Add a PROMINENT cross-link near the top of MigrateComparisonPaper → the ERP User Guide (e.g. a callout under
  the title). **PROPOSE the placement + wording first** (per `feedback_propose_before_editing_docs` — subjective
  presentation = propose→approve→edit, short+sourced, no ceremony). Then publish via `mkdocs gh-deploy` from the
  full superset branch (per `feedback_docs_deploy_landmine` — never from master; docs.yml is DISARMED).

## ORDER / NOTES
- Item 1 is the substantive build (do it spec-first with the per-model witness). Items 2–3 are quick once the
  user clarifies (item 2 needs the one clarification; item 3 needs placement approval).
- Read `prompts/MIGRATE_INSTALL_TENANT.md` + `build/erp/odoo_survey.json` FIRST for item 1.
