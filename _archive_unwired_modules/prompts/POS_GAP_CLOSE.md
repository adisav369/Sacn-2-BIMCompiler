# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: POS GAP-CLOSE LANE, queued 2026-06-12 (post killer-demo)
# Paste-to-start: `proceed with prompts/POS_GAP_CLOSE.md`
# Scope: close the gaps the 2026-06-12 whole-project read found AFTER the killer demo shipped
#   (bim-ootb PR #276 sw v656 LIVE; Lane E banked bim-compiler 9857aefc; cards POS_KILLER_DEMO /
#   POS_ENGINE_LANE → # DONE). G-1/G-3 = bim-compiler headless engine. G-2 = ONE bim-ootb train.
#   G-4 = banking/docs. Work top-to-bottom (WORK-TO-ZERO); G-3 is the only optional/stretch item.
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# NON-NEGOTIABLE: spec-first · witness-led (each test NAMES its issue) · deterministic NON-INVENT
#   (every value from the seed/dictionary or keyed input; no Date.now/Math.random in op paths) ·
#   newVerbs=[] (compose existing verbs + ops via kernel_ops.commitGroup) · BigDecimal for money/qty.
# HOUSE RULES (each has bitten): bim-compiler tree is DIRTY with other lanes — git add EXACT paths only ·
#   bim-ootb edits ONLY in /tmp/wt-* off FRESH origin/main, ONE PR, sw bump once, orphan-check the squash,
#   Pages greps quote-agnostic · seed facts from canonical build/erp/ad_seed_fullwidth.db CROSS-CHECKED vs
#   `git show origin/main:erp/ad_seed.db` (the shared ~/bim-ootb checkout is STALE — never trust it).
# STATE AT WRITING: sw v656 live · equivalence ledger 43 oracle-equivalent ⬜=NONE · coverage 7✅/32🟡/3⛔ ·
#   pos_core split = buildOrderOps + completionOps halves (W-POS-WR replay-equal) · inout_confirm.js landed
#   (W-WH-CONFIRM, rule-consistent) · img_store.js landed (W-IMG-FOLDER/SYNC, Map-adapter headless).
# Design sources: docs/POS_ADDON_SPEC.md §P-12 + docs/SPATIAL_PICKING_SPEC.md §S-2 (the deliver-later
#   engine note BOTH banked 06-12f: "ENGINE builds it") · build/erp/pos_core.js · build/erp/inout_confirm.js ·
#   prompts/POS_ENGINE_LANE.md # DONE (as-built §API + rows-as-data) · prompts/HARDEN_MATRIX.md (PG-drive
#   technique, G-3) · docs/POSLens.md §11 · docs/ERPUserGuide.md · docs/MigrateComparisonPaper.md.

---

## G-1 deliver-later sale variant — THE SEAM GAP (engine, build FIRST; unblocks the §S-2/fulfillment wave)
The banked specs promise it twice (§P-12 + §S-2): the pickable sale = plain-SOO shape — seed doctype
**132 "Standard Order" (DocSubTypeSO='SO')** — whose **M_InOut stays DR**; the walk's §S-4 scan-commit
(or the §P-12 confirm path when the shipment doctype demands it, e.g. 148) is what completes it and
moves on-hand. NOBODY BUILT IT: WR completes its M_InOut in-group (W-POS-WR), hold/recall completes
WR-style too. Build in `build/erp/pos_core.js` as a `completionOps` policy variant — the policy DERIVED
from the doctype 132 dictionary row (the wrPolicy discipline: flags from docsubtypeso, never POS code):
- Order ops (the existing buildOrderOps half) + SET_STATUS C_Order CO + shipment built via the SAME
  buildDoc spec but **NO SET_STATUS M_InOut** (born DR). Invoice timing: EXTRACT from the dictionary
  (InvoiceRule / docsubtypeso semantics) and NAME the choice — do not hardcode ship-then-invoice.
- Completion-by-pick: the walk/scan side completes the DR M_InOut (and when its doctype is 148, the
  W-WH-CONFIRM gate from `inout_confirm.js` applies: confirm first, on-hand at confirm).
Witness **W-POS-DELIVERLATER** (`scripts/poc_pos_deliverlater.js`): sale on doctype 132 → C_Order CO +
M_InOut DR in ONE signed group, chainOk=Y · **on-hand UNMOVED** after the sale (qtyOnHand fold) · the
shipment surfaces in the §S-2 selector query (`docstatus IN ('DR','IP')`, POS-generated) · scan-commit /
confirm-complete → on-hand moves by the picked qty · §FALSIFIER 1: the WR sale (doctype 135) produces
ZERO open docs (regression — W-POS-WR byte-unchanged) · §FALSIFIER 2: double-complete of the DR
shipment refused (FSM) · §FALSIFIER 3: totals to the cent, nothing invented.

## G-2 live imageKey = content-len STUB → real SHA-256 (bim-ootb, one train; non-visual, no screenshot gate)
Lane U's own # DONE admits it: live `pos_lens.js` registers ship `imageKey` as a content-length stub —
but W-IMG-SYNC's copy-job premise is CONTENT ADDRESSING (sha256 of the bytes). The engine proof and the
live wiring currently diverge (fork-class bug). Fix in /tmp/wt-* off fresh origin/main:
- `imageKey = 'sha256:' + hex(await crypto.subtle.digest('SHA-256', bytes))` — Pages is HTTPS, SubtleCrypto
  is present; keep an honest refusal/null when absent (never a fake key).
- SAME train: live-verify the browser-IDB half headless witnesses could not reach (named limit in
  poc_img_folder.log): localhost §-log proof that ImgStore's IDB folder put/get round-trips a register's
  photo and `readImage`/resolve tiers full→thumb→placeholder on the live page. Witness **W-IMG-LIVE**
  (`§IMG-LIVE folder=idb put=Y get=Y tier=full key=sha256:…`).
- sw v656→next, ?v= bump pos_lens, orphan-check, Pages verify. Falsifier: a tampered blob under a
  sha256 key must be detectable (key ≠ recomputed hash — log the mismatch, render thumb).

## G-3 upgrade W-WH-CONFIRM rule-consistent → oracle-equivalent (STRETCH; ⛔-park if PG not drivable)
The live `idempiere_test` Postgres that B-1/B-2 drove (HARDEN technique) can run a REAL doctype-148
cycle: create MMS shipment → complete (expect IP + PC confirm spawned) → complete the confirm →
re-complete the InOut → capture m_inout / m_inoutconfirm(lines) / m_storageonhand deltas. Diff
`inout_confirm.js`'s fold against the captured rows (spawn shape · IP wait · on-hand timing · short-pick
MovementQty reduction). Green ⇒ ledger 43→44 oracle-equivalent and the rule-consistent tier starts
draining. If the PG/docker is not drivable this session: ⛔-park with that one fact, move on.

## G-4 banking + doc honesty (the writer tail — factual edits direct; the PAPER is propose-first)
- **Matrix rows** (`docs/ERP_COVERAGE_MATRIX.md`): add the killer-demo POS rows — §P-9 register
  (W-POS-REGISTER) · §P-10 edit (W-POS-EDIT) · §P-13 hold/recall (W-POS-HOLD) · §P-12 confirmation fold
  (W-WH-CONFIRM, mark RULE-CONSISTENT like MProduction/MInventory — unless G-3 upgraded it) · images
  folder+copy job (W-IMG-FOLDER/SYNC + W-IMG-LIVE if G-2 ran). Rows-as-data sit in
  prompts/POS_ENGINE_LANE.md # DONE — copy, don't re-derive. Evidence rows; tally unchanged unless G-3.
- **docs/POSLens.md §11** honest-scope is STALE ("the browser POS lens itself: specified, not yet
  built") — add the supersede note: lens LIVE since #269 v652, full loop #274 v655, DIY demo #276 v656.
- **docs/ERPUserGuide.md**: document the shipped surfaces — album cards, floating payment panel,
  Import pill (snap+scan+price), DEMO payment QR, hold/recall — against the LIVE UI (cite § logs).
- **docs/MigrateComparisonPaper.md**: the snap-and-sell story (register-at-the-till, hold/recall,
  pick-confirm gate) is now LIVE and belongs in the hook + roadmap item 1 — **PROPOSE the diff to the
  user first** (standing MIGRATE_PAPER_REVISE rule), edit only on approve. Then `mkdocs gh-deploy`.
- Update PROGRESS.md §Current State + FRONTEND_LANE_MASTER handoff block; commit EXACT paths.

## DONE WHEN
G-1 W-POS-DELIVERLATER green (log read) + committed exact-path · G-2 one clean train, W-IMG-LIVE green,
Pages verified · G-3 ✅ (ledger 44) or ⛔ with its one fact · G-4 banked (paper edit only after user
approves the proposed diff). Report = ✅ list + ⛔ questions (WORK-TO-ZERO).

# DONE — 2026-06-12g2 (this session; every claim has a § line in the named log)

# G-1 ✅ W-POS-DELIVERLATER (bim-compiler 5bc4b389; build/erp/poc_pos_deliverlater.log exit 0, 0🔴)
#   pos_core.js: deliverLaterPolicy (doctype row VERBATIM — SO/N/N; WR refuses 'cash-and-carry') ·
#   buildDeliverLaterGroup (order ops + SET_STATUS C_Order CO + shipment via the SAME buildDoc spec,
#   born DR — NO SET_STATUS M_InOut; ship doctype = dictionary link 132→120; invoice NOT in group,
#   timing NAMED from extracted C_Order.InvoiceRule='I') · completeShipmentOps (§S-4 scan-commit:
#   short-pick REDUCES MovementQty — extracted SO-side rule; doctype 148/147 confirm flags REFUSE →
#   inout_confirm gate; double-complete refused not-open). §-evidence: `§POS-DELIVERLATER sale
#   order=950001 doctype=132(SO) ops=7 statuses=[C_Order→CO] inout=950002 born=DR invoice=none` ·
#   `§S-2 selector open-pos-docs=[950002 DR]` then EMPTY after pick · `pick … picked=2/3
#   onhand-delta=-2` (moves by PICKED, not asked) · falsifiers WR-regression (W-POS-WR + 5 sibling
#   logs byte-unchanged vs HEAD) / double-complete / cents gt=109.25 maxDiff=0c.
#   API: POSCore.buildDeliverLaterGroup(ctx, cart, {orderId, inoutId, c_bpartner_id, doctype:<row>,
#   invoiceRule}) · POSCore.completeShipmentOps(inout, lines, dt, {pickedQtyOf}) → {ops, movements}.
#   buildOrderOps gained opts.doctypeId (absent = station doctype, WR path byte-identical).

# G-2 ✅ W-IMG-LIVE (bim-ootb PR #277 squash 498eba8, sw v657, orphan-checked; poc_img_live.log exit 0)
#   pos_lens.js: imageKey = 'sha256:'+hex(SubtleCrypto digest of DECODED bytes) replacing the
#   content-length stub (`§POS-IMGKEY`); no SubtleCrypto → null (honest). img_store.js (synced ==
#   build/erp source): resolveImage(store,key,thumbOf,digestOf) re-hashes sha256-keyed blobs before
#   full-tier; mismatch → thumb + tampered:true. Read path rides it (`§IMG-TAMPER`). Pins
#   img_store?v=2 / pos_lens?v=3. §-evidence: `§IMG-LIVE folder=idb put=Y get=Y tier=full
#   key=sha256:…` · `tiers=full→thumb→none` · `§IMG-TAMPER detected=Y tier=thumb` · shipped-wiring
#   greps (stub absent, sha256 present, sw v657). W-POS-LIVE re-run on the tree: PASS (§POS-CENT 0c).
#   Headless W-IMG-FOLDER/W-IMG-SYNC re-run green (resolveImage back-compat, no digestOf = unchanged).

# G-3 ⛔ BLOCKED (one fact, proven by run — /tmp/confirm_oracle_run.log): the PG is reachable
#   (idempiere_test answers; doctype 148 row read) but Adempiere.startup(false) CANNOT run headless:
#   SecureEngine init NPEs on BaseActivator.getBundleContext()==null — iDempiere's Service locator
#   needs a live OSGi BundleContext (the JUnit plugin tests run under tycho/PDE OSGi). The rollback-
#   safe drive is WRITTEN + COMPILES (scripts/logic_oracle/ConfirmOracle.java, 35b8e96f): one-trx
#   MInOut(148) → IP gate → short-pick confirm → CO, on-hand ladder captured, then ROLLBACK. Runs the
#   moment an OSGi runtime hosts it. Ledger stays 43; W-WH-CONFIRM stays rule-consistent.

# G-4 ✅ banked: docs/ERP_COVERAGE_MATRIX.md third-axis +6 POS rows (register · edit · hold/recall ·
#   confirmation-fold 🟡 rule-consistent w/ G-3 park note · images+copy-job incl. W-IMG-LIVE ·
#   deliver-later) — evidence rows, tally unchanged · docs/POSLens.md §11 supersede note (lens LIVE
#   #269 v652 → #277 v657) · docs/ERPUserGuide.md §7 killer-demo surfaces (album cards, floating
#   panel, Import pill, DEMO QR, hold/recall, § cites) · PROGRESS.md + FRONTEND_LANE_MASTER 06-12g2
#   handoff. PAPER: hook + roadmap-item-1 diff PROPOSED to the user (MIGRATE_PAPER_REVISE rule) —
#   edit + mkdocs gh-deploy on approve.
