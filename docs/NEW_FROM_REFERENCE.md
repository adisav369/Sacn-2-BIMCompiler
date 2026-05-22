# New From Reference — Deterministic Design From Grammar Extraction

> **Prerequisite Reading — the Java concepts this feature ports to JS:**
>
> | Doc | What It Provides | How It Applies Here |
> |-----|-----------------|-------------------|
> | [BOMBasedCompilation.md](BOMBasedCompilation.md) (BBC) | The recipe model — BUILDING→STOREY→DISCIPLINE→ELEMENT hierarchy, tack offsets (dx/dy/dz), verb formulas (TILE/ROUTE/FRAME), factorization. **§4 tack convention** is critical. | The JS BOM extractor produces the same hierarchy from `elements_meta`. The BOM tree IS the constraint graph for grid drag. Parent-child spatial offsets drive constrained editing. |
> | [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) | Discipline validation — how the Java compiler verifies element placement per discipline (ARC/STR/MEP). Gate checks that every element lands in the correct spatial container. | The Gantt stepper (Next button) reveals elements discipline-by-discipline in the same order: STR first (columns, beams), then ARC (walls, slabs), then MEP. Grid refinement follows the same discipline sequence. |
> | [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) | DB-level validation — schema contracts, integrity hashes, element count reconciliation. The gates that prove round-trip fidelity. | The Rosetta Stone calibration mode records corrections the same way gates record pass/fail — verified facts about spatial positions that can be replayed and checked. |
> | [SPATIAL_COMPILATION_PAPER.md](SPATIAL_COMPILATION_PAPER.md) | The theoretical proof — BOM decomposition ↔ protein folding analogy, round-trip verification across 21 buildings. | The "grammar extraction" concept comes directly from this paper. A reference building's grammar = a protein template's fold topology. Grid drags = amino acid substitutions. |
> | [WorkOrderGuide.md](WorkOrderGuide.md) §5-6 | Pipeline flow — how IFC extraction feeds into BOM compilation, what the classification YAML provides, how output.db is generated. **§Invention Boundary** defines what can be computed vs. what must be extracted. | The JS BOM extractor must respect the same invention boundary: extract from elements_meta, never invent. Grid lines come from element positions, not algorithms. |
> | [CLASH_DETECTION.md](CLASH_DETECTION.md) | Clash rules, proximity checks, spatial indexing (R-tree). | UBBL compliance reuses the same clash engine. Clearance rules are clash rules with minimum distance thresholds. |
> | [TestArchitecture.md](TestArchitecture.md) §Traceability | The traceability matrix — every feature links to a test, every test names the issue it proves. | Rosetta Stone calibrations ARE the traceability entries for the grid. Each user correction is a witnessed fact linked to a specific grid line. |
> | [SQLite3D_Schema.md](SQLite3D_Schema.md) | The DB schema — `elements_meta`, `element_transforms`, `component_geometries`. The tables the JS BOM extractor reads. | Save produces a NewBuilding.db with this exact schema. The existing viewer loads it without modification. |
> | [DATA_MODEL.md](DATA_MODEL.md) §6.3 | The 4-DB architecture — extraction DB, BOM.db, component_library, output.db. | The browser collapses this to 1 DB per building + cached JSON BOM in IndexedDB. Same data, simpler packaging. |
>
> **Also:** [BIM Modeller OOTB](BIM_Modeller_OOTB.md) (kernel-op theory) · [2D Layout](2D_LAYOUT.md) (grid overlay) · [Kernel Ops Roadmap](../prompts/KERNEL_OPS_ROADMAP.md) (op log) · [ERP.md](ERP.md) (AD-in-browser)

> **Supersedes:** [BIM_Designer_Browser.md](BIM_Designer_Browser.md) (viewer layer — still valid, now the foundation this builds on)

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

### 4.4 HUD in Doc Mode — Grid Bays, Element Count, Discipline

When the Red Pill activates Doc mode, the HUD (top-left overlay) adapts to reflect design state:

| HUD Element | Step Zero | After Next Presses |
|---|---|---|
| **Grid Bays** (`#hud-gridbays-section`) | `A–B 42600 mm`, `1–2 41780 mm` (envelope spans) | Bays subdivide as grid lines appear: `A–B 6400 mm`, `B–C 7200 mm`, ... |
| **Element Count** (`#s-buildings-done`) | `0` | Running total of revealed elements |
| **Status Bar** | `Step 0 — envelope` | `Phase 3 — ARC | 186 elements` |

Grid bay rows are color-coded: **blue** (`#4fc3f7`) for X-axis (A–B, B–C), **green** (`#81c784`) for Z-axis (1–2, 2–3). Values in millimetres, updated on each Next press, Rosetta drag, or discipline switch.

The Grid Bays accordion appears only in Doc mode and hides on Home (deactivate). All values derive from `getGridState()` — no extra queries.

### 4.5 Edit-Time vs UBBL-Time

**During editing:** lightweight checks only. Door drags along its wall axis, stays on floor, avoids windows. Items can be deleted. No heavy rule evaluation — the constraint is purely parent-child axis binding + sibling collision (1D interval overlap).

**When UBBL is pressed:** all UBBL/compliance rules run against recently moved items. Violations marked as clashes with option to auto-correct (snap to compliant position). UBBL rules are standard constants (`ubbl_rules.json`), not learned per building — clearance is clearance.

### 4.6 The "Save" Gate

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
| `ELEMENT_DUPLICATE` | Clone element at offset | source_guid, dx, dy, dz | Remove clone |
| `BOM_ROTATE` | Rotate parent + children | parent_guid, angle_deg, pivot | Restore angle |
| `GRID_ROTATE` | Rotate grid line | axis, label, old_deg, new_deg, pivot_m | Restore old_deg |
| `ELEMENT_MIRROR` | Mirror element across axis | guid, axis, pivot_m | Mirror back |
| `BOOKMARK` | Mark timeline position | name, phaseIndex, disc, cam, tgt, gridState | Remove bookmark |
| `MATERIALIZE` | Generate NewIFC.db | timestamp | (not undoable — regenerate) |

The vocabulary is deliberately small. Rotation, mirroring, and duplication are the only additions to the original set — they complete the spatial operations without introducing parametric complexity. Each op is reversible and composable. The event log remains human-readable and machine-diffable.

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

---

## 17. Addendum — Enhancements from Collaborative Review (2026-05-21)

The following refinements emerged from deep technical review after the initial specification. They do not alter the core architecture but significantly improve usability, exploration, and version control.

### 17.1 Timeline Scrubber (TM Icon Role)

**Problem:** The single Next button forces linear stepping through potentially hundreds of construction phases. Users need fluid navigation.

**Solution:** The Time Machine icon in the Doc pill gains a new role — it opens a **permanent timeline widget** at the bottom of the canvas (collapsible, so the user can keep the view clean):

- Horizontal track with labelled markers for each phase (slab, columns, walls, openings, MEP, etc.)
- Click any marker to seek directly to that phase — materialization updates instantly
- Play/Pause button for automatic replay
- Current position indicator (draggable scrubber)
- Discipline-aware: when STR is active, timeline shows STR phases only; switch to ARC, it relabels
- Completed phases marked **blue** on the track
- Branch timelines shade below the main track like Git branch visualization

**Long-press Next:** Pressing and holding the Next button accelerates replay (1 phase/s → 5/s → 20/s). Release stops. An **Undo button** appears below Next during replay to reverse overshoot.

**Implementation:** The timeline is a visual binding to the existing `_phases[]` array and `_phaseIndex` cursor. Seek = set `_phaseIndex = N`, re-hide all meshes, replay phases 0..N. The full 4D TM panel remains available for detailed Gantt editing — the timeline widget handles navigation only.

### 17.2 Bookmarks & Compare — Blue Ocean Feature

**Bookmarks:** Users can mark any timeline position (phase + camera view + grid state) with a named note. Bookmarks persist in the design event log as `BOOKMARK` kernel ops:

```json
{ "op_type": "BOOKMARK", "parameters": {
    "name": "Option A - wider lobby",
    "phaseIndex": 12, "disc": "ARC",
    "cam": [x,y,z], "tgt": [x,y,z],
    "gridState": { "xPositions": [...], "zPositions": [...] }
}}
```

**Compare:** A dedicated Compare icon opens **toggle mode** — tap left bookmark to see state A, tap right to see state B. Differences render directly in the 3D viewer scene using the same visual language as Drop IFC merge diff variance:

- **Blue** — elements added (present in B, absent in A)
- **Red** — elements removed (present in A, absent in B)
- **Yellow** — elements shifted (same GUID, different position)

No geometry comparison needed — the op log provides deltas directly. Two designs from the same grammar differ only in their mutations. The mutations ARE the ops. Diffing two bookmarks is trivial: compare two `gridState` arrays of numbers.

### 17.3 Branching — Parallel Universe Timelines (Git for Buildings)

**Insight:** `kernel_ops` is already a linear commit log. Branching is a natural extension.

- Each event log gains a `branch_id` and `parent_branch_id` column.
- Timeline widget displays branches as parallel horizontal tracks (like `git log --graph`).
- **Fork** button at any timeline position creates a new variant branch from that point.
- **Switch** between branches — materialization rebuilds from the reference DB + that branch's op sequence.
- **Diff** between branches via the Compare tool (§17.2), rendered as spatial heatmap in the viewer.

**Visual:** Coloured horizontal lines that diverge at fork points, with bookmark stars along each track. This looks like nothing else in BIM software. Users familiar with Git will immediately grasp the ingenuity.

**Merge (post-MVP):** Replay all ops from branch A onto branch B's state. Because ops are high-level (`GRID_MOVE`, `FACADE_RATIO`), conflicts only occur if two ops modify the same grid line or storey height. Spatial conflict resolution requires clash detection during merge — this is hard and deferred. Fork + switch + diff covers 90% of the design exploration use case without merge.

### 17.4 Default Gold Calibration — Confirm Before Design

**Principle:** The algorithm proposes; the user confirms. No designing on uncalibrated skeletons.

**Flow:**
1. Enter Doc mode → **envelope grid (red)** — 2+2 lines at AABB edges are mathematically exact, no calibration needed
2. Press Next → elements + auto-detected grid lines appear → **auto-switch to gold** (calibration mode) — "confirm or drag these lines"
3. User taps "Accept All" or drags individual lines to correct positions → commits calibration
4. After confirmation → **switch to red** (design mode) — grid is now trusted
5. Subsequent loads of same building type → remembered calibrations apply via cached `GRID_CALIBRATE` ops, skip directly to red

**Why envelope starts red:** At step zero there are only 2+2 envelope lines — the AABB edges. These are exact (min/max of all elements). Calibration is irrelevant for exact bounds. Gold calibration activates when Next adds lines from element positions, where column detection might be off by a wall thickness.

### 17.5 Diff Heatmap for Design Variants

Building on §17.2, a **heatmap intensity overlay** shows magnitude of change between two bookmarked states:

- Light red/yellow → grid line moved <= 10 cm
- Deep red/yellow → moved > 50 cm
- Blue → new element cluster
- Red → removed element cluster

Generated from the op log deltas — no geometry comparison. A `GRID_MOVE` from 6.0m to 7.5m is a 1.5m delta. Colour intensity maps to delta magnitude. Same visual language as the Drop IFC merge diff, applied to design variants.

### 17.6 Implementation Priority

| Feature | Complexity | Phase | Reason |
|---|---|---|---|
| Gold-first calibration (§17.4) | Low | Phase 2 (revised) | Prevents garbage-in, highest safety impact |
| Timeline scrubber (§17.1) | Low | Immediate | Pure UI binding to existing `_phases[]` |
| Long-press Next + Undo | Low | Immediate | setInterval + clearInterval, 20 lines |
| Right-click context menu (§17.7C) | Low | Immediate | Raycast + popup, ~150 lines |
| BOM_ROTATE / element rotation (§17.7A) | Low | Phase 3 | Matrix4 already in pipeline, just expose to user |
| GRID_ROTATE / diagonal grids (§17.7B) | Medium | Phase 4 | Grid line model gains angle_deg, cascade is matrix multiply |
| Bookmarks storage | Low | Phase 5 | Just a kernel_op type |
| Compare toggle (§17.2) | Medium | Phase 5 | Reuses Drop IFC diff visuals (blue/red/yellow) |
| Branching fork + switch (§17.3) | Medium | Phase 6 | `branch_id` column + branch selector |
| Diff heatmap (§17.5) | Medium | Phase 6 | Op log deltas → colour overlay |
| Merge | High | Post-MVP | Spatial conflict resolution is hard |

### 17.7 Rotation & Diagonal Grids — Right-Click Context Design (2026-05-22)

**Observation (during S266 POC):** The rendering pipeline already carries full rotation data. `element_transforms` stores `rotation_x/y/z`. `streaming.js` composes full `Matrix4` via `Euler → Quaternion → compose()` for both BatchedMesh and InstancedMesh. Every element in the viewer already renders at its correct orientation. The gap is only in the *design layer* — grid lines are axis-locked, kernel_ops has no rotation op.

**What this enables with minimal new code:**

#### A. BOM Set Rotation (furniture groups, wall assemblies)

A BOM parent owns its children. A wall owns its doors and windows. A room owns its furniture. Rotating the parent rotates all children — the BOM tree IS the transform hierarchy. The Three.js scene already does this for grouped objects.

New kernel op:
```json
{ "op_type": "BOM_ROTATE", "parameters": {
    "parent_guid": "wall_17", "angle_deg": 45,
    "pivot": { "x": 12.5, "z": -8.0 }
}}
```

**UX:** Right-click an element → context menu → **Rotate** → drag arc shows angle (15° snap by default, free with Shift). All children follow. The BOM tree already knows who the children are — no discovery needed.

**Why this is hard in Revit:** Revit's constraint solver must re-evaluate every parametric relationship when an element rotates. Wall joins break, windows lose their host, dimensional constraints conflict. Here, there is no constraint solver — the BOM parent-child hierarchy IS the constraint. Rotation is a matrix multiply, not a constraint re-solve.

#### B. Diagonal Grid Lines (angled wings, non-orthogonal plans)

Current grid: `_xPositions[]` and `_zPositions[]` — strictly axis-aligned.

Extension: a grid line becomes `{ axis, position, angle_deg }`. At `angle_deg = 0`, it behaves exactly as today. At `angle_deg = 30`, the line rotates around its midpoint. Elements bound to that grid line rotate with it.

New kernel op:
```json
{ "op_type": "GRID_ROTATE", "parameters": {
    "axis": "X", "label": "C",
    "old_deg": 0, "new_deg": 30,
    "pivot_m": 15.0
}}
```

**Rendering:** A rotated grid line is still a `THREE.Line` between two points — the endpoints are computed from `position + angle` instead of `position + axis`. The `_addGridLine()` function already takes `(x0,y0,z0, x1,y1,z1)` — it just needs non-axis-aligned endpoints.

**Cascade:** When a grid line rotates, all elements attached to it receive the same rotation delta around the same pivot. This is one matrix multiply per element — the same operation `streaming.js` already does at load time.

**What this produces:** Hospital wings at 30°. L-shaped floor plans. Chevron facades. All from the same grid-drag paradigm, just with an angular degree of freedom added. The reference building's grammar provides the *proportions*; rotation provides the *configuration*.

#### C. Right-Click Context Menu (unified interaction)

The right-click (long-press on mobile) opens a context menu on any element or grid line:

| Target | Actions |
|---|---|
| **Grid line** | Rotate (drag arc), Delete, Properties (bay widths) |
| **Element** | Rotate (15° snap), Mirror, Duplicate, Remove, Properties (IFC data) |
| **BOM group** | Rotate group, Duplicate storey, Swap discipline template |
| **Empty space** | Add grid line, Add element from reference, Paste |

**Implementation:** One `contextmenu` / `pointerdown` handler on the canvas. Raycast to identify target (same as existing pick handler). Render a small radial menu or list popup. Each action emits a kernel_op. ~150 lines of code.

#### D. What This Does NOT Require

- No constraint solver. Rotation is a matrix operation, not a constraint re-evaluation.
- No new geometry generation. Existing meshes rotate in place.
- No new extraction. The BOM tree and element transforms already contain all needed data.
- No server. Same browser-only, event-log-based architecture.

#### E. Competitive Significance

No browser-based BIM tool supports diagonal grid manipulation with BOM-aware cascading. Desktop tools (Revit, ArchiCAD) support angled grids but require manual reconnection of constraints. This approach is unique: **the BOM tree propagates rotation automatically because parent-child is a spatial, not parametric, relationship.**

### 17.8 Why This Matters

Every other BIM tool treats the design as an opaque blob. Revit's `.rvt` is a black box — you cannot diff it, branch it, replay it, or verify it. This system treats the design as a **transparent, replayable event log** on top of a verified grammar. That is not just version control for buildings — it is **deterministic reproducibility**. Same grammar + same ops = same building. Always.

The branching feature is not "Git for buildings" as a metaphor — it is literally the same data structure (a DAG of commits) applied to spatial events instead of text patches. The `kernel_ops` table IS the `.git/objects` directory.

These enhancements transform the "New From Reference" designer from a clever replay tool into a **complete design environment**:
- Fluid timeline navigation replaces blind stepping
- Bookmarks and compare enable professional design reviews
- Branching introduces Git-like version control for buildings — a category first
- Default calibration mode eliminates the "garbage in, garbage out" risk

Together, they make the BIM Compiler not just a technical breakthrough but a tool that designers will actively choose over any incumbent.

### 17.9 Design Session Triage — Grid Rethink + BOM Completion (2026-05-22)

**Problem observed (S266b live testing):** Auto-generated grid lines flood the canvas.
215 walls in HospitalGarage produced 215 `GRID_ADD` kernel ops. The 4m wall filter and 2m
dedup help but cannot distinguish structural grid bays from corridor partition walls.
The heuristic approach (`CLASS_PRIORITY`, length filter, dedup) fails because:

- A hospital has ~8 structural bays but ~200 long (>4m) interior walls
- Without BOM parent-child relationships, every wall looks structural
- The `elements_meta` table has no parent/child — it's flat

**Root cause:** The JS BOM extractor groups by `storey × discipline × ifc_class` but
does NOT carry the Java BOM tree (`X_M_BOM` → `X_M_BOMLine` → leaf product). The
generation ordering (parent before child) and structural vs. partition classification
exist in the Java pipeline but are lost during extraction.

#### A. BOM Completion Jump — `bom_tree` Table in Extracted DB

The Java `BuildingCompiler` already produces the BOM tree. Include it in the extracted DB:

```sql
CREATE TABLE bom_tree (
  parent_guid TEXT NOT NULL,
  child_guid  TEXT NOT NULL,
  bom_level   INTEGER NOT NULL,  -- 0=building, 1=storey, 2=discipline, 3=element
  product_id  TEXT,               -- M_Product reference from ERP
  is_structural INTEGER DEFAULT 0 -- 1 for columns/load-bearing walls
);
```

**What this enables:**
1. Design Gantt follows BOM generations — parent before child, not alphabetical
2. Structural classification from Java (columns, load-bearing walls) → only these create grid lines
3. Opening-to-host relationship → doors know their parent wall
4. BOM cascade on edit: move parent → children follow via tack offsets (dx/dy/dz)

#### B. User-Initiated Grid Lines (replaces auto-grid flood)

**Rethink (per user feedback):** Grid lines should NOT auto-generate from walls.
Instead:

1. **Step zero:** Envelope AABB only (2+2 lines, as now — correct)
2. **Next (walls):** Show walls but do NOT auto-add grid lines
3. **User picks grid lines:** Tap/click a wall → "Add grid here?" confirmation →
   one grid line appears at the wall's structural axis
4. **Rosetta templates:** Remain as drag-to-place alternatives
5. **Result:** User controls how many grid lines exist. 8 bays, not 200.

The `GRID_ADD` kernel op still fires — but triggered by user intent, not autoGrid.

#### C. Multi-Axis Selection for Opening Drag

Problem: dragging a door opening may elongate it (stretch one axis) instead of
moving the whole piece. Doors have TWO governing axes (width + position along wall).

Fix: when an opening is selected, BOTH its X and Z grid lines highlight. Dragging
moves the entire bounding group — the opening plus its governing grid lines — as a
rigid body. This requires:

1. Opening → parent wall lookup (from `bom_tree`)
2. Parent wall axis → perpendicular axis = opening's offset axis
3. Multi-select: both axes constrained, drag moves whole set

#### D. Grid Edit Cascading — Later Phases Recompute from Changed Grid

When an outer wall's grid line is moved at phase N, subsequent Next presses must
place inner walls relative to the NEW grid position, not the original extraction.

Implementation: each grid line stores `{ position, original_position, delta }`.
When `nextPhase()` places elements, their positions are adjusted by the grid delta
of their nearest governing grid line. This is the BOM tack offset recalculation
in browser form: `new_position = original_position + grid_delta`.

**Requires `bom_tree`:** only children of the moved grid's parent should cascade.
Without BOM relationships, we don't know which elements depend on which grid line.

#### E. Z-Axis Grid Lines (Height / Storey)

Current grid: X (lettered) and Z (numbered) — horizontal plan only.
Missing: Y-axis grid lines for storey heights and vertical positioning.

Add: storey-height grid lines on the Y axis, labeled by storey name.
These appear at `storey.minZ` (converted to Three.js Y). Dragging a Y-grid line
changes `STOREY_HEIGHT`. Children of that storey cascade vertically.

#### F. GPU Performance — Render Throttle in Doc Mode

Problem: `THREE.WebGLRenderer: WEBGL_multi_draw extension not supported. 33845`
call count for 215 walls in one Next press. GPU heats up.

Mitigations:
1. **BatchedMesh merge:** walls per storey should be one BatchedMesh, not 215 draw calls
2. **Visibility-only reveal:** `setVisibleAt()` on existing BatchedMesh slots
   (already coded in S260) — but only if walls are pre-batched
3. **Throttled reveal:** instead of materializing all 215 at once, reveal in
   chunks of 20 per frame via `requestAnimationFrame`. Smooth visual build-up
   AND keeps frame rate stable
4. **Doc mode render budget:** cap at 30fps in Doc mode (vs 60fps normal). Less
   GPU heat for what is essentially a 2D layout task

#### G. Save and Recall

Save in Doc mode should:
1. Serialize `kernel_ops` log + grid state + phase index to IndexedDB
2. Offer "Open Previous Design Session" from Doc pill
3. Restore: replay kernel_ops log → exact same grid + element state

This is already spec'd in §9 (`MATERIALIZE` op) but needs the IndexedDB session
store and a recall UI. The kernel_ops log IS the save format — no new data model.

#### H. Print-Ready Mode — Max + Photo Icons (2026-05-22)

User request: Add **Maximize** and **Photo/Screenshot** icons to the main pill when in
Doc mode. The photo icon captures the canvas with a **white/reverse background** for
print-ready output. User controls grid density via double-click (add) / double-click
(remove), then takes a clean screenshot for documentation.

- Max icon: hides all UI chrome (pill, HUD, status, timeline) → full-screen canvas
- Photo icon: temporarily switches to white background, captures canvas as PNG, reverts
- White background: `renderer.setClearColor(0xffffff)`, grid lines become dark,
  bubbles become dark, dim labels become dark — automatic reverse for print

#### I. Next Session Priority Order

1. ~~**BOM completion jump**~~ — DONE (S267). See §17.10.
2. **User-initiated grid lines** — remove auto-grid, add tap-to-place from walls.
   Rosetta templates remain for manual placement.
3. **Multi-axis opening drag** — requires `bom_tree` for parent lookup
4. **Grid edit cascade** — requires `bom_tree` for child dependency
5. **Z-axis grid lines** — storey heights
6. **GPU throttle** — chunked reveal, 30fps cap
7. **Save/recall** — IndexedDB session store
8. **Bubble rotation** — already coded, needs visual testing

### 17.10 S267 BOM Walker — Java→JS Port + BOM-Driven Phases (2026-05-22)

**Delivered:** The BOM tree now drives the browser. No flat queries, no heuristic
class sorting, no invented structure. The recipe IS the truth.

#### What Was Built

| File | Lines | What |
|------|-------|------|
| `verb_expand.js` | 190 | JS port of 7 Java verb expanders (TILE, ROUTE, FRAME, CLUSTER, SPRAY, LINE, LINE_MULTI). Pure math, zero deps. |
| `bom_walker.js` | 175 | JS port of BOMWalker tree traversal. Walks m_bom/m_bom_line via sql.js. Three-way dispatch: sub-assembly, PHANTOM, leaf. MAX_DEPTH=20 guard. |
| `import_worker.js` | +50 | IfcRelVoidsElement + IfcRelFillsElement + IfcRelAggregates → `bom_tree` table in extracted DB (IFC Drop path). |
| `import_db_builder.js` | +10 | `bom_tree` table creation from worker data. |
| `doc_canvas.js` | rewrite | `_loadPhases` walks BOM.db tree, not flat query. `_buildEnvelope` uses BOM root AABB. Recomposition on grid drag. |
| `panels.js` | +75 | Lazy-fetch BOM.db from OCI on Red Pill, cache in IndexedDB. |

#### The Key Decision: BOM.db Drives Everything

The flat `bom_extract.js` query (storey × discipline × class) was **removed from
the phase builder**. Phases now come exclusively from `BOMWalker.walk()` on `A._bomDb`:

```
BUILDING_SH_STD
  └─ SH_GF_STR (FLOOR)
       Phase 1: Structure — IfcWall, IfcSlab       ← grid lines attach here
       Phase 2: Openings — IfcDoor, IfcWindow       ← children of structure
       Phase 3: Finishes — IfcCovering, IfcRoof      ← envelope/cladding
  └─ SH_ROOM_GF (FLOOR)
       Phase 4: Infill — IfcFurniture               ← fills defined spaces
```

No BOM.db → no phases → no Next button. The user sees the envelope wireframe but
cannot decompose without the recipe. IFC Drop buildings without BOM data show
envelope only until bom_tree is populated.

#### Envelope: Recipe, Not Scatter

The grid envelope now comes from the BOM BUILDING root's AABB + origin, not from
the extracted DB's structural scatter. The BOM AABB is a curated, verified definition:

| Building | BOM AABB (m) | Extracted scatter (m) | Diff |
|----------|-------------|----------------------|------|
| HI | 61.7 × 74.4 | 89.8 × 106.4 | 28m wider in scatter |
| SH | 16.9 × 8.7 | (matches) | Clean building |
| TE | 73.7 × 59.1 | (matches) | Federated MEP |

The scatter is larger because it includes outlier structural elements (site walls,
retaining walls) that the BOM recipe deliberately excludes. Grid lines should match
the recipe's definition of the building footprint.

#### BOM.db Fleet Status (as of S267)

| PFX | BOM.db | BOMs | Leaves | Verbs | Verb Types | Phases |
|-----|--------|------|--------|-------|------------|--------|
| SH | 127KB | 14 | 50 | 5 | CLUSTER, TILE, LINE | 8 |
| DX | 283KB | 36 | 242 | 5 | CLUSTER, LINE, LINE_MULTI | 13 |
| SC | 684KB | 7 | 342 | 138 | CLUSTER, TILE | 20 |
| HI | 516KB | 7 | 441 | 141 | CLUSTER, TILE, ROUTE | — |
| TE | 8.2MB | 50 | 5,519 | 725 | CLUSTER, LINE, LINE_MULTI, TILE, ROUTE, FRAME | 69 |

SH and DX BOM.db files were created this session via `IFCtoBOMMain` pipeline.
TE is the only building with a FRAME verb (grid-line-to-column mapping).

#### Tier Classification (Phase Ordering)

| Tier | Name | IFC Classes | What It Defines |
|------|------|-------------|-----------------|
| 1 | Structure | Column, Pile, Wall, WallStandardCase, Slab, Beam, Footing | Bay geometry — grid lines attach here |
| 2 | Openings | Door, Window, OpeningElement | Refines bays — children of walls |
| 3 | Finishes | Covering, CurtainWall, Roof, Plate, Stair, StairFlight, Railing, Member | Envelope/cladding |
| 4 | Infill | Everything else (Furniture, Proxy, MEP terminals) | Fills defined spaces |

#### Coordinate System Relationship

BOM positions are **floor-relative** (local to building origin). The extracted DB
has world-absolute coordinates. The bridge:

```
World position = BOM origin + local position
  [240.16, 294.65] = [237.39, 0] + [2.77, 57.26]  (HI columns X-axis)
```

All floor BOM origins in HI are (0,0,0) — storey height is NOT in origin_z.
Heights come from the dz tack offset on m_bom_line or from element_transforms.

#### Tests

- `test_verb_expand.js`: 20/20 — each verb type, edge cases, matches Java output
- `test_bom_walker.js`: 20/20 — mock + real HI_BOM.db (441 leaves, 11ms)
- `test_bom_phases.js`: 12/12 — tier ordering on SH/DX/SC/TE, MEP sub-BOMs, envelope
- `test_doc_canvas.js`: 54/54 — existing tests + _setPhases test injection

#### 17.10.1 Recomposition Rules — How Each Element Type Responds to Grid Drag

Grid drag is not "move everything by delta." Each element type has a specific
geometric response, mirroring how an architect would expect the building to adapt.
The BOM verb determines the math; the IFC class determines the behavior.

**Principle:** A grid line defines a structural datum. When it moves, elements
respond based on their relationship to that datum — attached, spanning, contained,
or independent. No element changes shape unless its verb demands it.

##### Columns (IfcColumn, IfcPile) — FRAME / CLUSTER verb

Columns sit AT grid intersections. When grid line A moves from X=12 to X=15:
- Columns at A reposition to X=15 (direct attachment)
- Columns at other grid lines: unchanged
- Verb re-expansion: FRAME verb gets new X coordinate in its grid list

**Architect expects:** Column moves with its grid line. Nothing stretches. Exact.

##### Walls (IfcWall, IfcWallStandardCase) — CLUSTER verb

Walls run BETWEEN grid lines (or along one). Two cases:
1. **Wall on grid A:** wall centerline moves with A. Full delta applied. Wall does not stretch.
2. **Wall spanning A to B:** when A moves outward by +3m, the wall's length increases by 3m.
   Currently S267 translates the wall (wrong). S268 should scale the wall's long axis.

For S268: identify wall orientation (long axis). If wall endpoints match two grid lines,
scale `bbox_x` or `bbox_y` by the ratio `new_bay_width / old_bay_width`. Apply via
`matrix.scale.x` or `matrix.scale.z` on the mesh (visual stretch — not new geometry).

**Architect expects:** Wall grows or shrinks to fill the bay. No gap, no overlap.

##### Openings (IfcDoor, IfcWindow, IfcOpeningElement) — child of wall

Openings are children of walls. They do NOT respond to grid lines directly.
They follow their host wall's movement. If wall moves +3m, its doors/windows
move +3m. If wall stretches, openings stay at their proportional position
along the wall (e.g. door at 40% of wall length stays at 40%).

**Source:** `bom_tree` table (IfcRelVoidsElement → IfcRelFillsElement) or
`host_element_ref` on m_bom_line.

**Architect expects:** Doors and windows stick to their wall. Never float free.

##### Slabs (IfcSlab) — CLUSTER / TILE verb

Slabs span the full bay or envelope. When a grid line moves:
- Slab edge that touches the moved grid: extends to follow
- Other edges: unchanged
- Implementation: scale slab mesh along the axis of the moved grid

**Architect expects:** Floor plate covers the full structural bay. No exposed gap.

##### Roof (IfcRoof) — envelope element

Roof sits on top of the building. When footprint changes (grid A moves):
- Roof extends horizontally to match new footprint
- **Slope angle does NOT change.** The roof pitch is a design decision, not a
  grid consequence. A later sub-grid line (Z-axis) with rotation can adjust pitch.
- Implementation: scale roof mesh X or Z to match new envelope width

**Architect expects:** Roof covers the new footprint at the same pitch. Pitch is
changed deliberately via a Z-axis grid (storey height change) or rotation tool, never
as a side effect of horizontal grid drag. This follows the standard architectural
sequence: plan first, then section (pitch/height), then detail.

##### Beams (IfcBeam) — structural span

Beams run between columns (grid intersection to grid intersection). When grid A moves:
- Beam connected to A: one end repositions, beam length changes
- Implementation: scale beam long axis, reposition to bridge the two grid points
- If both ends on the same grid line: translate only (no stretch)

**Architect expects:** Beam spans between its supporting columns. Length adjusts.

##### Stairs (IfcStair, IfcStairFlight) — vertical circulation

Stairs connect storey N to storey N+1. Horizontal grid drag:
- Stair translates with nearest grid line (if one end attached)
- **Angle does NOT change** from horizontal grid drag. Stair pitch is governed by
  the Y-axis grid (storey height). A horizontal grid drag may make the stair well
  wider or narrower (scale the well, not the stair itself).

**Architect expects:** Stair stays at the same pitch. Well adjusts.

##### Covering / Tiles (IfcCovering) — TILE verb

Floor/wall tiles fill a surface. When bay width changes:
- TILE verb recalculates: `nx = ceil(new_width / stepX)`
- Tile count changes. Some InstancedMesh instances are added or hidden.
- This is the only case where element COUNT changes from a grid drag.

**Architect expects:** Tiles fill the new surface. More tiles in wider bay.

##### Furniture (IfcFurniture, IfcFurnishingElement) — interior infill

Furniture sits inside rooms. When room boundary moves:
- Furniture near the moved wall follows it (nearest-delta)
- Furniture in room center: proportional shift based on bay center change
- Furniture touching opposite wall: no change

**Architect expects:** Furniture stays in its logical position within the room.
Not pixel-precise, but reasonable. The user can manually adjust.

##### MEP (IfcDuct*, IfcPipe*, IfcFlowTerminal) — routes

MEP routes connect fixtures. When structural grid moves:
- Fixtures follow their host room/wall (cascade)
- Routes between fixtures need recalculation (RouteWalker re-run)
- For S268: translate fixtures with host. Route regeneration deferred to S269.

**Architect expects:** Fixtures stay in rooms, pipes get longer/shorter. Full
re-routing is a specialist task (MEP engineer), not automatic.

##### Summary Table

| Element | Grid Response | Shape Change | Verb Path | Cascade |
|---------|--------------|-------------|-----------|---------|
| Column | Translate to new grid position | None | FRAME re-expand | Direct |
| Wall (on grid) | Translate with grid | None | CLUSTER shift | Parent of openings |
| Wall (spanning) | Scale long axis | Length changes | CLUSTER scale | Parent of openings |
| Opening | Follow host wall | None | — | Child of wall |
| Slab | Scale to new bay | Width/depth changes | CLUSTER scale | — |
| Roof | Scale to new footprint | Width/depth, NOT pitch | — | Envelope |
| Beam | Scale between grid points | Length changes | — | — |
| Stair | Translate, NOT change pitch | Well width may change | — | — |
| Covering/Tile | Tile count recalculated | Count changes | TILE re-expand | — |
| Furniture | Nearest-delta + proportional | None | — | — |
| MEP fixture | Follow host room | None | — | Child of room |
| MEP route | Deferred (S269) | Re-route needed | ROUTE | — |

#### What's Next (S268)

1. **Grid-to-wall confirmation** — grid lines only draggable after user confirms
   they attach to a structural wall from Phase 1
2. **Recomposition mesh mapping** — map BOM leaf guids to extracted DB guids
   (element_ref on m_bom_line → guid in element_transforms)
3. **BOM.db upload** — upload SH/DX BOM.db files to `bim-ootb/buildings/`
4. **IFC Drop bom_tree** — re-extract fleet with Node.js extractor to populate
   bom_tree table (IfcRelVoids/Fills/Aggregates)
5. **FRAME verb grid mapping** — TE's single FRAME verb maps column grid
   coordinates to user-visible grid lines
