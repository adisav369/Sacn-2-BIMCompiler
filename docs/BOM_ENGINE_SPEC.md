# BOM Recomposition Engine — Complete Specification

> **One-line:** A universal recursive engine where every element is a BOMNode. Parent provides AABB. Child fills it per strategy. `getChildren()` empty = leaf. `getParentBOM()` null = root. One algorithm, data-driven.

**Status:** Spec v4 — Java contract alignment, interface patterns defined
**Builds on:** [BOMBasedCompilation.md](BOMBasedCompilation.md) §2, §3, §4.2 | [BOM_AS_CONTEXT.md](BOM_AS_CONTEXT.md) | [REFACTOR_DOC_CANVAS.md](REFACTOR_DOC_CANVAS.md)
**Java precedent:** `DAGCompiler/src/main/java/com/bim/compiler/contract/` — 15 interfaces already define the abstract patterns. This spec is the JS port for browser-side recomposition.
**Relationship to grid_kinematics:** BOM engine **layers on top** of kinematics, consuming its attach map. L0 = geometry primitives. L1 = BOM reasoning. Both run.
**Implementing:** BBC.md §2 — Witness: W-BOM-ENGINE

---

## 1. Design Principles

### 1.1 The Object Class

Everything is a BOMNode. The engine does not know what a node IS concretely. It knows three things:
- `getParentBOM()` → null means root
- `getChildren()` → empty means leaf
- `strategy` → how children fill the parent's AABB

No vocabulary for concrete types. Only: parent, child, leaf, host AABB, tack offset, PHANTOM, BUFFER.

### 1.2 Three Patterns, One Tree

| Pattern | Role | Where |
|---------|------|-------|
| **Composite** | BOMNode tree — parent/children, recursive | `bom_node.js` |
| **Template Method** | `recompose()` — 5 fixed steps, data-driven behavior | `bom_node.js` |
| **Strategy** | Placement algebra — UNIFORM, PACKED, ROUTE, etc. | `bom_strategies.js` |

### 1.3 Data Polymorphism

No class inheritance. No `if (type === ...)`. The `m_bom_line` record carries the rules. The engine is one algorithm that reads different data. Adding a new element type = adding DB rows, not code branches.

### 1.4 Target State, Not Incremental Deltas

The engine computes **what the result should look like**, then diffs against current state. Output: KEEP/MOVE/ADD/REMOVE/SCALE commands. Idempotent — run twice, same result.

### 1.5 DB-First — No New Tables

Layout rules live on **existing `m_bom_line` columns**. Missing columns added via `ALTER TABLE` migration. No rules on a BOM line = L0 fallback (translate/scale only).

### 1.6 One Hosting Rule

```
child.hostAABB = child.parentBOM.AABB
```

Always. Every discipline. The parent BOM provides the AABB. The child fills it per its strategy. Clash checking against sibling elements is L3 validation — it does not change who the host is. (Java: `IBOMChildLine` — child knows only dx/dy/dz offset within parent, never absolute coordinates.)

### 1.7 Layered Execution — L0 + L1 + L3

```
Grid drag
  → L0: grid_kinematics (TRANSLATE, SCALE, EDGE_STRETCH, ROOF_VERTICES, ROOF_LIFT)
       ↓ attach map consumed by L1
  → L1: bom_engine.recompose() — recount, redistribute, add/remove
       ↓ target state
  → L3: bom_constraints.validate() — coherence sweep (incl. sibling clash)
       ↓ conflicts flagged
  → Diff: current vs target → commands
  → Execute via L0 primitives + scene ops
```

### 1.8 Levelled Materialization — DISC/Next Construct

The tree is NOT built all at once. Two axes, mutually exclusive:

```
Discipline axis:  ARC ↔ STR ↔ MEP ↔ FURN   (DISC switch — changes active BOM subtree)
Depth axis:       Level 0 → Level 1 → Level N  (Next — materializes one BOM level deeper)
```

**These cannot mix.** One discipline active. Within that discipline, Next/Prev walks depth. Switching discipline resets depth to 0. Same mutual exclusion as Rosetta Stone and Grid features.

Each "Next" press:
1. Takes current level's BOMNodes (already positioned, possibly dragged)
2. Queries `m_bom_line` for their children (ONE LEVEL ONLY — not recursive)
3. Children read parent's CURRENT AABB (after any drags)
4. Runs `recompose()` on new children
5. Creates level-scoped grid lines for draggable children
6. Computes PHANTOM (remaining capacity)

Each level is **decoupled**. The contractor shows up, measures the space AS IT IS, fills it per recipe.

### 1.9 The BUFFER Invariant (BBC.md §4.2)

For every non-leaf BOM, at every level:
```
parent.allocated_dim = SUM(children.allocated_dim)   per axis
```
The PHANTOM (`component_type='PHANTOM'`) absorbs the remainder:
```
PHANTOM.dim = max(0, parent.INNER.dim - SUM(children.dim))   per axis
```
PHANTOM is transparent to user — an internal guardrail. Only surfaces as "no more space" or "cannot remove mandatory element."

---

## 2. Java Contract Mapping

The DAGCompiler defined 8 abstract interfaces. The JS BOM engine maps each to a capability within the BOMNode or a separate concern:

| Java Interface | JS Equivalent | Role |
|---------------|--------------|------|
| `IBOMChildLine` | BOMNode recipe properties | What (childRef) + Where (dx/dy/dz) + How (rotationRule) + Required (isRequired → mandatory) |
| `BOMVisitor` | Template Method steps in recompose() | onSubAssembly → CASCADE, onLeaf → FILL placement, onPhantom → PHANTOM compute |
| `BOMWalker` | `bom_tree.js` materializeLevel() | Tree traversal — one level at a time in browser |
| `IHostable` | BOMNode.hostAABB + anchor | Host provides AABB, child attaches to face (anchor_face), may create opening |
| `IRepeatable` | REPEAT strategy | Template once, instance many — templateId + transform (offset, rotation, mirror) |
| `IRoutable` | ROUTE strategy (delegates to RouteWalker) | Anchor-to-anchor pairing, cross-section, clearance, waypoints |
| `IStackable` | Vertical cascade (fill_axis='y') | stackId persists across levels, grid position alignment |
| `IBOMContractor` | `bom_contractor.js` (v2) | Best-fit assignment: requirement → catalog search → candidate scoring |

### 2.1 What Carries Over Directly

From `IBOMChildLine`:
- `childRef()` → `child_product_id`
- `dx(), dy(), dz()` → `m_bom_line.dx/dy/dz`
- `rotationRule()` → `m_bom_line.rotation_rule` (literal radians OR semantic: `FACE_INTO_ROOM`, `PARALLEL_TO_WALL`)
- `sequence()` → `m_bom_line.sequence` (placement order — lower = reserves space first)
- `isRequired()` → `m_bom_line.mandatory`

From `IHostable`:
- `hostType()` → implicit from parent BOMNode's category
- `hostGuid()` → parent BOMNode's guid
- `attachmentFace()` → `m_bom_line.anchor_face`
- `createsOpening()` → derive from child creating a grid line that cuts parent's extent

From `IRepeatable`:
- `templateId()` → `child_product_id` (same product = same template)
- `transform()` → tack offset + rotation_rule
- `allowModification()` → `!mandatory` (mandatory = fixed, optional = modifiable)

### 2.2 What Needs Adaptation for Browser

| Java Pattern | Browser Adaptation | Why |
|-------------|-------------------|-----|
| `BOMWalker` walks full tree in one pass | `materializeLevel()` walks one level per Next | Browser = interactive, not batch compile |
| `IRoutable.routePath()` returns waypoints | RouteWalker.walk() already does this in JS | Already ported. ROUTE strategy delegates to it. |
| `IStackable` tracks across storeys | fill_axis='y' + storey column on m_bom_line | Same concept, data-driven |
| `IBOMContractor` does catalog search | Deferred to v2 — browser currently works from reference building, not catalog | Requires ERP.db access for full catalog scoring |
| `BOMVisitor` accumulates results | recompose() accumulates target state internally | Single visitor pattern sufficient for browser |

---

## 3. BOMNode — The Universal Node

### 3.1 Properties

```
BOMNode
  ── Identity ──
  bomId            : string    — m_bom.bom_id (parent) or leaf guid
  bomLineId        : integer   — m_bom_line.bom_child_id (PK)
  childProductId   : string    — m_bom_line.child_product_id → m_product
  categoryId       : string    — m_bom.m_product_category_id
  bomType          : string    — m_bom.bom_type (BUILDING|FLOOR|ROOM|SET|ITEM)

  ── Recipe (from m_bom_line — maps to IBOMChildLine) ──
  children[]       : BOMNode[] — recursive. Empty = leaf.
  strategy         : string    — m_bom_line.layout_strategy
  mandatory        : boolean   — m_bom_line.mandatory (= IBOMChildLine.isRequired())
  spacing_mm       : number    — m_bom_line.min_space_mm
  edge_offset_mm   : number    — m_bom_line.edge_offset_mm
  buffer_mm        : number    — m_bom_line.buffer_mm
  min_count        : integer   — m_bom_line.min_count
  max_count        : integer|null — m_bom_line.max_count
  anchor           : string    — m_bom_line.anchor_face (= IHostable.attachmentFace())
  fill_axis        : string    — m_bom_line.fill_axis ('x'|'y'|'z')
  rotation_rule    : string    — m_bom_line.rotation_rule (literal or semantic)
  fit_priority     : integer   — m_bom_line.fit_priority (= IBOMChildLine.sequence())
  qty              : integer   — m_bom_line.qty
  qty_type         : string    — m_bom_line.qty_type (VARIABLE|FIXED)
  allocatedSize    : {w,d,h}   — m_bom_line.allocated_width/depth/height_mm
  tack             : {dx,dy,dz} — m_bom_line.dx/dy/dz (parent LBD to child LBD)
  entityType       : string    — m_bom_line.entity_type (D|U|A)
  componentType    : string    — m_bom_line.component_type (MAKE|PHANTOM)

  ── Grid Properties (per level §5) ──
  creates_grid     : boolean   — m_bom_line.creates_grid
  drag_axis        : string|null — m_bom_line.drag_axis ('x'|'z'|'xz'|null)
  grid_shared_key  : string|null — m_bom_line.grid_shared_key (same key = shared grid line)
  grid_editable    : boolean   — m_bom_line.grid_editable (false = display only, UBBL-locked)

  ── Host Constraint ──
  hostAABB         : AABB      — parent BOM's current AABB. Always.

  ── State (runtime, not persisted) ──
  currentAABB      : AABB      — where this node is right now
  overridden       : boolean   — user manually repositioned (kernel_ops MOVE)
  phantom          : {w,d,h}   — remaining capacity after children placed

  ── L0 Bridge ──
  attachedGridIds[] : string[] — from kinematics attach map
  kinRelation       : string   — ATTACH|SPAN|EDGE_*|ROOF_*|INTERIOR
```

### 3.2 Invariants

- **I1:** `currentAABB ⊆ hostAABB`. Child never exceeds parent.
- **I2:** `mandatory` nodes never removed. Conflict flagged.
- **I3:** `overridden` nodes excluded from FILL. Reserved for space.
- **I4:** Leaf nodes skip Steps 2-4. Positioned by parent.
- **I5:** Tree is acyclic. One parent per child.
- **I6:** `entity_type` respected. D=dictionary, U=user, A=application.
- **I7:** `SUM(children) + PHANTOM = parent` per axis.

### 3.3 Strategy Vocabulary

| Strategy | Behavior | Java Parallel |
|----------|----------|--------------|
| `LINEAR` | Alias for UNIFORM (backwards compat) | — |
| `UNIFORM` | Equal spacing, recount on resize | `IRepeatable` with equal spacing |
| `PACKED` | Minimum gap, maximize count | Dense placement per clearance rules |
| `CENTERED` | Fixed count, centered in space | Focal placement |
| `REPEAT` | Clone entire child set with buffer | `IRepeatable.templateId()` + transform |
| `FIXED` | Never recount, proportional repositioning | Mandatory items, `isRequired()=true` |
| `SPAN` | Single child stretches to fill parent entirely | Envelope elements |
| `ROUTE` | Anchor-to-anchor pairing (delegates to RouteWalker) | `IRoutable` — MEP routing |

---

## 4. The Template Method — `recompose(hostAABB)`

Same 5 steps as v3. Maps to `BOMVisitor` events:

| Step | recompose() | BOMVisitor parallel |
|------|------------|---------------------|
| 1. FIT | Adjust AABB to host | `onSubAssembly()` — push context |
| 2. RESERVE | Mandatory children get zones | — (implicit in placement order) |
| 3. FILL | Optional children recount + place | `onLeaf()` — emit placement |
| 4. CASCADE | Recurse into child parents | Walker recurses into sub-assembly |
| 5. VALIDATE + PHANTOM | Coherence + remaining capacity | `onPhantom()` + `onSubAssemblyComplete()` |

Steps unchanged from v3 §3. See v3 for full step definitions.

---

## 5. Level-Scoped Grid Lines

### 5.1 Grid Discovery Per Level

When a BOM level materializes, grid lines are created per these rules:

| m_bom_line property | Grid behavior |
|---------------------|---------------|
| `creates_grid=1, grid_editable=1` | Draggable grid line. User can reposition child within parent. |
| `creates_grid=1, grid_editable=0` | Display-only grid line. Shows position but not draggable. (Code-mandated spans, UBBL-locked structural elements.) |
| `creates_grid=0` | No grid line. Child positioned by FILL algorithm. |
| `grid_shared_key='WIN_TYPE_A'` | All children with same key share ONE grid line. Drag one → all same-key children adjust. |
| `drag_axis='x'` | Grid line moves along X axis only (within parent AABB). |
| `drag_axis='xz'` | Grid line moves in XZ plane. |
| `drag_axis=null` | Not draggable (even if creates_grid=1). |

### 5.2 Grid Growth Per Level

Level 0 (root children): Structural grid lines — parent AABB edges. Labels: A, B, 1, 2.

Level 1 (children of Level 0): Inner partition grid lines appear. Grids grow: A → A, B, C, D...
- Where B-C might be an opening span (shared key)
- D might be an inner partition (editable)

Level 2+: Leaf-level elements. Minimal grids — only for children that have `creates_grid=1`. Most leaves are positioned by FILL, no grid.

**Minimal grid principle:** Not every child gets a grid. Only children where the user SHOULD adjust position. Opening spans share a line per product type. Structural spans show reference lines but `grid_editable=0`.

### 5.3 Grid Line Properties

```
GridLine (level-scoped)
  id              : string    — unique within level
  bomNodeId       : string    — which BOMNode this grid belongs to
  level           : integer   — BOM depth (0, 1, 2, ...)
  axis            : string    — 'x'|'z' (fill_axis of parent)
  position        : number    — current position along axis
  editable        : boolean   — from grid_editable column
  sharedKey       : string|null — from grid_shared_key
  parentGridIds[] : string[]  — which parent-level grids bound this one
  minPos          : number    — lower bound (parent AABB min on this axis)
  maxPos          : number    — upper bound (parent AABB max on this axis)
```

### 5.4 Grid Interaction Rules

- Dragging a Level N grid → recompose Level N children only
- Levels 0..N-1 grids are **frozen** while Level N is active
- Dragging a shared-key grid → all children with same `grid_shared_key` adjust (e.g., all openings of same product type resize together)
- Grid position always clamped to `[minPos, maxPos]` — child grid stays within parent AABB
- If child is itself a parent, dragging its grid changes its children's hostAABB → cascade

### 5.5 GridLineManager Interface

```js
GridLineManager
  addGridsForLevel(bomNodes[], level)  → gridLine[]
  removeGridsForLevel(level)           → void
  getEditableGrids(level)              → gridLine[]  // only draggable
  getDisplayGrids(level)               → gridLine[]  // all visible
  getSharedGroup(sharedKey)            → gridLine[]  // all lines sharing a key
  isEditable(gridId)                   → boolean
  setPosition(gridId, newPos)          → void        // clamped to [min, max]
  getAffectedBomNodes(gridId)          → BOMNode[]   // which nodes to recompose
```

---

## 6. Discipline Rule Templates — JSON/DB Dual Source

### 6.1 The Three-Stage Model (from DISC_VALIDATION_DB_SRS.md §6.3)

```
1st: AD_DocEvent_Rule (ERP.db)     → blanket discipline rules + government standards
2nd: ASI resolution (m_bom_line)   → per-instance attributes
3rd: ad_val_rule / user override   → user adds/changes/waives on specific lines
```

### 6.2 JSON Rule Templates

Discipline rules in separate JSON files — engineers examine and edit. Same schema as `AD_DocEvent_Rule`:

```
deploy/dev/rules/
  ├── disc_rules.json    — all discipline rules in one file, keyed by AD_Org
```

Schema per rule:
```json
{
  "rules": [
    {
      "ad_org_id": 0,
      "name": "SPACE_MIN_AREA",
      "rule_type": "DIMENSION",
      "check_method": "MIN_AREA",
      "standard_ref": "UBBL s.43(1)",
      "jurisdiction": "MY",
      "severity": "BLOCK",
      "params": { "min_area_m2": 9.3 },
      "condition": "bomType IN ('ROOM','SET')"
    },
    {
      "ad_org_id": 3,
      "name": "FP_SPRINKLER_SPACING",
      "rule_type": "SPACING",
      "check_method": "MAX_DISTANCE",
      "standard_ref": "NFPA 13 §8.6.2.2.1",
      "jurisdiction": "INTL",
      "severity": "BLOCK",
      "params": { "max_spacing_mm": 4600 }
    }
  ]
}
```

### 6.3 DiscRuleProvider Interface

```js
DiscRuleProvider
  loadRules(adOrgId, jurisdiction)      → Rule[]
  checkPlacement(bomNode, hostAABB, siblings, rules)
                                        → {ok: boolean, violations: Violation[]}
  // TODO: loadFromJSON(path) — load rules from JSON template
  // TODO: loadFromDB(db, adOrgId) — load from AD_DocEvent_Rule table
  // TODO: mergeUserOverrides(baseRules, userRules) — 3rd stage val_rule
```

### 6.4 Rule Types (from existing AD_DocEvent_Rule + RouteWalker)

| rule_type | check_method | What it enforces | Source |
|-----------|-------------|------------------|--------|
| SPACING | MAX_DISTANCE | Max distance between same-type children | NFPA 13, UBBL |
| SPACING | MIN_DISTANCE | Min clearance between children | RouteWalker wall_clearance |
| DIMENSION | MIN_AREA | Min area of parent AABB | UBBL room sizes |
| DIMENSION | MIN_DIMENSION | Min width/depth/height | UBBL corridor width |
| DIMENSION | DIMENSION_RANGE | Allowed W/D/H range per product | ad_val_rule (415 rows) |
| CONNECTIVITY | REQUIRED_HOST | Child must be hosted by specific parent type | IHostable.hostType() |
| COMPLETENESS | COUNT_PER_AREA | Min count per area (sprinklers per m²) | NFPA 13 |
| STANDARD | MAX_COVERAGE | Max coverage area per element | NFPA 13 coverage |
| HOST | EXCLUSION_ZONE | Min distance from specific sibling types | Gas clearance from ignition |
| PRIORITY | DISC_PRIORITY | Which discipline routes first when space conflicts | RouteWalker routing priority |

### 6.5 Integration with recompose()

Rules fire during **Step 5: VALIDATE**. Not during FILL — placement happens first, validation checks after. This matches the Java model: `BEFORE_PLACE` DocEvent fires before the walker places, but in the browser we place optimistically and flag violations.

```
recompose():
  Step 3: FILL — place children per strategy (optimistic)
  Step 4: CASCADE — recurse
  Step 5: VALIDATE
    5a: structural checks (I1-I7)
    5b: DiscRuleProvider.checkPlacement() — discipline rules
    5c: PHANTOM computation
    → violations[] returned, shown in UI
```

---

## 7. The Diff Engine

Unchanged from v3 §6-§7. Target state vs current state → KEEP/MOVE/ADD/REMOVE/SCALE commands.

### 7.1 Materialization (BOMInstanceFactory)

```js
BOMInstanceFactory
  createFromSibling(siblingGuid, bomNode, position, rotation)
    → ElementState  // clone geometry, assign to parent, log ELEMENT_PLACE to kernel_ops
  remove(guid)
    → void          // hide mesh, log to kernel_ops, retained for undo
```

Each instance inherits everything from its `m_bom_line`. The factory stamps instances at computed positions. Same pattern as RouteWalker: each emitted segment is a `kernel_ops.commitOp('ELEMENT_PLACE', ...)`.

---

## 8. DB Schema

### 8.1 New Columns (ALTER TABLE, append-only)

```sql
ALTER TABLE m_bom_line ADD COLUMN mandatory       INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN edge_offset_mm   REAL DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN buffer_mm         REAL DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN min_count         INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN max_count         INTEGER DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN fill_axis         TEXT DEFAULT 'x';
ALTER TABLE m_bom_line ADD COLUMN creates_grid      INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN drag_axis         TEXT DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN grid_shared_key   TEXT DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN grid_editable     INTEGER DEFAULT 1;
ALTER TABLE m_bom_line ADD COLUMN entity_type        TEXT DEFAULT 'D';
```

### 8.2 Existing Columns Reused

| Column | BOM Engine Usage | Java Parallel |
|--------|------------------|---------------|
| `layout_strategy` | Strategy name | — |
| `min_space_mm` | Center-to-center spacing | — |
| `anchor_face` | Attachment face | `IHostable.attachmentFace()` |
| `fit_priority` | Removal/placement order | `IBOMChildLine.sequence()` |
| `allocated_*_mm` | Child allocated dimensions | — |
| `rotation_rule` | Rotation (literal or semantic) | `IBOMChildLine.rotationRule()` |
| `qty` | Expected instance count | — |
| `qty_type` | VARIABLE or FIXED | — |
| `dx, dy, dz` | Tack offset (parent LBD → child LBD) | `IBOMChildLine.dx()/dy()/dz()` |
| `component_type` | MAKE or PHANTOM | `BOMVisitor.onPhantom()` |
| `storey` | Level grouping | `IStackable.storeys()` |
| `element_ref` | IFC GUID linkage | — |

### 8.3 One-Level Query (per Next press)

```sql
SELECT
  bl.bom_child_id, bl.bom_id AS parent_bom,
  bl.child_product_id, bl.qty, bl.qty_type, bl.sequence,
  bl.layout_strategy, bl.min_space_mm,
  bl.anchor_face, bl.fit_priority,
  bl.rotation_rule, bl.component_type,
  bl.allocated_width_mm, bl.allocated_depth_mm, bl.allocated_height_mm,
  bl.dx, bl.dy, bl.dz,
  bl.mandatory, bl.edge_offset_mm, bl.buffer_mm,
  bl.min_count, bl.max_count, bl.fill_axis,
  bl.creates_grid, bl.drag_axis, bl.grid_shared_key, bl.grid_editable,
  bl.element_ref, bl.storey,
  b.bom_type, b.m_product_category_id, b.aabb_qualifier
FROM m_bom_line bl
JOIN m_bom b ON bl.bom_id = b.bom_id
WHERE bl.is_active = 1 AND bl.bom_id = ?1
ORDER BY bl.sequence;
```

---

## 9. L0 Bridge — Consuming the Attach Map

Unchanged from v3 §9. Attach map from `grid_kinematics.js` is consumed, not replicated.

---

## 10. File Layout

```
deploy/dev/
  bom_engine/
  ├── bom_node.js           — BOMNode + recompose() Template Method
  ├── bom_tree.js           — DB → BOMNode (one level), materializeLevel(), getAffectedBranch()
  ├── bom_strategies.js     — 8 strategy functions (UNIFORM, PACKED, CENTERED, REPEAT, FIXED, SPAN, ROUTE, LINEAR)
  ├── bom_constraints.js    — L3 validation + PHANTOM computation
  ├── bom_diff.js           — Current vs target → commands
  ├── bom_grid.js           — GridLineManager: level-scoped grids, shared keys, editable flag
  └── bom_rules.js          — DiscRuleProvider: load/check discipline rules from JSON/DB

  rules/
  └── disc_rules.json       — discipline rule templates (engineers examine/edit)

  grid_kinematics.js        — L0. STAYS. 98 tests.
  grid_state.js             — Grid positions/labels. STAYS. 18 tests.
  route_walker.js           — ROUTE strategy implementation. STAYS.
  doc_canvas.js             — Orchestrator. L0 → L1 → L3 flow. DISC/Next controller.
  kernel_ops.js             — Undo/redo. Records L1 commands.
```

### 10.1 REFACTOR_DOC_CANVAS.md Alignment

- Step 1 (`grid_state.js`): **DONE** — stays.
- Step 2 (`grid_recompose.js`): **REPLACED** by `bom_engine/` (7 files).
- Step 3 (`grid_interaction.js`): **UNCHANGED** — still pending.
- Step 4 (thin `doc_canvas.js`): **UNCHANGED** — still the goal.

---

## 11. Interface Summary — The 7 Contracts

| # | Interface | File | Java Parallel | Methods |
|---|-----------|------|---------------|---------|
| I1 | **BOMNode** | `bom_node.js` | `IBOMChildLine` + `IHostable` | `recompose(hostAABB)`, `getChildren()`, `getParentBOM()` |
| I2 | **BOMTree** | `bom_tree.js` | `BOMWalker` | `materializeLevel(parentNodes[])`, `dematerializeLevel()`, `getAffectedBranch(gridLabel)` |
| I3 | **Strategy** | `bom_strategies.js` | `IRepeatable` + `IRoutable` | `UNIFORM()`, `PACKED()`, `REPEAT()`, `ROUTE()`, etc. — pure functions |
| I4 | **Constraints** | `bom_constraints.js` | `BOMVisitor.onPhantom()` | `validate(bomNode)`, `computePhantom(bomNode)` |
| I5 | **Diff** | `bom_diff.js` | — (browser-only) | `diff(current[], target[])` → commands |
| I6 | **GridLineManager** | `bom_grid.js` | `IStackable.gridPosition()` | `addGridsForLevel()`, `isEditable()`, `getSharedGroup()` |
| I7 | **DiscRuleProvider** | `bom_rules.js` | `AD_DocEvent_Rule` + `RoutingConstraints` | `loadRules()`, `checkPlacement()` |

### Deferred to v2:
| Interface | Java Parallel | Why Deferred |
|-----------|---------------|--------------|
| **BOMContractor** | `IBOMContractor` | Requires ERP.db catalog search — browser has building DB only |
| **StackValidator** | `IStackable` full validation | Cross-floor alignment needs multi-floor tree |
| **MirrorTransform** | `IRepeatable.Transform.mirrored()` | Mirror placement needs geometry mirroring |

---

## 12. Operations Catalog

### 12.1 Grid-Triggered

| # | Operation | Engine Response |
|---|-----------|-----------------|
| G1 | Grid drag (editable) | L0 → L1 recompose at active level |
| G2 | Grid drag (shared key) | All same-key children adjust |
| G3 | Grid add | New bay → recompose affected branches |
| G4 | Grid remove | Bays merge → recompose merged zone |

### 12.2 User-Triggered

| # | Operation | Engine Response |
|---|-----------|-----------------|
| U1 | Override (drag child) | Mark overridden. Excluded from FILL. |
| U2 | Promote override | Update m_bom_line columns. Clear overridden on siblings. |
| U3 | Delete child | If mandatory → CONFLICT. If optional → remove, recompose parent. |
| U4 | Point-and-move | Drag leaf to adjacent parent. Removes from old parent, adds to new. Both recompose. |

### 12.3 Level-Triggered

| # | Operation | Engine Response |
|---|-----------|-----------------|
| L1 | Next (depth++) | materializeLevel(). Children read parent's CURRENT AABB. New grids created. |
| L2 | Prev (depth--) | dematerializeLevel(). Parent state preserved. Level grids removed. |
| L3 | DISC switch | Reset depth to 0. Change active BOM subtree. |

### 12.4 Cascade

| # | Operation | Engine Response |
|---|-----------|-----------------|
| C1 | Top-down | Parent recomposes → children's hostAABB updates → children recompose |
| C2 | Conflict escalation | Child can't fit → CONFLICT flagged. UI shows. User resolves. |
| C3 | Selective | Attach map → affected guids → parent BOMNodes → recompose those subtrees. |

---

## 13. Fallback & Degradation

| Condition | Behavior |
|-----------|----------|
| No m_bom/m_bom_line in DB | L0 only. No tree. |
| layout_strategy='LINEAR', no new columns | L0 only. Backwards compatible. |
| Partial rules | Per-branch: rules → L1, no rules → L0. |
| No JSON rules file | No discipline validation. L1 still works. |
| No sibling for clone on ADD | PENDING flag. Position reserved. |
| >16ms recompose per frame | Debounce L1. L0 every frame. |

---

## 14. Testing Strategy

### 14.1 Unit Tests (~150, pure JS, no DOM)

| File | Focus | Est. |
|------|-------|------|
| `bom_strategies.js` | Each strategy × edge cases | ~35 |
| `bom_constraints.js` | Fit, overlap, buffer, mandatory, PHANTOM | ~25 |
| `bom_node.js` | recompose() 5-step, 2/3-level cascade | ~40 |
| `bom_diff.js` | Command generation from state pairs | ~15 |
| `bom_tree.js` | One-level materialization, affected branch | ~15 |
| `bom_grid.js` | Level-scoped grids, shared keys, editable | ~10 |
| `bom_rules.js` | Rule loading, placement checks | ~10 |

### 14.2 Integration Tests

| Scenario | Proves |
|----------|--------|
| Parent elongated → child count increases | UNIFORM recount |
| Parent shrunk → optional children removed, mandatory stays | REMOVE + mandatory + fit_priority |
| Shared grid drag → all same-key children adjust | grid_shared_key |
| UBBL-locked grid → not draggable | grid_editable=false |
| Level 0 drag → Level 1 materializes with new AABB | Levelled materialization |
| Level 1 drag → Level 2 adjusts | Cross-level cascade |
| DISC switch resets depth | Mutual exclusion |
| ROUTE strategy → RouteWalker | MEP integration |
| Discipline rule violation flagged | DiscRuleProvider |
| PHANTOM = 0 after fill | BUFFER invariant |

---

## 15. Implementation Order

| Step | Deliverable | Depends On | Tests |
|------|-------------|-----------|-------|
| 1 | `bom_strategies.js` — 8 pure functions | Nothing | ~35 |
| 2 | `bom_constraints.js` — validation + PHANTOM | Nothing | ~25 |
| 3 | `bom_diff.js` — state diff → commands | Nothing | ~15 |
| 4 | `bom_node.js` — BOMNode + recompose() | 1, 2 | ~40 |
| 5 | DB migration — ALTER TABLE m_bom_line | Nothing | SQL |
| 6 | `bom_grid.js` — GridLineManager | Nothing | ~10 |
| 7 | `bom_tree.js` — materializeLevel + attach map bridge | 4, 5 | ~15 |
| 8 | `bom_rules.js` — DiscRuleProvider stubs | Nothing | ~10 |
| 9 | `disc_rules.json` — UBBL + NFPA seed rules | 8 | JSON validation |
| 10 | `doc_canvas.js` rewiring — DISC/Next/L0→L1→L3 | All above | Integration |

Steps 1, 2, 3, 5, 6, 8 are independent — parallel.

---

## 16. Deferred to v2

| Item | Prerequisite |
|------|-------------|
| `IBOMContractor` (catalog best-fit search) | v1 working + ERP.db browser access |
| Non-rectangular AABB decomposition | v1 rectangular working |
| Cross-level alignment validation | v1 single-level working |
| Override promotion UX | v1 override working |
| Mirror transforms (`IRepeatable.Transform.mirrored()`) | v1 REPEAT working |
| Full NFPA/IBC/IMC rule coverage | v1 rule framework working |
| `bom_extract.js` bootstrap from element positions | v1 DB-first working |

---

## 17. Glossary — Abstract Terms Only

| Term | Definition | Source |
|------|-----------|--------|
| **Parent** | BOMNode with `getChildren()` non-empty | BBC.md §3.1 |
| **Child** | BOMNode within a parent's recipe | BBC.md §3.2 |
| **Leaf** | BOMNode with `getChildren()` empty | BBC.md §3.1 |
| **Root** | BOMNode with `getParentBOM()` null | BBC.md §3.1 |
| **Host AABB** | Parent BOM's current AABB | §1.6 |
| **Tack** | LBD attachment offset (dx/dy/dz) | BBC.md §4 |
| **PHANTOM** | Remaining capacity: `parent.INNER - SUM(children)` | BBC.md §4.2.2 |
| **BUFFER** | Invariant: `SUM(children) + PHANTOM = parent` | BBC.md §4.2 |
| **Strategy** | Pure function: host AABB + rules → child positions | §3.3 |
| **Level** | One BOM depth. Materialized per "Next" press. | §1.8 |
| **Override** | User-repositioned child. Excluded from FILL. | §12.2 U1 |
| **DISC** | Active discipline. Determines which BOM subtree is active. | §1.8 |
| **Contractor** | Best-fit assignment engine (v2). Finds closest catalog match. | `IBOMContractor` |
