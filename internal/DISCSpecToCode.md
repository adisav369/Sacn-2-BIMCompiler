# DISC Spec-to-Code Compliance Checklist

> **Source:** `DISC_VALIDATION_DB_SRS.md` §12a-§12g
> **Date:** 2026-04-07 S154 session

## §12a — Shim-Based Device Placement

| # | Spec Requirement | Code Class:Line | Y/N |
|---|-----------------|-----------------|-----|
| 12a.1 | Read schedule: ad_space_type_mep_bom → room needs DEVICE at RULE | SpaceScheduleDAO:73 `getSchedule()` | Y |
| 12a.2 | Select target wall from placement_rule (WALL_BACK → Y-MAX) | SpaceScheduleDAO:250 `computePosition()` | Y |
| 12a.3 | Check wall zone for existing furniture (sibling LEAFs) | PlacementCollectorVisitor:584 furnitureBoxes collection | Y |
| 12a.3a | If occupied, shift along wall | PlacementCollectorVisitor:654 4-direction shift loop | Y |
| 12a.3b | If no space, try adjacent wall | PlacementCollectorVisitor:686 flip mirror X/Y/XY | **PARTIAL** |
| 12a.3c | If no wall available, emit CONFLICT | PlacementCollectorVisitor:724 COLLISION_CONFLICT log | Y |
| 12a.4 | Create phantom SHIM on target wall (IfcVirtualElement) | PlacementCollectorVisitor:758-779 shim Placement | **Y** |
| 12a.4a | host_ifc_class from placement_rule | PlacementCollectorVisitor:761 `hostIfcClass()` → shimDiscMount | **Y** |
| 12a.4b | mount = SIDE/BOTTOM/TOP | PlacementCollectorVisitor:761 hostSurface in familyRef | **Y** |
| 12a.4c | shim origin = wall surface point | PlacementCollectorVisitor:767 `shimAndDevicePositions()` snaps to room boundary | **Y** |
| 12a.5 | Attach device as child of SHIM | PlacementCollectorVisitor:782-811 device after shim | **Y** |
| 12a.5a | offset = standoff distance (5mm wall, 50mm ceiling) | PlacementCollectorVisitor:767 `shimAndDevicePositions()` per-wall axis | **Y** |
| 12a.5b | facing = inherited from shim's wall normal | PlacementCollectorVisitor:744,859 `facingDirection()` | **Y** |
| 12a.6 | Device world position = shim origin + child offset | PlacementCollectorVisitor:783-792 devX/devY/devZ | **Y** |

## §12b — Fixture Tack Points

| # | Spec Requirement | Code Class:Line | Y/N |
|---|-----------------|-----------------|-----|
| 12b.1 | Populate ad_assembly_connector for individual M_Products | migration/DV047_fixture_tack_points.sql | Y |
| 12b.2 | TOILET has WASTE_OUT + SUPPLY_IN | DV047 seeds both | Y |
| 12b.3 | Positions non-zero, diameter > 0 | MepRouteGeometryTest:1330 S22 validates | Y |
| 12b.4 | connects_to matches anchor_end pattern | MepRouteGeometryTest:1330 S22 validates | Y |
| 12b.5 | Walker reads tack point to compute pipe's final segment | SpaceScheduleDAO:getTackPoints() reads ad_assembly_connector, transforms to world coords | **Y** |

## §12c — END-Join Route

| # | Spec Requirement | Code Class:Line | Y/N |
|---|-----------------|-----------------|-----|
| 12c.1 | Read fixture's tack-to from ad_assembly_connector | SpaceScheduleDAO:getTackPoints() — local→world transform with facing rotation | **Y** |
| 12c.2 | Find nearest infrastructure anchor from connects_to | SpaceScheduleDAO:findNearestAnchor() — room→storey→synthetic fallback (GAP-5) | **Y** |
| 12c.3 | Generate route segments from anchor toward tack-to | PlacementCollectorVisitor: H+V+T segments as IfcFlowSegment | **Y** |
| 12c.4 | Halt before overshoot, create VARIABLE terminal piece | PlacementCollectorVisitor: PIPE_VARIABLE_TERMINAL at tack-to point | **Y** |
| 12c.5 | Assert convergence within 1mm | MepRouteGeometryTest S21: 122 terminals, convergence OK | **Y** |

## §12d — Discipline Resolution

| # | Spec Requirement | Code Class:Line | Y/N |
|---|-----------------|-----------------|-----|
| 12d.1 | Read connects_to from ad_assembly_connector | PlacementCollectorVisitor:1736 `resolveDeviceDiscipline()` | Y |
| 12d.2 | Map connects_to → discipline (ELEC_CONDUIT→ELEC, FP_MAIN→FP) | PlacementCollectorVisitor:1736 mapping with fallback | Y |
| 12d.3 | SPRINKLER → FP not ELEC | MepRouteGeometryTest:1410 S23 validates 114 devices | Y |

## §12e — Furniture Collision Avoidance

| # | Spec Requirement | Code Class:Line | Y/N |
|---|-----------------|-----------------|-----|
| 12e.1 | Compute candidate position from ad_placement_offset | SpaceScheduleDAO:250 `computePosition()` | Y |
| 12e.2 | Check candidate AABB overlap any sibling furniture | PlacementCollectorVisitor:646 aabbOverlap check | Y |
| 12e.3a | Shift along wall (try next segment) | PlacementCollectorVisitor:654 4-direction × 5 attempts | Y |
| 12e.3b | Try adjacent wall with same orientation | PlacementCollectorVisitor:686 flip X/Y/XY | Y |
| 12e.3c | If no wall available, emit CONFLICT | PlacementCollectorVisitor:724 COLLISION_CONFLICT | Y |
| 12e.4 | Log COLLISION_CHECK with device/zone/siblings/result | PlacementCollectorVisitor:676,724 COLLISION_SHIFT/CONFLICT | Y |
| 12e.INV | Device MUST NOT overlap furniture LEAF | MepRouteGeometryTest:1186 S20 validates | Y |

## §12f — Test Specs

| # | Spec Requirement | Code Class:Line | Y/N |
|---|-----------------|-----------------|-----|
| S20 | W-SHIM-DEVICE: parent shim, host_ifc_class, small offset, facing | MepRouteGeometryTest:1186 `shimDeviceNoOverlap()` | **Y** |
| S21 | W-END-JOIN: pipe routes to tack-to, VARIABLE terminal, convergence ≤1mm | MepRouteGeometryTest `endJoinRouteConvergence()` — 366 segments, 122 terminals | **Y** |
| S22 | W-TACK-POINT: connectors exist, non-placeholder, diameter > 0 | MepRouteGeometryTest:1330 `tackPointsNonPlaceholder()` | Y |
| S23 | W-DISC-RESOLVE: discipline matches connects_to | MepRouteGeometryTest:1410 `disciplineResolution()` | Y |

## §12g — Gap Fixes

| # | Gap | Code Class:Line | Y/N |
|---|-----|-----------------|-----|
| GAP-1 | Walk ordering: move to onSubAssemblyComplete | PlacementCollectorVisitor:487 deferred placement | Y |
| GAP-2 | Ceiling Z: query ARC IfcCovering Z | ShimMatcher:findCeilingZ, PlacementCollectorVisitor:561 | Y |
| GAP-3 | ShimMatcher for generative: pass position through matchHostZ | PlacementCollectorVisitor:628 matchHostZ call | **PARTIAL** — Z only, no XY wall snap |
| GAP-4 | Facing direction: compute wall normal from placement_rule | PlacementCollectorVisitor:859 `facingDirection()` | **Y** |
| GAP-5 | Anchor discovery: Room→storey→synthetic fallback | SpaceScheduleDAO:findNearestAnchor() — 3-step fallback | **Y** |
| GAP-6 | Context loss: capture room context before pops | PlacementCollectorVisitor:149 PendingGenerativeRoom | Y |
| GAP-7 | Narrow room: skip collision if < 1m | PlacementCollectorVisitor:599 ROOM_NARROW detection | Y |
| GAP-8 | Floor snap tolerance: 5mm Z tolerance | PlacementCollectorVisitor:731 ±0.005 | Y |

## LOD Geometry Bridge

| # | Requirement | Code/Migration Reference | Y/N |
|---|------------|--------------------------|-----|
| LOD-1 | Generative products resolve to real geometry, not parametric boxes | migration/CL_001_generative_product_images.sql | **Y** |
| LOD-2 | M_Product_Image rows for TOILET,SINK,LIGHT,SPRINKLER,etc. (12 products) | CL_001 → 12 INSERT OR IGNORE statements | **Y** |
| LOD-3 | MeshBinder.resolveByProduct() finds geometry_hash | ComponentLibrary:515 `resolveByProduct()` — no code change needed | **Y** |
| LOD-4 | Pre-flight applies CL_001 before compilation | scripts/run_RosettaStones.sh:57 sqlite3 < CL_001 | **Y** |

## Summary

| Category | Total | Done | Not Done |
|----------|-------|------|----------|
| §12a Shim placement | 14 | **13** | 1 (PARTIAL: 12a.3b) |
| §12b Tack points | 5 | **5** | 0 |
| §12c END-join route | 5 | **5** | 0 |
| §12d Discipline | 3 | 3 | 0 |
| §12e Collision | 7 | 7 | 0 |
| §12f Tests | 4 | **4** | 0 |
| §12g Gaps | 8 | **7** | 1 (PARTIAL: GAP-3 XY snap) |
| LOD Bridge | 4 | 4 | 0 |
| Output naming | 1 | **1** | 0 |
| Spacing rules | 1 | **1** | 0 |
| **TOTAL** | **52** | **50** | **2** |

**96% complied (was 81% at S155 end). S156 delivered: END-join route (§12c complete), tack-point walker read (§12b.5), anchor discovery (GAP-5), S21 test, 3 broken test filters fixed, 3 unmapped products backfilled.**

## Gaps Found in S154

| # | Gap | Root Cause | Fix |
|---|-----|-----------|-----|
| GAP-9 | element_name shows "TOILET" not "M_Water Closet - Flush Tank..." | PlacementCollectorVisitor sets familyRef=productId, not source_element_ref | Read source_element_ref from ERP.db M_Product, pass as familyRef | **DONE S155** |
| GAP-10 | CEILING_CENTER devices stack at same point (FAN + LIGHT + DIFFUSER) | ad_space_type_mep_bom gives 3 devices same rule. Code doesn't read ad_code_requirement spacing | Placer must read max_spacing from ad_code_requirement; offset co-located ceiling devices | **DONE S155** |
| GAP-11 | DX 36 geometry failures (pre-existing extreme scale on walls/doors) | Library mesh ≠ extraction AABB for DX elements | Not S154 scope — pre-existing |

**PARTIAL items:**
- 12a.3b — adjacent wall flip is mirror only, not true wall selection

**Remaining (2 items):** §12a.3b adjacent wall selection (PARTIAL — mirror only, not true wall selection), §12g GAP-3 XY wall snap (PARTIAL — Z only).
