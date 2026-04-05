# DONE
# S145 — DX Mirror Placement: Reflection Not Rotation

**Spec:** `docs/BOMBasedCompilation.md` §4 (tack convention)
**Prior work:** S144 (GEO proof chain, A-side ZERO drift, root cause found)
**Prereq:** S144 committed (`ddd6771e`)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Problem

DX B-side elements are placed outside the building. The shared unit BOM
uses `rotation_rule=π` for B-side. rot=π negates BOTH X and Y offsets.
But mirror on X should negate X only, keeping Y the same.

```
A-side:  anchor=(0.36, 4.38, 0.30) + offset=(2.60, 9.10, 3.88) → Y = 13.48 ✓
B-side:  anchor=(9.21, 4.38, 0.30) + offset=(-2.60, -9.10, 3.88) → Y = -4.72 ✗ OUTSIDE
```

Y should stay positive. The building Y range is 0–22m. B-side goes to -35m.

## Evidence (S144 forensics)

- **A-side: 0.1mm max drift.** ZERO. Compiler is correct for A-side.
- **B-side: 23m worst drift.** 98/110 B-side elements have negative Y.
- **IFC mirror quality:** 95% of elements have a counterpart within 2m.
  Median slop: 600mm. Visually perfect mirror, numerically sloppy modelling.
- **Gates pass** (C8/C9/G3) because they check dimensions, not absolute Y.
  geo_verify.py (GUID-matched) catches it: 19,487 pair drifts.

## The Fix

Mirror = reflection, not rotation. For X-mirror:
- A offset `(dx, dy, dz)` → B offset `(-dx, dy, dz)` (negate X only)

The walker currently does `cos(π)*dx - sin(π)*dy = -dx, sin(π)*dx + cos(π)*dy = -dy`.
That's rotation. We need reflection.

### Approach: Walker MIRROR mode

Add a `MIRROR:X` rotation_rule (alongside existing numeric radians). When the
walker sees `MIRROR:X`, it negates only the X component of the offset.

In `PlacementCollectorVisitor.onLeaf()` lines 320-333 and `onSubAssembly()` lines 204-217:

```java
// Current: rotation
if (cumRot != 0.0) {
    double cos = Math.cos(cumRot);
    double sin = Math.sin(cumRot);
    rx = dx * cos - dy * sin;
    ry = dx * sin + dy * cos;
}

// New: check for MIRROR mode first
if ("MIRROR:X".equals(rotationRule)) {
    rx = -dx;  // negate mirror axis only
    ry = dy;   // cross-axes unchanged
} else if (cumRot != 0.0) { ... existing rotation ... }
```

In `CompositionBomBuilder` line 292, change UNIT_B rotation_rule from `π` to `MIRROR:X`:
```java
insertPairChild(bomConn, pairBomId, halfUnitBomId,
    "UNIT_B", 20, "MIRROR:" + mirror.axis().toUpperCase(),
    unitBDx, unitBDy, unitBDz);
```

### Changes needed

1. `PlacementCollectorVisitor.parseRotation()` — recognize `MIRROR:X/Y/Z`
2. `PlacementCollectorVisitor.onSubAssembly()` — apply reflection in anchor accumulation
3. `PlacementCollectorVisitor.onLeaf()` — apply reflection to leaf offsets
4. `CompositionBomBuilder` — emit `MIRROR:X` instead of `3.14159`

### What NOT to change

- The shared template pattern stays (one BOM, two instances)
- A-side offsets in BOM stay as-is (LBD convention, ZERO drift confirmed)
- The B-side anchor position stays as-is (computed correctly by CompositionBomBuilder)
- `VerbFactorizer`, `ExtractionPopulator` — extraction side untouched

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `PlacementCollectorVisitor.java` lines 204-217 (onSubAssembly rotation)
   and lines 320-333 (onLeaf rotation)
3. `CompositionBomBuilder.java` lines 279-293 (UNIT_B insertion)
4. Run `./scripts/run_RosettaStones.sh classify_dx.yaml` to see current state
5. Run `python3 scripts/geo_verify.py <DX_log> <DX_output> <DX_extracted>` for drift report

## Gate

- DX: 8/8 gates PASS
- geo_verify A-side: ZERO drift (must not regress)
- geo_verify B-side: < 1000mm drift (600mm = IFC modelling slop, not compiler error)
- B-side Y range matches A-side Y range (no negative, no outside building)
- SH: 8/8 PASS, geo_verify ZERO drift (no regression)

## When Done

Prepend `# DONE` to this file. Update PROGRESS.md §S145.
