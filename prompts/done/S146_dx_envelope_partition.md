# S146 — DX Envelope Walls: Building BOM, Not Half-Unit

**Spec:** `docs/BOMBasedCompilation.md` §4 (tack convention)
**Analysis:** `docs/DuplexAnalysis.md` §S145 Learning Points
**Prior work:** S145 (rot=π fix, proximity pairing, diagnostic logging)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Log-First Debugging Rule

Do not rely on judgement. Improve FINE/GEO logging to reveal the issue, run the
pipeline, then read the logs. No invention — extract insights from automated log
output only. The pipeline log (`logs/pipeline_*.log`) is the single source of truth.
If the logs don't show what you need, improve the logging first.

## Current State

S145 fixed the rot=π transform (both X+Y negated) and proximity pairing. 55/55
A/B pairs have zero symmetry drift. DX 8/8, SH 8/8.

**Remaining issue:** 3 exterior NS walls (Exterior Brick on Block, 417mm thick)
are classified as B-side paired and go through rot=π. They're envelope walls —
they should be at the BUILDING BOM level, not in the half-unit. The rot=π shifts
them 208mm (half thickness) in Y. This can make hosted openings appear uncovered.

## The Fix

**Principle:** envelope walls belong to the building, not to a unit. The party
wall is already SHARED (spans mirror). Exterior walls don't span it but they're
still building-level infrastructure. Let the envelope be the root BOM's concern.

### Partition: envelope detection in CompositionBomBuilder

In `CompositionBomBuilder.java`, after classifying elements into A/B/SHARED,
add an envelope check: a wall whose depth (extent on the building's long axis)
covers >80% of the building depth is envelope — move it from A-side/B-side to
SHARED regardless of which side its AABB falls on.

This keeps the fix in the partitioner. No walker changes, no post-processor.

### Verify via logging

After the fix, the pipeline log must show:
- The 3 exterior walls in SHARED, not in half-unit
- `LMP-DIFF` SpatialDiff with reduced drift on those walls
- `LASTINCH` symmetry audit still 55/55 pairs zero drift (or fewer pairs if
  exterior walls moved out — that's expected)
- TACK LEAF lines: no MIRROR:X on envelope walls

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `CompositionBomBuilder.java` lines 96-182 (partition + pairing)
3. `docs/DuplexAnalysis.md` §S145 Learning Points
4. Pipeline log from latest DX run: `grep LASTINCH logs/pipeline_DX*.log`
5. Run `./scripts/run_RosettaStones.sh classify_dx.yaml`

## Gate

- DX: 8/8 PASS (must not regress)
- SH: 8/8 PASS (no regression)
- 3 exterior walls: zero Y drift (moved to BUILDING BOM)
- Remaining A/B pairs: zero symmetry drift (must not regress)

## BOM Architecture (target)

```
BUILDING_DX_STD (BUILDING, root)
  ├── WALL_NS_417x3100 (exterior west)     ← SHARED: envelope
  ├── WALL_NS_417x2900 (exterior west L2)  ← SHARED: envelope
  ├── WALL_NS_417x609  (exterior west top) ← SHARED: envelope
  ├── WALL_NS_417x1250 (exterior west GF)  ← SHARED: envelope
  ├── ... (same for east side)
  ├── Party wall, slabs, roof               ← SHARED: spanning
  ├── DX_ROOM_L1, DX_ROOM_L2               ← scope/furniture
  └── DUPLEX_SET_STD (pair container)
        ├── UNIT_A → DUPLEX_SINGLE_UNIT_STD  rot=0
        └── UNIT_B → DUPLEX_SINGLE_UNIT_STD  MIRROR:X (rot=π)
              Interior walls, doors, windows, coverings, stairs only
```
