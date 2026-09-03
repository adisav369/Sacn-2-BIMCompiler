# ⚠ DO NOT REMOVE — DEDICATED LANE: "THE ERP CRITIC" — 5-tenant full-UX, judged by me, to a surpass bar
# Scope: drive the WHOLE user journey (front door → 5 demo tenants → navigate → create → process → post →
#   report → the differentiator lenses) to a coherent, satisfying experience — then I score it, as a critic who
#   has actually used iDempiere/Odoo/SAP/Oracle/Dynamics, against an HONEST axis-by-axis bar. Not feature parity:
#   surpass where this architecture genuinely wins, MATCH the core transaction loop, and NAME where it trails.
# Spec-first · §-log first (READ the log after every run) · witness-led (each leg NAMES its acceptance bar) ·
#   deterministic/NON-INVENT (real rows; a gap is a gap, never papered) · consume the seam, never fork a verb ·
#   GO before deploy. Deploy = bim-ootb/erp on a CLEAN /tmp/wt-* off origin/main; sw CACHE_VERSION clean line.
# Source of truth read FIRST each session: docs/ERP_COVERAGE_MATRIX.md (the honest scoreboard — 7✅/32🟡/3⛔,
#   engine is oracle-equivalent to the cent; most 🟡 = live render-wiring, NOT engine gaps) · docs/ERP.md
#   (blueprint) · docs/ERP_MODEL_ARCHETYPE.md (MOrder archetype = the real denominator) · prompts/FRONTEND_LANE_
#   MASTER.md (the front-end seam state) · erp_picker.js TENANT_SHARD (the 5 tenants already defined).

## ▶ NEXT SESSION — START HERE (paste this file's name to open the lane)
This lane card IS your standing backlog for the session — work the PHASES top-to-bottom (✅ DONE (witness) /
🟡 / ⛔ each leg; never re-park, never green-wash). Session-opening = durable authorization for the whole
solve incl. prod deploy.
STARTUP READS (before acting): this card in full · docs/ERP_COVERAGE_MATRIX.md (engine is oracle-equivalent;
most 🟡 = render-wiring, NOT engine gaps) · erp_picker.js TENANT_SHARD (the 5 demo tenants already defined) ·
prompts/FRONTEND_LANE_MASTER.md (front-end seam state — items 0–5 ✅, sw v690 live).
P0 ✅ DONE/LIVE (#325, sw v692). P1 IN PROGRESS: J2 ORIENT ✅ + J3 READ ✅ (W-CRITIC-ORIENT+READ 9/9, #328), J5 PROCESS
✅ DONE/LIVE (#329, sw v694, W-CRITIC-PROCESS-LIVE 18/18). J5 re-point shipped: the form Process ▶ pill, DocAction bar AND
grid gear-batch now EXECUTE + SIGN on the SHARED lane (`_fsmCtx.dispatch` → new `__crud.process` → `commitProcess` →
`completeFanout` → signed `commitGroup` → persist; ring never opens). Complete is signed + survives reload (DocStatus
read-the-tip via `_overlayDocTip`); GardenWorld POS RE→CO shows REAL fan-out (ship+invoice in the op-log); Odoo NULL-doctype
orders gate honestly. P1 doc-complete tenant DRAINED: J4 CREATE ✅ (#331, + full CRUD #337) · **J6 POST + REPORT ✅
DONE/LIVE (#338, sw v698, W-CRITIC-POST-LIVE 17/17)** — the completed invoice's GL is SHOWN to the cent via Posting-Preview
on iDempiere's own invoice window (Odoo client 12: DR AR 5002.50 / CR Tax 652.50 / CR Sales 4350.00 == oracle), a receipt +
financial statement fold, Accts-Posted honestly absent on the migrated tenant; fixed two render bugs (erp_preview
controller→node; report_overlay CamelCase `lc`). Witness `bim-ootb/erp/tests/poc_critic_post_live.js`.

S4 / P2 — FAN TO ALL 5 TENANTS ✅ DONE/LIVE (#342, sw v699, W-CRITIC-SWITCH-LIVE 36/36). J2 ORIENT + J3 READ across all 5
(Odoo/iDempiere doc-complete read an order; SAP/Oracle/Dynamics read a BP, honest thin doc cycle), GATING load-bearing
(GardenWorld co-resides, 0 bleed), J8 in-session rail switch Odoo→iDempiere with 0 cross-leak. Rail-demo fix enabled the
switch. Witness `tests/poc_critic_switch_live.js`.

▶ **FIRST TASK NEXT SESSION = S2B (AD-FOLDED CRUD GENERALITY)** — promoted to the TOP of `prompts/FRONTEND_LANE_MASTER.md
§OUTSTANDING` (user steer 2026-06-17); card `prompts/S2B_AD_FOLDED_CRUD_SESSION.md`. Build `foldCrudSpec(table)` (editability
from AD: AD_Field/AD_Column + IsView/IsReadOnly/IsUpdateable), retire the curated 5-table crud_ops.json allow-list, witness
W-AD-FOLDED-CRUD-LIVE (C_BPartner becomes New/Edit/Delete-capable, no curated entry) + the 4 audit gaps. NOTE: the Process/
DocAction browser leg is ALREADY DONE+LIVE (#329) — _showProcessChooser folds the full FSM legal set, NOT hardcoded CO; so
the only open "general not custom" item is CRUD editability.

▶ **THEN P3 — THE SURPASS LAYER** (J7 / S1–S7). Drive each differentiator (audit ✅ #343, then live model self-edit ×5 /
lens-swap / hot plugin) reachable + witnessed INSIDE the journey on a real tenant + W-CRITIC-GATING. THEN P3.5 (consolidated
cross-ERP report S8) → P4 (scorecard). PoC tenants (SAP/Oracle/Dynamics) stay HONEST about thin doc cycles: J4/J5/J6 ⛔
pending the build-side doc-cycle (P5).

## WHY THIS LANE EXISTS (the honest premise)
The HARD part is done: the engine reproduces iDempiere's posted journal, DocAction FSM, inventory and reversal
to the cent across both acctschemas (coverage matrix). What is NOT yet proven is that a real person can SIT DOWN,
sign in once, and have a complete, fluid ERP experience over FIVE migrated tenants — create a sales order on a
tenant's own BPartners/products/prices, complete it (real fan-out + GL), see it posted, report on it, and reach
the things no incumbent ERP has (op-log time-travel, speculative blue futures, per-field lineage, live model
self-edit, lens-swap). This lane stitches those into ONE journey and makes ME — playing a demanding ERP critic —
sign off, leg by leg, with a witness behind every "yes."

## THE CRITIC (persona + standing rule)
I am an evaluator who has run the real products. I am unimpressed by claims and by a single happy-path screenshot.
My satisfaction rule, applied to EVERY leg: "Could I do this in iDempiere/Odoo/SAP, and is THIS faster, clearer,
or more honest? If a step dead-ends, says 'not in seed', or needs an install I shouldn't need — that's a FAIL,
logged, not hidden." A leg is ✅ only when a live witness drives it on the served bundle AND I'd choose it over
the incumbent for that specific task. Hype is a FAIL — overclaiming is the one thing a critic never forgives.
I stress-test AGGRESSIVELY in BLUE (the pre-release harness, S3) — push every doc through its lifecycle and the
destructive paths in the speculative branch, judge the consequences, then accept or discard. Because blue can't
touch official books, the critic can be ruthless; a client only earns "release-ready" after surviving a blue run.

## ACCEPTANCE SPINE — the journey every tenant must survive (each leg = a witness)
J1  FRONT DOOR: open the app cold → sign in → all 5 demo tenants are PRESENT and enterable with NO install step
    (lazy-install on pick via the existing installShard; Install/Migrate pills still there for real DBs). W-CRITIC-FRONTDOOR.
J2  ORIENT: enter a tenant → the menu is real + role-pruned; open a window → records are THAT tenant's own rows
    (its BPartners/products), not GardenWorld bleed-through. W-CRITIC-ORIENT (per tenant).
J3  READ A DOCUMENT: open a sales order/invoice → header+lines render from the tenant's data; record-info (ⓘ),
    per-field lineage hover, and change history all answer from the op-log. W-CRITIC-READ.
J4  CREATE: New → pick the tenant's own BPartner + product (real price defaults via callout) → Save = ONE signed
    op; the row appears, the draft-restore boundary holds. W-CRITIC-CREATE (≥1 tenant, the "full set" one).
J5  PROCESS: Complete via the FSM (form Process ▶ pill AND grid gear-batch) → legal set is exact per docstatus →
    real fan-out (shipment/invoice) is created, drillable. W-CRITIC-PROCESS.
J6  POST + REPORT: the completed doc's GL is derivable to the cent (posting-preview / Accts-Posted) and a
    financial statement / receipt folds. W-CRITIC-POST (rides the matrix-proven folds; UI must SHOW them).
J7  THE SURPASS LAYER (see below) reachable in the SAME session without leaving the tenant. W-CRITIC-SURPASS.
J8  SWITCH: change tenant from the rail and repeat J2–J6 — the lens is identical, only the data changes (the
    "UI is a cheap swappable lens over one owned model" thesis, demonstrated across all 5). W-CRITIC-SWITCH.

## STANDING RULE — TENANT GATING IS LOAD-BEARING (security, not cosmetics)
Every per-tenant read is AD_Client-scoped (the login already adds `AD_Client_ID IN (0,<client>)`); a tenant must
NEVER see another tenant's BPartners/products/docs/GL. This is witnessed, not assumed (W-CRITIC-GATING: enter
tenant A, assert 0 rows of tenant B's data anywhere — grid, lenses, reports). The ONE deliberate cross-client
surface is the consolidated roll-up (S8) — and it is explicitly labelled as "all tenants", never a silent leak.

## DATA-FLOW / FIRST-IMPRESSION (my call, per your steer "impression = a demo all ready")
Present all 5 from a manifest at login INSTANTLY → the impression is "everything's already here". Hydrate each
tenant's data LAZILY on first pick via the existing `installShard` (small shards; resident + instant after), with
an optional silent background pre-warm so switching never stalls on stage. Nothing is fabricated to fake
readiness — a tenant shows real rows or it isn't listed. Demo tenants are DELETABLE when the user is ready to run
their own (reuse NEW_CLIENT_MGMT teardown — the `idmp-tenant-del` affordance; exact trigger TBD).

## THE SURPASS LAYER — the axes where this genuinely beats the incumbents (and WHY)
S1  ONE LOGIN, FIVE ERPs, ZERO INSTALL, FULLY OFFLINE — no incumbent shows Odoo + iDempiere + SAP + Oracle +
    Dynamics data side-by-side in one browser tab, no server. (iDempiere/Odoo/SAP each need a stack.)
S2  AUDIT BY CONSTRUCTION — record-info, per-field lineage (DELETES AD_ChangeLog), and full history are FREE
    folds of the signed op-log, always-on, zero setup. Incumbents need enabled change-log + a maintained table.
S3  SPECULATIVE BLUE FUTURE — long-press a dot → run a real CompleteIt (real fan-out + GL) in a discardable,
    unmistakably-UNOFFICIAL branch, accept-up-to-here = rebase to official. No incumbent has a drillable,
    atomically-discardable fork of live transactional state.
    ★ BLUE = THE PRE-RELEASE TEST HARNESS (user-aligned 2026-06-15): before a user "releases" a client for real
    use, they drive its WHOLE document lifecycle IN BLUE — complete every doc, watch the fan-out, GL and reports,
    push the destructive/edge paths — then ACCEPT (promote to official) if it's right or DISCARD (atomic foldback,
    books never moved) if not. So blue is not just a what-if toy: it is how a tenant is VALIDATED before go-live,
    and how the critic stress-tests aggressively without polluting official state. Connects to delete-when-ready
    (the demo→real handoff): test in blue → accept → it's yours; or discard/delete and start clean.
S4  LIVE MODEL SELF-EDIT — edit AD_Field/AD_Window and the form repaints on the spot (re-read, not recompile),
    propagable as a signed append-log to another node. iDempiere needs a restart/2Pack; we don't.
S5  TIME-TRAVEL + DRAFT — back-dot read-only restore; private draft (Save = publish boundary) never leaks a
    half-typed record to other docs. S6  LENS-SWAP — Kanban drag == a signed verb; POS / WH-walk / graph are the
    same model under different skins. S7  HOT PLUGIN — paste an ES-module URL → ACTIVE (vs drop-JAR-restart).
S8  CONSOLIDATED CROSS-ERP REPORTING (your steer) — at the TENANTS panel, press the graph icon → a roll-up
    ACROSS all 5 demo clients (by-status / financial summary) in ONE view. This is THE headline no incumbent can
    do: SAP can't roll up Odoo+Oracle+Dynamics, because they don't share a model — here every tenant is folded to
    the SAME AD model + op-log, so consolidation is a fold, not an integration project. Reads the gated per-client
    data deliberately and labels it "all tenants"; per-tenant working views stay strictly isolated (gating rule).
Each S-leg must be reachable in the J-journey and witnessed live; an S-leg that only works headless is a 🟡, not ✅.

## WHERE IT WILL NOT (and should not CLAIM to) SURPASS — the critic's honesty clause
- BREADTH: incumbents have hundreds of windows/modules; we ship a curated, deep slice. Say so.
- SCALE / CONCURRENCY / INTEGRATIONS: real multi-user write contention, EDI, bank feeds, payroll — out of scope.
- THE PoC TENANTS: SAP/Oracle/Dynamics shards are master-mapping (their BPartners+products), THIN on a full doc
  cycle. Only Odoo + GardenWorld are doc-complete today. "5 tenants doc-complete" needs a build-side doc-cycle
  (separate follow-on, tracked here, NOT faked into the demo).
A leg that would need one of these to pass is marked ⛔ with the one missing fact/build — never green-washed.

## PHASES (work top-to-bottom; each leg → spec → live witness → ✅ DONE (witness) / 🟡 / ⛔, then next)
P0  ✅ DONE (W-CRITIC-FRONTDOOR-LIVE 12/12) — bim-ootb PR #325, erp sw v691. Login step0 now unions resident
    tenants with the demo manifest (`ErpPicker.manifest = TENANT_SHARD`); a cold seed surfaces 6 rows (GardenWorld
    + 5 demos, tagged demo·ready / demo·PoC); `_enterDemoTenant()` lazy-installShards on pick — NO Install dialog,
    NO ?shard= URL — then lands on the tenant user list. CANONICAL SHARD RESOLVED: served erp/12-odoo.db is now the
    full W-P4-MASTERS extraction (393 KB, 38 BP), NOT the stale 5-BP postcfg slim (217 KB = build/erp/12-odoo_postcfg.db).
    In-tenant live tests now enter via ?client=garden (skip the new picker). Regressions green (recinfo/draft/blue/
    grid-batch/form-pill/audit-changelog/descriptor-seam). Pre-existing drift noted: poc_idmp_lifecycle logsOk
    (install=Y vs install=rail) fails on origin/main too — separate fix.
P1  THE DOC-COMPLETE TENANT END-TO-END (J2–J6 on Odoo client 12: 38 BP / 35 products / 35 prices / 27 orders).
    J2 ORIENT ✅ + J3 READ ✅ (W-CRITIC-ORIENT+READ-LIVE 9/9, tests/poc_critic_odoo_orient_read_live.js, branch
    feat/critic-p1-odoo-e2e @695c6aa — NOT yet PR'd): enter via ?shard=12-odoo.db&login=Odoo&window=143; Sales
    Order grid = 54 rows ALL client-12, S000xx, 0 of GardenWorld's 8 bleed (gating load-bearing); order form
    header DocumentNo==db, BP renders "Gemini Furniture" not an id (#324 refresolve), record-info ⓘ popup answers
    from op-log. Verification-only (no code change). NEXT: J4 CREATE (New via CRUD ring → BP+product line, price
    callout → Save=1 signed op), J5 PROCESS (Complete via form Process pill `_showProcessChooser`→_fsmCtx AND grid
    gear-batch), J6 POST (posting-preview GL to the cent). ⚠ J5 finding to verify: the UI Complete dispatches
    AdDocFsm (state→CO); the FAN-OUT (shipment/invoice) is erp_engine.completeOrder + DATA-FLAG-gated — confirm the
    served UI actually materializes drillable fan-out, or mark 🟡 (engine-proven via poc_gated_complete, UI-wiring TBD).
    J5 PROCESS ✅ DONE/LIVE (#329, sw v694, W-CRITIC-PROCESS-LIVE 18/18, tests/poc_critic_process_signed_live.js):
    the form Process ▶ pill, DocAction bar AND grid gear-batch now route through ONE shared seam — `_fsmCtx.dispatch`
    fires `__crud.process(table,id,action,{from,to,doctypeId})` (new in crud_overlay.js?v=12: parameterized doProcess,
    NO ring STORE entry → applyOp → commitProcess → completeFanout → signed commitGroup → persist; ring never opens).
    The FSM still decides legality + the transition; the lane signs exactly that one (no split-brain, no forked verb).
    Complete is SIGNED (verifyChain=ok) + persists + SURVIVES RELOAD (DocStatus read-the-tip via `_overlayDocTip`,
    which hydrates the lazy IDB sidecar; `overlay:committed` refolds on a DOC_ACTION commit). Three live-drive fixes:
    `completeFanout` lower-cases the SELECT * order row (mixed-case C_DocType_ID had gated fan-out); `_serializeCommit`
    queue (async commitGroup batch-tear); `_ensureStore` (host lane needs `__meta.docPolicy`). Host DocActions are NOT
    owner-gated (iDempiere = role+FSM+period, not creator). Witness: ACT1 Odoo form-pill signed+survives-reload, ACT2
    grid-batch signed over N, ACT3 GardenWorld POS RE→CO REAL fan-out (ship=1/invoice=1, CREATE_DOCUMENT M_InOut+C_Invoice
    in the op-log); Odoo NULL-doctype orders gate honestly. Regressions green (recinfo/draft/blue/grid-batch/form-pill/
    gridstatus). NEXT = J4 CREATE (S2) + J6 POST.
    J6 POST + REPORT ✅ DONE/LIVE (#338, sw v698, W-CRITIC-POST-LIVE 17/17, tests/poc_critic_post_live.js): the
    completed invoice's GL is SHOWN to the cent on iDempiere's OWN Sales Invoice window (167). ACT1 Odoo client 12 —
    Posting-Preview pill surfaces (AD `Posted`-column gate) → drawer RENDERS coverage=complete balanced DR 121000
    Account Receivable 5002.50 / CR 251000 Tax Received 652.50 / CR 400000 Product Sales 4350.00 == the Odoo oracle;
    a receipt folds (total 5002.50 = 4350.00 + 652.50); Accts-Posted HONESTLY absent (migrated shard carries posting
    config but no captured fact_acct journal — never faked; the critic isn't misled, preview="would post"). ACT2
    GardenWorld client 11 — preview lights $50.35, a FINANCIAL STATEMENT folds over the real 300-row fact_acct (Income
    Statement, 222 cells, W-PA-REPORT), receipt folds 47.50 + 2.85 = 50.35. Rode the matrix-proven posters
    (DocPoster/PostResolver/foldReceipt/foldStatement — no new verb); fixed two render bugs blocking the SHOW:
    erp_preview.openPreview unwrapped mountAccordion's controller to a DOM node (drawer had thrown on appendChild),
    report_overlay.show() lc()-aliases CamelCase bundle columns (receipt had read 0.00). Regressions green. NEXT = P2.
P2  FAN TO ALL 5 (J2–J3 + J8) ✅ DONE/LIVE (#342, sw v699, W-CRITIC-SWITCH-LIVE 36/36, tests/poc_critic_switch_live.js):
    ACT A fans Odoo(12)/iDempiere(13)/SAP(14)/Oracle(15)/Dynamics(16) — each enters via its OWN user, orients to its
    OWN rows, reads a record; doc-complete tenants read a Sales Order (143), the PoC tenants read a Business Partner
    (123) HONESTLY (J4/J5/J6 ⛔ pending the build-side doc-cycle, P5). GATING load-bearing: GardenWorld(11) co-resides
    + holds same-table rows, ZERO bleed into any tenant grid (asserted off the loaded sql.js db, not the DOM). ACT B
    switches Odoo→iDempiere in ONE session via the rail: with Odoo's 54 orders physically co-resident, iDempiere's grid
    is all client 13 — 0 Odoo bleed, 0 GW bleed (same window-143 lens, two tenants). FIX (the J8 enabler): the in-session
    "‹ Tenants" rail now passes the demo manifest to loginStep0, so a logged-in user can switch to a not-yet-installed
    demo tenant from the rail (lazy-install on pick) — previously the rail only reached resident tenants. Regressions
    green (frontdoor/draft/recinfo/grid-batch/form-pill/orient-read). NEXT = P3.
P3  THE SURPASS LAYER (J7 / S1–S7) — each differentiator reachable + witnessed inside the journey, on a real
    tenant, not a lab fixture. W-CRITIC-SURPASS live. Includes W-CRITIC-GATING (tenant A sees 0 of tenant B).
    KEY FRAMING (user steer 2026-06-16): drive the MODEL/op-log-fold axes (S2 audit, S4 self-edit, S6 lens-swap,
    S7 plugin) across ALL 5 — that cross-tenant repetition IS the proof they are SUBSTRATE INVARIANTS, not per-
    tenant features (same fold, 5 differently-sourced datasets, zero per-tenant code). Transaction-dependent axes
    (S2-strong / S3 blue CompleteIt) stay on doc-complete tenants, honest on the masters (post-import = empty trail).
    EVIDENCE (grep 2026-06-16): runtime engine/UI (idempiere.html/crud_overlay/ad_*/idmp_session/post_resolver/
    erp_engine) has ZERO vendor-name branching; "odoo/sap/oracle/dynamics" appear ONLY in migration adapters
    (odoo_descriptor/migrate_agent/ninja_*), the display manifest (erp_picker TENANT_SHARD), comments, and "oracle"
    = the TEST-ORACLE (ground truth), never the Oracle ERP. New domains plug in via a thin migration adapter →
    inherit ALL capabilities free. This is the "app in its own right" thesis.
    • S2 AUDIT ✅ DONE/LIVE (#343, witness-only, W-CRITIC-AUDIT 27/27, tests/poc_critic_audit_live.js): ⓘ record-
      info fold universal+scoped on all 5 (ACT A); captures a real signed create+update scoped to client (ACT B);
      honest empty-but-ready trail on freshly-migrated SAP (ACT C).
    • S4 LIVE MODEL SELF-EDIT ✅ DONE/LIVE (#350, sw v702, W-CRITIC-SELFEDIT 30/30, tests/poc_critic_selfedit_live.js):
      a signed dictionary edit (AD_Field.IsDisplayed) refolds the OPEN window on the spot — re-read, not recompile —
      identically on all 5 (ACT A: pick a displayed non-key field → __crud.applyOp signs the CRUD_UPDATE → §AD-SELFEDIT-LIVE
      refold → the column VANISHES live). ACT B (Odoo): the served BUNDLE row stays IsDisplayed=Y (the edit lives only in
      the op-log), and a reverse edit (N→Y) brings the column back — bidirectional, latest-wins = re-FOLD not recompile.
      REGRESSION FIXED: the overlay:committed data-CRUD branch (J4 CREATE, #331) returned for EVERY CRUD op, shadowing the
      dictionary refold (W-AD-SELFEDIT-LIVE #312) — now an AD_* CRUD_UPDATE falls through to the model refold.
    • RING LEAK ON iDempiere ✅ FIXED (#351, sw v703, W-RING-LEAK 10/10, tests/poc_critic_ring_leak_live.js): the ✎ CRUD
      toolbar button fanned the Glass ring on iDempiere (enable()+openRing → §CRUD ring view=on + §CRUD-IDMP-OPEN); S2B
      widened it to every folded table. Re-pointed ✎ ("✎ Edit") to the form host-seam (_openEdit → __crud.update, ring NOT
      fanned, doctrine §0); the ring stays Glass/Gravity-only.
    • S6 LENS-SWAP ✅ DONE/LIVE (PR #365, sw v712, W-CRITIC-LENS-SWAP 3/3, tests/poc_critic_lensswap_live.js):
      the kanban drag NO LONGER forks its own op-log (KanbanHost `_kanbanProj`/window.ERP). It now reuses the SAME
      `_fsmCtx(tab,rec).dispatch(action)` → `__crud.process` → commitProcess → completeFanout → signed commitGroup →
      persist seam the grid DocAction bar + gear-batch use (J5). The board reads its tip from the SHARED `__crud`
      kernel sidecar (`readTip`); AdDocFsm is the WRITE authority (wfmc drop-zones stay the visual mirror). ACT A — a
      kanban drag is the shared signed verb (`via=__crud.process`, engine `§CRUD process committed … verifyChain=ok`,
      `__crud.readTip`===dragged-to, ring NOT opened); ACT B — the grid row reflects the drag; ACT C — survives reload
      + the board folds its tip from the shared store. Old `poc_idmp_kanban` retargeted off window.ERP onto
      `__crud.readTip` (C_Order + C_Invoice). J5 18/18 green. NOTE (honest follow-on, NOT done this leg): the POS lens
      (`pos_lens.js`) still writes to `window.ERP.opDb` (=_kanbanProj) — same store-split class, separate lens; track
      as a sub-leg if POS-created/completed docs must show on the grid. NEXT P3 axis: S1 / S3 / S5 / S7.
P3.5 CONSOLIDATED CROSS-ERP REPORT (S8, the headline) — the tenants-panel graph icon → a by-status / financial
    roll-up across all installed demo clients (a fold over the gated per-client data, labelled "all tenants").
    Reuses the existing graph/report fold; the only new part is "iterate the clients + sum". W-CRITIC-CONSOLIDATED.
P4  THE CRITIC'S SCORECARD — I score this vs iDempiere/Odoo/SAP/Oracle/Dynamics on concrete axes (install,
    offline, speed-to-first-doc, audit, what-if, model-edit, breadth, scale), each row citing the witness that
    earns it. Net verdict: where it SURPASSES, where it MATCHES, where it TRAILS. Honest, no hype. → docs/ERP_
    CRITIC_SCORECARD.md. P5 (follow-on, optional) the PoC doc-cycle so all 5 are J4–J6 capable.

## DEPLOY / WITNESS DISCIPLINE
- Live witnesses = headless-chrome DOM probes on the served bundle (pattern: erp/tests/poc_*_live.js), §-tagged,
  0 pageerrors, READ the .log before any conclusion. Engine value-correctness stays headless (matrix witnesses).
- One bounded leg per PR; sw CACHE_VERSION bump (clean line, detail → internal/SW_CHANGELOG.md); auto-merge;
  VERIFY it lands on origin/main. Regressions (recinfo/draft/blue/grid-batch/form-pill) stay green each leg.
- Never mark a leg ✅ without the live witness AND the critic's "I'd pick this for that task" sign-off in the log.
