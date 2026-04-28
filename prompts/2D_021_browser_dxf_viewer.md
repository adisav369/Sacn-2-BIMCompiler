# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: Browser 2D Plans — dynamic generation from DB, scaling to any building
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: ACTIVE — POC renderer DONE, dynamic generation is NEXT

# 2D_021 — Browser 2D Plans

## S236 Session Done (2026-04-28)

### What was delivered (POC — static DXF renderer)
- Toolbar icon (colored 2D.png, 18px) in `index.html` toolbar strip
- `deploy/dev/2d.html` — Canvas2D DXF renderer with pan/zoom, layer panel, BIMSRC click
- `dxf-parser.js` bundled, BIMSRC xdata 93/94 tags survive parse
- 14 pre-generated DXFs in `deploy/dev/dxf/` (SH: 6 sheets, DX: 8 sheets)
- Building-aware: `?bld=` param filters dropdown to active building's sheets
- 8 Playwright tests in `14-2d-plans.spec.js`, all PASS
- Deployed to OCI `bim-ootb-dev`

### Lessons learned
1. **dxf-parser drops float xdata** (group code 1040). Fix: encode as strings
   (`"pos_x:-7.749"` not split label+float). One-line change in Python writer.
2. **`getComputedStyle()` needed** for CSS `display:none` toggle.
3. **`window.viewScale`** must be explicitly set — `let` is not on `window`.
4. **Pre-baked DXFs don't scale.** Copying files per building breaks the BIM OOTB
   principle ("two DBs, one browser, zero install"). Must generate from DB.

### What the POC proved
- Canvas2D renders DXF entities correctly (lines, polylines, arcs, circles, text, bulge)
- BIMSRC xdata survives JS parse → GUID correlation works
- Layer toggle, pan/zoom, click-to-info all functional
- The renderer is ready — it's the **input** that needs to change (DB → Canvas, not file → Canvas)

## ⚠ CRITICAL DIRECTION: Dynamic generation from DB

**Pre-baked DXFs are TEMPORARY.** The real system generates 2D directly from the DB
in the browser, same as the 3D viewer renders meshes from BLOBs. No Python, no files.

### Why
- Every building already has all the data: `base_geometries` (vertices/faces),
  `elements_meta` (ifc_class, storey, name), `element_transforms` (position/rotation)
- Section cut = plane-mesh intersection = pure math on Float32Array
- Grid derivation = SQL query on column positions
- Dimensions = arithmetic from grid coordinates
- The Python pipeline (`drawing_writer_dxf.py`) is the **reference implementation**
  — port its logic to JS, running on the same sql.js DB already in the browser

### Architecture (target state)
```
sql.js DB (already loaded in viewer)
  → section_cut.js: horizontal slice at 1.2m → 2D contours
  → grid.js: column centroids → grid axes
  → dims.js: grid-to-grid distances, snapped
  → render to Canvas2D (same renderer already working)
  → BIMSRC tags generated live (guid on each entity)
```

No DXF files. No pre-generation. No file copying. Works for any building.
DXF becomes an **export format** (Download button), not the data model.

### Standard 5-sheet set per building
Every building gets these 5 views (limit scope — don't try all §5 views):

| Sheet | How to generate | Complexity |
|-------|-----------------|------------|
| A-01 Floor Plan | Horizontal section cut at 1.2m above each storey FFL | Medium — needs section_cut port |
| A-02 Front Elevation | Orthographic projection onto XZ plane (Y=min face) | Low — project vertices, draw outlines |
| A-03 Side Elevation | Orthographic projection onto YZ plane (X=min face) | Low — same as A-02, different axis |
| A-06 Roof Plan | Top-down projection (XY), filter roof elements only | Low — just filter + project |
| E-01 MEP Plan | Floor plan + MEP discipline overlay | Medium — reuse A-01 + filter disc='MEP' |

### Scaling challenges (known from Python pipeline experience)

1. **Complex footprints (Hospital Y-shape, Terminal L-shape):**
   - Don't try to show the full building in one elevation
   - Auto-detect footprint aspect ratio; if non-rectangular (convex hull area / bbox area < 0.7),
     pick ONE wing (longest leg of the Y/L) for elevation POC
   - Floor plan still works for any shape — it's just a horizontal cut

2. **Large element counts (Terminal = 48K elements):**
   - Section cut on 48K meshes in JS will be slow
   - Strategy: pre-filter by storey + discipline before cutting
   - Only cut IfcWall, IfcColumn, IfcDoor, IfcWindow, IfcSlab — skip furniture, MEP, openings
   - Target: <2s generation time per view

3. **Multi-storey buildings:**
   - Dropdown lists storeys (from `spatial_structure` table)
   - Each storey = separate floor plan at that storey's elevation + 1.2m
   - DX already proven (GF + FF = 2 floor plans)

4. **Grid detection:**
   - Not all buildings have IfcColumn — fallback to wall boundary detection
   - Skip grid lines entirely if <3 columns found (show dims only)

### Implementation sequence (next session)

| Step | What | Port from |
|------|------|-----------|
| 1 | `section_cut.js` — plane-mesh intersection in JS | `2D_Layout/python/section_cut.py` (~200 lines) |
| 2 | Floor plan from DB — query elements, cut, render contours | `drawing_writer_dxf.py` `draw_floor_plan()` |
| 3 | Elevation from DB — orthographic projection | `drawing_writer_dxf.py` `draw_elevation()` |
| 4 | Grid + dimensions from DB | `drawing_writer.py` `derive_grids()` + `generate_dimensions()` |
| 5 | Sheet selector becomes storey/view selector (no files) | New UI |
| 6 | DXF export button (serialize Canvas state to DXF with BIMSRC) | New |

### Testing discipline
- Every step must have Playwright tests BEFORE manual inspection
- Tests catch regressions automatically — user should not need to visually check
- Test pattern: generate view → assert entity count > threshold, layer count > 3,
  BIMSRC tag count > 0, no JS errors in console
- Scaling tests: assert generation time < 2s for SH, < 5s for DX, < 10s for Hospital
- Run `node deploy/dev/tests/audit_specs.js` after any test changes — must exit 0

### Pre-flight for next session
1. Read `2D_Layout/python/section_cut.py` — understand the mesh-plane intersection algorithm
2. Read `2D_Layout/python/drawing_writer.py` lines 590-721 — grid derivation + room inference
3. Read `deploy/dev/2d.html` — understand current renderer (keep it, change the input)
4. Read `2D_Layout/drawing_template.json` — line weights, colors, grid styles (reuse in JS)
5. Test on SH first (65 elements, simple rectangle), then DX (1169, 2-storey), then Hospital

## Variant DB flow (unchanged from POC)

Editing in the 2D view produces a **variant DB**, not live sync:
1. User edits entities in browser (drag wall, delete door, add partition)
2. Each entity carries GUID (from DB query, or BIMSRC on DXF import)
3. Edits produce variant DB (clone base, update coordinates)
4. "Compare" → `?db=base&diffdb=variant` → existing `diff.js` fires
5. `boq_charts.html` shows 5D cost impact + VO Excel

Zero new diff code needed. Same pipeline as IFC variant drop.

## DXF round-trip with AutoCAD (unchanged)

DXF is the **export/interchange** format. Download DXF from browser → edit in AutoCAD
→ re-import → diff pipeline. BIMSRC xdata survives CAD round-trip.

## Key files
- `deploy/dev/2d.html` — current renderer (keep, change input source)
- `deploy/dev/2D.png` — toolbar icon (colored blueprint, 18px)
- `deploy/dev/dxf-parser.js` — JS DXF parser (keep for DXF import/export)
- `deploy/dev/dxf/` — TEMPORARY pre-baked DXFs (remove once dynamic works)
- `2D_Layout/python/section_cut.py` — reference: mesh-plane intersection (~200 lines)
- `2D_Layout/python/drawing_writer.py` — reference: grid derivation, room inference
- `2D_Layout/python/drawing_writer_dxf.py` — reference: full 2D pipeline (228KB)
- `2D_Layout/drawing_template.json` — shared template (line weights, colors, grid styles)
- `deploy/dev/diff.js` — existing diff pipeline
- `deploy/dev/boq_charts.html` — existing 5D charts

## Spec references
- `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §20 (BIMSRC xdata), §22 (browser editor)
- `docs/BIM_Designer_Browser.md` (viewer architecture)
- `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §2 (8-step deterministic pipeline — port to JS)
