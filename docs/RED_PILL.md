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
| `doc_canvas.js` | ~2090 | **Orchestrator.** Red Pill UX, Gantt stepper (Next), grid rendering, timeline, design save/open. Thin caller — delegates state and recompose to modules. |
| `grid_state.js` | ~335 | **Grid state.** Owns all position/label/original tracking. Single source of truth for grid lines. |
| `grid_recompose.js` | ~682 | **Recompose bridge.** Engine lifecycle, bbox swizzle, command dispatch, BOM L1 recompose, delta tracking. |
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
| `test_doc_canvas.js` | 73 | Doc canvas UX, grid ops, BatchedMesh, BUG-1/BUG-4, Save/Open, grid guards |
| `test_grid_kinematics.js` | 98 | Engine classification: ATTACH/SPAN/EDGE/ROOF, bay-proportional, cascades |
| `test_s268_recompose.js` | 63 | Attach-map recompose + bay-proportional integration |
| `test_grid_modules.js` | 114 | Grid detection, overlay, drag, label generation |

**Total: 348 tests, all whitebox §-tagged.**

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

This swizzle is applied in `_collectElementData()` (`grid_recompose.js`). The engine operates entirely in Three.js coordinates.

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

Grid starts as a 2-line envelope at step zero. Grid lines are added by user action only — double-click an element (wall/column) or Rosetta drag. Auto-grid was disabled (v17.9B) to prevent flooding with 200+ lines on large buildings.

### Grid Drag → Recompose

1. Click a grid line → highlight + attach info in status bar
2. Drag → `GridState.getDeltas()` → incremental delta (BUG-1 fix)
3. `GridRecompose.applyDrag()` → engine `dragGrid(gridId, incrementalDelta)` → commands
4. Commands applied to meshes (TRANSLATE/SCALE/ROOF_VERTICES/ROOF_LIFT)
5. BOM L1 recompose fires (debounced 16ms) if BOM nodes configured
6. Lengths and bays update in HUD
7. `GRID_MOVE` logged to `kernel_ops`

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

## 10. The Grid-Based Model — CUD + Attachment + Cascade

The grid is not decoration. It is the design model. Everything flows from two ideas:

1. **Grid lines are the user's design intent.** Create, move, or delete a grid line and the building responds.
2. **BOM elements attach to grid lines.** The attachment type determines how each element responds when its grid moves.

### 10.1 Grid Line CUD (Create / Update / Delete)

Grid lines are managed via three user actions, each logged as a `kernel_op`:

| Action | Trigger | Kernel Op | What Happens |
|---|---|---|---|
| **Create** | Rosetta drag from template line, or double-click a wall/column | `GRID_ADD` or `GRID_CALIBRATE` | New grid line inserted at element position. Engine rebuilds attach map. |
| **Update** | Select grid line → drag to new position | `GRID_MOVE` | Engine computes incremental delta → elements translate/scale/reshape. |
| **Delete** | Drag grid line beyond envelope boundary, or double-click existing line | `GRID_DELETE` | Line removed. Attached elements lose constraint. Engine rebuilds. |

**Rosetta Stone** is the primary Create tool. Three template lines (X, Y, Z) sit outside the envelope. When calibration mode is ON (gold lines), the user drags from a template → a committed grid line appears at the drop position. This is the user's creative contribution — they decide where the structural bays are.

**Double-click** is the secondary Create/Delete tool. Click an element (wall/column) → grid line appears at its position. Click near an existing line → removes it. Toggle behavior.

### 10.2 BOM Attachment — Why Elements Follow Grid Lines

When the engine rebuilds (after grid CUD or phase change), it classifies every visible BOM element against every grid line. The classification is spatial — based on proximity between element geometry and grid position:

```
For each element E, for each grid line G:
  distance = |E.center[axis] - G.position|
  halfExtent = E.bbox[axis] / 2

  if distance < 0.5m         → ATTACH  (centerline near grid)
  if G inside E body          → SPAN    (grid cuts through element)
  if |E.rightEdge - G| < 0.1m → EDGE_RIGHT
  if |E.leftEdge - G| < 0.1m  → EDGE_LEFT
  if E is roof at eave height → ROOF_EAVE
  if E is flat roof spanning G → ROOF_FLAT
  if G is Y-axis ceiling grid  → ROOF_LIFT
  else                         → INTERIOR (bay-proportional)
```

This is the **attach map** — the engine's index of which elements are bound to which grid lines, and how. The attach map makes the grid a structural model, not just a drawing overlay.

### 10.3 Cascade — How One Drag Moves Many Elements

When the user drags grid line B from position 10m to 13m:

1. **ATTACH** elements (columns at B) translate +3m rigidly
2. **SPAN** elements (slabs spanning A–B) scale to match new bay width
3. **EDGE** elements (walls with one edge at B) scale or translate depending on which edge
4. **ROOF_EAVE** elements have eave vertices move +3m while ridge stays fixed — slope changes
5. **INTERIOR** elements (furniture between A and B) reposition proportionally within the bay
6. **WALL_HEIGHT_SCALE** cascade: if a Y-axis grid (ceiling) moves, walls attached to it grow taller

This is how one grid drag propagates through the BOM hierarchy. The BOM tells you which elements exist and where they sit in the recipe. The attach map tells you how each one responds to the grid change. Together they form the **grid-based model**.

### 10.4 Rosetta Stone — The User IS the Gate

In the Java compiler, Rosetta Stone is an automated verification gate (G5): does the reconstruction match the original?

In Red Pill mode, Rosetta Stone is a **design tool**:
- **Create** grid lines by dragging from template lines (gold when ON, grey when OFF)
- **Calibrate** by recording the offset between auto-detected and user-placed positions
- **Delete** grid lines by dragging them beyond the envelope boundary

Each Rosetta Stone placement is a witnessed fact — a user-verified ground truth logged as `GRID_CALIBRATE` in kernel_ops. The user decides which grid lines matter. The engine makes every other element follow.

---

## 11. Implementation Status

### 11.1 Code Map

| File | Lines | Role |
|---|---|---|
| `doc_canvas.js` | 2214 | Orchestrator: UX, phases, grid rendering, timeline, save/open |
| `grid_state.js` | 353 | Grid positions, labels, originals, deltas — single source of truth |
| `grid_recompose.js` | 682 | Engine bridge, bbox swizzle, command dispatch, BOM L1 recompose |
| `grid_kinematics.js` | 672 | Pure-math engine: 8 relation types, cascades, bay-proportional |
| `bom_extract.js` | 350 | Grammar extractor: one SQL query, BOM → envelope + phases |
| `kernel_ops.js` | 400 | Event log: every user action as reversible JSON command |
| `bom_walker.js` | 175 | BOM tree traversal (JS port from Java) |
| `verb_expand.js` | 190 | Verb expansion math (FRAME, TILE, etc.) |

### 11.2 Done (S266–S270, branch `full`)

| Feature | Sprint | Evidence |
|---|---|---|
| Red Pill UX + Doc Pill | S266 | `doc_canvas.js`, 7 icons, activate/deactivate |
| JS BOM Extractor | S266 | `bom_extract.js`, one query, one pass |
| Gantt Stepper (Next/Prev) | S267 | Phase-by-phase, discipline filtering, timeline scrub |
| Grid Kinematics Engine | S268 | `grid_kinematics.js`, 98 tests, pure-math |
| BUG-1 Incremental Delta | S270 | `_lastAppliedDeltas` tracking, T52-T54 |
| BUG-4 SCALE Commands | S270 | IFC→Three bbox swizzle, T55-T58 |
| Ceiling Grid Auto-Place | S270 | Y-axis grid at eave height, Phase 3 trigger |
| Rosetta Stone CUD | S273 | Create via drag, Delete via envelope boundary, user grids preserved on scrub |
| Refactor: grid_state.js | S270 | Wired existing module, replaced 7 vars + 7 functions |
| Refactor: grid_recompose.js | S270 | Extracted engine bridge + BOM recompose (682 lines) |
| BOM Engine L1 Recompose | S272 | Phase 3+4, discipline rules, BomDiff commands |
| Design Save/Open | S273 | IndexedDB, kernel_ops replay |
| Y-axis Ceiling Drag UI | S270c | Click disc → drag up/down → ROOF_LIFT + WALL_HEIGHT_SCALE cascade |

### 11.3 Tests

| File | Count | Scope |
|---|---|---|
| `test_doc_canvas.js` | 79 | UX, grid ops, BatchedMesh, BUG-1/BUG-4, save/open, grid guards, ceiling drag |
| `test_grid_kinematics.js` | 98 | ATTACH/SPAN/EDGE/ROOF classification, bay-proportional, cascades |
| `test_s268_recompose.js` | 63 | Attach-map recompose + bay-proportional integration |
| `test_grid_modules.js` | 114 | Grid detection, overlay, drag, label generation |
| `whitebox_regression.js` | 34/36 | Split/IFC/offline/variance/ground (2 pre-existing) |
| `test_bom_*.js` (7 files) | 438 | BOM engine: strategies, constraints, diff, node, tree, grid, rules, deep |
| **Total** | **354+34+438** | All whitebox, §-tagged logs |

### 11.4 Known Issues

| ID | Severity | Description | Status |
|---|---|---|---|
| BUG-1 | Fixed | Incremental delta double-counting on second drag | T52-T54 |
| BUG-2 | Low | Grid with no attachments should warn, not silently allow drag | Open |
| BUG-3 | Medium | Next after drag materializes elements at original positions | Open (Stage 2) |
| BUG-4 | Fixed | SCALE commands not firing (bbox axis swizzle wrong) | T55-T58 |
| CREEP-1 | Low | BOM recompose code in grid_recompose.js (180 lines, undocumented in spec) | Accepted |
| CREEP-2 | Low | `applyDrag()` signature differs from spec (all-deltas vs per-grid) | Accepted |

### 11.5 Roadmap

| Priority | Feature | Scope |
|---|---|---|
| ~~P1~~ | ~~Y-axis drag UI~~ | ~~Done S270c — click disc → drag → cascade~~ |
| P2 | BUG-2 warning | Status: "no attached elements — place elements first" |
| ~~P3~~ | ~~BUG-3 phase-aware recompose~~ | ~~Done S270d — B1+B2 proven: `materializeLevel()` fires on Next, reads CURRENT AABBs. §BOM_NEXT logs confirmed.~~ |
| P4 | UBBL Validator | `bom_rules.js` + `disc_rules.json` done (8 rules). B1 done (scripts loaded). UI wiring (4c) remaining. |
| P5 | Materialization | Grammar + event log → NewBuilding.db |
| P6 | Share via URL | `?ref=SampleHouse&ops=...` |

### 11.6 BOM-Driven Cascade Tasks (S272 engine ready, needs wiring)

The BOM engine (438 tests) provides the algebra. These tasks wire it to visible behaviour.

| ID | Task | What the engine already does | What's missing |
|---|---|---|---|
| ~~B1~~ | ~~**Script tags**~~ | ~~Done S270d — 7 `bom_engine/*.js` scripts added to `index.html` + `sw.js` precache. All globals load in browser.~~ | — |
| ~~B2~~ | ~~**Wall→Window cascade**~~ | ~~Done S270d — `materializeBomLevel()` fires on Next, §BOM_NEXT level=1..3 confirmed. Column renamed `bom_child_id` → `M_BOM_Line_ID` (iDempiere convention).~~ | — |
| B3 | **DISC switch → MEP fresh route** | ROUTE strategy stub exists, `route_walker.js` exists | ROUTE stub returns straight line. Wire: `ROUTE(p)` → `RouteWalker.walk(db, anchors, disc)` (~10 lines) |
| B4 | **Tile recount on resize** | UNIFORM/PACKED strategies compute count from available space | verb_expand.js TILE formula not connected to BOM engine |
| B5 | **Cross-DISC read-only cascade** | `recompose()` is stateless — reads current AABBs, doesn't care who moved them | No code needed. DISC switch → `materializeLevel()` → children read current positions. Already wired in `_materializeBomLevel()`. |

**Key insight (2026-05-24 watchdog review):** "MEP rerouting after grid drag" (previously Deferred) is NOT needed. MEP never sees drag. User switches DISC → MEP reads current structural positions → RouteWalker fires once. One-way, user-triggered. No live coupling.

**Spec alignment notes (review before editing docs):**
- `BOM_ENGINE_SPEC.md §16` says ROUTE delegates to RouteWalker "in Phase 3" — Phase 3 is done but ROUTE is still a stub. §16 should say "Phase 5" or "B3 task". Fix after B3 is wired.
- ~~`RED_PILL.md §11.5 P3` (BUG-3) — CLOSED. B1+B2 wired and §BOM_NEXT logs prove `materializeLevel()` reads current AABBs.~~
- `BOM_ENGINE_SPEC.md §10` file layout lists `bom_rules.js` and `disc_rules.json` — both exist and pass tests, but §6.3 `DiscRuleProvider` still shows `// TODO: loadFromDB`. Acceptable — JSON-first is the current path, DB-load is v2.
- `NEW_FROM_REFERENCE.md` §17.9 BOM Completion Triage (items A-I) — not cross-checked against B1-B5 tasks. May have duplicates or gaps. Verify on next full review.

### 11.7 Deferred (Stage 2+)

- IFC export from NewBuilding.db
- Diagonal grids / rotation / mirroring
- Git-like branching (parallel design timelines)
- GridInteraction extraction (doc_canvas.js → grid_interaction.js, ~190 lines, low priority)

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
