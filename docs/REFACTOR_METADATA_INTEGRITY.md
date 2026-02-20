# Refactor: Metadata Integrity Enforcement

**When to use:** After sealed types refactor (REFACTOR_SEALED_TYPES.md) is complete and all tests green.  
**Prerequisites:** `MetadataStore` has `getXxxOrThrow()` pattern, `CompilerStage` interface exists, `List<CompilerStage>` pipeline exists, CompilerContractTest and BuildingRegistryTest passing.  
**This is the final layer:** Types enforce code → Pipeline enforces stages → Data enforces metadata → Tests enforce everything.

---

## Prompt for Code

**Audit metadata integrity: the Java contracts are only as strong as the data they read. Apply referential integrity enforcement to all ad_* tables so that garbage cannot enter the system.**

**Step 1: Map every ad_* table and its foreign key relationships. Show me:**

```
ad_element_rule.wall_type_id → ad_wall_type.wall_type_id
ad_element_rule.product_type → ad_product_dim.product_type
ad_wall_face.room_id → ad_room_boundary.room_id
ad_vertical_branch.system_id → ad_vertical_system.system_id
... (all relationships)
```

**Step 2: For every foreign key, create a SQL constraint if not already present:**

```sql
-- Every element rule must reference a real wall type
ALTER TABLE ad_element_rule 
    ADD CONSTRAINT fk_wall_type 
    FOREIGN KEY (wall_type_id) REFERENCES ad_wall_type(wall_type_id);

-- SQLite enforces this ONLY if PRAGMA foreign_keys = ON;
-- The compiler MUST set this on every DB connection.
```

**Step 3: Create `MetadataIntegrityTest.java` in the contract package that verifies on every build:**

```java
/**
 * METADATA INTEGRITY CONTRACT
 * ===========================
 * Garbage In = Garbage Out. The compiler trusts ad_* tables.
 * If a row references a wall_type that doesn't exist,
 * the compiler silently produces wrong geometry.
 * 
 * These tests load the actual metadata DB and verify:
 * - Every foreign key points to a real row (no dangling refs)
 * - Every provenance field is non-null and valid
 *   (EXTRACTED_TERMINAL, RESEARCHED_MS_xxxx, or PENDING_reason)
 * - Every dimension field is positive (no zero-width walls)
 * - Every rotation is a valid value (0, 90, 180, 270)
 * - No orphan rows (wall_type defined but never referenced)
 * - No duplicate keys
 */
```

**Step 4: Create `MetadataValidator.java` that runs at compiler startup before any compilation:**

```java
public class MetadataValidator implements CompilerStage {
    @Override
    public StageResult process(CompilationContext ctx) {
        // Runs FIRST in the pipeline, before any resolver
        List<String> errors = new ArrayList<>();
        
        // Dangling references
        for (ElementRule rule : ctx.meta().allRules()) {
            if (rule.wallTypeId() != null 
                && ctx.meta().getWallType(rule.wallTypeId()) == null)
                errors.add("Rule " + rule.id() 
                    + " references missing wall_type: " + rule.wallTypeId());
        }
        
        // No PENDING in production builds
        // (PENDING allowed during development, blocked for release)
        
        // Positive dimensions
        for (WallType wt : ctx.meta().allWallTypes()) {
            if (wt.thicknessMm() <= 0)
                errors.add("Wall type " + wt.id() 
                    + " has non-positive thickness: " + wt.thicknessMm());
        }
        
        if (!errors.isEmpty())
            throw new MetadataIntegrityException(errors);
    }
    
    @Override
    public String stageName() { return "MetadataValidator"; }
}
```

**Step 5: Wire `MetadataValidator` as the FIRST stage in the pipeline — before GridResolver, before anything. If metadata is corrupt, nothing else runs.**

```java
List<CompilerStage> PIPELINE = List.of(
    new MetadataValidator(),   // FIRST — validate data before use
    new GridResolver(),
    new RoomResolver(),
    new WallResolver(),
    new MEPResolver(),
    new SanityChecker(),
    new WitnessValidator(),
    new OutputWriter()
);
```

**Step 6: Ensure every DB connection sets `PRAGMA foreign_keys = ON;`** — find every place SQLite connections are opened and add this pragma. Without it, SQLite silently ignores FK constraints.

**Step 7: Show me the complete relationship map when done. I need to see every table, every FK, every provenance coverage percentage. This is the compiler's data contract — as important as the code contract.**

---

## Watchdog Constraints

- Run all tests before starting. All must be green.
- SpatialDigest for all buildings must not change. This adds validation, not behaviour change.
- CompilerContractTest must still pass — all 40+ tests.
- BuildingRegistryTest must still pass for all active buildings.
- MetadataValidator must be in the pipeline List — add a test in CompilerContractTest:
  `assertTrue(pipeline.hasStage(MetadataValidator.class), "MetadataValidator missing — NEVER remove")`
- Every new test uses hard assertions (assertEquals, assertThrows, assertTrue). No System.out.println warnings. No advisory-only checks.
- If metadata validation reveals existing bad data, fix the data — do not weaken the validator.
- If a test fails, fix the code or data, not the test.

---

## Verification

```bash
# Before starting
mvn test -pl DAGCompiler -q
# Should show: all tests green

# After MetadataIntegrityTest added
mvn test -pl DAGCompiler -q
# Should show: increased test count, all green

# After MetadataValidator wired into pipeline
mvn test -pl DAGCompiler -q
# Should show: all green, SpatialDigests unchanged

# Verify PRAGMA is set
grep -r "foreign_keys" DAGCompiler/src/
# Should show: PRAGMA foreign_keys = ON in every DB connection method
```

---

## The Complete Enforcement Stack After This Refactor

```
Layer 1: Java Type System (from sealed types refactor)
  Sealed Placement interface — invalid modes can't compile
  BoundElement non-null constructor — missing fields can't compile
  MetadataStore.getOrThrow — null lookups throw, never default

Layer 2: Metadata Integrity (from THIS refactor)
  SQL foreign keys — dangling references rejected by SQLite
  MetadataValidator — first pipeline stage, rejects bad data
  MetadataIntegrityTest — build-time verification of all ad_* tables

Layer 3: Contract Tests (from RM-8)
  CompilerContractTest — null rejection, pipeline stages, geometry
  BuildingRegistryTest — all buildings compile, digest, prove

Layer 4: Watchdog Review (human)
  Spot-check questions from docs/CODE_WATCHDOG.md
  Plan review before execution
  "Did you change the test or fix the code?"
```

When all four layers are active, the wrong thing cannot compile (Layer 1), cannot load (Layer 2), cannot pass the build (Layer 3), and cannot escape review (Layer 4).

---

## After This Refactor

The enforcement stack is complete. Future work is building construction, not compiler infrastructure:
- Terminal relational conversion (T1–T5)
- New building typologies
- MEP spec tables
- Vertical spine systems
- GUI/Bonsai integration

Each of these operates within the enforced pipeline — the infrastructure prevents drift while the construction knowledge grows.
