# ⚠ DO NOT REMOVE — LANE: AD_PROCESS FOLD (the "processes & reports" half of the journey)
# Scope: POPULATE AD_Process handlers BY DEMAND. The DISPATCH SPINE IS ALREADY BUILT + LIVE — do NOT rebuild it.
#   `build/erp/ad_process.js` (W-PROC) + host wiring (§AD-PROC-LIVE) already: read `ad_process`/`ad_process_para`,
#   fold the PARAM DIALOG from real para rows (with the iDempiere prepare() validate gate rejecting on-screen),
#   role-gate by `ad_process_access`, resolve `classname`→a JS HANDLER REGISTRY, and show an HONEST "absent-handler"
#   card for anything unported (counted, never a silent no-op / fake result). This lane grows the HANDLER SET only.
# STATUS (2026-06-19): 15 handlers live (5 spine reports/doc-actions + ProjectGenOrder #352 + InOutGenerate #355
#   + InvoiceGenerate #358 + Rpt C_Project #363 + Rpt M_InOut #367 + Rpt M_Movement #398 + Rpt M_Inventory #400
#   + Rpt PP_Order #414 + Rpt C_Payment #417 + Rpt M_InOutConfirm #424). sw v731, ad_process.js?v=11, report_overlay.js?v=9.
#   TWO fold verbs now: foldReceipt (header+lines; sales/inventory/project/manufacturing + the line-sort+product-join
#   variant for parent-line products, leg6) + foldVoucher (HEADER-ONLY financial docs, e.g. C_Payment — first #417).
#   ▶ NEXT remaining KIND-1 DOC candidates + their fold-shape (each needs care, NOT a quick map row):
#     · Header-only financial docs (C_Cash, C_Allocation, bank statement lines) → reuse foldVoucher (just a
#       REPORT_MAP row each — like a foldReceipt reuse but on the voucher verb) IF demand-reachable + real seed rows.
#       ⚠ leg6-survey CAVEAT: no "Rpt C_Cash/C_Allocation/C_BankStatement" report proc was found in the seed corpus
#       (the matching procs were absent on probe) — confirm a real demand-reachable report proc + seed rows BEFORE
#       picking one; these may not be reachable, in which case the foldVoucher-reuse band is effectively drained too.
#     · The rest of the KIND-1 tail is RV_* report-VIEW procs (e.g. RV_ProjectCycle 218, RV_InOutDetails 293/294,
#       RV_InOutConfirm 285) → need a NEW tabular report-view fold (see leg3 ⚠ note). This is the main remaining body.
#     · KIND-2 (16 doc-generators): all demand-reachable ones shipped.
#   NEXT LEG: a foldVoucher REUSE on a header-only financial doc IF one proves demand-reachable, ELSE the tabular
#   report-view fold (the RV_* body). Both foldReceipt-reuse + the line-sort/product-join variant are now drained.
#   Return to the GRAND_LANE spine after the batch.
#   iDempiere source oracle for non-invent extraction: `~/idempiere-dev-setup/idempiere/`.
# Run rule: work §OUTSTANDING top-to-bottom to zero — top open leg → spec → build → headless witness → LIVE
#   witness/§-log → ✅ DONE (witness) → next. §-log FIRST (exit code ≠ evidence). NON-INVENT. Worktree off FRESH
#   origin/main; sw bump (HIGHER version + keep both precache hunks); PR + auto-merge; VERIFY landed on main.
# ── TRIBUTARY of the GRAND LANE spine (`prompts/GRAND_LANE_STRATEGY.md`): the "processes run on demand" capability
#   of the J1→J8 journey (the doc-action FSM half is J5; AD_Process is the broader process half).

## DOCTRINE — classify each process by what its `doIt` body ACTUALLY is (same law as DocAction/CRUD)
Detect-from-AD is GENERAL (the row + params fold for ANY process); EXECUTE follows the kind:
- **KIND 1 — report / declarative** (`isReport='Y'`, blank classname, `PA_Report`/`AD_PrintFormat`, pure-SQL
  `procedurename`): FOLD through `report_overlay` (foldReceipt/foldStatement/foldPrint — oracle-equivalent to the
  cent). No per-process code; the spine routes blank-classname report procs by a `report:<headerTable>` key.
- **KIND 2 — effect IS an engine verb**: a THIN handler that ASSEMBLES an op-group via the EXISTING
  `erp_engine.buildDoc` archetype, **newVerbs=0** — NOT a Java port. Shipped precedents: ProjectGenOrder→C_Order
  (W-PROC-GENPO), InOutGenerate→M_InOut (W-PROC-SHIP), ReplenishReport→PO (W-FOLD-REPLENISH).
- **KIND 3 — genuinely imperative Java** (no declarative subset): a REGISTERED handler / `.foldbundle` plugin,
  NEVER auto-imported (engine law). Until ported → the honest absent-handler card stands (counted, never faked).
HONESTY INVARIANT: data that can't drive a process shows the honest empty/rejection card, NEVER a fabricated
result. A leg is ✅ only when a LIVE witness dispatches it on the served bundle AND (where the seed carries the
oracle) the result diffs to the cent against iDempiere's own output; else the positive fold is proven HEADLESS
(bind real rows / independent re-derivation) and the live leg is the honest empty/rejection, LOGGED not faked.

DEMAND AUDIT (`AdProcess.pickUsedProcesses`, access+workflow union = 451 used procs of 476):
148 KIND-1 reports · 16 KIND-2 doc-generators · 287 KIND-3 imperative. Fold BY DEMAND; do NOT bulk-port the corpus.

## §OUTSTANDING (work to zero, in order)

**✅ P1 — ProjectGenOrder (AD_Process 164) · KIND 2 · DONE/LIVE** (PR #352, sw v704). Folds a C_Project → a
  C_Order (SALES order — Env.setSOTrx=true, On-Credit "WI"; Qty=PlannedQty−InvoicedQty, Price=PlannedPrice) via
  buildDoc. getProject gate → honest `project-not-ready`. `renderProjectPicker` supplies the project (the
  GenerateTo button, ref 28, is dropped by S2B foldCrudSpec → decoupled menu/process path). Witnesses
  poc_proc_genorder.js (W-PROC-GENPO) + poc_genpo_live.js (W-PROC-GENPO-LIVE), both EXIT 0.

**✅ P2-leg1 — InOutGenerate (AD_Process 118) · KIND 2 · DONE/LIVE** (PR #355, sw v706). Folds a CO Sales Order →
  M_InOut shipment via the createShipment archetype. toDeliver=QtyOrdered−QtyDelivered; DeliveryRule 'F'→toDeliver,
  'A'→min(toDeliver,onHand) (Availability cap), O/L/M/5 named-deferred; gate DocStatus='CO' AND IsSOTrx='Y'.
  `renderOrderPicker` (CO Sales Orders; warehouse→the mandatory M_Warehouse_ID para). Witnesses poc_proc_inout.js
  (W-PROC-SHIP) + poc_genship_live.js (W-PROC-SHIP-LIVE), both EXIT 0.

**✅ P2-leg2 — C_Invoice_Generate (AD_Process 119, `org.compiere.process.InvoiceGenerate`) · KIND 2 · DONE/LIVE**
  (bim-ootb PR #358, sw v707, ad_process.js?v=4). The order-to-cash closer: a CO/CL Sales Order → a C_Invoice via
  the EXISTING `erp_engine.buildDoc` createInvoice archetype (newVerbs=0). Per-line qty `toInvoice = QtyOrdered −
  QtyInvoiced` under the **Immediate** InvoiceRule ('I'); the 'D'/'O'/'S' rules invoice from the shipment/schedule
  corpus → NAMED-DEFERRED (honest, flagged). EXTRACTED from `InvoiceGenerate.java:299` (codes X_C_Order:1546-1552:
  I/D/O/S; selection DocStatus∈{CO,CL} ∧ IsSOTrx, line 159/167). `registerInvoiceGenerate` + `invoiceGenGate` +
  `genInvoiceLines` + `INVOICE_SPEC` in `build/erp/ad_process.js`. Host: `_ensureProcHandlers` registers it;
  `renderOrderPicker` generalised for shipment+invoice (mandatory AD_Org_ID from the order, DocAction=Complete);
  `renderProcResult` renders the C_Invoice op-group (Invoice Qty column). Witnesses poc_proc_inv.js (W-PROC-INV
  8/8, oracle-equivalent toInvoice + partial-prior + falsifier + named-deferral + gate) + poc_geninv_live.js
  (W-PROC-INV-LIVE PASS, 0 pageerrors; served CO orders fully-invoiced/D-deferred → honest empty), both EXIT 0.

**✅ P2-leg3 — Project Print report (AD_Process 217 `Rpt C_Project`) · KIND 1 · DONE/LIVE** (bim-ootb PR #363, sw
  v710, report_overlay.js?v=4, ad_process.js?v=5). The first KIND-1 leg: a report proc folded through
  `report_overlay` with ZERO new fold code. The blank-classname "Rpt C_Project" resolves to `report:c_project`
  and folds a C_Project via the EXISTING `foldReceipt` — one new `REPORT_MAP.c_project` row (amount=PlannedAmt,
  qty=PlannedQty, price=PlannedPrice, total=header PlannedAmt) + a generic `docnoCol` (C_Project has no
  DocumentNo → reads Value). `resolveClassname` adds a C_Project-SPECIFIC match so the `RV_Project*` report-VIEW
  procs (218 RV_ProjectCycle, 226/228/229/234) stay the honest absent-handler card (a report-view fold is a later
  leg, NOT mis-routed to foldReceipt). No `idempiere.html` dispatch change (openProcess/renderProcResult already
  render any report fold). Witnesses poc_proc_projprint.js (W-PROC-PROJPRINT 6/6: resolve, cent-exact
  subtotal/total/tax vs BigDecimal, falsifier, broad-match guard) + poc_projprint_live.js (W-PROC-PROJPRINT-LIVE
  PASS, project 101 folds to 600.00 in-browser reading the real CamelCase bundle), both EXIT 0.
  ⚠ NOTE — `RV_ProjectCycle` (218) itself is a report-VIEW report (ad_reportview_id=128), NOT a doc receipt; it
  needs a tabular report-view fold (new fold code) → DEFERRED to a report-view leg, out of this KIND-1 "reuse" pass.

**▶ P2-tail — the reachable KIND-1 report procs, by demand.** Each: confirm the report-key route or add a
  `report:<headerTable>` map entry (foldReceipt for header+lines, foldVoucher for header-only) → live witness → ✅.
  Every unported classname stays the HONEST absent-handler card. **6 tail legs shipped (leg1 M_InOut #367, leg2
  M_Movement #398, leg3 M_Inventory #400, leg4 PP_Order #414, leg5 C_Payment #417, leg6 M_InOutConfirm #424 — see
  below); foldReceipt-reuse + the line-sort/product-join variant are drained, foldVoucher (header-only) added in
  leg5. NEXT candidates + their fold-shape are in the STATUS header (header-only docs reuse foldVoucher IF a real
  report proc is demand-reachable — leg6 survey found none for C_Cash/C_Allocation/C_BankStatement · the RV_*
  tabular report-view fold is the main remaining body). Return to the GRAND_LANE spine after the batch, or take a
  user-prioritised insert.**

**✅ P2-tail-leg1 — Rpt M_InOut (AD_Process 117 "Delivery Note / Shipment Print") · KIND 1 · DONE/LIVE** (bim-ootb
  PR #367, sw v714, ad_process.js?v=6). W-PROC-MINOUT 6/6 + W-PROC-MINOUT-LIVE PASS, both EXIT 0. The
  natural sibling of leg1 (InOutGenerate makes the M_InOut; this PRINTS it). Blank classname, isReport=Y →
  resolves to `report:m_inout` → folds an M_InOut via the EXISTING `report_overlay.foldReceipt` over the
  ALREADY-PRESENT `REPORT_MAP.m_inout` row. ZERO new fold code AND zero host change (leg3 added a map row +
  resolveClassname match; this leg needs only `registerHandler('report:m_inout')` + a resolveClassname match —
  the host `_procCtx` is generic over any REPORT_MAP key, `renderProcResult` already renders a non-financial
  fold). A shipment is a NON-FINANCIAL document (`amount:null` → subtotal/tax/total stay null, qty=MovementQty
  carried) — folded honestly, NEVER a fabricated total. EXTRACT: M_InOut 100 (DocumentNo 600000, 1 line,
  product 130, MovementQty 1) is a real ad_full.db row. GUARD: the match must be M_InOut-SPECIFIC — `m_inout\b`
  (word-boundary) so 292 "Rpt M_InOutConfirm" (Shipment Confirmation) + 293/294 RV_InOutDetails stay the honest
  absent-handler (a substring `rpt m_inout` would wrongly catch "m_inoutconfirm"). Witnesses poc_proc_minout.js
  (W-PROC-MINOUT) + poc_minout_live.js (W-PROC-MINOUT-LIVE). ad_process.js?v=6, sw bump.

**✅ P2-tail-leg2 — Rpt M_Movement (AD_Process 290 "Inventory Move Print") · KIND 1 · DONE/LIVE** (bim-ootb
  PR #398, sw v717, ad_process.js?v=7, report_overlay.js?v=5). W-PROC-MOVEMENT 6/6 + W-PROC-MOVEMENT-LIVE PASS,
  both EXIT 0. The warehouse sibling of tail-leg1 (InOutGenerate/Rpt M_InOut printed the M_InOut; this PRINTS the
  M_Movement). Blank classname, isReport=Y, no paras → resolves to `report:m_movement` → folds an M_Movement via
  the EXISTING `report_overlay.foldReceipt` over a NEW `REPORT_MAP.m_movement` row. ZERO new fold code AND zero
  host change (registration is automatic via installDefaultHandlers; the host `_procCtx`/`renderProcResult` is
  generic over any REPORT_MAP key). A material movement is a NON-FINANCIAL document (`amount/price/total:null` →
  subtotal/tax/total stay null, qty=MovementQty carried; internal locator→locator move has no c_bpartner_id →
  partner null) — folded honestly, NEVER a fabricated total. EXTRACT: M_Movement 100 (DocumentNo 10000000, 1
  line, product 123, MovementQty 4) is a real ad_full.db / served ad_seed.db row. GUARD: the resolveClassname
  match is M_Movement-SPECIFIC (`m_movement\b` word-boundary | "inventory move print") — the imperative
  M_Movement_Process (122) + M_MovementConfirm_Process (286) are isReport=N → resolveClassname never synthesizes
  a report key for them → they stay the honest absent-handler card. ⚠ DRIFT NOTE: the served `report_overlay.js`
  has DIVERGED from `build/erp/report_overlay.js` (served has the browser `lc()` CamelCase-alias helper + dropped
  `foldQWeb`; source has foldQWeb, no lc) — the REPORT_MAP edit was applied SURGICALLY to both, NOT blind-copied;
  foldReceipt + the existing REPORT_MAP rows are identical in both, so the addition is compatible. Witnesses
  poc_proc_movement.js (W-PROC-MOVEMENT) + poc_movement_live.js (W-PROC-MOVEMENT-LIVE).

**✅ P2-tail-leg3 — Rpt M_Inventory (AD_Process 291 "Physical Inventory Print") · KIND 1 · DONE/LIVE** (bim-ootb
  PR #400, sw v718, ad_process.js?v=8, report_overlay.js?v=6). W-PROC-MINVENTORY 6/6 + W-PROC-MINVENTORY-LIVE
  PASS, both EXIT 0. The third inventory-document print, completing the trio (m_inout #367 → m_movement #398 →
  m_inventory). Blank classname, isReport=Y, no paras → resolves to `report:m_inventory` → folds an M_Inventory
  via the EXISTING `report_overlay.foldReceipt` over a NEW `REPORT_MAP.m_inventory` row. ZERO new fold code, zero
  host change. NON-FINANCIAL: a physical count carries qty=QtyCount (the counted quantity, NOT MovementQty —
  M_InventoryLine's qty column is `qtycount`), amount/price/total null; M_Inventory has no c_bpartner_id →
  partner null. EXTRACT: M_Inventory 100 (DocumentNo 10000000, 1 line, product 147, QtyCount 1). GUARD: the match
  is M_Inventory-SPECIFIC (`m_inventory\b` | "physical inventory print"); the imperative M_Inventory Create/Update
  (105/106, non-blank classnames → resolveClassname returns their classname as-is) + M_Inventory Process (107,
  blank classname but isReport=N → no report key synthesized) stay the honest absent-handler card; bare "inventory"
  is NOT matched. Same DRIFT discipline as leg2 (surgical REPORT_MAP edit on the lc()-bearing served variant).
  Witnesses poc_proc_minventory.js (W-PROC-MINVENTORY) + poc_minventory_live.js (W-PROC-MINVENTORY-LIVE).

**✅ P2-tail-leg4 — Rpt PP_Order (AD_Process 53028 "Manufacturing Order") · KIND 1 · DONE/LIVE** (bim-ootb PR
  #414, sw v726, ad_process.js?v=9, report_overlay.js?v=7). W-PROC-PPORDER 6/6 + W-PROC-PPORDER-LIVE PASS, both
  EXIT 0. A 4th document family beyond sales/inventory: MANUFACTURING — proves the receipt verb generalises off
  the sales/inventory tables. Blank classname, isReport=Y, no paras → resolves to `report:pp_order` → folds a
  PP_Order via the EXISTING `report_overlay.foldReceipt` over a NEW `REPORT_MAP.pp_order` row. ZERO new fold code
  (PP_Order_BOMLine has `line` + `m_product_id` → no fold-shape change). NON-FINANCIAL: BOM lines carry
  qty=QtyRequiered (required component qty), amount/price/total null; internal order → no c_bpartner_id → partner
  null; date=DatePromised (DateStart null in seed). EXTRACT: PP_Order 50000 (DocumentNo 8000, 2 BOM lines, line0
  product 50008 QtyRequiered 5050.50505051 — folded EXACTLY, no rounding of the non-integer). GUARD: the match
  uses WORD-BOUNDARIES on both alternatives (`pp_order\b` skips "rv_pp_order_transactions" 120 — `_` is a word
  char; `manufacturing order\b` skips "manufacturing orders review" 53030 — the "s" breaks the boundary); those
  report-VIEW procs + the isReport=N PP_Order process (53026) stay the honest absent-handler. Same DRIFT discipline
  (surgical REPORT_MAP edit on the lc()-bearing served variant). Witnesses poc_proc_pporder.js (W-PROC-PPORDER) +
  poc_pporder_live.js (W-PROC-PPORDER-LIVE).

**✅ P2-tail-leg5 — Rpt C_Payment (AD_Process 313 "Payment Print") · KIND 1 · HEADER-ONLY · DONE/LIVE** (bim-ootb
  PR #417, sw v727, ad_process.js?v=10, report_overlay.js?v=8). W-PROC-PAYMENT 7/7 + W-PROC-PAYMENT-LIVE PASS,
  both EXIT 0. The FIRST header-only document fold — a payment has NO line table; its amount is PayAmt on the
  header. Folds via a NEW `report_overlay.foldVoucher` (additive — `foldReceipt` UNTOUCHED, all 5 foldReceipt legs
  still PASS), NOT foldReceipt (whose subtotal=Σline would yield tax=PayAmt on an unapplied payment, e.g.
  C_Payment 101: PayAmt 50, 0 alloc lines). foldVoucher folds the real header money cols EXACTLY (BigDecimal) into
  an Amount→Value breakdown (PayAmt/Discount/Write-off/Over-Under/Tax) + total=PayAmt + headerOnly=true, NO
  synthesized line, NO `lines` key. ORACLE = the allocation invariant RE-DERIVED (not asserted): PayAmt + Discount
  + Write-off − Over/Under = the settled invoice's GrandTotal — C_Payment 100 → C_Invoice 101: 98.50+1.90+0.30−0 =
  100.70 = GrandTotal. HOST CHANGE (this leg's only one): a new `renderProcResult` branch renders the headerOnly
  voucher (Amount→Value table + "Document total …") instead of an empty line-item table; `r.headerOnly` is set
  ONLY by foldVoucher + the voucher has no `.lines` key → the receipt/KIND-2 branches never fire for it (additive,
  regression-verified live on minout/pporder). GUARD: match is SPECIFIC (`rpt c_payment\b` | "payment print"), NOT
  bare `c_payment` — so C_Payment NotAllocated (317) + RV_* payment report-views (146/148/318) + isReport=N
  C_Payment_Process (149) stay the honest absent-handler. Witnesses poc_proc_payment.js (W-PROC-PAYMENT) +
  poc_payment_live.js (W-PROC-PAYMENT-LIVE).

**✅ P2-tail-leg6 — Rpt M_InOutConfirm (AD_Process 292 "Shipment Confirmation") · KIND 1 · LINE-SORT + PRODUCT-JOIN
  variant · DONE/LIVE** (bim-ootb PR #424, sw v731, ad_process.js?v=11, report_overlay.js?v=9). W-PROC-MINOUTCONFIRM
  6/6 + W-PROC-MINOUTCONFIRM-LIVE PASS (proc 292 dispatches in-browser; M_InOutConfirm 100 folds to qty 10 / product
  "Azalea Bush" via the parent-line join; 0 pageerrors), both EXIT 0. Report-fold regression 7/7 headless + minout/
  pporder/payment live PASS. The first receipt fold whose line table is NOT a plain `*line` row: the carried
  qty lives on `m_inoutlineconfirm`, but that table has (a) NO `line` column (the existing `ORDER BY line` THROWS →
  silently empty lines) and (b) NO `m_product_id` (the product is on the underlying `m_inoutline`). FOLD-SHAPE (a
  small GENERIC extension to the existing path, NOT per-process code, NOT a new fold verb):
  · NEW `REPORT_MAP.m_inoutconfirm` row — `qty:'confirmedqty'` (the carried quantity, per the STATUS-header note),
    `amount/price/total/date:null` (a confirmation is NON-FINANCIAL and has no business date column → honest null),
    PLUS two new GENERIC map fields any consumer honours: `lineSort:'m_inoutlineconfirm_id'` (the ORDER BY column
    when there is no `line`) and `lineProductVia:{ fk:'m_inoutline_id', table:'m_inoutline', pk:'m_inoutline_id',
    product:'m_product_id' }` (resolve each line's product through the parent shipment-line join).
  · `foldReceipt` itself is UNTOUCHED (it still reads `r[map.fkProduct]`); the db-aware CALLERS (report_overlay
    `show()`, the AD_Process `_procCtx`/witness ctx `fetchLines`) honour `lineSort` for the ORDER BY and, when
    `lineProductVia` is set, resolve the product id per line and set it on the row before folding — exactly as those
    same callers already resolve product NAMES. m_inoutconfirm has no `c_bpartner_id` → partner folds honestly null.
  · NEW `report:m_inoutconfirm` registration (receiptHandler) + a resolveClassname match. GUARD — the match is
    CONFIRM-SPECIFIC (`m_inoutconfirm\b` | "shipment confirmation"): it does NOT catch 117 "Rpt M_InOut" ("shipment
    print", no "confirm"), and the SIBLINGS that must stay the honest absent-handler are 280 M_InOutConfirm_Process
    ("Process Confirmation", isReport=N → no report key synthesised), 284 RV_InOutLineConfirm Open + 285
    RV_InOutConfirm Open (isReport=Y report-VIEWs, but their values are "rv_inout*confirm" — no literal "m_inoutconfirm"
    substring and names "Open Confirmation(s)" ≠ "Shipment Confirmation" → never match).
  EXTRACT (non-invent): M_InOutConfirm 100 (DocumentNo 10000000, m_inout 108, confirmtype XC, 1 confirm-line
  m_inoutline 126 → ConfirmedQty 10 → product 128 "Azalea Bush"). ORACLE = independent re-derivation: every folded
  line qty == its `ConfirmedQty`; product resolves through the join (128 "Azalea Bush", NOT the absent confirm-line
  product); a §FALSIFIER bending ConfirmedQty shifts the folded qty exactly. Witnesses poc_proc_minoutconfirm.js
  (W-PROC-MINOUTCONFIRM) + poc_minoutconfirm_live.js (W-PROC-MINOUTCONFIRM-LIVE). Same DRIFT discipline as leg2/3/4
  (surgical REPORT_MAP edit on the lc()-bearing served `report_overlay.js`). ✅ flip + STATUS-header bump on green.

## STANDING (already LIVE — do NOT rebuild)
- AD_Process DISPATCH SPINE ✅ — `build/erp/ad_process.js` (W-PROC: classname→registry→prepare/validate→doIt,
  absent-handler falsifier; `dispatch()` surfaces a handler's own `out.reason`) + §AD-PROC-LIVE: menu P/R leaf +
  `?process=` deep link (procSet-gated), param dialog from real `ad_process_para`, prepare-gate REJECT on-screen.
- KIND-2 PLUMBING ✅ — `_ensureProcHandlers` wires ERPEngine + registers the KIND-2 handlers; `_procCtx` carries
  fetchProject/fetchProjectLines/fetchOrder/fetchOrderLines/onHandOf (all real seed rows); `renderProjectPicker` /
  `renderOrderPicker` are the RECORD-SCOPED entry pattern (clone for new record-scoped procs); the result card's
  KIND-2 branch renders the folded header+lines doc-aware (off `result.header.table`).
- buildDoc archetype + DOC_SPECS (createShipment / createInvoice) + report_overlay folds ✅ — the KIND-2/KIND-1 reuse.

## CONFLICT / SEQUENCING — RESUMABLE IN A FRESH SESSION (checked 2026-06-17)
- GIT/CODE conflict: LOW. Work is ENGINE-side (`build/erp/ad_process.js` + a new handler) + a SMALL host edit in
  the PROCESS-DISPATCH region of `idempiere.html` (`_ensureProcHandlers`/`_procCtx`/`openProcess`/`renderOrderPicker`/
  `runProcess`/`renderProcResult`). This region is DISJOINT from the in-place CRUD session (worktrees
  `/tmp/wt-inplace*`), which rewrites the FORM/GRID/TOOLBAR mount + KEEPS crud_overlay's commit engine.
- `sw.js` IS THE CONFLICT MAGNET — on a BEHIND/DIRTY PR: `git fetch && git merge origin/main`, take the HIGHER
  CACHE_VERSION + KEEP BOTH precache hunks, re-run the live witnesses, push (this session merged clean past a
  Genesis-wizard + CRUD bump that way). BEHIND = sync, NOT a redo.
- The GeneratePO/S2B button dependency is RESOLVED — we use the decoupled picker path (a); no foldCrudSpec change.
  An OPTIONAL future enhancement (path b): extend foldCrudSpec to render ref-28 process-buttons (carrying an
  `ad_process_id`, excluding DocAction/Processing) wired to `_runProcess` → the trigger appears on the form. Do as
  its own leg, sequenced after the CRUD session settles the form surface; NOT a blocker for any P2 leg.
- Tributary: return to the GRAND_LANE spine after a demand-batch, or take a user-prioritised insert.
