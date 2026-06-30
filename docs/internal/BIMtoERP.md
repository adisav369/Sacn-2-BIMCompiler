# BIM → ERP Bridge — Blueprint

> **Status:** PAPER spec (no code yet). Umbrella for the element↔ERP link.
> **Prime alignment:** EXTRACT OR COMPILE ONLY. The bridge *reads* what extraction
> produced and *writes* only what it can trace to an element. Never invent a product,
> a price, a quantity, or an asset. Money/qty math via `site/bigdecimal.js` only
> (see `memory feedback_numbers_via_bigdecimal`).
> **Three Concerns (BOM PRINCIPLE) stay split:** WHAT = `M_Product`/`M_Product_Category`;
> HOW = UOM + price + BOM rules; WHERE = the ERP.db (`build/erp/ad_*.db`).

Executable lane: `prompts/BIM_TO_ERP.md`. Related interop: `prompts/BCF_INTEROP.md`
(BCF rides the same Export icon). ERP engine source-of-truth: `build/erp/` (see `docs/ERP.md`).

---

## §0 — THE JOIN (the one fact everything hangs on)

The bridge key is the **IFC GUID** already carried as `elements_meta.guid` in every served
building DB. On the ERP side it must land on a **single, deterministic column** so read
and write agree. Candidate (CONFIRM in `build/erp/ad_*.db`):

- `A_Asset.SerNo` (or a dedicated `IFC_GUID` column / `M_AttributeSetInstance`) holds the
  element GUID → one physical occurrence = one asset row.
- `M_Product.Value` = the element's **Type/classification** (e.g. IfcDoor family-type, or a
  Uniclass/OmniClass code if present) → one product = N occurrences of that type.

So: **Type → `M_Product`** (the recipe), **occurrence(GUID) → `A_Asset`** (the instance).
That is exactly the BOM PRINCIPLE — one parent product, N child assets.

> **OPEN-1 (confirm before build):** which column carries the GUID on `A_Asset`?
> **OPEN-2:** is the warehouse table `M_StorageOnHand` (newer iDempiere) or `M_Storage`
> (grep found `M_Storage` in the seed)? Pick the one the canonical ERP.db actually has.
> **OPEN-3:** which `build/erp/ad_*.db` is the canonical ERP.db the viewer reads/writes?

---

## §A — READ side: "Check ERP" on a selected element (data-gated)

**Scenario.** User selects an element (single-select is cleaner than right-click — reuse the
existing `picking.js` → info panel path). The info-panel box shows its properties. If — and
only if — there is a **real linked record or a cost in the OpLog** for that GUID, a small
**"Check ERP"** chip *lights up* on the panel. Plain element with nothing behind it → no chip.
This is the non-invent gate, identical in spirit to the lens probe (`W-LENS-PROBE` in
`docs/RevitParity.md`): an affordance appears only when its query returns rows.

**Probe (what lights the chip).** At select time, run the cheap checks:
1. Does the OpLog (`kernel_ops`) / 5D path hold a computed cost for this GUID? AND/OR
2. Does an `A_Asset` (by GUID) or a matching `M_Product` (by Type→Value) exist in ERP.db?
If either is true → chip on, else chip stays hidden.

**Drawer (what the chip opens).** Tapping the chip slides a drawer (side or bottom — match the
existing panel/drawer style) showing the element's ERP facts, all *read* from ERP.db + OpLog:
- `M_Product` (Value, Name, Category, `C_UOM`)
- price from the active `M_PriceList` → `M_ProductPrice.PriceStd` (BigDecimal)
- `A_Asset` row(s) for this GUID (asset value, serial/GUID)
- on-hand qty (`M_StorageOnHand`/`M_Storage`) by UOM
- (later) linked documents — POs, maintenance — if the schema carries them

**Witnesses.**
- `W-ERP-PROBE`: `§ERP_PROBE guid=<g> cost=<bool> product=<bool> asset=<bool>` — chip
  visibility == (cost || product || asset). NO chip when all three false (the non-invent proof).
- `W-ERP-DRAWER`: `§ERP_DRAWER guid=<g> product=<Value> price=<BigDecimal> uom=<EA|M|M2|M3> asset=<n>`
  — every field traces to a row; price folds exact == golden.

---

## §B — WRITE side: "Construction → ERP" clean export (another Export-icon child)

**Scenario.** From a selected element (or all elements of a Type), "clean export" pushes the
construction reality into ERP.db, creating only what's missing. Per element/Type:

1. **Resolve UOM.** From the element's QTO: count→`EA`, length→`M`, area→`M2`, volume→`M3`
   (map to the real `C_UOM` rows). Skip any element with no resolvable UOM qty — non-invent.
2. **Find-or-create `M_Product`.** Look up by `Value` = Type/classification. If absent, INSERT
   `M_Product` (Value, Name, `M_Product_Category_ID`, `C_UOM_ID`). Deterministic Value derivation
   (no random IDs) so re-export is idempotent.
3. **Price → `M_ProductPrice`.** Upsert into the active `M_PriceList`/version:
   `PriceStd` = 5D pack rate × (unit) folded via `site/bigdecimal.js`
   (rates from `rates/*.json` — locale pack, currency-tagged; never the unlabeled
   `A.MATERIAL_COSTS` fallback). Currency follows the pack's `meta.currency`.
4. **Occurrence → `A_Asset`.** Find-or-create one `A_Asset` per element GUID
   (link column per §0/OPEN-1). Asset value = qty × PriceStd (BigDecimal).
5. **On-hand → `M_StorageOnHand`/`M_Storage`.** Set `QtyOnHand` by UOM at the chosen
   locator/warehouse (CONFIRM OPEN-2/locator).

**Idempotent + traceable.** Re-running export must not duplicate: find-or-create keyed on
`Value`/GUID. Every created row carries the GUID or Value that produced it. Nothing is written
that isn't extracted from the model.

**Witnesses.**
- `W-ERP-EXPORT`: `§ERP_EXPORT type=<T> products=+<np> prices=+<npr> assets=+<na> onhand=+<ns> uom=<...>`
  — counts == elements processed; second run reports `+0` (idempotent).
- `W-ERP-FOLD`: total exported asset value folds exact == sum(qty×PriceStd) golden (BigDecimal).

---

## §C — UI surface: the Export chooser (one reusable component)

The Export pill (`report` in panels.js — `barChart`, already `ui_tt_export`) opens a **small
chooser box**, not a one-shot. The chooser presents the model/data outputs as **multi-select**:

```
   Export ▸  ┌─────────────────────────┐
             │ Model/Data              │
             │ ☐ IFC   ☐ BCF           │
             │ ☐ DB    ☐ ERP           │
             │ Reports                 │
             │ ☐ 4D  ☐ 5D ☐ DXF ☐ Excel│
             │ ───────────────  [All]  │
             │            [ Export ]   │
             └─────────────────────────┘
```

- **IFC** = `ifc_export_worker.js` (the model out). **BCF** = `prompts/BCF_INTEROP.md` (issues +
  viewpoints). **DB** = the served building SQLite (`*_extracted.db`) as-is. **ERP** = the §B push.
- **Two sections, one chooser:** *Model/Data* (IFC·BCF·DB·ERP) + *Reports* (4D·5D·DXF·Excel).
- **Multi-select + [All]** — tick several across both sections (or All) → one bundle/zip in a single
  action. KISS: tiles are checkboxes; "All" ticks every tile; one Export button fires the selected `fn`s.
- "Check ERP" (§A) is NOT in this chooser — it lives on the element info-panel (per-selection read).
- Registry-native: build the chooser once, drive it from the action `fn` (route via fn, never a DOM
  `.click()` — the `=` lesson). No new top-level keys (no-clutter rule).

**Reuse — the same chooser serves "Share / Drop IFC" later.** Share and the planned Drop-IFC flow
ask the same question ("which artifact(s)?"), so the chooser is a **shared component**, not bolted
into Export alone. Build it format-agnostic (a list of `{id, label, fn}` selectables + an All) so
Share can mount it with the same or a subset of tiles. Tracked as a follow-on, not this lane's gate.

---

## §D — What this is NOT (scope fences)

- Not a live ERP sync / two-way daemon — it is a deterministic push + read against ERP.db.
- Not invented financials — price only from the 5D rates pack, qty only from QTO, BigDecimal only.
- Not a new geometry path — pure data join over `elements_meta.guid`.
- gbXML/COBie are siblings, not part of this lane (COBie reuses §B's product/asset rows later).

---

## §E — Build order (paper → code, when GO)

1. Confirm OPEN-1/2/3 against the canonical `build/erp/ad_*.db` (one query each).
2. §A probe + chip (read-only, lowest risk, proves the join).
3. §A drawer.
4. §B export (find-or-create, idempotent) behind the Export icon.
5. §C Export-parent consolidation + BCF child.
Each step lands with its witness `§`-line before the next. Whitebox first; Playwright only drives.
