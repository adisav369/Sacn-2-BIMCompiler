# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: POS ENGINE LANE (Fable 5), user-assigned 2026-06-12
# Paste-to-start: `proceed with prompts/POS_ENGINE_LANE.md`
# Scope: LANE E of the killer demo (`prompts/POS_KILLER_DEMO.md` — read its §LAYOUT + handshake header first).
#   bim-compiler ONLY · headless · NO deploy · NO bim-ootb edits. The concurrent SONNET session executes Lane U
#   (UI) and is GATED on E-1 here — publish E-1 fast, the rest after.
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# NON-NEGOTIABLE: spec-first · witness-led (each test NAMES its issue) · deterministic NON-INVENT (every value
#   from the seed/dictionary or the operator's keyed input; no Date.now/Math.random in op paths) · newVerbs=[]
#   (compose the SIX existing verbs + CREATE/UPDATE ops via kernel_ops.commitGroup — a new verb = FOLD-NOT-FORK
#   red flag, STOP and report) · BigDecimal for money/qty.
# SEED FACTS: read from the canonical bim-compiler db AND cross-check `git show origin/main:erp/ad_seed.db`
#   (the shared ~/bim-ootb checkout is STALE — slim ad_image/c_doctype observed there 2026-06-12; never trust it).
#   Pre-verified 2026-06-12: ad_image EXISTS full-width (15 cols incl binarydata + ad_storageprovider_id) ·
#   c_poskey.ad_image_id exists · c_doctype full-width (DocSubTypeSO etc.) · m_inoutconfirm + m_inoutlineconfirm
#   EXIST · doctype 148 "MM Shipment with Pick" IsPickQAConfirm=Y · 147 "MM Receipt with Confirmation"
#   IsShipConfirm=Y · AD windows 330 Ship/Receipt Confirm + 333 Move Confirmation in seed.
# SHARED-TREE RULE: the bim-compiler tree is dirty with other lanes — git add EXACT paths only, never -A.
#   (This lane MAY commit its own named files when a § log proves them — single-session lane, no writer race.)
# Design sources: docs/POS_ADDON_SPEC.md §3b (§P-9/§P-10/§P-12/§P-13 + Fable5 review notes §P-9.1/§P-9.4) ·
#   docs/SPATIAL_PICKING_SPEC.md §S-2 open-docs note · build/erp/pos_core.js (the W-POS-* lineage) ·
#   build/erp/kernel_ops.js (commitGroup) · scripts/erp_engine.js (buildDoc/explodeBOM/qtyOnHand/movementSign).

---

## §API — the contract Lane U consumes (publish EXACTLY this; Sonnet wires the pill over it)
`POSCore.buildRegisterGroup(spec)` in `build/erp/pos_core.js`:
  spec = { stationId, barcode, name, price /*string, BigDecimal-safe*/, imageThumb /*dataURL|null, ≤CAP*/,
           imageKey /*content address|null*/ }
  → { ops: [...CREATE ops...], productId, poskeyId } ready for kernel_ops.commitGroup — or an HONEST
  refusal { ok:false, reason } (no-barcode · no-name · over-cap image · price not keyed).
`POSCore.buildEditGroup(spec)` analogous (UPDATE ops; only changed cols).
`ImgStore` (E-3, `build/erp/img_store.js`, UMD like pos_core): createFolder() · put(key, blob) · get(key) ·
  has(key). Browser copy = UMD of this file (sync-only into bim-ootb — the erp-source-of-truth rule).

## E-1 §P-9 register write-group — THE DEMO GATE, build FIRST, signal = green log
ONE signed commitGroup of CREATE ops (spec docs/POS_ADDON_SPEC.md §P-9, decisions .1-.4 already made):
- M_Product: mandatory cols (m_product_category_id, c_taxcategory_id, c_uom_id, …) defaulted from
  AD_Column.defaultvalue where set, else the rows the seed's own POS products use — EXTRACTED, logged
  (`§POS-REG defaults src=dictionary cat=… uom=…`), never hardcoded.
- m_productprice on the STATION's m_pricelist_version_id (the one the lens reads via c_pos) at the KEYED price.
- barcode → m_product.upc (+ value if the seed convention says so — check existing rows).
- c_poskey on the station's keylayout → the tile appears; ad_image_id wired when a photo ships.
- AD_Image: name + imageurl=content key + binarydata=capped thumbnail (≤~32KB; REFUSE over-cap — the op-log
  is the sync spine, no fat ops).
- Deterministic PKs: count prior CREATE ops (the nextIds pattern). Witness **W-POS-REGISTER**
  (`scripts/poc_pos_register.js` → `build/erp/poc_pos_register.log`): register → group commits chainOk=Y →
  the new tile RINGS at the keyed price through the UNCHANGED §P-2 sale path → falsifiers: no-barcode refused ·
  over-cap image refused · second register of the same barcode = honest collision answer (extract the rule:
  upc uniqueness is NOT enforced in real iDempiere — decide propose-merge vs allow-dup, NAME the choice).
**On green: write `# E-1 LANDED` + the API line into this card's # DONE area immediately — Sonnet polls the log.**

## E-2 §P-10 edit group
UPDATE ops (changed cols only) on M_Product / m_productprice / AD_Image via commitGroup — the #268 CRUD-rails
discipline (listOptions/splitStatusChange lineage; docs ride doc rails, master data rides the same signed path).
Witness **W-POS-EDIT**: price 1.00→X → next ring reflects X · name edit · photo swap (cap enforced) ·
falsifier: no-change edit emits NO ops.

## E-3 images folder + out-of-band copy job (§LAYOUT.3 of the demo card)
`img_store.js`: IDB object store ("the folder"), content-addressed put/get; read path = folder → ledger thumb →
null (caller renders placeholder). The COPY JOB is a separate, witnessed unit (NOT part of op sync): given a
synced log on a second device, fetch/receive blobs for every AD_Image key the log names, fill the folder.
Transport for the demo = the existing share/relay rails or explicit export-import — EXTRACT what exists
(share_sheet / relay / OCI bucket patterns) and name the choice; do NOT invent a sync protocol.
Witnesses **W-IMG-FOLDER** (put/get/has + folder-create idempotent) · **W-IMG-SYNC** (device-A register w/
photo → log to device B → B album data: thumb-only BEFORE job (`§IMG interim=thumb`), full-res AFTER
(`§IMG copied n=…`)). Headless = two IDB namespaces or two db handles; honest about what headless can't show.

## E-4 §P-13 hold / recall glue
Park = DR C_Order via commitGroup (cart lines verbatim). Recall list = plain query (DR orders, station BP).
Complete-after-recall = DOC_ACTION CO on the HELD order + createShipment/createInvoice FOR IT — never a second
buildSaleGroup. Witness **W-POS-HOLD**: park → lists in Sales Order window data + Kanban data (same rows) →
recall reloads exact lines → complete → falsifier 1: totals to the cent, nothing invented · falsifier 2:
exactly ONE C_Order exists for the sale (no duplicate).

## E-5 confirmation-layer fold (§P-12 pick gate) — ONLY after E-1..E-4 land; else ⛔-park, not this demo's gate
Fold m_inoutconfirm/m_inoutlineconfirm: doctype 148 path — shipment completeIt spawns the confirmation,
InOut waits (IP), ON-HAND MOVES AT CONFIRM-COMPLETE, not at sale. Oracle = MInOutConfirm/MInOut.completeIt
semantics (read the real Java in ~/idempiere sources; the B-1/B-2 compiled-classes technique if drivable,
else verbatim-source rule-consistency with named omissions). Witness **W-WH-CONFIRM** + falsifier (confirm
with mismatched qty → difference doc per the real model's rule, or honest refusal — EXTRACT which).
This unlocks: walk = mobile face of windows 330/333 · POS "with Pick" sales · SPATIAL §S-2 open-docs selector.

## DONE WHEN
E-1..E-4 witnesses green (logs read), files committed in bim-compiler by EXACT path, §API published, E-1
LANDED signal written for the Sonnet session, E-5 ✅ or ⛔-parked with its one fact. NO deploy from this lane —
engine files reach Pages by riding Lane U's train as sync-only copies. Report = ✅ list + ⛔ questions.
Matrix/lane-master/PROGRESS edits belong to the wave's single writer (Lane U's Phase 3) — return rows as data
if that session banks; bank yourself ONLY if you are the last session standing.

# DONE — (left for the executing session; write `# E-1 LANDED — <§ line>` here the moment W-POS-REGISTER is green)

# E-1 LANDED — 🟢 W-POS-REGISTER PASS (build/erp/poc_pos_register.log, 2026-06-12): ONE signed group of 4
#   CRUD_CREATE ops (M_Product+M_ProductPrice+AD_Image+C_POSKey), chainOk=Y, defaults EXTRACTED
#   (`§POS-REG defaults src=dictionary cat=108 uom=100 tax=107 client=11 org=0 … entitytype=U nextseqno=180`),
#   the new tile RINGS at the keyed 1.00 through the UNCHANGED §P-2 path; falsifiers green: no-barcode ·
#   over-cap (40002B>32768B) · price-not-keyed · duplicate barcode = PROPOSE-MERGE (existing handed back —
#   named decision: §P-8 scan needs barcode→ONE product; upc uniqueness NOT enforced in real iDempiere).
#
# §API AS BUILT (sync pos_core.js from bim-compiler build/erp/ — note the ctx first arg, pos_core house style):
#   POSCore.buildRegisterGroup(ctx, spec)
#     spec   = { stationId, barcode, name, price /*string, BigDecimal-safe*/, imageThumb /*dataURL|null, ≤cap*/,
#                imageKey /*content address|null*/ }
#     return = { ok:true, ops:[4× CRUD_CREATE], productId, poskeyId, priceId, imageId|null }
#              | { ok:false, reason: 'no-barcode'|'no-name'|'price-not-keyed'|'image-over-cap'|'barcode-exists' }
#     ctx    = the lens's station ctx (pos row) + register resolvers — priceListVersionId · registerDefaults() ·
#              productByBarcode(upc) · priorCreateCount(). Shapes + the EXTRACTION QUERIES the lens copies are
#              documented in pos_core.js §3b header and implemented as reference in scripts/poc_pos_register.js
#              (registerDefaults(db,pos) — dictionary literals + station-product modes + MAX(SeqNo)+10).
#     POSCore.IMAGE_CAP_BYTES = 32768 (decoded bytes; use POSCore.dataUrlBytes(dataURL) for the client check).
#     Commit = KO.commitGroup(opDb, g.ops.map(o => ({op_type:o.op_type, params:o})), {}) — same as the sale path.
#   POSCore.buildEditGroup(ctx, spec) — spec = { productId, name?, price?, imageThumb?, imageKey? };
#     UPDATE ops changed-cols-only; { ok:true, noop:true, ops:[] } on a no-change edit.
#     Edit ctx resolvers: productById · priceRowOf · poskeyOf · imageById (header docs).
#
# E-2 ✅ DONE — 🟢 W-POS-EDIT PASS (build/erp/poc_pos_edit.log, 13🟢/0🔴): price 1.00→2.50 = ONE CRUD_UPDATE
#   carrying exactly pricestd/pricelist/pricelimit {old,new}; the NEXT ring reads 2.50 through the unchanged
#   ringLine; name edit = M_Product.name + c_poskey.name (tile label); photo swap on the EXISTING AD_Image row
#   under the same ≤32KB cap; falsifiers: no-change edit emits ZERO ops (#268 no-op suppression) ·
#   unknown product refused · whole log chainOk=Y.
#
# E-3 ✅ DONE — 🟢 W-IMG-FOLDER + 🟢 W-IMG-SYNC PASS (build/erp/poc_img_{folder,sync}.log):
#   build/erp/img_store.js (UMD) — createFolder(opts) idempotent · put/get/has content-addressed ·
#   resolveImage(store, key, thumbOf) tiers full→thumb→none · syncFromLog(store, ops, fetchBlob) = THE COPY
#   JOB (idempotent; missing keys REPORTED, thumb keeps rendering). TRANSPORT NAMED: explicit-export-import
#   (relay/share rails = the same fetchBlob seam, named-deferred). Headless limit named: Map adapter; the IDB
#   binding (browser default) is wiring Lane U verifies live. Device-A register w/ photo → log to B → B
#   thumb-interim (`§IMG interim=thumb`) → job → full-res (`§IMG copied n=1` byte-exact).
#
# E-4 ✅ DONE — 🟢 W-POS-HOLD PASS (build/erp/poc_pos_hold.log, 12🟢/0🔴): buildSaleGroup refactored into
#   buildOrderOps + completionOps halves (replay-equality by CONSTRUCTION — W-POS-WR/RING/BACKFLUSH/REPLENISH
#   + W-POS-CRUD/VOID/REPLENISH-LOOP all re-run green after the refactor). API: POSCore.buildHoldGroup(ctx,
#   cart, {orderId, c_bpartner_id}) = the order half alone (DR, no completion ops) ·
#   POSCore.buildRecallCompleteGroup(ctx, heldOrder, heldLines, {inoutId, invoiceId}) = the completion half
#   on the EXISTING order (refuses not-draft). Witnessed: held DR order lists in Sales-Order-window +
#   Kanban queries (same ledger row) · recall reloads exact lines to the cent (109.25==109.25) · falsifier 2:
#   exactly ONE C_Order (1 CREATE op in the whole log, 1 row) · CO-recall refused.
#
# E-5 ✅ DONE (not parked) — 🟢 W-WH-CONFIRM PASS (build/erp/poc_wh_confirm.log, 21🟢/0🔴):
#   build/erp/inout_confirm.js — the §P-12 confirmation fold, ORACLE = real iDempiere Java read verbatim
#   (~/idempiere-dev-setup, line-cited: MInOut.prepareIt:1551 spawn · completeIt:1648 gate→IP ·
#   pendingCustomerConfirmations:2203 XC-never-blocks · MInOutConfirm.completeIt:394-480 ·
#   createDifferenceDoc:604-669 · MInOutLineConfirm.beforeSave:202 diff=target−confirmed−scrapped).
#   RULE-CONSISTENCY arm (named): GardenWorld has no completed confirmation cycle to fact-diff — anchored
#   instead on the seed's OWN rows (confirm 100 XC/DR/unprocessed on a CO shipment == the gate's verdict;
#   line 100 t=10/c=10 == the suggestion seeding). PROVEN: doctype 148 IsPickQAConfirm=Y spawns PC from the
#   DICTIONARY row · InOut waits IP · ON-HAND MOVES AT CONFIRM-COMPLETE by the PICKED qty (oak −3/plum −2
#   only after processed) · mismatched-qty EXTRACTED answer: SO-side short pick → NO difference doc,
#   MovementQty REDUCED, DifferenceQty on the confirm line; scrap → M_Inventory diff doc; PO-side linked
#   diff → AP credit memo (APC) · split-when-difference → honest named refusal (no seed case). NAMED
#   OMISSIONS: split-shipment path · DocumentNo/audit stamps (substrate) · PG-replay drive not used.
#   UNLOCKED: walk = mobile face of windows 330/333 · POS "with Pick" sales · SPATIAL §S-2 open-docs selector.
#
# ── LANE DRAINED 2026-06-12: E-1 ✅ E-2 ✅ E-3 ✅ E-4 ✅ E-5 ✅ — 6 witnesses green (W-POS-REGISTER ·
#   W-POS-EDIT · W-IMG-FOLDER · W-IMG-SYNC · W-POS-HOLD · W-WH-CONFIRM) + 7 regressions re-run green.
#   newVerbs=[] throughout. NO deploy from this lane (files ride Lane U's train as sync-only copies).
#
# ── ROWS FOR THE WAVE'S SINGLE WRITER (Lane U Phase 3 banks these; data only, not banked here) ──
#   matrix/POS rows: §P-9 register ✅(W-POS-REGISTER) · §P-10 edit ✅(W-POS-EDIT) · §P-13 hold/recall
#     ✅(W-POS-HOLD) · §P-12 confirmation fold ✅engine/W-WH-CONFIRM (walk UI + §S-2 selector = next wave) ·
#     images folder+copy job ✅(W-IMG-FOLDER/W-IMG-SYNC, browser-IDB live-verify rides Lane U).
#   SPATIAL_PICKING_SPEC §S-2 cross-ref: open-docs selector may now source docstatus IN ('DR','IP')
#     M_InOut whose doctype demands confirmation (the deliver-later shape) — engine gate exists.
#   FRONTEND_LANE_MASTER 06-12f Lane E row: ✅ all five items, witnesses above.
