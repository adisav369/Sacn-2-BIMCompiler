# ⚠ DO NOT REMOVE — scope: DISCOVERY witness for the full O2C+P2P business cycle on the live
# iDempiere surface (bim-ootb `erp/idempiere.html` + `erp/crud_overlay.js`). Read the log after every run
# (`build/erp/witness_e2e_business_cycle.log`, gitignored — regenerate via `bash build/erp/run_witness.sh
# scripts/witness_e2e_business_cycle.js` from bim-compiler). This is NOT a green-run hunt — a break IS the
# deliverable. Do not re-run "hoping" for a full pass; the findings below are the answer.

**2026-07-20** — witness: `scripts/witness_e2e_business_cycle.js` (bim-compiler). Precedents adapted:
`witness_e2e_crud_blob_race.js`, `witness_e2e_multiuser_login_fork.js`.

## THE QUESTION

Can a real user drive a full business cycle end-to-end through the live iDempiere surface today — Sales
Order → Shipment → Sales Invoice → stock effect → replenishment signal → Purchase Order → Material
Receipt → vendor invoice + three-way match — and if not, exactly where does it break?

## §Results — per-stage table

| # | Stage | Driven | Result |
|---|---|---|---|
| 1 | Sales Order | UI | **FAIL** |
| 2 | Delivery / Shipment | UI | **FAIL** (blocked by #1) |
| 3 | Sales Invoice | UI | **FAIL** (blocked by #1) |
| 4 | Stock effect | UI-observed | **ABSENT** |
| 5 | Replenishment signal | UI | **PASS** |
| 6 | Purchase Order | UI | **PASS** |
| 7 | Material Receipt | UI | **ABSENT** |
| 8 | Vendor invoice + three-way match | BLOCKED | **ABSENT** |

**Where the cycle first stops being user-drivable: Stage 1.** A freshly-authored Sales Order cannot be
reliably taken through its own Order Line tab — the master/detail selection silently locks the child tab
onto an unrelated, pre-existing seed order instead of the record the user just created and clicked on.
Everything downstream of that (Shipment, Invoice, stock effect) is a direct consequence, not a separate
break. Independently, Stage 8 (vendor invoice + three-way match) is **completely and permanently blocked**
by design, with no dependency on Stage 1 at all.

The cycle does **not** close end-to-end. Two real business partners (P2P vendor-invoice/matching, and
O2C shipment/invoice generation off a self-authored order) have no working path through this UI today.

## §Exact §CYCLE lines (from `build/erp/witness_e2e_business_cycle.log`)

```
§CYCLE stage=1 name=SalesOrder driven=UI result=FAIL detail=exception: Order Line tab locked onto the WRONG parent — expected C_Order_ID=-1, got: §IDEMPIERE-MD tab=Order Line level=1 parent=C_Order filter=C_Order_ID=108
§CYCLE stage=2 name=Shipment driven=UI result=FAIL detail=exception: new SO not offered by Generate-Shipments picker (target=-1, options=[{"v":"100","t":"80000  (rule A)"},{"v":"101","t":"80001  (rule A)"},{"v":"108","t":"60000  (rule A)"},{"v":"1300100","t":"80000  (rule A)"},{"v":"1300101","t":"80001  (rule A)"},{"v":"1300108","t":"60000  (rule A)"}])
§CYCLE stage=3 name=SalesInvoice driven=UI result=FAIL detail=exception: new SO not offered by Generate-Invoices picker (target=-1, options=[{"v":"100","t":"80000  (rule D)"},{"v":"101","t":"80001  (rule D)"},{"v":"102","t":"80002  (rule D)"},{"v":"108","t":"60000  (rule I)"},{"v":"1300100","t":"80000  (rule D)"},{"v":"1300101","t":"80001  (rule D)"},{"v":"1300102","t":"80002  (rule D)"},{"v":"1300108","t":"60000  (rule I)"}])
§CYCLE stage=4 name=StockEffect driven=UI-observed result=ABSENT detail=m_storageonhand product=130 warehouse=103 baseline=18 current=18 (no shipment was created upstream — Stage2 did not produce a document to reduce stock)
§CYCLE stage=5 name=ReplenishmentSignal driven=UI result=PASS detail=Generate Replenishment produced a staged list (real m_replenish level_min/level_max rows exist in seed)
§CYCLE stage=6 name=PurchaseOrder driven=UI result=PASS detail=PO 1784489523834 (id=-2) authored+completed via real UI — §AD-MODELVAL-LIVE table=c_order verb=create verdict=OK derived={"m_warehouse_id":103,"bill_location_id":null,"c_currency_id":100,"c_doctypetarget_id":132,"c_paymentterm_id":105} fired=11
§CYCLE stage=7 name=MaterialReceipt driven=UI result=ABSENT detail=M_InOut id=-5 created but header New form omits M_Warehouse_ID C_BPartner_ID (fields shown: documentno,movementdate,description,docstatus); no InOutGenerate-equivalent process exists for PO receipts (inoutGenGate hard-requires IsSOTrx='Y', confirmed by source read) so this manual path is the ONLY route to a Material Receipt at all
§CYCLE stage=8 name=VendorInvoiceThreeWayMatch driven=BLOCKED result=ABSENT detail=C_Invoice has no "create" verb (crud_ops.json, confirmed by §INPLACE-NEW ...skipped(create not permitted) live) so a vendor invoice can never be manually authored; Generate-Invoices (AD_Process 119) is gated IsSOTrx='Y'-only at BOTH the UI picker SQL and the ad_process.js invoiceGenGate() so it never offers a Purchase Order either (confirmed: PO -2 offered=false); no M_MatchPO CREATE_LINE exists anywhere in erp_engine.js (source-verified) and the only M_MatchInv CREATE_LINE is inside completeInvoice() which is unreachable without a vendor invoice — the three-way match therefore has NO path to ever populate through this UI today (m_matchpo=37 m_matchinv=18)
```

Log: `build/erp/witness_e2e_business_cycle.log` (1674 lines, gitignored — regenerate to re-view). Exit
code 0 = harness ran correctly to completion (all 8 stages produced a real verdict); it is NOT a claim
that the cycle passed — read the lines above, per the file's own header, not the exit code.

## §Findings — detail per stage

**Stage 1 (Sales Order) — FAIL, reproducible across 3 consecutive runs.** All four real GardenWorld Sales
Orders in the shipped seed (`documentno` 80000/80001/80002/60000, `c_order_id` 100/101/102/108) are
already fully delivered AND invoiced (`qtydelivered=qtyordered`, `qtyinvoiced=qtyordered`) — so exercising
a real shipment/invoice effect requires authoring a fresh order. The witness did: New → fill header
(documentno, C_BPartner_ID=112 "Standard", DateOrdered, GrandTotal, M_PriceList_ID, Bill_BPartner_ID) →
Save → **confirmed via the same `data-ad-record` attribute the app itself renders** that the new order got
a real (synthetic, negative) key. Clicking that exact row and opening the **Order Line** detail tab, the
app's own diagnostic (`§IDEMPIERE-MD tab=Order Line level=1 parent=C_Order filter=C_Order_ID=…`) showed
the child tab locked onto **C_Order_ID=108** — an unrelated, pre-existing seed order (documentno 60000,
already Closed) — not the order the user just created and clicked. This happened on every run. The
**identical** code path on window 181 (Purchase Order, Stage 6) correctly locked onto the new PO's own id
every time. So this is not generic breakage of "new records + child tabs" — it is specific to how Sales
Order's master/detail selection resolves a freshly-created (overlay-only) header row. A real user
authoring a new Sales Order and adding a line would silently add that line to Order 60000 instead — a
data-integrity hazard, not merely an inconvenience.

**Stage 2 / 3 (Shipment / Sales Invoice) — FAIL, direct consequence of #1.** Both Generate-Shipments
(AD_Process 118) and Generate-Invoices (AD_Process 119) were reached via the real, documented
`?process=<id>` deep link (idempiere.html's own comment: "mirrors `?window=`") and rendered their real
order picker. Since Stage 1 never produced a genuinely order-linked line, the new order was never a valid
"generate shipment/invoice" candidate.

**Stage 4 (Stock effect) — ABSENT, and structurally unreachable regardless of Stage 1/2.** `m_storageonhand`
lives only in the raw seed db (`window.__idmpDb`), which the witness confirmed by direct read (baseline
and current on-hand for Plum Tree/HQ Warehouse both read 18). No document produced through
`crud_overlay.js` (create, DocAction, or a Generate-process) writes into that raw table — every such write
lands only in the signed op-log sidecar (`§CRUD-PERSIST … source=sidecar`). Grepping the whole `erp/`
tree found **no live fold/recompute of `qtyonhand` off the op-log anywhere in `idempiere.html` or
`crud_overlay.js`** — `erp_engine.js`'s `qtyOnHand(events, opts)` is a pure function that takes its
`events` as a host-supplied argument; nothing in the live UI ever calls it. So even in a world where
Stage 1/2 worked perfectly, there is currently no way for a real user to SEE on-hand quantity change as a
result of any document they complete through this window.

**Stage 5 (Replenishment signal) — PASS.** Reached by logging in as GardenUser (the real POS-scoped
persona) and tapping the real cart pill (`#pos-pill-payment`) to open the float panel — same gesture the
User Guide documents ("the cart pill summons a floating panel"). **Generate Replenishment**
(`#pos-repl-generate`) produced a real staged list off the shipped `m_replenish` policy rows (19 real
`level_min`/`level_max` rows in the seed). This is the one stage of the eight that closes cleanly. It is
**only** reachable from inside the POS panel — grep of `ad_process.js`'s handler REGISTRY confirms no
standalone `org.compiere.process.ReplenishReport*` classname is registered, so there is no menu/process
path to a replenishment signal anywhere outside POS.

**Stage 6 (Purchase Order) — PASS.** New → header (documentno, C_BPartner_ID=120 "Seed Farm Inc.", a real
vendor) → Save → PO Line (product 130, qty 5, real tax/UOM) → Save, confirmed via
`§IDEMPIERE-MD … filter=C_Order_ID=<the new PO's own id>` that the line correctly attached to the new PO
— → Complete via the real DocAction bar. The status chip read `Completed · CO`. Notably the PO header's
New form (same curated `crud_ops.json` `c_order` entry as Sales Order) has **no editable M_Warehouse_ID
field at all**, yet `§AD-MODELVAL-LIVE` showed a save-hook correctly derives `m_warehouse_id:103` — a
genuine, working default, not a gap.

**Stage 7 (Material Receipt) — ABSENT.** Window 184's New form for `M_InOut` shows only **4** fields:
`documentno`, `movementdate`, `description`, `docstatus` — no `M_Warehouse_ID`, no `C_BPartner_ID` (both
mandatory on a real receipt). A record was created, but with no route through the UI to give it a
warehouse or vendor. There is also no process-driven alternative: `inoutGenGate()` in `ad_process.js`
hard-requires `IsSOTrx='Y'`, and `renderOrderPicker()`'s own SQL hardcodes `AND issotrx='Y'` — so
Generate-Shipments can never be pointed at a Purchase Order. **A Material Receipt has exactly one route in
this product (the sparse manual form above) and no route at all to a properly warehouse/vendor-linked
one.**

**Stage 8 (Vendor invoice + three-way match) — ABSENT, blocked by design, independent of every other
stage.** Three separate, mutually-reinforcing facts, each confirmed live:
1. `erp/crud_ops.json`'s `c_invoice` entry lists `"verbs": ["update","delete","process"]` — **no
   "create"**. Clicking New on window 183 (Purchase Invoice) produces the real console line
   `§INPLACE-NEW table=c_invoice skipped (create not permitted)` and no inline form mounts. A vendor
   invoice — or a sales invoice, same table — can never be manually authored, by design.
2. `invoiceGenGate()` requires `IsSOTrx='Y'`, and the picker's own SQL is `AND issotrx='Y'` — confirmed
   live: the completed Purchase Order from Stage 6 was **not** offered by the Generate-Invoices picker.
3. `M_MatchPO` is never emitted anywhere in `erp_engine.js` (grep-verified, zero hits outside a display-
   label map); `M_MatchInv` is only built inside `completeInvoice()` when `invoice.issotrx==='N'` —
   unreachable per (1). `m_matchpo`/`m_matchinv` counts (37/18) are pre-existing seed rows, untouched.

The three-way match has **no path to ever populate through this UI today** — not a bug to fix incrementally,
a structurally absent capability.

## §Gaps — ranked by what most blocks a user completing the cycle

1. **Sales Order master/detail mis-selection (Stage 1).** The single highest-leverage fix: it silently
   misattributes a new order's lines to an unrelated existing order, and blocks every downstream O2C stage
   in this cycle. Reproduce: create a new Sales Order via window 143, click its row, open Order Line,
   watch `§IDEMPIERE-MD` report the wrong `C_Order_ID`. The identical flow on window 181 (Purchase Order)
   works correctly — the fix is localized to whatever differs between those two tabs' selection handling.
2. **Vendor invoice creation is entirely absent (Stage 8).** By design (`crud_ops.json`), not a bug — but
   it means the P2P side of this ERP cannot close a purchase cycle at all: no manual invoice, no
   Generate-Invoices path (SO-only gate), no `M_MatchPO`/`M_MatchInv` population. This is the largest
   capability gap of the eight stages.
3. **Material Receipt has no properly-linked authoring path (Stage 7).** The header form omits the two
   fields (warehouse, vendor) that make a receipt meaningful, and there is no generator to fall back to.
4. **No live on-hand recompute anywhere in the UI (Stage 4).** Even a perfect shipment produces no visible
   stock change — `m_storageonhand` is a static seed table with nothing reading the op-log to update it.
5. **Replenishment is POS-only (Stage 5, the one PASS).** It works, but only from inside the POS panel —
   there is no standalone process/menu path for a non-POS user (e.g. a warehouse manager in the standard
   windows) to see the same signal.

## §Witness

`scripts/witness_e2e_business_cycle.js` — single browser tab, real click-through UI (`page.click`/
`page.fill`/`page.selectOption` on the real toolbar, inline CRUD-ring fields, DocAction bar, and the
documented `?process=<id>` deep link), read-only observation only via `window.__idmpDb.exec()` (the app's
own `_sqlRows()` accessor, valid for unmodified seed tables) and `window.__crud.kernelDb()` +
`window.__crud.core.tipDocs()` (the app's own published sidecar read API, necessary because every
CRUD-overlay write — create, DocAction, or a Generate-process — lands only in the signed op-log sidecar,
never as a raw INSERT into the base seed db; this is itself a fact this witness had to discover
empirically mid-build, not assumed). Real seed data only (GardenWorld client 11): C_BPartner 112
"Standard" (customer), 120 "Seed Farm Inc." (vendor), M_Product 130 "Plum Tree" (18 on-hand at recon),
M_Warehouse 103 "HQ Warehouse". No product code was touched. Run:
`bash build/erp/run_witness.sh scripts/witness_e2e_business_cycle.js` from bim-compiler; log at
`build/erp/witness_e2e_business_cycle.log` (gitignored, regenerate to re-view).
