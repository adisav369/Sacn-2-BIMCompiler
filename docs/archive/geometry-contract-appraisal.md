# Geometry Contract Appraisal

**Date:** 2026-02-03
**Purpose:** Evaluate the gap in contract-architecture-specification.md that allowed the roof geometry bug to escape
**For Review:** Next session

---

## 1. The Escaped Bug

**Symptom:** Roof appears rotated/twisted in 3D viewer for TB-LKTN single-storey house.

**Root Cause:** In `BuildingCompiler.java` lines 3634-3641, the face indices for `ridgeAlongX=true` create diagonal triangles instead of proper roof slopes:

```java
// CURRENT (WRONG for ridgeAlongX)
List<int[]> faces = List.of(
    new int[]{0, 1, 2},  // SW → SE → W_ridge  ← diagonal!
    new int[]{3, 5, 4},  // E_ridge → NE → NW  ← diagonal!
    ...
);
```

The faces connect vertices across the building diagonally rather than forming proper south/north slopes.

---

## 2. Why the Contract Architecture Didn't Catch It

### 2.1 Current Layer Model (from contract-architecture-specification.md)

```
Layer 5: SEMANTIC    - Domain validation rules
Layer 4: AGGREGATION - Merge, compose, deduplicate
Layer 3: RELATIONSHIP - Connect, host, feed, require
Layer 2: IDENTITY    - Same, different, continues
Layer 1: EXISTENCE   - GUID, storey, discipline, bounds
```

### 2.2 The Gap

| Layer | What It Validates | Does It Catch Roof Bug? |
|-------|-------------------|-------------------------|
| 5 | "Bathroom must have ventilation" | No - domain rules, not geometry |
| 4 | "Deduplicate by uniqueKey()" | No - about merging, not mesh validity |
| 3 | "Element connectsTo() junctions" | No - about relationships, not faces |
| 2 | "continuityId() across storeys" | No - about identity, not triangles |
| 1 | "Must have GUID, storey, bounds" | **Almost** - has `bounds()` but only BoundingBox |

**Critical Finding:** `IBIMEntity.bounds()` returns `BoundingBox` (min/max coordinates), NOT mesh validity. A mesh with completely wrong face winding still produces a valid bounding box.

### 2.3 RoofSpec Contract Status

```java
// BuildingCompiler.java line 4368
public record RoofSpec(
    String type,
    double pitchDegrees,
    double width, double depth, double ridgeRise,
    List<Point3D> vertices,
    List<int[]> faces
) {}  // NO INTERFACE IMPLEMENTATION
```

**RoofSpec implements ZERO contracts.** Even if it did, no existing layer validates:
- Face indices within vertex bounds
- Consistent face winding
- No degenerate triangles
- Proper normal directions

---

## 3. Research Findings: Industry Patterns

### 3.1 CGAL (Computational Geometry Algorithms Library)

**Source:** [CGAL Polygon Mesh Processing](https://doc.cgal.org/latest/Polygon_mesh_processing/index.html)

- Uses **concept-based design** (C++ concepts ≈ Java interfaces)
- `is_valid_polygon_mesh()` checks 2-manifold topology
- `does_self_intersect()` detects geometric problems
- Separates **combinatorial validity** from **geometric validity**

### 3.2 Manifold Library (Best Design Found)

**Source:** [Manifold Library Wiki](https://github.com/elalish/manifold/wiki/Manifold-Library)

Key principle: **"Topological exactness over geometric precision"**

- Topology (integer connectivity) is EXACT
- Geometry (float positions) is INEXACT
- **Three-level validation:**
  1. Topological: every edge connects to exactly one reversed edge
  2. ε-Validity: valid input → valid output (precision tracked)
  3. Soft Geometric: remove degenerate triangles

### 3.3 Euler Characteristic

**Source:** [Mesh Topology Analysis](https://max-limper.de/a_euler.html)

Formula: **χ = V + F - E**

- Closed manifold: χ = 2
- With genus g: χ = 2 - 2g
- With boundaries b: χ = V + F - E + B

**Use:** Fast topological sanity check before expensive operations.

### 3.4 glTF Validator

**Source:** [Khronos glTF Validator](https://github.com/KhronosGroup/glTF-Validator)

- Specification-based validation rules
- Example: `ACCESSOR_INDEX_TRIANGLE_DEGENERATE`
- Returns structured report with violation codes

### 3.5 BRep/Euler Operators (CAD/Solid Modeling)

**Source:** [Boundary Representation - Wikipedia](https://en.wikipedia.org/wiki/Boundary_representation)

- **Euler operators** guarantee valid topology by construction
- Every modification preserves Euler formula
- Manifold requirement: each edge shared by exactly 2 faces

---

## 4. Comparative Analysis

### 4.1 What the Current Spec Does Well

| Strength | Evidence |
|----------|----------|
| **Grounded in IFC standard** | Maps to IfcRelConnects*, IfcRelAggregates |
| **Theoretical foundation** | RCC-8, mereotopology, DDD patterns |
| **Solves boundary ownership** | SharedElementRegistry + JunctionPoint |
| **Layered abstraction** | Clean separation of concerns |
| **Practical migration path** | Phase 1-5 incremental rollout |

### 4.2 What the Current Spec Misses

| Gap | Consequence |
|-----|-------------|
| **No mesh primitive validation** | Invalid faces go undetected |
| **BoundingBox ≠ mesh validity** | Wrong geometry, correct bounds |
| **No topological invariant check** | Can't detect non-manifold meshes |
| **No face winding validation** | Inverted normals, rendering issues |
| **RoofSpec not covered** | 14 of 19 specs have no contract |

### 4.3 Layer Model Comparison

**Current (5 layers):**
```
L5: Semantic (domain rules)
L4: Aggregation (merge/dedupe)
L3: Relationship (connect/host)
L2: Identity (unique/continuous)
L1: Existence (guid/bounds)
```

**Proposed Extension (Layer 0):**
```
L5: Semantic (domain rules)
L4: Aggregation (merge/dedupe)
L3: Relationship (connect/host)
L2: Identity (unique/continuous)
L1: Existence (guid/bounds)
L0: GEOMETRY (mesh validity)  ← NEW
```

---

## 5. Why Layer 0 (Geometry) is Needed

### 5.1 Separation of Concerns

The Manifold library insight: **topology and geometry are different concerns**.

| Concern | Type | Validation |
|---------|------|------------|
| Topology | Integer (face indices) | Exact - can be proven correct |
| Geometry | Float (vertex positions) | Inexact - bounded by epsilon |

Current Layer 1 (`IBIMEntity.bounds()`) conflates these - it derives a geometric property (bounding box) without validating topological correctness (face indices).

### 5.2 Fail-Fast Principle

Invalid geometry should fail at creation, not at rendering:

```
CURRENT:  DSL → Parse → Compile → Write DB → Export glTF → View → "Why is roof wrong?"
                            ↑
                    Bug here, detected here ──────────────────────────────→

PROPOSED: DSL → Parse → Compile → VALIDATE → Write DB → Export → View
                            ↑         ↑
                    Bug here, caught here
```

### 5.3 The Euler Characteristic Test

A simple topological invariant check would have caught this:

```java
// For the roof (6 vertices, 6 faces, ? edges)
// If faces are wrong, edge count will be wrong
// χ = V + F - E should equal expected value for roof topology
```

---

## 6. Proposed Layer 0: Geometry Contract

### 6.1 Interface Design (Following Manifold Pattern)

```java
/**
 * Layer 0: Geometry Contract - Mesh primitive validity.
 *
 * Validates BEFORE element becomes a BIM entity.
 * Separates topological correctness (exact) from geometric precision (bounded).
 *
 * Theory: Euler characteristic, manifold topology
 * Reference: Manifold Library, CGAL concepts
 */
public interface IGeometryValidatable {

    // === Topological Checks (Exact) ===

    /** All face indices must reference valid vertices */
    boolean hasValidIndices();

    /** Euler characteristic matches expected topology */
    int eulerCharacteristic();  // V + F - E

    /** Each edge shared by exactly 2 faces (for closed mesh) */
    boolean isManifold();

    // === Geometric Checks (Bounded) ===

    /** No zero-area triangles */
    boolean hasNoDegenerateFaces();

    /** Face normals consistent (all outward or all inward) */
    boolean hasConsistentWinding();

    /** Vertices/faces arrays provided */
    List<Point3D> vertices();
    List<int[]> faces();

    // === Combined Validation ===

    default List<GeometryViolation> validateGeometry() {
        List<GeometryViolation> v = new ArrayList<>();
        if (!hasValidIndices())
            v.add(GeometryViolation.error("FACE_INDEX_OUT_OF_BOUNDS"));
        if (!hasNoDegenerateFaces())
            v.add(GeometryViolation.warning("DEGENERATE_FACE"));
        if (!hasConsistentWinding())
            v.add(GeometryViolation.error("INCONSISTENT_WINDING"));
        return v;
    }
}
```

### 6.2 Why This is Better

| Aspect | Current Spec | With Layer 0 |
|--------|--------------|--------------|
| **Roof bug** | Escapes | Caught by `hasConsistentWinding()` |
| **Theoretical grounding** | IFC/DDD only | + Euler characteristic, manifold theory |
| **Fail-fast** | At viewer | At compile time |
| **Separation** | Bounds = geometry | Topology ≠ geometry |
| **Industry alignment** | IFC only | + CGAL, Manifold, glTF patterns |

### 6.3 Implementation Path

1. **Define IGeometryValidatable** in `com.bim.compiler.contract`
2. **RoofSpec implements IGeometryValidatable** (catches roof bug)
3. **StairSpec implements IGeometryValidatable** (stairs have vertices/faces)
4. **Add GEOMETRY_VALID witness claim** to verify at compile time
5. **Existing contracts unchanged** - Layer 0 is below Layer 1

---

## 7. Spec Coverage Audit

### 7.1 Current State (19 Specs)

| Spec | Has Contract | Layer |
|------|--------------|-------|
| WallAssemblySpec | ✓ | IAggregatable (L4) |
| FrameSpec | ✓ | IIdentifiable (L2) |
| RoomSpec | ✓ | IIdentifiable (L2) |
| ColumnSpec | ✓ | IAggregatable (L4) |
| BeamSpec | ✓ | IAggregatable (L4) |
| **RoofSpec** | ✗ | None |
| **StairSpec** | ✗ | None |
| **DoorSpec** | ✗ | None |
| **WindowSpec** | ✗ | None |
| **SlabSpec** | ✗ | None |
| LandingSpec | ✗ | None |
| CladdingSpec | ✗ | None |
| OpeningSpec | ✗ | None |
| SprinklerSpec | ✗ | None |
| LightSpec | ✗ | None |
| ElectricalSpec | ✗ | None |
| FixtureSpec | ✗ | None |
| PlumbingSpec | ✗ | None |
| DiffuserSpec | ✗ | None |

**Coverage: 5 of 19 specs (26%)**

### 7.2 Which Need Layer 0 (Geometry)

| Spec | Has vertices/faces | Needs IGeometryValidatable |
|------|-------------------|---------------------------|
| RoofSpec | ✓ List<Point3D>, List<int[]> | **YES** |
| StairSpec | ✓ vertices, faces | **YES** |
| LandingSpec | Uses createBoxGeometry | Maybe (via box) |
| SlabSpec | Uses createBoxGeometry | Maybe (via box) |
| DoorSpec | Uses library or box | Maybe |
| WindowSpec | Uses createBoxGeometry | Maybe |

---

## 8. Recommendation

### 8.1 Immediate Fix
Fix the roof face indices in `BuildingCompiler.java` - this is a code bug independent of architecture.

### 8.2 Architectural Enhancement
Add Layer 0 (Geometry) to the contract hierarchy:

1. Create `IGeometryValidatable` interface
2. Have `RoofSpec` and `StairSpec` implement it
3. Add `GEOMETRY_VALID` witness claim
4. Document in contract-architecture-specification.md

### 8.3 Why Not Just Fix the Bug?

The bug will recur. Any spec with `vertices` + `faces` can have:
- Wrong face indices
- Inconsistent winding
- Degenerate triangles

Without a contract, each is a latent bug waiting for visual discovery.

---

## 9. References

- [CGAL Polygon Mesh Processing](https://doc.cgal.org/latest/Polygon_mesh_processing/index.html)
- [Manifold Library](https://github.com/elalish/manifold/wiki/Manifold-Library)
- [Mesh Topology Analysis - Euler Characteristic](https://max-limper.de/a_euler.html)
- [Khronos glTF Validator](https://github.com/KhronosGroup/glTF-Validator)
- [Boundary Representation - Wikipedia](https://en.wikipedia.org/wiki/Boundary_representation)
- [IfcFaceSurface - IFC4.3](https://standards.buildingsmart.org/IFC/RELEASE/IFC4_3/HTML/lexical/IfcFaceSurface.htm)

---

*Document prepared for review. The existing contract architecture is sound for relationship/identity/semantic concerns. The gap is in geometric primitive validation, which operates at a lower level than BIM entity relationships.*
