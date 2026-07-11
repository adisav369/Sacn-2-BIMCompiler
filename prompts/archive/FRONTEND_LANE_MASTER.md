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

### OUTSTANDING — RETIRED 2026-06-20 (fully drained → archived)
**This dictated/parked backlog ran to ZERO and was retired by user decree** so WORK-TO-ZERO stops re-surfacing it
every session. The full ✅/⛔ history (PRs, witnesses, §-logs — Blue-Future, in-place CRUD, AD self-edit, POS/WH,
doc-panel band, the lot) lives verbatim in `prompts/archive/FRONTEND_LANE_MASTER_OUTSTANDING_drained_2026-06-20.md`.
Do NOT re-walk it — it is provenance, not work.

**The live ERP/Viewer spine moved on** — the active plan is now: `prompts/GRAND_LANE_STRATEGY.md` (the single index +
doctrine) + the current RESUME card (`prompts/RESUME_IDMP_FIDELITY.md`) + MEMORY.md §SPINE. New dictated items append
to **§NEW BACKLOG** below (this is the WORK-TO-ZERO list going forward).

**CARRIED-FORWARD — RESOLVED by user 2026-07-01 (all three closed; kept here as the record, not active work):**
- **G-3 — headless WH-confirm (doctype-148) oracle. ⛔ DROPPED (low value).** User (2026-07-01) didn't recall needing
  it; clarified. The blocker was never the DB — it's the Java **OSGi/Equinox runtime** (`Adempiere.startup` NPEs on the
  BundleContext / SecureEngine service locator); docker Postgres (GardenWorld) is necessary-not-sufficient. The
  browser-side `inout_confirm.js` rule already SHIPS (E-5 W-WH-CONFIRM) with every fold citing its exact
  `MInOut`/`MInOutConfirm` source line + a rule-consistency arm → the Java fact-diff is a nice-to-have, not load-bearing.
  If ever wanted: run `ConfirmOracle.java` (`35b8e96f`, compiled+rollback-safe) INSIDE the already-bootable iDempiere
  server's OSGi runtime (the A2 Release-13 instance) against a throwaway docker-PG GardenWorld — NOT as a standalone main.
- **§P-11 payable-QR. ✅ CLOSED — QR is always a demo "SAMPLE".** User (2026-07-01): the explicit DEMO/SAMPLE-labelled
  generic QR IS the answer; no real registered-merchant DuitNow payload is wanted. Nothing further to do.
- **renderer #2 (Odoo) descriptor seam. ⛔ CANCELLED by doctrine (not blocked).** User (2026-07-01): the framework's
  end-state is that all other ERPs are ABSORBED into a single iDempiere(-2.0) base — they exist only TRANSITIONALLY as
  migration sources after a fresh migration off their legacy. So there is no permanent "2nd renderer" to abstract a
  descriptor seam for; Odoo's view types/reconciliation become fold-projections in the ONE core (`docs/internal/IDEMPIERE_2.md`
  §pivot: "a lingua franca other ERPs map onto"). Building the seam = the speculative one-consumer abstraction the recorded
  decision pre-empts. Retired. See [[project_erp_one_base_doctrine]].

### NEW BACKLOG — dictated items go here (WORK-TO-ZERO list, post-retire)
_(append new dictated items below. NOTE: the TM/variance/shopfloor + Zoom-Across arc has its OWN dedicated
 prompts — do NOT track it here: `prompts/ZOOM_ACROSS_SCOPE_SESSION.md`, `prompts/GW_HOSPITAL_SHOWCASE_SPEC.md`,
 `prompts/TM_SHOPFLOOR_COSTING_SPEC.md`.)_

- [✅] **Landing-page Save/Merge/Grid-preview arc (5 items, 2026-07-05)** — ALL 5 ITEMS DONE same day, dictated
  from a design dialogue, full reasoning trail in `prompts/GRID_PREDRAG_PREVIEW_SAVE_COMPLETEIT.md`. Built as
  4 parallel/sequenced background sub-agents in isolated `/tmp/wt-*` worktrees (item 1 turned out to be an
  already-landed false alarm; 2 and 4 fired once 1 confirmed landed). PRs: **#654** (item 1, regression witness
  only, MERGED) · **#656** (item 3, grid green/orange, MERGED) · **#657** (item 2, version-merge popup, MERGED) ·
  **#658** (item 4, Save clash+auto-heal, MERGED). Watchdog pass done: every DONE claim below has a matching `§`
  log line, no exceptions. **⚠ 3 default design picks across items 2/4 still need real user confirmation —
  see the callout right after item 4's entry**; none of them block the PRs from being reviewable/mergeable,
  they're just not hard-confirmed yet.
  1. [✅] `prompts/LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md` — DONE 2026-07-05, **false alarm**: `525eb18`
     was already squash-merged to main as `56d401d` via PR #396 on 2026-06-19 (same day) — the stale
     `origin/feat/landing-multimerge-viewer-saveopen` branch ref was just never deleted, making it LOOK
     unlanded. Independently verified live on current main: `importMultiIFC()`, Save/Open pills
     (`A.saveModelDb`/`A.openModelDb`), Doc Mode already removed from the pill registry by #396 (no new
     dependents since), outside-tap-to-close matches a later independently-reconfirmed 2026-07-02 decree,
     card/list constraint clean (`index.html` hub-card grid = fixed public sample catalog, not a user-import
     list). Live E2E witnessed headless (`§MULTI_IMPORT_DONE`/`§SAVE_DONE`/`§OPEN_DB`, SQLite-verified
     lossless round-trip 273 elements). Added a permanent regression witness (`tests/witness_landing_resurrect_e2e.js`)
     since none existed to catch this from silently rotting again — PR #654, MERGED. Stale branch refs
     (`origin/feat/landing-multimerge-viewer-saveopen`, `origin/lane/landing-multimerge-resurrect`) deleted.
  2. [✅] `prompts/LANDING_VERSION_MERGE_PROMPT.md` — DONE 2026-07-05, PR #657 (MERGED `f0b40975`).
     Similarity rule (DEFAULT pick, flagged not user-confirmed): stem match ignoring a trailing
     version-ish suffix (`_v2`, `(1)`, `-copy`, `_final`, etc — `_stripVersionSuffix()` in `import_own.js`),
     deliberately NOT `_commonPrefix()` (spec flagged that as false-positive-risky). One native `confirm()`
     popup, closest match only, no list/card. Accept pushes onto the EXISTING record's `versions[]`, bumps
     `latestVersion`, reuses the exact same `openProject()` auto-launch call already used for a fresh
     import (not a new path). Live headless-Chromium E2E (`witness_landing_version_merge_e2e.js`, real
     `<input type=file>` drops) proved all 3 paths: `§VERSION_MERGE_ACCEPT existingKey=... versions=2
     latestVersion=1`, `§VERSION_MERGE_NOMATCH`, `§VERSION_MERGE_DECLINE` (declined record left the merged
     one untouched, IDB-verified). **⚠ CORRECTION 2026-07-05 (watchdog re-check, post-merge):** this entry
     previously claimed a post-hoc fix replaced 2 duplicate 2.2MB IFC fixtures with in-memory Playwright
     buffers, "real diff now +332/-9." **That fix was never verified against the actual merged commit and
     did not land** — `gh pr view 657` + `git ls-tree origin/main` both confirm `tests/fixtures/
     SampleHouse_ARC_final.ifc` and `_v2.ifc` are still on `main` right now, byte-identical, 47318 lines /
     2,273,870 bytes each (headRefOid `f0b40975`, merged `2026-07-04T19:18:08Z`). **The 4.4MB duplicate
     fixture cleanup is STILL OPEN, not done** — folded into the next backlog item below. Lesson: a
     session's own written claim of a fix is not evidence either — the Log Mandate applies to prose in
     this file exactly as much as to a chat summary.
  3. [✅] `prompts/GRID_PREDRAG_GREENORANGE_PREVIEW.md` — DONE 2026-07-05, PR #656 (MERGED).
     Fixed the known gap first (`gmTint` never called `stretchRide`, so a hosted opening could show the
     WRONG tint mid-drag) by unifying preview+commit onto one shared `previewCommands()` pipeline
     (`bonsai_gridmove.js`) — they can no longer disagree. Added GREEN tint state + per-drag-session
     `GM._overrides` Set, ctrl+click toggles bidirectionally (`§GREEN-EXCLUDE toggle fid=2 -> green/orange`),
     `commit()` genuinely skips excluded elements (`§GREEN-EXCLUDE commit skipped=1 fid(s)`, numeric
     bbox-equal before/after). New real-browser E2E witness `witness_e2e_grid_greenorange.js` 12/12
     (actual `pg.mouse`/`pg.keyboard`, incl. a synthetic hosted-opening rider case proving the tint fix).
     All 4 named regression witnesses + the new one: 44/44 assertions, exit 0.
  4. [✅] `prompts/MODELLER_SAVE_COMPLETEIT.md` — DONE 2026-07-05, PR #658 (MERGED). Two
     DEFAULT picks (flagged, not user-confirmed): mirrors `ad_docfsm.js`'s `{ok,reason}` signal shape only
     (never calls `DocumentEngine`/`processIt`, no ERP DocStatus coupling); writes into the SAME shared
     landing-page IndexedDB catalog `versions[]`/`latestVersion`. New `sdg_save.js` (planning core) +
     `save_catalog.js` (IDB writer). **Real bug found+fixed along the way:** the original heal-op moved
     only the flagged wall, not its hosted doors riding along with it — would have manufactured a fresh
     door-out RED on the very next re-verify; fixed by cascading the heal through `SdgCascade.ridersFor`
     (mirrors `commitMove()`'s existing behavior). Live `§`-tagged evidence, 11/11 passing: RED blocks
     (`§SAVE_BLOCKED reason=RED_CLASH count=3 healed=0`), auto-heal→Clean→real snapshot
     (`§SAVE_AUTOHEAL fixed=1` → `§SAVE_REVERIFY red=0` → `§SAVE_SNAPSHOT ... created=true`), one-hop
     cascade genuinely stops (`§SAVE_AUTOHEAL_ONEHOP stoppedAt=... newIssue=[...]`, new issue surfaced not
     chased). No regression: 5 existing witnesses (gate/cascade/undo/move/export-db) all still green,
     unmodified. Honesty note carried into code+PR: `sdg_gate.js` only ever attaches `proposedDelta` to
     `abuts-realign` today, not `clearance` — heal checks for the field generically so it's forward-compat,
     but only `abuts-realign` heals in practice right now.

  **3 DEFAULT DESIGN PICKS ACROSS #2/#4 — RESOLVED 2026-07-05 in user dialogue, not re-opened as tasks:**
  1. #2 similarity rule = stem-match-ignoring-version-suffix — ACCEPTED, low risk, matches spec intent.
  2. #4 DocFSM reuse = mirror signal shape only, no real ERP DocStatus coupling — ACCEPTED, correct
     conservative default (don't entangle two unproven systems before either is battle-tested).
  3. #4 versioning = Modeller Save shares the SAME landing-page catalog — ACCEPTED on reflection: the
     Modeller/Viewer separation doctrine is about not leaking domain concepts (4D/5D) into the authoring UI,
     not a ban on shared infrastructure (a version catalog is closer to a shared filesystem than a shared
     domain model). No follow-up needed.

- [✅] **Next batch (2026-07-05) — split into 2 independent assignments, per user's own grouping (2026-07-05):**
  ALL 3 sub-items ✅ DONE 2026-07-05: A.1 Teams overlay E2E → **PR #661 MERGED**; B.2 PR657 fixture cleanup →
  **PR #660 MERGED**; B.3 parametric-depth recon → findings in `prompts/PARAMETRIC_DEPTH_RECON_FINDINGS.md`.

  **Assignment A — standalone session, Teams overlay is unrelated enough to the rest to run alone:**
  1. [✅] `prompts/TEAMS_OVERLAY_LIVE_E2E_TEST.md` — DONE 2026-07-05, **PR #661** (bim-ootb, MERGED).
     §0: clarified the Modeller `#b-guide` "Teams" line to explicitly distinguish it from HBA (a separate
     Viewer-only overlay, doesn't exist in the Modeller) — the confusing overlap was under-specification,
     not a doctrine drift; reconfirmed panels.js/Teams seam split still holds in code. Traced the real
     dual-user workflow FIRST per spec: fork/edit/post-it/bundle/gate/merge has **zero clickable UI**
     anywhere (`teams.html` renders a hardcoded static scenario; every S1-S12 "DONE" witness calls engine
     functions directly in Node, never through rendered DOM) — scoped the E2E to the one piece with real
     production UI, the live Modeller presence embed (`#b-teams`→`#teams-pill`). New
     `witness_e2e_dual_presence_modeller.js` drove TWO real Playwright `BrowserContext`s through the actual
     click path and found+fixed a real bug: `modeller/teams_embed.js` sent its own heartbeat but never
     subscribed to the bus (`window.__teamsPeerBeats` read every render, written nowhere) — fixed via
     `_conn.bus.on()` + live re-render. Also empirically proved+documented a platform fact (not a bug):
     `BroadcastChannel` never crosses separate `BrowserContext`s, so two genuinely separate real users can
     never see each other's presence this way (only two tabs of one profile) — and named a second real gap
     left un-fixed: a peer who joined before your subscription stays invisible until they re-announce (no
     periodic heartbeat/replay exists). 11/11 new witness green; full teams/ suite (26 files) +
     wire_teams_pill/ui_consistent/tabs_consistent/find_placement_dom/wire_teams_embed_modeller unaffected.
     Verdict: Teams overlay's **presence** sub-feature is now proven for the one real dual-user path it
     actually supports (same-profile multi-tab); the branch/postit/gate/merge workflow LOOKS shipped but
     has no real user path to test at all yet (engine-only).

  **Assignment B — one session, sequential (small unrelated cleanup, then the recon; NOT a parallel fan-out):**
  2. [✅] `prompts/PR657_FIXTURE_CLEANUP.md` — DONE 2026-07-05, **PR #660** (bim-ootb, **MERGED** `a05ea60`,
     confirmed via `git ls-tree origin/main` — both fixtures gone from `main`). Both duplicate
     fixtures `git rm`'d (`-94636` lines, 4.4MB); `witness_landing_version_merge_e2e.js` now reads the real
     corpus `IFC/SampleHouse_ARC.ifc` buffer ONCE and drops it under renamed in-memory Playwright file objects
     `{name,mimeType,buffer}` for the `_v2`/`_final` cases (the §VERSION_MERGE similarity check keys on FILENAME
     stem only, never content). Witness re-run GREEN, real §-log not exit-code (Log Mandate): `§E2E_RESULT
     pass=true`, all 3 paths fired — `§VERSION_MERGE_NOMATCH`(base+Duplex) · `§VERSION_MERGE_ACCEPT versions=2
     latestVersion=1` (with `§DISC_OVERRIDE file=SampleHouse_ARC_v2.ifc` proving the renamed buffer drove the
     popup) · `§VERSION_MERGE_DECLINE key=SampleHouse_ARC_final.ifc` + `§E2E_STEP4_MERGED_UNCHANGED versions=2`.
     Diff = fixtures deleted + witness +7/-3, zero new binary blobs.
  3. [✅] `prompts/PARAMETRIC_DEPTH_RECON.md` — DONE 2026-07-05 (recon-only, no code). Findings written to
     `prompts/PARAMETRIC_DEPTH_RECON_FINDINGS.md`; Sonnet single-pass as reassessed, and it DID catch a
     UBBL-shape landmine. Answers: **Q1 BLOCKED-on-aggregation** (raw per-instance door bboxes 0.147–1.86m in
     `component_definitions` (129 rows), but no min/max ever persisted — small GROUP BY pass owed before an LOD
     variance axis is "real"). **Q2 MEASURABLE** (Building→Storey→Room populated corpus-wide in all 5 DBs;
     `spatial_structure.type` only ever Storey/Space, nothing coarser incl. Terminal; `city_index*.db` are
     0-byte stubs → free-lasso only adds value SUB-room). **Q3 MEASURABLE** (material real+populated:
     `ad_element_placement` 62%, `material_layers`/`surface_styles`/`elements_meta.material_*` real) —
     **⚠ LANDMINE:** `building_type` double-labels the Terminal (`SJTII_Terminal` 81% populated + registered vs
     orphaned `TERMINAL` 1.6% + no `ad_building` row) → build any material gate against `SJTII_Terminal`, never
     `TERMINAL`. **Q4 BLOCKED-on-group-linkage** (`placeAssembly` modeller.html:2690 commits each furniture leaf
     as an unlinked `GEOM_INSERT`; the fix is the already-proven `commitSeedGroup` at modeller.html:3403 —
     "dining set" tier stays skipped until applied). Build-scoping: §1 needs mining first · §2/§2a/§2b/§3-loose/§4a
     buildable today · §3 dining-set + §4b need the two named gaps. See [[project_parametric_depth_recon_landmine]].
     **Model reassessed 2026-07-05: Sonnet 5 was sufficient, not Opus** — — the prompt's own
     rationale cited `UBBL_RULES_RECON.md` as needing Opus-grade judgment, but that recon was actually run
     on a Sonnet-tier agent and still caught the 3-inconsistent-numbers/mislabeled-source finding. This
     recon's 4 questions are internal schema/row-count/linkage checks (column exists? populated? shared
     op-id?), not cross-source authority adjudication like UBBL's KPKT-vs-config reconciliation was — a
     mechanical-verification task Sonnet already handles well. The load-bearing constraint is **sequential,
     not parallel** (regardless of model) — keep that.
  If any of these should be different, say so — each is an isolated, easy re-diff on its own PR (#657/#658).
  5. [✅] `prompts/UBBL_RULES_RECON.md` — DONE 2026-07-05 (recon-only, no build, as scoped). User confirmed
     demo/mockup depth (not real-compliance). Findings: `duplex_rules.db`/`terminal_rules.db` are 100%
     empirical, zero UBBL content (clean). BUT found a real landmine the recon's own premise missed:
     `library/component_library.db` + `config/spacetypes.yaml` + `config/profiles/malaysian_residential.yaml`
     carry **3 mutually-inconsistent "UBBL bedroom min area" values already in the repo (6.5 / 9.0 / 9.3 m²)**,
     one of them (6.5m²) actually cited to US IRC not UBBL — mislabeled. Verified real source: KPKT-hosted
     UBBL 1984 text, cross-checked 2 clauses (By-Law 39 lighting/ventilation, By-Law 42 room min areas —
     11/9.3/6.5/4.5m²) against an independent secondary source. Extraction-readiness: room area+height
     measurable today (Duplex only); egress/setback/fire-rating all blocked on missing extraction (named
     per-category in the recon). Follow-on build spec written: `prompts/UBBL_RULES_GATE.md` (single demo
     check: room area/height vs. verified By-Law 39/42 thresholds, Duplex only, explicitly labeled
     "indicator, not a compliance verdict"). See [[project_ubbl_recon_landmine]].

  **Watchdog cross-check 2026-07-05 (independent, not trust-on-recap):** re-verified #660/#661/#664 against
  actual `gh pr view`/`git ls-tree`/`gh pr diff` output, and read `PARAMETRIC_DEPTH_RECON_FINDINGS.md` in full.
  All claims above check out as written — no discrepancies this round (contrast the earlier #657 false
  "already fixed" claim, which watchdog caught and this file now shows corrected above). PR #661 in particular
  exceeds spec: real bug fix, honest scoping, a platform constraint documented rather than hidden.

- [✅] **`prompts/SCALE_AND_UX_SWEEP.md`** — DONE 2026-07-05 (bim-ootb `lane/watchdog-scale-ux-sweep`, commit
  `b914587`, **pushed, not merged** — 4 real follow-up findings need their own decisions before this should land
  on `main`, see the hardened backlog items right after this entry). Renamed from `WATCHDOG_SCALE_AND_UX_SWEEP.md`
  for role clarity ("watchdog" names a review role, not a task file); the background session was redirected
  mid-flight and picked the rename up cleanly. **Watchdog re-check 2026-07-05 (independent, not trust-on-recap):**
  panels.js diff confirms 7/7 claimed `R.register` removals real; all 4 claimed witness files exist on the
  branch; the Q1 migration commit is real and correctly cites its recon source. **2 gaps found and fixed by this
  watchdog pass:** (1) `SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md` had only been written into the bim-ootb
  worktree's own `prompts/`, unreachable from this file's `prompts/...` pointer — copied into bim-compiler
  `prompts/` to match the `PARAMETRIC_DEPTH_RECON_FINDINGS.md` precedent. (2) the session's own writeup named
  only 3 findings; reading the raw log directly (`modeller/tests/logs/scale_check_terminal.log`, Log Mandate)
  surfaced a 4th, unflagged one — see Finding 4 below.
  **2 governance decisions (both implemented, not just decided):**
  1. Teams presence architecture — **(a)** shipped: guide text now documents the same-tab-multi-profile
     `BroadcastChannel` limitation honestly; no server-relay built. `witness_guide_text_updates.js` 5/5 PASS.
  2. UBBL indicator UI location — decision recorded (reuse the gate's toast+`_emis` highlight, bake the
     disclaimer into the toast text); nothing built yet since `UBBL_RULES_GATE.md`'s actual check hasn't landed
     anywhere in code (confirmed, not assumed).
  **§1 scale checks — all 3 measured at real Terminal scale (~35k elements), 2 surfaced real unfixed findings
  (full detail: `prompts/SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md`):**
  - Grid green/orange tint (#656): **degrades** — `§SCALE_CHECK feature=grid_tint elements=35000 ms=1513.8
    avgMs=1175.4 frames=15`, ~1.2s/frame sustained during drag. Root-caused (rebuilds the whole attach-map from
    scratch every pointermove frame) but NOT fixed — needs `bonsai_gridmove.js`'s `previewCommands()` restructured
    to cache the attach-map once per drag-session, a real change not a threshold flip. **NEW backlog item, not
    yet started.**
  - Save/auto-heal (#658): **over budget + a genuine new failure mode** — `§SCALE_CHECK feature=save_autoheal
    elements=35000 ms=57033 findings=5`. The heal itself is correctly batched (one `commitGesture`); the 57s is
    2 full-scene gate evaluates + the fold. Worse: at Terminal density, one heal move landed in new contact with
    an unrelated 3rd element, escalating to a fresh RED and blocking Save anyway (`§SAVE_BLOCKED reason=RED_CLASH
    detail=clash(129,34451) healed=5`) — small fixtures never have enough neighbour density to hit this. Needs a
    real design call on `runSave()`'s escalation behavior. **NEW backlog item, not yet started.**
  - Teams presence (#661): confirmed fine, peer-bounded not element-bounded (`§SCALE_CHECK feature=teams_presence
    elements=35000 peers=300 ms=1.40`).
  - **Incidental Finding 3:** the same run surfaced a real TOCTOU-shaped id-collision race (27x `UNIQUE constraint
    failed: kernel_ops.id`) during grid-drag→STR-rewalk — matches this project's own standing pattern (memory
    `feedback_toctou_race_scrutiny_pattern`, 2 prior confirmed hits). Not fixed, needs a read-the-code pass on
    the STR-rewalk commit path. **NEW backlog item, not yet started.**
  **§3 UX items:** discoverability hint ✅, Save-blocked-UX now selects+flies to the offending element ✅
  (`witness_e2e_save_blocked_focus.js` 3/3, no regression on the 11/11 save suite), UBBL decision ✅ (above),
  HBA CCTV rAF bug ✅ fixed (`witness_hba_iot_scanline_fix.js` 3/3, dead→39 rAF calls), Terminal double-label ✅
  reconciled with root cause (superseded legacy extraction pass, confirmed dormant — zero code references the
  orphan string — documented in `PARAMETRIC_DEPTH_RECON_FINDINGS.md`, deliberately not bulk-deleted from the
  shared db), duplicate pill registration ✅ (all 7 dead pre-#S280 `R.register` calls removed, not just `shadow`
  — `§PILL_AUDIT ids_checked=7 collisions=[xray,section,sunglass,fly,shadow,bg,grid2d]`) — **this unblocks
  `PILL_DRAWER_REORGANIZATION.md` below.**
  **§4 aggregation fixes:** Q1 ✅ bim-compiler `migration/W024_component_dimension_range.sql`, pushed as branch
  `lane/q1-component-dimension-range` (**not merged** — the main working tree's `library/component_library.db`
  already carries an unrelated uncommitted edit from 2026-07-04, needs manual reconciliation before merge, not
  conflated here). Q4 ✅ bim-ootb `placeAssembly()` now uses `commitSeedGroup`, `witness_e2e_placeassembly_group_commit.js` 5/5 PASS.
  **Net: both branches pushed for backup/review, neither merged — 4 new real findings (grid_tint perf,
  save_autoheal escalation, id-collision race, autosave-quota data loss) need their own follow-up sessions before
  this work is considered fully closed out.**

  **HARDENED FOLLOW-UP ITEMS (converted from prose above into real tracked `[ ]` entries so WORK-TO-ZERO doesn't
  skip them — full detail in `prompts/SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md`):**
  - [✅] **Grid-tint perf restructure** — DONE 2026-07-05 (`0dcbe8a`). `bonsai_gridmove.js` now caches the built
    `GridKinematicEngine` + attach-map + mesh/box-by-fid maps ONCE per drag-session (`beginDragSession()`/
    `endDragSession()`, wired at gridline-grab/`exitGridMove()`) instead of rebuilding on every pointermove frame.
    **Independently re-verified (fresh re-run by this watchdog pass, not just the fix agent's own report):**
    `§SCALE_CHECK feature=grid_tint elements=35818 ms=14.4 avgMs=7.2 frames=15` — was `avgMs=1175.4`, a real
    ~163x speedup, confirmed on a full fresh Terminal-scale run, not trusted from the recap alone. Correctness
    unchanged per `witness_e2e_gridstretch(_multi).js` (21/21), `witness_e2e_grid_greenorange.js` (12/12).
  - [✅] **Save/auto-heal over-budget + escalation design call** — DONE 2026-07-05 (message-clarity part only,
    code in `0dcbe8a`, witness in `f13e4bf`). **Design question asked twice, timed out both times (no user
    response):** keep block-whole-Save vs. roll back just the offending heal. **Default applied (flagged, not
    hard-confirmed): KEPT blocking** (lower-risk, no unproven partial-rollback complexity) — `runSave()` now
    tracks which elements the heal itself moved and distinguishes heal-induced RED from pre-existing RED in both
    the `§SAVE_BLOCKED_REASON heal_induced=true/false` log and the user-facing toast (`"⛔ Save blocked —
    auto-heal fixed N issue(s) but created a new clash while doing so (X vs Y) — please resolve manually"`
    instead of a generic message). Witnessed `witness_e2e_save_blocked_heal_induced.js` 7/7 PASS, no regression
    on `witness_e2e_save.js` 11/11 (independently re-run by this watchdog pass) or `witness_e2e_save_blocked_focus.js` 3/3.
    **STILL OPEN, not done:** the 57s wall-clock itself (2 full-scene `SdgGate.evaluate` calls + one fold) was
    never profiled down to WHERE the time goes — this needs its own follow-up session if the wall-clock budget
    itself (not just the messaging) needs to come down.
  - [✅] **STR-rewalk id-collision race (TOCTOU-shaped)** — DONE 2026-07-05 (`ce61f2f`). `str_walker_outliner.js`'s
    `wrapGridMove()` used to fire the STR-rewalk's ~30 ops as individual unawaited `oplog.commit()` calls inside
    a synchronous `forEach` (never batched), racing `commitGroup`'s optimistic `nextId` snapshot. Now collects
    the ops and commits them as ONE signed group via `commitGesture` — the same shape `bonsai_gridmove.js`'s own
    stretch-ride commit and `_commitDiscWalk` already use. **Independently re-verified** (fresh re-run):
    `§STRWALK_RACE_FIX ops=31 collisions_before=27 collisions_after=0` — zero `UNIQUE constraint failed`/
    `§KRN_GROUP ROLLBACK` lines anywhere in a full fresh Terminal-scale run (was 27).
  - [✅] **Autosave silently fails at Terminal scale (data-loss risk) — WATCHDOG-ADDED, not in the original
    session writeup** — DONE 2026-07-05 (`c846f5f`). `bonsai_oplog.js`'s `_save()` now falls back to IndexedDB
    (new `oplog_fallback` store, reusing the existing `bim_ootb_cache` IDB pattern) when `localStorage.setItem`
    throws `QuotaExceededError`, with a one-time toast on first fallback and length-based restore precedence on
    boot (never silently drops the more-complete copy). Witnessed `witness_e2e_autosave_idb_fallback.js` 8/8
    PASS (real round-trip: edit → simulated reload → op-log length matches, `source=idb` in the restore log) —
    **independently re-run by this watchdog pass**, all 8 assertions confirmed real. Also fired naturally
    throughout a full fresh Terminal-scale re-run (`§AUTOSAVE_FIX path=idb_fallback bytes=25579520+ key=mo_Terminal`,
    repeated correctly across dozens of real commits, not a one-off).
  **Watchdog note on this whole 4-item follow-up batch:** did NOT just trust the fix agent's own reported
  numbers — independently re-ran the Terminal-scale witness suite fresh (`witness_e2e_scale_check_terminal.js`,
  `witness_e2e_save.js`, `witness_e2e_autosave_idb_fallback.js`) myself and confirmed the exact same evidence
  from a clean run. One pre-existing, unrelated `RangeError: Too many properties to enumerate` reproduces
  identically in both the pre-fix and post-fix logs at the same point in a later, unrelated test step — confirmed
  NOT a regression from these fixes (present before any of this session's changes), left un-investigated as
  genuinely out of scope for this batch — flag if picked up later.
  - [✅] **Merge decision — bim-ootb `lane/watchdog-scale-ux-sweep` (`b914587`):** DECIDED — opened as
    **bim-ootb PR #665** for human review/merge (not auto-merged). The 6 shipped UX/audit fixes are independent
    of the 3 unfixed perf/race findings, don't regress anything, and unblock `PILL_DRAWER_REORGANIZATION.md` —
    no reason to hold the PR open pending the follow-up fixes above (those are landing as NEW commits on the
    same branch, which the open PR will pick up automatically).
  - [✅] **Merge decision — bim-compiler `lane/q1-component-dimension-range` (`3e50bc7c9`):** RESOLVED — the
    "needs manual reconciliation" concern turned out to be a non-issue. The main working tree's unrelated
    pending 2026-07-04 edit only renames `ad_geometry_map`→`I_Geometry_Map` and adds `M_Product_Image` —
    orthogonal tables. Verified directly: copied the live dirty `component_library.db`, re-ran the Q1 migration
    against it, got the identical correct result (`§Q1_RECONCILE_CHECK door=(129, 0.147, 1.86)`, both the old
    and new table names coexist cleanly). No actual reconciliation work needed — ready to merge whenever the
    other pending edit itself gets committed, in either order.

- [✅] **`prompts/PILL_DRAWER_REORGANIZATION.md`** — DONE 2026-07-06. Grew well past the original spec through
  a live design dialogue with the user (superseded 3 rounds of correction, fully reconciled in-file) — final
  shape is **4 real drawers**, not the 2 first scoped here: Visual FX (Palette-hosted: Night/Shadow+Ground-merge/
  Reverse-bg/Audio), Camera/View (new camera-icon host: Feather/Reset/Pivot), Navigate (new Sailboat-icon host:
  Find/World-History/Home/Walk), Inspect (new drafting-compass-icon host: Measure+Clash/X-Ray(now Bone icon)/
  Section/Time-Machine/4D-5D/Fly). Rail cut from 20 standalone icons to 9. Plus, folded in during the same
  session: Alt+X retired into a 3-state Alt+Z cycle (Off→X-Ray→Bbox→Off), Screenshot/Record/2D deleted (dead,
  confirmed unreferenced), 6 pre-existing `isActive` state mismatches fixed (icons that could never highlight —
  e.g. X-Ray checked `A._xrayOn`, its own toggle set `A.xrayOn`), floating-panel overlap fixed (5 panels were
  all hardcoded to the same top-right spot, covering the pill rail itself), pill-rail auto-reshuffle-on-click
  killed (`_bumpAction` removed + a version-stamped localStorage migration so already-scrambled browsers self-heal
  on next load), and a real perf fix the user flagged mid-session: `focusElement()`'s auto-obscure-on-select
  always ran full per-material X-Ray — now routes through the cheap `filterByGuids` (visibility-only, same
  primitive Alt+X's ghost mode already used) above `activeBuildingTotal > 50000`, matching `time_machine.js`'s
  own existing perf-cliff threshold.
  **Shipped as bim-ootb PR #667 → auto-merged after only its FIRST commit** (this repo's CI has an
  auto-merge-on-green step that fired immediately — a real process trap, not a false alarm: every commit pushed
  to that branch AFTER the auto-merge landed on a branch whose PR had already closed, never reaching `main`).
  **Caught and reconciled same-session**: all 4 orphaned commits cherry-picked cleanly onto fresh `main` →
  **PR #669** (merged), then the perf-fix + auto-reshuffle-kill round → **PR #672** (merged). Net: nothing lost,
  but flag this CI behavior for any future multi-commit branch on this repo — check `gh pr view <n> --json
  mergedAt` before assuming a branch is still open to push more commits to.
  Live-verified throughout (headless Playwright, real clicks/keypresses, not code-reading alone) — rail count,
  drawer open/close/no-dual-fire, all 6 `isActive` fixes on/off, Alt+Z 3-state cycle incl. real keypress + the
  lazy-loaded ghost-mode async path, 5-panel no-overlap, Shadow+Ground's 3 static sample-image boxes + single-
  box-highlight cycle, the `>50k` perf-fix branch (forced via `activeBuildingTotal` override — no real 50k+
  fixture was loaded, branch logic confirmed via the `mode=filter-cheap(>50k)` log + a live selection-highlight-
  overlay visibility check), 0 console errors across every check.
  **Diagnostic-only, not yet resolved:** a long-reported "Find box appears on its own at onset" bug could not be
  reproduced synthetically (cold load, simulated back/forward, reload all stayed clean) — added `§FIND_VIS_TRACE`
  (a `MutationObserver` stack-trace logger on both `#find-panel` and the legacy `#search-box`) so the next real
  occurrence in the field pins down the actual trigger instead of continuing to guess.

- [✅] **Desktop/mobile "installer" — RESOLVED 2026-07-05, user accepted one-time online touch.** Real ask
  (dialogue in `prompts/OFFLINE_GITHUB_RELEASE_BUNDLE.md`, superseded — see its corrected §THE REAL ASK note)
  was a home-screen/desktop icon that launches app-like, with local Drop Import/Export working offline after.
  That IS the existing PWA install flow (`viewer/scene.js` §S283 — `beforeinstallprompt`, full asset+building
  download, cache verify, `navigator.storage.persist()`, native install prompt): Chromium's install already
  plants a real desktop icon + standalone window, same mechanism covers mobile Add-to-Home-Screen. User
  confirmed (2026-07-05): one-time online bootstrap is fine, "we can explain to users why." No
  Electron/Tauri/zip-installer path needed — CANCELLED, not just deferred.
  - Recon (background agent, sourced not assumed): NO evidence anywhere (git log, PROGRESS.md, docs) supports
    an earlier "past attempts showed it's unreliable" claim — §S283 has a real `setOffline(true)` reload test
    (`tests/specs/38-offline-pwa.spec.js`, 9/9 Playwright at authorship). The one real gap: those offline specs
    weren't wired into CI (`ci.yml` only ran `s274-golden-path`) — still NOT wired in, see honest note below.
  - **§OFFLINE-GATEWAY-LEAK found+fixed+PR'd** (user's own question: "why does it still leak online when DBs
    are already in IndexedDB?"): 3 real bypasses of the sw.js cache-first gateway. **bim-ootb PR #666**
    (`lane/offline-gateway-leak-fix`, commit `cd36c07`):
    1. `viewer/sw.js` — `sfx.json` was hardcoded network-first ("during tuning" — stale debug carve-out).
       Now precached like every other config file (`CACHE_VERSION` bumped v740→v741).
    2/3. `viewer/streaming.js` (single-DB size check + split-DB detect) and `viewer/city.js` (archetype
       split-DB detect) each fired an unconditional network HEAD probe *before* checking IndexedDB — now
       check `A._checkCache()` first, only probing the network on an actual cache miss.
    - **Witness (real, run twice — first attempt caught a false pass):** new
      `tests/witness/witness_offline_gateway_leak.js` loads Duplex online, then uses `context.route()` to make
      `sfx.json` network-unreachable and confirms the route handler NEVER fires (proves the SW never attempts
      network for it once precached — `page.on('request')` alone was tried first and rejected as a metric,
      since it also fires for pure-cache-served responses and would have under-proven the fix). §-log:
      `PASS2_SFX_NETWORK_TOUCHED=false`, `§DB_SIZE_CHECK src=cache` (was always `src=network` pre-fix),
      `PASS3_OFFLINE_RENDERED=true` after `context.setOffline(true)` + reload. Exit 0.
    - **CI wiring — deliberately NOT added, flagged not faked:** tried wiring `38-offline-pwa`,
      `s283-pwa-install`, `s284-offline-pwa-ifc` into `ci.yml`'s `e2e-tests` job, then reverted. Two pre-existing,
      unrelated problems surfaced: (a) `38-offline-pwa.spec.js`'s `VIEWER_URL` hardcodes `/dev/index.html`, a
      personal-machine deploy-layout path that doesn't exist in a normal checkout — already documented as
      **Issue 4** in `GH_DEPLOY_ISSUES.md` (2026-05-27), predates this session; (b) `s283-pwa-install.spec.js`
      uses the *correct* `/bim-ootb/viewer/viewer.html` path yet still fails on `S283.4 beforeinstallprompt
      listener wired early` and others — looks like a headless-Chromium PWA-installability limitation, not
      a path bug. Wiring broken specs in just to "close the gap" would make CI red for reasons unrelated to
      this fix, so left undone. **Genuinely open follow-up:** fix `38-offline-pwa.spec.js`'s hardcoded path
      (one-line, same fix GH_DEPLOY_ISSUES.md already prescribes) + investigate whether s283's failures are
      fixable or need documented `test.skip`/xfail — then CI-wire all three.

- [✅] **Retire `viewer/2d.html`** — RESOLVED 2026-06-27 (user call): the viewer-side 2D/red-pill work is
  **DEPRECATED by the Modeller/3DGrid — leave it as-is** (dead-weight but harmless, lazy/new-tab only; nothing
  to learn from it). Do NOT spend effort on the mechanical retirement. Focus shifted to Modeller feature UI polish.
  (Step-1 witness below still stands as the record of WHY a naive retirement was wrong.)
  **Step-1 witness: `grid_overlay.js` does NOT cover what `2d.html` serves.** EXTRACTED (not assumed): `grid_overlay.js` has ZERO DXF capability
  (grep dxf|bimsrc|aia-layer|drag-drop = 0); `2d.html` is a standalone DXF/CAD plan viewer — parse DXF,
  AIA layers panel, BIMSRC xdata→GUID correlation, drag-drop external DXF (per `tests/specs/14-2d-plans.spec.js`).
  They overlap ONLY on the 2D toolbar button (already routed to the in-scene grid overlay); `2d.html` is now
  reachable only via the `main.js:264` error-fallback + direct URL. So retiring it DELETES the DXF-import
  capability, not just dead weight. **THE ONE QUESTION (user owns):** accept losing the standalone DXF/CAD
  floor-plan viewer (drag-drop DXF, AIA layers, BIMSRC correlation), or keep `2d.html` until a replacement
  DXF path exists? If "accept loss" → the 4 steps below are mechanical and ready to run.
  Last touched 2026-05-23. **NOT a file move** — it is still wired live, so retirement = 4 steps,
  all in a `/tmp/wt-*` worktree (shared `~/bim-ootb` checkout is hook-blocked):
  1. CONFIRM `grid_overlay.js` fully covers the 2D-plan cases that `2d.html` served (don't assume — witness it).
  2. Delete the fallback in `viewer/main.js:242` (`open2DPlans()` → `window.open('2d.html?...')`).
  3. Remove `'2d.html'` from the precache list in `viewer/sw.js:61` **and bump `CACHE_VERSION`** (sw.js = conflict magnet;
     dropping a file without de-listing → SW install 404s).
  4. Retire/redirect the ~30 tests in `tests/specs/14-2d-plans.spec.js` + the fallback-guard in `28-grid-overlay-init.spec.js`.
  THEN `git mv viewer/2d.html` to an archive dir. Witness: `§2D-RETIRE main-fallback=gone sw-precache=gone tests=retired`.
  Rationale: 434 KB / 47k-line single inline block = the codebase's biggest under-modularized file; dead weight once
  `grid_overlay.js` confirmed. Costs the running app nothing today (lazy, new-tab-only), so LOW urgency — purely declutter.

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
