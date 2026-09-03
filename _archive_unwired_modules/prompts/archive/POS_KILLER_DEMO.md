# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: POS KILLER DEMO (snap-and-sell), user-dictated 2026-06-12
# Paste-to-start: `use a workflow — proceed with prompts/POS_KILLER_DEMO.md`
# Scope: TWO lanes + serial train. The killer demo: open shop cart → GW album → Import pill → SNAP a photo +
#   CAPTURE barcode + key price → tile appears with photo → tap → tender → receipt. Sold in under a minute,
#   browser tab, offline-capable, signed end-to-end (§POS-FIRSTSELL realized).
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# Design sources (read first): docs/POS_ADDON_SPEC.md §3b (§P-6..§P-13 + Fable5 review notes) ·
#   prompts/POS_LENS_SESSION.md (SYNC preamble + NEXT block) · docs/SPATIAL_PICKING_SPEC.md §S-2 open-docs note ·
#   the 06-12f block in prompts/FRONTEND_LANE_MASTER.md (lane split) · this card's §LAYOUT (user dictation).
# HOUSE RULES (each has bitten): bim-ootb edits ONLY in /tmp/wt-* off FRESH origin/main · ONE PR in flight,
#   sw bump once per landing, orphan-check the squash, Pages greps quote-agnostic · matrix/lane-master ONE writer ·
#   bim-compiler lane agents do NOT git-commit (dirty shared tree; writer commits exact paths) · seed facts from
#   `git show origin/main:erp/ad_seed.db`, NEVER the stale shared ~/bim-ootb checkout · UI deploys WAIT for the
#   user to see screenshots (UI-iteration rule) · newVerbs=[] gate everywhere · MODEL SPLIT: engine=Fable5, UI=Sonnet.
# EXISTING BRANCH: feat/pos-diy-ux on /tmp/wt-posux (NOT merged) carries built §P-6 grid/§P-7 pills/§P-8 scan/
#   §P-11 receipt + screenshots — UI lane CONTINUES that branch (rebase onto fresh origin/main first); the §P-6
#   fixed bottom sheet is SUPERSEDED by §LAYOUT's floating panel (keep the grid/media work, replace the sheet).

# ▶ TWO-SESSION SPLIT (user-assigned 2026-06-12): THIS card = the SONNET / LANE-U session (UI). LANE E runs in
#   its OWN Fable-5 session via `prompts/POS_ENGINE_LANE.md` — do NOT execute Lane E items here.
# ▶ RUN ORDER / HANDSHAKE (Sonnet may start FIRST):
#   - U-1, U-2, U-4 have ZERO engine dependency — build them now, screenshots to the user.
#   - U-3 is ⛔ GATED on Lane E's E-1: proceed only when `build/erp/poc_pos_register.log` exists with
#     `🟢 W-POS-REGISTER PASS` (read the log, not the exit code) — then SYNC pos_core.js (+ img_store.js if
#     present) from bim-compiler build/erp/ into the worktree and wire the pill over the published API
#     (`POSCore.buildRegisterGroup(spec)` — shape pinned in POS_ENGINE_LANE.md §API).
#   - pos_core.js / erp_engine.js / img_store.js in the worktree are SYNC-ONLY copies (erp-source-of-truth
#     rule) — NEVER hand-edit them in bim-ootb; lens files (pos_lens.js etc.) are yours.
#   - U-1 image reads go through a feature-detect: `window.ImgStore?.get(key)` → ledger thumb fallback →
#     placeholder glyph. ImgStore absent (E-3 not landed) is a NORMAL state — album must render without it.
#   - ONE deploy train for the whole wave, AFTER U-3 + the user's screenshot OK (default; user may order an
#     earlier layout-only landing — then train twice, sw bump each).

---

## §LAYOUT (user dictation 2026-06-12 — the crammed-mobile fix, verbatim intent)
1. **Item ALBUM:** products as a nice scrollable frame of CARDS WITH IMAGES — the landing-page building-card
   idiom (clear, big touch target) restyled POS-like: photo, name, PRICE prominent, tap = ring (flash feedback).
   The album owns the screen; it scrolls; nothing else sits in its layout flow.
2. **Floating payment panel (replaces the fixed bottom sheet):** summoned by the cart/payment PILL; a THIN
   floating panel — cart lines, LARGE total, tender button — **draggable by the user to a preferred spot**;
   position persisted per device (localStorage; presentation state, NOT the op-log). It lives on its OWN
   z-layer over the album and is NOT attached to the album's scroll/transform layer (the current sheet moving
   with the items layer is the named bug). Dismiss = pill again or swipe-down.
3. **Images "folder" model:** a device-local images folder = an IndexedDB object store, created on first use
   ("create folder"); album cards render from it by content key. The LEDGER carries only the AD_Image row with
   the content KEY + a capped bootstrap thumbnail (≤~32KB, spec §P-9.1) — when the log is posted, image blobs
   COPY OVER AS A SEPARATE JOB (out-of-band image sync, witnessed); a NEW user's freshly-created folder points
   at the synced blobs and the album renders. Thumb = instant tiles before the folder syncs; folder = full-res.

## LANE E — ENGINE (Fable 5 · bim-compiler ONLY · headless · NO deploy; files returned uncommitted)
- **E-1 §P-9 register write-group (THE DEMO GATE — first):** ONE signed `commitGroup` of CREATE ops —
  M_Product (mandatory cols defaulted from the DICTIONARY per spec §P-9.4) + m_productprice on the STATION's
  m_pricelist_version_id at the keyed price + barcode→upc + c_poskey (tile) + AD_Image (key + capped thumb,
  refused over-cap). Deterministic PKs = count prior CREATE ops (nextIds pattern), never Date.now/random.
  Witness **W-POS-REGISTER** + falsifiers (no-barcode refused · over-cap image refused · price=keyed value only).
- **E-2 §P-10 edit group:** UPDATE ops on M_Product/m_productprice/AD_Image via commitGroup (crud rails).
  Witness **W-POS-EDIT** (price 1.00→X; next ring reflects X; chainOk=Y).
- **E-3 images folder + copy job:** `img_store.js` (IDB folder: create/put/get by content key, thumb fallback
  read from the ledger row) + the out-of-band copy job (separate, witnessed: device A registers w/ photo →
  log syncs → device B creates folder → job copies blobs → album renders). Witness **W-IMG-FOLDER** /
  **W-IMG-SYNC** (B renders from folder; thumb-only until job runs — honest interim named in the §-log).
- **E-4 §P-13 hold/recall glue:** park cart = DR C_Order via commitGroup; recall completes the HELD order
  (createShipment/createInvoice FOR it — never a duplicate buildSaleGroup). Witness **W-POS-HOLD** + both
  falsifiers (spec §P-13).
- **E-5 confirmation-layer fold (§P-12 pick gate — start only if E-1..E-4 land):** fold m_inoutconfirm/
  m_inoutlineconfirm; doctype 148 "MM Shipment with Pick" path — InOut completes via confirm, ON-HAND MOVES
  AT CONFIRM not at sale; walk = the mobile Ship/Receipt-Confirm + Move-Confirmation (windows 330/333) surface.
  Witness **W-WH-CONFIRM** (oracle = MInOutConfirm.completeIt semantics; named omissions allowed). Else ⛔-park
  with the one fact needed — this is the wave-after centerpiece, not this demo's gate.

## LANE U — UI (Sonnet · /tmp/wt-posux branch feat/pos-diy-ux CONTINUED · deploys via the train)
- **U-1 album rework (§LAYOUT.1):** product cards w/ images (img_store key → thumb fallback → placeholder
  glyph when neither); landing-card idiom, POS-styled; tap=ring flash. Keep §P-6 grid/media-query work.
  Witness **W-POS-ALBUM** (`§POS-ALBUM cards=N imgs=N thumbs=N placeholders=N`) + desktop falsifier.
- **U-2 floating payment panel (§LAYOUT.2):** pill-summoned, draggable (pointer events), position persisted
  (presentation-only), own z-layer, never in the album scroll layer. REPLACES the fixed sheet. Witness
  **W-POS-FLOAT** (`§POS-FLOAT drag=ok persisted=Y layer=own`) — totals/tender logic byte-identical.
- **U-3 Import pill camera flow (over E-1):** snap + barcode capture (reuse §P-8 scanner) + key price →
  E-1 group → tile appears WITH photo → sell immediately. THE demo witness **W-POS-FIRSTSELL**
  (`§POS-FIRSTSELL snap=Y scan=Y price=keyed tile=rendered sold=Y elapsed<60s`). Same pill later fronts the
  import spine (Excel/social feeds = batch §P-9) — name it "Import", do NOT build the batch half now.
- **U-4 DEMO payment QR (§P-11 payable half — UNBLOCKED by user 2026-06-12):** generic mockup labeled
  **DEMO/SAMPLE**, no branded rails/logos, wired as the `paymentQR(provider, amount, ref)` seam with
  provider=DEMO shipping (the C_PaymentProcessor seam analogue — SIs plug real gateways here). Witness
  **W-POS-PAYQR** (amount == committed GrandTotal to the cent; DEMO label visible in the screenshot).
- **U-5 screenshots:** every U item mobile 390×844 + desktop falsifier → /tmp/wt-posux/shots/ — the train
  WAITS until the user has seen them (UI-iteration rule; the card's one hard user-gate).

## PHASE 2 — deploy train (serial, after user OKs screenshots)
Rebase feat/pos-diy-ux onto fresh origin/main → sw v655→next (+precache qrcode.min.js + any new module; ?v=
bumps pos_lens.js etc.) → re-run W-POS-LIVE + the three regression witnesses (crud/void/replenish_loop) +
new W-POS-* on the bumped worktree → push → PR → auto-squash → WAIT merged → orphan check → Pages live-verify
(version + one §-behavior probe). S274 GP.2 e2e flake on ERP-only diffs → rerun once.

## PHASE 3 — single-writer bank (one agent, after the train)
bim-compiler: commit Lane E files then Lane U's bim-compiler files (exact paths, separate commits) · docs:
POS_ADDON_SPEC §3b status + §LAYOUT cross-ref + SPATIAL §S-2 if E-5 ran · matrix: POS rows evidence update ·
# DONE appendices (this card + POS_LENS_SESSION) · FRONTEND_LANE_MASTER §OUTSTANDING 06-12f → ✅/⛔ per item ·
PROGRESS §Current State · push. Watchdog: no § line = not done.

## DONE WHEN
W-POS-REGISTER/EDIT/ALBUM/FLOAT/FIRSTSELL/PAYQR + W-IMG-FOLDER/SYNC green · demo runs end-to-end on a phone
(snap→scan→price→tile→sell→receipt w/ DEMO QR) · live on Pages after the user's screenshot OK · banked.
E-5 ✅ or ⛔-parked with its one fact. Report = ✅ list + ⛔ questions (WORK-TO-ZERO).

## NAMED RESIDUALS / NEXT WAVES (do not open here)
iOS BarcodeDetector absent → JS decoder fallback later (Android Chrome = demo device) · batch import spine
(Excel exists as providerFromExcel; social-feed adapters = own card) · BPartner import (same spine) ·
§P-12 walk doc-list selector UI + fulfillment-walk (rides E-5) · §S-12/§S-13 WH context layer ·
returns-with-restock UI · §P-5 multi-station · EOD email.

# DONE — 2026-06-12 (killer demo SHIPPED LIVE; two-session split, deploy train clean)

## LANE E — ENGINE (Fable-5 session via prompts/POS_ENGINE_LANE.md, banked bim-compiler 9857aefc)
- **E-1 W-POS-REGISTER** ✅ — `POSCore.buildRegisterGroup`: ONE signed group (M_Product + m_productprice@keyed +
  c_poskey + AD_Image), defaults extracted from AD_Column + station rows, deterministic PKs (max+900000), tile rings
  at the keyed price through the unchanged §P-2 path; falsifiers no-barcode/over-cap(32KB)/dup(propose-merge, upc
  uniqueness NOT enforced — named) all refuse. `build/erp/poc_pos_register.log`.
- **E-2 W-POS-EDIT** ✅ — `buildEditGroup`: changed cols only, next ring reflects X, no-change → 0 ops.
- **E-3 W-IMG-FOLDER + W-IMG-SYNC** ✅ — `build/erp/img_store.js`: content-addressed IDB folder + out-of-band copy
  job (transport=explicit-export-import, relay named-deferred); device-B honest-thumb before the job, full-res after.
- **E-4 W-POS-HOLD** ✅ — `buildHoldGroup`/`buildRecallCompleteGroup`: park=DR C_Order (shows in Sales Order + Kanban),
  recall CO-walks THE HELD order (ship/invoice FOR it), exactly ONE C_Order (no duplicate).
- **E-5 W-WH-CONFIRM** ✅ (unblocked — MInOutConfirm.java found at idempiere-dev-setup/.../org/compiere/model/):
  `build/erp/inout_confirm.js` — doctype 148 spawns the PC confirm, InOut waits at IP, on-hand moves at confirm by
  the PICKED qty; short-pick(SO)=no diff doc, scrap=M_Inventory, PO-diff=AP credit memo (oracle-cited line numbers);
  split-when-difference refused honestly (seed 148 has IsSplitWhenDifference=N).

## LANE U — UI (this Sonnet session, /tmp/wt-posux feat/pos-diy-ux → bim-ootb PR #276 squash 97b8832, sw v656)
- **U-1 album cards** ✅ — `.pos-card` (img→thumb→Lucide image placeholder glyph), big price, tap=ring flash.
  `§POS-ALBUM cards=16 imgs=0 thumbs=0 placeholders=16`. Glyph fix: added `image` to icons.js (was blank zones).
- **U-2 floating payment panel** ✅ — `#pos-float-panel` (position:fixed, z-9500, own layer), pill-summoned (pointerup
  toggle .open), draggable + position persisted (localStorage `pos_panel_pos`), swipe-down dismiss; old fixed
  `#pos-side` sheet removed (the album-moves-with-sheet bug gone). `§POS-FLOAT drag=ok persisted=Y layer=own`.
- **U-3 Import pill** ✅ — `#pos-pill-import`: snap (getUserMedia→≤32KB thumb) + scan (§P-8 BarcodeDetector/typed) +
  key price → `buildRegisterGroup` → commitGroup → new card rings immediately. `§POS-FIRSTSELL snap=Y scan=Y
  price=keyed tile=rendered sold=Y elapsed<60s`. (Camera path honest-untested headless; imageKey = content-len stub,
  SHA-256 a one-line upgrade when SubtleCrypto present.)
- **U-4 DEMO payment QR** ✅ — `#pos-pay-qr` on the receipt: "DEMO PAYMENT QR" (#fa0) encoding `paymentQR(DEMO,amount,
  ref)`, "scan to pay · DEMO ONLY"; additive below the receipt-URL QR. `§POS-PAYQR amount=137.75 ref=… demo=Y`
  (== committed GrandTotal to the cent). The ⛔ payable-QR user-fact is sidestepped by the explicit DEMO/SAMPLE label.
- **U-5 screenshots** ✅ — shown to the user (album/float/import/receipt-QR/desktop, 390×844 + 1280×800); glyph
  re-shot after the fix; user OK'd → train ran.

## DEPLOY TRAIN (serial, clean — #138/#265 orphan trap did NOT fire)
- W-POS-LIVE re-run on the bumped worktree exit 0 (witness updated for the new surface, banked bim-compiler a68bbbee):
  `§POS-SALE lines=2 …newVerbs=[] chainOk=Y ops=12` · `§POS-DOC order=910001 completeIt ok` · `§POS-PAYQR demo=Y` ·
  `§POS-CENT live db=ad_seed.db order=910001 Dr=137.75 Cr=137.75 maxDiff=0c` · `§POS-ALBUM placeholders=16`.
  Regressions green: W-POS-CRUD · W-POS-VOID · W-POS-REPLENISH-LOOP. Engine headless: all E-1..E-5 green.
- sw v655→v656; precache + load `img_store.js` (before pos_lens — window.ImgStore); ?v= bumps pos_core 2 / pos_lens 2
  / icons 4. PR #276 auto-squash-merged (CI fast-checks + e2e green), squash 97b8832.
- Orphan check on origin/main: sw v656 · img_store.js blob+precache+script-tag · icons image glyph · float panel — all
  present. Pages live: `CACHE_VERSION="v656"` (CI-minified double-quote — quote-agnostic grep) + live img_store.js /
  pos_lens.js (float panel, DEMO QR, import pill) / icons.js (image glyph) served; idempiere.html loads img_store
  between pos_core and pos_lens.

## NOTE — the two Fable sessions shared quota: this card's workflow Lane-E (model:fable) hit the session limit because
the user's parallel POS_ENGINE_LANE Fable session was running concurrently and did + banked the engine (9857aefc).
The UI lane + E-5 (Opus) + deploy completed from this session. No work lost.
