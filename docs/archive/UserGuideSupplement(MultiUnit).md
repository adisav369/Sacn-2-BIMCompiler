# User Guide Supplement: Multi-Unit Buildings

**Applies to:** BIM Compiler v0.47+
**Updated:** February 2025
**Prerequisite:** Read main [USER_GUIDE.md](../USER_GUIDE.md) first

---

## Overview

This supplement covers multi-unit residential buildings: duplexes, townhouses, and apartments where multiple dwelling units share party walls. Features include:

- **UNIT blocks** for defining separate dwellings
- **Party walls** with fire rating enforcement
- **Per-unit MEP** systems (electrical, plumbing)
- **IfcZone export** for IFC consumers to distinguish units
- **PARTY_WALLS_VALID** witness claim with topology analysis

---

## DSL Syntax for Multi-Unit

### Building Declaration

```
BUILDING "Duplex-Pilot" type:MULTI_UNIT {
    UNIT "A" type:RESIDENTIAL entry:DIRECT { ... }
    UNIT "B" type:RESIDENTIAL entry:DIRECT { ... }
    PARTY_WALL between:A,B fire_rating:FRL_60_60_60
}
```

| Attribute | Values | Description |
|-----------|--------|-------------|
| `type:` | `MULTI_UNIT` | Enables unit blocks and party walls |
| `entry:` | `DIRECT`, `SHARED` | Direct = own entry, Shared = common corridor |
| `fire_rating:` | `FRL_60_60_60`, `FRL_90_90_90` | Fire/Structural/Integrity minutes |

### UNIT Block

Each unit contains its own storeys, rooms, and MEP:

```
UNIT "A" type:RESIDENTIAL entry:DIRECT {
    STOREY "Ground" level:0 height:2.8m {
        LIVING "living_a" size:5x4m {
            exterior: west
            adjacent_unit: B direction:EAST   // Party wall constraint
        }
        KITCHEN "kitchen_a" size:3x4m {
            adjacent: living_a
        }
        BEDROOM "bed_a" size:4x3m {
            exterior: west
            exterior: north
        }
        BATHROOM "bath_a" size:2x2m {
            adjacent: bed_a
        }
    }
    METER electrical at:living_a
    METER water at:bath_a
}
```

### Party Wall Constraint

The `adjacent_unit:` constraint specifies where units meet:

```
LIVING "living_a" size:5x4m {
    adjacent_unit: B direction:EAST
}
```

This tells the solver that `living_a` must have its EAST edge aligned with Unit B's rooms, creating a party wall at that boundary.

### PARTY_WALL Declaration

Explicit party wall specification with fire rating:

```
PARTY_WALL between:A,B fire_rating:FRL_60_60_60
```

Fire ratings follow UBBL format:
- `FRL_60_60_60` = 60 min fire resistance / 60 min structural / 60 min integrity
- `FRL_90_90_90` = Enhanced rating for taller buildings

---

## Party Wall Topology

The compiler analyzes party wall geometry and classifies topology:

| Topology | Description | Example |
|----------|-------------|---------|
| `LINEAR` | Single straight boundary | Side-by-side duplex, clean division |
| `JAGGED` | L-shaped or stepped boundary | Units with offset rooms |
| `COMPLEX` | Multiple disconnected segments | Irregular layouts |

### Witness Output

```json
{
  "claims": {
    "PARTY_WALLS_VALID": {
      "status": "PROVEN",
      "witness": {
        "party_wall_count": 1,
        "total_length_m": 5.0,
        "topology": "LINEAR",
        "fire_rating": "FRL_60_60_60",
        "party_wall_segments": [
          {
            "unit_pair": ["A", "B"],
            "room_pairs": [
              {"room_a": "living_a", "room_b": "living_b", "direction": "EAST", "length_m": 4.0},
              {"room_a": "living_a", "room_b": "kitchen_b", "direction": "EAST", "length_m": 1.0}
            ],
            "collinear": true,
            "wall_x": 15.0
          }
        ]
      }
    }
  }
}
```

---

## Per-Unit MEP Systems

Multi-unit buildings generate separate MEP systems per unit:

### Electrical

Each unit gets its own distribution board and circuits:

```
ELEC-UNIT_A     Electrical system for Unit A
ELEC-UNIT_B     Electrical system for Unit B
ELEC-_SHARED    Common areas (if any)
```

Query per-unit electrical:
```sql
SELECT system_id, node_count, edge_count
FROM mep_systems
WHERE system_id LIKE 'ELEC-UNIT_%'
```

### Plumbing

Each unit has independent water supply and drainage:
- Separate water meters
- Independent risers
- Shared sewer connection at property boundary

---

## IFC Export: IfcZone

Multi-unit IFC files include `IfcZone` entities that group spaces by dwelling unit.

### Schema Structure

```
IFCZONE('...','Unit_A','Fire compartment / dwelling unit boundary');
IFCRELASSIGNSTOGROUP('...','Unit_A_Spaces',...,(#living_a,#kitchen_a,...),...,#zone_a);
```

### Querying Zones in IFC

IFC consumers can:
1. Query all `IfcZone` entities to enumerate units
2. Follow `IfcRelAssignsToGroup.RelatedObjects` to get spaces per unit
3. Calculate area per unit from space geometries
4. Validate fire compartment boundaries

### Python Example (ifcopenshell)

```python
import ifcopenshell

model = ifcopenshell.open("duplex.ifc")

for zone in model.by_type("IfcZone"):
    print(f"Unit: {zone.Name}")

    # Find related spaces via IfcRelAssignsToGroup
    for rel in model.by_type("IfcRelAssignsToGroup"):
        if rel.RelatingGroup == zone:
            for space in rel.RelatedObjects:
                if space.is_a("IfcSpace"):
                    print(f"  - {space.Name}")
```

---

## Complete Multi-Unit Example

### Duplex-Pilot.bim

```
BUILDING "Duplex-Pilot" type:MULTI_UNIT profile:"Malaysian_Residential" {

    UNIT "A" type:RESIDENTIAL entry:DIRECT {
        STOREY "Ground" level:0 height:2.8m {
            LIVING "living_a" size:5x4m {
                exterior: west
                exterior: south
                adjacent_unit: B direction:EAST
                DOOR type:D1 wall:south
                WINDOW type:W1 wall:west
            }
            KITCHEN "kitchen_a" size:3x4m {
                adjacent: living_a direction:NORTH
                exterior: west
            }
            BEDROOM "bed_a" size:4x3m {
                exterior: west
                exterior: north
                adjacent: kitchen_a direction:EAST
            }
            BATHROOM "bath_a" size:2x3m {
                adjacent: bed_a direction:EAST
            }
        }
        METER electrical at:living_a
        METER water at:bath_a
    }

    UNIT "B" type:RESIDENTIAL entry:DIRECT {
        STOREY "Ground" level:0 height:2.8m {
            LIVING "living_b" size:5x4m {
                exterior: east
                exterior: south
                adjacent_unit: A direction:WEST
                DOOR type:D1 wall:south
                WINDOW type:W1 wall:east
            }
            KITCHEN "kitchen_b" size:3x4m {
                adjacent: living_b direction:NORTH
                exterior: east
            }
            BEDROOM "bed_b" size:4x3m {
                exterior: east
                exterior: north
                adjacent: kitchen_b direction:WEST
            }
            BATHROOM "bath_b" size:2x3m {
                adjacent: bed_b direction:WEST
            }
        }
        METER electrical at:living_b
        METER water at:bath_b
    }

    PARTY_WALL between:A,B fire_rating:FRL_60_60_60

    ROOF pitch:25deg overhang:600mm
}
```

### Compilation Output

```
=== MULTI-UNIT COMPILATION ===
Building: Duplex-Pilot
Type: MULTI_UNIT (2 units)

Unit A: 4 rooms, 45.0 m²
Unit B: 4 rooms, 45.0 m²

Party Walls:
  A-B: LINEAR topology, 5.0m @ X=15.0
       Fire rating: FRL_60_60_60

MEP Systems:
  ELEC-UNIT_A: 12 nodes
  ELEC-UNIT_B: 12 nodes

Witness: 14/14 PROVEN
  - PARTY_WALLS_VALID: PROVEN (LINEAR, FRL 60/60/60)

Output:
  → output/duplex_pilot.db
  → output/duplex_pilot_witness.json
  → output/duplex_pilot.ifc (with IfcZone)
```

---

## Running Multi-Unit Tests

```bash
# Compile Duplex-Pilot
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.MultiUnitCompilerTest" -q

# Verify IfcZone export
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.IfcZoneExportTest" -q

# View party wall witness
cat output/duplex_pilot_witness.json | jq '.claims.PARTY_WALLS_VALID'
```

---

## Limitations (Current Phase)

| Feature | Status | Notes |
|---------|--------|-------|
| Side-by-side (LINEAR) | Supported | Duplex, townhouse |
| Stacked units | Phase 48 | Requires IfcSlab separating floor |
| Shared corridors | Partial | SHARED entry type parsed |
| Mixed-use (COMMERCIAL) | Parsed | Fire separation not enforced |

---

## Troubleshooting

### "Party wall not detected"

Ensure `adjacent_unit:` constraint is specified on rooms that should share the party wall:
```
LIVING "living_a" size:5x4m {
    adjacent_unit: B direction:EAST  // Required
}
```

### "PARTY_WALLS_VALID: FAILED"

Check witness for details:
```bash
cat output/duplex_pilot_witness.json | jq '.claims.PARTY_WALLS_VALID'
```

Common causes:
- Units not geometrically adjacent (solver gap)
- Missing `PARTY_WALL` declaration

### "No IfcZone in IFC output"

Verify spaces have `unitId` set. This requires using the multi-unit compilation path (`BuildingParser` + `BuildingCompiler`), not the simple `DSLParser` path.

---

## Related Documentation

- [USER_GUIDE.md](../USER_GUIDE.md) - Main user guide (single-unit)
- [witness-system-specification.md](witness-system-specification.md) - Witness claim details
- [dsl-extension-multi-unit.md](dsl-extension-multi-unit.md) - Technical specification

---

*User Guide Supplement v0.47 - Multi-Unit Buildings*
*Party walls, per-unit MEP, IfcZone export*
