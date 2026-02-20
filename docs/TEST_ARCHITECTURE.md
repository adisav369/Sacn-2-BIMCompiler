# BIM Compiler Test Architecture Reference

**Version:** 1.0  
**Date:** 2026-02-20  
**Purpose:** Reference for new Code sessions — what tests exist, how they're structured, what they enforce, and how to run them.

---

## 1. Test Structure Overview

```
src/test/java/com/bim/compiler/
├── contract/
│   └── CompilerContractTest.java    ← 40 architectural contract tests
│       ├── BoundElement null rejection (guid, ifcClass, bounds, material, rotation, provenance)
│       ├── Pipeline stage assertions (WitnessValidator, SanityChecker, MetadataValidator present)
│       ├── Geometric assertions G1–G8 (coordinate proofs at 0.001mm tolerance)
│       └── Metadata lookup throws on missing (MetadataMissingException, not defaults)
│
├── registry/
│   └── BuildingRegistryTest.java    ← 4 dynamic tests (one per active building)
│       ├── Reads ad_building_registry WHERE is_active = 1 ORDER BY seq_no
│       ├── Per building: element count == expected_elements
│       ├── Per building: SpatialDigest == spatial_digest (if non-null)
│       ├── Per building: witness proofs pass (critical proofs, not advisory)
│       └── Per building: geometry failures <= geometry_fail_threshold
│
└── (legacy — 49 files in src/main/java, NOT src/test)
    ├── SampleHouseE2ETest.java       ← SHOULD BE DELETED (replaced by registry)
    ├── DuplexE2ETest.java            ← SHOULD BE DELETED
    ├── TBLKTNEndToEndTest.java       ← SHOULD BE DELETED
    └── ... 46 more with main() methods ← tech debt, confusion bait
```

**Total: 44 tests (40 contract + 4 registry). All must be green.**

---

## 2. Maven Surefire Configuration (pom.xml)

Contract tests run on EVERY build — not by choice, by configuration:

```xml
<!-- ARCHITECTURAL CONTRACT TESTS — run on EVERY build.
     These tests guard BoundElement, Witness, SanityChecker invariants.
     If a contract test fails, the BUILD fails. Do not skip.
     Do not add <skipTests>. Do not exclude contract tests.
     Fix the code, not the test. -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/contract/*Test.java</include>
            <include>**/contract/*Tests.java</include>
            <include>**/registry/*Test.java</include>
        </includes>
    </configuration>
</plugin>
```

**Key rules:**
- `mvn compile` → tests run. `mvn package` → tests run.
- Code doesn't decide to run tests. Maven decides. Code can't forget.
- The `contract/` and `registry/` package convention means new tests in those packages auto-run.
- NEVER add `<skipTests>`. NEVER exclude contract tests.

---

## 3. CompilerContractTest — What It Guards

```java
package com.red1.bim.contract;

/**
 * ARCHITECTURAL CONTRACT TESTS
 * ============================
 * These tests guard the compiler's core invariants.
 * They are NOT optional. They run on every build.
 * 
 * If a test here fails, do NOT weaken the test.
 * Fix the code that broke the contract.
 *
 * RULE FOR ALL NEW CODE:
 * - Every value from metadata lookup, never hardcoded literals
 * - Every element through BoundElement with all params non-null
 * - Every compilation through WitnessValidator — no bypass
 * - Every placement through SanityChecker — no skip
 * - If metadata lookup returns null → throw, never default
 */
```

### Section 1: BoundElement Null Rejection
Tests that constructor throws on null for: guid, ifcClass, bounds, material, rotation.
**Drift 3 (Contract Dilution) detector.** If Code makes a field nullable, these fail.

### Section 2: Pipeline Stage Presence
```java
assertTrue(pipeline.hasStage(WitnessValidator.class), 
    "WitnessValidator missing from pipeline — NEVER remove this");
assertTrue(pipeline.hasStage(SanityChecker.class),
    "SanityChecker missing from pipeline — NEVER remove this");
assertTrue(pipeline.hasStage(MetadataValidator.class),
    "MetadataValidator missing — NEVER remove");
```
**Drift 4 (Pipeline Bypass) detector.** If Code removes a stage, these fail.

### Section 3: Metadata Lookup Throws on Missing
```java
assertThrows(MetadataMissingException.class, () ->
    metadata.getRotation("NONEXISTENT", "NONEXISTENT"));
```
**Drift 2 (Value Invention) detector.** If Code adds getOrDefault, this catches it.

### Section 4: Geometric Assertions G1–G8
Hard coordinate assertions at 0.001mm tolerance:
- G1: Wall placement coordinates
- G2: Window FRACTION position (centroidX == wall.startX + fraction × wall.length)
- G3: Fixture facing wall (back face coordinate == wall surface coordinate)
- G4: Door centred in opening
- G5: No zero-volume elements
- G6: Element within room bounds
- G7–G8: Building-specific structural proofs

**These expected values were hand-verified from construction knowledge. They are the oracle. Do NOT change them during refactoring.**

---

## 4. BuildingRegistryTest — What It Guards

```java
/**
 * REGISTRY-DRIVEN COMPILATION TEST
 * =================================
 * Reads ad_building_registry. For each row where is_active = 1,
 * ordered by seq_no:
 *   1. Parse the dsl_content
 *   2. Load metadata for that building_id
 *   3. Run the single CompilationPipeline
 *   4. Assert element count == expected_elements
 *   5. Assert SpatialDigest == spatial_digest (if non-null)
 *   6. Assert all critical witness proofs PASS
 *   7. Assert geometry failures <= geometry_fail_threshold
 *
 * To add a new building: INSERT one row. Zero Java changes.
 * To disable temporarily: SET is_active = 0.
 * To control test order: SET seq_no.
 */
```

### Current Buildings

| building_id | name | elements | threshold | status |
|-------------|------|----------|-----------|--------|
| SAMPLE_HOUSE | SampleHouse | 55 | 0 | UK residential, Rosetta Stone 1 |
| DUPLEX | Duplex | 1085 | 0 | US residential, Rosetta Stone 2 |
| TBLKTN | CitizenHome | 69 | 0 | Malaysian generative, first pure DSL |
| TERMINAL | Terminal | 51088 | 8 | Malaysian institutional, Rosetta Stone 3 |

### Building Assertions (ad_building_assertions)

```sql
CREATE TABLE ad_building_assertions (
    building_id   TEXT NOT NULL REFERENCES ad_building_registry,
    assertion_id  TEXT NOT NULL,
    element_match TEXT NOT NULL,    -- 'IfcRoof', 'IfcWall:PARTY', etc.
    property      TEXT NOT NULL,    -- 'centroidX', 'maxZ', 'count'
    operator      TEXT NOT NULL,    -- 'EQUALS', 'GREATER_THAN', 'BETWEEN'
    expected      TEXT NOT NULL,    -- '4.5', '0|5.5' for BETWEEN
    tolerance     REAL DEFAULT 0.001,
    PRIMARY KEY (building_id, assertion_id)
);
```

Building-specific geometric checks loaded from metadata, not separate test classes.

---

## 5. The Drift Diagnostic Script

Run after each refactor. Paste output to watchdog for assessment:

```bash
echo "=== DRIFT DIAGNOSTIC ==="
echo ""
echo "--- 1. BoundElement constructor signature ---"
grep -n "public.*BoundElement\|record BoundElement" DAGCompiler/src/main/java -r
echo ""
echo "--- 2. Nullable fields (should be ZERO) ---"
grep -rn "Optional\|@Nullable\|getOrDefault\|orElse(" DAGCompiler/src/main/java --include="*.java" | grep -v "test" | grep -v "Test"
echo ""
echo "--- 3. Magic numbers in resolvers (should be ZERO) ---"
grep -rn "rotation = [0-9]\|thickness = [0-9]\|spacing = [0-9]\|height = [0-9]\|width = [0-9]" DAGCompiler/src/main/java --include="*.java" | grep -v "test\|Test\|enum\|final.*="
echo ""
echo "--- 4. Pipeline stages ---"
grep -n "new.*Stage\|new.*Resolver\|new.*Validator\|new.*Checker\|new.*Writer\|List.of" DAGCompiler/src/main/java -r | grep -i "pipeline\|PIPELINE\|stages"
echo ""
echo "--- 5. Per-building special cases (should be ZERO) ---"
grep -rn "equals(\"SampleHouse\"\|equals(\"Duplex\"\|equals(\"TBLKTN\"\|equals(\"Terminal\"\|equals(\"Ifc4_\"\|equals(\"Ifc2x3_\"" DAGCompiler/src/main/java --include="*.java"
echo ""
echo "--- 6. Test count and status ---"
mvn test -pl DAGCompiler -q 2>&1 | tail -5
echo ""
echo "--- 7. SpatialDigests ---"
grep -A2 "spatialDigest\|SpatialDigest" DAGCompiler/src/test/java -r | grep "assertEquals"
echo ""
echo "--- 8. getOrThrow vs getOrDefault method count ---"
echo "OrThrow methods: $(grep -rn "OrThrow\|orThrow" DAGCompiler/src/main/java --include="*.java" | wc -l)"
echo "OrDefault methods: $(grep -rn "OrDefault\|orDefault\|orElse" DAGCompiler/src/main/java --include="*.java" | grep -v test | grep -v Test | wc -l)"
echo ""
echo "=== END DIAGNOSTIC ==="
```

### Reading the Output

| Section | Clean | Dirty | What It Means |
|---------|-------|-------|---------------|
| 1. BoundElement | Record with non-null fields | Constructor with nullable params | Contract dilution (Drift 3) |
| 2. Nullable fields | Zero hits | Any hit in resolver/placer | Value invention path exists (Drift 2) |
| 3. Magic numbers | Zero hits | `rotation = 0`, `height = 2.8` | Hardcoded values bypassing metadata (Drift 2) |
| 4. Pipeline stages | List.of with all stages | Missing MetadataValidator/Witness | Pipeline bypass (Drift 4) |
| 5. Per-building | Zero hits | `equals("SampleHouse")` etc. | Building-specific logic in pipeline (Drift 5) |
| 6. Tests | 44/44 green | Any failure | Regression |
| 7. Digests | assertEquals with known hashes | Changed or missing | Geometry regression (Drift 7) |
| 8. OrThrow ratio | OrThrow >> OrDefault | OrDefault >> OrThrow | Fallback methods dominate (Drift 2) |

### Baseline after RM-9 (2026-02-20)

```
OrThrow:    8 (target: >> 30)
OrDefault:  133 (target: << 100)
Tests:      44/44 green
Digests:    SH=55, DX=1085, TB-LKTN=69, Terminal=51088
Per-building special cases: 0
BoundElement null-checks: 3/11 (known debt — Drift 3 WARNING)
```

---

## 6. Known Tech Debt

### 49 Legacy Test Files in src/main/java

**Problem:** Old per-building compiler files with `main()` methods sitting in `src/main/java` instead of `src/test`. They're not running in the pipeline. They're confusion bait — Code might copy patterns from them.

**Fix:** Delete after metadata integrity refactor lands. They were replaced by BuildingRegistryTest.

**Prompt when ready:**
```
Delete all *Test.java and *E2E*.java files from src/main/java. 
These are legacy per-building tests replaced by BuildingRegistryTest. 
Verify: mvn test still 44/44 green after deletion.
```

### BoundElement Null-Checks: 3 of 11

**Problem:** Only placement, transformedMesh, geometryHash are null-checked. Eight fields (guid, ifcClass, elementRef, type, storey, provenance, materialName, materialRgba) can be null without rejection.

**Fix:** Part of sealed types refactor — BoundElement builder with mandatory fields.

### PlacementProver.detectBuildingName()

**Problem:** Uses filename-based heuristic instead of metadata to determine building identity.

**Fix:** Pass building_id from ad_building_registry through CompilationContext.

---

## 7. Test Rules for Code

These rules are embedded in CompilerContractTest comments but repeated here for emphasis:

1. **If a test fails, fix the code — not the test.** The only valid reason to change a test's expected value is if the expected value was wrong, with evidence from metadata.

2. **SpatialDigest changes during refactoring = something broke.** Pure refactors produce identical output. If the digest changes, the refactor changed behaviour.

3. **New tests must use hard assertions.** `assertEquals`, `assertThrows`, `assertTrue` only. Never `System.out.println` warnings. Never advisory-only checks.

4. **Every new building = one SQL INSERT to ad_building_registry, zero Java files.** If a new building requires a new test class, the architecture is wrong.

5. **Contract tests and registry tests are not negotiable.** They cannot be @Disabled, excluded from surefire, or softened with wider tolerances. They are the law.

6. **Run tests before AND after every change.** Not just at the end of a refactor. Per-class, tests green throughout.

---

## 8. Verification Commands

```bash
# Run all tests
mvn test -pl DAGCompiler -q

# Run only contract tests  
mvn test -pl DAGCompiler -Dtest="**/contract/*Test" -q

# Run only registry tests
mvn test -pl DAGCompiler -Dtest="**/registry/*Test" -q

# Run drift diagnostic
# (copy full script from Section 5)

# Check for PRAGMA foreign_keys
grep -r "foreign_keys" DAGCompiler/src/

# Count OrThrow vs OrDefault
echo "OrThrow: $(grep -rn 'OrThrow\|orThrow' DAGCompiler/src/main/java --include='*.java' | wc -l)"
echo "OrDefault: $(grep -rn 'OrDefault\|orDefault\|orElse' DAGCompiler/src/main/java --include='*.java' | grep -v test | wc -l)"
```
