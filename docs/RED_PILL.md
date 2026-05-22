# Red Pill — Design Buildings From Grammar, Not From Scratch

> **The Red Pill:** You never start from a blank canvas. You start from a building you trust,
> extract its grammar, and design by interrupting its replay.

**Status:** Active development (S266–S270+)
**Entry point:** Tap the Red Pill (capsule icon) in the viewer's icon pill
**Supersedes:** [BIM_Designer_Browser.md](BIM_Designer_Browser.md) (viewer layer — still the foundation)
**Full spec:** [NEW_FROM_REFERENCE.md](NEW_FROM_REFERENCE.md) (1800-line technical spec)

---

## 1. The Big Idea

Every BIM modeller starts from a blank canvas or a parametric script. Neither reuses the spatial intelligence already embedded in real buildings.

The BIM Compiler already solves the inverse problem: **IFC → SQLite → verified reconstruction** across 21 buildings and 9 verification gates. The grammar is already extracted — BOM abstract sets, bay ratios, floor-to-floor heights, MEP densities.

**Red Pill is the forward path:** grammar → new design.

```
Reference Building (IFC.db)
       │
       ▼
  Grammar Extraction (BOM, bays, heights)
       │
       ▼
  2D Grid (seeded from grammar)
       │
       ▼
  4D Replay (construction phases, one discipline at a time)
       │
       ▼  ← USER INTERRUPTS HERE: drag grid lines, alter proportions
       │
       ▼
  NewIFC.db (materialized view, deletable, regenerable)
```

The original `IFC.db` never changes. The design is an event log applied to the grammar.

---

## 2. What Makes This Different

| | Red Pill | Revit | Grasshopper | SketchUp |
|---|---|---|---|---|
| **Starting point** | Real building grammar | Blank template | Parametric script | Blank canvas |
| **Constraint system** | Grid + BOM hierarchy | Full parametric solver | Node graph | None |
| **Server** | None (browser-only) | Desktop (60GB) | Desktop plugin | Cloud/desktop |
| **Round-trip** | Verified (Rosetta Stone gates) | One-way IFC export | No IFC | Lossy IFC |
| **Design method** | Interrupt a replay | Place families | Wire nodes | Free draw |
| **Deterministic** | Yes (same log → same result) | No | Depends | No |

---

## 3. Architecture — Two Layers, One Direction

```
┌──────────────────────────────────────────────────────────┐
│                  DESIGNER LAYER (new)                     │
│  Red Pill → grammar → grid → replay → commands → save    │
└──────────────────────────┬───────────────────────────────┘
                           │ reads (never writes back)
┌──────────────────────────┴───────────────────────────────┐
│              COMPILER LAYER (existing, unchanged)         │
│  IFC → SQLite → streaming viewer → grid overlay → ERP    │
└──────────────────────────────────────────────────────────┘
```

**Rule:** The compiler never imports designer code. The designer imports the compiler's APIs. The compiler does not know the designer exists.

---

## 4. Code Map — Where Things Live

### Core Files (`deploy/dev/`)

| File | Lines | Role |
|---|---|---|
| `doc_canvas.js` | ~2200 | **Orchestrator.** Red Pill UX, Gantt stepper (Next), grid drag → engine → mesh update. The thin caller. |
| `grid_kinematics.js` | ~670 | **Engine.** Pure-math grid recomposition. No Three.js, no DB, no DOM. Takes element positions + grid deltas, returns transform commands. |
| `bom_extract.js` | ~350 | **Grammar extractor.** Walks `m_bom` table, produces envelope + storey + phase hierarchy. The JS port of 13 Java classes — one function, one query, one pass. |
| `kernel_ops.js` | ~400 | **Event log.** Every user action as a JSON command. Undo/redo by replay. |
| `grid_overlay.js` | ~600 | **Grid rendering.** AABBCC labels, bubble markers, bay dimensions, drag handles. |
| `grid_dims.js` | ~300 | **Grid detection.** Extracts structural grid from element positions. |
| `measure.js` | ~1200 | **Clash detection + spatial index.** R-tree, proximity checks, clearance rules. |
| `scene.js` | ~3000 | **Viewer core.** BatchedMesh rendering, DLOD streaming, 4D Time Machine. |

### Test Files (`deploy/dev/tests/`)

| File | Tests | What It Proves |
|---|---|---|
| `test_doc_canvas.js` | 61 | Doc canvas UX, grid ops, BatchedMesh, BUG-1/BUG-4 fixes |
| `test_grid_kinematics.js` | 98 | Engine classification: ATTACH/SPAN/EDGE/ROOF, bay-proportional, cascades |
| `test_s268_recompose.js` | 63 | Attach-map recompose + bay-proportional integration |
| `test_grid_modules.js` | 114 | Grid detection, overlay, drag, label generation |

**Total: 336 tests, all whitebox §-tagged.**

---

## 5. The Grid Kinematics Engine

The engine is the mathematical core. It answers three questions per element:

1. **WHAT is attached?** → relation type (ATTACH, SPAN, EDGE_LEFT/RIGHT, ROOF_EAVE, ROOF_FLAT, INTERIOR)
2. **HOW may it move?** → action (TRANSLATE, SCALE, ROOF_VERTICES, ROOF_LIFT)
3. **WHAT cascades?** → wall height scales when roof lifts

### Relation Types

| Relation | When | Action |
|---|---|---|
| ATTACH | Element centerline within 0.5m of grid | TRANSLATE (rigid move) |
| SPAN | Grid line inside element body | SCALE (stretch) |
| EDGE_RIGHT | Element's right edge at grid line | SCALE or TRANSLATE depending on drag direction |
| EDGE_LEFT | Element's left edge at grid line | SCALE or TRANSLATE depending on drag direction |
| ROOF_EAVE | Roof eave vertices near grid | ROOF_VERTICES (eave moves, ridge fixed, linear interpolation) |
| ROOF_FLAT | Flat roof slab spanning grid | SCALE (like slab) |
| ROOF_LIFT | Roof on Y-axis (ceiling) grid | ROOF_LIFT (rigid vertical translate) |
| INTERIOR | Not attached to any grid | Bay-proportional repositioning |

### Design Invariants

1. `dragGrid()` is **pure**: `(positions, delta) → commands`. Never mutates input.
2. Engine is **stateless** re kernel_ops. Parent replays log on load.
3. Only attach-map elements get commands. Others via bay-proportional.
4. O(K) per drag where K = attached count. Pre-indexed by grid ID.
5. Coordinate swizzle: IFC bbox (Z-up) → Three.js (Y-up) happens in the caller, not the engine.

### Coordinate Transform

```
IFC (Z-up)          Three.js (Y-up)
  X (width)    →      X (same)
  Y (depth)    →      Z (into screen, negated)
  Z (height)   →      Y (up)

bbox_x  →  bboxX (same)
bbox_y  →  bboxZ (IFC depth → Three Z)
bbox_z  →  bboxY (IFC height → Three Y)
```

This swizzle is applied in `_collectElementData()` (`doc_canvas.js`). The engine operates entirely in Three.js coordinates.

---

## 6. The UX Flow — Red Pill Mode

### Entry
Tap the Red Pill icon → canvas clears → building envelope appears as ghost wireframe.

### Doc Pill Icons

| # | Icon | Action |
|---|---|---|
| 1 | Home | Return to viewer mode |
| 2 | Grid `#` | Toggle 2D grid + lengths + bubbles (all-or-nothing) |
| 3 | Clock | Time Machine replay |
| 4 | Next `›` | Advance one construction phase (STR → ARC → MEP) |
| 5 | Folder | Load saved design from IndexedDB |
| 6 | Disk | Save event log to IndexedDB |
| 7 | UBBL | Compliance check (planned) |

### Gantt Stepper (Next Button)

Each press reveals one construction phase, discipline by discipline:

1. **STR** — columns, beams, footings (structural grid emerges)
2. **ARC** — walls, slabs, doors, windows (architectural infill)
3. **MEP** — pipes, ducts, terminals (services)

Grid lines auto-generate from element positions at each phase. The grid refines as more elements appear — from 2-line envelope at step zero to full structural grid after all phases.

### Grid Drag → Recompose

1. Click a grid line → highlight + attach info in status bar
2. Drag → `_computeGridDeltas()` → incremental delta (BUG-1 fix)
3. Engine classifies → `dragGrid(gridId, incrementalDelta)` → commands
4. Caller applies commands to meshes (`_applyCommand`)
5. Lengths and bays update in HUD
6. `GRID_MOVE` logged to `kernel_ops`

---

## 7. The BOM — Recipe, Not Scatter

A BOM is a recipe: one parent, N children, each with a quantity. Each child can itself be a BOM — building → floor → room → furniture → leaf, recursively.

```sql
-- The JS BOM extractor runs ONE query:
SELECT bom.m_product_id, bom.name, line.m_product_id AS child_id,
       line.qty, meta.ifc_class, meta.guid,
       meta.center_x, meta.center_y, meta.center_z
FROM m_bom bom
JOIN m_bomline line ON line.m_bom_id = bom.m_bom_id
LEFT JOIN elements_meta meta ON meta.m_product_id = line.m_product_id
ORDER BY bom.m_bom_id, line.line
```

The result is a tree. The tree drives:
- **Envelope** — AABB of root BOM children
- **Phases** — grouped by storey × discipline × IFC class
- **Grid seeding** — element positions → grid line candidates

---

## 8. Command Vocabulary

Every user action is a reversible `kernel_ops` entry:

| Command | Trigger | Undo |
|---|---|---|
| `GRID_MOVE` | Drag grid line | Restore old position |
| `GRID_ADD` | Add grid line | Remove line |
| `GRID_DELETE` | Remove grid line | Restore line |
| `STOREY_HEIGHT` | Adjust floor-to-floor | Restore old height |
| `ELEMENT_PLACE` | Place element | Remove element |
| `ELEMENT_REMOVE` | Remove element | Restore element |
| `BOM_ROTATE` | Rotate parent + children | Restore angle |
| `GRID_ROTATE` | Rotate grid line | Restore angle |
| `BOOKMARK` | Mark timeline position | Remove bookmark |

The vocabulary is deliberately small. Each op is reversible and composable. A full design session produces < 50 KB of commands.

---

## 9. Data Architecture — No Cloning

```
┌─────────────────┐     ┌──────────────────┐
│  Reference.db   │────▶│  Event Log (JSON) │
│  (read-only)    │     │  (kernel_ops)     │
└─────────────────┘     └────────┬─────────┘
                                 │ materialize
                                 ▼
                        ┌──────────────────┐
                        │  NewBuilding.db   │
                        │  (regenerable)    │
                        └──────────────────┘
```

- **Reference.db** is never modified. Not even a single byte.
- **Event log** is the primary storage. Small, diffable, shareable via URL hash.
- **NewBuilding.db** is a materialized view — delete it and regenerate from grammar + log.

---

## 10. Rosetta Stone — The User IS the Gate

In the Java compiler, Rosetta Stone is an automated verification gate (G5): does the reconstruction match the original?

In Red Pill mode, Rosetta Stone is a **calibration tool**:
1. User selects a grid line
2. User drags a "Rosetta Stone" marker to a known real-world position
3. The offset between auto-detected and user-placed position is recorded
4. All future grid operations on that line account for the correction

Each Rosetta Stone placement is a witnessed fact — a user-verified ground truth that the grid algorithm can learn from.

---

## 11. Roadmap — What's Built, What's Next

### Done (S266–S270)

| Feature | Status | Evidence |
|---|---|---|
| Red Pill UX + Doc Pill | ✓ Deployed | `doc_canvas.js` |
| JS BOM Extractor | ✓ Deployed | `bom_extract.js`, one query |
| Gantt Stepper (Next) | ✓ Deployed | Phase-by-phase reveal, discipline filtering |
| Grid Auto-Generation | ✓ Deployed | From element positions per phase |
| Grid Kinematics Engine | ✓ 98 tests | Pure-math, 8 relation types, cascades |
| BUG-1 Incremental Delta | ✓ Fixed | T52-T54, `_lastAppliedDeltas` tracking |
| BUG-4 SCALE Commands | ✓ Fixed | T55-T58, IFC→Three bbox swizzle |
| Ceiling Grid Auto-Place | ✓ Deployed | Y-axis grid at eave height |
| Rosetta Stone Drag | ✓ Deployed | User calibration mode |
| 336 whitebox tests | ✓ All pass | 4 test files, §-tagged logs |

### Next (S270b+)

| Priority | Feature | Scope |
|---|---|---|
| P1 | Y-axis drag UI | Click ceiling disc → ROOF_LIFT + cascade |
| P2 | BUG-2: Empty attach map warning | UX: "no attached elements" status |
| P3 | BUG-3: Phase-aware recompose | New elements respect moved grid |
| P4 | UBBL Validator | Compliance engine, clearance rules |
| P5 | Save/Recall | IndexedDB persistence of event log |
| P6 | Materialization | Grammar + log → NewBuilding.db |
| P7 | Share via URL hash | `?ref=SampleHouse&ops=...` |

### Deferred (Stage 2)

- Tile recount / FRAME coord replacement at runtime
- MEP rerouting after grid drag
- IFC export from NewBuilding.db
- GPU throttle in Doc mode
- Diagonal grids / rotation / mirroring
- Git-like branching (parallel design timelines)

---

## 12. Technology Stack

| Layer | Technology | Notes |
|---|---|---|
| Rendering | Three.js r160 ESM | BatchedMesh, InstancedMesh, DLOD |
| Database | sql.js (WASM SQLite) | Browser-side, no server |
| Storage | IndexedDB | Per-building DBs, offline-capable |
| Caching | Service Worker | Offline-first, versioned cache |
| Hosting | OCI Object Storage + GitHub Pages | Static files, CDN-cached |
| Language | Plain JavaScript (ES5/6) | No frameworks, no build tools |

**Zero server dependency.** The entire designer runs in a browser tab.

---

## 13. For New Developers

### Quick Start

1. Open the viewer at the deployed URL (or `deploy/dev/index.html` locally)
2. Load any building (e.g. SampleHouse, SampleCastle)
3. Tap the Red Pill icon (capsule, top-right)
4. Press Next repeatedly → watch elements appear phase by phase
5. Click a grid line → drag it → watch walls/slabs recompose

### Running Tests

```bash
# All grid kinematics tests
node deploy/dev/tests/test_grid_kinematics.js

# Doc canvas tests (includes BUG-1/BUG-4 regression)
node deploy/dev/tests/test_doc_canvas.js

# Full recompose integration
node deploy/dev/tests/test_s268_recompose.js

# Grid modules (detection, overlay, drag)
node deploy/dev/tests/test_grid_modules.js
```

All tests are whitebox: §-tagged `console.log()` lines prove values, counts, and state. No Playwright needed for value verification — the maths tells you.

### Key Concepts

- **BOM** = Bill of Materials. Recipe: parent → children with quantities. Recursive.
- **Grammar** = the proportional skeleton extracted from a reference building.
- **Event Log** = deterministic command sequence. Same log → same result. Always.
- **Rosetta Stone** = user-verified ground truth. The calibration layer.
- **Kernel Op** = one reversible command in the event log.
- **Attach Map** = engine's index: which elements attach to which grid lines, and how.

### Related Docs

| Doc | What It Provides |
|---|---|
| [BOMBasedCompilation.md](BOMBasedCompilation.md) | The recipe model — BOM hierarchy, tack offsets, verb formulas |
| [TestArchitecture.md](TestArchitecture.md) | 9 verification gates, traceability matrix |
| [SQLite3D_Schema.md](SQLite3D_Schema.md) | DB schema — `elements_meta`, `element_transforms` |
| [CLASH_DETECTION.md](CLASH_DETECTION.md) | Clash rules, R-tree spatial indexing |
| [BIM_Designer_Browser.md](BIM_Designer_Browser.md) | Viewer layer — the foundation Red Pill builds on |
| [SPATIAL_COMPILATION_PAPER.md](SPATIAL_COMPILATION_PAPER.md) | Theoretical proof — BOM ↔ protein folding analogy |
| [NEW_FROM_REFERENCE.md](NEW_FROM_REFERENCE.md) | Full 1800-line technical spec (all the details) |
