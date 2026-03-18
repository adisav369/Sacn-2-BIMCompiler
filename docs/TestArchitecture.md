# Test Architecture — QA Hardening Plan

## Anti-Drift Policy (read first)

**These rules override all other instructions. No exceptions.**

1. **No Magic Coordinates.** If a transform requires a hardcoded (x,y,z) instead of a derived DAG offset, STOP. Ask for the parent matrix. Use named constants referencing a standard (IRC/NFPA/IEC) or derive from the database.
2. **No Invented Data.** If the BOM spec is unclear or missing, DO NOT invent a placeholder. Stop and request the specific file or data model. Every `child_product_id` must resolve. Every MAKE needs an M_Product stub.
3. **No Silent Geometry.** Before modifying geometry (scale, translate, mesh bind), state the intended math. Duplicate vertex arrays are waste — check the mesh cache first, normalize hashes to mm precision.
4. **No Hallucinated Success.** If a test fails, state the exact error. Do not weaken assertions, add workarounds, or re-seal without explaining why.
5. **Verify Before Commit.** Run `DataIntegrityTest` (D-1b catches ALL orphan types). Run `RosettaStoneGateTest` (G1-G6). Both must be GREEN.

---

## Problem Statement

Deep QA audit (2026-03-11) found that tests verify **consistency with themselves**,
not **correctness against external truth**. The compiler can produce wrong output
and all gates stay green because tests check counts, not content.

This document defines the fixes, grouped by severity.

---

## CRITICAL Fixes (C1–C7)

### C1. Golden Digest Verification — PARTIAL

**Problem:** `BOMDigestVerifyTest` and `HelloWorldVerbTest` assert digests are
"not null" — never compared to a known-good value.

**Fix (Phase 3, 2026-03-11):**
1. BOMDigestVerifyTest: EXT_SH/EXT_DX → BUILDING BOM tree walk via `computeFromBOMTree()`.
   Reconstructs world coords from MAKE offsets + BUY centroids. 5/5 tests PASS.
2. Golden digests confirmed stable: SH=496022db, DX=4dd805b3.
3. **Remaining:** Store digests as constants and assert `assertEquals` (not just "not null").
4. **Remaining:** HelloWorldVerbTest golden digest comparison.

**Files:**
- `DAGCompiler/.../contract/BOMDigestVerifyTest.java` — DONE (tree walk)
- `DAGCompiler/.../validation/SpatialDigest.java` — DONE (computeFromBOMTree added)
- `BIM_COBOL/.../HelloWorldVerbTest.java` — lines 156-165 (pending)

---

### C2. Content Spot-Check Assertions

**Problem:** `F5IntegrationTest` and `PlaceBomVerbTest` write to output.db then
read back row counts. Never check if the data is correct.

**Fix:**
1. After compilation, spot-check 5 elements per building:
   - Pick elements by known product_id (e.g., `SH_WALL_EXTERIOR_001`)
   - Assert exact coordinates (dx, dy, dz) against extraction reference
   - Assert material_name and material_rgba match source BOM
   - Assert geometry_hash matches component_library entry
2. Add `SpotCheckContract` test class with per-building element verification

**Files:**
- `BIM_COBOL/.../F5IntegrationTest.java` — lines 382-387
- `BIM_COBOL/.../verb/PlaceBomVerbTest.java` — lines 107-124
- NEW: `DAGCompiler/.../contract/SpotCheckContract.java`

---

### C3. Gate Count Cross-Validation

**Problem:** Hardcoded `assertEquals(55, ...)` — no independent source for "55".

**Fix:**
1. At test time, query extraction DB: `SELECT COUNT(*) FROM I_Element_Extraction WHERE building_id = ?`
2. Use that live count as expected value instead of hardcoded `55`
3. This way, if `{PREFIX}_BOM.db` drifts from extraction, the test catches it

**Files:**
- `DAGCompiler/.../contract/ExtractedBOMWalkTest.java` — line 62
- `BIM_COBOL/.../verb/PlaceBomVerbTest.java` — line 114

---

### C4. Enable DX Furniture Centroid Test — DONE (partial)

**Problem:** `FurnitureGeometryTest:127` uses `assumeTrue(false)` — hides a real
coordinate-frame bug (unit-local positive Y vs IFC global negative Y).

**Fix (2026-03-11):** Converted to `@Disabled("TICKET: DX coordinate frame alignment")`.
Maven reports SKIPPED (visible) instead of silent pass. T6 tamper rule exempts
`@Disabled` with TICKET: reference (negative lookahead regex).

**Remaining:** Actual DX coordinate frame fix (align unit-local positive Y with
IFC global negative Y). Tracked as Phase 4.

**Files:**
- `DAGCompiler/.../contract/FurnitureGeometryTest.java` — line 124

---

### C5. Fix SQL Injection — DONE

All 3 files already use `PreparedStatement` with `?` placeholders. No injection risk.

**Files:** `PlaceBomVerb.java`, `EnBlocVerb.java`, `WalkThruVerb.java` — verified 2026-03-11.

---

### C6. Fix Negative Tack Offsets in `{PREFIX}_BOM.db`

**Problem:** 3 m_bom_line records have negative dx/dy/dz, violating §4.
Java PO guards exist but Python scripts bypass them via raw SQL.

**Fix:**
1. Audit the 3 records in `RosettaStoneToBOM.py`:
   - `KITCHEN_CABINET_SET` child 111: dx = -3.0
   - `KITCHEN_PREFAB_MY` child 152: dz = -0.3
   - `SH_LIVING_SET` child 197: dx = -0.663
2. Determine: wrong reference point or intentional semantics?
3. Correct offsets to positive (re-derive from extraction) or document exception
4. Add `Rule8_NoNegativeOffsets` assertion to `MetadataIntegrityTest`

**Files:**
- `scripts/RosettaStoneToBOM.py` — BOM_LINE data arrays
- NEW assertion in `DAGCompiler/.../contract/MetadataIntegrityTest.java`

---

### C7. Convert StackedDuplexWitnessTest to JUnit — DONE

Already in `src/test/java/.../contract/StackedDuplexWitnessTest.java` as JUnit 5.
Uses `@Test`, `assertEquals`, proper assertions. Verified 2026-03-11.

---

## HIGH Fixes (H1–H7)

### H1. Derive Test Expected Values from Data

Replace all hardcoded magic numbers with live queries at test time.

| Constant | Current | Source |
|----------|---------|--------|
| 55 (SH elements) | Hardcoded | `SELECT COUNT(*) FROM I_Element_Extraction` |
| 1099 (DX elements) | Hardcoded | Same query, DX extraction DB |
| 8 (SH IFC classes) | Hardcoded | `SELECT COUNT(DISTINCT ifc_class)` |
| 3898 (remaining mm) | Hardcoded | Compute from AABB - placed width |
| 8869 (living width) | Hardcoded | Query from m_bom AABB |

---

### H2. Verb Wrappers for Raw SQL — DONE (H2+ expanded)

All 8 raw SQL statements on protected + state tables (m_bom, m_bom_line, c_order,
wm_empty_storage_line) are now wrapped in BIM COBOL verbs. T16 tamper rule enforces
zero regressions.

| Location | Raw SQL | Verb Wrapper | Status |
|----------|---------|-------------|--------|
| TopologyWriter.writeBom() | INSERT m_bom + m_bom_line | `COMPOSE PREFAB BOM` | DONE |
| TopologyWriter.fillBuffers() | DELETE m_bom_line (variance) | `CLEAR VARIANCE FROM BOM` | DONE |
| TopologyWriter.fillBuffers() | SELECT+UPDATE+INSERT m_bom_line | `FILL BUFFERS IN BOM` | DONE |
| CompilationPipeline.copyCOrderToOutput() | INSERT c_order | `REGISTER BUILDING` | DONE |
| CompilationPipeline.updateCOrderComputedResults() | UPDATE c_order | `COMPLETE BUILDING` | DONE |
| M_WmEmptyStorageLine.voidForBuilding() | UPDATE wm_empty_storage_line | `VOID EMPTY_SPACE FOR BUILDING` | DONE |

**T16 tamper rule** scans `{DAGCompiler,TopologyMaker,ORMSandbox}/src/main/**/*.java`
for `INSERT/UPDATE/DELETE` on `m_bom`, `m_bom_line`, `c_order`, `wm_empty_storage_line`.
BIM_COBOL excluded (authorized verb layer). Current violations: **0**.

---

### H3. Data-Driven BOM Category Mapping

Replace hardcoded category lists and switch statements with DB lookups:

| Location | Hardcoded | Fix |
|----------|-----------|-----|
| CompilationPipeline:506 | `IN ('LI','BD','KT','BT','DN')` | Query M_BomCategory |
| CompilationPipeline:597 | `categoryToRoomType()` switch | Lookup M_BomCategory.getName() |
| ComposeBuildingVerb:36 | `RESIDENTIAL→RE` map | Query C_DocType |
| BomTemplateComposer:128 | Hardcoded `"RE"` root | Derive from docSubType |
| BomTemplateContract:65 | Hardcoded `"RE"` root | Same |

---

### H4. Remove Building Type String Checks

| Location | Check | Fix |
|----------|-------|-----|
| BuildingInspector:508 | `contains("SampleHouse")` | Derive prefix from BOM structure |
| AllModelsReportGenerator:197 | `"TB".equals(subType)` | Use data-driven approach |

---

### H5. Fix Error Suppression — DONE

All 4 patterns log `[WARN]` — defensible error handling, not suppression.
PlaceBomVerb resource leak fixed via try-with-resources (ComponentLibrary
now implements AutoCloseable). Verified 2026-03-11.

---

### H6. Semantic Witness Verification

`PrimeRuleWitnessTest` checks "field exists and > 0" — needs to check
"field matches reference geometry."

**Fix:** For each BUILDING BOM, compare AABB against extraction DB envelope:
```
actual_width = query m_bom.aabb_width_mm WHERE bom_id = BUILDING_SH_STD
expected_width = query MAX(x) - MIN(x) FROM I_Element_Extraction WHERE building = SH
assertEquals(expected_width, actual_width, TOLERANCE)
```

---

### H7. Re-enable Default Maven Test Phase

Remove `<skip>true</skip>` from DAGCompiler `pom.xml` default-test execution,
or document why custom executions are necessary.

---

## MEDIUM Fixes (M1–M6)

| # | Fix | Location |
|---|-----|----------|
| M1 | Remove invented coordinates from backup code | `backup_phase_DE4/AutoFitter.java` |
| M2 | Centralize thresholds in BIMConstants | `CheckPlacementVerb:28-32` |
| M3 | Track C_OrderLine as Phase F debt | PROGRESS.md |
| M4 | Automate expected_elements derivation | `construction_manifest.yaml` |
| M5 | Remove @Order test dependencies | 6 occurrences |
| M6 | Document ST→RE mapping in data model | `MCDocType.java:81` |

---

## Execution Order

**Phase 1 — Stop the Bleeding:** DONE
- ~~C5 (SQL injection)~~ — already PreparedStatement
- ~~C7 (move witness test)~~ — already JUnit 5 in src/test/
- ~~H5 (error suppression)~~ — all log [WARN], PlaceBomVerb try-with-resources fixed

**Phase 2 — Golden Values:** DONE (2026-03-11)
- ~~C1 (golden digests)~~ — PARTIAL: BOMDigestVerifyTest tree walk done, digests confirmed stable.
  Remaining: store as constants + HelloWorldVerbTest.
- G5 material_rgba pipeline: RosettaStoneExtract.py now writes all instance columns.
- G4-TAMPER: 48→0 violations (T14 catch-ignored ×23, T15 assertNotNull ×5, T6 TICKET-exempt).

**Phase 3 — Content Verification (next session):**
- C1 remaining: golden digest assertEquals + HelloWorldVerbTest
- C2 (spot-check contract)
- C3 (live count queries)
- C6 (negative tack offsets)
- H1 (derive expected values)
- H6 (semantic witness)

**Phase 4 — Architecture Cleanup (when stable):**
- ~~C4 (DX furniture test)~~ DONE (partial) — @Disabled with TICKET. Actual coord fix pending.
- ~~H2 (verb wrappers)~~ DONE — 6 verbs + T16 tamper rule (expanded to wm_empty_storage_line)
- H3–H4 (data-driven mappings)
- H7 (Maven config)
- M1–M6 (medium fixes)

---

## Traceability Matrix — Spec → Test → Witness

**Purpose:** When a BBC.md section changes, this table shows exactly which
tests are affected. When a test fails, this table shows which spec it traces
to. Without this mapping, spec changes silently orphan tests — the test still
passes but no longer proves what the spec requires.

**CTFL principle:** Every test case traces to a requirement. Every requirement
has at least one test. Untested requirements are gaps. Tests without requirements
are waste.

### BBC.md §1–§3 — BOM Structure and Compilation Model

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §1 Three BOM dimensions | Category+Owner+SpaceSize govern selection | CompilerContractTest | G1-COUNT | PASS |
| §1.1 Disciplines as metadata | No switch(docBaseType) in BOM path | DriftGuardTest D6 | G4-TAMPER | PASS (legacy DSL path excluded) |
| §2 Gospel Principle | Every element traces to IFC (EXTRACTED) or template (GENERATIVE) | DataIntegrityTest D-1/D-4 | G5-PROVENANCE | PASS |
| §2.1.6 Count invariant | SUM(non-PHANTOM qty) = output count | ExtractedBOMWalkTest | G1-COUNT | PASS (SH/DX/TE) |
| §2.2 Recursive placement | Walker decides BOM-vs-leaf by m_bom existence | BOMWalkerTest | W-DS-15 | PASS |
| §2.2 component_type ignored | No code branches on BUY/MAKE/PHANTOM | DriftGuardTest | G4-TAMPER (structural) | PASS |
| §3.3 EN-BLOC | Single BOM per singularity, no selection cascade | RosettaStoneGateTest | G1-G6 | PASS (SH/DX/TE) |
| §3.4 WALK-THRU | Multi-candidate selection per slot | — | W-WALKTHRU-DIFFERS-1 | **PENDING** (unproven) |

### BBC.md §4 — Tack Convention

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §4.0 LBD offsets | dx = child.minX - parent.minX (never centroid) | BomValidator | W-TACK-1 | IMPLEMENTED (advisory → FAIL after TACK-FIX) |
| §4.0 extends to work_output | C_OrderLine.dx/dy/dz uses same LBD convention | WorkOutputTackTest | W-TACK-WO-1 | **SPEC ONLY** (G4_SRS §5.4) |
| §4.1 World coord reconstruction | element_LBD = origin + Σ(tack_from[i]) | PlacementCollectorVisitorTest | SB-2 | PASS |
| §4.1 Origin convention | Only BUILDING BOM has non-zero origin | BOMChainMathTest | — | PASS (R16 fix verified) |
| §4.2 BUFFER invariant | parent.width = SUM(children.allocated_width) | BomValidator | W-BUFFER-1 | IMPLEMENTED (advisory) |
| §4.3 Centroid drift fix | ScopeBomBuilder uses minX not centroidX | — | W-TACK-1 post-fix | **IMPLEMENTED** (testing held for post-SRS expedition) |

### BBC.md §4 — Tack Convention (FIX-1/2/3 from TACK_FIX_SPEC)

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| TACK-FIX FIX-1 | ScopeBomBuilder leaf offset = LBD | (unit test §4.3) | W-TACK-1 PASS post-fix | **SPEC ONLY** |
| TACK-FIX FIX-2 | FloorRoomBomBuilder FLOOR→SPACE offset | (pipeline coord test §4.4) | FLOOR→SPACE dx ≠ 0 | **SPEC ONLY** |
| TACK-FIX FIX-3 | VerbDetector.detectCluster minX offsets | (unit test §4.3) | Cluster offsets ≥ 0 | **SPEC ONLY** |
| TACK-FIX §4.4 | Pipeline coordination chain (cumulative) | (integration test §4.4) | BUILDING→LEAF sum = world LBD | **SPEC ONLY** |

### BBC.md §3.2 — ESLine Mechanism

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §3.2 ESLine tack_from | ESLine.tack_from = m_bom_line.dx/dy/dz | — | W-ESLINE-TACK-1 | **PENDING** (needed before WALK-THRU) |
| §3.2 extends to work_output | CO_EmptySpaceLine.tack_from = C_OrderLine.dx/dy/dz | — | — | **PENDING** |

### G4_SRS — work_output.db

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| G4_SRS §2.1 | CreateNew spawns master + sub-orders + ASI | ConstructionModelSpawnerTest | spawn counts derived from BOM | **SPEC ONLY** |
| G4_SRS §2.2 | Save creates sub-C_Order (CO) + W_Variant pointer | WorkOutputDAOTest | save_createsSubOrderAndVariant | **SPEC ONLY** |
| G4_SRS §2.3 | Recall copies sub-order (non-destructive) | SaveRecallIntegrationTest | recall_spawnsNewSubOrder | **SPEC ONLY** |
| G4_SRS §3 | DocStatus: DR→IP→AP→CO (master), DR→IP→CO (sub) | SaveRecallIntegrationTest | approve/promote lifecycle | **SPEC ONLY** |
| G4_SRS §3 AP gate | AP requires PlacementValidator PASS + dangles + tack | SaveRecallIntegrationTest | approve_blockOnValidationFail | **SPEC ONLY** |
| G4_SRS §5.4 | Promoted m_bom_line preserves LBD tack from C_OrderLine | WorkOutputTackTest | promote_preservesTackConvention | **SPEC ONLY** |

### DocValidate — Validation Engine

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| DocValidate §15.1 | 3-tier validation (per-disc, cross-disc, vertical) | PlacementValidatorImplTest | 7 tests | PASS |
| DocValidate §15.2 | ConstructionModelSpawner spawn sequence | ConstructionModelSpawnerTest | spawn counts | **SPEC ONLY** |
| DocValidate §15.3 | Non-Disturbance protocol (exceptions vs adjustments) | NonDisturbanceTest | 6 tests | PASS |
| DocValidate §15.5 | 17 concrete mining rules (M1-M17) | — | V004_mined_rules.sql | **SQL SEEDED** (Non-Disturbance: G4_SRS §6) |
| DocValidate §15.5 M16 | Opening face-anchor consistency | — | AD_Val_Rule 812 | **SQL SEEDED** (tolerance 10mm, skip partitions <150mm) |
| DocValidate §15.5 M17 | Opening host association | — | AD_Val_Rule 813 | **SQL SEEDED** (AABB_PROXIMITY until R20 adds host_id) |
| BIM_Designer §8.3 | Opening face-anchor + swing attributes | — | M16/M17 rules | **SQL SEEDED** |
| BBC.md §2 + DocValidate §15.6 | Schema-Not-Geometry: AABB arithmetic = missing column | — | R21-R24 extraction gaps | **AUDIT DONE** (8/17 rules use AABB fallback) |
| BIM_COBOL §20 | Spatial predicates standardise ERP-maths queries | — | Predicate catalog (13 predicates) | **SPEC ONLY** |

### BIM_Designer — BOM Outliner + YAML v3

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| BIM_Designer §17.19 | BOM Outliner: listOrderLines wire action, recursive tree | — | Wire protocol response | **SPEC ONLY** (G-9 scope) |
| G4_SRS §7 | YAML v3: ProcessIt() pattern, grid pitch formula, typical_spacing_mm | — | V004 params (sprinkler 3500, light 3000) | **SQL SEEDED** |

### Gap Summary

| Status | Count | Meaning |
|---|---|---|
| PASS | 18 | Spec → test → green. Proven. |
| IMPLEMENTED | 3 | Test exists but advisory (not gating). Promote pending. |
| SQL SEEDED | 6 | AD_Val_Rule SQL written, Non-Disturbance analysed, not yet code-tested. |
| SPEC ONLY | 14 | Spec written, test spec defined, code not yet written. G-4/G-9 scope. |
| PENDING | 3 | Spec exists, no test spec yet. Needed before WALK-THRU. |

**Rule:** No code change without checking this matrix first. If the change
touches a PASS row, run the test. If it touches a SPEC ONLY row, write the
test. If it touches a PENDING row, write the spec first.

### Executable Traceability — Code-Level Enforcement

The matrix above is a doc. Docs drift. To make traceability structural:

**Convention — `@Traces` annotation on every test class:**

```java
/**
 * @Traces BBC.md §4.0 — LBD tack convention
 * @Traces BBC.md §4.2 — BUFFER completeness invariant
 * @Witness W-TACK-1, W-BUFFER-1
 */
class BomValidatorTest { ... }
```

**Convention — `// Implementing` citation before code changes:**

```java
// Implementing BBC.md §4.1 — world coord reconstruction (R16 origin convention)
double worldX = buildingOrigin.x + accumulatedDx;
```

**T21 tamper rule — Orphan Test Detection (G4-TAMPER):**

```
Rule: Every test class in src/test/ within sealed modules must have a
      @Traces comment referencing a BBC.md, DocValidate.md, G4_SRS.md,
      or TACK_FIX_SPEC.md section.

Scan: grep -rL '@Traces' {DAGCompiler,BIM_COBOL,...}/src/test/**/*Test.java
      → count files without @Traces → report as orphaned tests

Gate: advisory initially (report orphan count). Promote to FAIL when
      all existing tests have been annotated.

Why:  A test without a spec reference is either:
      (a) testing something undocumented → document it, or
      (b) testing something that no longer exists → delete it.
      Both are drift.
```

**T22 tamper rule — Spec-Code Alignment (G4-TAMPER):**

```
Rule: Every production Java file modified in the last 10 commits must
      have a // Implementing or // Per BBC.md comment if the change
      touches tack offsets, BOM writes, or spatial computation.

Scan: git diff HEAD~10 --name-only -- '*/src/main/**/*.java'
      → for each file: grep for dx|dy|dz|origin|tack|m_bom|C_OrderLine
      → if spatial code changed AND no spec citation → WARN

Gate: advisory (WARN, not FAIL). Developers add citation habit gradually.
```

**Bidirectional lookup:**
- **Spec → Test:** Read the Traceability Matrix above (sorted by spec section)
- **Test → Spec:** Read the `@Traces` annotation in the test file header
- **Orphaned test:** T21 catches tests without `@Traces`
- **Orphaned spec:** Matrix rows with status PENDING or SPEC ONLY

---

## Layer 4 — Data Integrity Guards (against data fraud)

The first 3 layers guard CODE. This layer guards DATA.

**The problem:** `{PREFIX}_BOM.db` is populated by the IFCtoBOM Java pipeline (SH/DX)
or `RosettaStoneToBOM.py` (TE legacy). If the pipeline puts in wrong dimensions,
wrong products, or wrong offsets, the compiler faithfully compiles the wrong
data — and all code-level guards say GREEN.

**The independent oracle:** `component_library.db` is extracted from real IFC
files by IfcOpenShell (an external tool we don't control). It contains:
- `I_Element_Extraction`: every element from the IFC source files
- `M_Product`: product dimensions extracted from IFC geometry

This is the one database we didn't write. It's our ground truth.

**Cross-database checks (implement as test assertions):**

| Check | Query | What It Catches |
|-------|-------|-----------------|
| D-1: Orphan products | `m_bom_line.child_product_id NOT IN m_product.product_id` | BOM references to invented products |
| D-2: Dimension mismatch | `ABS(m_bom_line.allocated_width_mm - m_product.width) > tolerance` | BOM dimensions that don't match extracted geometry |
| D-3: Count match | `COUNT(*) from I_Element_Extraction WHERE building_type=X` must equal expected_elements in manifest | Element counts match what IFC actually contains |
| D-4: Product existence | Every BUY product_id in `{PREFIX}_BOM.db` must exist in component_library.db | No fabricated components |
| D-5: AABB vs extraction envelope | `m_bom.aabb_width_mm` must match `MAX(x)-MIN(x)` from `I_Element_Extraction` | Building envelope not invented |

**Status: DONE (2026-03-11, Phase 4; D-1b added 2026-03-11)** — `DataIntegrityTest.java`, 6/6 PASS.
- D-1: 0 BUY orphans (child_product_id → M_Product FK clean)
- D-1b: 0 orphans across ALL component types (BUY/MAKE/PHANTOM). Catches
  assembly stubs missing from M_Product — the gap that let KITCHEN_CABINET_SET_DX_A/B slip through.
- D-2: Unit consistency (M_Product dims in meters, all < 100m, > 0).
- D-3: SH=55, DX=1099, TE=51088 match extraction ✓
- D-4: 25 IFC class products excluded, 16 known aliases documented, 0 unknown
- D-5: SH + DX AABB within 1mm of extraction envelope ✓

**Why this stops data fraud:**
- Pipeline invents a product → D-1/D-4 catches it (not in component_library)
- Someone changes AABB dimensions → D-5 catches it (doesn't match extraction envelope)
- Script miscounts elements → D-3 catches it (extraction DB has the real count)
- Dimensions are wrong → D-2 catches it (M_Product has the extracted geometry)

The oracle (component_library.db) can't be silently changed because it's
regenerated from IFC files by an external tool. To cheat D-1 through D-5,
you'd have to fake the IFC source files themselves.

---

## Drift Prevention Checklist

Run these checks when adding BOMs, products, or geometry paths.

| Drift Type | Guard | What To Do |
|------------|-------|------------|
| **Orphan product** | D-1b catches ALL component_type orphans | Every MAKE child_product_id needs an M_Product stub (0.001 dims). For SH/DX: `ProductRegistrar` auto-creates products via IFCtoBOM pipeline. For TE: add to `RosettaStoneToBOM.py` products list. |
| **Geometry stagnation** | Mesh cache in DoorWindowLibraryMapper + StairLibraryMapper | Library mesh is cached by geometry_hash. If you add a new mapper, add `Map<String, Mesh> meshCache`. |
| **Transform hash collision** | MeshBinder uses mm-precision integers | Hash format: `LOD_{refHash}_{tx_mm}_{ty_mm}_{tz_mm}_s{sx_mm}_{sy_mm}_{sz_mm}`. Don't use `%.Nf` string formatting for geo hashes — round to `Math.round(val * 1000)`. |
| **Zero-delta transform** | EdgeVertexTest X5a flags sibling bunching | If furniture centroids within 100mm on same storey → BOM line dx/dy not applied. Fix the BOM offsets, not the test. |
| **Magic coordinates** | T12 catches hardcoded coords > 1000 | Use named constants or derive from DB. Reference the standard (IRC/NFPA/IEC) in a comment. |

---

## Anti-Patterns to Prevent

1. **No `assertNotNull` as sole verification** — always compare to expected value
2. **No `|| true` in test scripts** — capture and report exit codes
3. **No `assumeTrue(false)`** — use `@Disabled("TICKET: reason")` instead
4. **No test in `src/main/`** — all tests in `src/test/`
5. **No `catch (Exception ignored) {}`** — log or fail
6. **No hardcoded counts without comment** — always cite source of truth
7. **No digest "not empty" checks** — compare to golden value
8. **No write-then-read-back as sole proof** — cross-check against reference
9. **No silent re-seal** — every `[SEAL]` commit must explain WHY the test
   changed, not just that it changed. Review the diff before accepting.

---

## Tamper Seal — Trust Boundary Hash Manifest

### How It Works (Plain English)

The audit found that tests and production code can drift together silently —
both change, tests still pass, but correctness is lost. The hash lock prevents
this by creating a "wax seal" over all 68 critical files.

**The idea is simple:**
1. We take a fingerprint (SHA256 hash) of every test file and every critical
   production file. If even one byte changes, the fingerprint changes completely.
2. We store all 68 fingerprints in this document (the manifest below).
3. We also compute a single "super-hash" — a fingerprint of all fingerprints
   combined. This is the one number you check.
4. The script `./scripts/verify_test_seal.sh` recomputes everything and compares.

**What it catches:**
- Someone (or Claude) weakens a test assertion → hash changes → SEAL BROKEN
- Production code changes but test wasn't updated → hash changes → SEAL BROKEN
- A test file is deleted or renamed → file missing → SEAL BROKEN
- A new test is added but not sealed → super-hash won't match next time

**What it does NOT catch (the re-seal loophole):**
- Bugs in logic that existed at seal time (the seal locks the current state)
- Changes to files not in the trust boundary (e.g., utility classes, POMs)
- **Cheating re-seal:** someone weakens a test, re-seals, and commits — the
  seal says INTACT but the test is now softer. The hash lock is Layer 1 only.

**Layer 2 — Structural Guards (can't be re-sealed away):**
The hash detects *that* something changed. These guards verify the change
is *honest*. They live in the codebase and run automatically during `mvn test`.
Crucially, they examine **bytecode, reflection, compiled output, and git history** —
not test assertions. Weakening an assertion in one test doesn't affect what
these guards find.

| Guard | What It Enforces | Why It Can't Be Cheated |
|-------|-----------------|------------------------|
| `DriftGuardTest` (ArchUnit) | D1: no silent defaults, D2: no fake geometry, D5: no hardcoded identity, D6: no name-match guards, D8: no direct WorldCoord, D9: SQL isolation | Scans compiled **bytecode** — independent of test data or assertions |
| `ArchitectureTest` (ArchUnit) | A1: contract types are interfaces, A2: BOM concretes not accessed outside approved packages, A3: no flat world-coord fields | Same — **bytecode** scan, can't be fooled by test changes |
| `RosettaStoneGateTest` G1-G3 | Element counts, volume, spatial digest: **reference DB vs output DB** | Queries two **independent databases** and compares — if the compiler produced wrong output, this fails no matter what |
| `RosettaStoneGateTest` G4-TAMPER | Scans **git diff** + **source files** for cheating patterns: `@Disabled` added (T1), no-op assertions (T2), stubs (T8), empty tests (T11), hardcoded coords (T12) | **This is the anti-cheat gate.** Even if you re-seal, G4 scans the git diff of recent commits and catches the weakened assertion. |
| `RosettaStoneGateTest` G5 | Every output element traced to component_library | Queries output.db + component_library.db — **cross-DB join**, independent of test assertions |
| `OrderLineInterfaceContractTest` | C_OrderLine has ONLY the declared columns | **Java reflection** on the actual compiled class |
| EntityType guards in PO classes | `beforeSave()` throws on D record writes | **Runtime enforcement** in production code — can't weaken from test side |

**How G4-TAMPER specifically blocks the re-seal cheat:**
G4 has 16 tamper rules (T1–T16) that scan both git history (last 10 commits)
and current source files. T6 exempts `@Disabled` with `TICKET:` reference
(documented skip vs silent skip). T14 scans active modules only (excludes
backup_phase_DE4). If you weaken a test and re-seal, the weakened code
will trigger one of these rules:
- Changed `assertEquals(55, x)` to `assertTrue(x > 0)` → T2 or T11 may catch
- Added `@Disabled` → T1 catches in git diff AND T6/T7 in source scan
- Inserted a stub `return null` in a validator → T8 catches
- Added hardcoded coordinate > 1000 → T12 catches
- Raw SQL on protected tables (m_bom, m_bom_line, c_order) → T16 catches

To cheat G4 you'd have to also modify G4's tamper rules — but G4 is itself
inside the hash seal (file `RosettaStoneGateTest.java`), so changing it
breaks the seal AND shows in `git diff`.

**G4-TAMPER scope extension (G-4):** Current scan paths cover `DAGCompiler`,
`TopologyMaker`, `ORMSandbox` modules. When G-4 code lands, `BonsaiBIMDesigner`
must be added to the scan scope. The promote path (C_OrderLine → m_bom) writes
to protected tables and needs T16 coverage. New G-4 test classes
(WorkOutputDAO, ConstructionModelSpawner, SaveRecall, Wire) must be added to
the tamper seal. Until then, G-4 code operates outside the trust boundary.

**Layer 3 — Git Diff Review (the human check):**
Every `[SEAL]` commit shows the exact diff of what changed in the test files.
`git log --oneline --all -- docs/TestArchitecture.md` lists every re-seal.
`git diff <old-seal>..<new-seal> -- '*/src/test/**'` shows exactly what was
weakened or strengthened. A cheating re-seal is visible in the diff history.

**The three layers together:**
1. **Hash seal** catches accidental/silent drift (run `verify_test_seal.sh`)
2. **ArchUnit + reflection guards** catch structural cheats (run `mvn test`)
3. **Git diff** catches intentional re-seal cheats (human review of `[SEAL]` commits)

**Daily workflow:**
- Start of session: run `bash scripts/verify_test_seal.sh` — should say INTACT
- After intentional changes: re-seal (see "How to Re-seal" below)
- If BROKEN unexpectedly: run `bash scripts/verify_test_seal.sh --detail` to see which files changed

---

**Sealed:** 2026-03-19 (v14: sessions 17-22, Design Mode + @Traces + traceability matrix, 74 files)
**Super-hash:** `8ef7d9f9ad5ba102cda7ac3b9e1b574b4c6f781f4f8281661828315592e963a3`

Quick verify: `bash scripts/verify_test_seal.sh`

### DAGCompiler Tests (31 files)
```
801ac925  contract/ArchitectureTest.java
4fa82454  contract/RosettaPlacementTest.java
d32f0a2f  library/AnchorComputationTest.java
5dafc8e4  contract/TranslationChainTest.java
233fddba  coordinate/LocalCoordTest.java
cb37cde4  contract/PhantomLayoutTest.java
6d5d35d3  contract/PlacementCollectorVisitorTest.java
7f837e14  contract/BOMWalkerTest.java
d00d791c  library/StallDividerParamsTest.java
49211783  contract/VerbStageTest.java
863473a7  contract/ExtractedGeometryTruthTest.java
7c0986ba  contract/EdgeVertexTest.java
b9d57454  contract/OutputTemplateTest.java
4ba30be3  contract/BOMDigestVerifyTest.java
9709b84b  contract/StructuralCrossCheckTest.java
bd2ed3d0  arch/DriftGuardTest.java
f296b95c  contract/CompilerContractTest.java
b9e828e5  contract/RosettaStoneGateTest.java
8acdaac0  contract/ExtractedBOMWalkTest.java
4414fe64  contract/WalkThruCompilationTest.java
a41306f0  contract/CoEmptySpaceTest.java
0e21e5e5  contract/BomChainIntegrityTest.java
46e2e2f2  contract/BOMChainMathTest.java
75dfd1a5  contract/SpatialPlacementVisitorTest.java
304eb7ea  contract/StTemplatePipelineTest.java
82b87433  contract/BuildingRegistryTest.java
1cedf232  contract/IntraBOMRelativeTest.java
028950d9  contract/MetadataIntegrityTest.java
c7159115  contract/DataIntegrityTest.java
bc39a88e  contract/FurnitureGeometryTest.java
a0287085  contract/StackedDuplexWitnessTest.java
```

### BIM_COBOL Tests (27 files)
```
9f35fe2f  CheckBomVerbTest.java
142bb5c6  CoverWithRoofVerbTest.java
6a1e1293  RouteSprinklersVerbTest.java
07afaa08  RosettaStoneTest.java
0f1130c0  ConnectFittingsVerbTest.java
6c88148d  CheckPlacementClashTest.java
46ff4ef3  CheckRoomComplianceTest.java
26422d9f  WireLightingVerbTest.java
539d485b  VerifyPlacementVerbTest.java
81ca9121  TileSurfaceVerbTest.java
7c9c693c  ArrayVerbTest.java
130ff90c  VerbStageIntegrationTest.java
b3855232  VerbNodePersisterTest.java
ad490cdc  verb/PlaceBomVerbTest.java
4f9b6563  verb/FloorVerbTest.java
58590f5b  verb/ConvenienceVerbTest.java
be7ff2c1  VerbRegistryTest.java
6e3a37c4  verb/ReportVerbTest.java
97a9ba51  F5IntegrationTest.java
ee8d3478  HelloWorldVerbTest.java
ec71dd2f  verb/SyntheticBomPrimitiveTest.java
431eae11  verb/BuildingVerbTest.java
255c02b9  verb/UtilityVerbTest.java
1e6dfc0d  verb/OverrideRoofVerbTest.java
db2b0c62  verb/FixOpeningBboxVerbTest.java
92ee1dab  verb/BuildSpatialStructureVerbTest.java
05af480e  PrimeRuleWitnessTest.java
```

### ORMSandbox + TopologyMaker Tests (6 files)
```
181e34fa  EmptySpaceTest.java
0d2a3c77  PP_Order_NodeTest.java
1a0321a3  BuildingInspectorTest.java
20ead299  OrderLineInterfaceContractTest.java
50f65541  BasePOTest.java
13ad060c  TopologyBatchProcessTest.java
```

### Critical Production Files + Hook (10 files)
```
456917d7  CompilationPipeline.java
fd1cd3d9  BuildingCompiler.java
09e05d7c  PlaceBomVerb.java
a1909001  EnBlocVerb.java
af068cf9  WalkThruVerb.java
ef278ec6  MBOM.java
9e6a380e  MBOMLine.java
8e266f19  run_tests.sh
efc9353c  run_RosettaStones.sh
39839729  pre-commit
```

### Verification

Script: `scripts/verify_test_seal.sh` (source of truth for the file list)

```
bash scripts/verify_test_seal.sh            # quick check: INTACT or BROKEN
bash scripts/verify_test_seal.sh --detail   # also shows which files changed
```

### How to Re-seal After Intentional Changes

```bash
# 1. Make your code changes, run tests, confirm GREEN
# 2. Check what broke:
bash scripts/verify_test_seal.sh --detail
# 3. Get new hash for changed file(s):
sha256sum path/to/ChangedFile.java          # copy first 8 chars
# 4. Update the per-file hash in this document
# 5. Recompute super-hash (the script prints the actual hash — copy it):
bash scripts/verify_test_seal.sh            # grab "Actual: ..." line
# 6. Update super-hash on line 293 of this doc + line 8 of verify_test_seal.sh
# 7. Verify:
bash scripts/verify_test_seal.sh            # should now say INTACT
# 8. Commit:
git add docs/TestArchitecture.md scripts/verify_test_seal.sh
git commit -m "[SEAL] Re-seal after <change description>"
```

---

## Addendum: Industry Precedent — "Who Watches The Watchers?"

The 4-layer defense above is not novel. Every high-integrity project converges
on the same answer: **test data must come from outside the system being tested.**

### Projects That Solve This

**SQLite** — Most relevant to our scale. Single-team project, extreme quality.
- Test:code ratio is ~600:1. For every line of SQLite, 600 lines of test.
- 100% branch coverage (not line — branch).
- Every test asserts an exact expected value. No `assertNotNull`. No "count > 0".
- The proprietary TH3 test harness is worth more than the code itself.
  You can rewrite SQLite from scratch; you cannot recreate TH3.
- **Lesson for us:** Our gate tests must assert exact values (golden digests,
  exact counts), not existence checks. The test data IS the specification.

**NASA / JPL** (Mars rovers, spacecraft)
- Every line of flight code traces to a requirement. No code exists "just because."
- Independent Verification & Validation (IV&V): a separate team at a different
  site writes their own tests from the same requirements. If the two teams'
  tests agree, the code is probably right.
- **Lesson for us:** This is exactly what Layer 4 does. `component_library.db`
  is our IV&V — extracted by IfcOpenShell (external tool we don't control)
  from IFC files (industry standard we didn't write). Cross-checking `{PREFIX}_BOM.db`
  against it is our independent verification.

**Chromium / Google Chrome** — 35M+ lines, thousands of contributors.
- OWNERS files: every directory lists who can approve changes. You cannot merge
  to `//net/` without a net-OWNER approving. Tests have owners too.
- Sheriffs (rotating duty) monitor test dashboards. Flaky tests are reverted
  immediately — not investigated later, reverted NOW.
- "Layout tests" compare pixel-perfect screenshots against golden files.
  Any pixel drift = FAIL.
- **Lesson for us:** Golden digest comparison (C1) is our layout test equivalent.
  The `[SEAL]` commit review is our Sheriff rotation.

**Bitcoin Core** — If a test is wrong, real money is lost.
- "Consensus tests" are sacred. The test vectors ARE the spec.
  Changing one requires public peer review across hundreds of developers.
- Test vectors are published independently (BIPs — Bitcoin Improvement Proposals).
  You cannot silently weaken a consensus test.
- **Lesson for us:** Our element count `55` and digest `496022db` should be
  treated like a Bitcoin block hash. Change it and you need proof.

**Linux Kernel** — Thousands of contributors, subsystem maintainers.
- Every bug fix MUST include a test that would have caught the bug.
- `git bisect` — binary-search finds the exact commit that introduced a
  regression. The commit is either reverted or fixed. No hiding.
- The git history itself is the seal: every commit's SHA includes its parent's
  SHA. You cannot rewrite history without breaking the chain.
- **Lesson for us:** Our git-based Layer 3 (`[SEAL]` commit diffs) follows
  the same principle. The chain of `[SEAL]` commits is our audit trail.

### How They Map To Our Layers

| Challenge | Our Layer | Industry Equivalent |
|-----------|-----------|-------------------|
| Accidental code drift | L1: Hash seal | Chromium CQ (Commit Queue) |
| Intentional test weakening | L2: G4-TAMPER (T1–T15) | Bitcoin consensus test review |
| Data fraud | L3: Pre-commit Gate 4 (cross-DB) | NASA IV&V (independent oracle) |
| Re-seal cheat | L4: Git diff of `[SEAL]` commits | Linux `git bisect` + maintainer review |
| Test as specification | Golden digests (Phase 2) | SQLite TH3 exact-value tests |

### The Convergence Principle

All five projects arrive at the same conclusion:

> **The oracle must be external.** No system can verify itself.

- SQLite's expected values come from the SQL standard
- NASA's V&V comes from a separate team at a separate site
- Bitcoin's test vectors come from public, peer-reviewed BIPs
- Chromium's golden screenshots come from a reference renderer
- Our element counts come from `component_library.db` (IfcOpenShell extraction)

The hash seal, tamper rules, and pre-commit hook are the enforcement mechanism.
The real defense against fraud is the independent oracle — a database we didn't
write, containing truth we cannot fake. To cheat our Layer 4, you would have
to forge the IFC source files maintained by buildingSMART International.

---

## Known Limitation: Rosetta Stone Gates Prove Copying, Not Compilation

The current Rosetta Stone pipeline for EXTRACTED buildings (SH, DX) is:

```
IFC file → IfcOpenShell → extraction positions → IFCtoBOM pipeline → {PREFIX}_BOM.db
                                                      (copies positions)
{PREFIX}_BOM.db → Compiler → output.db → Gate test: output == extraction?  → YES (always)
              (copies positions again)
```

The gate proves the copy pipeline didn't drop rows. It does NOT prove the
compiler can derive geometry from abstract rules. Getting 55-for-55 with
matching digests proves data integrity, not compilation correctness.

**True compilation proof requires the GENERATIVE path (Phase E):**

```
EXTRACTED (current):     BOM stores dx=0.046, dy=0.503  ← position from IFC
GENERATIVE (Phase E):    BOM stores role=AGAINST_WALL, wall=NORTH, offset=50mm
                         Compiler must FIND the wall, COMPUTE the position
                         Gate checks: computed position matches extraction
```

The Rosetta Stone concept is sound — the flaw is that the BOM currently
encodes COORDINATES (the answer) instead of INTENT (the rules). When the
generative path is implemented, the same gate infrastructure will prove
actual compilation correctness.

**Until then:** The extracted Rosetta Stones prove **no data loss** (necessary)
but not **correct computation** (sufficient). Document this honestly.

---

## Appendix: Illegal SQL Patterns — Why BIM COBOL Verbs Exist

The verb-first rule is not bureaucracy. Raw SQL is the mechanism by which
every fraud pattern in this document enters the codebase. Verbs are the
structural defense.

### Illegal SQL Patterns (never write these in Java)

```sql
-- ILLEGAL: Raw INSERT into BOM tables (bypasses EntityType guards)
INSERT INTO m_bom (bom_id, ...) VALUES (?, ...);
INSERT INTO m_bom_line (bom_id, child_product_id, ...) VALUES (?, ...);

-- ILLEGAL: Raw UPDATE of compiled output (bypasses verb audit trail)
UPDATE c_order SET DocStatus = 'CO' WHERE C_Order_ID = ?;
UPDATE m_bom_line SET dx = ? WHERE bom_child_id = ?;

-- ILLEGAL: Raw DELETE of BOM data (bypasses EntityType D guards)
DELETE FROM m_bom_line WHERE bom_id = ?;

-- ILLEGAL: String concatenation in queries (SQL injection)
"SELECT ... WHERE DocSubType='" + docSubType + "'"
```

### Legal Alternatives (use these instead)

```java
// CREATE → use a verb
COMPOSE BOM "MY_ROOM_SET" TYPE SET CATEGORY KT;

// MODIFY → use a verb
PLACE BOM "MY_ROOM_SET" INTO FLOOR "FLOOR_GF" AT SLOT 3;

// DELETE → use a verb
REMOVE LINE 42 FROM BOM "MY_ROOM_SET";

// QUERY → use PreparedStatement
try (PreparedStatement ps = conn.prepareStatement(
        "SELECT ... WHERE DocSubType = ?")) {
    ps.setString(1, docSubType);
}
```

### Why Verbs Can't Cheat

Each BIM COBOL verb:
1. **Validates** inputs before touching the database
2. **Logs** to PP_Order_Node (audit trail — who did what, when)
3. **Respects** EntityType guards (D records are read-only)
4. **Uses** PO classes (beforeSave() hooks enforce invariants)
5. **Is testable** — each verb has a witness test

Raw SQL bypasses all five. That's why TopologyWriter (5 raw SQLs) and
CompilationPipeline (2 raw SQLs) are flagged as H2 — they need verb wrappers.

### Current Violations: **ZERO** (resolved by H2 verb wrappers)

All 8 raw SQL statements are now wrapped in verbs. T16 tamper rule enforces
this structurally — any new raw SQL on m_bom, m_bom_line, c_order, or
wm_empty_storage_line in `{DAGCompiler,TopologyMaker,ORMSandbox}/src/main/**/*.java`
will trigger a G4-TAMPER violation. BIM_COBOL is excluded (authorized verb layer).

---

*Generated from deep QA audit, 2026-03-11.*
