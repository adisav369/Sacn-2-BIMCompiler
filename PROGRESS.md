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
- `prompts/RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §2026-07-06 — Viewer Pt2 + Modeller Ph1-2 done, open: G6, Ph3, Pt1 parked.
- `prompts/PILL_DRAWER_REORGANIZATION.md` §2026-07-06 — done (#673 `953d1e4`). Open: first-touch flicker.
- `prompts/OPEN_BUTTON_IFC_BCF_MERGE.md` — not started.
- **DiscWalk containment (Bug A) — RESOLVED+VERIFIED 2026-07-12 (5-axis proof, DX+SC; Terminal honestly
  unmeasurable, data gap). Bug B merged. D3/D4/D4b fixed. Room-taxonomy Lane A (R-MERGE+R-REJECT) also
  SHIPPED both mirrors, 6/6 parity verified; R-DOOR-SCORE tried+cleanly reverted (broke a real witness).
  Read `[[project_discwalk_containment_utmost]]` memory for detail — do NOT re-attempt the `_hostAxis`
  cardinal-swap patch (disproven) or R-DOOR-SCORE's hallwayness formula (also disproven) unbuilt.**
  **NEW 2026-07-13, NOT closed — read `prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md`
  §STOREY-UNKNOWN first, this is the source of truth, not the memory link above.** One-liner: `substrate()`
  walked the data-artifact storey value `'Unknown'` as a real floor (fixed, SampleCastle outliers 23→11,
  Duplex unaffected); a `hostBind()` Z-fallback to close the rest was tried+reverted (made dak-storey
  fixtures worse). `walk-fixtures.png` guide swap deliberately not shipped — reverted `da27f8598`.
  §TE-ARC-DATUM DONE+MERGED both repos 2026-07-11 (bim-compiler `b202eb44b` PR #40; bim-ootb PR #726). Remaining:
  `prompts/DISC_WALKER_BRANCH_CLOSEOUT.md` — 3 stale PRs (#722/#724/#725) need re-verify + the undiagnosed
  guide-screenshot camera bug. §LIVEWIRE CLOSED 2026-07-11 — W-SCHED-MINE 7/7 + W-DX-WALKBACK-RSGT 14/14, 0 unpushed.
- ⏸ **PUSH PAUSE in effect (2026-07-11, until lifted)** — commits locally, verifies on localhost,
  does not push/open a PR. See `CLAUDE.md` §⏸ PUSH PAUSE + `prompts/MANAGER.md`.
- **`prompts/SPARSE_WALL_ROOM_INFERENCE.md`** — Phase 0 (data-health guard) DONE+witnessed, merged
  into bim-compiler `fable/meshdb-livewire`. Phase 1 (grid+door+slab+envelope fusion for
  sparse-wall federated buildings, e.g. HHS) scoped as a ready-to-pick-up 4-step follow-up, not
  built — genuinely substantial new engineering, deserves its own session.
- **✅ Modeller glass parity + guide-quality pass — DONE, multi-round, 2026-07-13.**
  `MODELLER_RENDER_MATERIAL_PARITY.md` shipped 2026-07-11 (bim-ootb PR #735); `docs/ModellerGuide.md` caught
  up across several live-review rounds same day: `gizmo`/`rotate-yaw`/`scale-stretched`/`delete-gone`/
  `samplecastle-arc-open` recaptured (opaque→real glass), `seedtrunk-entry.png` fixed (was showing stale
  pre-fix 267-fixture low-LOD geometry behind the dialog), `walk-fixtures.png` X-ray-reveal added then
  re-cropped for legibility, `seedtrunk-trunk.png` recoloured magenta (was low-contrast gold-on-gold).
  `docs/ModellerKernelFold.md` extended with the graph-cascade conformity disclosure (verified live against
  `sdg_gate.js`, not stale memory) + a Bonsai/FreeCAD comparison, folded into the existing Feature
  Comparison section; gap-timeline moved to `prompts/BONSAI_KERNEL_RESEARCH.md §GAP-TO-COMPETITIVE` Tier 2b.
  **New, ready to assign:** `prompts/Modeller/DISC_Walker/XRAY_FIXTURE_CLASSIFICATION_FIX.md` —
  `xrayReveal()` misclassifies SampleCastle's structural walls as glowing fixtures (root-caused to exact
  file:line, POC-gated, SampleCastle set as primary test building per user call). `GUIDE_VISUAL_QUALITY.md`'s
  separate Duplex→SampleCastle visual-richness lane retired same session (dispatch worktree never started,
  zero unique commits — see that doc's §RETIRED). `prompts/MANAGER.md`'s GUIDE STALENESS METHOD hardened
  with 2 lessons from this pass: test novel captures before shipping, expect a second review round.
- **From the 2026-07-10 marathon, still unmerged, not superseded:** `fix/grid-tilt-guard`,
  `fix/dw-rot-units`, `fable/dwprobe-dedup`, `fix/terminal-oracle-source` (bim-ootb, all verified+
  pushed, no PR). Full detail: `project_disc_walker_grid_guard_marathon_2026-07-10.md` (memory).
- **`prompts/FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md` §HALLWAY-BACKBONE (2026-07-14/15) — 4 corridor
  fixes + `RoomGraph.fullConnectivity()` shipped (bim-ootb PR #792, #793, both merged to main).
  §TOP PRIORITY for next session: close the disconnected-island gaps `fullConnectivity()` now
  quantifies (Clinic 71.8% connected, HHS 49.4%) — user called this a show-stopper blocking
  dependent value. Read that file's §TOP PRIORITY section first, don't re-derive.**

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
- **Room Intelligence / Functional Spaces lane, ACTIVE — read `prompts/FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md`
  first, it supersedes `FUNCTIONAL_SPACES_ENSEMBLE.md` and `ROOM_INTELLIGENCE_SCOREBOARD.md` as of
  2026-07-14.** Latest: bim-ootb PR #780/#781 shipped+deployed (sw.js `/lib/` cache trap +
  needle-inject trusting an unproven-empty patch — both were silently blocking every room fix from
  reaching a browser). `§HALLWAY-BACKBONE` section = a verified-but-uncommitted door+wall+crossing
  algorithm for hallway/corridor detection (Clinic-tested), superseding the old shape-only
  `hallwayness()` formula; one open gap (stair-termination) named plainly with its exact next step.
- **✅ Building Parts Taxonomy** (STAIRWAY/LIFT_SHAFT/PLANT_ROOM, Find panel + Outliner + disc-walk
  room-type-aware placement) — merged bim-ootb `main` local (not pushed). Detail: `prompts/BUILDING_PARTS_TAXONOMY.md`.
- **✅ MANAGER housekeeping 2026-07-11:** 6 stale branches/worktrees pruned, 1 orphan landed (`c254cb271`),
  ERP PR #8 re-verified (real conflict, correctly left open).
- **✅ MANAGER housekeeping 2026-07-13:** audited all 32 bim-ootb `/tmp/wt-*` worktrees — 1 safe to prune
  (`wt-terminal-verify`, detached HEAD, clean, commit already an `origin/main` ancestor) removed; the other
  31 all carry real unpushed commits or uncommitted changes, correctly left alone (not blindly swept for a
  tidy count). Own session's throwaway capture worktrees (5 total) cleaned as each was finished with.
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
