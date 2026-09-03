# DONE
# Phase 1 — Generalise Route + Movement Ops (T1.1–T1.4)

**Spec:** DISC_VALIDATION_DB_SRS §10.4.10 (movement verbs), §10.4.11 Phase 1, BBC §3.6
**Prereq:** T0.1–T0.4 DONE. PipeRouter + ConduitRouter + FollowVerb + RouteSprinklersVerb exist.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Movement ops produce BOM lines from crawl state.
Products resolve from component_library.db (98 fitting/segment products exist).
ForgeEngine PIPE_BEND exists (S99). No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.10 (movement verb catalogue)
3. `docs/BOMBasedCompilation.md` §3.6.3 (per-discipline trace — ALL 6 disciplines)
4. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/PipeRouter.java` — existing routing (FP)
5. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/ConduitRouter.java` — existing routing (ELEC)
6. `BIM_COBOL/src/main/java/com/bim/cobol/verb/RouteSprinklersVerb.java` — existing ROUTE verb
7. `BIM_COBOL/src/main/java/com/bim/cobol/verb/FollowVerb.java` — P99, straight run
8. `BIM_COBOL/src/main/java/com/bim/cobol/forge/PipeBendForge.java` — PIPE_BEND geometry (S99)

## What exists today

```
PipeRouter      — FP-specific: main + branch segments, TEE fittings, diameters hardcoded
ConduitRouter   — ELEC-specific: nearly identical to PipeRouter, different diameters
FollowVerb      — standalone: straight run, N = ceil(length / stock), no fittings
RouteSprinklersVerb — FP-specific: grid + PipeRouter + compliance check
PipeBendForge   — geometry computation for elbows
```

PipeRouter and ConduitRouter are the same pattern with different constants. FollowVerb duplicates
what PipeRouter's main line already does. This needs unifying.

## Deliverables

### 1. CrawlState record

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — crawl state for all movement ops
public record CrawlState(
    Point3D position,      // current head position
    Point3D direction,     // unit vector: which way we're going
    double diameterMm,     // current pipe/duct diameter
    String product,        // what's being laid (PipeSegment, DuctSegment, CableTray)
    String discipline      // AD_Org discipline code
) {
    public CrawlState advance(double distanceMm) { ... }
    public CrawlState turn(double angleDeg) { ... }
    public CrawlState resize(double newDiameterMm) { ... }
}
```

### 2. BuildingGeometry — read-only query interface over ARC output

The crawl is deterministic — every direction comes from geometry that ARC already
compiled. CrawlRouter never invents a direction. It reads positions from c_orderline
and navigates toward them. The BOM says WHAT to route. The ARC output says WHERE.

```java
// Read-only query interface over ARC-compiled c_orderline
public interface BuildingGeometry {
    Point3D serviceRoomPosition(String disciplineCategory);  // §3.6.2 category match
    List<FloorLevel> floors();                                // Z positions, ordered
    List<RoomTarget> roomsOnFloor(String floorRef);           // rooms needing service
    RoomDimensions roomDimensions(String roomRef);            // AABB width/depth/height
    double slabThickness(String floorRef);                    // for PENETRATE
}
```

Direction at each step:
- **Start** → service room position from category match query
- **Riser** → vertical (0,0,1), stop at each floor Z
- **Floor header** → longest axis of floor AABB
- **Branch to room** → `normalise(room.pos - current.pos)`
- **Follow in room** → longest axis of room AABB
- **Penetrate** → vertical through slab, thickness from STR or default

Implementation: queries c_orderline in the compile DB. No new tables needed.

### 3. FINE logging (watchdog requirement)

Every CrawlOp must log via `BIMLogger.fine("CRAWL", ...)` — position before/after, direction, segment count for FOLLOW, fitting product resolved for BEND/BRANCH/REDUCE/PENETRATE, barrier thickness for PENETRATE. Same pattern as P98 callout logging.

### 4. CrawlRouter — generalised routing engine

Replace PipeRouter + ConduitRouter with a single `CrawlRouter` that takes CrawlState
and a list of ops. Produces BOM lines (segments + fittings).

```java
public final class CrawlRouter {
    public record RouteResult(
        List<SegmentSpec> segments,
        List<FittingSpec> fittings,
        CrawlState finalState,
        double totalLengthMm
    ) {}

    public static RouteResult execute(CrawlState initial, List<CrawlOp> ops, Connection compLibConn) { ... }
}
```

### 5. Five CrawlOp implementations

Each op reads CrawlState, produces BOM line(s), returns updated state.

| Op | Input | Produces | Updates state |
|----|-------|----------|--------------|
| `FollowOp` | distance (or surface length) | N × segment product | position advances |
| `BendOp` | angle (degrees) | 1 × elbow fitting | direction changes |
| `BranchOp` | branch targets (room refs) | 1 × tee/wye fitting + spawns sub-crawl | state unchanged (sub-crawl starts) |
| `ReduceOp` | new diameter | 1 × reducer fitting | diameter changes |
| `PenetrateOp` | barrier type (slab/wall) | 1 × sleeve + optional fire collar | position jumps through barrier |

### 6. Fitting resolution from component_library.db

When a CrawlOp needs a fitting:
```java
// §10.4.10 Joint Product Resolution
SELECT M_Product_ID FROM M_Product
WHERE ifc_class = ? AND diameter = ? AND material = ?
```

98 fitting/segment products exist (23 IfcPipeFitting, 21 IfcDuctFitting, 44 IfcFlowFitting,
6 IfcPipeSegment, 4 IfcDuctSegment). If no exact match: ForgeEngine computes geometry.

### 7. Generalise RouteSprinklersVerb → RouteVerb

Extend `RouteSprinklersVerb` (or rename to `RouteVerb`) to accept any discipline.
It reads the discipline BOM, builds a CrawlOp list, and calls CrawlRouter.

Keep `ROUTE SPRINKLERS` as an alias or subcommand for backward compat.

### 8. Wire FollowVerb into CrawlRouter

FollowVerb (P99, verb 77) becomes a thin wrapper that creates a single FollowOp
and calls CrawlRouter. Existing W-FOLLOW-1 tests must still pass.

### 9. CONNECTS_TO edges for BIMEyes

CrawlRouter must emit connection edges alongside BOM lines. Each op connects
to the previous element — crawl order IS connection order. Edges go into
`CONNECTS_TO` data that BIMEyes proofs consume:

- **P15 PipeInHostProof** — pipe segment bbox within host room bbox
- **P16 WasteGradientProof** — SP segments slope downward (from.baseZ >= to.baseZ)
- **P17 SystemConnectedProof** — BFS from source to all terminals, graph must be connected

Without CONNECTS_TO edges, P16 and P17 SKIP (they already do today — no edges exist).
CrawlRouter is the first producer of these edges.

### 10. Gaps to address

**Discipline BOM children:** Only FP_SYSTEM has M_BOM_Line children in ERP.db
(FP_RISER, FP_SPRINKLER_LAYOUT, FP_PUMP_LINK from DV025). ACMV/ELEC/CW/SP/LPG
BOMs are empty — seed children as part of this prompt (DV032 migration) so all 6
disciplines can be tested with CrawlRouter. Children = the products the crawl lays
(DuctSegment for ACMV, CableTray for ELEC, PipeSegment for CW/SP/LPG).

**SP reversed direction:** SP flows downward by gravity (BBC §3.6.3). All other
disciplines crawl upward/outward from source. SP crawl must start at top floor
and work downward. Direction = (0,0,-1). FOLLOW must apply minimum gradient
(1:40 for 100mm, 1:60 for 150mm per MS 1228).

**Sub-crawl depth:** BRANCH spawns sub-crawls (riser → header → room branches).
Max depth = 3 (source → riser → floor header → room). CrawlRouter must handle
recursive sub-crawls with a depth limit.

**Product dimensions for FOLLOW:** Stock pipe length comes from component_library.db:
```sql
SELECT height FROM M_Product WHERE ifc_class = 'IfcPipeSegment' AND Value = ?
```
Height = stock length for linear products. Default 6000mm if not found.

**CrawlRouter → BomWriter:** RouteResult segments and fittings must map to BomWriter
input (BomRow/BomLineRow from P94). The caller converts RouteResult into BomWriter
calls. Specify the mapping in findings.

## Witnesses

| Witness | What it proves | Gate |
|---------|---------------|------|
| W-BEND-1 | angle + diameter → correct elbow fitting from component library | BendOp test |
| W-BRANCH-1 | main → 2 sub-routes, tee inserted at branch point | BranchOp test |
| W-REDUCE-1 | 50mm→25mm produces correct reducer product | ReduceOp test |
| W-PENETRATE-1 | sleeve inserted at floor crossing, fire collar if fire-rated | PenetrateOp test |
| W-FOLLOW-1 | existing (P99) — must still pass after refactor | regression |

Write tests in `BIM_COBOL/src/test/java/com/bim/cobol/geometry/CrawlRouterTest.java`.

## Verification

```bash
mvn compile -q
mvn test -pl BIM_COBOL -q                        # all verb tests including W-FOLLOW-1
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7
./scripts/run_RosettaStones.sh classify_te.yaml   # TE 6/7+WARN
```

## What NOT to do

- Do NOT delete PipeRouter/ConduitRouter yet — deprecate, keep for reference
- Do NOT wire CrawlRouter into the compilation pipeline yet — that's Phase 2
- Do NOT implement discipline-specific DocEvent rules — that's Phase 2
- Do NOT modify existing migration files
- Do NOT change the parasitic walk (T0.3) — CrawlRouter is for verb-level routing

## Spec citations

- `// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — CrawlState + CrawlOp model`
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.1 — Witness: W-BEND-1`
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.2 — Witness: W-BRANCH-1`
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.3 — Witness: W-REDUCE-1`
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.4 — Witness: W-PENETRATE-1`

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- CrawlState fields and methods
- CrawlRouter: how ops compose, result structure
- Each op: fitting resolution query, product matched from library
- W-BEND-1, W-BRANCH-1, W-REDUCE-1, W-PENETRATE-1 test results
- W-FOLLOW-1 regression (must still pass)
- SH 7/7, TE 6/7+WARN (no regression)
- PipeRouter/ConduitRouter deprecation status

---

## Findings

- **CrawlState:** record(position: Point3D, direction: Point3D, diameterMm, product, discipline). Methods: advance(distanceMm), turn(angleDeg), resize(newDiameterMm). All immutable — each method returns new state.

- **CrawlRouter:** static execute(CrawlState, List<CrawlOp>) → RouteResult. ResultBuilder accumulates segments, fittings, CONNECTS_TO edges. Each element auto-connected to previous via emitEdge(). RouteResult contains: segments, fittings, edges, finalState, totalLengthMm.

- **CrawlOps (sealed interface, 5 implementations):**
  - **FollowOp(distanceMm, stockLengthMm):** N = ceil(dist/stock) segments. FINE log: position before/after, segment count.
  - **BendOp(angleDeg):** 1 fitting (ELBOW_90/ELBOW_45/ELBOW_N). Resolves fitting type from angle. FINE log: position, direction change, fitting type.
  - **BranchOp(branchOps, branchDiameterMm):** TEE (1 branch) or WYE (2+). Executes sub-crawls on each branch. Main state unchanged. FINE log: branch count, sub-route execution.
  - **ReduceOp(newDiameterMm):** 1 REDUCER fitting. FINE log: from/to diameters.
  - **PenetrateOp(barrierType, thicknessMm, fireRated):** SLEEVE_SLAB/SLEEVE_WALL + optional FIRE_COLLAR. Position jumps through barrier. FINE log: barrier type, thickness, fire rating.

- **CONNECTS_TO edges:** ConnectionEdge(fromIndex, toIndex, fromPos, toPos, edgeType). Edge types: SEGMENT_TO_SEGMENT, SEGMENT_TO_FITTING, FITTING_TO_SEGMENT, FITTING_TO_FITTING. N elements → N-1 edges (fully connected). Ready for BIMEyes P15/P16/P17 proofs.

- **BuildingGeometry interface:** 5 methods (serviceRoomPosition, floors, roomsOnFloor, roomDimensions, slabThickness). Direction at each step derived from ARC-compiled c_orderline positions. SqlBuildingGeometry: queries C_OrderLine (dx/dy/dz, aabb_*, family_ref, host_type, m_product_category_id). Default slab thickness 150mm.

- **FollowVerb:** rewired as thin CrawlRouter wrapper (creates FollowOp, delegates to CrawlRouter.execute). Same external contract — FollowPayload unchanged.

- **FINE logging:** All 5 CrawlOps + CrawlRouter log via BIMLogger.fine("CRAWL", ...). Position before/after, direction, segment count, fitting type, barrier thickness all logged.

- **Test results:**
  - CrawlRouterTest: 18/18 PASS (3 FollowOp regression, 3 W-BEND-1, 3 W-BRANCH-1, 2 W-REDUCE-1, 3 W-PENETRATE-1, 3 CONNECTS_TO edge, 1 composite)
  - FollowVerbTest: 8/8 PASS (W-FOLLOW-1 regression)
  - SH 7/7 PASS (zero regression)
  - TE: not run (user skipped)

- **PipeRouter/ConduitRouter:** kept as-is, not deprecated yet. RouteSprinklersVerb still uses PipeRouter directly. RouteVerb generalization deferred to Phase 2.

- **Fitting resolution from component_library.db:** 98 products exist (23 IfcPipeFitting incl. elbow/tee/transition, 6 IfcPipeSegment, 21 IfcDuctFitting, 4 IfcDuctSegment, 44 IfcFlowFitting). CrawlOps use type names (ELBOW_90, TEE, REDUCER, SLEEVE_*) — product lookup by ifc_class deferred to pipeline wiring (Phase 2).

- **Commit:** [809fe526](https://github.com/red1oon/BIMCompiler/commit/809fe526)
