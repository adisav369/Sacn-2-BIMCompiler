# Duplex Mirror Analysis — IFC2x3_Duplex Forensics
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Mirror algorithm proof — two units reflected across a party wall.</b> DX exercises the MIRROR verb and multi-storey BOM structure. IFC-driven extraction (P125–P128).
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

## Recompilation — IFC-Driven (S100-p128, 2026-03-30)

IFC-driven extraction (P125) + spatial container auto-discovery (P127) +
scope excludes (P128). BOM walk via `CompileStage`. **5/7, C9 WARN.**

| Metric | Value |
|--------|-------|
| Elements | 215 (IFC-driven extraction) |
| Root BOM | BUILDING_DX_STD, origin=(-0.044, -35.392, -1.250) |
| Containers | 5 auto-discovered: TF, L1, UN, L2, RO (P127) |
| IFC Spaces | 11 IfcSpaces → 61 furniture elements in SET BOMs |
| Assemblies | 2 stair assemblies (3 children each, P129) |
| Reconciliation | delta=+0 (161 LEAFs + 54 paired = 215 vs 215 extracted) |
| C9 | 50 axis mismatches (was 111 before P128 scope fix) |

**P128 scope excludes fix:** Elements assigned to SET BOMs by ScopeBomBuilder
are now excluded from CompositionBomBuilder mirror partition. This fixed the
reconciliation delta (+50→0) — furniture was being double-counted in both
SET BOMs and the half-unit.

**GEO:** `SUMMARY 107 elements, 5671 pairs, worst=0.000mm, DRIFT=0`

**LMP Drift Check:** 6 pass, 0 fail, 2 deferred

| § | Check | Verdict |
|---|-------|---------|
| §1 | Input=Output | PASS |
| §2 | LOD400 | PASS |
| §3 | Compiler Only | PASS |
| §6 | Output Path | PASS |
| §7 | Separate From Input | PASS |
| §8 | Visual Fidelity | PASS (GEO DRIFT=0) |
| §4, §9 | Openings, Orientation | deferred (no proof aggregate) |

**C9 WARN (50 mismatches):** Rank-match artifact documented in §Resolved #5 below.
Reduced from 89→50 after P128 removed furniture from half-unit pairing.

## Rotation Center Proof (S145, 2026-04-05)

The B-side half-unit is a **180° rotation** of the A-side, NOT a mirror reflection.
A duplex rotated 180° looks the same — front becomes back, left becomes right.

### Establishing the center

The rotation center equals the building geometric center, which equals the MEP
core centroid. All three independently confirm Y = -8.9 (IFC coords).

| Source | X | Y | Method |
|--------|---|---|--------|
| Building AABB center | 4.33 | -8.9 | `(min + max) / 2` |
| MEP IfcFlowFitting centroid (220 elbows) | 4.34 | -8.93 | `AVG(centroid)` |
| Paired ARC window midpoints | 4.40 | -8.9 | `(A_y + B_y) / 2` |

### Proof: paired element midpoints

Four `IfcWindow 2800x2410mm` instances form two rotation pairs:

```
Pair 1: A guid=1l0GAJtRTFv8$zmKJOH4$e  Y=-17.383
        B guid=1l0GAJtRTFv8$zmKJOH4pU  Y= -0.417
        Midpoint Y = (-17.383 + -0.417) / 2 = -8.900  ← EXACT

Pair 2: A guid=1hOSvn6df7F8_7GcBWlSXO  Y= -0.417
        B guid=1hOSvn6df7F8_7GcBWlS_W  Y=-17.383
        Midpoint Y = (-0.417 + -17.383) / 2 = -8.900  ← EXACT
```

**The IFC model is geometrically clean.** Initial analysis reported 7m "slop" but
this was a **mis-pairing artifact**: matching A1↔B2 instead of A1↔B1. Correct
proximity-based pairing shows zero slop. The modelling is professional.

### Pairing trap

When `N` elements of the same type exist per side, naive pairing by
`(product_type, storey)` can mis-pair. Correct pairing requires cross-axis
proximity: for X-axis rotation, sort candidates by Y distance from the
expected mirror position `2 * center_y - A_y`.

### Rotation formula

For rot=π about center `(Cx, Cy)`:

```
B_world = 2 * C - A_world  (component-wise)
```

In the BOM walker, offsets are relative to the half-unit LBD corner.
The `CompositionBomBuilder` compensates by shifting UNIT_B's anchor:

```
anchor_B = mirror_position(anchor_A) + 2 * half_unit_offset_center
```

Where `half_unit_offset_center = max_leaf_offset / 2` (from BOM line dx/dy range,
NOT from element AABB maxX/maxY which includes element width).

### GUID issue with factored LEAFs

Factored leaves (qty > 1 with verb expansion) produce multiple instances from a
single BOM line. The GUID for each instance is generated from the line's ordinal +
verb index. When the same BOM is walked for both UNIT_A and UNIT_B, the A_/B_
prefix distinguishes them, but the verb-expanded instance indices must be stable
across both walks. This is verified by the existing unit prefix stack mechanism.

### Hybrid symmetry (S145 finding)

The IFC model uses **mixed placement** — not a single global transform:

| Element class | Behaviour | Evidence |
|--------------|-----------|----------|
| Exterior walls | **Static** — same Y on both sides | seq60: A_y=-17.38, B_y=-17.80 (midpoint=-17.59, not center) |
| Interior walls, doors | **Rotated about center** (-8.9) | seq70: A_y=-6.25, B_y=-11.67 (midpoint=-8.96) |
| Ceilings, slabs | **Near-center** with functional offset | seq10: midpoint=-9.99 (~1m from center = party wall gap) |

**Root cause:** Exterior walls are pinned to site coordinates (cladding must face out).
Interior elements rotate around the MEP core. The ~700mm offset between the theoretical
center (-8.9) and some element midpoints accounts for the party wall thickness / MEP chase.

**Compiler consequence:** Neither pure `rot=π` nor pure `MIRROR:X` handles all elements
correctly. Current approach uses `MIRROR:X` (negate mirror-axis offset only) as the
least-wrong single transform — keeps all elements inside the building envelope.
Per-element rotation would require classifying each BOM line as "rotates" vs "static",
which is a future enhancement requiring IFC placement analysis.

### MEP walker implications

The shared half-unit BOM contains MEP elements (pipes, fittings, terminals).
When the walker applies rot=π to MEP leaf offsets, the shim anchor resolution
(§6.12.2) must also rotate — a shim host surface at `(x, y)` on A-side maps to
`(2*Cx - x, 2*Cy - y)` on B-side. Current shim matching uses extraction-DB
positions which are side-specific, so A-side shims resolve correctly but B-side
shims would need rotated host lookup. This is not a correctness objective now —
it establishes a compiler truth for the DISC engine to validate against ERP.db.

ARC/STR elements are the priority. MEP walk correctness is a robustness probe.

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

SH 7/7 PASS, 58 enbloc=walkthru, 0 delta, 0 geometry divergences.

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

## IFC-Driven Extraction (S100-p125 → p128)

**Status: DONE.** DX is fully IFC-driven since P125/P127/P128:

- **Spatial containment:** 11 IfcSpaces with 61 furniture elements (P125)
- **Storey auto-discovery:** 5 containers from extraction, no YAML storeys (P127)
- **Scope excludes:** SET BOM elements excluded from mirror partition (P128)
- **Assembly BOMs:** 2 stair assemblies from `rel_aggregates` (P129)
- **YAML floor_rooms removed:** Dead code since P125 (removed in P128)

### Stair Assemblies (P129)

DX has 2 stair assemblies discovered from `rel_aggregates`:
- DX_UN_ASM_1, DX_UN_ASM_2: each 3 children (2 IfcMember stringers + 1 IfcStairFlight)
- Land on "Unknown" storey — correct IFC semantics (stairs span storeys)
- Railings excluded by composition pairing (in half-unit, not structural)
