# The Locator BIM Concept

> Brainstormed 2026-02-25. Captures the full WMS↔BIM spatial model before implementation.
> Cross-references: ARCHITECTURE.md §9, PREFAB_ARCHITECTURE.md §8

---

## 1. The Core Insight

A finished building is a **Warehouse**. It is already built — the physical space is fixed.
The floor plan is the warehouse map. Furniture and fixtures are the **stock to be put away**.
BOM compilation is the **putaway run**.

This is not a metaphor. It is the exact iDempiere WMS operational model applied to spatial geometry.

---

## 2. The ABL Hierarchy

| WMS Term | BIM Term | Table / Source |
|---|---|---|
| **Warehouse** | Building | `ad_building_registry` |
| **Aisle** | Storey (floor level) | `ad_element_rule.storey` |
| **Bin** | Room | `ad_room_boundary` |
| **Zone / Locator** | Grid Line | `ad_building_grid` |

All coordinates in **mm** throughout. The grid IS the coordinate system.

---

## 3. Grid Lines Are Zones

The structural grid (`ad_building_grid`) is not just a reference overlay.
**Each grid line IS a zone** — the physical wall face that furniture is placed against.

```
ad_building_grid for Ifc4_SampleHouse:
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
| FLOAT | — | explicit from `ad_bom_child_param.dx/dy` |

All values resolved by JOIN on `ad_building_grid`. No mm values stored in the instance record — they are **computed at createDraft() time** and written once into `wm_empty_storage_line.capacity_mm`.

---

## 4. Two-Level Naming

BOM templates and WMS instance records use different naming levels:

| Level | Column | Value example | Meaning |
|---|---|---|---|
| **BOM template** | `ad_bom_child.locator_ref` | `NORTH_WALL` | Semantic — portable across all buildings |
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

## 5. The Locator Instance IS the Orderline

The key WMS insight: there is no pre-existing M_Locator master record.
**The WMS line is the locator coming into existence** — created when a putaway order is processed.

The bin does not have a row until stock arrives. Same here:
the locator does not have a row until BOM resolution begins.

```
AD layer (templates / one-time):
  ad_building_grid    — grid line positions in mm (AUTHORITY)
  ad_room_boundary    — rooms as grid-label ranges (grid_min_x / grid_max_x / grid_min_y / grid_max_y)
  ad_bom_child        — stock catalogue with semantic locator_ref hints

Instance layer (per compilation):
  wm_empty_storage_line — THE locator instance = THE orderline
                          one row per locator per room per compilation
                          capacity_mm derived from grid at createDraft() time
                          filled_mm / remaining_mm / nextAnchor = GPD fill state
                          DocStatus: DR (in progress) → CO (complete) → VO (voided)
```

No separate M_Locator table is needed.

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
| `FLOAT` | Explicit dx/dy from `ad_bom_child_param` | No GPD walk — direct placement |
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
Capacity = explicit value from `ad_bom_child_param`.
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
| SH grid_label recalibration (`grid_min_x` etc. in `ad_room_boundary`) | Needed before W-PHANTOM-1 can be witnessed |
| `FurnitureBOMResolver.initPhantomLayout()` — capacity from grid JOIN | Witness W-PHANTOM-1 first |
| CENTRE capacity formula — min(w,h)/2 | Pinned in PhantomLayout.java javadoc |
| SURROUND layout strategy | Future — TB-LKTN courtyard scope |
| Depth clearance (front-to-back fit) | Separate — `ad_assembly_manifest` clearance path |
| Cross-locator collision detection | Separate — per-locator PhantomLayout is independent |
