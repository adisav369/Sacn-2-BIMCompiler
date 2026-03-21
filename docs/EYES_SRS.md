# BIMEyes SRS — Geometric Comprehension Engine
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 1.0 (2026-03-21, session 49)
**Depends on:** [LAST_MILE_PROBLEM](LAST_MILE_PROBLEM.md) §Geometric Fingerprint, [DISC_VALIDATE_SRS](DISC_VALIDATE_SRS.md) §DV010, [TestArchitecture](TestArchitecture.md) §Traceability Matrix
**Pre-requisite:** CP-4 (ProductCategory.java, GeometricFingerprint.java — both DONE session 48)

---

## 1. Scope

The geometric intelligence that proves building correctness is currently scattered
across 8 classes in 3 modules (DAGCompiler, IFCtoBOM, BonsaiBIMDesigner). Session 48
proved that pure mathematics can verify buildings: 97% of 90,310 elements across 32
buildings pass both shape AND position proof, with 22 buildings at 100%.

BIMEyes unifies this scattered intelligence into a single reusable module that
provides **geometric comprehension** — not just comparison to a reference, but the
ability to tell you what shapes ARE and whether spatial relationships are valid.

**Delivers:**
1. A standalone Maven module (`BIMEyes`) with zero runtime dependencies beyond orm-core + sqlite-jdbc
2. Canonical types for shape classification (archetype, scale band, fingerprint)
3. A tiered proof system (26 proofs in 3 tiers) with structured results
4. Cross-mode comparison (multiset and BOM-traced)
5. Spatial diff with tolerance bands
6. Two new aggregate proofs: P25 ROOM_VALIDITY, P26 BUILDING_COMPLETENESS

**Out of scope:**
- Parametric mesh generation (BBC.md §2.2.1 — library LODs only)
- Visual rendering or viewport integration
- IFC parsing (remains in IFCtoBOM)
- Mesh binding (remains in DAGCompiler MeshBinder)

---

## 2. Architecture

### 2.1 Current State (Pre-Eyes)

```
IFCtoBOM/                          DAGCompiler/                         BonsaiBIMDesigner/
├ DimensionRangeValidator          ├ validation/                        ├ PlacementValidatorImpl
│   (P24 mined dim check)         │   ├ GeometricFingerprint            │   .validate()     → Tier 1
│                                 │   │   (ShapeArchetype, ScaleBand,   │   .checkDimRange() → P24
├ BomValidator                    │   │    Fingerprint, Multiset,       │
│   .checkShapeConsistency()      │   │    BomTraced comparisons)       │
│                                 │   ├ PlacementProver                 │
│                                 │   │   (P01-P23, prove/proveFromDB)  │
│                                 │   ├ SpatialDiff                     │
│                                 │   │   (per-element coordinate diff) │
│                                 │   ├ GeometryIntegrityChecker        │
│                                 │   │   (vertex bounds, topology)     │
│                                 │   └ ProductCategory                 │
│                                 │       (IFC class → domain category) │
```

**Problem:** 8 classes × 3 modules = no single import for "geometric understanding."
Every consumer re-invents the wiring. Thresholds are duplicated. Types are inner
classes buried in 845-line files.

### 2.2 Target State (BIMEyes Module)

```
BIMEyes/
  pom.xml                            ← depends: orm-core, sqlite-jdbc
  src/main/java/com/bim/eyes/
  │
  ├── ProductCategory.java           ← moved from DAGCompiler (Phase 1)
  ├── EyesConstants.java             ← all thresholds in one place
  │
  ├── shape/
  │   ├── ShapeArchetype.java        ← enum: PLANAR, ELONGATED, COMPACT, MIXED
  │   ├── ScaleBand.java             ← enum: ARCHITECTURAL, FURNITURE, FITTING, TINY
  │   ├── Fingerprint.java           ← record: dimensionless ratios + centroid
  │   ├── FingerprintComputer.java   ← computeFromExtracted(), computeFromOutput()
  │   └── ShapeClassifier.java       ← classifyArchetype(), classifyScaleBand(),
  │                                     isHostedOpening(), verifyClassConsistency()
  │
  ├── proof/
  │   ├── ProofResult.java           ← record: proofId, Status, element, evidence, value
  │   ├── ProofReport.java           ← aggregate: results[], proven, violated, skipped
  │   ├── EyesProofRunner.java       ← orchestrator: prove(source, tiers)
  │   ├── tier1/                     ← P01-P04: per-element arithmetic
  │   │   ├── PositiveExtentProof.java
  │   │   ├── FiniteCoordsProof.java
  │   │   ├── MinDimensionProof.java
  │   │   └── StoreyZBandProof.java
  │   ├── tier2/                     ← P05-P09, P15, P20-P23: pairwise/relational
  │   │   ├── DuplicatePositionProof.java
  │   │   ├── SameClassOverlapProof.java
  │   │   ├── OpeningContainmentProof.java
  │   │   ├── FurnitureInRoomProof.java
  │   │   ├── FixtureOnSurfaceProof.java
  │   │   ├── PipeInHostProof.java
  │   │   ├── WallOrientationProof.java
  │   │   ├── ElementInRoomProof.java
  │   │   ├── OpeningMeshInBboxProof.java
  │   │   └── DrainCornerAlignmentProof.java
  │   └── tier3/                     ← P10-P14, P16-P19, P25(NEW), P26(NEW): aggregate
  │       ├── ShapeIdentityProof.java
  │       ├── PerimeterClosureProof.java
  │       ├── WallCoverageProof.java
  │       ├── RoomHasDoorProof.java
  │       ├── PerimeterLengthProof.java
  │       ├── FloorAreaProof.java
  │       ├── WasteGradientProof.java
  │       ├── SystemConnectedProof.java
  │       ├── VentAboveRoofProof.java
  │       ├── LodGeometryProof.java
  │       ├── RoomValidityProof.java         ← P25 (NEW)
  │       └── BuildingCompletenessProof.java ← P26 (NEW)
  │
  ├── compare/
  │   ├── MultisetComparator.java    ← proveMultisetEquivalence()
  │   └── BomTracedComparator.java   ← proveBomTraced()
  │
  └── diff/
      └── SpatialDiff.java           ← moved from DAGCompiler (Phase 1)
```

### 2.3 Dependency Graph

```
                ┌──────────────┐
                │   BIMEyes    │  ← depends: orm-core, sqlite-jdbc
                └──────┬───────┘
                       │
          ┌────────────┼────────────┐
          │            │            │
   ┌──────▼──────┐ ┌──▼──────┐ ┌──▼──────────────┐
   │ IFCtoBOM    │ │DAGComp  │ │BonsaiBIMDesigner│
   │             │ │         │ │                 │
   │ DimRange    │ │ Builder │ │ PlacementValid  │
   │ BomValid    │ │ Prover* │ │ .validate()     │
   └─────────────┘ └─────────┘ └─────────────────┘

   * PlacementProver becomes thin facade delegating to EyesProofRunner
```

---

## 3. Type Specifications

### 3.1 ShapeArchetype (enum)

Geometric invariant classification from AABB dimensionless ratios.
Source: GeometricFingerprint.java lines 57-66.

| Archetype | Condition | Examples |
|-----------|-----------|---------|
| PLANAR | planarity < 0.15 AND elongation >= 0.40 | wall, slab, plate, roof, door, window |
| ELONGATED | planarity < 0.15 AND elongation < 0.40 | column, beam, member, pipe, mullion |
| COMPACT | planarity >= 0.25 AND elongation >= 0.50 | furniture, equipment, compact fittings |
| MIXED | none of above | transitional shapes |

### 3.2 ScaleBand (enum)

Absolute size classification from AABB volume.

| Band | Volume Range (m³) | Examples |
|------|-------------------|---------|
| ARCHITECTURAL | > 1.0 | rooms, walls, slabs, roofs |
| FURNITURE | 0.01 – 1.0 | furniture, large fittings |
| FITTING | 0.0001 – 0.01 | small fittings, hardware |
| TINY | < 0.0001 | fasteners, tiny parts |

### 3.3 Fingerprint (record)

```java
public record Fingerprint(
    String guid, String ifcClass, String elementName,
    double smallMM, double mediumMM, double largeMM,       // sorted AABB dims
    double planarity, double elongation, double squareness, // dimensionless ratios
    double volumeM3, double topologyRatio,                  // absolute + mesh
    ShapeArchetype archetype, ScaleBand scaleBand,          // classifications
    double cx, double cy, double cz                         // world-space centroid (m)
) {}
```

**Dimensionless ratios** (given AABB dimensions sorted S ≤ M ≤ L):
- `planarity  = S / L` — how thin (0 = infinitely thin, 1 = cube)
- `elongation = M / L` — how stretched (0 = needle, 1 = square cross-section)
- `squareness = S / M` — cross-section shape (0 = ribbon, 1 = square)

**Cross-mode invariance theorem:** Dimensionless ratios are identical for extracted
IFC mesh and compiled library mesh even when absolute dimensions differ by the
inner-surface gradient (10-90mm). Same shape = same ratios. This is ratio invariance
under uniform scaling — a theorem, not an approximation.

### 3.4 ProductCategory (moved)

Static IFC class → domain category resolution. 10 categories, 37 IFC class mappings.
Authority: `component_types.product_category` in component_library.db (CP4_002 migration).

| Category | IFC Classes |
|----------|------------|
| STRUCTURAL_LINEAR | IfcBeam, IfcColumn, IfcMember, IfcReinforcingBar, IfcDiscreteAccessory, IfcElementAssembly |
| STRUCTURAL_PLANAR | IfcSlab, IfcWall, IfcWallStandardCase, IfcFooting, IfcPile, IfcPlate |
| MEP_ROUTING | IfcPipeSegment, IfcPipeFitting, IfcDuctSegment, IfcDuctFitting, IfcFlowSegment, IfcFlowFitting, IfcFlowController |
| MEP_TERMINAL | IfcFireSuppressionTerminal, IfcLightFixture, IfcAirTerminal, IfcAlarm, IfcSanitaryTerminal, IfcFlowTerminal, IfcSensor, IfcValve, IfcElectricAppliance, IfcController, IfcOutlet, IfcSwitchingDevice, IfcFan |
| OPENING | IfcDoor, IfcWindow, IfcOpeningElement |
| FURNISHING | IfcFurnishingElement, IfcFurniture, IfcBuildingElementProxy |
| ENVELOPE | IfcRoof, IfcCovering, IfcChimney |
| CIRCULATION | IfcStairFlight, IfcRailing, IfcRampFlight |
| SITE | IfcEarthworksFill, IfcGeographicElement |
| INFRASTRUCTURE | IfcRail, IfcTrackElement, IfcCourse, IfcSurfaceFeature, IfcSign |

### 3.5 EyesConstants

Single source of truth for all geometric thresholds. Currently scattered across
GeometricFingerprint.java, PlacementProver.java, SpatialDiff.java, GeometryIntegrityChecker.java.

| Constant | Value | Source | Usage |
|----------|-------|--------|-------|
| PLANARITY_THRESHOLD | 0.15 | GeometricFingerprint | Archetype boundary PLANAR/ELONGATED |
| ELONGATION_THRESHOLD | 0.40 | GeometricFingerprint | Archetype boundary PLANAR/ELONGATED |
| COMPACT_PLANARITY | 0.25 | GeometricFingerprint | Archetype boundary COMPACT |
| COMPACT_ELONGATION | 0.50 | GeometricFingerprint | Archetype boundary COMPACT |
| POSITION_TOLERANCE_M | 0.050 | GeometricFingerprint | Centroid match (50mm) |
| CENTROID_GRID_M | 0.1 | GeometricFingerprint | Quantization grid for sorting |
| COORD_MIN | -100.0 | PlacementProver | Sanity range (m) |
| COORD_MAX | 1000.0 | PlacementProver | Sanity range (m) |
| MIN_DIMENSION_M | 0.001 | PlacementProver | Non-degenerate (1mm) |
| CENTROID_TOLERANCE_M | 0.001 | PlacementProver | Duplicate detection (1mm) |
| OVERLAP_VOLUME_M3 | 1e-5 | PlacementProver | Same-class overlap (10mm cube) |
| CONTAINMENT_TOLERANCE_M | 0.050 | PlacementProver/BIMConstants | Opening in host wall |
| COVERAGE_TOLERANCE_M | 0.010 | PlacementProver/BIMConstants | Wall face coverage |
| BOUNDS_TOLERANCE_M | 0.001 | GeometryIntegrityChecker | Vertex-to-bbox (1mm) |
| SPATIAL_EXACT_MM | 1.0 | SpatialDiff | EXACT band |
| SPATIAL_DRIFT_MM | 50.0 | SpatialDiff | DRIFT band |
| DIM_RANGE_RATIO | 5.0 | DimensionRangeValidator | P24 outlier threshold |

---

## 4. Proof Catalog

All proofs follow the same contract: `(PlacementData | Connection) → ProofResult`.
Each proof is a pure function — deterministic, no side effects.

### 4.1 Tier 1 — Per-Element Arithmetic (4 proofs)

Applied to every element individually. O(n), no inter-element state.

| Proof ID | Name | What it proves | Critical? |
|----------|------|----------------|-----------|
| P01 | POSITIVE_EXTENT | Every axis has positive extent (maxX > minX) | Yes |
| P02 | FINITE_COORDS | No NaN/Inf, coords in [-100, 1000] m | Yes |
| P03 | MIN_DIMENSION | Smallest axis > 1mm (not degenerate) | Yes |
| P04 | STOREY_Z_BAND | Element Z falls within storey band ±500mm | Yes |

### 4.2 Tier 2 — Pairwise/Relational (10 proofs)

Require pairs of elements or element-to-context relationships.

| Proof ID | Name | What it proves | Critical? |
|----------|------|----------------|-----------|
| P05 | NO_DUPLICATE_POSITION | No two elements at same centroid (1mm) | Yes |
| P06 | NO_SAME_CLASS_OVERLAP | Same-class AABBs do not intersect >10mm³ | Yes |
| P07 | OPENING_CONTAINED | Every door/window AABB within a host wall | No |
| P08 | FURNITURE_IN_ROOM | Every furniture element within a room AABB | No |
| P09 | FIXTURE_ON_SURFACE | Fixtures (lights, outlets) touch a wall/ceiling | No |
| P15 | PIPE_IN_HOST | Pipe segments within their host floor/wall | No |
| P20 | WALL_ORIENTATION | Walls aligned to cardinal axes (±5°) | No |
| P21 | ELEMENT_IN_ROOM | Non-structural elements contained within rooms | No |
| P22 | OPENING_MESH_IN_BBOX | Opening mesh vertices within AABB (vertex-level) | Yes |
| P23 | DRAIN_CORNER_ALIGNMENT | Drain pipe segments share corner points | No |

### 4.3 Tier 3 — Aggregate/Conservation (12 proofs)

Require global state: all walls on a floor, all rooms in a building, etc.

| Proof ID | Name | What it proves | Critical? |
|----------|------|----------------|-----------|
| P10 | SHAPE_IDENTITY | Fingerprint consistent with claimed IFC class | No |
| P10b | PERIMETER_CLOSURE | Wall faces form closed perimeter per floor | No |
| P11 | WALL_COVERAGE | Wall faces cover room boundaries (no gaps) | No |
| P12 | ROOM_HAS_DOOR | Every room has at least one door | No |
| P13 | PERIMETER_LENGTH | Computed perimeter matches expected ±10% | No |
| P14 | FLOOR_AREA | Sum of room slab areas matches floor footprint ±10% | No |
| P16 | WASTE_GRADIENT | Waste pipes slope downward | Yes |
| P17 | SYSTEM_CONNECTED | MEP system forms connected graph | Yes |
| P18 | VENT_ABOVE_ROOF | Vent pipes terminate above roof level | No |
| P19 | LOD_GEOMETRY | Output geometry is library LOD, not fallback box | No |
| **P25** | **ROOM_VALIDITY** | **Room has walls + floor + ceiling + door** | **No** |
| **P26** | **BUILDING_COMPLETENESS** | **Building has rooms, roof, external door, circulation** | **No** |

### 4.4 New Proof: P25 ROOM_VALIDITY

Derived from analysis of 34 onboarded IFC buildings. A valid room requires:
1. At least 2 walls (STRUCTURAL_PLANAR with PLANAR archetype, height > width)
2. A floor slab (STRUCTURAL_PLANAR at room base Z ±500mm)
3. A ceiling or slab above (at room top Z ±500mm)
4. At least 1 door (OPENING category, PLANAR archetype)

```java
// Implementing BBC.md §2.2.1 — Witness: W-ROOM-VALID
@Test void P25_roomValidity_allRoomsValid() {
    // Given: compiled output DB with elements and rooms
    // When:  EyesProofRunner.prove(dbPath, Set.of(ProofTier.TIER3))
    // Then:  all P25 results are PROVEN or SKIPPED (no VIOLATED)
    //        each room with IfcSpace in elements_meta has
    //        walls.count >= 2 AND floor.present AND ceiling.present AND door.count >= 1
}
```

**Detection algorithm:**
1. Query IfcSpace elements from elements_meta → room AABBs
2. For each room AABB, find elements whose centroids are within the room
3. Classify via ProductCategory + ShapeArchetype
4. Check presence: walls (≥2), floor (≥1), ceiling (≥1), door (≥1)
5. Missing component → VIOLATED with diagnostic listing the absent elements

### 4.5 New Proof: P26 BUILDING_COMPLETENESS

Aggregate proof across the entire building. A complete building requires:
1. At least 1 room (P25 prerequisite)
2. A roof element (ENVELOPE category)
3. At least 1 external door (OPENING at building perimeter)
4. Circulation elements if multi-storey (CIRCULATION category when storeys > 1)

```java
// Implementing BBC.md §2.2.1 — Witness: W-BLDG-COMPLETE
@Test void P26_buildingCompleteness_allComponentsPresent() {
    // Given: compiled output DB
    // When:  EyesProofRunner.prove(dbPath, Set.of(ProofTier.TIER3))
    // Then:  P26 PROVEN: rooms exist, roof exists, external door exists,
    //        if multi-storey then stair/ramp exists
}
```

**Detection algorithm:**
1. Count distinct storeys from elements_meta
2. Check ENVELOPE elements exist (roof)
3. Check OPENING elements at building perimeter (door whose AABB touches building AABB face)
4. If storeys > 1: check CIRCULATION elements exist (stair flights or ramps)
5. Missing component → VIOLATED

---

## 5. Comparison Subsystem

### 5.1 MultisetComparator

Pairing-free Rosetta Stone proof: two element sets contain the same shapes at the
same positions.

**Algorithm:**
1. Quantize centroids to 100mm grid (absorbs minor AABB drift)
2. Sort by (cx_q, cy_q, cz_q, planarity, elongation, squareness) — canonical form
3. Compare element i to element i: shape within epsilon AND centroid within 50mm
4. Falls back to shape-only if centroids unavailable (NaN)

**Result record:**
```java
public record MultisetResult(
    int extractedCount, int compiledCount, int matched,
    List<String> mismatches, boolean spatialProof
) {
    boolean isEquivalent();  // counts match AND no mismatches
    double matchRate();      // matched / min(ext, cmp) × 100
}
```

### 5.2 BomTracedComparator

Element-level comparison via BOM identity — matches compiled elements to extracted
elements by element_name, then compares shape AND position.

**Pairing key:** `element_name` (compiled) → `element_name` stripped of IFC entity
suffix `:DIGITS` (extracted). For duplicate names, nearest-position matching within
the name group.

**Result record:**
```java
public record BomTracedResult(
    int extractedCount, int compiledCount, int paired,
    int shapeOK, int posOK, int bothOK,
    List<BomTracedElement> failures
) {
    double shapeRate();  // shapeOK / paired × 100
    double posRate();    // posOK / paired × 100
    double bothRate();   // bothOK / paired × 100
}
```

**Current performance:** 97% of 90,310 elements (bothRate), 22 of 32 buildings at 100%.

---

## 6. SpatialDiff Subsystem

Per-element spatial comparison between two output DBs with tolerance band classification.

**Tolerance bands:**

| Band | Threshold | Meaning |
|------|-----------|---------|
| EXACT | ≤ 1mm all axes | Identical placement |
| DRIFT | > 1mm, ≤ 50mm | Minor positional drift |
| SHIFT | > 50mm | Significant misplacement |
| MISSING | — | In reference but not output |
| EXTRA | — | In output but not reference |

**Matching strategies** (tried in order):
1. **Identity match:** guid (ref) ↔ element_ref (output), requires >25% overlap
2. **Position match:** fallback — sort by (ifc_class, position bins, dimension tiebreakers)

**Hosted opening handling (CP-4 §4b):** Doors/windows use centroid distance instead
of per-coordinate comparison. Detected via `isHostedOpening()` (geometry, not IFC class).
Rationale: extraction AABB includes full frame + trim + sill; library mesh is canonical
product. Centroids are invariant to this asymmetric frame projection.

---

## 7. Integration Points

### 7.1 Pipeline Consumers

| Pipeline Stage | Current Class | Eyes API Call |
|---|---|---|
| IFCtoBOM extraction | DimensionRangeValidator | `ShapeClassifier.classifyArchetype()` + P24 |
| IFCtoBOM BOM build | BomValidator.checkShapeConsistency | `ShapeClassifier.verifyClassConsistency()` |
| DAGCompiler pre-write | PlacementProver.prove() | `EyesProofRunner.prove(placements, TIER1, TIER2)` |
| DAGCompiler post-write | PlacementProver.proveFromDB() | `EyesProofRunner.proveFromDB(dbPath, ALL)` |
| Rosetta Stone gate | GeometricFingerprintTest | `MultisetComparator` + `BomTracedComparator` |
| BIM Designer real-time | PlacementValidatorImpl | `EyesProofRunner.prove(single, TIER1)` |
| BIM Designer dim check | PlacementValidatorImpl | `ShapeClassifier` + EyesConstants thresholds |
| Spatial comparison | SpatialDiff.diff() | `SpatialDiff.diff()` (moved to Eyes) |
| Mesh validation | GeometryIntegrityChecker | Unchanged (stays in DAGCompiler — needs Mesh class) |

### 7.2 Facade Strategy

During migration, thin facades in DAGCompiler delegate to BIMEyes:

```java
// DAGCompiler: PlacementProver.java (Phase 2 facade)
public static ProofReport prove(List<PlacementData> placements, String name) {
    return EyesProofRunner.prove(placements, name, ProofTier.ALL);
}

// DAGCompiler: GeometricFingerprint.java (Phase 1 facade)
public static List<Fingerprint> computeFromExtracted(String path) {
    return FingerprintComputer.computeFromExtracted(path);
}
```

---

## 8. Test Specifications

### 8.1 Module Independence

```java
// BIMEyes/src/test/java/com/bim/eyes/EyesModuleTest.java
// Witness: W-EYES-INDEPENDENT
@Test void module_compiles_independently() {
    // Given: BIMEyes pom.xml with only orm-core + sqlite-jdbc dependencies
    // When:  mvn compile -pl BIMEyes -am
    // Then:  BUILD SUCCESS, no DAGCompiler or IFCtoBOM on classpath
}
```

### 8.2 Shape Classification

```java
// BIMEyes/src/test/java/com/bim/eyes/shape/ShapeClassifierTest.java
// Witness: W-EYES-CLASSIFY
@Test void wall_is_planar() {
    // Given: AABB dims (150, 3000, 2700) mm — typical wall
    // When:  ShapeClassifier.classifyArchetype(150, 3000, 2700)
    // Then:  PLANAR (planarity = 150/3000 = 0.050 < 0.15, elongation = 2700/3000 = 0.90 > 0.40)
}

@Test void column_is_elongated() {
    // Given: AABB dims (300, 300, 3000) mm — typical column
    // When:  ShapeClassifier.classifyArchetype(300, 300, 3000)
    // Then:  ELONGATED (planarity = 300/3000 = 0.10 < 0.15, elongation = 300/3000 = 0.10 < 0.40)
}

@Test void table_is_compact() {
    // Given: AABB dims (800, 600, 750) mm — typical table
    // When:  ShapeClassifier.classifyArchetype(800, 600, 750)
    // Then:  COMPACT (planarity = 600/800 = 0.75 > 0.25, elongation = 750/800 = 0.94 > 0.50)
}
```

### 8.3 Fingerprint Cross-Mode Equivalence

```java
// BIMEyes/src/test/java/com/bim/eyes/shape/FingerprintComputerTest.java
// Witness: W-EYES-FINGERPRINT
@Test void crossMode_sameElement_equivalentFingerprint() {
    // Given: SH extracted DB and SH output DB
    // When:  FingerprintComputer.computeFromExtracted(extractedPath)
    //        FingerprintComputer.computeFromOutput(outputPath)
    // Then:  for each GUID appearing in both,
    //        proveEquivalence(ext, cmp, 0.02) returns null (equivalent)
}
```

### 8.4 Proof Tier Tests

```java
// BIMEyes/src/test/java/com/bim/eyes/proof/EyesProofRunnerTest.java
// Witness: W-EYES-PROOF-TIER1
@Test void tier1_validPlacements_allProven() {
    // Given: list of 5 valid PlacementData records (positive extent, finite, non-degenerate)
    // When:  EyesProofRunner.prove(placements, "TEST", Set.of(ProofTier.TIER1))
    // Then:  report.violated() == 0, report.proven() == 20 (5 elements × 4 proofs)
}

// Witness: W-EYES-PROOF-DETECT
@Test void tier1_negativeExtent_detected() {
    // Given: PlacementData with minX > maxX (negative extent)
    // When:  EyesProofRunner.prove(List.of(bad), "TEST", Set.of(ProofTier.TIER1))
    // Then:  report.violated() >= 1
    //        report.results().stream().anyMatch(r -> r.proofId().equals("P01_POSITIVE_EXTENT")
    //            && r.status() == Status.VIOLATED)
}
```

### 8.5 Non-Disturbance

```java
// DAGCompiler/src/test/java/com/bim/compiler/contract/CompilerContractTest.java
// Witness: W-EYES-NONDISTURB
@Test void eyes_migration_preserves_SH_results() {
    // Given: SH Rosetta Stone (55 elements)
    // When:  run full pipeline through PlacementProver facade
    // Then:  9/10 PASS (same as pre-migration baseline)
    //        W-BOM-TRACED results unchanged
}
```

---

## 9. Non-Disturbance Analysis

### 9.1 PlacementProver Facade

**Rule:** After Phase 2 migration, PlacementProver.prove() and proveFromDB() must
produce identical ProofReport as the pre-migration version.

**Verification:** CompilerContractTest prover tests (7 tests) provide regression gate.
SH Rosetta Stone 9/10 is the acceptance bar.

**Non-Disturbance decision:**
- PlacementProver remains as public API (thin facade)
- All proof methods move to BIMEyes proof/ packages
- Facade delegates to EyesProofRunner
- Return types unchanged (ProofResult, ProofReport stay in eyes module, PlacementProver re-exports)

### 9.2 GeometricFingerprint Types

**Rule:** ShapeArchetype, ScaleBand, Fingerprint records must remain binary compatible.

**Verification:** GeometricFingerprintTest (multiset + BOM-traced) provides regression.
W-BOM-TRACED 97% across 90K elements is the acceptance bar.

**Non-Disturbance decision:**
- Types move to com.bim.eyes.shape package
- DAGCompiler GeometricFingerprint.java becomes thin wrapper with deprecated type aliases
- Phase 3 removes wrappers after all consumers migrate

### 9.3 SpatialDiff

**Rule:** SpatialDiff.diff() must produce identical DiffReport.

**Verification:** SpatialDiff is consumed by tests only (not production code).
Moving it does not affect runtime behavior.

### 9.4 GeometryIntegrityChecker

**Decision:** NOT MOVED. GeometryIntegrityChecker depends on `com.bim.compiler.geometry.Mesh`
and `Point3D` — DAGCompiler internal types. Moving it would drag geometry primitives
into BIMEyes, breaking the "depends only on orm-core + sqlite-jdbc" constraint.

GeometryIntegrityChecker can call BIMEyes APIs (ShapeClassifier, EyesConstants) but
lives in DAGCompiler.

---

## 10. Implementation Sequence

### Phase 1: Create Module + Move Core Types (1 session)

1. Create `BIMEyes/pom.xml` (groupId=com.bim, artifactId=bim-eyes)
2. Add `<module>BIMEyes</module>` to parent pom.xml
3. Move: ProductCategory → com.bim.eyes
4. Move: ShapeArchetype, ScaleBand, Fingerprint → com.bim.eyes.shape
5. Create: FingerprintComputer (computeFromExtracted, computeFromOutput)
6. Create: ShapeClassifier (classifyArchetype, classifyScaleBand, isHostedOpening, verifyClassConsistency)
7. Create: EyesConstants (all threshold constants)
8. Move: MultisetResult, BomTracedResult, proveMultisetEquivalence, proveBomTraced → com.bim.eyes.compare
9. Move: SpatialDiff → com.bim.eyes.diff
10. Leave thin wrappers in DAGCompiler GeometricFingerprint.java (delegates to Eyes)
11. Add bim-eyes dependency to DAGCompiler, IFCtoBOM, BonsaiBIMDesigner pom.xml files

**Verification:**
- `mvn compile -pl BIMEyes` — module compiles independently
- `mvn test -pl DAGCompiler` — all existing tests GREEN
- SH Rosetta Stone 9/10

### Phase 2: Extract Proof Methods (1-2 sessions)

1. Create: ProofResult, ProofReport → com.bim.eyes.proof
2. Create: Individual proof classes in tier1/, tier2/, tier3/
3. Create: EyesProofRunner orchestrator
4. PlacementProver becomes thin facade delegating to EyesProofRunner
5. Wire BonsaiBIMDesigner PlacementValidatorImpl to use EyesProofRunner

**Verification:**
- CompilerContractTest prover: 7/7 PASS
- BonsaiBIMDesigner: 258/258 GREEN
- W-BOM-TRACED unchanged

### Phase 3: New Proofs + Cleanup (1 session)

1. Implement P25 ROOM_VALIDITY
2. Implement P26 BUILDING_COMPLETENESS
3. Wire DimensionRangeValidator to use ShapeClassifier (P24 alignment)
4. Remove deprecated wrappers from DAGCompiler
5. Update TestArchitecture.md traceability matrix

**Verification:**
- P25/P26 PROVEN for SH, FK (simple buildings)
- P25/P26 results documented for all 34 buildings
- Full `run_tests.sh` GREEN

---

## 11. Design Decisions

| ID | Decision | Rationale |
|----|----------|-----------|
| D1 | BIMEyes depends only on orm-core + sqlite-jdbc | Module must be importable by any consumer without pulling DAGCompiler's geometry stack |
| D2 | GeometryIntegrityChecker stays in DAGCompiler | Depends on Mesh/Point3D internal types; moving would break D1 |
| D3 | ProofResult/ProofReport defined in Eyes, not PlacementProver | Canonical types belong to the comprehension engine, not a consumer |
| D4 | Thin facade phase (wrappers) before full migration | Non-disturbance: existing tests continue to import from DAGCompiler during transition |
| D5 | Individual proof classes, not one monolithic prover | Each proof is independently testable, replaceable, and documentable |
| D6 | P25/P26 are advisory, not critical | New aggregate proofs need validation across all 34 buildings before promotion to critical |
| D7 | EyesConstants centralizes ALL thresholds | Eliminates duplication; single file to audit for threshold drift |
| D8 | ProductCategory moves to Eyes (not stays in DAGCompiler) | It is semantic classification, not compilation logic — every consumer needs it |

---

## 12. Witness Registry

| Witness ID | Proof/Feature | Acceptance Criteria |
|------------|---------------|---------------------|
| W-EYES-INDEPENDENT | Module independence | `mvn compile -pl BIMEyes` succeeds with no DAGCompiler dependency |
| W-EYES-CLASSIFY | Shape classification | Wall→PLANAR, Column→ELONGATED, Table→COMPACT (3+ test cases) |
| W-EYES-FINGERPRINT | Cross-mode equivalence | SH extracted vs output: all matched GUIDs equivalent at ε=0.02 |
| W-EYES-PROOF-TIER1 | Tier 1 proofs | 5 valid elements → 20 PROVEN, 0 VIOLATED |
| W-EYES-PROOF-DETECT | Violation detection | Bad input → VIOLATED with correct proof ID |
| W-EYES-NONDISTURB | Non-disturbance | SH 9/10, CompilerContractTest 7/7, BIMDesigner 258/258 |
| W-ROOM-VALID | P25 room validity | SH rooms: walls≥2, floor, ceiling, door per room |
| W-BLDG-COMPLETE | P26 building completeness | SH: rooms, roof, external door present |

---

*End of EYES_SRS.md — Implementation begins Phase 1 next session.*
