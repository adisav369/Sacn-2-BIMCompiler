# PROGRESS — Current Development State

> **⛔ Don't run ANY git command on `library/component_library.db`.** No stash, checkout, restore, reset. S63 lost 2400+ products this way.

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
| TE | 33848 | 2/4 | Extraction reconciliation |

**Pipeline:** 11 stages. 77 verbs. 7403 products (ERP.db). 4-DB architecture.
**Tests:** BIMBackOffice 20/20. BonsaiBIMDesigner 408/414. BIMEyes 28 proof classes.

## Active Work — Browser BIM OOTB

**S228 (2026-04-26): Drop Zone Multi-Format Import.** ALL FORMATS WIRED.
  - SRS: `internal/DROP_ZONE_MULTI_FORMAT_SRS.md`
  - S228a DONE: `semantic_enrichment.js` + `scene_to_db.js` (pure layer)
  - S228b DONE: Format router + mesh worker + plumbing
  - S228c DONE: Auto-detect up-axis (Y-up vs Z-up) + auto-scale
  - S228d DONE: DAE (ColladaLoader), GLB/GLTF (GLTFLoader), FBX (FBXLoader+fflate), 3DS (TDSLoader)
  - S229a DONE: Guided Classification Wizard — amber panel, 6-step flow. `deploy/dev/wizard.js`
  - S229b DONE: Browser IFC Export — DB → .ifc download. Pure STEP text builder (no web-ifc dependency). Export triangle on import cards, flyout chooser (IFC/SQLite). Round-trip test: 30 PASS.
    - `deploy/dev/ifc_export_worker.js` (new), `deploy/dev/import.js` (IFC button), `deploy/landing2.html` (export triangle + flyout)
    - Test: `deploy/dev/test/test_ifc_export.html`
  - Playwright: 72/72 PASS (desktop), 53/55 pure-function (2 known pre-existing)
  - Test fixtures: `deploy/dev/test/seaside-villa.obj`, `engel-house.obj`

**S225b (2026-04-25): Rates + Locale.** DONE (dev).
  - `rates.js` single source of truth. 15 locale files (iDempiere _TRL pattern). Prompt: `prompts/S226_localisation.md`

**S222-S224 (2026-04-24): DB Refactor + Diff + VO + Versioned Cards.** DONE (dev).
  - Diff engine, VO Excel (FIDIC Clause 12), versioned IndexedDB v2. Prompt: `prompts/S220_import_tshoot.md` §S222-S224
  - Tests: `s220_test.js` 65 PASS, `s211_test.js` 58 PASS

**S220 (2026-04-24): IFC Browser Import.** DONE (dev).
  - web-ifc WASM, IFC2x3+IFC4 proven at 122K elements. Prompt: `prompts/S220_import_tshoot.md`

## Recent DONE — Viewer & Blender Federation

- **S206:** Cinematic tour (7-phase, wall avoidance). Prompt: `prompts/S206_cinematic_tour.md`
- **S205:** Walk-through engine (action-based tour, stair climb). Prompt: `prompts/S205_walk_through_fly.md`
- **S204:** Mobile site camera (GPS, compass, markup, QR). Spec: `docs/MOBILE_DEPLOY.md`
- **S203:** IndexedDB cache, city mode, per-building DBs. Prompt: `prompts/S203_viewer_ux_fixes.md`
- **S200:** BIM OOTB browser viewer. Spec: `docs/BIM_Designer_Browser.md`
- **S198:** Envelope-first streaming (3-phase). In `direct_stream.py`.
- **S195:** Direct DB streaming (no .blend). Prompt: `prompts/S193_dlod_auto_linker.md` §S195
- **S188-nD:** Template-driven nD engine (4D-8D). Spec: `docs/4D5DAnalysis.md`
- **S188-RTree:** Void filter, transparency, parallel bake. Library: 123,573 meshes.

## Recent DONE — 2D Layout

- **2D_020:** TB-LKTN Phase A — 6 features, 6 issues. Prompt: `prompts/2D_020_tbkltn_phase_a.md`
- **2D_019:** Hardening + Java DxfWriter. Architecture study.
- **2D_018:** Grid bubbles, template-driven layout. Spec: `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md`

## Recent DONE — DAGCompiler & Pipeline

- **S190:** Fleet health-check (21 buildings, 4 ALL GREEN).
- **S104:** IFCtoERP complete — TE 8/8, 48428 elements, 0 critical violations.
- **S100-S152:** Generative MEP, RouteWalker, discipline separation, material extraction. [Git history.]

## What's Next

1. **Gerard's HDP DAE** — tune classification for real HDP node names
2. **S229 wizard live test** — drop OBJ on dev landing, walk through wizard flow
3. **S227 refactor triage** — `prompts/S227_refactor_triage.md` (4 sessions spec'd)
4. **2D Layout Phase B** — missing pages (section, schedule, electrical, ceiling)

## Reference

- Docs site: https://red1oon.github.io/BIMCompiler/
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
