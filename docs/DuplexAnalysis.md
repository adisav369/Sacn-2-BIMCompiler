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

## BOM Compilation Model (S147, 2026-04-05)

The duplex is compiled entirely from ERP BOM primitives — `m_bom` + `m_bom_line`
(iDempiere Bill of Materials). No intermediate geometry representation. The BOM
IS the spatial model.

```
BUILDING_DX_STD (BUILDING)                    ← the order: "build this duplex"
 ├─ DX_TF_STR (FLOOR)      14 children       ← foundation: footings, ground slab
 ├─ DX_L1_STR (FLOOR)      28 children       ← Level 1 structure: walls, slabs, beams
 ├─ DX_L2_STR (FLOOR)       5 children       ← Level 2 structure
 ├─ DX_RO_STR (FLOOR)       4 children       ← Roof structure
 ├─ DX_ROOM_L1 (FLOOR)      5 rooms          ← Room index: points to SET BOMs
 │   ├─ DX_A102_SET (SET)   5 → 5 instances  ← A-side living: sofa, tables, lamp
 │   ├─ DX_A103_SET (SET)   5 → 15 instances ← A-side kitchen: 8 base + 4 upper cabinets + counter
 │   ├─ DX_A104_SET (SET)   1 → 1 instance   ← A-side pantry: vanity cabinet
 │   ├─ DX_B102_SET (SET)   5 → 5 instances  ← B-side living (rot=π of A)
 │   └─ DX_B103_SET (SET)   5 → 15 instances ← B-side kitchen (rot=π of A)
 ├─ DX_ROOM_L2 (FLOOR)      6 rooms          ← Level 2 rooms: bedrooms, bathroom
 ├─ DUPLEX_SINGLE_UNIT_STD (FLOOR) 51 children ← half-unit: all per-unit elements
 │   └─ DUPLEX_SET_STD (SET) 2 → 2 instances ← stair assembly (stringers + railings)
 ├─ FLOOR_SLAB_GF (ASSEMBLY)                  ← ground floor slab (empty stub)
 ├─ FLOOR_SLAB_L2 (ASSEMBLY)                  ← Level 2 slab (empty stub)
 └─ ROOF_ASSEMBLY (ASSEMBLY)                   ← roof (empty stub)
```

**25 BOMs, 169 lines, 189 instances.** The gap between lines and instances is
the verb expansion — `LINE:X:0.012,1.772,2.772,3.772` (4 explicit positions
for 4 upper cabinets) and `LINE_MULTI:X:0.76,1.76,2.76,3.76;X:0.00,1.00,2.00,3.76`
(8 base cabinets in two rows). VerbFactorizer compresses 15 elements into 5 lines.

**Half-unit walk:** `DUPLEX_SINGLE_UNIT_STD` is walked twice — once with
`IDENTITY` (A-side) and once with `MIRROR:X` (B-side, rot=π). All 51 children
inherit the transform. The B-side kitchen cabinets, stair, walls all get their
positions from the same BOM data — only the sign flips.

**Discipline separation:** 904 MEP elements (IfcFlowSegment, IfcFlowTerminal,
IfcFlowFitting, IfcFlowController) excluded from BOM by §6.12.1. Confirmed by
`SPATIAL-REPORT: missing_summary: disc_excluded=904 not_in_bom=0`. These belong
in the DISC path (IFCtoERP), not the ARC BOM.

**Logging proof:** `grep BOM-SUMMARY logs/*ifctobom*.log` shows this tree.
`grep SPATIAL-REPORT logs/pipeline_DX*.log` shows 0 wall outliers, 15 furniture
GUID-order cosmetic mismatches. `grep LOD-ROTATE logs/pipeline_DX*.log` confirms
51 B-side meshes rotated 180°.

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

## S145 Learning Points — Mirror vs Rotation (2026-04-05)

### 1. Duplex is rot=π, not mirror

MIRROR:X (negate X only) was wrong. The IFC model rotates the B-side 180°
about the party wall axis — this negates **both** X and Y offsets. For AABBs,
rot=π about center (cx, cy) is equivalent to mirror X about cx + mirror Y
about cy. A pure axis mirror keeps the cross-axis unchanged, which placed
interior elements at wrong Y positions (up to 17m drift on elements far from
building center Y).

**Fix:** Walker negates both X and Y offsets under MIRROR:X. BOM builder
reflects UNIT_B anchor on both axes (X about party wall, Y about building
center). Half-extent signs flip on both axes for centroid computation.

### 2. Proximity pairing eliminates mis-pairing

Sort-and-zip pairing (sort A and B by cross-axis, zip by index) fails when
multiple elements of the same product cluster at different positions. Example:
3 instances of WALL_INT_EW_124x2900, sorted by Y — A[0]↔B[0] was correct
but A[1]↔B[2] and A[2]↔B[1] were swapped, causing 1.6–6m GUID drift.

**Fix:** `ProximityMirrorPairer` computes each A element's expected B
position under rot=π, then greedily assigns the nearest unmatched B element.
Interface `MirrorPairer` keeps the algorithm pluggable.

**Result:** 15/18 B walls at zero drift. 4 thin walls at 15mm (cluster
rounding). The 3 exterior walls at 208mm were resolved in S146 — moved
to SHARED (building BOM), not half-unit. See §3 below.

### 3. Envelope walls are building infrastructure, not half-unit (S146)

The IFC model uses three placement strategies:
- **Static envelope:** exterior walls at same Y both sides (don't rotate)
- **Rotation about center:** interior elements rotate 180°
- **Spanning:** party wall, EW walls, slabs — shared across both units

**S145 residual (208mm on 3 walls) was a partition error, not an irreducible
residual.** Envelope NS walls (417mm thick, spanning >80% of the building
wall footprint) were incorrectly classified as A/B-side and paired into the
half-unit. Under rot=π they shifted by half their thickness.

**Fix (S146):** `CompositionBomBuilder` now detects envelope walls after
Tier 1 classification: any IfcWall whose cross-axis extent exceeds 80% of
the spanning walls' footprint is reclassified A/B → SHARED. Result: 8
envelope walls (4 products × 2 sides) moved to building BOM with IDENTITY
placement. Zero drift. DX 8/8, SH 8/8.

**⚠ WARNING — Historical regression pattern:**
The shared + 2 half-units architecture was established in `514ee302` (DX-1,
2026-03-14) but **never had envelope detection**. The original 3-tier
partition only checked if an element's AABB *spans* the mirror line.
Envelope NS walls sit entirely on one side (X=[0,0.417] or X=[8.383,8.800])
so they were always classified A/B and paired. The BOM model was too large —
not abstract enough, not cascade-aware. Each session peeled another layer:

| Session | What broke | Root cause |
|---------|-----------|------------|
| DX-1 `514ee302` | B-side excess excluded | Only paired B should be excluded |
| S138 `e40e705a` | B-side rooms not under pair container | Rotation didn't cascade |
| S142 `a14e5f6f` | MEP in composition BOM | MEP belongs to DISC path (IFCtoERP) |
| S145 `bf6cb1ee` | MIRROR:X was wrong | Duplex is rot=π (negate both axes) |
| S146 (this) | Envelope walls in half-unit | Site-pinned walls don't rotate |

The pattern: every fix assumed one uniform transform for all elements.
The partition must separate *what rotates* from *what doesn't* before
applying any transform.

### 4. Rotation confirmed: indistinguishable sides (S146)

The rot=π reconstruction produces output that is **geometrically identical**
when the building is turned 180°. Stairs, stringers, railings, stairwell
walls, and adjacent rooms all land at the correct positions. The two sides
are indistinguishable — you cannot tell which side you are looking at.

This is the correct result: a duplex rotated 180° IS the same building.
The IFC may use different meshes per side (different `geometry_hash` for
A vs B stair flights), but the compiler reconstructs from BOM + rotation,
not from IFC meshes. The AABB positions and midpoints confirm exact
symmetry about center (4.4, -8.9).

**Log evidence (TACK output):**
- Stair flight midpoint: X=4.400, Y=-8.900 — exact rotation center
- Stairwell wall midpoint: X=4.400, Y=-8.900 — exact
- A/B stair extents: 3.805m each — identical
- All stair assembly elements correctly placed under MIRROR:X

**Lesson:** Do not use visual inference. The logs are the proof.

### 5. Log-first debugging

The mis-pairing was invisible to gate tests (8/8 PASS) and envelope checks
(all inside building AABB). Only element-by-element LEAF logging with actual
AABB coordinates (`X=[min,max] Y=[min,max]`) and comparison to IFC reference
revealed the 6m drift. The improved TACK log now shows:
- Transform state: `IDENTITY` vs `MIRROR:X`
- Half-extent sign: `sign=+1` or `sign=-1`
- Actual AABB: `X=[min,max] Y=[min,max] Z=[min,max]`
- Mirror-aware containment check (eliminated 55 false OVERSHOOTs)

### 6. Zigzag partition axis — rot=π holds for non-straight party walls (S147)

The duplex party wall is not a straight line. Between the stair wells, the
partition zigzags inward on each side to provide stair clearance — a common
duplex design pattern. The A-side notches in at X=4.420 (wall `36_A`), the
B-side notches in at X=5.160 (wall `87_B`).

**Proof:** rot=π about building center (4.790, 13.283) verified:
- A zigzag center: X=4.496, Y=13.253
- B zigzag center: X=5.084, Y=13.313
- (A + B) / 2 = (4.790, 13.283) — exact building center

The rotation axis holds despite the non-straight partition because rot=π is
a global transform about the building center, not about the party wall itself.
Each element is positioned independently via BOM tack offsets; the rotation
negates both X and Y offsets under MIRROR:X (S145 fix). The zigzag walls are
ordinary children of the half-unit BOM — no special-case geometry.

**Code path:** `PlacementCollectorVisitor.onLeaf()` reads `mirrorAxisStack`,
negates offsets via `xySign = -1.0`. `CompositionBomBuilder.buildComposition()`
classifies walls as A/B/SHARED. Zigzag walls are correctly classified A/B
(they sit entirely on one side of the building center X).

**LOD rotation (S147):** B-side LOD meshes now receive rot=π via
`MeshBinder.bind()` (rotate around mesh center, then translate to AABB).
Confirmed by `LOD-ROTATE` log: 51 B-side elements at `rotZ=3.1416rad`.
Stair flight, stringers, railings all rotated — visual orientation correct.

**Logging:** `SPATIAL-REPORT` (grep pipeline log) confirms 0 wall outliers
after identity-based matching. `BOM-SUMMARY` (grep IFCtoBOM log) shows the
BOM tree structure including the half-unit's 51 children.

## S150/S151 Generative MEP Placement

**What it does:** The walker reads `ad_space_type_mep_bom` + `ad_placement_offset`
and synthesizes MEP devices (sprinklers, outlets, fridge, etc.) in rooms with
matching capabilities. Devices are NOT from the original IFC — they are compiled
from building code rules. Output has 329 elements (215 extracted + 114 generative).

**Room classification (S151):** `ScopeBomBuilder.inferRoleFromContent()` classifies
rooms from furniture names when IfcSpace name is a room number (A102, B203):
cabinet+counter→KITCHEN, vanity→BATHROOM, bed→BEDROOM, sofa→LIVING.

**Generative device counts:** 20 OUTLET, 15 SWITCH, 13 SPRINKLER, 11 LIGHT,
8 CEILING_FAN, 8 SUPPLY_DIFFUSER, 7 OUTLET_GFCI, 6 AIRCON_POINT, 6 OUTLET_20A,
5 EXHAUST_FAN, 5 FLOOR_TRAP, 5 SINK, 3 TOILET, 2 FRIDGE.

### Logging Channels

| Channel | Grep pattern | What it shows |
|---------|-------------|---------------|
| GENERATIVE ROOM | `GENERATIVE.*ROOM` | Per-room: BOM ID, space type, anchor, AABB, device count |
| GENERATIVE PLACE | `GENERATIVE.*PLACE` | Per-device: rule, anchor, discipline, position, IN/OUT verdict |
| GENERATIVE BREACH | `GENERATIVE.*BREACH` | Devices outside room AABB — axis-by-axis diagnosis |
| GENERATIVE SUMMARY | `GENERATIVE.*SUMMARY` | Total devices, rooms, breaches |

### Known Findings (S151 — RESOLVED `e09294f8`)

**Finding 1: Z-axis breach — FIXED.** 13/114 breaches → 0. DV044 adds
`default_ceiling_height_mm` to `ad_space_type` (2700mm residential).
`SpaceScheduleDAO.getCeilingHeightM()` reads it; walker overrides `rh` when
bomAABB height < 2400mm. Logged as `GENERATIVE CEILING_OVERRIDE`.

**Finding 2: LOD geometry — FIXED.** `SpaceScheduleDAO.getProductDimensions()`
reads M_Product width/depth/height; walker uses half-extents for Placement AABB.
FRIDGE: 0.70×0.70×1.80m (was 0.10×0.10×0.10m). Logged as `GENERATIVE AABB`.

**Geometry stubs:** Products with no extraction geometry (SWITCH, OUTLET, FRIDGE,
AIRCON_POINT, CEILING_FAN, EXHAUST_FAN, DATA_POINT, EMERGENCY_LIGHT, OUTLET_20A,
OUTLET_GFCI) use box stub `f7051d6c5f17ad77` (8 vertices) in component_library.db.
Products with extraction geometry (SPRINKLER, SINK, TOILET, LIGHT, FLOOR_TRAP,
SUPPLY_DIFFUSER) resolve via LIKE match to real IFC meshes.

**Witness:** MepRouteGeometryTest S15 — ceiling override + product AABB proof.
15/15 PASS. S14: 0 gaps across 27 space types.
