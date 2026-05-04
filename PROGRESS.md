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

## Active Work — Browser BIM OOTB

**S245c DONE (2026-05-04): R-tree + Clash Performance & UX overhaul.**
  - WASM swap: sql.js → rtree-sql.js@1.7.0 (CDN, SQLITE_ENABLE_RTREE). SW cache v249.
  - R-tree built async (5k batches, ~1.2s non-blocking). For S245d single-element lookups.
  - R-tree self-join O(N²) — not viable for pair-finding. All queries stay bbox arithmetic.
  - Matrix bg check: discipline envelope overlap (one GROUP BY, instant, accurate).
  - Cell click: LIMIT 30 storey-scoped (auto-picks top 5 storeys, avoids full-building N²).
  - Clash viz: clipped actual mesh (red+blue) at overlap zone. Camera targets overlap centre.
  - Overlap clipping padded to 0.3m min visibility. Row highlight on selected clash.
  - Matrix persists on cell click. Measure dots disabled while panels open.
  - Info card X close fixed. No auto-dismiss on canvas click.
  - Status: 🟡RVW 🟢SLV ⚪ACC with live counts. Right-click/long-press/double-click toggle.
  - clash_rules.json: 6→12 rules (added ELEC/FP/ACMV pairs, removed dead HVAC/PLUMB).
  - Right-click empty space = whole-building info card (all storeys, all disciplines).
  - HTML report: matrix snapshot, stat cards, 6 Chart.js charts (severity, status, disc pair,
    class pair, discipline risk radar, top offenders), matrix summary table, editable action sheet.
  - CSV export from HTML report (includes editable fields). Sorted by severity, capped at 100.
  - **Next:** S245d — see `prompts/S245c_rtree_clash.md` §S245d. Key: query heat problem.

**S245b DONE (2026-05-04): Clash Detection + Measure UX overhaul.**
  - Measure: tap-same-dot for area (replaces double-click), DB-backed info card, adaptive status text.
  - Clash Matrix: visual grid with 3D CSS spheres (pulsing grey → green/orange/red).
  - Lazy loading: matrix instant, async sampled check per pair, full query only on click.
  - Click cell → LIMIT 30 paginated clash list with tolerance slider (1-100mm).
  - Click clash row → fly-to + clipped actual meshes at overlap zone (depthTest:false).
  - Review status cycle: Reviewed → Resolved → Accepted (localStorage persisted).
  - Excel export from matrix title bar. clash_rules.json: 6 discipline pair rules.
  - Glass UI (backdrop-filter blur), draggable panels, constant-size measure dots.
  - Runtime SQL indexes (discipline, storey, center_x). Performance: skip dimming >3k meshes.

**S244 DONE (2026-05-03): Sunglasses — material contrast slider + theme toggle.**
  - 🕶 button replaces ☼: click = reverse background (light/dark), opens slider panel.
  - Slider (0–100, 10 zones): recolors all meshes by IFC class/storey/discipline with 10 strategies.
    Warm pastels → cool pastels → earth tones → storey warm/cool → discipline → zebra → mono → random → HARD.
  - Each IFC class gets unique color via golden-angle hue spacing. Largest classes get most contrasting slots.
  - Near-white materials (RGB > 0.85) auto-tamed in `_getMaterial` at streaming time.
  - `ifcClass` stored on mesh/instanced userData for grouping. Zero perf cost — just material swaps.
  - Deployed to `bim-ootb-live/sandbox/`. Proven on Terminal canteen (IfcSlab vs IfcFurniture contrast).

**S242 DONE (2026-05-03): Single-DB deployment, IFC bbox placeholders, instanced IFC export.**
  - Viewer: single DB only (`A.libDb = A.db`), no library fetch. Config: `LIB_URL` removed.
  - Bbox placeholders: use IFC `bbox_x/y/z` from `element_transforms` (not fixed cubes).
  - IFC export: IfcMappedItem instancing (geometry once per hash, elements reference via map).
    Batched geometry loading avoids OOM on large buildings (122K elements proven).
  - VALID_DISCS expanded: +AIR, DUCT, HVAC, MECH, FIRE, SPR, GAS, LIFT, CONV, etc.
  - All 22 buildings re-extracted as single DBs, deployed to `bim-ootb-live/buildings/`.
  - DB queryable immediately on load (IndexedDB cached) — bbox enables 4D/5D/clash without meshing.

**S241 DONE (2026-05-02): Drop IFC merge + disc from filename + Node.js extractor.**
  - Multi-disc merge: combines elements into one DB (not version stacking). Building name normalized.
  - Disc from filename: aliases (ELE→ELEC, FIRE→FP, MECH→ACMV, etc.). Landing-side override.
  - Variance only on "revised" filename. 10-col transforms fix (bbox columns).
  - Node.js extractor: `scripts/extractIFC2DB.js --disc HEAT`. All 25 OCI buildings re-extracted.
  - Proven: LTU SAN+VOID merge, all UNMERGED/ filenames.
  - Specs: `prompts/DropIFCMergeNoVarianceDISC.md`, `docs/SQLite3D_Schema.md`

**S239 DONE (2026-05-01): Deep refactor — `full` branch.**
  - helpers.js, 18 traverse→0, 31 db.exec→dbQuery, 4 SQL injections fixed
  - Lazy-load navigate/wizard, sw.js versioning, minify script (44% reduction)
  - `deploy/dev/` is canonical source. OCI full = minified dev.
  - Remaining: wizard.js traversals, measure.js traverse (low priority)

**S236 DONE: 2D Plans browser DXF viewer.** `deploy/dev/2d.html`, Canvas2D, dxf-parser.

**S243 DONE: Offline PWA.** SW precache, manifest, install prompt, offline/online toast. 45/45 Playwright PASS + 7/7 offline sandbox test. Mobile confirmed.

**S240 UPDATED: 4D Gantt Sync spec.** Added §0 Prelim Check (8-point audit), §0.3 Template System (rates.js-driven, user-checkable, export/import). Ready to implement.

**S233b DONE: Find & Navigate.** Indoor wayfinding. 26/26 Playwright PASS.

**S232 DONE: Mobile merge + InstancedMesh.** 95% draw call reduction on mobile.

**S228-S231 DONE: Drop Zone Multi-Format Import.** IFC/OBJ/DAE/GLB/FBX/3DS/STL.
  - Classification Wizard, IFC Export, InstancedMesh batching. 108/108 Playwright PASS.

**S225b DONE: Rates + Locale.** `rates.js`, 15 locale files.

**S222-S224 DONE: DB Refactor + Diff + VO + Versioned Cards.** Diff engine, VO Excel.

**S220 DONE: IFC Browser Import.** web-ifc WASM, IFC2x3+IFC4 proven at 122K elements.

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
