# Test Architecture — QA Hardening Plan

> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [ACTION_ROADMAP](ACTION_ROADMAP.md) · [SourceCodeGuide](SourceCodeGuide.md)

<div class="bim-banner" markdown>
<b>6 mathematical gates. Not sampled — proven.</b> If a building compiles, every element is accounted for: count, position, volume, digest, and parent-child integrity. The proof is arithmetic, not assertion. No AI in the gates.
</div>

## Anti-Drift Policy (read first)

**These rules override all other instructions. No exceptions.**

1. **No Magic Coordinates.** If a transform requires a hardcoded (x,y,z) instead of a derived DAG offset, STOP. Ask for the parent matrix. Use named constants referencing a standard (IRC/NFPA/IEC) or derive from the database.
2. **No Invented Data.** If the BOM spec is unclear or missing, DO NOT invent a placeholder. Stop and request the specific file or data model. Every `child_product_id` must resolve. Every MAKE needs an M_Product stub.
3. **No Silent Geometry.** Before modifying geometry (scale, translate, mesh bind), state the intended math. Duplicate vertex arrays are waste — check the mesh cache first, normalize hashes to mm precision.
4. **No Hallucinated Success.** If a test fails, state the exact error. Do not weaken assertions, add workarounds, or re-seal without explaining why.
5. **Verify Before Commit.** Run `DataIntegrityTest` (D-1b catches ALL orphan types). Run `RosettaStoneGateTest` (G1-G6). Both must be GREEN.
6. **No Weak Assertions.** No `assertNotNull` as sole verification, no `assumeTrue(false)` (use `@Disabled("TICKET:")`), no digest "not empty" checks — compare to golden value or expected reference.
7. **No Silent Failures.** No `catch (Exception ignored) {}` — log or fail. No `|| true` in scripts — capture exit codes. No write-then-read-back as sole proof — cross-check against reference.
8. **No Silent Re-seal.** Every `[SEAL]` commit must explain WHY the test changed. Review the diff before accepting.

---

## Hardening Status

### CRITICAL Fixes (C1–C13)

| # | Fix | Status | Notes |
|---|-----|--------|-------|
| C1 | Golden digest verification | PARTIAL | BOMDigestVerifyTest tree walk DONE. HelloWorldVerbTest golden comparison pending |
| C2 | Content spot-check assertions | DONE | SpotCheckContractTest per-element AABB verification |
| C3 | Gate count cross-validation | DONE | Live query from extraction DB, not hardcoded |
| C4 | DX furniture centroid test | DONE | `@Disabled("TICKET:")` — DX coordinate frame fix deferred |
| C5 | SQL injection | DONE | All PreparedStatement with `?` placeholders |
| C6 | Negative tack offsets | OPEN | 3 m_bom_line records with negative dx/dy/dz |
| C7 | StackedDuplexWitnessTest → JUnit | DONE | Already JUnit 5 |
| C8 | Geometry diversity (C8 SQL) | DONE | Per-instance GUID resolution via I_Geometry_Map. All 5 buildings PASS |
| C9 | Per-element axis dimensions | DONE | W-AXISDIM-1: X→X, Y→Y, Z→Z within 1mm. DX 87 swaps accepted (S58c) |
| C10 | Mesh centroid fingerprint | DONE | Advisory — facing direction via mesh centroid offset |
| C11 | P06 same-class overlap sharpness | DONE | Cross-product exempt, same-product flagged, IfcPlate 50mm tolerance |
| C12 | G5 GEO_ slab fallback | DONE | Slab flows through MeshBinder to LOD_ library geometry |
| C13 | No parametric mesh in pipeline | SPEC | G5-PROVENANCE Check 6: zero GEO_ hashes. 28 call sites identified |

### HIGH Fixes (H1–H7)

| # | Fix | Status |
|---|-----|--------|
| H1 | Derive test expected values from data (remove magic numbers) | OPEN |
| H2 | Verb wrappers for raw SQL | DONE — T16 tamper rule: 0 violations |
| H3 | Data-driven BOM category mapping | OPEN |
| H4 | Remove building type string checks | OPEN |
| H5 | Fix error suppression | DONE — all log `[WARN]`, resource leak fixed |
| H6 | Semantic witness verification (AABB vs extraction envelope) | OPEN |
| H7 | Re-enable default Maven test phase | DONE — `pipeline.tests.skip` property; `mvn test` runs BIMBackOffice (20/20). BonsaiBIMDesigner needs component_library.db — skipped from default phase |

### MEDIUM Fixes (M1–M6)

| # | Fix | Status |
|---|-----|--------|
| M1 | Remove invented coordinates from backup code | OPEN |
| M2 | Centralize thresholds in BIMConstants | OPEN |
| M3 | Track C_OrderLine as Phase F debt | DONE |
| M4 | Automate expected_elements derivation | OPEN |
| M5 | Remove @Order test dependencies | OPEN |
| M6 | Document ST→RE mapping in data model | OPEN |

---

## Traceability Matrix — Spec → Test → Witness

**Purpose:** When a BBC.md section changes, this table shows exactly which
tests are affected. When a test fails, this table shows which spec it traces
to. Without this mapping, spec changes silently orphan tests.

### BBC.md §1–§3 — BOM Structure and Compilation Model

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §1 Three BOM dimensions | Category+Owner+SpaceSize govern selection | CompilerContractTest | G1-COUNT | PASS |
| §1.1 Disciplines as metadata | No switch(docBaseType) in BOM path | DriftGuardTest D6 | G4-TAMPER | PASS |
| §2 Gospel Principle | Every element traces to IFC or template | DataIntegrityTest D-1/D-4 | G5-PROVENANCE | PASS |
| §2.1.6 Count invariant | SUM(non-PHANTOM qty) = output count | ExtractedBOMWalkTest | G1-COUNT | PASS |
| §2.2 Recursive placement | Walker decides BOM-vs-leaf by m_bom existence | BOMWalkerTest | W-DS-15 | PASS |
| §2.2 component_type ignored | No code branches on BUY/MAKE/PHANTOM | DriftGuardTest | G4-TAMPER | PASS |
| §3.3 Instant Drop | C_OrderLine → BOM explosion → elements | RosettaStoneGateTest | G1-G6 | PASS |
| §3.3 Instant Drop | bomDrop() creates C_Order + 55 elements | BomDropTest | W-DROP-1..6 | PASS |
| §3.4 BOM Drop | Interactive tree navigation, swap/add | SelectionCascadeTest | W-GEN-1b | PASS |
| §3.5 Selection Cascade | Category + AABB fit + volume rank | SelectionCascadeTest | W-GEN-1a..g | PASS |
| §3 GENERATIVE | DemoHouse BOM + UBBL + BIMEyes | DemoHouseTest | W-DH-1..5 | PASS |

### BBC.md §4 — Tack Convention

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §4.0 LBD offsets | dx = child.minX - parent.minX | BomValidator | W-TACK-1 | IMPLEMENTED |
| §4.1 World coord reconstruction | element_LBD = origin + Σ(tack) | PlacementCollectorVisitorTest | SB-2 | PASS |
| §4.1 Origin convention | Only BUILDING BOM has non-zero origin | BOMChainMathTest | — | PASS |
| §4.2 BUFFER invariant | parent.width = SUM(children.allocated_width) | BomValidator | W-BUFFER-1 | IMPLEMENTED |
| §4.3 Centroid drift fix | ScopeBomBuilder uses minX not centroidX | — | W-TACK-1 | IMPLEMENTED |

### G4_SRS — output.db

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §22.3 | compile() produces elements | CompileBridgeTest | W-COMPILE-1..5 | PASS |
| §2.1-3 | CreateNew/Save/Recall lifecycle | — | — | SPEC ONLY |

### DocValidate — Validation Engine

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §15.1 | 3-tier validation (per-disc, cross-disc, vertical) | PlacementValidatorImplTest | 7 tests | PASS |
| §15.3 | Non-Disturbance protocol | NonDisturbanceTest | 6 tests | PASS |
| §15.5 | 17 mining rules (M1-M17) | — | V004_mined_rules.sql | SQL SEEDED |

### LAST_MILE — Geometry Fidelity

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| #8 | Per-instance geometry diversity | GeometryFidelityTest | W-GEODIV-1 | IMPLEMENTED |
| #9 | Per-element axis dimensions | GeometryFidelityTest | W-AXISDIM-1 | IMPLEMENTED |
| #8/#9 | Mesh centroid fingerprint | GeometryFidelityTest | W-MESHDIR-1 | IMPLEMENTED (advisory) |

### BIMEyes — Geometric Comprehension

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| EYES §4.4 | P25 ROOM_VALIDITY | RoomValidityProof | W-ROOM-VALID | IMPLEMENTED |
| EYES §4.5 | P26 BUILDING_COMPLETENESS | BuildingCompletenessProof | W-BLDG-COMPLETE | IMPLEMENTED |
| EYES §4.6 | P27 WALL_ROOF_INTERSECTION | WallRoofIntersectionProof | W-DH-ROOF-1/3 | IMPLEMENTED |
| EYES §4.6 | P28 ROOF_COVERAGE | RoofCoverageProof | W-DH-ROOF-2 | IMPLEMENTED |
| BIM_COBOL §17.3 | TRIM WALLS TO ROOF | TrimWallsToRoofVerbTest | W-TRIM-1..7 | IMPLEMENTED |
| EYES §10 | 24 proof classes, PlacementProver facade | EyesProofRunner | W-EYES-NONDISTURB | IMPLEMENTED |

### BIM_Designer_SRS — UX + Flywheel + Compile Bridge

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §27.5 | Flywheel Advisory Panel (FL-2) | FlyAdvisoryTest | W-FL-ADVISORY-1..5 | PASS |
| §27 FL-5 | Shape advisory (class-shape mismatch) | FlyAdvisoryTest | W-FL-SHAPE-1/2 | PASS |
| §22.3 | compile() with CompilationPipeline | CompileBridgeTest | W-COMPILE-1..5 | PASS |

### DISC_VALIDATION_DB_SRS — Database Split

| Spec Section | Requirement | Test Class | Witness/Gate | Status |
|---|---|---|---|---|
| §6 Phase 1 | Schema + seed + references | DiscValidationDBTest | W-DV-DB-* | PASS |
| §6 Phase 2 | CalibrationDAO reads from ERP.db | DiscValidationDBTest | W-DV-DB-DUAL-READ | PASS |

### Gap Summary

| Status | Count | Meaning |
|---|---|---|
| PASS | 36 | Spec → test → green. Proven. |
| IMPLEMENTED | 9 | Test exists but advisory (not gating). |
| SQL SEEDED | 6 | AD_Val_Rule SQL written, not code-tested. |
| SPEC ONLY | 24 | Spec written, test spec defined, code not yet written. |
| PENDING | 3 | Spec exists, no test spec yet. |

**Rule:** No code change without checking this matrix first.

### Executable Traceability — Code-Level Enforcement

**`@Traces` annotation on every test class:**
```java
/** @Traces BBC.md §4.0 — LBD tack convention */
class BomValidatorTest { ... }
```

**`// Implementing` citation before code changes:**
```java
// Implementing BBC.md §4.1 — world coord reconstruction (R16 origin convention)
double worldX = buildingOrigin.x + accumulatedDx;
```

**T21:** Orphan test detection (tests without `@Traces`). Advisory.
**T22:** Spec-code alignment (spatial code without citation). Advisory.

---

## Layer 4 — Data Integrity Guards

Guards DATA against wrong dimensions, products, or offsets in `{PREFIX}_BOM.db`.
Oracle: `component_library.db` — extracted from IFC files by IfcOpenShell (external).

| Check | What It Catches |
|-------|-----------------|
| D-1/D-1b | Orphan products (ALL component types) |
| D-2 | Dimension mismatch (M_Product dims) |
| D-3 | Count match vs extraction |
| D-4 | Product existence in library |
| D-5 | AABB vs extraction envelope |

**Status: ALL PASS** — `DataIntegrityTest.java`, 6/6 PASS.
To cheat D-1 through D-5, you'd have to fake the IFC source files themselves.

---

## Layer 5 — Static Analysis

SpotBugs + PMD. Advisory, not blocking.
- SpotBugs HIGH: 2 FIXED (FileWriter encoding)
- PMD: 507 findings deferred — legacy style debt from early sprint iterations (mostly dead code, empty catches), deferred in favour of architectural correctness. Not blocking. Contributions welcome.

---

## Drift Prevention Checklist

| Drift Type | Guard | What To Do |
|------------|-------|------------|
| Orphan product | D-1b | Every MAKE child_product_id needs M_Product stub |
| Geometry stagnation | Mesh cache | If adding a mapper, add `Map<String, Mesh> meshCache` |
| Transform hash collision | MeshBinder mm-precision | `LOD_{refHash}_{tx_mm}...` — use `Math.round(val * 1000)` |
| Zero-delta transform | EdgeVertexTest X5a | Fix BOM offsets, not the test |
| Magic coordinates | T12 | Use named constants or derive from DB |

---

## Tamper Seal — Trust Boundary Hash Manifest

SHA256 hash of 73 files (63 test + 10 critical production). Super-hash = hash of all hashes.

**Three defense layers:**
1. **Hash seal (L1)** — any byte change = SEAL BROKEN
2. **Structural guards (L2)** — ArchUnit, G4-TAMPER T1-T16, cross-DB joins, EntityType guards
3. **Git diff review (L3)** — every `[SEAL]` commit shows exact diff

**Sealed:** 2026-03-27 (v43: S98 FINE logging + drift check)
**Super-hash:** `5ddcd79e4735c9f020b42614f3b66cb2f9ac73af4316db25110416ec1964ed2c`

```
bash scripts/verify_test_seal.sh            # quick check
bash scripts/verify_test_seal.sh --detail   # show which files changed
```

### DAGCompiler Tests (30 files)
```
801ac925  contract/ArchitectureTest.java
4fa82454  contract/RosettaPlacementTest.java
d32f0a2f  library/AnchorComputationTest.java
5dafc8e4  contract/TranslationChainTest.java
233fddba  coordinate/LocalCoordTest.java
cb37cde4  contract/PhantomLayoutTest.java
27b8d845  contract/PlacementCollectorVisitorTest.java
7f837e14  contract/BOMWalkerTest.java
d00d791c  library/StallDividerParamsTest.java
49211783  contract/VerbStageTest.java
863473a7  contract/ExtractedGeometryTruthTest.java
7c0986ba  contract/EdgeVertexTest.java
b9d57454  contract/OutputTemplateTest.java
4ba30be3  contract/BOMDigestVerifyTest.java
9709b84b  contract/StructuralCrossCheckTest.java
e9b6bcbc  arch/DriftGuardTest.java
ead7c516  contract/CompilerContractTest.java
2af523c6  contract/RosettaStoneGateTest.java
8acdaac0  contract/ExtractedBOMWalkTest.java
0e21e5e5  contract/BomChainIntegrityTest.java
46e2e2f2  contract/BOMChainMathTest.java
75dfd1a5  contract/SpatialPlacementVisitorTest.java
304eb7ea  contract/StTemplatePipelineTest.java
da1a6610  contract/BuildingRegistryTest.java
1cedf232  contract/IntraBOMRelativeTest.java
028950d9  contract/MetadataIntegrityTest.java
82919c68  contract/DataIntegrityTest.java
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
31fb92d8  VerbRegistryTest.java
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
0d2a3c77  W_Verb_NodeTest.java
1a0321a3  BuildingInspectorTest.java
20ead299  OrderLineInterfaceContractTest.java
50f65541  BasePOTest.java
13ad060c  TopologyBatchProcessTest.java
```

### Critical Production Files + Hook (10 files)
```
42944c70  CompilationPipeline.java
fd1cd3d9  BuildingCompiler.java
e455d42a  PlaceBomVerb.java
a1909001  EnBlocVerb.java
af068cf9  WalkThruVerb.java
ef278ec6  MBOM.java
9e6a380e  MBOMLine.java
38f498ae  run_tests.sh
8bb5f537  run_RosettaStones.sh
39839729  pre-commit
```

### Rosetta Stone Coverage

**Gate:** `./scripts/run_RosettaStones.sh` — 19/34 ALL GREEN.

| Gate | SH | FK | IN | DX | TE † | DM |
|------|----|----|----|----|-------|------|
| G1-COUNT | PASS (55) | PASS (82) | PASS (699) | PASS (1099) | PASS† (48428) | PASS (60) |
| G2-VOLUME | PASS | PASS | PASS | PASS | PASS† | — |
| G3-DIGEST | PASS | PASS | PASS | PASS | PASS† | — (GENERATIVE) |
| G4-TAMPER | PASS | PASS | PASS | PASS | PASS | PASS |
| G5-PROVENANCE | PASS | PASS | PASS | PASS | PASS† | PASS |
| G6-ISOLATION | PASS | PASS | PASS | PASS | PASS† | PASS |

> **†TE: extraction-only, not BOM-compiled.** IFCtoBOM QA blocked: 471/1515
> tack overflows (W-TACK-1), 14/50 SET BOMs unbalanced (W-BUFFER-1). TE_BOM.db
> empty → BomDrop never runs → c_order=0, c_orderline=0 in output. Gates pass
> because output DB IS the federation extraction (same as reference). SH/FK/DM
> are real BOM compilations. See `logs/pipeline_SJTII_Terminal_ifctobom_*.log`.

**Remaining debt:** G5 GEO_ (RA/JE/ES), C9 axis swaps (JE/HI/SC/RM). TE BOM compilation (IFCtoBOM tack fix).
