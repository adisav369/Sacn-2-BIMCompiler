# The Locator BIM Concept

> **Status note (2026-02-26):** WMS↔BIM spatial concept — not yet implemented in current architecture.
> This document captures design intent for future phases. Cross-reference with
> `METADATA_DRIVEN_ARCHITECTURE.md` for what is currently implemented.

> Cross-references: ARCHITECTURE.md §9, PREFAB_ARCHITECTURE.md §8

## Version History

| Version | Date | Author | Changes |
|---|---|---|---|
| v1.0 | 2026-02-25 | Coder | Initial brainstorm — full WMS↔BIM model, ALB hierarchy, putaway flow, variance child, §1–14 |
| v1.1 | 2026-02-25 | WatchDog | BOM model readiness gate (§15); layout_strategy DEFAULT conflict flagged (§15.1); §14 open items updated against actual DB state |
| v1.2 | 2026-02-25 | WatchDog | Generative building model (§16) — TopologyMaker flow, new building = SQL only, multi-storey, known gaps |
| v1.3 | 2026-02-25 | WatchDog | Z-axis atomicity analysis (§17); Coder implementation appendix (Appendix A) — pre-conditions, Z traps, guards, witness sequence |
| v1.4 | 2026-02-25 | WatchDog | 3D bin accounting (§18); BBoxes as tags (§19); OnceOverCheck + AD_Val_EventSpace (§20); Element orientation chain (§21); conduit prefab intent (§20.3) |
| v1.5 | 2026-02-25 | WatchDog | Foundational principle (§22): EmptySpace atom = coords+tags; IFC world frame as root container; AVAIL lifecycle from one big initial EmptySpace; §5 revised |
| v1.6 | 2026-02-25 | WatchDog | Corrected acronym: ABL → ALB (Aisle/Level/Bin). §2 table updated — Aisle=Unit/Zone, Level=Storey, Bin=Room |
| v1.7 | 2026-02-25 | WatchDog | Simplification decision (§23): WMS ceremony dropped. EmptySpace extracted as the one useful concept. Compiler uses transient Java record; wm_empty_storage_line demoted to optional post-compilation summary |

---

## 1. The Core Insight

A finished building is a **Warehouse**. It is already built — the physical space is fixed.
The floor plan is the warehouse map. Furniture and fixtures are the **stock to be put away**.
BOM compilation is the **putaway run**.

This is not a metaphor. It is the exact iDempiere WMS operational model applied to spatial geometry.

---

## 2. The ALB Hierarchy

ALB = **Aisle / Level / Bin** — the three spatial coordinates of a WMS M_Locator record.

| WMS Term | BIM Term | Table / Source | Spatial meaning |
|---|---|---|---|
| **Warehouse** | Building | `ad_building_registry` | Total bounded space |
| **Aisle** | Building Unit / Zone | `ad_building_registry` (or unit label) | Lengthwise corridor — e.g., Duplex Unit 1 vs Unit 2, Terminal Zone A/B |
| **Level** | Storey | `ad_element_rule.storey` | Height band — which floor |
| **Bin** | Room | `ad_room_boundary` | Width-wise slot within a Level — the specific room |
| **Zone / Locator** | Grid Line | `ad_building_grid` | Sub-Bin placement zone (NORTH_WALL, CENTRE…) |

For a single-unit building (SampleHouse, TB-LKTN) the Aisle level collapses to the building itself.
All coordinates in **mm** throughout. The grid IS the coordinate system.

---

## 3. Grid Lines Are Zones

The structural grid (`ad_building_grid`) is not just a reference overlay.
**Each grid line IS a zone** — the physical wall face that furniture is placed against.

```
ad_building_grid for SampleHouse:
  X axis:  A(-7710)  B(-6101)  C(-4468)  D(-2834)  E(1572)  F(1667)  G(6120)  H(6410)
  Y axis:  1(-1391)  2(-1221)  3(-1101)  4(638)    5(851)   6(946)   7(2523)  8(4408)  9(4698)
```

A room is the rectangle enclosed by four grid lines.

```
BEDROOM in SH:
  west wall  = X:F  (1667mm)
  east wall  = X:G  (6120mm)
  south wall = Y:5  (851mm)
  north wall = Y:8  (4408mm)
```

The **locator address** of the north wall of this bedroom is `Y:8`.
Not "NORTH_WALL_OF_BEDROOM" — that is a human alias.
The physical WMS address is `Y:8`.

### Capacity formula

The locator's capacity is the perpendicular span of the room at that grid line:

| Locator | Axis | Capacity formula |
|---|---|---|
| North wall = Y:8 | along X | `position(X:G) − position(X:F)` |
| East wall = X:G | along Y | `position(Y:8) − position(Y:5)` |
| South wall = Y:5 | along X | same as north |
| West wall = X:F | along Y | same as east |
| CENTRE | — | `min(room_width, room_depth) / 2.0` |
| FLOAT | — | explicit from `m_attribute.dx/dy` |

All values resolved by JOIN on `ad_building_grid`. No mm values stored in the instance record — they are **computed at createDraft() time** and written once into `wm_empty_storage_line.capacity_mm`.

---

## 4. Two-Level Naming

BOM templates and WMS instance records use different naming levels:

| Level | Column | Value example | Meaning |
|---|---|---|---|
| **BOM template** | `m_bom_line.locator_ref` | `NORTH_WALL` | Semantic — portable across all buildings |
| **WMS instance** | `wm_empty_storage_line.locator_ref` | `Y:8` | Physical grid address — specific to building |

### Resolution

```
BOM locator_ref = "NORTH_WALL"
  → look up room.grid_max_y in ad_room_boundary
  → look up position_mm in ad_building_grid where axis='Y' AND grid_label = grid_max_y
  → capacity_mm = position(grid_max_x) − position(grid_min_x)
  → write wm_empty_storage_line with locator_ref = "Y:" || grid_max_y
```

The BOM says **"put this against the north wall."**
The resolver maps it to the actual grid address for this building.
The WMS line records the physical address.

---

## 5. The Initial EmptySpace and the AVAIL Lifecycle

*Revised v1.5. See §22 for the foundational principle.*

The building IS the first EmptySpace. It needs a container to sit in — that container is the
**IFC world frame** (origin 0,0,0; declared by `IfcGeometricRepresentationContext`). The world
frame is implicit and never stored. Everything else is a subdivision of the building EmptySpace,
progressively tagged as the DSL compiles.

**At DSL compile time — one record, almost no tagging:**

```
wm_empty_storage_line {
    building_type = 'SampleHouse'
    locator_ref   = 'BUILDING'
    doc_status    = 'AVAIL'          ← exists, empty, waiting
    capacity_mm   = total extent     ← computed from grid bounds
    filled_mm     = 0
    -- coords: building origin to max extents
    -- tags: building_type only
}
```

**Tags accumulate as the DSL compiles downward:**

```
Parse UNIT     → one AVAIL box, tagged to world origin only
Parse FLOORS   → box subdivides in Z — each slice gets dZ tag
Parse ROOMS    → floor slice subdivides in XY — grid_label tags added
Parse BOMs     → room subdivides into wall zones — locator_ref tag added
Allocate       → AVAIL transitions DR → GPD walk fills it → CO
```

Each step adds the minimum new tag. The BBox is never stored — it materialises from whatever
tags exist at that moment via grid JOIN. Early in compile: coarse (whole building).
By allocation time: precise (NORTH_WALL of LIVING_ROOM, 4645mm wide).

**The bin does not need to know its walls until something is being put into it.**
The warehouse exists before the shelving is installed. The shelving (wall locators, room
subdivisions) is added by the compile process, not pre-seeded by hand.

```
AD layer (templates / one-time):
  ad_building_grid    — grid line positions in mm (AUTHORITY)
  ad_room_boundary    — rooms as grid-label ranges (grid_min_x / grid_max_x / grid_min_y / grid_max_y)
  m_bom_line        — stock catalogue with semantic locator_ref hints

Instance layer (per compilation):
  wm_empty_storage_line — EmptySpace at every level of the hierarchy
                          AVAIL: exists, empty (compile time)
                          DR:    allocation in progress (BOM time)
                          CO:    allocation complete
                          VO:    voided — recompile required
```

No separate M_Locator table is needed. The EmptySpace record IS the locator at every grain.

---

## 6. The Putaway Flow (BOM Compilation)

```
1. voidForBuilding()
   → all CO lines for this building become VO
   → warehouse cleared for restock

2. For each room × locatorRef in the BOM:
   createDraft(conn, building, storey, room, locatorRef="Y:8", capacityMm, anchorXmm, ...)
   → putaway order opened (DocStatus = DR)
   → locator comes into existence

3. For each BOM child assigned to this locator (sequenceNo order):
   PhantomLayout.placeNext(place)
   M_WmEmptyStorageLine.placeChild(extentMm, newAnchorX, newAnchorY)
   → GPD advances: filled_mm increases, remaining_mm decreases, nextAnchor moves

4. complete()
   → putaway closed (DocStatus = CO)
   → locator is stocked for this compilation
```

The `nextAnchor` is the forklift position — it advances with every item placed.

---

## 7. The Variance Child = Reserved Empty Slot

`SPACER_VAR` (`is_variance=1`, NULL dims in `ad_product_dim`) is not dead space.
It is a **first-class bin occupant** — a placeholder that absorbs the gap.

```
capacity_mm  = 6500  (room wall width)
piano        = 1500mm placed → filled=1500  remaining=5000
SPACER_VAR   = 0mm at placement time → filled=1500  remaining=5000
sofa         = 2200mm placed → filled=3700  remaining=2800

remaining_mm = 2800  ← this IS the variance child's actual extent
```

`remaining_mm` at CO time tells the DSL designer:
**"this BOM fits this room with 2800mm to spare."**

If `remaining_mm < 0` → GIC violation: the BOM does not fit. Compiler rejects.

---

## 8. The Picking Flow (DSL Design)

The warehouse is stocked. The designer asks: *"where can the next piece go?"*

```java
M_WmEmptyStorageLine.getAvailableLocators(conn, building, roomName)
// → CO lines with remaining_mm > 0, ordered by remaining DESC
```

This IS a pick list. Bins with available space. The designer picks one.
Adding a new BOM child = a putaway into that bin, updating the line.

---

## 9. Shared Walls (Party Walls)

Grid line `X:G` is the east wall of Room A **and** the west wall of Room B.
One grid line → two locators → two WMS lines.

```
Room A  EAST_WALL  → locator_ref = "X:G"  capacity = span of Room A along Y
Room B  WEST_WALL  → locator_ref = "X:G"  capacity = span of Room B along Y
```

These are distinct rows in `wm_empty_storage_line` (different room_name).
The grid line address `X:G` appears in both — the grid is the shared coordinate.

Party walls, load-bearing walls, apartment dividers — all expressed naturally as shared grid lines.

---

## 10. Layout Strategies

| `layout_strategy` | Meaning | GPD behaviour |
|---|---|---|
| `LINEAR` | Pack along hostAxis from locator origin | `nextAnchor` advances sequentially |
| `FLOAT` | Explicit dx/dy from `m_attribute` | No GPD walk — direct placement |
| `SURROUND` | Radial from centroid (future) | Not yet implemented — TB-LKTN courtyard scope |

`FLOAT` = direct bin assignment. The BOM author has already decided the exact cell.
WMS equivalent: "skip the putaway algorithm — place directly at (x, y)."

---

## 11. Special Cases

### CENTRE locator
Not a grid line — a computed point (room centroid).
Capacity = `min(room_width_mm, room_depth_mm) / 2.0` — inner radius from centre.
`hostAxis` = the room's longer axis.
Stored in `wm_empty_storage_line` with `locator_ref = "CENTRE"` (convention, not grid-derived).

### FLOAT locator
Explicit override. The grid is not consulted.
Capacity = explicit value from `m_attribute`.
`locator_ref = "FLOAT"` in the WMS line.

---

## 12. What ad_room_boundary Becomes

The mm columns (`min_x_mm`, `max_x_mm`, `min_y_mm`, `max_y_mm`) become **derived values**:

```sql
-- Room extents are always computed from grid, never stored as source of truth
SELECT
  gx_min.position_mm  AS min_x_mm,
  gx_max.position_mm  AS max_x_mm,
  gy_min.position_mm  AS min_y_mm,
  gy_max.position_mm  AS max_y_mm
FROM ad_room_boundary rb
JOIN ad_building_grid gx_min ON gx_min.building_type = rb.building_type
                             AND gx_min.axis = 'X'
                             AND gx_min.grid_label = rb.grid_min_x
JOIN ad_building_grid gx_max ON gx_max.building_type = rb.building_type
                             AND gx_max.axis = 'X'
                             AND gx_max.grid_label = rb.grid_max_x
JOIN ad_building_grid gy_min ON gy_min.building_type = rb.building_type
                             AND gy_min.axis = 'Y'
                             AND gy_min.grid_label = rb.grid_min_y
JOIN ad_building_grid gy_max ON gy_max.building_type = rb.building_type
                             AND gy_max.axis = 'Y'
                             AND gy_max.grid_label = rb.grid_max_y
WHERE rb.building_type = ? AND rb.room_name = ?
```

**Calibration** = fix the grid label references in `ad_room_boundary`.
Once the labels are correct, all downstream mm values self-correct permanently.
This resolves Issue 1 (SH G8 calibration drift) at the root.

---

## 13. Current State vs Target State

| Concern | Current state | Target state |
|---|---|---|
| Room mm values | Stored directly (`EXTRACTED` from IFC) | Derived from grid label JOIN |
| Locator naming | Semantic only (`NORTH_WALL`) | Semantic in BOM, grid address in WMS line |
| Capacity source | Computed from stored mm (fragile) | Computed from grid JOIN at `createDraft()` |
| M_Locator table | None | Not needed — WMS line IS the locator instance |
| Shared walls | Not expressible | Grid line address shared across rooms naturally |
| SH calibration | G8 drift from IFC extraction errors | Fix grid_label references → all mm self-correct |

---

## 14. Open Items

| Item | Status |
|---|---|
| `m_bom_line.locator_ref` wall tagging — SH/DX BOMs | GAP — all existing rows default `FLOAT`. Data migration needed per BOM chain before GPD activates |
| `ad_room_boundary` mm calibration — SH/DX | GAP — stored `min_x_mm` etc. are G8-drifted. Code must switch to grid JOIN (§12). Grid label columns already exist |
| `layout_strategy` DEFAULT conflict | GAP — DEFAULT `'LINEAR'` conflicts with `locator_ref='FLOAT'` on all existing rows. See §15.1 |
| `is_variance=1` / SPACER_VAR seeding | GAP — no BOM chain has a variance child row yet. Must be added per BOM after locator_ref tagging |
| `FurnitureBOMResolver.initPhantomLayout()` — capacity from grid JOIN | Witness W-PHANTOM-1 first |
| CENTRE capacity formula — min(w,h)/2 | Pinned in §11 and PhantomLayout.java javadoc |
| SURROUND layout strategy | Future — TB-LKTN courtyard scope |
| Depth clearance (front-to-back fit) | Separate — `ad_assembly_manifest` clearance path |
| Cross-locator collision detection | Separate — per-locator PhantomLayout is independent |

---

## 15. BOM Model Readiness — Pre-Implementation Gate

DB inspection 2026-02-25. Coder MUST verify each item before refactoring `FurnitureBOMResolver`.

### 15.1 Schema Readiness (DONE)

| Item | Status | Evidence |
|---|---|---|
| `ad_building_grid` seeded — SH | DONE | X:8, Y:9 = 17 rows |
| `ad_building_grid` seeded — DX | DONE | X:28, Y:30 = 58 rows |
| `ad_building_grid` seeded — TB_LKTN | DONE | X:5, Y:5 = 10 rows |
| `ad_room_boundary` grid_label columns | DONE | `grid_min_x/max_x/min_y/max_y` all present |
| `m_bom_line` strategy columns | DONE | `locator_ref`, `is_variance`, `anchor_face`, `layout_strategy` |
| `m_bom_line.sequence` ordering | DONE | Column present, DEFAULT 100 |
| `SPACER_VAR` product seed | DONE | NULL dims in `ad_product_dim` |
| `wm_empty_storage_line` table | DONE | DR/CO/VO lifecycle, index on (building_type, room_name, locator_ref, doc_status) |
| `M_WmEmptyStorageLine` PO | DONE | `placeChild()`, `complete()`, `voidForBuilding()`, `getOverflows()` |

### 15.2 Data Readiness (GAPS — must resolve before refactor)

| Item | Gap | Action |
|---|---|---|
| `m_bom_line.locator_ref` seeds | All SH/DX children = `'FLOAT'` (default) | Migration: tag each BOM child with its wall (NORTH_WALL, SOUTH_WALL…) |
| `ad_room_boundary` mm values | Stored `min_x_mm` G8-drifted (e.g. SH ROOM_1 min_x=-7510, grid F=1667) | Code switch to grid JOIN; stored columns become cache/display only |
| `is_variance=1` rows | No BOM chain has a variance child | Migration: add SPACER_VAR child to each multi-piece BOM after locator tagging |

### 15.3 layout_strategy DEFAULT Conflict

`m_bom_line` defaults are `locator_ref='FLOAT'` + `layout_strategy='LINEAR'`. Every existing row is therefore FLOAT/LINEAR — which is contradictory: FLOAT locator means "use explicit dx/dy", LINEAR strategy means "GPD walk."

**Dispatch rule Coder must implement:**

```
if locator_ref == 'FLOAT':
    → use m_bom_line.dx/dy (explicit path — existing behaviour preserved)
    → layout_strategy ignored
else (NORTH_WALL, SOUTH_WALL, EAST_WALL, WEST_WALL, CENTRE):
    → use layout_strategy to drive GPD walk or radial placement
    → m_bom_line.dx/dy ignored (GPD computes them)
```

**Alternative (cleaner):** change `layout_strategy DEFAULT` from `'LINEAR'` to `'FLOAT'` — so FLOAT/FLOAT = old explicit path, any wall locator + LINEAR = GPD path. Eliminates the ambiguity in all existing rows without a data migration. Coder to decide.

### 15.4 m_attribute Clarification

`m_attribute` is a **named key-value parameter store** (z_offset, spacing, coverage_m2, head_type) used by MEP rules (NFPA source codes). It is **not** a placement offset table. Placement offsets (dx, dy, dz, rotation_rule) live directly in `m_bom_line` columns. The Three-Table Authority Rule memory entry "m_attribute = assembly-relative offset" is imprecise — correct reading: `m_bom_line.dx/dy/dz` = assembly-relative offset for FLOAT children; `m_attribute` = MEP sizing params.

---

## 16. Generative Building Model

*Added v1.2 — how TB-LKTN and any future generative building assembles under this model.*

### 16.1 Why Generative is the Native Target

EXTRACTED buildings (SH, DX) carry historical baggage: IFC global frame drift, legacy dx/dy placements, stored mm values that need calibration. Every gain for SH/DX requires a migration away from the old model.

Generative buildings (TB-LKTN, TERRACE_MY_1S, any new typology) start clean. The grid IS the coordinate system from birth. No extraction, no drift, no legacy. **M_Locator + GPD + PhantomLayout is the native assembly model for generative buildings.**

### 16.2 The Generative Assembly Flow

```
Step 1 — TopologyMaker generates grid
  UBBL strip zones → ad_building_grid rows (mm offsets from building origin)
  Each zone boundary = one grid line
  Rooms = grid-label rectangles (grid_min_x/max_x/min_y/max_y set at creation)
  No IFC extraction. No frame drift possible.

Step 2 — TopologyMaker generates rooms
  ad_room_boundary rows with grid_label references (not stored mm)
  Room extents are always: JOIN on ad_building_grid → live mm values
  UBBL compliance verified at topology time (room area ≥ UBBL minimum)

Step 3 — BOM catalog dispatched by ad_room_slot
  room_type → assembly_id lookup (BEDROOM → BEDROOM_PREFAB_MY_3100)
  BOM children tagged with locator_refs (NORTH_WALL, SOUTH_WALL, CENTRE)
  BOM is room-size-agnostic — GPD + variance child absorb any width

Step 4 — Compile: PhantomLayout per locator
  For each room × locator in BOM:
    capacity_mm = grid JOIN on room's grid_min/max labels
    createDraft(DR) → locator comes into existence
    GPD walks BOM children in sequence order
    placeChild() advances filled_mm, nextAnchor
    complete(CO) → locator stocked

Step 5 — DAGCompiler emits IFC elements
  BOM expansion → world coordinates from GPD anchor + Place.front/up
  Elements emitted with correct XY (GPD), Z (m_bom_line.dz + product height)
  wm_empty_storage_line.remaining_mm → variance report per locator
```

### 16.3 A New Building = SQL Only

```sql
-- 1. Register the building
INSERT INTO ad_building_registry (building_id, building_type, ...)
VALUES ('MY_TERRACE_B', 'TERRACE_MY_2S', ...);

-- 2. Seed grid lines (TopologyMaker generates these)
INSERT INTO ad_building_grid (building_type, axis, grid_label, position_mm)
VALUES ('TERRACE_MY_2S', 'X', 'A', 0),
       ('TERRACE_MY_2S', 'X', 'B', 5000),
       ('TERRACE_MY_2S', 'Y', '1', 0),
       ('TERRACE_MY_2S', 'Y', '2', 3100), ...;

-- 3. Seed rooms (grid-label referenced, no stored mm)
INSERT INTO ad_room_boundary (building_type, storey, room_name, room_type,
                               grid_min_x, grid_max_x, grid_min_y, grid_max_y)
VALUES ('TERRACE_MY_2S', 'Ground', 'BEDROOM_1', 'BEDROOM', 'A','B','1','2');

-- 4. BOM catalog already exists (BEDROOM_PREFAB_MY_3100, etc.)
-- 5. ad_room_slot already maps BEDROOM → BEDROOM_PREFAB_MY_3100
-- Compile → building emerges. Zero Java changes.
```

The same BEDROOM BOM fits any room width. If width=3100mm → remaining_mm=0 (perfect fit). If width=3600mm → remaining_mm=500mm (SPACER_VAR absorbs). If width=2800mm → remaining_mm<0 → GIC violation → compiler rejects before emit.

### 16.4 Multi-Storey Generative (TB-LKTN Pattern)

`ad_building_grid` has no storey column — XY grid lines are shared across all floors (same structural axes). Storey disambiguation is via `wm_empty_storage_line.storey` and `ad_room_boundary.storey`.

For stacked floors with different orientations (DX Level 2 = 180° rotation): the grid lines are in IFC global frame — already account for rotation. `locator_ref='NORTH_WALL'` on Level 2 resolves to the correct grid address for that storey. Phase 4b orientation cascade is a **grid-level concern**, not a BOM-level concern.

### 16.5 Known Gaps for Generative

| Gap | Scope | Path |
|---|---|---|
| `SURROUND` layout strategy | Non-linear rooms (TB-LKTN courtyard, atrium, rotunda) | Future — TB-LKTN courtyard scope |
| Non-rectangular grid cells | L-shaped, T-shaped plans | TopologyMaker strip algorithm is linear only; needs decomposition |
| Multi-BOM per locator | Two separate BOMs in one locator (e.g., study desk + bookshelf at NORTH_WALL) | PhantomLayout per BOM chain — two DR records for same locator/room, each with independent GPD. Not yet seeded. |
| BOM sizing vs room minimum | BEDROOM_PREFAB_MY_3100 sized for 3100mm. A 2500mm room fails. | GIC via overflow check — but designer needs feedback at typology time, not at compile time. TopologyMaker UBBL gate must cover BOM footprint, not just room area. |

---

## §17. Z-Axis Atomicity — Where the Model Holds and Where It Gaps

*WatchDog analysis 2026-02-25. Cross-ref: PREFAB_ARCHITECTURE.md §8.2, §8.5.*

### 17.1 The Claim vs the Reality

The model claims XYZ atomicity: `Place` has a 3D `anchor` (Point3D) and a 3D `hostAxis` (Vector3D). Structurally this is true. But the implementation as written has a narrower scope:

```
Record level:   XYZ atomic — Place.anchor and Place.hostAxis are full 3D vectors  ✓
GPD level:      XY only — placeChild() advances nextAnchorX + nextAnchorY, Z frozen  ✗
Data level:     m_bom_line.dz is in METERS, wm_empty_storage_line is in MM        ✗ (unit trap)
Ceiling level:  height_extent_mm = 0 for all floor Orderlines — ceiling Z unknown   ✗
```

The WMS analogy holds at the warehouse level (building = warehouse, room = bin). It gaps at the bin interior: a WMS bin is a 3D volume, but the current PhantomLayout only walks the XY floor plane of that volume.

### 17.2 The Z Chain (actual DB values)

```
Floor Orderline (ad_element_rule):
  FLOOR_DX_L1   position_value_3 = 0mm      height_extent_mm = 0  ← UNSET
  FLOOR_DX_L2   position_value_3 = 3000mm   height_extent_mm = 0  ← UNSET
  FLOOR_SH_GF   position_value_3 = 0mm      height_extent_mm = 0  ← UNSET

BOM child Z offsets (m_bom_line.dz — METERS, not mm):
  Base_Cabinet    dz = 0.0m   → floor level
  Counter_Top     dz = 0.86m  → 860mm above floor (hardcoded, not derived from product height)
  Upper_Cabinet   dz = 1.0m   → 1000mm above floor (hardcoded, not derived)
  Sprinklers      dz = 0.0m   → uses m_attribute.z_offset instead (meters)
```

**Key finding:** `Upper_Cabinet.dz = 1.0m` is hardcoded, not computed from `Base_Cabinet.height (0.86m) + clearance`. If the base cabinet changes height, the upper cabinet does not follow. This is exactly the abstraction failure the user anticipated: Z is fixed by human entry, not derived from relationships.

**Ceiling Z is uncomputable** from current data: `height_extent_mm = 0` for every floor Orderline. AD Val Rules that check element-to-ceiling clearance (e.g., NFPA sprinkler drop ≤ 300mm from ceiling) cannot be evaluated.

### 17.3 Three Z Failure Modes

**Mode 1 — Horizontal walk, fixed dz (current state)**
Furniture along a wall, all at floor level. Z = floor.position_value_3 + dz_meters × 1000.
Works. Unit conversion is the only trap.

**Mode 2 — Ceiling-referenced elements (gap)**
Sprinklers, pendant lights, ceiling fans. Z = ceiling - drop_mm.
Ceiling = floor.position_value_3 + storey_height_mm. Neither storey_height_mm (height_extent_mm=0)
nor a CEILING_* locator type exists. No M_Locator can be created at ceiling level.

**Mode 3 — Vertical GPD walk (gap)**
Bookshelves, rack systems, modular storage. hostAxis = [0,0,1].
placeChild() cannot advance Z — it takes only newAnchorXMm, newAnchorYMm.
Currently handled by hardcoded dz per child. Falls apart for generated assemblies where
product heights change.

### 17.4 The Fix — Contained, Not a Redesign

The record model is already correct. Three targeted fixes close the gaps:

**Fix 1: Populate height_extent_mm for all floor Orderlines**
```sql
-- Storey clear height is a standard value — seed it
UPDATE ad_element_rule SET height_extent_mm = 3000 WHERE element_ref LIKE 'FLOOR_DX%';
UPDATE ad_element_rule SET height_extent_mm = 2700 WHERE element_ref LIKE 'FLOOR_SH%';
UPDATE ad_element_rule SET height_extent_mm = 3000 WHERE element_ref LIKE 'FLOOR_TBLKTN%';
```
Once set, ceiling Z = `position_value_3 + height_extent_mm`. CEILING_* locators can initialise.

**Fix 2: Extend placeChild() to advance Z**
```java
// Current signature (XY only):
public void placeChild(double extentMm, double newAnchorXMm, double newAnchorYMm)

// Required signature (full 3D GPD):
public void placeChild(double extentMm, double newAnchorXMm, double newAnchorYMm, double newAnchorZMm)
```
For horizontal walks: caller passes `currentZ` (unchanged). For vertical walks: caller passes `currentZ + stride`. No existing caller breaks — they pass the same Z they received.

**Fix 3: dz unit standardisation**
`m_bom_line.dz` is in meters (matching ad_product_dim). Convert to mm at the resolver boundary — never elsewhere. One conversion point in `FurnitureBOMResolver`: `dzMm = bc.dz * 1000.0`. Document this explicitly; do not sprinkle conversions.

### 17.5 The Abstraction Gate

For arbitrary models to be genuinely abstractable (the user's core concern):

| Scenario | Current model | With fixes |
|---|---|---|
| Horizontal furniture row | Works (dz=0) | Same |
| Stacked kitchen cabinets | Works (hardcoded dz) | Same — but Upper_Cabinet dz should derive from Base_Cabinet.height |
| Ceiling sprinklers | Fails — ceiling Z unknown | Works after Fix 1 + CEILING_REF locator type |
| Bookshelf vertical stack | Fails — placeChild() XY only | Works after Fix 2 |
| Generated assembly (unknown product heights) | Fails — dz must be hardcoded | Works after Fix 2: hostAxis=[0,0,1], stride=product.height×1000 |
| Sloped surface (ramp) | Out of scope | hostAxis with Z component handles it, but room boundary must be 3D |

**The guard Coder must not violate:**
`hostAxis.z` must never be hardcoded to 0 in `FurnitureBOMResolver`. The GPD formula in PREFAB_ARCHITECTURE.md §8.2 already generalises:
```
nextZ = currentZ + hostAxis.z × stride
```
If Coder writes `hostAxis = new Vector3D(1, 0, 0)` unconditionally, vertical stacking is permanently locked out. Instead: derive hostAxis from locator type — NORTH/SOUTH/EAST/WEST → Z=0; RACK_* → Z=1; CEILING_* → Z=-1 (downward from ceiling).

---

## Appendix A: Coder Implementation Guide — Phase 4c

*WatchDog pre-implementation checklist. All items must be confirmed before refactoring `FurnitureBOMResolver`.*

### A.1 Implementation Sequence (strict order)

```
Step 0 — DB prerequisite migrations (before any Java)
  0a. UPDATE height_extent_mm for all floor Orderlines (Fix 1 above)
  0b. Decide: change layout_strategy DEFAULT to 'FLOAT' (§15.3 recommendation)
      If yes: ALTER TABLE m_bom_line ALTER COLUMN layout_strategy SET DEFAULT 'FLOAT'
      If no: document the FLOAT-locator precedence rule in FurnitureBOMResolver Javadoc

Step 1 — Data migration: locator_ref tagging
  Tag existing SH/DX BOM children with wall locators.
  Start with ONE BOM chain only (e.g. KITCHEN_CABINET_SET → SOUTH_WALL).
  Do not tag everything at once — witness W-PHANTOM-1 first (Step 3).
  Keep all others as locator_ref='FLOAT' — they compile unchanged via existing path.

Step 2 — Extend placeChild() (Fix 2)
  Add newAnchorZMm parameter. All existing callers pass currentZ (unchanged).
  This is a safe extension — no caller breaks.

Step 3 — Write witness W-PHANTOM-1 BEFORE implementing
  Claim: "KITCHEN_CABINET_SET SOUTH_WALL locator — Base_Cabinet placed at
          (gpd_origin_x, room.min_y, floor_z), remaining_mm = room_width - base_width"
  Witness proves the GPD origin is correct and capacity_mm is grid-JOIN-derived.
  Implement FurnitureBOMResolver.initPhantomLayout() only after W-PHANTOM-1 is defined.

Step 4 — Switch ad_room_boundary mm source to grid JOIN (§12 SQL)
  Do this AFTER W-PHANTOM-1 passes. One room at a time. SH KITCHEN first.
  This is the G8 calibration fix — do not combine with Step 3.

Step 5 — Extend to remaining SH/DX BOM chains (after SH kitchen passes)
  Tag locator_refs for BED_SET, LIVING_SET, TOILET_BLOCK_FIXTURES.
  Each needs its own witness extension before tagging.
```

### A.2 Unit Conversion Rules (absolute)

| Column | Unit | Conversion at resolver boundary |
|---|---|---|
| `m_bom_line.dz` | meters | `dzMm = dz * 1000.0` — one place only in FurnitureBOMResolver |
| `m_bom_line.dx`, `dy` | meters | `dxMm = dx * 1000.0` — same boundary |
| `ad_product_dim.width/depth/height` | meters | `× 1000` at resolver boundary |
| `wm_empty_storage_line.*_mm` | mm | never convert — already mm |
| `ad_building_grid.position_mm` | mm | never convert |
| `ad_element_rule.position_value_3` | mm | never convert |

**Trap:** `m_attribute.z_offset` (MEP sprinklers) is in **meters** (NFPA source). Same conversion rule. Document separately in MEPWriter — do not conflate with FurnitureBOMResolver path.

### A.3 hostAxis Derivation Rules

```java
// Derive hostAxis from locator_ref. NEVER hardcode.
Vector3D hostAxis = switch (locatorRef) {
    case "NORTH_WALL", "SOUTH_WALL" -> new Vector3D(1, 0, 0);  // walk East↔West
    case "EAST_WALL",  "WEST_WALL"  -> new Vector3D(0, 1, 0);  // walk North↔South
    case "CENTRE"                   -> longerAxisOf(room);      // walk along longer dim
    case "FLOAT"                    -> Vector3D.ZERO;            // no GPD walk
    // Future:
    // case "RACK_*"    -> new Vector3D(0, 0, 1);  // vertical stack
    // case "CEILING_*" -> new Vector3D(1, 0, 0);  // ceiling walk, Z from ceiling
    default -> throw new IllegalArgumentException("Unknown locatorRef: " + locatorRef);
};
```

`longerAxisOf(room)` = `[1,0,0]` if `room_width > room_depth` else `[0,1,0]`.

### A.4 Z Origin Rules per Locator Type

```java
double anchorZ = switch (locatorRef.startsWith("CEILING") ? "CEILING" : locatorRef) {
    case "NORTH_WALL", "SOUTH_WALL",
         "EAST_WALL",  "WEST_WALL",
         "CENTRE", "FLOAT"   -> floorZ;                          // floor-referenced
    case "CEILING"           -> floorZ + heightExtentMm;         // ceiling-referenced (Fix 1 needed)
    default                  -> floorZ;
};
// floorZ        = floor Orderline.position_value_3 (already mm)
// heightExtentMm = floor Orderline.height_extent_mm (must be non-zero — Fix 1)
```

### A.5 GIC Checks to Add

Beyond the existing `getOverflows()` (remaining_mm < 0), add before CO transition:

| Check | Rule | Blocks emit? |
|---|---|---|
| Depth fit | `element.depth_mm ≤ room_depth_at_locator_mm` | Yes — element protrudes into room centre |
| Height fit | `element.height_mm ≤ floor.height_extent_mm` | Yes — element taller than storey |
| Ceiling clearance | `element_top_z ≤ ceiling_z - min_clearance_mm` | Yes (if height_extent_mm set) |

Depth fit and height fit are new. They close the cross-locator collision gap (§14) for the most common cases without needing a full 3D spatial solver.

### A.6 First Witness Claim (write this before any implementation)

```json
{
  "claim": "W-PHANTOM-1",
  "description": "KITCHEN_CABINET_SET SOUTH_WALL — PhantomLayout capacity derived from grid JOIN",
  "assertions": [
    "capacity_mm == position_mm(grid_max_x) - position_mm(grid_min_x) for kitchen room",
    "Base_Cabinet placed at (grid_origin_x, room.min_y_mm, floor_z)",
    "Upper_Cabinet placed at (same_x, same_y, floor_z + 860)",
    "wm_empty_storage_line DocStatus == CO after BOM expansion",
    "remaining_mm >= 0 (no overflow)"
  ]
}
```

Upper_Cabinet Z = floor_z + 860 (Base_Cabinet height in mm, derived from ad_product_dim, not from hardcoded dz). This is the abstraction fix: dz for Upper_Cabinet should be `Base_Cabinet.height_m * 1000`, not the hardcoded 1000mm currently in the DB.

---

## §18. 3D Bin Accounting

*v1.4 — WatchDog 2026-02-25.*

A WMS bin is a 3D volume. The current `wm_empty_storage_line.capacity_mm` tracks only ONE dimension (width along hostAxis). A locator's full 3D extent is:

| Dimension | Direction | Current state | Required |
|---|---|---|---|
| `capacity_mm` | along `hostAxis` (wall width) | Tracked ✓ | Done |
| `capacity_depth_mm` | along `front` (room intrusion depth) | `ad_assembly_manifest` only | Add to `wm_empty_storage_line` |
| `capacity_height_mm` | along `up` (floor to ceiling) | Not tracked (height_extent_mm=0) | Add after Fix 1 (§17.4) |

**GIC check becomes 3D:**
```
element.bbox.width_mm  ≤ remaining_mm           → width fit
element.bbox.depth_mm  ≤ capacity_depth_mm      → depth fit (no protrusion into room centre)
element.bbox.height_mm ≤ capacity_height_mm     → height fit (no collision with ceiling)
```

All three must pass before DocStatus → CO. `getOverflows()` extended to report which dimension fails.

**Phase 4c scope:** `capacity_mm` only (width). `capacity_depth_mm` and `capacity_height_mm` deferred until `height_extent_mm` is populated (Fix 1, §17.4).

---

## §19. BBoxes Are Imaginary — Tags Are the Data

*v1.4 — WatchDog 2026-02-25.*

The hierarchical BBox overlay (building → floor → room → locator → element) is **never stored as geometry**. It is always computed on demand from the minimum tags:

```
Building  →  tagged to origin (0,0,0)                    [implicit]
Floor     →  tagged with one number: position_value_3     [ad_element_rule — MISSING, Phase BOM-2c]
Room      →  tagged with 4 grid labels: F,G,5,8           [ad_room_boundary.grid_min/max_x/y]
Locator   →  tagged with wall ref: NORTH_WALL             [m_bom_line.locator_ref]
Element   →  tagged with sequence + extent                [m_bom_line.sequence + ad_product_dim]
```

BBox at any level = resolve tags via `ad_building_grid` JOIN → materialise mm extents → compute. The geometry evaporates after the resolver passes through it. Only the tags persist.

**IFC alignment:** IFC `IfcLocalPlacement` IS a tag — a relative offset from parent placement. Tags → accumulated chain → IFC world coordinates. No special translation needed; the data model and the IFC placement model are structurally identical.

**The two missing tags (Phase BOM-2c):**
UNIT and FLOOR Orderlines in `ad_element_rule` — without these, rooms resolve from flat absolute coords (IFC extraction world space) instead of from the tag chain. Once seeded, the chain is unbroken from origin to leaf element.

---

## §20. OnceOverCheck — Building-Scale BBox Clash Detection

*v1.4 — WatchDog 2026-02-25.*

After all EmptySpace records reach DocStatus CO, one sweep validates the full assembled hierarchy:

```
For every pair of overlapping element BBoxes in the building:
  IF element_A.AD_Val_EventSpace PERMITS overlap WITH element_B.ifc_class
  AND element_B.AD_Val_EventSpace PERMITS overlap WITH element_A.ifc_class
  → PASS (permitted co-occupation — bilateral rule)
  ELSE
  → CLASH — GIC violation at building scale
```

**AD_Val_EventSpace** is a metadata property on each element type declaring which other element types it may share space with, and under what condition. Examples:

| Element | Permitted overlap | Condition |
|---|---|---|
| `IfcPipeSegment` | `IfcSlab` | traversal (runs through penetration) |
| `IfcCableSegment` | `IfcWall` | embedded (runs in wall cavity) |
| `IfcReinforcingBar` | `IfcBeam` / `IfcSlab` | structural embedding |
| Furniture | *none* | no co-occupation permitted |

The rule is **bilateral** — both elements must carry the matching permission. A pipe saying "I can cross slabs" does not exempt it if the slab says nothing. The slab must also declare "pipes may traverse me."

This closes the cross-locator collision gap (§14) at building scale without needing a 3D spatial solver.

### 20.1 Relationship to EmptySpace GIC

OnceOverCheck is **post-assembly**. EmptySpace GIC is **per-locator during assembly**. They are complementary:

- EmptySpace GIC: catches overflow during placement (remaining_mm < 0) — fast, per-locator
- OnceOverCheck: catches residual clashes after full assembly — slower, building-wide, catches cross-locator and cross-discipline collisions that per-locator GIC cannot see

### 20.2 Scope for Phase 4c

Not in Phase 4c scope. The per-locator GIC (§18) is sufficient for furniture placement. OnceOverCheck becomes relevant when MEP discipline BOMs are active.

### 20.3 Conduit and Traversal Disciplines (Later Session)

Conduit, pipework, cable tray, and duct routing are **prefab** — extracted from the reference IFC (SH/DX/TB-LKTN), not generated. The routing is already engineered in the Rosetta Stone. The compiler's job:

```
1. Extract conduit assembly from reference IFC → BOM children with FLOAT layout (explicit dx/dy/dz per segment)
2. Tag to parent locator (floor/wall cavity) via same grid JOIN
3. Snap into position — no routing algorithm, no path planning
4. OnceOverCheck validates placement (AD_Val_EventSpace permits wall/slab penetrations)
5. AD_Val_RuleSpace validates compliance: bend radius, separation codes, zone rules
```

The conduit "traverses space" in the reference IFC design. In our model it is a chain of FLOAT-layout BOM children — a series of segment BBoxes with explicit positions. Code compliance is a post-placement validation rule, not a placement constraint.

---

## §21. Element Orientation Chain

*v1.4 — WatchDog 2026-02-25.*

Every placed element's final world orientation is accumulated through an unbroken chain — all data-driven, nothing invented:

```
Canonical mesh orientation     (baked into component_geometries at extraction)
  × rotation_rule              (m_bom_line: FACE_INTO_ROOM / FACE_AWAY_FROM_WALL / radians)
  × FixturePlacer resolution   (resolveRotationRule() → radians)
  × Place.front + Place.up     (resolved orientation vectors in parent frame)
  × floor orientation cascade  (Phase 4b: floorOrientations map → θ rotation around Z)
  = final world orientation    → IFC LocalPlacement rotation matrix
```

The library geometry carries the manufacturer's canonical facing. The rotation rule says how it reorients in its room context (toilet faces away from plumbing wall). The floor cascade applies the building-level bearing (DX Level 2 is mirrored 180°). The accumulation is purely relational — same chain-walk as world position.

**This is already fully intact in the current implementation.** No Phase 4c change needed. Noted here for completeness as Coder reviews this document.

---

## §22. The Foundational Principle — EmptySpace as the Universal Atom

*v1.5 — WatchDog 2026-02-25. This is the principle from which §§1–21 derive.*

### 22.1 The Atom

Every node in the spatial hierarchy — building, floor, room, locator, placed element — is the
same data atom:

```
EmptySpace {
    coords  — position + extent in parent frame   (where it sits and how big it is)
    tags    — pointer to parent                   (building_type, storey, room_name, locator_ref…)
}
```

Nothing else is needed. BBox derives from coords on demand. World position accumulates tags up
the chain. EmptySpace volume at any level = parent coords − Σ(children coords). The remainder
is circulation, void, structural mass — valid unallocated space.

### 22.2 The Root Container

The building EmptySpace must sit in something. That something is the **IFC world frame**:

```
IFC world origin (0,0,0)   ← implicit container, declared by IfcGeometricRepresentationContext
                               never stored as a record — always assumed
  └── Building EmptySpace  ← first AVAIL record: coords=building extents, tag=building_type
        └── Floor           ← coords=floor slice, tag=storey + dZ
              └── Room       ← coords=grid_label bounds, tag=room_name
                    └── Locator  ← coords=wall zone, tag=locator_ref
                          └── Element  ← coords=product bbox, fills locator
```

The world frame is the only thing that is not an EmptySpace record. Everything below it is.

### 22.3 Progressive Tagging

At DSL compile time the building EmptySpace exists with almost no tags — just `building_type`
and `AVAIL`. Tags accumulate as the DSL compiles downward. The BBox at each level is never
stored — it materialises from whatever tags exist at that moment, resolved via `ad_building_grid`
JOIN. Coarse at the start, precise by allocation time.

```
Minimum tag set at each level:
  Building   →  building_type                                   (1 tag)
  Floor      →  building_type + storey + position_value_3       (3 tags)
  Room       →  + room_name + grid_min/max_x/y                  (7 tags)
  Locator    →  + locator_ref                                    (8 tags)
  Element    →  + sequence + product_ref                        (10 tags)
```

### 22.4 At Output: Tags Accumulate to IFC

Walk tags from leaf to root, accumulate coords → IFC `IfcLocalPlacement` chain.
No translation layer. The data model IS the IFC placement model.

```
IFC IfcLocalPlacement (sofa)
  .RelativePlacement.Location = (nextAnchor.x, nextAnchor.y, nextAnchor.z)
  .PlacementRelTo → IfcLocalPlacement (room)
    .PlacementRelTo → IfcLocalPlacement (floor)
      .PlacementRelTo → IfcLocalPlacement (building)
        .PlacementRelTo → world origin
```

Each level = one EmptySpace record's coords, expressed in parent frame.

### 22.5 Implications for Implementation

1. **`wm_empty_storage_line` is the universal EmptySpace table** — stores nodes at all
   levels of the hierarchy (building, floor, room, locator), distinguished by `locator_ref`
   granularity and tag completeness.

2. **Initialisation is top-down** — one AVAIL record at building level, then subdivide
   as DSL compiles. Not bottom-up accumulation from extracted room coords.

3. **OnceOverCheck** — after all AVAIL records exist for a level, verify children coords
   fit within parent coords and no siblings overlap. Runs per level before allocation begins.

4. **IFC output** — walk `wm_empty_storage_line` records leaf-to-root, accumulate coords,
   emit `IfcLocalPlacement` chain. The stored coords are already in the right frame
   (parent-relative) for direct IFC emission.

5. **The compiler becomes a tag accumulator** — it does not compute positions; it resolves
   tags to coords and accumulates them. The spatial math is reduction, not construction.

---

## §23. Simplification Decision — EmptySpace Only

*v1.7 — WatchDog 2026-02-25.*
*Decision: WMS ceremony is overkill. Extract the one useful concept: EmptySpace.*

### 23.1 What the Compiler Actually Needs

During a compilation run the compiler needs exactly one thing from the WMS model:
**how much space remains in a wall locator after placing each BOM child.**

That is three numbers and a label:

```
EmptySpace {
    locatorRef  : String    — which wall (NORTH_WALL, etc.)
    capacityMm  : double    — total wall width at compile time
    usedMm      : double    — accumulated extents of placed children
}
remainingMm() = capacityMm - usedMm
isOverflow()  = remainingMm() < 0
```

Nothing else is required during compilation. DocStatus, next_anchor coordinates, audit
timestamps, building_type/storey context — all of these are derivable from context being
passed anyway (room, locator, BOM chain). Storing them in a table is WMS ceremony,
not a compiler requirement.

### 23.2 What Gets Dropped

| WMS concept | Why overkill | Replacement |
|---|---|---|
| DocStatus DR/CO/VO lifecycle | Compilation is synchronous — begin/end is the call stack | None needed |
| `next_anchor_x/y/z_mm` persistence | GPD cursor is a local variable in `resolveWithGPD()` | Local `double anchorX` in the loop |
| `building_type`, `storey`, `room_name` columns | Already in calling context (room param) | Context param |
| `created`, `updated` audit columns | iDempiere ceremony — not a compiler concern | Omit |
| `filled_mm` column | Redundant: `filled = capacity - remaining` | Compute on demand |
| Putaway / picking flow as DB operations | Compiler is not a WMS transaction system | Not needed |

### 23.3 EmptySpace as a Java Record (Testable in orm-core)

```java
// In com.bim.orm (orm-core) — zero DB dependency
public record EmptySpace(String locatorRef, double capacityMm, double usedMm) {

    /** Remaining capacity in mm. Negative = overflow (GIC violation). */
    public double remainingMm() { return capacityMm - usedMm; }

    /** Returns a new EmptySpace after placing an item of the given extent. */
    public EmptySpace place(double extentMm) {
        return new EmptySpace(locatorRef, capacityMm, usedMm + extentMm);
    }

    public boolean isOverflow() { return remainingMm() < 0; }

    /** Factory: full wall, nothing placed yet. */
    public static EmptySpace of(String locatorRef, double capacityMm) {
        return new EmptySpace(locatorRef, capacityMm, 0);
    }
}
```

**Why orm-core?** EmptySpace has no table — it is a value object. It belongs in the shared
`com.bim.orm` module (orm-core) so any module (DAGCompiler, TopologyMaker) can use it without
circular dependencies. It is pure Java with no imports — testable with zero DB setup.

Unit test example (no mock, no DB, no fixtures):
```java
@Test void piano_sofa_loveseat_fit_north_wall() {
    EmptySpace es = EmptySpace.of("NORTH_WALL", 8869);
    es = es.place(1371);  // Piano
    es = es.place(2000);  // Sofa
    es = es.place(1600);  // Loveseat
    assertFalse(es.isOverflow());
    assertEquals(1898, es.remainingMm(), 1.0);
}
```

This test currently exists as a manual witness (W-PHANTOM-1). The `EmptySpace` record makes
it a first-class automated unit test — no SQL, no file I/O, one assertion.

### 23.4 What Happens to wm_empty_storage_line

The table is not deleted — it was already migrated. It is **demoted to an optional
post-compilation summary**: after the compiler finishes a building, a summary writer may
flush `EmptySpace` states to `wm_empty_storage_line` for ERP reporting or design-time
inspection (`remaining_mm` per locator). This is a write-only, one-way export.

The compiler **reads nothing from wm_empty_storage_line**. It creates `EmptySpace` records
from `ad_building_grid` + `ad_room_boundary` + `m_bom_line` at run time.

### 23.5 Scope for Phase 4c

Phase 4c Java work:
1. Add `EmptySpace` record to `com.bim.orm` (orm-core)
2. Replace `M_WmEmptyStorageLine` usage in `FurnitureBOMResolver.resolveWithGPD()` with
   transient `EmptySpace` locals
3. Keep `wm_empty_storage_line` table — write summary after `complete()` if needed
4. W-PHANTOM-1 becomes an `EmptySpace` unit test (no DB required)
