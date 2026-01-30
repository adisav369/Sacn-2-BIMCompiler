# BIM COMPILER DSL ARCHITECTURE

## Design Principle: SPACE as Universal Primitive

**Date:** January 2025  
**Status:** Architectural Guideline  
**Purpose:** Ensure all development conforms to coherent DSL grammar

---

## Executive Summary

The BIM Compiler DSL follows a pattern derived from mature ERP architecture (iDempiere). The universal primitive is **SPACE** - all building elements either bound, connect, serve, or occupy spaces.

This document provides guidelines for Code to verify that implementations conform to the DSL grammar rather than introducing incoherent one-off constructs.

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

### New Validation Rule

- [ ] Keyed to SpaceType(s)
- [ ] Code reference cited (IRC, IBC, NFPA, etc.)
- [ ] Severity level assigned (CRITICAL, WARNING, INFO)
- [ ] Does not duplicate existing check

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

---

## Summary

The BIM Compiler DSL achieves coherence by treating SPACE as the universal primitive:

1. **All elements relate to SPACE** - bounding, connecting, serving, or occupying
2. **SpaceType determines behavior** - fixtures, MEP, validation
3. **Constraints express SPACE relationships** - adjacent, stack, exterior
4. **Components trace to SPACE via assemblies** - enabling BOM generation
5. **Lifecycle follows Document pattern** - draft → solved → compiled → exported

When in doubt, ask: **"How does this relate to SPACE?"**

If it doesn't, it may not belong in the grammar.

---

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

### Debug Log Format

All outlier encounters use consistent format:

```
[OUTLIER:<category>] <component> | <context> | <action_taken>
  → GUIDANCE: <what developer should do to fix permanently>
```

Examples:

```
[OUTLIER:UNKNOWN_SPACETYPE] OBSERVATORY | building.bim:15 | Using GENERIC fallback
  → GUIDANCE: Add OBSERVATORY to SpaceType enum with fixture/MEP/validation rules

[OUTLIER:MISSING_COMPONENT] bidet | BATHROOM "ensuite" | Skipped placement
  → GUIDANCE: Add bidet to component_library.db or create parametric fallback

[OUTLIER:UNSATISFIABLE] adjacent+not_adjacent conflict | kitchen↔garage | Solver returned UNSAT
  → GUIDANCE: User error - constraints contradict. Report to user.

[OUTLIER:GEOMETRY_IMPOSSIBLE] toilet in 0.5m wide space | BATHROOM "tiny" | Fixture skipped
  → GUIDANCE: Min bathroom width for toilet = 0.8m. Add to RoomRequirements.

[OUTLIER:VOCABULARY_GAP] "sunroom" | intent parsing | Mapped to LIVING with exterior:south
  → GUIDANCE: Consider adding SUNROOM as SpaceType with glass wall requirements
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

GENERIC SpaceType rules:
- No auto-fixtures
- Basic MEP (sprinklers, lights)
- Minimal validation (egress only)
- BOM captures geometry only

### Component Fallback Hierarchy

When requested component missing from library:

```
Requested Component
    │
    ├── Exact match in library? → Use it (LOD400)
    │
    ├── Similar match? (K11.2 sprinkler → K5.6) → Use similar + log
    │
    ├── Parametric fallback exists? → Generate + log
    │
    └── No fallback → Skip + log WARNING
```

### Solver Failure Handling

When constraints are unsatisfiable:

```java
SolverResult result = solver.solve(constraints);
if (result.status == UNSATISFIABLE) {
    log.warn("[OUTLIER:UNSATISFIABLE] {} | Constraints: {}", 
             result.conflictingConstraints,
             constraints);
    
    // Attempt relaxation
    SolverResult relaxed = solver.solveRelaxed(constraints, 
        RELAX_ORDER: [not_adjacent, aligns, adjacent]);
    
    if (relaxed.status == SATISFIABLE) {
        log.info("Solved with relaxed constraints: dropped {}", 
                 relaxed.droppedConstraints);
        return relaxed;
    }
    
    // Cannot solve even relaxed
    throw new UnsatisfiableLayoutException(result.conflictingConstraints);
}
```

### Validation Unknown SpaceType

When validator encounters SpaceType without rules:

```java
ValidationRules rules = getRulesForSpaceType(space.getType());
if (rules == null) {
    log.info("[OUTLIER:VALIDATION_UNKNOWN] {} | No rules defined", 
             space.getType());
    report.addInfo(space, 
        "SpaceType %s has no validation rules - using defaults",
        space.getType());
    rules = ValidationRules.GENERIC_DEFAULTS;
}
```

---

## Developer Feedback Loop

### Outlier Log Aggregation

After each compilation, summarize outliers:

```
=== OUTLIER SUMMARY ===
UNKNOWN_SPACETYPE: 1
  - OBSERVATORY (1 occurrence) → GUIDANCE: Add to SpaceType enum

MISSING_COMPONENT: 2
  - bidet (1) → GUIDANCE: Add to library
  - japanese_toilet (1) → GUIDANCE: Add to library

VOCABULARY_GAP: 1
  - "sunroom" (1) → GUIDANCE: Consider SUNROOM SpaceType

Total: 4 outliers in this compilation
See full log: output/outliers.log
```

### Outlier-Driven Development

The outlier log becomes the backlog:

1. **Frequent outliers** → High priority additions to grammar
2. **One-off outliers** → User education or edge case
3. **Conflicting outliers** → Grammar design review needed

### Metrics to Track

| Metric | Meaning | Target |
|--------|---------|--------|
| Outlier rate | Outliers / total elements | < 5% |
| Fallback rate | Fallbacks used / total placements | < 10% |
| Solver relaxation rate | Relaxed solves / total solves | < 5% |
| Vocabulary gap rate | Unknown intents / total intents | < 10% |

High rates indicate grammar gaps; low rates indicate mature coverage.

---

## Session State: Outlier Tracking

Add to `SESSION_STATE.md`:

```markdown
## Outlier Backlog

| Date | Outlier | Count | Status |
|------|---------|-------|--------|
| 2025-01-29 | SUNROOM SpaceType | 3 | Pending review |
| 2025-01-28 | bidet component | 1 | Added to library |
| 2025-01-27 | curved wall | 2 | Out of scope (rectangular only) |
```

---

*Document Version 1.1 - January 2025*
*Derived from SA discussion: ERP DSL patterns applied to BIM domain*
*Added: Outlier handling, graceful degradation, developer feedback loop*
