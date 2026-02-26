# Refactor: Sealed Types and Pipeline Hardening

**When to use:** After RM-5 (flat table becomes computed cache) is complete and all tests green.  
**Prerequisites:** Registry-driven pipeline (RM-8), CompilerContractTest (40+ tests), BuildingRegistryTest passing for all active buildings.  
**Next after this:** REFACTOR_METADATA_INTEGRITY.md

---

## Prompt for Code

**Refactor: Apply iDempiere-strength Java patterns across the compiler. The codebase has grown organically — methods do too much, contracts are enforced by convention instead of types, and the same lookup-or-guess pattern keeps recurring. Time to harden.**

**Pattern 1: Sealed types for placement modes.** Currently placement mode is likely a string or enum that code can ignore. Make it a sealed interface:

```java
public sealed interface Placement
    permits GridPlacement, FractionPlacement, AbsolutePlacement, BayPlacement {
    BoundingBox resolve(RoomContext room, MetadataStore meta);
}
```

Each placement type carries only the fields it needs. `FractionPlacement` has `fraction` and `wallFace`. `GridPlacement` has `row` and `col`. No invalid combinations possible. The `resolve()` method returns a `BoundingBox` or throws — no null, no default.

**Pattern 2: Context objects that eliminate parameter passing.** Stop passing 8 parameters through 5 methods. Create:

```java
public record RoomContext(
    String storeyId, int floorLevel, BoundingBox roomBounds,
    List<WallFace> walls, MetadataStore meta
) {
    public WallFace wallOrThrow(String faceName) {
        return walls.stream()
            .filter(w -> w.face().equals(faceName))
            .findFirst()
            .orElseThrow(() -> new MetadataMissingException(
                "No wall face " + faceName + " in room " + roomBounds));
    }
}
```

Every resolver method takes `RoomContext` — it can't forget the floor level or lose the room bounds between calls.

**Pattern 3: Builder with mandatory fields (iDempiere's MOrder/MInvoice pattern).** For elements that need many fields, use a builder that refuses to build until all required fields are set:

```java
BIMElement element = BIMElement.builder()
    .guid(guid)                    // required
    .ifcClass("IfcSanitaryTerminal") // required
    .bounds(resolvedBounds)        // required
    .material(material)            // required
    .rotation(rotation)            // required
    .provenance(EXTRACTED_TERMINAL) // required
    .build();  // throws if ANY required field is null
```

**Pattern 4: Pipeline as typed chain, not method calls.** The compilation pipeline should be a `List<CompilerStage>` that runs in order, not a sequence of method calls that Code can reorder or skip:

```java
public interface CompilerStage {
    StageResult process(CompilationContext ctx);
    String stageName();
}

// Pipeline is declared once, immutable:
List<CompilerStage> PIPELINE = List.of(
    new GridResolver(),
    new RoomResolver(),
    new WallResolver(),
    new MEPResolver(),
    new SanityChecker(),      // can't be skipped
    new WitnessValidator(),   // can't be skipped
    new OutputWriter()
);
```

Adding a new resolver = adding one entry to the list. Can't forget Witness because it's in the list, not called by hand.

**Pattern 5: MetadataStore as single source of truth with no fallbacks.**

```java
public class MetadataStore {
    /** Returns value or throws. NEVER returns null. NEVER defaults. */
    public int getRotationOrThrow(String productType, String context) {
        Integer val = lookupRotation(productType, context);
        if (val == null) throw new MetadataMissingException(
            productType + "/" + context + " — add to ad_product_dim");
        return val;
    }
    // No getRotationOrDefault() method exists. Don't create one.
}
```

**Apply systematically: audit every resolver class. For each one, extract placement logic into the sealed Placement type, wrap parameters into RoomContext, replace raw constructors with builder-with-mandatory-fields, and ensure all metadata access goes through MetadataStore.getXxxOrThrow(). Run CompilerContractTest after each class. Commit per class — small refactors, each green.**

---

## Watchdog Constraints

- Run all tests before starting. All must be green.
- SpatialDigest for all buildings must not change. This is a pure refactor — behaviour stays identical.
- BoundElement null rejection tests in CompilerContractTest must still pass.
- BuildingRegistryTest must still pass for all active buildings.
- No new `getOrDefault()` methods anywhere. Only `getOrThrow()`.
- No `if (building.name().equals("..."))` anywhere in pipeline code.
- Commit per class. Tests green throughout, not just at the end.
- If a test fails, fix the code, not the test.

---

## Verification

```bash
# Before starting
mvn test -pl DAGCompiler -q
# Should show: 44+ tests, all green

# After each class refactored
mvn test -pl DAGCompiler -q
# Should show: same test count, all green, same SpatialDigests

# After completion
mvn test -pl DAGCompiler -q
# Should show: same test count, all green, all SpatialDigests unchanged
```

---

## After This Refactor

Next session uses REFACTOR_METADATA_INTEGRITY.md which builds on:
- `MetadataStore.getXxxOrThrow()` methods created here
- `CompilerStage` interface and `List<CompilerStage>` pipeline created here
- `MetadataValidator` will become the first stage in that pipeline
