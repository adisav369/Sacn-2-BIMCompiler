# Phase 46: Multi-Unit Residential Support

**Version**: 0.46.0
**Status**: Foundation Complete (46A-D)
**Date**: 2026-02-01

## Completed Sub-phases

### 46A: Parser + Data Model
- **Enums**: BuildingType, UnitType, EntryType, WallType, FireRating
- **Records**: UnitDefinition, SharedDefinition, MeterDef, RiserDef
- **Parser**: UNIT, SHARED, METER, RISER patterns
- **Backward compat**: Single-unit buildings work unchanged

### 46B: Compilation Pipeline
- `compileMultiUnit()` - main entry point
- `buildUnitDependencies()` - cross-unit constraint graph
- `topologicalSortUnits()` - dependency-ordered compilation
- `mergeStoreysByLevel()` - combine unit storeys at same level

### 46C: Wall Classification
- `RoomSpec.unitId` - unit ownership tracking
- `WallAssemblySpec.wallType/fireRating` - wall attributes
- `classifyAndDeduplicateWalls()` - party wall detection
- Canonical ownership (Option B) for deduplication
- **Pending**: Cross-unit solver for `adjacent_unit:` constraint

### 46D: MEP Scoping
- `buildMultiUnitMEPSystems()` - per-unit MEP graphs
- `collectUnitElectrical()` - filter by room ownership
- Separate distribution boards per unit (DB=UNIT_A, DB=UNIT_B)
- Shared plumbing risers across units

## Test Results

```
Duplex-Pilot.bim:
  Building type: MULTI_UNIT
  Units: 2 (A, B)
  Rooms: 8 (4 per unit, tagged with unitId)
  Walls: 21 (classification ready, pending solver)
  MEP: 5 systems (2 per-unit electrical + 3 shared plumbing)

TB-LKTN-2S.bim (backward compat):
  Building type: SINGLE_UNIT
  Storeys: 2
  MEP: 4 systems (original behavior)
```

## Deferred to Phase 47

| Item | Reason |
|------|--------|
| 46E: IfcZone export | Independent, can wait |
| 46F: Validation | Depends on party walls |
| 46G: Witnesses | Depends on validation |
| Cross-unit solver | Complex, separate effort |

## Technical Debt

1. **adjacent_unit: constraint** - Parser pattern exists, solver doesn't enforce
2. **Party wall detection** - Code ready, needs adjacent units to test
3. **IfcZone grouping** - Implementation map exists in docs/

## Files Changed

### New Files (9)
- `BuildingType.java`, `UnitType.java`, `EntryType.java`
- `WallType.java`, `FireRating.java`
- `UnitDefinition.java`, `SharedDefinition.java`
- `MeterDef.java`, `RiserDef.java`

### Modified Files (4)
- `BuildingDefinition.java` - added units, shared, buildingType
- `BuildingParser.java` - UNIT/SHARED parsing
- `BuildingCompiler.java` - multi-unit compilation, wall classification, MEP scoping
- `MultiUnitParserTest.java`, `MultiUnitCompilerTest.java` - tests

### Documentation (3)
- `docs/dsl-extension-multi-unit.md` - DSL spec
- `docs/phase-46-implementation-map.md` - detailed design
- `docs/phase-46-summary.md` - this file

## DSL Example

```
BUILDING "Duplex-Pilot" type:MULTI_UNIT {
    UNIT "A" type:RESIDENTIAL entry:DIRECT {
        STOREY "Ground" level:0 height:2.8m {
            LIVING "living_a" size:4x5m { ... }
        }
        METER electrical at:living_a
    }
    UNIT "B" type:RESIDENTIAL entry:DIRECT {
        STOREY "Ground" level:0 height:2.8m {
            LIVING "living_b" size:4x5m {
                adjacent_unit: living_a  // Party wall (pending solver)
            }
        }
        METER electrical at:living_b
    }
    ROOF pitch:15deg
}
```
