# ⚠ DO NOT REMOVE — WHERE I'M AT (handoff written 2026-06-17, supersedes any older NEXT_SESSION.md)
# Read CLAUDE.md + MEMORY.md first. Non-negotiables (every turn): spec-first · witness-led (each test NAMES its
# issue) · §-log FIRST (read the log, exit code is NOT evidence) · deterministic/NON-INVENT · consume the seam
# (never fork a verb) · edit bim-ootb ONLY via a clean /tmp/wt-* worktree off FRESH origin/main · ship via
# auto-merge + VERIFY it LANDED on main.

## ⛳ TOP PRIORITY (user steer 2026-06-17, do BEFORE resuming P3 surpass) — iDempiere-FAITHFUL IN-PLACE CRUD
Card: `prompts/CRUD_INPLACE_EDIT_SESSION.md` (top item in FRONTEND_LANE_MASTER §OUTSTANDING). Our edit UX is inverted
vs iDempiere (read-only form → ✎ Edit button → modal `#crudForm` popup). iDempiere = form/grid directly editable, NO
Edit button, NO modal; toolbar New/Copy/Save/Save&New/Delete/**Ignore(=undo)**/Refresh/GridToggle. Spec is GROUNDED in
the ZK source we have (`/home/red1/idempiere-dev-setup/idempiere/org.adempiere.ui.zk/.../adwindow/`) — plan from it, do
NOT ask the user about behaviours. Reuse crud_overlay's SIGNED write seam; move only the MOUNT (inline, not modal).
SUPERSEDES the ✎ Edit shipped in #351. Recommended as its OWN focused session (core-path refactor, full regression
surface). THEN resume P3 surpass below.

## ★ THE MAIN LANE — THIS IS THE WORK (don't lose sight of it)
**The spine = the ERP CRITIC UX journey J1→J8 + surpass layer S1–S8** (`prompts/GRAND_LANE_STRATEGY.md` doctrine +
`prompts/ERP_CRITIC_UX_LANE.md` the judged journey). Everything else (finance, ninja, viewer lenses) is a TRIBUTARY
that funnels back here. Standing backlog = `prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING`, worked top-to-bottom to zero.

**Where the spine is:** P0 ✅ · P1 ✅ (J2 ORIENT/J3 READ/J4 CREATE+full CRUD/J5 PROCESS/J6 POST+REPORT) · P2 ✅ (fan to
all 5 tenants #342) · S2B AD-folded CRUD ✅ (#348). **→ NEXT = P3, THE SURPASS LAYER (J7).**

**P3 surpass legs — each must be reachable IN the J-journey on a real tenant + live-witnessed + W-CRITIC-GATING
(headless-only = 🟡, not ✅):**
- S2 AUDIT ✅ DONE (#343, W-CRITIC-AUDIT 27/27).
- S4 LIVE MODEL SELF-EDIT ×5 ✅ DONE (#350, sw v702, W-CRITIC-SELFEDIT 30/30, tests/poc_critic_selfedit_live.js): a
  signed AD_Field.IsDisplayed edit refolds the open window on the spot — re-read, not recompile — on all 5 (ACT A),
  bundle untouched + reversible (ACT B). Fixed a regression: the overlay:committed data-CRUD branch (#331) shadowed the
  dictionary refold (#312) — AD_* CRUD_UPDATE now falls through to the model refold.
- RING LEAK ON iDempiere ✅ FIXED (#351, sw v703, W-RING-LEAK 10/10, tests/poc_critic_ring_leak_live.js): ✎ CRUD button
  re-pointed to the form host-seam ("✎ Edit" → _openEdit → __crud.update, ring NOT fanned); ring stays Glass-only.
- **NEXT P3 axis: S6 LENS-SWAP** (Kanban drag == signed verb; POS/WH-walk/graph = same model, different skin) · S1
  (offline 5-ERP) · S3 (speculative BLUE pre-release harness) · S5 (time-travel + draft) · S7 (HOT PLUGIN — paste URL→active).
- THEN P3.5 = S8 CONSOLIDATED CROSS-ERP REPORT (graph icon @ tenants panel) → P4 = scorecard.
- Honesty rule: SAP/Oracle/Dynamics PoC tenants stay ⛔ on thin doc cycles (J4/J5/J6) — never green-wash; name the gap.

**START NEXT SESSION:** read ERP_CRITIC_UX_LANE.md §PHASES + GRAND_LANE_STRATEGY §0/§2/§3 + docs/ERP_COVERAGE_MATRIX.md,
confirm the top OPEN P3 leg (S2 ✅ #343 · S4 ✅ #350 · ring-leak ✅ #351 → next = S6 lens-swap), spec → live witness → ✅,
then the next. Also tracked on FRONTEND_LANE_MASTER
§OUTSTANDING; the DOC-PANEL band is DRAINED, USER_JOURNEY_LANE #3 (kanban discoverability/mount) is MED-open.

## ▷ TRIBUTARY QUEUED — AD_PROCESS FOLD lane (`prompts/AD_PROCESS_FOLD_LANE.md`, written 2026-06-17)
Populate AD_Process handlers by demand (the "processes run on demand" half of the journey). DISPATCH SPINE ALREADY
LIVE (ad_process.js W-PROC + §AD-PROC-LIVE) — this lane only adds handlers, classified KIND 1 (report-fold) / KIND 2
(engine-verb via buildDoc, newVerbs=0) / KIND 3 (registered plugin). **First leg = GeneratePO from Project (C_Project,
KIND 2)** — C_Project has no DocAction, but GeneratePO etc. dispatch through the spine; fold its committed lines → a
signed C_Order(PO) op-group, W-PROC-GENPO(+LIVE). LOW conflict with the spine (mostly build/erp + a handler; host
process-UI already built). A tributary — slot after the spine reaches process-depth or as a user-prioritised insert.

## ⛔ KNOWN DOCTRINE VIOLATION (found live 2026-06-17, queued — lane to handle, do NOT hot-patch)
RING LEAK ON iDempiere: the legacy `✎ CRUD` toolbar button (idempiere.html:1088) fans the Glass/Gravity ring on the
iDempiere surface (`§CRUD ring … view=on` / `§CRUD-IDMP-OPEN`), violating GRAND_LANE §0 ("iDempiere NEVER opens the
ring"). Pre-dates J4/J5; S2B widened it (`_crudHas` foldable-aware → fires for any folded table). Doctrine-correct
form-pill path (`§FORM-PILL … ring not fanned`) works in parallel. FIX is one bounded idempiere.html leg — re-point
`✎ CRUD` to the form host-seam (or remove it) so the ring is Glass/Gravity-only. Full spec + witness in
FRONTEND_LANE_MASTER §OUTSTANDING ("RING LEAK ON iDempiere"). Panel claim stays true (form path is iDempiere-native).

## ⚠ STALE-STATE LANDMINE — read before scoping
The S2B card says "S3/J6 POST is the next spine leg" — **STALE**. S3/J6 POST+REPORT ✅ shipped #338 (sw v698), then
P2 ✅ #342, P3/S2 AUDIT ✅ #343, S2B ✅ #348. **Do NOT redo S3/J6 POST.** Reconcile the frontier from the lane docs first.

────────────────────────────────────────────────────────────────────────────────────────────────────────────────
## ✅ RECENTLY SHIPPED (context — do NOT rebuild; verify-only if you touch it)
- **S2B AD-FOLDED CRUD GENERALITY** — PR #348 (squash 9d6a9e9), sw **v701**, crud_overlay.js?v=15, ad_parser.js?v=24.
  `CORE.foldCrudSpec` derives editability FROM the dictionary (type via authoritative AD_Reference_ID; read-only via
  IsView+IsReadOnly+IsUpdateable; @#AD_Client/Org_ID@/@#Date@ ctx defaults resolved). FOLDED fallback in `entryFor`;
  host folds `_curTab()` + registers before create/update/remove. crud_ops.json now an OPTIONAL override (5 doc tables
  keep docPolicy fan-out). Audit gaps closed: (b) AD_OrgInfo.M_Warehouse_ID, (c) C_DocType.DocNoSequence_ID, (d)
  MV_INSTALLER discovers all install*SaveHooks from the registry. W-AD-FOLDED-CRUD 14/14 + W-AD-FOLDED-CRUD-LIVE 14/14;
  9 regressions GREEN. Black Book doctrine: detect-from-AD is GENERAL (CRUD); Process EXECUTE gated to ported
  DOC_FAMILY (consequences extracted, never invented) — C_Project has no Process b/c MProject isn't a DocAction model.
  Follow-ons (deferred): foldCrudSpec renders list/yesno as raw-value text (fold AD_Ref_List options later); only the
  3 well-known @#…@ context defaults are resolved (full AD default-expr language dropped).
- **BIM Project Finance lane (F1–F10)** — TRIBUTARY, LANDED PR #349 (squash f019730). currency/UOM/GL-map/EVM/
  VO-approve/claim/tax/period/blue-spec/blue-rollback; ad_seed.db conflict resolved by REGENERATING (main seed +
  idempotent seed_fin_* scripts). 10 W-FIN witnesses + ERP regressions green. Branches deleted. Open follow-ons
  (non-blocking, user concluded the lane): F10 BROWSER leg (live `‹ dots ›` rollback gesture on a blue Project Order —
  engine done/wired in viewer/blue_fold.js, live gesture unwitnessed); GH Pages viewer deploy not done (code on main;
  `minify_viewer.sh` is a separate publish step).

## HOUSEKEEPING
- MEMORY.md is over budget (~25.8KB > 24.4KB) — trim index entries (move detail to topic files) at next housekeeping.
- ~250 old remote feat/*/fix/* branch refs linger (mostly squash-merged + never deleted = false "unmerged"); a real
  audit would cross-ref each to a merged PR title to find any genuinely-stranded lane (like finance was). Optional cleanup.
