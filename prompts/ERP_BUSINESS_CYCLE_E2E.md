# ⚠ DO NOT REMOVE — scope: DISCOVERY witness for the full O2C+P2P business cycle on the live
# iDempiere surface (bim-ootb `erp/idempiere.html` + `erp/crud_overlay.js`). Read the log after every run
# (`build/erp/witness_e2e_business_cycle.log`, gitignored — regenerate via `bash build/erp/run_witness.sh
# scripts/witness_e2e_business_cycle.js` from bim-compiler). This is NOT a green-run hunt — a break IS the
# deliverable. Do not re-run "hoping" for a full pass; the findings below are the answer.

**2026-07-20**, updated **2026-07-21** — witness: `scripts/witness_e2e_business_cycle.js` (bim-compiler).
Precedents adapted: `witness_e2e_crud_blob_race.js`, `witness_e2e_multiuser_login_fork.js`.

## THE QUESTION

Can a real user drive a full business cycle end-to-end through the live iDempiere surface today — Sales
Order → Shipment → Sales Invoice → stock effect → replenishment signal → Purchase Order → Material
Receipt → vendor invoice + three-way match — and if not, exactly where does it break?

## §Results — per-stage table (2026-07-21, current)

| # | Stage | Driven | Result |
|---|---|---|---|
| 1 | Sales Order | UI | **PASS** (was FAIL 2026-07-20 — §Fix entries below, landed) |
| 2 | Delivery / Shipment | UI | **FAIL** (picker gap + `cleanVals` warehouse-drop both fixed+landed; now fails one layer deeper — the process handler's own `fetchOrder` reads the raw base table, misses the sidecar CO status) |
| 3 | Sales Invoice | UI | **FAIL** (blocked earlier, at process param-validation — `AD_Org_ID=NaN`, a NEW casing bug in `listTip`'s stdDefaults fold, named not yet fixed) |
| 4 | Stock effect | UI-observed | **ABSENT** |
| 5 | Replenishment signal | UI | **PASS** |
| 6 | Purchase Order | UI | **PASS** |
| 7 | Material Receipt | UI | **ABSENT** |
| 8 | Vendor invoice + three-way match | BLOCKED | **ABSENT** |

**Where the cycle now first stops being user-drivable: Stage 2, one layer deeper than before.** Stage 1 is
fully closed (both root causes fixed and re-verified). Stage 2/3's picker gap is ALSO fixed and verified —
a freshly-created order is now correctly offered as a candidate. What still blocks the cycle: the order's
warehouse (derived by a save-hook, since the New form has no field for it) never actually gets persisted —
`cleanVals()` in `crud_overlay.js`'s shared `buildOp` strips any field not declared in `crud_ops.json`,
silently discarding the hook-derived value moments after it was correctly computed. This is now the
frontier — named precisely (§Fix 2026-07-21 below), not yet fixed (it touches the shared CREATE path every
table uses, more central than the two fixes already landed). Independently, Stage 8 (vendor invoice +
three-way match) is **completely and permanently blocked** by design, with no dependency on Stages 1-3.

The cycle still does **not** close end-to-end — the break has now moved TWO layers in from where it stood
2026-07-20: "can't even complete an order" → "can complete an order, picker offers it, but the order's own
data is incomplete" (the derived warehouse never survives to storage).

<details><summary>Original 2026-07-20 table (superseded, kept for history)</summary>

| # | Stage | Driven | Result |
|---|---|---|---|
| 1 | Sales Order | UI | FAIL |
| 2 | Delivery / Shipment | UI | FAIL (blocked by #1) |
| 3 | Sales Invoice | UI | FAIL (blocked by #1) |
| 4 | Stock effect | UI-observed | ABSENT |
| 5 | Replenishment signal | UI | PASS |
| 6 | Purchase Order | UI | PASS |
| 7 | Material Receipt | UI | ABSENT |
| 8 | Vendor invoice + three-way match | BLOCKED | ABSENT |

</details>

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

## §Fix — 2026-07-20, Gap 1 (Sales Order master/detail mis-selection) FIXED, not yet pushed

**Root cause, confirmed exactly as §Gaps #1 predicted:** `erp/idempiere.html`'s `overlay:committed`
handler's `CRUD_CREATE` branch (~line 2967) folds the new row into `_records` via `_overlayListTip()` and
repaints, but never updated `_recIdx`/`_selByLevel` — master-detail child tabs filter by
`_selByLevel[level-1]` (`renderActiveTab` ~line 1723), so it stayed pinned to whichever record was current
at window open. Purchase Order (window 181) was never actually different code — it just hadn't been
exercised New+Save+drill in a session state where the stale index pointed elsewhere.

**One correction to the mechanism assumed going in:** the fix could NOT key off `overlay:committed`'s
`id` field (`d.id`) as first suspected — `buildOp()` in `erp/crud_overlay.js` never sets `base.id` for a
`CRUD_CREATE` (only update/delete/process do), so that field is always `null` on create. The new row's
real synthetic pk (`-opId`) only becomes known inside `_overlayListTip`'s `listTip()` fold. Fix: capture
that fold's `created` array into a new `_lastFoldCreated` var, and in the `CRUD_CREATE` branch use its
last entry (the just-committed row) to locate the record in `_records`, set `_recIdx`, and call
`_setSel(ct)` so `_selByLevel[ct.tabLevel]` updates to the real new pk. New `§CRUD-CREATE-SEL` log line
proves the threading.

**Witness: `W-SO-CHILD-BIND`** (`bim-compiler/scripts/witness_so_child_bind.js`, run via
`bash build/erp/run_witness.sh scripts/witness_so_child_bind.js`) — single focused check: New+Save a
Sales Order, drill into Order Line, assert `§IDEMPIERE-MD filter=C_Order_ID=<pk>` equals the new order's
own pk. 🟢 on the fixed worktree:
```
§CRUD-CREATE-SEL table=c_order id=-1 recIdx=5 selLevel=0 sel=-1
§IDEMPIERE-MD tab=Order Line level=1 parent=C_Order filter=C_Order_ID=-1
🟢 W-SO-CHILD-BIND: Order Line child tab filters by the NEW order's pk, not a stale seed order
```
Before/after via the original `witness_e2e_business_cycle.js`: unfixed run (`/home/red1/bim-ootb` main,
unmodified) — `§IDEMPIERE-MD ... filter=C_Order_ID=108` (the seed order) against new order pk `-1`, Stage 1
throws exactly the predicted error. Same script re-run against the fixed worktree — `filter=C_Order_ID=-1`,
matches; the parent-binding sub-check now passes. Stage 1's overall PASS/FAIL in that broader witness
still shows FAIL, but for an **unrelated, separate reason**: the DocAction Complete (CO) button did not
render on that run (`chip=null coButtonVisible=0`) — out of scope for this fix (not the parent-binding
defect), reported as-is, not investigated further. Stages 2-4 remain blocked by the pre-existing,
already-documented Gap 2/3/4 facts above (P2P absence, no receipt-linking path, no on-hand recompute),
unchanged by this fix.

**State:** DEPLOYED 2026-07-21. Branch was 1 commit behind `main` — merged `origin/main` into
`fix/so-child-bind` in worktree `/tmp/wt-so-bind` (clean, no conflicts; merge commit `306effe`),
re-ran `W-SO-CHILD-BIND` against the synced worktree (still 🟢), pushed, opened
[PR #928](https://github.com/red1oon/bim-ootb/pull/928), auto-merge (squash) armed — merges once
`fast-checks` CI passes.

---

## §Fix — 2026-07-21, Stage 1's remaining "CO did not land" — WITNESS artifact, not a product bug

With `fix/so-child-bind` now live on `main` (PR #928 merged, confirmed via a fresh checkout re-sync), the
"unrelated, separate reason" named above — `chip=null coButtonVisible=0` — was investigated rather than
left as a standing note. Root-caused via a debug worktree (`/tmp/wt-so-docaction`, throwaway diagnostics,
never committed) with targeted `console.log`s at `_fsmCtx`, `buildForm`, the `<tr>`/`<td>` click handlers,
and `editInline`'s call stack — each step ruled out a hypothesis (tableId mismatch: no, both SO/PO tabs
are `AD_Table_ID=259`, verified via `sqlite3`; case-sensitivity in `recVal`: no, it's already
case-insensitive; a stale `_newMode`: no, `afterSaveCreate` correctly clears it) until the actual
mechanism surfaced:

**`idempiere.html`'s grid deliberately makes every individually crud-editable cell open a single-field
inline cell editor on click** (`_editGridCell`, P4/W-INPLACE-GRID-LIVE, "GridView parity" — `td`'s click
handler calls `ev.stopPropagation()`, so the row's own click handler never fires). Playwright's
`row.click()` lands on whatever cell happens to sit at the row's geometric center — column layout
happened to put `DocumentNo` (curated crud-editable, per `crud_ops.json`'s `c_order` field list) there for
window 143's Order tab, and `POReference` (NOT in that field list, so `_editGridCell` correctly falls
through to `openForm()` → the full record + DocAction bar) for window 181's Purchase Order tab. **Same
table, same code, no Sales-Order-specific defect** — pure column-position luck determined by which window
was under test, confirmed with `§DEBUG-TDCLICK`/`§DEBUG-TRCLICK` instrumentation showing every single
click (both windows, both the first and second row-open) landing on a `<td>`, never the bare `<tr>`.

**Fix (witness-only, no product code touched):** `scripts/witness_e2e_business_cycle.js` gained
`clickRowOpen(row, opts)` — clicks the row's `td[data-ad-col="POReference"]` cell explicitly (present in
both windows, deterministically non-crud-editable) instead of the bare row, falling back to the row click
if that column isn't rendered. Replaces all four `found*.handle.click()` call sites (Stage 1's two row-opens
+ Stage 6's two, the only stages driving `c_order`). Also made `ROOT` env-overridable
(`process.env.WITNESS_ROOT`) so this and future debug sessions can point the witness at a throwaway
worktree without editing the file each time.

**Result — Stage 1 now PASSES.** Re-run (`bash build/erp/run_witness.sh scripts/witness_e2e_business_cycle.js`
against live `bim-ootb main`), log read in full (Log Mandate):
```
§CYCLE stage=1 name=SalesOrder driven=UI result=PASS detail=order 1784602787337 (id=-1) authored+completed via real UI; header New form has NO m_warehouse_id field but a save-hook derives it (103) per §AD-MODELVAL-LIVE
```
A real, freshly-authored Sales Order can now be taken all the way through New → Save → Order Line → back to
header → **Complete (CO)** via the real DocAction bar, end to end, through the live UI — the most basic
iDempiere user action (author + complete a document) genuinely works today.

**Stages 2/3 now fail for a NEW, more precise, and real reason** (previously masked by Stage 1's failure,
since 2/3 are gated on 1's order actually reaching CO):
```
§CYCLE stage=2 name=Shipment driven=UI result=FAIL detail=exception: new SO not offered by Generate-Shipments picker (target=-1, options=[...four existing seed orders, no -1...])
§CYCLE stage=3 name=SalesInvoice driven=UI result=FAIL detail=exception: new SO not offered by Generate-Invoices picker (same shape)
```
The freshly-completed order (a synthetic negative-pk overlay row) is never offered as a candidate by either
Generate-process's order picker — both pickers' options lists show only the four pre-existing seed orders.

---

## §Fix — 2026-07-21, Stage 2/3 picker gap — CONFIRMED and fixed (one layer); a DEEPER, distinct layer found underneath

**Confirmed:** `renderOrderPicker()` (`idempiere.html:2386`) read candidates via `_sqlRows("SELECT ... FROM
c_order WHERE " + statusClause + " AND issotrx='Y'")` — `window.__idmpDb` only, the raw seed bundle. It
never folded the op-log overlay, so a freshly-created-then-Completed order (a signed `CRUD_CREATE` +
`SET_STATUS` pair in the sidecar) could never be a candidate no matter how legitimately it reached CO. Same
class of "engine-proven, UI reads the wrong source" gap this lane keeps finding.

**Fix (branch `fix/order-picker-overlay-fold`, worktree `/tmp/wt-order-picker-overlay`):** `renderOrderPicker`
now pulls ALL `issotrx='Y'` base rows (no status filter — a synthetic row's status only exists post-fold),
folds `CRUD_CREATE`/`CRUD_UPDATE` via `window.__crud.core.listTip` (the SAME primitive `idempiere.html`'s own
`_overlayListTip` already uses for grids — non-invent, reused verbatim), overlays each row's latest signed
`SET_STATUS` via `core.readTip` (same primitive `_overlayDocTip` uses for the status chip), THEN applies the
status filter. No sidecar/engine present → falls through to the OLD base-only behavior (never worse than
before). Rendering is now async (mount the pane immediately with a "Loading…" option, repaint once the fold
resolves) since the sidecar hydrate is async.

**Witnessed — the picker itself is fixed.** Re-run (`WITNESS_ROOT=/tmp/wt-order-picker-overlay bash
build/erp/run_witness.sh scripts/witness_e2e_business_cycle.js`), log read in full:
```
§GENSHIP-LIVE-CANDIDATES count=7 ids=[100,101,108,1300100,1300101,1300108,-1]
§E2E-PICKER genship options=[...six seed orders..., {"v":"-1","t":"1784606993701  (rule undefined)"}]
```
The new order (`-1`) is now correctly offered — confirms the picker fold works exactly as designed.

**But Stage 2/3 still FAIL, for a NEW and DEEPER reason, found immediately underneath:**
```
§GENSHIP-LIVE run proc=118 Record_ID(C_Order_ID)=-1 M_Warehouse_ID=NaN
§AD-PROC-LIVE proc=118 name="Generate Shipments" classname=org.compiere.process.InOutGenerate dispatched=N reason=param-validation-failed
```
The process now RUNS (picker gap closed) but is rejected at param validation: `M_Warehouse_ID=NaN`. Traced
to the actual root cause, one more layer down, via `saveForm`/`buildOp` in `crud_overlay.js`:

1. `c_order`'s New form has no `m_warehouse_id` field (confirmed, Stage 1 §Findings) — the value is
   **derived** by a `BEFORE_SAVE` model-validation hook (`fireBeforeSaveHooks`, `crud_overlay.js:1546`) and
   correctly merged into `vals.m_warehouse_id` at save time (`crud_overlay.js:1242`, whose OWN comment
   already states the intent: *"a beforeSave-filled MANDATORY default that has NO visible field... must
   STILL persist on the new row — iDempiere saves what beforeSave derived"*).
2. But `buildOp('create', ...)` (`crud_overlay.js:212`) then calls `base.fields = cleanVals(entry, values)`,
   and `cleanVals` (`crud_overlay.js:160-164`) **only copies keys present in `entry.fields`** — `c_order`'s
   crud_ops.json entry does not declare `m_warehouse_id` (by design — no visible field for it), so
   `cleanVals` silently drops the just-derived value before it ever reaches the signed `CRUD_CREATE` op.
3. Net effect: the derived value correctly unblocks the SAVE (the hook doesn't reject), but is never actually
   PERSISTED anywhere — not just invisible to the Generate-Shipments picker, genuinely absent from the
   record. Any future reader of this order's warehouse (not only this picker) would see it missing.

Stage 4 (stock effect) remains structurally unreachable regardless (§Findings above, unchanged). Stages 5-8
unchanged from prior runs (Stage 8's PO-not-offered-to-Generate-Invoices finding, IsSOTrx-gated by design,
is unrelated to any of the three fixes in this cycle).

---

## §Fix — 2026-07-21, `cleanVals`/`buildOp` — FIXED and witnessed; one more layer found underneath

**Fix (branch `fix/cleanvals-create-derived`, worktree `/tmp/wt-cleanvals-create`):** `cleanVals`
(`erp/crud_overlay.js:165-179`) now takes a third `verb` arg. On `verb==='create'` only, after copying every
declared field (unchanged behavior), it also copies any OTHER key already present in `values` that isn't
`null`/`''` — i.e. exactly the hook-derived columns `saveForm`'s `fireBeforeSaveHooks` callback already
merges into `vals` at `crud_overlay.js:1242` (confirmed by tracing `gatherVals`, `crud_overlay.js:1210-1213`:
it seeds `vals` ONLY from `entry.fields`, so no other writer can put an extra key there — the passthrough is
provably scoped to hook-derived values, not stray UI state). `buildOp`'s one call site
(`crud_overlay.js:225`) now passes `verb` through. `cleanVals` has exactly one caller in the whole tree
(confirmed via grep, incl. its `CORE.cleanVals` export) — always inside the CREATE branch — so this is a
fully self-contained, single-function change; UPDATE's tight declared-only guard is untouched.

**Witnessed** — re-ran `scripts/witness_e2e_business_cycle.js` against both the unfixed baseline
(`WITNESS_ROOT` unset → `bim-ootb` `main`) and the fixed worktree (`WITNESS_ROOT=/tmp/wt-cleanvals-create`),
full log read both times (Log Mandate):
```
baseline: §GENSHIP-LIVE run proc=118 Record_ID(C_Order_ID)=-1 M_Warehouse_ID=NaN
          §AD-PROC-LIVE proc=118 ... dispatched=N reason=param-validation-failed
fixed:    §GENSHIP-LIVE run proc=118 Record_ID(C_Order_ID)=-1 M_Warehouse_ID=103
          §AD-PROC-LIVE proc=118 ... dispatched=Y ok=N rows=0 reason=order-not-shippable
```
The derived warehouse now rides the `CRUD_CREATE` op and reaches the process param — Generate-Shipments
moves from rejected-before-running to actually dispatched. No regression: Stage 1 (SalesOrder) still PASS,
Stage 6 (PurchaseOrder, same `c_order` create path, same derived-field set) still PASS with the identical
`§AD-MODELVAL-LIVE derived={...}` line, Stages 4/5/7/8 unchanged.

**Stage 2 now fails ONE LAYER DEEPER, a genuine business-rule gate, not this bug:** `inoutGenGate()`
(`ad_process.js:251-256`) requires the order's `docstatus==='CO'` — but the process HANDLER's own
`ctx.fetchOrder(info)` (unlike the picker, which was already fixed 2026-07-21 above to fold the sidecar)
still reads the RAW base `c_order` table only, so it sees the synthetic order's status as it exists in
`window.__idmpDb` (never `CO` — the CO only exists as a signed op in the sidecar). Same "engine/handler reads
the wrong source, picker was fixed but the handler wasn't" pattern this whole lane keeps finding — one
function (`fetchOrder`'s wiring into `registerInOutGenerate`/`registerInvoiceGenerate`'s ctx), not yet
touched, named here for the next session.

**A SEPARATE, independently-confirmed new bug found investigating Stage 3 (Generate Invoices) — real root
cause identified, NOT fixed this session:** `§GENINV-LIVE` shows `AD_Org_ID=NaN` even after the cleanVals
fix (unlike `M_Warehouse_ID`, which is now correct). Traced to `listTip`'s stdDefaults-materialization block
(`crud_overlay.js:386-405`, "Task 1 — iDempiere setStandardDefaults parity"): it writes the new row's derived
tenant/audit columns in **mixed case** — `nr['AD_Org_ID']`, `nr['AD_Client_ID']`, `nr['CreatedBy']`,
`nr['UpdatedBy']`, `nr['Created']`, `nr['Updated']`, `nr['IsActive']`, `nr['Processed']`, `nr['Processing']`,
`nr['Posted']` — while every OTHER key on that same row object (`nr[c] = f[c]` from `op.fields`, one line
above at `crud_overlay.js:385`) is lowercase, matching this codebase's column convention throughout (`
m_warehouse_id`, `c_currency_id`, etc. — confirmed via `_getTableCols`, which itself lowercases whatever
`PRAGMA table_info` returns before comparing). `renderOrderPicker` (`idempiere.html:2408/2433`) reads
`r.ad_org_id` (lowercase) off the SAME folded row — finds nothing, `Number(undefined)` → `NaN`. Confirmed
this is a real bug and not by-design: grepped every `.js`/`.html` file in `erp/` for a JS (non-SQL) property
read of `.AD_Org_ID`, `.AD_Client_ID`, `.CreatedBy`, `.UpdatedBy`, `.IsActive` etc. on a folded row —
zero hits, nothing anywhere consumes the mixed-case form. **Not yet fixed** (out of scope for this
session's handoff, which was cleanVals specifically) — the fix is a straight 10-line case-lowering in that
one block, no other logic change, but confirming it doesn't regress anything reading real `AD_*` system-table
rows elsewhere (a genuinely different, PascalCase-cased table family, per `ad_graph.js:264`'s `rec.IsActive`
on a raw `AD_Table` query row) needs a deliberate look before touching it — named precisely here so the next
session doesn't have to re-derive it.

Stage 3 (SalesInvoice) result is unaffected either way this session (still FAIL) — `invoiceGenGate()`
(`ad_process.js:319-324`) never even reaches the `AD_Org_ID` question yet; `§AD-PROC-LIVE proc=119 ...
dispatched=N reason=param-validation-failed` fires first because the process's own mandatory-param check
(`validateParams`, `ad_process.js:515-530`) rejects the `NaN` before the handler runs at all.
