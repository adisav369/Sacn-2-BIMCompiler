# ERP Model Archetype — MOrder is the core; the document family is deltas

**Companion to:** [ERP Coverage Matrix](ERP_COVERAGE_MATRIX.md) (the surface scoreboard) ·
[Migrate & Compare](MigrateComparisonPaper.md) (the conversion estimate — its [4-state status panel](MigrateComparisonPaper.md#status): 🟢 folds-today · 🟠 extraction · 🔴 fold-gap · 🔵 deleted-by-architecture) ·
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
| **Metadata (I/O)** | `C_Order`/`C_OrderLine` columns: type/reference/default/readonly/mandatory/display logic | `ad_evaluator` + `ad_access` + `ad_reference` + `ad_valrule` | ✅ **oracle-equivalent for the 3 SQL-grounded engines** (H-3: W-VALRULE-HARDEN 10/10 · W-REFERENCE-HARDEN 12/12 · W-ACCESS-HARDEN 15/15 — membership/verdict == live Postgres, diff=0). **Residual:** `ad_evaluator` display-logic verdict (live-render, parked UI bridge) |
| **`beforeSave` invariants** | PriceList_ID · M_Warehouse_ID · DateOrdered · C_DocTypeTarget_ID · BPartner id/location · credit-status | `ad_modelval` BEFORE_SAVE timing | ✅ **oracle-equivalent (W-MORDER-SAVE)** — `MOrder.java:1183-1396` as 11 cited hooks: 8/8 stored orders accept w/ 0 contradictions; Bill/Currency defaults re-derive stored 8/8; pricelist/term/doctype defaults = the Java query (EXPLICIT picks named); foreign location CLEARED (:1243); 4 reject paths fire (client=0 · warehouse+ctx-fallback · prepay+cash conjunctive · CannotChangePl). **Residual:** :1361 pricelist-version date twin (no `m_pricelist_version` in capture); credit-status lives at prepareIt (lifecycle row) |
| **`DocAction` lifecycle** | `prepareIt` · `completeIt` · `reopenIt` (+ the inherited void/close/reverseCorrect/reverseAccrual/reActivate set) over `DocumentEngine`/WfMC | `ad_process` + DocAction dispatch + `ad_docfsm.legalActionsOrder/transitionOrder` | ✅ **`completeIt` oracle-equivalent** (W-FOLD-COMPLETE chain · W-FOLD-INVOICE standalone) + **void/reverseCorrect oracle-anchored** (W-FOLD-REVERSE nets-to-zero, CO→RE) + **the FULL action FSM oracle-equivalent (W-MORDER-FSM)** — legal sets + outcomes == the runtime-PARSED `DocumentEngine.getValidActions:1008-1090`/action methods + MOrder deltas (prepay CO→WP; **RA not implemented on orders** :3042; completed orders offer CL/VO/(RE) only — the per-table narrowing), 23+12 fixtures + 8/8 stored replays diff=0. **Residual:** reverseAccrual booked-date n/a (RA unreachable on MOrder — proven, not deferred) |
| **Posting (GL derivation)** | `Doc_Order.java` debit/credit derivation from the lines + acct-config | `post_resolver` / `poc_post_derive` | ✅ **oracle-diffed**: `Doc_Invoice`(318) **sales AND purchase** (W-FOLD-AP-INVOICE: CR V_Liability / DR InventoryClearing, 4/4)/`Doc_InOut`(319)/`Doc_Payment`(335)/`Doc_AllocationHdr`(735, incl. tax-correction) all `maxDiff=0c` vs real `fact_acct` + **per-document LINE granularity closed (W-MORDER-POST)**: `Doc_Order` proper = the oracle's ZERO set gated by real `commitmenttype='N'` (falsifier flips the gate) and the whole order chain — incl. **receipts DR Asset / CR NotInvoicedReceipts @ qty×PO-price** — diffs per (record, line_id, account, side), 7/8 `diff=0c` (1 named drift). **Residual:** commitment-ON posting unreachable in seed (offset accts uncaptured, named) · allocation schema-200000 = a delta (folded, see matrix) |
| **Callouts** | qty/price/bpartner field-change derivations | `ad_callout` dispatch (mechanism, W-CALLOUT) | ✅ **oracle-equivalent (W-CALLOUT-HARDEN, H-3)** — `CalloutOrder.product` price derive 27/27 + `CalloutOrder.amt` 26/27 == live Postgres stored (1 named price-drift residual, the doc-109 pattern). **Residual:** the 139 unbound callout atoms (corpus, named) |

**"Got MOrder" means: this table fully GREEN to behavioural equivalence** — every cell oracle-diffed against a
running iDempiere, not merely surface-interpreted. That is the one archetype proof the backend arc should target.

**✅ REACHED (2026-06-11, the H-1 Fable lane — `prompts/FABLE5_MORDER_EQUIVALENCE.md`).** All five rows are
GREEN by diff: metadata via the H-3 SQL-grounded engines, beforeSave via W-MORDER-SAVE, the full DocAction FSM
via W-MORDER-FSM (oracle parsed from the checkout at runtime), posting via W-MORDER-POST at per-fact-LINE
granularity over the W-FACTACCT-DOC row-faithful capture, callouts via W-CALLOUT-HARDEN. Named residuals (not
gaps): `ad_evaluator` display-logic render verdict (UI bridge) · commitment-ON posting + pricelist-version date
gate (data absent in seed) · the 139 unbound callout atoms (corpus). The reusable oracle-diff TEMPLATE for H-2:
**stored-state oracle + runtime source-parse + live-PG row-diff + a load-bearing §FALSIFIER per surface.**

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

**✅ H-2 CONFIRMED-BY-DIFF (2026-06-11, the Fable H-2 lane — `prompts/FABLE5_H2_DELTAS.md`).** The deep
deltas are now WALKED, not assumed: `MInOut` (save W-MINOUT-SAVE + FSM W-MINOUT-FSM, 9 stored docs),
`MInvoice` (W-MINVOICE-SAVE/-FSM, 8 docs), `MPayment` (W-MPAYMENT-SAVE/-FSM, 2 docs — the whole seed,
stated), `MMovement`+`MInventory`+`MProduction` FSM (W-MINVENTORY-FAMILY-FSM; Movement's 1 doc replayed,
Inventory/Production = source-parse only, no-seed ⛔ stated). Each = the H-1 template verbatim: stored-state
oracle + gate-aware RUNTIME parse of `DocumentEngine.getValidActions`/the class's action methods + a
load-bearing §FALSIFIER. The MEASURED family fact the walk surfaced: the per-table Completed blocks carry
**three distinct gate nestings** — InOut/Movement/Inventory/Production (RC ⊂ periodOpen∧backDate),
Invoice (RE and RC both ⊂ the periodOpen frame), Payment (RC ⊂ periodOpen only) — and `reActivateIt` is
implemented ONLY on Invoice/Payment; `voidIt` on any processed family doc delegates to the reversal pair and
lands `RE` (DocumentEngine:616-618 preserves it). Posting for all walked classes was ALREADY oracle-folded
(table below) — cited, not redone.

**✅ ISOMORPH TAIL CONFIRMED-BY-DIFF (2026-06-11 — `prompts/H2_ISOMORPH_TAIL.md`).** The remaining family
rows are now walked with the SAME two witnesses: `MJournal`+`MJournalBatch` (W-MJOURNAL-FSM/-SAVE — the
shared Journal block, a fourth/fifth nesting: RC⊂periodOpen, RE⊂periodOpen∧canReact; journal voids ONLY
DR/IN, batch NEVER), `MAllocationHdr` (W-MALLOCHDR-FSM/-SAVE — no RE arm; beforeSave = ONE IsActive guard,
K=1 stated), `MCash` (W-MCASH-FSM/-SAVE — CO→VO only, and `reverseIt` sets Reversed ITSELF so **Void lands
RE even from DRAFT**; org←cashbook + ending-balance derives, stmtdiff == Σ real cashlines), `MBankStatement`
(W-MBANKSTMT-FSM/-SAVE — periodOpen frames BOTH RE and VO; the beginning-balance derive is STATE-DEPENDENT,
diffed against the captured master), and the **11-class generic-block tail** (W-GENERIC-TAIL-FSM/-SAVE —
`MRMA`/`MRequisition`/`MTimeExpense` replayed; `MBankTransfer`/`MDepositBatch`/`MProjectIssue`/the 5
Fixed-Assets classes source-parse-only, 0-seed ⛔ stated; MTimeExpense's beforeSave ABSENCE itself proven).
**No DocAction table in the family table below remains unwalked.**

**Oracle-folded so far (the deepest deltas first, each `§FOLD-COMPLETE … maxDiff=0c` vs real GardenWorld):**

| Delta | Witness | What folds, against which oracle |
|---|---|---|
| **MOrder.completeIt chain** | W-FOLD-COMPLETE | Order→Ship→Invoice fan-out == `m_inoutline`/`c_invoiceline` + `fact_acct(318/319)` |
| **MInvoice + MatchInv** | W-FOLD-INVOICE | `completeIt(C_Invoice)` CO + 18/18 `m_matchinv` junctions (per tuple); sales GL == `fact_acct(318)` |
| **MInvoice AP posting** (purchase manifest) | W-FOLD-AP-INVOICE | vendor `Doc_Invoice` CR `{Vendor.V_Liability}` / DR `{Product.InventoryClearing}` per line == `fact_acct(318)` (4/4 `maxDiff=0c`) |
| **MMatchInv posting** (clearing loop + IPV) | W-FOLD-MATCHINV | DR `{BPGroup.NotInvoicedReceipts}` / CR `{Product.InventoryClearing}` + avg-cost IPV split (on-hand-proportioned) == `fact_acct(472)` (**18/18** `maxDiff=0c`) |
| **MMatchInv FX** (2nd schema, EUR) | W-FOLD-MATCHINV-FX | same USD manifest, per-leg conversion (0.85 HALF_UP, BigInt) == `fact_acct(472)` schema-200000 (**18/18** `maxDiff=0c`) |
| **MPayment** (allocation engine, simple half) | W-FOLD-PAYMENT | Doc_Payment receipt == `fact_acct(335)` |
| **MAllocationHdr** (headerless, deep half) | W-FOLD-ALLOC | DR cash/disc/wo / CR receivable **+ VAT tax-correction sub-cents** == `fact_acct(735)` |
| **MAllocationHdr FX** (2nd schema, EUR) | W-FOLD-ALLOC-FX | per-leg currency conversion (0.85 HALF_UP) + CurrencyBalancing line == `fact_acct(735)` schema-200000 |
| **MStorageOnHand / MTransaction** | W-FOLD-QTYONHAND | on-hand = Σ(sign×qty) == `m_storageonhand` (20/20 cells) + sign rule (28/28) |
| **MMovement** (inter-org transfer) | W-FOLD-MOVEMENT | cost transfer + Intercompany Due-To/From == `fact_acct(323)`; cost via schema costing-method → cost element |
| **MMovement FX** (2nd schema, EUR) | W-FOLD-MOVEMENT-FX | same fold at the per-schema EUR cost (43.7325, full-precision line rounding) == `fact_acct(323)` schema-200000 (1/1 `maxDiff=0c`) |
| **ReplenishReport → PO** | W-FOLD-REPLENISH | QtyToOrder (movement-folded on-hand) == iDempiere formula (8/8); PO via `buildDoc` |
| **Reversal family** (reverseCorrect / void) | W-FOLD-REVERSE | engine reversal (swap Dr↔Cr) **+ real `fact_acct` NETS TO ZERO** per account (6/6 C_Payment+C_Invoice); FSM CO→RE; ORACLE-ANCHORED (transform of real posting) |
| **GL_Journal** (manual + inter-org) | W-FOLD-GLJOURNAL | direct lines (amtacct=amtsource×rate) + per-org Intercompany Due-To/From balancing == `fact_acct(224)` (2/2, both schemas) |
| **MProduction backflush** | W-FOLD-BACKFLUSH | recursive BOM explosion == path-enumeration (recipe-equivalent; `m_production`=0 in seed) |
| **MProduction movement** | W-FOLD-PRODUCTION | enacted P+/P- ledger folds through the qty spine (finished +Q / leaf −used); rule-consistent, GL named-deferred (component cost absent) |
| **MInventory count** | W-FOLD-INVENTORY | enacted I± folds on-hand→counted + GL `\|adjQty\|×cost` balances; rule-consistent, offset acct named-deferred |
| **MInOut save + FSM** (H-2.1) | W-MINOUT-SAVE / W-MINOUT-FSM | beforeSave 5-hook port == 9 stored docs (movement-type/delivery-rule MUST; salesrep = measured source-evolution drift) + per-table FSM == gate-aware runtime parse (154 fixtures; RC ⊂ periodOpen∧backDate, RE not implemented, VO@CO→RE) |
| **MInvoice save + FSM** (H-2.2) | W-MINVOICE-SAVE / W-MINVOICE-FSM | beforeSave 8-hook port == 8 stored docs (setBPartner so/po flavors, ARI\|API target, EUR currency-rate gate via captured ad_clientinfo) + the NESTED periodOpen gate frame (145 fixtures; RE implemented→IP) |
| **MPayment save + FSM** (H-2.3) | W-MPAYMENT-SAVE / W-MPAYMENT-FSM | beforeSave 10-hook port == 2 stored docs (K=2 honest; CASH_AS_PAYMENT sysconfig gate, org←bank-account, BP==invoice's BP) + the third gate nesting (101 fixtures; RC ⊂ periodOpen only) |
| **Inventory-family FSM** (H-2.4) | W-MINVENTORY-FAMILY-FSM | Movement+Inventory share one source block; Production same shape (161 fixtures); Movement's 1 doc replayed + doctype-default derive; Inventory/Production stored-replay ⛔ no-seed (stated) |
| **GL Journal family save + FSM** (isomorph tail) | W-MJOURNAL-SAVE / W-MJOURNAL-FSM | 8+3-hook port == 2 journals + 1 batch (period 155/category 108/schema 101/convtype 114 MUST; ParentComplete reject on the REAL processed batch) + the shared Journal block (207 fixtures; RC⊂periodOpen, RE⊂periodOpen∧canReact; journal voids ONLY DR/IN, batch never) |
| **MAllocationHdr save + FSM** (isomorph tail) | W-MALLOCHDR-SAVE / W-MALLOCHDR-FSM | the ONE IsActive guard (K=1 stated) == 2 stored docs + the Allocation block (100 fixtures; RC⊂periodOpen, RA, NO RE arm; VO@CO→RE delegation) |
| **MCash save + FSM** (isomorph tail) | W-MCASH-SAVE / W-MCASH-FSM | org←cashbook + ending=beginning+stmtdiff (==Σ real cashlines) == 3 stored docs + the smallest block (34 fixtures; CO→VO only — and reverseIt sets Reversed itself → **VO lands RE even from DR**) |
| **MBankStatement save + FSM** (isomorph tail) | W-MBANKSTMT-SAVE / W-MBANKSTMT-FSM | CMB-doctype + balance derives == 2 stored docs (the beginning-balance derive STATE-DEPENDENT, diffed vs the captured master) + the fourth nesting (99 fixtures; periodOpen frames BOTH RE and VO; RC/RA never) |
| **Generic-block tail FSM + save** (isomorph tail) | W-GENERIC-TAIL-FSM / W-GENERIC-TAIL-SAVE | 11 classes == the generic fall-through (333 fixtures; CO→[CL] only); RMA/Requisition/TimeExpense replayed + their beforeSave ports (shipment derives MUST; pricelist FALLBACK arm; the MTimeExpense ABSENCE proven); 8 zero-seed classes source-parse ⛔ stated |

**Score: 14 of the ~40 oracle-targets cent/unit-equivalent + 17 MODEL-LAYER surfaces (H-1 MOrder save/FSM/post/capture + H-2 the deep-family save/FSM walk + the ISOMORPH TAIL's 10 → ledger total FORTY-ONE oracle-equivalent rows in [ERP_COVERAGE_MATRIX.md](ERP_COVERAGE_MATRIX.md)) + 1 recipe-equivalent + 2 rule-consistent (enacted, no seed oracle)** — and they are the deepest deltas
(the whole Money family — sales AND purchase invoice posting, allocation in BOTH accounting schemas, the MatchInv
clearing loop **in both schemas** — + the inventory loop incl. the inter-org MMovement cost transfer **in both schemas**). **Logic-folded is no longer
~0.2%**: the trade-doc loop (order→ship→invoice→**match-posting**→pay→allocate) and the inventory loop
(movement→on-hand→replenish-PO) both fold end-to-end to their iDempiere oracle, on **both** the sales and purchase
sides of `Doc_Invoice` and across the base (USD) **and** foreign-currency (EUR) acctschema — and the PO
match→clearing loop folds in full incl. the avg-cost IPV split (on-hand-proportioned, riding the qty spine).
**Unfolded tail:** `MInventory` + `MProduction` **cost-valued GL** (movement folds proven
W-FOLD-INVENTORY/PRODUCTION; component-cost + offset-account DATA absent in seed → GL named-deferred, not faked) ·
the Fixed-Assets + GL/Project families' **save/FSM are now walked (isomorph tail)** — what remains there is the
POSTING of the classes with no seed documents (Cash/BankStatement/RMA/Requisition/TimeExpense/FA `Doc_*` posters,
0 `fact_acct` rows to diff) · the declarative surfaces (still surface-interpreted, not oracle-diffed).

## What this changes for the backend arc

1. **The honest denominator is small.** Not 496 classes / 735k LOC — it is **`MOrder` to equivalence + a 25-row
   delta table + a near-empty master-data tail.** Replaces "322 named-deferred overrides" (an unquantified IOU)
   with a finite, walkable list.
2. **The metric must split: *surface-interpreted* vs *oracle-equivalent*.** Today the matrix is 37🟡 = surfaces
   *touched*. **Exactly one is oracle-diffed** — the trial-balance read (`test_report_fin.js` reproduces the real
   GardenWorld `fact_acct`, 300 rows, `maxDiff=0c`); everything else is touched-but-undiffed. The archetype proof = make the MOrder table above
   GREEN by **differential test** (the same discipline the Odoo/SAP fold POCs already use — extend it to the model
   layer), then each family row is a delta-diff, not a port.
3. **Sequence:** nail `MOrder` (events + full DocAction set + `Doc_Order` posting) to oracle-equivalence → then
   walk the 25-row delta table, deepest-delta-first (`MInOut`/`MPayment`/`MProduction`) → the master-data tail is
   largely the declarative engines already shipped.

**Bottom line:** the Java→JS migration is going the right way for the *declarative* layer (extract the AD, interpret
it). The missing rigour is (a) one archetype proven to *equivalence* not *touch*, and (b) the family expressed as
*deltas* off it. Both are bounded and finite — which is exactly the practitioner's point: get `MOrder`, the rest is
more of the same, and now there is a table that says how much more.
