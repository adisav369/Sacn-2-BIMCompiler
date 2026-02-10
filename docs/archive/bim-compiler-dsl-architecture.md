# BIM COMPILER DSL ARCHITECTURE

## Design Principle: SPACE as Universal Primitive

**Version:** 2.0 (January 2025)  
**Status:** Architectural Guideline  
**Purpose:** Ensure all development conforms to coherent DSL grammar

---

## Executive Summary

The BIM Compiler DSL follows a pattern derived from mature ERP architecture (iDempiere). The universal primitive is **SPACE** - all building elements either bound, connect, serve, or occupy spaces.

This document provides guidelines to verify that implementations conform to the DSL grammar rather than introducing incoherent one-off constructs.

**Key additions in v2.0:** MEPSystem graph for connectivity proofs.

---

## The Pattern Origin

iDempiere's power comes from a universal abstraction:

```
Document (typed container)
    │
    ├── DocType (determines behavior)
    ├── DocLines (contents)
    ├── StateMachine (lifecycle)
    ├── Rules (type-specific logic)
    └── Events (downstream triggers)
```

This pattern transfers to BIM:

```
Space (typed container)
    │
    ├── SpaceType (determines behavior)
    ├── Components (contents)
    ├── StateMachine (lifecycle)
    ├── Rules (type-specific logic)
    └── Events (downstream triggers: BOM, IFC)
```

---

## Core DSL Grammar

### Hierarchy

```
BUILDING
    │
    ├── ENVELOPE (special spaces)
    │   ├── FOUNDATION
    │   └── ROOF
    │
    ├── CIRCULATION (vertical spaces)
    │   ├── STAIR
    │   ├── ELEVATOR
    │   └── RAMP
    │
    ├── SYSTEMS (MEP graphs)           ← NEW in v2.0
    │   ├── PLUMBING_WASTE
    │   ├── PLUMBING_VENT
    │   ├── PLUMBING_SUPPLY
    │   └── ELECTRICAL
    │
    └── STOREY (repeating)
        │
        └── SPACE (universal container)
            │
            ├── SpaceType (ROOM, CORRIDOR, BALCONY, SHAFT, etc.)
            ├── Constraints (relationships to other spaces)
            ├── Openings (connections: DOOR, WINDOW)
            └── MEP (services: SPRINKLERS, LIGHTS, HVAC)
```

### SpaceType Categories

| Category | SpaceTypes | Characteristics |
|----------|------------|-----------------|
| Habitable | BEDROOM, LIVING, KITCHEN, STUDY | Requires light, egress, min dimensions |
| Service | BATHROOM, LAUNDRY, UTILITY | Plumbing stack, exhaust, may be interior |
| Circulation | CORRIDOR, LOBBY, FOYER | Connects other spaces, width constraints |
| Vertical | STAIR, ELEVATOR, SHAFT | Spans storeys, special constraints |
| Exterior | BALCONY, PORCH, TERRACE | Open to air, guards required |
| Vehicle | GARAGE, CARPORT | Vehicle access, fire separation |
| Special | DEPARTURE_LOUNGE, GATE, CONCOURSE | Domain-specific (Terminal vocabulary) |

---

## Design Conformance Rules

### Rule 1: Everything is a SPACE or relates to SPACE

When adding a new element, ask:

- Is it a SPACE? → Add as new SpaceType
- Does it BOUND spaces? → It's a wall/slab/roof element
- Does it CONNECT spaces? → It's an opening (door/window/hatch)
- Does it SERVE spaces? → It's MEP (sprinkler/light/diffuser)
- Does it OCCUPY spaces? → It's a fixture/furniture

**If none of the above, the element may not fit the grammar.**

### Rule 2: SpaceType determines behavior

All type-specific logic keys off SpaceType:

| SpaceType | Fixtures | MEP | Validation |
|-----------|----------|-----|------------|
| BATHROOM | toilet, sink, exhaust fan | plumbing stack | May be interior |
| BEDROOM | (future: wardrobe) | sprinklers, lights | Requires window, egress |
| KITCHEN | sink | sprinklers, lights | Requires light source |
| CORRIDOR | none | sprinklers, lights, smoke detector | Min width 914mm |
| GARAGE | (future: EV charger) | smoke detector | Fire separation |

**New SpaceType = new row in this matrix, not new code path.**

### Rule 3: Constraints express SPACE relationships

Valid constraint patterns:

```
adjacent: <space>       # Shared wall, auto-door
not_adjacent: <space>   # Separation enforced
exterior: <direction>   # Building edge, auto-window
aligns: <space>         # Same X,Y across storeys
above: <space>          # Directly above (implies aligns)
below: <space>          # Directly below (implies aligns)
stack: <name>           # Named vertical group (plumbing, structure)
```

**Do not add constraints that don't express SPACE-to-SPACE relationships.**

### Rule 4: Components belong to assemblies, assemblies belong to spaces

Hierarchy:

```
SPACE
    └── Assembly (wall panel, ceiling grid)
        └── Component (frame, cladding, fastener)
            └── Material (steel, concrete, timber)
```

BOM generation walks this tree. Every component must trace back to a SPACE.

### Rule 5: Validation is SpaceType-aware

Validators check:

| Check | Applies To | Rule Source |
|-------|-----------|-------------|
| Natural light | Habitable spaces | IRC R303.1 |
| Egress | All occupied spaces | IRC R311 |
| Min dimensions | Per SpaceType | IRC R304 |
| Plumbing access | Service spaces | IPC |
| Fire separation | GARAGE adjacent to habitable | IRC R302.6 |

**New SpaceType = new validation row, same validator framework.**

### Rule 6: MEP elements belong to systems (NEW in v2.0)

MEP elements are not just placed in spaces—they participate in **system graphs** that prove connectivity.

```
MEP Element
    │
    ├── Contained in SPACE (spatial relationship)
    │
    └── Node in MEPSystem (connectivity relationship)
        ├── Has role: SOURCE, DISTRIBUTION, TERMINAL, CONNECTOR
        └── Has edges: FEEDS, DRAINS_TO, VENTS_TO, SUPPLIES
```

**Every MEP terminal must have a path to its system source.**

---

## MEP System Graph Architecture (Phase 35+)

### The Problem MEPSystem Solves

Before Phase 35, the compiler proved:
- ✅ Pipes exist in the model
- ✅ Pipes have valid dimensions
- ❌ Pipes connect to each other
- ❌ Fixtures drain to septic

MEP elements were placed but not proven connected.

### The Solution: System Graphs

MEPSystem models connectivity as a **directed graph**:

```
MEPSystem
    │
    ├── systemId: "waste_system_1"
    ├── type: PLUMBING_WASTE
    │
    ├── nodes: [
    │   {nodeId: "MH1", role: SOURCE, elementGuid: null},
    │   {nodeId: "riser_bilik_mandi", role: DISTRIBUTION, elementGuid: "..."},
    │   {nodeId: "toilet_bilik_mandi", role: TERMINAL, elementGuid: "..."}
    │ ]
    │
    └── edges: [
        {from: "riser_bilik_mandi", to: "MH1", type: DRAINS_TO},
        {from: "toilet_bilik_mandi", to: "riser_bilik_mandi", type: DRAINS_TO}
      ]
```

### SystemType Enumeration

| SystemType | Description | Direction |
|------------|-------------|-----------|
| PLUMBING_WASTE | Drainage to septic/sewer | Terminal → Source |
| PLUMBING_VENT | Vent to atmosphere | Terminal → Source |
| PLUMBING_SUPPLY | Water supply | Source → Terminal |
| ELECTRICAL | Power distribution | Source → Terminal |
| HVAC_SUPPLY | Conditioned air | Source → Terminal |
| HVAC_RETURN | Return air | Terminal → Source |
| FIRE_SUPPRESSION | Sprinkler system | Source → Terminal |

### NodeRole Enumeration

| Role | Description | Examples |
|------|-------------|----------|
| SOURCE | Origin of system | MH, water meter, DB panel, vent termination |
| DISTRIBUTION | Mid-path element | Riser, circuit, trunk line |
| TERMINAL | End-point | Fixture, outlet, diffuser |
| CONNECTOR | Junction | Fitting, tee, elbow |

### EdgeType Enumeration

| Type | Description | Systems |
|------|-------------|---------|
| FEEDS | Power/signal flow | Electrical |
| DRAINS_TO | Waste water flow | Plumbing waste |
| VENTS_TO | Vent air flow | Plumbing vent |
| SUPPLIES | Water/air supply | Plumbing supply, HVAC |
| RETURNS | Return flow | HVAC return |

### Graph Operations

```java
// Core queries
MEPSystem.isConnected()           // All terminals reachable from/to source
MEPSystem.isComplete()            // Every terminal has valid path
MEPSystem.getPath(from, to)       // BFS pathfinding
MEPSystem.getOrphanedTerminals()  // Terminals with no path

// Traversal direction
// - Waste/Vent: backward (terminal → source)
// - Supply/Electrical: forward (source → terminal)
```

### Database Schema (mep_systems)

```sql
CREATE TABLE mep_systems (
    system_id TEXT PRIMARY KEY,
    system_type TEXT NOT NULL,      -- PLUMBING_WASTE, PLUMBING_VENT, etc.
    building_guid TEXT NOT NULL,
    is_connected INTEGER,           -- 0 or 1
    is_complete INTEGER,            -- 0 or 1
    node_count INTEGER,
    edge_count INTEGER
);

CREATE TABLE system_nodes (
    node_id TEXT PRIMARY KEY,
    system_id TEXT NOT NULL REFERENCES mep_systems(system_id),
    element_guid TEXT,              -- NULL for external (MH, water meter)
    role TEXT NOT NULL,             -- SOURCE, DISTRIBUTION, TERMINAL, CONNECTOR
    name TEXT,
    properties_json TEXT
);

CREATE TABLE system_edges (
    edge_id TEXT PRIMARY KEY,
    system_id TEXT NOT NULL REFERENCES mep_systems(system_id),
    from_node_id TEXT NOT NULL REFERENCES system_nodes(node_id),
    to_node_id TEXT NOT NULL REFERENCES system_nodes(node_id),
    edge_type TEXT NOT NULL,        -- FEEDS, DRAINS_TO, VENTS_TO, SUPPLIES
    properties_json TEXT
);

CREATE INDEX idx_edges_from ON system_edges(from_node_id);
CREATE INDEX idx_edges_to ON system_edges(to_node_id);
```

### Witness Claims from MEPSystem

| Claim | Graph Query | Proves |
|-------|-------------|--------|
| `PLUMBING_WASTE_COMPLETE` | All TERMINAL nodes have path to SOURCE (MH) | Every fixture drains to septic |
| `PLUMBING_VENT_COMPLETE` | All TERMINAL nodes have path to SOURCE (vent term) | Every trap vents to atmosphere |
| `PLUMBING_SUPPLY_COMPLETE` | SOURCE can reach all TERMINAL nodes | Water meter supplies all fixtures |
| `ELECTRICAL_CIRCUITS_COMPLETE` | SOURCE (DB) can reach all TERMINAL nodes | All outlets on circuits |

### Adding a New MEP System Type

1. Add enum value to `SystemType.java`
2. Determine traversal direction (forward or backward)
3. Identify SOURCE element (external or in-model)
4. Implement placer that builds graph while placing elements
5. Add witness claim to `WitnessBuilder.java`
6. Test with `isComplete()` assertion

---

## Code Review Checklist

When reviewing new features, verify:

### New Element Type

- [ ] Classified as SPACE, BOUNDING, CONNECTING, SERVING, or OCCUPYING
- [ ] If SPACE: SpaceType defined with fixture/MEP/validation rules
- [ ] If not SPACE: relationship to SPACE documented
- [ ] No orphan elements that don't relate to any SPACE

### New Constraint

- [ ] Expresses SPACE-to-SPACE relationship
- [ ] Solver can enforce it
- [ ] Geometry layer can realize it
- [ ] Validator can verify it

### New Fixture/MEP

- [ ] Assigned to SpaceType(s) that receive it
- [ ] Placement logic documented (wall-mounted, ceiling, floor)
- [ ] Clash detection included
- [ ] Component exists in library (LOD400) or parametric fallback documented
- [ ] **NEW:** Added to appropriate MEPSystem graph
- [ ] **NEW:** Witness claim proves connectivity

### New Validation Rule

- [ ] Keyed to SpaceType(s)
- [ ] Code reference cited (IRC, IBC, NFPA, etc.)
- [ ] Severity level assigned (CRITICAL, WARNING, INFO)
- [ ] Does not duplicate existing check

### New MEP System (NEW in v2.0)

- [ ] SystemType defined with traversal direction
- [ ] SOURCE identified (external or in-model)
- [ ] NodeRole assigned to each element type
- [ ] EdgeType defined for connections
- [ ] Placer builds graph during element placement
- [ ] Witness claim added for `*_COMPLETE`
- [ ] Database tables populated

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: One-off keywords

**Bad:**
```
GARAGE_DOOR south size:2400x2100
```

**Good:**
```
SPACE "garage" type:GARAGE {
    DOOR south type:VEHICLE size:2400x2100
}
```

The door is an opening connecting the SPACE to exterior, not a special keyword.

### Anti-Pattern 2: Hardcoded behavior outside SpaceType

**Bad:**
```java
if (room.getName().contains("garage")) {
    addFireSeparation();
}
```

**Good:**
```java
if (room.getSpaceType().requiresFireSeparation()) {
    addFireSeparation();
}
```

Behavior derives from SpaceType, not string matching.

### Anti-Pattern 3: Elements without SPACE relationship

**Bad:**
```
SOLAR_PANEL roof count:12
```

Where does this belong in the SPACE hierarchy?

**Good:**
```
ENVELOPE {
    ROOF pitch:15deg {
        SOLAR_PANEL grid:2x6
    }
}
```

ROOF is part of ENVELOPE (a SPACE category), solar panels serve/occupy it.

### Anti-Pattern 4: Constraints that aren't SPACE relationships

**Bad:**
```
BEDROOM { color: blue }
```

Color is a material property, not a spatial constraint.

**Good:**
```
BEDROOM { 
    WALL finish:PAINTED color:blue  # Component attribute
}
```

### Anti-Pattern 5: MEP without system graph (NEW in v2.0)

**Bad:**
```java
// Place toilet without adding to waste system
placeToilet(bathroom);
// No connectivity proof!
```

**Good:**
```java
// Place toilet AND add to system graph
PlumbingSpec toilet = placeToilet(bathroom);
wasteSystem.addNode(new SystemNode(
    "toilet_" + bathroom.name(),
    toilet.guid(),
    NodeRole.TERMINAL,
    "toilet in " + bathroom.name(),
    Map.of()
));
wasteSystem.addEdge(new SystemEdge(
    "edge_toilet_" + bathroom.name(),
    "toilet_" + bathroom.name(),
    "riser_" + bathroom.name(),
    EdgeType.DRAINS_TO,
    Map.of()
));
// Now connectivity is provable!
```

### Anti-Pattern 6: Claiming connectivity without graph proof (NEW in v2.0)

**Bad:**
```java
// Trust that pipes are connected because they're in the same space
boolean connected = pipe1.getSpace().equals(pipe2.getSpace());
```

**Good:**
```java
// Use graph traversal to prove connectivity
boolean connected = system.getPath(pipe1.nodeId(), pipe2.nodeId()).size() > 0;
```

---

## Lifecycle States

Following the Document pattern:

| State | Meaning | Transitions |
|-------|---------|-------------|
| DRAFT | DSL parsed, not solved | → SOLVING |
| SOLVING | Constraint solver running | → SOLVED, UNSATISFIABLE |
| SOLVED | Positions determined | → COMPILING |
| COMPILING | Geometry being generated | → COMPILED |
| COMPILED | Geometry complete | → VALIDATING |
| VALIDATING | Validators running | → VALID, INVALID |
| VALID | Ready for export | → EXPORTING |
| EXPORTING | IFC/BOM generation | → EXPORTED |

**Every SPACE instance has a state. Building state = minimum of child states.**

---

## Extending the Vocabulary

### Adding a new SpaceType

1. Add enum value to `SpaceType.java`
2. Define in SpaceType matrix:
   - Required fixtures
   - MEP requirements
   - Validation rules (code references)
   - Allowed constraints
3. Add fixture placement rules to `FixturePlacer.java`
4. Add validation rules to appropriate Validator
5. Test with DSL example
6. Document in this file

### Adding a new Constraint type

1. Verify it expresses SPACE-to-SPACE relationship
2. Add to `RoomConstraint.java` (or rename to `SpaceConstraint.java`)
3. Implement in solver (`SpaceSolver.java`)
4. Implement geometry realization in `BuildingCompiler.java`
5. Add validator check if applicable
6. Document syntax in DSL grammar
7. Add test case

### Adding a new Component type

1. Verify component exists in `component_library.db` (PRIME RULE)
2. If not: document parametric fallback with source
3. Classify: BOUNDING, CONNECTING, SERVING, or OCCUPYING
4. Assign to SpaceType(s) that receive it
5. Implement placer with clash detection
6. Test placement and BOM generation

### Adding a new MEP System (NEW in v2.0)

1. Add enum value to `SystemType.java`
2. Determine traversal direction:
   - Forward (source → terminal): supply, electrical
   - Backward (terminal → source): waste, vent
3. Identify SOURCE element (external like MH, or in-model like DB)
4. Define NodeRole for each element type in system
5. Define EdgeType for connections
6. Modify placer to build graph while placing elements
7. Add `*_COMPLETE` witness claim to `WitnessBuilder.java`
8. Persist to `mep_systems`, `system_nodes`, `system_edges` tables
9. Test with `isComplete()` and `getOrphanedTerminals()`

---

## Reference: iDempiere to BIM Mapping

| iDempiere Concept | BIM Equivalent | Implementation |
|-------------------|----------------|----------------|
| AD_Table | SpaceType enum | `SpaceType.java` |
| C_DocType | SpaceType instance | DSL `type:` attribute |
| AD_Column | Space attributes | Size, constraints, etc. |
| C_DocLine | Components | assembly_components table |
| DocAction | Compile/Export actions | `BuildingCompiler.java` |
| DocStatus | Building state | (implicit currently) |
| Fact_Acct | BOM generation | `IDempiereExporter.java` |
| AD_Process | Validators | `ValidatorChain.java` |
| AD_Reference | Enums | `WallThickness.java`, etc. |
| Callout | Placers | `FixturePlacer.java` |
| **AD_Tree** | **MEPSystem graph** | **`MEPSystem.java`** (NEW) |

---

## Summary

The BIM Compiler DSL achieves coherence by treating SPACE as the universal primitive:

1. **All elements relate to SPACE** - bounding, connecting, serving, or occupying
2. **SpaceType determines behavior** - fixtures, MEP, validation
3. **Constraints express SPACE relationships** - adjacent, stack, exterior
4. **Components trace to SPACE via assemblies** - enabling BOM generation
5. **Lifecycle follows Document pattern** - draft → solved → compiled → exported
6. **MEP elements belong to system graphs** - enabling connectivity proofs (NEW)

When in doubt, ask: **"How does this relate to SPACE?"**

If it doesn't, it may not belong in the grammar.

---

## Handling Outliers: Graceful Degradation

The system will encounter elements and intents that don't fit the current grammar. Rather than crash or silently produce wrong output, the system should:

1. **Detect** the outlier
2. **Log** diagnostic information for developers
3. **Degrade gracefully** (skip, substitute, or prompt)
4. **Continue** processing what it can

### Outlier Categories

| Category | Example | Detection Point | Response |
|----------|---------|-----------------|----------|
| Unknown SpaceType | `SPACE type:OBSERVATORY` | Parser | Log + use GENERIC fallback |
| Missing component | Bidet requested, not in library | FixturePlacer | Log + skip placement |
| Unsatisfiable constraint | `A adjacent: B, A not_adjacent: B` | Solver | Log + report failure |
| Geometry impossible | Room 0.5m wide with toilet | BuildingCompiler | Log + skip fixture |
| Validation unknown | SpaceType has no validation rules | Validator | Log + INFO warning |
| Vocabulary gap | Intent mentions "sunroom" | IntentResolver | Log + prompt user |
| **Orphaned MEP terminal** | Fixture not in system graph | MEPSystem | Log + add to orphan list (NEW) |

### Debug Log Format

All outlier encounters use consistent format:

```
[OUTLIER:<category>] <component> | <context> | <action_taken>
  → GUIDANCE: <what developer should do to fix permanently>
```

### Fallback Hierarchy

When encountering unknown SpaceType:

```
Requested SpaceType
    │
    ├── Exact match in enum? → Use it
    │
    ├── Partial match? (MASTER_BEDROOM → BEDROOM) → Use parent + log
    │
    ├── Category match? (OBSERVATORY → HABITABLE) → Use category default + log
    │
    └── No match → Use GENERIC + log WARNING
```

### Metrics to Track

| Metric | Meaning | Target |
|--------|---------|--------|
| Outlier rate | Outliers / total elements | < 5% |
| Fallback rate | Fallbacks used / total placements | < 10% |
| Solver relaxation rate | Relaxed solves / total solves | < 5% |
| Vocabulary gap rate | Unknown intents / total intents | < 10% |
| **Orphaned MEP rate** | Orphans / total MEP terminals | **0%** (NEW) |

---

*Document Version 2.0 - January 2025*
*Added: MEPSystem graph architecture, system connectivity proofs, witness claims*
*Package: `com.bim.compiler.system`*
