# PRIME RULE

**EXTRACT, DON'T IMAGINE.**

Query the federated model DB. Copy patterns you find. Never invent.

---

**When lost:** `cat SESSION_STATE.md` then query DB.

**After token refresh:** Read this file → state phase → continue.

---

# NEXT SESSION: READ THESE FIRST

1. **`~/Downloads/handoff-prompt-v0.48.0.md`** — Phase 48 handoff with witness matrix
2. **`~/Downloads/context-engineering-setup.md`** — **IMPORTANT: Housekeeping task** to set up tiered context files (CLAUDE.md restructure, subdirectory scopes)

---

# CURRENT STATUS (2026-02-01)

| Phase | Status | Description |
|-------|--------|-------------|
| Phases 0-45 | ✓ Complete | Foundation through Room Area BOMs |
| Phase 46A-D | ✓ Complete | Multi-unit residential foundation |
| Phase 47A-D | ✓ Complete | Cross-unit solver, party walls, zone partitioning |
| **Phase 48** | ✓ Complete | STACKED multi-unit, separating floors, per-unit egress |

**Compiler Version:** 0.48.0
**Commit:** c37955a
**Witnesses:** 19 claims (see witness matrix)
**Sanity Checks:** 11/11 PASS
**GitHub:** https://github.com/red1oon/BIMCompiler

---

# PHASE 46: Multi-Unit Residential Support

## Completed (46A-D)

| Sub-phase | Deliverable |
|-----------|-------------|
| 46A | Parser + data model (9 new classes) |
| 46B | Compilation pipeline with topological sort |
| 46C | Wall classification (INTERNAL/PARTY/EXTERNAL/SHARED) |
| 46D | Per-unit MEP scoping (separate DBs per unit) |

## New DSL Keywords
- `UNIT "name" type:RESIDENTIAL entry:DIRECT { ... }`
- `SHARED { ... }` for common areas
- `METER electrical at:room_name`
- `adjacent_unit: room_name` ✓ WORKING (Phase 47A)

## Test Results
```
Duplex-Pilot.bim:
  Type: MULTI_UNIT
  Units: 2 (A, B)
  Rooms: 8 (tagged with unitId)
  MEP: 5 systems (2 per-unit electrical + 3 shared plumbing)
  Party wall: living_a <-> living_b ADJACENT ✓

TB-LKTN-2S.bim: Backward compatible (SINGLE_UNIT)
```

---

# PHASE 47A: Cross-Unit Solver (COMPLETE)

## Completed (47A.1-A.3)

| Sub-phase | Deliverable |
|-----------|-------------|
| 47A.1 | PartyWallConstraint record, RoomDef.adjacentUnit field, extractPartyConstraints() |
| 47A.2 | solveMultiUnitLayout() - joint solve with relaxation, position injection |
| 47A.3 | Unit envelope computation, exterior edge filtering, validator relaxation for multi-unit |

## Key Files
- `PartyWallConstraint.java` - constraint record with unit resolution
- `BuildingCompiler.java` - solveMultiUnitLayout(), computeUnitEnvelopes(), getUnitExteriorEdges()
- `BuildingDefinition.java` - RoomDef.adjacentUnit field, withPositionAndExterior()
- `BuildingParser.java` - adjacent_unit: parsing
- `HabitabilityValidator.java` - relaxed exterior check for multi-unit

## Two-Phase Solve Architecture
1. **Phase 1**: Joint room placement with relaxation (exterior constraints may be dropped)
2. **Phase 2**: Compute unit envelopes, determine true exterior edges, filter room exterior info

## Validation Approach
- Single-unit: strict exterior check (window must be on building edge)
- Multi-unit: relaxed check (window on room wall, exterior placement not verified)

## Deferred to Phase 47C+
- 47C: PARTY_WALLS_VALID witness (#18)
- IfcZone export for unit grouping
- Unit-level witnesses
- Proper two-phase solve with envelope-first constraint handling

---

# PHASE 47B: Party Wall Geometry (COMPLETE)

## Implemented
| Feature | Description |
|---------|-------------|
| Cross-unit wall generation | `generateCrossUnitPartyWalls()` in merge phase |
| Party wall thickness | 250mm (vs 90mm internal walls) |
| Fire rating | FRL 60/60/60 per UBBL |
| Wall classification | PARTY type with geometry and cladding |

## Key Code
```java
// BuildingCompiler.java - generateCrossUnitPartyWalls()
for (RoomSpec roomA : rooms) {
    for (RoomSpec roomB : rooms) {
        if (!roomA.unitId().equals(roomB.unitId())) {
            String sharedSide = getSharedSide(roomA, roomB);
            if (sharedSide != null) {
                // Generate 250mm party wall with FRL 60/60/60
            }
        }
    }
}
```

## Test Results
```
Duplex-Pilot.bim:
  Party walls: 2 (FRL 60/60/60)
    - living_a <-> living_b (EAST, 4.00m)
    - bed_a <-> living_b (SOUTH, 3.00m)
  Thickness: 250mm
  Fire rating: 60/60/60 (structural/integrity/insulation)
```

---

# PHASE 47C: Party Wall Witness (COMPLETE)

## Implemented
| Feature | Description |
|---------|-------------|
| Witness #18 | PARTY_WALLS_VALID claim |
| Topology analysis | LINEAR vs JAGGED detection |
| Segment collection | Party wall bounds, rooms, units |
| Fire rating check | FRL 60/60/60 compliance |
| Thickness check | 250mm minimum |

## Witness Output (Duplex-Pilot.bim)
```json
{
  "PARTY_WALLS_VALID": {
    "status": "PROVEN",
    "witness": {
      "party_wall_segments": [
        {"rooms": ["living_a","living_b"], "edge": "EAST", "length_m": 4.0},
        {"rooms": ["bed_a","living_b"], "edge": "SOUTH", "length_m": 3.0}
      ],
      "total_separation_length_m": 7.0,
      "topology": "JAGGED",
      "continuity_proven": false,
      "topology_warning": {
        "type": "JAGGED_TOPOLOGY",
        "message": "Party wall has direction changes - requires fire stopping"
      }
    }
  }
}
```

## Key Files
- `WitnessBuilder.java` - partyWall(), buildPartyWallsClaim(), analyzePartyWallTopology()
- `BuildingWriter.java` - Party wall witness collection in generateWitness()

---

# PHASE 47D: Unit Zone Partitioning (COMPLETE)

## Implemented
| Feature | Description |
|---------|-------------|
| LayoutType enum | SIDE_BY_SIDE, STACKED, COMPLEX |
| Zone bounds | RoomConstraint with minX/maxX/minY/maxY |
| detectLayoutType() | Determines layout from party constraints |
| Zone partitioning | Divides grid by unit count |

## Key Changes
```java
// SpaceSolver.java - RoomConstraint with zone bounds
public record RoomConstraint(
    ...,
    Integer zoneMinX, Integer zoneMaxX,  // Phase 47D
    Integer zoneMinY, Integer zoneMaxY
)

// BuildingCompiler.java - Zone partitioning
if (layoutType == LayoutType.SIDE_BY_SIDE) {
    int unitWidth = maxWidth / units.size();
    for (int i = 0; i < units.size(); i++) {
        unitZoneBounds.put(units.get(i).name(),
            new int[]{i * unitWidth, (i+1) * unitWidth, 0, maxDepth});
    }
}
```

## Result
```
Before (47C): Topology: JAGGED, continuity_proven: false
After (47D):  Topology: LINEAR, continuity_proven: true

Party walls: 2 (both on EAST edge at X=15)
  - living_a <-> living_b (4.0m)
  - living_a <-> kitchen_b (1.0m)
Total: 5.0m continuous separation
```

---

# RESUME PROMPT

```
Continue BIM Compiler project. Phase 48 COMPLETE.

## FIRST: Read these files
1. ~/Downloads/handoff-prompt-v0.48.0.md (witness matrix, phase summary)
2. ~/Downloads/context-engineering-setup.md (HOUSEKEEPING: tiered context setup)

## Current State (v0.48.0, commit c37955a)
- 19 witness claims (see witness matrix)
- 11/11 sanity checks PASS
- Test files: TB-LKTN-2S, Duplex-Pilot, Stacked-Duplex

## Phase 48 Complete
- 48A: STACKED detection, cross-unit above:, center→corner fix
- 48B: Separating floor (FRL 90/90/90, 200mm, STC/IIC 50)
- 48C: SEPARATING_FLOORS_VALID witness (#19)
- 48D.2: Per-unit ENTRY_EXISTS with SHARED egress paths

## Witness Matrix (layout-dependent)
| Claim | Single | SIDE_BY_SIDE | STACKED |
|-------|--------|--------------|---------|
| PARTY_WALLS_VALID | SKIP | PROVEN | SKIP |
| SEPARATING_FLOORS_VALID | SKIP | SKIP | PROVEN |
| ENTRY_EXISTS | PROVEN | PROVEN (per-unit) | PROVEN (per-unit) |

## Deferred to Phase 49
- Stair geometry generation (HybridFactory patterns)
- IFC IfcStair + IfcStairFlight export

## Test Commands
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.StackedDuplexDoorTest"
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.MultiUnitCompilerTest"
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTN2SEndToEndTest"

## Standing Rules
PRIME RULE: Extract from federated DB, don't imagine.
Math is the final arbiter - witnesses prove correctness.
```

---

# 19 WITNESS CLAIMS (v0.48.0)

| # | Claim | Single | SIDE_BY_SIDE | STACKED |
|---|-------|--------|--------------|---------|
| 1-13 | Baseline (v0.37) | PROVEN | PROVEN | PROVEN |
| 14 | STOREYS_VERTICALLY_CONSISTENT | PROVEN | PROVEN | PROVEN |
| 15 | ALL_OUTLETS_ON_CIRCUIT | PROVEN | PROVEN | PROVEN |
| 16 | MEP_NO_STRUCTURAL_CLASH | PROVEN | PROVEN | PROVEN |
| 17 | ROOM_AREAS_CONSISTENT | PROVEN | PROVEN | PROVEN |
| 18 | PARTY_WALLS_VALID | SKIPPED | PROVEN | SKIPPED |
| 19 | SEPARATING_FLOORS_VALID | SKIPPED | SKIPPED | PROVEN |

SKIPPED = intentional exclusion (not applicable to layout type)
UNPROVABLE = proof system deficiency (must never appear)

---

# KEY FILES (Phase 46)

```
src/main/java/com/bim/compiler/dsl/
├── BuildingType.java           ← SINGLE_UNIT / MULTI_UNIT
├── UnitType.java               ← RESIDENTIAL / COMMERCIAL
├── EntryType.java              ← DIRECT / SHARED
├── WallType.java               ← INTERNAL / PARTY / EXTERNAL / SHARED
├── FireRating.java             ← UBBL FRL notation
├── UnitDefinition.java         ← Unit with storeys, meters
├── SharedDefinition.java       ← Common areas, risers
├── MeterDef.java               ← Utility metering
├── RiserDef.java               ← Vertical service shafts
├── BuildingParser.java         ← UNIT/SHARED parsing
├── BuildingCompiler.java       ← compileMultiUnit(), MEP scoping
├── MultiUnitParserTest.java    ← Parser tests
└── MultiUnitCompilerTest.java  ← Compiler tests

examples/
└── Duplex-Pilot.bim            ← Multi-unit test building

docs/
├── dsl-extension-multi-unit.md ← DSL spec
├── phase-46-implementation-map.md ← Design details
└── phase-46-summary.md         ← Phase summary
```

---

# TEST COMMANDS

```bash
# Multi-unit parser test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.MultiUnitParserTest"

# Multi-unit compiler test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.MultiUnitCompilerTest"

# 2-Storey (backward compat) - 17/17 PROVEN
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTN2SEndToEndTest"

# Sanity checker - 11/11 PASS
java -cp "lib/*:target/classes:tools/sanity-checker/target/classes" \
  com.bim.tools.sanity.HouseSanityChecker output/tb_lktn_2s.db
```

---

**Phase 48 complete. STACKED multi-unit with separating floors working.**
**Next session: Run context-engineering-setup.md housekeeping, then Phase 49 (stair geometry).**
