# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: 2D Architectural Views — Section Contours + Elevation Edges in Grid Overlay
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: ARCHITECTURE COMPLETE — engines wired, renderer built, tests passing (36/36)

# 2D_023 — 2D Architectural Views in 3D Grid Overlay

## Context — What Exists and Works

### Baseline (commit cadf12d4 — preserved)
- Grid lines + legend panel + click-to-highlight + zoom — 408 lines, clean
- All baseline functions confirmed intact: `buildGridScene`, `addGridLine`, `createBubble`, `buildPanel`, `highlightGrid`, `zoomToGrid`, `onPanelRowClick`
- See screenshot `~/Pictures/Screenshots/Screenshot from 2026-05-06 07-35-04.png` for the clean Roof view baseline

### Module Architecture (9 files, single responsibility each)

| File | Concern | Namespace | Lines |
|------|---------|-----------|-------|
| `grid_config.js` | JSON view config (styles, retain, clip, contourMode) | `GridConfig` | ~200 |
| `grid_views.js` | Camera positioning, section clip, theme | `GridViews` | ~275 |
| `grid_contours.js` | 2D line renderer (engine output → Three.js lines) | `GridContours` | ~240 |
| `grid_door_arcs.js` | Door swing arc geometry | `DoorArcs` | ~170 |
| `grid_overlay.js` | Grid scene, panel, dimensions, orchestration | `setupGridOverlay` | ~700 |
| `grid_assembler.js` | Module wiring, preflight checks | `GridAssembler` | ~85 |
| `grid_dims.js` | Grid detection from columns | `GridDims` | ~450 |
| `section_cut.js` | Horizontal mesh slicing → closed contours | `SectionCut` | ~360 |
| `elevation.js` | Vertical mesh projection → edge segments | `Elevation` | ~360 |

### Load Order (index.html)
```
grid_dims → grid_config → grid_views → grid_door_arcs →
section_cut → elevation → grid_contours →
grid_overlay → grid_assembler → main.js
```

### Test Suite
- `node deploy/dev/tests/test_grid_modules.js` — 36 tests, 77 §-log lines
- Covers: syntax, config alignment, API contracts, maths proofs (Pythagoras, dimension sums, frustum geometry), no hardcoded classes, load order

## What Each View Does

### Floor Plans (GF, L1) — contourMode: 'section'
1. `GridViews.lockView()` — ortho camera top-down + horizontal clip
2. `SectionCut.sectionCut(db, libDb, cutZ)` — slices meshes → closed polyline contours
3. `GridContours.renderContours()` — converts contours to Three.js Lines, styled by GridConfig
4. `DoorArcs.generateArcs()` → `GridContours.addDoorArcs()` — quarter-circle swing arcs
5. **Retain list** (from GridConfig JSON): IfcFurnishingElement, IfcFurniture, IfcFlowTerminal, IfcSanitaryTerminal, IfcElectricalAppliance, IfcLightFixture, IfcBuildingElementProxy, IfcCovering
6. Wall thickness = contour distance (extracted, not invented)

### Elevations (F, R, L, S) — contourMode: 'elevation'
1. `GridViews.lockView()` — ortho camera facing building
2. `Elevation.generateElevation(db, libDb, face)` — projects mesh edges orthographically
3. `GridContours.renderEdges()` — converts edge segments to Three.js LineSegments, depth-sorted
4. `GridContours.renderLevelMarkers()` — dashed storey lines from `SectionCut.detectStoreys()`
5. **3D meshes hidden** for clean line drawing — restored on clear/unlock
6. Styles per IFC class: walls thick (#000, 2.5), windows medium (#444, 1.0), beams thin (#888, 0.5)

### Roof — contourMode: null
- Keep 3D top-down view as-is. No contours, no changes.

## What Went Wrong Before (and is now fixed)
1. **Elevation aspect correction** — `halfW = halfH * aspect` distorted proportions → REMOVED
2. **Forced theme toggle** — toggled on every view lock/unlock → now tracks `floorForcedLight` flag
3. **Floor plan via clipping plane only** — shows flat 3D cut, not architectural contours → now uses `section_cut.js` contours
4. **`localClippingEnabled` never reset** — clip persisted into Roof/R/L/S → now reset to `false` in `clearFloorClip()`
5. **`clippingPlanes` restored to `[]` not `null`** — Three.js default is `null` → fixed
6. **Hardcoded skipClasses** — `{ IfcFurnishingElement: 1, IfcFurniture: 1 }` → now reads `GridConfig.retainSet(mode)` with 8 classes

## JSON Strategy — Config Not Code
All styling decisions live in `grid_config.js`:
- **To change a line weight**: edit `GridConfig.views.front.styles.IfcWall.weight`
- **To add a retained class**: add to `GridConfig.views.floor.retain` array
- **To disable level markers**: set `GridConfig.views.front.levelMarkers.enabled = false`
- **To add a new view**: add entry in `GridConfig.views` + `GridViews.VIEW_DEFS`

## What to Tweak Next (Output Quality)
The architecture is solid. Output may need adjustments — all via JSON config, not code:

1. **Elevation edge density** — too many internal edges? Adjust `elevation.js` depth margin or add a min-edge-length filter in GridConfig
2. **Level marker positions** — currently auto-detected from storeys. May need manual overrides for EAVE/RIDGE (add to config)
3. **Line weight rendering** — WebGL `linewidth` > 1 only works on some platforms. If needed, use `THREE.Line2` from Three.js examples for pixel-accurate line widths
4. **Floor plan furniture projection** — retained classes are shown unclipped but not yet rendered as contour outlines. Next step: use EdgesGeometry on retained meshes for top-down outlines

## Coordinate Transforms Reference
- IFC: X=east, Y=north, Z=up
- Three.js: X=east, Y=up, Z=south
- `A.ifc2three(ix, iy, iz)` → `{x: ix-off.x, y: iz-off.z, z: -(iy-off.y)}`
- Elevation (h,v) → IFC: front h=X v=Z, rear h=-X v=Z, left h=Y v=Z, right h=-Y v=Z

## §5 — What NOT to Do
- Do NOT modify `section_cut.js` or `elevation.js` — proven engines, read-only
- Do NOT invent geometry — every line traces to mesh data or DB query
- Do NOT hardcode styles in renderers — read from GridConfig
- Do NOT add aspect ratio correction — building proportions are sacred
- Do NOT merge concerns — renderer doesn't call engines, orchestrator doesn't create Three.js objects
- Do NOT touch Roof view — it works as-is

## Design Blueprint — Why Each Module Exists

### The Pipeline (data flows left to right)
```
DB (meshes) → Engine (geometry) → Renderer (Three.js) → Scene (display)
                                       ↑
                                  GridConfig (style)
```

**Engines extract geometry from meshes. Renderers produce Three.js objects from geometry. Config drives style. No module crosses its boundary.**

### Single Responsibility Proof

| Module | Exactly one reason to change it |
|--------|-------------------------------|
| `GridConfig` | A style/retain/clip value changes |
| `GridViews` | Camera behaviour changes |
| `GridContours` | How geometry becomes Three.js lines changes |
| `DoorArcs` | Door arc computation changes |
| `section_cut.js` | Mesh slicing algorithm changes |
| `elevation.js` | Edge projection algorithm changes |
| `grid_overlay.js` | UI orchestration changes |
| `grid_assembler.js` | Module dependency graph changes |

### Dependency Rules
- Engines (`section_cut.js`, `elevation.js`) depend on NOTHING except the DB
- Renderers (`grid_contours.js`, `grid_door_arcs.js`) depend only on GridConfig + Three.js
- Orchestrator (`grid_overlay.js`) calls engines then passes output to renderers
- Assembler checks modules exist at init time

## Testing Strategy — Maths is Truth

### Whitebox §-Log Protocol
Every module emits §-tagged `console.log()` lines. Tests capture these to verify values mathematically, not visually. A human never needs to "look at the screen" to know if it works.

### How to Prove Each Purpose (Maths)

| Purpose | What to prove | Maths test |
|---------|--------------|------------|
| **Grid lines correct** | Grid positions match column DB positions | `§GRID_LINE ifc=X.XXX` matches `SELECT center_x FROM element_transforms WHERE ifc_class='IfcColumn'` |
| **Dimensions sum** | Bay dims sum to overall | `Σ(bay[i].distance) === total.distance` (T22 — arithmetic) |
| **No aspect distortion** | Frustum uses building dims directly | `halfW = (bldW/2)*margin`, `halfH = (bldH/2)*margin`, no `innerWidth/innerHeight` anywhere (T15, T19) |
| **No clip leaking** | Only floor views apply clip | `VIEW_DEFS.clip === true` iff `GridConfig.clipFor(mode) !== null` (T16) |
| **Door arc on circle** | Every arc point satisfies x²+y²=r² | `dist(pt, hinge) === radius` for all 17 points (T12 — Pythagoras) |
| **Hinge at wall** | Nearest endpoint to wall contour | `dist(hinge, wallPt) < dist(free, wallPt)` (T11 — distance comparison) |
| **Arc midpoint at 45°** | Parametric proof at t=0.5 | `pt = (r·cos(π/4), r·sin(π/4))` relative to hinge (T20) |
| **Camera direction orthogonal** | Each view has exactly 1 non-zero axis | `count(dx≠0, dy≠0, dz≠0) === 1` (T13) |
| **Config ↔ code alignment** | VIEW_DEFS keys match GridConfig keys | `sort(Object.keys(VIEW_DEFS)) === sort(Object.keys(GridConfig.views))` (T2) |
| **Retain only on clip views** | Non-clip views have empty retain | `clip ? retain.length > 0 : retain.length === 0` (T3) |
| **Style coverage** | All ELEVATION_CLASSES have styles | Every class in elevation.js ELEVATION_CLASSES has a GridConfig style entry (T26) |
| **Section cutZ** | CutZ computed from config offsets | `floor: zMin + 1.0 = 0.7`, `floor1: zMin + 9.0*0.55 = 4.65` (T23) |
| **Baseline preserved** | All 8 original functions present | String search in source code (T21) |
| **No hardcoded classes** | grid_overlay.js has no `skipClasses` | String absence check (T5) |

### Running Tests
```bash
node deploy/dev/tests/test_grid_modules.js
# 36/36 passed, 77 §-log lines
```

### Adding a New Test
1. Name the issue it proves: `T30: [description] — [maths method]`
2. Compute expected value from first principles (no "looks right")
3. Assert with tolerance: `assertClose(actual, expected, 0.001, 'msg')`
4. Emit §-log showing both values: `logTag('T30_X', 'actual=' + a + ' expected=' + b)`

## Files Reference
- Grid config: `deploy/dev/grid_config.js`
- Camera/clip/theme: `deploy/dev/grid_views.js`
- 2D renderer: `deploy/dev/grid_contours.js`
- Door arcs: `deploy/dev/grid_door_arcs.js`
- Grid scene/panel: `deploy/dev/grid_overlay.js`
- Module wiring: `deploy/dev/grid_assembler.js`
- Section engine: `deploy/dev/section_cut.js` (read-only)
- Elevation engine: `deploy/dev/elevation.js` (read-only)
- Tests: `deploy/dev/tests/test_grid_modules.js`
- Viewer HTML: `deploy/dev/index.html`
