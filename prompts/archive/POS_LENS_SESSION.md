# ⚠ DO NOT REMOVE — Scope guard: the POS LENS (mum-pop store front door) + engine hardening en route
# Scope: add ONE new lens — a minimal Point-of-Sale — over the FROZEN model+seam, AND harden the document
#   processing engine the POS exercises (order→ship→backflush→invoice→replenish-PO). The POS is the un-served's
#   front door; it is the convergence of everything already built, NOT new machinery.
# GATED: build this AFTER FRONTEND_LANE_MASTER.md write-path is green (items D wire-dispatch + E re-fold). A lens
#   can only ride rails that exist. If the POS needs its own persistence or a NEW verb → that is the FOLD-NOT-FORK
#   red flag = the rails were built wrong; STOP and report to the engine lane, do not hack it POS-side.
# NON-NEGOTIABLE: spec-first · witness-led (each test NAMES its issue) · §-log first (READ the log) ·
#   deterministic/NON-INVENT (real product/price rows from the imported sheet; NO Date.now/Math.random in op paths) ·
#   CLEAN-ROOM (Unicenta is defunct + code private — spec the BEHAVIOUR from the wiki + RED1's own design, copy NO code) ·
#   consume the seam / NEVER fork a verb · EXPLICIT GO before deploy.
# Read first: prompts/FRONTEND_LANE_MASTER.md (the rails this rides) · docs/AD_GEN_FROM_DICTIONARY_SPEC.md §10 (Excel
#   provider — the product import) · docs/ENGINE_CONTRACT.md §1/§2 · docs/HolyGrail.md §migration (the DocAction corpus +
#   the six verbs) · docs/PLUGIN_ARCHITECTURE.md §13.7 (readPostings).

---

## ▶ THE REFERENCE (RED1's own iDempiere Unicenta POS plugin — clean-room spec source)
Sources (idempiere.org wiki + community): Plugin:_Unicenta_POS · Unicenta_oPos_to_iDempiere · Plugin:_Wanda_POS ·
How_to_setup_POS_ACTIVEMQ · How_to_activate_the_POSSync_and_OrderSync_buttons · Plugin:_AutoBOMOrder.
- **Premise:** dumb POS records sale + payment, sends the completed order to the ERP. ERP does the intelligence.
- **Old transport:** Apache ActiveMQ 5.5.0, async queues PER station (POS Locator = Organisation/Station). Products +
  customers sync ERP→POS; orders sync POS→ERP.
- **BOM:** AutoBOMOrder backflushes components on sale and triggers replenishment.
- **Vision:** "a ring of dumb POS stations around one iDempiere ERP" — record/pay/send, no ERP smarts in the terminal.
**The translation (old → ours):** ActiveMQ queue → **signed op-log + remote sync** (offline-capable; each station = a
lens). POS plugin (Java/OSGi) → **a browser LENS** dispatching verbs via `window.ERP`. AutoBOMOrder → **the fold**
(existing verbs, no new ones). This lens is RED1's decade-old roadmap realized on the serverless-fold model.

## ▶ WHAT THE POS LENS IS (thin — most of it already exists)
A touch-first lens with ONE job loop: **import catalog → ring a sale → take payment → send to ERP → see remote report.**
1. **Catalog import** = `gen_ad.js` `providerFromExcel` (DONE): a shop's Excel of products+prices → product master →
   POS tile grid. Empty grids become a real catalog. (`§AD-GEN source=xlsx`.)
2. **Ring a sale** = touch tiles → a cart → each line is real product+price+qty from the imported sheet (NON-INVENT).
3. **Take payment** = tender → append the **ORDERLINES** as signed ops to the shared log (`window.ERP.dispatch`,
   the SEND==DRAG==VERB gesture). The payload is orderlines — NOTHING computed terminal-side, no doc, no totals.
4. **The fold derives everything** — any node folds orderlines → order → ship → backflush → invoice → PO (§ engine
   hardening below). There is NO central source of truth and NO centralised control: truth = the fold over the signed
   sequence (DistributedERP doctrine — server is a dumb facilitator, stations are peers sharing a log, merge = sequence).
5. **Remote report** = `readPostings`/fact_acct **re-folded from the log** by whoever asks (the "send reports remotely"
   itch) — not fetched from an authority; a report is just another fold of the same signed sequence.
6. **Receipt URL to the buyer** = the counterparty's signed copy of the transaction (their half of "the parties"),
   shareable by link — doubles as the buyer's **e-invoice** (MyInvois/EU mandate) for free: the signed orderline IS
   the e-invoice. `§POS-RECEIPT url=… signed=Y buyer-copy=Y`.
7. **End-of-day email to self** = the fold delivered — at close, fold the day (sales, stock, reorder, variance) and
   email the owner. No dashboard login; hands-free reporting. `§POS-EOD emailed=Y sales=… variance=…`.

## ▶ PORTING INTO THE SUITE + SIMPLEST FIRST-USE ("use it right away")
- **Ports as a LENS + a PILL, ZERO new engine.** `pos_lens.js` consumes `window.ERP` like kanban_lens/chat_lens
  (read + dispatch, NEVER fork a verb). Register one POS pill in the lens family + the setup QR/scan icon (= the
  INSTALL tier of FRONTEND_LANE_MASTER B1/§3.3, aimed at the SHOP OWNER, not a DBA). Gated on write-path D/E green.
- **POS in hand = a phone browser tab, no install.** Lens loads from a URL/QR (QR bootstrap = scan → lens loads).
  Offline-capable: orderlines append to the LOCAL signed log, merge on reconnect. The "ring of stations" = more
  phones loading the same lens against the same merged log (no central control — DistributedERP doctrine).
- **Catalog = the Excel Ninja, already built:** `providerFromExcel` (the same import RED1's Unicenta plugin gave) →
  product master → POS tiles. `handAuthored=0`.
- **GROWTH TIERS — start at ONE item, scale to a restaurant, SAME lens, no re-architecture (the "one model, many
  lenses" payoff — never re-platform from corner-stall to chain):**
  - **Tier 0 — sell one silly item NOW (the front door):** author one master row in ~10s — **snap photo** (human id /
    tile / receipt, literacy-free) + **scan QR** (machine key) + **key price** + **key opening qty** → tap → tender →
    dispatch → sold. Or onboard by **scanning stock as it arrives** (receiving = catalog build; no separate setup).
    Under a minute. `§POS-FIRSTSELL`.
  - **Tier 1 — whole catalog:** import the Excel Ninja (`providerFromExcel`) → qualify → lock → hand to cashier.
    (Same master either way — DIY snap/scan/key vs bulk import are two on-ramps to one sealed master.)

  **KEYING RECONCILIATION (not a contradiction):** keying price/qty at **master authoring** is the owner declaring
  their OWN goods (they're the authority; can't cheat self) — the legitimate ORIGIN of provenance. Keying at
  **transaction** time is forbidden: a sale scans the QR and pulls the SEALED master price, never re-keys. **You key
  the master, never the sale.**
  **TRUST TIERS (the enforcement dial = headcount, not fixed overhead):** SOLO owner = trust-yourself mode — author
  freely, no lock/qualify friction, just the convenience (perpetual stock, auto-replenish, EOD email). HIRE a cashier
  = flip SACRED mode on — master locks, sale-time keying refused, theft surfaces to the unit. Same lens; the owner
  turns the dial when there's someone who could cheat. ("No central control" applied to the security model itself.)
  - **Tier 2 — recipes:** add BOMs → selling a burger backflushes bun/meat/sauce-grams + auto-replenish (§ below).
  - **Tier 3 — ring of stations:** more phones, same merged signed log, no central control.
  Each tier is the SAME lens with MORE DATA — never a fork. Witness the front door first: `§POS-FIRSTSELL item=1 → sold`.

## ▶ ENGINE HARDENING EN ROUTE (the POS is the forcing function for the document engine)
The POS exercises the full doc lifecycle end-to-end, so it is the lever to HARDEN it. Each step MUST be a real,
witnessed engine verb — `newVerbs=[]` is the gate (same six verbs the iDempiere/Odoo/SAP folds use; a POS sale must
NOT invent a verb). Per sale the chain to harden + witness:
- **completeIt(C_Order)** → order confirmed. `§POS-DOC order=… completeIt ok`
- **M_InOut (ship / stock move out)** → on-hand decremented. `§POS-DOC inout lines=N onhand-delta=…`
- **BOM backflush** → finished-good sale consumes components (the AutoBOMOrder behaviour), recursive per BOM level.
  `§POS-BACKFLUSH parent=… components=[…] consumed=Y` (real qty from the BOM recipe, no invented consumption).
- **completeIt(C_Invoice)** → revenue + tax posted. `§POS-DOC invoice posted Dr==Cr`
- **replenishment PO** → when on-hand < min, generate a PO to the next-door/supplier source. `§POS-REPLENISH
  trigger=onhand<min product=… qty=… po=created` (the rule fires from data, not a hard-coded threshold).
Harden in DOCS as you go: ENGINE_CONTRACT (the verbs the POS calls) + HolyGrail §migration (the DocAction recipe these
verbs realize). Any gap = a NAMED finding back to the frozen engine lane, NOT a POS-side patch.

## ▶ NON-INVENT GUARDRAILS
- **DUMB TERMINAL — resist fattening the client (RED1's minimalist-fork vision; the anti-Eduardo-Gil rule).** The POS
  carries ZERO ERP intelligence: no client-side pricing rules, tax computation, inventory logic, or document state. It
  records, takes payment, and SENDS — every rule resolves in the engine fold. A "convenience" calc creeping into the
  terminal IS the fold-becomes-fork failure. The browser makes the thin terminal free — old way needed ActiveMQ +
  a fat Java client to stay thin; ours is thin by construction. Keep it dumb on purpose.
- Every cart line, price, tax, backflush qty, and PO qty traces to imported data or a BOM recipe — never synthesized.
- No POS-side ledger: the POS holds NO truth, it only dispatches; the op-log is the single source (fold-not-fork).
- Offline: a sale rung offline queues the signed op locally and folds on reconnect — state == replayed op-log, period.
- Determinism: no Date.now/Math.random in op paths (`performance.now()` only for `§METER`).

## ▶ WITNESSES (the gate)
- `§POS-IMPORT source=xlsx:<sheet> products=N priced=N → catalog tiles=N` (catalog from a real sheet, handAuthored=0).
- `§POS-SALE lines=N dispatch=SALE newVerbs=[] chainOk=Y` (one sale = one verb, signed, no new verb).
- `§POS-DOC` / `§POS-BACKFLUSH` / `§POS-REPLENISH` chain lines above — the document engine, hardened + balanced.
- `§POS-REPORT remote read posted Dr==Cr coverage=…` (the remote report half, honesty from readPostings).
- Whitebox §-log carries value verification; Playwright ONLY for wiring (tiles render, a sale dispatches). audit_specs.js exit 0.

## ▶ SCOPE BOUNDARIES (stated, not hidden)
- ONE lens, ONE store, the happy path + replenishment. NO multi-station orchestration, NO returns/voids/discounts this
  session (named as the next increment). Big-screen orchestration is a SEPARATE lens (this is the phone/touch ACT half).
- The POS does NOT compete as "a POS" (Square/Loyverse are commodity). The moat is the TAIL — backflush →
  replenishment → remote consolidated report, offline + signed + zero-install. Lead simple; show the tail.

## ▶ DEFINITION OF DONE
All `§POS-*` witnesses green; `newVerbs=[]`; the doc engine chain balanced + hardened in ENGINE_CONTRACT/HolyGrail;
the lens rides `window.ERP` with zero verb forks. Then the POS is the named front-door demo (import a shop's sheet →
sell → backflush → PO → remote report). Update PROGRESS.md + FRONTEND_LANE_MASTER §2 (POS lens = the destination the
rails lead to). EXPLICIT GO before any deploy.

---

# DONE — 2026-06-11 (§P-1..§P-4 built + witnessed; deploy HELD for explicit GO per this card)

## ENGINE SIDE (bim-compiler — `build/erp/pos_core.js`, pure fold glue, newVerbs=[])
- **W-POS-RING** (`scripts/poc_pos_ring.js` → `build/erp/poc_pos_ring.log`, exit 0):
  `§POS-IMPORT source=dictionary:c_poskey layout=100 products=16 priced=16 → catalog tiles=16 handAuthored=0` ·
  ring all 16 tiles `drift=0/16` (priceactual == m_productprice.pricestd verbatim, BigDecimal) ·
  `§POS ring product=124 qty=3 price=57.00 linenetamt=171.00` ·
  `§FALSIFIER pos=ring product=122 price-row=absent → ok=false reason=no-price` (real unpriced product REFUSED) · qty=0 refused.
- **W-POS-WR** (`scripts/poc_pos_wr.js` → `poc_pos_wr.log`, exit 0): WR from the DICTIONARY
  (`doctype 135 docbasetype=SOO docsubtypeso=WR`) · shipment/invoice ops REPLAY-EQUAL to
  E.createShipment/createInvoice · `C_Order→CO,M_InOut→CO,C_Invoice→CO` in ONE group ·
  `§POS-SALE lines=2 dispatch=SALE newVerbs=[] ops=12` · ad_docfsm `DR --CO--> CO` legal ·
  `§KRN_GROUP committed gid=… ops=12 … (WHOLE — all-or-none)` + `§KRN_CHAIN verify OK len=12` ·
  `§FALSIFIER pos=wr mutation=tamper-qty verifyChain.ok=false` (group torn) ·
  `§POS-DOC invoice posted Dr==Cr (derivePostings==fact_acct(318) accounts=3 maxDiff=0c)` (glassbowl_data anchor, order 80000).
- **W-POS-BACKFLUSH** (`scripts/poc_pos_backflush.js` → log, exit 0): ring Patio Furniture Set(145, a REAL
  key tile) → `§POS-BACKFLUSH … components=[… Cap Screw×64, Ultra Glue×4600, Seat×4 …] consumed=Y sameGroup=Y`
  — group CONSUME ops == E.explodeBOM == independent path-enumeration (NESTED recursion fires) · every CONSUME
  is 'P-' (movementSign=-1) · `§FALSIFIER pos=backflush product=124 bomLines=0 consumeOps=0`.
- **W-POS-REPLENISH** (`scripts/poc_pos_replenish.js` → log, exit 0): baseline fold == iDempiere formula SQL
  `products=8 maxDiff=0` · `§POS-REPLENISH trigger=onhand<min product=124 qty=11 po=suggested` (ring Elm ×6 →
  available 9 ≤ min 10 → QtyToOrder 11 = max−available) · **THE TAIL**: `tail=backflush parent=145 component=134
  consumed=2 → suggested qty=5` (the CONSUMED Patio Table crosses ITS min — AutoBOMOrder→ReplenishReport, one chain) ·
  PO via buildDoc(REPLENISH_PO_SPEC) `newVerbs=[]` · `§FALSIFIER … policy-row=absent suggestionsChanged=false`.
- `scripts/erp_engine.js` got a UMD tail (audit (c): browser copy = UMD of this file) — body unchanged;
  poc_replenish + poc_backflush + all 4 W-POS-* re-run green after the wrap (0 red lines).

## LENS SIDE (bim-ootb branch `feat/pos-lens`, worktree /tmp/wt-poslens — NOT deployed)
- `erp/pos_lens.js` (dumb terminal: record/pay/SEND — zero pricing/tax/inventory/doc state client-side) ·
  `erp/pos_core.js` + `erp/erp_engine.js` synced from bim-compiler source of truth · `pos` pill in
  `pills_idmp.json` (Lucide shopping-cart verbatim in icons.js, order 4.5) · `idmp_pills.js` generic
  `showWhen:pos-station` gate (mirrors posting-doc) · `idempiere.html` openPosFor → PosLens.open over
  `_b3` + `window.ERP.opDb` + KernelOps (publishes the seam via the SAME _ensureKanbanERP path).
- **W-POS-LIVE** (`scripts/poc_pos_live.js` → `build/erp/poc_pos_live.log`, exit 0):
  `§POS-LIVE open station=100 tiles=16 priced=16 handAuthored=0` ·
  `§POS-SALE lines=2 dispatch=SALE newVerbs=[] chainOk=Y gid=… ops=12 sealed=12` ·
  `§POS-DOC order=910001 completeIt ok` · `§POS-LIVE-REPLENISH suggestions=8` rendered on-screen ·
  refusals honest (empty cart / no partner → no commit; seed c_pos.BPartnerCashTrx is NULL, §-named).

## RESIDUALS (named, not hidden)
- **Matrix "POS lens" row PENDING** (spec §5 bar = a LIVE ring folded to the cent): to-the-cent proven
  headless on the acct-linked db; live ad_seed.db lacks posting linkage (same data-gate as Posting-Preview
  → `prompts/MIGRATE_POSTING_CONFIG.md` lights it).
- Card §6/§7 items NOT in the addon contract's witness set (spec supersedes): receipt-URL buyer copy
  (`§POS-RECEIPT`) · EOD email (`§POS-EOD`) · Tier-0 snap/scan first-sell (`§POS-FIRSTSELL`) · returns/voids/
  discounts · §P-5 multi-station ring — each a next increment, none blocks the loop shipped here.
- Replenish seed honesty: station wh 104 carries no policy/ledger rows — crossing legs ring wh 103 (§-named
  in the witness); live panel shows the wh-103 baseline (8 suggestions).
- audit_specs.js exit 1 — PRE-EXISTING viewer violation (38-sh-dx-2d-runtime.spec.js, 5 SKIP paths),
  untouched by this session (no deploy/dev/tests changes here); belongs to the viewer lane.
- Docs hardened en route: ENGINE_CONTRACT §1 (first-addon note) · HolyGrail §DocAction (recipe runs live at POS).

## DEPLOY (the card's EXPLICIT-GO rule — not taken)
Branch `feat/pos-lens` committed, NO PR, NO sw.js bump. On GO: deploy train = rebase off origin/main →
sw v651→v652 + precache pos_lens.js/pos_core.js/erp_engine.js + ?v= bumps → re-run W-POS-LIVE on the bumped
tree → PR → auto-squash → orphan check → Pages live-verify.

## DEPLOY DONE — 2026-06-12 (GO taken; wave-2 deploy train, every claim = a § line)
- **PR:** #269 https://github.com/red1oon/bim-ootb/pull/269 — feat(erp-pos): POS lens addon §P-1..§P-4 (sw v652), MERGED.
- **sw:** v651 → v652 (erp/sw.js CACHE_VERSION; +precache pos_lens.js, pos_core.js, erp_engine.js;
  ?v= bumps icons.js v2→3, idmp_pills.js v10→11, pills_idmp.json v27→28).
- **Orphan check (the #138/#265 trap — did NOT fire):** squash d8d3adf552aa05b9c319aa552a83cf6921d2b9b5
  carries the diff — `git show origin/main:erp/sw.js` → v652; `git show origin/main:erp/pos_lens.js | head -3`
  → MIT header + lens banner; `git ls-tree origin/main erp/` lists erp_engine.js + pos_core.js + pos_lens.js;
  precache grep in origin/main sw.js = 3/3 entries.
- **Live verify (Pages):** live erp/sw.js serves CACHE_VERSION="v652" (CI-minified — single-quote/spaced greps
  miss it; use quote-agnostic patterns) + the pos_lens precache entry; curl erp/pos_lens.js returns the lens
  source (window.PosLens published, §POS-LIVE/§POS-SALE/§POS-DOC strings present); erp/idempiere.html carries
  erp_engine.js?v=1 + pos_core.js?v=1 + pos_lens.js?v=1 + icons.js?v=3 + idmp_pills.js?v=11.
  pages-build-deployment run 27368173617 success; fast-checks 15s + e2e-tests 59s green (no GP.2 flake rerun).
- **W-POS-LIVE re-run on the bumped tree** (ERP_ROOT=/tmp/wt-poslens/erp, `build/erp/poc_pos_live.log`, exit 0):
  `§POS-LIVE open station=100 tiles=16 priced=16 handAuthored=0` ·
  `§POS-SALE lines=2 dispatch=SALE newVerbs=[] chainOk=Y gid=e3607c9a-23a1-4404-9424-3821da498f2f ops=12 sealed=12` ·
  `§POS-DOC order=910001 completeIt ok (C_Order+M_InOut+C_Invoice CO in ONE group)` ·
  `§POS-LIVE-REPLENISH suggestions=8 (suggest-by-default; PO via buildDoc on tap)` ·
  `🟢 W-POS-LIVE PASS — gated pill, 16 dictionary tiles, one signed group (chainOk=Y, newVerbs=[]),
  live replenishment fold, refusals honest`.
- **Residuals (stand as named in # RESIDUALS above):** matrix "POS lens" row pends the live to-the-cent ring
  (posting-config data-gate, `prompts/MIGRATE_POSTING_CONFIG.md`) · next increments §POS-RECEIPT / §POS-EOD /
  §POS-FIRSTSELL / returns-voids-discounts / §P-5 ring · replenish legs ring wh 103 (station wh 104 has no
  policy/ledger rows) · audit_specs.js exit 1 = PRE-EXISTING viewer violation (38-sh-dx-2d-runtime.spec.js),
  untouched by this train.

---

# ▶▶▶ NEXT SESSION — DIY MOBILE POS UX (user-dictated 2026-06-12, spec'd `docs/POS_ADDON_SPEC.md §3b / §P-6..§P-11`) ▶▶▶

**SYNC 2026-06-12 (Fable5 review — read before building):** wave 3 LANDED (bim-ootb PR #274, sw
v655): `pos_lens.js` on origin/main now carries the replenish-PO-on-tap wiring (`vendorOf()` from
`m_product_po` + `POS.buildReplenishPO`) — extend it, don't clobber it. Three new headless witnesses
exist: `scripts/poc_pos_{crud,void,replenish_loop}.js` (W-POS-CRUD · W-POS-VOID · W-POS-REPLENISH-LOOP)
— re-run them green after ANY pos_lens/pos_core touch. **Void/reverse is DONE (§L-2)** — the
"returns/voids" residual below is now returns-with-restock UI only. §P-9 data-Q (a) is **DECIDED**
(AD_Image row + capped thumbnail in `binarydata` — verified in the origin/main seed; see
`docs/POS_ADDON_SPEC.md §P-9.1`); Qs (b)/(c) stand. ⚠ the shared `~/bim-ootb` checkout can be STALE
(slim ad_image/c_doctype observed there) — verify seed facts against `git show origin/main:erp/ad_seed.db`.
Engine-boundary items (§P-9/§P-10 write groups, §P-12 deliver-later DR-shipment sale, §P-13
hold/recall pos_core glue) = the Fable5/engine lane; this card's UI session builds §P-6/§P-7/§P-8/§P-11
+ presentation halves only. §P-11 payable-QR has a ⛔ user fact (see the rails-honesty note in the spec).

**Hard rule, user repeated 3×:** *never disturb the underlying engine or flow* — this whole lane is the
lens's PRESENTATION + input layer. Every price stays `POSCore.ringLine`, every sale stays ONE
`kernel_ops.commitGroup`, every product write rides the SAME signed write path as a sale. If anything
needs a NEW verb or POS-private persistence → STOP (FOLD-NOT-FORK red flag), report to the engine lane.

**Build → show → next (user rhythm; UI-iteration = screenshot + show before deploy):**
1. **§P-6 Mobile layout (PURE UI — do first, it's the live complaint).** `pos_lens.js` `wrap` is a flex
   ROW; `side` `min-width:230px` hogs a phone → items squeezed to one left column. Fix = scoped
   `@media (max-width:~640px)`: items GRID 3-wide (12 fill screen, ~20 overflow → scroll up), payment =
   FIXED bottom sheet (~40–46vh, `env(safe-area-inset-bottom)` clear of phone nav), LARGE total, grid
   bottom-padded to scroll clear. Add ids `pos-wrap/pos-grid/pos-side/pos-total`; touch NO JS logic.
   Desktop two-col row unchanged (falsifier). **Mock on puppeteer 390×844 + SCREENSHOT, tune by eye,
   show user before deploy.** Witness W-POS-MOBILE. (Harness to drive POS open: `scripts/poc_pos_live.js`
   — `idempiere.html?login=GardenAdmin` → open dock → `#pill-pos` pointerup → `.pos-tile`.)
2. **§P-7 pill-icon-only POS mode** (Lucide-only, walk-mode "engage" idiom). W-POS-PILLS.
3. **§P-8 continuous QR scan** — QR icon → camera stays live, each barcode adds a line + flashes total,
   no re-tap; resolves barcode→product then EXISTING ringLine. Shared W-QR-INPUT module (same as
   `wh_walk.js` scanner). Unknown barcode → honest "register it?" (→ §P-9). W-POS-SCAN.
4. **§P-11 receipt + payment QR** — after Complete, on-screen receipt, last item flashes (not obscuring
   the LARGE total), payment-QR button encodes the committed `GrandTotal` + order ref (DISPLAY of the
   sale, no new financial logic). W-POS-RECEIPT (QR amount == GrandTotal to the cent).
5. **§P-9 register-a-product + §P-10 edit (⚠ ENGINE BOUNDARY — settle 2 data Qs with user FIRST):**
   scan barcode + snap photo → M_Product @ Std 1.00 (editable), tile sells immediately. WRITES master
   data → ONE signed `commitGroup` of CREATE ops (M_Product + m_productprice@1.00 + upc + c_poskey),
   like `buildSaleGroup` composes order/ship/invoice. **Qs:** (a) photo = ✅ DECIDED — AD_Image row
   in the signed group, `binarydata` = downscaled ≤~32KB thumbnail (spec §P-9.1; origin/main seed
   verified); (b) m_productprice needs the station's pricelist_version row at 1.00; (c)
   deterministic PK alloc (count prior CREATE ops, never Date.now/random); (d) mandatory M_Product
   cols (category/taxcategory/UOM) defaulted from the DICTIONARY, never hardcoded (spec §P-9.4).
   W-POS-REGISTER / W-POS-EDIT. **ENGINE LANE builds these** (write-group glue); UI session wires the icons only.

**Deploy discipline:** erp/sw.js train (CACHE_VERSION bump once per landing, orphan-check the squash,
Pages live-verify quote-agnostic). erp/sw.js currently v655. pos_lens.js?v=1. §P-6..§P-8/§P-11 = no
engine touch; §P-9/§P-10 compose existing ops only.

## ▶ ADDED 2026-06-12 (design discussion → hardened in `docs/POS_ADDON_SPEC.md`; user passing to Fable5)
6. **§P-12 POS ⇄ WH walk = a DOCUMENT FOLD, not a coupling.** Completed POS sale already commits an
   `M_InOut`; the walk's §S-2 already sources open `M_InOut`/`M_Movement` lines → "finish sale → go
   pick it" is two ends of ONE ledger. Build cost = `wh_walk.js draftPick` gains an option to source
   the route from open POS-generated docs (additive §S-2 selector; cross-ref `SPATIAL_PICKING_SPEC §S-2`).
   Surface switch = **open the walk in a NEW TAB** (POS tab never unloads → cart preserved; deeper
   coupling SHELVED). Navigation/back = **the Z/W History timeline is the single source of event
   navigation**; **REJECTED: browser-back auto-restoring POS state** (the cross-page deep-link timing
   bug class, recurred 3× — v636/v637/v642). Granular Z punches = **spec-to-verify** against the
   existing `history_tap.js §EVT SNIFF` sniffer BEFORE building (witness: Z timeline replays a POS
   session); don't build blind.
7. **§P-13 Hold / recall sale (the real primitive).** Park the in-progress cart as a **draft `C_Order`
   (DR)** via the existing signed `commitGroup`; recall from a list on tap (user-initiated → dodges the
   timing bug class). **Must appear in the Sales Order window + Kanban board** (proof it's ledger-folded,
   not a private store — else fold-not-fork red flag). Witness W-POS-HOLD (held order lists in Sales
   Orders/Kanban; recall reloads exact lines; totals to the cent, nothing invented).
8. **Webstore/POS-display filter — CHECKED 2026-06-12 (don't fix):** this seed's `m_product` has NO
   `IsWebStore`/`IsSold`/`IsPurchased` (real iDempiere does; slim seed omits). POS tiles are governed by
   **`c_poskey` membership**, not a product flag → a "show on POS" toggle = c_poskey add/remove, not a
   product checkbox. Incidental: **`c_poskey.ad_image_id` already exists** → §P-9 snapped photo's
   dictionary-native home = an `AD_Image` row on the new key's `ad_image_id` (data-Q (a) ✅ DECIDED 2026-06-12:
   AD_Image row + capped `binarydata` thumbnail — spec §P-9.1; origin/main seed verified).
