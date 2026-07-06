# DONE — partial. MIRROR:X implemented, 8/8 gates, but B-side positional drift remains.
# S145 — DX B-Side Placement: Mirror, Rotation, or Hybrid?

**Spec:** `docs/BOMBasedCompilation.md` §4 (tack convention)
**Analysis:** `docs/DuplexAnalysis.md` §Rotation Center Proof, §Hybrid Symmetry
**Prior work:** S144 (GEO proof chain, A-side ZERO drift, B-side root cause found)
**Commit:** `bf6cb1ee` (MIRROR:X + hybrid symmetry documentation)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Current State

MIRROR:X is implemented (`CompositionBomBuilder` emits `MIRROR:X`, walker negates
only X offset). DX 8/8 gates PASS, SH 8/8 PASS. B-side inside building envelope.
But geo_verify shows 13,379 drifts, 31.5m worst — B-side positions are approximate,
not correct. A-side remains ZERO drift.

## The Real Problem: AABB Direction After Transform

**Root cause found in self-analysis of `PlacementCollectorVisitor.java` line 497:**

```java
double cx = anchor[0] + offsets[qi][0] + iHalfW;   // centroid = LBD + half
double cy = anchor[1] + offsets[qi][1] + iHalfD;
double cz = anchor[2] + offsets[qi][2] + iHalfH;
```

The walker ALWAYS adds `+halfW` to compute centroid from LBD. This means the
element AABB extends in the +X direction from the offset point. For A-side this
is correct. For B-side, the element should extend in the -X direction (the LBD
corner becomes the max corner, not the min).

### Proof from IFC data

Beam pair (GUID `2OrWItJ6zAwBNp0OUxK$Dw` / `$CR`):

```
IFC A beam: X = [0.271, 4.371]  LBD at minX = 0.271, extends RIGHT 4.1m
IFC B beam: X = [4.429, 8.529]  LBD at minX = 4.429, extends RIGHT 4.1m

True mirror about X=4.4:
  B_minX = 2*4.4 - A_maxX = 8.8 - 4.371 = 4.429 ✓
  B_maxX = 2*4.4 - A_minX = 8.8 - 0.271 = 8.529 ✓
  (The element FLIPS: A's right edge → B's left edge)

Compiler A output: X = [0.661, 4.761]  ✓ (correct, within building origin shift)
Compiler B output: X = [8.918, 13.018] ✗ (extends 3.6m outside building)

What happened: offset = 0.661, MIRROR:X → offset = -0.661
  B LBD_x = anchor(9.21) + (-0.661) = 8.55
  B centroid = 8.55 + halfW(2.05) = 10.60
  B maxX = 10.60 + 2.05 = 12.65  ← extends RIGHT, should extend LEFT
```

The element should extend LEFT from LBD on B-side (toward the party wall).
The walker unconditionally extends RIGHT (`+halfW`).

## Building Geometry

```
IFC envelope:  X=[-0.39, 8.83]  Y=[-22.18, 4.38]  Z=[-1.55, 6.64]
Mirror plane:  X = 4.400 (party wall center)
Building origin (compiler shifts to positive): (-0.39, -22.18, -1.55)
Compiler envelope: X=[0, 9.22]  Y=[0, 26.57]  Z=[0, 8.19]
```

## Element Census

```
IFC extraction: 1119 elements
  MEP:  904 (81%) — IfcFlowFitting 358, IfcFlowSegment 427, IfcFlowTerminal 105
  ARC:  196 (17%) — IfcWall 57, IfcFurnishingElement 61, IfcWindow 24, IfcSlab 21
  STR:   12 (1%)  — IfcBeam 8, IfcMember 4

Compiler output: 215 elements (ARC 118, STR 97, MEP 0)
  A-side:  55 (from half-unit walk, A_ prefix)
  B-side:  55 (from half-unit walk, B_ prefix)
  Shared: 105 (structural/scope, no prefix)

MEP: 904 extracted → 0 compiled. MEP elements go to EXCESS/SHARED in
partition but are not compiled. No MEP discipline in output.
```

## Mirror Partition

```
Half-unit (A-side paired): 485 leaves in DUPLEX_SINGLE_UNIT_STD
Spanning (crosses mirror):  55
Excess (unmatched):         74 (97% MEP)
SHARED total:              129
Verification: 485 × 2 + 129 = 1099
```

## Hybrid Symmetry — IFC Uses Mixed Transforms

Paired element analysis (IFC coordinates):

```
Exterior wall:  A_y=-17.38  B_y=-17.80  midpoint=-17.59  STATIC (same Y both sides)
Interior wall:  A_y=-6.25   B_y=-11.67  midpoint=-8.96   ROTATED about center -8.9
Ceiling:        A_y=-9.73   B_y=-10.25  midpoint=-9.99   NEAR-CENTER + ~1m offset
Door:           A_y=-2.32   B_y=-16.88  midpoint=-9.60   ROTATED + 0.7m party wall gap
```

The IFC model uses three placement strategies simultaneously:
1. **Static envelope:** exterior walls at same Y (normal must face outward)
2. **Rotation about center:** interior elements rotate 180° about Y=-8.9
3. **Rotation + functional offset:** ~700mm shift for party wall / MEP chase

## Rotation Center Proof

Building center, MEP centroid, and paired element midpoints all independently
confirm the same center:

```
Building AABB center:         (4.33, -8.900)
MEP IfcFlowFitting centroid:  (4.34, -8.926)  from 220 elbows
Paired window midpoints:      (4.40, -8.900)  exact after correct proximity pairing
```

Initial analysis reported 7m "slop" — this was a **mis-pairing artifact**.
4 windows of same type, naive pairing matched A1↔B2 instead of A1↔B1.
Correct proximity-based pairing shows zero slop. IFC modelling is clean.

## BOM Structure

```
BUILDING_DX_STD (BUILDING, origin=(-0.39, -22.18, -1.55))
  ├── DX_L1_STR, DX_L2_STR, DX_RO_STR  (structural storeys)
  ├── DX_ROOM_L1, DX_ROOM_L2            (scope/furniture)
  ├── FLOOR_SLAB_GF, FLOOR_SLAB_L2      (shared slabs)
  └── DUPLEX_SET_STD (PAIR container)
        ├── UNIT_A → DUPLEX_SINGLE_UNIT_STD  rot=0,       dx=0.36, dy=4.38
        └── UNIT_B → DUPLEX_SINGLE_UNIT_STD  MIRROR:X,    dx=9.21, dy=4.38
              Same BOM, 55 leaf lines (ARC/STR only, no MEP)
              Walker recurses, applies transform per rotation_rule

DUPLEX_SINGLE_UNIT_STD leaf offset ranges:
  dx: [0, 4.055]   dy: [0, 17.44]   dz: [0, 7.71]
  Offset center: (2.03, 8.72, 3.85)
```

## Current Output Ranges

```
            count   X range          Y range         Status
A-side       55    [0.37, 4.76]    [4.38, 22.21]    ✓ correct
B-side       55    [5.16, 13.02]   [4.38, 22.21]    ✗ X overshoots by 3.6m
Shared      105    [0.15, 9.43]    [0.00, 26.57]    ✓ correct
Building env  —    [0.00, 9.22]    [0.00, 26.57]    (reference)
```

## What Needs Fixing (Next Session)

### Fix 1: AABB direction under transform
`PlacementCollectorVisitor.java` line 497 unconditionally adds `+halfW` to get
centroid from LBD. Under mirror/rotation, the half-extent on the mirror axis should
be SUBTRACTED (element extends toward party wall, not away from it).

### Fix 2: Per-element transform classification
Three-layer residual classifier:
- Residual ≈ 0 → static envelope (use identity or simple mirror)
- Residual ≈ rotation about center → use rot=π about (4.4, -8.9)
- Residual ≈ rotation + offset → add functional vector for party wall

### Fix 3: MEP compilation
904 MEP elements extracted but 0 compiled. Need to either:
- Include MEP in the half-unit BOM (currently excluded as EXCESS), or
- Compile MEP through the structural/shared path

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `PlacementCollectorVisitor.java` lines 495-500 (centroid = LBD + half)
3. `CompositionBomBuilder.java` lines 279-295 (UNIT_B MIRROR:X)
4. `docs/DuplexAnalysis.md` §Rotation Center Proof, §Hybrid Symmetry
5. Run `./scripts/run_RosettaStones.sh classify_dx.yaml`

## Gate (for next session)

- DX: 8/8 gates PASS (must not regress)
- geo_verify A-side: ZERO drift (must not regress)
- geo_verify B-side: < 1000mm drift (IFC modelling tolerance)
- B-side X range within building envelope (no overshoot)
- SH: 8/8 PASS (no regression)

---

# History — S145 Session Log (2026-04-05)

## Attempt 1: MIRROR:X (negate X only)
Implemented mirrorAxisStack in walker. B-side stays inside building (Y positive).
DX 8/8 PASS. But visual examination shows slabs jutting out on one wall.

## Attempt 2: rot=π + center shift (add 2*center to anchor)
Tried shifting UNIT_B anchor by `2 * halfUnitCenter` so rot=π about origin = rot=π
about center. Failed: B-side overshoots to Y=40m, X=17m. Center calculation used
element AABB (`maxX`) instead of offset range (`max(minX)`).

## Attempt 3: Cross-axis-only center shift
Fixed to shift only cross-axes (Y,Z for X-mirror). B-side Y correct for some elements
but exterior walls (static, same Y both sides) were shifted to wrong Y. Hybrid symmetry
discovered: some elements rotate, some don't.

## Key discoveries:
1. **IFC has zero slop** — initial 7m slop was mis-pairing artifact (4 same-type windows)
2. **Rotation center = building center = MEP centroid** (Y=-8.9, independently confirmed)
3. **Hybrid symmetry** — exterior static, interior rotated, ~700mm party wall offset
4. **AABB direction bug** — walker always extends element in +X from LBD, should flip
   direction under mirror/rotation (line 497: `cx = anchor + offset + halfW`)
5. **MEP absent from output** — 904 extracted, 0 compiled (EXCESS path doesn't compile)
