# ⚠ DO NOT REMOVE — S267 BOM Walker in Browser
# Scope: Port Java BOM tree + verb expansion to JS. Read the log after every run.

## Goal
Port the Java BOMWalker + verb expanders to JS so the browser can recompose
element positions at runtime when the user drags grid lines. No Java install needed.
Two data sources: BOM.db (OOTB fleet) and web-ifc bom_tree (IFC Drop).

## Why (from S266c live testing, 2026-05-22)
- Auto-grid heuristics failed: 215 walls flood → user-initiated grid works but
  Next still replays original positions, not grid-adjusted positions
- Stage 2 (recomposition) requires verb expansion: TILE recalculates tile count
  for new bay width, CLUSTER shifts positions, FRAME recomputes grid intersections
- Java verb expanders are pure math (~200 lines) — proven portable (route_walker.js)
- BOM.db already exists for the fleet (504KB HITOS, 8MB Terminal)

## Architecture Decision: 100% Browser

Java stays as the offline pipeline (fleet extraction, IFC export, validation gates).
The browser handles everything the end user touches:

| Stage | What | How |
|-------|------|-----|
| Stage 1: Extract | IFC → elements + BOM tree | web-ifc WASM (IFC Drop) or pre-computed (OOTB) |
| Stage 2: Recompose | Grid drag → new positions | JS BOMWalker + expandVerb on BOM.db / bom_tree |
| Stage 3: Export | New building → IFC file | Desktop Pro only (Java Bridge, optional install) |

**End user experience:** Drop IFC → press Red Pill → drag grids → see elements
reposition → take screenshot for print. All in browser. No install. No server.

## Data Sources

### A. OOTB Fleet — pre-computed BOM.db (lazy fetch on Red Pill)

Already exists in `library/`:
```
HI_BOM.db  504KB  — BUILDING→6 FLOOR→447 elements, CLUSTER/TILE/FRAME verb_ref
TE_BOM.db  8MB    — largest building (48K elements)
BR_BOM.db  108KB  — smallest
```

Tables: `m_bom`, `m_bom_line`, `M_Product`, `M_Product_Category`, `ad_sysconfig`

Key columns in `m_bom_line`:
- `bom_id` → parent BOM
- `child_product_id` → child (recurse if matches another bom_id)
- `component_type` → MAKE (sub-assembly) | LEAF (geometry) | PHANTOM (skip)
- `verb_ref` → placement recipe: TILE:nx:ny:stepX:stepY, ROUTE:legs, FRAME:grids, CLUSTER:positions
- `role` → IFC class (IfcColumn, IfcDoor, etc.)
- `allocated_width_mm`, `allocated_depth_mm`, `allocated_height_mm` → element dimensions

Upload to OCI bucket `bim-ootb/buildings/{PREFIX}_BOM.db`. Fetch on Red Pill press,
cache in IndexedDB alongside extracted DB.

### B. IFC Drop — extract bom_tree from web-ifc at import time

`import_worker.js` already uses `ifcApi.GetLineIDsWithType()` and already reads
`IFCRELCONTAINEDINSPATIALSTRUCTURE` (line 234). Add 3 more relationship queries:

```javascript
// ~30 lines in import_worker.js
var voidRels = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELVOIDSELEMENT);
// → wall_guid → opening_guid (parent hosts child)

var fillRels = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELFILLSELEMENT);
// → opening_guid → door/window_guid

var aggRels  = ifcApi.GetLineIDsWithType(modelID, WebIFC.IFCRELAGGREGATES);
// → assembly hierarchy
```

Write to `bom_tree` table in the extracted DB (same SQL schema as §17.9A):
```sql
CREATE TABLE bom_tree (
  parent_guid TEXT NOT NULL,
  child_guid  TEXT NOT NULL,
  rel_type    TEXT NOT NULL,
  PRIMARY KEY (parent_guid, child_guid)
);
```

For IFC Drop, there's no verb_ref — positions come from element_transforms directly.
Grid drag applies delta to original positions. Verb expansion is OOTB-only (needs BOM.db).

## What to Port: Java → JS

### 1. bom_walker.js — BOMWalker tree traversal (~100 lines JS)

Source: `DAGCompiler/.../bom/walker/BOMWalker.java` (251 lines)

Core logic:
```
walk(rootBomId):
  bom = SELECT * FROM m_bom WHERE bom_id = rootBomId
  lines = SELECT * FROM m_bom_line WHERE bom_id = rootBomId
  for each line:
    childBom = SELECT * FROM m_bom WHERE bom_id = line.child_product_id
    if childBom exists → recurse (sub-assembly)
    else if PHANTOM → skip
    else → leaf: expand verb_ref, compute position, emit element
```

JS version reads from sql.js (BOM.db already loaded). Visitor pattern → callback.

### 2. verb_expand.js — Verb expanders (~200 lines JS)

Source: `PlacementCollectorVisitor.java` lines 1470-1630

Port these pure-math functions:

| Function | Input | Output | Lines |
|----------|-------|--------|-------|
| `expandTile(verbRef, ox, oy, oz)` | `TILE:nx:ny:stepX:stepY` | `[[dx,dy,dz], ...]` 2D grid | 15 |
| `expandRoute(verbRef, ox, oy, oz)` | `ROUTE:X:step:n\|Y:step:n` | axis-aligned legs | 20 |
| `expandFrame(verbRef, oz)` | `FRAME:x1,x2\|y1,y2` | cartesian grid product | 15 |
| `expandCluster(verbRef, ox, oy, oz)` | `CLUSTER:x,y,z,w,d,h;...` | literal position table | 15 |
| `expandSpray(verbRef, qty, ox, oy, oz)` | `SPRAY:stepX:stepY` | semi-regular grid | 15 |

Each returns `double[][]` (array of [dx, dy, dz] offsets). Zero framework dependency.

### 3. Grid delta application — recomposition on drag

When user moves grid line C from 12m to 15m (delta +3m):

```
For each m_bom_line with FRAME verb:
  if FRAME x-positions include 12.0 → rewrite to 15.0
  re-expand → new element positions at grid intersections

For each m_bom_line with CLUSTER verb:
  entries near x=12 → shift by +3m delta
  re-expand → repositioned elements

For each m_bom_line with TILE verb:
  if bay width changed → recalculate nx from new width / stepX
  re-expand → new tile count at new positions
```

Elements materialize at new coordinates using existing geometry (meshes reposition,
don't regenerate). This is visual recomposition — same meshes, new transforms.

## Integration with doc_canvas.js

Current flow (S266c):
```
Next → _materializePhase(phase) → show elements at ORIGINAL positions
```

New flow (S267):
```
Next → _recomposePhase(A, phase):
  1. Read m_bom_line entries for this phase's elements
  2. For each entry with verb_ref:
     - Apply grid deltas to verb parameters
     - expandVerb() → new positions
  3. Set element transforms to new positions
  4. Show elements
```

For IFC Drop (no BOM.db, no verb_ref):
```
Next → _materializeWithDelta(phase, gridDeltas):
  1. For each element, find nearest grid line
  2. Apply that grid line's delta to element's original position
  3. Show at adjusted position
```

## Files to Create/Modify

| File | Action | Lines |
|------|--------|-------|
| `deploy/dev/bom_walker.js` | **NEW** — JS port of BOMWalker | ~100 |
| `deploy/dev/verb_expand.js` | **NEW** — JS port of expandVerb | ~200 |
| `deploy/dev/import_worker.js` | **EDIT** — add IfcRelVoids/Fills/Aggregates queries | ~30 |
| `deploy/dev/doc_canvas.js` | **EDIT** — wire recomposePhase, grid delta application | ~100 |
| `deploy/dev/bom_extract.js` | **EDIT** — query bom_tree for parent-child when available | ~50 |
| `deploy/dev/scene.js` or `main.js` | **EDIT** — lazy fetch BOM.db on Red Pill, cache IndexedDB | ~40 |
| `deploy/dev/index.html` | **EDIT** — add script tags for bom_walker.js, verb_expand.js | 2 |
| `deploy/dev/tests/test_verb_expand.js` | **NEW** — test each verb against Java output | ~150 |
| `deploy/dev/tests/test_bom_walker.js` | **NEW** — test tree traversal on real HI_BOM.db | ~100 |

## Verification

### Verb expansion tests (test_verb_expand.js)
- TILE:3:4:2.0:1.5 → 12 positions, match Java output
- ROUTE:X:1.2:5|Y:0.8:3 → 8 positions, match Java output
- FRAME:0,5.4,10.8|0,4.8 → 6 positions (3×2 grid)
- CLUSTER from real HI_BOM.db line → compare with Java PlacementCollectorVisitor
- Edge: empty verb_ref → single position at origin
- Edge: PHANTOM → skip (no output)

### BOM walker tests (test_bom_walker.js)
- Load HI_BOM.db via sql.js
- Walk from BUILDING root → expect 7 nodes (1 BUILDING + 6 FLOOR)
- Walk 1.etg FLOOR → expect 97 leaf elements
- Verify LEAF count matches m_bom_line COUNT
- Verify no infinite recursion (MAX_DEPTH guard)

### Integration test
- Load HITOS in browser, press Red Pill
- Step through phases → elements appear at original positions (baseline)
- Drag grid line → press Next → elements at NEW positions
- §-tagged logs prove: `§RECOMPOSE verb=FRAME old_x=12.0 new_x=15.0 delta=+3.0`

## Session Startup
1. Read this prompt
2. Read `docs/NEW_FROM_REFERENCE.md` §17.9 (triage context)
3. Read Java sources:
   - `DAGCompiler/.../bom/walker/BOMWalker.java` (251 lines — tree traversal)
   - `DAGCompiler/.../bom/walker/PlacementCollectorVisitor.java` lines 1470-1630 (verb expansion)
   - `DAGCompiler/.../bom/walker/BOMVisitor.java` (visitor interface)
4. Read `deploy/dev/import_worker.js` lines 230-247 (existing IfcRel query pattern)
5. Read `deploy/dev/route_walker.js` (precedent for Java→JS port)
6. Query real BOM.db: `sqlite3 library/HI_BOM.db "SELECT verb_ref, role FROM m_bom_line LIMIT 10"`

## Out of Scope (S268+)
- NEW geometry generation (wall polygons from boundary lines) → Java Bridge / Desktop Pro
- IFC export → Java Bridge / Desktop Pro
- Validation gates (9 Rosetta Stone gates) → Java Bridge / Desktop Pro
- Max + Photo icons for print-ready mode
- Diagonal grid lines / rotation
- GPU throttling
- Save/recall design sessions
