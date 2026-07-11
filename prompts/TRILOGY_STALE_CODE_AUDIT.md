<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# TRILOGY STALE-CODE AUDIT — mark dead/superseded code across Modeller+Viewer+ERP (2026-07-12, Fable one-shot)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `modeller/`, `viewer/`, `erp/` (the "trilogy" — 277 top-level files, ~177K lines,
confirmed this session) — top-level files only (not node_modules/tests/build). Generalizes
`viewer/2d.html`'s pilot case (below) into a full trilogy sweep. User's own framing (2026-07-12):
"make the prompt more general... should it be done by Fable one shot, to review the whole codebase
in use by trilogy only, and mark which are not or stale for removal?" Answer worked out below —
**one-shot for DISCOVERY/MARKING only, NOT for deletion.** Read this whole file before running
anything. PUSH PAUSE LIFTED for this repo — commit locally, push the REPORT when done (docs-only,
no PR ceremony needed for a marked-report commit). Actual code removal is explicitly OUT of this
task's scope (see "Why discovery and deletion are split," below) — do not delete files in this pass
even if a finding looks obvious.
```

## Why discovery and deletion are split (read before objecting to the scope)
This project's own standing discipline, used consistently all session (DiscWalk branch closeout,
FIND_PANEL_PLANT_ROOM_GATE_FIX, WALKER_FIXTURE_RENDER investigation): **investigation tasks report,
they don't act** — a separate pass (often a different session, always independently verified) does
the actual change, once a human/Manager has reviewed the findings. At trilogy scale (277 files), a
single one-shot pass that BOTH discovers AND deletes has no review checkpoint before something
real is destroyed — that's the wrong risk shape for this size of change, however good the evidence
looks in the moment. One-shot IS appropriate for the discovery/marking half (see feasibility below);
it is not appropriate for bundling deletion into the same unreviewed pass.

## Feasibility check (done before writing this, not assumed)
- 54 Playwright spec files exist (`tests/specs/*.spec.js`) — a real, substantial exercise of the live
  apps, confirmed this session. Running the full suite with coverage collection once is mechanically
  tractable in a single session (each spec is `@fast`-tagged or a normal integration test, not a
  multi-hour job).
- Three real entry points anchor the "trilogy": `modeller/modeller.html`, `viewer/viewer.html`,
  `erp/idempiere.html` (there are also `index.html`/`index2.html`/`gallery.html`/`LargeCity.html` at
  the repo root — landing/launcher pages that route INTO the trilogy; treat these as additional
  entry points too, not part of the trilogy being audited).

## Method — same higher-leverage approach as the 2d.html pilot, applied trilogy-wide
**Two complementary passes, run both — they catch different things:**

1. **Static reachability (highest-confidence signal, do this FIRST — it's cheap and decisive).**
   From the entry points above, trace every `<script src=...>`, dynamic `import()`, `fetch()` of a
   `.js` file, `window.open()` target, and iframe `src` — build the set of files EVER referenced from
   any entry point, directly or transitively. **A file that appears in `modeller/`, `viewer/`, or
   `erp/` but is in NO entry point's reachability set at all is a confirmed orphan** — nothing loads
   it, ever, regardless of what a test suite does or doesn't exercise. This is stronger evidence than
   coverage (a referenced-but-never-covered file might just be under-tested; a never-referenced file
   is definitionally dead weight).
2. **Dynamic coverage (for files that ARE reachable, but might still be dead in practice).** Run the
   full 54-spec suite with Playwright V8 coverage collection (`page.coverage.startJSCoverage()`/
   `stopJSCoverage()`, same method as the 2d.html pilot below) across all three apps. Files/functions
   reachable-but-zero-coverage across the ENTIRE suite are candidates — same caveat as the pilot: not
   automatic proof, do a quick manual sanity check per flagged file before marking it, since the test
   suite may simply not exercise every real feature.
3. **Combine into a 3-tier marking, not a binary live/dead:**
   - **CONFIRMED ORPHAN** — unreachable from any entry point. Highest confidence, name for removal.
   - **STALE CANDIDATE** — reachable but zero coverage across the full suite, sanity-checked
     manually and still looks dead. Name for removal, flag the sanity-check reasoning.
   - **LIVE** — reachable AND covered, or reachable-but-uncovered where the sanity check found real,
     plausible functionality the suite just doesn't happen to exercise (name this reasoning too, so
     the "why we kept it" is on record, not silent).

## Task 0 — the 2d.html pilot (already scoped in detail, do this one FIRST as the proof-of-method)
`viewer/2d.html` (48,307 lines) is the concrete case that started this audit — already has a full,
detailed task spec ready to execute (grid-manipulation half confirmed superseded by
`viewer/grid_overlay.js`, DXF half confirmed live via `docs/AboutMore.md` + 9 active tests incl. one
`@sacred`). Doing this one first, in full (structure map → split → archive → re-verify both affected
specs green), proves the method works before applying it at trilogy scale — don't skip straight to
the full sweep untested. Full detail (don't re-derive): see this file's git history / the prior
version of this spec — **[full original Task 1-3 detail, condensed]:**
1. Static+coverage map `2d.html`'s ~69 functions/10 script blocks against `14-2d-plans.spec.js`'s 9
   real DXF tests.
2. Split: keep DXF+SHARED code live (in `2d.html` or a renamed file, your call), move confirmed
   GRID-ONLY code to an archive location (physical `archive/` folder is a nice-to-have, git history
   is the accepted baseline per the user's own words — ask if genuinely unclear which they want).
3. Re-verify `14-2d-plans.spec.js` (9/9, incl. `@sacred`) AND `28-grid-overlay-init.spec.js` both
   green post-split. Report before/after line counts.

## Task 1 — trilogy-wide static reachability sweep
Build the full reachability set from all entry points (modeller.html, viewer.html, idempiere.html,
+ the 4 root launcher pages) across `modeller/`, `viewer/`, `erp/`. Report every file NOT in that set
as a CONFIRMED ORPHAN, with how you traced it (don't just assert "unreferenced" — show the grep/trace
evidence per file, this is a reviewable checkpoint).

## Task 2 — trilogy-wide coverage sweep
Run the full 54-spec suite with coverage across all three apps. Cross-reference against Task 1's
reachable-but-not-yet-classified files. Produce the 3-tier marking (CONFIRMED ORPHAN / STALE
CANDIDATE / LIVE) for every file in the trilogy, with the evidence and (for STALE CANDIDATE/LIVE
calls) the sanity-check reasoning, not just a verdict.

## DONE WHEN
Task 0 (the pilot) fully executed and verified — this is the proof the method works, not optional.
Task 1's reachability map committed as its own reviewable checkpoint (real trace evidence per
orphan, not assertions). Task 2's full 3-tier marking table committed, covering every file in the
trilogy. **No files deleted in this pass** — the marking table is the deliverable; removal is
explicitly a separate, later, per-item follow-up (this file's own scope guard, re-stated: report,
don't act).

---

## 2026-07-12 — EXECUTION (Fable one-shot). Task 0 + Task 1 results

### Scope corrections found during execution (report-first, per this file's own rules)
- Depth-1 trilogy file count at `origin/main` b83c791 is **352**, not 277 (repo grew since the count).
  Classification below covers all 352 depth-1 files; subdirs (`tests/`, `lib/`, assets) traced for
  reachability but not individually marked.
- The 54-spec suite navigates THREE URL universes (`/dev/…` = bim-compiler deploy layout,
  `/bim-ootb/…` = home-dir-root layout, `/landing2.html` = deploy root). No single server root
  satisfies all as-checked-out — audit runs used a symlink root (dev→viewer + index.html→viewer.html,
  buildings→bim-compiler/deploy/buildings, bim-ootb→repo). This layout drift is itself a finding
  (see spec 38 below and the production-readiness section).

### Task 0 — 2d.html pilot, RE-STEERED mid-run by user (2026-07-12)
User: *"2D html is no longer used and Modeller 3DGrid has taken over.. so i wana remove it, but
check first any prior art or learning there"* + *"otherwise it be just archived without risk as no
icon calls it."* This supersedes the split-and-keep-DXF plan for this one file. Verified before
acting: the 2D Plans button toggles `grid_overlay.js`; `2d.html` survived only as a degraded
fallback branch in `main.js open2DPlans()` that spec 28 (T_INIT_02/04) proves never fires.

**Prior-art check (the requested deliverable) — what 2d.html contained, where it lives now:**
- **DB→DXF sheet synthesis** (`generateFromDb`, `sectionToEntities`, `annotationEntities`,
  `elevationToEntities`, `gridToEntities`, `exportDxf`) — the only trilogy code that generated CAD
  deliverables from the extracted DB. The generation pipeline proper lives in **bim-compiler**
  (`2D_Layout/python/*` ezdxf + `DAGCompiler …/drawing2d/dxf/`), unaffected by this removal.
- **BIMSRC xdata GUID round-trip** — DXF entities tagged with source-BIM GUIDs surviving parse
  (spec 14.4/14.9). Locked @sacred baseline for SH_FLOOR.dxf: **entities=292, layers=12,
  bimsrc=93** — recorded here since the spec retires with its subject.
- **Canvas2D DXF renderer** (bulge polylines, splines, dimensions, INSERT, xdata panel) +
  `dxf-parser.js`/`dxf_export.js`/`title_block.js`.
- 46,970 of the file's 48,307 lines were an inline pre-baked SH DXF baseline string (data, not code).
- All of it remains in git history at the parent of the removal commit.

**Removal executed** (bim-ootb branch `fable/trilogy-stale-audit`, commit `f94d930`, LOCAL —
not pushed): deleted `viewer/2d.html`, `viewer/dxf_export.js`, `viewer/dxf-parser.js`,
`viewer/title_block.js`, `viewer/dxf/*.dxf` (14 sheets), `tests/specs/14-2d-plans.spec.js`;
kept shared satellites `section_cut.js`/`grid_dims.js`/`elevation.js` (live consumers verified);
`main.js` fallback now reports "2D unavailable" instead of opening a dead link; `sw.js` precache
pruned + CACHE_VERSION v742→v743; `project_technical.md` stale entries pruned.
**Witness:** spec 28 → 4/4 green post-removal (13.4s); `audit_sw_precache.js` → 109 found /
0 missing; `eslint viewer` → clean. `audit_specs.js` flags `38-sh-dx-2d-runtime.spec.js` (5 SKIP
paths) — PRE-EXISTING on main (reproduced in the untouched checkout), caused by its hardcoded
`../../../../deploy/buildings/…` path assumption; unrelated to this change, left as a finding.

### Task 1 — trilogy-wide static reachability (committed as its own checkpoint)
**Method:** BFS from 7 entry points (`index.html`, `index2.html`, `gallery.html`, `LargeCity.html`,
`modeller/modeller.html`, `viewer/viewer.html`, `erp/idempiere.html`); edges = every path-like
string literal + src/href attribute that resolves to a repo file (deliberately over-approximating,
so "unreachable" is a strong claim). Every orphan candidate below was then adjudicated by repo-wide
basename grep with the actual reference sites read — comment/doc mentions do NOT count as reachability.
Tracer: session scratchpad `reach.js`; raw map `reach_out.json`. Numbers: 1,660 repo files scanned,
438 reachable from entries, 352 depth-1 trilogy files → **310 reachable, 42 static-orphan candidates**,
adjudicated below. A second BFS with sw.js precache edges REMOVED isolates the **precache-only** class
(13 files reachable through a service-worker precache list and nothing else).

**CONFIRMED ORPHAN — code/pages, zero non-comment references anywhere (evidence: basename grep hits
listed; "NOTHING" = no hit outside the file itself):**
| file | evidence |
|---|---|
| viewer/2D_Editor.html | NOTHING |
| viewer/2D.png | NOTHING |
| viewer/city_bench.html | NOTHING (contrast dlod_bench.html, driven by tests/run_bench.js) |
| viewer/s210_test.js, s211_test.js, s220_test.js | NOTHING — S-era dev scripts at depth-1 |
| viewer/test_all.js (+ walk_math_test.js) | walk_math_test referenced ONLY by test_all; test_all by NOTHING |
| viewer/bom_tree.js | superseded — viewer.html loads `bom_engine/bom_tree.js`; modeller has own copy |
| viewer/idmp_session.js | superseded — erp/idempiere.html loads erp/idmp_session.js (erp-relative) |
| viewer/manifest.json | stale copy — root manifest.json is fetched (index.html:552); sw precaches manifest.webmanifest |
| viewer/swipe.js | no loader; gestures COPIED into hba_mobile_stack.js ("adopted from swipe.js") |
| viewer/contribute.js | no loader; share.js absorbed its validation ("same as contribute.js") |
| viewer/category_loader.js, viewer/doc_engine.js | no html loads them; only legacy node tests + erp_panel.js header comment (erp_panel itself dead, below) |
| viewer/mep_qto_populate.js | only eslint.config.js lists it |
| viewer/route_walker.js | viewer/sw.js precache ONLY — no loader anywhere |
| viewer/erp.html, viewer/idempiere.html | precache-only; superseded by erp/erp.html + erp/idempiere.html |
| viewer/specs.jpg, viewer/submit.png, viewer/sunglass.png | NOTHING (icons are inline SVG now; pills manifests don't list them) |
| erp/erp_panel.js, erp/role_band.js, erp/menu_seed.js | precache-only chain; NO html loads any of them |
| erp/migrate_showme.js | superseded — overlay_kit.js extracted it "byte-for-byte"; sw comment: about_diy.js "replaces migrate_showme.js here" |
| erp/spike_writepath_browser.log | tracked LOG FILE in the app dir (junk; prompts/COMBINED_ERP_LANE.md mention is historical) |
| modeller/room_templates.json | superseded by config/room_templates.yaml (room_type_classifier.js); only a test witness reads the json |

**STALE CANDIDATE — unwired but with documented intent; user/Manager call per item:**
| file | reasoning |
|---|---|
| erp/chat_lens.html + chat_lens.js + feed_fold.js | documented in README.md:139 as a component, but NO page/pill links or loads any of the trio — feature parked, not shipped |
| erp/kanban_lens.html | comment-only references; kanban_lens.js itself IS live via idempiere seam — the standalone page looks superseded by embedding |
| erp/spike_writepath.html | spike harness, driven only by tests/drive_spike.js + prompt docs |
| erp/ad_table_map.js | dormant-by-design bridge ("behavior-preserving until turned on", ad_data.js:12) — never activated anywhere |
| erp/erp_key_epochs.js | precached + witness W-ROSTER-VERIFY exists, but no page loads it — #630 roster design not yet wired |
| erp/migrate_agent.js (depth-1 copy) | duplicated by erp/idempiere_agent/migrate_agent.js (the zip's source); user-facing download points at the ZIP (about_diy.js:199) |
| viewer/construction_seed.sql, viewer/schema_5table.sql | read only by legacy node tests; erp_kernel.js embeds its own runtime copy of the schema |
| erp/preview_demo.db | tracked binary; referenced only by a poc test + comment (also a DB-policy violation, see below) |

**LIVE despite looking dead (kept, with the why on record):**
- viewer/offline.html — sw.js offline navigation fallback (runtime-served, not just precached).
- erp/ninja_sample.xlsx — ninja_pill.js `grab('ninja_sample.xlsx')` local-first + GH fallback.
- erp/idempiere_agent.zip — About/DIY download artifact (about_diy.js) — but flag: a tracked binary zip duplicating erp/idempiere_agent/.
- erp/sfx.json — panels.js settings registry + sfx.js/wh_walk.js consumers.
- viewer/dlod_bench.html — dev bench driven by tests/run_bench.js + run_bench_mobile.js.
- modeller/Terminal_meta.db, modeller/SampleCastle_ARC_extracted.db — runtime data fetched via
  COMPUTED names (`${prefix}_meta.db` / `${name}_extracted.db` — real_geometry.js §GEO-SPLIT); LIVE
  as data, but tracked multi-MB binaries in git (policy flag below).
- Depth-1 .md docs (erp/ERP.md, HolyGrail.md, OpLogERP.md, BIMERPPaper.md, DistributedERP.md,
  migrate_compare.md, viewer/NLP_QUERIES.md, viewer/project_technical.md) — docs class, heavily
  cross-referenced from code comments; reachability is the wrong test for docs. Kept.

**Remaining 310 depth-1 files: LIVE (reachable from an entry point; per-file referrer recorded in
reach_out.json `reachedFrom`).**

### Task 2 — dynamic coverage sweep + final 3-tier marking
**Run:** full suite, 458 tests (desktop project), V8 JS coverage per test via an auto-fixture patch
in the worktree's throwaway @playwright/test install → 445 coverage captures, 23.7 min.
**Results: 168 passed / 252 failed / 38 skipped.** The failures are dominated by environment fit
(the suite addresses the bim-compiler deploy layout; see layout-drift finding) — for THIS audit the
coverage signal is what matters: a page/script that LOADED counts as executed regardless of later
assertion failures. Raw log: session scratchpad `run_full_suite.log`; aggregation `agg_full.json`.

**Honesty bounds of the coverage evidence (state before the verdicts):**
- **Zero specs drive modeller/ or erp/** (grepped: no spec references modeller, idempiere, or
  erp.html). The prompt's assumption that the 54-spec suite exercises "the live apps" holds for
  viewer only. Modeller/erp markings therefore rest on static reachability + adjudication + their
  own node/witness harnesses (modeller/tests/witness_*, erp/tests/poc_*), NOT Playwright coverage.
- Page JS coverage cannot see: Web Workers (ifc_export_worker/import_worker/mesh_import_worker),
  the service worker itself, JSON/image/db assets, or popup tabs (window.open surfaces). Files in
  those classes are never "condemned by zero coverage" here.

**Viewer (136 reachable depth-1+lib files in the coverage frame): 105 LOADED by the suite
(LIVE-covered, per-file load counts in agg_full.json). The 31 reachable-but-never-loaded, each
sanity-checked:**
- Coverage-corroborated orphans (already CONFIRMED in Task 1): viewer/erp.html,
  viewer/idempiere.html, viewer/route_walker.js — precache-only AND never loaded across 458 tests.
- Popup/new-tab surfaces the suite never followed (LIVE): clash_report.html (opened by
  clash_report.js), schedule_editor.html + schedule_editor_ui.js + foreign_schedule.js (opened by
  time_machine.js §SE-C; editor page script-tags the other two).
- Lazy-loaded feature modules with a verified loader in main.js (LIVE): navigate.js,
  navigate_controls/engine/find/grid/path.js (deliberate lazy-load, main.js:137-147), wizard.js.
- Coverage-blind classes (LIVE, evidence = verified consumer): workers (3 above), sw.js,
  offline.html (sw fallback), manifest.webmanifest, data/assets (clash_rules.json, grid_rules.json,
  ground_config.json, corporate.json, sfx.json, dagevu_*.json ⇐ modeller/bonsai_library.js,
  mep_rw.db ⇐ routewalker.js, help.png, YT.png, hba_cctv_still.jpg).
**Net: the coverage sweep produced NO new stale candidates in viewer beyond Task 1's static
findings — and corroborated all three it could see.** That is itself the pilot-method result: the
conservative static tracer + per-file adjudication was accurate; dynamic coverage confirmed rather
than contradicted it.

**Modeller (52 reachable) / ERP (122 reachable): marked LIVE-static.** Static reachability from
their entry pages is the operative evidence; the Playwright suite contributes nothing here (bound
stated above). Their orphans/stale candidates are already itemized in Task 1's tables.

**FINAL 3-TIER TALLY (352 depth-1 trilogy files @ b83c791):**
- CONFIRMED ORPHAN: 24 files (Task 1 table; 3 of them additionally corroborated by zero loads
  across 458 tests) + the 6-file 2d.html set already removed in Task 0 (commit f94d930).
- STALE CANDIDATE: 10 files (Task 1 table — each carries its reasoning; user/Manager call).
- LIVE: everything else — 105 viewer files with direct execution evidence, the rest by verified
  reference chains (reachedFrom map in reach_out.json).
**No files deleted in Task 1/2** (per this file's scope guard). The only removal this session is
Task 0's 2d.html set, which the user directed explicitly mid-run.

### Task 4 (user-added mid-run) — high-level production-readiness review
User steer (2026-07-12): *"look at possible high level code issues that this is heading production
level as SQLite WASM browser based, offline mode, local first app."* Findings ranked by risk to
that exact profile. Report-only — no fixes applied in this pass.

1. **The op-log kernel is forked 3 ways.** `kernel_ops.js` exists in erp/ (51,421 B), modeller/
   (36,270 B), viewer/ (32,470 B) — all three DIVERGENT (md5-verified). For a local-first system
   whose durability story is op-log replay, three drifting kernels is the #1 structural risk:
   an op recorded on one surface may replay differently (or not at all) on another. Same fork
   pattern, smaller blast radius: `grid_kinematics.js`, `real_placement_resolver.js`,
   `routewalker.js`, `teams_embed.js` (modeller vs viewer, all divergent), `idmp_session.js`
   (erp live vs viewer stale), `bom_tree.js` (identical twin, viewer copy orphaned). `common/`
   already exists and works (about_diy, pill_builder, whole_history) — the pattern is established,
   these modules just never moved. Recommendation: kernel first (single `common/kernel_ops.js` or
   an explicit op-schema version stamp + cross-surface replay witness), the rest opportunistically.
2. **Same-app service worker registered under different URLs per page.** erp/idempiere.html
   registers `sw.js?v=683`, erp/erp.html `sw.js?v=600`, glassbowl pages bare `sw.js`; viewer.html
   `sw.js?v=527` vs boq_charts.html `sw.js?v=594`. The registration URL (query included) identifies
   the SW — alternating between pages of the SAME app flips the registration back and forth,
   re-running install/activate and re-fetching the precache (113–126 entries) for zero benefit.
   Offline correctness survives (same CACHE_NAME) but update churn + bandwidth is real. Fix: one
   canonical registration URL per app (drop `?v=` — SW updates are byte-diff driven; CACHE_VERSION
   already handles cache turnover).
3. **Hand-maintained precache lists ship confirmed-dead files to every offline user.** Before this
   audit, viewer/sw.js precached the orphaned viewer/erp.html, viewer/idempiere.html,
   route_walker.js (and 2d.html + 3 DXF satellites, now removed); erp/sw.js precaches the unwired
   erp_panel.js/role_band.js/menu_seed.js/erp_key_epochs.js chain plus .md docs
   (DistributedERP.md, migrate_compare.md). Every CACHE_VERSION bump makes every offline user
   re-download dead bytes. `tests/audit_sw_precache.js` checks existence, not liveness — the gap
   this audit's reachability tracer closes. Recommendation: generate precache lists from the
   reachability set (or at minimum prune the entries named here after review).
4. **~4.3 MB of identical sql.js WASM vendored 5×.** sql-wasm.wasm (631 KB) ×3 (viewer/lib,
   modeller/lib, erp/sqljs — byte-identical) + sql-wasm-fts5.wasm (1.2 MB) ×2 (viewer/lib, erp/lib).
   Per-app scopes mean a user touching all three surfaces downloads the same engine 3–5×, and an
   upgrade must be repeated in five places to avoid version skew (none today — copies are in
   lockstep). One shared `common/lib/` copy fixes both. Related version-hygiene note:
   project_technical.md says sql.js 1.10.3, root package.json declares ^1.14.1, tests pin 1.14.1 —
   the doc is stale and there is no single pinned source of truth.
5. **Tracked binaries + a committed browser log in the app tree.** modeller/Terminal_meta.db,
   modeller/SampleCastle_ARC_extracted.db, erp/preview_demo.db, erp/idempiere_agent.zip,
   erp/ninja_sample.xlsx are git-tracked (several also .gitignore-listed — ignore rules don't
   untrack); erp/spike_writepath_browser.log is a committed debug log. Contradicts the standing
   DB-distribution policy (runtime data → OCI; content fixes → SQL migration + self-heal loader)
   and feeds the LFS/clone-weight problem. The .zip additionally duplicates erp/idempiere_agent/
   (its own source dir) — two things to keep in sync by hand.
6. **The test suite cannot run against the repo's own layout.** Specs address three URL universes
   (`/dev/…` = bim-compiler deploy tree, `/bim-ootb/…` = home-dir root, `/landing2.html` = deploy
   root); the checked-in playwright.config serves the repo's PARENT directory. As checked out,
   `/dev/…` resolves to nothing — this audit had to synthesize a symlink root to run at all.
   Meanwhile spec 38 hardcodes `../../../../deploy/buildings/…`, silently SKIPs 5 of its tests when
   absent (audit_specs.js RULE 2 FAIL — pre-existing on main), and CI runs only the s274 golden
   path. For a production-bound offline app, the suite must run green from a bare clone in CI, and
   a SKIPped test must be loud, not green-looking.
7. **Near-duplicate filenames invite wrong-file edits.** viewer/routewalker.js (live, script-tag'd
   by viewer.html + modeller.html) vs viewer/route_walker.js (orphan, precache-only) differ by one
   underscore. Same shape: viewer/bom_tree.js (orphan) vs viewer/bom_engine/bom_tree.js (live).
   Removing the orphans (Task 1 table) eliminates the trap.
8. **Memory headroom on mobile is untested against real DB sizes.** buildings/ ships a 75 MB
   extracted DB (HHS_Office_Federated); sql.js loads whole DBs into the WASM heap. viewer has the
   httpvfs lazy-range path and modeller's bonsai_library uses it, but the import/self-heal paths
   are full-load. No witness exercises a large-DB open on a mobile profile — worth one before
   calling offline production-ready (ties to the existing Import-IDB-limit lane).

### DONE — status against DONE WHEN (2026-07-12)
- Task 0 pilot: executed in re-steered form (user's own mid-run directive: remove, after prior-art
  check) — prior art recorded above, removal committed LOCALLY as bim-ootb `fable/trilogy-stale-audit`
  @ f94d930 with witnesses. NOT pushed (code, not report — pushing/merging is the Manager's call,
  and spec 14 retirement includes an @sacred test, which deserves an explicit human ack).
- Task 1: reachability map committed as its own checkpoint (bim-compiler 0d72920df).
- Task 2: 3-tier marking above, committed with this section.
- Added mid-run by user: production-readiness review (previous section).
- Suggested follow-ups, strictly separate passes: (1) delete/park the CONFIRMED ORPHAN set after
  review; (2) decide the 10 STALE CANDIDATEs; (3) kernel_ops.js unification (production finding #1);
  (4) precache generation from reachability; (5) deploy-side cleanup of the retired 2d.html set in
  bim-compiler deploy/dev + OCI dev bucket (bim-ootb-side removal does not touch deploys).

### 2026-07-12 — FOLLOW-UP EXECUTED: orphan sweep (user go-ahead: "U go ahead to do it")
2d.html retirement merged as bim-ootb #750 (squash 375bf32). Orphan sweep executed off fresh main
as `fable/orphan-sweep` → PR #751 (auto-merge enabled): removed the CONFIRMED ORPHAN set (29 files)
+ **viewer/lib/kernel/ — a 22MB occt-wasm copy byte-identical to modeller's, referenced by NOTHING
in viewer** (subdir find, caught while sizing; modeller's working copy untouched). Wiring: viewer
sw v743→v744 (-3 precache entries), erp sw v763→v764 (-3), poc_overlay_kit + witness_xss_filename
updated to drop their retired migrate_showme halves.

**Two audit corrections found by executing (reachability is the wrong test for these classes):**
- `viewer/mep_qto_populate.js` KEPT — real node CLI (MEP_5D_QTO.md §2.1–2.3, W-QTO_CACHE_WRITE);
  CLI tools, like docs, are never page-reachable. Reclassified LIVE (dev CLI).
- `modeller/room_templates.json` KEPT — `witness_disc_room_type_weight.js:38` still reads it;
  retire json+witness together only after confirming config/room_templates.yaml is the sole source.
- False-alarm worth recording: erp/tests/test_idempiere_login.js has a var literally named `VIEWER`
  that resolves to `erp/` — read the code, don't trust the grep.

**Size ledger (tracked bytes per git ls-tree; LFS pointers count as pointers in all columns so
deltas are honest):**
| app | before audit (b83c791) | after 2d.html (#750) | after sweep (#751) | Δ total |
|---|---|---|---|---|
| viewer | 51.9 MB | 50.1 MB | **28.4 MB** | **−23.5 MB (−45%)** |
| erp | 40.4 MB | 40.4 MB | 40.3 MB | −0.1 MB |
| modeller | 172.5 MB | 172.5 MB | 172.5 MB | 0 (data-heavy: tracked .db fixtures — separate lane, production finding #5) |

Verification: spec 28 4/4 green; witness_xss_filename 5/5 PASS; audit_sw_precache 106/0 missing;
eslint 0 errors (10 warnings pre-exist on main); spec 21.3/21.4 and poc_overlay_kit's live section
fail IDENTICALLY on untouched main (pre-existing env-fit, verified side-by-side, not regressions).
Still open from the marking table: the 10 STALE CANDIDATEs (user call), kernel_ops.js 3-way
unification (needs its own spec'd session — divergent code, not a mechanical merge), sql-wasm/
qrcode vendor dedupe, modeller tracked-.db relocation, deploy-side (OCI dev bucket) cleanup.
