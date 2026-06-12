# POS Addon Spec — the 2012 Unicenta loop, rebuilt as folds

*Spec for the first op-log-native **addon**: a browser Point-of-Sale on the migrated tenant, with
BOM **backflush** and **replenishment** — the same loop RED1 shipped in the 2012 era as the
[Unicenta POS plugin](https://wiki.idempiere.org/en/Plugin:_Unicenta_POS) +
[AutoBOMOrder](https://wiki.idempiere.org/en/Plugin:_AutoBOMOrder) for iDempiere, with the middleware
deleted. Doctrine: [The POS Lens](POSLens.md). Roadmap home: item 1 of the
[Migrate & Compare paper's roadmap](MigrateComparisonPaper.md#v-roadmap).*

> **Status: §P-1..§P-4 BUILT + WITNESSED (2026-06-11, `prompts/POS_LENS_SESSION.md` `# DONE`).**
> Engine side: `build/erp/pos_core.js` (pure fold glue over the existing verbs, newVerbs=[]) —
> W-POS-RING · W-POS-WR · W-POS-BACKFLUSH · W-POS-REPLENISH all green (`build/erp/poc_pos_*.log`).
> Lens side: `erp/pos_lens.js` + the `pos` pill (showWhen:pos-station) on idempiere.html —
> W-POS-LIVE green on localhost (bim-ootb branch `feat/pos-lens`, deploy awaiting explicit GO).
> §P-5 multi-station stays out of scope (named below). The matrix "POS lens" row stays PENDING:
> §5's bar is a LIVE ring folded to the cent — the to-the-cent leg is proven headless on the
> acct-linked db (W-POS-WR), but the live ad_seed.db lacks posting linkage (the same data-gate as
> Posting-Preview; lighting it = `prompts/MIGRATE_POSTING_CONFIG.md`).
> Clean-room rule: Unicenta is defunct and the plugin code private — behaviour is spec'd from the
> public wiki + RED1's own design; **no code is copied**.
>
> **Status update 2026-06-12 (supersedes the PENDING lines above):** GO was taken — POS lens LIVE
> (bim-ootb PR #269, sw v652). The posting data-gate is DISSOLVED: default `ad_seed.db` carries the
> acct linkage (PR #271, v653) and the live ring folds **to the cent** — `§POS-CENT maxDiff=0c` —
> so the matrix "POS lens" row is FULLY lit. Wave 3 (PR #274, sw v655) closed the full loop:
> **§L-1 CRUD on POS docs** (W-POS-CRUD) · **§L-2 void/reverse** (W-POS-VOID — postings net 0c,
> on-hand restored, double-void refused) · **§L-3 replenishment ENACTED** (W-POS-REPLENISH-LOOP —
> suggest → PO CO → receipt CO → on-hand +N to the unit → suggestion clears; `vendorOf()` from real
> `m_product_po` rows, PO-on-tap wiring live in `pos_lens.js`). Voids are therefore DONE;
> returns-with-restock UI remains a next increment. Witnesses: `scripts/poc_pos_{crud,void,replenish_loop}.js`.

---

## 1. What "addon" means here

In 2012 the integration needed a fat Java POS, ActiveMQ queues per station, and sync plugins on both
ends. The premise was right — *the POS is dumb: record the sale, take payment, send the order; the
ERP holds the intelligence* — but the plumbing was the product. An addon on the browser kernel keeps
the premise and deletes the plumbing:

| 2012 (Unicenta ⇄ iDempiere) | Addon (this spec) |
|---|---|
| Fat Java POS client per station | One **lens** (HTML/JS surface) over the resident tenant db |
| ActiveMQ queue per station, POSSync/OrderSync | The **signed op-log** — ops are the queue, replay is the sync |
| Station = AD_Org/POS Locator config | Same — `c_pos` row per station (already in the seed) |
| AutoBOMOrder plugin backflushes on sale | `erp_engine.explodeBOM` — already witnessed (W-FOLD-BACKFLUSH) |
| ReplenishReport → PO | `poc_replenish.js` formula fold — already witnessed (W-FOLD-REPLENISH) |
| Products/customers sync ERP→POS | Nothing to sync — the lens reads the same db the ERP renders |

An addon therefore touches **four registries and zero engines**:

1. a **pill** in the pill registry (the only way UI enters — `pills_idmp.json` + `IdmpPillActions`),
2. **AD windows** it may deep-link (already in the dictionary — see §2),
3. **process handlers** in the `ad_process.js` registry (`registerHandler(classname, fn, meta)`),
4. **ops** through the kernel (`kernel_ops.commitGroup`) — handlers never write tables directly.

If an addon needs a fifth thing — its own persistence, a new verb, a forked engine — the rails were
built wrong: **stop and report to the engine lane** (the FOLD-not-FORK red flag in
`prompts/POS_LENS_SESSION.md`).

## 2. Substrate inventory — what already exists (EXTRACT, don't rebuild)

Everything below is shipped and witnessed as of 2026-06-11; the addon *consumes* it.

**The POS dictionary ships in the browser seed** (full-width `ad_seed.db`, bim-ootb PR #265 — the
old column-slice had these tables but the windows were unreachable):

| Asset | Rows | Detail |
|---|---|---|
| `c_pos` | 1 | "Garden User - Store": doctype 135, keylayout 100, warehouse 104, pricelist 101, cashbook, cashdrawer |
| `c_poskey` / `c_poskeylayout` | 163 / 5 | the ring-up key grid, real GardenWorld layouts |
| `u_posterminal` | 1 | extended terminal config (37 cols) |
| AD windows | — | 338 POS Terminal · 339 POS Key Layout · 200008 POS Payment · 200009 POS Tender Type · Sales Order tab 200016 "POS Payment" |
| `C_DocType` 135 | — | DocBaseType `SOO`, **DocSubTypeSO `WR`** — iDempiere's on-the-fly shipment+invoice POS order |

**The fold verbs** (`scripts/erp_engine.js` — pure, host-injected, no DB/clock):

| Verb | Witness | What it proves |
|---|---|---|
| `buildDoc(spec, parent, lines)` | W-FOLD-BUILDDOC | the one archetype create-verb; `createShipment`/`createInvoice` are *specs* of it, not code |
| `explodeBOM(bomOf, productId, qty)` | W-FOLD-BACKFLUSH (`build/erp/poc_backflush.log`) | recursive recipe explosion on the REAL GardenWorld Patio recipe; multi-path accumulation (cap-screw 8+8=16); flat-explosion falsifier |
| `movementSign` / `qtyOnHand(events, opts)` | W-FOLD-QTYONHAND | on-hand is a FOLD of the movement ledger (trailing-char polarity), never a trusted column |
| replenishment formula | W-FOLD-REPLENISH (`poc_replenish.js`) | the `ReplenishReport:294-327` port — `available = onhand − reserved + ordered`, type 1/2 → `QtyToOrder` → PO via `buildDoc`; **oracle-equivalent to iDempiere's own formula SQL, per product, to the unit** |

**The spine** the addon rides: `ad_docfsm.js` (`legalActionsOrder` knows `docsubtypeso`; WR completes
CO→CO), `doc_poster.derivePostings` + `post_resolver` (frozen, oracle-anchored postings),
`kernel_ops.commitGroup` (atomic signed op groups, hash-chained), `ad_process.js`
`registerHandler` (the addon's classname entry-point), and the pill registry
(`pills_idmp.json` + `idmp_pills.js`, Lucide line icons only).

**Conceptual spine:** [POSLens.md](POSLens.md) §1 (one clean act in), §5 (scan/QR door), §6
(backflush), §7 (shrinkage), §11 (honest scope at time of writing — this spec supersedes its
"to build" list where §2 above says witnessed).

## 3. The addon, end to end (the 2012 loop as folds)

```
ring items (c_poskey grid / QR scan)
  → C_Order (doctype 135 WR, c_pos defaults: warehouse, pricelist, BP-cash)
  → tender (POS Payment — cash first; tender types from window 200009's dictionary)
  → Complete = ONE signed op group:
        CREATE_DOCUMENT C_Order + lines            (buildDoc)
        CREATE_DOCUMENT M_InOut C- + lines          (buildDoc spec createShipment)
        CREATE_DOCUMENT C_Invoice + lines           (buildDoc spec createInvoice)
        CONSUME leaf components                     (explodeBOM — only when the product is a BOM)
        DOC_ACTION CO on each                       (ad_docfsm dispatch)
  → postings = derivePostings on each doc (preview before, fold after — same engine)
  → on-hand falls (qtyOnHand fold over the new movements)
  → replenishment fold sees available ≤ Level_Min → suggests/creates the PO (W-FOLD-REPLENISH)
```

No step is new machinery. The *sequence* is the addon.

### §P-1 POS surface (the lens)

- Pill `pos` in `pills_idmp.json` (icon: Lucide `shopping-cart` family; `showWhen` gated on a
  `c_pos` row existing for the logged-in org — data-gated like the Posting-Preview pill).
- Surface renders FROM the dictionary: the key grid from `c_poskey`/`c_poskeylayout` (163 real
  keys), prices from the `c_pos.m_pricelist_id` list, BP defaulting to `c_bpartnercashtrx_id`.
  Zero per-product code — a new product is a row, not a feature.
- Money math via `site/bigdecimal.js` (never raw JS Number — standing rule).
- Witness **W-POS-RING**: `§POS ring product=<id> qty=<n> price=<pricelist-row>` — every ringed
  line traces to a `c_poskey`→`m_product`→`m_productprice` row. §FALSIFIER: ringing a product
  absent from the price list must refuse (no invented price).

### §P-2 Complete = the signed group (the sacred transaction)

- One `commitGroup` carrying order+shipment+invoice+backflush+doc-actions. All-or-none; gid
  idempotent; hash-chained (POSLens §4 unbroken provenance).
- The WR semantics come from the dictionary (`docsubtypeso='WR'`), not from POS code — the same
  order completed from the ERP window must produce the same group.
- Witness **W-POS-WR**: group replay == the engine's own `createShipment`/`createInvoice` specs;
  postings == `derivePostings` to the cent; §FALSIFIER: tampered op breaks `verifyChain`.

### §P-3 Backflush (AutoBOMOrder reborn)

- On Complete, for each line whose product is a BOM (`pp_product_bom` lookup — host-injected
  `bomOf`), `explodeBOM` yields leaf consumption; emit the consumption movements in the SAME group.
- Already proven on the real recipe (W-FOLD-BACKFLUSH); the addon's witness **W-POS-BACKFLUSH**
  only needs to prove the *wiring*: ring Patio Set ×1 → the §-log shows the same leaf dict the
  headless witness derived. §FALSIFIER: a non-BOM product must emit zero consumption ops.

### §P-4 Replenishment (the loop closes)

- After the group commits, run the W-FOLD-REPLENISH fold over the new ledger state; render the
  suggestion (product, warehouse, QtyToOrder) in the POS surface; one tap = the PO group
  (`buildDoc`, IsSOTrx=N) — *suggest by default, auto-create behind a `c_pos`-level flag*.
- Witness **W-POS-REPLENISH**: sell below `Level_Min` → suggestion appears with the
  formula-derived qty; §FALSIFIER: selling a non-replenish product never suggests.

### §P-5 Multi-station ring ("a ring of dumb POS stations around one ERP")

- Each station = a `c_pos` row (org-scoped), its ops a signed channel — the 2012 ActiveMQ
  queue-per-station, now just the op-log's own grouping. Offline-first: ops seal locally,
  sync rides the existing §0.20 sync FSM / relay when present. **Out of scope for the first
  build session; named so it is never re-invented.**

## 3b. DIY single-user UX — the mobile checkout (user direction 2026-06-12)

*All of §P-6..§P-11 below is the lens's PRESENTATION + input layer. Hard rule the user repeated
three times: **never disturb the underlying engine or flow** — every price is still `POSCore.ringLine`,
every sale is still ONE `kernel_ops.commitGroup`, every product write rides the SAME signed write-path
as a sale. The dumb-terminal contract (§1) is unchanged; these sections only change what the operator
sees and how input is collected. Target user: a lone DIY operator on a phone, grabbing stuff lying
around to demo a sale right away. A dedicated session resumes this — `prompts/POS_LENS_SESSION.md`.*

### §P-6 Mobile layout — items grid + payment as a fixed bottom sheet  *(pure UI; do FIRST)*

**Observed (user live test, mobile):** the lens is a flex ROW — `grid` (items, `flex:2`) beside
`side` (cash panel, `min-width:230px`). On a ~390 px phone the `min-width` side panel hogs the right,
squeezing items into ONE left column. Not mobile-fit.

**Spec:**
- Items in a GRID that fills the width — ~3 across × 4 down = **12 tiles fill the first screen**; with
  ~20 products the rest **overflow below**, the operator scrolls the item blocks UP.
- **Payment = a fixed bottom sheet**, floating over the items, anchored low but **not flush to the very
  bottom** (leave the phone's own nav/home-indicator clear — `env(safe-area-inset-bottom)`); occupies
  roughly the lower ~40–46vh. The item grid gets bottom-padding so its last row scrolls clear above it.
- The running **Total shows LARGE** in the sheet (the operator's primary readout).
- Desktop layout (the current two-column row) must stay intact — drive it from a `@media (max-width)`
  query scoped to the overlay; add ids (`pos-wrap`/`pos-grid`/`pos-side`/`pos-total`) but touch NO JS
  logic (cart, `ringLine`, `buildSaleGroup`, handlers all byte-identical).
- **Build method (user-dictated):** mock it on a MOBILE viewport (puppeteer 390×844, deviceScaleFactor)
  and **screenshot — tune by eye**, not §-log alone. Show the user before deploy (UI-iteration rule).
- Witness **W-POS-MOBILE**: screenshot at 390×844 with a seeded cart; grid is 3-wide, payment sheet is
  fixed at bottom and does not cover the grid's scroll; `§POS-MOBILE cols=3 sheet=fixed total=large`.
  §FALSIFIER: at desktop width the two-column row is unchanged.

### §P-7 Pill-icon-only POS mode  *(pure UI)*

POS mode presents its actions as **pill icons only** (the walk-mode "engage a distinct mode" idiom):
a clean DIY surface, Lucide-only icons (the pill-icon-consistency rule), no verbose chrome. The icons
host §P-8..§P-11 (scan, register, edit, receipt). Witness **W-POS-PILLS**: actions render as icon
pills; `§POS-PILLS n=<k> icons-only=Y`. §FALSIFIER: non-icon text chrome absent in POS mode.

### §P-8 Continuous QR scan mode  *(input only — reuses ring)*

Tapping the **QR icon** enters a **continuous scan mode**: the camera stays live and each scanned
barcode **adds a line and flashes the total**, item after item, with no re-tap between scans (cashier
convenience). Each scan resolves the barcode → product (the §P-9 registry / `m_product.upc`/value),
then calls the EXISTING `POSCore.ringLine` + cart add — **no new pricing/cart logic**. The QR module is
the proven W-QR-INPUT pattern (`BarcodeDetector` + `getUserMedia`, honest typed fallback), shared with
the warehouse walk's scanner. Witness **W-POS-SCAN**: N scans → N ring calls, total updates each;
unknown barcode → honest "not found — register it?" (links §P-9), never a fabricated line.

### §P-9 Register-a-product (DIY) — scan + snap → M_Product  *(⚠ ENGINE BOUNDARY — discuss before build)*

A **register icon**: the operator scans an item barcode, **snaps a photo**, and the product is created
at **Std price 1.00** (a real user-entered default, editable), usable for a sale immediately.

- **This is a master-data WRITE** — it must ride the SAME signed write path as a sale: a
  `kernel_ops.commitGroup` whose ops CREATE the `M_Product` row (+ the `m_productprice` row at 1.00,
  + the barcode as `upc`/value, + a `c_poskey` so the tile appears). Not a bypass, not invented data.
  The engine/verbs are unchanged; this just composes existing CREATE ops into a new group, the way
  `buildSaleGroup` composes order/ship/invoice ops.
- **DATA DECISIONS (1 = DECIDED on extracted facts, Fable5 plumbing review 2026-06-12):**
  1. **Photo = an `AD_Image` row in the SAME signed group — ✅ DECIDED.** Verified on the
     origin/main full-width seed: `ad_image` EXISTS (15 cols **including `binarydata`**) and
     `c_poskey.ad_image_id` references it — fully dictionary-native, no schema change. ⚠ the
     stale shared `~/bim-ootb` checkout shows a slim `ad_image` WITHOUT `binarydata`; always
     verify against `git show origin/main:erp/ad_seed.db`. **Plumbing bound:** the op-log is the
     sync/storage spine (O.c bytes discipline) — `binarydata` carries a DOWNSCALED tile-resolution
     thumbnail (≤ ~32KB JPEG, ~256px), produced client-side before commit; tiles need exactly that.
     Full-res may additionally sit device-local (IndexedDB), never in the op-log. §FALSIFIER:
     a register op whose image exceeds the cap is refused (no fat ops in the chain).
  2. **`m_productprice` requires a pricelist version** — the DIY product needs a price row on the
     station's `m_pricelist_version_id` (the same one the lens already reads), at 1.00.
  3. **PK allocation** for the new product/price/poskey ids (the deterministic-id discipline — count
     prior CREATE ops, like `nextIds`, never `Date.now`/random).
  4. **Mandatory M_Product columns come from the DICTIONARY, not invention** — real iDempiere
     requires `m_product_category_id`, `c_taxcategory_id`, `c_uom_id` on M_Product: default each
     from `AD_Column.defaultvalue` where set, else the station's existing GardenWorld rows (e.g.
     the category/UOM the seed's own POS products use). EXTRACT the defaults; never hardcode.
- Witness **W-POS-REGISTER**: scan+snap → ONE signed group creates product+price+poskey, `chainOk=Y`,
  the new tile rings at 1.00 and sells through §P-2 unchanged. §FALSIFIER: a register with no barcode
  or no price refuses; the price is the operator's value, never auto-invented beyond the 1.00 default.

### §P-10 Edit-a-product  *(⚠ ENGINE BOUNDARY — same write path)*

An **edit icon**: change a DIY product's name / price / photo. Same signed-write discipline — an UPDATE
op group on `M_Product`/`m_productprice` through `commitGroup`, the CRUD-edit-persist path already
proven for documents (`crud_overlay` lineage). Witness **W-POS-EDIT**: edit price 1.00→X → signed group
→ the tile and the next ring reflect X; `chainOk=Y`.

### §P-11 On-screen receipt + payment QR  *(presentation of a completed sale)*

After Complete (§P-2 commits the signed sale), show an **on-screen receipt**: the lines + the LARGE
total, the **last item flashes but does not obscure** the total, and a **payment QR button** renders a
QR the customer scans to pay by the usual rail (e.g. Maybank2U / DuitNow). The QR encodes a payment
string (amount + reference) — it is a *display* of the already-committed order total, **no new financial
logic** (the sale is the truth; the QR is a presentation of its grand total). Witness **W-POS-RECEIPT**:
after a sale, receipt shows the order's exact total, payment QR encodes that amount + order ref;
§FALSIFIER: the QR amount == the committed `GrandTotal` to the cent, never a recomputation.

> **⚠ Rails honesty (Fable5 review 2026-06-12, ⛔ pending user fact):** real DuitNow QR is an EMVCo
> payload bound to a REGISTERED merchant id — a self-composed "amount+ref" QR will not parse in a
> bank app; composing a synthetic EMVCo string = inventing. The mum-and-pop-honest variant: the owner
> snaps/uploads their OWN static DuitNow QR once (stored exactly like the §P-9 photo — an `AD_Image`
> row) and the receipt displays THAT QR with the LARGE committed total beside it (customer keys the
> amount — the universal static-QR flow). Order-ref QR generation stays for the receipt-URL use
> (§POS-RECEIPT link), which any camera can read. Ask the user which payable-QR reality applies
> before building the "encodes amount" half.

**Sequencing (user rhythm — build → show → next):** §P-6 layout first (pure UI, the live complaint) →
§P-7 pill icons → §P-8 continuous scan → §P-11 receipt+QR → then §P-9/§P-10 product register/edit
(after the two open data decisions are settled with the user). The first four touch no engine; the last
two write master data through the existing signed path only.

### §P-12 POS ⇄ WH walk — a document fold, NOT a live coupling (design agreed 2026-06-12)

The realization (user): the two lenses are **already connected through the ledger**, so there is no
coupling to build. A completed POS sale already commits an `M_InOut` (§P-2: C_Order + M_InOut +
C_Invoice in one group); the warehouse walk's §S-2 route already sources "open `M_Movement` /
`M_InOut` lines." So the honest flow is: **finish the sale → its shipment/movement is an open doc →
go to WH walk → the walk routes that doc and you pick it.** Another device picking the same list is
just the multi-device version of the same fold (the §P-5 / distributed story — roles on separate
devices, one ledger). The mobile demo "just shows the roles."

- **Build cost is small and additive:** the walk's `draftPick` (`wh_walk.js`) gains an option to
  source its route from **open POS-generated `M_InOut`/`M_Movement` docs**, alongside its current
  replenishment draft — a §S-2 selector, not new machinery. Spec the cross-ref in
  `docs/SPATIAL_PICKING_SPEC.md §S-2`.
- **Open-doc honesty (engine note, Fable5 review 2026-06-12):** the WR sale (§P-2) completes
  `M_InOut → CO` **inside the sale group** (W-POS-WR: `C_Order→CO, M_InOut→CO, C_Invoice→CO` in ONE
  group) — its lines are NOT open and on-hand has already moved; routing a walk over them would
  double-move stock. "Finish the sale → go pick it" therefore rides the **deliver-later shape**: a
  sale on the dictionary's plain `SOO` doctype (seed has `132 Standard Order`) whose shipment stays
  `DR` — the walk's scan-commit (§S-4) IS the act that completes the `M_InOut` and moves on-hand.
  That DR-shipment sale variant is **engine glue (pos_core spec choice from the dictionary), not a
  walk-side patch** — engine lane builds it; the walk's selector then honestly sources
  `docstatus IN ('DR','IP')` only. WR stays the cash-and-carry default (nothing to pick).
- **Switching surfaces** (POS on idempiere.html → walk on viewer.html): **open the walk in a new
  tab** (`_blank`). The POS tab never unloads, so its cart is preserved by definition — no draft, no
  restore key, no cross-page timing. (Mobile tab-swap is acceptable; the user confirmed it is no
  longer a big issue.) A deeper in-app coupling stays **shelved** — not needed for the role demo.
- **Navigation / "back" = the History timeline is the single source of event navigation.** The Z
  (page) / W (world) history already deep-links a surface+state across pages; POS↔walk hops are
  entries in that same log. **REJECTED (do not build): making the browser BACK button responsible
  for restoring POS state** — that is the cross-page deep-link-restore timing bug class that recurred
  3× here (World-card "sometimes gets there", sw v636/v637/v642). The timeline (tap to reach back)
  is deterministic; back-button-auto-restore is not.
- **Granular Z events — spec-to-verify (not now):** the Z history should record granular user
  "punches" (the buttons pressed), so the timeline can walk an operator's exact session. There is
  already a sniffer — `history_tap.js` `§EVT SNIFF|on — recording every §act, deny-filtered`. NEXT
  SESSION: check whether POS/walk punches already surface through that sniffer (likely cheap), and if
  so spec a witness that the Z timeline replays a POS session; if not, scope the gap. Do not build
  blind — verify against the existing `§EVT` log first.

### §P-13 Hold / recall sale — the in-progress cart as a DR order (the real primitive)

Instead of bespoke state-rescue, POS gains the standard **hold / recall** primitive: park the
in-progress cart as a **draft `C_Order` (docstatus DR)** through the existing signed `commitGroup`,
recall it from a list on tap. This is useful far beyond any walk excursion (phone dies, switch
customer, close the tab) and it **sidesteps the timing bug class** because recall is *user-initiated
on tap*, never auto-restore on page load.

- **Proof it is folded in, not a private store (user requirement):** a held/draft order MUST appear
  in the standard **Sales Order window** and therefore on the **Kanban board** — because it is a real
  DR `C_Order` in the ledger, the same row those surfaces already read. If a held sale does NOT show
  up in Sales Orders, the parking used a private store and is wrong (fold-not-fork red flag).
- **Recall completes the HELD order (engine note, Fable5 review 2026-06-12):** on recall→Complete,
  the group must `DOC_ACTION CO` the EXISTING DR `C_Order` and build ship/invoice FOR IT (the engine's
  `createShipment`/`createInvoice` specs take the order) — never re-run `buildSaleGroup` into a second
  order. That park/complete split is pos_core glue (engine lane), the list/recall UI rides it.
- Witness **W-POS-HOLD**: hold a cart → DR `C_Order` committed (`chainOk=Y`) → it lists in the Sales
  Order window + Kanban → recall re-loads the exact lines into the cart → Complete proceeds normally.
  §FALSIFIER: the held order's lines/total == the cart to the cent; nothing invented on park or recall.
  §FALSIFIER 2: after recall+Complete exactly ONE `C_Order` exists for the sale (no duplicate row).

### Findings — webstore/POS-display filter (checked 2026-06-12, per user "check, don't fix")

- This seed's `m_product` carries **no `IsWebStore` / `IsSold` / `IsPurchased`** column (only
  `IsActive`). Real iDempiere has `IsWebStore`; the slim `ad_seed.db` `m_product` does not. **Not
  fixed** (per instruction). **Which items appear on POS is governed by `c_poskey` membership** (a
  product tiles because it is in the POS key layout), not a per-product checkbox — so a future
  "show on POS" filter is a `c_poskey` add/remove, not a product flag, unless the seed gains the
  column via the install lifecycle.
- Incidental for §P-9: **`c_poskey.ad_image_id` already exists** → the snapped product photo's
  dictionary-native home is an `AD_Image` row referenced by the new key's `ad_image_id` (the tile
  then renders it). **DECIDED 2026-06-12 (see §P-9.1): AD_Image row + capped thumbnail in
  `binarydata`** — verified present in the origin/main seed.

## 4. Honest gaps (named, not hidden)

- **WR completeIt enactment**: `ad_docfsm` proves the *status* walk; the order→ship→invoice
  *enactment* exists as engine verbs + witnesses, but the live wiring (UI Complete → group) is
  exactly what §P-2 builds. GardenWorld has no production docs, so backflush stays
  **rule-consistent** (two independent implementations agree + falsifier), not fact_acct-diffed.
- **Tender types beyond cash** (card processors) — dictionary rows exist; processors are not
  in scope (the 2012 plugin was cash-first too).
- **Seed scale**: 1 `c_pos` row / 163 keys is a demo shop; a real tenant brings its own rows via
  the install lifecycle (NEW_CLIENT_MGMT — closed for Odoo + iDempiere).
- **Gate** (unchanged from the work-order): build AFTER the write-path lane is green; a lens can
  only ride rails that exist.

## 5. Done-when

Each § above is ✅ (witness lines + on-screen verify + deploy) or ⛔ with the one blocking fact;
matrix gains a "POS lens" row only when W-POS-WR folds a live ring to the cent.
