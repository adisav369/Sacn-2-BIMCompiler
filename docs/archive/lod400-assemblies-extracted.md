# LOD 400 Assemblies - Extracted Inventory

## What We HAVE (Extracted from DBs)

**Date:** 2026-01-30
**Sources:** shed_test.db, tb_lktn.db, component_library.db

---

## 1. Assembly Types Implemented

### WALL_PANEL Assembly
**Status:** ✓ EXTRACTED & IMPLEMENTED

| Source | Count | Structure |
|--------|-------|-----------|
| Shed | 4 | 1 CLADDING + 4 FRAME |
| TB-LKTN | 15 | 1 CLADDING + 4 FRAME |

#### Schema
```sql
-- element_assemblies
assembly_guid TEXT PRIMARY KEY
assembly_type TEXT  -- 'WALL_PANEL'
name TEXT           -- 'SOUTH_WALL_ASSEMBLY'
total_width REAL
total_depth REAL    -- 0.15m (wall thickness)
total_height REAL   -- 2.4m (shed), 2.8m (TB-LKTN)
storey TEXT

-- assembly_components
assembly_guid TEXT
component_guid TEXT
role TEXT           -- 'FRAME' or 'CLADDING'
local_x, local_y, local_z REAL
sequence INTEGER
optional BOOLEAN
```

#### Component Roles
| Role | IFC Class | Count per Assembly | Description |
|------|-----------|-------------------|-------------|
| CLADDING | IfcPlate | 1 | External sheet |
| FRAME | IfcMember | 4 | Vertical studs |

#### Shed Wall Dimensions
```
NORTH_WALL: 4.0m × 0.15m × 2.4m (5 components)
SOUTH_WALL: 4.0m × 0.15m × 2.4m (5 components)
EAST_WALL:  0.15m × depth × 2.4m (5 components)
WEST_WALL:  0.15m × depth × 2.4m (5 components)
```

#### TB-LKTN Wall Dimensions
```
SOUTH_WALL: 11.2m × 0.15m × 2.8m
NORTH_WALL: 11.2m × 0.15m × 2.8m
WEST_WALL:  8.5m × 0.15m × 2.8m
EAST_WALL:  8.5m × 0.15m × 2.8m
+ 11 interior walls
```

---

## 2. Component Library Inventory

### From TERMINAL Extraction (component_library.db)

| IFC Class | Type | Count | Status |
|-----------|------|-------|--------|
| IfcPipeFitting | PIPE_FITTING | 4198 | ✓ Available |
| IfcFireSuppressionTerminal | SPRINKLER | 891 | ✓ Used by SprinklerPlacer |
| IfcLightFixture | LIGHT | 801 | ✓ Available |
| IfcDuctFitting | DUCT_FITTING | 683 | ✓ Available |
| IfcBeam | BEAM | 404 | ✓ Available |
| IfcMember | MEMBER | 382 | ✓ Available |
| IfcAirTerminal | DIFFUSER | 268 | ✓ Used by HVACPlacer |
| IfcFlowTerminal | FIXTURE | 253 | ✓ Available |
| IfcWindow | WINDOW | 183 | ◐ Available, not connected |
| IfcFurniture | FURNITURE | 131 | ✓ Available |
| IfcColumn | COLUMN | 122 | ✓ Used by StructuralPlacer |
| IfcDoor | DOOR | 112 | ◐ Available, not connected |
| IfcValve | VALVE | 111 | ✓ Available |
| IfcAlarm | ALARM | 71 | ✓ Available |
| IfcRailing | RAILING | 34 | ✓ Available (stairs) |
| IfcStairFlight | STAIR | 32 | ✓ Used by HybridFactory |
| IfcElectricAppliance | APPLIANCE | 19 | ✓ Available |
| IfcController | CONTROLLER | 6 | ✓ Available |

**Total: 8,698 component definitions**

---

## 3. Placers Using Library

| Placer | Component Type | Status |
|--------|----------------|--------|
| SprinklerPlacer | SPRINKLER | ✓ Functional |
| HVACPlacer | DIFFUSER | ✓ Functional |
| StructuralPlacer | COLUMN, BEAM | ✓ Functional |
| HybridFactory | STAIR, RAILING | ✓ Functional |
| FixturePlacer | FIXTURE | ◐ Exists, incomplete |

---

## 4. Assembly Types NOT Yet Implemented

### From Dictionary Spec (need residential IFC):

| Assembly Type | Components | Source Needed |
|---------------|------------|---------------|
| STUD_WALL | plates, studs, noggins | Residential IFC |
| ROOF_TRUSS | chords, webs, nail plates | Residential IFC |
| FLOOR_JOIST | joists, bearers, blocking | Residential IFC |
| DOOR_ASSEMBLY | frame, leaf, hardware | Can research |
| WINDOW_ASSEMBLY | frame, sash, glazing | Can research |

---

## 5. TERMINAL Has No Assemblies Table

```sql
-- TERMINAL (enhanced_federation_GI.db) structure:
-- NO element_assemblies table
-- Components are flat, not grouped into assemblies

-- The library contains individual components, not assembly definitions
-- Assembly patterns must be defined from standards/research
```

---

## 6. Schema for New Assembly Types

### Proposed DOOR_ASSEMBLY
```sql
INSERT INTO element_assemblies
(assembly_guid, assembly_type, name, total_width, total_depth, total_height)
VALUES ('door_001', 'DOOR_ASSEMBLY', 'D1_900x2100', 0.9, 0.04, 2.1);

-- Components
-- FRAME: timber lining + stops
-- LEAF: door panel
-- HARDWARE: hinges (3), handle set (1), lock (1)
```

### Proposed WINDOW_ASSEMBLY
```sql
INSERT INTO element_assemblies
(assembly_guid, assembly_type, name, total_width, total_depth, total_height)
VALUES ('window_001', 'WINDOW_ASSEMBLY', 'W1_1800x1000', 1.8, 0.1, 1.0);

-- Components
-- FRAME: aluminium frame
-- SASH: sliding/casement panel(s)
-- GLAZING: glass panel(s)
-- HARDWARE: locks, stays
```

---

## Summary

| Category | ✓ Extracted | ◐ Partial | ○ Pending |
|----------|-------------|-----------|-----------|
| Wall assemblies | WALL_PANEL | - | STUD_WALL |
| MEP components | 6000+ | - | - |
| Structural | COLUMN, BEAM | - | - |
| Doors | 112 defs | Not assembled | DOOR_ASSEMBLY |
| Windows | 183 defs | Not assembled | WINDOW_ASSEMBLY |
| Stairs | STAIR | - | - |
| Roof | - | - | ROOF_TRUSS |

---

*Extracted from shed_test.db, tb_lktn.db, component_library.db - 2026-01-30*
