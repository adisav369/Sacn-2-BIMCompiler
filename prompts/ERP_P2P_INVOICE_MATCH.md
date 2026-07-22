# ⚠ DO NOT REMOVE — scope: build the Procure-to-Pay closer — Material Receipt (Stage 7) + Vendor
# Invoice/three-way match (Stage 8) — on the live iDempiere surface (bim-ootb `erp/idempiere.html` +
# `erp/crud_overlay.js` + `erp/erp_engine.js`). Read the log after every run (regenerate via
# `bash build/erp/run_witness.sh scripts/witness_p2p_invoice_match.js` from bim-compiler). Spec-first:
# every fix below is EXTRACTED from the real iDempiere Java source at
# `~/idempiere-dev-setup/idempiere` before a line of JS is written — never invented.

**2026-07-22** — scoped OUT of `prompts/ERP_BUSINESS_CYCLE_E2E.md`'s closed O2C lane (its `§Closed`
section named this as a separate project). Implementation branch: bim-ootb `feat/p2p-invoice-3way-match`
(worktree `/tmp/wt-p2p-invoice-match`).

## THE QUESTION

Can a real user complete the P2P closer — Purchase Order → Material Receipt → Vendor Invoice, with a
genuine three-way match (PO ⋈ Receipt ⋈ Invoice) — through the live iDempiere UI, and if the two
structural gaps named in `ERP_BUSINESS_CYCLE_E2E.md` (`§Closed`) are real, what EXACTLY does the real
Java do instead of the SO-only paths already ported?

## §Extract — real Java ground truth (`~/idempiere-dev-setup/idempiere`, read before any JS written)

**Confirms the existing SO-only gates are NOT bugs.** `org.compiere.process.InOutGenerate` (Generate
Shipments) and `org.compiere.process.InvoiceGenerate` (Generate Invoices) — already ported faithfully in
`erp/ad_process.js` (`inoutGenGate`/`invoiceGenGate`, both citing real line numbers) — genuinely hard-select
`IsSOTrx='Y'` in the real Java too. There is no PO-side twin of these batch processes. **Do not touch either
gate.** The Purchase side uses a completely different, already-identified real mechanism:

1. **Material Receipt lines** come from `org.compiere.process.CreateFromInOut` (window-level "Create Lines
   From" action) → `MInOut.createLineFrom()` (`MInOut.java:3206`). The Receipt HEADER (BPartner, Warehouse,
   doc type Receipt) is entered manually first; then the user picks open PO lines from a selection screen,
   enters/adjusts qty, and `createLineFrom` copies `M_Product_ID`, UOM, `M_AttributeSetInstance_ID`,
   `C_OrderLine_ID` (the match link) and other order-line fields straight from `C_OrderLine` — qty is the
   only free input.

2. **Vendor Invoice lines** come from the mirror process `org.compiere.process.CreateFromInvoice` →
   `MInvoice.createLineFrom()` (`MInvoice.java:3262`) → `MInvoiceLine.setShipLine()`/`setOrderLine()`
   (`MInvoiceLine.java:330`/`286`). **Price (`PriceEntered`/`PriceActual`/`PriceLimit`/`PriceList`), tax, and
   line amount are ALWAYS copied from the PO/Receipt line — never freely typed.** Only qty is user-adjustable.
   `GrandTotal` is the roll-up of these copied line amounts — this is exactly consistent with `c_invoice`'s
   existing `grandtotal` field note in `crud_ops.json` ("derived by replaying the log... not entered"): adding
   line-item creation via Create-From does NOT violate that guarantee, because the price still isn't typed.

3. **`M_MatchPO`** rows are created at DOCUMENT-COMPLETION time, not by a separate matching UI —
   `MInOut.completeIt()` (`MInOut.java:2079-2096`): for each receipt line with `C_OrderLine_ID != 0`, calls
   `MMatchPO.create(null, sLine, movementDate, qty=sLine.getMovementQty())`, stamping
   `M_InOutLine_ID`/`C_OrderLine_ID`/`M_Product_ID`/`Qty`. (Edge case: invoice-created-before-shipment also
   creates `MMatchPO` from `MInvoice.completeIt()`, `MInvoice.java` ~line 2134 — NAMED-DEFERRED here, see
   §Fix sequencing below; the mainline receipt-first path is what this lane witnesses.)

4. **`M_MatchInv`** rows are created the same way at completion time — `MInvoice.completeIt()`
   (`MInvoice.java:2085-2109`): for each purchase invoice line with `M_InOutLine_ID != 0` and a completed
   receipt, `qty = min(receiptLine.MovementQty, invoiceLine.QtyInvoiced)`, `new MMatchInv(line, dateInvoiced,
   qty).save()`. **This exact shape is already ported** in `erp/erp_engine.js`'s `completeInvoice()`
   (lines 266-274, citing "real Java line ~2075") — it has simply never been wired to the live UI's doc-action
   fanout (`completeFanout` in `crud_overlay.js` is hard-gated to `op.key === 'c_order'` only, line 1894-1895).

5. **Three-way match has no separate validator process.** It is the natural join of `M_MatchPO` and
   `M_MatchInv` both pointing at the same `M_InOutLine_ID` — confirmed by `MMatchPO.java`'s own cross-reference
   logic (`beforeSave`, lines 1004-1041) and its hard invariant (lines 1105-1116): if a `MMatchPO` row has both
   `M_InOutLine_ID` and `C_InvoiceLine_ID` set, a corresponding `M_MatchInv` row for that pair MUST already
   exist, else `IllegalStateException`. **This shared-`m_inoutline_id` linkage is the witness proof for Stage
   8** — not a new report/process.

## §Design — the JS port (mirrors the real Java 1:1, reuses existing O2C-lane machinery)

| Real Java | JS port target | Reuses |
|---|---|---|
| `CreateFromInOut` picker + `createLineFrom` | New "Create Receipt Lines from PO" picker in `idempiere.html`, modeled on `renderOrderPicker` (2414-2469) but sourced from `c_orderline` WHERE parent `issotrx='N' AND docstatus='CO'` | picker UI shape, `applyOpGroup` commit wrapper |
| `MInOut.completeIt()` → `MMatchPO.create` | New `completeReceipt(receipt, lines, policy)` in `erp_engine.js`, same shape as `completeInvoice` | `completeInvoice`'s CREATE_LINE-only (no buildDoc) pattern |
| `CreateFromInvoice` picker + `createLineFrom`/`setShipLine` | New "Create Invoice Lines from PO/Receipt" picker, same shape as the Receipt picker, sourced from `m_inoutline` for a Purchase receipt | same picker shape again |
| `MInvoice.completeIt()` → `MMatchInv` | **Already written**, `erp_engine.js:266-274` `completeInvoice()` — just needs wiring | — |
| fan-out gate | Generalize `completeFanout` (`crud_overlay.js:1894-1922`) from `op.key === 'c_order'`-only to also branch `m_inout` → `completeReceipt`, `c_invoice` → `completeInvoice` | `applyOpGroup`/`commitGroup`, unchanged |

**Explicitly NOT doing:** touching `inoutGenGate`/`invoiceGenGate`/`InOutGenerate`/`InvoiceGenerate` (real
Java confirms these are Sales-only, full stop); adding a freely-typed `grandtotal`/price field anywhere
(violates the extracted price-copy rule and the existing anti-invent note); building a general
"three-way-match report" (real Java has none — the shared FK linkage in the op-log IS the proof).

## §Correction — no new picker needed for lines (found mid-build, before any wrong code shipped)

The original plan above (Fix 2/4: a custom "Create Lines From PO" picker, committing via `CREATE_LINE`/
`applyOpGroup`) was WRONG and was built, then discarded, before landing: `M_InOutLine` (real AD_Tab 297
"Receipt Line") and `C_InvoiceLine` (real AD_Tab 291 "Invoice Line") already have live AD_Field dictionary
rows exposing `C_OrderLine_ID`/`M_InOutLine_ID`/`M_Product_ID`/qty (verified directly against `ad_seed.db`,
not assumed) — meaning `crud_overlay.js`'s GENERIC AD-dictionary-driven CRUD (`foldCrudSpec`, "S2B: AD-folded
CRUD spec, general not curated") already supports creating both line types manually, the same generic
mechanism `C_OrderLine` itself already uses (PR #956's "child-tab parent-FK" fix works on ANY table, not
just `c_order`'s children). **No new UI code is needed to create the lines — a user can already navigate
the Receipt/Invoice's Line child-tab and enter `c_orderline_id` + product + qty by hand.**

A second, more serious problem surfaced by writing (and discarding) that picker first: `completeFanoutOrder`'s
own established pattern — `db.exec('SELECT * FROM c_order WHERE c_order_id=...')` against the raw `withBundle`
db — **only ever finds a SEED row.** A manually-created record (Receipt, Invoice, or its lines) exists ONLY
as `CRUD_CREATE` ops in the sidecar's `kernel_ops` (confirmed: `commitCrud`/`_commitCrudSealed` write only to
the sidecar, never to the raw bundle; `listTip`'s own doc-comment: "Created rows get a SYNTHETIC negative
pk" — and the witness log's own Stage 6/7 lines show real receipt/PO ids as negative, e.g. `id=-5`). The SAME
class of bug the O2C lane's own `§Fix 2026-07-21` (picker overlay gap) already found and fixed for
`renderOrderPicker` — just recurring here in `completeFanout`'s NEW branches, caught before shipping instead
of after. **Fixed**: `completeFanoutReceipt`/`completeFanoutInvoice` now fold via `CORE.listTip` against the
sidecar (the exact convention `renderOrderPicker` already established), not a raw bundle SELECT.

Price/tax auto-copy-from-the-PO-line (real Java's `setShipLine`/`setOrderLine`) is NOT ported — a user must
type price manually on the invoice line for now. NAMED-DEFERRED (a convenience gap, not a blocker): the
structural requirement for a real three-way match is the `c_orderline_id`/`m_inoutline_id` LINKAGE, which the
existing generic form already lets a user set; the amount typed doesn't need to be faked to prove the match
mechanics work.

## §Fix sequencing (witness after each layer, same cadence as the O2C lane)

1. Header fields: `m_warehouse_id`/`c_bpartner_id`/`c_order_id` on `m_inout`, `c_bpartner_id`/`c_order_id` on
   `c_invoice` + `"create"` verb on `c_invoice`. **DONE.**
2. Per-window `movementtype`/`issotrx` derivation for a manually-created `m_inout` (AD_Tab 296 "Material
   Receipt" 's real WhereClause is `MovementType IN ('V+')`, not `IsSOTrx`, unlike `c_order`/`c_invoice` —
   a NEW seam, `MInOut.movementTypeFromWindow`) + the equivalent `issotrx`-from-window seam for `c_invoice`
   (`MInvoice.issotrxFromWindow`, `c_invoice`'s tabs DO use `IsSOTrx` directly so the EXISTING `_createIsSOTrx`
   thread just needed a table-specific consumer hook). **DONE** — no new picker needed (see §Correction).
3. `completeReceipt` (erp_engine.js) + `completeFanout` generalized (was `c_order`-only) to dispatch `m_inout`
   → `M_MatchPO` emitted on Receipt CO, reading via listTip fold (not raw bundle). **DONE, unwitnessed.**
5. `completeFanout` dispatch for `c_invoice` → `completeInvoice()` (already written in the O2C lane) now
   actually runs → `M_MatchInv` emitted on Invoice CO, same listTip-fold fix. **DONE, unwitnessed.**
6. End-to-end witness: drive PO → Receipt(CO) → Invoice(CO) through the real UI (manually entering
   `c_orderline_id`/`m_inoutline_id` on each line via the existing generic child-tab form); confirm `MATCH_PO`
   and `MATCH_INV` CREATE_LINE ops in the op-log share the same `m_inoutline_id` (the three-way-match
   invariant), `verifyChain=ok`. **NEXT.**

Each numbered item lands as its own PR, `§Fix` section appended below, dated, most recent first — same
discipline as `ERP_BUSINESS_CYCLE_E2E.md`.

## §Fix — 2026-07-23, Fixes 1/2/3 WITNESSED end-to-end; Fix 5's exact remaining layer found

Witness: `scripts/witness_p2p_invoice_match.js` (bim-compiler), run against `/tmp/wt-p2p-invoice-match`
(bim-ootb `feat/p2p-invoice-3way-match`). Full log: `build/erp/witness_p2p_invoice_match.log`.

**PROVEN, real signed op-log evidence** (`§P2P-MATCH` line, `build/erp/witness_p2p_invoice_match.log:1608`):
```
M_MatchPO rows=[{"op_type":"CREATE_LINE","table":"M_MatchPO","c_orderline_id":108,"m_inoutline_id":-5,
  "m_product_id":123,"qty":1,"_sv":1,"_sigv":2,"signed_by":"3059301306072a8648ce..."}]
```
A real user: opened a real seed Purchase Order's line (104/108), created a Material Receipt header through
the live UI with the new `m_warehouse_id`/`c_bpartner_id`/`c_order_id` fields (§Fix 1 — confirmed shown on
the form, `§P2P-FORM` line), had `movementtype`/`issotrx` correctly auto-derived from the window
(§Fix 2 — `§AD-MODELVAL-LIVE table=m_inout verb=create ... derived={"movementtype":"V+","issotrx":"N",...}`),
created a Receipt Line linked to the real PO line via the (unlocked, manually-fillable) `c_orderline_id`
field, then hit Complete — and `completeFanoutReceipt`/`completeReceipt` (§Fix 3) emitted a REAL,
cryptographically signed `M_MatchPO` CREATE_LINE op, `verifyChain=ok`. **Stage 7 (Material Receipt) is
UNBLOCKED, for real, proven — not asserted.**

The Vendor Invoice side (§Fix 1's `c_invoice` "create" verb + §Fix 2's `issotrxFromWindow`) is ALSO proven
working the same way — header form mounts, `issotrx` correctly derives to `'N'`
(`derived={"issotrx":"N",...}`), the invoice reaches CO through the real DocAction bar. But
`§INVOICE-FANOUT invoice=-8 issotrx=N lines=1 matchInvOps=0` — **zero** `M_MatchInv` ops, because the
invoice LINE's `m_inoutline_id`/`c_orderline_id` fields could not be set: `§P2P-FILL m_inoutline_id
value=-5 result=locked`.

**Root cause, EXTRACT-verified, not a bug in this lane's own code:** queried `ad_seed.db` directly —
`AD_Field` for `C_InvoiceLine_ID`'s `M_InOutLine_ID`/`C_OrderLine_ID` columns (`AD_Tab_ID=291`) both carry
`IsReadOnly='Y'` at the FIELD level (hard lock, not merely `IsUpdateable='N'` — `foldCrudSpec`'s own
readonly rule, `crud_overlay.js` ~line 692, only relaxes `IsUpdateable='N'` on CREATE, never `IsReadOnly='Y'`
regardless of verb). **This is a faithful port of real iDempiere**, confirmed by the earlier Java extract
(`ERP_P2P_INVOICE_MATCH.md §Extract` point 2): these two columns are ONLY ever set by
`MInvoiceLine.setShipLine()`/`setOrderLine()` — i.e. by the `CreateFromInvoice` PROCESS, never by a user
typing into the form directly, in the real product too. (By contrast, `M_InOutLine`'s own
`C_OrderLine_ID` — the field that DID work above — is NOT `IsReadOnly='Y'` on `AD_Tab_ID=297`, confirmed;
that asymmetry in the real AD dictionary is exactly why Stage 7 could be closed by a plain form fill while
Stage 8 cannot.)

**What Fix 5 actually needs (not yet built):** a SMALL, targeted seed mechanism — not the abandoned generic
picker (§Correction above), and not a relaxation of the readonly lock (that would be inventing a UI real
iDempiere doesn't have) — that pre-fills `m_inoutline_id`/`c_orderline_id`/copied-price into the
c_invoiceline New form's value BEFORE it renders, the same class of mechanism `idempiere.html buildForm()`'s
existing `seedVals` already uses for a child tab's locked PARENT-link column (PR #956,
`ERP_BUSINESS_CYCLE_E2E.md §Fix 2026-07-22 "order-LINE parent-FK"`) — generalized to a PEER cross-reference
FK instead of the tab's own parent link, triggered from a real AD_Process entry point that already exists
in the seed (`AD_Process 200143 "Create lines from Invoice"`, `org.compiere.process.CreateFromInvoice`,
mandatory para `C_Invoice_ID`) rather than a bare New-form click. Scoped as its own next dated `§Fix`, not
implemented yet.

**Status:** Fix 1/2/3 done, witnessed, real. Fix 5 (M_MatchInv) has its exact remaining blocker named with
file:line-level precision — no rediscovery needed for whoever picks it up next.
