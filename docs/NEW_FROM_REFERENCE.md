# New From Reference — Deterministic Design From Grammar Extraction

> **Related:** [BIM Modeller OOTB](BIM_Modeller_OOTB.md) (kernel-op theory) · [2D Layout](2D_LAYOUT.md) (grid overlay) · [Kernel Ops Roadmap](../prompts/KERNEL_OPS_ROADMAP.md) (op log) · [Spatial Compilation Paper](SPATIAL_COMPILATION_PAPER.md) (round-trip proof) · [BOM Compilation](BOMBasedCompilation.md) (recipe model)

<div class="bim-banner" markdown>
<b>You never start from a blank canvas.</b> You start from a building you trust, extract its grammar, and design by interrupting its replay.
</div>

---

## 0. How We Got Here

The BIM Compiler began as a Java pipeline: IFC files in, SQLite databases out. Over 21 buildings and 9 verification gates, it proved that any IFC building can be decomposed into a BOM recipe (Bill of Materials — parent/child assemblies with quantities and spatial offsets), compiled back into placed elements, and verified to match the original via Rosetta Stone gates.

That was the inverse path: **real building → grammar → verified reconstruction**.

The forward path — **grammar → new design** — was always the goal but required several layers to be built first:

- **BOM-Based Compilation** (Java) — the recipe model. Buildings as hierarchical assemblies: building → storey → discipline → element. Each level carries spatial offsets (dx/dy/dz) relative to its parent. The BOMWalker traverses, verb formulas (TILE, ROUTE, FRAME) expand type lines into placed instances. Rosetta Stone gates verify the round-trip.

- **Browser Viewer** (S200+) — the delivery platform. Single HTML + SQLite + sql.js WASM + Three.js. No server. 30 buildings streaming from OCI object storage into IndexedDB. BatchedMesh rendering, DLOD culling, 4D Time Machine playback.

- **2D Grid Overlay** (S240+) — structural grid detection from element positions. AABBCC grid lines, bubble labels, bay dimensions, drag-to-adjust with kernel_ops logging. This proved that grid manipulation works in the browser.

- **Kernel Ops** (S250+) — the event log. Every user action recorded as a JSON command. Undo/redo by replay. The infrastructure for deterministic, reproducible design sessions.

- **Clash Detection + UBBL** — rule-based spatial validation. Proximity checks, clearance rules, clash highlighting. The validation layer that confirms a design change is compliant.

Each layer was built independently and verified in isolation. "New From Reference" wires them together into a single workflow.

**Technology stack:** Plain JavaScript (no frameworks, no build tools), sql.js (SQLite in WASM), Three.js r160 (ESM, BatchedMesh), IndexedDB for persistence, Service Worker for offline. The entire designer runs in a browser tab with zero server dependency. The Java compiler's BOM extraction is now ported to JS — one function, one query, one pass — producing the same grammar that took 13 Java classes to build, because the browser only needs one building at a time.

---

## 1. The Problem

Every BIM modeller starts from either a blank canvas (Revit template) or a parametric script (Grasshopper definition). Both require the designer to specify every dimension from scratch. Neither reuses the spatial intelligence already embedded in real buildings.

The BIM Compiler already solves the inverse problem: IFC → SQLite → verified reconstruction. The grammar is already extracted — BOM abstract sets, bay ratios, floor-to-floor heights, MEP densities. What is missing is the forward path: grammar → new design.

**"New" is that forward path.**

---

## 2. The Claim

A new building can be designed by:

1. Loading a reference building (already in IndexedDB from prior extraction)
2. Extracting its spatial grammar (BOM abstract sets, bay proportions, storey heights)
3. Generating a 2D structural grid seeded from that grammar
4. Replaying the reference building's construction sequence (4D Gantt)
5. Interrupting the replay to drag grid lines and alter proportions
6. Materializing the result as `NewIFC.db`

**The original `IFC.db` never changes.** The design is an event log applied to the grammar. `NewIFC.db` is a materialized view — deletable and regenerable at any time.

---

## 2b. How Others Do It — And Why This Is Different

| Platform | Approach | Starting Point | Constraint System | Server Dependency | File Size | Round-Trip Fidelity |
|---|---|---|---|---|---|---|
| **Revit** (Autodesk) | Parametric families. Each element is a constraint-bound parametric object. Design by placing families and setting parameters. | Blank template or company template | Full constraint solver — dimensional, geometric, relational. Edits propagate through constraint graph. | Standalone desktop (60GB install). Cloud for collaboration (BIM 360/ACC). | `.rvt` binary, typically 50–500MB | One-way: Revit → IFC export. IFC → Revit import loses parametric data. No verified round-trip. |
| **ArchiCAD** (Graphisoft) | Object-based with GDL scripting. Elements are parametric GDL objects placed in a virtual building model. | Template with pre-configured GDL libraries | GDL parameter constraints + hotlinked modules. Less rigid than Revit — more manual coordination. | Desktop (15GB install). BIMcloud for teamwork. | `.pln` binary, 20–200MB | IFC export/import with known data loss on complex geometry. |
| **Grasshopper/Rhino** (McNeel) | Visual scripting. Design as a node graph of parametric operations. Every dimension is a slider or formula. | Empty canvas. User builds from zero. | No built-in constraints — the script IS the constraint. User must encode all rules as nodes. Powerful but requires programmer-designer. | Desktop (2GB install). No server needed. | `.gh` + `.3dm`, typically 1–50MB | No IFC native. Plugin-based export. No round-trip. |
| **IFC.js / That Open Company** | IFC fragment viewer + editor. Loads IFC geometry, allows selection/modification in browser. | Existing IFC file loaded via fragments | No constraint system. Manual element placement. Geometry editing is direct mesh manipulation. | Server recommended for large files. WASM for geometry parsing. | Streams IFC fragments, variable size | Reads IFC. Writes modified IFC. No BOM or grammar concept. |
| **Speckle** | Data transport layer. Moves geometry between apps. Not a modeller itself. | Data from another tool (Revit, Rhino, etc.) | None — passes through whatever constraints the source app had | Cloud server required (speckle.xyz or self-hosted) | JSON stream, variable | Converts between formats. Schema translation, not verification. |
| **BlenderBIM** (IfcOpenShell) | IFC-native modelling in Blender. Elements are IFC entities from the start. | Blank IFC project or imported IFC | Blender's general 3D constraints. IFC-aware but not parametric-BIM-aware. | Desktop (Blender, 300MB + add-on) | `.ifc` directly, 1–100MB | Best IFC fidelity of any open tool. But no BOM concept, no grammar extraction. |
| **BIM OOTB (this)** | Grammar extraction from reference building. Design by grid drag, not element placement. Construction replay as design preview. | **An existing building you trust** — its grammar IS your starting point | Parent-child axis binding. 1D interval collision. UBBL constants. No constraint solver — the BOM tree IS the constraint graph. | **None. Browser tab. Zero install.** | SQLite DB, 5–50MB. Event log < 50KB. | **Rosetta Stone verified.** The grammar was proven by round-trip compilation of 21 buildings through 9 gates. The user verifies the grid. The system records the verification. |

**Key differences:**

1. **No blank canvas.** Every other tool starts from nothing or a template. This starts from a real building's extracted grammar. The reference building has already been verified — its proportions, storey heights, structural cadence, and MEP densities are proven facts, not assumptions.

2. **No constraint solver.** Revit and ArchiCAD spend enormous engineering effort on parametric constraint propagation. This approach doesn't need it because the BOM tree already encodes the spatial relationships. A wall belongs to a grid line. An opening belongs to a wall. The parent-child relationship IS the constraint. No solver required — just axis binding and interval collision.

3. **No server.** Every commercial BIM tool requires either a desktop install (gigabytes) or a cloud server. This runs in a browser tab with sql.js (SQLite in WASM) and Three.js. The entire design session is a 50KB event log. The building database is cached in IndexedDB.

4. **Verified round-trip.** No other tool can prove that its output matches its input through systematic gates. The Rosetta Stone verification — 21 buildings, 9 gates, 77 verbs — is unique to this approach. The grammar extraction is not a guess; it's a proven decomposition.

5. **Human-in-the-loop calibration.** Other tools trust their constraint solvers. This trusts the user's eyes. The Rosetta Stone icon puts the grid in calibration mode — the user drags lines to where they actually belong, and the system records the correction as verified truth. The algorithm proposes; the human confirms.

---

## 3. Architecture — Layered, Not Merged

```
┌──────────────────────────────────────────────────────────┐
│                  MODELER LAYER (new)                      │
│  "New" pill → grammar → grid → replay → commands → save  │
└──────────────────────────┬───────────────────────────────┘
                           │ imports (read-only)
┌──────────────────────────┴───────────────────────────────┐
│              COMPILER LAYER (existing, unchanged)         │
│  IFC → SQLite → streaming viewer → grid overlay → ERP    │
└──────────────────────────────────────────────────────────┘
```

**Dependency rule:** The compiler never imports modeler code. The modeler imports the compiler's public API. The compiler does not know the modeler exists.

### 3.1 Integration Points (exhaustive)

| Compiler Export | Used By Modeler For |
|---|---|
| `loadIFCFromIndexedDB()` | Loading the reference building |
| `GridDims.detectGrids()` | Seeding the initial grid from reference geometry |
| `extractBOMAbstractSets()` | Grammar extraction — ratios, heights, densities |
| `getGanttEvents()` | 4D replay timeline |
| `renderer.addToScene()` | Displaying ghost envelope and materialized elements |
| `spatialIndex.query()` | Grid snapping to structural positions |
| `kernel_ops.commitOp()` | Logging every user command |

No other coupling. The modeler lives in a single new module (`new_from_ref.js`) that wires these existing APIs together.

---

## 4. The Doc Pill (Red Pill) — UX Flow

### 4.1 Entry

The **Red Pill** icon (capsule image) replaces the Time Machine clock in the main icon-pill (position 1, right edge). Tapping it swaps the entire pill to **Doc mode** — a red-glass background with white icons.

### 4.2 Doc Pill Icons (implemented)

| # | Icon | ID | Role |
|---|------|----|------|
| 1 | Home | `doc-home-btn` | Return to main pill (viewer mode) |
| 2 | Grid (`#`) | `doc-grid-btn` | 2D grid + lengths + AABBCC bubbles (single toggle, all-or-nothing) |
| 3 | Clock (TM) | `doc-tm-btn` | Time Machine replay — full TM panel |
| 4 | Next (`›`) | `doc-next-btn` | Advance one construction phase (separate from TM) |
| 5 | Folder (Open) | `doc-open-btn` | Load saved design event log from IndexedDB |
| 6 | Disk (Save) | `doc-save-btn` | Materialize — write event log to building's IndexedDB entry |
| 7 | UBBL (TBD) | `doc-ubbl-btn` | Compliance check — run all UBBL rules on moved items, mark clashes |

**Lengths and Envelope are NOT separate toggles.** Grid ON shows everything (lines, bubbles, lengths, envelope). Grid OFF shows only materialized meshes. One toggle, no clutter.

### 4.3 Default State — Auto "New Doc"

When the user taps the Red Pill:
1. Check IndexedDB — does the current building have a saved design event log?
2. **No saved design** → canvas clears, shows only the **building envelope** (ghost wireframe from the BOM root's AABB). This is "New doc" state — a blank canvas with the proportional skeleton.
3. **Has saved design** → auto-resume from the event log. Canvas shows envelope + materialized elements.

There is no separate "New doc" button. Entering Doc mode IS "New doc" when no saved work exists.

### 4.4 Edit-Time vs UBBL-Time

**During editing:** lightweight checks only. Door drags along its wall axis, stays on floor, avoids windows. Items can be deleted. No heavy rule evaluation — the constraint is purely parent-child axis binding + sibling collision (1D interval overlap).

**When UBBL is pressed:** all UBBL/compliance rules run against recently moved items. Violations marked as clashes with option to auto-correct (snap to compliant position). UBBL rules are standard constants (`ubbl_rules.json`), not learned per building — clearance is clearance.

### 4.5 The "Save" Gate

Dragging grid lines adjusts bay proportions. Each drag is a `GRID_MOVE` command logged to `kernel_ops`. The user sees the result live — lengths update, envelope reshapes.

**Save** writes the event log into the current building's IndexedDB entry. The materialized geometry is regenerable from grammar + event log — only the event log is persisted. No separate `NewIFC.db` file.

---

## 5. JS BOM Extraction — Browser-Side, Not Java

### 5.1 Why JS, Not Java

The Java BIM Compiler's BOM pipeline (BOMWalker, verb expansion, factorization, component_library.db, output.db) was built for multi-building ERP compilation. The browser needs none of that. One building in context, one BOM, extracted on the fly from `elements_meta`.

| Java BOM | JS BOM (new) |
|---|---|
| Multi-building library | Single building in context |
| component_library.db + BOM.db + output.db | One building.db, BOM cached as JSON in IndexedDB |
| BOMWalker + verb expansion + factorization | Group-by tree from elements_meta |
| Recipe lines with tack offsets | Element positions already world-space |
| Compile step required | Extract on Red Pill entry, instant |

### 5.2 What JS Extracts From elements_meta

The building.db already has `elements_meta` with: `ifc_class`, `storey`, `guid`, `width_mm`, `depth_mm`, `height_mm`, centroid XYZ, `discipline`, `material_name`. That's enough to derive:

| Abstract Set | Source | Example |
|---|---|---|
| **BOM tree** | Group-by: building → storey → discipline → ifc_class → elements | Hierarchy, not recipe |
| **Bay proportions** | Column/wall positions per storey (`GridDims.detectGrids()`) | `[1.0, 0.75, 1.0, 0.75]` |
| **Storey heights** | Z deltas between storeys (already in storey filter data) | `[3.6, 3.2, 3.2, 3.0]` |
| **Envelope** | Min/max AABB of all elements | 60×72×22m |
| **Structural cadence** | Column placement pattern relative to grid intersections | `column_at_every_intersection` |
| **Element counts** | Count per ifc_class per storey per discipline | Density numbers |

No verb expansion, no factorization, no TILE/ROUTE/FRAME. The browser extractor reads what's there and groups it.

### 5.3 STD_MEP — Default MEP for Small Buildings

Small buildings (houses, small commercial) often have no extracted MEP discipline data. A default template provides standard services:

- Standard bathroom plumbing per room type
- Standard electrical points per room area
- Standard fire points per floor area

Stored as `STD_MEP` JSON lookup (same pattern as `STD_MAT` for materials). The building's own extracted MEP overrides the template only when it exists.

### 5.4 Grammar, Not Clone

The grammar is the proportional skeleton — not the building itself. Two buildings with the same grammar but different grid drags produce different designs. The grammar provides intelligent defaults; the user provides the variation.

---

## 6. Constrained Drag + UBBL Compliance

### 6.1 Parent-Child Axis Binding

Every draggable child knows its parent and locked axis. The BOM tree IS the constraint graph:

- **Wall owns Opening** → Opening slides along wall's length axis only
- **Opening cannot exceed wall width** → clamped to `[0, wall_length - opening_width]`
- **Siblings don't overlap** → 1D interval collision check among children of same parent

Same pattern for MEP runs along corridors — pipe slides along its route axis.

### 6.2 Edit-Time Checks (Lightweight)

During editing, only parent-child constraints are enforced:
- Door drags along wall, stays on floor, avoids windows
- Items can be deleted freely
- No heavy rule evaluation — just axis lock + sibling collision

### 6.3 UBBL Button (Full Compliance)

When the UBBL icon is pressed, all rules run against recently moved items:
- Door min width (900mm), corridor min width, fire escape distances
- Stairwell clearances, accessibility compliance
- Violations marked as clashes (same red highlight as existing clash detection)
- **Auto-correct option** — snap violating elements to nearest compliant position

UBBL rules are **standard constants** stored in `ubbl_rules.json` (or embedded in existing `clash_rules.json`). Clearance is clearance — not learned per building.

### 6.4 Grid Progression — BOM Hierarchy Drives Grid Detail

**Principle: the grid follows the BOM tree, top-down. Extract, not invent.**

The BOM tree is hierarchical: BUILDING → STOREY → DISCIPLINE → IFC_CLASS → ELEMENT. The grid mirrors this hierarchy exactly. At each level, the grid shows only what that BOM level justifies — never more.

**Step zero — BUILDING level (highest BOM parent):**

- The grid shows the **envelope AABB only** — the root BOM node's bounding box
- **2 X-lines** (A, B) at envelope minX and maxX → one span = total width
- **2 Z-lines** (1, 2) at envelope minZ and maxZ → one span = total depth
- **Bubbles** A, B and 1, 2 at line ends
- **Span dimensions** showing full width and full depth
- No column cadence. No subdivision. No internal structure. Nothing is invented.
- The only data source is `BOM.envelope.{minX, maxX, minY, maxY}` — extracted, not computed

**Why envelope only:** We have not started the building design. Step zero is just the bounding rectangle. The grid cannot show detail that doesn't yet exist in the construction sequence. Showing column-derived subdivisions at step zero would be **inventing** structure that hasn't been placed — violating the extract-not-invent principle.

**As Next advances through BOM children, the grid refines:**

| Next Press | BOM Level Revealed | Grid Refinement |
|---|---|---|
| 1 | STOREY: floor slab | Horizontal line added at storey Z height |
| 2 | DISCIPLINE/STR: columns, beams | X-lines split at column positions (A → A, A', A'') |
| 3 | DISCIPLINE/ARC: walls | Grid lines align to wall centerlines |
| 4 | IFC_CLASS: openings | No grid change — openings live on parent walls |
| 5 | DISCIPLINE/MEP: pipes, ducts | No grid change — MEP follows routes, not grid |

Each refinement is extracted from the elements that just appeared — never guessed in advance. The grid is never more detailed than the elements that justify it.

**Implementation note (BUG, 2026-05-21):** `doc_canvas.js _buildGrid()` currently subdivides by column cadence at step zero. This violates §6.4. Fix: at step zero, use only `e.x0, e.x1` and `e.z0, e.z1` — 4 lines, 4 bubbles, 2 span dimensions. Cadence subdivision moves to the Next handler when columns appear.

### 6.5 Rosetta Stone — The User IS the Verification Gate

The Rosetta Stone icon (bottom of Doc pill) toggles **calibration mode**. This is distinct from normal grid drag:

| Mode | Purpose | Visual | Log entry | Geometry moves? |
|---|---|---|---|---|
| **Normal drag** | Design — change proportions | Red grid lines | `GRID_MOVE` kernel_op | Yes — walls, slabs follow |
| **Rosetta drag** | Calibration — teach correct positions | **Gold grid lines** | `GRID_CALIBRATE` kernel_op | No — only the grid line moves |

**Why this matters:** The algorithm detects grid lines from column positions, but only human eyes can confirm where the structural grid actually falls. The Java BOM compiler's Rosetta Stone gates verified that compiled placement matched the original IFC. Here, the same principle inverts:

- **Old (Java):** machine compiles → machine verifies against original
- **New (browser):** machine detects grid → **human verifies by dragging** → machine records the correction

When the user drags a grid line in Rosetta mode:
1. The line turns gold — signaling "you're teaching, not designing"
2. The user drags it to the correct position
3. Release = **snap** — the line locks at the user-verified position
4. The correction is recorded: `{axis, label, detected: 4.50, corrected: 4.72, delta: 0.22}`
5. The corrected grid becomes the authoritative skeleton for all subsequent design work
6. Future buildings of the same type can reference these calibrations as hints

The user's drag IS the gate pass. The snap IS the witness. The Rosetta Stone icon activates the mode where human eyes replace algorithmic verification.

### 6.6 Grid Drag → Geometry Response → UBBL Validate (applies after elements appear)

The core POC cycle:
1. **Next** → elements appear phase by phase (slab first, then walls, then openings)
2. **Drag grid line A** → wall attached to line A moves with it, floor/slab stretches to fill new span
3. **Press UBBL** → compliance check confirms the wall can sit at new position
4. **Mesh reapplied** — wall geometry extends/shrinks, floor slab adjusts. IFC values (center_x, bbox_x, width_mm) update in the DB
5. **Save** → NewBuilding.db with updated element_transforms and elements_meta — a valid, self-contained DB

---

## 7. 4D Replay — Construction as Design Preview

### 6.1 The Replay Loop

The reference building's Gantt extraction (already in the compiler) provides a construction sequence. The New pill replays this sequence as a design preview:

```
for each constructionStep in ganttTimeline:
    materialize(step.elements, currentGridState)
    renderer.update()
    if userPaused or userDragged:
        break  // user takes control
```

The replay uses the **current grid state** (after any drags) — not the original positions. This means dragging grid line B from 6.0m to 7.5m and then resuming replay shows walls, windows, and MEP at the adjusted proportions.

### 7.2 Replay as Validation

The replay is not decoration. It is the user's primary validation tool:

- **Structural → Architectural → MEP order** — the user sees whether their grid adjustment produces sensible room sizes before MEP fills them
- **Clash preview** — elements that collide at the adjusted proportions flash red during replay (existing clash detection reused)
- **Cost preview** — the cost panel (`cost_panel.js`) updates live during replay, reflecting adjusted quantities

---

## 8. The No-Clone Data Architecture

### 8.1 Immutable Reference

`IFC.db` (the reference building) is read-only. It lives in IndexedDB exactly as extracted. The modeler never writes to it.

### 8.2 Event Log as Primary Storage

Every user action is a JSON command in `kernel_ops`:

```json
{ "op_type": "GRID_MOVE", "parameters": { "axis": "X", "label": "B", "old_m": 6.0, "new_m": 7.5 } }
{ "op_type": "STOREY_HEIGHT", "parameters": { "level": 0, "old_m": 3.6, "new_m": 4.0 } }
{ "op_type": "FACADE_RATIO", "parameters": { "face": "S", "old": 0.40, "new": 0.55 } }
```

The event log is tiny — kilobytes for a full design session. Undo is replay-to-previous. Redo is replay-to-next. The infrastructure for this already exists in `kernel_ops.js`.

### 8.3 Materialized View = NewIFC.db

`NewIFC.db` is generated from: `grammar(IFC.db) + event_log → NewIFC.db`

It can be deleted and regenerated at any time. It is a cache, not a source. The schema is identical to any extracted IFC.db — meaning the existing streaming viewer, clash detection, cost panel, and ERP layer all work on `NewIFC.db` without modification.

### 8.4 No Duplication

| What | Stored Where | Size |
|---|---|---|
| Reference building | IndexedDB (already there) | 5–50 MB |
| Grammar (abstract sets) | IndexedDB (cached JSON) | < 10 KB |
| Event log | `kernel_ops` table in IndexedDB | < 50 KB |
| Materialized design | `NewIFC.db` (generated) | 5–50 MB (regenerable) |

The design itself — the user's creative contribution — is the event log. Under 50 KB. Shareable as a URL hash. Versionable in git. Diffable in SQL.

---

## 9. Command Vocabulary

The modeler needs a small, fixed set of commands. Each is a `kernel_ops` op type:

| Command | Trigger | Parameters | Undo |
|---|---|---|---|
| `GRID_MOVE` | Drag grid line | axis, label, old_m, new_m | Restore old_m |
| `GRID_ADD` | Add grid line | axis, label, position_m | Remove line |
| `GRID_DELETE` | Remove grid line | axis, label | Restore line |
| `STOREY_HEIGHT` | Adjust floor-to-floor | level, old_m, new_m | Restore old_m |
| `STOREY_ADD` | Add storey | level, height_m | Remove storey |
| `STOREY_DELETE` | Remove storey | level | Restore storey |
| `FACADE_RATIO` | Adjust window/wall ratio | face, old, new | Restore old |
| `MEP_DENSITY` | Adjust MEP density | discipline, storey, old, new | Restore old |
| `ELEMENT_PLACE` | Place element at coordinate | guid, x, y, z | Remove element |
| `ELEMENT_REMOVE` | Remove element | guid | Restore element |
| `MATERIALIZE` | Generate NewIFC.db | timestamp | (not undoable — regenerate) |

This is not extensible by design. A fixed vocabulary prevents feature creep and keeps the event log machine-readable. New element types are expressed as `ELEMENT_PLACE` with different `ifc_class` parameters, not as new command types.

---

## 10. What Changes vs. What Is Reused

### Reused (zero changes)

| Component | File(s) | Role in New |
|---|---|---|
| Grid detection | `grid_dims.js` | Seeds initial grid from reference |
| Grid overlay rendering | `grid_overlay.js` | Renders AABBCC lines and bubbles |
| Grid drag | `grid_drag.js` | Drag-to-adjust interaction |
| Kernel ops | `kernel_ops.js` | Event log storage and replay |
| Cost panel | `cost_panel.js` | Live cost during replay |
| Clash detection | `measure.js` | Clash preview during replay |
| Streaming renderer | `streaming.js` | Displays ghost + materialized meshes |
| Section cut | `section_cut.js` | Floor plan views of new design |
| Gantt extraction | existing 4D pipeline | Provides construction sequence |

### New (to be built)

| Component | Responsibility | Estimated Lines |
|---|---|---|
| `new_from_ref.js` | Orchestrator — wires grammar extraction, grid seeding, replay, materialization | ~600 |
| Grammar extractor | Extracts BOM abstract sets from reference IFC.db | ~400 |
| Materialization service | Applies grammar + event log → generates NewIFC.db | ~500 |
| New pill UI | Context pill toolbar with grid/lengths/envelope toggles | ~300 |
| Ghost renderer | Translucent wireframe envelope of reference building | ~200 |

**Total new code: ~2,000 lines.** No duplication of existing viewer or compiler logic.

---

## 11. Share Integration

The event log is small enough to encode in a URL hash. A shared "New" design is:

```
?ref=SampleHouse&ops=W3sib3AiOiJHUklEX01PVkUiLCJ...
```

The receiver loads the reference building from the catalog, applies the event log, and sees the design. No file transfer. No server. The same `buildShareUrl()` mechanism used for share context (S265) is extended with an `ops` parameter.

---

## 12. Sequence — What to Build When

### Phase 1: Grid Seeding (the "New" produces a 2D grid)

- New pill appears in toolbar
- Loads reference IFC.db from IndexedDB
- Calls `GridDims.detectGrids()` to seed AABBCC grid
- Displays grid with bay lengths
- Toggle: grid ON/OFF, lengths ON/OFF
- **No replay, no materialization yet** — just the proportional skeleton

### Phase 2: Grid Adjustment

- Enable `grid_drag.js` in New context
- Each drag logs `GRID_MOVE` to `kernel_ops`
- Bay lengths update live
- Undo/redo via existing `kernel_ops` infrastructure
- Save serializes event log to IndexedDB

### Phase 3: Grammar Extraction

- Extract BOM abstract sets from reference
- Seed storey heights, facade ratios, structural cadence
- When grid is adjusted, grammar re-proportions dependent elements

### Phase 4: 4D Replay

- Wire Gantt extraction to replay service
- "Next" steps through construction sequence
- Elements materialize at current grid positions
- Drag-to-interrupt during replay

### Phase 5: Materialization

- `MATERIALIZE` command generates `NewIFC.db`
- Output follows standard schema — entire existing tool chain works on it
- Rosetta Stone verification can compare `NewIFC.db` against the grammar's predictions

---

## 13. What This Is Not

- **Not a parametric modeller.** There is no constraint solver, no parametric family system. The grammar provides defaults; the user overrides them by dragging grid lines. The commands are explicit, not computed.

- **Not a clone tool.** The reference building's geometry is never copied. Its proportional skeleton is extracted and offered as a starting point. The user's design diverges from the first grid drag.

- **Not a generative tool.** There is no algorithm generating design options. The user designs by interrupting a deterministic replay. The machine provides the grammar; the human provides the variation.

- **Not a replacement for the compiler.** The compiler extracts IFC → SQLite. The modeler extends SQLite → NewIFC.db. The compiler is unmodified. The modeler is an addition.

---

## 14. The Protein Analogy (Revisited)

The Spatial Compilation Paper draws a structural analogy between BOM compilation and protein folding. The "New From Reference" workflow extends that analogy:

| Protein Science | New From Reference |
|---|---|
| Template-based modelling: known fold → new sequence | Reference building: known grammar → new design |
| PDB template provides the fold topology | IFC.db provides the proportional skeleton |
| Amino acid substitutions alter binding affinity | Grid drags alter bay proportions |
| The fold is the grammar; substitutions are mutations | The grid is the grammar; drags are mutations |
| AlphaFold validates against experimental structure | Rosetta Stone validates against the grammar |

The difference: AlphaFold is stochastic. This is deterministic. Same event log → same NewIFC.db. Always.

---

## 15. Deferred: Save/Open + Drop DB

### 15.1 Save/Open — IndexedDB, Not Separate Files

Design event logs are stored in the same IndexedDB entry as the building they derive from. No separate `NewIFC.db` file — the event log lives alongside the extracted data. Save writes the `kernel_ops` event log into the building's DB. Open resumes from it. Multiple named designs per building = multiple named event logs in the same DB.

**Save As** (from the New card) creates a named snapshot of the current event log within the building's DB entry. The materialized geometry is regenerable — only the event log is persisted.

**Save button** in the Doc pill also writes the current session's event log to IndexedDB, giving users who access buildings online a way to secure work on their own machine without requiring explicit file export.

### 15.2 Drop DB — Accept .db Files Alongside IFC

The existing Drop IFC zone must also accept `.db` files. A dropped `.db` is loaded directly into IndexedDB and opened in the viewer — same as if it had been extracted from IFC. This allows users to share materialized designs as `.db` files, or to reload previously exported databases without re-extraction.

---

## 16. Success Criteria

The "New From Reference" feature is done when:

1. **Grid seeding works** — New pill loads reference, displays AABBCC grid with correct bay lengths
2. **Grid drag works in New context** — drags log to `kernel_ops`, lengths update live
3. **Toggles work** — grid/lengths/envelope independently toggleable from the New pill toolbar
4. **Event log is tiny** — a full design session produces < 50 KB of commands
5. **Materialization round-trips** — `NewIFC.db` loads in the existing viewer without modification
6. **Share works** — event log encodes in URL hash, receiver sees the same design
7. **Original untouched** — `IFC.db` is byte-identical before and after a New session
