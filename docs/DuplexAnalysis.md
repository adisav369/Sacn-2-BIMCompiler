# Duplex Mirror Analysis — IFC2x3_Duplex Forensics
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Mirror algorithm proof — two units reflected across a party wall.</b> DX exercises the MIRROR verb and multi-storey BOM structure with 1,099 elements.
</div>

**Spec alignment (2026-03-18):** DX BOM uses centroid-floorMin offsets — same
tack convention drift as SH and TE. Must implement `BOMBasedCompilation.md` §4
(parent LBD to child LBD, BUFFER lines, SUM invariant). Code changes spec in
`ACTION_ROADMAP.md` §Pre-Code Specs.

## Building Geometry

| Axis | Min      | Max    | Extent | Half  |
|------|----------|--------|--------|-------|
| X    | -0.390   | 8.825  | 9.215m | 4.607 |
| Y    | -22.183  | 4.383  | 26.565m| 13.283|
| Z    | -1.250   | 6.635  | 7.885m | 3.943 |

**Party wall:** `Basic Wall:Party Wall - CMU Residential Unit Dimising Wall`
spans X = [4.125, 4.675], center X = **4.400**.

The mirror plane is at **X = 4.4** (world coords), running along Y (depth).
Elements are side-by-side in X, not Y.

## Partition Algorithm

Three-tier partition based on element AABB vs mirror line:

```
          A-side          │ party wall │          B-side
  ◄───── max_x ≤ 4.4 ─── │ SPANS 4.4  │ ──── min_x ≥ 4.4 ─────►
         553 elements     │ 55 shared  │      491 elements
```

**Tier 1 — SPANNING:** Element AABB crosses the mirror line
(`min_x < mirror_x AND max_x > mirror_x`).
These are shared infrastructure: party walls, full-width exterior walls,
cross-unit plumbing risers, conduit runs.

**Tier 2 — PAIRED:** For elements entirely on one side, group by
`(M_Product_ID, storey)`. Per group, `min(A_count, B_count)` elements
are *paired* — they have counterparts on the other side.

**Tier 3 — EXCESS:** Per group, `|A_count - B_count|` elements have
no mirror counterpart. These are unique plumbing fixtures, extra
fittings from asymmetric routing. They go into SHARED.

## Partition Results

| Category | Count | What |
|----------|-------|------|
| Half-unit (paired per side) | 485 | min(A, B) per product per storey |
| Spanning (crosses mirror) | 55 | Party walls, exterior walls, risers |
| Excess (unmatched) | 74 | Extra elbows, pipes, PVC bends |
| **SHARED total** | **129** | Spanning + Excess |

**Verification:** 485 × 2 + 129 = 1099 ✓

**Stored:** 485 (half-unit) + 129 (shared) = **614 lines in DX_BOM.db**
**Produced:** 485 (A) + 485 (mirror) + 129 (shared) = **1099 elements**

## Per-Storey Breakdown

| Storey  | Paired/side | Excess | Spanning |
|---------|-------------|--------|----------|
| Level 1 | 237         | 59     | (in total)|
| Level 2 | 231         | 14     |          |
| Roof    | 4           | 1      |          |
| T/FDN   | 1           | 0      |          |
| Unknown | 12          | 0      |          |
| **Total**| **485**    | **74** | **55**   |

*(Note: per-product-per-storey grouping gives 485 paired vs 487 per-product-only.
The 2 difference is from products appearing in multiple storeys with different balance.)*

## Structural Symmetry (ARC) — Perfect Mirror

| Class               | L1 A-ctr-B  | L2 A-ctr-B  |
|---------------------|-------------|-------------|
| IfcWall             | 8-4-9       | 10-4-11     |
| IfcDoor             | 3-0-3       | 4-0-4       |
| IfcWindow           | 2-0-2       | 9-0-9       |
| IfcFurnishingElement| 18-2-21     | 10-0-10     |
| IfcSlab             | 3-4-3       | 5-0-5       |

Structural elements have near-perfect count symmetry. Center elements
are party wall / full-width elements.

## MEP Symmetry — Paired With Trunk

| Class             | L1 A-ctr-B   | L2 A-ctr-B  |
|-------------------|--------------|-------------|
| IfcFlowFitting    | 83-57-120    | 31-17-46    |
| IfcFlowSegment    | 68-30-65     | 96-41-126   |
| IfcFlowTerminal   | 29-3-30      | 18-3-22     |
| IfcFlowController | 7-0-7 (Unk) |             |

MEP has a large center cluster (party wall trunk — shared risers and
drain stacks). The slight L/R count differences (e.g. 83 vs 120 fittings)
are from asymmetric plumbing routing in the IFC model.

## Excess Element Inventory

| Product              | A-side | B-side | Excess | Type |
|----------------------|--------|--------|--------|------|
| FITTING_ELBOW_GENERIC| 115    | 105    | 10     | MEP  |
| PIPE_MECHANICAL_33MM | 89     | 80     | 9      | MEP  |
| PIPE_PVC_33MM        | 11     | 2      | 9      | MEP  |
| FITTING_BEND_PVC_DWV | 10     | 2      | 8      | MEP  |
| PIPE_COLD_WATER_25MM | 22     | 15     | 7      | MEP  |
| CONDUIT_EMT_30MM     | 7      | 2      | 5      | MEP  |
| PIPE_WASTE_48MM      | 32     | 27     | 5      | MEP  |
| FITTING_TEE_GENERIC  | 41     | 37     | 4      | MEP  |
| CONDUIT_ELBOW_STEEL  | 6      | 2      | 4      | MEP  |
| PIPE_HOT_WATER_13MM  | 16     | 13     | 3      | MEP  |
| BEAM_W310X60         | 2      | 0      | 2      | STR  |
| SLAB_FINISH_WOOD     | 3      | 5      | 2      | STR  |
| Others (< 2 each)    |        |        | 6      | mixed|
| **Total excess**     |        |        | **74** |      |

97% of excess is MEP (plumbing fittings and pipe segments).

## Algorithm (Abstract)

```
partition(elements, mirror_x):
    spanning = [e for e where e.min_x < mirror_x AND e.max_x > mirror_x]
    a_side   = [e for e where e.max_x <= mirror_x]
    b_side   = [e for e where e.min_x >= mirror_x]

    half_unit = []
    shared_excess = []

    for each (product_id, storey) group:
        a_group = a_side elements in this group
        b_group = b_side elements in this group
        paired  = min(|a_group|, |b_group|)

        # Take 'paired' elements from A-side → half-unit
        half_unit += a_group[:paired]

        # Excess from either side → shared
        if |a_group| > paired:
            shared_excess += a_group[paired:]
        if |b_group| > paired:
            shared_excess += b_group[paired:]

    return half_unit, spanning + shared_excess
```

**YAML carries the concrete:** `mirror_center`, `mirror_axis`.
**Java applies the abstract:** partition → half-unit → pair.

## Abstract Mirror Model — Industry-Wide

The mirror is defined by a **plane** (axis + position). The algorithm
is axis-agnostic: works for X-mirror (DX party wall), Y-mirror
(row houses), Z-mirror (stacked units), or rotated planes.

### YAML Convention

```yaml
composition:
  type: MIRRORED_PAIR
  pair_bom_id: DUPLEX_SET_STD
  half_unit_bom_id: DUPLEX_SINGLE_UNIT_STD
  mirror:
    axis: X                # partition axis: X, Y, or Z
    position: 4.4          # world-coord position on that axis
    rotation: 3.14159      # radians rotation applied to B-side (pi = 180°)
```

**`axis`** — which world axis the mirror plane is perpendicular to.
- `X`: party wall runs along Y (DX duplex, side-by-side units)
- `Y`: party wall runs along X (row houses, front-to-back mirror)
- `Z`: floor plate mirror (stacked inverted units — rare)

**`position`** — the partition coordinate on that axis (world coords).
Derived from the party wall center, not building AABB half-dims.

**`rotation`** — how the B-side is produced from A-side.
For simple mirror: π radians about the mirror axis.
For L-shaped or angled: could be π/2 (90°) or other values.

### Future: L-Shaped and Multi-Axis

For L-shaped buildings with a 90° rotation:
```yaml
composition:
  type: ROTATED_PAIR
  mirror:
    axis: Z                # rotate about Z (vertical)
    position: [4.4, 11.0]  # pivot point (X, Y world coords)
    rotation: 1.5708       # pi/2 = 90°
```

For quad-plex (4 units from 1 master):
```yaml
composition:
  type: QUAD_MIRROR
  mirrors:
    - { axis: X, position: 4.4 }
    - { axis: Y, position: 11.0 }
  # Produces: original + X-mirror + Y-mirror + XY-mirror
```

### BOM Model

```
BUILDING_DX_STD (BUILDING)
  ├── ... shared structural (MAKE/LEAF children) ...
  └── DUPLEX_SET_STD (has children → recurses, category=PR)
        ├── UNIT_A → DUPLEX_SINGLE_UNIT_STD (LEAF, rot=0,   dx=huA_offset)
        └── UNIT_B → DUPLEX_SINGLE_UNIT_STD (LEAF, rot=π,   dx=huB_offset)
              Both reference the SAME half-unit BOM.
              Walker recurses into it. Rotation applied to child offsets.
```

The pair container (`DUPLEX_SET_STD`) is a **SET** BOM with two LEAF
children pointing to the same half-unit BOM ID. The walker recurses
into the half-unit for each child, applying the rotation_rule from
the BOM line. Same BOM, different placement = mirror.

### Code Design (Abstract)

```java
// CompositionBomBuilder — generic, axis-agnostic
interface MirrorPartitioner {
    /** Classify element: A_SIDE, B_SIDE, or SHARED */
    Side classify(ExtractionElement e);
}

// PlaneMirrorPartitioner — partition by plane perpendicular to axis
class PlaneMirrorPartitioner implements MirrorPartitioner {
    final String axis;       // "X", "Y", or "Z"
    final double position;   // mirror plane position

    Side classify(ExtractionElement e) {
        double eMin = axisMin(e, axis);  // e.minX, e.minY, or e.minZ
        double eMax = axisMax(e, axis);
        if (eMin < position && eMax > position) return SHARED;  // spans
        if (eMax <= position) return A_SIDE;
        return B_SIDE;
    }
}
```

Then the pairing (per product per storey, min(A,B) = paired,
excess → shared) is completely independent of the axis.

## Recompilation (S100-p85 Fleet Audit, 2026-03-28)

BOM walk recompilation via `CompileStage` → `writeFromBomWalk()`. **6/7 PASS, C9 WARN (89 axis mismatches — rank-match artifact).**

| Metric | Value |
|--------|-------|
| Elements | 1099 |
| Root BOM | BUILDING_DX_STD, origin=(-0.044, -35.392, -1.250) |
| Verbs | 992 PLACE, 17 CLUSTER |
| Disciplines | ARC=1011, STR=2 |
| LOD binding | 1099 LOD, 0 fallback, 0 missing |
| H6 WARNs | 57 (MEP schedule vs actual, 13 rooms) |

**LMP Drift Check:** 6 pass, 0 fail, 2 deferred

| § | Check | Verdict |
|---|-------|---------|
| §1 | Input=Output | PASS (1099/1099) |
| §2 | LOD400 | PASS (1099/1099, 0 warn, 0 fail) |
| §3 | Compiler Only | PASS |
| §6 | Output Path | PASS |
| §7 | Separate From Input | PASS |
| §8 | Visual Fidelity | PASS (geometry OK) |
| §4, §9 | Openings, Orientation | deferred (no proof aggregate) |

**C9 WARN (89 mismatches):** Same rank-match artifact documented in §Resolved #5 below. Not a geometry error — position-sorted rank matching shuffles elements near the party wall. Element count is exact (1099=1099).

## Resolved Issues

### 1. Element Count Gap: 1093 vs 1099 — FIXED (2026-03-14)

Pipeline produced 1093 elements instead of expected 1099. Gap = 6 elements.

**Root cause:** `CompositionBomBuilder` excluded ALL B-side elements (491),
but only 485 had A-side mirror partners. The 6 B-side excess elements were
excluded from structural BOMs but had no A-side counterpart to be mirrored from.

**Fix:** Changed B-side exclusion loop to only exclude paired B elements
(`for i < paired` instead of iterating all B). B-side excess now flows to
structural as shared, matching A-side excess behavior.

Result: 485×2 + 129 structural = **1099 ✓** (enbloc=walkthru, delta=0).

### 2. GUID Uniqueness — FIXED (2026-03-14)

- `PlacementCollectorVisitor`: unit prefix stack ("A_", "B_") prepended to
  `elementRef` and auto-incrementing ordinal for mirrored elements.
- `BuildingWriter`: GUID suffix ("_A", "_B") based on elementRef prefix.

### 3. SH Regression — VERIFIED (2026-03-14)

SH 7/7 PASS, 55 enbloc=walkthru, 0 delta, 0 geometry divergences.

### 4. Walker Rotation — VERIFIED (2026-03-14)

DX full delta test: 13 IFC classes, all enbloc=walkthru counts match,
0 geometry divergences, Rule 8 PASS (all coordinates parent-relative).

### 5. C9 Axis Dimension — Matching Artifact (Not a Geometry Error)

C9 reports 89 wall/slab axis mismatches. Root cause: C9 matches elements by
position-sorted rank (`ROW_NUMBER` partitioned by `ifc_class`), not by GUID.
For mirrored buildings, elements near the party wall (X ≈ 4.4) have similar
positions, causing rank shuffles that pair different element types together.

Evidence: element counts match exactly (1099 ref = 1099 out), walker rotation
verified (§Resolved #4: 0 divergences), and the "mismatched" pairs show different
element names (e.g., ref `Exterior Brick` vs output `Interior Partition`).

**Status:** Non-issue. C9 matching needs GUID-based pairing to work correctly
for MIRRORED_PAIR buildings. Filed as future enhancement — does not affect
compilation correctness.

## Remaining

### Scope Space Origins for DX

The DX `floor_rooms` section has scope boxes without `origin_m` coordinates.
These need proper values for scope assignment to work (furniture elements
assigned to rooms). Currently all scope spaces are empty SETs.
This is a data completeness issue, not a pipeline bug.
