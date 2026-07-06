# ⚠ DO NOT REMOVE — GRAND LANE STRATEGY (the organizing spine for all ERP/Viewer lanes)
# This file is the SINGLE index + doctrine. Lanes are tributaries of it, not competing top-level cards.
# Resume model: one bounded leg per session, R→E→V, until the ERP-critic journey is complete.
# Authored 2026-06-15 (consolidation session). Supersedes the flat 156-prompt sprawl.

## 0. THE DOCTRINE (locked — the architecture every ERP-UI session obeys)
**⚖ FUNDAMENTAL LAW (user decree 2026-06-18) — the iDempiere surface is INDISTINGUISHABLE from real iDempiere:
zero learning curve, that is the wow factor.** Every chrome element a real iDempiere user knows stays exactly
where iDempiere puts it — the classic ADWindow toolbar IS the CRUD surface (New/Copy/Save/Save&New/Delete/Ignore
+ Process ▶), the header carries only iDempiere-native bits (menu · logo · search · role/client ctx · role-switch/
logout), folder/box tabs, no tree glyphs, ONE toolbar (no duplicate inline verb bar). **Anything NOT in iDempiere
goes to the pill rail (the `⋯` extras launcher) — never the iDempiere chrome:** Share, engine/Home view, graph,
kanban, POS, Ninja, plugin, audio, history. The Blue-Future band + the history scrubber are HIDDEN by default and
appear ONLY when History is pressed. Test every surface against the real product; if it adds a learning curve, it
is wrong. (Shipped Leg 1: PR after #365, W-CLASSIC-CHROME — red pill + "just the pill" clean mode retired, form-CRUD
pills removed, Share/Home → pills, tabs restyled, bottom bars opt-in.)
**Separation of surface, ONE shared truth.**
- The **visual CRUD ring** (ring-of-fire) is **Glass/Gravity ONLY**. iDempiere NEVER opens it.
- **iDempiere keeps its own UI convention** — DocAction bar, Process ▶ pill, grid gear-batch, form/grid.
- All surfaces **SHARE the underlying signed-commit engine** (`crud_overlay.commitProcess` → `completeFanout`
  [`ERPEngine.completeOrder`, oracle-equivalent W-FOLD-COMPLETE] → signed `commitGroup` → persist). The UI differs;
  the write path + the fold are one. Never fork a verb; never re-implement a commit per surface.
- **The correctness oracle is the REAL iDempiere**: source `~/idempiere-dev-setup/idempiere`, db `idempiere_test`
  (ORACLE, fact_acct populated). Every document fold is diffed **to the cent** vs that db — see
  `docs/ERP_MODEL_ARCHETYPE.md` (MOrder archetype) + `docs/ERP_COVERAGE_MATRIX.md` (the honest scoreboard).
- NON-INVENT, §-log first, witness-led. A gap is a gap (🟡/⛔), never papered or hyped.
- **(2026-06-16 — the engine north star, user decision, "Holy Grail"):** the engine IS the product. It folds the
  **declarative** model (AD canonical — user comes from Compiere/iDempiere, fine to absorb) and **exposes every rule
  for users to FIND & RESOLVE**; it does NOT absorb foreign **imperative** code (Odoo Python / ABAP / Java bodies) —
  that path is conflict + chaos, explicitly OUT (no codebase impact by construction). **The common automation law:**
  re-file every ERP rule by its **op-log effect**, not its source binding — **DERIVE** (`out ← f(in)`, emits no op) ·
  **VALIDATE** (`assert(predicate)`, gates a commit, no op) · **ACT** (emits signed ops). Store derives in **dependency
  form** (Odoo's declaration + iDempiere's field-change firing → deterministic topological replay, better for the log).
  Genuinely-mixed remainder → user **plugin** (`.foldbundle` JS), never an auto-import. **Op-only invariant:** adopted
  automation may ONLY emit signed ops, never hidden mutations. **Future seam (forethought, don't build):** a 4th
  `behavior` ErpDescriptor facet so each ERP's automation maps through one contract like rendering does — don't bury
  AD-only callout assumptions deeper. The "Holy Grail" = **solid engine + transparent rules + no chaos** = substrate /
  delivery, NOT feature parity. (Op-log = git-for-data: edit = append a signed op, state folds anew, past kept;
  maintainable = replay/branch/reverse, full provenance.)

## 1. THE RHYTHM — every session runs R → E → V
- **R — Review** the leg's iDempiere convention against the oracle (source/db). Establish what "correct" means BEFORE code.
- **E — Execute**: iDempiere UI surface + the SHARED underlying seam. Never the ring. Never a forked verb. Spec before code.
- **V — Verify**: live witness (`erp/tests/poc_*_live.js`, §-tagged, 0 pageerrors) AND oracle-diff to the cent AND
  regressions green (recinfo/draft/blue/grid-batch/form-pill/gridstatus). Then ship (clean `/tmp/wt-*` off origin/main,
  sw CACHE_VERSION clean line, auto-merge, VERIFY it lands).

## 2. THE SPINE — ERP CRITIC UX JOURNEY (`prompts/ERP_CRITIC_UX_LANE.md`)
The whole ERP effort funnels into ONE judged journey: a real person signs in once, works five migrated tenants end to
end, reaches the differentiators, and the critic (oracle-grounded) signs off leg by leg. **This is the spine; the lanes
in §4 are tributaries that feed it.** Status (2026-06-15):
- **P0 FRONT DOOR (J1)** ✅ LIVE — #325, sw v692 (5 demo tenants at login, lazy-install, canonical 38-BP Odoo shard).
- **P1 J2 ORIENT** ✅ + **J3 READ** ✅ — #328 (W-CRITIC-ORIENT+READ 9/9). **J5 PROCESS** ✅ DONE/LIVE — #329, sw v694
  (W-CRITIC-PROCESS-LIVE 18/18: Complete is now SIGNED + persists + survives reload on iDempiere's own surfaces; see S1).
- **J4 CREATE** ✅ #331 (+ full CRUD #337). **J6 POST + REPORT** ✅ DONE/LIVE — #338, sw v698 (W-CRITIC-POST-LIVE 17/17:
  the completed invoice's GL is SHOWN to the cent via Posting-Preview on iDempiere's own invoice window + a receipt/
  statement folds; Accts-Posted honestly absent on the migrated tenant; see S3).
- Remaining: P2 (5 tenants) → P3 (surpass+gating) → P3.5 (consolidated) → P4 (scorecard).

## 3. THE SESSION SERIES (resume here, one bounded leg each)
Each S-row is a session-opening prompt. Open the named card + this doctrine + the oracle, then R→E→V.

- **S1 — J5 PROCESS, the signed complete ✅ DONE/LIVE (#329, sw v694, W-CRITIC-PROCESS-LIVE 18/18).** Re-pointed iDempiere's
  Process ▶ pill, DocAction bar AND grid gear-batch off the in-memory FSM preview onto the SHARED signed lane via ONE seam:
  `_fsmCtx.dispatch` now fires `__crud.process(table,id,action,{from,to,doctypeId})` (new in crud_overlay.js?v=12 — a
  parameterized `doProcess`, NO ring STORE entry → applyOp → commitProcess → completeFanout → signed commitGroup → persist;
  ring never opens). `_overlayDocTip` makes DocStatus read-the-tip (hydrates the lazy IDB sidecar) → the signed CO shows +
  SURVIVES RELOAD; `overlay:committed` refolds on a DOC_ACTION commit. Three live-drive fixes: `completeFanout` lower-cases
  the SELECT * order row (mixed-case C_DocType_ID gated fan-out); `_serializeCommit` queue (async commitGroup batch-tear);
  `_ensureStore` (host lane needs `__meta.docPolicy`). Host DocActions are NOT owner-gated (iDempiere = role+FSM+period, not
  creator). Witness: ACT1 Odoo form-pill signed+survives-reload, ACT2 grid-batch signed over N, ACT3 GardenWorld POS RE→CO
  REAL fan-out (ship=1/invoice=1, CREATE_DOCUMENT M_InOut+C_Invoice in the op-log). Regressions green
  (recinfo/draft/blue/grid-batch/form-pill/gridstatus).
- **S2 — J4 CREATE.** New via iDempiere's own New (CRUD seam, not the ring visual) → pick the tenant's own BPartner +
  product, price defaults via callout → Save = ONE signed op; row appears; draft-restore boundary holds. W-CRITIC-CREATE.
- **S3 — J6 POST + REPORT ✅ DONE/LIVE (#338, sw v698, W-CRITIC-POST-LIVE 17/17).** The completed invoice's GL is SHOWN to
  the cent on iDempiere's OWN Sales Invoice window (167): the Posting-Preview pill surfaces (AD `Posted`-column gate) and the
  drawer RENDERS the to-the-cent journal == the tenant oracle (Odoo client 12: DR Account Receivable 5002.50 / CR Tax Received
  652.50 / CR Product Sales 4350.00). A receipt folds (foldReceipt) and a financial statement folds (foldStatement over the
  real 300-row fact_acct on GardenWorld). Accts-Posted is HONESTLY absent on the migrated Odoo tenant (config but no captured
  posted journal — never faked). Rode the matrix-proven posters (DocPoster/PostResolver/foldReceipt/foldStatement, no new verb);
  the leg fixed two render bugs that had kept the UI from showing them: erp_preview.openPreview unwrapped mountAccordion's
  controller to a real DOM node (drawer had thrown on appendChild), and report_overlay.show() lc()-aliases CamelCase bundle
  columns (receipt amounts had read 0.00). Regressions green (poc_preview_demo, poc_accts_posted, J5 process 18/18).
- **S4 — P2 FAN TO ALL 5 (J2–J3 + J8).** Every tenant enters, orients to its OWN data, reads a doc, switches cleanly.
  PoC tenants (SAP/Oracle/Dynamics) honest about thin doc cycles (J4/J5 may be ⛔ pending the build-side doc-cycle).
- **S5 — P3 SURPASS LAYER (J7 / S1–S7) + W-CRITIC-GATING.** Each differentiator reachable + witnessed inside the journey:
  audit-by-construction, Blue Future as pre-release harness, live model self-edit, time-travel+draft, lens-swap, hot plugin.
- **S6 — P3.5 CONSOLIDATED CROSS-ERP REPORT (S8, the headline).** Tenants-panel graph icon → by-status/financial roll-up
  across all installed demo clients (a fold over the gated per-client data, labelled "all tenants"). W-CRITIC-CONSOLIDATED.
- **S7 — P4 THE SCORECARD.** Score vs iDempiere/Odoo/SAP/Oracle/Dynamics on concrete axes, each row citing its witness;
  honest surpass/match/trail verdict → `docs/ERP_CRITIC_SCORECARD.md`.

## 4. THE LANE MAP — tributaries (ACTIVE), grouped; retired + reference split out
**ACTIVE lanes feed the spine; consult the named card when its concern is the session's leg.**
- **ERP engine / equivalence (the oracle floor):** `FABLE5_MORDER_EQUIVALENCE` · `FABLE5_H2_DELTAS` · `FABLE5_SOURCE_FALSIFY_AUDIT`
  · `FABLE5_WORKFLOW_ORACLE` · `H2_ISOMORPH_TAIL` · `HARDEN_MATRIX` · `GAP_CLOSURE_LANE` · `APP_COVERAGE_LANE` ·
  `ERP_CALLOUT_PORT` · `KERNEL_COMMITGROUP` · `ENGINE_WRITE_PATH_NEXT` · `ENGINE_FULL_ERP_ISSUES` · `BACKEND_SUBSTRATE_LANE`
  · `ERP_SUBSTRATE_INTEGRATION` · `FOLD_MODEL_LOGIC` · `MIGRATE_FULL_MODEL_FRAME` · `MIGRATE_POSTING_CONFIG`.
- **Financial depth (FORWARD — engine-capable, DATA-BLOCKED; sequence AFTER the journey, P2+; track, do NOT build now):**
  analytic / cost-centre accounting + posting edge-cases (charge lines · GL distribution · realised-FX gain/loss) fold the
  day a richer tenant supplies the data — synergy: `BIM_TO_PROJECT` cost dimensions ARE analytic accounting. Captured here
  so they are not orphaned by `GAP_CLOSURE_LANE`'s closure (2026-06-14, all priorities done) — these were data-blocked, not
  unbuilt. **Odoo server-actions = a fold NON-GOAL** (imperative Python, no declarative subset) → route custom logic to the
  **plugin lane** (`.foldbundle` JS callout), not the fold. PM note: leaving all three alone does NOT impact the
  substrate/delivery thesis — the only risk was losing track, which this line closes.
- **ERP frontend (iDempiere UI convention — obeys the doctrine):** `FRONTEND_LANE_MASTER` (the front-end seam state) ·
  `GRID_BATCH_FORM_PILL_SPEC` · `CONSISTENCY_FINISH` · `LENS_FAMILY` · `IDEMPIERE_RECORD_PANEL` · `IDEMPIERE_TOUR_GUIDE` ·
  `IMPORT_EXPAND_POC` · `ERP_GLOBE_SEARCH` · `ERP_INIT_BUBBLE_INSTANT` · `MIGRATE_INSTALL_TENANT` · `MIGRATE_SHOWME_OVERLAY`.
- **Odoo / migration fold:** `ODOO_FOLD_CONTINUE` · `ODOO_QWEB_PRINT_FOLD` · `ODOO_SERVER_ACTION_FOLD` · `SAP_FOLD_POC` ·
  `MIGRATION_CAMPAIGN_RESUME` · `IDMP_FULLWIDTH_SEED` · `ERP_DATA_SHARDING_SESSION`.
- **POS (a lens over the shared model):** `POS_SHOWCASE_LANE` · `POS_FULL_LOOP` · `POS_GAP_CLOSE` · `WH_POS_PICK_LANE` ·
  `WH_SHIP_LATER_CLIPBOARD` · `WH_ROBOTICS_LANE`.
- **BIM ↔ ERP bridge:** `BIM_ERP_FOLD` · `BIM_TO_PROJECT` · `BIM_TO_ERP` (the Blue-dot + project-lines-with-costs path now LIVE).
- **Glassbowl / Gravity (where the ring + bubbles belong — NOT iDempiere):** `PILLS_TO_GLASSBOWL` ·
  `PILLS_TO_GLASSBOWL_CONSOLIDATE` · `GLASSBOWL_LAYOUT` · `GLASSBOWL_SWUPDATE` · `GUIDE_SHOWME_PROCESS`.
- **Viewer (history / 4D / city / offline / find):** `HISTORY_*` (knob_dial, parallel_timeline, persist_recall, session_events,
  tap_to_idempiere) · `REALISM_CLOUDS_4D` · `GANTT_ACCURACY` · `TM_SCHEDULE_EDITOR` · `4D_CAPTURE_AND_FALLBACK` ·
  `S285_CITY_*` · `S284*` (offline/installer) · `S280_IMPORT_POLISH` · `S276b_WEBGPU_POLISH` · `REVIT_PLUS_LENS` ·
  `REVIT_LENS_RESUME` · `SPATIAL_STOREY_NORMALIZE` · `IMPORTER_LENS_RESUME` · `OFFLINE_BUTTON_SURFACE` · `VIEWER_SFX_AUDIO`.
- **BOM:** `S271_BOM_CONTEXT` · `S272_BOM_ENGINE_PHASE1` · `S272_BOM_ENGINE_PHASE3`.
- **Governance / watchdogs (standing roles, not features):** `FUNDAMENTALS_WATCHDOG` · `watchdog` · `WATCHDOG_RED_PILL` ·
  `S227c_playwright_watchdog` · `MULTI_LANE_LAUNCH` · `MULTI_LANE_WAVE3` · `LIVE_ONLY_BUG_HUNT`.

**REFERENCE (specs/doctrine — cite, don't execute):** `ADVERSARIAL_EQUIVALENCE_AUDIT` · `ORDER_OF_PLAY` ·
`UI_OVERLAY_GOVERNANCE` · `ODOO_FOLD_POC` · `TOUR_GUIDE_FRONTEND_HANDOFF` · `CONSTRAINT_MITIGATION_LANE` ·
`MAP_GAP_CLOSURE` · `BIM_TO_ERP` (paper) · `MIGRATE_PAPER_REVISE` · `IMPROVE_MIGRATE_COMPARE_ILLUSTRATION` ·
`HISTORY_KNOB_SIGNAL_TAP`. Plus the canonical docs: `docs/ERP_MODEL_ARCHETYPE.md` · `docs/ERP_COVERAGE_MATRIX.md` ·
`docs/ERP.md` · `docs/ERP_BACKEND_SEPARATION.md`.

**RETIRED (work shipped/absorbed — moved to `prompts/archive/` this session, 29 files):** BLUE_FUTURE_VISUAL_LANE ·
CRUD_EDIT_PERSIST · CRUD_P_R_REPORT · DRAFT_RESTORE_VISUAL_LANE · ERP_AUDIT_CHANGELOG · ERP_TESTING_PILL_UX(absorbed) ·
HISTORY_WHOLE_TIMELINE · I4_OPLOG_RECONCILE · IDEMPIERE_PILL_HANDOFF · IDLE_RENDER_GATE · MIGRATE_ERP_PICKER ·
NEW_CLIENT_MGMT · NINJA_MODE_LANE · NINJA_MODE_PILL · PLUGIN_SYSTEM_LANE · POS_ENGINE_LANE · POS_KILLER_DEMO ·
POS_LENS_SESSION · POS_PANEL_UX_COMPACT(absorbed) · POSTING_PREVIEW_PANEL · PRECISION_PIVOT · REFRESOLVE_SPEC ·
REPORTING_LANE · RESUME_REDPILL_DEPLOY · REVIT_LENS_FIX · RULE_EDIT_ONE_GESTURE · SETTINGS_JSON_EDITOR · UI_PAYLOAD_PERF ·
WH_WALK_UX(absorbed). (Reversible — git history preserved; restore with `git mv prompts/archive/<f> prompts/<f>`.)

## 5. HOW TO USE THIS FILE
1. New ERP-UI session → read §0 doctrine + §2 spine status + the §3 S-row you're on + its named card + the oracle.
2. Do ONE leg, R→E→V, ship, update §2/§3 status here + `ERP_CRITIC_UX_LANE.md`.
3. Touching a tributary (§4)? It serves the spine — obey the doctrine (iDempiere UI + shared underlying, ring stays Glass).
4. Finishing a lane → move its card to `prompts/archive/` + drop it from §4. Keep this file the single live map.
