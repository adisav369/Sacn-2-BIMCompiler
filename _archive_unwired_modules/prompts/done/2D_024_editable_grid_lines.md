# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: 2D Grid Line Editing — Drag grid lines, rules-driven cascade, shadow outlines, compound undo
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: IMPLEMENTING — grid_drag.js + grid_rules.json + tests
# RESUME: Read this prompt → run tests → verify §-logs → deploy dev

# 2D_024 — Editable Grid Lines with Rules-Driven Cascade

## Context — What Exists (commit 9a6c9cf3 + this session)

### Architecture (11 modules, 83+ tests, 146+ §-logs)

| File | Concern | Lines |
|------|---------|-------|
| `grid_config.js` | JSON view config (IIFE, DRY shared styles) | 170 |
| `grid_views.js` | Camera positioning, section clip | 275 |
| `grid_contours.js` | 2D line renderer (engine output → Three.js) | 267 |
| `grid_door_arcs.js` | Door swing arcs (cross-product direction) | 210 |
| `grid_dim_chains.js` | Dimension chain rendering | 193 |
| **`grid_drag.js`** | **Drag editing + cascade + shadow + undo (NEW)** | **~300** |
| `grid_overlay.js` | Grid scene, panel, orchestration + state accessor | 640 |
| `grid_assembler.js` | Module wiring, preflight | 90 |
| `grid_dims.js` | Grid detection from columns | 450 |
| `section_cut.js` | Horizontal mesh slicing → contours | 360 |
| `elevation.js` | Full orthographic projection → edges | 310 |

### Current Interaction
- Click bay row (e.g. "1–5") → both grid lines highlight orange + transparent slab
- Grid lines are static — positions from `GridDims.detectGrids()` reading column positions from DB

### What This Prompt Adds
1. **Drag-to-reposition** grid lines with rules-driven constraints
2. **Post-move cascade** repositions furniture/devices/switches per clearance rules
3. **Shadow outlines** show proposed element positions before commit
4. **Compound undo** reverts grid + all cascaded elements in one shot
5. **All constants from `grid_rules.json`** — zero hardcoded values

## §1 — Rules JSON (`grid_rules.json`)

ALL drag constraints and cascade strategies are defined in this JSON file.
Code reads from this file — never hardcodes values.

```json
{
  "grid_move": {
    "min_bay_m":    0.5,    // minimum bay width (prevents unreasonable rooms)
    "max_extend_m": 5.0,    // max beyond original building envelope
    "snap_m":       0.05,   // 50mm snap granularity
    "max_step_m":   2.0     // max distance per single drag gesture
  },
  "clearance": [
    { "class": "IfcFurnishingElement", "wall_min_m": 0.1, "grid_min_m": 0.3, "strategy": "proportional" },
    { "class": "IfcSwitchingDevice",   "wall_min_m": 0.15, "grid_min_m": 0.0, "strategy": "pin_to_wall" },
    { "class": "IfcOutlet",            "wall_min_m": 0.05, "grid_min_m": 0.0, "strategy": "pin_to_wall" },
    { "class": "IfcLightFixture",      "ceiling_min_m": 0.0, "grid_min_m": 0.5, "strategy": "center_bay" },
    { "class": "IfcFlowTerminal",      "wall_min_m": 0.05, "grid_min_m": 0.0, "strategy": "pin_to_wall" },
    { "class": "IfcSanitaryTerminal",  "wall_min_m": 0.1, "grid_min_m": 0.0, "strategy": "pin_to_wall" }
  ],
  "shadow": {
    "color":   "#ff6600",
    "opacity":  0.35,
    "dash_m":   0.3
  }
}
```

## §2 — Drag Rules (constraints from grid_rules.json)

### Rule 1: Cannot cross neighbours
Grid line CANNOT be dragged past its adjacent line. Preserves ordering.

**Maths:** `pos[i-1] + min_bay_m ≤ pos[i] ≤ pos[i+1] - min_bay_m`

### Rule 2: Minimum bay width
No bay narrower than `min_bay_m`. Prevents unreasonably thin rooms.

### Rule 3: Outermost grids have envelope limit
First/last grid lines cannot exceed `max_extend_m` beyond original envelope.

**Maths:** `originalEnv.xMin - max_extend_m ≤ pos[0]`

### Rule 4: Snap granularity
`pos = Math.round(pos / snap_m) * snap_m`

### Rule 5: Drag axis locked
X-axis grid moves in X only. Y-axis in Y only. No diagonal.

### Rule 6: Max step per gesture
Single drag gesture limited to `max_step_m` from start position.

**Maths:** `startPos - max_step_m ≤ pos ≤ startPos + max_step_m`

## §3 — Interaction Design

### Drag Start
- pointerdown on grid line or bubble in 3D scene
- Raycaster identifies by `userData.gridLabel`
- Line → orange, linewidth 3; orbit controls disabled

### Drag Move
- Pointer → IFC coordinates via ground-plane raycast
- Apply constraints (§2 Rules 1-6, all from grid_rules.json)
- Update grid line, bubbles, dim chains, panel — all live
- Cascade: compute proposed element repositioning
- Show shadow outlines at proposed positions

### Drag End (pointerup)
- Record compound undo: `{ grid: {label, axis, oldPos, newPos}, elements: [{guid, oldX, oldY, newX, newY, strategy}] }`
- Clear shadow outlines
- Re-enable orbit controls
- Log: `§GRID_DRAG label=3 axis=X oldPos=12.900 newPos=14.200 delta=+1.300 cascaded=5`

## §4 — Post-Move Cascade

### Three strategies (from grid_rules.json clearance array):

| Strategy | Behaviour | Use case |
|----------|-----------|----------|
| `proportional` | Scale position within bay: `newPos = loNew + t * newWidth` where `t = (coord - lo) / oldWidth`. Enforce `grid_min_m` clearance from bay edges. | Furniture — moves proportionally as room stretches/shrinks |
| `pin_to_wall` | Stay at same distance from nearest bay edge. Enforce `wall_min_m`. | Switches, outlets — pinned to wall face |
| `center_bay` | Place at centre: `(loNew + hiNew) / 2` | Light fixtures — always centred in bay |

### Affected bays
When grid line at index `i` moves, two bays are affected:
- Bay before: grid[i-1] .. grid[i]
- Bay after:  grid[i] .. grid[i+1]

Elements in these bays are queried by IFC class + coordinate range from `elements_meta JOIN element_transforms`.

## §5 — Shadow Outlines

- Wireframe boxes (THREE.EdgesGeometry) at proposed new positions
- Colour/opacity from `grid_rules.json → shadow`
- Created during drag (live preview), cleared on pointerup
- Each shadow tagged with `userData.shadowGuid` for identification

## §6 — Compound Undo

Each drag gesture produces one compound record:
```javascript
{
  grid:     { label, axis, idx, oldPos, newPos, delta },
  elements: [ { guid, ifcClass, oldX, oldY, newX, newY, strategy } ]
}
```

`GridDrag.undo()` pops the last record, reverts the grid line shift + logs each element revert.

## §7 — What Updates on Drag

| Element | Update |
|---------|--------|
| Grid line (Three.js Line) | v0/v1 shift by delta on locked axis |
| Both bubbles (Sprites) | position shifts by delta |
| Dim chain segments | endpoints shift, label recalculated |
| Panel text | bay distance recalculated in mm |
| Shadow outlines | wireframe boxes at proposed cascade positions |
| Contours | NOT updated during drag — too expensive |
| Elevation edges | NOT updated during drag |

## §8 — Module Placement

### `grid_drag.js` (IIFE → `GridDrag`)
Single concern: pointer events + constraint logic + cascade maths + shadow display.

**Depends on:** `grid_overlay.js` (reads state via `A._gridOverlayState`), `DimChains`, `grid_rules.json` (fetched on init).

**API:**
```javascript
GridDrag.init(APP, state)                    // wire pointer events
GridDrag.loadRules(json)                     // load parsed rules JSON
GridDrag.rules()                             // current rules object
GridDrag.enabled()                           // true if drag in progress
GridDrag.history()                           // compound undo records
GridDrag.undo()                              // revert last drag + cascade
GridDrag.clamp(pos, idx, positions, axis, env, rules, startPos)
GridDrag.snap(pos, snapM)
GridDrag.cascadeElements(axis, idx, oldPos, newPos, gridLines, db, rules)
GridDrag.applyStrategy(strategy, axis, cx, cy, bay, rule)
```

### Load order (index.html)
```
... grid_dim_chains.js → grid_drag.js → grid_overlay.js → grid_assembler.js
```

### Assembler registration
```javascript
GridDrag: { required: false, desc: 'grid line drag editing' }
```

### State accessor (`grid_overlay.js`)
`A._gridOverlayState` exposes closure variables as live getters:
`{ active, gridGroup, gridData, envCache, lineMeshes, bubbleScale, rebuildPanel() }`

## §9 — What NOT to Do
- Do NOT recompute contours or elevation during drag
- Do NOT write to DB during drag — deltas are in-memory until explicit commit
- Do NOT allow diagonal drag
- Do NOT invent positions — all derive from DB + user delta
- Do NOT hardcode any constant — all from grid_rules.json
- Do NOT modify grid_rules.json without explicit instruction (feedback: no_invent_rules)
- Do NOT break existing click-to-highlight

## §10 — Future (not this prompt)
- Commit button → writes grid deltas + element moves to DB
- Feed deltas to DXFSyncVerb-style recompilation
- RouteWalker re-routes MEP through new geometry
- Multi-undo / redo stack
- Visual diff mode: before/after toggle

## §11 — Testing Strategy

### New tests to add (T75+)
| Test | What it proves | §-log |
|------|---------------|-------|
| T75 | grid_rules.json parses without error | §T75_RULES |
| T76 | min_bay_m prevents bay < 500mm | §T76_MINGAP |
| T77 | Grid cannot cross neighbour (ordering preserved) | §T77_NOCROSS |
| T78 | Outermost grid respects max_extend_m | §T78_ENVELOPE |
| T79 | snap rounds to snap_m (50mm) | §T79_SNAP |
| T80 | Drag axis locked (X-grid moves only in X) | §T80_AXISLOCK |
| T81 | max_step_m limits single gesture distance | §T81_MAXSTEP |
| T82 | Delta recorded with correct old/new positions | §T82_DELTA |
| T83 | Compound undo record includes grid + elements | §T83_COMPOUND |
| T84 | Undo reverts grid to original position | §T84_UNDO |
| T85 | proportional strategy scales position in bay | §T85_PROPORTIONAL |
| T86 | pin_to_wall strategy preserves wall distance | §T86_PINWALL |
| T87 | center_bay strategy centres element | §T87_CENTER |
| T88 | clearance rules filter correct IFC classes | §T88_CLASSES |
| T89 | Dim chain updates after drag (rebuild called) | §T89_DIMCHAIN |
| T90 | Panel text updates after drag | §T90_PANEL |
| T91 | GridDrag in assembler module registry | §T91_ASSEMBLER |
| T92 | grid_drag.js in correct load order in index.html | §T92_ORDER |

### Maths proofs
- Constraint: `pos[i-1] + 0.5 ≤ pos[i] ≤ pos[i+1] - 0.5` — test with synthetic grids
- Snap: `round(1.273 / 0.05) * 0.05 = 1.25` — exact arithmetic
- Envelope: `pos[0] ≥ env.xMin - 5.0` — boundary test
- max_step: `|pos - startPos| ≤ 2.0` — clamp test
- proportional: `t = 0.3 → newPos = loNew + 0.3 * newWidth` — exact
- pin_to_wall: `distLo = 0.5 → newPos = loNew + 0.5` — exact
- center_bay: `(3.0 + 7.0) / 2 = 5.0` — exact

## Files Reference
- Grid config: `deploy/dev/grid_config.js`
- Camera/clip: `deploy/dev/grid_views.js`
- 2D renderer: `deploy/dev/grid_contours.js`
- Door arcs: `deploy/dev/grid_door_arcs.js`
- Dim chains: `deploy/dev/grid_dim_chains.js`
- **Grid drag: `deploy/dev/grid_drag.js` (NEW)**
- **Grid rules: `deploy/dev/grid_rules.json` (NEW)**
- Grid scene/panel: `deploy/dev/grid_overlay.js` (updated — state accessor)
- Module wiring: `deploy/dev/grid_assembler.js` (updated — GridDrag registered)
- Section engine: `deploy/dev/section_cut.js`
- Elevation engine: `deploy/dev/elevation.js`
- Tests: `deploy/dev/tests/test_grid_modules.js`
- Viewer HTML: `deploy/dev/index.html` (updated — grid_drag.js script tag)
