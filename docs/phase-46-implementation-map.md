# Phase 46: Implementation Map

## Data Model Changes

### New Classes

```
BuildingDefinition
├── name: String
├── type: BuildingType (SINGLE_UNIT | MULTI_UNIT)
├── units: List<UnitDefinition>        // NEW
├── shared: SharedDefinition           // NEW
├── storeys: List<StoreyDef>           // DEPRECATED for multi-unit
├── roof: RoofDef
└── grid: GridDef

UnitDefinition                         // NEW
├── name: String
├── type: UnitType (RESIDENTIAL | COMMERCIAL)
├── entry: EntryType (DIRECT | SHARED) // UBBL compliance
├── storeys: List<StoreyDef>
├── meters: List<MeterDef>
└── constraints: List<UnitConstraint>

enum EntryType { DIRECT, SHARED }      // NEW

SharedDefinition                       // NEW
├── storeys: List<StoreyDef>
├── risers: List<RiserDef>
└── circulation: List<CirculationDef>

MeterDef                               // NEW
├── type: MeterType (ELECTRICAL | WATER | GAS)
├── location: String (room name)
└── position: Point3D (computed)

RiserDef                               // NEW
├── name: String
├── type: RiserType (ELECTRICAL | PLUMBING)
├── location: String
└── servesUnits: List<String>
```

### Modified Classes

```
RoomDefinition
├── ... existing fields ...
├── unitId: String                     // NEW - which unit owns this room
└── adjacentUnit: String               // NEW - party wall constraint

WallInstance
├── ... existing fields ...
├── wallType: WallType                 // NEW enum
└── fireRating: FireRating             // NEW enum

enum WallType { INTERNAL, PARTY, EXTERNAL, SHARED }

enum FireRating {                      // NEW - UBBL FRL notation
    NONE,
    FRL_30_30_30,    // 30 min
    FRL_60_60_60,    // 1 hour (party walls)
    FRL_90_90_90,    // 1.5 hour
    FRL_120_120_120  // 2 hour
}
```

## Parser Changes (BuildingParser.java)

### New Patterns

```java
// UNIT "name" type:RESIDENTIAL {
private static final Pattern UNIT_PATTERN = Pattern.compile(
    "UNIT\\s+\"([^\"]+)\"(?:\\s+type:(RESIDENTIAL|COMMERCIAL))?\\s*\\{"
);

// SHARED {
private static final Pattern SHARED_PATTERN = Pattern.compile(
    "SHARED\\s*\\{"
);

// METER electrical at:room_name
private static final Pattern METER_PATTERN = Pattern.compile(
    "METER\\s+(electrical|water|gas)\\s+at:(\\w+)"
);

// RISER "name" type:plumbing at:location
private static final Pattern RISER_PATTERN = Pattern.compile(
    "RISER\\s+\"([^\"]+)\"(?:\\s+type:(electrical|plumbing))?\\s+at:(\\w+)"
);

// type:MULTI_UNIT on BUILDING
private static final Pattern BUILDING_TYPE_PATTERN = Pattern.compile(
    "type:(SINGLE_UNIT|MULTI_UNIT)"
);

// adjacent_unit: constraint (party wall)
private static final Pattern ADJACENT_UNIT_PATTERN = Pattern.compile(
    "adjacent_unit:\\s*(\\w+)"
);
```

### Parse Logic

```java
public static BuildingDefinition parse(String dsl) {
    // ... existing header parsing ...

    BuildingType buildingType = parseBuildingType(header);

    if (buildingType == BuildingType.MULTI_UNIT) {
        // Parse UNIT blocks
        List<UnitDefinition> units = parseUnits(buildingContent);

        // Parse SHARED block
        SharedDefinition shared = parseShared(buildingContent);

        return new BuildingDefinition.Builder()
            .name(buildingName)
            .type(buildingType)
            .units(units)
            .shared(shared)
            .roof(roof)
            .build();
    } else {
        // Existing single-unit logic (backward compatible)
        // Wrap in implicit default unit
        ...
    }
}
```

## Compiler Changes (BuildingCompiler.java)

### Entry Point

```java
public IFCModel compile(BuildingDefinition building) {
    if (building.type() == BuildingType.MULTI_UNIT) {
        return compileMultiUnit(building);
    } else {
        return compileSingleUnit(building);  // existing logic
    }
}

private IFCModel compileMultiUnit(BuildingDefinition building) {
    // 1. Compile each unit independently
    Map<String, UnitCompilation> unitResults = new HashMap<>();
    for (UnitDefinition unit : building.units()) {
        unitResults.put(unit.name(), compileUnit(unit));
    }

    // 2. Compile shared spaces
    SharedCompilation shared = compileShared(building.shared());

    // 3. Resolve party walls
    List<WallInstance> partyWalls = resolvePartyWalls(unitResults);

    // 4. Merge into single model
    return mergeCompilations(unitResults, shared, partyWalls, building.roof());
}
```

### Wall Classification

```java
private WallType classifyWall(RoomInstance roomA, RoomInstance roomB) {
    String unitA = roomA.unitId();
    String unitB = roomB.unitId();

    if (unitA == null && unitB == null) {
        // Both in SHARED - internal shared wall
        return WallType.INTERNAL;
    }
    if (unitA == null || unitB == null) {
        // One in unit, one in shared
        return WallType.SHARED;
    }
    if (unitA.equals(unitB)) {
        // Same unit - internal wall
        return WallType.INTERNAL;
    }
    // Different units - party wall
    return WallType.PARTY;
}

private double getWallThickness(WallType type) {
    return switch (type) {
        case INTERNAL -> 0.150;  // 150mm
        case PARTY -> 0.250;     // 250mm fire-rated
        case SHARED -> 0.200;    // 200mm
        case EXTERNAL -> 0.250;  // 250mm
    };
}
```

## MEP Scoping Changes

### ElectricalPlacer.java

```java
// Current: building-wide circuit graph
public ElectricalGraph buildElectricalGraph(List<RoomInstance> rooms)

// New: unit-scoped with main connection
public BuildingElectricalSystem buildElectricalSystem(
    Map<String, List<RoomInstance>> roomsByUnit,
    SharedDefinition shared
) {
    Map<String, ElectricalGraph> unitGraphs = new HashMap<>();

    // Build per-unit graphs
    for (var entry : roomsByUnit.entrySet()) {
        String unitId = entry.getKey();
        List<RoomInstance> unitRooms = entry.getValue();

        // Each unit gets its own DB
        ElectricalGraph unitGraph = buildUnitElectricalGraph(unitRooms);
        unitGraphs.put(unitId, unitGraph);
    }

    // Build main distribution
    ElectricalGraph mainGraph = buildMainDistribution(unitGraphs, shared);

    return new BuildingElectricalSystem(unitGraphs, mainGraph);
}
```

### PlumbingPlacer.java

```java
// Current: single building-wide waste/vent/supply graphs
// New: per-unit branches connecting to shared risers

public BuildingPlumbingSystem buildPlumbingSystem(
    Map<String, List<RoomInstance>> roomsByUnit,
    List<RiserDef> risers
) {
    Map<String, PlumbingGraph> unitBranches = new HashMap<>();

    for (var entry : roomsByUnit.entrySet()) {
        String unitId = entry.getKey();
        List<RoomInstance> unitRooms = entry.getValue();

        // Find riser serving this unit
        RiserDef riser = findRiserForUnit(unitId, unitRooms, risers);

        // Build unit branch graph
        PlumbingGraph branch = buildUnitBranchGraph(unitRooms, riser);
        unitBranches.put(unitId, branch);
    }

    // Build riser stacks
    List<RiserStack> stacks = buildRiserStacks(risers, unitBranches);

    return new BuildingPlumbingSystem(unitBranches, stacks);
}
```

## Validation Extension

### New Validators

```java
// UnitValidator - per-unit checks
public class UnitValidator implements Validator {
    public List<ValidationError> validate(UnitDefinition unit, UnitCompilation comp) {
        List<ValidationError> errors = new ArrayList<>();

        // Minimum rooms check
        if (countHabitableRooms(comp) < 1) {
            errors.add(error("Unit must have at least one habitable room"));
        }

        // Internal circulation check
        if (!hasInternalCirculation(comp)) {
            errors.add(error("Unit rooms must be internally connected"));
        }

        // Egress check
        if (!hasEgressRoute(comp)) {
            errors.add(error("Unit must have direct exterior egress"));
        }

        return errors;
    }
}

// PartyWallValidator - fire separation
public class PartyWallValidator implements Validator {
    public List<ValidationError> validate(List<WallInstance> partyWalls) {
        List<ValidationError> errors = new ArrayList<>();

        for (WallInstance wall : partyWalls) {
            // No openings in party walls
            if (hasOpenings(wall)) {
                errors.add(error("Party wall cannot have openings: " + wall.id()));
            }

            // Minimum thickness
            if (wall.thickness() < 0.200) {
                errors.add(error("Party wall must be at least 200mm: " + wall.id()));
            }
        }

        return errors;
    }
}
```

## Witness Extension

### New Witness Types

```java
// Unit boundary witness
WITNESS unit_boundary {
    type: UNIT_SEPARATION
    units: ["A", "B"]
    party_wall_length: 14.5m
    fire_rating: "1HR"
    proven_by: party_wall_thickness >= 200mm AND no_openings
}

// Per-unit MEP completeness
WITNESS unit_mep_complete {
    type: UNIT_MEP
    unit: "A"
    electrical_db: present
    water_meter: present
    circuits: 4  // general, wet_area, lighting, dedicated
    proven_by: all_rooms_have_required_mep
}
```

## Cross-Unit Constraint Resolution

For stacked duplexes with `above:` constraints referencing rooms in other units:

```java
// In BuildingCompiler.compileMultiUnit()

private IFCModel compileMultiUnit(BuildingDefinition building) {
    // 1. Build cross-unit dependency graph
    Map<String, Set<String>> unitDependencies = buildUnitDependencies(building);

    // 2. Topologically sort units
    List<String> compilationOrder = topologicalSort(unitDependencies);

    // 3. Compile units in dependency order
    Map<String, UnitCompilation> unitResults = new LinkedHashMap<>();
    for (String unitName : compilationOrder) {
        UnitDefinition unit = building.getUnit(unitName);

        // Pass already-resolved positions for cross-unit constraints
        Map<String, RoomBounds> resolvedPositions = extractResolvedPositions(unitResults);

        UnitCompilation result = compileUnit(unit, resolvedPositions);
        unitResults.put(unitName, result);
    }

    // ... rest of compilation
}

private Map<String, Set<String>> buildUnitDependencies(BuildingDefinition building) {
    Map<String, Set<String>> deps = new HashMap<>();

    for (UnitDefinition unit : building.units()) {
        deps.put(unit.name(), new HashSet<>());

        for (StoreyDef storey : unit.storeys()) {
            for (RoomDef room : storey.rooms()) {
                // Check for cross-unit references
                if (room.above() != null) {
                    String referencedUnit = findUnitContaining(room.above(), building);
                    if (referencedUnit != null && !referencedUnit.equals(unit.name())) {
                        deps.get(unit.name()).add(referencedUnit);
                    }
                }
            }
        }
    }
    return deps;
}
```

## IFC Export: IfcZone for Units

Multi-unit buildings use IfcZone to group spaces by unit:

```java
// In BuildingWriter.java

private void writeMultiUnitZones(
    IFCModel model,
    Map<String, UnitCompilation> unitResults
) {
    for (var entry : unitResults.entrySet()) {
        String unitName = entry.getKey();
        UnitCompilation unit = entry.getValue();

        // Create IfcZone for this unit
        String zoneGuid = generateGuid("ZONE_" + unitName);
        IfcZone zone = new IfcZone(zoneGuid, "Unit_" + unitName);

        // Add to model
        model.addZone(zone);

        // Relate spaces to zone
        for (RoomInstance room : unit.rooms()) {
            IfcSpace space = model.getSpace(room.guid());
            IfcRelAssignsToGroup rel = new IfcRelAssignsToGroup(
                generateGuid("REL_ZONE_" + unitName + "_" + room.name()),
                zone,
                space
            );
            model.addRelationship(rel);
        }
    }
}
```

IFC output structure:
```
#100 = IFCBUILDING('...', 'Duplex-Pilot', ...);
#200 = IFCBUILDINGSTOREY('...', 'Ground', ...);
#300 = IFCSPACE('...', 'living_a', ...);
#301 = IFCSPACE('...', 'kitchen_a', ...);
#302 = IFCSPACE('...', 'living_b', ...);
#400 = IFCZONE('...', 'Unit_A', ...);
#401 = IFCZONE('...', 'Unit_B', ...);
#500 = IFCRELASSIGNSTOGROUP('...', #400, (#300, #301));
#501 = IFCRELASSIGNSTOGROUP('...', #401, (#302, ...));
```

## File Changes Summary

| File | Change Type | Effort |
|------|-------------|--------|
| `BuildingDefinition.java` | Add units, shared, type fields | Low |
| `UnitDefinition.java` | **NEW** | Medium |
| `SharedDefinition.java` | **NEW** | Low |
| `MeterDef.java` | **NEW** | Low |
| `RiserDef.java` | **NEW** | Low |
| `EntryType.java` | **NEW** enum | Low |
| `WallInstance.java` | Add wallType, fireRating | Low |
| `WallType.java` | **NEW** enum | Low |
| `FireRating.java` | **NEW** enum (UBBL FRL) | Low |
| `BuildingParser.java` | Add UNIT, SHARED, entry parsing | Medium |
| `BuildingCompiler.java` | Add compileMultiUnit(), cross-unit deps | High |
| `BuildingWriter.java` | Add IfcZone export | Medium |
| `ElectricalPlacer.java` | Unit-scoped graphs | Medium |
| `PlumbingPlacer.java` | Unit branches + shared risers | Medium |
| `UnitValidator.java` | **NEW** | Medium |
| `PartyWallValidator.java` | **NEW** | Low |
| `WitnessBuilder.java` | Unit witnesses | Low |

## Estimated Sub-phases

| Phase | Focus | Files | Complexity |
|-------|-------|-------|------------|
| 46A | Parser + Data Model | 8 new, 2 modified | Medium |
| 46B | Compilation Pipeline + Cross-unit deps | BuildingCompiler | High |
| 46C | Wall Classification + Fire Rating | BuildingCompiler, WallInstance | Medium |
| 46D | MEP Scoping | ElectricalPlacer, PlumbingPlacer | Medium |
| 46E | IFC Export (IfcZone) | BuildingWriter | Medium |
| 46F | Validation | 2 new validators | Low |
| 46G | Witnesses | WitnessBuilder | Low |

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Backward compatibility | Implicit single-unit wrapper for existing DSL |
| SpaceSolver complexity | Units solved independently, then merged |
| MEP graph merging | Clear interface between unit branches and shared infrastructure |
| Validation ordering | Unit validation before building validation |
