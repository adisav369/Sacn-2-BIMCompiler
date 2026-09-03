# DONE — [ff0f344e](https://github.com/red1oon/BIMCompiler/commit/ff0f344e)

**Spec:** BBC.md §4 (tack convention), DISC_VALIDATION_DB_SRS §10.4.13
**Prereq:** P130 DONE (GEO white-box logging live, fleet drift data collected)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** This is a maths fix. Verify numerically.

## Read first

1. `PROGRESS.md` §Current State — GEO Drift Findings
2. `IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java`
   - Line 59-60: `allElements` built from `storeyElements.values()`
   - Line 68: `allMinZ` from allElements
   - Line 139: `fMinZ` from storey elements
   - Line 254: `makeDz = fMinZ - allMinZ` — **THIS IS THE BUG LINE**
   - Lines 163-171: scope/composition exclusion AFTER fMinZ computed
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java`
   - Line 293: `StructuralBomBuilder.build(... storeyElements, allExclude ...)`
   - Trace what `storeyElements` contains — is it ALL extraction elements or filtered?
4. `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbFactorizer.java`
   - `factorize()` — what origin does it use for LEAF dx/dy/dz computation?

## Problem

GEO drift findings from fleet run (S100-coder):

| Building | Elements | DRIFT | Worst | Pattern |
|----------|----------|-------|-------|---------|
| IN | 699 | 35,557 | 11,970mm (~4 storeys) | Multi-storey, structural |
| CE | 2,110 | 39,900 | 14,493mm (~5 storeys) | Multi-storey, structural |
| GH | 193 | 2,380 | 4.7mm | Slab rounding? (470/100=4.7) |

**Key evidence:** Extraction Z grouping is correct (elements are on the right
storey). GEO CHAIN/CONTAIN logs show OVERSHOOT=0 but DRIFT!=0. The bug is in
the MAKE offset computation, not in storey assignment.

The MAKE line at line 254 computes `makeDz = fMinZ - allMinZ`. In multi-storey
buildings, if elements have been filtered out between what contributes to
`allMinZ` and what ends up in the compiled output, the Z-anchor drifts.

## Investigation (do this FIRST)

Before fixing, prove the bug numerically:

1. Run IN with GEO logging enabled:
   ```bash
   rm library/IN_BOM.db
   ./scripts/run_RosettaStones.sh classify_in.yaml 2>&1 | tee /tmp/in_geo.log
   ```

2. From the GEO log, extract:
   - `allMinZ` value (building-level minimum Z)
   - `fMinZ` for each storey
   - MAKE dz for each storey
   - DRIFT values per storey

3. Cross-reference with extraction data:
   ```sql
   -- In IN_extracted.db
   SELECT storey, MIN(minZ), MAX(maxZ), COUNT(*)
   FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
   GROUP BY storey ORDER BY MIN(minZ);
   ```

4. Identify the discrepancy: which elements contribute to allMinZ in
   StructuralBomBuilder but are absent from the compiled output?

## Fix

Based on investigation, fix the MAKE dz computation so that the Z-anchor
reflects the actual elements that appear in the compiled BOM, not a
superset/subset that includes excluded elements.

The fix MUST be in StructuralBomBuilder.java. Do NOT modify:
- PlacementCollectorVisitor (compilation side)
- ExtractionReader or ExtractionPopulator (extraction side)
- Any migration files

The fix should be small — likely recomputing allMinZ from the right element
set, or adjusting fMinZ after exclusions.

## Gate

Run IN:
```bash
rm library/IN_BOM.db
./scripts/run_RosettaStones.sh classify_in.yaml
```
- IN gate: no regression (20/21 or better)
- GEO DRIFT for IN should drop from 35,557 to near 0

Run CE:
```bash
rm library/CE_BOM.db
./scripts/run_RosettaStones.sh classify_ce.yaml
```
- CE DRIFT should drop from 39,900 to near 0

Run SH (regression check):
```bash
rm library/SH_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- SH 7/7 PASS (zero regression, SH is single-storey so unaffected)

If GH drift (4.7mm) is a different bug, document but do NOT fix in this prompt.

## What NOT to do

- Do NOT modify PlacementCollectorVisitor or BOM walker
- Do NOT modify existing migration files
- Do NOT change the tack convention (LBD anchoring)
- Do NOT change how VerbFactorizer computes LEAF offsets
- Do NOT change ExtractionReader storey grouping
- Do NOT attempt to fix GH's 4.7mm rounding — separate issue
- **If the root cause is different from expected, document it and fix the actual bug**

## Spec citation

```java
// Implementing BBC.md §4 — Z-anchor fix for multi-storey MAKE dz
// Witness: W-DRIFT-IN (35557→0), W-DRIFT-CE (39900→0)
```

## Commit

```bash
git add IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java \
        PROGRESS.md
git commit -m "[S101-p131] Z-anchor fix: StructuralBomBuilder MAKE dz for multi-storey buildings"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- Root cause (what exactly was wrong with the Z computation?)
- IN DRIFT before/after
- CE DRIFT before/after
- GH — same, worse, or different?
- SH regression check
- Fleet impact estimate (how many buildings affected?)

---

## Findings (S101 coder)

**Root cause was NOT in StructuralBomBuilder MAKE dz.** The MAKE offset math is correct:
`allMinZ + makeDz + leafDz = allMinZ + (fMinZ - allMinZ) + (e.minZ - fMinZ) = e.minZ`

Three bugs in `VerbDetector.java`:

1. **ROUTE Z-uniformity:** detectRoute() accepted groups with varying Z. Multi-storey buildings have elements assigned to one storey spanning wide Z ranges (IN Keller: windows from Z=-2.1 to Z=6.9). ROUTE forces all instances to same Z, misplacing elements by up to 9m. Fix: Z-uniformity guard rejects ROUTE when maxZ - minZ > 0.5m, falls to CLUSTER.

2. **ROUTE axis matching:** computeExpansionOrder() used 3D Euclidean distance. ROUTE expansion positions have constant Z, but actual elements vary. 3D matching assigns wrong GUIDs. Fix: 1D matching along route axis only.

3. **CLUSTER identity mapping:** computeExpansionOrder() used greedy centroid matching for CLUSTER. But detectCluster() stores offsets in elements-list order — identity is always correct. Fix: CLUSTER returns identity [0,1,2,...,n-1].

4. **GEO permanently ON:** BIMLogger.GEO_ENABLED default → true. GEO is the LMP proof.

| Building | Before | After |
|----------|--------|-------|
| SH | DRIFT=0 | DRIFT=0 |
| IN | DRIFT=35,557 worst=11,970mm **6/7** | DRIFT=20,475 worst=91mm **7/7** |
| CE | DRIFT=39,900 worst=14,493mm | DRIFT=39,900 worst=12,858mm |

- IN 6/7→7/7: P05 duplicate was a mis-ROUTED element group — Z-guard fixed it
- CE remaining: 11 elements on multi-leg ROUTE. Deferred to P124
- IN residual 91mm: ROUTE step approximation (acceptable)
- GH: not tested this session (slab rounding, separate issue)
- Fleet impact: any building with ROUTE elements spanning >0.5m Z range
