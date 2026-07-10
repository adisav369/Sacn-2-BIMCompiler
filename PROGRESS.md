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
- `prompts/Modeller/DISC_Walker/ROOM_INJECTION_HYBRID.md` 2026-07-10/11 — Tasks 1+5 DONE (habitability classifier,
  Duplex 20 real rooms, bim-ootb `fable/modeller-lod400-livewire`@`2821b8e`, unmerged). Task 6 (SampleHouse
  extraction bug, 3 usable rooms) assigned Sonnet, NOT STARTED. §2's Terminal-43-rooms claim corrected (was
  wrongly "real," is 100% synthetic). Tasks 2-4 NOT STARTED. 4 more bim-ootb branches (grid-tilt-guard,
  dw-rot-units, dwprobe-dedup, terminal-oracle-source) MANAGER-verified+pushed, PRs #722-725 open, unmerged.
- `prompts/Modeller/DISC_Walker/ROOM_WALKER_JS_PORT.md` 2026-07-11 — NEW, all 5 tasks NOT STARTED (JS port of
  `compile_rooms.py`, explicit-trigger "Room Walker" Outliner action). Progress report pending from user.
- ⛔ **GitHub LFS bandwidth quota EXHAUSTED (2026-07-11), resets 2026-08-01.** See `CLAUDE.md` §LFS QUOTA
  EXHAUSTED — any push may hang regardless of LFS content; don't retry blindly, don't create fresh worktrees
  for uncached branches. Cleaned up: duplicate `~/Projects/bim-ootb` clone removed, 25 stale worktrees pruned.
- Two live Viewer UI bugs found, unfixed: Find panel renders above browser top border (root cause diagnosed,
  not fixed — `deploy/dev/navigate_find.js`); mobile pill flyouts don't open (prompt handed off, not started).

### Other open work (lower/no current juggling priority)
- **HBA IoT "wow" batch** (bim-ootb PR #659 shipped item 4b) — items 1/2/0 (CCTV double-click capture, camera-POV
  fly-to ⛔ needs human-declared facing vector, mobile card-stack redesign) not yet built. Full detail + phased
  build order: `prompts/RESUME_HBA_MOBILE_CARD_STACK.md` (bim-ootb) + memory `project_hba_iot_lod400_lane.md`.
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
- **✅ pending merge only:** `prompts/SCALE_AND_UX_SWEEP.md` (bim-ootb PR #665) — Terminal-scale checks + 6 UX/audit
  fixes, independently re-verified. `OFFLINE_GITHUB_RELEASE_BUNDLE.md` (bim-ootb `lane/offline-gateway-leak-fix`,
  `cd36c07`) — cache-leak fix, independently verified live. Both just need the human merge action.
- **2026-07-05 arc: landing Save/Open+multimerge resurrect+versioning, grid green/orange, Save/auto-heal,
  Teams E2E, HBA mobile stack, UBBL+parametric recon** — bim-ootb #654/#656/#657/#658/#660/#661/#662/#664, ALL
  MERGED, watchdog-verified against real diffs (not trust-on-recap — caught 1 false "already fixed" fixture
  claim, corrected). Full detail: `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG` + `prompts/
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
