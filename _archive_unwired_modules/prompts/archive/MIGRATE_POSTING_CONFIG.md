# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: emit RESOLVABLE POSTING CONFIG on a migrated tenant
# Lane: a migrated tenant db (Client 12 Odoo / Client 13 iDempiere) must carry the posting-account config the
#   engine's `post_resolver` reads — else EVERY GL view (the Posting-Preview drawer, the Accts-Posted panel)
#   renders `coverage:absent`. GardenWorld (Client 11) carries it (via `extract_fact_acct.sh`); migrated tenants
#   today do NOT. This card closes that gap. Feeds prompts/POSTING_PREVIEW_PANEL.md + the Accts-Posted lane.
# STATUS: NEW (handed off 2026-06-10) — discovered while proving the Posting-Preview drawer.
# NON-NEGOTIABLE: EXTRACT, DON'T INVENT — every account comes from a REAL tenant column (iDempiere AD column or
#   Odoo account property), never synthesized. Spec-first; §-log first (READ the log); integer cents; one
#   diff-oracle at a time (MIGRATION_CAMPAIGN_RESUME discipline). Reuse `scripts/doc_poster.js` +
#   `scripts/post_resolver.js` FROZEN — do NOT fork them; the GL views light up automatically once config resolves.

---

## THE GAP (evidence — this session, 2026-06-10)
The Posting-Preview verb (`doc_poster.derivePostings` → `post_resolver.resolve`) is tenant-AGNOSTIC: it reads the
posting config by `(master_id, c_acctschema_id)` from whatever db loads. GardenWorld is just the witness fixture.
But probing the live shards:
- **`bim-ootb/erp/12-odoo.db` (Client 12 Odoo):** `c_order=27, c_invoice=1, ad_role=2` BUT
  `c_bp_customer_acct`, `m_product_category_acct`, `c_validcombination`, `c_elementvalue`, `fact_acct` = **ALL ABSENT**.
  → `derivePostings` resolves ZERO tokens → drawer shows `coverage:absent`. The migrated tenant has DOCUMENTS but no
  resolvable POSTING CONFIG.
- **Live default `ad_seed.db`:** same class — tables present but the docs lack the bpartner/product→acct linkage
  (only `{Tax.Due}` resolved). (Also: `ad_role` went lazy mid-session under the reshard lane — source gate roles
  from stable `ad_full.db`.)

## THE CONTRACT TO SATISFY (what `post_resolver` reads — `scripts/post_resolver.js` TOKENS)
A migrated tenant db must emit, keyed by `(key, c_acctschema_id)`:
- `{BPartner.Receivable}` ← `c_bp_customer_acct.c_receivable_acct` (key `c_bpartner_id`)
- `{Product.Revenue}` / `{Product.Cogs}` / `{Product.Asset}` ← `m_product_category_acct.{p_revenue,p_cogs,p_asset}_acct`
  via `product → m_product.m_product_category_id`
- `{Tax.Due}` ← `c_tax_acct.t_due_acct` (key `c_tax_id`)
- the resolved value is a `C_ValidCombination` id → `c_validcombination.account_id` → `c_elementvalue` (value/name)
- plus `c_acctschema_default` (the fallback row) and the master keys (bpartner→group, product→category).
So emit: `c_bp_customer_acct`, `m_product_category_acct`, `c_tax_acct`, `c_acctschema_default`,
`c_validcombination`, `c_elementvalue` (+ ensure docs carry `c_bpartner_id`/`m_product_id` linkage).

## TWO PATHS
- **iDempiere (Client 13) — SAME schema, the easy half.** The migration extract must PULL the acct-config tables
  (mirror the acct-config section of `scripts/extract_fact_acct.sh`: `c_bp_customer_acct`, `m_product_category_acct`,
  `c_tax_acct`, `c_validcombination`, `c_elementvalue`, `c_acctschema_default` — INT-typed to match the integer keys).
  Mostly a query-list addition to the tenant extractor + re-shard. Then the views render directly.
- **Odoo (Client 12) — DIFFERENT schema, the real work.** Odoo has no AD posting tables; it has
  `account.account` (code/name), and account PROPERTIES on the partner/category (VERIFY exact field names against the
  live `odoodemo` schema the Odoo-fold already connects to — likely `res.partner.property_account_receivable_id`,
  `product.category.property_account_income_categ_id`/`property_account_expense_categ_id`). The existing Odoo-fold
  (`gen_ad_odoo.js` / the browser O2C fold, `§ODOO-MIGRATE-BROWSER mapped=5/5`) maps the CHAIN but NOT accounts.
  EXTEND it to map the Odoo CoA + partner/product account properties → the `c_bp_customer_acct` /
  `m_product_category_acct` / `c_validcombination` / `c_elementvalue` equivalents. EXTRACT, don't invent — every
  account from a real Odoo column.

## WITNESS (the proof it works — run the GL verb on the migrated tenant db)
`§MIGRATE-POSTCFG client=12|13 doc=C_Order id=<n> tokens_resolved=N/N coverage=complete balanced=Y` — i.e.
`doc_poster.derivePostings(tenantDb, {table:'C_Order', id}, schema)` returns `coverage:complete`, balanced, all
tokens resolved. Where the tenant has its OWN posted GL (Odoo `account.move.line` / iDempiere `fact_acct`), diff
the derived journal against it to the cent (the diff-oracle discipline) → promote to oracle-equivalent.
`§FALSIFIER` drop one emitted acct-config row → a token goes absent → coverage drops (the config is load-bearing).

## COORDINATION
- Owning arc: `prompts/MIGRATE_INSTALL_TENANT.md` (Client 12 Odoo / 13 iDempiere install). This card is its
  posting-config dependency. Method/discipline: `prompts/MIGRATION_CAMPAIGN_RESUME.md` (one diff-oracle at a time).
- Consumes (FROZEN, do NOT fork): `scripts/doc_poster.js` (W-DOC-POSTER), `scripts/post_resolver.js`. Once config
  resolves, `bim-ootb/erp/erp_preview.js` (Posting-Preview) + `accts_posted.js` (Accts-Posted) render with zero
  further UI work — that is the whole point of keeping them tenant-agnostic.
- See [[project_posting_preview]] (the GL-view lane + the `preview_demo.db` GardenWorld unblock).

## STOP CONDITION
A migrated Client 12 (Odoo) AND/OR Client 13 (iDempiere) tenant db carries resolvable posting config; the GL verb
returns `coverage:complete` + balanced on a real tenant order (`§MIGRATE-POSTCFG`); where the tenant's own GL exists,
the derived journal diffs to the cent; nothing invented (every account traced to a real tenant column). If a step
needs a user fact that can't be EXTRACTED → `⛔ BLOCKED: <the one question>` and move on.

---

# DONE — 2026-06-12 (2 parallel lanes + serial deploy train; user GO "go all"/"proceed")

## iDempiere half (seed + Client 13) — W-MIGRATE-POSTCFG (`build/erp/poc_migrate_postcfg_idmp.log`, exit 0)
- PREMISE CORRECTION: the six acct-config tables were ALREADY pulled (they flow via `scripts/ad_seed_manifest.json`
  → `export_ad_seed.js`; the "pulls NONE" evidence was a grep of the wrapper only). True gaps: seed lacked
  `fact_acct`; Client-13 lacked `fact_acct_id` PK banding.
- `export_ad.sh` extended: fact_acct pull (client 11, `idempiere_test` — `idempiere` shows 0 posted; doc-id sets
  verified identical across both PG dbs) + `§EXPORT postcfg` FAIL-LOUD audit (7 resolver tables present+non-empty,
  linkage 8/8 + 27/27, TB line). `gen_ad_idmp.sh`: `fact_acct_id:fact_acct` added to re-band FAMILIES — un-banded
  PK collided with seed rows → INSERT OR IGNORE silently dropped the tenant ledger (the #5-install bug class);
  `§REBAND fact_acct.fact_acct_id +1300000 rows=300`.
- `§MIGRATE-POSTCFG client=11 doc=C_Order id=100 tokens_resolved=3/3 coverage=complete balanced=Y basis=invoice`
  · `client=11 oracle=fact_acct(318) invoice=100 rows=3 maxDiff=0c` · same pair for client=13 (id=1300100)
  · both: `fact_acct rows=300 ΣDR=46574.97 ΣCR=46574.97 TB-balanced=Y`
  · `§FALSIFIER dropped c_bp_customer_acct rows=2 (bp=112) → coverage=partial({BPartner.Receivable})`.

## Odoo half (Client 12) — W-MIGRATE-POSTCFG-ODOO (`build/erp/poc_migrate_postcfg_odoo.log`, exit 0)
- Generator = `build/erp/gen_ad_odoo.js` (NOT scripts/). Odoo 17 facts (live odoodemo): properties live in
  `ir_property` (all company-level, res_id NULL — ORM-read so per-record overrides on richer tenants ARE honored).
- §5d honesty fixes: `{Product.Asset}` ← `property_stock_valuation_account_id` (was a COPY of expense — the one
  invented token, now real) · tax ← `account_tax_repartition_line.account_id` (config column, not posted-AML
  observation) · `c_acctschema_default` ← company defaults. `§MIGRATE-POSTCFG-EMIT … (0 invented)`.
- `§MIGRATE-POSTCFG client=12 doc=C_Order id=1200001 tokens_resolved=5/5 coverage=complete balanced=Y
  sumDr=5002.50 sumCr=5002.50` · `§LINKAGE orders=27/27 orderlines=56/56 OK` ·
  `§FRAME-FIT … oracle=live odoodemo maxDiff=0c verdict=ORACLE-EQUIVALENT` · `§FALSIFIER … loadBearing=Y`.

## Deploy train (bim-ootb PR #271, squash 81dd2b3, MERGED + live-verified)
- Ships erp/ad_seed.db (26,144,768 B) + 13-idempiere.db (552,960 B) + 12-odoo.db (217,088 B); sw v652→v653;
  IDB ad_seed_v14→v15 (idempiere.html ×5 + erp.html ×2; glassbowl has no ad_seed refs). Orphan-checked
  (ls-tree sizes + sw grep); live: `§LIVE-POSTCFG … fact_acct rows=300 … TB-balanced=Y → PASS`.
- LIVE FLIPS: `§POS-CENT live db=ad_seed.db order=910001 coverage=complete balanced=Y Dr=137.75 Cr=137.75
  cartCents=13775 maxDiff=0c` (POS matrix row §5 bar met — FULLY lit) · `§AD-PROC-LIVE proc=310 "Trial Balance"
  dispatched=Y ok=Y rows=21` (honest-empty residual closed; poc_ad_process_live.js seed-gap assertion updated
  to the new truth — the one sanctioned witness edit). Regressions W-AD-DOCFSM-LIVE / W-POS-LIVE green.
- Banked: matrix POS row + AD_Process residual + new W-MIGRATE-POSTCFG evidence row (tally unchanged);
  lane-master 06-12b handoff; PROGRESS; bim-compiler commits 0986251b + bank commit.

## Residuals (named)
- Posting-Preview / Accts-Posted now resolvable on the DEFAULT db — on-screen visual confirm pending (log≠visual).
- `c_acctschema_default` acct columns are provenance-only (resolver re-queries token tables under the default schema).
- odoodemo has zero per-record property overrides — extractor reads via ORM, so richer tenants are covered by design.
- Reband §A static log text still says "fact_acct skip = by design" — stale message-only, assertions correct.
