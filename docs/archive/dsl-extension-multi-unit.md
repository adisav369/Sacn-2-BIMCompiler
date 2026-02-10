# DSL Extension: Multi-Unit Residential Support

**Phase 46 Proposal** - Adding UNIT abstraction for apartments/duplexes

## Current DSL Hierarchy

```
BUILDING "name" {
    STOREY "name" level:N height:Xm {
        ROOMTYPE "name" size:WxDm {
            constraints...
            openings...
        }
    }
    ROOF pitch:Xdeg
}
```

## Proposed Extension

```
BUILDING "name" type:MULTI_UNIT {

    UNIT "A" type:RESIDENTIAL {
        STOREY "Ground" level:0 height:2.8m {
            // rooms belonging to Unit A
        }
        STOREY "Upper" level:1 height:2.8m {
            // rooms belonging to Unit A
        }
        METER electrical at:entry
        METER water at:kitchen
    }

    UNIT "B" type:RESIDENTIAL {
        // Unit B rooms
    }

    SHARED {
        STOREY "Ground" level:0 height:2.8m {
            CORRIDOR "main_corridor" size:1.2x8m {
                // shared circulation
            }
            LOBBY "entry" size:3x3m {
                DOOR south  // main entry
            }
        }
        RISER "electrical_main" at:lobby
        RISER "water_main" at:corridor
    }

    ROOF pitch:15deg
}
```

## New Keywords

| Keyword | Purpose | Attributes |
|---------|---------|------------|
| `UNIT` | Groups rooms into independent dwelling | `type:RESIDENTIAL\|COMMERCIAL` |
| `SHARED` | Common areas serving all units | implicit |
| `METER` | Utility metering point | `electrical\|water\|gas`, `at:room` |
| `RISER` | Vertical service shaft | `electrical\|plumbing`, `at:location` |
| `type:MULTI_UNIT` | Building classification | on BUILDING |

## Wall Classification

Walls between spaces now have classification:

| Wall Type | Between | Properties |
|-----------|---------|------------|
| `INTERNAL` | Rooms in same unit | 150mm, no fire rating |
| `PARTY` | Rooms in different units | 250mm, fire-rated, acoustic |
| `EXTERNAL` | Room and exterior | 250mm, weatherproof |
| `SHARED` | Unit room and shared space | 200mm, fire-rated |

The compiler infers wall type from room ownership:
- Room A in Unit X, Room B in Unit X → INTERNAL
- Room A in Unit X, Room B in Unit Y → PARTY
- Room A in Unit X, Corridor in SHARED → SHARED

## Pilot Example: Side-by-Side Duplex

```
BUILDING "Duplex-Pilot" type:MULTI_UNIT {

    // Unit A - West side
    UNIT "A" type:RESIDENTIAL {
        STOREY "Ground" level:0 height:2.8m {
            LIVING "living_a" size:4x5m {
                exterior: west
                exterior: south
                DOOR south          // private entry
                WINDOW west
                LIGHTS grid:3.0m
            }
            KITCHEN "kitchen_a" size:3x3m {
                adjacent: living_a
                LIGHTS grid:2.5m
            }
            BATHROOM "bath_a" size:2x2.5m {
                adjacent: kitchen_a
                stack: riser_a
            }
            BEDROOM "bed_a" size:3x4m {
                adjacent: living_a
                exterior: west
                WINDOW west
                LIGHTS grid:3.0m
            }
        }
        METER electrical at:living_a
        METER water at:bath_a
    }

    // Unit B - East side (mirror of A)
    UNIT "B" type:RESIDENTIAL {
        STOREY "Ground" level:0 height:2.8m {
            LIVING "living_b" size:4x5m {
                exterior: east
                exterior: south
                DOOR south
                WINDOW east
                LIGHTS grid:3.0m
            }
            KITCHEN "kitchen_b" size:3x3m {
                adjacent: living_b
                LIGHTS grid:2.5m
            }
            BATHROOM "bath_b" size:2x2.5m {
                adjacent: kitchen_b
                stack: riser_b
            }
            BEDROOM "bed_b" size:3x4m {
                adjacent: living_b
                exterior: east
                WINDOW east
                LIGHTS grid:3.0m
            }
        }
        METER electrical at:living_b
        METER water at:bath_b
    }

    ROOF pitch:15deg
}
```

## Implied Constraints

When the parser encounters this DSL:

1. **Party wall between units**
   - living_a and living_b share a wall → PARTY wall (250mm, fire-rated)
   - Compiler checks no openings cross party walls

2. **Independent plumbing stacks**
   - `stack: riser_a` creates vertical riser for Unit A
   - `stack: riser_b` creates vertical riser for Unit B
   - Stacks do NOT share horizontal runs

3. **Independent electrical circuits**
   - Each unit gets its own distribution board
   - `METER electrical at:living_a` places DB near entry
   - Circuit graph is unit-scoped

4. **Unit-level validation**
   - Each unit validated independently for:
     - Minimum habitable rooms
     - Internal circulation
     - Emergency egress
   - Building-level validation for:
     - Fire separation between units
     - Structural continuity

## Stacked Duplex Variant (2-Storey)

```
BUILDING "Stacked-Duplex" type:MULTI_UNIT {

    UNIT "A" type:RESIDENTIAL {
        // Unit A occupies ground floor only
        STOREY "Ground" level:0 height:2.8m {
            LIVING "living_a" size:5x6m { ... }
            KITCHEN "kitchen_a" size:3x3m { ... }
            BATHROOM "bath_a" size:2x2.5m { stack: riser_shared }
            BEDROOM "bed_a" size:3x4m { ... }
        }
        METER electrical at:living_a
    }

    UNIT "B" type:RESIDENTIAL {
        // Unit B occupies upper floor only
        STOREY "Upper" level:1 height:2.8m {
            LIVING "living_b" size:5x6m { above: living_a }
            KITCHEN "kitchen_b" size:3x3m { above: kitchen_a }
            BATHROOM "bath_b" size:2x2.5m {
                above: bath_a
                stack: riser_shared  // shared riser, separate branches
            }
            BEDROOM "bed_b" size:3x4m { above: bed_a }
        }
        METER electrical at:living_b
    }

    SHARED {
        // External staircase serving Unit B
        STOREY "Ground" level:0 height:2.8m {
            STAIR "external" at:E1 width:1.0m to:"Upper"
        }
        STOREY "Upper" level:1 height:2.8m {
            LANDING "upper_landing" at:E1 size:2x2m from:"external"
        }
    }

    ROOF pitch:15deg
}
```

## Implementation Phases

### Phase 46A: Parser Extension
- Add UNIT pattern to BuildingParser
- Add SHARED pattern
- Create UnitDefinition class
- Modify BuildingDefinition to hold List<UnitDef>

### Phase 46B: Compilation Pipeline
- BuildingCompiler.compileUnit() method
- Wall classification based on room ownership
- Party wall geometry (thicker, fire-rated)

### Phase 46C: MEP Scoping
- ElectricalPlacer unit-aware (separate DBs)
- PlumbingPlacer unit-aware (shared vs separate risers)
- buildElectricalGraph() per-unit subgraphs

### Phase 46D: Validation Extension
- UnitValidator for per-unit checks
- PartyWallValidator for fire separation
- Multi-unit egress validation

### Phase 46E: Witness Extension
- Unit boundary witnesses
- Party wall fire rating witnesses
- Per-unit MEP completeness

## Backward Compatibility

Existing DSL without UNIT blocks continues to work:

```
BUILDING "Simple-House" {
    STOREY "Ground" level:0 height:2.8m {
        // rooms...
    }
}
```

This is interpreted as:
```
BUILDING "Simple-House" type:SINGLE_UNIT {
    UNIT "default" type:RESIDENTIAL {
        STOREY "Ground" level:0 height:2.8m {
            // rooms...
        }
    }
}
```

The compiler synthesizes an implicit single unit.

## Design Decisions (Resolved)

| Question | Decision | Rationale |
|----------|----------|-----------|
| Shared stack naming | **Automatic branch separation** | Plumbing always branches per unit; explicit DSL adds no value |
| Party wall openings | **Prohibited** | Dual-key is edge case; simpler validation; defer to future phase |
| Mirrored units | **Deferred** | Adds parser complexity; manual duplication clearer for pilot |
| Unit numbering | **IFC IfcZone** | Group spaces by unit across storeys |

## Unit Entry Requirements (UBBL Compliance)

Each unit MUST have independent entry:
- `entry:DIRECT` - Unit has exterior door (terrace house, ground floor)
- `entry:SHARED` - Unit accessed via shared corridor (apartment)

```
UNIT "A" type:RESIDENTIAL entry:DIRECT {
    // Ground floor unit with own front door
}

UNIT "B" type:RESIDENTIAL entry:SHARED {
    // Upper unit accessed via shared stairwell
}
```

**Validation rule**: No pass-through access (cannot enter Unit B only via Unit A).

## IFC Export Structure

Multi-unit buildings use IfcZone for unit grouping:

```
IfcBuilding "Duplex-Pilot"
├── IfcBuildingStorey "Ground"
│   ├── IfcSpace "living_a"
│   ├── IfcSpace "kitchen_a"
│   ├── IfcSpace "living_b"
│   └── IfcSpace "corridor"
├── IfcZone "Unit_A"              ← Groups unit A spaces
│   ├── IfcRelAssignsToGroup → living_a
│   ├── IfcRelAssignsToGroup → kitchen_a
│   └── ...
└── IfcZone "Unit_B"              ← Groups unit B spaces
    └── ...
```

## Fire Rating Encoding

Use enum for type safety (Malaysian UBBL FRL notation):

```java
enum FireRating {
    NONE,
    FRL_30_30_30,    // 30 min (structural/integrity/insulation)
    FRL_60_60_60,    // 1 hour - party walls
    FRL_90_90_90,    // 1.5 hour
    FRL_120_120_120  // 2 hour - fire compartments
}
```

Party walls default to `FRL_60_60_60` (1-hour rating).

## Cross-Unit Constraint Resolution

For stacked duplexes with `above:` constraints across units:

```
UNIT "B" {
    LIVING "living_b" { above: living_a }  // References Unit A room
}
```

**SpaceSolver ordering**:
1. Parse all units, identify cross-unit spatial constraints
2. Build dependency graph (Unit B depends on Unit A)
3. Topologically sort unit compilation order
4. Pass resolved positions to dependent units

This ensures `living_a` geometry is known before placing `living_b`.
