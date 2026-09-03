# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: migrated tenants must FIT the full AD model frame
# Lane: make the iDempiere (Client 13) + Odoo (Client 12) Install/Migrate produce a tenant that loads into the
#   SAME canonical AD model frame GardenWorld (Client 11) uses — so EVERY renderer feature works on the migrated
#   tenant with zero per-tenant code: AD fields, the AD `Posted` Button field + its AD-gated accounting view
#   (Accts-Posted / Posting-Preview), per-document reports/process, lifecycle. "One engine, N dictionaries."
# STATUS: ODOO (Client 12) FRAME-FIT ✅ HEADLESS-PROVEN (2026-06-10). Consolidates + sequences three existing
#   lanes (see §COORDINATION) against the deployed model frame (#232 v625 + #235 v628).
#
# ✅ DONE — ODOO CLIENT 12 frame items 1-3 fit, ORACLE-EQUIVALENT (the deep/hard half, "the real work"):
#   • Extended the Odoo tenant generator (SOURCE-OF-TRUTH `build/erp/gen_ad_odoo.js`, synced to
#     `~/bim-ootb/erp/tests/gen_ad_odoo.js` + the artifact `…/erp/12-odoo.db`) to EXTRACT + emit the posting
#     config + richer documents from REAL Odoo columns (§1b pull, §5d emit): c_elementvalue (4 real accounts:
#     121000/400000/600000/251000) · c_validcombination · c_bp_customer_acct (partner.property_account_receivable_id)
#     · m_product_category_acct (category income/expense properties) · c_tax_acct (sale-tax GL account off the posted
#     AML) · c_acctschema_default · c_invoiceline+c_invoicetax+c_ordertax. Keyed on c_acctschema_id=101 (the FROZEN
#     consumer's default schema) → views light up with ZERO consumer change. 0 invented (every account a real Odoo col).
#   • Frame items 1 & 2 come FREE: the tenant's canonical C_Order/C_Invoice bind to the SHARED AD dictionary, which
#     already carries the `Posted` AD-button (AD_Reference=28) on those tables → _isPostingDoc fires structurally.
#   • WITNESS `scripts/poc_migrate_postcfg.js` → `build/erp/poc_migrate_postcfg.log` (exit 0):
#       §MIGRATE-POSTCFG client=12 doc=C_Order id=1200001 tokens_resolved=3/3 coverage=complete balanced=Y $5002.50=$5002.50
#       §FRAME-FIT … oracle=live odoodemo maxDiff=0c verdict=ORACLE-EQUIVALENT  (derived journal == Odoo account.move.line)
#       §FALSIFIER dropped c_bp_customer_acct → {BPartner.Receivable} absent, unbalanced → config is load-bearing
#     (FROZEN engine doc_poster/post_resolver UNTOUCHED; witness is non-destructive — falsifier on a tmp copy.)
#   NOTE: ad_seed.db lost its ad_role table (a parallel reshard race) → generator bases on stable build/erp/ad_full.db.
#
# ✅ DONE — iDempiere CLIENT 13 path (the easy/same-schema half), also ORACLE-EQUIVALENT (user: "it is all there"):
#   The iDempiere PG is the live docker `postgres` container (db `idempiere_test` carries the posted fact_acct oracle;
#   user adempiere — see memory reference_idempiere_source). iDempiere IS the AD schema, so "migration" = EXTRACT via
#   the EXISTING install routine (`scripts/migrate_pg_to_sqlite.js`, the script that "passed before") + RE-KEY 11→13.
#   • `build/erp/gen_ad_idmp.sh` (deterministic, re-runnable): pulls the engine read-set + AD frame + fact_acct from
#     idempiere_test, re-keys GardenWorld(11)→Client 13 "iDempiere" (System(0) untouched). Artifact `build/erp/13-idempiere.db`
#     (synced to `~/bim-ootb/erp/13-idempiere.db`): orders=8 invoices=8 bp_cust_acct=36 validcombinations=158 fact_acct=300.
#   • WITNESS `scripts/poc_idmp_frame_fit.js` → `build/erp/poc_idmp_frame_fit.log` (exit 0):
#       §FRAME-FIT client=13 doc=C_Order coverage=complete balanced=Y oracle=fact_acct(3) maxDiff=0c → ORACLE-EQUIVALENT
#       (derived 518 DR 50.35 / 758 CR 47.50 / 596 CR 2.85 == iDempiere's own posted fact_acct for invoice 100)
#       §FALSIFIER dropped c_bp_customer_acct → token absent, unbalanced → config load-bearing.
#   BOTH paths now satisfy the AND/OR stop condition; engine (doc_poster/post_resolver) UNTOUCHED throughout.
#
# ▶ REMAINING (browser consummation — separate GO-gated lane, NOT this card's core): driving the DEPLOYED AD-gated
#   view (`idempiere.html?db=12-odoo.db` → Posting-Preview pill) is BLOCKED in THIS working copy because the
#   Posting-Preview SEAM itself (erp_preview.js/doc_poster.js/post_resolver.js + the #235 AD-gate) is NOT in
#   ~/bim-ootb/erp here — that is project_posting_preview §8 "GO-gated deploy". Once that seam ships, the
#   frame-complete 12-odoo.db (already synced) renders coverage:complete to the cent with no further work.
# NON-NEGOTIABLE: EXTRACT/MAP, never invent — every AD row + every account comes from a REAL source column
#   (iDempiere AD / Odoo model+properties). Spec-first; §-log first (READ the log); delegate-to-install (the
#   browser never touches the source DB); reuse the FROZEN engine (doc_poster / post_resolver / erp_preview) — the
#   views light up automatically once the frame fits. Doctrine: docs/ERP.md · docs/DistributedERP.md · docs/HolyGrail.md.

---

## WHY NOW (the model frame just became load-bearing)
The accounting view is AD-DRIVEN as of #235: a pill (and soon the `Posted` field-button) surfaces ONLY where AD
defines the `Posted` column (`AD_Reference=28`, the posting documents). So a migrated tenant is no longer "good
enough" with just its O2C documents — to fit the frame its documents must plug into the canonical AD dictionary
AND carry the posting model. Today they do not:
- **`12-odoo.db` (Client 12 Odoo) — proven gap:** has `c_order`/`c_invoice` (docs) but NO `Posted` field, NO posting
  config (`c_bp_customer_acct`/`m_product_category_acct`/`c_validcombination`/`c_elementvalue` ABSENT), so the
  AD-gate never fires and the view would be empty even if it did.
- **Install is PLAN/not-started** (`MIGRATE_INSTALL_TENANT.md`) — Migrate is still verify/preview, not a real
  logged-in Client 12/13.

## THE FULL MODEL FRAME a migrated tenant MUST carry (the checklist)
1. **AD metadata frame** — the canonical AD dictionary (AD_Window/Tab/Field/Column/Reference/Reference_List/
   AD_DocType) REUSED (shared engine), with the tenant's documents bound to it. Not re-authored per tenant.
2. **Posting-document definitions** — the posting tables (C_Invoice / M_InOut / C_Payment / GL_Journal / …) carry
   the `Posted` AD Button field (`AD_Column.ColumnName='Posted'`, `AD_Reference=28`) so `_isPostingDoc` is true and
   the AD-gated accounting view appears exactly where it should.
3. **Posting config** — `c_bp_customer_acct`, `m_product_category_acct`, `c_tax_acct`, `c_acctschema_default`,
   `c_validcombination`, `c_elementvalue` (+ master keys) so `post_resolver` resolves the tenant's accounts to the
   cent (the full token contract lives in `prompts/MIGRATE_POSTING_CONFIG.md`).
4. **Doc-types + lifecycle** — `C_DocType` rows so `completeIt`/DocAction/`Doc_*` config-gated fan-out work.

## TWO PATHS (deepest-first)
- **iDempiere (Client 13) — SAME schema, the easy half.** The source already IS the AD frame. The install/extract
  must pull the AD frame slice for the tenant's docs + the posting config + the doc-types (mirror + extend
  `scripts/extract_fact_acct.sh`'s config section, INT-typed keys). Then it loads as Client 13 and fits 1–4 for free.
- **Odoo (Client 12) — DIFFERENT schema, the real work.** The Odoo-fold (`gen_ad_odoo.js` / the browser O2C fold,
  `§ODOO-MIGRATE-BROWSER mapped=5/5`) maps the CHAIN but not the frame. EXTEND it to ALSO emit: (a) the `Posted`
  field on the mapped posting docs (so they register as posting documents); (b) the posting config from Odoo's CoA +
  partner/product account properties (VERIFY field names against the live `odoodemo`: likely
  `res.partner.property_account_receivable_id`, `product.category.property_account_income_categ_id`, `account.account`);
  (c) the `C_DocType` mapping. EXTRACT — every row from a real Odoo column.

## WITNESS (the proof the frame fits — drive the migrated tenant through the DEPLOYED views)
Load the migrated tenant in `idempiere.html` (`?db=<tenant>.db`), open a posting document:
- `§FRAME-FIT client=12|13 doc=<table> postingDoc=true previewPill=visible coverage=complete balanced=Y` — the
  AD-gate fires (table carries `Posted`), the accounting view resolves all tokens, balanced.
- Where the tenant has its OWN posted GL (Odoo `account.move.line` / iDempiere `fact_acct`), diff the rendered
  journal vs it to the cent (the diff-oracle discipline → oracle-equivalent).
- `§FALSIFIER` strip the `Posted` field on a doc → the gate stops firing (the AD rule is load-bearing).

## §COORDINATION (this card SEQUENCES three lanes against the deployed frame)
- `prompts/MIGRATE_INSTALL_TENANT.md` — the INSTALL (leave you logged in as Client 12/13). This card adds the
  "must fit the full frame" acceptance bar to that install.
- `prompts/MIGRATE_POSTING_CONFIG.md` — frame item 3 (the posting config). Subsumed here as one of four.
- `prompts/MIGRATE_ERP_PICKER.md` — the pick-your-ERP dialog + the LIVE Odoo fold to extend.
- Consumer (FROZEN, do NOT fork): `scripts/doc_poster.js` + `post_resolver.js` + `bim-ootb/erp/erp_preview.js` /
  `accts_posted.js`. The AD-gate is `idmp_pills.js` (showWhen:"posting-doc") + `idempiere.html` `_isPostingDoc`.
- See [[project_posting_preview]] (the deployed AD-gated view this frame feeds).

## METHOD / STOP CONDITION
Spec-first, §-log first (READ the log), EXTRACT-don't-invent (every AD row + account traced to a real source
column), one diff-oracle at a time. DONE when a migrated Client 12 (Odoo) AND/OR Client 13 (iDempiere) loads as a
real tenant whose posting documents fit frame items 1–4 and the DEPLOYED AD-gated accounting view renders
`coverage:complete` + balanced (diffed to the tenant's own GL where it exists) — `§FRAME-FIT`. If a step needs a
user fact that can't be EXTRACTED → `⛔ BLOCKED: <the one question>` and move on.
