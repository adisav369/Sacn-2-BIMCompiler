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
| TE | 48428 | 8/10 | C8 mesh diversity (16 types), GEO no pairs (federated). 24 storeys, 50 BOMs, 5568 lines |

**Pipeline:** 11 stages. 77 verbs. 7403 products (ERP.db). 4-DB architecture.
**Tests:** BIMBackOffice 20/20. BonsaiBIMDesigner 408/414. BIMEyes 28 proof classes.

## Active Work — Browser BIM OOTB

**S228 (2026-04-26): Drop Zone Multi-Format Import.** ALL FORMATS WIRED.
  - SRS: `internal/DROP_ZONE_MULTI_FORMAT_SRS.md`
  - S228a DONE: `semantic_enrichment.js` + `scene_to_db.js` (pure layer)
  - S228b DONE: Format router + mesh worker + plumbing
  - S228c DONE: Auto-detect up-axis (Y-up vs Z-up) + auto-scale
  - S228d DONE: DAE (ColladaLoader), GLB/GLTF (GLTFLoader), FBX (FBXLoader+fflate), 3DS (TDSLoader)
  - S229a DONE: Guided Classification Wizard — amber panel, 4-step flow. `deploy/dev/wizard.js`
  - S229b DONE: Browser IFC Export — DB → .ifc download. Pure STEP text builder (no web-ifc dependency).
  - S230b DONE: Wizard UX — 0-based storey elevations, raycaster visibility check, storey walkthrough (Walk button isolates floors). 97/97 PASS.
    - Analysis: `reference/residential/EngelHouseAnalysis.md`, `reference/residential/PlaywrightAnalysis.md`
  - S231 DONE: TE BOM storey fix (YAML 7→29 keys, 48428 el, 8/10 gates) + InstancedMesh perf (85% fewer draw calls). Prompt: `prompts/S231_session_done.md`
  - S232 DONE: Mobile merge (95% draw call reduction), InstancedMesh filter/pick, 5D Excel fix. Deployed to OCI dev.
    - streaming.js: mobile merge by storey|disc|rgba (Clinic 16K→729 draws). Desktop unchanged.
    - panels.js: storey/disc filter on InstancedMesh via zero-scale matrix.
    - picking.js: InstancedMesh pick (instanceId→guid), merged pick (group info).
    - boq_charts.html: `CUR_SEC_RATE` typo→`CUR_RATE`, try/catch + saveAs for reliable download.
    - Symlinks broken: streaming.js, picking.js now independent dev copies.
  - S236 DONE: 2D Plans browser DXF viewer — toolbar icon, `2d.html`, Canvas2D renderer, BIMSRC xdata (93/94 survive), layer toggle, pan/zoom, click-to-info, drag-drop. Deployed to OCI dev.
    - Prompt: `prompts/2D_021_browser_dxf_viewer.md`. SH+DX only. Variant DB→diff pipeline spec'd.
  - Playwright: 108/108 PASS (desktop), 18 specs. 5D Excel test (6.4) no longer skipped.
  - Test fixtures: `deploy/dev/test/seaside-villa.obj`, `engel-house.obj`
  - S233a DONE: Playwright hardening — @fast/@slow/@bench tags, waitForTimeout purge, 4 bugs fixed (flip camera, IFC stale DB, double-flip, storey rename UI). Audit: 106 tests, 236 expects, ratio 2.23. @fast=79s, full=3.2min, 0 pre-existing failures.
  - S233b DEPLOYED: Find & Navigate — indoor wayfinding. Spec: `prompts/S233_find_and_navigate.md`
    - 26/26 Playwright PASS (desktop). navigate.js lazy-loaded on demand (saves 78KB on mobile).
    - **S239 fix:** sw.js CACHE_VERSION='v239' — network-first for JS/HTML. Stale cache issue resolved.
    - **Remaining:** mobile field test (touch, mic, deviceOrientation). Occupancy grid not yet populated.
  - S237b FIX: 4D5D locale_loader.js + locales/ uploaded to all 3 buckets (dev/live/full).
  - S234 KIV: Wizard Toggle UX — code written, not field-proven. Spec: `prompts/S234_wizard_toggle_ux.md`
    - Bug 6b (storey order inverted after flip) + Bug 6c (fixed 3m bands) — OPEN.
    - Z-gap clustering spec'd but not implemented. flip sign propagation is entangled.
    - **Needs:** §WIZARD_STOREY_SORT log line + field test.
  - S239 DONE: Deep refactor — `full` branch. helpers.js, 18 traverse→0, 31 db.exec→dbQuery,
    4 SQL injections fixed, lazy-load navigate/wizard, sw.js versioning, minify script (44% reduction).
    Testing hierarchy: §-log primary, Playwright secondary. See `docs/TestArchitecture.md`.
  - **S238n DONE (2026-04-30): Annotation density config + guide + docs deploy.** 6 tests PASS.
    - `getAnnoConfig()`: localStorage → OCI remote → built-in defaults, no install needed
    - Density control: `furnDetailRooms`, `maxFurnPerRoom`, `seatingThreshold`, hall aggregate bbox, tag caps
    - `docs/2D_HTML_GUIDE.md`: user guide + §9 developer code structure section
    - mkdocs.yml: 2D Plans Viewer added to Guides nav
    - **Fix (2026-04-30):** `_computedScale` hoisted before GridDims block — was computed only inside TitleBlock, so grid crowding filter received stale `undefined`. Now single computation with `isFinite(bx0)` guard + `50` px/m fallback. Docs deployed to gh-pages.
  - **S238m DONE (2026-04-30): §25.6 remaining annotation features.** All whitebox-tested (12 key tests pass).
    - A-WALL-PATT hatch fills: solid fill behind wall outlines (ratio vs DXF = 0.94, 1:1 hatch per contour)
    - A-FURN furniture outlines: 4-point rectangle from element name dimensions (14/14 SH furniture)
    - Room labels: BEDROOM/DINING ROOM/LIVING ROOM inferred from IFC element names (3/3 SH rooms)
    - Door/window tags: D1-D3, W1-W4 sequential from DB positions (3 D-tags, 4 W-tags = exact DB count)
    - A-ANNO-SECT section cut marker: auto A-A line at building mid-Y + circles + labels
    - section_cut.js: `center:{x,y,z}` added to all result objects (feeds furniture/tag positions)
    - Tests 14.37–14.42 added (6 new whitebox tests), 172 total. §AUDIT FURN/ROOMS/TAGS → PASS

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

1. **S233b wrap-up** — re-add navigate.js to index.html, deploy to dev, mobile field test
2. **S234 field test** — deploy wizard changes to OCI DEV, test flip+storey+classify on engel-house
3. **Gerard's HDP DAE** — tune classification for real HDP node names
4. **S227 refactor triage** — `prompts/S227_refactor_triage.md` (4 sessions spec'd)

## Reference

- Docs site: https://red1oon.github.io/BIMCompiler/
- OCI Live: `bim-ootb-live` | OCI Dev: `bim-ootb-dev` | DBs: `bim-ootb-full` | Test: `bim-ootb-live2`
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
