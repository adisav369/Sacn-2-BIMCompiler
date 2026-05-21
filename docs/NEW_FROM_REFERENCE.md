# New From Reference — Deterministic Design From Grammar Extraction

> **Related:** [BIM Modeller OOTB](BIM_Modeller_OOTB.md) (kernel-op theory) · [2D Layout](2D_LAYOUT.md) (grid overlay) · [Kernel Ops Roadmap](../prompts/KERNEL_OPS_ROADMAP.md) (op log) · [Spatial Compilation Paper](SPATIAL_COMPILATION_PAPER.md) (round-trip proof) · [BOM Compilation](BOMBasedCompilation.md) (recipe model)

<div class="bim-banner" markdown>
<b>You never start from a blank canvas.</b> You start from a building you trust, extract its grammar, and design by interrupting its replay.
</div>

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

## 4. The "New" Pill — UX Flow

### 4.1 Entry

User selects a catalog item (reference building) and taps **New**. A new **context pill** appears in the toolbar — the "New" pill — joining the existing pills (Storey, Discipline, etc.).

### 4.2 Initial State

The New pill activates with the **2D grid visible by default**:

- **XYZ grid lines** derived from the reference building's structural grid (`GridDims.detectGrids()`)
- **Bay lengths displayed** on each span (e.g., `A–B: 6.000 m`, `B–C: 4.500 m`) — same format as the existing grid overlay measurements panel
- **AABBCC grid markings** — lettered/numbered bubble labels at grid intersections, identifying each grid line for drag adjustment
- **Ghost envelope** — a translucent wireframe showing the reference building's bounding volume

This is the design canvas. The user sees the reference building's proportional skeleton, not its full geometry.

### 4.3 Toggle States (New Pill Toolbar)

The New pill toolbar provides three visibility toggles:

| Toggle | ON | OFF |
|---|---|---|
| **2D Grid** | Grid lines + AABBCC bubbles + bay lengths visible. Drag-to-adjust enabled. | Grid hidden. Only envelope or materialized meshes visible. |
| **Lengths** | Bay span dimensions shown on each grid interval | Dimensions hidden — cleaner view for spatial judgement |
| **Envelope** | Ghost wireframe of reference building's bounding volume | Envelope hidden — user sees only the grid or materialized elements |

**Key rule:** AABBCC grid markings and drag handles are ONLY visible when the 2D Grid toggle is ON. Turning the grid off leaves only the envelope and/or materialized (done) meshes — a clean visualization for presentation or review. Turning the grid back on restores the AABBCC markings and re-enables drag adjustment.

### 4.4 The "Save" Gate

Dragging grid lines adjusts bay proportions. Each drag is a `GRID_MOVE` command logged to `kernel_ops`. The user sees the result live — lengths update, envelope reshapes.

**Save** materializes the current grid state + all logged commands into `NewIFC.db`. Before Save, the design is an event log. After Save, it is a queryable database with geometry BLOBs, element_transforms, and elements_meta — identical schema to any extracted IFC.db.

---

## 5. Grammar Extraction — What "New" Learns From the Reference

### 5.1 BOM Abstract Sets

The reference building's BOM is not copied — it is **abstracted** into ratios and rules:

| Abstract Set | What It Captures | Example |
|---|---|---|
| **Bay proportions** | Ratio of structural bay widths along each axis | `[1.0, 0.75, 1.0, 0.75]` (alternating wide/narrow) |
| **Storey heights** | Floor-to-floor heights for each level | `[3.6, 3.2, 3.2, 3.0]` (taller ground floor) |
| **Wall/window ratio** | Percentage of external wall area occupied by openings per facade orientation | `N: 0.25, S: 0.40, E: 0.15, W: 0.15` |
| **MEP density** | Service elements per square metre per discipline per storey | `HVAC: 0.3/m², Elec: 0.5/m², Plumb: 0.1/m²` |
| **Structural cadence** | Column/wall placement rule relative to grid intersections | `column_at_every_intersection` or `wall_on_perimeter_only` |
| **BOM depth** | Number of hierarchical levels in the reference BOM | `building → storey → zone → element → leaf` |

These abstract sets are **cached** after first extraction. They are JSON — bytes, not megabytes.

### 5.2 Grammar, Not Clone

The grammar is the proportional skeleton of the building — not the building itself. Two buildings with the same grammar but different grid drags produce different designs. The grammar provides intelligent defaults; the user provides the variation.

This is analogous to how a protein template provides the fold topology while the specific amino acid substitutions determine binding affinity. The fold is the grammar. The substitutions are the grid drags.

---

## 6. 4D Replay — Construction as Design Preview

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

### 6.2 Replay as Validation

The replay is not decoration. It is the user's primary validation tool:

- **Structural → Architectural → MEP order** — the user sees whether their grid adjustment produces sensible room sizes before MEP fills them
- **Clash preview** — elements that collide at the adjusted proportions flash red during replay (existing clash detection reused)
- **Cost preview** — the cost panel (`cost_panel.js`) updates live during replay, reflecting adjusted quantities

---

## 7. The No-Clone Data Architecture

### 7.1 Immutable Reference

`IFC.db` (the reference building) is read-only. It lives in IndexedDB exactly as extracted. The modeler never writes to it.

### 7.2 Event Log as Primary Storage

Every user action is a JSON command in `kernel_ops`:

```json
{ "op_type": "GRID_MOVE", "parameters": { "axis": "X", "label": "B", "old_m": 6.0, "new_m": 7.5 } }
{ "op_type": "STOREY_HEIGHT", "parameters": { "level": 0, "old_m": 3.6, "new_m": 4.0 } }
{ "op_type": "FACADE_RATIO", "parameters": { "face": "S", "old": 0.40, "new": 0.55 } }
```

The event log is tiny — kilobytes for a full design session. Undo is replay-to-previous. Redo is replay-to-next. The infrastructure for this already exists in `kernel_ops.js`.

### 7.3 Materialized View = NewIFC.db

`NewIFC.db` is generated from: `grammar(IFC.db) + event_log → NewIFC.db`

It can be deleted and regenerated at any time. It is a cache, not a source. The schema is identical to any extracted IFC.db — meaning the existing streaming viewer, clash detection, cost panel, and ERP layer all work on `NewIFC.db` without modification.

### 7.4 No Duplication

| What | Stored Where | Size |
|---|---|---|
| Reference building | IndexedDB (already there) | 5–50 MB |
| Grammar (abstract sets) | IndexedDB (cached JSON) | < 10 KB |
| Event log | `kernel_ops` table in IndexedDB | < 50 KB |
| Materialized design | `NewIFC.db` (generated) | 5–50 MB (regenerable) |

The design itself — the user's creative contribution — is the event log. Under 50 KB. Shareable as a URL hash. Versionable in git. Diffable in SQL.

---

## 8. Command Vocabulary

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

## 9. What Changes vs. What Is Reused

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

## 10. Share Integration

The event log is small enough to encode in a URL hash. A shared "New" design is:

```
?ref=SampleHouse&ops=W3sib3AiOiJHUklEX01PVkUiLCJ...
```

The receiver loads the reference building from the catalog, applies the event log, and sees the design. No file transfer. No server. The same `buildShareUrl()` mechanism used for share context (S265) is extended with an `ops` parameter.

---

## 11. Sequence — What to Build When

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

## 12. What This Is Not

- **Not a parametric modeller.** There is no constraint solver, no parametric family system. The grammar provides defaults; the user overrides them by dragging grid lines. The commands are explicit, not computed.

- **Not a clone tool.** The reference building's geometry is never copied. Its proportional skeleton is extracted and offered as a starting point. The user's design diverges from the first grid drag.

- **Not a generative tool.** There is no algorithm generating design options. The user designs by interrupting a deterministic replay. The machine provides the grammar; the human provides the variation.

- **Not a replacement for the compiler.** The compiler extracts IFC → SQLite. The modeler extends SQLite → NewIFC.db. The compiler is unmodified. The modeler is an addition.

---

## 13. The Protein Analogy (Revisited)

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

## 14. Success Criteria

The "New" feature is done when:

1. **Grid seeding works** — New pill loads reference, displays AABBCC grid with correct bay lengths
2. **Grid drag works in New context** — drags log to `kernel_ops`, lengths update live
3. **Toggles work** — grid/lengths/envelope independently toggleable from the New pill toolbar
4. **Event log is tiny** — a full design session produces < 50 KB of commands
5. **Materialization round-trips** — `NewIFC.db` loads in the existing viewer without modification
6. **Share works** — event log encodes in URL hash, receiver sees the same design
7. **Original untouched** — `IFC.db` is byte-identical before and after a New session
