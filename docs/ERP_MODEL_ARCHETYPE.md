# ERP Model Archetype — MOrder is the core; the document family is deltas

**Companion to:** [ERP Coverage Matrix](ERP_COVERAGE_MATRIX.md) (the surface scoreboard) ·
[Migrate & Compare](MigrateComparisonPaper.md) (the conversion estimate) ·
[HolyGrail §Abstracting the DocAction corpus](HolyGrail.md) · [ERP Backend Separation](ERP_BACKEND_SEPARATION.md).

**Why this page exists.** The coverage matrix counts ~735,200 LOC across **496 `M*` model classes** (+703 `X_*` AD
stubs) and risks reading as a 735k-LOC mountain to port. That is the *raw* count, not the *real denominator*. An
iDempiere practitioner's framing (2026-06-09): **"as long as you have `MOrder`, you have the core."** The 496
classes are not 496 problems — they collapse to **two archetypes plus AD_Column metadata**, and everything else is
a *delta*, not novel code. This page maps that — so "the rest is more of the same" is **shown, not asserted**.

## The radius — two foci span the whole model

| | The radius | Spanned by | Our engine home |
|---|---|---|---|
| **Input / Output** | what a record IS + how it persists/renders | **`AD_Table` + `AD_Column`** (the dictionary) | `ad_evaluator` (logic) · `ad_access` (security) · `ad_valrule` (validation) · `ad_reference` (FK/format) — **declarative, already interpreted** |
| **Process** | what a document DOES over its life | **`DocAction` over WfMC**, with the real logic in **`MOrder` events** | `ad_process`/DocAction dispatch + `ad_modelval` timing hooks + `post_resolver` GL derivation |

Every class is one of two shapes inside that radius:
- **The document archetype** — `MOrder` (header `C_Order` + line `C_OrderLine`), a `DocAction` lifecycle, `beforeSave`/`afterSave` events, and a posting deriver. **25 classes** are this shape (every class with `completeIt`).
- **The master-data record** — `MProduct`/`MBPartner`-like: essentially `AD_Column` metadata + a couple of light invariants. The **~470 remaining** classes are mostly this — closer to pure dictionary, already covered by the declarative engines.

So the migratable denominator is **1 document archetype (fully captured) + ~25 document deltas + a near-empty master-data tail** — not 496 classes.

## The MOrder archetype — its surface, and the engine hook that covers each

`MOrder.java` = 3,287 LOC. Decomposed (every item below extracted from the checkout, not invented):

| MOrder surface | Real content (from MOrder.java) | Engine hook | Equivalence status |
|---|---|---|---|
| **Metadata (I/O)** | `C_Order`/`C_OrderLine` columns: type/reference/default/readonly/mandatory/display logic | `ad_evaluator` + `ad_access` + `ad_reference` + `ad_valrule` | 🟡 surface-interpreted on real AD rows; **not oracle-diffed** |
| **`beforeSave` invariants** | PriceList_ID · M_Warehouse_ID · DateOrdered · C_DocTypeTarget_ID · BPartner id/location · credit-status | `ad_modelval` BEFORE_SAVE timing | 🟡 mechanism built; `hasLines`/`total≥0` ported; **the pricelist/credit/warehouse derivations = the delta** |
| **`DocAction` lifecycle** | `prepareIt` · `completeIt` · `reopenIt` (+ the inherited void/close/reverseCorrect/reverseAccrual/reActivate set) over `DocumentEngine`/WfMC | `ad_process` + DocAction dispatch | 🟡 `CO` proven; **the full transition set = the delta** |
| **Posting (GL derivation)** | `Doc_Order.java` debit/credit derivation from the lines + acct-config | `post_resolver` / `poc_post_derive` | 🟡 derivation proven on `C_Invoice`; **`Doc_Order` itself = a delta** |
| **Callouts** | qty/price/bpartner field-change derivations | `ad_callout` dispatch (mechanism, W-CALLOUT) | 🟡 spine built; **the bound callout classes = the delta** |

**"Got MOrder" means: this table fully GREEN to behavioural equivalence** — every cell oracle-diffed against a
running iDempiere, not merely surface-interpreted. That is the one archetype proof the backend arc should target.

## The document family — the ~25 isomorphs, each a measured delta from MOrder

Every class below has `completeIt` (the document shape). The delta from MOrder is its **line table**, its
**DocAction specifics**, and its **posting deriver** (the 20 `Doc_*` in `org.compiere.acct`). Grouped by family:

| Group | Classes (LOC) | Delta from the MOrder archetype | Poster |
|---|---|---|---|
| **Trade** | `MOrder` 3287 · `MInvoice` 3632 · `MInOut` 3632 · `MRMA` 1008 | the canonical pattern; MInOut adds **in-transit locator + MovementDate**; MInvoice adds matching | Doc_Order/Invoice/InOut |
| **Money** | `MPayment` 3336 · `MAllocationHdr` 1097 · `MCash` 898 · `MBankStatement` 804 · `MBankTransfer` 468 · `MDepositBatch` 638 | MPayment adds the **allocation engine**; Allocation has **no line/header split** | Doc_Payment/AllocationHdr/Cash/BankStatement |
| **Inventory** | `MMovement` 1230 · `MInventory` 1354 · `MProduction` 1100 · `*Confirm` (InOut/Movement) | MInventory = **physical count**; MProduction = **BOM explosion** | Doc_Movement/Inventory/Production |
| **GL / Project** | `MJournal` 1090 · `MJournalBatch` 995 · `MProjectIssue` 596 · `MRequisition` 624 · `MTimeExpense` 613 | journal = direct fact lines (no derivation); requisition = pre-order | Doc_GLJournal/ProjectIssue/Requisition |
| **Fixed Assets** | `MAssetAddition` 1253 · `MAssetDisposed` 522 · `MAssetReval` 309 · `MAssetTransfer` 305 · `MDepreciationEntry` 432 | asset lifecycle; depreciation is the [DepreciationPerf](DepreciationPerf.md) batch | Doc_AssetAddition/Disposed/Reval/Transfer/DepreciationEntry |

**The non-trivial deltas (where "more of the same" must be *confirmed*, not assumed):** `MInOut` in-transit
locator, `MPayment` allocation, `MProduction` BOM explosion, `MInventory` physical count, `MAllocationHdr`
(headerless). These ~5 carry genuinely document-specific logic; the rest are the trade pattern with a different
line table and poster.

## What this changes for the backend arc

1. **The honest denominator is small.** Not 496 classes / 735k LOC — it is **`MOrder` to equivalence + a 25-row
   delta table + a near-empty master-data tail.** Replaces "322 named-deferred overrides" (an unquantified IOU)
   with a finite, walkable list.
2. **The metric must split: *surface-interpreted* vs *oracle-equivalent*.** Today the matrix is 37🟡 = surfaces
   *touched*. None is oracle-diffed against a running iDempiere. The archetype proof = make the MOrder table above
   GREEN by **differential test** (the same discipline the Odoo/SAP fold POCs already use — extend it to the model
   layer), then each family row is a delta-diff, not a port.
3. **Sequence:** nail `MOrder` (events + full DocAction set + `Doc_Order` posting) to oracle-equivalence → then
   walk the 25-row delta table, deepest-delta-first (`MInOut`/`MPayment`/`MProduction`) → the master-data tail is
   largely the declarative engines already shipped.

**Bottom line:** the Java→JS migration is going the right way for the *declarative* layer (extract the AD, interpret
it). The missing rigour is (a) one archetype proven to *equivalence* not *touch*, and (b) the family expressed as
*deltas* off it. Both are bounded and finite — which is exactly the practitioner's point: get `MOrder`, the rest is
more of the same, and now there is a table that says how much more.
