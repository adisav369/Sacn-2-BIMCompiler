# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: POS FULL LOOP — CRUD on POS docs + replenishment ENACTED to closure
# Scope: the POS chain today ENDS at "replenish SUGGESTS" — close the loop: (1) full CRUD/doc-action on the
#   POS-created docs (edit · void · reverse, through the EXISTING rails), (2) the replenishment PO actually
#   CREATED → COMPLETED → RECEIVED → on-hand RISES to the unit → the suggestion CLEARS. Lens stays a dumb
#   terminal: CRUD/doc-actions happen in the iDempiere window via the deep link, NOT POS-side smarts.
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`
#   from /home/red1/bim-compiler. EXTRACT don't invent · newVerbs=[] (the gate) · consume the seam, never fork ·
#   BigDecimal/integer cents · deterministic op paths · Lucide-only · spec-first, witness names its issue.
# HOUSE RULES: ~/bim-ootb edits ONLY in a /tmp/wt-* worktree off FRESH origin/main · ONE PR in flight ·
#   sw.js conflict = keep both hunks, higher CACHE_VERSION · orphan-check every squash (`git show origin/main:`) ·
#   matrix + FRONTEND_LANE_MASTER single-writer (bank phase only).
# READ FIRST: prompts/POS_LENS_SESSION.md # DONE (what exists) · docs/POS_ADDON_SPEC.md §replenish ·
#   build/erp/pos_core.js (ringLine/buildSaleGroup/replenishSuggest/buildReplenishPO — ALL BUILT) ·
#   scripts/poc_pos_replenish.js (the suggest fold == iDempiere formula) · the reversal engine is PROVEN:
#   W-FOLD-REVERSE (reverseCorrect/void, fact_acct NETS-TO-ZERO, FSM CO→RE — scripts side) · the CRUD rails are
#   PROVEN: PR #268 CORE.listOptions + CORE.splitStatusChange (explicit status = DOC_ACTION lane, never a column write).

---

## FACTS (2026-06-12 — verified, do not re-derive)
- POS lens FULLY LIVE: #269 sw v652 (lens) · #271 v653 (posting config — `§POS-CENT maxDiff=0c` on default seed) ·
  #272 v654 (deep links/share/home nav). Live chain: ring → ONE signed group (C_Order+M_InOut+C_Invoice CO) →
  backflush → `§POS-LIVE-REPLENISH suggestions=8` — and STOPS there. `buildReplenishPO` exists (headless-witnessed,
  newVerbs=[]) but no live enact→receive→on-hand-rises path is witnessed.
- Replenish seed honesty (KEEP naming it): policy/ledger rows live at wh 103; station ships wh 104 — crossing legs
  ring wh 103. Vendor/price for the PO must come from real seed rows (m_product_po / c_bpartner vendor flags) —
  if the seed lacks a vendor linkage for a product, that suggestion's PO is an HONEST refusal, not an invented vendor.
- Doc FSM legal sets are live (W-AD-DOCFSM-LIVE): C_Order CO→[CL,VO,RE] · M_InOut no-VO-on-completed · etc. Use
  `ad_docfsm.dispatchFor` — the POS docs are ordinary docs.

## THE WORK
### §L-1 CRUD on POS docs (ride #268's rails — wiring + witness only)
Deep-link a POS-created order/invoice into the idempiere window (the #272 `?window=&record=` pattern). Witness
W-POS-CRUD: edit an UNRELATED column on the CO order → persist line has NO docstatus (`cols=description …
verifyChain=ok`) · the docstatus select renders CO selected · explicit status change routes through DOC_ACTION.
(If all three already pass live untouched — likely, the rails shipped — the witness IS the deliverable; say so.)

### §L-2 Void/reverse the sale — the engine's reversal, live
VO (or RE per legal set) the POS sale's docs via dispatchFor → ONE signed group: status flips per FSM, postings
reverse (derived journal NETS-TO-ZERO — the W-FOLD-REVERSE recipe, now on the acct-linked live seed), backflushed
components RESTORED on the qty spine. Witness W-POS-VOID: `§POS-VOID order=<id> CO→VO group ops=N chainOk=Y
onhand-restored=Y postings-net=0c` · §FALSIFIER: voiding an already-voided doc REFUSED by the FSM (legal=[]).

### §L-3 Replenishment ENACTED to closure (the headline)
From the live replenish panel: tap a suggestion → `buildReplenishPO` creates the PO as a signed group (DR doc,
real vendor+price from seed rows) → Complete PO (dispatchFor) → receive: M_InOut RECEIPT lines from the PO
(movementSign '+', the V+ leg — buildDoc from the PO, the WR-fan-out precedent reversed) → Complete receipt →
`qtyOnHand` fold RISES by exactly the received qty at the right locator → re-run replenishSuggest: the suggestion
CLEARS (available ≥ min). Witness W-POS-REPLENISH-LOOP (headless first on the canonical db, then live ERP_ROOT):
`§POS-LOOP suggest qty=N → po=CO → receipt=CO → onhand +N@<locator> → suggestions: gone` · every qty to the UNIT ·
newVerbs=[] · §FALSIFIER-A: receipt without a PO refused · §FALSIFIER-B: short-receive leaves the remainder open
and the suggestion SHRINKS but does not clear.

### §L-4 Deploy train (standard serial) + bank
Worktree → sw bump (+?v=) → re-run live witnesses on the bumped tree → PR → auto-squash → orphan check → Pages
live-verify. Bank: matrix POS row gains the loop evidence · POS_ADDON_SPEC/POS_LENS_SESSION # DONE lines ·
FRONTEND_LANE_MASTER handoff · PROGRESS · this card's # DONE appendix (every claim = a § line).

## SCOPE BOUNDARIES (named)
Returns-with-restock UI, discounts, multi-station §P-5, receipt-URL/EOD email — still next increments, NOT here.
The POS terminal gains AT MOST a deep-link affordance to its own docs — zero client-side doc logic (dumb-terminal rule).

## DONE WHEN
W-POS-CRUD + W-POS-VOID + W-POS-REPLENISH-LOOP green live (logs read) · loop closed to the unit/cent · deployed +
live-verified · banked. Un-EXTRACTABLE fact → `⛔ BLOCKED: <one question>`, move on.

# DONE — 2026-06-12, Lane B (MULTI_LANE_WAVE3) + deploy train. Every claim = a § line in the named log
# (each run via `bash build/erp/run_witness.sh scripts/poc_X.js`, exit 0, logs READ).

- **§L-1 CRUD (W-POS-CRUD 🟢, `build/erp/poc_pos_crud.log`)** — `§POS-CRUD fixture order=100 docstatus=CO bp=112 grandtotal=50.35` · `§POS-CRUD edit=description cols=description statusOp=none verifyChain=ok` · `§POS-CRUD listOptions cur=CO selected=CO DR.selected=false (was DR before fix)` · `§POS-CRUD docstatus-edit=VO statusOp.op_type=DOC_ACTION to=VO fieldOp.cols=description` · `§FALSIFIER pos=crud op=no-docstatus statusOp=null`. Rides the #268 CRUD rails (CORE.listOptions + splitStatusChange).
- **§L-2 void/reverse (W-POS-VOID 🟢, `build/erp/poc_pos_void.log`)** — `§POS-VOID order=100 CO→VO group ops=2 chainOk=Y` · forward AR posting `Dr=50.35 Cr=50.35 accounts=3` · `§POS-VOID postings-net=0c accounts=3 maxNet=0c` (annihilation contract, W-FOLD-REVERSE recipe on the live seed) · `§POS-VOID qty-restore product=130 before=18 afterSale=17 afterVoid=18 restored=Y` · `§POS-VOID backflush=N/A products-are-leaves=Y` · `§FALSIFIER pos=void docstatus=VO action=VO refused=true reason=illegal-action` (double-void refused).
- **§L-3 replenish loop ENACTED (W-POS-REPLENISH-LOOP 🟢, `build/erp/poc_pos_replenish_loop.log`)** — full cycle: `§POS-LOOP suggest qty=11 product=124 (Elm Tree)` → `§POS-LOOP vendor=114 (Tree Farm Inc.) pricepo=30 newVerbs=[]` (real m_product_po, no invented rows) → `§POS-LOOP po=CO dispatch=DR→CO ok=true` → `§POS-LOOP receipt=CO dispatch=DR→CO ok=true` (movementtype V+, 9 lines, verb=buildDoc) → `§POS-LOOP onhand +11@locator=101 product=124 before=9 after=20` → `§POS-LOOP suggestions: gone product=124 available=20 min=10 cleared=Y`. Falsifiers: `§FALSIFIER pos=receipt-no-po lines=0` · `§FALSIFIER pos=short-receive qty=5 available=14 min=10 suggestion-clear=true po-remainder=6` (remainder open, not tracked by the suggestion engine — engine only re-fires below min, §-named).
- **§L-4 deployed + live-verified (train)** — bim-ootb **PR #274** squash-merged (CI fast-checks + e2e SUCCESS), **sw v655**; orphan check `git show origin/main:erp/sw.js → CACHE_VERSION = 'v655'`; Pages verify: live `erp/sw.js` carries v655 AND live `pos_lens.js` contains `vendorOf()` querying `m_product_po WHERE iscurrentvendor='Y'` + `POS.buildReplenishPO()` (§L-3 wiring confirmed live). Post-merge W-POS-LIVE re-run in worktree: exit 0, `§POS-CENT Dr=137.75 Cr=137.75 maxDiff=0c newVerbs=[]`, all 5 stages green.
- **Banked** — witnesses committed bim-compiler 23ae7807 (`scripts/poc_pos_{crud,void,replenish_loop}.js`); matrix addon-lenses rows §L-1..§L-3 added.
- **Residuals** — short-receive PO remainder=6 untracked by suggestions (§-named) · returns-with-restock UI / §P-5 multi-station / receipt-URL / EOD email out of scope per §SCOPE BOUNDARIES.
