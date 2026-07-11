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
- **Room Intelligence lane — canonical status: `prompts/ROOM_INTELLIGENCE_SCOREBOARD.md`.**
  User's verdict 2026-07-11: building taxonomy is **good enough, no more perfection work there.**
  15 features shipped/verified. Weakest links unchanged: door-access signal (4/10), classifier
  sample size (5/10, Duplex+SampleHouse only). Open proposals not dispatched (user's call):
  fixture-in-room recognition, graph-joint-inference, external RoomGraph/SAGC-A68 datasets,
  OmniClass/Uniclass mapping. Refresh the scoreboard doc, don't re-derive in prose here.
- ⏸ **PUSH PAUSE in effect (2026-07-11, until lifted)** — commits locally, verifies on localhost,
  does not push/open a PR. See `CLAUDE.md` §⏸ PUSH PAUSE + `prompts/MANAGER.md`.
- **`prompts/SPARSE_WALL_ROOM_INFERENCE.md`** — Phase 0 (data-health guard) DONE+witnessed, merged
  into bim-compiler `fable/meshdb-livewire`. Phase 1 (grid+door+slab+envelope fusion for
  sparse-wall federated buildings, e.g. HHS) scoped as a ready-to-pick-up 4-step follow-up, not
  built — genuinely substantial new engineering, deserves its own session.
- **Building Parts Taxonomy (STAIRWAY/LIFT_SHAFT/PLANT_ROOM) — DONE, both VISION-LOCK sentence-5 UI
  halves merged into bim-ootb `main` (local, 6 commits ahead of `origin/main`, not pushed):** Find
  panel (`d04ddd5`) + Modeller Outliner (`f10c5295`) + disc-walk room-type-aware placement
  (`20ad5c4`, PLB/FP real measured signal — BATHROOM/UTILITY→PLB, FOYER→FP; ELEC/ACMV honestly
  refused, no signal). Full trail incl. the DB-snapshot-divergence landmine (now saved as
  `project_db_snapshot_divergence_landmine.md`, memory) and a real leaf-id click-path bug caught+
  fixed: `prompts/BUILDING_PARTS_TAXONOMY.md`.
- **Guide blocked on ONE thing, named and spec'd, not fire-fought:** `docs/ModellerGuide.md` (29
  real screenshots, otherwise complete) can't get a Building Parts entry yet — user's own live
  review: LOD is fine now, but Modeller glass/window material isn't see-through like the Viewer's.
  Spec: `prompts/MODELLER_RENDER_MATERIAL_PARITY.md` (also folds in a named quirk: Outliner panel's
  collapse control isn't discoverable). Companion spec, Viewer side (evidence gap, not a code
  gap): `prompts/VIEWER_FIND_PANEL_PARTS_VERIFICATION.md` — needs one real driven-from-fresh-load
  screenshot, 3 known dead-ends already named so it isn't re-attempted blind.
- **`prompts/MANAGER.md` hardened this session** — anti-ad-hoc-debugging conduct rule (stop after
  a 2nd failed quick-check, write a spec + dispatch instead of trial-and-erroring in-turn). Read it
  before picking up either spec above.
- **MANAGER housekeeping this session:** 6 stale bim-compiler branches/worktrees verified
  fully-superseded and pruned (nothing lost); one real orphan found+landed (W024
  `component_dimension_range` migration, cherry-picked `c254cb271`). ERP PR #8 (5wk stale)
  independently re-verified and correctly left open — real merge conflict found (Blue Future vs.
  I-D checkpoint), not a courtesy hedge.
- **From the 2026-07-10 marathon, still unmerged, not superseded:** `fix/grid-tilt-guard`,
  `fix/dw-rot-units`, `fable/dwprobe-dedup`, `fix/terminal-oracle-source` (bim-ootb, all verified+
  pushed, no PR). Full detail: `project_disc_walker_grid_guard_marathon_2026-07-10.md` (memory).

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
- **✅ pending merge only:** `SCALE_AND_UX_SWEEP.md` (bim-ootb PR #665), `OFFLINE_GITHUB_RELEASE_BUNDLE.md`
  (`lane/offline-gateway-leak-fix`) — both independently re-verified, just need the human merge click.
- **2026-07-05 arc** (landing Save/Open, grid, Teams E2E, HBA mobile, UBBL recon) — ALL MERGED
  (bim-ootb #654-664), watchdog-verified. Detail: `prompts/archive/FRONTEND_LANE_MASTER.md`.
- Pre-2026-07-05: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-05.md` /
  `_pre_2026-06-14.md`. Viewer S-series/DAGCompiler: MEMORY.md "Project — Shipped".

## OCI Deployment

- Live: `bim-ootb-live` (SYSNOVA landing + viewer + single DBs). Always upload here.
- Single DB per building: `buildings/{Name}_extracted.db` (metadata + geometry + bbox).
- `deploy/sandbox/` stale (last ~S225) — not used for deploy. `deploy/dev/` is canonical.
- Deploy SOP: `deploy/OCI_UPLOAD.md`

## Reference

- Docs site: https://red1oon.github.io/BIMCompiler/
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
