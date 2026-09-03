# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: FOLD the M-class logic (close the CODEBASE gap)
# Lane: BUILD the irreducible "must be folded" logic — the M* document actions — onto the op-log kernel, starting
#       from the MOrder.completeIt() worked example, each proven to ORACLE-EQUIVALENCE. This is the BUILD arm; its
#       VERIFY arm is prompts/HARDEN_MATRIX.md (the oracle harness) and its MAP is docs/ERP_MODEL_ARCHETYPE.md.
#       UI stays PARKED (this is engine logic, not screen).
# THE GAP (be precise): the INTERPRETER-coverage ladder is CLOSED (matrix 0✅/37🟡/3⛔ — every declarative surface
#       interpreted). The CODEBASE-LOGIC gap is OPEN: only ~0.2% of the M-class behaviour is folded (`M*.java` =
#       104,940 code-LOC / 6.2 MB src; ~205 LOC folded so far). 37🟡 = surfaces TOUCHED, not behaviour PROVEN.
# NON-NEGOTIABLE: EXTRACT, DON'T INVENT — the behaviour comes from the iDempiere checkout (`~/idempiere-dev-setup/
#       idempiere` M*/Doc_*), the accounts/config from ad_full.db, the ORACLE from real GardenWorld `fact_acct`
#       (via `scripts/extract_fact_acct.sh`). Never hand-author a posting, invariant, or expected line. Spec-first;
#       whitebox §-log FIRST (READ the log); deterministic (INTEGER CENTS, recorded ts, no Date.now/Math.random);
#       the §0 SEPARATION seams hold — M-class logic is CODE that EMITS ops, the books are a FOLD of those ops
#       (declaration→data, interpreter→engine, log→fold never merge). MECHANISM then CORPUS: fold MOrder WHOLE,
#       then deltas; name the unfolded tail. A fold is NOT done until it diffs maxDiff=0c against iDempiere.
# READ FIRST:
#   0. docs/ERP_COVERAGE_MATRIX.md — THE scoreboard this lane closes against. Every fold ends by re-verdicting a
#      row here: the second (equivalence/Oracle) axis ⬜→✅ + the logic-folded % moving off ~0.2%. If a result
#      doesn't move a matrix row, it isn't done.
#   1. docs/ERP_MODEL_ARCHETYPE.md — the denominator (MOrder archetype + ~25 completeIt deltas + master-data tail).
#   2. docs/MigrateComparisonPaper.md → "Worked example — MOrder.completeIt() folded onto the op-log" — the LITERAL
#      starting template + the shipped-vs-TODO split + the GH links to the shipped primitives.
#   3. prompts/HARDEN_MATRIX.md — the VERIFY method: the oracle already exists (GardenWorld fact_acct in
#      glassbowl_data.db; test_report_fin.js proves TB-equiv maxDiff=0c). H-1 step 1 = re-capture WITH record_id/
#      ad_table_id for per-document diffs.
#   4. docs/ERP_BACKEND_SEPARATION.md — the seams a fold must not violate.

---

# Fold the M-class logic — to equivalence, archetype-first

## ORDER

### F-1 ⭐ MOrder.completeIt() to ORACLE-EQUIVALENCE — the keystone ("got MOrder, got the core") — ✅ DONE (W-FOLD-COMPLETE, commit 10653051)
Build the parts the worked example names as TODO, on the SHIPPED primitives (kernel_ops.commitGroup /
ad_modelval.fireHooks / ad_docfsm.transition+legalActions / post_resolver.resolve / crud_overlay.docActionOutcome+
buildDocActionGroup — do NOT fork them):
1. `buildDoc(db, table, parent, lines, reserve?)` — the archetype create-verb: stage CREATE_DOCUMENT + CREATE_LINE
   ops for a child doc (this is the recursion `createShipment`/`createInvoice` use — same verb, table-parameterised).
2. `autoGenerateInOut` / `autoGenerateInvoice` — config branches read from `C_DocType` (DATA, not code) — when to recurse.
3. `createCounterDoc` — intercompany create on the counter org (or honest no-op if no counter relationship in seed).
4. Wire `completeIt(db, order)` exactly per the paper's worked example; emit ONE signed op-group.
5. **Oracle-diff** (the deliverable): complete a REAL GardenWorld order; diff the resulting `Fact_Acct` lines +
   final DocStatus against iDempiere's actual rows. (First extend `extract_fact_acct.sh` to pull
   `record_id, ad_table_id, line_id` and re-capture — HARDEN_MATRIX H-1 step 1.)
- Witness: `§FOLD-COMPLETE doc=C_Order id=<n> status=CO postings=<k> ΣDR=ΣCR oracle=iDempiere maxDiff=0c`;
  `§FALSIFIER` drop one derived line → maxDiff≠0c (proves it diffs the real lines, not just balance).
- Close: matrix — the MOrder archetype table in ERP_MODEL_ARCHETYPE.md goes GREEN; the matrix Oracle column for
  the document/posting rows flips ✅; bump the **logic-folded %** (was ~0.2%).

### F-2 Walk the 25-delta table — deepest-delta-first — 🟡 IN PROGRESS (MPayment ✅ backflush ✅ MAllocationHdr+tax-correction ✅ MAllocationHdr-FX ✅ MMovement ✅ MMatchInv ✅ qtyOnHand ✅ Replenish ✅ MInvoice/AP ✅ reverseCorrect/void ✅ MProduction/MInventory ✅ GL_Journal ✅ [W-FOLD-GLJOURNAL inter-org]; ONLY declarative-surfaces-need-live-UI + cost-valued-prod/inv-GL-needs-seed-data remain)
For each document-family class, fold ONLY its delta from MOrder.completeIt and oracle-diff it. Order by delta size:
`MInOut` (reserveStock + in-transit locator) · `MPayment` (allocation engine) · `MProduction` (BOM explosion) ·
`MInventory` (physical count) · `MAllocationHdr` (headerless) FIRST — the rest are the trade pattern with a
different line table + `Doc_*` poster. `§FOLD-COMPLETE doc=<MClass> deltaFrom=MOrder … maxDiff=0c`.

### F-3 Master-data tail (~471 M*) — light
Their invariants ride the `ad_modelval` hook (mostly done). Fold the genuinely-distinct ones (e.g. `MProduct`
price/UOM rules); name the rest deferred. Most is AD_Column + a generic hook — no per-class code.

## METHOD (every fold)
1. Spec the fold (cite the M*/Doc_* method + the AD/oracle source) before code.
2. `// Implementing ERP_MODEL_ARCHETYPE.md §<class> — Witness: W-FOLD-<CLASS>` pre-flight.
3. Build on the shipped primitives; node POC against the REAL kernel + the GardenWorld oracle. READ the log.
4. Close the loop: re-verdict — archetype table GREEN, matrix Oracle column ✅, **logic-folded % up**. A fold isn't
   done until `§FOLD-COMPLETE … maxDiff=0c`.

## STOP CONDITION
Each folded class has a `§FOLD-COMPLETE … oracle maxDiff=0c` witness; the logic-folded % has risen; unfolded classes
stay named in the delta table. Separation seams intact; M-class logic stays CODE-that-emits-ops (not data); UI parked.
If a fold needs a user decision that can't be EXTRACTED → `⛔ BLOCKED: <the one question>` and move on.

## RECOMMENDED FIRST MOVE
Extend `extract_fact_acct.sh` (+ record_id/ad_table_id) → re-capture the GardenWorld oracle per-document → build
`buildDoc` + wire `completeIt()` → land the first `§FOLD-COMPLETE doc=C_Order … maxDiff=0c`. That single result
proves the archetype and turns "0.2% folded" into a moving number with a real oracle behind it.

---

# ⇆ HANDOFF FROM THE HARDEN/VERIFY SESSION (pushed 2026-06-09, commit a763203a) — READ BEFORE F-1

The VERIFY arm's H-1 keystone is **DONE and on origin** — your F-1 fold can diff against it instead of
building its own oracle rig. **Pull `feat/erp-substrate-phase012` before you touch the shared files below.**

**What landed (reusable, do NOT rebuild):**
- `scripts/extract_fact_acct.sh` — now captures, from `idempiere_test` client 11 → `build/erp/glassbowl_data.db`:
  (a) `fact_acct` WITH per-document granularity `ad_table_id/record_id/line_id` (H-1 step 1, done),
  (b) the acct-config `post_resolver` needs (`c_bp_customer_acct`, `m_product_category_acct`, `c_tax_acct`,
      `c_validcombination`, `c_acctschema_default`, `c_invoicetax`) — **INT-typed** to match the integer-stored
      source tables (a text/int storage mismatch will silently return ABSENT — keep the join-path one class),
  (c) the 6 `*_Access` grant tables (5093 grants) for the H-3 access oracle.
  Regenerate with `bash scripts/extract_fact_acct.sh` (needs the Docker `postgres`/`idempiere_test` up).
- `scripts/poc_post_harden.js` (W-POST-HARDEN) — the oracle-diff harness: derives a doc via `post_resolver`,
  aggregates by **(natural-account, side) in INTEGER CENTS**, diffs vs real `fact_acct` (schema 101). Reuse this
  shape for every fold you prove. It also carries a **post-posting-drift classifier** (`updated>dateinvoiced`):
  doc 109 diverges only because its line was edited after posting (`fact_acct`=posting-time, source=edited) —
  that's data-drift, NOT a derivation gap, and the harness names it instead of failing.
- Result banked in `docs/ERP_COVERAGE_MATRIX.md` §"Second axis — EQUIVALENCE": **2nd oracle-✅** (per-document GL
  derivation). 4/4 sales invoices resolve iDempiere's EXACT accounts (518/758/596); 3/4 `maxDiff=0c`; 0 gaps.

**Coordination:** I am **standing down on the fold / H-2 lane** so it's all yours. Shared CONFLICT files —
fetch origin first, never drop the other side's hunk: `scripts/extract_fact_acct.sh`, `docs/ERP_COVERAGE_MATRIX.md`
(equivalence axis is additive — keep both rows), and `build/erp/glassbowl_data.db` (**untracked** — regenerate
from the script, it won't come down in a pull). Your F-1 = build MOrder.completeIt onto the op-log; verify each
emitted posting with a `poc_post_harden`-style per-account cents diff → bank each as Oracle-✅.

---

# ⇆ SESSION HANDOFF (2026-06-09, commits 10653051 · 97c88d38 · a8ae83bc on feat/erp-substrate-phase012)

**F-1 keystone DONE + the trade→cash→backflush spine landed, all oracle-witnessed against real GardenWorld
(`build/erp/glassbowl_data.db`, client 11, schema 101). Pull the branch before continuing.**

## What landed (witnessed, pushed — do NOT rebuild)
- **F-1 `completeIt(C_Order)` → ORACLE-EQUIVALENT** — `scripts/poc_fold_complete.js` (W-FOLD-COMPLETE, exit 0,
  `build/erp/poc_fold_complete.log`). ONE signed op-group (SET_STATUS CO + config-gated fan-out). The full
  **Order→Ship→Invoice posting chain** diffs `maxDiff=0c`: fan-out lines == oracle `m_inoutline`/`c_invoiceline`;
  **invoice** posting (Doc_Invoice) == `fact_acct(318)` 3/4 equiv + 1 named post-posting drift; **shipment** posting
  (Doc_InOut COGS/Inventory) == `fact_acct(319)` — amount from `m_costdetail` (cost-at-movement; current `m_cost`
  has drifted), accounts from the master; `DocStatus=CO`; §FALSIFIER load-bearing.
- **F-2 MPayment ✅** — `scripts/poc_money_post.js` (W-FOLD-PAYMENT). Doc_Payment receipt = DR {Bank.InTransit} /
  CR {Bank.UnallocatedCash} = payamt; 2/2 == `fact_acct(335)` `maxDiff=0c`; §FALSIFIER fires.
- **Δ-A backflush explosion ✅** — `scripts/poc_backflush.js` (W-FOLD-BACKFLUSH). `erp_engine.explodeBOM` recursive
  verb proven on the real nested recipe (Patio Chair ×30 → Screw×480, Glue×34500, legs×60…) == an independent
  path-enumeration oracle; multi-path accumulation invariant (screw 16/chair across both leg assemblies); flat
  (non-recursive) §FALSIFIER fires. (No fact_acct oracle — `m_production`=0 in seed; the oracle is recipe explosion.)

**New ENGINE verbs (in `scripts/erp_engine.js`, fold-not-fork — reuse, don't fork):** `buildDoc(spec,parent,lines)`
(the table-parameterised archetype create-verb; `createShipment`/`createInvoice` are now spec rows through it) ·
`explodeBOM(bomOf,productId,qty)` (recursive BOM, host-injected resolver, cycle-guarded). **New post_resolver
tokens:** `{Product.Cogs}` `{Product.Asset}` `{Bank.InTransit}` `{Bank.UnallocatedCash}`.

**Oracle capture (`scripts/extract_fact_acct.sh`, ADDITIVE — H-1 contract preserved, fact_acct Dr=Cr=46574.97):**
now also pulls `m_storageonhand`, `m_product_category_acct.p_cogs_acct/p_asset_acct`, `m_cost`, `m_costdetail`,
`c_bankaccount_acct`, `m_product_bom` (recipe) + `m_product` (now incl. `m_product_category_id` — DB is
self-contained). Regenerate with `bash scripts/extract_fact_acct.sh` (needs Docker `postgres`/`idempiere_test` up;
it WAS up this session). `glassbowl_data.db` is untracked — regenerate, it won't come down in a pull.

## NEXT — the deep tail (each a focused session; all "last-cent" deltas, none a quick add)
1. ✅ **DONE — MAllocationHdr posting (the deep half of Money) — `fact_acct(735)`, W-FOLD-ALLOC** (`scripts/poc_alloc_post.js`,
   `build/erp/poc_alloc_post.log`, exit 0). Folds Doc_AllocationHdr SO-invoice branch onto post_resolver: per line
   DR {Payment.UnallocatedCash}|{CashBook.CashTransfer} + DR {BPGroup.PayDiscount} + DR {BPGroup.WriteOff} /
   CR {BPartner.Receivable}, THEN the proportional **tax-correction sub-cents** (`round(tax/total × {disc,wo})` per
   invoice header fact line — alloc 101 = **0.11/0.02**, the exact Doc_AllocationTax.calcAmount HALF_UP algorithm).
   **2/2 == fact_acct(735) maxDiff=0c** (schema 101); §FALSIFIER-A drop-correction=13c, §FALSIFIER-B truncate≠HALF_UP.
   New post_resolver tokens `{BPGroup.PayDiscount}` `{BPGroup.WriteOff}` `{CashBook.CashTransfer}` (+ `bpartner->group`
   via-hop). Extract extended (ADDITIVE, H-1 balance preserved 46574.97): `c_allocationhdr/line`, `c_acctschema`
   (taxcorrectiontype), `c_bp_group_acct`, `c_cashbook_acct`, `c_bpartner`, `c_cashline`. **Residual:** schema-200000
   foreign-ccy Realized-G/L (Doc_AllocationHdr.createInvoiceGainLoss) named-deferred — a distinct fold.
2. ✅ **DONE — StorageOnHand QTY fold (the inventory spine), W-FOLD-QTYONHAND** (`scripts/poc_qtyonhand.js`,
   `build/erp/poc_qtyonhand.log`, exit 0). on-hand = `Σ(sign(MovementType)×|qty|)` folded by NEW pure engine verbs
   `erp_engine.movementSign`/`qtyOnHand` over the REAL movement ledger `m_transaction` (28 events). Diffs TWO
   independent oracles: **(a)** sign rule reproduces every stored signed `movementqty` (28/28); **(b)** per-cell
   accumulation == `m_storageonhand.qtyonhand` (**20/20 cells maxDiff=0**) — separate iDempiere code paths, so
   no movement dropped/double-counted. Source-decomposed (receipt +401 / shipment −15 / internal-movement 0);
   §FALSIFIER-A flip-polarity + §FALSIFIER-B drop-movement fire. Extract extended (ADDITIVE) with `m_transaction`.
   **Residual:** MInventory(I±) + MProduction(P±) movements absent in seed (m_production=0) — named, ride this spine.
3. ✅ **DONE — Δ-B replenishment PO, W-FOLD-REPLENISH** (`scripts/poc_replenish.js`, `build/erp/poc_replenish.log`,
   exit 0). Engine folds on-hand from `m_transaction` (RIDES #2 — never reads m_storageonhand) per product within
   the warehouse's locators (m_locator→wh map load-bearing: loc102 is a different warehouse), applies the exact
   ReplenishReport QtyToOrder formula (type-1 reorder-below-min / type-2 maintain-max, lines 294-327), emits the PO
   via the EXISTING `buildDoc` archetype (**newVerbs=0**). **8/8 products == iDempiere formula-SQL over
   m_storageonhand, maxDiff=0**; PO op-group = 1 CREATE_DOCUMENT(C_Order,PO)+8 CREATE_LINE; §FALSIFIER-A max↔min,
   §FALSIFIER-B type-1-always-order both fire. Extract extended (ADDITIVE): m_replenish, m_storagereservation,
   m_locator. **Residual:** Order_Min/Order_Pack adjustments + QtyReserved/QtyOrdered absent/zero in seed — named.
4. ✅ **DONE — Standalone `completeIt(C_Invoice)` wiring, W-FOLD-INVOICE** (`scripts/poc_invoice_complete.js`,
   `build/erp/poc_invoice_complete.log`, exit 0). NEW handler `erp_engine.completeInvoice`: SET_STATUS C_Invoice CO
   (8/8) + PO-side MatchInv fan-out gated on `!IsSOTrx && line.M_InOutLine_ID<>0`. Richer than "thin": the emitted
   M_MatchInv set == real `m_matchinv` per (invoiceline,inoutline) TUPLE — **18/18 junctions** across PO invoices
   102/104/105/106 (one CREATE_LINE op per junction; buildDoc is NOT used — MatchInv is a single junction record,
   not header+lines; newVerbs=0). Completed SALES invoices fold to fact_acct(318) maxDiff=0 (3/4; 109=named drift).
   §FALSIFIER-A corrupt-junction, §FALSIFIER-B SO-emits-no-MatchInv. Extract: m_matchinv oracle added (ADDITIVE).
   **Residual:** PO-invoice GL value-derivation (V_Liability token set) — ✅ now folded (W-FOLD-AP-INVOICE).
5. ✅ **DONE — Close the matrix.** `docs/ERP_MODEL_ARCHETYPE.md`: MOrder DocAction+Posting surface rows flipped ✅
   (completeIt chain + Doc_Invoice/InOut/Payment/AllocationHdr oracle-diffed); added an **oracle-folded scoreboard**
   (6 of ~40 cent/unit-equivalent + 1 recipe-equivalent — the deepest deltas) and named the unfolded tail.
   `docs/ERP_COVERAGE_MATRIX.md` equivalence axis: **8 oracle-equivalent surfaces** (+backflush). Logic-folded is
   no longer ~0.2% — the trade-doc loop (order→ship→invoice→match→pay→allocate) AND the inventory loop
   (movement→on-hand→replenish-PO) both fold end-to-end to their iDempiere oracle.

## REMAINING TAIL (named, each its own focused session — none blocking)
- ✅ **DONE — PO-invoice GL value-derivation, W-FOLD-AP-INVOICE** (`scripts/poc_invoice_post_ap.js`,
  `build/erp/poc_invoice_post_ap.log`, exit 0). Vendor (IsSOTrx=N) `Doc_Invoice` manifest: CR `{Vendor.V_Liability}`
  =GrandTotal (per-vendor `c_bp_vendor_acct`, mirrors customer receivable: bp114→419 / bp120→749) / DR
  `{Product.InventoryClearing}`=linenet per matched-receipt item line (→780 Inventory Clearing, via product→category,
  mirrors `{Product.Revenue}`) / DR `{Tax.Credit}`=input-VAT (zero in seed). **4/4 invoices (102/104/105/106) ==
  `fact_acct(318)` per (account,side), maxDiff=0c**; resolver picks iDempiere's EXACT accounts. §FALSIFIER-A
  clearing→liability (20000c), §FALSIFIER-B flip-Dr/Cr (20000c). 3 NEW post_resolver tokens; extract extended
  (ADDITIVE, H-1 balance 46574.97 preserved): `c_bp_vendor_acct.v_liability_acct` +
  `m_product_category_acct.p_inventoryclearing_acct` + `c_tax_acct.t_credit_acct`. `Doc_Invoice` now folds on BOTH
  sales and purchase sides. **Residual:** charge-line + service-product expense DR absent in seed (all 4 lines are
  matched item receipts); schema-200000 deferred.
- ✅ **DONE — Allocation schema-200000 (foreign-ccy), W-FOLD-ALLOC-FX** (`scripts/poc_alloc_fx.js`,
  `build/erp/poc_alloc_fx.log`, exit 0). Same line manifest folded into schema 200000 (EUR, ccy 102; source USD 100)
  with two schema-deltas: (a) per-leg currency conversion `round(AmtSource×0.85, HALF_UP)` — rate from
  `c_conversion_rate` (default Spot 114, valid on dateacct), multiplied in **BigInt** off the TEXT-preserved exact
  decimal (no float-drift); (b) TaxCorrectionType='N' → no VAT sub-cents — then a CurrencyBalancing line
  (`c_acctschema_gl.currencybalancing_acct` → 724) absorbs the per-doc accounted imbalance (alloc 101 = 0.01 CR).
  **2/2 == fact_acct(735) schema 200000, maxDiff=0c**; §FALSIFIER-A drop balancing (1c), §FALSIFIER-B wrong rate.
  Extract ADDITIVE (H-1 46574.97 preserved): `c_conversion_rate` (multiplyrate as TEXT), `c_acctschema_gl`,
  `c_conversiontype`. Closes the §DEFERRED in `poc_alloc_post.js`. NO new post_resolver tokens.
- ✅ **DONE — void/reverseCorrect/reverseAccrual DocAction set, W-FOLD-REVERSE** (`scripts/poc_reverse.js`,
  `build/erp/poc_reverse.log`, exit 0). The engine ENACTS the reversal GardenWorld never posted: re-derive each
  completed doc's FORWARD posting from source (proven `post_resolver` paths) → re-confirm == REAL `fact_acct`
  (anchor) → feed to NEW pure verb `erp_engine.reversePosting` (swap Dr↔Cr, NEVER reads the books) → prove engine
  reversal **+ real original NETS TO ZERO per account** (iDempiere reverseCorrect contract). **6/6 docs
  (C_Payment 100/101 + vendor C_Invoice 102/104/105/106) annihilate to the cent** (residual=0c, swapDiff=0c); FSM
  **CO→RE** via pure `ad_docfsm.transition` RC/RA→RE, VO→VO. ORACLE-ANCHORED (transform of real client-11 data,
  non-tautological — the forward derive is re-confirmed against the oracle, the reversal is built from source not
  copied). §FALSIFIER-A same-sign no-swap → doc DOUBLES (19700c), §FALSIFIER-B wrong reversal account (9850c).
  Enacted in a SANDBOX copy (`_sandbox_reverse.db`, deleted after); the read-only anchor stays CO. **newVerbs=1**
  (`reversePosting`, the reversal RULE; FSM + forward derives reused, fold-not-fork). **Residual (named):**
  `reverseAccrual` booked-DATE (next-open-period needs `c_period`, absent in this fact_acct extract — the posting
  negation IS proven, only the date is unverifiable in seed) · `void`(unposted)=status-only · sales-invoice
  reverseCorrect (same fold, AP shown) · MFactReversal schema-200000 (same negation, EUR).
- ✅ **DONE — Inter-org M_Movement posting, W-FOLD-MOVEMENT** (`scripts/poc_movement.js`,
  `build/erp/poc_movement.log`, exit 0). Doc_Movement cross-org transfer (loc101/org11→loc102/org12):
  `amt=round(qty×cost,2)`, DR/CR `{Product.Asset}` + DR `{Schema.IntercompanyDueFrom}` / CR `{Schema.IntercompanyDueTo}`.
  **Cost selection** (the NEW reusable rule): `c_acctschema.costingmethod`('A') → `m_costelement` w/ matching
  costingmethod (103 Average PO) → `m_cost.currentcostprice` (prod123=51.45, 4×51.45=205.80). **1/1 == fact_acct(323)
  schema 101, maxDiff=0c**; §FALSIFIER-A swap intercompany (20580c), §FALSIFIER-B wrong cost element (10980c).
  Extract ADDITIVE: m_movement, m_movementline, m_warehouse, m_costelement + c_acctschema.{costingmethod,m_costtype_id}
  + c_acctschema_gl.{intercompanydueto,duefrom}. Intercompany accts resolved DIRECTLY (schema-level, like balancing).
- ✅ **DONE (RULE-CONSISTENT tier) — MInventory physical-count + MProduction movement.** `scripts/poc_production.js`
  (W-FOLD-PRODUCTION, `build/erp/poc_production.log`, exit 0) + `scripts/poc_inventory.js` (W-FOLD-INVENTORY,
  `build/erp/poc_inventory.log`, exit 0). No iDempiere oracle (`m_production`=0, no I± in seed), so the engine
  ENACTS each doc and verifies against the ALREADY-PROVEN rules — labelled rule-consistent, NOT "== iDempiere":
  **Production** = `explodeBOM` (W-FOLD-BACKFLUSH) → synthesize P+ (finished +Q) / P- (each leaf −used) ledger →
  it folds through the proven qty spine (`movementSign`/`qtyOnHand`) to finished +Q / leaf −used (Patio Chair ×30
  → +30 / 6 leaves incl. 480 screws); §FALSIFIER-A flip P-→P+, §FALSIFIER-B flat-explosion (6→4 leaves). **GL
  named-deferred** — leaf-component `m_cost` absent in seed (cost RULE proven W-FOLD-MOVEMENT, the component-cost
  DATA is absent → can't value the CR side without inventing). **Inventory** = book on-hand folded from real
  `m_transaction` (== `m_storageonhand`) → I+/I- by sign(counted−book) → fold lands on-hand == counted (6/6,
  gain+loss) + GL value `|adjQty|×cost` via the proven cost rule (Oak Tree +3×51.45=154.35) BALANCES;
  §FALSIFIER-A wrong polarity, §FALSIFIER-B Material cost element (24≠51.45). **GL offset account named-deferred**
  (Inventory-Gain/Loss `ACCTTYPE_InvDifferences` has no extractable column; leg value + balance proven).
  **Both CLOSE the I±/P± riders that W-FOLD-QTYONHAND + W-FOLD-BACKFLUSH explicitly named-deferred to the spine.**
  newVerbs=0 (explodeBOM/movementSign/qtyOnHand/post_resolver/cost-rule all reused, fold-not-fork).
- **Fixed-Assets + GL/Project families** + the declarative surfaces (surface-interpreted, not yet oracle-diffed).

## ▶ THE ENACTMENT METHOD (the un-blocking — our engine WRITES the sandbox, then we verify the right way)
The data-blocked items were blocked only because GardenWorld never *posted* such docs. Our engine doesn't need
iDempiere to: it ENACTS the DocAction into a sandbox SQLite (a COPY of `glassbowl_data.db`; the real client-11
rows stay the read-only anchor). Two honest verification tiers — pick by whether a real-anchored oracle exists:
- **ORACLE-ANCHORED** (reversals/void): the result is a DETERMINISTIC TRANSFORM of a REAL posting (exact negation
  / period-shift). Diff vs the transform of real `fact_acct`. This IS equivalence — the anchor is iDempiere data,
  the rule is iDempiere's contract; nothing invented. Promote to a normal W-FOLD.
- **RULE-CONSISTENT** (production/inventory physical-count): NO real posting to anchor to. Verify the enacted GL
  obeys the already-PROVEN rules (cost-selection, movement-sign) + BALANCES + a falsifier — the backflush tier.
  State "rule-consistent (no fact_acct oracle in seed)" verbatim; never dress it as oracle-equivalent.
Sandbox discipline: write to a copy, never mutate the anchor; deterministic (no Date.now — pass the reversal/accrual
date in); reuse the PURE verbs (`buildDoc`/`explodeBOM`/`qtyOnHand`/`post_resolver`), `newVerbs=[]`, fold-not-fork.

## NEWLY DISCOVERED foldable families (real `fact_acct` oracle data, NOT in the old tail — deepest-first)
- ✅ **DONE (18/18, FULL) — M_MatchInv posting incl. avg-cost IPV split, W-FOLD-MATCHINV** (`scripts/poc_matchinv.js`,
  `build/erp/poc_matchinv.log`, exit 0). DR `{BPGroup.NotInvoicedReceipts}`=round(matchQty×PO-price) / CR
  `{Product.InventoryClearing}`=round(matchQty×invoice-price), full-price-precision then rounded (caught a real bug:
  price 2.975×30 = 89.25, not round-price-first 89.40). The 1 variance match (doc 100, PO 30/inv 20 → IPV 100) folds
  in full: **onHandAtMatch = Σ m_transaction.movementqty up to the match date** (product 130 = 7 of qty 10, rides the
  qty spine) → `round(IPV×min(onHand,qty)/qty)` = 70 `{Product.Asset}` / 30 `{Product.AverageCostVariance}`. **18/18
  == fact_acct(472) schema 101, maxDiff=0c.** New tokens `{BPGroup.NotInvoicedReceipts}`, `{Product.AverageCostVariance}`;
  extract ADDITIVE (po_price/inv_price into m_matchinv + movementdate into m_transaction + p_averagecostvariance_acct).
  §FALSIFIER-A swap NIR↔Clearing (36000c), §FALSIFIER-B all-IPV→Asset (3000c). **The entire PO/inventory trade loop
  now folds to the cent.** Schema 200000 = same fold (named-residual).
- ✅ **DONE — GL_Journal posting, W-FOLD-GLJOURNAL** (`scripts/poc_gljournal.js`, `build/erp/poc_gljournal.log`,
  exit 0). NOT near-tautological as feared: GardenWorld's journal is INTER-ORG (DR Checking@org11 / CR Checking@org12,
  same natural account 508 via different C_ValidCombinations), so the fold = direct lines (amtacct=round(amtsource×
  currencyrate,2), derived+checked vs stored, not copied) + `Fact.balanceAccounting` per-org Intercompany
  Due-To(600)/Due-From(741) balancing (the SAME `c_acctschema_gl` rule as W-FOLD-MOVEMENT). **2/2 journals ==
  `fact_acct(224)` maxDiff=0c, BOTH acctschemas (101 USD + 200000 EUR).** §FALSIFIER-A swap Due-To/From (10000c),
  §FALSIFIER-B drop per-org balancing → single-org (10000c). Extract ADDITIVE (H-1 46574.97 preserved):
  `gl_journal` + `gl_journalline` (account_id + ad_org_id from the line's C_ValidCombination, currencyrate TEXT).
  **Residual:** foreign-RATE journal (rate≠1) — degenerate (rate=1 in seed, journal entered in schema currency).

**Method unchanged:** spec-first, §-log first (READ the log), integer cents, deterministic, EXTRACT-don't-invent,
`newVerbs=[]`, fold-not-fork. A fold isn't done until `§FOLD-COMPLETE … maxDiff=0c` (or, for backflush, the
two-impl agreement + falsifier). `scripts/erp_engine.js` is PURE (host injects `query`/`bomOf`) — keep it that way.

---

# ⇆ SESSION HANDOFF (2026-06-09, commits 8d682782 · 5a8063da · 476fd2d0 · 273bcee0 · 5e5bfd6c on feat/erp-substrate-phase012, all PUSHED)

**The ENTIRE prior deep-tail list (5 items) is DONE — zeroed, oracle-witnessed, on origin. Pull the branch.**
Two full loops now fold end-to-end to real GardenWorld: trade-doc (order→ship→invoice→**match**→pay→**allocate**)
and inventory (movement→on-hand→replenish-PO). Equivalence axis = **8 oracle-equivalent surfaces** + 1 recipe.

## What landed this session (witnessed, pushed — do NOT rebuild)
- **W-FOLD-ALLOC** (`scripts/poc_alloc_post.js`) — Doc_AllocationHdr SO-invoice branch **incl. VAT tax-correction
  sub-cents** (`round(tax/total×{disc,wo})`, alloc 101 = 0.11/0.02); 2/2 == `fact_acct(735)` maxDiff=0c. New
  post_resolver tokens `{BPGroup.PayDiscount}` `{BPGroup.WriteOff}` `{CashBook.CashTransfer}` + `bpartner->group` hop.
- **W-FOLD-QTYONHAND** (`scripts/poc_qtyonhand.js`) — NEW pure verbs `erp_engine.movementSign`/`qtyOnHand`;
  on-hand = Σ(sign(MovementType)×|qty|) from `m_transaction` == `m_storageonhand` (20/20) + sign rule (28/28).
- **W-FOLD-REPLENISH** (`scripts/poc_replenish.js`) — ReplenishReport QtyToOrder from movement-folded on-hand
  (RIDES qtyonhand, never reads m_storageonhand) == iDempiere formula (8/8); PO emitted via `buildDoc` (newVerbs=0).
- **W-FOLD-INVOICE** (`scripts/poc_invoice_complete.js`) — NEW handler `erp_engine.completeInvoice`: SET_STATUS CO
  + PO-side MatchInv fan-out; emitted M_MatchInv set == real `m_matchinv` per (invoiceline,inoutline) tuple
  (18/18); sales GL == `fact_acct(318)` maxDiff=0c. (MatchInv = CREATE_LINE op, NOT buildDoc — single junction.)
- **Matrix CLOSED** — `docs/ERP_MODEL_ARCHETYPE.md` MOrder DocAction+Posting rows ✅ + oracle-folded scoreboard;
  `docs/ERP_COVERAGE_MATRIX.md` equivalence axis 8 rows.

**Extract (`scripts/extract_fact_acct.sh`, all ADDITIVE — H-1 balance 46574.97 preserved):** now also pulls
`c_allocationhdr/line`, `c_acctschema`(taxcorrectiontype), `c_bp_group_acct`, `c_cashbook_acct`, `c_bpartner`,
`c_cashline`, `m_transaction`, `m_replenish`, `m_storagereservation`, `m_locator`, `m_matchinv`. Regenerate with
`bash scripts/extract_fact_acct.sh` (Docker `postgres`/`idempiere_test` must be up). `glassbowl_data.db` is
untracked — regenerate, it won't come down in a pull. **NOTE:** the script does NOT create the base doc tables
(`c_invoice`/`c_order`/`m_inout`/…) — those persist from a prior full seed; never narrow-recapture them or you
drop columns siblings need (learned this session on `c_invoiceline`).

## NEXT — the remaining tail (the §"REMAINING TAIL" list above is now THE backlog; deepest-first)
1. ✅ **DONE — PO-invoice GL value-derivation, W-FOLD-AP-INVOICE** (`scripts/poc_invoice_post_ap.js`). 4/4 vendor
   invoices == `fact_acct(318)` maxDiff=0c. CR `{Vendor.V_Liability}` / DR `{Product.InventoryClearing}` per matched
   line. 3 new post_resolver tokens; extract ADDITIVE (c_bp_vendor_acct + p_inventoryclearing_acct + t_credit_acct).
2. 🔓 **ENACTABLE (un-blocked) — void/close/reverseCorrect/reverseAccrual DocAction set.** Our engine enacts the
   reversal in a sandbox copy; oracle = REAL-ANCHORED (reverseCorrect == exact negation of the real `fact_acct`).
   FSM transition already built (`ad_docfsm.js`). See §"ENACTMENT METHOD" + the REMAINING-TAIL entry. → `poc_reverse.js`.
3. ✅ **DONE — Allocation schema-200000 (foreign-ccy), W-FOLD-ALLOC-FX** (`scripts/poc_alloc_fx.js`). 2/2 ==
   fact_acct(735) schema 200000, maxDiff=0c. Per-leg FX conversion (0.85 HALF_UP, BigInt) + CurrencyBalancing line.
4. ✅ **DONE — Inter-org M_Movement posting, W-FOLD-MOVEMENT** (`scripts/poc_movement.js`). 1/1 == fact_acct(323)
   schema 101, maxDiff=0c. Cost transfer + intercompany bridge; cost-selection rule proven (reusable for M_MatchInv).
5. ✅ **DONE (18/18, FULL) — M_MatchInv posting incl. avg-cost IPV split, W-FOLD-MATCHINV** (`scripts/poc_matchinv.js`).
   == fact_acct(472) maxDiff=0c across all 18; IPV split rides the qty spine (onHandAtMatch). PO loop folds in full.
6. **NEXT (deepest-first, all now ENACTABLE via the engine — see §"ENACTMENT METHOD"):**
   a. ✅ **DONE — Reversals/void** (`poc_reverse.js`, W-FOLD-REVERSE) — ORACLE-ANCHORED: engine reversal + real
      `fact_acct` nets to zero per account, 6/6 docs `maxDiff=0c`; FSM CO→RE. newVerbs=1 (`reversePosting`).
   b. ✅ **DONE — MProduction / MInventory** (`poc_production.js` W-FOLD-PRODUCTION / `poc_inventory.js`
      W-FOLD-INVENTORY) — RULE-CONSISTENT: enacted P±/I± fold through the proven qty spine (production finished
      +Q/leaf −used; inventory on-hand→counted + GL `|adjQty|×cost` balances); closes the qtyonhand/backflush
      I±/P± riders. GL cost-value named-deferred (component cost / offset acct absent in seed). newVerbs=0.
   c. ✅ **DONE — GL_Journal** (`poc_gljournal.js`, W-FOLD-GLJOURNAL) — turned out NOT near-tautological: the
      seed journal is INTER-ORG, so the fold = direct lines (amtacct=amtsource×rate) + per-org Intercompany
      Due-To/From balancing (same rule as W-FOLD-MOVEMENT) == `fact_acct(224)` 2/2 maxDiff=0c, both schemas.
      Extract extended ADDITIVELY (gl_journal/gl_journalline w/ validcombination org). ORACLE-EQUIVALENT.
   d. Schema-200000 variants of M_Movement / M_MatchInv (same fold, EUR cost) = easy completeness adds.
   The DECLARATIVE surfaces still need live-UI for ✅ (the AD_BEHAVIOR_HANDOFF bridge), separate lane.

**Witness set (all green this session, run before continuing):** `for s in poc_alloc_post poc_alloc_fx poc_movement
poc_matchinv poc_qtyonhand poc_replenish poc_invoice_complete poc_invoice_post_ap poc_fold_complete poc_money_post
poc_backflush poc_post_harden; do node scripts/$s.js; done` + `node scripts/test_report_fin.js` (TB == 46574.97).
Method unchanged (see above).

---

# ⇆ SESSION HANDOFF (2026-06-10, commits 542c9aaf · 20c5623b · e0e635c2 · 52d598e5 · 14df1408 on feat/erp-substrate-phase012, ALL PUSHED)

**THE FOLD LANE IS DRAINED of everything provable against the current seed/oracle.** Pull the branch.
Equivalence axis = **14 oracle-equivalent + 1 recipe + 2 rule-consistent**; **16 witnesses green**; TB 46574.97.

## What landed this session (witnessed, pushed — do NOT rebuild)
- **W-FOLD-REVERSE** (`scripts/poc_reverse.js`, 542c9aaf) — reverseCorrect/void, ORACLE-ANCHORED. Re-derive each
  completed doc's forward posting → re-confirm == real fact_acct → NEW pure verb `erp_engine.reversePosting`
  (swap Dr↔Cr) → engine reversal **+ real original NETS TO ZERO** per account (6/6 C_Payment+C_Invoice); FSM CO→RE.
- **W-FOLD-PRODUCTION + W-FOLD-INVENTORY** (`poc_production.js`/`poc_inventory.js`, 20c5623b) — RULE-CONSISTENT tier
  (no seed oracle): enacted P±/I± fold through the proven qty spine (production finished +Q/leaf −used; inventory
  on-hand→counted + GL `|adjQty|×cost` balances). Closes the I±/P± riders qtyonhand/backflush deferred. GL
  cost-value NAMED-DEFERRED (leaf-component m_cost + Inventory-Gain/Loss offset absent in seed — NOT faked).
- **W-FOLD-GLJOURNAL** (`poc_gljournal.js`, e0e635c2) — manual GL journal, ORACLE-EQUIVALENT (NOT tautological):
  GardenWorld's journal is INTER-ORG, so fold = direct lines (amtacct=amtsource×rate) + per-org Intercompany
  Due-To/From balancing (same c_acctschema_gl rule as W-FOLD-MOVEMENT) == fact_acct(224) 2/2 both schemas.
- **Docs** (52d598e5 · 14df1408) — MigrateComparisonPaper equivalence axis 1→14 surfaces; FoldEngineQuality
  scorecard 11→16 witnesses (tiers marked honestly). **mkdocs deployed live → https://red1oon.github.io/BIMCompiler/**
  (gh-deploy to origin gh-pages; verified live).
- **Extract** (`scripts/extract_fact_acct.sh`, ADDITIVE, H-1 46574.97 preserved): now also pulls `gl_journal` +
  `gl_journalline` (account_id+ad_org_id from line's C_ValidCombination). Regenerate: `bash scripts/extract_fact_acct.sh`
  (Docker postgres/idempiere_test must be up — it was this session). `glassbowl_data.db` is UNTRACKED — regenerate, won't pull.
- **New pure engine verb:** `reversePosting(facts,{mode})` (the only newVerb this session; production/inventory/gljournal = newVerbs=0).

## NEXT — all REMAINING items are gated on something OUTSIDE this lane (none a quick fold)
1. **Cost-valued MProduction/MInventory GL** — needs a RICHER seed extract: leaf-component `m_cost` + the
   Inventory-Gain/Loss offset account (`Doc.ACCTTYPE_InvDifferences`) are absent. Even then MProduction has NO
   real fact_acct (m_production=0) → stays rule-consistent, not oracle-equivalent. Extend `extract_fact_acct.sh`
   ONLY if pursuing; do NOT invent the missing cost/account.
2. **Declarative surfaces** (logic/access/valrule/callout/modelval/FSM) — interpreted but never oracle-diffed;
   need LIVE UI for ✅ (the AD_BEHAVIOR_HANDOFF bridge, [[project_ad_erp]]) — a SEPARATE lane, UI parked.
3. **Easy completeness adds** (low value): foreign-RATE GL journal (degenerate rate=1 in seed) · schema-200000
   variants of M_Movement / M_MatchInv / reversal (same fold, EUR cost).

## Witness set (16, all green — run before continuing)
`for s in poc_fold_complete poc_money_post poc_backflush poc_alloc_post poc_qtyonhand poc_replenish
poc_invoice_complete poc_invoice_post_ap poc_alloc_fx poc_movement poc_matchinv poc_post_harden poc_reverse
poc_gljournal poc_production poc_inventory; do node scripts/$s.js; done` + `node scripts/test_report_fin.js` (TB 46574.97).

Method unchanged: spec-first · §-log first (READ the log) · integer cents · deterministic (no Date.now/random) ·
EXTRACT-don't-invent · newVerbs minimal · fold-not-fork · `erp_engine.js` stays PURE (host injects query/bomOf).
A fold isn't done until `§FOLD-COMPLETE … maxDiff=0c` (or the rule-consistent two-impl/balance+falsifier for no-oracle docs).

## ⚠ MATRIX COORDINATION (shared file w/ the UI_UNPARK lane — like sw.js, the conflict magnet)
`docs/ERP_COVERAGE_MATRIX.md` has TWO axes owned by TWO lanes:
- **FOLD lane (this) owns the 2nd "EQUIVALENCE (oracle-diffed)" axis** — the rows I append per fold. Now
  **STABLE at 16 oracle-equivalent + 1 recipe + 2 rule-consistent** (the two schema-200000 completeness adds
  banked this session; lane drained; no more rows coming until a richer seed unblocks the remaining gated items).
  The FOLD lane does NOT touch the 1st axis.
- **UI_UNPARK lane owns the 1st "coverage" axis** — the §1/§2 declarative-surface re-verdict 🟡→✅, which needs
  LIVE UI (parked). That re-verdict is REAL-but-matrix-unbanked; it is the UI lane's to bank, not FOLD's.
On a merge conflict here: KEEP BOTH — take FOLD's equivalence rows AND the UI lane's coverage re-verdict; never
drop one axis to resolve the other. (The FOLD lane being drained means it won't fight the UI refresh.)

---

# ⇆ SESSION HANDOFF (2026-06-10b, schema-200000 completeness adds on feat/erp-substrate-phase012)

**Banked the two provable "low-value completeness adds" the prior handoff (NEXT#3) had left dangling.** Both fold
to the cent against real schema-200000 `fact_acct`; lane now **16 oracle-equivalent + 1 recipe + 2 rule-consistent**,
**18 witnesses green**, TB 46574.97 unchanged.

## What landed (witnessed — do NOT rebuild)
- **W-FOLD-MOVEMENT-FX** (`scripts/poc_movement_fx.js`) — inter-org M_Movement in the EUR schema 200000. NOT an FX
  conversion: `m_cost` holds a SEPARATE per-schema cost (43.7325 EUR), so the fold is `round(qty × that schema's
  cost)`. **1/1 == `fact_acct(323)` schema 200000, maxDiff=0c.** Exposed + fixed a latent **1c round-cost-first bug
  in `poc_movement.js`** (rounded the per-unit cost to cents before ×qty; schema 101's 2dp cost masked it, the 4dp
  EUR cost caught it). Fix = carry cost as milli-cents (×10000), round the LINE amount — output-IDENTICAL at schema
  101 (20580c), so poc_movement.js stays green. This is the SAME full-precision rule W-FOLD-MATCHINV proved.
- **W-FOLD-MATCHINV-FX** (`scripts/poc_matchinv_fx.js`) — M_MatchInv in schema 200000. SAME USD source manifest as
  W-FOLD-MATCHINV, each fact leg converted independently to EUR (`round(amtsource × 0.85, HALF_UP)`, BigInt off the
  TEXT-preserved rate — the W-FOLD-ALLOC-FX rule). At 0.85 every leg converts exactly (the 70/30 IPV split →
  59.50/25.50) so NO currency-balancing residual. **18/18 == `fact_acct(472)` schema 200000, maxDiff=0c.**
- **Docs updated** (FOLD lane's equivalence axis only): `ERP_COVERAGE_MATRIX.md` 14→16, `ERP_MODEL_ARCHETYPE.md`
  scoreboard 12→14 of ~40, `FoldEngineQuality.md` 16→18 witnesses / 14→16 oracle-equivalent, `MigrateComparisonPaper.md`
  the two count refs. newVerbs=0 (both reuse existing pure verbs + post_resolver).

## Witness set (18, all green) — run before continuing
`for s in poc_fold_complete poc_money_post poc_backflush poc_alloc_post poc_qtyonhand poc_replenish
poc_invoice_complete poc_invoice_post_ap poc_alloc_fx poc_movement poc_movement_fx poc_matchinv poc_matchinv_fx
poc_post_harden poc_reverse poc_gljournal poc_production poc_inventory; do node scripts/$s.js; done`
+ `node scripts/test_report_fin.js` (TB 46574.97).

## NEXT — lane is again DRAINED; remaining items still gated outside this lane (unchanged from prior handoff)
1. **Cost-valued MProduction/MInventory GL** — needs a RICHER seed (leaf-component `m_cost` + Inventory-Gain/Loss
   offset acct absent). Even then MProduction has no real `fact_acct` → stays rule-consistent. Do NOT invent.
2. **Declarative surfaces** (logic/access/valrule/callout/modelval/FSM) — need LIVE UI for ✅ ([[project_ad_erp]]
   bridge, separate parked lane).
3. **Last easy add:** foreign-RATE GL journal (degenerate rate=1 in seed → no data to prove) · schema-200000
   reversal variant (W-FOLD-REVERSE already nets-to-zero; an FX leg is the only delta, low value).
**mkdocs:** redeploy if pushing the doc count changes (`gh-deploy` → https://red1oon.github.io/BIMCompiler/).
