# BIMEyes SRS — Geometric Comprehension Engine
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>The compiler's ability to see shapes and verify spatial relationships.</b> Unifies geometric intelligence from 8 classes into one module — 97% of 90,310 elements pass shape and position proof.
</div>

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
No trained models — thresholds are derived from geometric definitions (a wall IS planar
by IFC class definition, not by statistical inference). No tolerance tuning, no heuristics.

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

### 4.2 Tier 2 — Pairwise/Relational (12 proofs)

Require pairs of elements or element-to-context relationships.

| Proof ID | Name | What it proves | Critical? |
|----------|------|----------------|-----------|
| P05 | NO_DUPLICATE_POSITION | No two elements at same centroid (1mm) | Yes |
| P06 | NO_SAME_CLASS_OVERLAP | Same-class AABBs do not intersect >10mm³ | Yes |
| P07 | OPENING_CONTAINED | Every door/window AABB within a host wall | No |
| P08 | FURNITURE_IN_ROOM | Every furniture element within a room AABB | No |

**P08 known limitation:** 2D centroid check only (X/Y). Z-axis containment relies on P04 storey Z-band.
| P09 | FIXTURE_ON_SURFACE | Fixtures (lights, outlets) touch a wall/ceiling | No |
| P15 | PIPE_IN_HOST | Pipe segments within their host floor/wall | No |
| P20 | WALL_ORIENTATION | Walls aligned to cardinal axes (±5°) | No |
| P21 | ELEMENT_IN_ROOM | Non-structural elements contained within rooms | No |

**P21 known limitation:** Assumes axis-aligned walls. Rotated buildings may produce false violations.
| P22 | OPENING_MESH_IN_BBOX | Opening mesh vertices within AABB (vertex-level) | Yes |
| P23 | DRAIN_CORNER_ALIGNMENT | Drain pipe segments share corner points | No |
| **P27** | **WALL_ROOF_INTERSECTION** | **Wall maxZ does not exceed roof surface at wall position** | **No** |
| **P28** | **ROOF_COVERAGE** | **Roof footprint covers building footprint in plan** | **No** |

### 4.6 New Proofs: P27 WALL_ROOF_INTERSECTION, P28 ROOF_COVERAGE

#### P27 WALL_ROOF_INTERSECTION

Detects walls that penetrate the roof surface. Essential for pitched/curved roofs where
glass curtain walls or full-height walls may extend above the slope line.

**Algorithm:** Tent-model estimation. Ridge runs along the longer AABB dimension.
Slope is linear from ridge (maxZ) to eave (minZ). For each wall under the roof footprint:
- Estimate roofZ at (wall.cx, wall.cy) via linear interpolation
- If wall.maxZ exceeds roofZ + 50mm tolerance → VIOLATED

**Flat roof:** dz < 0.1m → returns maxZ everywhere (no slope check needed).

**Witness:** W-DH-ROOF-1 (standard walls pass), W-DH-ROOF-3 (curtain wall violates).

#### P28 ROOF_COVERAGE

Verifies that the roof footprint (XY) covers the building footprint derived from all wall extents.
Detects gaps where the building has no roof overhead.

**Algorithm:** Compare roof AABB (union of all IfcRoof/IfcRoofSlab) against wall AABB
(union of all IfcWall/IfcCurtainWall/IfcWallStandardCase) with 50mm overhang tolerance.

**Witness:** W-DH-ROOF-2.

P27/P28 are verification proofs — they prove compiled geometry is correct. The same
maths running in reverse can *produce* geometry. See [Geometry Forge](GEOMETRY_FORGE_SRS.md)
§4 and §8 for how EYES verification extends to formula-computed pieces.

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

### 4.7 Tier 4 — Provenance and Surface Conformance (GEO Proofs)

Tier 1–3 proofs verify the output in isolation — they check spatial
invariants without knowing where the data came from. Tier 4 proofs
verify against a **comparison target**: either the extraction source
(for Rosetta Stone buildings) or a mathematical surface definition
(for ShipYard domains).

Tier 4 activates only when GEO mode is enabled (`-Dbim.geo.debug=true`).
In normal compilation, Tier 4 is skipped — no extraction DB connection,
no performance cost. See [LMP §7](LAST_MILE_PROBLEM.md#7-separate-from-input)
for the T18 exemption rationale.

| Proof ID | Name | What it proves | Comparison target | Critical? |
|----------|------|----------------|-------------------|-----------|
| **P29** | TACK_FIDELITY | Compiled position matches source within 1mm per axis | extracted.db `elements_rtree` by GUID | Yes |
| **P30** | GUID_CHAIN | Every compiled element traces to an IFC GUID via `m_bom_line_ma` | BOM.db `m_bom_line_ma` | Yes |
| **P31** | RELATIVE_OFFSET | Sibling elements within a BOM assembly preserve BOM `dx/dy/dz` separation | BOM.db `m_bom_line` | Yes |
| **P32** | SURFACE_CONFORMANCE | Element position lies on a design surface within tolerance | `SurfaceFunction.evaluate(u, v) → (x, y, z)` | Yes |

#### P29 TACK_FIDELITY — Round-Trip Position Proof

```
∀ element e with guid g in compiled output:
  Let source = extracted.elements_rtree WHERE guid = g
  Assert: |e.minX - source.minX| < 1mm ∧ |e.minY - source.minY| < 1mm ∧ |e.minZ - source.minZ| < 1mm
  Result: MATCH (delta ≤ 1mm) or DRIFT (delta > 1mm, per-axis diagnostic)
```

P29 is the mathematical formulation of the GEO MATCH/DRIFT verdict.
For each element, it answers: "did the BOM tack chain faithfully reproduce
the extraction position?" The TACK LEAF log line is the emission of this
proof — `anchor + offset + half → centroid` with the delta against source.

#### P30 GUID_CHAIN — IFC Provenance Proof

```
∀ element e in compiled output:
  Assert: e.guid ∈ extracted.elements_meta.guid
          (the GUID is a real IFC GloballyUniqueId, not a synthetic ID)
  Assert: ∃ ma ∈ m_bom_line_ma WHERE ma.guid = e.guid
          (the GUID was carried through the BOM chain)
  Result: TRACED (GUID found in both extraction and BOM) or ORPHAN (synthetic GUID)
```

P30 answers: "can I trace this compiled element back to a specific IFC
entity in the original file?" Without this, the compilation is a black box
— you get a building but can't prove which IFC elements it came from.

#### P31 RELATIVE_OFFSET — Sibling Separation Proof

```
∀ BOM assembly B with children c_i, c_j:
  Let bom_dx = m_bom_line(c_j).dx - m_bom_line(c_i).dx
  Let out_dx = output(c_j).minX - output(c_i).minX
  Assert: |out_dx - bom_dx| < 1mm (per axis)
  Result: MATCH or DRIFT
```

P31 answers: "is the desk still 2.6m from the bed?" It verifies that the
relative spatial arrangement within a BOM assembly survived compilation.
This is the single-level tack proof — one parent, one offset. Verified for
SH_BED_SET: 0.000mm error on all axes.

#### P32 SURFACE_CONFORMANCE — Mathematical Surface Proof

```
∀ element e in compiled output where domain has SurfaceFunction f:
  Let (x, y, z) = f(u(e), v(e))    — evaluate design surface at element's parametric coordinates
  Assert: |e.minX - x| < tolerance ∧ |e.minY - y| < tolerance ∧ |e.minZ - z| < tolerance
  Result: MATCH (on surface) or DRIFT (off surface, with normal distance)
```

P32 is the ShipYard extension. The comparison target is not a database
lookup but a function evaluation. A hull plate is verified against the
NURBS hull surface. A tunnel segment is verified against the bore arc.
A bridge element is verified against the survey alignment curve. Same
proof interface, different `SurfaceFunction` implementation per domain.

**SurfaceFunction interface:**
```java
public interface SurfaceFunction {
    Point3D evaluate(double u, double v);
    double tolerance();
    String description();  // "NURBS hull surface", "circular bore r=3200mm"
}
```

#### Why Tier 4 is separate

Tiers 1–3 run on every compilation with zero external dependencies.
Tier 4 requires either an extraction database (P29/P30/P31) or a surface
definition (P32). These are verification-mode proofs — activated for quality
assurance, not for production throughput. The `bim.geo.debug` flag controls
this: off by default, on when you need the proof chain.

**The progression:**
- Tier 1: "Is this element physically valid?" (arithmetic)
- Tier 2: "Does this element relate correctly to its neighbours?" (spatial)
- Tier 3: "Does this building satisfy conservation laws?" (aggregate)
- Tier 4: "Does this output match its source or design intent?" (provenance)

#### P33 PATTERN_MATCH — Vocabulary-Based Generative Verification

For generative buildings (no extraction source), P33 verifies against the
**spatial vocabulary** learned from Rosetta Stone GEO data.

**Input:** GEO TACK data from the building under test + vocabulary database
built from verified Rosetta Stones (`geo_verify.py` output).

**Vocabulary structure:**

```
vocabulary.db:
  spatial_pattern(
    pattern_id    INTEGER PK,
    archetype     TEXT,      -- PLACE, CLUSTER, TILE, ROUTE, FRAME
    ifc_class     TEXT,      -- IfcDoor, IfcWall, IfcFurnishingElement
    parent_class  TEXT,      -- parent BOM category (ROOM, FLOOR, SET)
    offset_range  TEXT,      -- min/max dx,dy,dz from verified examples
    sibling_count_range TEXT, -- min/max siblings in this pattern
    source_buildings TEXT,   -- which Rosetta Stones contributed this pattern
    instance_count INTEGER   -- how many verified examples
  )
```

Populated by scanning GEO logs from verified buildings. Each TACK LEAF
line contributes an observation: "IfcDoor in ROOM parent, offset
(10.74, 2.18, 0.47), 2 sibling doors." Aggregated across 35 buildings,
the pattern becomes: "IfcDoor in ROOM: dx=[0.1–15.5], dy=[0.1–8.7],
dz=[0.4–0.5], siblings=[1–5]."

**Proof logic:**

```
∀ element e in generative output:
  1. Classify: archetype from verb_ref, ifc_class, parent category
  2. Match: find spatial_pattern WHERE archetype AND ifc_class AND parent match
  3. Check: e.offset within pattern.offset_range AND
           e.sibling_count within pattern.sibling_count_range
  Result:
    CONSISTENT — matches a proven pattern (with confidence = instance_count / total)
    NOVEL — no pattern match (new arrangement, needs human review)
    ANOMALOUS — matches pattern type but offset outside proven range
```

**Example: DemoHouse (DM) — generative, composed from SH + FK:**

```
P33 IfcDoor in ROOM: offset=(10.74, 2.18, 0.47) — CONSISTENT
    (matches SH pattern, 3 verified instances, range dx=[10.7–12.2])

P33 IfcFurnishingElement in SET: offset=(0.42, 2.60, 0.00) — CONSISTENT
    (matches SH BED_SET pattern, 1 verified instance, exact match)

P33 IfcFlowTerminal in ROOM: offset=(2.10, 1.50, 0.85) — NOVEL
    (no FlowTerminal pattern in vocabulary — FP discipline, new to DM)

P33 IfcWall floating dz=2000mm above slab — ANOMALOUS
    (wall patterns have dz=[0.0–470mm], this is 4x outside range)
```

**What P33 tells you without visual inspection:**
- Doors are where doors should be (CONSISTENT)
- Furniture arrangements match proven sets (CONSISTENT)
- Fire protection terminals are new — review but not alarming (NOVEL)
- A floating wall is wrong (ANOMALOUS)

**Implementation:** Java class `PatternMatchProof` in BIMEyes. Reads
vocabulary from SQLite. Runs as Tier 4 proof alongside P29-P32.
Vocabulary built offline by `scripts/geo_verify.py --build-vocabulary`
scanning all verified GEO logs.

### 4.8 Research Direction — Tack Signatures as Geometric Semantics

> **Status:** Research question, not implementation spec. Documented here
> because the evidence points to a deeper capability than rule checking.

#### The observation

After 35 buildings, the compiler has accumulated thousands of verified tack
chains — parent-child spatial relationships extracted from real IFC files,
stored as BOM offsets, recompiled, and verified to sub-millimetre accuracy.

A tack is three numbers: dx, dy, dz. But within its BOM hierarchy, it
carries **semantic meaning** that is never explicitly stated:

```
SH_BED_SET:
  bed  → dx=0.00, dy=0.00, dz=0.00  (anchor)
  desk → dx=0.42, dy=2.60, dz=0.00  (beside and behind the bed)
```

Nobody wrote "beside and behind the bed." The tack offset implies it. The
BOM Drop doesn't describe the spatial arrangement — it drops a recipe and
the tacks carry the meaning. The ecosystem is inferred from the data.

#### Tack signature = syntactic + semantic

- **Syntactic layer:** the numbers — `{child_count, offsets[], product_categories[], aspect_ratios[]}`
- **Semantic layer:** what the numbers mean in context — the parent BOM,
  the sibling tacks, the spatial pattern they form together

The same tack `(0.42, 2.60, 0)` in a BED_SET means "workspace corner."
In a KITCHEN_SET it might mean "fridge behind the counter." The numbers
are identical. The semantics change because the context — parent category,
sibling products — changes.

#### The finite vocabulary hypothesis

Protein science discovered that despite billions of possible amino acid
sequences, nature uses approximately **1,400 distinct protein folds.** Every
protein is a composition of these ~1,400 spatial archetypes. The folding
problem reduced from "predict any 3D shape" to "which of 1,400 folds?"

Evidence suggests construction has a similar finite vocabulary. Across 35
buildings and 48,428 elements in the largest, the dominant placement
patterns are only **5 verb types** (PLACE, CLUSTER, TILE, ROUTE, FRAME).
These 5 patterns cover 99% of all element placements.

| Verb pattern | Geometric archetype | Construction example | Cross-domain |
|-------------|-------------------|---------------------|--------------|
| PLACE | Single object at offset | Door in wall | Equipment on skid |
| CLUSTER | Irregular group in AABB | Furniture set | Cabin fitout |
| TILE | Regular grid | Ceiling tiles | Hull plates |
| ROUTE | Linear chain with branches | Pipe riser + header | Cable tray |
| FRAME | Structural repetition | Column grid | Ship frames |

If the vocabulary is finite, then after enough Rosetta Stones, EYES has
seen every geometric archetype that construction produces — not every
building, but every **spatial pattern.** A new IFC can be classified
instantly by matching tack signatures against known archetypes.

#### What EYES could do with this

Given a new IFC (never seen, unknown building type), EYES reads the raw
geometry — AABBs, positions, adjacency — and matches spatial signatures
against its library of tack patterns learned from 35 buildings:

- "These four walls enclosing a slab with furniture at regular offsets
  — CLUSTER archetype, room confidence 0.98"
- "These vertical segments with horizontal branches at floor intervals
  — ROUTE archetype, fire riser confidence 0.95"
- "These plates with continuously varying curvature and frame-aligned
  stiffeners — TILE archetype, hull surface confidence 0.92"

Not rule matching. **Pattern recognition from compiled experience.** The
compiler is the teacher. EYES is the student. The GEO tack chain is the
curriculum. Each Rosetta Stone that passes through compilation adds to the
geometric vocabulary.

#### Open questions

1. **Is the archetype vocabulary provably finite?** The 5-verb evidence is
   suggestive but not a proof. Need: formal analysis of the tack offset
   equivalence classes under scale/rotation/reflection.

2. **How many Rosetta Stones until saturation?** Protein science needed
   ~200K structures to cover 1,400 folds. How many buildings cover the
   construction vocabulary? The 5-verb convergence at 35 buildings suggests
   the number is small.

3. **Does the vocabulary transfer across domains?** A TILE pattern from
   ceiling tiles and a TILE pattern from hull plates have the same geometric
   signature. Does EYES trained on buildings recognise ship geometry? The
   tack math is identical — the question is whether the signatures match.

4. **Can EYES infer the formula from the pattern?** Given 500 hull plates,
   can EYES reconstruct the NURBS surface they sample? Given a column grid,
   can it infer the structural bay spacing? This is the perception question
   — not checking against a known formula, but deriving the formula from the
   observed geometry.

These questions connect to the cross-domain precedent in
[TheRosettaStoneStrategy.md](TheRosettaStoneStrategy.md#cross-domain-precedent--the-folding-problem)
and [ShipYard.md](ShipYard.md#geo-formula-verification--the-shipyard-breakthrough).

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
    // Given: SH Rosetta Stone (58 elements)
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
- DAGCompiler GeometricFingerprint.java becomes thin wrapper with type aliases
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

### Phase 3: New Proofs + Cleanup (1 session) — DONE (session 50)

1. ~~Implement P25 ROOM_VALIDITY~~ DONE — `RoomValidityProof.java` (162 lines)
2. ~~Implement P26 BUILDING_COMPLETENESS~~ DONE — `BuildingCompletenessProof.java` (159 lines)
3. ~~Wire DimensionRangeValidator to use ShapeClassifier (P24 alignment)~~ DONE — uses `EyesConstants.DIM_RANGE_RATIO`
4. Remove DAGCompiler wrappers — **DEFERRED** (wrappers harmless)
5. ~~Update TestArchitecture.md traceability matrix~~ DONE — 9 FL-2/FL-5 witnesses added

**FL-5 Integration (session 50):**
- `ShapeAdvisoryWriter` in IFCtoBOM writes `layer='SHAPE'` rows to `W_Validation_Advisory`
- Two checks: CLASS_SHAPE (archetype vs IFC class), DISCIPLINE_SHAPE (geometry-inferred discipline)
- Wired as Layer 3 in IFCtoBOMPipeline, after DIMENSION (DV010) and PROFILE (DV011)
- SH: 19 shape advisories detected (thin mullions flagged as MEP-like)
- FlyAdvisoryTest: W-FL-SHAPE-1, W-FL-SHAPE-2 GREEN

**Verification:**
- P25/P26 wired into EyesProofRunner.proveFromDB
- SH: P25 SKIPPED (no IfcSpace in output), P26 roof PROVEN
- FlyAdvisoryTest 9/9 GREEN
- BonsaiBIMDesigner 285/285 GREEN

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
| W-TE-PROOF | P05/P06 TE position disambiguation | Terminal: 0 critical violations, 48428 elements (jitter guard for IFC source duplicates + cross-discipline co-location) |

---

## 10. Audit Finding: Proof Coverage Honesty (S60 post-audit)

Post-S60 audit ([AUDIT_S51_FOCUSED.md §Oracle Independence](https://github.com/red1oon/BIMCompiler/blob/master/internal/AUDIT_S51_FOCUSED.md)) found the 28-proof claim overstates actual per-element coverage:

| Category | Count | Nature |
|----------|-------|--------|
| Genuine per-element checks | ~14 | P01-P06, P07-P09, P10, P11 (per-face), P12 (per-room), P15-P18 |
| Aggregate (single building verdict) | ~8 | P10b, P13, P14, P25-P28 |
| Conditional (skipped without relational context) | ~6 | P20-P23, P07-P09 when no wall/room data |

S61 update: P11 (WallCoverage) now emits per-room-face results. P12 (RoomHasDoor) now populates element field with room identifier. P16-P18 (WasteGradient, SystemConnected, VentAboveRoof) confirmed per-element.

**FL-2/FL-5 are advisory output** (`W_Validation_Advisory` with `severity='SUGGESTION'`), not gating proofs. Zero pipeline gate impact.

**SpatialDiff** exists in EYES but is not integrated into G3 gate failure reporting.

### EYES Role in 3-Layer Test Architecture

EYES is **Layer 3: reference-free geometric sanity** — independent of both the Rosetta Stone round-trip (Layer 1) and generative assembly verification (Layer 2). EYES catches geometric violations (doors outside walls, open perimeters, missing roofs) that neither layer would detect.

Layer 1 and Layer 2 verification (BOM offset fidelity, library provenance) belong in the **gate tests**, not EYES, because they require BOM + library joins that EYES has no access to.

Two EYES checks support Layer 2: rotation validity (pure math, P-ROT candidate) and spatial containment (P07 already exists). All other Layer 2 checks are gate-test scope.

See [LAST_MILE_PROBLEM.md §Relational Round-Trip](LAST_MILE_PROBLEM.md#relational-round-trip-verification-s60-post-audit) and [TestArchitecture.md §Corrected Understanding](TestArchitecture.md#corrected-understanding-rosetta-stone-gates-prove-relational-round-trip).

### Mathematical Foundation of EYES Proofs

Each EYES proof is a predicate `P: Element → {PROVEN, VIOLATED, SKIPPED}` over the compiled output database. The proofs are grouped by the mathematical structure they verify.

**Tier 1 — Per-element arithmetic invariants.** These are necessary conditions for any valid physical object. Given an element `e` with axis-aligned bounding box `AABB(e) = [minX, maxX] × [minY, maxY] × [minZ, maxZ]`:

- **P01 (PositiveExtent):** `∀e: maxX-minX > 0 ∧ maxY-minY > 0 ∧ maxZ-minZ > 0` — a physical object occupies positive volume in all three axes.
- **P02 (FiniteCoords):** `∀e: |minX|, |maxX|, ..., |maxZ| < ∞` — coordinates are finite (rejects NaN, ±∞ from degenerate transforms).
- **P03 (MinDimension):** `∀e: min(dx, dy, dz) ≥ ε` where `ε = 10mm` — below this, the element is sub-resolution (parametric degeneration).
- **P04 (StoreyZBand):** `∀e: minZ(e) ∈ [z_storey - δ, z_storey + H + δ]` where `z_storey` is the storey elevation and `H` is storey height — elements must lie within their declared storey's vertical band.

**Tier 2 — Pairwise spatial relations.** These verify topological relationships between elements using AABB containment and intersection predicates.

Let `contains(A, B) ≡ A.minX ≤ B.minX ∧ B.maxX ≤ A.maxX ∧ ...` (all 6 faces).
Let `overlaps(A, B) ≡ A.minX < B.maxX ∧ B.minX < A.maxX ∧ ...` (all 3 axes).

- **P07 (OpeningContainment):** `∀ opening ∈ {IfcDoor, IfcWindow}: ∃ wall: contains(AABB(wall) ⊕ τ, AABB(opening))` where `⊕ τ` is Minkowski dilation by containment tolerance `τ = 150mm`. A door or window must project within its host wall, within tolerance.
- **P08 (FurnitureInRoom):** `∀ furniture: ∃ room: contains(AABB(room) ⊕ τ, AABB(furniture))`. Furniture must lie within its assigned room.
- **P09 (FixtureOnSurface):** `∀ fixture: ∃ wall: |centroid(fixture) - face(wall)| < τ_depth`. Fixtures (outlets, switches) must be surface-mounted on a wall face.
- **P15 (DuplicatePosition):** `∀ e_i, e_j where class(e_i) = class(e_j): ¬(|centroid(e_i) - centroid(e_j)| < ε)`. No two elements of the same IFC class may occupy the same position (detects copy errors).
- **P06 (SameClassOverlap):** Same position + same dimensions → CRITICAL (real duplicate). Partial overlap → PROVEN (structural joint). P130 ([c78c743b](https://github.com/red1oon/BIMCompiler/commit/c78c743b)) replaced the black-box volume heuristic: DX 83→0 critical, FK 15→0.

**P05/P06 IFC source duplicate guard (W-TE-PROOF):** TE (Terminal) has two classes of co-located pairs: (1) IFC source duplicates — 19 pairs from Revit modeling error (same element twice at identical position, ACMV/ELEC/FP/CW classes); (2) cross-discipline co-location — 16 IfcFlowTerminal pairs where CW supply and SP drainage each hold a distinct IFC entity at the same physical fixture. `PlacementCollectorVisitor` applies a 2mm Z jitter per collision index, keyed on `ifcClass|mm(cx)|mm(cy)|mm(cz)`. Count stays 48428. 2mm (not 1mm) avoids FP boundary: centroid accumulation at cz≈26m yields `dist(1mm) ≈ 0.000998m < 0.001m`. GEO log: `[GEO][JITTER]` line per jittered element.

**Tier 2 — Wall orientation and roof coverage.** Verified against cardinal geometry:

- **P-ORIENT (WallOrientation):** `∀ wall: aspect_ratio(wall) > 3:1 → orientation(wall) ∈ {NS, EW}` where `NS ≡ dy/dx > 3`, `EW ≡ dx/dy > 3`. Elongated walls must be axis-aligned.
- **P-ROOF (RoofCoverage):** `∀ room: ∃ roof: projection_XY(AABB(roof)) ⊇ projection_XY(AABB(room)) ⊖ τ`. Every room must have a roof element whose XY projection covers the room footprint.

**Tier 3 — Conservation laws and topological invariants.** These are building-level constraints that cannot be decomposed per-element:

- **P10b (PerimeterClosure):** The exterior wall graph `G = (V, E)` where `V` = wall endpoints and `E` = wall segments forms an **Eulerian cycle**: `∀v ∈ V: deg(v) = 2`. An open perimeter means a gap in the building envelope.
- **P13 (PerimeterLength):** `|Σ_i length(wall_i) - L_expected| < τ_perim` where `L_expected` is derived from room boundary geometry. Wall lengths must sum to the declared perimeter.
- **P14 (FloorArea):** `|Σ_j area(slab_j) - Σ_k area(room_k)| / Σ_k area(room_k) < 0.1`. Total slab area must approximate total room area within 10%.

**Tier 3 — Per-room structural completeness:**

- **P11 (WallCoverage):** `∀ room, ∀ face ∈ {N,S,E,W}: ∃ wall: |length(wall ∩ face) - length(face)| < τ_coverage`. Each room face must be covered by a wall segment. Emits one `ProofResult` per room face (S61 update).
- **P12 (RoomHasDoor):** `∀ room ∉ {utility, porch}: ∃ door ∈ children(room)`. Every habitable room must have at least one door. Emits one `ProofResult` per room with room identifier (S61 update).
- **P25 (RoomValidity):** `∀ room (IfcSpace): has_walls(room) ∧ has_floor(room) ∧ has_ceiling(room) ∧ has_door(room)`. Composite per-room structural completeness.

**Tier 4 closes the offset gap.** Tiers 1–3 operate on the output database only
— they verify geometric sanity but cannot verify that the result honours the
BOM's declared offsets. Tier 4 (§4.7) adds GEO provenance proofs that compare
compiled positions against extraction source (P29 TACK_FIDELITY) or mathematical
surface definitions (P32 SURFACE_CONFORMANCE). These require external data
(extraction DB or surface function) and activate only in GEO debug mode.
Standard gate tests (G1-G6, C8/C9) remain the production verification layer.
*(S67: W-GEN-COMPILE-5 removed; DM compiles through the same pipeline as every Rosetta Stone.)*

**What EYES replaces.** Without EYES, a human must visually inspect every compiled building in a 3D viewer to detect misplaced doors, floating furniture, open perimeters, and missing roofs. EYES automates this inspection with mathematical predicates that are faster, exhaustive, and reproducible. The human viewer becomes a confirmation tool — you open it to see what EYES has already proven, not to find what might be wrong.

---

*End of EYES_SRS.md*
