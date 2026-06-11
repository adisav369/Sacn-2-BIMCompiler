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
- **[BIM-viewer → own lane] Time Machine Gantt** (`boq_charts.html`) not wired to `kernel_ops`. (`project_time_machine`)
- **[BIM-viewer → own lane] Grid UX debt** — 8 §-tags to verify + merge the two save systems into one. (`project_grid_ux_debt`)
- **[BIM-viewer → own lane] 4D capture** — widened DDL + `§GANTT_SOURCE` branch + coverage badge. (`project_4d_capture`)
- **[BIM-viewer → own lane] Ground+Sky** — live cloud layer (C1) not done. (`project_ground_sky`)
- **[BIM-viewer → own lane] Settings JSON editor** — Phase 2 editable schedule parked. (`project_settings_json_editor`)
- **[BIM-viewer → own lane · NOT vital, noted for future revisit] Pill-registry drift — latest icons hand-roll DOM instead of going through the registry.** The S281/S282 registry (`_actions` in `viewer/panels.js` + `ICONS` table + `PillBuilder`/`pill_builder.js`) is the single source for toolbar icons. Two recent areas skip it:
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
