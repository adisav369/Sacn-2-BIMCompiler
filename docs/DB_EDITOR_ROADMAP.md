# DB Editor Roadmap — Edit the Database, Regenerate the Building

> **The .blend is disposable. The DB is the source of truth.**
> Edit → re-bake → view. The building is always one query away.

Status: DESIGN (S186 session 2 discussion, 2026-04-14)

---

## Principle

Every BIM authoring tool edits geometry and derives data.
This system edits data and derives geometry.

The extraction pipeline produces `elements_meta`, `element_transforms`,
`element_instances`. The bake script produces a `.blend` in 36-123 seconds.
The viewer links it as collection instances — 5 scene entries, not 63K objects.

If the DB changes, re-bake. The `.blend` is always regenerable.

---

## Part 1 — Delta Bake

### Problem
Full re-bake of Hospital (63K) takes 123s. Moving one wall shouldn't cost 123s.

### Solution
Track which guids changed (timestamp column or changelog table in DB).
Bake only the changed elements into a **patch .blend**:

```
Baked_T0_Hospital/
  ├── T0_Hospital_ARC_inst   (original, 10K meshes)
  ├── T0_Hospital_STR_inst   (original, 12K meshes)
  ├── T0_Hospital_MEP_inst   (original, 38K meshes)
  └── T0_Hospital_PATCH_001_inst  (delta: 12 changed walls, 2-3s bake)
```

The patch instance overlays the originals. Changed elements in the original
are hidden by guid lookup. Net visual = correct building with minimal bake cost.

### MEP Delta
RouteWalker changes 200 pipe segments → delta bake those 200 → swap
`T0_Hospital_MEP_inst` with a new version. Other discipline instances untouched.
Cost: ~3s for 200 elements. Full MEP re-bake (38K): ~18s. Either way, fast.

### Implementation
- Add `last_modified` column to `elements_meta` (set by edit scripts)
- `bake_building_blend.py --delta --since <timestamp>` mode
- Patch .blend linked as additional collection instance
- Full re-bake remains available as "clean rebuild"

---

## Part 2 — 2D Layout DXF Feedback Loop

### Flow
```
2D Layout (DXF/Python)           DB                          Viewer
  move grid line ──────────────→ UPDATE wall x/y ──────────→ delta bake ~3s
  change ceiling height ───────→ UPDATE storey z ──────────→ delta bake ~3s
  adjust room spacing ────────→ UPDATE transforms ─────────→ delta bake ~3s
```

### Live DB Watch
File watcher on `*_extracted.db`. When the 2D Layout script writes changes:
- Viewer detects mtime change
- SHORT-CUT style button: "Building changed — re-bake? ~3s"
- User clicks → background bake → building updates in viewport

### Grid-Governed Geometry
Walls, columns, floors are positioned by grid intersections.
Moving a grid in the 2D DXF layout → all governed elements recompute positions
→ DB update → delta bake → 3D model reflects 2D change.

---

## Part 3 — BIM Script Editor

### Concept
`.bimcobol` scripts and Python scripts edit the DB programmatically:
- Swap device types (UPDATE element_instances SET geometry_hash = ?)
- Change materials (UPDATE elements_meta SET material_rgba = ?)
- RouteWalker rule changes (recalculate MEP routing, INSERT new segments)
- Quantity adjustments (UPDATE BOM quantities)

### REPL Workflow
```
Write script → Execute (DB transaction) → Background bake → See result
    ↑                                                           │
    └───── adjust script ←──────────────────────────────────────┘
```

Like a Jupyter notebook for buildings. Each cell is a DB mutation.
Each execution produces a visual result in seconds.

---

## Part 4 — Diff Viewer + UNDO/REDO

### Diff Panel (collapsible)
Before/after comparison from DB changelog:
```
[▼ CHANGES — 15 elements]
  IfcWall × 3    moved (grid A3 → A4)
  IfcDoor × 1    swapped (single → double)
  IfcPipe × 11   re-routed (RouteWalker v2)
[UNDO] [REDO] [ACCEPT]
```

### Implementation
- Each edit script writes to `edit_changelog` table: guid, field, old_value, new_value, timestamp
- UNDO = restore old_value for last batch
- REDO = re-apply new_value
- ACCEPT = clear changelog, mark as committed
- All collapsible in N-panel (BoolProperty toggle)

---

## Part 5 — Collapsible Panel Sections

### Current pain: panel too long, obscures PAUSE/CANCEL

Add `BoolProperty` toggles for each section in `BIMFederationProperties`:

```python
rtree_show_elements: BoolProperty(default=True)     # Top 10 element list
rtree_show_disc_bars: BoolProperty(default=True)     # Discipline bar graph
rtree_show_loaded: BoolProperty(default=False)       # Loaded collections inventory
rtree_show_diff: BoolProperty(default=False)         # Diff/changelog panel (future)
```

UI pattern:
```
[▼ ELEMENTS — top 10]               ← click to collapse
  IfcWall · Basic Wall · Level 01
  IfcDoor · Single Panel · Level 01
  ...

[▶ LOADED COLLECTIONS]              ← collapsed by default
```

Overnight progress + SHORT-CUT button always visible (never collapsible).

---

## Part 6 — Top 10 as Edit Entry Point

### Current: read-only preview list
### Target: lightweight DB inspector + edit palette

Each element row in the top 10 becomes expandable:

```
[▼ IfcWall · Basic Wall · Level 01]
    GUID: 2Oq...  [copy]
    Material: Concrete,0.7,0.7,0.7,1.0
    Geometry: a3f8c2... (library mesh)
    Position: (45.2, 12.8, 3.0)
    [SWAP MESH] [CHANGE MATERIAL] [MOVE]
```

- SWAP MESH: pick from library.blend mesh list → UPDATE element_instances
- CHANGE MATERIAL: colour picker → UPDATE elements_meta.material_rgba
- MOVE: numeric input → UPDATE element_transforms
- Each edit writes to DB + triggers delta bake for that element

The top 10 represents the most common types in the current scope.
Editing one IfcWall type can propagate to all walls of that type (opt-in).

---

## Part 7 — City-Level Edit Propagation

Change Hospital MEP routing:
1. RouteWalker script runs → DB updated (200 pipes)
2. Delta bake Hospital MEP → 3s
3. Swap `T0_Hospital_MEP_inst` in city.blend
4. Other buildings untouched
5. City reflects change immediately

No need to re-bake the whole city. Each building is an independent
collection instance. Edit one → re-bake one → city auto-updates.

---

## Architecture Summary

```
Source of Truth          Derived (disposable)        View (instant)
─────────────           ────────────────            ──────────────
elements_meta        →  baked/{bld}.blend       →  collection instances
element_transforms   →  (36-123s full bake)     →  (5 scene entries)
element_instances    →  (2-3s delta bake)        →  (<1s swap)
edit_changelog       →  patch .blend             →  overlay instance
```

The DB is always right. The .blend is always regenerable.
The viewer is always fast.
