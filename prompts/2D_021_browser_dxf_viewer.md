# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: Browser 2D Plans — dynamic generation from DB, scaling to any building
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: ACTIVE — SH+DX+Hospital proven. Pending: visual QA, elevation HLR, roof plan, OCI deploy

# 2D_021 — Browser 2D Plans

## ⚠ SACRED BASELINES — locked, never regenerate

| File | Entities | Layers | BIMSRC |
|------|----------|--------|--------|
| `dxf/SH_FLOOR.dxf` | **292** | **12** | **93** |
| `dxf/SH_ROOF.dxf`  | **122** | **6**  | **0**  |

Tests 14.9 + 14.10 enforce exact counts. `dxf/SH_FLOOR.dxf` and `dxf/SH_ROOF.dxf` are **READ-ONLY**.

## Current State (after S238, 2026-04-29)

### What works
- `2d.html` — dual-mode: DXF-file path (no `?db=`) AND dynamic-from-DB path (`?db=&lib=`)
- `section_cut.js` — mesh-plane intersection, rtree+transforms dual fallback, **clipBox option**
- `grid_dims.js` — column→grid→dimension pipeline (empty for buildings without IfcColumn)
- `elevation.js` — orthographic projection with depth sort
- `dxf_export.js` — AC1015 DXF serializer with BIMSRC xdata, browser download
- Auto-clip for large buildings: `getBuildingStats()` + `MAX_ELEMENTS_POC=500`, `CLIP_MARGIN=15m`
- **Hospital proven**: 63,917 elements → clipped to 30m×30m centre → 1872 entities, 756ms

### Proven numbers
| Building | Elements | Cut time | Entities out | Clip? |
|----------|----------|----------|--------------|-------|
| SH       | 65       | ~20ms    | 68 polylines | No    |
| DX       | 1169     | ~40ms    | ~200+        | No    |
| Hospital | 63917    | ~756ms   | 1872         | Yes (auto) |

### Test suite
- 34 tests in `14-2d-plans.spec.js` — **34/34 PASS**
- Sacred baselines 14.9/14.10 locked
- 14.32: SH dynamic structurally similar to pristine DXF (ratio=0.62, 5/6 classes shared)
- 14.33: Hospital auto-clips, <15s, `§2D_LARGE_BUILDING` log appears
- 14.34: `getBuildingStats`, constants exported correctly
- Audit: 158 tests, ratio 2.51

### Known issues / schema facts
1. **sql.js has NO R-tree** — `elements_rtree` table exists in DBs but queries throw. Code catches + falls back to `element_transforms`. Never remove the try/catch.
2. **Deployed DBs schema**: SH has no `base_geometries` → geometry in `component_geometries` (library DB). `lookupGeometry()` tries both tables in both DBs.
3. **OCI path** — `2d.html` must be uploaded to BOTH `dev/` and `sandbox/` if scripts use relative `src=`. Check `internal/OCI_SETUP.md`.
4. **Uint8Array alignment** — BLOB returns from sql.js may not be 4-byte aligned. Always copy to fresh `ArrayBuffer` before `Float32Array`.

## Pending (priority order)

### 1. Visual QA — compare dynamic floor plan vs pristine DXF (manual + screenshot)
**How:** Open `http://localhost:8080/dev/2d.html?db=/buildings/SampleHouse_extracted.db&lib=/buildings/SampleHouse_library.db`
Then open `http://localhost:8080/dev/2d.html` (loads `dxf/SH_FLOOR.dxf`).
Both should show the same footprint — walls, doors, windows in same positions.
Dynamic uses world metres; DXF uses local mm. Scale differs — shape must match.

### 2. Roof plan from DB
SH_ROOF.dxf (122 entities, 6 layers) = top-down projection of roof elements.
Dynamic equivalent: filter `ifc_class IN ('IfcRoof','IfcSlab')`, project XY, render outlines.
Add a `roof` view-mode option to `section_cut.js` or handle in `2d.html`.
Proof: test 14.35 — dynamic roof has `IfcRoof` or `IfcSlab`, entity count > 10, layers ≥ 1.

### 3. OCI deploy — upload new files
Files changed this session (not yet on OCI):
- `deploy/dev/section_cut.js` — has new `clipBox` + `getBuildingStats`
- `deploy/dev/2d.html` — has auto-clip logic
Follow `internal/OCI_SETUP.md` upload flow. Bump `?v=N` on script refs if needed.
Verify with `curl` that the new functions are live.

### 4. Hospital / Terminal visual QA
Hospital clip centre=[137,71] (world coords). The 30m window may land on a corridor or
external area with few walls. If clip produces empty/sparse results visually:
- Option A: user-selectable clip centre (click on plan to re-centre clip)
- Option B: auto-detect densest 30m tile using element_transforms grid

### 5. Elevation hidden-line removal
Current approach: depth-sorted edge overdraw. Works for SH. Complex buildings show wireframe.
Defer true HLR. When ready: port Python `drawing_writer_dxf.py draw_elevation()` properly.

### 6. Grid detection for buildings without IfcColumn
SH has 0 IfcColumn → grids empty (correct, test 14.27 confirms).
Future: wall-boundary fallback using corner clusters from wall contours.

## Key files
- `deploy/dev/2d.html` — dual-mode renderer, auto-clip in `generateFromDb()`
- `deploy/dev/section_cut.js` — `sectionCut(db,libDb,cutZ,storeyName,options)`, `getBuildingStats(db)`
- `deploy/dev/grid_dims.js` — grid pipeline
- `deploy/dev/elevation.js` — elevation pipeline
- `deploy/dev/dxf_export.js` — DXF serializer
- `deploy/dev/tests/specs/14-2d-plans.spec.js` — 34 tests
- `2D_Layout/python/section_cut.py` — reference Python implementation
- `2D_Layout/python/drawing_writer_dxf.py` — full Python pipeline (elevation, roof)

## Pre-flight for next session
1. Read `deploy/dev/section_cut.js` — `getBuildingStats`, `clipBox` filter
2. Read `deploy/dev/2d.html` lines 820–850 — auto-clip logic in `generateFromDb()`
3. Read `deploy/dev/tests/specs/14-2d-plans.spec.js` — last 3 tests (14.32–14.34)
4. Run `node deploy/dev/tests/audit_specs.js` — must exit 0 before any changes
5. Run `npx playwright test specs/14-2d-plans.spec.js --grep "@sacred"` — must be 2/2 PASS
