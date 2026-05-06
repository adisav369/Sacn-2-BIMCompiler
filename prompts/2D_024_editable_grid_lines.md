# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: 2D Grid Line Editing — Drag grid lines to reposition, write deltas to DB
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: NEW — spec only, no implementation yet
# RESUME: Read this prompt → run tests (83/83) → implement drag → test → deploy dev

# 2D_024 — Editable Grid Lines

## Context — What Exists (commit 9a6c9cf3)

### Architecture (10 modules, 83 tests, 146 §-logs)

| File | Concern | Lines |
|------|---------|-------|
| `grid_config.js` | JSON view config (IIFE, DRY shared styles) | 170 |
| `grid_views.js` | Camera positioning, section clip | 275 |
| `grid_contours.js` | 2D line renderer (engine output → Three.js) | 267 |
| `grid_door_arcs.js` | Door swing arcs (cross-product direction) | 210 |
| `grid_dim_chains.js` | Dimension chain rendering | 193 |
| `grid_overlay.js` | Grid scene, panel, orchestration | 590 |
| `grid_assembler.js` | Module wiring, preflight | 87 |
| `grid_dims.js` | Grid detection from columns | 450 |
| `section_cut.js` | Horizontal mesh slicing → contours | 360 |
| `elevation.js` | Full orthographic projection → edges | 310 |

### Current Interaction
- Click bay row (e.g. "1–5") → both grid lines highlight orange + transparent slab between them
- Grid lines are static — positions come from `GridDims.detectGrids()` which reads column positions from DB

### What This Prompt Adds
Drag-to-reposition grid lines. The grid line becomes a handle — drag it and the dimension chain, bubbles, and panel update live. On release, the delta is recorded.

## §1 — Drag Rules (constraints)

### Rule 1: Cannot cross neighbours
A grid line CANNOT be dragged past its adjacent grid line. Grid 3 sits between 2 and 4 — it cannot go left of 2 or right of 4. This preserves grid ordering.

**Maths:** For X-axis grid at index `i` with positions `pos[0..n-1]`:
```
pos[i-1] + MIN_GAP  ≤  pos[i]  ≤  pos[i+1] - MIN_GAP
```
Edge grids (i=0, i=n-1): only one-sided constraint.

### Rule 2: Minimum bay width
No bay may become narrower than `MIN_GAP`. This prevents unreasonably thin rooms.

**Value:** `MIN_GAP = 0.5` metres (500mm). Below this, no structural element fits.

### Rule 3: Outermost grids have envelope limit
The first and last grid lines on each axis cannot be dragged more than `MAX_EXTEND` beyond the original building envelope.

**Value:** `MAX_EXTEND = 5.0` metres. Prevents dragging a grid line to infinity.

**Maths:** For first grid on X-axis:
```
originalEnv.xMin - MAX_EXTEND  ≤  pos[0]
pos[n-1]  ≤  originalEnv.xMax + MAX_EXTEND
```

### Rule 4: Snap granularity
Positions snap to nearest `SNAP = 0.05` metres (50mm). Prevents sub-centimetre jitter.

**Maths:** `pos = Math.round(pos / SNAP) * SNAP`

### Rule 5: Drag axis locked
An X-axis grid line (constant IFC X, runs along Y) can only be dragged along the X direction. No diagonal movement. Same for Y-axis: Y-direction only.

### Summary of constants
```javascript
var MIN_GAP    = 0.5;   // metres — minimum bay width
var MAX_EXTEND = 5.0;   // metres — max beyond original envelope
var SNAP       = 0.05;  // metres — snap granularity (50mm)
```

## §2 — Interaction Design

### Drag Start
- User presses (pointerdown) on a grid line or its bubble in the 3D scene
- Raycaster identifies the grid line by `userData.gridLabel`
- The line enters drag mode: colour changes to bright orange, cursor changes

### Drag Move
- Pointer moves → compute delta in IFC coordinates along the locked axis
- Apply constraints (§1 Rules 1–4)
- Update grid line position, both bubbles, dimension chain segments, panel text — all live

### Drag End (pointerup)
- Snap final position (Rule 4)
- Record delta: `{ label, axis, oldPos, newPos, delta }`
- Update `gridData` in memory (the working copy, not the DB columns yet)
- Rebuild panel with new dimensions
- Rebuild dimension chains
- Log: `§GRID_DRAG label=3 axis=X oldPos=12.900 newPos=14.200 delta=+1.300`

### Visual Feedback During Drag
- Dragged line: orange, linewidth 3
- Adjacent lines: pulse or highlight to show constraint boundaries
- Dimension text between dragged line and neighbours: live-update in mm
- If drag hits constraint: line stops, no rubber-banding past the limit

## §3 — What Updates on Drag

| Element | Update |
|---------|--------|
| Grid line (Three.js Line) | v0.x / v0.z and v1.x / v1.z shift by delta |
| Both bubbles (Sprites) | position shifts by delta |
| Dim chain — bay segments touching this line | endpoints shift, label recalculated |
| Dim chain — overall segment | endpoints shift if first/last line |
| Panel text | bay distance recalculated in mm |
| Panel total | recalculated |
| Contours | NOT updated during drag — too expensive. Recompute on view change. |
| Elevation edges | NOT updated during drag — recompute on view change. |

## §4 — Module Placement

### New module: `grid_drag.js`
Single concern: pointer event handling + constraint logic for grid line repositioning.

**Depends on:** `grid_overlay.js` (reads `lineMeshes`, `gridData`), `DimChains` (rebuild), Three.js raycaster.

**API:**
```javascript
GridDrag.init(APP, gridOverlayState)  // wire pointer events
GridDrag.enabled()                     // true if drag mode active
GridDrag.history()                     // array of {label, axis, oldPos, newPos, delta}
GridDrag.undo()                        // revert last drag
```

### Load order (index.html)
```
... grid_dim_chains.js → grid_drag.js → grid_overlay.js → grid_assembler.js
```

### Assembler registration
```javascript
GridDrag: { required: false, desc: 'grid line drag editing' }
```

## §5 — What NOT to Do
- Do NOT recompute contours or elevation during drag — too expensive, recompute on view switch
- Do NOT write to DB during drag — deltas are in-memory until explicit save
- Do NOT allow diagonal drag — axis-locked only
- Do NOT invent positions — all positions derive from column DB data + user delta
- Do NOT break existing click-to-highlight — drag is a separate gesture (pointerdown+move vs pointerup-only)

## §6 — Future (not this prompt)
- Save deltas to DB (`element_transforms` update or new `grid_deltas` table)
- Feed deltas to DXFSyncVerb-style recompilation
- RouteWalker re-routes MEP through new geometry
- Undo/redo stack beyond single undo

## §7 — Testing Strategy

### New tests to add
| Test | What it proves |
|------|---------------|
| T75 | MIN_GAP prevents bay < 500mm |
| T76 | Grid cannot cross neighbour (ordering preserved) |
| T77 | Outermost grid respects MAX_EXTEND |
| T78 | SNAP rounds to 50mm |
| T79 | Drag axis locked (X-grid moves only in X) |
| T80 | Delta recorded with correct old/new positions |
| T81 | Dimension chain updates after drag |
| T82 | Panel text updates after drag |
| T83 | Undo reverts to original position |

### Maths proofs
- Constraint: `pos[i-1] + 0.5 ≤ pos[i] ≤ pos[i+1] - 0.5` — test with synthetic grids
- Snap: `round(1.273 / 0.05) * 0.05 = 1.25` — exact arithmetic
- Envelope: `pos[0] ≥ env.xMin - 5.0` — boundary test

## Files Reference
- Grid config: `deploy/dev/grid_config.js`
- Camera/clip: `deploy/dev/grid_views.js`
- 2D renderer: `deploy/dev/grid_contours.js`
- Door arcs: `deploy/dev/grid_door_arcs.js`
- Dim chains: `deploy/dev/grid_dim_chains.js`
- Grid scene/panel: `deploy/dev/grid_overlay.js`
- Module wiring: `deploy/dev/grid_assembler.js`
- Section engine: `deploy/dev/section_cut.js`
- Elevation engine: `deploy/dev/elevation.js`
- **NEW — Grid drag: `deploy/dev/grid_drag.js`**
- Tests: `deploy/dev/tests/test_grid_modules.js` (83 tests, 146 §-log lines)
- Viewer HTML: `deploy/dev/index.html`
