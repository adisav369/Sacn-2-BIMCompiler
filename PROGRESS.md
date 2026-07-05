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
- **HBA IoT "wow" batch (3 items, bim-ootb PR #659 shipped item 4b)** — item 1: sensor-click should double the
  matching CCTV tile + render a real in-scene capture facing the device (feasible, not yet built). Item 2:
  clicking a CAM tile should fly the Viewer camera to assume that camera's POV — an Opus feasibility pass found
  the facing AXIS is really extractable (door bbox thin-dimension) but the SIGN is not (rotation is uniformly
  0 in this extraction, no reliable inside/outside signal) — needs one human-declared `facing` vector per
  camera (⛔ needs a person to actually look at each door in the viewer and pick a side). Item 0: a mobile
  swipeable-card-stack redesign of all HBA panes has a full written spec (`prompts/RESUME_HBA_MOBILE_CARD_STACK.md`
  in bim-ootb, PR #659) — not yet implemented, phased build order included. Memory: `project_hba_iot_lod400_lane.md`.
- **Modeller Spatial Dependency Graph, Phase 3 (backprop)** — accept/ignore UI for ORANGE suggestions is the
  next unbuilt piece (a genuine UX design call, not mine to invent). Spec: `prompts/SPATIAL_DEPENDENCY_GRAPH.md`
  §BUILD ORDER phase 3, `prompts/RESUME_SESSION_2026-07-04_GATE_BACKPROP.md`.
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
- **HBA IoT/CCTV lane §BUILD ORDER + §P10c + icons/connector/docs — bim-ootb PR #652/#653/#655** (2026-07-05):
  7 sensors (incl. Motion/PIR, each with an icon) + 6 cameras bound to real HHS elements, real `M_Product`/
  `C_Order`/`C_UOM` persistence (idempotent), wider "not too near" zoom, USD/RM costing, per-sensor siren tones
  (mute-off-by-default), and a `camerasNearDevice` storey-connector (click a sensor -> rings the same-floor
  CCTV tile). `docs/HRBIMAssetGuide.md` updated (7-sensor table + icons + a "for developers" API section,
  screenshots recaptured) and pushed to master — not yet gh-pages-deployed. `witness_p10b.js` 47/47, full HBA
  suite zero regression, live CDP smoke in real Chrome. Memory: `project_hba_iot_lod400_lane.md`.
- **Modeller STR-into-ARC + canopy render + readPixels "EYES" harness — bim-ootb PR #638 MERGED** (2026-07-04
  MYT): ported the non-redundant half of a stranded branch — STR skeleton + canopy render into the laid ARC,
  plus a real pixel-level `readPixels` harness (verifies rendered geometry visually, not just by data
  assertion). The redundant/regressive mesh-path half was correctly discarded, not merged (confirmed
  byte-identical to main). W-STR-INTO-ARC 11/11, W-STR-CANOPY 8/8 + 3 more, all match pre-port counts.
  Memory: `project_arc_meshreadpixels_branch_unmerged.md`.
- Modeller Conformity Gate + SDG backprop slice 1, round-2 hardening — bim-ootb #644/#645/#646/#647/#648/#649/#650
  all merged (2026-07-04): clear-state-leak round 1+2, door-crush RED, abuts-realign ORANGE (first backprop
  slice), Conformity Gate user-guide doc, cross_edges.js real-per-element-AABB fix. Memory: `project_arc_editable_substrate.md`.
- Modeller IFC-direct-open + Outliner unification — bim-ootb #642, `witness_e2e_walk_ifcopen.js` 18/18,
  `witness_e2e_gridstretch_multi.js` 21/21 (2026-07-04). Detail: `prompts/ARC_GEO_FETCH_SPEC.md §3D` + memory
  `project_modeller_arc_fetch_redesign.md`/`project_modeller_competitive_polish.md`.
- Fable5 follow-up + watchdog session — bim-ootb #639/#640/#641/#643/#645, kernel T1/T2/T3/T6/T7 (2026-07-04).
  Reverse Zoom Across loop (HBA ERP→Viewer nav) done same day. Memory: `project_fable5_wrapup_2026-07-03.md` +
  `project_hba_construction_window_person_linkthrough.md`.
- Codebase quality audit §2/§5 — bc #20, bim-ootb #618 (2026-07-03).
- Fable5 wrap-up, all 6 items + pills consolidation — bc #37, ootb #633/#634/#635/#636/#637, bc #38 (2026-07-04; `prompts/archive/FABLE5_WRAPUP_2026-07-03.md` + `prompts/archive/PILLS_CONSOLIDATION_REVIEW_2026-07-03.md`)
- Unified docs pass leftovers — bc #35/#36, HBA BOM shot + anchors + branch dedupe (2026-07-03; `prompts/RESUME_UNIFIED_DOCS_PASS_2026-07-03.md`)
- HBA Stage 3 + C_Attendance retirement — bim-ootb PR #632, suite 40/40, §HBA_GOVERN live smoke (2026-07-03; `prompts/RESUME_HBA_ERP_STAGE3.md`)
- Modeller §NEEDS-DESIGN batch + item 10 T/S arms — bim-ootb #625/#627/#631, 30/30 + 8/8; spec bim-ootb `prompts/RESUME_MODELLER_POLISH3.md` (2026-07-03)
- HBA lane/hr-overlay sync+PR handoff — bim-ootb PR #628 `e42a96b` + closeout #629, 39/39 (2026-07-03; memory [[project_hba_erp_governed_display]])
- Ninja Create two-way engine + live export — `prompts/NINJA_MODE_PILL.md # DONE`, W-NINJA-{EXTRACT,CALLOUT,EXPORT,EXPORT-LIVE} + W-ASSET-STATUS (bim-ootb PR #301/#309, sw v673/v681, 2026-06-14)
- Reflexive AD self-edit — W-AD-{OPLOG-DISTRIB,SELFEDIT,SELFEDIT-LIVE} (bim-ootb PR #312 sw v683, 2026-06-14)
- Odoo red-band fold-gap re-audit — W-ODOO-QWEB 41/41 to-the-cent; server actions honestly deferred; migrate_status_panel live (2026-06-14)
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
