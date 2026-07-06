# 2D_029: 3D Grid Planes + kernel_ops Log + Opening Labels

# ⚠ DO NOT REMOVE — Read the log after every run. Scope: deploy/dev/ ONLY.

## Status: IMPLEMENTED (2026-05-09) — committed afed96c5 + 4c669979

**What is done:**
- `kernel_ops.js`: commitOp/undoOp/redoOp/replayOps — grid moves persist across reload
- `cost_panel.js`: live BOQ panel with close button, spatial SQL query
- `grid_overlay.js`: 3D planes, storey band visibility, replay on init
- `grid_drag.js`: commitOp('GRID_MOVE') on pointerup, Ctrl+Z/Ctrl+Y undo/redo
- `grid_door_arcs.js`: addOpeningLabel — width + type tag sprites
- `scene.js`: G key shortcut toggles grid overlay
- `grid_rules.json`: plane_3d + opening_label config blocks
- 20 wiring tests pass, audit 33 specs / 316 tests / 776 expects
- Deployed to ootb-dev bucket, SW v280
- Docs: BIM_2D_Guide.md (new), 2D_LAYOUT.md updated, MANIFESTO.md linked
- First live kernel_ops proof on Duplex building (2026-05-09)

**What to do next session (PRIORITY ORDER):**

### P1 — Grid Alignment (TOP PRIORITY)
Grid lines don't align with structural walls on complex buildings (SampleCastle, HITOS).
The opportunity-vote algorithm produces too many candidates that get thinned to sparse lines.
Fix: add snap-to-wall post-processing — after clustering, snap each grid line to the nearest
structural face within tolerance. Consider weighting by wall length (longer walls = stronger vote).
Buildings to test: SampleCastle (castle, many walls), HITOS (hospital, 7 storeys), Duplex (known good).

### P2 — Saved Section Restore
Saved section appears in the panel but clicking it back doesn't restore the view.
Debug `restoreSavedSection()` in grid_overlay.js — check if `lockView()` is called,
if the section's `cut_value` is applied, and if contours are rendered.

### P3 — 2D Convention Parser
Scissors mode shows raw contours with no door arcs, no noise filtering, no line weight convention.
Create a unified 2D rendering pass that applies architectural conventions to ANY section cut:
- Door swing arcs (already in GF/L1 path, needs wiring to scissors path)
- Class-based line weights (walls thick, partitions thin, doors dashed)
- Noise filtering: exclude classes above/below storey band
- Opening labels on all floor plan views, not just GF/L1

### P4 — Drag Result as Variant Building
The killer feature from the screenshot: drag a grid line, see cost change, SAVE that as a variant.
`commitOp('VARIANT')` forks the building DB. The orange highlight shows what changed.
This makes the building a live query against a cost model — drag to redesign, save to compare.

### P5 — SH/DX Arc Audit
User reports SH has "invented" arcs and DX has furniture drawn but no arcs.
Verify door arc detection on SH and DX — check if `extractLeafAxis` is finding
the correct door panel contour or matching furniture contours by mistake.

**What this prompt delivers:**
1. `kernel_ops` table + `commitOp()` JS function — the first transactional write path
2. Opening callout labels on 2D floor plans (§9 carry-forward from 2D_028)
3. 3D grid planes projected into the live scene during Adjust Grid mode
4. 3D grid drag — raycaster on planes → `commitOp('GRID_MOVE', ...)` → undo/redo
5. Live cost panel — spatial BOQ query recomputed on grid drag
6. Storey band visibility filter on 3D scene from saved cuts

---

## Session Isolation

**Category:** Browser JS — zero Java, zero live/ edits, zero new files unless named here.

**Prefix:** `S2D29-`

**Files OWNED by this session (create or edit):**
- `deploy/dev/grid_rules.json` — extend with `plane_3d` + `opening_label` keys
- `deploy/dev/grid_overlay.js` — add `renderGridPlanesIn3D()`, storey band visibility
- `deploy/dev/grid_drag.js` — extend drag to 3D planes, `commitOp()` integration
- `deploy/dev/grid_door_arcs.js` — opening callout labels (width, type tag)
- `deploy/dev/grid_dims.js` — read-only (no changes expected), but OWNED if bug found
- `deploy/dev/kernel_ops.js` — **NEW FILE**: `commitOp()`, `undoOp()`, `replayOps()`, table DDL
- `deploy/dev/cost_panel.js` — **NEW FILE**: live BOQ query panel responding to grid position
- `deploy/dev/index.html` — add `<script>` tags for new files
- `deploy/dev/tests/specs/29-3d-grid-kernel.spec.js` — **NEW FILE**: wiring tests

**Files READ-ONLY (reference only):**
- `deploy/dev/section_cut.js` — storey band filter (2D_028), read for data contract
- `deploy/dev/grid_contours.js` — sectionCut consumer
- `deploy/dev/grid_views.js` — VIEW_DEFS source of truth
- `deploy/dev/boq_charts.html` — existing BOQ queries (reference for cost_panel.js)
- `deploy/dev/measure.js` — raycaster pattern (reference for 3D plane drag)
- `deploy/dev/scene.js` — scene setup, APP object
- `deploy/dev/main.js` — APP.ifc2three(), modelOffset
- `deploy/live/*` — PRODUCTION, never touch
- `docs/BIM_Modeller_OOTB.md` — architectural manifesto, kernel_ops schema origin

**Pre-flight citation:** Every changed block must start with:
```
// Implementing 2D_029 §X.Y — Witness: W-2D29
```

---

## Background — Why These Three Things Share One Prompt

Opening labels (§1), 3D grid planes (§3-§5), and kernel_ops (§2) appear to be three
separate features. They share a root: **the grid system is becoming interactive and
persistent.** Opening labels annotate what the grid intersects. 3D planes make the grid
visible in the live scene. kernel_ops makes grid changes survive reload. All three converge
at the drag event: the user drags a 3D grid plane, sees the opening labels shift, and the
cost panel updates — all from a single committed `GRID_MOVE` operation.

**Architectural reference:** `docs/BIM_Modeller_OOTB.md` §The Modelling Inversion defines
the kernel-op log as the source of truth. This prompt implements the first consumer of that
pattern. Grid drag is the simplest possible `kernel_op` — one axis, one position, one output.
No geometry evaluation, no Boolean, no extrude. If this works, Proof 1 is proven.

---

## §1  Opening Callout Labels (carry-forward from 2D_028 §9)

### The Principle

Real architectural 2D drawings have two separate annotation layers:

1. **Grid dim chain** (major): structural bay widths between grid lines — `3600 / 7200 / 3600`
2. **Opening callout** (minor): door/window clear widths stuck directly to the element — `900W`, `D1`

The grid dim chain carries structural module. Opening callouts carry element identity.
These never share the same annotation line.

### §1.1  Width Label

For each `IfcDoor`/`IfcWindow` in the section cut result:

```
width_mm = Math.round(Math.max(bbox_x, bbox_y) * 1000);
label = width_mm + 'W';   // e.g. "900W"
```

- Positioned as a small canvas-textured `THREE.Sprite`
- Offset perpendicular to the wall face, above the door arc / window symbol
- Offset distance: `rules.floor_plan.opening_label_offset_m` (default 0.15 m)
- Font size: `rules.floor_plan.opening_label_font_px` (default 9 px)
- Colour: `#666666` (neutral grey, does not compete with grid labels)

### §1.2  Type Tag

Below the width label, a second line:

```
tag = (element_name || '').split(':')[0] || ifcClass.replace('Ifc', '').toUpperCase();
// e.g. "Single-Flush" from Revit family name, or "DOOR" from IFC class
```

- Same sprite, second line at smaller font (7 px)
- Grey (#999999), subordinate to width label

### §1.3  Implementation in grid_door_arcs.js

The existing `generateWindowOpenings()` and `generateDoorSwings()` functions already know
each opening's position and wall axis. Add label generation at the end of each function:

```javascript
// Implementing 2D_029 §1.1 — Witness: W-2D29
function addOpeningLabel(group, ifcPos, wallAxis, width_mm, tag, rules) {
  var fp = (rules && rules.floor_plan) || {};
  var offset = fp.opening_label_offset_m || 0.15;
  var fontSize = fp.opening_label_font_px || 9;
  // Canvas texture with two lines: width_mm + 'W' and tag
  // Sprite positioned at ifcPos + offset perpendicular to wallAxis
}
```

### §1.4  Log Tag

```
§DOOR_ARC_LABEL guid=G width=NNNmm tag=TYPENAME
```

One line per opening labelled. Proves: label generation fired, correct width extracted,
correct type resolved.

### §1.5  Rules Extension

Add to `grid_rules.json → floor_plan`:
```json
"opening_label_offset_m": 0.15,
"opening_label_font_px":  9
```

---

## §2  kernel_ops — The First Transactional Write Path

### The Principle

From `BIM_Modeller_OOTB.md`: *"The model is a log, not a file."* Every operation is a
committed database transaction. `kernel_ops` is that log.

From DeepSeek's analysis: *"You need to formalise the log as the primary interface, not as
an implementation detail."* The discipline is: every state change goes through `commitOp()`.

**Scope for this prompt:** Grid operations only. BOM walker, RouteWalker, and all other
writers continue to write directly to their existing tables. `kernel_ops` is a parallel log
for grid operations — the first consumer, not a rewrite. This is DeepSeek's transition
step 1-2.

### §2.1  Table DDL

```sql
CREATE TABLE IF NOT EXISTS kernel_ops (
    id           INTEGER PRIMARY KEY,
    timestamp    INTEGER NOT NULL,        -- unixepoch() ms
    op_type      TEXT    NOT NULL,         -- GRID_MOVE, VIEW_FILTER, GRID_DETECT
    parameters   TEXT    NOT NULL,         -- JSON: { axis, from, to, ... }
    input_guids  TEXT,                     -- JSON array: elements affected (nullable)
    output_guid  TEXT,                     -- element/grid created or modified (nullable)
    undone       INTEGER DEFAULT 0         -- 1 = undone, skip in replay
);

CREATE INDEX IF NOT EXISTS idx_kernel_ops_type
    ON kernel_ops(op_type);
CREATE INDEX IF NOT EXISTS idx_kernel_ops_undone
    ON kernel_ops(undone, id);
```

**Why `undone` instead of DELETE:** Undo marks the op as undone (soft delete). Redo clears
the flag. The full history is always visible for audit. DELETE would lose the log.

### §2.2  commitOp() — The Single Write Function

```javascript
// kernel_ops.js — Implementing 2D_029 §2.2 — Witness: W-2D29
(function () {
  'use strict';

  var TABLE_DDL = '...';  // §2.1 DDL

  function ensureTable(db) {
    db.run(TABLE_DDL);
    // indexes...
  }

  /**
   * Commit an operation to the kernel_ops log.
   * @param {Object} db       - sql.js database
   * @param {string} opType   - GRID_MOVE | VIEW_FILTER | GRID_DETECT
   * @param {Object} params   - operation parameters (serialised as JSON)
   * @param {Array}  [inputGuids] - affected element GUIDs (optional)
   * @param {string} [outputGuid] - created/modified entity ID (optional)
   * @returns {number} op id
   */
  function commitOp(db, opType, params, inputGuids, outputGuid) {
    ensureTable(db);
    db.run(
      'INSERT INTO kernel_ops (timestamp, op_type, parameters, input_guids, output_guid) ' +
      'VALUES (?, ?, ?, ?, ?)',
      [Date.now(), opType, JSON.stringify(params),
       inputGuids ? JSON.stringify(inputGuids) : null,
       outputGuid || null]
    );
    var r = db.exec('SELECT last_insert_rowid()');
    var opId = r[0].values[0][0];
    console.log('§KERNEL_OP committed id=' + opId + ' type=' + opType +
                ' params=' + JSON.stringify(params));
    return opId;
  }

  /**
   * Undo: mark the most recent non-undone op as undone.
   * @returns {Object|null} the undone op's parameters, or null if nothing to undo
   */
  function undoOp(db) {
    ensureTable(db);
    var r = db.exec('SELECT id, op_type, parameters FROM kernel_ops ' +
                    'WHERE undone = 0 ORDER BY id DESC LIMIT 1');
    if (!r.length || !r[0].values.length) return null;
    var row = r[0].values[0];
    db.run('UPDATE kernel_ops SET undone = 1 WHERE id = ?', [row[0]]);
    console.log('§KERNEL_OP undo id=' + row[0] + ' type=' + row[1]);
    return { id: row[0], op_type: row[1], parameters: JSON.parse(row[2]) };
  }

  /**
   * Redo: clear undone flag on the most recent undone op.
   * @returns {Object|null} the redone op's parameters, or null if nothing to redo
   */
  function redoOp(db) {
    ensureTable(db);
    var r = db.exec('SELECT id, op_type, parameters FROM kernel_ops ' +
                    'WHERE undone = 1 ORDER BY id ASC LIMIT 1');
    if (!r.length || !r[0].values.length) return null;
    var row = r[0].values[0];
    db.run('UPDATE kernel_ops SET undone = 0 WHERE id = ?', [row[0]]);
    console.log('§KERNEL_OP redo id=' + row[0] + ' type=' + row[1]);
    return { id: row[0], op_type: row[1], parameters: JSON.parse(row[2]) };
  }

  /**
   * Replay all non-undone ops of a given type, in order.
   * Used on page reload to restore grid positions from the log.
   * @returns {Array} array of { id, op_type, parameters } objects
   */
  function replayOps(db, opType) {
    ensureTable(db);
    var r = db.exec(
      'SELECT id, op_type, parameters FROM kernel_ops ' +
      'WHERE undone = 0' + (opType ? ' AND op_type = ?' : '') +
      ' ORDER BY id',
      opType ? [opType] : []
    );
    if (!r.length) return [];
    var ops = r[0].values.map(function (row) {
      return { id: row[0], op_type: row[1], parameters: JSON.parse(row[2]) };
    });
    console.log('§KERNEL_OP replay type=' + (opType || 'ALL') + ' count=' + ops.length);
    return ops;
  }

  window.KernelOps = {
    commitOp: commitOp,
    undoOp:   undoOp,
    redoOp:   redoOp,
    replayOps: replayOps,
    ensureTable: ensureTable
  };
})();
```

### §2.3  Operation Types (initial vocabulary)

| op_type | parameters | When fired |
|---------|-----------|------------|
| `GRID_MOVE` | `{ axis, label, from, to }` | User drags a grid line (2D or 3D) |
| `VIEW_FILTER` | `{ mode, bandMin, bandMax, shown, hidden }` | Saved cut restores storey band visibility |
| `GRID_DETECT` | `{ xCount, yCount, method }` | Grid detection runs (informational, not undoable) |

`GRID_DETECT` is logged for audit only — it records that detection happened and what it
found. It is not undoable (no state to reverse). `GRID_MOVE` and `VIEW_FILTER` are the
undoable operations.

### §2.4  Crash Recovery — Replay on Reload

When the grid overlay initialises (`setupGridOverlay`), after detecting grids:

```javascript
// Implementing 2D_029 §2.4 — Witness: W-2D29
var savedMoves = KernelOps.replayOps(A.db, 'GRID_MOVE');
savedMoves.forEach(function (op) {
  var p = op.parameters;
  // Apply: find grid line by label, update position
  var lines = p.axis === 'X' ? gridData.xLines : gridData.yLines;
  var line = lines.find(function (l) { return l.label === p.label; });
  if (line) { line.position = p.to; line.rawPosition = p.to; }
});
if (savedMoves.length) {
  console.log('§KERNEL_OP grid positions restored from log, moves=' + savedMoves.length);
  // Rebuild scene with updated positions
}
```

Grid lines moved in a previous session reappear at their moved positions. No save button.
No localStorage hack. The DB is the truth.

### §2.5  Log Tags

```
§KERNEL_OP committed id=N type=GRID_MOVE params={...}
§KERNEL_OP undo id=N type=GRID_MOVE
§KERNEL_OP redo id=N type=GRID_MOVE
§KERNEL_OP replay type=GRID_MOVE count=N
§KERNEL_OP grid positions restored from log, moves=N
```

---

## §3  3D Grid Planes — Visible Grid Lines in the Live Scene

### The Concept

During **Adjust Grid** mode, the grid lines currently visible as 2D lines on the ground
plane are *also* rendered as semi-transparent 3D planes that slice through the building.
The building remains fully visible (no clipping). The planes are annotation — construction
lines floating in space.

### §3.1  PlaneGeometry Creation

For each grid line (X or Y), create a `THREE.Mesh` with `PlaneGeometry`:

```javascript
// Implementing 2D_029 §3.1 — Witness: W-2D29
function createGridPlane3D(axis, ifcPos, env, rules) {
  var p3d = (rules && rules.plane_3d) || {};
  var opacity = p3d.plane_opacity || 0.12;
  var colorX  = p3d.plane_color_x || '#ff4444';
  var colorY  = p3d.plane_color_y || '#4444ff';

  var color = axis === 'X' ? colorX : colorY;
  var height = env.zMax - env.zMin;          // building height in IFC metres
  var width  = axis === 'X'
    ? (env.yMax - env.yMin)                  // X-plane spans Y range
    : (env.xMax - env.xMin);                 // Y-plane spans X range

  // Convert to Three.js dimensions (IFC Z → Three.js Y, IFC Y → Three.js -Z)
  var threeHeight = height;
  var threeWidth  = width;

  var geo = new THREE.PlaneGeometry(threeWidth, threeHeight);
  var mat = new THREE.MeshBasicMaterial({
    color: new THREE.Color(color),
    transparent: true,
    opacity: opacity,
    side: THREE.DoubleSide,
    depthWrite: false
  });
  var mesh = new THREE.Mesh(geo, mat);

  // Position: convert IFC pos to Three.js world
  var midZ = (env.zMin + env.zMax) / 2;
  if (axis === 'X') {
    var p = APP.ifc2three(ifcPos, (env.yMin + env.yMax) / 2, midZ);
    mesh.position.set(p.x, p.y, p.z);
    mesh.rotation.y = Math.PI / 2;  // face along X axis
  } else {
    var p = APP.ifc2three((env.xMin + env.xMax) / 2, ifcPos, midZ);
    mesh.position.set(p.x, p.y, p.z);
    // Y-plane: default orientation faces camera when looking along Y
  }

  mesh.userData = { gridAxis: axis, gridPos: ifcPos, isGridPlane: true };
  mesh.renderOrder = -1;  // behind solid geometry
  return mesh;
}
```

### §3.2  Injection into Scene

```javascript
// Implementing 2D_029 §3.2 — Witness: W-2D29
function renderGridPlanesIn3D(APP, grids, env, rules) {
  // Remove previous 3D planes
  removeGridPlanes3D(APP);

  var group = new THREE.Group();
  group.name = 'gridPlanes3D';

  var count = 0;
  grids.xLines.forEach(function (line) {
    group.add(createGridPlane3D('X', line.position, env, rules));
    count++;
  });
  grids.yLines.forEach(function (line) {
    group.add(createGridPlane3D('Y', line.position, env, rules));
    count++;
  });

  APP.scene.add(group);
  APP.markDirty();
  console.log('§GRID_3D_PLANES count=' + count + ' mode=adjust');
  return group;
}

function removeGridPlanes3D(APP) {
  var old = APP.scene.getObjectByName('gridPlanes3D');
  if (old) {
    old.traverse(function (c) {
      if (c.geometry) c.geometry.dispose();
      if (c.material) c.material.dispose();
    });
    APP.scene.remove(old);
  }
}
```

### §3.3  Activation: When Are 3D Planes Shown?

3D grid planes appear when the user enters **Adjust Grid** mode (the grid drag long-press
activation). They disappear when the user exits Adjust Grid mode or toggles the grid overlay
off.

Integration point in `grid_drag.js`:

```javascript
// On drag activation (long-press fires):
if (window.GridOverlay && window.GridOverlay.renderGridPlanesIn3D) {
  window.GridOverlay.renderGridPlanesIn3D(APP, gridData, envCache, rules);
}

// On drag end / mode exit:
if (window.GridOverlay && window.GridOverlay.removeGridPlanes3D) {
  window.GridOverlay.removeGridPlanes3D(APP);
}
```

### §3.4  Log Tag

```
§GRID_3D_PLANES count=N mode=adjust
```

### §3.5  Rules Extension

Add to `grid_rules.json`:
```json
"plane_3d": {
  "plane_opacity":  0.12,
  "plane_color_x":  "#ff4444",
  "plane_color_y":  "#4444ff",
  "show_on_drag":   true
}
```

---

## §4  3D Grid Drag — Raycaster on Planes

### The Concept

When 3D planes are visible, the user can drag them directly in the 3D scene. The drag
uses the same constraint system as 2D drag (`GridDrag.clamp`, `GridDrag.snap`) but the
input comes from raycaster intersection on the plane mesh instead of 2D pointer offset.

### §4.1  Raycaster Plane Intersection

```javascript
// Implementing 2D_029 §4.1 — Witness: W-2D29
function dragPlane3D(event, APP, planeMesh) {
  var rect = APP.renderer.domElement.getBoundingClientRect();
  var mouse = new THREE.Vector2(
    ((event.clientX - rect.left) / rect.width) * 2 - 1,
    -((event.clientY - rect.top) / rect.height) * 2 + 1
  );
  var raycaster = new THREE.Raycaster();
  raycaster.setFromCamera(mouse, APP.camera);

  // Intersect the drag plane (perpendicular to the grid axis)
  var axis = planeMesh.userData.gridAxis;
  var planeNormal = axis === 'X'
    ? new THREE.Vector3(1, 0, 0)
    : new THREE.Vector3(0, 0, 1);  // Three.js: Y-axis grids run along Z
  var dragPlane = new THREE.Plane().setFromNormalAndCoplanarPoint(
    planeNormal, planeMesh.position
  );
  var intersection = new THREE.Vector3();
  raycaster.ray.intersectPlane(dragPlane, intersection);
  if (!intersection) return null;

  // Convert Three.js world point back to IFC coordinate on the grid axis
  // (inverse of APP.ifc2three)
  var ifcPos = axis === 'X'
    ? intersection.x + APP.modelOffset.x
    : -(intersection.z) + APP.modelOffset.y;  // Three.js -Z = IFC +Y

  return ifcPos;
}
```

### §4.2  Integration with GridDrag Constraint System

The 3D drag uses the same `clamp()` and `snap()` as 2D drag:

```javascript
// Implementing 2D_029 §4.2 — Witness: W-2D29
// Inside the pointermove handler when a 3D plane is being dragged:
var rawPos = dragPlane3D(event, APP, activePlaneMesh);
if (rawPos === null) return;

var axis = activePlaneMesh.userData.gridAxis;
var lines = axis === 'X' ? gridData.xLines : gridData.yLines;
var idx = lines.findIndex(function (l) { return l.label === activeLabel; });
var positions = lines.map(function (l) { return l.position; });
var moveRules = rules.grid_move || {};

var clamped = GridDrag.clamp(rawPos, idx, positions, axis, envCache, moveRules, startPos);
var snapped = GridDrag.snap(clamped, moveRules.snap_m || 0.05);

// Update plane position in Three.js
updatePlanePosition(activePlaneMesh, axis, snapped, APP);

// Update 2D grid line to match (keep 2D and 3D in sync)
lines[idx].position = snapped;
```

### §4.3  Commit on Drag End (pointerup)

```javascript
// Implementing 2D_029 §4.3 — Witness: W-2D29
// On pointerup after 3D drag:
var from = startPos;
var to   = lines[idx].position;
if (Math.abs(to - from) > 0.001) {
  // Commit to kernel_ops log
  KernelOps.commitOp(APP.db, 'GRID_MOVE', {
    axis:  axis,
    label: activeLabel,
    from:  from,
    to:    to
  });
  // Cascade elements (same as 2D drag)
  var cascade = GridDrag.cascadeElements(axis, idx, from, to, lines, APP.db, rules.clearance);
  // Rebuild 2D scene + dim chains
  rebuildGridScene();
  // Update cost panel
  if (window.CostPanel) window.CostPanel.refresh(APP, gridData);
}
```

### §4.4  Undo/Redo Keyboard Binding

```javascript
// Implementing 2D_029 §4.4 — Witness: W-2D29
document.addEventListener('keydown', function (e) {
  if (!APP.db) return;
  if (e.ctrlKey && e.key === 'z' && !e.shiftKey) {
    var op = KernelOps.undoOp(APP.db);
    if (op && op.op_type === 'GRID_MOVE') {
      applyGridMove(op.parameters.axis, op.parameters.label, op.parameters.from);
      rebuildGridScene();
      if (window.CostPanel) window.CostPanel.refresh(APP, gridData);
    }
  }
  if (e.ctrlKey && e.key === 'z' && e.shiftKey) {
    var op = KernelOps.redoOp(APP.db);
    if (op && op.op_type === 'GRID_MOVE') {
      applyGridMove(op.parameters.axis, op.parameters.label, op.parameters.to);
      rebuildGridScene();
      if (window.CostPanel) window.CostPanel.refresh(APP, gridData);
    }
  }
});
```

### §4.5  Log Tags

```
§GRID_3D_DRAG axis=X label=3 from=4.200 to=5.100
§GRID_3D_DRAG_END axis=X label=3 final=5.100 cascaded=N
```

---

## §5  Live Cost Panel — Spatial BOQ on Grid Drag

### The Concept

A floating panel that shows the BOQ (Bill of Quantities) for the spatial scope defined by
the current grid positions. When a grid line moves, the panel recomputes: the bay area
changes → material quantities change → cost updates.

**This is the killer feature from BIM_Modeller_OOTB.md:** *"drag a grid line in 3D, watch
the cost panel update in real time. The building is not a static object — it is a live query
against a cost model."*

### §5.1  SQL Query — Elements Within Grid Bays

```sql
-- Implementing 2D_029 §5.1 — Witness: W-2D29
-- For a selected bay (between two adjacent grid lines on each axis):
SELECT m.ifc_class,
       COUNT(*)                       AS qty,
       SUM(t.bbox_x * t.bbox_y)      AS area_m2,
       SUM(t.bbox_x * t.bbox_y * t.bbox_z) AS vol_m3
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE t.center_x BETWEEN :gridX1 AND :gridX2
  AND t.center_y BETWEEN :gridY1 AND :gridY2
GROUP BY m.ifc_class
ORDER BY vol_m3 DESC
```

When a rate template is loaded (from `rates/*.json`), multiply quantities by unit rates
to get cost. When no rate template is loaded, show quantities only.

### §5.2  Panel UI

```javascript
// cost_panel.js — Implementing 2D_029 §5.2 — Witness: W-2D29
(function () {
  'use strict';

  var panel = null;

  function createPanel() {
    panel = document.createElement('div');
    panel.id = 'costPanel';
    panel.style.cssText = 'position:fixed; bottom:60px; right:12px; width:280px; ' +
      'max-height:320px; overflow-y:auto; background:rgba(30,30,30,0.92); ' +
      'color:#eee; font:11px/1.4 monospace; padding:10px; border-radius:6px; ' +
      'pointer-events:auto; z-index:800; display:none;';
    document.body.appendChild(panel);
    return panel;
  }

  function refresh(APP, gridData) {
    if (!APP.db || !gridData) return;
    if (!panel) createPanel();

    // Compute bounding box from grid extremes
    var xs = gridData.xLines.map(function (l) { return l.position; });
    var ys = gridData.yLines.map(function (l) { return l.position; });
    var x1 = Math.min.apply(null, xs), x2 = Math.max.apply(null, xs);
    var y1 = Math.min.apply(null, ys), y2 = Math.max.apply(null, ys);

    var sql = 'SELECT m.ifc_class, COUNT(*) AS qty, ' +
      'ROUND(SUM(t.bbox_x * t.bbox_y), 2) AS area_m2, ' +
      'ROUND(SUM(t.bbox_x * t.bbox_y * t.bbox_z), 3) AS vol_m3 ' +
      'FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid ' +
      'WHERE t.center_x BETWEEN ? AND ? AND t.center_y BETWEEN ? AND ? ' +
      'GROUP BY m.ifc_class ORDER BY vol_m3 DESC';

    var r = APP.db.exec(sql, [x1, x2, y1, y2]);
    if (!r.length) { panel.innerHTML = '<i>No elements in grid scope</i>'; panel.style.display = 'block'; return; }

    var totalQty = 0, totalArea = 0, totalVol = 0;
    var html = '<b>Bay Scope</b> X[' + x1.toFixed(1) + '–' + x2.toFixed(1) +
               '] Y[' + y1.toFixed(1) + '–' + y2.toFixed(1) + ']<br><br>';
    html += '<table style="width:100%;border-collapse:collapse">' +
            '<tr><th style="text-align:left">Class</th><th>Qty</th><th>Area m²</th><th>Vol m³</th></tr>';
    r[0].values.forEach(function (row) {
      html += '<tr><td>' + row[0] + '</td><td style="text-align:right">' + row[1] +
              '</td><td style="text-align:right">' + row[2] +
              '</td><td style="text-align:right">' + row[3] + '</td></tr>';
      totalQty += row[1]; totalArea += row[2]; totalVol += row[3];
    });
    html += '<tr style="border-top:1px solid #666"><td><b>Total</b></td>' +
            '<td style="text-align:right"><b>' + totalQty + '</b></td>' +
            '<td style="text-align:right"><b>' + totalArea.toFixed(2) + '</b></td>' +
            '<td style="text-align:right"><b>' + totalVol.toFixed(3) + '</b></td></tr>';
    html += '</table>';

    console.log('§GRID_3D_BOQ elements=' + totalQty + ' area=' + totalArea.toFixed(2) +
                ' vol=' + totalVol.toFixed(3));

    panel.innerHTML = html;
    panel.style.display = 'block';
  }

  function hide() {
    if (panel) panel.style.display = 'none';
  }

  window.CostPanel = { refresh: refresh, hide: hide };
})();
```

### §5.3  Log Tag

```
§GRID_3D_BOQ elements=N area=A vol=V
```

---

## §6  Storey Band Visibility Filter on 3D Scene

### The Concept

When a saved section view is restored (e.g., "GF Plan"), the storey band filter already
removes roof/foundation noise from the 2D contours (2D_028 §2). The 3D counterpart:
elements outside `[bandMin, bandMax]` get `mesh.visible = false` in the Three.js scene.

This is NOT the scissors cut (which uses a clipping plane). It is a visibility filter.
Roofs, upper floors, foundations disappear from view. What remains is a "see-through
floor plate" — the GF plan in context of the 3D building.

### §6.1  Implementation

```javascript
// Implementing 2D_029 §6.1 — Witness: W-2D29
function applyStoreyBandVisibility(APP, bandMin, bandMax) {
  var shown = 0, hidden = 0;
  APP.scene.traverse(function (obj) {
    if (!obj.isMesh || !obj.userData.guid) return;  // skip non-element meshes
    var cz = obj.userData.center_z;
    if (cz === undefined) return;
    if (cz >= bandMin && cz <= bandMax) {
      obj.visible = true;
      shown++;
    } else {
      obj.visible = false;
      hidden++;
    }
  });
  APP.markDirty();
  console.log('§GRID_3D_BAND_VIS bandMin=' + bandMin.toFixed(2) +
              ' bandMax=' + bandMax.toFixed(2) + ' shown=' + shown + ' hidden=' + hidden);

  // Log as kernel_op for persistence
  if (window.KernelOps) {
    KernelOps.commitOp(APP.db, 'VIEW_FILTER', {
      mode: 'storey_band',
      bandMin: bandMin,
      bandMax: bandMax,
      shown: shown,
      hidden: hidden
    });
  }
}

function clearStoreyBandVisibility(APP) {
  APP.scene.traverse(function (obj) {
    if (obj.isMesh && obj.userData.guid) obj.visible = true;
  });
  APP.markDirty();
  console.log('§GRID_3D_BAND_VIS cleared — all elements visible');
}
```

### §6.2  Integration: Saved Section Restore

When the user clicks a saved section in the panel, after the 2D section cut is applied:

```javascript
// In grid_overlay.js, restoreSavedSection():
if (window.applyStoreyBandVisibility && section.bandMin !== undefined) {
  applyStoreyBandVisibility(APP, section.bandMin, section.bandMax);
}
```

### §6.3  Element center_z on userData

The visibility filter needs `center_z` on each mesh's userData. This is set at import time
in `streaming.js` or `import_worker.js`. If not already present, add during scene traversal:

```javascript
// Fallback: query DB for center_z if not on userData
if (obj.userData.guid && obj.userData.center_z === undefined) {
  var r = APP.db.exec('SELECT center_z FROM element_transforms WHERE guid = ?',
                       [obj.userData.guid]);
  if (r.length && r[0].values.length) obj.userData.center_z = r[0].values[0][0];
}
```

This is a one-time cost at filter activation, not per-frame.

### §6.4  Log Tag

```
§GRID_3D_BAND_VIS bandMin=B bandMax=B shown=N hidden=N
§GRID_3D_BAND_VIS cleared — all elements visible
```

---

## §7  Script Loading Order

Add to `deploy/dev/index.html`, after `grid_drag.js` and before `grid_overlay.js`:

```html
<script src="kernel_ops.js"></script>
<script src="cost_panel.js"></script>
```

Order rationale:
- `kernel_ops.js` must load before `grid_drag.js` uses `KernelOps.commitOp()`
- `cost_panel.js` must load before `grid_overlay.js` calls `CostPanel.refresh()`
- Both are self-contained IIFEs with no import dependencies

**Revised load order for grid modules:**
```
grid_dims → grid_config → grid_views → grid_door_arcs → section_cut → elevation →
grid_contours → grid_dim_chains → kernel_ops → cost_panel → grid_drag →
grid_scissors → grid_overlay
```

---

## §8  Rules Extension Summary

All new keys added to `grid_rules.json` by this prompt:

```json
{
  "floor_plan": {
    "opening_label_offset_m": 0.15,
    "opening_label_font_px":  9
  },
  "plane_3d": {
    "plane_opacity":  0.12,
    "plane_color_x":  "#ff4444",
    "plane_color_y":  "#4444ff",
    "show_on_drag":   true
  }
}
```

These are additive — existing keys in `grid_rules.json` are unchanged.

---

## §9  Witness Table

| ID | Claim | Evidence |
|----|-------|----------|
| W-2D29-01 | `kernel_ops` table created on first commitOp | `§KERNEL_OP committed id=1` in console |
| W-2D29-02 | GRID_MOVE persists across page reload | Reload page → `§KERNEL_OP replay type=GRID_MOVE count=N` → grid at moved position |
| W-2D29-03 | Ctrl+Z undoes last GRID_MOVE | `§KERNEL_OP undo id=N` → grid returns to previous position |
| W-2D29-04 | Ctrl+Shift+Z redoes undone GRID_MOVE | `§KERNEL_OP redo id=N` → grid returns to moved position |
| W-2D29-05 | Opening labels show width in mm | `§DOOR_ARC_LABEL guid=G width=900mm tag=Single-Flush` |
| W-2D29-06 | 3D planes appear on grid drag activation | `§GRID_3D_PLANES count=N mode=adjust` |
| W-2D29-07 | 3D plane drag updates grid position | `§GRID_3D_DRAG axis=X label=3 from=F to=T` |
| W-2D29-08 | Cost panel shows element counts + volumes | `§GRID_3D_BOQ elements=N area=A vol=V` |
| W-2D29-09 | Storey band visibility hides out-of-band elements | `§GRID_3D_BAND_VIS shown=N hidden=N` |
| W-2D29-10 | `grid_rules.json` has `plane_3d` block | JSON parse succeeds, keys present |
| W-2D29-11 | `grid_rules.json` has `opening_label_offset_m` | JSON parse, `floor_plan.opening_label_offset_m` exists |
| W-2D29-12 | `kernel_ops.js` loaded by index.html | `typeof window.KernelOps !== 'undefined'` |
| W-2D29-13 | `cost_panel.js` loaded by index.html | `typeof window.CostPanel !== 'undefined'` |

---

## §10  Test Spec — 29-3d-grid-kernel.spec.js

Wiring tests (source-read, not browser). Follow 2D_028 pattern.

```javascript
// 29-3d-grid-kernel.spec.js — 2D_029: 3D grid planes + kernel_ops + opening labels
// Issues proven:
//   T_2029_01: kernel_ops.js exposes window.KernelOps — §2.2 commitOp available
//   T_2029_02: kernel_ops.js has commitOp function — §2.2 write path present
//   T_2029_03: kernel_ops.js has undoOp function — §4.4 undo available
//   T_2029_04: kernel_ops.js has redoOp function — §4.4 redo available
//   T_2029_05: kernel_ops.js has replayOps function — §2.4 crash recovery path
//   T_2029_06: kernel_ops.js has §KERNEL_OP log tag — §2.5 operations observable
//   T_2029_07: kernel_ops.js has CREATE TABLE kernel_ops DDL — §2.1 schema present
//   T_2029_08: grid_overlay.js has renderGridPlanesIn3D — §3.2 3D planes function present
//   T_2029_09: grid_overlay.js has removeGridPlanes3D — §3.2 cleanup function present
//   T_2029_10: grid_overlay.js has §GRID_3D_PLANES log tag — §3.4 3D planes observable
//   T_2029_11: grid_drag.js has §GRID_3D_DRAG log tag — §4.5 3D drag observable
//   T_2029_12: grid_drag.js calls KernelOps.commitOp — §4.3 drag commits to log
//   T_2029_13: grid_door_arcs.js has addOpeningLabel — §1.3 label function present
//   T_2029_14: grid_door_arcs.js has §DOOR_ARC_LABEL log tag — §1.4 labels observable
//   T_2029_15: cost_panel.js exposes window.CostPanel — §5.2 cost panel available
//   T_2029_16: cost_panel.js has §GRID_3D_BOQ log tag — §5.3 cost query observable
//   T_2029_17: grid_rules.json has plane_3d block — §8 3D plane config externalised
//   T_2029_18: grid_rules.json has opening_label_offset_m — §8 label config externalised
//   T_2029_19: grid_overlay.js has §GRID_3D_BAND_VIS log tag — §6.4 band visibility observable
//   T_2029_20: grid_overlay.js calls KernelOps.replayOps — §2.4 replay wired on init
```

---

## §11  Implementation Order

1. **§2 kernel_ops.js** — foundational, no dependencies, everything else uses it
2. **§8 grid_rules.json** — add `plane_3d` and `opening_label` keys
3. **§1 grid_door_arcs.js** — opening labels (independent of 3D work)
4. **§7 index.html** — add script tags
5. **§3 grid_overlay.js** — `renderGridPlanesIn3D()` + `removeGridPlanes3D()`
6. **§6 grid_overlay.js** — storey band visibility filter
7. **§5 cost_panel.js** — live BOQ panel
8. **§4 grid_drag.js** — 3D drag + commitOp integration + undo/redo
9. **§2.4 grid_overlay.js** — replay on init (depends on §4 being committed)
10. **§10 tests** — wiring spec

---

## §12  What This Prompt Does NOT Do

These are explicitly deferred (per DeepSeek's transition path):

- **Replay engine / time travel UI** — replay is internal (crash recovery), not user-facing
- **Branching / merging / CRDT** — single-writer only for now
- **Log compression / snapshots** — not needed until 100K+ ops
- **Migrate existing tables to log-derived** — `element_transforms` stays direct-write
- **Geometry evaluation (Extrude, Boolean)** — Proof 2+, not this prompt
- **Manifold WASM integration** — future prompt
- **IFC re-export from kernel_ops** — future prompt

The scope is: **grid drag is the first kernel_op consumer. Everything else continues unchanged.**

---

## §13  Session Closeout Checklist

- [ ] `§KERNEL_OP committed id=1 type=GRID_MOVE` in browser console after grid drag
- [ ] Reload page → `§KERNEL_OP replay type=GRID_MOVE count=N` → grid at moved position
- [ ] Ctrl+Z → `§KERNEL_OP undo` → grid reverts
- [ ] `§DOOR_ARC_LABEL` tags in console for buildings with doors/windows
- [ ] `§GRID_3D_PLANES count=N` appears on drag activation
- [ ] `§GRID_3D_BOQ elements=N` appears in cost panel
- [ ] `§GRID_3D_BAND_VIS` appears when restoring saved GF section
- [ ] `node deploy/dev/tests/audit_specs.js` exits 0
- [ ] All 20 wiring tests in `29-3d-grid-kernel.spec.js` pass
- [ ] Update PROGRESS.md

---

*Spec: 2D_029. Implements BIM_Modeller_OOTB.md Proof 1 (reparametrizable operation via kernel_ops log).*
*Pre-requisite: 2D_028 (53c0cffa) — opportunity-vote grids + storey band filter.*
