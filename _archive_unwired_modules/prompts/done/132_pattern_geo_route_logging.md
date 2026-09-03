# DONE — [d518e326](https://github.com/red1oon/BIMCompiler/commit/d518e326)

**Spec:** DISC_VALIDATION_DB_SRS §10.4.10, EYES_SRS §4.7, LMP §7
**Prereq:** P123 DONE (GEO TACK logging live), P130 DONE (fleet GEO data)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Logging only — no behavior changes.

## Read first

1. `PROGRESS.md` §Current State
2. `orm-core/src/main/java/com/bim/orm/BIMLogger.java`
   - Lines 46-54: GEO channel pattern (`bim.geo.debug`, `bim.geo.filter`)
   - Lines 191-211: `geo()` and `geoMatch()` methods — replicate this pattern
3. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java`
   - Lines 460-505: `expandVerb()`, `expandTile()`, `expandRoute()`, etc.
   - Lines 399-417: LEAF/CHAIN/DIMS/CONTAIN log points (existing GEO TACK)
4. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/CrawlRouter.java`
   - Lines 72-85: existing FINE logging (start/done)
   - CrawlOp implementations: FollowOp, BendOp, BranchOp, ReduceOp, PenetrateOp
5. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java`
   - Lines 185-204: container resolution (auto-discover vs YAML)
6. `IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java`
   - Lines 124-128: storey iteration and element assignment

## Problem

Two blind spots in the white-box logging:

1. **Storey assignment** — Elements get assigned to storeys during IFCtoBOM
   extraction, but there's no logging of WHY an element landed on a particular
   storey. The auto-discovery sorts by minZ, but we can't see the Z-bands or
   container ordering in the log. This matters for debugging multi-storey drift
   (P131 finding: IN 11,970mm, CE 14,493mm).

2. **MEP route segments** — CrawlRouter computes pipe/duct/cable segment
   positions, but only logs start/done summaries at FINE level. There's no
   GEO-equivalent per-segment proof like we have for structural LEAFs
   (TACK LEAF/CHAIN/DIMS/CONTAIN). Routes are blind — no white-box evidence
   for MEP placement positions.

## Fix — Two New Channels

### Channel 1: PATTERN (IFCtoBOM extraction logging)

Add to `BIMLogger.java` following the existing GEO pattern:

```java
// PATTERN debug mode — storey/container assignment logging
private static final boolean PATTERN_ENABLED =
    "true".equalsIgnoreCase(System.getProperty("bim.pattern.debug",
        readBimProperty("bim.pattern.debug", "false")));

public static void pattern(String component, String format, Object... args) {
    if (!PATTERN_ENABLED) return;
    // Same file-only output as geo()
}
```

Log points in IFCtoBOM:

**A. Container Z-ordering** — In `SpatialContainerConfig.discover()`:
```java
BIMLogger.pattern("STOREY", "Container '{}': minZ={:.3f}m, {} elements, seq={}",
    name, minZ, elementCount, seq);
```

**B. Element-to-storey assignment** — In `ExtractionPopulator` where elements
are grouped by storey:
```java
BIMLogger.pattern("ASSIGN", "Element {} (minZ={:.3f}m) → storey '{}'",
    elementRef, minZ, storeyName);
```

**C. Storey processing** — In `StructuralBomBuilder` storey loop:
```java
BIMLogger.pattern("FLOOR", "Storey '{}' (code={}): {} elements, fMinZ={:.3f}m, makeDz={:.3f}m",
    storeyName, code, elemCount, fMinZ, makeDz);
```

Controlled by: `-Dbim.pattern.debug=true`

### Channel 2: GEO-ROUTE (compilation-side MEP logging)

Use the existing `geo()` method with component tag `"ROUTE"` (no new channel
needed — GEO mode already exists, just add ROUTE log points).

Log points in CrawlRouter/CrawlOps:

**A. Per-segment placement** — After each FollowOp creates a segment:
```java
BIMLogger.geo("ROUTE", "SEGMENT {}: start=({:.0f},{:.0f},{:.0f}) end=({:.0f},{:.0f},{:.0f}) len={:.0f}mm dia={:.0f}mm",
    product, startX, startY, startZ, endX, endY, endZ, lengthMm, diameterMm);
```

**B. Fitting placement** — After each BendOp/BranchOp/ReduceOp places a fitting:
```java
BIMLogger.geo("ROUTE", "FITTING {}: pos=({:.0f},{:.0f},{:.0f}) type={} angle={:.0f}deg",
    product, x, y, z, fittingType, angleDeg);
```

**C. Route summary** — At end of CrawlRouter.execute():
```java
BIMLogger.geo("ROUTE", "DONE {}: {} segments, {} fittings, {} edges, total={:.0f}mm",
    product, segments, fittings, edges, totalLengthMm);
```

**D. Penetration** — After PenetrateOp:
```java
BIMLogger.geo("ROUTE", "PENETRATE {}: through={} at=({:.0f},{:.0f},{:.0f})",
    product, wallType, x, y, z);
```

Controlled by: `-Dbim.geo.debug=true` (same as existing GEO TACK)

## Gate

Run SH with PATTERN enabled:
```bash
rm library/SH_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml 2>&1 | tee /tmp/sh_pattern.log
```
- SH 7/7 PASS (zero regression)
- Log contains PATTERN STOREY lines with container Z-ordering
- Log contains PATTERN FLOOR lines with fMinZ and makeDz values

Run SH with GEO enabled (for ROUTE logging):
```bash
rm library/SH_BOM.db
java -Dbim.geo.debug=true -Dbim.log.level=FINE ... classify_sh.yaml
```
- Log contains GEO ROUTE SEGMENT lines with coordinates
- Verify: segment start/end positions match expected wall/ceiling paths

Run IN (multi-storey verification):
```bash
rm library/IN_BOM.db
java -Dbim.pattern.debug=true ... classify_in.yaml 2>&1 | tee /tmp/in_pattern.log
```
- PATTERN log shows storey Z-ordering for all IN storeys
- Container minZ values are monotonically increasing
- Element counts per storey sum to total extraction count

## What NOT to do

- Do NOT change any behavior — logging only
- Do NOT modify PlacementCollectorVisitor GEO TACK log points (those are proven)
- Do NOT modify VerbDetector or VerbFactorizer
- Do NOT modify existing migration files
- Do NOT add logging that fires at INFO level (PATTERN and GEO are debug-only)
- Do NOT log element-level detail at PATTERN level for large buildings (use
  summary per storey, not per element — IN has 699 elements)

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — PATTERN logging channel
// EYES_SRS §4.7 — GEO-ROUTE proof coverage for MEP segments
```

## Commit

```bash
git add orm-core/src/main/java/com/bim/orm/BIMLogger.java \
        DAGCompiler/src/main/java/com/bim/compiler/util/BIMLogger.java \
        BIM_COBOL/src/main/java/com/bim/cobol/geometry/CrawlRouter.java \
        BIM_COBOL/src/main/java/com/bim/cobol/geometry/CrawlOp.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java \
        PROGRESS.md
git commit -m "[S101-p132] PATTERN + GEO-ROUTE logging: storey assignment + MEP segment white-box"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- PATTERN log sample (SH: how many STOREY lines, what Z values?)
- GEO-ROUTE log sample (SH: how many SEGMENT/FITTING lines?)
- IN PATTERN: storey count, Z-band ordering, element distribution
- Any surprises in the Z-ordering or segment positions

---
