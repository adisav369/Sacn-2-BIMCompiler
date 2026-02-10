# GAP ANALYSIS: Dictionary Specification vs Current Implementation

## Overview

This document compares the **bim-dsl-dictionary.md** specification with the current Java implementation to identify what construction models need to be built next.

**Analysis Date:** 2026-01-30
**Dictionary Version:** 2.0
**Implementation Phase:** 28

---

## 1. CORE DSL ELEMENTS

### BUILDING Definition
| Feature | Dictionary Spec | Implementation | Status |
|---------|----------------|----------------|--------|
| `BUILDING "<name>"` | ✓ | `BuildingDefinition.java` | IMPLEMENTED |
| `profile:` attribute | ✓ | `BuildingDefinition.profile` | IMPLEMENTED |
| `protocol:` attribute | ✓ | `BuildingDefinition.protocol` | IMPLEMENTED |
| `lod:` attribute | ✓ | `BuildingDefinition.lod` | IMPLEMENTED |

### GRID System
| Feature | Dictionary Spec | Implementation | Status |
|---------|----------------|----------------|--------|
| `axes: A,B,C / 1,2,3` | ✓ | `GridDef.xAxes, yAxes` | IMPLEMENTED |
| `spacing:` list | ✓ | `GridDef.xSpacing, ySpacing` | IMPLEMENTED |
| `bounds:A2-B4` reference | ✓ | `GridBounds.parse()` | IMPLEMENTED |
| Grid intersection points | ✓ | `GridDef.getIntersections()` | IMPLEMENTED |

### SCHEDULE Registry
| Feature | Dictionary Spec | Implementation | Status |
|---------|----------------|----------------|--------|
| `SCHEDULE doors { D1: 900x2100 }` | ✓ | `ScheduleDef` | IMPLEMENTED |
| `SCHEDULE windows { W1: 1200x1000 }` | ✓ | `ScheduleDef` | IMPLEMENTED |
| Type code resolution | ✓ | `resolveDoorType()`, `resolveWindowType()` | IMPLEMENTED |
| Description field | ✓ | `ScheduleEntryDef.description` | IMPLEMENTED |

### ENVELOPE
| Feature | Dictionary Spec | Implementation | Status |
|---------|----------------|----------------|--------|
| `FOUNDATION` | ✓ | `FoundationDef` | IMPLEMENTED |
| Foundation types (SLAB, STRIP, etc.) | ✓ | `FoundationType enum` | IMPLEMENTED |
| `PERIMETER_DRAIN` | ✓ | `DrainageDef` | IMPLEMENTED |
| `ROOF` correlation | ✓ | `validateRoofDrainCorrelation()` | IMPLEMENTED |
| `GUTTER` | Specified | Not implemented | **GAP** |
| `DOWNPIPE` locations | ✓ | `DrainageDef.downpipeLocations` | IMPLEMENTED |

### STOREY
| Feature | Dictionary Spec | Implementation | Status |
|---------|----------------|----------------|--------|
| `STOREY "<name>"` | ✓ | `StoreyDef` | IMPLEMENTED |
| `level:` | ✓ | `StoreyDef.level` | IMPLEMENTED |
| `height:` | ✓ | `StoreyDef.height` | IMPLEMENTED |
| Rooms list | ✓ | `StoreyDef.rooms` | IMPLEMENTED |
| Stairs/Landings | ✓ | `StairDef`, `LandingDef` | IMPLEMENTED |

---

## 2. SPACE & SPACETYPE

### SpaceType Enum
| Type | Dictionary | `RoomType.java` | Status |
|------|------------|-----------------|--------|
| BEDROOM | ✓ | ✓ | IMPLEMENTED |
| MASTER_BEDROOM | ✓ | ✓ | IMPLEMENTED (Phase 29) |
| BATHROOM | ✓ | ✓ | IMPLEMENTED |
| KITCHEN | ✓ | ✓ | IMPLEMENTED |
| WET_KITCHEN | ✓ | ✓ | IMPLEMENTED (Phase 29) |
| DINING | ✓ | ✓ | IMPLEMENTED (Phase 29) |
| LIVING | ✓ | ✓ | IMPLEMENTED |
| CORRIDOR | ✓ | ✓ | IMPLEMENTED |
| LOBBY | ✓ | ✓ | IMPLEMENTED |
| OFFICE | ✓ | ✓ | IMPLEMENTED |
| STORAGE | ✓ | ✓ | IMPLEMENTED |
| GARAGE | ✓ | ✓ | IMPLEMENTED |
| PORCH | ✓ | ✓ | IMPLEMENTED |
| CAR_PORCH | ✓ | ✓ | IMPLEMENTED (Phase 29) |
| VERANDAH | ✓ | ✓ | IMPLEMENTED (Phase 29) |
| OPEN_PLAN | ✓ | ✓ | IMPLEMENTED |
| DEPARTURE_LOUNGE | ✓ | ✓ | IMPLEMENTED |
| GATE | ✓ | ✓ | IMPLEMENTED |
| CONCOURSE | ✓ | ✓ | IMPLEMENTED |
| GENERIC | ✓ | ✓ | IMPLEMENTED |
| TOILET | Specified | Maps to BATHROOM | OK |
| STUDY | Specified | Maps to OFFICE | OK |
| UTILITY | Specified | Maps to STORAGE | OK |
| LAUNDRY | Specified | Maps to WET_KITCHEN | OK |
| ENSUITE | Specified | Not distinct type | **GAP** |
| POWDER_ROOM | Specified | Not distinct type | **GAP** |
| JACK_AND_JILL | Specified | Not distinct type | **GAP** |
| SUNROOM | Specified | Not distinct type | **GAP** |
| BALCONY | Specified | Not distinct type | **GAP** |
| STAIR | Specified | Not distinct type | **GAP** |

### WallRule Enum
| Rule | Dictionary | `RoomType.WallRule` | Status |
|------|------------|---------------------|--------|
| ENCLOSED | ✓ | ✓ | IMPLEMENTED |
| PERIMETER_ONLY | ✓ | ✓ | IMPLEMENTED |
| NONE | ✓ | ✓ | IMPLEMENTED |
| AS_REQUIRED | ✓ | ✓ | IMPLEMENTED |

### Malaysian Vocabulary
| Term | Mapping | `tryExactMatch()` | Status |
|------|---------|-------------------|--------|
| BILIK_TIDUR, BT | BEDROOM | ✓ | IMPLEMENTED (Phase 29) |
| BILIK_UTAMA, MR | MASTER_BEDROOM | ✓ | IMPLEMENTED (Phase 29) |
| BILIK_MANDI | BATHROOM | ✓ | IMPLEMENTED (Phase 29) |
| TANDAS, WC | BATHROOM | ✓ | IMPLEMENTED (Phase 29) |
| DAPUR | KITCHEN | ✓ | IMPLEMENTED (Phase 29) |
| DAPUR_BASUH, BASUH | WET_KITCHEN | ✓ | IMPLEMENTED (Phase 29) |
| RUANG_TAMU, RT, TAMU | LIVING | ✓ | IMPLEMENTED (Phase 29) |
| RUANG_MAKAN, MAKAN | DINING | ✓ | IMPLEMENTED (Phase 29) |
| ANJUNG | PORCH | ✓ | IMPLEMENTED (Phase 29) |
| ANJUNG_KERETA, CP | CAR_PORCH | ✓ | IMPLEMENTED (Phase 29) |
| SERAMBI | VERANDAH | ✓ | IMPLEMENTED (Phase 29) |

---

## 3. CONSTRAINTS

| Constraint | Dictionary | Implementation | Status |
|------------|------------|----------------|--------|
| `exterior: <direction>` | ✓ | `RoomDef.exteriorWall`, `exteriorWalls` | IMPLEMENTED |
| `opens_to: <room>` | ✓ | `RoomDef.opensTo` | IMPLEMENTED |
| `adjacent: <room>` | ✓ | `RoomDef.adjacentTo` | IMPLEMENTED |
| `not_adjacent: <room>` | ✓ | `RoomDef.notAdjacentTo` | IMPLEMENTED |
| `stack: <name>` | ✓ | `RoomDef.stack` | IMPLEMENTED |
| `above: <room>` | ✓ | `RoomDef.above` | IMPLEMENTED |
| `below: <room>` | ✓ | `RoomDef.below` | IMPLEMENTED |
| `aligns: <room>` | ✓ | `RoomDef.alignsWith` | IMPLEMENTED |
| Constraint solver | Partially | `IntentCompiler` (basic) | **PARTIAL** |

---

## 4. OPENINGS

### DOOR
| Feature | Dictionary | Implementation | Status |
|---------|------------|----------------|--------|
| `DOOR wall: <dir>` | ✓ | `OpeningDef` | IMPLEMENTED |
| `type: D1` schedule ref | ✓ | `OpeningDef.typeCode` | IMPLEMENTED |
| `offset:` position | Specified | Not implemented | **GAP** |
| `connectsTo:` | ✓ | `OpeningDef.connectsTo` | IMPLEMENTED |

### WINDOW
| Feature | Dictionary | Implementation | Status |
|---------|------------|----------------|--------|
| `WINDOW wall: <dir>` | ✓ | `OpeningDef` | IMPLEMENTED |
| `type: W1` schedule ref | ✓ | `OpeningDef.typeCode` | IMPLEMENTED |
| `sill:` height | Specified | Not implemented | **GAP** |
| `offset:` position | Specified | Not implemented | **GAP** |

---

## 5. ZONES (OPEN_PLAN)

| Feature | Dictionary | Implementation | Status |
|---------|------------|----------------|--------|
| `ZONE "<name>"` in OPEN_PLAN | ✓ | `RoomDef.zones` | PARTIAL |
| Zone position | Specified | Not implemented | **GAP** |
| Zone fixtures | Specified | Not implemented | **GAP** |
| Zone furniture hints | Specified | Not implemented | **GAP** |

---

## 6. FIXTURES

| Fixture Type | Dictionary | Implementation | Status |
|--------------|------------|----------------|--------|
| TOILET | Specified | Not implemented | **GAP** |
| SINK | Specified | Not implemented | **GAP** |
| SHOWER | Specified | Not implemented | **GAP** |
| BATHTUB | Specified | Not implemented | **GAP** |
| STOVE_POINT | Specified | Not implemented | **GAP** |
| EXHAUST_HOOD | Specified | Not implemented | **GAP** |
| COUNTER | Specified | Not implemented | **GAP** |
| WARDROBE | Specified | Not implemented | **GAP** |
| BED (furniture hint) | Specified | Not implemented | **GAP** |

**Note:** `FixturePlacer.java` exists but fixture dictionary not fully implemented.

---

## 7. MEP DICTIONARY

### Electrical
| Component | Dictionary | Implementation | Status |
|-----------|------------|----------------|--------|
| CEILING_LIGHT | Specified | `LightDefinition.java` exists | PARTIAL |
| CEILING_FAN | Specified | Not implemented | **GAP** |
| POWER_OUTLET | Specified | Not implemented | **GAP** |
| SWITCH | Specified | Not implemented | **GAP** |
| DISTRIBUTION_BOARD | Specified | Not implemented | **GAP** |

### Plumbing
| Component | Dictionary | Implementation | Status |
|-----------|------------|----------------|--------|
| WATER_SUPPLY | Specified | Not implemented | **GAP** |
| WASTE_DRAIN | Specified | Not implemented | **GAP** |

### HVAC
| Component | Dictionary | Implementation | Status |
|-----------|------------|----------------|--------|
| AIR_CONDITIONING | Specified | `HVACPlacer.java` exists | PARTIAL |
| EXHAUST_FAN | Specified | Not implemented | **GAP** |

### Fire Protection
| Component | Dictionary | Implementation | Status |
|-----------|------------|----------------|--------|
| SPRINKLER | ✓ | `SprinklerPlacer.java` | IMPLEMENTED |
| SMOKE_DETECTOR | Specified | Not implemented | **GAP** |

---

## 8. PROFILES

| Profile | Dictionary | `ProfileRegistry.java` | Status |
|---------|------------|------------------------|--------|
| Malaysian_Residential | ✓ | ✓ | IMPLEMENTED |
| US_Residential_IRC | ✓ | ✓ | IMPLEMENTED |
| UK_Residential | ✓ | ✓ | IMPLEMENTED |
| Commercial_IBC | ✓ | ✓ | IMPLEMENTED |
| Profile inheritance | Specified | Not implemented | **GAP** |
| Climate attribute | Specified | Not implemented | **GAP** |

---

## 9. SPECIALIZATION (Type Hierarchy)

| Feature | Dictionary | Implementation | Status |
|---------|------------|----------------|--------|
| `specializes` keyword | Specified | Not implemented | **GAP** |
| MASTER_BEDROOM → BEDROOM | Specified | Flat enum only | **GAP** |
| ENSUITE → BATHROOM | Specified | Not implemented | **GAP** |
| GALLEY_KITCHEN → KITCHEN | Specified | Not implemented | **GAP** |
| Inheritance rules | Specified | Not implemented | **GAP** |

**Note:** Dictionary specifies full type hierarchy with inheritance. Current implementation uses flat enum.

---

## 10. LOD LEVELS

| Feature | Dictionary | Implementation | Status |
|---------|------------|----------------|--------|
| LOD 100 (Conceptual) | Specified | Not validated | **GAP** |
| LOD 200 (Approximate) | Specified | Not validated | **GAP** |
| LOD 300 (Precise) | Default | Implicit | PARTIAL |
| LOD 350 (Coordination) | Specified | Not validated | **GAP** |
| LOD 400 (Fabrication) | Specified | Library components | PARTIAL |
| LOD 500 (As-Built) | Specified | Not implemented | **GAP** |
| LOD-based validation | Specified | `LODValidatorRegistry` not found | **GAP** |

---

## 11. PROTOCOLS (Building Type Templates)

| Protocol | Dictionary | `ProtocolValidator.java` | Status |
|----------|------------|--------------------------|--------|
| Residential_Single_Storey | ✓ | Basic check | PARTIAL |
| Residential_Multi_Storey | Specified | Not implemented | **GAP** |
| Apartment_Unit | Specified | Not implemented | **GAP** |
| Commercial_Office | Specified | Not implemented | **GAP** |
| Airport_Terminal | Specified | Not implemented | **GAP** |
| required_spaces validation | Specified | Not fully implemented | **GAP** |
| excluded_spaces validation | Specified | Not fully implemented | **GAP** |
| required_relationships | Specified | Not implemented | **GAP** |

---

## 12. VALIDATION

### Geometry Validators (Implemented)
- WallThicknessValidator ✓
- PipeDiameterValidator ✓
- OpeningPlacementValidator ✓
- MultiStoryElementValidator ✓
- MepStructureClearanceValidator ✓
- SprinklerSpacingValidator ✓

### Missing Validators (Dictionary Specified)
| Validator | Purpose | Status |
|-----------|---------|--------|
| HabitabilityValidator | Natural light, min area | **GAP** |
| EgressValidator | Emergency exit paths | **GAP** |
| StackValidator | Plumbing stack alignment | **GAP** |
| ProfileValidator | Code-specific rules | **GAP** |
| ProtocolValidator | Building type rules | PARTIAL |
| LODValidator | Content completeness | **GAP** |
| ConnectivityValidator | Door/corridor paths | **GAP** |

---

## 13. OUTPUT PIPELINE

| Output | Dictionary | Implementation | Status |
|--------|------------|----------------|--------|
| Federated DB (.db) | ✓ | `BuildingWriter.java` | IMPLEMENTED |
| IFC Export (.ifc) | ✓ | `DSLExporter.java` | IMPLEMENTED |
| BOM Export (.csv) | Specified | Not implemented | **GAP** |
| Blender Bake | ✓ | Python scripts | IMPLEMENTED |

---

## PRIORITY GAPS (Recommended Next Steps)

### High Priority (Core Functionality)
1. **FIXTURE Dictionary** - TOILET, SINK, SHOWER placement
2. **Opening offset/sill** - Precise door/window positioning
3. **ZONE geometry** - OPEN_PLAN zone boundaries
4. **HabitabilityValidator** - Natural light, min area checks
5. **BOM Export** - Quantities for ERP integration

### Medium Priority (Enhanced Features)
6. **LOD Validation** - Content completeness per LOD level
7. **Protocol Validation** - Full required_spaces/relationships
8. **SpaceType Specialization** - Type hierarchy with inheritance
9. **MEP Electrical** - Outlets, switches, lighting circuits
10. **Profile Inheritance** - Extending profiles

### Lower Priority (Advanced Features)
11. **GUTTER modeling** - Envelope drainage chain
12. **Furniture Hints** - BED, SOFA placement for LOD 400
13. **AS_BUILT LOD 500** - Survey deviation tracking
14. **Full Terminal Protocol** - Airport-specific validation

---

## IMPLEMENTATION STATISTICS

| Category | Specified | Implemented | Coverage |
|----------|-----------|-------------|----------|
| SpaceTypes | 30+ | 20 | ~67% |
| Constraints | 8 | 8 | 100% |
| Profiles | 4 | 4 | 100% |
| Validators | 15+ | 6 | ~40% |
| Fixtures | 20+ | 0 | 0% |
| MEP Components | 15+ | 2 | ~13% |
| LOD Levels | 6 | 2 | ~33% |
| Protocols | 5 | 1 | 20% |

**Overall Implementation Coverage: ~50%**

---

*Generated from bim-dsl-dictionary.md analysis - 2026-01-30*
