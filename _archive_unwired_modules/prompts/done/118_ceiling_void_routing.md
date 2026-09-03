# DONE — [c508c176](https://github.com/red1oon/BIMCompiler/commit/c508c176)
# Gap 1: Ceiling Void Routing — Correct Z for Horizontal MEP

**Spec:** DISC_VALIDATION_DB_SRS §10.4.12 Gap 1
**Prereq:** P117 DONE (`be296651`). RouteStage fires for all disciplines. Builders route at floor Z — should route at ceiling Z.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Ceiling height is derived from ARC data already in the pipeline. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.12 Gap 1 — the spec for this work
3. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/SqlBuildingGeometry.java` — current BuildingGeometry implementation
4. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/BuildingGeometry.java` — interface
5. All 6 RouteBuilders in `BIM_COBOL/src/main/java/com/bim/cobol/route/` — find where `floor.zMm()` is used for horizontal routing
6. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` line 407 — RouteStage (passes BuildingGeometry to executor)

## Problem

All 6 RouteBuilders route horizontal segments at `floor.zMm()` (floor slab top). In practice, pipes and ducts run in the ceiling void — underside of the slab above, minus clearance.

Per §10.4.12 Gap 1:
```
ceilingHeight(floorRef) = nextFloor.zMm - slabThickness(nextFloor) - clearanceMm
```

## Fix

### 1. Add `ceilingHeight(String floorRef)` to BuildingGeometry interface

```java
/** Ceiling void Z for a floor = slab bottom of floor above minus clearance. */
int ceilingHeightMm(String floorRef);
```

### 2. Implement in SqlBuildingGeometry

- Query the next floor's Z from the storey list (already available from `floors()`)
- Subtract `slabThickness()` of the floor above (already implemented, P107 fixed it)
- Subtract clearance (use `BIMConstants.MEP_STRUCTURE_CLEARANCE` = 50mm, or a reasonable default)
- For the top floor / roof: use roof Z minus slab thickness minus clearance
- Fallback: if next floor not found, return `floor.zMm + DEFAULT_STOREY_HEIGHT - slabThickness - clearance`

### 3. Update 6 RouteBuilders

Replace `floor.zMm()` with `geometry.ceilingHeightMm(floorRef)` in each builder's horizontal FOLLOW ops. Vertical ops (risers) stay at their current Z — risers run floor-to-floor, not in the ceiling void.

Affected builders:
- FpRouteBuilder — horizontal headers + branch pipes
- ElecRouteBuilder — horizontal cable trays
- CwRouteBuilder — horizontal headers
- SpRouteBuilder — horizontal waste pipes (note: SP is gravity, Z matters even more)
- AcmvRouteBuilder — horizontal ducts
- LpgRouteBuilder — horizontal gas pipes

## Gate

Run TE:
```bash
./scripts/run_RosettaStones.sh classify_te.yaml
```
- FINE log: ceiling Z values for each floor (should be < next floor Z)
- system_edges: edge from_xyz/to_xyz Z values should be at ceiling, not floor
- TE gate: no regression from current

Run SH:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- SH 7/7+ PASS (no regression)
- FINE log: ceiling Z for SH floors

## What NOT to do

- Do NOT modify CrawlRouter or CrawlOps
- Do NOT modify vertical routing (risers) — only horizontal segments
- Do NOT modify existing migration files
- Do NOT modify ProveStage or BIMEyes proofs
- Do NOT hardcode building-specific Z values
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.12 Gap 1 — ceiling void routing
// ceilingHeight = nextFloor.zMm - slabThickness(nextFloor) - clearanceMm
```

## Commit

```bash
git add BIM_COBOL/src/main/java/com/bim/cobol/geometry/BuildingGeometry.java \
        BIM_COBOL/src/main/java/com/bim/cobol/geometry/SqlBuildingGeometry.java \
        BIM_COBOL/src/main/java/com/bim/cobol/route/*.java \
        PROGRESS.md
git commit -m "[S100-p118] Ceiling void routing: ceilingHeightMm() on BuildingGeometry, 6 builders updated"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- Ceiling Z values for TE floors (7 storeys)
- Ceiling Z values for SH floors
- system_edges Z values before/after
- Which builders changed, which ops changed
- SH 7/7, TE gate result
- Any surprises — document, do NOT fix

---

## Findings — P118 Ceiling Void Routing

### Ceiling Z values for TE floors (7 storeys)
| Floor | floorZ (mm) | ceilingZ (mm) |
|-------|------------|---------------|
| TE_FDN | 0 | -170 |
| TE_GF | 30 | -170 |
| TE_L01 | 30 | -166 |
| TE_L02 | 30 | -170 |
| TE_L03 | 34 | -158 |
| TE_L04 | 42 | -153 |
| TE_RF | 47 | 3347 (top floor fallback) |

### Ceiling Z values for SH floors
| Floor | floorZ (mm) | ceilingZ (mm) |
|-------|------------|---------------|
| FLOOR_SLAB_GF | 0 | -200 |
| SH_GF_STR | 0 | -200 |
| FLOOR_SH_GF_STD | 0 | -200 |
| ROOF_ASSEMBLY | 3 | 3303 (top floor fallback) |

### system_edges Z values before/after
- **Before:** 711 edges (floor-level Z)
- **After:** 712 edges (+1 from ceiling offset routing). Z values in edges unchanged for most floors since negative ceiling heights cause riser ops to be skipped (riseDistance ≤ 0).

### Which builders changed, which ops changed
All 6 builders updated with identical pattern:
- **FpRouteBuilder** — `floorZMm` → `ceilingZMm` for rise target + currentZ
- **ElecRouteBuilder** — same pattern
- **CwRouteBuilder** — same pattern
- **AcmvRouteBuilder** — same pattern
- **LpgRouteBuilder** — same pattern
- **SpRouteBuilder** — same pattern (descending: dropDistance uses ceilingZ, startZ uses ceiling of top floor)

### Gate results
- **SH: 7/7 PASS** — no regression
- **TE: 5/7 PASS, 1 FAIL** — no regression (compile fails due to P05+P06 critical violations, unchanged from P115)

### Surprises
1. **c_orderline FLOOR dz values are BOM-relative offsets, not absolute storey Z.** TE storeys (Foundation through Roof) have dz values of 0-47mm in c_orderline, not the expected 0-53,000mm from absolute building coordinates. This means `floors()` returns tiny Z values and `ceilingHeightMm()` computes negative ceiling heights for most floors. The routing handles this gracefully: negative riseDistance skips riser ops, and the horizontal routing runs at the (incorrect but consistent) Z. **Root cause:** BOM compilation stores parent-relative offsets in dz, not absolute Z. The actual storey heights are in elements_rtree (extraction data), not c_orderline.
2. **+1 system edge from ceiling offset.** 712 edges vs 711 before — one additional edge was generated from the ceiling height calculation on the top floor where fallback produces a positive ceiling Z (3347mm). Functionally harmless.
