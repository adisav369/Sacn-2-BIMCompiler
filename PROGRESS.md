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
- **`prompts/HBA_UNITCLASS_OUTLINE_AND_CAMERA_POV_FIX.md`** (pointer) → full detail in
  `prompts/RESUME_HR_BIM_ASSET.md` § "▶ 2026-07-06". Status: ✅ DONE 2026-07-06, bim-ootb #668+#671 MERGED.
  Only remaining thread: camera-tile-assumes-own-POV + real in-scene captures, still blocked on a human
  declaring 6 facing vectors (not a coding task).
- **`prompts/WORLD_HISTORY_BROKEN_RECALL.md`** (pointer) → full detail in
  `prompts/RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` § "▶ 2026-07-06". Status: PARTIAL — Part 2 (Viewer
  undo-spawns-dots) ✅ DONE 2026-07-06, bim-ootb #670 MERGED. Part 1 (Modeller World History wiring, 4 steps)
  RE-CONFIRMED still zero-built. Bomb-clear + tap-vs-long-press still not live-tested.
- **`prompts/PILL_DRAWER_REORGANIZATION.md`** — Status: ✅ SUBSTANTIALLY DONE 2026-07-06, bim-ootb #667+#669
  MERGED (4 drawers, Shadow+Ground merge, Alt-Z/X 3-state cycle). Small open item: a "Find box appears on its
  own" bug reported but not yet reproduced (`§FIND_VIS_TRACE` added to catch it next time it happens).
  `§DELETIONS`/`§NEW ICONS` sections written. No open design questions left.

### Other open work (lower/no current juggling priority)
- **HBA IoT "wow" batch (3 items, bim-ootb PR #659 shipped item 4b)** — item 1: sensor-click should double the
  matching CCTV tile + render a real in-scene capture facing the device (feasible, not yet built). Item 2:
  clicking a CAM tile should fly the Viewer camera to assume that camera's POV — an Opus feasibility pass found
  the facing AXIS is really extractable (door bbox thin-dimension) but the SIGN is not (rotation is uniformly
  0 in this extraction, no reliable inside/outside signal) — needs one human-declared `facing` vector per
  camera (⛔ needs a person to actually look at each door in the viewer and pick a side). Item 0: a mobile
  swipeable-card-stack redesign of all HBA panes has a full written spec (`prompts/RESUME_HBA_MOBILE_CARD_STACK.md`
  in bim-ootb, PR #659) — not yet implemented, phased build order included. Memory: `project_hba_iot_lod400_lane.md`.
- **✅ DONE, pending merge:** `prompts/SCALE_AND_UX_SWEEP.md` (bim-ootb PR #665, `lane/watchdog-scale-ux-sweep`) —
  Terminal-scale checks + 6 UX/audit fixes + 2 recon aggregation fixes, ALL 4 follow-up findings also fixed +
  independently re-verified. Only the human merge decision is left.
- **✅ RESOLVED, pending merge:** offline-gateway cache leak (bim-ootb `lane/offline-gateway-leak-fix`,
  commit `cd36c07`) — 3 real fixes (sw.js/streaming.js/city.js), independently verified live. Desktop/mobile
  installer question closed as: native PWA install is sufficient, no Electron/zip build needed. See
  `prompts/OFFLINE_GITHUB_RELEASE_BUNDLE.md` for the full closure record.
- **Held, not yet built (user's own call — prove smallest piece first):** Modeller prefab design dialogue —
  DAG-guided lasso, escalating selection, macro-capture — `prompts/PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`.
- **Spec ready, not built:** `prompts/UBBL_RULES_GATE.md` (room area/height vs. 2 verified By-Law thresholds).
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
- **2026-07-05 arc: landing Save/Open+multimerge resurrect+versioning, grid green/orange, Save/auto-heal,
  Teams E2E, HBA mobile stack, UBBL+parametric recon** — bim-ootb #654/#656/#657/#658/#660/#661/#662/#664, ALL
  MERGED, watchdog-verified against real diffs (not trust-on-recap — caught 1 false "already fixed" fixture
  claim, corrected). Full detail: `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG` + `prompts/
  GRID_PREDRAG_PREVIEW_SAVE_COMPLETEIT.md` (design dialogue) + memory `project_teams_e2e_no_ui_finding.md` /
  `project_ubbl_recon_landmine.md`.
- Pre-2026-07-05 DONE items (13 lines) → `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-05.md`
- Pre-2026-06-14 DONE items (21 lines) → `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-06-14.md`
- Viewer S-series (S188–S286): browser viewer, DLOD, mobile perf, find/nav, multi-format import, cinematic — see MEMORY.md "Project — Shipped"

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
