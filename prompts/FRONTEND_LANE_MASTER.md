# ⚠ DO NOT REMOVE — Combined FRONT-END lane · THE SINGLE PLAN (open this first; it supersedes the handoffs)
# WHO I AM: the one combined FRONT-END lane. Backend/engine = CLOSED+FROZEN. Tour = DONE+BOUND. I own everything
#   front-of-seam: host-conformance · engine consumption (`window.ERP`, never reach past it) · the AD-gen STRUCTURE
#   (any-source → renderable iDempiere) · data-acquisition (INSTALL + MIGRATE icons) · the lenses · Tour stability.
# THIS SUPERSEDES (kept only for detail; act from HERE): COMBINED_ERP_LANE.md · TOUR_GUIDE_FRONTEND_HANDOFF.md ·
#   AD_RENDER_HANDOFF.md · LENS_FAMILY.md · MIGRATE_SHOWME_OVERLAY.md · SPECS_AND_STRATEGY_RESUME.md.
#   Specs: docs/AD_GEN_FROM_DICTIONARY_SPEC.md · docs/ENGINE_CONTRACT.md §1/§2/§6.1 · docs/PLUGIN_ARCHITECTURE.md §13.7.
# NON-NEGOTIABLE (every turn): spec-first · witness-led (each test NAMES its issue) · §-log first (READ the log) ·
#   deterministic/NON-INVENT (real rows; absent→source/coverage, never synthesized; NO Date.now/Math.random in op paths) ·
#   consume the seam / NEVER fork a verb (browser files are UMD copies of bim-compiler/scripts/) · EXPLICIT GO before deploy.

---

## ▶ THESIS + STATE (2026-06-03)
ONE owned model (AD dictionary + data + signed op-log); the UI is a cheap swappable LENS. Three streams converged:
the ENGINE is frozen behind a 5-call seam (`window.ERP`); the TOUR is bound + read-only; I built the AD-gen STRUCTURE
(fold ANY source → renderable iDempiere seed, render-proven headless). What remains is front-end assembly: the two data
icons (INSTALL + MIGRATE) over `dispatch`, the live write path into the lenses, the Accts-Posted panel, and shipping the
render. **NEXT SESSION = plan + organise agents from §WORK; build ONE bounded task at a time; GO before deploy.**

## ▶ POC SHIPPED — localhost (2026-06-03, this arc) + GAP LEDGER  ← READ THIS FIRST for resume
Phase decision: **deploy = LOCALHOST** (bim-ootb/erp, dev :9090, sw **v568**), NOT gh-pages (Accts-Posted Item C
did go to gh-pages PR #94/#97; everything after is localhost). Built + §-witnessed on `idempiere.html`:
- **Accts-Posted lens** — desktop `mount` + mobile `mountAccordion`, `§POSTED-READ/-GATE/-COVERAGE/-CTX/-MOBILE`. (`prompts/ACCTS_POSTED_PANEL.md`)
- **Pill rail** — `icons.js` Lucide SVG (NO emoji), ALONGSIDE the classic bar ([[project_pill_alongside]]); iDempiere toolbar actions transferred (nav/refresh/grid-form REAL; New/Save/Delete/Attach honest-disabled); glassbowl/gravity REMOVED. `§RAIL/§RAIL-NAV`.
- **RED-PILL 3-state** — classic→expanded→clean (header 🔴 rightmost + in-rail 🔴 revert + `⋯` mini; bar hides, `#idmp-content` maxes; localStorage). `§REDPILL`.
- **Empty-start DASHBOARD** — KPI tiles + by-status strip, real `ad_seed.db`, `§DASHBOARD tiles=6 handAuthored=0` (`erp_dashboard.js`).
- **Mobile cards** (reuse `ad_ui .acc`) `§MOBILE-VIEW` · **Graph/Kanban switchable views** `§VIEW` · **Migrate**→`MigrateShowMe` · **Install**→QR/pair stub `§INSTALL-PILL`.
- **WRITES (POC-DEMO, signed kernel)** — `ErpSigner` installed; kanban drag→`SET_STATUS`, New/Save/Delete → signed+chained ops. `§WRITE-DRAG/-CRUD/-CHAIN/-SIGNER`. I-4 decided (POC): use deployed signed `kernel_ops.js`.

### GAP LEDGER — what the NEW session closes (in priority order)
1. **⚠ ENGINE (gates ALL real writes):** resolve `prompts/ENGINE_FULL_ERP_ISSUES.md` decision matrix (I-A durability · I-B New/DocNo via §6.1 edge-mint · I-C callouts · I-D O(n²) seal · I-E single-writer · I-F schema · I-G posting · I-H migration · I-I fold/hash). Each resolution → wire that write; until then it stays demo/disabled.
2. **Projection persistence:** edits commit to the op-log but NOT IDB (`kernel_ops` keys on unset `APP.DB_URL`) → reload re-folds `ad_seed`, visible edits reset (op-log survives). Fix: set `APP.DB_URL` + persist, OR replay op-log over projection on boot.
3. **Streaming T1/T2** ("the rest of the data") NOT wired — non-seed tables show "not in seed". `docs/DATA_ACQUISITION_ORCHESTRATION.md §8` (the unified login→client→tier→lens flow, written this arc).
4. **Attach** (no blob path) · real **posting** beyond sales-invoice class (§13.6 record-keyed `fact_acct`) · **client→shard** select on read.
5. **Odoo depth:** the landing dashboard → a real interactive Kanban dashboard (pillar 1); kanban drag→dispatch as a default view (needs write path, gated by #1).
   - **✅ kanban drag→dispatch WIRED + DEPLOYED LIVE (§KANBAN-WRITE-RESULT PASS, 2026-06-04, bim-ootb PR #115, sw v569).**
     The board chrome/drag-resolution were already done; the gap was that `dispatch`/`ctx` were null (TODO STEP-0) → snap-back.
     `kanban_lens.html` now boots `window.ERP` (the seam) like `spike_writepath.html`: per-row fold (real doc cards) +
     role-gated ctx + all wfmc stages as columns. A legal drag commits a **signed SET_STATUS** (chainOk=Y, card moves);
     illegal drag snaps back. Witness `tests/poc_kanban_write.js` (C_Invoice#109 CO→VO).
   - **✅ gap #2 DURABILITY DONE + LIVE (§KANBAN-PERSIST-RESULT PASS, bim-ootb PR #117, sw v570):** `kanban_lens.html`
     persists the projection op-log to IDB (key `kanban_proj`) after each ok dispatch (onResult export+idbPut — the seam's
     erp_kernel path bypasses KernelOps.commitOp so APP.DB_URL alone won't fire) and restores it on boot; `foldDocStatus`
     overlays the projection `documents` tip (read-the-tip). A drag now survives a full reload (C_Invoice#109 CO→VO comes
     back in VO, tipOverlaid=1). Witness `tests/poc_kanban_persist.js`.
   - **✅ gap (c) MAIN-RENDERER DONE + LIVE (§IDMP-KANBAN-RESULT PASS, bim-ootb PR #119, sw v571):** idempiere.html's
     Kanban pill now mounts the REAL draggable `KanbanLens` board over the open window's records (per-row docstatus fold,
     op-log tip overlay) and a drag commits a signed `SET_STATUS` via `window.ERP` built from the login `_session`.
     Factored the host into **`kanban_host.js`** (`window.KanbanHost.{publish,tip,persist}`) so the lens + idempiere share
     ONE write path. Witness `tests/poc_idmp_kanban.js`: login → Invoice window 167 → Kanban → board 11 cols/4 C_Invoice
     cards → drag C_Invoice#100 CO→VO (chainOk=Y). Honest read-only fallback if engine absent.
   - **✅ (a) LAUNCH-FROM-GRAPH UX DONE + LIVE (bim-ootb PR #120, sw v572):** the Graph pill and Kanban pill are two
     lenses of the SAME doc-status data, so the Graph view now carries a **🗂 View as Kanban** button (launch the
     interactive board in one tap from the graph icon after login) and the board carries **📊 View as Graph** back.
     User-directed UX call (made it, didn't hand back). Verified visually (`tests/see_idmp_flow.js` + switch_2_kanban.png).
   - **STILL OPEN (parallels, not blocking):** chat lens `send`→dispatch (same TODO(STEP-0), now trivial via
     `kanban_host`) · making the board the literal *default landing* (bigger entry-view change) · R5 receipt channel-deliver.

### OUTSTANDING — dictated / parked backlog (surfaced from memory 2026-06-04; memory now only LINKS here)
**WORK-TO-ZERO (CLAUDE.md contract):** this is THE list. Each session works it top-to-bottom to zero — do the
item, witness it, then prefix it `✅ DONE (witness)`. Never re-park, never re-ask what the code answers. If an
item needs a user fact you can't extract, prefix it `⛔ BLOCKED: <one question>` and move on. Don't stop until
every line is `✅` or `⛔` (or the user interrupts).
These were dictated across sessions and were sitting in memory (or nowhere). Moved here so they are on ONE
visible list, not relied-on memory. Tagged by lane — ERP-UI items belong to THIS list; OTHER-lane items are
listed for visibility and route to their own prompt/lane.

- ✅ DONE (witness) **[ERP-UI] Two rogue floating pills in the `idempiere.html` top bar → align to the registry/top-bar.** (logged 2026-06-14, USER-CONFIRMED via live mobile screenshot)
  THE TWO (live-confirmed, not the burger):
  1. **"▤ Reports"** = `#reportMenuPill`, `position:fixed; left:14px; top:10px` (`report_overlay.js:487/947`).
     Added by the **Reporting lane — PR #295** (`07f37de`, sw v669) — it shipped a LOOSE fixed top-left pill
     instead of a registry pill.
  2. **"✎ Edit mode"** = `#crudModeWrap`, `position:fixed; right:150px; top:10px` (`crud_overlay.js:330/931`) —
     from the CRUD overlay; a floating checkbox-label, mismatched with the idempiere top bar.
  USER DIRECTION for the fix: **(a)** the Edit-mode button must be **synced with the idempiere top-bar style**
  (live in the header bar consistently, not a loose mismatched float); **(b)** "Reports" belongs in the pill
  rail / menu, not a fixed float; **(c)** **Red pill stays in the pill rail ONLY** (it already is — don't
  regress it out). No controls outside the pill/menu/top-bar ([[feedback_pill_icon_consistency]]).
  FIX: remove the two `position:fixed` controls; surface Reports from the registry (`pills_idmp.json` +
  `IdmpPillActions.reports` → the same `report_overlay` entry); restyle/relocate Edit-mode into the idempiere
  header bar (match `#idmp-header` button style) or a pill. Bump erp/sw.js + `?v=`, worktree off origin/main, PR.
  Verify with a LIVE-driven mobile DOM probe (no `#reportMenuPill`/`#crudModeWrap` floats; Reports reachable;
  redpill still pill-only). NOTE: local `~/bim-ootb` was 9 commits stale during triage — use origin/main.
  SEPARATE pre-existing bug (not the user's complaint, fix opportunistically): the ☰ burger is malformed —
  `<button id="idmp-burger" class="hbtns"><button title="Menu">&#9776;</button></button>` (idempiere.html ~L337,
  from PR #88) renders as a button-in-a-button; unnest to one `<button id="idmp-burger" class="hbtns" title="Menu">&#9776;</button>`.

> **✅ CLIPBOARD RELAY LANE (2026-06-14, Sonnet) — PR #300 erp sw v672 / viewer sw v653.**
> W-OPLOG-CLIPBOARD PASS (§CL-SERIAL+§CL-UUID+§CL-DELTA). W-WH-POS-PICK-LIVE + W-WH-LIVE PASS.
> Card: `prompts/WH_SHIP_LATER_CLIPBOARD.md → # DONE`

> **▶ RED-BAND FOLD-GAP CLOSURE (carded 2026-06-14) — ✅ RE-AUDITED + QWEB DONE (Opus 2026-06-14).**
> The 🔴 band of `docs/migrate_status_panel.html` re-audited against live odoodemo via
> `scripts/extract_odoo_extras.js` → `build/erp/odoo_extras.db`. Scoreboard: `docs/ERP_COVERAGE_MATRIX.md`.
> - ✅ **W-ODOO-QWEB PASS** (`852dea16`) — 41/41 QWeb report defs + arch views extracted; `CORE.foldQWeb`
>   (`build/erp/report_overlay.js`) folds the invoice line loop to the cent (`price_subtotal=4350.00
>   maxDiff=0c`, §FALSIFIER load-bearing). Promoted to the 🟢 Done band; consolidated into the one "Odoo
>   migration" surface bullet (44 surfaces). Witness `scripts/poc_fold_qweb.js`.
> - ✅ **RECLASSIFIED — server actions are NOT a code gap.** `§SRVACT-CLASSIFY code=64 total=64`: all 64
>   `ir_act_server` are Python `code` type — there is NO declarative subset in this instance to interpret.
>   Honestly named-deferred (requires a Python runtime, not a JS fold). Panel red-band item reworded
>   accordingly; `prompts/ODOO_SERVER_ACTION_FOLD.md` superseded by this finding (no W-ODOO-SRVACT owed —
>   nothing declarative to witness). The other two red items remain NOT code gaps (analytic = data-blocked;
>   posting edge-branches = seed-dormant).

> **▶ CLIPBOARD RELAY LANE (dictated 2026-06-14) — POS ship-later → clipboard → WH walk apply.**
> Cross-device op-log transport with no server: deliver-later sale serialized to base64 blob, copied
> to clipboard, pasted into WH walk receive box → ops replay into IDB sidecar → selector offers the
> shipment → pick proceeds. Demo proves the architecture; dumb relay is the upgrade path, same format.
> Card: `prompts/WH_SHIP_LATER_CLIPBOARD.md` · Witnesses: `§CL-1` sender + `§CL-2` receiver +
> `W-OPLOG-CLIPBOARD` headless · Regressions: `poc_wh_pos_pick_live.js` + `poc_wh_walk_live.js`.

> **▶ PENDING WITNESS (dictated 2026-06-13) — REFLEXIVE AD SELF-EDIT — ✅ ENGINE LEGS DONE (Opus 2026-06-14).**
> Prove the loop the migration thesis leans on: edit an `AD_Menu`/`AD_Window`/`AD_Field` row → the menu/form
> **rebuilds right away**, no reload/codegen/restart; and an admin's change **propagates as a signed append-log**
> and **re-folds on another node**. Two of the three §-tagged witnesses now PASS headless:
> - ✅ **W-AD-OPLOG-DISTRIB** (`scripts/poc_ad_oplog_distrib.js`) — node A edits `AD_Window.Name` via a signed
>   `CRUD_UPDATE` (commitGroup, sealed+chained, verifyChain ok) → serialized to a 280-byte base64 append-log →
>   replayed on a FRESH copy of the dictionary (node B) → SAME edited name, verifyChain ok both nodes.
>   §FALSIFIER: node B before replay = original name (op-log load-bearing). The "mail the append log" leg, proven.
> - ✅ **W-AD-SELFEDIT** (`scripts/poc_ad_selfedit.js`) — engine reflexive rebuild: a signed edit to
>   `AD_Field.IsDisplayed` (Y→N hide, then N→Y show) re-folds the tab's displayed-field set 26→25→26 via
>   `crud_overlay.listTip` — "rebuild" = **re-read the dictionary, not recompile**. §FALSIFIER clean read = original.
> - ⬜ **W-AD-SELFEDIT-LIVE** (browser-gated, still owed) — the LIVE DOM actually repainting the form/menu on the
>   spot in `idempiere.html` (a live-driven DOM probe). The ENGINE half is proven above; the DOM-repaint leg needs
>   the browser. Status panel may claim "modify the model live, like iDempiere" for the engine + distribution; the
>   in-browser instant-repaint stays an architectural claim until W-AD-SELFEDIT-LIVE lands.

> **▶ 2026-06-13 — UI/UX LANE (`prompts/UI_UX_LANE.md` → # DONE): ✅ ALL THREE TRACKS SHIPPED, one session.**
> Presentation only, `newVerbs=[]`, no engine/fold changes; Lucide-only icons; NON-INVENT (ids extracted).
> - **Track A — Testing Pills** (bim-ootb PR #287 MERGED, erp sw v665): Verify-ledger toast→`#verify-card`
>   (shares `.erp-test-card` chrome with doc-cycle), tooltips, ERPUserGuide §13. `§VERIFY-CARD ok=… len=…`.
> - **Track B — POS Compact** (bim-ootb PR #288, erp sw v666): items + replenishment collapsible drawers,
>   Tender/Deliver-later as Lucide icon buttons (banknote/package), scan close-focus hint + macro focus.
>   ids/handlers/door-gate kept verbatim. `W-WH-POS-PICK-LIVE` + `W-POS-LIVE` PASS (`§POS-CENT maxDiff=0c`).
> - **Track C — WH Walk** (bim-ootb PR #289 MERGED, viewer sw v649): auto-engage on WH load (geometry-ready
>   gated), zoom pull-back, fast-confirm auto-scan, route-list drawer, ↺ switch-source (rotateCcw), audio
>   earcons (sfx.json rows). `W-WH-LIVE` + `W-WH-POS-PICK-LIVE` PASS; witnesses adapted same train; eslint 0.
> Stub prompts ERP_TESTING_PILL_UX / POS_PANEL_UX_COMPACT / WH_WALK_UX absorbed into UI_UX_LANE.md.
>
> **▶ 2026-06-13 (cont., Opus) — UI/UX LANE live-test follow-ups: ✅ BOTH SHIPPED.**
> - **Track C live-fixes** (bim-ootb PR #290 MERGED, viewer sw v650, wh_walk.js?v=5): dropped the C-3
>   auto-scan-reopen (premature "I'm at this bin" — `advance(true)`→`advance()`); zoom now frames the UNION
>   AABB of REMAINING route bins (`tan(fov/2)·min(aspect,1)` portrait-aware fit, 1.3× margin). `§WH_ZOOM
>   fit=whole`. W-WH-LIVE + W-WH-POS-PICK-LIVE PASS.
> - **Track D — POS Minimalist** (bim-ootb PR #293 MERGED, erp sw v668 [synced over #292 v667], pos_lens.js?v=7,
>   icons.js?v=7 +qrCode): fully-textless orange/green edge-rim drawers; total flanked by scan + `$`; `$` opens
>   the receipt-preview modal ([QR]·[#pos-pay-ok=Complete]·[Cancel]) — commit moved `#pos-float-tender`→
>   `#pos-pay-ok`, engine byte-identical; ⋯ dock (home/import/receipt/deliver-later); deliver-later POC opens
>   WH walk in a new tab. W-POS-LIVE (`§POS-CENT maxDiff=0c`) + W-WH-POS-PICK-LIVE PASS; user-approved on
>   localhost screenshots. ERPUserGuide §7/§9 + `mkdocs gh-deploy`.
>
> **▶ 2026-06-13 — WH×POS PICK LANE: ✅ DONE + LIVE (bim-ootb PR #283 MERGED, erp sw v663 / viewer sw v648).**
> Sonnet session ran §SONNET-TEST: **W-WH-POS-PICK-LIVE PASS** (full loop — live deliver-later sale →
> walk offers `pos-docs=1` → short-pick `CO picked=2/3 diffs=0` → write-back empties the selector) +
> W-WH-LIVE + W-POS-LIVE regressions byte-honest. **One live-only bug fixed in the train:** `wh_walk.js`
> opened the IDB cache at version 1 (below scene.js's v2 `openCacheDB`) → VersionError → sidecar never
> read; fixed via a shared `_openCacheDB()` (kernel_ops §KRN_PERSIST_FIX idiom). Also declared
> `POSCore`/`InOutConfirm` in `eslint.globals.json` (no-undef gate). Synced origin (#282 NinjaExcel) →
> erp sw v661→v663. Spec status flipped: SPATIAL_PICKING_SPEC **§S-2b ✅ LIVE**. Original dictation kept below for scope:
> W-1 door + W-2 selector (sidecar fold + write-back) + W-3 completion (built by Fable5; spec §S-2b).
> Original dictation (2026-06-12i):
> W-1 POS deliver-later door (dictionary-gated) · W-2 draftPick selector (the witnessed query; the
> static-seed vs IDB-sidecar seam folded honestly) · W-3 pick-complete via completeShipmentOps + the
> 148 confirm gate · W-4 witnesses + ONE train.
>
> **▶▶▶ SESSION HANDOFF 2026-06-12h — USER LIVE-TEST FIXES (bim-ootb PR #280, erp sw v659 / viewer sw v647) ▶▶▶**
> - **✅ POS float panel dispose-with-cart** (`§POS-FLOAT dispose=cart-empty`, pos_lens?v=4): sale completes /
>   last line removed → panel dismisses itself; cart pill re-summons; ✕+swipe-down had landed v658 (#278).
>   Dismiss never touches the cart or the signed sale (Complete = ONE signed group, unchanged). W-POS-LIVE
>   extended (dispose + re-summon) PASS.
> - **✅ "WH does not have the DB" = the PWA-RESUME TRAP**, not the pill: the shipped GH pill URL is healthy
>   (W-WH-LIVE-PAGES PASS on `../buildings/warehouse_gardenworld.db`); an OCI-era `pwa_last_db` resumed the
>   DELETED bucket URL on bare opens → viewer bricked. main.js?v=41: clear stale key + ONE redirect to the
>   landing (`§PWA_RESUME_CLEAR`); explicit ?db= errors stay visible (falsifier). NEW W-PWA-RESUME witness
>   (scripts/poc_pwa_resume.js) PASS; W-WH-LIVE regression PASS; stale OCI pin in poc_wh_live_pages.js fixed
>   (bim-compiler 5dfe3da8). NOTE: big-building dbs still ride OCI `_prodBase` (landing); ONLY the warehouse
>   db is in-repo — bare viewer default `buildings/Duplex_extracted.db` does not exist on Pages (named).
>
> **▶▶▶ SESSION HANDOFF 2026-06-12g2 — POS GAP-CLOSE LANE DRAINED (`prompts/POS_GAP_CLOSE.md` → # DONE) ▶▶▶**
> - **G-1 ✅ W-POS-DELIVERLATER** (bim-compiler 5bc4b389) — THE SEAM GAP closed: doctype-132 sale = order CO +
>   M_InOut born DR (policy from the dictionary row, invoice timing named from InvoiceRule='I'); the §S-2
>   selector (`docstatus IN ('DR','IP')`, POS-generated) surfaces it; `completeShipmentOps` scan-commit moves
>   on-hand by the PICKED qty, confirm-demanding doctypes (148) refuse → inout_confirm gate. WR regression
>   byte-unchanged. **UNBLOCKS the §S-2 selector / fulfillment-walk wave** (walk-side wiring still to build).
> - **G-2 ✅ W-IMG-LIVE** (bim-ootb PR #277 sw v657) — sha256 imageKey ships (stub removed), tamper-gated read
>   path (`§IMG-TAMPER detected=Y`), live IDB folder proven on idempiere.html. W-POS-LIVE re-run PASS.
> - **G-3 ⛔ BLOCKED (one fact):** headless `Adempiere.startup` NPEs on OSGi BundleContext (SecureEngine
>   Service locator) — the 148 PG-drive needs an OSGi runtime. `ConfirmOracle.java` written+compiled, rollback-
>   safe, ready (35b8e96f). Ledger stays 43; W-WH-CONFIRM stays rule-consistent.
> - **G-4 ✅:** matrix +6 POS third-axis rows · POSLens §11 supersede · ERPUserGuide §7 killer-demo surfaces ·
>   PROGRESS block. Paper hook/roadmap diff PROPOSED to user (await approve → edit + `mkdocs gh-deploy`).
>
> **▶▶▶ SESSION HANDOFF 2026-06-12g — POS KILLER DEMO ✅ SHIPPED LIVE (`prompts/POS_KILLER_DEMO.md` + `prompts/POS_ENGINE_LANE.md` → # DONE; two Fable/Sonnet sessions + Opus deploy, bim-ootb PR #276 sw v656) ▶▶▶**
> - **✅ ENGINE (Fable-5 session, bim-compiler 9857aefc):** E-1 W-POS-REGISTER (`buildRegisterGroup` — signed
>   M_Product+price+poskey+AD_Image group, dict-defaulted, det-PKs, falsifiers no-barcode/over-cap/dup) · E-2
>   W-POS-EDIT · E-3 W-IMG-FOLDER + W-IMG-SYNC (`img_store.js` IDB folder + copy job) · E-4 W-POS-HOLD (park=DR,
>   recall completes the HELD order, 1 C_Order) · **E-5 W-WH-CONFIRM** (unblocked — `inout_confirm.js`, doctype-148
>   pick-confirm, on-hand moves at confirm; oracle MInOutConfirm.java line-cited; split refused honestly).
> - **✅ UI (Sonnet session → Opus deploy, bim-ootb PR #276 squash 97b8832, sw v656):** U-1 album image cards
>   (`§POS-ALBUM cards=16 placeholders=16`, +Lucide `image` glyph to icons.js) · U-2 floating draggable payment
>   panel replacing the fixed sheet (`§POS-FLOAT`, own z-layer, position persisted) · U-3 Import pill snap+scan+price
>   → tile (`§POS-FIRSTSELL`) · U-4 DEMO payment QR (`§POS-PAYQR amount==GrandTotal demo=Y`) · U-5 screenshots
>   shown+OK'd before the train.
> - **✅ TRAIN:** sw v655→v656, precache+load `img_store.js`, ?v= pos_core 2/pos_lens 2/icons 4; W-POS-LIVE re-run on
>   the bumped tree PASS (`§POS-CENT maxDiff=0c`, witness updated for the new surface, banked a68bbbee); regressions
>   crud/void/replenish_loop green; orphan-checked (origin/main carries it all) + Pages live-verified v656 + behavior probe.
> - **§P-11 payable-QR ⛔ → RESOLVED for the demo** via the explicit DEMO/SAMPLE-labeled generic QR (no branded EMVCo
>   rails); real-DuitNow static-QR-upload variant stays the next increment.
> - **▶ NEXT (named, not opened):** §S-12 ERP info chips on WH pick → §S-13b stock-color pill → §S-13a Find Products
>   facet (read-only folds, data-gated like the Walk pill — the WH-context arc) · POS returns-with-restock UI · §P-5
>   multi-station · receipt-URL buyer copy · EOD email · batch Import spine (Excel/social, the §P-9 import half).
>
> **▶▶▶ DICTATED 2026-06-12f — POS DIY-UX + WH-context arc SPLIT INTO TWO LANES (Fable5 review of the UI session's specs — `docs/POS_ADDON_SPEC.md §3b/§P-6..§P-13` + `docs/SPATIAL_PICKING_SPEC.md §S-2/§S-12/§S-13` + resume card `prompts/POS_LENS_SESSION.md`) ▶▶▶**
> - **Specs REVIEWED + revised to fit (Fable5, this block's writer):** POS_ADDON_SPEC status block synced to v655
>   reality (#269/#271/#274; void DONE, cent ring lit) · §P-12 + SPATIAL §S-2 gained the **open-docs honesty note**
>   (WR completes M_InOut IN-GROUP → nothing to pick; pickable sale = deliver-later plain-SOO shape, doctype 132,
>   shipment stays DR — ENGINE builds it) · §P-13 gained recall-completes-the-HELD-order + no-duplicate falsifier ·
>   §P-9 data-Q (a) **DECIDED on extracted facts**: AD_Image row + capped ≤~32KB `binarydata` thumbnail (origin/main
>   seed verified; ⚠ shared ~/bim-ootb checkout was STALE — verify seed facts via `git show origin/main:erp/ad_seed.db`)
>   + §P-9.4 dictionary-defaulted mandatory M_Product cols.
> - **▶ UI LANE (Sonnet): §P-6 mobile layout → §P-7 pill icons → §P-8 continuous scan → §P-11 receipt (display
>   half)** per `prompts/POS_LENS_SESSION.md` NEXT block — pure presentation, worktree off fresh origin/main,
>   screenshots 390×844, witnesses green, branch committed, **NO deploy until the user sees the screenshots**
>   (UI-iteration rule). Then §S-12 chips → §S-13b stock pill → §S-13a Find facet (read-only folds, same gate).
> - **▶ ENGINE LANE (Fable5): §P-9/§P-10 signed master-data write groups · §P-12 deliver-later DR-shipment sale
>   (pos_core spec from the dictionary) + walk's open-docs §S-2 selector engine side · §P-13 hold/recall pos_core
>   glue (park-DR + complete-the-held-order).** Each = spec'd, witness-named, newVerbs=[].
> - **⛔ BLOCKED (one user fact): §P-11 payable-QR** — real DuitNow QR = registered-merchant EMVCo payload; a
>   self-composed amount QR won't parse in bank apps. Proposed: owner snaps their OWN static DuitNow QR once
>   (stored as an AD_Image like §P-9) + receipt shows it beside the LARGE committed total. Awaiting the user's call.
>
> **▶▶▶ SESSION HANDOFF 2026-06-12e — MULTI-LANE WAVE 3 ✅ (`prompts/MULTI_LANE_WAVE3.md` → # DONE; 2 parallel lanes + serial train, bim-ootb PR #274 sw v655) ▶▶▶**
> - **✅ LANE A — B-2 workflow oracle (Fable 5, `prompts/FABLE5_WORKFLOW_ORACLE.md` → # DONE):** `ad_workflow.js` gained a
>   `replay` arm diffed against REAL iDempiere workflow traces from the live PG `idempiere_test` (11 ad_wf_process /
>   13 ad_wf_activity / 13 ad_wf_eventaudit, captured verbatim → `build/erp/oracle/wf_oracle.json`):
>   `§HARDEN surface=ad_workflow fixtures=11 diff=0 oracle=iDempiere-PG-trace` · defs md5-set both schemas
>   `§HARDEN-SRC kind=wf setdiff=0` (58 wf/262 node/207 next/1 cond) · compiled-classes semantics arm
>   (`scripts/logic_oracle/WorkflowOracle.java`, the B-1 LogicOracle technique): `§HARDEN-STATE … 6/6` +
>   `§HARDEN-GATE … diff=0` · `§HARDEN-DOC replayed C_Order docstatus=CO == live c_order(200002)` · 2 load-bearing
>   §FALSIFIERs · 7 `§HARDEN-SKIPS` (claim = 11 real processes diff=0, NOT corpus-wide). **Oracle ledger 42→43,
>   ⬜=NONE** (matrix + ERP_EXECUTION_ROADMAP + HARDEN_MATRIX updated). Log `build/erp/poc_wf_harden.log`; W-WF
>   regression intact. Banked bim-compiler f941f073.
> - **✅ LANE B — POS FULL LOOP §L-1..§L-3 (`prompts/POS_FULL_LOOP.md` → # DONE):** W-POS-CRUD (`§POS-CRUD edit=description
>   cols=description statusOp=none verifyChain=ok` · listOptions CO-selected · docstatus edit routes DOC_ACTION) ·
>   W-POS-VOID (`§POS-VOID order=100 CO→VO group ops=2 chainOk=Y` · `postings-net=0c accounts=3` ·
>   `onhand-restored=Y` · double-VO refused) · W-POS-REPLENISH-LOOP ENACTED (`§POS-LOOP suggest qty=11 product=124`
>   → vendor 114 Tree Farm from real m_product_po → `po=CO` → `receipt=CO` → `onhand before=9 after=20` →
>   `suggestions … cleared=Y`; newVerbs=[]; falsifiers receipt-no-po + short-receive). Witnesses banked
>   bim-compiler 23ae7807; logs `build/erp/poc_pos_{crud,void,replenish_loop}.log`.
> - **✅ TRAIN:** bim-ootb **PR #274 squash-merged** (CI SUCCESS), **sw v655**, orphan check `origin/main:erp/sw.js =
>   v655`, Pages live-verify v655 + live `pos_lens.js` carries `vendorOf()`/`buildReplenishPO()` (§L-3 wiring live);
>   post-merge W-POS-LIVE `§POS-CENT Dr=137.75 Cr=137.75 maxDiff=0c` all 5 stages green. Rebase over #273 clean.
> - **Residuals (named):** short-receive PO remainder=6 untracked by the suggestion engine (re-fires below min only,
>   §-named) · §HARDEN-SKIPS actions F/X/P/R/C unexercised (replay THROWS, never invents) + wf115 conditioned
>   transition untraced · AD_Rule stays ⛔ n/a-in-seed (fact_reconciliation=0 re-verified live).
> - **▶ NEXT (named sequel, not opened):** B-3 0-seed posting oracles (own Fable-5 card) · Phase C UI wiring C-1..C-4 ·
>   POS next-increments (returns-with-restock / §P-5 multi-station / receipt-URL / EOD email).
>
> **▶▶▶ SESSION HANDOFF 2026-06-12c — WAREHOUSE_GH_LINK_PILL ALL 5 ITEMS ✅ (bim-ootb PR #272 sw v654, `prompts/WAREHOUSE_GH_LINK_PILL.md` → DONE) ▶▶▶**
> - **✅ ITEM 1 — WH db on GH Pages:** `buildings/warehouse_gardenworld.db` (61,440 B, md5 `520be9be…`) tracked in bim-ootb (`.gitignore` removed `buildings/` dir exclude, added `!buildings/warehouse_gardenworld.db` exception). Short URL: `viewer/viewer.html?db=../buildings/warehouse_gardenworld.db`. OCI duplicate pending delete (STEP 8 — do after LIVE Pages confirm).
> - **✅ ITEM 2 — Warehouse pill:** `box` icon, order 4.7, added to `pills_idmp.json` (v29) + `IdmpPillActions.warehouse` (opens viewer with `?home=<idempiere.html>`) + `warehouseShare`. W-WH-PILL exit 0: `§WH-PILL opened url=…warehouse_gardenworld.db&home=…` + §FALSIFIER (no pill on wrong surface).
> - **✅ ITEM 3 — POS pill verify:** PR #269 already on `origin/main`; W-POS-LIVE exit 0 (§POS-CENT `maxDiff=0c`). Added `?lens=pos` deep link (`_pendingLens` → `openPosFor()` post-login) + `posShare` in `IdmpPillActions`.
> - **✅ ITEM 4 — Share icon in header:** `#idmp-share-btn` with `ICONS.share` SVG; wired to share current `?window=<id>&record=<pk>` URL (§IDMP-SHARE). Added between switch and home buttons.
> - **✅ ITEM 5 — Home nav chain:** ⌂ in POS overlay (`pos_lens.js` homeBtn removes `#posted-overlay`, logs `§POS-HOME`); ⌂ in WH viewer (`config.js A.HOME_URL = _params.get('home')`, `panels.js §WH-HOME renderer` fixed top-left). `#idmp-home-btn → erp.html` UNTOUCHED.
> - **Orphan-checked:** `CACHE_VERSION='v654'` + `buildings/warehouse_gardenworld.db` 61440 B + `"warehouse"` in pills_idmp on `origin/main` ✅.
> - **✅ REMAINING CLOSED (2026-06-12c same session):** W-WH-LIVE-PAGES PASS on OCI URL · OCI duplicate deleted (404 confirmed) · `docs/ERPUserGuide.md §9` updated with full UX guide + screenshots · `docs/SPATIAL_PICKING_SPEC.md §6` extension arc written (§S-7 packing · §S-8 handling overlays · §S-9 robot · §S-10 AR via SiteCam · §S-11 bin-share deep link) · docs deployed `mkdocs gh-deploy`.
>
> **▶ DICTATED 2026-06-12d — WH browse-mode ERP context (user live-test finding, spec'd `docs/SPATIAL_PICKING_SPEC.md §S-12 + §S-13`):**
> User opened warehouse_gardenworld.db, picked "Store Central rack" — info card shows IFC fields ONLY
> (no M_Locator / on-hand / M_Movement context) and the pill bar is the full 30-action construction
> profile. Two items, both spec'd with witnesses + falsifiers:
> **✅ DONE (W-WH-CACHE, bim-ootb PR #273 sw v644, LIVE-verified 2026-06-12) — P0 BUG: Walk poisoned the building's IDB cache.**
> Fix shipped: `kernel_ops.js?v=4` `§KRN_PERSIST_GUARD` — persist only `APP.db`, guard before the
> debounce timer; `§KRN_PERSIST_SKIP` logged on foreign-db commits. Witness `scripts/poc_wh_cache.js`:
> RED on unfixed origin/main (reproduced 16KB clobber + dead reload), 11/11 green on fix, W-WH-LIVE
> walk regression PASS. Squash orphan-checked + Pages live (`sw v644`, guard fetched live). Original report:
> `kernel_ops.js _persistToIdb(db)` exports the PASSED db handle but keys the write by `APP.DB_URL` →
> wh_walk's `commitGroup(W.opDb,…)` (its own in-memory op db) persists a 16KB op-only db OVER the
> cached building (user log: `§KRN_PERSIST url=../buildings/warehouse_gardenworld.db size=16KB`,
> was 80KB) → refresh `§CACHE_HIT` serves it → building never loads (no geometry tables, silent
> death). Fix = one guard: `_persistToIdb` skips (or keys separately) when `db !== APP.db`.
> Witness W-WH-CACHE: open walk → commit a step → reload → `§DB_LOADED` + `§CENTRES_RESULT rows=1`
> + cached size unchanged. §FALSIFIER: viewer-op persist (the S243 path) must STILL survive refresh.
> User unblock documented: delete `bim_ootb_cache/dbs` key via console, reload.
> 0. **✅ DONE (bim-ootb PR #275 sw v645, W-WH-WALKMODE 13/13 + W-WH-LIVE regression PASS) — WALK-MODE UX:**
>    (a) walk mode ENGAGES — open hides #mobile-pill BIM chrome, close restores it (§MODE walk-on/off);
>    (b) MANUAL CONFIRM beside QR — green "✓ I'm at this bin" → confirmHere() feeds expected locator
>    through the SAME scanInput gate (via=manual), fixes desktop/no-camera dead-end; (c) ⌂ HOME on the
>    walk strip → A.HOME_URL or ../index.html. Presentation only, engine byte-identical.
>    RESIDUAL (still open, lower-pri): the 3D-tap-on-lit-bin path (`§PICK no guid for mesh.id=55` —
>    highlight overlay intercepts raycast) — manual button sidesteps it, but a direct dblclick-lit-bin
>    affordance (overlay raycast=noop) is still nice-to-have. Witness W-WH-DESKTOP.
> 1. **§S-12 ERP info drawer on pick** — gate=on + ELEMENT_PICK → data-gated chips (locator Value,
>    qtyOnHand FOLD per product, open movement lines; rack/aisle = aggregate of child bins from
>    m_bom_line — the observed pick WAS a rack, first-class case). Reuse wh_walk.js ensureDeps +
>    BIMtoERP §A drawer pattern. Witness W-WH-INFO.
> 2. **§S-13 WH context layer — ADDITIVE (user re-directed 2026-06-12: KEEP all BIM goodies —
>    Find/Fly/Shadow/Night — do NOT strip/profile pills).** Read-only overlays on the GUID↔locator
>    key, all data-gated like the Walk pill: **13a** Find panel grows a Products facet (product →
>    bins via qtyOnHand fold → existing fly-to+highlight; W-WH-FIND) · **13b** Stock lens pill
>    (bins colored by on-hand vs level_min, palette idiom; composes with Night/Shadow; W-WH-STOCK)
>    · **13c** Movement arrows from→to = v2. Sequence: §S-12 chips → 13b → 13a. Falsifiers: gate=off
>    → Find tree + pill bar byte-identical.
>
> **▶ DICTATED 2026-06-12c — WH bin-share deep link (§S-11, `docs/SPATIAL_PICKING_SPEC.md §S-11`):**
> Share "look at this bin" from the Walk strip the same way BIM viewer shares a picked element.
> Spec: `config.js` +`A.WH_LOCATOR = _params.get('wh_locator')` · `wh_walk.js` gate poll → if
> `A.WH_LOCATOR` set after gate=on → `focusStep` VIEW-ONLY (fly-to + bright bin + ghost rest, no
> draft `M_Movement`) · strip share button → `navigator.share(?db=…&wh_locator=<locator_id>)` ·
> log `§WH-SNAG` + `§WH-STEP-SHARE`. No login needed for recipient. Architecture: 3 small changes,
> same `focusStep`, no new kernel path. Witness: `poc_wh_snag.js` — load with `?wh_locator=X` →
> `§WH-SNAG locator=X view=only` logged, bin overlay present, no M_Movement drafted.
>
> **▶▶▶ SESSION HANDOFF 2026-06-12b — POSTING CONFIG SHIPPED (`prompts/MIGRATE_POSTING_CONFIG.md` → # DONE; 2 parallel lanes + serial train, bim-ootb PR #271 sw v653, IDB ad_seed_v15) ▶▶▶**
> - **✅ SEED + CLIENT 13 (iDempiere):** `export_ad.sh` now pulls `fact_acct` (client 11 from `idempiere_test`, 300 rows
>   TB 46574.97 — doc-id sets verified identical across both PG dbs); the six acct-config tables were ALREADY in the
>   manifest (the card's "pulls NONE" premise was a wrapper-grep artifact). REAL BUG found+fixed: `gen_ad_idmp.sh`
>   lacked `fact_acct_id` in the re-band FAMILIES → un-banded tenant ledger PK-collided with the seed's rows →
>   `INSERT OR IGNORE` silently dropped it (the #5-install bug class, one table later) — `§REBAND fact_acct.fact_acct_id
>   +1300000 rows=300`. `§MIGRATE-POSTCFG client=11|13 tokens_resolved=3/3 coverage=complete balanced=Y` ·
>   `oracle=fact_acct(318) maxDiff=0c` · §FALSIFIER load-bearing (W-MIGRATE-POSTCFG, `poc_migrate_postcfg_idmp.log`).
> - **✅ CLIENT 12 (Odoo):** `gen_ad_odoo.js` §5d honesty fixes — `{Product.Asset}` from
>   `property_stock_valuation_account_id` (was a COPY of expense = the one invented token, now real) · tax acct from
>   `account_tax_repartition_line.account_id` · `c_acctschema_default` carries the company defaults (`ir_property
>   res_id NULL`; odoodemo = Odoo 17, properties live in ir_property, zero per-record overrides). `§MIGRATE-POSTCFG
>   client=12 tokens_resolved=5/5 coverage=complete balanced=Y` · `§FRAME-FIT … oracle=live odoodemo maxDiff=0c
>   verdict=ORACLE-EQUIVALENT` (`poc_migrate_postcfg_odoo.log`). Lane files committed bim-compiler 0986251b.
> - **✅ DEPLOY TRAIN (PR #271, squash 81dd2b3, sw v652→v653, IDB ad_seed_v14→v15 on idempiere.html ×5 + erp.html ×2;
>   glassbowl carries no ad_seed refs):** ships regenerated `erp/ad_seed.db` (26,144,768 B) + `13-idempiere.db` +
>   `12-odoo.db`; orphan-checked via ls-tree sizes; live: sw v653 + `§LIVE-POSTCFG … fact_acct rows=300 ΣDR=ΣCR=46574.97
>   TB-balanced=Y → PASS` on the live page. **TWO FLIPS LIT LIVE:** `§POS-CENT live db=ad_seed.db order=910001
>   coverage=complete balanced=Y Dr=137.75 Cr=137.75 cartCents=13775 maxDiff=0c` (the POS matrix row's pending bar —
>   CLOSED) · `§AD-PROC-LIVE proc=310 name="Trial Balance" … ok=Y rows=21` (the fact_acct honest-empty residual —
>   CLOSED; `poc_ad_process_live.js` seed-gap assertion updated to the new truth, the one sanctioned witness edit).
>   Regressions green: W-AD-DOCFSM-LIVE · W-POS-LIVE (+ new §POS-CENT step) · W-MIGRATE-POSTCFG re-run on the worktree.
> - **Residuals:** Posting-Preview/Accts-Posted now have resolvable config on the DEFAULT db (was `?db=preview_demo.db`
>   data-gated) — on-screen visual confirm pending (log≠visual proof) · station-wh-104 replenish honesty unchanged.
> - **▶ Warehouse entry icon = ANSWERED 2026-06-12: user says YES** — landing icon/card like the POS cart (POC demo);
>   executing in its OWN Sonnet session via `prompts/WAREHOUSE_GH_LINK_PILL.md` (user expanded it to 5 items). ⚠ THAT
>   CARD'S ITEM 3 IS STALE: `feat/pos-lens` is NOT held — it MERGED as PR #269 (sw v652) earlier today; the Sonnet
>   session must skip the cherry-pick and just verify the pos pill on origin/main. Short deep link target:
>   `viewer/viewer.html?db=../buildings/warehouse_gardenworld.db` once the db lands in-repo; OCI URL until then.
>
> **▶▶▶ SESSION HANDOFF 2026-06-12 — MULTI-LANE WAVE 2 (POS train · spatial §S-2..§S-5 · HARDEN B-1, `prompts/MULTI_LANE_LAUNCH.md # DONE — 2026-06-12 wave 2`) ▶▶▶**
> **✅ ALL THREE LANES DONE + DEPLOYED (serial train, zero orphaned squashes — #138/#265 trap did not fire either time):**
> - **✅ LANE 1 — POS lens §P-1..§P-4 DEPLOYED (bim-ootb PR #269, sw v651→v652, LIVE-VERIFIED on Pages).** The held
>   `feat/pos-lens` GO taken: precache pos_lens/pos_core/erp_engine + ?v= bumps (icons v3, idmp_pills v11); W-POS-LIVE
>   re-run on the bumped tree exit 0 — `§POS-LIVE open station=100 tiles=16 priced=16 handAuthored=0` ·
>   `§POS-SALE … newVerbs=[] chainOk=Y ops=12 sealed=12` · `§POS-DOC order=910001 completeIt ok` ·
>   `§POS-LIVE-REPLENISH suggestions=8`. Squash d8d3adf5 orphan-checked; Pages serves v652 (CI-minified — use
>   quote-agnostic greps). **Matrix "POS lens" addon row LIVE; pends the live to-the-cent ring → CLOSED in 06-12b above (§POS-CENT maxDiff=0c)** (posting-config
>   data-gate, `prompts/MIGRATE_POSTING_CONFIG.md`). Ledger: `prompts/POS_LENS_SESSION.md ## DEPLOY DONE`.
> - **✅ LANE 2 — Spatial picking §S-2..§S-5 BUILT + DEPLOYED (bim-ootb PR #270, viewer sw v642→v643, LIVE-VERIFIED).**
>   Route = `m_bom_line.ordinal` walk order (`§W-WH-ROUTE PASS`: deterministic, permutation-invariant, each-line-once,
>   off-model→unroutable-never-dropped; draft via buildDoc `newVerbs=[]`); walk lens on the phone viewport
>   (`§W-WH-LIVE PASS`, 25 verdicts 🟢: data-gated pill on/off falsifier, wrong-bin scan REFUSED, short-pick,
>   long-press skip→ANNOTATE, `§WH COMPLETE … dispatchFor(323) foldKeys=4 diffs=0 chainOk=Y`). DB
>   `warehouse_gardenworld.db` → OCI COMMON bucket (md5-verified; OCI_UPLOAD.md §RULES followed over the card's
>   dev-bucket line — conflict flagged); LIVE Pages probe `§W-WH-LIVE-PAGES PASS` (no cubes, `§WH PILL gate=on` on
>   the COMMON-bucket deep-link). Matrix addon rows §S-2..§S-5 ✅ LIVE. Residuals: camera QR unverified on a physical
>   phone · offline walk = §P-5 sync-FSM v2 · manifest.json landing card = ⛔→ANSWERED, see 06-12b block above
>   (user says YES, Sonnet session executes `prompts/WAREHOUSE_GH_LINK_PILL.md`).
> - **✅ LANE 3 — HARDEN B-1 logic-evaluator oracle-diff (bim-compiler a77827cc, no deploy).** `ad_evaluator` ==
>   the REAL compiled iDempiere SimpleBooleanParser+EvaluationVisitor (headless via `scripts/logic_oracle/
>   LogicOracle.java`) over 2751 live-PG record-grounded fixtures: `§HARDEN surface=ad_evaluator fixtures=2751
>   diff=0 … oracle_errors=0`; md5 expr-sets ours==PG; falsifier flips both sides. **Equivalence ledger 41→42**;
>   matrix ⬜ now = ad_workflow ONLY (B-2 entrance, no seed ad_wf_activity). Honest skips named: 3 @SQL= · 542
>   window/login-context · 32 no-pk · 236 zero-row. `build/erp/poc_logic_harden.log` exit 0.
> - **NEXT SESSIONS (queued):** 1. MIGRATE_POSTING_CONFIG (one data-gate lights POS + Posting-Preview + §S-5 rings).
>   2. B-2 workflow oracle or B-4 Track B substrate (`prompts/ERP_EXECUTION_ROADMAP.md`). 3. Phase C UI wiring (C-1..C-4).
>
> **▶▶▶ SESSION HANDOFF 2026-06-11b — MULTI-LANE LAUNCH (3 parallel lanes + serialized deploy train, `prompts/MULTI_LANE_LAUNCH.md`) ▶▶▶**
> **✅ ALL THREE LANES DONE + DEPLOYED (deploy train serial, zero orphaned squashes):**
> - **✅ LANE A — B-5/C-5 live process dispatch (bim-ootb PR #267, sw v650, LIVE-VERIFIED on Pages).** Menu P/R leaf +
>   procSet-gated `?process=` deep link → `AdProcess.dispatch` via the _b3 shim; param dialog from real `ad_process_para`
>   (prepare-gate `§PROC_PARAM_VALIDATE` rejects on-screen); unregistered classname → honest absent-handler card (333
>   falsifier); B-4 pruning intact (`§IDMP-SESSION` 116/159). W-AD-PROC-LIVE exit 0 + docfsm/displaylogic/menu-prf
>   regressions green. **Matrix: AD_Process 🟡→✅ → 7✅/32🟡/3⛔.** Residuals: `fact_acct` not in seed (TrialBalance
>   honest-empty, seed-regen item) · handler registry = 5 classnames (454 SvrProcess named-deferred, unchanged).
> - **✅ LANE B — docstatus-select bug, the silent CO→DR flip (bim-ootb PR #268, sw v651, LIVE-VERIFIED).** Root cause:
>   populateRefs never marked the current value selected (gatherVals read DR off a CO order → phantom diff) AND explicit
>   status edits rode CRUD_UPDATE column writes invisible to readTip. Fix = PURE CORE seams (v646 precedent):
>   `CORE.listOptions` (current selected, absent value PREPENDED never flipped) + `CORE.splitStatusChange` (no-op diff
>   suppressed; explicit change → DOC_ACTION SET_STATUS, requires-gated like Process ▶). W-CRUD-DOCSTATUS D1–D8 PASS +
>   poc_crud_persist/poc_crud_group green. Also VERIFIED (not redone): the card's date-widget + signed-persist items
>   shipped v646. **POS write-path gate verdict: GREEN in substance** — one caveat: lane-master D/E's LITERAL
>   `§WRITE`/`§REFOLD` strings exist nowhere; equivalent evidence = `§SEAM-LIVE` + `…verifyChain=ok`. If POS insists on
>   the verbatim lines, add a thin witness over the existing seam — no rail is missing.
> - **✅ LANE C — SPATIAL_PICKING §S-1 GardenWorld warehouse compiled (bim-compiler a828258e, NO deploy).**
>   `config/warehouse_gardenworld.yaml` (11 m_locator rows EXTRACTED, X/Y/Z confirmed TEXT labels) → existing BOM
>   recursion (bom_walker + verb_expand TILE/ROUTE) → `build/erp/warehouse_gardenworld.db` (61,440 B, regenerable);
>   bin guid == m_locator_id. W-WH-COMPILE 11/11 bijection + BUFFER sum-invariant + ghost-locator falsifier + no-cubes
>   render gate (6/6 distinct vertex blobs) + local viewer smoke (`§WH_SMOKE_BINS` 11 bins streamed). §S-2 route rides
>   `m_bom_line.ordinal` (walk_seq already in the db) + drafted M_Movement/M_InOut locator pairs.
> - **NEXT SESSIONS (queued, not this card):** 1. POS build — `prompts/POS_LENS_SESSION.md` (gate GREEN per Lane B).
>   2. Spatial §S-2..§S-5 (rides warehouse_gardenworld.db). 3. HARDEN_MATRIX ladder. Deploy-train lesson held: one PR
>   in flight, squash verified via `git show origin/main:` both times — the #138/#265 orphan trap did NOT re-fire.
>
> **▶▶▶ SESSION HANDOFF 2026-06-11 — CONVENTION AUDIT + ROADMAP WRITE + NINJA EXCEL EVAL ▶▶▶**
> **✅ ALL THREE DONE 2026-06-11 (same session that shipped the UI bridge lane, bim-ootb #264 sw v647 / matrix 6✅):**
> - **✅ TASK 1 DONE (audit; flag-only as ordered).** 15-module sweep vs ERP_BACKEND_SEPARATION + ENGINE_CONTRACT:
>   zero Date.now/Math.random in op paths, zero layer-2 cross-coupling, zero kernel/localStorage/fetch reach-throughs,
>   every witness names its issue. **⚠ DRIFT (2 real, NOT fixed):** (1) `bim-ootb/erp/report_overlay.js` is a STALE
>   FORK (256 vs 908 lines — lacks the whole 527117eb reporting lane: foldStatement/foldPrint/menu surfaces); matches
>   the known "bim-ootb visual confirm" residual but is now a real source↔browser divergence → fix = sync from
>   build/erp/ + sw bump + visual confirm. (2) `build/erp/ad_callout.js:25` `round2` uses raw float `Math.round` for
>   LineNetAmt money math — violates [Numbers via BigDecimal] (negative half-cents diverge from Java HALF_UP; the
>   header claims integer-cent). 3 list corrections: ad_statements/ad_printformat don't exist (both live INSIDE
>   report_overlay.js); fold_model_logic is a prompt, code = scripts/erp_engine.js+post_resolver.js.
> - **✅ TASK 2 DONE.** `prompts/ERP_EXECUTION_ROADMAP.md` written (<100 lines): §DONE tally (41 oracle-eq ·
>   6✅/33🟡/3⛔ · UI bridge live) → §PHASE B hardening (B-1 logic-evaluator oracle-diff vs live PG · B-2 workflow
>   ⛔-unless-trace · B-3 0-seed posting oracles · B-4 Track B §H-7..§H-11) → §PHASE C UI wiring (C-1 RO/Mandatory
>   DOM → C-2 tab Where/OrderBy live → C-3 valrule+callout fields → C-4 AccessLevel record-gate → C-5 ⛔ B-5 seed →
>   C-6 docstatus-select bug) → §DEFERRED. Fable-5 keystone cards named as SPENT, not duplicated.
> - **✅ TASK 3 DONE (eval only — NO code; awaits user go/no-go).** NinjaExcel FITS the pill registry: pill id
>   `ninja`, label "Excel Report (Ninja)", EXISTING `grid` glyph, order 4.5, opens an `_overlay` panel via the
>   RuleFold contract (`NinjaExcel.open({db, SQL, status, mount})`). Touch-list = 1 manifest row + ~5-line action
>   binding + NEW `ninja_excel.js` panel (THE whole cost: the Java engine is 866 LOC of stubs — port needed, plus a
>   vendored .xlsx reader or CSV-first v1). Keep OFF the AD menu (separate paradigm, never feeds the matrix).
>   Three §9 design forks still undecided (confirm-each-binding · read-only vs op-log write-back · raw-SQL vs
>   foldStatement coupling) — **⛔ next step needs the user's go + fork picks.**
>
> **CONTEXT — just-concluded lanes to read first (holistic picture before auditing):**
> - `prompts/HARDEN_MATRIX.md` — equivalence arc resume card (the hardening discipline + MOrder archetype).
> - `prompts/ERP_BACKEND_GAP.md` — Track A DONE (all 7 interpreter modules built + witnessed).
> - `docs/ERP_COVERAGE_MATRIX.md` — live scoreboard: 0✅/39🟡/3⛔; headline tells the story.
> - `project_erp_reporting_lane` memory + commits 527117eb / 9744255d — the last two shipped lanes
>   (Reporting: foldStatement+foldPrint oracle-equivalent; A-GRAIL: fold-back via KernelOps).
> These are the SOURCE of TRUTH for what was built. Read them before drawing any conclusions.
>
> **TASK 1 — Convention & code-source audit (read-only; flag drift, do NOT fix)**
> Evaluate how well the shipped engine modules follow the established conventions:
> - Read `docs/ERP_BACKEND_SEPARATION.md` (3-layer invariant + seams) and `docs/ENGINE_CONTRACT.md §1/§2/§6.1`.
> - For each module in `build/erp/` (ad_valrule, ad_callout, ad_modelval, ad_docfsm, ad_workflow,
>   ad_tabquery, ad_reference, ad_statements, ad_printformat, post_resolver, fold_model_logic, ad_evaluator,
>   ad_access, ad_process, report_overlay), check:
>   (a) consumes `window.ERP` seam only — never reaches past it?
>   (b) pure/headless — no `Date.now`/`Math.random` in op paths?
>   (c) browser copy is a UMD of `bim-compiler/scripts/` — no silent forks?
>   (d) each witness names a real issue (CLAUDE.md "Tests expose issues")?
> - Output: a bulleted `⚠ DRIFT:` list (file:line) for any violation found. If zero drift, say so explicitly.
> - Do NOT fix anything. Flag only. Use `bash build/erp/run_witness.sh` (NOT tee) if re-running any witness.
>
> **TASK 2 — Write `prompts/ERP_EXECUTION_ROADMAP.md` (Sonnet-ready execution card; no code)**
> `docs/ERP_COVERAGE_MATRIX.md` is a status LEDGER. The goal is a NEW prompt card a fresh Sonnet session can
> open and execute with zero ambiguity — same format as `prompts/HARDEN_MATRIX.md` (scope guard + READ FIRST
> list + numbered phases, each with entrance criterion, exact files to touch, and a named witness as exit gate).
> Synthesise from (read all before writing):
>   - `docs/ERP_COVERAGE_MATRIX.md` — 0✅/39🟡/3⛔ ledger; §headline + §equivalence table tell the story.
>   - `prompts/HARDEN_MATRIX.md` — equivalence arc: H-1 MOrder (keystone, 14 oracle-eq) → H-2 25-delta table
>     (MInOut/MPayment/MProduction/MInventory/MAllocationHdr) → H-3 declarative spot-diff. Scoreboard: 14/~40.
>   - `prompts/ERP_BACKEND_GAP.md` — Track A DONE (7 interpreter modules); Track B §H-7..§H-11 still open.
>   - Recent shipped lanes: commits 527117eb (Reporting: foldStatement+foldPrint) + 9744255d (A-GRAIL fold-back).
>   - `docs/ReportingFold.md` if it exists — reporting boundary (DATA tree, not pixel).
> **Model-lane split (decided 2026-06-11):** the H-1 MOrder→equivalence keystone is carved out as a dedicated
> **Fable 5 lane** — `prompts/FABLE5_MORDER_EQUIVALENCE.md` (already written). It is the one phase worth the premium
> model (deepest reasoning, 1M context holds MOrder.java + ad_full.db + fixtures). The roadmap's Phase B must NAME
> that card as the Fable 5 lane and sequence everything else (H-2 delta walk, H-3 declarative spot-diff, UI wiring)
> as Sonnet/Opus work. Do NOT duplicate H-1 detail into the roadmap — point to the card.
> Structure the card as:
>   - `# ⚠ DO NOT REMOVE` scope + "read the log after every run"
>   - **§ DONE** — single-line tally of what's already proven (oracle-eq count, last commit, witnesses).
>   - **§ PHASE B — next hardening target** — numbered steps, each: READ X → BUILD Y → WITNESS `W-NAME`
>     (exit = `bash build/erp/run_witness.sh scripts/poc_Y.js` exit 0 + oracle maxDiff=0c).
>   - **§ PHASE C — UI wiring (🟡→✅)** — what needs a live render to flip from partial to covered; entrance
>     criterion = Phase B complete; each step names the module + the existing lens it wires into.
>   - **§ DEFERRED** — items explicitly out of scope with one-line reason (e.g. 454 SvrProcess corpus, T_* folds).
> Rules: no invented scope; every step traces to a source doc or existing witness; keep under 100 lines total.
>
> **TASK 3 — NinjaExcel as main menu feature (eval/design only; no code)**
> Evaluate whether NinjaExcel (`internal/NinjaExcelAdaptation.md`, [[project_ninja_excel]]) fits as a
> named entry in the iDempiere main menu:
> - Read `internal/NinjaExcelAdaptation.md` for scope and design.
> - Assess: does it fit the pill-registry pattern (`erp/pills_idmp.json` + `idmp_pills.js`)?
>   What is the minimal integration surface (a new pill → NinjaExcel panel lens)?
> - Propose ONE concrete approach: pill label + icon (from `icons.js`) + what it opens.
> - Do NOT implement until user says go.
>
> **OPERATING NOTES:** localhost verify first → single-shot deploy ([[feedback_run_witness]]).
> After all three tasks, continue §OUTSTANDING items below in order.
>
> **▶▶▶ SESSION HANDOFF 2026-06-07 — THE BIG ERP PUSH (continue here; supersedes the 06/06b blocks below) ▶▶▶**
> **THESIS driving this arc (user, repeated):** iDempiere = effortless, FRICTIONLESS, model-AGNOSTIC absorption —
> it folds ANY source's model and the chrome renders it with ZERO per-model code. Every UI add must honour that
> (dictionary-driven, NON-INVENT) AND delight the long-tail / lower-literacy user (colourful, status-at-a-glance,
> consistent L&F, common HMI — don't overthink). [[feedback_pill_icon_consistency]] · [[project_kanban_marvel]].
>
> **SHIPPED LIVE this arc (GH Pages, erp sw → v597; all whitebox-witnessed, all verified live):**
> - **Rule pill client-scoping** (PR #171, RULE_EDIT_SPEC §11) — folds over the logged-in client (`window.__idmpClient`),
>   honest tenant label + honest-disable; killed the hardcoded `AD_Client_ID=12`. `§RULE-CLIENT-SCOPE PASS`.
> - **iDempiere chrome §A–§D** (PR #170) — pill registry (retired hand-rolled rail) · Install/Migrate pre-client lifecycle ·
>   cross-tab history scrubber · RED PILL "just-the-pill" ⟷ classic toggle. Spec `erp/docs/ERP_BOTTOM_BAR_AND_LIFECYCLE.md`.
> - **Kanban "Odoo-marvel" cards + shared Graph/Kanban status palette** (PR #177, `erp/docs/KANBAN_MARVEL_SPEC.md`) —
>   dictionary-driven avatar/title/amount/date (zero per-model code), semantic status colours on cards AND graph bars
>   (consistent L&F). `§KANBAN-MARVEL-RESULT PASS`. Group-by deferred (honest: columns ARE the wfmc group-by/drop-targets).
> - **Mobile ⋯ pill fixes** — reopen-on-retap (PR #176; tap landed on inner `<svg>`) + FLAT horizontal kebab on all
>   surfaces (vs Android's vertical ⋮) + mobile dock ⋯ anchored right-edge so it doesn't re-center (PR #182). `§PILL-*-RESULT PASS`.
>
> **NEXT — work top-to-bottom (WORK-TO-ZERO):**
> 1. **✅ DONE + LIVE — ⏱ erp.html init-bubble INSTANT** (sw v599, PRs #188+#192, 2026-06-07; details in the DONE block
>    at line ~143). Navigation SWR + one-shot controllerchange reload backstop; warm bubblePaint 883ms→46ms.
> 2. **More "marvel" optics where they pay** — continue making lenses visual/colourful/consistent for the long tail
>    (the user's explicit direction); keep one shared status palette (`window.KanbanLens.statusColor/...`), NON-INVENT.
> 3. **⛔ Renderer #2 (Odoo) descriptor-driven** — still blocked on the user's go/no-go (see the ⛔ item below).
> 4. Then keep going down §OUTSTANDING to zero.
>
> **OPERATING NOTES (this arc, proven):** deploy = isolated worktree off FRESH `origin/main` → erp-only diff → whitebox
> `§`-witness (corroborate `§…RESULT PASS` with raw DOM, not the line alone) → PR → CI → squash-merge → bump `erp/sw.js`
> CACHE_VERSION + touched `?v=` → VERIFY live on Pages. The **viewer/history lane is concurrently active** (PRs #172–#178+,
> worktrees `/tmp/wt-h*`) — all viewer-only/orthogonal to `erp/`; `sw.js` is the conflict magnet → on conflict take the
> HIGHER version + keep ALL changelogs. An auto-resyncing merge poll (merge `origin/main` → re-witness → push) lands erp
> PRs through the churn. Symlink `~/bim-ootb/tests/node_modules` into the worktree `tests/`. Clean up your worktrees/branches
> at end ("leave no stale"); do NOT touch the viewer lane's `/tmp/wt-*` or the shared `~/bim-ootb` tree.
>
> **▶▶ SESSION HANDOFF 2026-06-06 (close-out) — NEXT SESSION START HERE to close the loop:**
> This session shipped LIVE: `§MOBILE-VIEW` record-list cards (PR#157, v586) + `§MOBILE-LANDING` portrait menu-drawer
> (PR#159, v587); committed the B1 adapter (bim-compiler `c5ba835e`); and TRIAGED+SPEC'd the ERP chrome work with
> both gates DECIDED (PRs #160/#161 merged). Also measured the bloat thesis on the live docker PG: 143 MB Postgres →
> **43 MB SQLite (3.3×)** — see `internal/BLOAT_MEASUREMENT.md` + [[reference_bloat_reduction]].
> **CONTINUE THE CHECKLIST (work-to-zero):** the top open ERP-UI item is the iDempiere chrome below — gates are
> decided, so EXECUTE `prompts/ERP_BOTTOM_BAR_AND_LIFECYCLE.md` **§A → §C → §B** (start §A: registry + ⋯, delete the
> hand-rolled `#idmp-pillrail`). Whitebox §-log on localhost (NOT forced-viewport Playwright — [[feedback_whitebox_not_playwright]]),
> worktree off `origin/main` → PR → sw bump. Then keep going down §OUTSTANDING until every line is ✅/⛔.
> **▶▶ SESSION HANDOFF 2026-06-06b — iDempiere chrome §A–§D DONE, on PR #170 (HELD). NEW SESSION START HERE:**
> - **State:** bim-ootb branch `feat/idmp-pill-registry` pushed → **PR #170 (HELD — do NOT merge until user says "deploy")**,
>   off fresh origin/main, ZERO conflicts (only touches `erp/*` + spec; origin's recent commits are all `viewer/*`).
>   Worktree `/tmp/idmp-chrome`. erp sw **v592**. **12/12 gated witnesses PASS** (whitebox §-log on localhost).
> - **Done §A–§D** (spec `prompts/ERP_BOTTOM_BAR_AND_LIFECYCLE.md`, all 4 sections written): §A bar from shared registry
>   (sibling `pills_idmp.json`+`idmp_pills.js`+PillBuilder, hand-roll `#idmp-pillrail` deleted, `icons.js`+4 verbatim glyphs);
>   §C Install/Migrate pre-client-only (GATE-2); §B cross-tab history scrubber (Glassbowl `#scrub`, dots-only, read-only restore,
>   0 op-log mutations); §D **RED PILL** — "just the pill" (our design, DEFAULT) ⟷ classic iDempiere L&F, key `,` (=BIM Doc Mode),
>   persistent dock (`PillBuilder opts.persistent`), arrow-key record nav.
> - **Deploy = merge PR #170** ONLY on user "deploy" go → then verify erp sw v592 + `idempiere.html` live on Pages.
> - **▶▶ DEPLOYED 2026-06-06 (user said "deploy") — BOTH held PRs MERGED + LIVE-VERIFIED on GH Pages (erp sw v593):**
>   - **PR #171 (rule client-scope) ✅ LIVE** — `64fc284`. Merged first (auto-merge once CI green).
>   - **PR #170 (chrome §A–§D) ✅ LIVE** — `91ebcfd` (sw v592→**v593** after merge-resolution). The merge collided on the
>     concurrent viewer lane (#172/#173 landed mid-deploy, all viewer-only/orthogonal). Resolved per CLAUDE.md: `sw.js` →
>     higher version (v593, kept both changelogs); `idempiere.html` → kept #170's registry script tags + #171's
>     `rule_fold.js?v=2`+`__idmpClient`. Re-synced past #172/#173 (clean erp merge each time), re-ran ALL 6 erp witnesses
>     PASS on the merged tree (§A pills / §B history / §C lifecycle / §D redpill / poc_rule_edit / poc_rule_client_scope),
>     auto-merged. Also updated `poc_rule_client_scope.js` to drive the NEW registry chrome (`#pill-rule` pointerup, open
>     `#idmp-pill` dock). Live-verified: sw v593, `rule_fold.js?v=2`, registry tags, `__idmpClient`, 0 hardcoded-Odoo.
> - **Open items:** (1) ✅ **DONE + LIVE (PR #171, `64fc284`)** — **Odoo-tenant bug** in `erp/rule_fold.js` (hardcoded
>   `AD_Client_ID=12` → Rule pill lied `tenant=Odoo(12) FAIL no-population` on any non-Odoo login). FIXED: fold over the live
>   login client (`window.__idmpClient`, set in `idempiere.html applySession`), honest tenant label + honest-disable on
>   no-population (`§RULE-DISABLE`). Spec `erp/docs/RULE_EDIT_SPEC.md §11`. Whitebox `§RULE-CLIENT-SCOPE PASS` (Odoo regression
>   PASS N=35 + GardenWorld(11) pop=114 + maycomplete honest-disabled); `poc_rule_edit.js` still PASS. (2) Kanban "Odoo marvel"
>   graphic polish (avatars/color tags/group-by) — visual only, backlog, **NOT started — needs design direction** (which
>   avatars / color-tag scheme / group-by field; subjective optics, don't invent — ask the user).
> - **▶▶ NEXT SESSION TOP ITEM (user-dictated 2026-06-07): erp.html init-bubble must be INSTANT — `prompts/ERP_INIT_BUBBLE_INSTANT.md`.**
>   The Phase-1 init bubble (initbubble.json constellation, claimed <300ms) lags ~1s; the 12.7MB ad_seed.db must not block
>   first paint ("sharding that was promised"). MEASURE §BENCH first (head script-wall? SW network-first on .json? Phase-2
>   stealing the paint? bubble size?), then decouple → witness `§INIT-INSTANT-RESULT PASS` (bubblePaint ≤300ms cold+warm,
>   db starts AFTER bubble). Then continue §OUTSTANDING.
>   **✅ DONE + LIVE on GH Pages (sw v599, PRs #188 + #192, 2026-06-07).** MEASURED first: on localhost the bubble already
>   paints 120ms cold / 42ms warm with `dbStartsAfterBubble=Y` — the sharding promise was STRUCTURALLY KEPT (12.7MB db off
>   the paint path, Phase-1 scripts precached/cache-first). The residual ~1s = SW serving the navigation
>   (`erp.html`/`idempiere.html`) **network-first** → every load awaited a network round-trip for the HTML even fully cached.
>   FIX-1 = navigation **stale-while-revalidate** (`erp/sw.js`: `networkFirst`→`staleWhileRevalidate`). Witness
>   `erp/tests/poc_init_instant.js` (injects 800ms nav latency so localhost discriminates): warm bubblePaint **883ms→46ms**.
>   FIX-2 (deploy-freshness backstop) = a one-shot `controllerchange→location.reload()` in both pages — because SWR alone
>   regressed deploy freshness to TWO reloads (old SW serves the nav before the new one activates); witness
>   `erp/tests/poc_init_deploy_fresh.js` proved **TWO→ONE reload** convergence. Regression `poc_mobile_cards` PASS.
>   Live-verified: sw v599 active, backstop in served erp.html+idempiere.html, boot 0 pageErrors, db deferred(network 12.4MB).
>   **⚠ ORPHAN-TRAP HIT (the CLAUDE.md squash+late-push):** #188 auto-merged on its FIRST CI run (SWR only) BEFORE the
>   backstop was pushed → backstop orphaned on the dead branch. Fixed forward via #192 off FRESH origin/main (cherry-picked
>   the orphan, bumped v598→v599). LESSON: a feature with a required follow-up commit → either ONE commit, or disable
>   auto-merge until the last commit is pushed. Don't push to a branch that may auto-merge mid-stream.
> - **✅ DONE + LIVE 2026-06-07 [ERP-UI] Kanban "Odoo-marvel" cards + shared Graph/Kanban status palette (PR #177, sw v596→…);
>   mobile ⋯ pill-reopen fix (PR #176); flat ⋯ kebab all surfaces + mobile dock ⋯ anchor (PR #182, sw v597).** See
>   [[project_kanban_marvel]]. Kanban "group-by" deferred (honest: columns ARE the wfmc group-by/drop-targets). Open papercut
>   CLEARED: the collapsed ⋯ no longer re-centers (anchored right-edge, `§PILL-TRIGGER-RESULT PASS dx=0`).
> - **Standing principles (this arc):** [[feedback_pill_icon_consistency]] — OUR surface = clean Lucide line icons only
>   (icons.js, verbatim panels.js); no unicode/ad-hoc glyphs; reuse pill-registry + settings-editor; common HMI, don't overthink.
>   Tests: whitebox §-log first (NOT forced-viewport Playwright); `§…RESULT PASS` alone can lie — corroborate w/ raw-DOM +
>   baseline diff. Run gated tests by symlinking `~/bim-ootb/tests/node_modules` into the worktree `tests/`.
> - **Shared-tree reconcile** (`~/bim-ootb/prompts/SHARED_TREE_RECONCILE.md`): my row CLAIMED (work safe on PR #170, ~/bim-ootb
>   reset is lossless for me). Do NOT run the `reset --hard`/`git clean` until the ERP + Sidecar sessions also claim. The shared
>   tree's dirty `erp/*` files are NOT mine.

- **✅ DONE + DEPLOYED LIVE (bim-ootb PR #170 `91ebcfd`, erp sw v593, 2026-06-06) [ERP-UI] iDempiere chrome — pill registry + lifecycle + scrubber + red pill (§A–§D).**
  All four sections live-verified on GH Pages; merged after resolving the concurrent viewer-lane churn (#172/#173, orthogonal). Shipped alongside the rule client-scope fix (PR #171 `64fc284`). See the DEPLOYED handoff block at §OUTSTANDING top.
  (history) Built + whitebox §-witnessed on localhost first; was held for deploy-go. bim-ootb branch `feat/idmp-pill-registry`; sw v587→v590→v593.
  - **§A DONE** — iDempiere bottom/side bar now renders from the SHARED registry (sibling `erp/pills_idmp.json` [GATE-1]
    + new `idmp_pills.js` binding fn BY ID to `window.IdmpPillActions` + `PillBuilder`, incl. ⋯ collapse); icons.js
    +barChart/layout/save/pipe (verbatim from panels.js); hand-rolled `#idmp-pillrail` DELETED. Witness
    `erp/tests/poc_idmp_pills.js` → `§IDMP-PILLS source=registry pills=6 handAuthored=0 overflow=⋯` · handRoll gone ·
    iconMiss=none · 0 pageErr · desktop right-strip + mobile bottom-row dock.
  - **§C DONE** — Install/Migrate context-aware lifecycle [GATE-2: HIDE in-client]: shown only pre-client (login/tenant
    picker), hidden once a client is committed. Witness `erp/tests/poc_idmp_lifecycle.js` →
    `§IDMP-LIFECYCLE stage=pre-client install=Y migrate=Y` → login → `stage=in-client install=context migrate=context`
    (posted/graph/kanban/rule kept both stages) · 0 pageErr.
  - **§B DONE** — cross-tab history scrubber (Glassbowl `#scrub` pattern) in `idmp_history.js`: records {window/tab/record}
    moments across `#idmp-wintabs`; double-tap blooms labelled chips (real fields); dot click = READ-ONLY restore (never
    mutates op-log). Determinism: monotonic seq + performance.now() only. Witness `erp/tests/poc_idmp_history.js` →
    4 moments incl. true cross-tab (2 tabs) + `push=record:'Tree GardenWorld ElementValue (...)'` · bloom · restore
    readOnly=Y · kernelMutations=0 · 0 pageErr.
  - **NEXT = deploy-go**: on user GO → push `feat/idmp-pill-registry` → PR off `origin/main` → CI → squash-merge →
    verify sw v590 live. (Spec `prompts/ERP_BOTTOM_BAR_AND_LIFECYCLE.md`, bim-ootb.)
- **✅ DONE + LIVE (§MOBILE-LANDING, bim-ootb PR #159, sw v587, 2026-06-06) [ERP-UI] Mobile main-page (portrait).**
  Post-login the phone landed on an empty desktop canvas ("Select a menu item") with the menu hidden behind the ☰
  burger. Now `@media≤760px` AUTO-OPENS the existing menu drawer on the empty landing (and on returning to it after
  closing the last window) + a tap-to-close dim backdrop; closes on menu-item/backdrop tap; desktop unchanged.
  Witness (localhost, iPhone emulation, whitebox — NOT forced-viewport): `§MOBILE-LANDING drawer=open
  reason=empty-landing innerW=390` · menuOpen=Y backdropShown=Y treeRows=547 · backdrop-tap→close · 0 pageErrors.
  Live-verified `erp/sw.js`=v587. (Landscape already worked; this is the portrait upgrade the user asked for.)
- **✅ DONE + LIVE (§MOBILE-VIEW PASS, bim-ootb PR #157, sw v586, 2026-06-06) [ERP-UI] Mobile card record-list.**
  At `@media(max-width:760px)` `buildGrid()` now renders the record list as `.idmp-cards` `.acc` cards (one record =
  stacked label:value, reusing the existing `.acc/.hd/.bd` idiom + `_displayFields/fmt/recVal` — NON-INVENT) INSTEAD
  of the `<table>`; the desktop `<table class="idmp-grid">` path is UNCHANGED (still shown ≥761px); the `#idmp-pillrail`
  re-docks to a BOTTOM bar so it stops covering rows. Witness `erp/tests/poc_mobile_cards.js` → `§MOBILE-VIEW cards=35
  tableHidden@≤760=Y cardsShown@≤760=Y pillRail=bottom@≤760:Y desktopTable=Y` (0 pageErrors @390px AND @1280px) +
  before/after/desktop screenshots. Built in an isolated worktree off fresh `origin/main`; merged; Pages built `fd690d0`;
  live-verified: `erp/sw.js`=v586, live `idempiere.html` carries `idmp-cards`. `erp/sw.js` v585→v586 (no new runtime asset).
  - *(superseded original task note)* The record LIST used to render as a DESKTOP multi-column data-grid squeezed onto the
  phone (witnessed 2026-06-06 at 390px: side-scrolling spreadsheet, Name column off-screen, pill rail overlaps rows;
  `§MOBILE-ERP horizOverflow=N` only meant it scrolled IN-container — NOT that it was mobile). Spec = "Mobile cards (reuse `ad_ui .acc`)".
  **⚠ DO NOT OVERWRITE the done work:** (1) `§MOBILE-GRIDFIT` (bim-ootb PR#125 — `#idmp-main{min-width:0}` so the grid
  scrolls inside `#idmp-content`; the `@media(max-width:760px)` drawer menu); (2) the `.acc` accordion COMPONENT already
  exists in `ad_ui.js` (table-overlay, full styling + open/close, lines ~551–566 & ~1159) — REUSE it, don't rebuild;
  (3) Accts-Posted lens already has a mobile `mountAccordion` precedent (`§POSTED-MOBILE`, `prompts/ACCTS_POSTED_PANEL.md`).
  **BUILD:** at `@media ≤760px` render the record list as `.acc` cards (one record = stacked label:value) INSTEAD of the
  `<table>`; single-column forms; horizontally-scrollable tabs; dock the pill rail as a bottom bar so it stops covering
  records. Keep DESKTOP exactly as-is (the grid is correct there). Witness `tests/poc_mobile_cards.js` →
  `§MOBILE-VIEW cards=N table-hidden@≤760=Y desktop=table` + before/after 390px screenshots; 390px pass BEFORE deploy.
  Ship via a bim-ootb worktree off **fresh origin/main** (sw bump; `idempiere.html`/`ad_ui.js`).
- **SESSION STATE 2026-06-06 (migration arc, this session):** **P0 ✅** Migrate▸Odoo staged box + self-sufficient
  `odoo_agent.zip` bundle (bim-ootb PR#154 merged, sw v584). **P2 ✅** INSTALL persists the merged tenant — Odoo
  **Client 12 now `resident=Y`** (survives reload, no `?shard=`; bim-ootb PR#156 merged, sw v585; witnesses
  `tests/poc_client12_resident.js §C12-RESIDENT resident=Y` + `tests/poc_odoo_records_show.js §ODOO-RECORDS showable=Y`).
  **SAP target decided = B1 (Business One), NOT S/4** (SMB market fit; `OJDT/JDT1` clean double-entry; Service-Layer HTTP
  extraction = same agent as Odoo). **B1 adapter built + folds** (mock): `scripts/b1_adapter.js` + `scripts/poc_b1_fold.js`
  + `build/erp/b1_oracle.template.json` → adapter+runner NON-INVENT gated (`§B1-FOLD BLOCKED` until a real Service-Layer
  export; folds clean on a mock) — **COMMITTED 2026-06-06 `c5ba835e`** on `feat/revit-plus-lens`, with the
  `poc_sap_flight_fold.js` per-event-register clobber bug-fix folded in.
  Real B1/SAP folds stay gated on a real export (non-invent). iDempiere import already exists (`migrate_pg_to_sqlite.js`/
  `migrate_agent.js`, spec'd 2026-06-02) — GW pull is its default. See `prompts/MIGRATE_INSTALL_TENANT.md` (P0/P2 done,
  P1-iDempiere & P3-switcher open) + [[project_migrate_erp_picker]].
- **✅ DONE (§PILL-REGISTRY PASS, 2026-06-04) [ERP-UI] Remove the two redundant buttons at the TOP of `erp.html`.**
  THE TWO (user-confirmed) = the **🫧 Glassbowl + ✦ Gravity** companion links rendered OUTSIDE the pill rail in the
  graph-view HUD (`ad_ui.js` `gbHud`, top-right) — they DUPLICATED the glassbowl/gravity pills in the rail. Principle
  the user set: **no controls outside the pill; use the BIM-OOTB registry concept** (`pills.json` + `erp_pills.js`).
  Fix (all in the pill, none outside): removed `gbHud` (`§AD_UI gbHud-removed`); the non-duplicate 📖 Read (ERP-doc)
  link moved INTO the registry as `id=erpdoc` (doc glyph, nav). Also folded in the other free-floating controls found:
  the **System/GardenWorld** client switcher (`ad_ui.js` showMenu — redundant w/ swipe `_switchClient`+toast, removed)
  and the bottom-left **⛓ Verify-ledger** button → registry `id=verify` (checkList glyph copied verbatim from
  `viewer/panels.js`, fn=`window.ErpVerifyLedger`). Witness: real-browser `tests/poc_pill_registry.js` →
  `§PILL-REGISTRY PASS` (pill-verify+pill-erpdoc mounted, floating button gone, 0 System/GW buttons, 0 HUD links,
  0 icon-miss, 0 pageerror, 16 pills handAuthored=0) + screenshot `tests/pill_registry.png`.
  **DEPLOYED LIVE 2026-06-04** (user POC standing-GO): bim-ootb PR #113 squash-merged to main (CI green),
  Pages built `96d65b31`, SW v567 + `?v=23`. Live-smoke on the real URL: 16 pills, verify+erpdoc, 0 floating,
  0 HUD, 0 pageerr (`tests/live_smoke.png`).
  - **✅ DONE + DEPLOYED LIVE (§AD_UI hud-dedup, bim-ootb PR #134, 2026-06-05) — outside-pill HUD icons removed.** User
    dictated the consolidation ("remove them, Pill has it already") = the go-ahead this item was gated on. Removed the graph
    HUD's 🔍 search-overlay + ⛶ globe maximize (`ad_ui.js` searchBtn deleted; fsBtn kept DEFINED for `_resizeGraph`'s
    auto-maximize but no longer appended). Both already in the pill rail (`erp_pills.js` find→search, maximize→fullscreen).
    sw.js v578→v579. Live-verified on real URL: searchBtn count=0, witness `§AD_UI hud-dedup removed=[maximize,search]` present.
  - **idempiere.html top bar = ALREADY DONE (not part of this task):** the 🔴 Red-Pill 3-state hides the classic
    `#idmp-toolbar` in expanded/clean (witness `§REDPILL state=… barHidden=Y`, idempiere.html:63/1296/1322). Leave it.
- **✅ DONE + DEPLOYED LIVE (§MOBILE-GRIDFIT PASS, bim-ootb PR #125, 2026-06-05) [ERP-UI] Mobile UI.** Earlier the
  `§MOB-TOPBAR-RESULT PASS` was a NARROW over-claim (breadcrumb + pill rail only; never measured content). Re-check
  at 390px exposed the real gap: `#idmp-main` was **2145px** wide (the AD grid never constrained to the viewport) so
  `body{overflow:hidden}` clipped the right grid columns + the header role/home/help buttons — unreachable on touch.
  FIX: `#idmp-main { min-width:0 }` → the grid scrolls INSIDE `#idmp-content` (overflow:auto), not by pushing the
  page; `@media(max-width:760px)` hides the duplicate header breadcrumb (`#idmp-ctx`) so the buttons fit. Witness
  `tests/poc_mobile_gridfit.js` → **§MOBILE-GRIDFIT PASS** (docSW 2145→390, no page overflow, grid scrolls
  in-container, switch/home/help on-screen, 0 pageerror; picker+heatmap still green, no desktop regression). sw v575.
  **Lesson logged:** new UI must get a 390px pass BEFORE deploy; a passing witness only proves what it measured.
  ([[feedback_mobile_events]] · [[feedback_log_not_visual_proof]] · [[feedback_whitebox_before_deploy]])
- **✅ DONE (§SHARE-ROUNDTRIP PASS, 2026-06-04) [ERP-UI] Share icon in the pill** — captures AND restores full
  context. The pill copied a bare href (recipient landed on the home globe); now `ADUI.buildShareUrl()` emits the
  SAME deep-link params erp.html restores on load (`?client=&window=&record=` — capture mirrors restore, non-invent).
  `share` fn → `navigator.share`(mobile)/clipboard(desktop). Fixed a latent restore TIMING bug (deep-link ran before
  hydrate → moved into `_waitAndHydrate`). Spec `bim-ootb/erp/docs/ERP_SHARE_SPEC.md`. Witness
  `tests/poc_share_roundtrip.js`: sender opens window 123 → `?client=gardenworld&window=123` → fresh load restores
  same window (screen=window), 0 pageerr + `share_restore.png` (record-level wired via navToRecord; seed metadata-only
  so unwitnessed — honest). **DEPLOYED LIVE**: bim-ootb PR #114 squash-merged (CI green), Pages `4c4a20e`, SW v568 +
  `?v=24`, fetch-back-verified. (`project_share_sheet`)
- **⛔ BLOCKED: start renderer #2 (Odoo) now? [ERP-UI] `idempiere.html` descriptor-driven** — AD as first descriptor,
  not hardcoded (renderer #2 reuse). **Recorded decision** (`project_idempiere_renderer` / `docs/IDEMPIERE_2.md`
  §pivot): *"I1 calls ADParser directly; build the descriptor seam WHEN renderer #2 starts."* Renderer #2 hasn't
  started, so doing it now = speculative one-consumer abstraction the decision pre-empts. Unblocks the moment a 2nd
  renderer (Odoo/ERPNext) is greenlit — that's the trigger, a roadmap call only the user owns. (`project_idempiere_renderer`)
- **✅ DONE (already wired + LIVE; verified 2026-06-04) [ERP-UI] Glassbowl Process button** — the "dry-run, NOT wired"
  state was STALE. GP1·GP2·GP3 + E2E landed 2026-06-01 (commit `c5dbbfc2`, glassbowl sw v7): `crud_overlay.js`
  `applyOp` DOC_ACTION → `commitProcess(op)` = REAL signed write (`buildDocActionGroup`→`KernelOps.commitGroup`,
  sidecar log / read-the-tip), dry-run only as a no-kernel FALLBACK. CRUD create/update/delete stay dry-run (T3-gated,
  separate). Witnesses (memory): W-HELP-COACH 21 · W-HELP-NEXTGATE 11 · W-CRUD-WRITELOOP-OVERLAY 12 ·
  W-GUIDE-PROCESS-E2E 14. **Verified live now:** `curl BIMCompiler/crud_overlay.js` → GP3 real-write + commit fns
  present. Remaining is GP4 (ProcessBatch/Gravity — 3rd overlay consumer) + History-view UI + error_reporter
  factor-out = ENHANCEMENTS, not the stated dry-run gap. (`project_glassbowl`)
- **✅ R4 DONE + DEPLOYED LIVE (§RPT-OUT-R4 PASS, 2026-06-04) [ERP-UI] After-the-receipt output** — the receipt panel
  (`report_overlay.js`, glassbowl) was view-only (✕ only). **R4** adds Print / Share / Save — edge-only, server-free,
  each serializing the SAME folded rec (no re-query, non-invent): Print=print-iframe of `receiptHtml(rec)`;
  Share=`navigator.share({files:[html]})`→`share({text})`→clipboard; Save=`Blob`→`receipt_<doc>.html` download.
  `§RPT-OUT` per action. Witness `build/erp/poc_rpt_out.js` → 3 buttons, genuine fold (foldReceipt c_order
  150/12/162 BigDecimal), Save download fires, Share called, Print iframe (+`rpt_out.png`). **DEPLOYED**: bim-compiler
  `full` (compile gate ✓) + `mkdocs gh-deploy` to BIMCompiler gh-pages (worktree-isolated off `full`, NOT the dirty
  shared tree), glassbowl **sw v8→v9**, fetch-back-verified live. **R5 (channel-deliver signed receipt, `§RPT-SEND`)
  still open.** NOTE separate concern: "Completed" ≠ books moved — posting set delegated install-side (§I-K/§13.6),
  `commitProcess` flips status only. (`project_glassbowl` · `project_share_sheet`)
  - **✅ R5 DONE + DEPLOYED LIVE (§RPT-SEND PASS, 2026-06-05) — the signed, self-verifying receipt.** Share/Save
    now deliver a SIGNED receipt (`.erpreceipt.json` embedded in self-contained HTML) carrying the op-chain
    (canonical `id|ts|op_type|parameters|input_guids|output_guid`, `op_hash=SHA-256(prev|canonical)`, sig over
    op_hash) + an inline self-verifier + a Verify affordance — the recipient replays + checks the chain with NO
    server. Witness `build/erp/poc_rpt_send.js` → **§RPT-SEND PASS**: payload signed, verify chainOk, money==golden
    (BigDecimal), and **tamper-evidence proven** (flip an op param → FAILS at that op; flip a sig byte → FAILS;
    forge the displayed total → `recBoundOk=false`); verifies from JSON AND embedded-HTML alone. HONESTY: attests
    "the recorded, signed op-chain (tamper-evident) — NOT a GL posting" (§I-K/§13.6). Built worktree-isolated off
    `full` (the real R4 baseline; shared dirty tree untouched). **DEPLOYED**: mkdocs gh-deploy → BIMCompiler
    gh-pages, glassbowl **sw v9→v10**, fetch-back byte-identical (report_overlay.js 39543 B). Source on
    `origin/feat/r5-rpt-send` (off `full`) — PR it to `full` when ready. (`project_glassbowl`)
- **✅ DONE + DEPLOYED LIVE (§ERP-PICKER PASS + §HEATMAP PASS, 2026-06-04) [ERP-UI/engine] Install + Migrate = the
  pick-your-ERP dialog.** Stubs replaced by `bim-ootb/erp/erp_picker.js` (`window.ErpPicker.open({mode})`), wired to
  BOTH pills. Lists all 5 (iDempiere · Odoo · SAP · Oracle · MS Dynamics) always; **live-detects Odoo** (`:8069`
  no-cors liveness only — never reads data cross-origin), highlights detected, greys coming, defaults + asks
  *"migrate your <X>?"*. Routes: iDempiere→`MigrateShowMe` · Odoo→delegate-to-install fold · others→honest "coming".
  **Odoo real fold:** `scripts/odoo_agent.js` (install-side, live-pulls odoodemo SO S00023 → `odoo_chain.json`) → the
  browser RE-FOLDS each hop through `window.ERPKernel` + the carried wfmc (`§ODOO-MIGRATE-BROWSER mapped=5/5 newVerbs=[]
  glDr==glCr chainOk=Y`). Witness `tests/poc_erp_picker.js` → **§ERP-PICKER PASS** (allFive, Odoo detected, route=odoo,
  0 pageerr). **DEPLOYED**: bim-ootb PR #123 squash-merged (CI green), SW v573 + erp_picker.js precached.
  **Also DONE — heat-map fallback** when a window has no docstatus pivot: `_bestPivot()` picks a lookup column
  (FK/list/yesno or `*_ID`), Kanban renders a heat map (tiles ∝ real counts) instead of an empty board. Witness
  `tests/poc_heatmap.js` → **§HEATMAP PASS** (win=140 Product→`by=M_Product_Category_ID` 13 cells; win=167
  C_Invoice→board). **DEPLOYED**: bim-ootb PR #124 squash-merged (CI green), SW v574. (`project_migrate_erp_picker`)
  - **REMAINING (own lanes, not blocking):** commit `scripts/poc_odoo_fold_live.js`+`scripts/odoo_agent.js` on the
    bim-compiler **engine lane** (still on dirty `feat/revit-plus-lens`); SAP/Oracle/Dynamics adapters when greenlit.
- **✅ DONE + LIVE (bim-ootb, sw v626, 2026-06-08) [BIM-viewer] Viewer reliability batch — 3 fixes shipped, rest of the stale 2D list triaged.** (`project_2d_regression`)
  - **Blank-screen-on-idle** (PR #191, sw v623) — the §IDLE-PARK self-park loop blanked on a resize while parked (`setSize` clears the buffer, no re-render). `_onResize`/grid_views ortho handler now `markDirty()` → one frame then re-park; idle CPU savings intact. Witness `tests/probe_idle_blank.js` (`resize→markDirty: 0→2`).
  - **Kernel-op edits survive reload** (PR #202, sw v626) — `kernel_ops._persistToIdb` opened `bim_ootb_cache` at **v1** vs scene.js's **v2** → VersionError, `§KRN_PERSIST` never fired, edits lost on refresh (S243 §3.7 was dead). Now persists via `APP.openCacheDB` (v2). Witness `tests/probe_krn_persist.js` (marker survives reload 0→1).
  - **2D saved-card delete durable** (coupled w/ the above — re-enabling persistence would have resurrected it). `loadSavedSections` now treats **localStorage as authoritative** for cards (reconciles DB to it); `saveSectionToDb` clears ls before reload. Witness `tests/probe_2d_delete.js` (delete sticks in-session / reopen / reload / delete-ALL, even with `§W2 flush=true`). The original "only removes DOM" diagnosis was STALE.
  - **Triage of the 28-day-old 2D-regression list (memory `project_2d_regression`):** #2 furniture→slab mispick = **already fixed** (floor view hides 3D, furniture has pickable contours → `§PICK_2D class=IfcFurnishingElement`; witness `tests/probe_2d_furnpick.js`); #3 cards-delete = fixed (above); #1 wall-contour-offset = **⛔ BLOCKED on repro** (affected bldg `Ifc4_SampleHouse` only exists as a reference-rosetta extraction the viewer can't load; on canonical bldgs wall-vs-gridline offset is an INVALID metric — grids are bay lines, not per-wall centerlines); #4 opening-dims = a FEATURE add (conflicts w/ "no new features"); #5 faint door arcs = NOT low-cost (`grid_door_arcs.js:262` `LineBasicMaterial.linewidth` is ignored by WebGL → needs `Line2`/`LineMaterial`); #6 grid bubbles/dim text = likely already-addressed (white bubbles + bold 26px). **SampleCastle 2D verified intact** (storeys 3621/3621 survive the compile/SC-BOM-seed; 19 grid lines, 810 contours render); it is NOT in canonical `bim-ootb/buildings` — load the bim-compiler copy to test.
  - **NEXT low-cost candidates (when someone returns to 2D):** #5 fat door arcs via `Line2` (visual, needs eye); #6 quick visual confirm; #1 needs a viewer-format SampleHouse OR a named off-grid wall.
- **[BIM-viewer → STALE 2026-06-14: these predate current bim-ootb viewer; verify before acting] Time Machine Gantt** · Grid UX debt · 4D capture · Ground+Sky · Settings JSON editor
- **[BIM-viewer → STALE 2026-06-14 · NOT vital] Pill-registry drift — latest icons hand-roll DOM instead of going through the registry.** The S281/S282 registry (`_actions` in `viewer/panels.js` + `ICONS` table + `PillBuilder`/`pill_builder.js`) is the single source for toolbar icons. Two recent areas skip it:
  - **Find-panel axis pills + lenses** (`viewer/navigate_find.js`, commit `743ac35`): `_renderAxes` (~:366) builds `<button>`s with hardcoded inline `cssText`; `_micSvg`/`_searchSvg` (~:132) re-declare SVGs the `ICONS` table already owns (`_searchSvg` ≡ `ICONS.search`). In-panel controls being local DOM is fine; the fix-worthy drift is the duplicated SVGs (fold into `ICONS`, add a `mic` entry) and the inline styling (lift to a CSS class — today `corporate.json` theming can't reach them).
  - **Precision/Reset/Pivot cluster** (`viewer/precision_cam.js`, the "feather · reset · pivot" row): a full PARALLEL implementation — `precision-btn` self-injected into the toolbar (~:259-271) + `prec-reset-chip`/`prec-pivot-chip` built in `revealPrecisionReset` (~:300-336), all raw DOM + inline `cssText`. The registry carries duplicate stubs (`precision` in-pill; `cam-reset`/`cam-pivot` `pill:false` — the `pill:false` exists only to stop a 2nd copy painting). Feather + reset SVGs are duplicated across `panels.js` and `precision_cam.js`. Possible double feather (`precision-btn` vs `pill-precision`) — verify visually. Clean target: delete the self-built DOM, let the registry render all three via the standard `_revealChip` (`pill_builder.js:116`), move SVGs into `ICONS`. (`project_s281_pill_registry` · `project_precision_pivot`)

## 1. DONE + FROZEN — consume, do NOT rebuild
- **Engine seam (C0):** `bim-compiler/scripts/erp_seam.js` `makeSeam→{read,dispatch,manifest,verbs,verify}`; `dispatch(intent,ctx)`
  gates role+owner engine-side; `verify→{chainOk,len,tip}`. `poc_seam.js` ALL PASS. Browser UMD `window.ERP` published by the
  reference spike `bim-ootb/erp/spike_writepath.html` (signed chain `chainOk=Y`, gate zero-leak). (`fad5b096`)
- **readPostings (§13.7):** `erp_postings.js` → `{visible,posted,lines,balanced,source,coverage,note,reason}`, role-gated by
  `isshowacct`; honest degrade `absent→partial→complete`. `poc_postings.js` ALL PASS.
- **Data:** 15 closed D2 shards + `manifest.json` (`§SHARD-MANIFEST tables=660`) + real `fact_acct` (`Dr=Cr=46574.97`). (`a541a873`,`30a1e1a6`)
- **MIGRATE backing:** `scripts/odoo_adapter.js` + `poc_odoo_fold*.js` → `§ODOO-FOLD PASS newVerbs=[]` (each foreign hop = one `dispatch`).
- **Tour (read-only, bound):** `help_overlay.js`/`help_idmp.js` `forked=0`, `W-TOUR-BIND 11/11`, suite green. ShowMe drives real
  `IdmpHost.focus→openWindow` (#80001); NeedHelp? gated on real `[data-ad-table]`.
- **AD-gen STRUCTURE (mine, this arc, on `full` `8abed18c`+`8f6071c9`):** `scripts/gen_ad.js`+`error_report.js`. Fold any source's
  dictionary → AD seed the renderer draws with ZERO renderer change. Providers `fromSqlite`(deterministic) + `fromExcel`(majority-infer);
  `ErrorReport` traps rubbish (import goes through); positive role-id (entity BPartner/Products/Orders + identifier+amounts+key); line→header
  FK nest (L0/L1); render-contract + session tables match `ad_parser.js`+`idmp_session.js` EXACTLY. Headless **`§RENDER-SIM ALL-CLEAN=Y`**.
  Seeds in `deploy/dev/`: `sap_ad_seed.db`(14/90, full scaffold) · `odoo_ad_seed.db`(8/8, cols=0 gap) · `glassbowl_ad_seed.db`(13/721,
  richest — regenerated WITH session tables) · `sampleerp_ad_seed.db`(Excel 4/20). `idempiere.html?seed=` loader wired (UNCOMMITTED, bim-ootb).

## 2. THE WORK — bounded, agent-assignable items (next session sequences + fans these out)
> **THE DESTINATION REACHED (2026-06-11):** the write-path rails this section built now carry their first
> addon — the **POS lens** (`docs/POS_ADDON_SPEC.md` §P-1..§P-4, `prompts/POS_LENS_SESSION.md # DONE`):
> ring → ONE signed group (order+ship+invoice+backflush, WR from the dictionary) → replenishment fold.
> W-POS-* ×4 headless + W-POS-LIVE green; newVerbs=[]; **DEPLOYED 2026-06-12 (PR #269 sw v652, Pages
> live-verified — `prompts/POS_LENS_SESSION.md ## DEPLOY DONE`)**.
**Chosen first (user):** fold A+B1+F into ONE bim-ootb deploy PR off `origin/main`. Engine-lane order for the write path: C → D → B2.

| ID | Item | Files (edit-only) | Witness | Depends on | Parallel? |
|----|------|-------------------|---------|-----------|-----------|
| **A** | Ship AD-gen RENDER | `bim-ootb/erp/idempiere.html` (`?seed=`) + ship a demo seed | `§AD-RENDER … menu nodes=N windows openable=N` + `§AD-RENDER VBAK fields==ad_field count` | — | yes (isolated render path) |
| **B1** | INSTALL icon | pill registry (`erp_pills.js`/`pill_builder.js`) + `migrate_showme.js` | `§INSTALL-PILL opens=dialog` | install-tier §3.3 | yes |
| **B2** | MIGRATE icon | new migrate chrome → `odoo_adapter` fold → `window.ERP.dispatch` | `§MIGRATE source=odoo hops=N newVerbs=[]` | D, I-4 §3.1 | after D |
| **C** | Accts-Posted panel | new panel + `buildCtx()` over `readPostings` | `§POSTED-READ`/`-GATE` rendered verbatim | — (read-only) | yes (decision-free, ship FIRST) |
| **D** | Wire `window.ERP` into chrome | `kanban_lens` drag→dispatch · `idempiere` record-panel · `chat_lens` send · `buildCtx` (augment `idmp_session`) | `§WRITE dispatch→refold chainOk=Y` + `§METER` | I-4 §3.1 | after I-4 decided |
| **E** | Re-fold seam | the host's post-dispatch re-derive | `§REFOLD view=… ms=…` | D | after D |
| **F** | Remove stale icons | main viewer (`deploy/dev/index.html` — glassbowl/gravity) | `§ICONS removed=[…] pill-covers=Y` | — | yes (isolated file) |
| **G** | DataSource (optional) | serve D2 shards behind `read` on window-open | `§DATASOURCE tier=shard swap=Y` | — | yes |
| **H** | Odoo master extractor | `bim-compiler/scripts/migrate_odoo_to_sqlite` (allowlist+AD-key map) | `§MIGRATE-ODOO-MASTERS fabricated=0` | — | yes (bim-compiler) |

**Demo-source strategy (A):** prove §AD-RENDER on `sap_ad_seed.db`/`odoo_ad_seed.db` (full scaffold, known-good). For the data-rich
front-door demo use **`glassbowl_ad_seed.db`** — iDempiere's own order→invoice→payment data, the one source we own STRUCTURE *and* DATA for.
SAP = structure-only with honest empty grids = the "and it generalizes" reach claim, not the front door.

## 3. DECISIONS I OWN (make BEFORE the dependent build; don't guess)
1. **[I-4] op-log schema** — live `erp_kernel.kernel_ops`(`op_uuid` PK) ≠ signed `kernel_ops.js`(`id/prev_hash/op_hash/sig`). Reconcile to
   ONE schema **before** wiring signing into the live path (engine lane: *"first decision, not cleanup; signed-over-the-wrong-table is worse than unsigned"*). Blocks D, B2.
2. **Persist** — per-write (simple, O(n²) seal, fine at hundreds) vs batch/compact (needs I-4). Lean: per-write now, resolve I-4 before claiming signed, defer perf backlog to thousands.
3. **★ Install-icon TIER** — does INSTALL launch **MigrateShowMe (master-data ONLY)** or a **unified full-install**? Tiers: master browse
   (MigrateShowMe) · `coverage:complete` (S1 Fact_Acct §13.6 cent-gated) · full AD metadata (shard streaming) · full editing (T3). Sets B1 copy
   AND unblocks the Tour pointer (owed-back). Don't over-promise a tier the icon doesn't deliver.

## 4. INVARIANTS — don't break through UI finishing
- **Tour A1–A4:** keep `window.IdmpHost` (5 methods) · **keep render-path `data-ad-table/record` tagging** (⚠ the one real render-rewrite risk —
  drop it → badges go SILENT, no error; guard with a `§`-assert `[data-ad-table]` count>0 after render) · keep `#idmp-content` mount · keep keymap window names matching AD menu.
- **Column casing bites:** sql.js/better-sqlite3 return DECLARED case — **alias every read column** (`SELECT grandtotal AS grandtotal`) or `undefined→NaN→silent unbalanced POST`.
- **readPostings honesty is engine-enforced** — render `source`/`coverage` verbatim; never gate the Posted tab; INSTALL/MIGRATE lift it.
- **Determinism** — no `Date.now`/`Math.random` in op paths; `performance.now()` only for `§METER`/`§BLOAT`.

## 5. OWED BACK to the Tour lane
1. Install-icon tier answer (§3.3) → sets Tour pointer copy. 2. Live-browser screenshot of NeedHelp? lit (I have Playwright, Tour doesn't). 3. Ping if UI finishing touches A1–A4.

## 6. KNOWN ISSUES (spike-measured, N=300; non-invent)
I-1 dispatch double-hashes/write (drift 1.57×)→incremental hash · I-2 seal+verify re-hash whole log/persist→O(n²), signed verify 4.6→26.6ms→rolling seal ·
I-3 projection bloat (52→336KB/600 ops, full re-export/write)→compact/prune · I-4 schema mismatch (§3.1) · I-5 re-fold full GROUP BY (watch 10k+).
~500 op/s, comfy at hundreds. Re-measure `scripts/spike_writepath.js [N]`.

## 7. DEPLOY + STATE
- **Deploy = PR to bim-ootb protected `main`** (Pages only from main; CI~95s+review+~60s rebuild). **Branch off `origin/main` BEFORE editing**
  ([[feedback_gh_deploy_base]] — currently on `idmp-host-conformance`, WRONG base). Bump `erp/sw.js` CACHE_VERSION (now **v564**) + `?v=` in sync; PRECACHE the seed. EXPLICIT GO.
- bim-compiler `full`: AD-gen `8abed18c`,`8f6071c9`; engine `fad5b096`,`a541a873`,`30a1e1a6`. Seeds in `deploy/dev/`.
- bim-ootb `idmp-host-conformance` (LOCAL): `idempiere.html` `?seed=` MODIFIED-uncommitted (move to fresh branch); `spike_writepath.html` `09773e1` not pushed.

## 8. ▶ AGENT ORGANISATION (next session)
Fan out from §2 as **worktree-isolated agents**, each owning ONE item, editing ONLY its files, integrating by **key + seam + §-witness** (never co-edit).
- **Round 1 (parallel, no blockers):** C (Accts-Posted) · F (icon cleanup) · A (render) · H (Odoo extractor). Each independently witnessable, no deploy.
- **Gate:** decide §3 (I-4 · persist · install-tier) BEFORE round 2.
- **Round 2 (after gate):** D (wire `window.ERP`) → E (re-fold) → B2 (MIGRATE). B1 (INSTALL) once tier is decided.
- **Agent firewall:** consume `window.ERP`, NEVER fork a verb (re-copy UMD from `bim-compiler/scripts/`) · NEVER edit Tour chrome (`help_*`) or drop `data-ad-table` tagging · alias every read column · §-log first · NO deploy (EXPLICIT GO) · a missing verb = a NAMED finding back to the frozen engine, not a UI hack.
- **Deploy = ONE bundled PR** off `origin/main` (fold A+B1+F + sw bump), after their §-witnesses are green.
