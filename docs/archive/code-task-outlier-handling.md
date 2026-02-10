# CODE TASK: Implement Outlier Handling Framework

**Priority:** High  
**Reference:** `bim-compiler-dsl-architecture.md` (Section: Handling Outliers)  
**Goal:** System handles unknown inputs gracefully with actionable debug logs

---

## Context

The BIM Compiler DSL treats SPACE as the universal primitive. However, the system will encounter:
- Unknown SpaceTypes not yet in enum
- Missing components not in library
- Unsatisfiable constraints
- Vocabulary gaps in intent parsing
- Geometry that can't accommodate requested fixtures

Currently these may cause crashes, silent failures, or wrong output. We need graceful degradation with developer-actionable logging.

---

## Task 1: Create OutlierLogger Utility

Create `src/main/java/com/bim/compiler/util/OutlierLogger.java`:

```java
public class OutlierLogger {
    
    public enum OutlierCategory {
        UNKNOWN_SPACETYPE,
        MISSING_COMPONENT,
        UNSATISFIABLE,
        GEOMETRY_IMPOSSIBLE,
        VALIDATION_UNKNOWN,
        VOCABULARY_GAP
    }
    
    // Log format: [OUTLIER:<category>] <component> | <context> | <action>
    //   → GUIDANCE: <developer action>
    
    public static void log(OutlierCategory category, 
                          String component,
                          String context,
                          String actionTaken,
                          String guidance);
    
    // Accumulate for summary report
    public static void summarize(Path outputPath);
    
    // Clear between compilations
    public static void reset();
}
```

---

## Task 2: Implement SpaceType Fallback

In `SpaceType.java` or new `SpaceTypeResolver.java`:

```java
public static SpaceType resolve(String requested) {
    // 1. Exact match
    SpaceType exact = SpaceType.fromString(requested);
    if (exact != null) return exact;
    
    // 2. Partial match (MASTER_BEDROOM → BEDROOM)
    SpaceType partial = findPartialMatch(requested);
    if (partial != null) {
        OutlierLogger.log(UNKNOWN_SPACETYPE, requested, 
            "DSL parsing", 
            "Using " + partial,
            "Add " + requested + " to SpaceType enum");
        return partial;
    }
    
    // 3. Category match (guess from name)
    SpaceType category = guessCategory(requested);
    if (category != null) {
        OutlierLogger.log(UNKNOWN_SPACETYPE, requested,
            "DSL parsing",
            "Using category default " + category,
            "Add " + requested + " to SpaceType enum with specific rules");
        return category;
    }
    
    // 4. GENERIC fallback
    OutlierLogger.log(UNKNOWN_SPACETYPE, requested,
        "DSL parsing",
        "Using GENERIC fallback",
        "Add " + requested + " to SpaceType enum - no fixtures/MEP will be placed");
    return SpaceType.GENERIC;
}
```

Add `GENERIC` to SpaceType enum if not present, with minimal rules.

---

## Task 3: Component Library Fallback

In `HybridFactory.java` or `ComponentLibrary.java`:

```java
public ComponentDefinition getComponent(String requested, String context) {
    // 1. Exact match
    ComponentDefinition exact = library.findExact(requested);
    if (exact != null) return exact;
    
    // 2. Similar match (fuzzy search)
    ComponentDefinition similar = library.findSimilar(requested, 0.8);
    if (similar != null) {
        OutlierLogger.log(MISSING_COMPONENT, requested,
            context,
            "Using similar: " + similar.getName(),
            "Add " + requested + " to component_library.db");
        return similar;
    }
    
    // 3. Parametric fallback
    if (hasParametricFallback(requested)) {
        OutlierLogger.log(MISSING_COMPONENT, requested,
            context,
            "Using parametric generation",
            "Add LOD400 " + requested + " to library for better quality");
        return generateParametric(requested);
    }
    
    // 4. Skip
    OutlierLogger.log(MISSING_COMPONENT, requested,
        context,
        "Skipped - no fallback available",
        "Add " + requested + " to component_library.db or create parametric fallback");
    return null;
}
```

---

## Task 4: Solver Constraint Relaxation

In `SpaceSolver.java`:

```java
public SolverResult solve(List<SpaceConstraint> constraints) {
    SolverResult result = solveStrict(constraints);
    
    if (result.getStatus() == SATISFIABLE) {
        return result;
    }
    
    // Log the conflict
    OutlierLogger.log(UNSATISFIABLE, 
        formatConflicts(result.getConflictingConstraints()),
        "Constraint solving",
        "Attempting relaxation",
        "Review constraints for logical conflicts");
    
    // Try relaxation in priority order
    List<ConstraintType> relaxOrder = Arrays.asList(
        NOT_ADJACENT,  // Drop separation constraints first
        ALIGNS,        // Then alignment
        STACK,         // Then vertical grouping
        ADJACENT       // Adjacent last (most important)
    );
    
    for (ConstraintType toRelax : relaxOrder) {
        List<SpaceConstraint> relaxed = removeConstraintsOfType(constraints, toRelax);
        result = solveStrict(relaxed);
        
        if (result.getStatus() == SATISFIABLE) {
            OutlierLogger.log(UNSATISFIABLE,
                "Dropped: " + toRelax,
                "Constraint relaxation",
                "Solved with relaxed constraints",
                "Original constraints were over-constrained");
            result.setDroppedConstraints(getDropped(constraints, relaxed));
            return result;
        }
    }
    
    // Cannot solve even fully relaxed
    OutlierLogger.log(UNSATISFIABLE,
        formatConflicts(result.getConflictingConstraints()),
        "Constraint solving",
        "FAILED - cannot solve",
        "Layout is fundamentally impossible - review room sizes and constraints");
    
    return result; // Status = UNSATISFIABLE
}
```

---

## Task 5: Fixture Placement Clash Handling

In `FixturePlacer.java`:

```java
public void placeFixtures(Space space, List<PlacedElement> existing) {
    List<FixtureSpec> required = getFixturesForSpaceType(space.getType());
    
    for (FixtureSpec fixture : required) {
        PlacementResult placement = findValidPlacement(fixture, space, existing);
        
        if (placement.isValid()) {
            existing.add(placement.getElement());
        } else {
            OutlierLogger.log(GEOMETRY_IMPOSSIBLE,
                fixture.getName(),
                "Space: " + space.getName() + " (" + space.getType() + ")",
                "Skipped - " + placement.getReason(),
                getGuidanceForFailure(fixture, space, placement.getReason()));
        }
    }
}

private String getGuidanceForFailure(FixtureSpec fixture, Space space, String reason) {
    if (reason.contains("too narrow")) {
        return String.format("Min width for %s = %.2fm. Space is %.2fm. Increase room size or remove fixture requirement.",
            fixture.getName(), fixture.getMinWidth(), space.getWidth());
    }
    if (reason.contains("clash")) {
        return String.format("Fixture clashes with existing element. Increase room size or reduce fixture count.");
    }
    return "Review fixture placement rules for " + space.getType();
}
```

---

## Task 6: Validation Unknown SpaceType

In validators (e.g., `HabitabilityValidator.java`):

```java
public void validate(Space space, ValidationReport report) {
    ValidationRules rules = rulesMap.get(space.getType());
    
    if (rules == null) {
        OutlierLogger.log(VALIDATION_UNKNOWN,
            space.getType().name(),
            "Space: " + space.getName(),
            "Using GENERIC validation rules",
            "Define validation rules for " + space.getType() + " in validator");
        rules = ValidationRules.GENERIC;
    }
    
    // Apply rules...
}
```

---

## Task 7: Intent Vocabulary Gap

In `intent_resolver.py` (Python):

```python
def resolve_space_type(token, context):
    # Known mappings
    known = {
        'bedroom': 'BEDROOM',
        'bathroom': 'BATHROOM',
        'kitchen': 'KITCHEN',
        # ... etc
    }
    
    if token.lower() in known:
        return known[token.lower()]
    
    # Fuzzy match
    match, score = fuzzy_match(token, known.keys())
    if score > 0.8:
        log_outlier('VOCABULARY_GAP', token, context,
            f'Mapped to {known[match]}',
            f'Consider adding "{token}" as alias for {known[match]}')
        return known[match]
    
    # Guess from context
    if 'sun' in token.lower() or 'glass' in token.lower():
        log_outlier('VOCABULARY_GAP', token, context,
            'Guessed LIVING with exterior constraint',
            f'Consider adding {token.upper()} as new SpaceType')
        return 'LIVING'  # With exterior flag
    
    # Unknown
    log_outlier('VOCABULARY_GAP', token, context,
        'Unknown - using GENERIC',
        f'Add "{token}" to vocabulary with appropriate SpaceType mapping')
    return 'GENERIC'
```

---

## Task 8: Compilation Summary

At end of `BuildingCompiler.compile()` or `IntentCompiler.compile()`:

```java
public CompilationResult compile(BuildingDefinition def) {
    OutlierLogger.reset();
    
    // ... existing compilation logic ...
    
    // At end
    Path outlierLog = outputDir.resolve("outliers.log");
    OutlierLogger.summarize(outlierLog);
    
    result.setOutlierCount(OutlierLogger.getTotalCount());
    result.setOutlierSummary(OutlierLogger.getSummary());
    
    return result;
}
```

Summary output format:

```
=== OUTLIER SUMMARY ===
Compilation: output/my_house
Date: 2025-01-29 14:32:00

UNKNOWN_SPACETYPE: 1
  - OBSERVATORY (1 occurrence)
    → GUIDANCE: Add to SpaceType enum with fixture/MEP/validation rules

MISSING_COMPONENT: 2
  - bidet (1 in BATHROOM "ensuite")
    → GUIDANCE: Add to component_library.db
  - japanese_toilet (1 in BATHROOM "master_bath")
    → GUIDANCE: Add to component_library.db

GEOMETRY_IMPOSSIBLE: 1
  - exhaust_fan in BATHROOM "powder" (too small)
    → GUIDANCE: Min width for exhaust_fan = 1.5m. Space is 1.2m.

Total: 4 outliers
Outlier rate: 4/127 elements = 3.1%

Full details: output/my_house/outliers.log
```

---

## Task 9: Test Cases

Create `OutlierHandlingTest.java`:

```java
@Test
void unknownSpaceTypeFallsBackToGeneric() {
    // SPACE type:OBSERVATORY should not crash
    // Should log outlier and use GENERIC
}

@Test
void missingComponentSkipsGracefully() {
    // Request bidet in bathroom
    // Should log and skip, not crash
}

@Test
void unsatisfiableConstraintsRelax() {
    // A adjacent B, A not_adjacent B
    // Should relax not_adjacent and solve
}

@Test
void tooSmallRoomSkipsFixtures() {
    // 1m x 1m bathroom
    // Should skip toilet (too small) with log
}

@Test
void outlierSummaryGenerated() {
    // Compile with multiple outliers
    // Verify outliers.log exists with correct format
}
```

---

## Acceptance Criteria

1. **No crashes** from unknown SpaceType, missing component, or impossible geometry
2. **Every outlier logged** with consistent format
3. **Guidance actionable** - developer knows exactly what to add
4. **Summary generated** after each compilation
5. **Metrics tracked** - outlier rate calculable
6. **Tests pass** for all outlier scenarios

---

## Reference Files

- Architecture: `bim-compiler-dsl-architecture.md`
- SpaceType enum: `src/main/java/com/bim/compiler/dsl/SpaceType.java` (or RoomType.java)
- Component library: `library/component_library.db`
- Fixture placer: `src/main/java/com/bim/compiler/library/FixturePlacer.java`
- Solver: `src/main/java/com/bim/compiler/solver/SpaceSolver.java`
- Validators: `src/main/java/com/bim/compiler/validation/`

---

## Definition of Done

- [ ] OutlierLogger utility created
- [ ] SpaceType fallback implemented with GENERIC
- [ ] Component library fallback with skip
- [ ] Solver constraint relaxation
- [ ] Fixture placement clash → skip with log
- [ ] Validator unknown SpaceType → GENERIC rules
- [ ] Intent resolver vocabulary gap handling
- [ ] Outlier summary at compilation end
- [ ] All tests passing
- [ ] Documentation updated

---

*Task created: January 2025*
*Reference: SA discussion on graceful degradation and developer feedback loop*
