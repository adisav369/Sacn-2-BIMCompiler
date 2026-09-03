# ⚠ DO NOT REMOVE — BIM → ERP bridge: element↔ERP read drawer + Construction→ERP clean export
# SCOPE: wire the IFC GUID join between a served building and the ERP.db (build/erp/ad_*.db).
#   (A) READ — select an element → a data-gated "Check ERP" chip lights on the info panel ONLY when
#   a real linked record/cost exists; tapping it opens a drawer of M_Product/price/A_Asset/on-hand.
#   (B) WRITE — "Construction → ERP" clean export (an Export-icon child) find-or-creates M_Product,
#   upserts M_ProductPrice on the active M_PriceList, creates A_Asset per GUID, sets on-hand by UOM.
# STATE (2026-06-05): PAPER ONLY. Spec = docs/BIMtoERP.md (read it first, update it as you build).
#   Nothing implemented. Three OPENs must be answered before code (see below).
# NON-NEGOTIABLE: EXTRACT/COMPILE ONLY — never invent a product, price, qty, or asset. Money + qty
#   via site/bigdecimal.js ONLY (== Java BigDecimal, proven). Chip/affordance appears ONLY when its
#   query returns rows (non-invent gate, same as W-LENS-PROBE). Export is idempotent (re-run = +0).
#   Whitebox §-log FIRST is the value proof; Playwright only drives/captures. Read the log after every
#   run. Every claim names a witness. Route shortcuts/buttons through the action fn, not a DOM .click()
#   (lesson from the '=' fix). Three Concerns stay split (WHAT/HOW/WHERE — BOM PRINCIPLE).
# READ FIRST: docs/BIMtoERP.md (the blueprint) · docs/ERP.md (ERP engine blueprint) · build/erp/
#   (ERP source-of-truth — edit ERP only here) · viewer picking.js + panels.js (element select →
#   info panel) · viewer kernel_ops.js (the OpLog / per-element cost) · rates/*.json (5D cost packs,
#   currency-tagged) · memory feedback_numbers_via_bigdecimal · memory feedback_erp_source_of_truth.

---

## TASK 0 — Confirm the OPENs (one query each, before any UI) · Witness W-ERP-SCHEMA
Against the canonical `build/erp/ad_*.db`:
- OPEN-1: which column on `A_Asset` carries the IFC GUID (SerNo / dedicated col / AttributeSetInstance)?
- OPEN-2: `M_StorageOnHand` or `M_Storage`? (grep saw `M_Storage` in the seed — verify the live one.)
- OPEN-3: which `ad_*.db` is canonical for the viewer to read/write?
- **W-ERP-SCHEMA:** `§ERP_SCHEMA db=<file> guid_col=<col> onhand=<table> uom_rows=<n>` — all named, not guessed.

## TASK A1 — "Check ERP" probe + chip (read-only) · Witness W-ERP-PROBE
On element select, probe (cost in OpLog) || (A_Asset by GUID) || (M_Product by Type→Value).
Light the chip iff true. Plain element → no chip.
- **W-ERP-PROBE:** `§ERP_PROBE guid=<g> cost=<bool> product=<bool> asset=<bool>` — chip visible == OR of the three; absent when all false.

## TASK A2 — "Check ERP" drawer · Witness W-ERP-DRAWER
Tap chip → drawer (match existing panel style) showing M_Product, M_ProductPrice.PriceStd
(BigDecimal), A_Asset row(s), on-hand by UOM. All READ from ERP.db + OpLog.
- **W-ERP-DRAWER:** `§ERP_DRAWER guid=<g> product=<Value> price=<bd> uom=<EA|M|M2|M3> asset=<n>` — price folds exact == golden.

## TASK B — "Construction → ERP" clean export · Witness W-ERP-EXPORT / W-ERP-FOLD
Per element/Type: resolve UOM from QTO (count→EA, len→M, area→M2, vol→M3; skip if none) →
find-or-create M_Product(Value=Type) → upsert M_ProductPrice (rate×unit via bigdecimal.js, from
rates pack) → find-or-create A_Asset per GUID → set on-hand qty. Idempotent (keyed on Value/GUID).
- **W-ERP-EXPORT:** `§ERP_EXPORT type=<T> products=+<np> prices=+<npr> assets=+<na> onhand=+<ns>` — second run = all +0.
- **W-ERP-FOLD:** Σ exported asset value folds exact == Σ(qty×PriceStd) golden.

## TASK C — Export chooser (reusable component) · Witness W-EXPORT-CHOOSER
`report` (panels.js) opens a small **multi-select chooser** with two sections —
*Model/Data*: ☐IFC ☐BCF ☐DB ☐ERP · *Reports*: ☐4D ☐5D ☐DXF ☐Excel — + [All] → one Export fires
the ticked `fn`s (bundle when >1). Build it format-agnostic — a list of `{id,label,fn}` selectables
+ All — so Share / the future Drop-IFC flow can mount the SAME component (subset of tiles).
"Check ERP" (A) stays on the info panel, NOT here. No new top-level keys; route via fn (the `=` lesson).
- **W-EXPORT-CHOOSER:** `§EXPORT_CHOOSER picked=<ids> fired=<n>` — every ticked tile's fn runs (effect-level, not routing-only — the 42.1 lesson); [All] picks every tile.

## DEPLOY / TEST
Localhost only until EXPLICIT GO. Whitebox §-log first; add effect-level specs (not routing-only).
`node tests/audit_specs.js` must not add violations. Update docs/BIMtoERP.md §status as built.
ERP writes go to build/erp/ ONLY (source-of-truth); never touch deploy/live/.
