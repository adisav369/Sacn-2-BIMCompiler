# PROGRESS — Current Development State

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and `prompts/`. Keep this file under 80 lines.

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — S190 fleet: 116/157 PASS, 4 ALL GREEN (BR,MO,RL,WI). 21 buildings. 9-gate system.

| PFX | EL | GATES | Notes |
|-----|----|-------|-------|
| BR | 33 | 9/9 | ALL GREEN |
| MO | 2791 | 9/9 | ALL GREEN |
| RL | 1 | 9/9 | ALL GREEN |
| WI | 1 | 9/9 | ALL GREEN |
| DX | 1169 | 8/9 | MetadataMissing (IfcOpeningElement) |
| SH | 65 | 8/9 | MetadataMissing (generative MEP) |
| TE | 48428 | 8/10 | C8 mesh diversity, GEO no pairs (federated) |

**Pipeline:** 11 stages. 77 verbs. 7403 products (ERP.db). 4-DB architecture.

## Backend lane — DATA half (D2/D3/R2) + ENGINE-SEAM half (C0 + readPostings) DONE (2026-06-03, on `full`, NOT deployed)

Committed `a541a873` + `30a1e1a6` (origin/full). bim-compiler = canonical data lane ([[project_repo_split_lanes]]); outputs LOCAL, deploy HOLD-for-GO. Resume: `prompts/BACKEND_LANE_S2.md`.

**S2 ENGINE-SEAM (this session — unblocks the 3 frontend lanes; spec-first, witness-led):**
- **C0 — five-call seam** (`scripts/erp_seam.js` `makeSeam`; witness `scripts/poc_seam.js`→`build/erp/poc_seam.log`, **ALL PASS**). THIN wrapper, no new engine logic: `read`(+allowOrgs scope I3) · `dispatch`(role+owner gate→`erp_kernel.dispatch`) · `manifest`(D2 json) · `verbs`(`erp_kernel.handlers` reflection) · `verify`(`replay`×2). `§SEAM surface=read,dispatch,manifest,verbs,verify`; `§SEAM dispatch …before=DR after=CO replay rebuildA==rebuildB agree=Y` (I4); `§SEAM gate owner-gate rejected=Y role-no-grant rejected=Y`; `§SEAM read role-orgs=[11] in-scope=8 | [50000] out-of-scope=0`. Spec `docs/ENGINE_CONTRACT.md §6.1`. (+`erp_kernel.handlers` read-only registry reflection.)
- **readPostings (§13.7)** (`scripts/erp_postings.js`; witness `scripts/poc_postings.js`→`build/erp/poc_postings.log`, **ALL PASS**). Role-gated read-fold, exact shape `{visible,posted,lines[],balanced,source,coverage,note,reason}`. `§POSTED-READ role=102 isshowacct=Y posted=Y rows=3 balanced=Y source=oplog coverage=partial` (real GW accts 12110/41000/21610); `§POSTED-GATE role=103 isshowacct=N → visible=N rows=0` (zero leak) + out-of-scope; `§POSTED-COVERAGE` absent/partial/complete. Spec `docs/PLUGIN_ARCHITECTURE.md §13.7a`.
  - ⚠ `complete` UNREACHABLE on bundled data: `fact_acct` is TOTALS (`factHasRecordKey=N`) → per-record gate needs §13.6 re-extract WITH `ad_table_id`/`record_id` (NOT done — non-speculative). Branch proven via labelled shape fixture (`fact=fixture realdata=N`) + off-by-1c discrimination (mismatch→partial).
- **⚠ JOINT re-freeze flag** (do NOT resolve solo): §1 `gravityRank` vs D2 manifest `menuGroup` — record-panel co-ratification. `§SEAM-FROZEN` left open.
- **D2** — 15 closed T2 module shards over tree-10 groups (`scripts/build_all_shards.js`): `§SHARD-SET tiers=[T0:8.2MB, T2:15] none-oversized=Y total=10.46MB`. `buildModule` now subtracts T0 dictionary + defers `_trl`→T1 (was 88MB/3 oversized). Per-shard coverage over T0∪shard (`erp_shard_coverage.js`) `§SHARD-COVERAGE-SET all-dangling=0=Y` (13 absent-in-source tabs = `ad_full.db` gaps, reported not invented). Deterministic manifest (`build_shard_manifest.js`) `§SHARD-MANIFEST tables=660 hash=2c7c4ecef5802987`. Spec `docs/ERP_SHARD_GENERATOR.md §8a`.
- **D3** — `--rekey-client 11 12` clones GardenWorld access subgraph (+10M PK offset, coexist): `§CLIENT-SWITCH client=12:GardenWorld roles=4 windows=414`, gate `dangling=0`. §8b · [[project_erp_shard_rekey]].
- **R2 fact_acct** — `scripts/extract_fact_acct.sh` real PG extract (idempiere_test, client 11) → `glassbowl_data.db`: `§EXTRACT fact_acct=300 Dr=Cr=46574.97`; `test_report_fin.js` Trial Balance/P&L ALL PASS. Already in bim-ootb consumer (byte-identical). TOTALS extract (no record-ref cols) — per-record fold needs re-extract.
- **Open (next):** C0+readPostings now BUILT (above) → the 3 frontend lanes (Tour coverage-marker, record-panel write-seam, Accts-Posted) are unblocked, awaiting host-conformance STEP-0. Carried data-lane items: manifest `resident` regen vs seed-demo before deploy; 13 absent-in-source PG views (migrate doesn't extract); §13.6 fact_acct re-extract WITH record-ref cols (only when per-record `complete` needed). Engine deliverables LOCAL — deploy HOLD-for-GO.

## Active Work — Browser BIM OOTB

**IDEMPIERE TOUR GUIDE — LIVE BIND VERIFIED (2026-06-03):** the SINGLE `help_overlay.js` now rides idempiere, NO FORK, bound to the real host contract. (spec: `prompts/IDEMPIERE_TOUR_GUIDE.md` + `docs/TourGuideHostContract.md`; lane: TourGuide overlay-aspect, [[project_tour_guide]])
  - **Lift:** `build/erp/help_overlay.js` factored mount + nav (`trace/focus/openTab`) + locate (`has/locate`) + audio (`jive`) into a host `ADAPTER` whose **defaults reproduce glassbowl verbatim** (un-init'd page = diff=0); `window.__help.init({host,nav})` rebinds for idempiere. Pure COACH core byte-identical → W-HELP-COACH 21/21 + W-HELP-NEXTGATE 11/11 still green.
  - **Built (overlay side):** `build/erp/help_idmp_keymap.json` (6 O2C keys → AD windows, honest `coverage:partial`), `build/erp/help_idmp.js` (init-only adapter, ZERO coach logic, consumes `window.IdmpHost.*`), `docs/TourGuideHostContract.md` (host-contract spec).
  - **Witness:** `scripts/test_tour_idempiere.js` **W-TOUR-IDEMPIERE 24/24** (`build/erp/tour_idempiere_witness.log`) — `§TOUR forked=0 mounts=2 keysMatched=Y showme-nav=via-globals`, `§TOUR-O2C steps=6 #80001`, `§TOUR-ALIGN orphan=0/0`, `§SEAM tour-writes=0`, `§TOUR-DEGRADE coverage=partial`. DOM-shim proves ShowMe drives `IdmpHost.trace/focus/openTab` (not chrome internals).
  - **LIVE BIND DONE (frontend shipped the host contract on `bim-ootb/erp/idempiere.html` 06:45 — `IdmpHost`+tagging+inlined keymap+`#idmp-content`+scripts+`§SEAM-FROZEN`):** `forked=0` (deployed overlay BYTE-IDENTICAL to source), inlined keymap verbatim, **W-TOUR-BIND 11/11** (`scripts/test_tour_idmp_bind.js`→`build/erp/tour_bind_witness.log`) binds the unforked overlay against the host's ACTUAL `IdmpHost` — `ShowMe(c_order)`→real `focus→openWindow("Sales Order")` #80001 + `trace`, badge gated on real `[data-ad-table]`. NO chrome edits.
  - **Open:** no live-browser pixel render (no puppeteer/Playwright; frontend screenshots from pipeline). Glassbowl (`docs/`) still pre-lift → converges on its own deploy GO (behavior diff=0). Coverage stays `partial` (backend `readPostings` spec-not-built; `complete`=real-instance install, never POST).

**GLASSBOWL CRUD PROCESS + GUIDE POLISH (2026-06-01): DEPLOYED LIVE (full `7ad55e71`, gh-pages, sw `glassbowl-offline-v5`).** (specs: `prompts/READSHOWME_DYNAMIC_SPEC.md` §ShowMe-as-coach, `prompts/CRUD_OVERLAY.md` §Process; next session: `prompts/GUIDE_SHOWME_PROCESS.md`; memory: [[project_glassbowl]])
  - **CRUD Process/DocAction verb (E2 dry-run):** `crud_ops.json` `verbVocab += process` + `docAction{action,from,to,requires[],oracle}` on the 4 lifecycle docs (real `M*.completeIt()`); `c_allocationline` N/A (no docstatus/completeIt — non-invent verified vs `glassbowl_data.db`+`G`). `crud_overlay.js` ▶ ring icon + `CORE.docActionOutcome` (CO when requires met / IP unmet) + `#docStatusBar` (the Guide's `statusbar` target). Mounted in glassbowl.html.
  - **WRITE LOOP PROVEN at kernel level** (`scripts/test_crud_process_writeloop.js` W-CRUD-WRITELOOP **11/11**): signed Complete commits+seals+verifies+tamper-detected (`kernel_ops.js`); `SET_STATUS` DR→CO re-folds via `Kernel.dispatch` ladder, illegal rejected, replay = identical hash. Map: overlay `DOC_ACTION` ≙ kernel `SET_STATUS`. Also `test_crud_process.js` 27/27; `drive_glassbowl.js` puppeteer localhost PASS.
  - **Help card** steers clear of chain bubbles + draggable (`help_overlay.js`).
  - **GAP / NEXT (`prompts/GUIDE_SHOWME_PROCESS.md`):** deployed Process button is dry-run (logs, labeled), NOT wired to kernel. Push-on = GP1 Guide coach vocab (pulse/highlight statusbar, assert-nothing) → GP2 Next-gated-on-success (CO advance / IP hold / error→ErrorReport) → GP3 **E3 wire button → proven kernel `K.commitOp`** (real signed write; re-fold seam = open design Q) → GP4 ProcessBatch/Gravity.

**SETTINGS / 4D SCHEDULE (2026-05-31): READ-ONLY schedule showcase (both providers) + Settings-panel fixes, DEPLOYED live (sw v556).** (spec: `prompts/SETTINGS_JSON_EDITOR.md`; memory: [[project_settings_json_editor]])
  - **PR #68:** Settings → "4D Schedule (this building)" read-only, contract `Project + Phases[]` (`internal/schedule_instance.template.json`, jointly owned w/ gantt-support-gate session). `panels.js _projectSchedule()` = **CAPTURED provider** — native IFC `IfcWorkSchedule` (`tasks`/`task_elements`), Ceiling/TOS→Level, span=own structural span. Hospital 2.0 = captured 2900 / 10 phases. `test_schedule_projector` 10/10 on real `Hospital_meta.db`.
  - **PR #76 (v556):** `_projectGenerated()` = **GENERATED provider** — dropped IFCs (e.g. LTU) have NO native 4D; the support-gate fallback writes the instance to `kernel_ops` (ELEMENT_PLACE) and TM plays it. When captured is empty, project `kernel_ops` into the SAME contract: storey-grouped (Ceiling/TOS collapsed), `source` generated/captured/mixed (from `_captured` overlay). `test_schedule_generated` 8/8. Honest "open TM" note only when kernel_ops also empty.
  - **settings_editor.js:** `opts.readonly` (§PROPSHEET_READONLY writable=0); recursive `children[]` view handler (DORMANT — needs contract `children[]`); `__labelKey`/`__summary` display directives.
  - **PR #70/#71/#73:** fixed `_openSettingsPanel` (hidden until 2nd click) + `_makeDraggable` (measure.js) — capture pointer only after >4px move, release via capture-phase pointerup; fixes section-fold AND "panel sticks to cursor". Benefits ALL draggable panels. Verified LIVE via Playwright real clicks.
  - **OPEN (pre-existing, cosmetic):** `§PANEL_FOCUS` stack churn — every panel double-registered (`createPanel`'s `_registerPanel` dash-stripped id + `InputReg.register` fixed id) + not popped on close. Fix = single id + pop-on-close in `scene.js`/`input_registry.js` (all-panels blast radius, deferred).
  - **NEXT (Phase 2, parked):** editable schedule → `schedule_override` DB table, captured rows protected.

## iDempiere Renderer #1 — I1 LIVE + master-detail drill (2026-06-02, sw v560, PR #82/#83/#84)

**Master-detail drill LIVE (PR #84, sw v560):** select a header record → child tab (tabLevel>0) filters by parent FK, folded from SQLite WASM; nested AD tab-strip. Witnessed on the AD self-management window (Window→Tab→Field): AD_Window #100 → 13 tabs (of 1130) → 31 fields (of 20911) — `§IDEMPIERE-MD`, `tests/test_idempiere_master_detail.js`. AD models (AD_Window 370 / AD_Table 1003 / AD_Field 20911 / AD_Reference 604 / AD_Element 6024) are all browsable (read-only; writes = CRUD-P). Dictionary self-management = the engine-as-data point; the "better way" (descriptor/op-log, live-edit grail) is `docs/IDEMPIERE_2.md`, grail-gated.

## LENS family — lane-3 chrome fleet BUILT + witnessed + DEPLOYED LIVE (2026-06-03, PR #92, gh-pages)

**LIVE (gated demo pages, smoke-tested 200):** `red1oon.github.io/bim-ootb/erp/chat_lens.html` + `kanban_lens.html` over mock `glassbowl_data.db` (whitelisted `!erp/glassbowl_data.db`). Writes/nav inert BY DESIGN until STEP-0. Purely additive — live glassbowl/idempiere untouched.


**4 lens chromes over the op-log, consolidated in `bim-ootb/erp/`** (spec `prompts/LENS_FAMILY.md` + `prompts/MOBILE_CHAT_LENS.md`; memory [[project_lens_family]]). Engine REUSED via the seam, no fork, no new verb; resident mock `glassbowl_data.db` copied into `erp/`.
  - **F1 chat** (`chat_lens.js/.html`): op-log → bubbles/thread, pills-as-flips, dismiss=view-op (log unmutated), honest verified-tick (`identity`, no faked ✓/🔒), coverage strip. `§CHAT-CHROME-THREAD/DISMISS/PILLS/TICK/REFLOW/REPLAY/COVERAGE` **7/7** (`erp/tests/poc_chat_chrome.log`). Adversarial-reviewed → APPROVE-WITH-NITS → 2 nits fixed (Replay now slices to `replayStep`; coverage surfaced, `Dr=Cr=46574.97` matches proven `§CHAT-COVERAGE`).
  - **F2 kanban** (`kanban_lens.js/.html`): board=`doc_status` fold, drag→`dispatch(SET_STATUS)` **byte-identical** to chat-send; illegal drag snaps back (engine-owned). `§KANBAN-CHROME-FOLD/DRAG/LEGALITY` PASS.
  - **feed-fold** (`feed_fold.js`): thread-list-as-inbox, role-scoped, ranked by real fields; honestly **empty** over the all-terminal seed. `§FEED/RANK/SCOPE` PASS.
  - **user:0-names** (`user_names.js`): AD_User fold; no AD_User#0 row in seed → honest `System (AD_User#0)`, never invented. `§USERNAME/ZERO/ABSENT` PASS.
  - **STEP-0 seam #2 (host contract) DELIVERED on idempiere.html — this session, 2026-06-03, on disk / NOT yet deployed.** See the §SEAM-FROZEN entry below. Seam #1 (write `dispatch`/`ctx`) still pending engine **C0** → lens kanban-drag/chat-send remain inert; nav (Tour O2C walk) unblocks.

## STEP-0 §SEAM-FROZEN — idempiere.html host conformance (record-panel deliverable, 2026-06-03, NO deploy)

This session crossed into the record-panel lane (per GO) and built the host-contract freeze on `idempiere.html` (spec `prompts/IDEMPIERE_RECORD_PANEL.md` + `docs/TourGuideHostContract.md §2`):
  - **Exposed** `window.IdmpHost`{trace,focus,openTab,has,locate} over idempiere's nav (`focus` resolves a window BY NAME via `_winByName`; `has`/`locate` match `[data-ad-table]` **case-insensitively** so the lowercase keymap resolves canonical `C_Order` tags).
  - **Tagged** the O2C DOM `data-ad-table`/`data-ad-record` at render (`buildGrid` rows + `buildForm`), `data-ad-column` on fields; `#idmp-content` mount (already present); loaded **unforked** `help_overlay.js`+`help_idmp.js` (copied into `erp/`) + inlined `__helpIdmpKeymap` (zero drift vs `build/erp/help_idmp_keymap.json`).
  - **Witness** `erp/tests/poc_idmp_host.js` → `poc_idmp_host.log`: **§SEAM-FROZEN 31/31** (executes the REAL extracted IdmpHost; `focus` on an absent window no-ops + honest coverage, never fabricates). Tour `scripts/test_tour_idempiere.js` still **24/24**. Help-layer detection grep **GREEN** (IdmpHost + data-ad-* + mount). Additive only — existing render/login untouched.
  - **HONEST gaps:** (a) seam #1 write path left `// TODO(STEP-0 seam#1)` pending engine C0 (kernel proven, only the wrapper+ctx remain); (b) live-browser ShowMe walk + real-data record nav = post-deploy Playwright (secondary, §-log primary); (c) O2C window NAMES resolve only if present in `ad_seed.db`'s menu — absent → graceful degrade. **NO deploy (awaiting GO).**

  - **(lens adoption)** Lens lane **DEFERS** — all nav/dispatch are `// TODO(STEP-0)`; adopts `IdmpHost` (not a new name) per `docs/TourGuideHostContract.md`. Adoption map: `prompts/LENS_FAMILY.md §STEP-0`.

## Migrate ShowMe + ERP folder home (2026-06-02, LIVE)

**First-mile migrate ShowMe SHIPPED LIVE** (spec `docs/ERP.md §0.10a`, prompt `prompts/MIGRATE_SHOWME_OVERLAY.md`; memory [[project_erpmaker.md]]):
  - **Agent** (`bim-compiler:full be758f3d`): `scripts/migrate_pg_to_sqlite.js` gains `--list-clients` (AD_Client enumerate + auto-seek tenant + confirm) and `--masters` (master/metadata-only, per-table stream, instance registry 11→12 no-clobber, operational tables excluded by rule). NOT forked. Witnessed vs live GardenWorld Docker: `§MIGRATE-CLIENTS`/`§MIGRATE-AGENT`/`§MIGRATE stream` (C_BPartner=18…C_ElementValue=379)/`§MIGRATE-INSTANCE`. Scope decided live: take all AD metadata incl. custom/plugin tables, defer plugin **logic** only.
  - **Overlay** (`bim-ootb/erp/migrate_showme.js`, LIVE): thin 4-step (Connect creds+client-picklist → Run one-liner → Watch masters land via sql.js real counts → Done) + step-5 ReadMe (send file=identical DB via hash; online=served no backend). Launched from the **`idempiere.html` login card** (first-mile = data IN; Help pill stays general) — PR #89, sw v563. Honest bridge: guides local agent, reflects produced `ad_masters_<n>.db`. Witnessed `scripts/test_migrate_showme.js`: `§SHOWME-MIGRATE` 9/9 + `§README-SHARE replay-hash-match=Y` OVERALL=PASS.

**ERP folder home (PR #88 MERGED + LIVE):** ERP app separated `viewer/`→`bim-ootb/erp/` with own scope-isolated SW (`erp-ootb-` prefix, v562). Coupling was 1 file (pill_builder.js). Reroute stubs at old `viewer/{erp,idempiere}.html` (renderer #1 LIVE refs preserved). Merged main's #87 login into the moved `erp/idempiere.html`+`idmp_session.js` (no login regression, verified live 200). Specs `docs/ERP_FOLDER_HOME.md` + paste-note `docs/ERP_FOLDER_HOME_NOTE.md` (rebase advice for other sessions). **ERP now lives at `bim-ootb/erp/`, NOT viewer/.**


**"Pick your ERP" seam (idempiereUI.md I1): pill bar on erp.html + `idempiere.html` = renderer #1 (iDempiere-classic chrome). LIVE at `red1oon.github.io/bim-ootb/viewer/idempiere.html` — production render verified (587 menu nodes, Menu window folds from SQLite WASM).** (spec: `docs/IDEMPIERE_RENDERER_SPEC.md`; prompt: `prompts/idempiereUI.md` +§Review pass; pill manifest: `docs/PILL_MANIFEST_SPEC.md`; edited in `bim-ootb/viewer/`)
  - **Decision (user):** idempiere.html is a SEPARATE page launched from erp.html via the `idempiere` pill → `idempiere.html`. "Follow exactly iDempiere UI" — **desktop EXACT, mobile adapts**. POC framing: *"if SQLite WASM is up to it — success invites more strategy."* ETHICS: the user's neutral **A+** raster (`aplus.png`, supersedes erp_mark.svg per `prompts/IDEMPIERE_PILL_HANDOFF.md` + `docs/IDEMPIERE_2.md` §Guardrails 3), NOT the trademarked iDempiere logo (`idempiere_logo.png` removed).
  - **New files (bim-ootb/viewer):** `idempiere.html` (classic chrome: header+A+ · left AD_Menu tree · MDI window tabs · toolbar · AD tab-strip · grid/form · status bar — boots sql.js+ad_seed.db, reuses ad_parser/ad_data) · `icons.js` (12 Lucide icons VERBATIM from panels.js, data-only — panels.js can't load standalone) · `erp_pills.js` (mounts pill bar from pills.json; real ADUI handlers where they exist, honest toasts where surfaces land later) · `aplus.png` (user's A+ raster). Edited: `erp.html` (loads the 3; bottom-nav KEPT not hidden — no regression), `pills.json` (idempiere→aplus.png).
  - **Witnesses (READ logs, ALL PASS):** `§PILL-MANIFEST page=erp pills=13 handAuthoredButtons=0 inlineSvg=0` + `§ICONS-PARITY verbatim=12 drift=0` (`tests/test_pills_manifest.js`); `§IDEMPIERE-FOLD menu groups=53 leaves=534 source=ad_menu · window=Menu(105) tabs=2 fields=20 gridRows=587 source=sqlite handAuthored=0` (`tests/test_idempiere_fold.js`). Playwright smoke: live page renders 587 menu nodes + 14 standard groups + window/tabs/grid, boot 183ms (`build/erp/idempiere_smoke.png`). 153/153 AD-UI baseline untouched (ad_ui.js not edited).
  - **Honest UX:** ad_seed.db carries AD metadata for ALL 370 windows but DATA for a GardenWorld subset only → idempiere.html names "table not in this seed" rather than faking "0 records".
  - **DEPLOYED (sw v559):** PR #82 merged the feature + PR #83 the sw bump/precache (idempiere.html/icons.js/erp_pills.js/pills.json/aplus.png). CI green (fast-checks+e2e), fetch-back-verified live (sw v559, all assets HTTP 200, prod Playwright render OK). main branch-protected — no required reviews, only the 2 CI checks (enforce_admins=true).
  - **SPECCED NEXT (2026-06-02, data-verified, not yet built):** (1) **Login + Role/Client/Org session** — `docs/IDEMPIERE_RENDERER_SPEC.md §3b`: real iDempiere login→context-choose→role-scoped menu (AD_Window_Access 1080)+client/org-scoped data, folds from AD_User 8 (System/SuperUser/GardenAdmin/…)/AD_Role 4/AD_Org 9; HONEST = identity SELECTION not auth (no server); `§IDEMPIERE-LOGIN`. (2) **Data streaming "the rest of the data"** — `docs/IDEMPIERE_DATA_STREAMING_SPEC.md`, user chose **Hybrid**: T0 dictionary precached + T1 httpvfs RANGE over hosted ad_full.db (45MB, only touched pages) + T2 per-module shard offline fallback; window-open trigger; `DataSource` abstraction; range/shard ONLY never full download; P1→P4 `§STREAM*`. One filter model (role→menu, client/org→rows, parent→child, tier→source).
  - **NEXT:** **Review pass** (prompts/idempiereUI.md §Review pass) — verify the fold vs Docker PG AD (`postgres:15` :5432, DBs idempiere/idempiere_test) + idempiere.html chrome vs `/home/red1/idempiere-dev-setup` `org.adempiere.ui` (clean-room). Then I2 (ledger Report folds), I3 (AD_Menu long-press drawer), I4 (renderer registry + Odoo/ERPNext/Glassbowl slots). Help/ShowMe deferred until UI confirmed. Architectural NEXT (`docs/IDEMPIERE_2.md` §pivot): make idempiere.html descriptor-driven (AD = first descriptor) so renderer #2 (Odoo) reuses the engine.
  - **PROCESS LESSON ([[feedback_gh_deploy_base]]):** read `bim-ootb/GH_DEPLOY.md` + branch off origin/main BEFORE coding viewer changes (this session started on `docs/fair-readme` — wrong base; recovered by cherry-picking the sw delta onto main since PR #82 already had the feature).

## Lens family — phone ∥ desktop, one engine (2026-06-03, SPEC hardened + 2 witnesses; UNFROZEN)
- Specs `prompts/MOBILE_CHAT_LENS.md` + `docs/DESKTOP_LENS_SPEC.md` (Odoo pain→antidote→witness, web-sourced); roadmap `docs/CONCURRENT_LANES_ROADMAP.md` (backend serving ∥ frontend lens, seam=ENGINE_CONTRACT 5-calls + DataSource). Witnesses `build/erp/poc_chat.log` (op-log→thread) + `build/erp/poc_kanban.log` (Kanban=doc_status; SEND==DRAG==VERB). Freeze gate = live-Odoo diff-oracle. Memory [[project_lens_family]].

## Engine POST plugin — §13.1 closed, accounting genome PROVEN (2026-06-02)

**Headless POC DONE+PASS (engine lane, no UI/deploy).** `prompts/ENGINE_POST_PROTOTYPE.md` → spec `docs/PLUGIN_ARCHITECTURE.md §13.5`. A new posting doc-type is a MANIFEST, not code.
  - **§13.1 gap closed (`scripts/erp_kernel.js`):** `journal` projection extended to double-entry (`account_id, amtacctdr, amtacctcr`, mirror `gl_journalline`; NULL-default → ALLOCATE unaffected) + generic `POST` verb (`edgeMint`/`applyOne`) that asserts ΣDR=ΣCR to the cent, writes one row/line, replayable.
  - **Resolver (`scripts/post_resolver.js`):** `{Master.Role}` → real seed column, keyed `(master, c_acctschema_id)`, `c_acctschema_default` fallback. Non-invent: reads columns, never picks an account.
  - **Witnesses (`build/erp/post_poc/poc_post.log`, vs local `ad_seed.db` copy):** `§PLUGIN-POST C_Invoice#103 dr=[234:161.12] cr=[229:152,255:9.12] balanced=Y` · `§PLUGIN-RESOLVE` ×5 (receivable 234→ev518, revenue 229→ev758, tax-due 255→ev596) · `§PLUGIN-REPLAY agree=Y` · `§PLUGIN-MINIMAL lines=3 files=1 newSkill=none`. No regression: poc_kernel/identity/longtail PASS, writeloop 11/11.
  - **DECISION:** all seed sales invoices carry tax → 3-line entry with `{Tax.Due}` (superseded §13.3 "post net first", user-confirmed).
  - **NEXT (separate, EXPLICIT-GO session):** Track A Tier-1 overlay wiring into bim-ootb (pills.json) + the engine-contract seam into idempiere.html — coordinate with the renderer/login session.

## ERPMaker / AnyAppMaker docs + Odoo fold source (2026-06-02)

**Two forward-looking docs written** (SPEC, far-off, grail-gated): `docs/ERPMaker.md` (zero-install ERP from industry+Excel+B-card → fold → oracle-gate vs operator's own totals → offline signed HTML; the adoption-grail WEDGE vs iDempiere/Odoo/ERPNext install-fail) + `docs/AnyAppMaker.md` (horizon: any vertical from one sentence + `<URL>`; womb model; compiler front-end; BYO-LLM-as-bundle). Framing: ERPMaker = the OTHER half of HolyGrail (adoption), not "higher". Memory: [[project_erpmaker]].

**Migration targets (initial two):** iDempiere migrator DONE+witnessed (`migrate_pg_to_sqlite.js`+`compile_rules.js`+`diff_oracle.js`+`verify_migration.js`). **Odoo fold WITNESSED (2026-06-03) — `§ODOO-FOLD PASS`, `newVerbs=[]`.** One sell-side O2C chain (SO `S00023` → delivery → invoice → GL post → payment → reconcile) driven via RPC to completion on Odoo 17 `odoodemo`, frozen as a static oracle (`build/erp/odoo_oracle.{json,db}`, §0.12); a pure adapter (`scripts/odoo_adapter.js`) folded it through the existing kernel verbs — all 5 hops mapped, effects reproduce Odoo to the cent, replay exact (`scripts/poc_odoo_fold.js`, log `build/erp/odoo_fold.log`). The migration-solvent thesis survived its fairest external test: a *second* ERP dissolved with nothing invented. **Bound (named, f1–f4):** ONE sell-side chain; account-determination is host data (POST owns only ΣDR==ΣCR, §13.1); full payment = FK-directed `ALLOCATE`. **BUY-SIDE 3-WAY DONE (2026-06-03) — `§ODOO-FOLD-3WAY PASS`, `newVerbs=[]`.** Installed Odoo `purchase`, drove P2P (PO `P00011` → receipt → vendor bill → GL post → 3-way reconcile), froze `build/erp/odoo_oracle_p2p.json`; `scripts/poc_odoo_fold_3way.js` folded it invoking the EXISTING matcher (`erp_engine.match`, 2 calls — genuinely fired, not stubbed) → the 6th verb `MATCH` now exercised. **`§ODOO-FOLD-VERB-COVERAGE`: all 6 kernel verbs fold Odoo across the two chains, nothing invented.** AP double-entry balances (DR Expenses 5736 + Tax 860.40, CR Payable 6596.40). **f7 PARTIAL 3-way DONE — `§ODOO-FOLD-PARTIAL PASS`.** Drove PO `P00012` ordered 20 / received 12 (backorder 8) / billed 12; `scripts/poc_odoo_fold_partial.js` showed the partial DECOMPOSES exactly as §0.17 predicts: received==billed → exact MATCH on the settlement leg (matcher fires), ordered−received=8 → FK-directed open remainder (NOT a matcher pairing) → matched 12 + open 8 == ordered 20 = Odoo's backorder; `newVerbs=[]`, no new matcher policy. **f8 RESOLVED — `§ODOO-FOLD-F8 PASS` (was BOUNDED; Stage-1 fix landed 2026-06-03).** Drove PO `P00013` received 12, bill EDITED to 8 (bill≠receipt); the SHIPPED exact-qty matcher returns 0 pairs — it CANNOT reconcile bill≠receipt (the bound, confirmed, kept as contrast). **Fix LANDED in `erp_engine.match`:** opt-in `opts.partial=true` → partial-QUANTITY matching (pair `min(qty)` + carry remainder; the exact-qty fast path + `[[idL,idR]]` return shape are preserved for every existing caller; partial mode returns `[{l,r,qty}]`). `poc_odoo_fold_f8.js` now EXERCISES the engine (local characterisation removed): reconciles to the unit (matched 8 + open-to-bill 4 == received 12) still via the SAME `MATCH` verb → **`newVerbs=[]` holds; bound was matcher BEHAVIOUR (code), NOT a new verb, NOT adapter-shaped.** Witness `§MATCH-PARTIAL pairs=1 matchedQty=8 remainder=4`. **No regression:** 3way (matcherInvoked=2) · f7 partial · longtail · diff_oracle 5/6 · POCMATCH (FIFO/LIFO/role-scope) · WIRE 18/18 all still PASS (`build/erp/f8_fix.log`). **f2 PARTIAL PAYMENT DONE — `§ODOO-FOLD-PAYPART PASS` (Stage 2, 2026-06-03).** Drove a fresh sell-side chain to a PARTIAL-payment state via RPC (`scripts/drive_odoo_paypart.py`, JSON-RPC — XML-RPC's `allow_none=False` chokes on wizard nulls): SO `S00027` → deliver → INV/2026/00007 (total 5002.50) → register payment **3000** → Odoo leaves residual **2002.50**, payment_state=`partial`; froze `build/erp/odoo_oracle_paypart.json` (§0.12). `scripts/poc_odoo_fold_paypart.js` (+ adapter `buildPayPartEvents`) folds it: same `ALLOCATE` verb at the smaller amount, **residual=total−allocated reproduces Odoo to the cent**, payment_state derives as `partial`, replay exact → **`newVerbs=[]` AND no engine change** (the cleanest finding — *free*, unlike f8). **Odoo campaign:** 5 scenarios fold — sell-O2C, buy-3way-full, f7 partial-receipt, f8 bill≠receipt, f2 partial-PAYMENT (ALL PASS). **f1 ACCOUNT-DERIVATION DONE — `§ODOO-FOLD-ACCTDERIV PASS` (Stage 2, 2026-06-03).** The campaign's standing bound (accounts taken as host data) is CLOSED. Extracted Odoo's determination CONFIG (`scripts/drive_odoo_acctcfg.py` → `build/erp/odoo_oracle_acctderiv.json`): product income = template account → category fallback; tax = repartition tax-line account; AR = partner property. `scripts/poc_odoo_fold_acctderiv.js` (+ adapter `resolveAccounts`/`buildDerivedPost`) DERIVES the accounts from config alone → matches Odoo's posting to the account (400000 Sales / 251000 Tax / 121000 AR), derived double-entry balances + totals 5002.50 to the cent, replay exact, **`newVerbs=[]`; host GLUE not engine** (POST still owns only ΣDR==ΣCR, §13.1; determination logic learned clean-room from config structure). **Claim raised: "reproduces GIVEN accounts" → "DERIVES the accounts."** **Odoo Definition-of-Done MET + EXCEEDED: f8 fix + partial-payment + f1 derivation; no regression (`build/erp/f8_fix.log`, 11 runners 0🔴).** **SAP SKELETON PREPARED — `§SAP-FOLD BLOCKED` (the allowed half, 2026-06-03).** Clean-room blind schema/state-map HYPOTHESIS `scripts/sap_adapter.js` (VBAK/VBAP→C_Order, LIKP/LIPS→M_InOut, VBRK/VBRP→C_Invoice, BKPF/BSEG|ACDOCA→journal, VBFA=derivation spine §0.13, BSEG/BSAD clearing=ALLOCATE/MATCH; `normalizeGLLine` SHKZG/DRCRK S/H→dr/cr; `buildSapEvents` VBFA-led; 6 NAMED_DIVERGENCES standard-vs-Z) + gated runner `scripts/poc_sap_fold.js` → prints the hypothesis & planned witnesses and STOPS at `§SAP-ORACLE unavailable` (no oracle, no fabricated rows; exit 0). Template `build/erp/sap_oracle.template.json`. Activates automatically when a real IDES/S/4HANA export drops in. **Step 0 (the real blocker): no SAP oracle obtained** — SAP stays SPEC, folds only against a real source (`prompts/SAP_FOLD_POC.md`, log `build/erp/sap_fold.log`). **SECOND SOURCE — SAP Flight Reference Scenario (license-free, `§SAP-FLIGHT-FOLD BLOCKED`).** SAP's OFFICIAL `/DMO/` travel-booking RAP demo (github.com/SAP-samples/abap-platform-refscen-flight) unblocks the IDES-licence problem for the DOCUMENT-LIFECYCLE half: `sap_adapter.SCHEMA_MAP_FLIGHT`/`buildFlightEvents` map `/DMO/TRAVEL`→C_Order, `/DMO/BOOKING`(+SUPPLEMENT)→lines, status→SET_STATUS; gated runner `scripts/poc_sap_flight_fold.js` folds 3 of 6 verbs (CREATE_DOCUMENT/CREATE_LINE/SET_STATUS) + the price-aggregation invariant — **honest scope: flight model has NO FI, so POST/ALLOCATE/MATCH untested here (proven on iDempiere+Odoo; SD/ACDOCA = field-mapping, not architectural change).** Template `build/erp/sap_flight_oracle.template.json`; activates with zero code change when a real `/DMO/` export drops in (needs an ABAP system: dev-edition Docker or BTP trial). **NEXT (spec written, not built) — `docs/AD_GEN_FROM_DICTIONARY_SPEC.md`:** the "just show it" + installer-prep step — generate the iDempiere AD (AD_Table/Column/Window/Tab/Field/Menu/TreeNodeMM, matching `export_ad.sh`'s renderer contract) FROM an adapter's table/column dictionary (`SCHEMA_MAP`), load a separate `sap_ad_seed`, and let the EXISTING `idempiere.html` renderer draw SAP's tables as navigable screens with zero renderer edits — metadata-only (no transaction oracle needed; empty grids honest). Proof gate `§AD-GEN`/`§AD-RENDER` (handAuthored=0, counts traced to dictionary); the ERPMaker installer emit/sign step is GATED on that proof. **Open (optional Odoo):** multi-currency / anglo-saxon COGS. Falsifier specs `prompts/{ODOO,SAP}_FOLD_POC.md`.

**NEXT (strategic, user 2026-06-02):** next session = **iDempiere UI lookalike**; THEN **Odoo UI lookalike**. Downstream artifact flagged important: a data dictionary mapping Odoo model (sale.order/account.move/...) ↔ iDempiere (C_Order/C_Invoice/...) → 5-table bridge. Odoo fold POC body runs as its own focused session after.

## Holy Grail doc + falsifier POC prompts + MIT license sweep (2026-06-01, session close)

**`docs/HolyGrail.md` LIVE** (github.io, MIT, first-person as ADempiere founder): the grail = "edit a rule, watch records re-fold live" (`§RULE-EDIT`, T3-gated). Sections: PO.java-freed-at-runtime (oracle stays the anchor); DocAction corpus **de-interleaved** (status-table + shared verbs + per-type recipe; only completeIt-class heavy + oracle-verified); migration solvent (behaviour-as-data folds foreign ERP via adapter); **hard parts worked through** (compaction = period-close signed checkpoint = balance b/f; atomicity = op-group; OLTP = physics + CAS; keep just the ledger). ERP.md gained §20 addendum + section-map + footnote→HolyGrail. Pushed `full` (9acff45…e5f4f33), gh-deploy LIVE.
### POCs EXECUTED + PERF FIRM-UPS (2026-06-02): all GREEN, witnessed in LIVE HolyGrail

**3 falsifier POCs DONE + GREEN** (deterministic, reuse poc_chain/poc_sign/poc_persist + mirror crud_overlay CORE.buildOp): **SHOWSTOPPER** `scripts/poc_showstopper.js` `§SHOW PASS` (op-group all-or-NONE; period-close checkpoint re-folds cold archive to signed balances TO THE CENT + tamper caught at exact op; CAS holds across checkpoint) — *also independently re-run GREEN by a 2nd session, same box* · **VOLUME** `scripts/poc_volume.js` `§VOL PASS` (bootstrap-from-checkpoint flat across 100× history; working set bounded; binding constraint = per-op SHA-256) · **EMAIL_DR** `scripts/poc_email_dr.js` `§EMAIL-DR PASS` (Layer A data recovers unconditionally; Layer B key-floor stated + 3 anchors named; rotation preserves history).
**4 PERF FIRM-UPS DONE** (`scripts/poc_volume_ceiling.js` / `poc_volume_sqljs.js` / `poc_legacy_ab.js` / `poc_remote_pos.js`, logs in build/erp/): RAM no wall to 20M ops (~437 B/op, linear); browser product-stack per-op append ≈15µs (crypto.subtle-bound, ~70k/s seq); on-box legacy A/B in-RAM ~98× vs PG-sync / ~115× vs SQLite-sync (durability-trade-dominated); REMOTE = RTT-bound legacy (per-txn 2–5 orders, offline-capable) vs local+async ours, batch+graphs ~12× (server-side-batch fair). Web-search: every PART precedented, COMBINATION (signed-log+fold+period-checkpoint+serverless+ERP on SQLite) appears novel.
**2 falsifier POC prompts LEFT** (oracle-gated, own sessions): **ODOO_FOLD_POC** (needs Odoo demo dump) · **SAP_FOLD_POC** (ACDOCA=fold, VBFA=spine; needs IDES). Each FALSIFIER — "thesis bounded"/"needs verb X" = valid logged result.
**MIT license sweep** (ratified MIT, creator Redhuan — code-as-gift, moat=corpus+authorship): stamped SPDX/copyright on ALL non-vendored JS — bim-compiler scripts 41/41, deploy/dev 89/89, build/erp 3/3 (`full` 2341fe93); bim-ootb viewer 108/108 (branch `feat/erp-phase-a-live` 677f52e — NOT merged to main, deploy gated). Fixed pre-existing s220_test.js shebang parse error. CRUD/Process overlay (E2 dry-run) source preserved in the same commit. [[project_holygrail_poc]] · [[project_license_mit]].

## ERP Secured/Distributed — doctrine + POC suite + W-CHAIN live (2026-06-01)

**Doctrine written & pushed** (`full` b29b9376): `docs/DistributedERP.md` consolidated rev — §0 two truths (root = *the fact is a fold over a signed sequence*) + four mantras + server→serverless mapping + git analogy; §3 normal multi-POS day; §9 edge-scenario adversarial suite (folds old residuals); §10 how-we-differ. ERP.md §0.20 server-domain CORRECTED (Replicache-authority → **dumb facilitator**); Companion-docs map added. LocalFirstPriorArt:91/93 corrected.
**POC suite — all 6 witnesses PASS, deterministic, in-process, NO server** (`scripts/poc_{chain,distributed,sign,persist,policy}.js` + `test_kernel_chain.js`; work-order `prompts/DISTRIBUTED_POC.md` [gitignored] has the `# DONE` ledger): W-CHAIN · Merge/G-IDENTITY · W-OWNER/CAS · W-SIGN · W-PERSIST/email-recovery · lease-expiry/value-tiering. Each = spec for its production change.
**W-CHAIN LIVE** in `bim-ootb/viewer/kernel_ops.js` v5 (`s284e-b-inplace-viewer` ba5a569): prev_hash/op_hash/sig + `sealChain`/`verifyChain`/`setSigner` (crypto.subtle, sealed at persistence seam, signature opt-in). Verified vs real file.
**NEXT (phased):** W-SIGN prod wiring (edge keypair + setSigner); UUID PK + owner-gate/CAS in replay path (retire `docKey`/`lineKey`); `persist()` on erp.html load; deferred operational checks (real eviction/email/network); schema-migration stays the honest hard one. 2nd-mantra/tagline PENDING. Memory: [[project_erp_secured_phase]].

**GLASSBOWL — engine-as-data explorer (2026-05-30): LIVE read-only MVP + interactive.** (spec: `docs/GLASSBOWL.md`; Phase 2 spec: `docs/GLASSBOWL_DOSSIER.md`)
  - **What:** the ERP engine renders ITSELF from data (W-GLASSBOWL) — FK graph (273 edges, 0 hand-authored) + 6 cells + 155 cold backlog, spine-classified. `scripts/system_explorer.js` → `build/erp/{system_graph.json,glassbowl.html}` (self-contained). §GLASSBOWL PASS.
  - **Live:** https://red1oon.github.io/BIMCompiler/glassbowl.html (BIMCompiler repo, `mkdocs gh-deploy`, `docs/glassbowl.html`). Interactive (drag/zoom/pan), business-user explainer + plain-English legend/inspector. Proven in real browser: `deploy/dev/tests/test_glassbowl.js` 18/18 §GLASSBOWL-WIRING PASS (incl. drag/zoom/reset/About). Render bug found+fixed: SVG via `createElementNS` NOT `innerHTML` (innerHTML SVG counts but never lays out headless).
  - **Phase 2a DONE (2026-05-30): LIFECYCLE CHAIN + sql.js data bundle. §LIFECYCLE PASS + §LIFECYCLE-BUNDLE agree=Y + §LIFECYCLE-XCHECK agree=Y.** (spec/witness: `docs/GLASSBOWL_DOSSIER.md §2a`, W-LIFECYCLE)
    - **Lineage extractor (`system_explorer.js` Layer 4):** the §0.19 derivation/settlement FK walk for SO 101 → ordered 5-doc chain `C_Order#101(80001) → M_InOut#101 → C_Invoice#101(200001) → C_Payment#100 → C_AllocationLine#101($98.5)`, missing=0. Each hop's FK column ASSERTED present in the extracted graph (fk-hops 4/4) → 0 hand-authored hops. Chain inlined into `system_graph.json`/`glassbowl.html` (file://-safe).
    - **sql.js data bundle (`build/erp/glassbowl_data.db`, 182 rows / 92KB):** 11 lifecycle tables copied whole; Node re-walks the BUNDLE ALONE → identical chain (§LIFECYCLE-BUNDLE agree=Y, deterministic, browser-independent). `sqljs/sql-wasm.{js,wasm}` copied alongside (NOT `lib/` — that's `.gitignore`'d as Maven deps).
    - **Viewer:** "▸ Trace a record" dims the map, lights exactly the 5 chain bubbles + 10 derivation/settlement edges (animated flow), step-strip shows real doc numbers + the partial 98.50. On trace it lazy-loads the bundle via sql.js + re-walks in-browser (§LIFECYCLE-XCHECK agree=Y), graceful if assets absent.
  - **Phase 2-viz DONE (2026-05-30): §ORBIT PASS + 38/38 §GLASSBOWL-WIRING PASS.** (spec: `docs/GLASSBOWL_DOSSIER.md §2-viz`, W-ORBIT) — a session of co-designed "fresh eye candy," all pure read, no T3, same lightweight `createElementNS` SVG (no engine/raster swap):
    - **Pseudo-3D orbit (W-ORBIT):** z assigned per node FROM spine role (spine 0 / settlement +220 / reference −220, `§ORBIT planes=3 spine=9 settlement=4 reference=56`, 0 hand-placed — metadata-driven, NOT coordinates). Bottom **trackball** orbits the camera (yaw/pitch); bubbles stay static in 3D; orthographic head-on = **pixel-identical to the flat static layout** (depth cue gated by orbit amount). "Like BIM disciplines, orbited." Bubble-drag kept via inverse projection.
    - **Recent-items accordion** (the activity / RecentChanges log — iDempiere has Recent Items, this is it spatial): each look-up flows in as a collapsible bar; minimise keeps the title (return later); swipe/✕ dismiss; shows real op-count `⟐ tracked N runs (op-log depth, §0.6)`. Seam to a true `kernel_ops` change-log later.
    - **Click-to-focus filter:** click a bubble → only its links stay, the rest dim; bg-click clears. **Collapsible + drag-resizable** info panel (width=CSS var, 240–640px); ⓘ "how to read this" moved into the panel as an appendix.
    - **Round 2 (same session):** click a lifecycle bubble → its bar offers "▸ trace this flow"; **record SEARCH** (datalist of real orders from the bundle → walk ANY seed in-browser via declarative `LIN.steps`, e.g. order 60000 → its own chain); reference shell given **depth (z-jitter) + longer springs** so orbit de-bunches it; **Reset now re-homes dragged bubbles** (stores hx/hy); **SCENE PERSISTENCE** — localStorage save-on-unload / restore-on-load keeps orbit+pan+zoom+panel+focus+recent-bars+positions+trace across a hard refresh ("continue anytime, no re-login" — the killer vs iDempiere). 44/44 wiring.
  - **Phase 2bcd DONE + DEPLOYED LIVE (2026-05-30, commit 62a293eb): 62/62 §GLASSBOWL-WIRING PASS, all pure-read/additive/0-hand-authored.** (spec: `docs/GLASSBOWL_DOSSIER.md §Phase 2bcd`)
    - **W-DATA-BARS:** click a bubble → its REAL rows surface in the accordion bar from the bundle (c_invoice→200001/100.70/CO), honest "not carried" for non-bundle tables. `§DATA-BARS bundle-tables=11`.
    - **W-DOSSIER:** right-click → movable dossier, lazy tabs Data|Rules|Columns|History; History = read-only undo preview from real `kernel_ops` before/after (↶ greys "would reverse N ops", NO writes). `§DOSSIER rules=3 columns=30 §OPLOG ops=12`.
    - **W-ACTIONS:** double-tap → action blurb (Trace/View data/History/Dossier) + GREYED CRUD teaser ("editing — coming later (T3)"); blurb follows bubble on orbit, dismiss on click-away.
    - Live verified: openDossier/recrows/injectRecs/"the actual records"/"would reverse"/#blurb/crud all present in served HTML.
  - **Phase 2efg + mobile DONE + DEPLOYED LIVE (2026-05-30, commit e2d6702d): 85/85 §GLASSBOWL-WIRING PASS (62 prior + 18 Tasks4/5/6 + 5 mobile), all pure-read/additive/0-hand-authored; gen §SWIPE-PICKER/§AUDIO/§QR-INPUT/§MOBILE wired=Y, hand=0; §GLASSBOWL/§LIFECYCLE/§ORBIT still PASS.** Live https://red1oon.github.io/BIMCompiler/glassbowl.html (curl-verified phandle+qrLookup present; .db+sqljs assets 200 w/ correct MIME). `docs/ERP.md` intro gained the "Underlying Structure — appraisal for the iDempiere mind" (engine-as-data thesis, the 6-row collapse table, proven-vs-parked). (spec: `docs/GLASSBOWL_DOSSIER.md §Phase 2bcd` Tasks 4/5/6)
    - **MOBILE polish:** yellow `#phandle` at the panel border slides the info panel open/closed on tap (large target, [[feedback_mobile_events]]); fresh small-screen load (`isMobile()` ≤760px) starts collapsed so the graph gets the screen; the "What am I looking at" About panel is bounded to the viewport (`@media max-width:760px` top/bottom 8px, overflow auto — bottom no longer cut off) + a ✕ close. `§MOBILE wired=Y`.
  - **Eye-candy round DONE + DEPLOYED LIVE (2026-05-31): comet trace + view scrubber (double-tap bloom) + mobile diagonal arrange (commit c16b70ff, sw v2), then shared `Layout` class — nudge-apart + macOS dock-lens, BOTH pages (commit bd20123e, sw v3, 109/109 §GLASSBOWL-WIRING PASS, hand=0).** `scripts/glassbowl_layout.js` = single source of truth for positioning (seed/nudge/dockLens + orbitPlanes/diagonal/gravitySpiral/dictionaryRow); inlined into glassbowl.html via generator + HAND-PASTED into glassbowl_gravity.html (SYNC-POINT comment — re-paste on change). Dock-lens fixes the crowded Gravity Dictionary view (overlaps-at-rest=0, swell on hover, visual-only). Specs: `prompts/GLASSBOWL_LAYOUT.md` (gitignored).
  - **SW UPDATE-TOAST DONE + DEPLOYED LIVE (2026-06-01, sw v4): 118/118 §GLASSBOWL-WIRING PASS, hand=0; W-SWUPDATE ×9.** `updatefound`→"new version ready, tap to refresh" toast (#swToast, hidden at rest)→`reg.waiting.postMessage(skipWaiting)`→one guarded reload (`__swReloaded`, no loop, never auto). Both pages + `sw.js` skipWaiting message handler. Bug caught in review: generator was emitting a DUPLICATE SW-registration `<script>` (stale precache block) — removed; glassbowl.html now has exactly 1 `register()`. Spec: `prompts/GLASSBOWL_SWUPDATE.md` (gitignored). CAVEAT: v3→v4 was the bootstrap deploy (running v3 had no listener) — one last manual reload, then every future bump self-announces. Memory: [[feedback_sw_update_toast]].
  - **⚠ CROSS-SESSION HEADS-UP (any session touching glassbowl):** (1) `build/erp/sw.js` is **v4** + carries a `skipWaiting` message handler — PRESERVE both and **bump v4→v5** on ANY glassbowl page change (single point of failure: forget = no toast + stale cache). (2) `glassbowl_gravity.html` is HAND-AUTHORED — its `Layout` (`scripts/glassbowl_layout.js`) + toast blocks are HAND-PASTES marked SYNC-POINT; re-paste on change (glassbowl.html re-inlines via `node scripts/system_explorer.js`, gravity does NOT). (3) deploy = copy `build/erp/{glassbowl.html,glassbowl_gravity.html,sw.js}`→`docs/` + commit `full` + `mkdocs gh-deploy --force`; running gh-deploy ships whatever is in `docs/`, so commit glassbowl changes BEFORE any gh-deploy.
  - **NEXT — Phase 3 "The Organic View" (SPEC DONE, craft in NEW session): `docs/GLASSBOWL_DOSSIER.md §Phase 3`.** Co-designed 2026-05-30: static FK map → organic/gravity/lens-driven. **W-LENS** (a lens = which COLUMN the whole web wears — DocNo/#lines/totals/dates/status — shown as a SUBTITLE under each bubble's name, name stays [user RESOLVED fork 3]; iDempiere columns across ALL docs at once; spine filter composes) → **W-ARRANGE** (trackball demoted camera→arranger: small lean=jiggle post-its for clearer mind-map; neutral=flat at-rest bowl preserved) → **W-SLICE** (push past threshold = dive into the focused doc's LINE rows, the X-scissors) → **W-REFORM** (deliberate focus blooms neighbours into a weighted gravity ring, reversible ease-home). Push-not-pull (TikTok), extract-only forces. OPEN FORKS to confirm first (slice-in: bubbles-fan vs row-strip; lens scope: whole-web vs focus+neighbours). Protect baseline 85/85. Deploy on explicit go only.
    - **W-SWIPE-PICKER (Task 4):** `#recpick` — a swipeable/scrollable list of the 8 real bundle orders (documentno + grandtotal) anchored at the trackball; flick to scroll, **tap a row → traces THAT record** (`applyChain(walkBundle(GDB,oid))`), zero typing. **Augments** the typed `#recsearch`/`#reclist` (8 opts preserved). `§SWIPE-PICKER rows=8 seed=80001 scrollable=Y`.
    - **W-AUDIO (Task 5):** WebAudio synth "ear candy" — ZERO assets, file://-safe. pick=tick (pitch by spine colour), bar=whoosh-sweep, **trace=rising arpeggio (one note per real hop)**, dismiss=down-tick, dossier=click, not-carried=thunk. `AudioContext` unlocked on first gesture (`§AUDIO unlocked state=running`); 🔊/🔇 mute toggle in `.ctl`, **persisted in scene localStorage**; muted pick schedules nothing (34→34). Whitebox: pick scheduled 31→34.
    - **W-QR-INPUT (Task 6, read-only slice):** `#qrbtn` "▣ Scan QR" in the trace input panel → `#qrcam` overlay; native `BarcodeDetector` + `getUserMedia`, ZERO libs; **honest "QR scan not supported" fallback** (headless: `§QR-INPUT supported=N`); decoded payload matching a bundle record (e.g. 80002→order 102) **traces it** (`window.qrLookup`), unmatched returns false (no faked record); **stream stopped on close**. Scan-to-FILL-an-edit-value stays T3 (parked, greyed).
    - T3 PARKED (explicit go, write-loop): editing / view-back-input / on-screen-keyboard / rules-as-editor / scan-to-fill. NORTH STAR "make job as play" (Twain); positioning = eye/ear candy backed by real data → "make them talk".

**ERP P3c DONE (2026-05-30): long-tail vertical ships matcher-free; matcher composes in at one pro-gate seam. §VERTICAL + §GATE + §LONGTAIL PASS, 7/7 gates green.** (record: `docs/ERP.md §0.19`)
  - **Task 1 — per-document dispatch (`diff_oracle_cells.js` MMR:CO/API:CO, `build/erp/diff_oracle.log`):** closed the §0.18 gap — was one synthetic `invoiceDocId:'ALL'` event, now per receipt / per invoice, each matching only its own lines in its partition (consumed counterpart lines withheld, no double-claim). **Isolation guard:** Σ(per-document)==universe (M_MatchPO 19/19, M_MatchInv 18/18, M_MatchPO 18/18), 0 missed/0 extra. §ORACLE-SUITE still 5/6 PASS — no regression.
  - **Task 2 — lone-operator vertical (`scripts/poc_longtail.js`, `build/erp/poc_longtail.log`):** SO 101 (DocType 135, BP 112) → 5 per-document-event op-groups (raise SO→ship→invoice→pay→allocate) via `completeOrder`+`createShipment`+`createInvoice`+FK-directed `ALLOCATE`+decision tables. `E.match` monkey-patched + asserted **NOT-INVOKED** (calls=0). Effects reproduce oracle (ship/invoice line + allocation edge, partial 98.5/100.7), replay EXACT (hash match, 7 ops). `§VERTICAL flow=lone-operator events=5 matcher=NOT-INVOKED ops=7 replay=EXACT diff=MATCH`.
  - **Task 3 — pro-gate seam:** matcher becomes necessary at buyer reconciliation (`C_Invoice:CO` 3-way). Tier split is DATA (`CAPABILITY` flag, §0.11): OFF→cheap setStatus, matcher off, edges=0; ON→matcher **composes in** (same cheap op + APPENDED edges, additive not a fork). `§GATE pro-gate-cell=(C_Invoice,CO 3-way) composes=Y data-gated=Y`. Open edge: persist CAPABILITY as an `erp_rules.db` rule. T3 relocation still PARKED.

**ERP P3b BREADTH DONE (2026-05-29): contained-set thesis PROVEN across O2C/P2P/GL, diff-verified vs the GardenWorld oracle. §ORACLE-SUITE 5/6 PASS + §GRAVITY PASS.** (record: `docs/ERP.md §0.17`)
  - **Diff-oracle harness (`scripts/diff_oracle.js` + `diff_oracle_cells.js`, `build/erp/diff_oracle.log`):** per hot cell, register the PURE handler → dispatch the doc-event through `Kernel.dispatch` (§18.6 ladder) → normalise emitted op-group + oracle rows → diff → `§ORACLE cell=(..) checks=N fixtures=N diff=MATCH`. **STATIC oracle, NOT live (§0.12):** GardenWorld's already-executed rows in `ad_full.db` ARE the oracle output (deterministic — no Docker id/seq drift). Docker = documented fallback for data-less cells only.
  - **6 hot cells, each EXTRACTED from the Java oracle (`MOrder`/`MInOut`/`MInvoice`/`MPayment`/`MJournal.completeIt`):** SOO:CO shipment 6/6 ✅ · POO:CO no-fanout (DocType 126 isautogenerateinout=N) ✅ · MMR:CO M_MatchPO 19/19 + no-mutation ✅ · API:CO M_MatchInv 18/18 + M_MatchPO 18/18 ✅ · APP:CO allocation 1/1 ✅ · GLJ:CO dataless (fact_acct=0 → docker-fallback, logged not faked).
  - **THESIS HELD:** 3-way match (M_MatchInv+M_MatchPO across MMR+API) = **ONE** `E.match`, **zero** new verbs; derivation = `completeOrder`+decision-table (policy flag flips SOO vs POO). **2 findings logged loud:** (1) the matcher RE-DERIVES what iDempiere reads off the invoice-line FK (documented divergence, §18.10); (2) **allocation is NOT the matcher class** — partial + user-directed (PayAmt 98.5 ≠ GrandTotal 100.7), reproduced by following `C_Payment.C_Invoice_ID` FK + the existing ALLOCATE verb → *cheaper* than 3-way match, refines §0.14.
  - **Gravity seed (`scripts/gravity_seed.js`, `build/erp/gravity_seed.log`):** ops stamped with emitting cell; one §14-weighted query self-ranks the backlog — **C_Invoice #1** (56.0, 36 MATCH ops) confirms §0.13 (settlement spine = where the hell concentrates). 155 cold cells stay unwritten (cost nothing, §18.6).
  - **Kernel extended:** added `MATCH` settlement op (§0.1 document_lines.match_type) + ALLOCATE now carries invoice_id. **No regression:** §POC/§POCMATCH/§WIRE/§KERNEL all still PASS. T3 relocation still PARKED.

**ERP RUNTIME ENGINE DONE (2026-05-29): the compile→engine WIRE + the op-log KERNEL. §WIRE PASS + §KERNEL PASS.** (record: `docs/ERP.md §0.16`)
  - **T1 wire (`scripts/erp_runtime.js`):** `loadCell` sources EVERY engine opt from data — ordering ← `MATCHPOLICY:<cell>` (new seed, FIFO default), fan-out ← `DOCPOLICY`, access (allowOrgs+may-run) ← `ACCESS:<roleId>` (new Step-1 ext compiling ad_role+orgaccess+document_action_access), guards ← Validation. Sales→Ship with **zero JS opts** still 18/18; FIFO→LIFO rule-edit moved the pairing (`pairsChanged=1`); org-scope emptied the partition (`match=0/18`); no-grant role `mayRun=N`. Compiler now 746 records; `verify_rules.js` extended (MatchPolicy/Access) — gate PASS.
  - **T2 kernel (`scripts/erp_kernel.js`):** handlers PURE `(doc,ctx)→ops[]`; `Kernel.apply` applies to 5-table projection + commits a RICH op (payload+actor+before/after+lineage). `dispatch` = §18.6 ladder (wfmc→evalGuard→Handlers.run→apply). Violation guard rolls back out-of-log writes (`BLOCKED`); replay rebuilds projection from `kernel_ops` alone (hash match); frozen effects hold (`old-ship=3 new-ship=0 replay-old-ship=3`). Fixture `T_ORDER_SHIPMENT_ALLOCATION` green. Witnesses: `build/erp/{poc_wire,poc_kernel}.log`.
  - **T3 PARKED (needs go):** relocate erp_engine/erp_runtime/erp_kernel → `bim-ootb/viewer/` as shared module (kernel_ops.js must NOT fork). push=live.

**ERP POC DONE (2026-05-29): Sales→Ship hard-parts vertical, diff-verified vs GardenWorld oracle. §POC PASS.**
  - `scripts/erp_runtime_probe.js` (`build/erp/probe.log`): abstract engine on sql.js over the real corpus. READ/DECIDE/policy GREEN free; surfaced 2 gaps — `@ctx@` unresolved (176/331 val rules) + PG dialect (`least`); handlers RED (backlog). Corpus serveable: sql=335 policy=52 free, handler=294 hand-port, table=58 matcher.
  - `scripts/poc_sales_to_ship.js` (`build/erp/poc.log`): diff-oracle WITHOUT live iDempiere — GardenWorld's M_InOut/M_Match* rows ARE the oracle output; replay logic on inputs, compare. Results:
    - **DERIVATION proven (pattern A+B):** completeOrder = state + decision-table(policy flags) + verbs createShipment/createInvoice→ops[]; reproduced oracle shipment lines EXACTLY for all 4 SO orders. Effect verbs = BOM derivation (order→ship == building→floor).
    - **SETTLEMENT proven (pattern C, the hell):** three-way match = a CONSTRAINT not a ported algorithm. M_MatchInv 18/18 + M_MatchPO 18/18, 0 missed 0 extra. KEY INSIGHT: settlement partitions by **C_BPartner_ID (trading-partner account) + document polarity (vendor V+/issotrx=N)**, NOT the order lineage (matched vendor invoices have c_order_id=NULL). The BOM tree spine ≠ the settlement spine.
    - **FROZEN EFFECTS proven (§18.8):** decision-table edit is forward-only; new order changes, REPLAY of old order's stored ops reproduces original shipment.
    - Gaps closed: dialect shim (least→min) + `@ctx@` resolver. Finding: 3/4 AccountingRules are correlated fragments (need host query) — deferred (GL posting out of scope, fact_acct=0).
  - **Engine EXTRACTED + separation debt paid:** `scripts/erp_engine.js` — the §0.10 abstract engine as ONE pure module, DB-binding-agnostic (host injects query → runs under both better-sqlite3 AND sql.js). Was smeared/inlined across the two probes; now `poc_sales_to_ship.js` imports it and still §POC PASS (regression proves same logic). Exports: resolveCtx, dialectShim, evalGuard (GUARD: predicate→bool), match (GENERATE: the generic Detail⋈Detail matcher — partition + key + tolerance + role/org access-scope + pluggable ordering policy + greedy), VERBS, completeOrder.
  - **Role-scoped ambiguous matcher proven** (`scripts/poc_role_scoped_match.js`, `build/erp/poc_match.log`, §POCMATCH PASS, runs the engine under **sql.js**): synthetic ambiguous fixture (2 receipts same product+qty) — FIFO picks R1, LIFO picks R2 (ordering policy is a real deterministic knob); role-scope(org) NARROWS the matcher partition and overrides policy (access = predicate ON the edge, composes IN — §0.8, not a separate engine); evalGuard runs a @ctx@ predicate under sql.js. ONE engine: match + ordering + access + guard, all via opts/data. (Caught a real bug: string-date comparator did numeric subtract → NaN; fixed.) Ambiguity oracle = FIFO/LIFO SPEC (synthetic); real-data match still 18/18 vs iDempiere.
  - SEPARATION now: 🟢 compile-time (extract-only DAG) + 🟢 engine (one module, both bindings) + 🟢 model (edges+predicates, dispatch-by-form, running). REMAINING DEBT: matcher opts (partition/policy/access) are passed as JS in the harness — NOT yet LOADED from erp_rules policy JSON + ad_role/ad_role_orgaccess tables (the compile→engine wiring); NO kernel applies ops to kernel_ops log yet (handlers return ops, diff-compared, not committed); engine lives in bim-compiler/scripts, must move to bim-ootb as a shared module when wired. Uncommitted, not deployed. Learnings recorded in `docs/ERP.md §0.12–§0.15`. **NEXT SESSION: `prompts/ERP_RUNTIME_ENGINE.md`** — Task 1 compile→engine wiring (opts from erp_rules policy + ad_role), Task 2 op-log kernel (apply+commitOp, replay, violation guard).

**ERP Step 0 DONE (2026-05-29): raw PG→SQLite migration — full iDempiere dictionary, no column-strip.**
  - `scripts/migrate_pg_to_sqlite.js`: drives `docker exec psql "COPY … TO STDOUT"` (TEXT format — escapes embedded newlines, so rule bodies round-trip byte-identical), builds `build/erp/ad_full.db` (43MB, gitignored) via better-sqlite3. bytea→BLOB hex-decode. Loose affinity.
  - Witness (`build/erp/migrate.log`): `§MIGRATE tables=925 rows=187133`; `§MIGRATE rules AD_Rule=4 AD_Val_Rule=332 callouts=284 docTypeFlags=4/4 docTypes=52`; `§MIGRATE flagged sequences=0(dropped) functions=83(skipped) triggers=4(skipped) views=161(snapshot,deferred)`. The rule/Callout/DocType-flag layer ad_seed.db STRIPPED is now present raw.
  - Gate `scripts/verify_migration.js` (`build/erp/verify.log`): per-table count vs PG = **0 mismatches**; AD_Rule.Script **4/4 byte-identical** (md5 vs PG); blob ad_attachment 400803B exact; sql.js opens cluster + reads every major table. **VERDICT PASS.** Deterministic (re-run = identical counts).
  - Java NOT migrated (stays §18.10 oracle). Spec: `prompts/ERP_RAW_MIGRATION.md` Step 0, `docs/ERP.md §0.10`.

**ERP Step 1 DONE (2026-05-29): the Rule Compiler — §0.9 rule records + handler backlog from ad_full.db.**
  - `scripts/compile_rules.js`: reads LOCAL `ad_full.db` (not PG/Java), emits `build/erp/erp_rules.db`. AUTO-EXTRACT (declarative): AD_Rule→sql, AD_Val_Rule→sql (binding=referencing columns), C_DocType flags→policy JSON, ad_workflow→table. HAND-PORT BACKLOG (procedural Java, NOT auto-translated): 284 Callout bindings→handler stubs (oracle ptr=class.method); product-scope (§0.3) DocEvent stubs (C_Order/Invoice/InOut/Payment/GL_Journal × CO/VO) gravity-ranked to the confirmed §18.10 oracle. `rules` + `handler_backlog` tables; every rule ships `editable=1` (§0.8 capability-first).
  - Witness (`build/erp/compile_rules.log`): `§RULES extracted=445 (sql=335 expr=0 policy=52 workflow=58) callouts=284 docevents=10 handler-stubs=155 backlog=294`; totalRecords=739 presets=445 stubs=294 oraclePresent=146/155 (9 absent = eevolution/cm plugins, out of scope).
  - Gate `scripts/verify_rules.js` (`build/erp/verify_rules.log`): 739 records 0 defects; AD_Rule **PG→ad_full→erp_rules 4/4 byte-identical**; product DocEvents **10/10 oracle-present**, hottest=C_Order:CO@100; sql.js dispatch-by-form reads OK. **VERDICT PASS.**
  - Next: runtime rule evaluator (§0.10 "abstract engine" — dispatch by form: sql→sql.js, expression→sandbox, table→§0.5 matcher, handler→named fn) wired at the kernel cell (P3/P3b); hand-port hottest cell C_Order:CO (MOrder.completeIt, diff-oracle verified). erp_rules.db destined for bim-ootb (push=live) — NOT deployed.

**ERP P2 DONE (2026-05-29): WfMC state-machine-per-DocType compile + extracted derivation graph (NOT deployed).**
  - `scripts/compile_manifest.js` extended: `manifest.wfmc` (shared iDempiere doc engine — 11 states, 22 transitions, oracle DocumentEngine), `manifest.doctypes` (51 real DocTypes, sentinel id=0 excluded), `manifest.downstream` (EXTRACTED, replaces §4 hardcode) + `manifest.settlement` (tagged back-edges, excluded from acyclicity). §3 compiler/view/temp tables excluded.
  - Gate `scripts/test_manifest_wfmc.js`: `§MANIFEST doctypes=51 transitions=22`; downstream acyclic=PASS (settlement=2); C_Order→[C_Invoice,M_InOut,...]; gz 19.1KB (<25KB). VERDICT PASS (`/tmp/pb/p2_gate.log`).
  - Additive/behavior-preserving (no consumer reads new keys). manifest.json uncommitted in bim-ootb (push=live). Next: P3 (kernel enforcement). Spec: `prompts/ERP_KERNEL_BUILD.md §P2`.

**ERP PB DONE (2026-05-29): AD→5-table bridge built + gate GREEN (NOT deployed).**
  - `bim-ootb/viewer/`: `schema_5table.sql` (canonical 5-table, domain fields in metadata JSON), `ad_table_map.js` (37 explicit overrides + 10 hub fk_maps + PV heuristic), `ad_data.js` bridge mode (default OFF, behavior-preserving; `useBridge` routes CRUD to 5 tables).
  - Gate `scripts/test_bridge.js`: `§BRIDGE map windows=7 docTypes=49 unmapped=0`; roundtrip C_Order match=OK; lineage source_id/source_line_id; M_MatchInv match_type. VERDICT PASS (`/tmp/pb/bridge.log`).
  - ⚠ doc_engine.js (Spatial ERP POC) has same-named tables, different columns — reconciliation deferred. bim-ootb uncommitted (push=live). Spec: `prompts/ERP_KERNEL_BUILD.md §PB`, `docs/ERP.md §0/§0.1`. Next: P2 (WfMC state machines) or P1 (deploy P0).


**S284b DONE (2026-05-29): web-ifc JS embedded — IFC import from offline HTML proven.**
  - web-ifc JS (6MB) embedded as `<script type="text/plain" id="webifc-src">` — browser stores as inert text, `_createWorker()` reads via DOM `.textContent`.
  - Fixed `$'` corruption in `String.replace()` — all html.replace() use function replacement.
  - Removed `_STANDALONE` redirect guard — `handleImportFile()` now runs Blob Worker locally.
  - Sandbox e2e: Playwright Chromium from `file://`, real IFC dropped, parsed, saved to IndexedDB. 165 tests, 0 failures.
  - Output: BIM-OOTB.html ~6.8MB. IFC import works but **web-ifc.wasm (~4MB) still fetches from CDN**.
  - **Next: embed web-ifc.wasm as base64 for true zero-CDN offline. Prompt: `prompts/S284b_WEBIFC_EMBED.md`.**

**S284 DONE (2026-05-28): Save Offline Copy — About box packages landing as single HTML.**
  - `packageLandingPage()`: inlines 2 external JS + Sysnova.png (base64) + manifest snapshot, injects `_STANDALONE` flag, absolute GH Pages URLs, strips analytics. Downloads `BIM-OOTB.html` (~250KB→6.8MB with web-ifc).
  - Standalone manifest guard: `_MANIFEST_SNAPSHOT` bypasses fetch, cards render from embedded data.
  - sql-wasm.js + sql-wasm.wasm (base64) + worker sources embedded for offline SQLite + import.

**S282b DONE (2026-05-28): PanelNav + ListBuilder + _isMobile registry.**
  - `panel_nav.js`: universal zone-based keyboard nav (~135 lines). Fixes ArrowDown-from-input bug.
  - `list_builder.js`: reorderable list extraction from PillBuilder (~85 lines).
  - Find panel migrated to PanelNav (4 zones). Settings panel gets PanelNav.
  - `_isMobile` single source in `config.js` — no UI re-detection. Renderer files keep stricter `screen.width` checks.
  - Status bar persists in maxed (`[][]`) mode. 143 whitebox tests.
  - **Spec: `prompts/S282b_LISTBUILDER_PANEL_NAV.md`. Next: Phase 2c Locale + Rate pickers.**

**S274b DONE (2026-05-26): Import Perf + CI + Auto-Open.**
  - Split-DB for dropped IFC: landing page persists metaDb/geoDb, openProject uses split URLs.
  - Instant card click (64ms): cache pre-populated during import, count() key check skips 760MB record read.
  - Auto-open: viewer launches immediately after import (single + multi-file). No card click needed.
  - Error reporter: toast fires on uncaught errors, Report button → GitHub/Email.
  - GitHub Actions CI: Tier 1 (syntax+audit, 7s) + Tier 2 (golden-path Playwright, 47s). Vogel fixture.
  - **Open: IFC export broken (IFCFLOWSEGMENT), email report too long, material white handling. See `prompts/S280_IMPORT_POLISH.md`.**

**S278 DONE (2026-05-25): Memory Leaks + Clash Polish + Mobile Perf + Find Optimization.**
  - Memory leaks: PointLight shadow disposal, peel material clone, per-frame Vector3 caching (time_machine, picking, walk).
  - Clash: color orange→blue (0x2266ff). First-phase behavior preserved — no invented guards/isolation/outline.
  - Clash multi-select: keyboard Shift+Arrow works (red spheres + fly-to bbox). Ctrl+click deferred to refactor.
  - Clash matrix: query cache per discipline, offset reset on pair change.
  - Find: deferred element query on open (storeys+types only), cached storey list, BatchedMesh/InstancedMesh guid search.
  - Mobile: EffectComposer skipped entirely (no creation, no GPU allocation). Direct render always.
  - Storey active button: bright red (#ff2222).
  - **Spec: `prompts/S278_REFACTOR_CLASH_PANELS.md`. Next: refactor clash_panels/matrix/effects out of scene.js, restore Palette.**

**S277 DONE (2026-05-25): Cinematic Rendering + Isolation Pick + Movie Maker.**
  - WebGL-only (WebGPU deferred). EffectComposer: SSAO + OutlinePass + OutputPass (desktop only).
  - Isolation pick: dim 15%, picked 70% transparent, orange outline (Bonsai-style). Find: blue outline.
  - TM twilight: 20x detail at dawn/dusk (5x slower + 4x finer steps). Auto-speed by element count.
  - Night mode: 12 POL at orbit target + ambient 0.2 + fixture emissive glow (all surfaces, zero cost).
  - Movie maker: record icon → .webm via MediaRecorder. Desktop only.
  - Fog, lensflare, procedural normals, near clip 0.1m, clash red+blue.
  - Keyboard: shortcuts over typeahead. Shadow: chunked traverse. Scissors: envelope range.
  - **Spec: `docs/CINEMATIC_RENDERING.md`. Next: CSM, animation export.**

**S274 DONE (2026-05-24): DLOD Rewrite + Mobile Perf — bench proven.**
  - Discovered Three.js r160 `perObjectFrustumCulled=true` handles BatchedMesh frustum culling natively — zero JS cost.
  - Desktop: InstancedMesh zero-scale frustum culling (35K instances, ~32% hidden when zoomed in). ~1.5ms tick.
  - Mobile: DLOD off (r160 native handles BM, IM zero-scale too expensive). On-demand render gate restored. DPR 0.75 during orbit.
  - Stripped 250 lines dead code (spatial grid, geometry swap, storey culling — all indexed zero meshes).
  - Bench tool: `viewer/dlod_bench.html` — localhost direct test, no Playwright overhead.
  - Watchdog fixes: sw v444, share.js precache, bom_engine scripts + viewer.html tags.
  - **Net:** Desktop IM culling is new. Mobile faster via render gate + DPR reduction + no DLOD overhead.

**S273 DONE (2026-05-23): Red Pill UI Hardening — 73 tests, 0 failures.**
  - F1+F2: Save/Open design to IndexedDB (grid state + kernel_ops serialize/restore via prompt picker)
  - F3: Timeline scrub preserves user-placed grid lines (`_userGrids` tracking, re-added after replay)
  - F4: Rosetta delete — drag grid 2m beyond envelope removes it + GRID_DELETE kernel_op
  - F5: Grid attachment guard — blocks drag on grids with no attached elements
  - F6: W023 SC BOM seed — 342/348 lines typed (9 IFC classes), strategies, storey, dimensions applied to SampleCastle
  - **Next: S270 — Grid Kinematics Engine. Prompt: `prompts/S270_GRID_KINEMATICS.md`.**

**S272 ALL PHASES DONE (2026-05-23): BOM Recomposition Engine — 438 tests, 0 failures.**
  - Phase 1-4: `bom_engine/` (4 files), bom_tree/grid/rules, W022 migration, disc_rules.json.
  - Deep tests: diff-self invariant, containment, idempotency, full-cycle smoke, 500-node perf, rule monotonicity.

**S266 IN PROGRESS (2026-05-22): New From Reference — Doc Pill + JS BOM + Design Canvas + RouteWalker. See `docs/NEW_FROM_REFERENCE.md`.**
  - Red Pill icon replaces TM in main pill → swaps to 9-icon red glass Doc pill (Home/Grid/TM/Next/Disc/Open/Save/UBBL/Rosetta)
  - `bom_extract.js`: JS BOM extractor from elements_meta. Groups storey→discipline→ifc_class, envelope, storey heights, cadence. STD_MEP fallback. Cached in IndexedDB.
  - `doc_canvas.js` HARDENED: §6.4 BUG fixed (envelope-only step zero: 2+2 lines, no cadence). GRID_STRATEGY table (23 IFC classes → grid behavior). `_ifcToThree()` DRY coord transform. Discipline-scoped Next. Rosetta Stone template lines (gold/grey, drag-to-place instances). kernel_ops wired: GRID_ADD, GRID_CALIBRATE, DISC_SWITCH.
  - **S266b (2026-05-22):** BatchedMesh+InstancedMesh materialize fix. HUD grid bays accordion. Wall dedup + 30-line cap. Grid lines dashed, depthTest off, extend 8m. Rosetta templates 14m out.
  - **S266c (2026-05-22):** Grid rethink — AUTO-GRID REMOVED. User-initiated grid lines via double-click (add/remove toggle). Envelope tightened to structural-only AABB (excludes proxy/site). Design Gantt ordering: CLASS_PRIORITY (walls→openings→proxy, not alphabetical). Timeline slider (◀ scrubber ▶) with prevPhase/scrubToPhase. Grid select-then-drag (click→highlight→constrained drag). Both-end bubbles on grid lines.
  - `route_walker.js` NEW: JS port of Java RouteWalker (395→250 lines). Pattern applier for MEP.
  - `panels.js`: 8 discipline icons + discipline selector popup.
  - **Tests:** 54/54 PASS — `test_doc_canvas.js` updated for handleElementPick (user-pick, not auto-grid).
  - **Spec updated:** §17.7 Rotation. §17.9 Grid Rethink + BOM Completion Triage (A–I). §17.9H Print-Ready Mode.
  - **DECISION: 100% browser.** Java verb expanders are pure math (~200 lines) — port to JS like route_walker.js. BOM.db already exists for fleet (504KB–8MB), lazy-fetch on Red Pill. IFC Drop: web-ifc extracts IfcRel* (3 queries, ~30 lines in import_worker.js). No Java install needed for end users.
  - Spec: `docs/NEW_FROM_REFERENCE.md` §4-6, §9, §17

**S270 NEXT: Grid Kinematics Engine — extract pure-math module + roof vertex recomposition. Prompt: `prompts/S270_GRID_KINEMATICS.md`. Spec: `docs/NEW_FROM_REFERENCE.md` §17.10.2–4.**

**S268+S269 DONE (2026-05-22): Attach-Map Recompose — governed translate/scale + bay-proportional.**
  - `doc_canvas.js` REWRITE: `recomposeAfterGridDrag` replaced nearest-delta with attach-map.
  - S268: ATTACH (centerline within 0.5m of grid → translate) + SPAN (body straddles grid → scale width).
  - S268: Eliminated 67% false positives from S267 nearest-delta (318 wrong moves on SC with 6 grid lines).
  - S269: Bay-proportional interior repositioning — unattached elements shift proportionally within their bay.
  - S269: TILE recount formula (`ceil(newWidth/step)`), FRAME coord replacement verified in tests.
  - Attach map built lazily, dirty-flagged on phase change. `_getMeshPosition` reads BatchedMesh/InstancedMesh transforms.
  - **Tests:** 149/149 PASS — test_s268_recompose (43 new), test_doc_canvas (54), test_verb_expand (20), test_bom_walker (20), test_bom_phases (12).
  - **Sandbox:** `/tmp/ootb-dev/sandbox/sandbox_recompose.js` + `sandbox_grid_attach.js` — algorithm proven on SC_BOM.db before implementation.
  - **Deferred (S270+):** Max+Photo icons. NEW geometry generation (Java Bridge). IFC export. Z-axis grids. GPU throttle. Save/recall. Timeline↔TM binding.

**S267 DONE (2026-05-22): BOM Walker + Verb Expansion — BOM.db drives phases, meshes follow grid. SW v436.**
  - `verb_expand.js` NEW: JS port of 7 Java verb expanders (TILE/ROUTE/FRAME/CLUSTER/SPRAY/LINE/LINE_MULTI). Pure math, zero deps.
  - `bom_walker.js` NEW: JS port of BOMWalker tree traversal via sql.js. Three-way dispatch, MAX_DEPTH=20 guard.
  - `doc_canvas.js` REWRITE: `_loadPhases` walks BOM tree (no flat CLASS_PRIORITY query). `_buildEnvelope` from BOM root AABB. Nearest-delta recomposition on grid drag.
  - `panels.js`: Lazy-fetch BOM.db fallback + IndexedDB cache. Reactivate Doc canvas when BOM.db arrives async.
  - `import_worker.js` +50 lines: IfcRelVoids/Fills/Aggregates → `bom_tree` table (IFC Drop path).
  - BOM data merged into extracted DBs: SH, DX, SC, HI, TE (m_bom + m_bom_line tables appended).
  - SH_BOM.db (127KB, 14 BOMs) and DX_BOM.db (283KB, 36 BOMs) created via IFCtoBOMMain pipeline.
  - 5 BOM.db files + 5 merged extracted DBs uploaded to OCI `bim-ootb` bucket.
  - **Tests:** 106/106 PASS — test_verb_expand (20), test_bom_walker (20), test_bom_phases (12), test_doc_canvas (54).
  - **Key finding:** BOM positions are floor-relative (local). Building origin bridges to world coords. Grid envelope from BOM root AABB (23.9×24.5m) is tighter than extracted scatter (43.5×43.5m) because BOM excludes outliers.
  - **Limitation:** Grid drag uses nearest-delta (dumb shift), not verb re-expansion. Roof slides instead of extending, tiles don't recount, openings don't cascade with host wall.
  - **Timeline note:** Timeline appears tied to Doc canvas, should be tied to TimeMachine ON/OFF state (spec update, not code fix).
  - **Next session (S268):** Verb re-expansion on grid drag + parent-child cascade. Prompt: `prompts/S268_RECOMPOSE_CASCADE.md`.
  - **Deferred (S269+):** Max+Photo icons. NEW geometry generation (Java Bridge). IFC export. Z-axis grids. GPU throttle. Save/recall. Timeline↔TM binding.

**S265 Phase 5 DONE (2026-05-21): UI Aesthetics Overhaul. SW v416→v431. See `prompts/S265_UI_AESTHETICS.md`.**
  - Foundation: `A.createPanel()` factory + `.bim-panel` CSS + `ICONS` registry (24 icons) + `A.icon()` factory
  - P1 DONE: Color Palette rebuilt — 5 icon-only slider rows (palette/sun/sunDim/lightbulb/sunrise), value fades on drag
  - P6 DONE: Standalone NLP 🎤 button removed — mic merged into Find panel search bar
  - P10 DONE: Help palette — 6 entries with expandable sub-items (blue/red bar toggle), G5 mobile focus guard
  - Find panel restyled to `.bim-panel` glass — dual-purpose input (NLP + element search), context-aware chips
  - All panels + pill + HUD at 50% opacity with blur(16px)
  - G1 partially fixed (overflow init reset), G2 DONE (focus stack dedup)
  - **Next:** Find panel mic not showing (debug), P2 Section slider, P3-P4 Info+Issues restyle

**S265c DONE (2026-05-20): Material color fix + unconditional render. SW v414→v416.**
  - Removed `_spread < 0.08` threshold — IFC colors as-is, NULL-only STD_MAT fallback
  - Sunglasses slider handles grey buildings (Terminal/LTU) on demand
  - Removed `_needsRender` gate — render every frame, sliders/palette/bbox instant
  - Overflow init reset (partially fixes double-click glitch)

**S265 Phase 4 DONE (2026-05-19): HUD unification + keyboard + z-index + markDirty. SW v404→v411.**

**S265 Phase 3 IN PROGRESS (2026-05-19→21): Share refactor. SW v411→v414. Deployed to ootb-dev.**
  - share.js rewritten as `setupShare(A)` — called by main.js `_mods` like sitecam.js (eager load, not lazy)
  - Pill Share onclick → `APP.quickShare()` direct call (preserves user gesture for `navigator.share`)
  - Mobile: canvas snapshot as JPEG File + `navigator.share({files:[photo], text})` — same pattern as sitecam.js
  - Desktop (Firefox, no Web Share API): preview card with Copy Link button
  - `buildShareUrl()` captures 7 contexts in URL hash: cam, tgt, pick, storey, xray, clash, tm, tour
  - Clash context delegates to `_buildClashDeepLink` (proven working S246 function, untouched)
  - Clash text body has discipline pair, element names, storey, overlap mm, severity — same as `_shareClashSnag`
  - Hash parser in main.js restores: pick, storey, xray, tour, tm, camera on load
  - `tmGetState()` exposed from time_machine.js for share URL
  - WhatsApp/Email hardcodes removed. Old sendWhatsApp/sendEmail functions deleted.
  - Whitebox: 13 share tests PASS (anchor URL, 10 context scenarios, clash text, delegation)
  - **BUG OPEN: Receiver-side clash restore not showing on Firefox.** Sender URL is correct (verified by whitebox anchor test). The hash parser + `_flyToClash` code is identical to the S246 deep-link that works. Clash context text appears in shared message. But recipient does not see clash highlights on load.
  - **WORKING URL (user-verified, old clash snag long-press):**
    `...index.html?db=https%3A%2F%2F...HospitalGarage_extracted.db#clash=01WyKs2cnByA_VPsbZDzFL~0GjpF04mX1K8P$TdM8fU2_&st=Existing%20Garage%20-%201st%20Level&cam=-49.82,-5.49,-33.29&tgt=-51.52,-6.91,-34.99&tol=25`
  - **NEW URL (quickShare → _buildClashDeepLink, same format but not restoring):**
    `...index.html?db=https%3A%2F%2F...HospitalGarage_extracted.db#clash=0IajW5Y89BRxvKnf5AfDkQ~1itcVTZhD87QA6mzuiLzD3&st=Existing%20Garage%20-%201st%20Level&cam=-50.02,-5.17,-22.33&tgt=-51.71,-6.58,-24.02&tol=25`
  - **Next session:** Debug receiver-side restore. Both URLs parse identically (whitebox proven). Issue likely in timing, Firefox console, or _flyToClash visual rendering. Must reproduce locally — do NOT deploy-and-ask-user.

**S2D30 Grid UX Troubleshoot DONE (2026-05-10): SW v293**
  - `grid_views.js` refactored — atomic single-responsibility: `classifyMesh`, `computeCutZ`, `applyFloorClip`, `clearFloorClip`, `boostLighting`, `restoreLighting`
  - IfcRoof/IfcCovering meshes fully hidden (`visible=false`) in floor plan — roof no longer bleeds through clip plane
  - Contours: white fill/stroke on dark bg, black on light — true reverse for print. No invented colors, no artificial ribbon.
  - Band filter: original next-storey lookup + 1.5m minimum clamp (fixes SampleHouse crushed band)
  - Cost panel: variance columns (Δ Qty, Δ Vol), ✕ close button fixed (innerHTML was overwriting it)
  - Panel toggle −/+ always visible (was mobile-only). Hides all UI chrome for screenshots.
  - Dwell/bookmark/flash removed from scissors — not requested.
  - Save Cut button on scissors — only in 2D mode (gated by `isIn2DView`)
  - `saveSectionFromScissors` exposed for card save from scissors slider
  - Snap-to-structural post-cluster alignment (§GD_SNAP_ALIGN)
  - 38 specs / 391 tests / 927 expects pass
  - **Next:** S251b polish (dwell flash, Save button in 2D, grid Enter)

**S251 Keyboard Modes DONE (2026-05-10): SW v295**
  - Key sequence engine: S=Sunglasses, SC=Screenshot, G/X/F/C/M/P/4/-/+/?
  - Command palette: ? key or 🛟 button, search filter, Report Bug + Documentation links
  - Panel focus: Tab/Shift+Tab cycles, blue glow, auto-expand collapsed, focus stack (Esc pops)
  - ListKeyNav: arrows, Shift+range, Ctrl+Space toggle, Ctrl+A, typeahead, slider step, PageUp/Down
  - Multi-select: storeys (show multiple), disciplines (hide unselected), clash list (red spheres + bbox frame)
  - Mutual exclusion: 2D↔Clash/Measure blocked in each other's mode
  - Static panel init: section/sunglasses/toolbar registered at page load
  - Zombie card fix: _noauto flag prevents autoCreateCards after user clears all
  - Title fixes: "Grid Dimensions"→"Plan Grid", Z ↗→Z ⊥, sunglasses × button
  - Mobile: zero impact (all keyboard paths guarded by _isMobile)
  - 197 tests (90 logic + 107 wiring), 40 specs / 407 tests / 1007 expects
  - **Open:** BUG-1 dwell flash, BUG-2 Save button in 2D, BUG-4 grid Enter → see `prompts/S251b_keyboard_polish.md`

**S2D31 Card-First View Model DONE (2026-05-10): SW v296**
  - Card = one SQL (queryStoreyGuids) → one scene pass (hide/fade/retain/clip) → contours
  - IfcCovering = wall/floor tiles, NOT roof — removed from HIDE_IN_FLOOR
  - `FADE_IN_FLOOR`: IfcSlab/IfcPlate → opacity 0.08
  - `autoCreateCards()`: door-count ranking (was lowest-z → picked basements, improved 19 buildings)
  - Save button always on when scissors ON (was wrongly gated)
  - 11 bugs found + fixed by CTFL analysis (state completeness, boundary, data flow)
  - Contour overlay meshes skipped in card pass (BUG W — was clipping 2D lines)
  - 41 specs / 417 tests / 1035 expects — all pass. Fleet: 30 buildings in 5s.
  - Deployed to ootb-dev. Tests: `specs/35-card-first-views.spec.js` + `specs/36-card-first-browser.spec.js`
  - **Next:** Browser smoke test, then `2D_024_editable_grid_lines.md` (drag highlight, hover, alignment)
  - **Unresolved 2D UX debt:** grid alignment, drag highlight, IFC popup, Terminal curtain walls, DX door arcs — see `prompts/2D_031_card_first_views.md` §Outstanding
  - **Known issues:**
    1. Terminal outer envelope walls: contour geometry may not cover curtain walls at cutZ=1.2m
    2. DX door arcs: `§DOOR_ARC_SKIP reason=no_leaf` — geometry BLOBs don't have door panels
    3. HITOS: verify GF wall visibility with current settings

**S226a DONE (2026-05-08): Localisation — rate JSONs + locale wiring + flag picker.**
  - 16 country rate JSONs (`deploy/dev/rates/`): MY, UK, US, AU, DE, FR, ES, CN, TH, JP, KR, SA, BR, ID, ZA, BD.
  - Each: 50 IFC materials, 10 trades, 6 equipment, full sequence/work_packages/provisions — all in native currency.
  - `LOCALE_RATE_MAP` in rates.js — auto-loads correct rate JSON per locale.
  - `_syncCur()` fix — CUR/CUR2/CUR_RATE as `var` so locale overrides work at render time.
  - boq_charts.html: BOQ table headers, summary footer, chart axis/legend labels all `_TRL.*`.
  - mep_report.html: all labels translated, charts-first layout, `initRateTemplate()` locale-aware, waits for `trl-ready`.
  - 🏠 Home + 🌐 Flag buttons on boq_charts, mep_report, clash_report, 2d.
  - `locale_loader.js` added to mep_report + clash_report.
  - Gantt chart height dynamic (scales with bar count).
  - Test: `26-locale-currency.spec.js` — 6/6 PASS.
  - **Next:** Phase 4 — translate ~30 remaining hardcoded strings in viewer JS modules (measure.js, city.js, import.js, main.js, tools.js, panels.js). See `prompts/S226_localisation.md` §Phase 4.

**S250b DONE (2026-05-07): Scissors-driven adaptive grids (2D_025 D1).**
  - `grid_scissors.js`: new module — 3-axis cut detection, debounced slider, dispose/restore lifecycle.
  - `grid_dims.js`: `detectGridsAtPlane(db, cutZ)` + hoisted filter/thin, sequential relabelling, IfcBeam/IfcMember.
  - `section_cut.js`: `lookupGeometry` fallback for BLOB-only DBs (SampleHouse schema).
  - `grid_door_arcs.js`: `extractLeafAxis` — real closed-polygon contours, bbox-based leaf detection. 3 arcs on SH verified (852mm double, 726mm singles).
  - `tools.js`: `localClippingEnabled` fix (pre-existing bug), slider/off callbacks.
  - Wall contour thickness from real mesh: 290mm exterior, 95mm interior (SH verified).
  - 114/114 grid module tests pass.
  - **Next:** D1 fine-tuning (stair symbols, roof removal, opening dims), then D2 (save section), D3 (print sheet).

**Spatial ERP P0-P2 DONE (2026-05-13): Core engine + registry + handlers — 79/79 tests.**
  - `deploy/dev/doc_engine.js` (253 lines): 6 tables (§3.1), StateMachine (5 states, 4 events), JournalEngine (rule-based auto-post on COMPLETED).
  - `deploy/dev/category_loader.js` (100 lines): getCategory, listCategories, renderLabel.
  - `deploy/dev/construction_seed.sql`: 8 containers, 2 docs, 2 lines, 4 categories, 8 metadata keys.
  - `deploy/dev/handlers/construction.js` (200 lines): 7 handlers — screenLead, planFAR, submitApproval, approve, reject, generateBOQ, closeLead.
  - `deploy/dev/kernel_ops.js`: +user_tag column (§2.3).
  - `SYSNOVA/index.html`: "ERP — GOD MODE" link in footer → `sandbox/erp.html`.
  - `deploy/dev/tests/test_doc_engine.js`: 79/79 tests — P0 core + P1 seed/registry + P2 full lead lifecycle.
  - **Next:** P3 — erp.html + swipe.js + role_band.js (UI layer). Keep offline-first (sw.js precache).

**S246b IN PROGRESS (2026-05-06): WASM/SW/panel hardening + local-first libs.**
  - Vendor libs (Three.js, OrbitControls, sql-wasm, SheetJS) localised to `lib/` in ootb-dev — single origin, CDN fallback. ootb-live stays CDN-only for A/B comparison.
  - SW v254: cache key strips `?v=N` (was causing cache miss → offline.html served as JS → initViewer undefined). `.js` fallback returns 503 not offline.html.
  - R-tree + indexes built eagerly after DB loads (was lazy on first clash open — caused matrix stall on large buildings).
  - `_countClashesRtree` rewritten as single SQL R-tree join (was N queries per discA element).
  - Clash snag: direct `drawImage` from WebGL canvas (was `toBlob→Image` roundtrip ~500ms → now ~5ms). `preserveDrawingBuffer` enabled.
  - Long-press: timer/flag cleaned on measure toggle off. Info card auto-dismisses instead of blocking.
  - Panel touch-through: `pointerdown.stopPropagation` on all static panels.
  - Swipe-hide exits measure mode. Swipe-show clears `collapsed` class.
  - Clash snag routing: Share/Save → clash-specific save with both GUIDs, overlap, deep-link.
  - Issues list filtered to active building.
  - 11 code quality fixes from codebase audit (setup guards, onerror on scripts, IDB error handling, etc.)
  - **Still investigating:** panel touch-through may still seep on some mobile browsers. Large building clash matrix may still have initial delay during R-tree population. InitViewer undefined may recur if SW cache cycle needs 2 reloads.
  - **Next session:** continue troubleshooting panel event propagation, verify R-tree eager build timing on large buildings, test SW v254 cache key fix across browsers.

**S246 DONE (2026-05-04): Clash Snag + R-tree perf + full-mesh LOD.**
  - Snag: long-press clash row → JPEG capture (async toBlob) → metadata strip (severity, GPS, timestamp) → freehand annotation → share via Web Share API + deep-link URL.
  - Deep-link: `#clash=guidA~guidB&cam=x,y,z&tgt=...` — recipient opens, 2s cinematic fly-to, red/orange highlights. Desktop + mobile.
  - Deep-link in Issues panel: "Fly to clash" (in-viewer, no reload) + Share button.
  - R-tree perf: pre-load discB into JS map (halves SQL calls), progressive loader + COUNT + EXISTS all R-tree.
  - SW v251, WASM preload (eliminates cold-start InitViewer), updateHash guards #clash= hash.
  - Home: 🌐 flag button navigates to landing page. Report: standards references, all-pair R-tree counts.
  - Accept propagation: Accepted status applies to all same IFC class pairs in session cache.
  - DLOD disabled: S232 InstancedMesh batching sufficient, full scene stays during clash analysis.
  - Clash viz: discipline-colored full mesh (25% opacity) + bright red/orange clipped overlap (depthWrite:false).
  - Report: R-tree counts across all pairs (envelope skip), max_report in clash_rules.json, standards references.
  - Dev banner baked into deploy/dev/index.html, absent from deploy/live/.

**S245c DONE (2026-05-04): R-tree + Clash Performance & UX overhaul.**
  - WASM swap: sql.js → rtree-sql.js@1.7.0 (CDN, SQLITE_ENABLE_RTREE). SW cache v249.
  - R-tree built async (5k batches, ~1.2s non-blocking). For S245d single-element lookups.
  - R-tree self-join O(N²) — not viable for pair-finding. All queries stay bbox arithmetic.
  - Matrix bg check: discipline envelope overlap (one GROUP BY, instant, accurate).
  - Cell click: LIMIT 30 storey-scoped (auto-picks top 5 storeys, avoids full-building N²).
  - Clash viz: clipped actual mesh (red+blue) at overlap zone. Camera targets overlap centre.
  - Overlap clipping padded to 0.3m min visibility. Row highlight on selected clash.
  - Matrix persists on cell click. Measure dots disabled while panels open.
  - Info card X close fixed. No auto-dismiss on canvas click.
  - Status: 🟡RVW 🟢SLV ⚪ACC with live counts. Right-click/long-press/double-click toggle.
  - clash_rules.json: 6→12 rules (added ELEC/FP/ACMV pairs, removed dead HVAC/PLUMB).
  - Right-click empty space = whole-building info card (all storeys, all disciplines).
  - HTML report: matrix snapshot, stat cards, 6 Chart.js charts (severity, status, disc pair,
    class pair, discipline risk radar, top offenders), matrix summary table, editable action sheet.
  - CSV export from HTML report (includes editable fields). Sorted by severity, capped at 100.
  - **Next:** S245d — see `prompts/S245c_rtree_clash.md` §S245d. Key: query heat problem.

**S245b DONE (2026-05-04): Clash Detection + Measure UX overhaul.**
  - Measure: tap-same-dot for area (replaces double-click), DB-backed info card, adaptive status text.
  - Clash Matrix: visual grid with 3D CSS spheres (pulsing grey → green/orange/red).
  - Lazy loading: matrix instant, async sampled check per pair, full query only on click.
  - Click cell → LIMIT 30 paginated clash list with tolerance slider (1-100mm).
  - Click clash row → fly-to + clipped actual meshes at overlap zone (depthTest:false).
  - Review status cycle: Reviewed → Resolved → Accepted (localStorage persisted).
  - Excel export from matrix title bar. clash_rules.json: 6 discipline pair rules.
  - Glass UI (backdrop-filter blur), draggable panels, constant-size measure dots.
  - Runtime SQL indexes (discipline, storey, center_x). Performance: skip dimming >3k meshes.

**S244 DONE (2026-05-03): Sunglasses — material contrast slider + theme toggle.**
  - 🕶 button replaces ☼: click = reverse background (light/dark), opens slider panel.
  - Slider (0–100, 10 zones): recolors all meshes by IFC class/storey/discipline with 10 strategies.
    Warm pastels → cool pastels → earth tones → storey warm/cool → discipline → zebra → mono → random → HARD.
  - Each IFC class gets unique color via golden-angle hue spacing. Largest classes get most contrasting slots.
  - Near-white materials (RGB > 0.85) auto-tamed in `_getMaterial` at streaming time.
  - `ifcClass` stored on mesh/instanced userData for grouping. Zero perf cost — just material swaps.
  - Deployed to `bim-ootb-live/sandbox/`. Proven on Terminal canteen (IfcSlab vs IfcFurniture contrast).

**S242 DONE (2026-05-03): Single-DB deployment, IFC bbox placeholders, instanced IFC export.**
  - Viewer: single DB only (`A.libDb = A.db`), no library fetch. Config: `LIB_URL` removed.
  - Bbox placeholders: use IFC `bbox_x/y/z` from `element_transforms` (not fixed cubes).
  - IFC export: IfcMappedItem instancing (geometry once per hash, elements reference via map).
    Batched geometry loading avoids OOM on large buildings (122K elements proven).
  - VALID_DISCS expanded: +AIR, DUCT, HVAC, MECH, FIRE, SPR, GAS, LIFT, CONV, etc.
  - All 22 buildings re-extracted as single DBs, deployed to `bim-ootb-live/buildings/`.
  - DB queryable immediately on load (IndexedDB cached) — bbox enables 4D/5D/clash without meshing.

**S241 DONE (2026-05-02): Drop IFC merge + disc from filename + Node.js extractor.**
  - Multi-disc merge: combines elements into one DB (not version stacking). Building name normalized.
  - Disc from filename: aliases (ELE→ELEC, FIRE→FP, MECH→ACMV, etc.). Landing-side override.
  - Variance only on "revised" filename. 10-col transforms fix (bbox columns).
  - Node.js extractor: `scripts/extractIFC2DB.js --disc HEAT`. All 25 OCI buildings re-extracted.
  - Proven: LTU SAN+VOID merge, all UNMERGED/ filenames.
  - Specs: `prompts/DropIFCMergeNoVarianceDISC.md`, `docs/SQLite3D_Schema.md`

**S239 DONE (2026-05-01): Deep refactor — `full` branch.**
  - helpers.js, 18 traverse→0, 31 db.exec→dbQuery, 4 SQL injections fixed
  - Lazy-load navigate/wizard, sw.js versioning, minify script (44% reduction)
  - `deploy/dev/` is canonical source. OCI full = minified dev.
  - Remaining: wizard.js traversals, measure.js traverse (low priority)

**S236 DONE: 2D Plans browser DXF viewer.** `deploy/dev/2d.html`, Canvas2D, dxf-parser.

**S243 DONE: Offline PWA.** SW precache, manifest, install prompt, offline/online toast. 45/45 Playwright PASS + 7/7 offline sandbox test. Mobile confirmed.

**S240 UPDATED: 4D Gantt Sync spec.** Added §0 Prelim Check (8-point audit), §0.3 Template System (rates.js-driven, user-checkable, export/import). Ready to implement.

**S233b DONE: Find & Navigate.** Indoor wayfinding. 26/26 Playwright PASS.

**S232 DONE: Mobile merge + InstancedMesh.** 95% draw call reduction on mobile.

**S228-S231 DONE: Drop Zone Multi-Format Import.** IFC/OBJ/DAE/GLB/FBX/3DS/STL.
  - Classification Wizard, IFC Export, InstancedMesh batching. 108/108 Playwright PASS.

**S225b DONE: Rates + Locale.** `rates.js`, 15 locale files.

**S222-S224 DONE: DB Refactor + Diff + VO + Versioned Cards.** Diff engine, VO Excel.

**S220 DONE: IFC Browser Import.** web-ifc WASM, IFC2x3+IFC4 proven at 122K elements.

## OCI Deployment

- Live: `bim-ootb-live` (SYSNOVA landing + viewer + single DBs). Always upload here.
- Single DB per building: `buildings/{Name}_extracted.db` (metadata + geometry + bbox).
- `deploy/sandbox/` stale (last ~S225) — not used for deploy. `deploy/dev/` is canonical.
- Deploy SOP: `deploy/OCI_UPLOAD.md`

## Earlier Work (compressed)

- **S200-S210:** BIM OOTB browser viewer, OCI deployment, BOQ charts, health checks
- **S195-S198:** Direct DB streaming (replaced Blender .blend pipeline)
- **S188-S193:** RTree, nD engine, DLOD — all Blender-era, superseded by browser viewer
- **S165-S186:** GN instances, chunked loading, cockpit UI — GN HALTED, RTree won
- **2D Layout:** Phase A closed, Java pipeline 5/5, 13/13 conformity. Browser DXF viewer (S236).
- **DAGCompiler:** S190 fleet 21 buildings. S104 IFCtoERP complete.

## Reference

- Docs site: https://red1oon.github.io/BIMCompiler/
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
