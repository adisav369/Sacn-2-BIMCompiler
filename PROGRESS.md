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
- **Room Intelligence lane — canonical status: `prompts/ROOM_INTELLIGENCE_SCOREBOARD.md`
  (2026-07-11).** 13 features shipped/verified in one session (scored 0-10 + WORKS/GAP each), 8
  buildings' room coverage measured fresh. 6 PRs merged (bim-ootb #728/#731/#732/#733, bim-ootb
  #729 UBBL gate, plus room-habitability/Room-Lens/MEP-render/Find-panel), 1 open (bim-compiler #41
  Terminal coordinate fix), 7 threads committed locally under the push pause (OBB clash-gate,
  room-type classifier, door-access signal, tier+BOM wiring, DISC-walk room-type-aware, corridor
  pathway routing, prior-art analysis). Weakest links named plainly: door-access signal (4/10, net
  regression if defaulted on), classifier sample size (5/10, real ground truth = Duplex+SampleHouse
  only). Open proposals (not dispatched, priority is the user's call): scale-tiered templates
  (HHS's corridors are real but invisible to the residential-fit classifier, confirmed), fixture-
  in-room recognition (`IfcFurnishingElement` data already extracted, unused), graph-joint-inference,
  external RoomGraph/SAGC-A68 datasets, OmniClass/Uniclass naming-convention mapping. Don't re-derive
  any of this in prose here — refresh the scoreboard doc instead, it IS the current state.
- ⏸ **PUSH PAUSE in effect (2026-07-11, until lifted)** — new work commits locally, verifies on
  localhost, does not push/open a PR. See `CLAUDE.md` §⏸ PUSH PAUSE + `prompts/MANAGER.md`.
- **`prompts/SPARSE_WALL_ROOM_INFERENCE.md` (2026-07-11, parallel thread, MANAGER-assigned) —
  dispatched, not yet landed.** Two phases: (1) data-health guard for sparse-wall federated models
  (HHS: 1.06 walls/room vs Hospital 7.16/Clinic 5.48 — flood-fill fails universally, falls back to
  weakest INTERNAL_DOORPART method), corrected from an initial round-numbers version to real
  derived thresholds; (2) harder signal-fusion room inference (grid+door+slab+envelope) to recover
  room structure when walls alone aren't enough — the general "most real-world IFC" problem, not
  HHS-specific. Quiet until it lands — do not duplicate, do not chase.
- **Building Parts Taxonomy: BOTH VISION-LOCK sentence-5 UI halves landed 2026-07-11** — Find panel
  (bim-ootb `d04ddd5`) + Modeller Outliner (bim-ootb `f10c5295`), both MANAGER-verified
  independently against real diffs, both local-only (push pause). See
  `prompts/BUILDING_PARTS_TAXONOMY.md` for the full trail incl. a 3-way Duplex-count DB-divergence
  finding and a real leaf-id click-path bug the Outliner task caught and fixed.
- **MANAGER housekeeping pass, 2026-07-11 (this session):** 6 stale local branches + their worktrees
  (fable/g1-count-independent-oracle, fable/meshdb-device-consolidation, fable/terminal-no-spaces,
  merge-staging-docs, fix/te-arc-datum, lane/q1-component-dimension-range) verified fully-superseded-
  by-origin/master (content already landed via other paths/squash-merges) or genuinely stale, then
  pruned — nothing lost, each checked commit-by-commit first. One real exception found and landed:
  `lane/q1-component-dimension-range`'s W024 (component_dimension_range aggregate migration, §Q1_AGG
  witness re-confirmed match=True) was never merged anywhere — cherry-picked onto
  `fable/meshdb-livewire` (`c254cb271`). Also committed 2 pre-existing uncommitted regen artifacts
  (`build/duplex_rules.db` rule_placement rows, `deploy/dev/navigate_find.js` find-panel position-
  fixed fallback) that were sitting dirty from a prior session. `library/component_library.db` stays
  intentionally uncommitted (Sacred Files gate blocks direct binary commit; regenerates from the SQL
  migration). ERP PR #8 (5 weeks stale, `feat/erp-write-path-ik-ij`) dispatched for independent
  re-verify+merge-decide — not yet reported back, check before assuming still open.
- **From the 2026-07-10 marathon (master), still real, not superseded by the above:** 4 more branches
  verified+pushed, no PR yet — grid rotation-guard X/Y-tilt (`fix/grid-tilt-guard`), rot-units
  radians/degrees bug (`fix/dw-rot-units`), `__dwPixelProbe` dedup+harness fixes (`fable/dwprobe-dedup`),
  Terminal MEP-oracle stale-path fix (`fix/terminal-oracle-source`). **Guide screenshots FAILED on
  direct review** (washed-out framing / broken camera-inside-mesh capture) — confirmed NO, not
  unverified, status unknown since. SampleCastle rooms CLOSED (disc_walker needs none, works via
  `duplex_rules.db`+`substrate()`) — the room-injection feature above is separate, don't conflate.
  Open: Terminal PLB walk graded (T6/T7), old op-log rot-units data unmigrated, `cat[0]` legacy
  fallback, W5 RSS-exact ratchet. Full detail: `project_disc_walker_grid_guard_marathon_2026-07-10.md`
  (memory).

### Other open work (lower/no current juggling priority)
- **HBA IoT "wow" batch** (bim-ootb PR #659 shipped item 4b) — items 1/2/0 (CCTV double-click capture, camera-POV
  fly-to ⛔ needs human-declared facing vector, mobile card-stack redesign) not yet built. Full detail + phased
  build order: `prompts/RESUME_HBA_MOBILE_CARD_STACK.md` (bim-ootb) + memory `project_hba_iot_lod400_lane.md`.
- **Held, not yet built (user's own call — prove smallest piece first):** Modeller prefab design dialogue —
  DAG-guided lasso, escalating selection, macro-capture — `prompts/PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`.
- UBBL room-size demo gate — SHIPPED+MERGED 2026-07-11, see `ROOM_INTELLIGENCE_SCOREBOARD.md` row 3.
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
