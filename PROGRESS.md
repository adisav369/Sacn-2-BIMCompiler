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

## OPEN — real work, not yet done (check these before starting a new session)

### 🔀 CURRENTLY JUGGLED (user runs these concurrently across terminals, shuts down/resumes cold — read this
### list FIRST, don't ask the user to restate status, it's kept current here)
- `prompts/RESUME_HR_BIM_ASSET.md` §2026-07-06c — camera-POV done (#674 `77f41b9`). Open: A/B/C bugs + E decision.
- `prompts/RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §2026-07-06 — Viewer Part2 done (#670 `d6bfb80`); Modeller
  port Ph1-2 done (#675 `9ff9a5a`+`6d9f906a`), open: G6, Ph3. World-History Part1 parked.
- `prompts/PILL_DRAWER_REORGANIZATION.md` §2026-07-06 — done (#673 `953d1e4`). Open: first-touch flicker.
- `prompts/OPEN_BUTTON_IFC_BCF_MERGE.md` — not started.
- `prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md` §ROTATION-BOUND 2026-07-09 — Bug B shipped
  (PR #717, unmerged). Bug A (hostBind) + items 1-2 (occupancy() fix, VENT_WINDOW_SHIM 415→498mm) all fixed+witnessed
  (W-OCC-TRUE-MIDPOINT 17/17, 0 regressions). §TE-ARC-DATUM DONE 2026-07-11: bim-compiler merged (`b202eb44b`,
  PR #40), bim-ootb ported+verified+pushed (PR #726, open, not merged — heals live Terminal collapse once landed).
  §LIVEWIRE CLOSED 2026-07-11 (Fable, MANAGER-verified) — W-SCHED-MINE 7/7 + W-DX-WALKBACK-RSGT 14/14, 0 unpushed.
- `prompts/Modeller/DISC_Walker/ROOM_INJECTION_HYBRID.md` — Tasks 1-6 DONE incl. self-heal patch loader
  (bim-ootb `fix/meshdb-selfheal-loader` @ `a1aeab7`, PR open unmerged, W-PATCH-SELFHEAL 43/43); full task
  log + witnesses in the doc. 4 more bim-ootb branches pushed, PRs #722-725 open, unmerged.
- `prompts/Modeller/DISC_Walker/ROOM_WALKER_JS_PORT.md` — ALL 5 TASKS DONE. `ROOM_INJECTION_HYBRID.md`
  §7 (well-formedness: corridor/wall-crossing fix, SUSPECT_* review rows) and §8 (MULTI-RECT: rooms as a
  rectangle set, closes "doesn't fully form room space", room_guid grouping) both DONE + MANAGER-verified
  2026-07-11 (W-ROOM-WALKER-PARITY 6/6, W-ROOM-WELLFORMED 19/19, W-ROOM-FILL 18/18, all re-run independently).
  Self-heal patches at `ROOM015-020` (supersede `ROOM009-014`) on bim-ootb `fix/meshdb-selfheal-loader`
  @ `e7384f4`/`fd7da67`, W-PATCH-SELFHEAL 43/43 verified. Full detail in the spec doc, not here.
  **✅ CLOSED 2026-07-11 (Sonnet, §9) — OCI room-data upload block LIFTED.** `_allRoomVolumes()`/
  `_roomLensOn()` now group `spatial_structure` by `room_guid` and render one shell box per §8
  sub-rect (union = real footprint); ported `hba_lens.js`'s proven grouping shape but fixed a
  latent bug the direct copy would've inherited (`A.dbQuery` never throws on a bad column ref,
  so the try/catch fallback never fired — replaced with a `PRAGMA table_info` probe). Witness
  `witness_room_lens_volbox.js` 8/8: rendered box footprint-area SUM = spatial_structure's own
  rect-union area SUM, diff=0.000m² (real multi-rect data, 33 rooms/43 sub-rects, from running
  the proven `room_walker.js` against real HHS wall/door geometry); existing
  `witness_room_lens_hab.js` reruns 11/11, zero regression. bim-ootb PR
  https://github.com/red1oon/bim-ootb/pull/733 (`fix/room-lens-volume-box` @ `8cb7c49`, pushed).
  Follow-up (not blocking, not done): `_roomSelect()`/`_buildRoomTree()` still aren't
  `room_guid`-aware — likely the ACTUAL source of the original "~6 nearby wall elements" report
  (a different function, `_roomBoundingGuids`, from the one this fix targeted). Full detail
  `ROOM_INJECTION_HYBRID.md` §9 RESULTS.
  **✅ Terminal coordinate-frame mismatch — ROOT-CAUSED + FIXED LOCALLY, 2026-07-11** (own investigation,
  NOT a room-injection bug — full evidence in `prompts/TERMINAL_COORDINATE_FRAME_MISMATCH.md`). Cause:
  bim-compiler's `deploy/buildings/Terminal_extracted.db` was a carve-out of the multi-building
  `sandbox_1M` city demo (`scripts/extract_per_building.py` reading `T0_Terminal_` rows verbatim from
  `build_sandbox_1M.py`'s tile-placement output) stacked on the extractor's own S169 centroid-normalize
  — both uncorrected, giving a constant (545.6m, 51.2m, 14.7m) offset vs raw-IFC ground truth. bim-ootb's
  `Terminal_ARC.db` was already correct (matches ground truth <2cm). Fixed in place with a proven-constant
  SQL offset correction (backed up, integrity-checked, re-verified against ground truth <6mm) — Terminal's
  OCI room-data gate can now move to the separate `ROOM_INJECTION_HYBRID.md` decision (not made here).
  Flagged, not verified: other `CBD_BUILDINGS` sharing the same carve-out path (Hospital/HospitalGarage/
  LTU_AHouse) may carry the same class of bug — unchecked this session. Hospital/SampleCastle/Garage/
  SampleHouse/Clinic already shipped via OCI (fresh, self-consistent walk).
  **HHS room data + Viewer self-heal loader — DONE+MERGED 2026-07-11** (Sonnet, MANAGER-verified, bim-ootb
  PR #732 auto-merged `f60bfb7`, CI green): Room habitability filter (shared `common/room_habitability.js`,
  both apps) + Type-toggle COMPILED-fallthrough fix + a NEW `buildings/patches/*.sql` Viewer-side self-heal
  loader (`viewer/scene.js A._applyPendingPatch`, ported 1:1 from the Modeller's proven `_applyPendingPatch`
  convention — fetches+applies an idempotent SQL patch client-side on every DB open, fails safe, no binary
  push needed). `buildings/patches/HHS_Office_Federated_extracted.db.sql` ships the 14→105-row fix this way,
  per standing policy (patch+loader, never a binary push) — closes the gap bim-compiler
  `ROOM021_HHS_buildings_extracted_carry.sql` (`cf3ea28ea`) named as missing. Witness 11/11 (independent
  node ground-truth, not hardcoded). Full detail:
  `VIEWER_FIND_PANEL_ROOM_ACCURACY.md` §5.
  **W5 precision lane (Fable, same `fable/meshdb-livewire` branch) DONE through §WALL-SLOT:** per-room Z
  offsets, wall-light sconce fix, which-wall slot seam (honestly 0 slots on Duplex — mirror-unit geometry
  has no absolute wall-side signal, proven not guessed). All witnessed (W-SCHED-MINE 7/7, W-DX-WALKBACK-RSGT
  14/14 throughout). Next named follow-up, not yet assigned: mirror-invariant wall-anchor mining (relative
  to ARC adjacency, e.g. "the wall the door is on," not absolute XMIN/XMAX).
- `git push`/worktree-checkout may hang (LFS pre-push probe against the capped quota, resets 2026-08-01) —
  a pure git-ops caution, unrelated to DB policy (DB binaries are never pushed regardless). See `CLAUDE.md`
  §DB CHANGES = MIGRATION SCRIPT + SELF-HEAL LOADER, ALWAYS.
- Find panel visible-at-onset + rendered-above-top-border — FIXED 2026-07-11 (Fable, MANAGER-verified):
  `display:none` default + top/transform fix in `#find-panel` CSS (`viewer/navigate_find.js`), W-FIND-PANEL-VIS
  28/28. bim-ootb PR #728 @ `4de186d`, OPEN/mergeable, **not merged (user's call)**.
- §8E-3 MEP routed-network render — DONE+MERGED 2026-07-11 (Sonnet, MANAGER-verified, bim-ootb PR #731
  auto-merged `9abb845`, CI green): render machinery already shipped (PR #555/#686) — real gaps were
  `__dwPixelProbe` missing routed-tube tag + no dedicated witness. `witness_mep_route_render.js` 12/12
  (Terminal PLB 4315+ACMV 1002=5317, Duplex PLB 358, readPixels-proven, matches W-WALKBACK-MEP oracle).
  Found: shipped Terminal resident now ARC-only (0 MEP data) — substrate regression, out of scope, named
  in `RESUME_GRAPH_MODELLER_INTEGRATION.md` §8E-3.

### Other open work (lower/no current juggling priority)
- **HBA IoT "wow" batch** (bim-ootb PR #659 shipped item 4b) — items 1/2/0 (CCTV double-click capture, camera-POV
  fly-to ⛔ needs human-declared facing vector, mobile card-stack redesign) not yet built. Full detail + phased
  build order: `prompts/RESUME_HBA_MOBILE_CARD_STACK.md` (bim-ootb) + memory `project_hba_iot_lod400_lane.md`.
- **Held, not yet built (user's own call — prove smallest piece first):** Modeller prefab design dialogue —
  DAG-guided lasso, escalating selection, macro-capture — `prompts/PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`.
- **UBBL room-size demo gate — SHIPPED 2026-07-11** (Fable, MANAGER-verified): `SdgGate.ubblRoomSizeDemo()`
  in bim-ootb `modeller/sdg_gate.js`, 5th gate case, only the 2 verified By-Law 42 thresholds (area≥6.5m²,
  headroom≥2.5m), every row labeled "not a compliance verdict" per spec. Witness 9/9 (8 of Duplex's 21
  real rooms flagged, e.g. A104 1.456×2.171=3.161m², witness queries live DB not hardcoded) + regression
  `witness_sdg_gate.js` 11/11. bim-ootb PR #729, branch `feat/ubbl-room-size-demo` @ `4433dad`,
  OPEN/mergeable, **not merged (user's call)**. Full detail: `prompts/UBBL_RULES_GATE.md`.
- **Kernel op-log T4+T5** (unify 3 kernel copies) — BROWSER-GATED, needs W-ONE-KERNEL building-load smoke.
  Deferred: `commitGroup` id-race retry. Spec: `prompts/KERNEL_HARDENING_BATCH1_SPEC.md §STATUS`.
- **Modeller onboarding** — Hospital/Clinic/LTU/HHS_Office as Modeller residents + migrate SH/DX/SC into the
  canonical `IFC/` folder. Spec: `prompts/ARC_GEO_FETCH_SPEC.md §NEXT` item 2.
- ⛔ BLOCKED (user call): are `migration/DV_*_rules.sql` mined-rule files EXEMPT from append-only, or enforce?
  Full triage: `prompts/CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE` (§1 refactors, §3 shallow specs also open).
- Modeller unassigned polish: item 9 PBR textures; SSAO (needs EffectComposer vendored).
- ARC occupancy density drift (99%→92-95%, `W-DW-DENSITY-TE` D3) surfaced by PR #638 below — real, unexplained,
  low-priority, not urgent. Memory: `project_arc_meshreadpixels_branch_unmerged.md`.

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- **✅ pending merge only:** `prompts/SCALE_AND_UX_SWEEP.md` (bim-ootb PR #665) — Terminal-scale checks + 6 UX/audit
  fixes, independently re-verified. `OFFLINE_GITHUB_RELEASE_BUNDLE.md` (bim-ootb `lane/offline-gateway-leak-fix`,
  `cd36c07`) — cache-leak fix, independently verified live. Both just need the human merge action.
- **2026-07-05 arc: landing Save/Open+multimerge resurrect+versioning, grid green/orange, Save/auto-heal,
  Teams E2E, HBA mobile stack, UBBL+parametric recon** — bim-ootb #654/#656/#657/#658/#660/#661/#662/#664, ALL
  MERGED, watchdog-verified against real diffs (not trust-on-recap — caught 1 false "already fixed" fixture
  claim, corrected). Full detail: `prompts/archive/FRONTEND_LANE_MASTER.md §NEW BACKLOG` (archived 2026-07-11) + `prompts/
  GRID_PREDRAG_PREVIEW_SAVE_COMPLETEIT.md` (design dialogue) + memory `project_teams_e2e_no_ui_finding.md` /
  `project_ubbl_recon_landmine.md`.
- Pre-2026-07-05 DONE (13 lines) → `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-05.md`; pre-2026-06-14 (21
  lines) → `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-06-14.md`. Viewer S-series/2D Layout/DAGCompiler
  (S104-S286): see MEMORY.md "Project — Shipped".

## OCI Deployment

- Live: `bim-ootb-live` (SYSNOVA landing + viewer + single DBs). Always upload here.
- Single DB per building: `buildings/{Name}_extracted.db` (metadata + geometry + bbox).
- `deploy/sandbox/` stale (last ~S225) — not used for deploy. `deploy/dev/` is canonical.
- Deploy SOP: `deploy/OCI_UPLOAD.md`

## Reference

- Docs site: https://red1oon.github.io/BIMCompiler/
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
