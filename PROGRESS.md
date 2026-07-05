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
  `prompts/RESUME_HR_BIM_ASSET.md` § "▶ 2026-07-06b" + NEW "▶ 2026-07-06c". Status: camera-POV-assume-flight
  ✅ BUILT+MERGED (bim-ootb PR #674). Live-testing on top of it surfaced 5 NEW items, researched not yet built:
  (A) UnitClass room-outline doesn't shine through occluding geometry (plain depth-tested LineSegments, unlike
  the OutlinePass-based pick highlight); (B) all 6 HBA side panels share the identical fixed position and fully
  cover each other when 2+ are open (confirmed by grep, separate codepath from PR #669's panel-spread fix);
  (C) IoT sensor bars still read as "too similar" — every sensor shares the same sine phase, only
  baseline/amplitude differ, so all 7 rise/fall in lockstep despite #671's jitter; (D) cam snapshots being
  stub photos is explicitly OK per user, no action; (E) HBA device LOD mesh was never built by design (tint-
  on-existing-element, not a standing device mesh) — real geometry exists unparsed in `IFC/LOD/*.ifc` if ever
  wanted. Real in-scene CCTV captures (old Item 1) still not built either.
- **`prompts/WORLD_HISTORY_BROKEN_RECALL.md`** (pointer) → full detail in
  `prompts/RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` § "▶ 2026-07-06". Status: PARTIAL — Part 2 (Viewer
  undo-spawns-dots) ✅ DONE 2026-07-06, bim-ootb #670 MERGED. Part 1 (Modeller World History wiring, 4 steps)
  RE-CONFIRMED still zero-built (user confirmed 2026-07-06: only testing Viewer, not Modeller — Part 1
  stays parked, not urgent). The "Z" page-timeline icon relocation (out of the W-pill's long-press-only
  drawer into a one-tap row, bomb stays hidden) ✅ DONE same session, bim-ootb PR #673 commit `d11263c` —
  see `prompts/PILL_DRAWER_REORGANIZATION.md`. User explicitly told that session to SKIP the "is the
  timeline actually being populated by real browsing" question this round — still genuinely open, not
  chased. Bomb-clear + tap-vs-long-press dual behavior still not live-tested either.
- **`prompts/PILL_DRAWER_REORGANIZATION.md`** — Status: PR #673 **MERGED** (`953d1e4`, all 5 items — master
  de-highlight, Tab+Space, Shadow+Ground CSS bug, Z-history row, Alt-Z/X shake-out — independently
  re-verified by watchdog before merge). Watchdog also found+fixed a real CI failure the PR introduced
  (`ICONS` undefined in `scene.js`, allowlisted in `eslint.globals.json`). **NEW, not yet built:** pill rail
  flickers off-then-on on the FIRST touch — user pasted a real console log showing 2 `§PILL open=` flips
  within ~2 render-loop cycles; traced `#mobile-trigger`'s binding (no duplicate listener found, that
  hypothesis ruled out) but the double-flip itself is unexplained — needs a live single-tap repro with a
  `console.trace()` in `_toggle()` to settle it. Full detail: `§2026-07-06 — NEW: pill rail flickers`.
- **`prompts/OPEN_BUTTON_IFC_BCF_MERGE.md`** (NEW file, 2026-07-06) — user ask, previously discussed verbally
  in another session but never written down (searched both repos' prompts/, found nothing): move the
  landing-page's Drop-IFC-and-merge gesture onto the Viewer's Open button itself; Save As should support
  IFC/BCF output, not just native `.db`. Flagged extensive/separate by the user. Cites Modeller's own
  `EXPORT_MENU_NATIVE_DB.md` (PR #633) as directly relevant prior art (same DB/IFC/BCF chooser-menu shape,
  different surface). 3 open design questions logged, not yet resolved with the user — not started.

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
