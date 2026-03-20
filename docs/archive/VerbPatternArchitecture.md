# Verb Pattern Architecture

BOM recipe factorization via BIM_COBOL verb patterns.

## The Problem

TE unfactored: 48,428 instance-placement rows (one m_bom_line per element).
Root cause: conflating the BOM recipe (what types) with compiled output (where each goes).

## The Solution

Verb patterns (BIM_COBOL) compress repeated placements into recipe lines:
one line per product-group with `qty=N` + `verb_ref` encoding the placement formula.

### Verb Taxonomy

| Verb | Pattern | verb_ref format | Detection |
|------|---------|-----------------|-----------|
| TILE | 2D uniform grid | `TILE:nx:ny:stepX:stepY` | Uniform X+Y step, full grid |
| ROUTE | Axis-aligned runs | `ROUTE:X:step:n\|Y:step:n\|...` | Constant-axis chains |
| FRAME | Grid intersections | `FRAME:x1,x2,...\|y1,y2,...` | Irregular grid, all cells filled |
| ~~SPRAY~~ | ~~Semi-regular grid~~ | ~~`SPRAY:stepX:stepY`~~ | **DEPRECATED** — superseded by CLUSTER (session 32). CLUSTER stores exact per-instance offsets, eliminating SPRAY's 10% tolerance errors. |
| CLUSTER | Catch-all exact offsets | `CLUSTER:dx,dy,dz,w,d,h;...` | Lossless per-instance positions + dimensions |

Future verbs: ARRAY, STACK, MIRROR, WRAP, BRANCH, SCATTER.

### ERP Manufacturing Model

The pattern maps to iDempiere's manufacturing model:
- **M_Product** = product type (generic recipe)
- **verb_ref** = AttributeSetInstance (formula parameters)
- **AD_Val_Rule** = compliance constraints (sprinkler spacing, pipe clearance)
- **qty** = production quantity per recipe line
- **dx/dy/dz** = pattern origin (floor-relative)

### Non-Disturbance Principle

Patterns are mined from I_Element_Extraction data (RosettaStone baseline).
The verb formula must reproduce exact centroids within tolerance (5mm).
If it can't, elements stay unfactored (qty=1, no verb_ref).
SH/DX groups are too small (< 4 elements) — fall through to unfactored writes.

### Known Limitation: ROUTE Step Uniformity (Gap 6)

`VerbDetector.detectRoute()` chains elements by checking the **constant axis**
stays within 5mm tolerance (`countAxisRun`), but does **not** verify that the
**varying axis** has uniform step spacing. The step is computed as
`(last - first) / (count - 1)` — a simple average.

**What goes wrong:** A group of 5 pipe fittings at X = [90.8, 103.6, 108.3,
133.8, 138.5] gets accepted as `ROUTE:X:11.9:5` (avg step 11.9m) even though
actual spacing varies from 4.7m to 25.5m. The verb expansion then places all
5 elements at uniform 11.9m intervals. Intermediate positions diverge by up to
7m from their extraction centroids.

**Why it's invisible:** This drift doesn't trigger any gate failure:
- G1-COUNT: qty matches (same number of elements)
- G2-VOLUME: AABB volumes sum-match (element sizes unchanged)
- G3-DIGEST: SKIP for TE (CO mode)
- Verb fidelity check (step 9b): catches it, but is advisory-only

**TE impact:** ROUTE covers 34,139 instances (70% of all elements). After the
grouping key fix (R9), the fidelity check still shows avg ~7m error for ROUTE,
confirming the step-uniformity problem is real.

**Fix (R8):** Add step-uniformity guard to `countAxisRun()` — reject groups
where `max_step / min_step > threshold` (e.g. 1.5). Non-uniform groups fall
back to per-instance writes (lower compression, correct positions).

**Code:** `VerbDetector.java:386-396` (countAxisRun — only checks constAxis)

## Data Flow

```
Phase 1: Populate (one-time, IFCtoBOMMain --populate)
  reference DB ──ExtractionPopulator──→ component_library.db
    I_Element_Extraction (positions, dimensions, geometry hashes)
    M_Product (catalog — INSERT OR IGNORE = reuse across buildings)
    M_Product_Image (product → geometry_hash link)

Phase 2: BOM Pipeline (IFCtoBOMPipeline — reads component_library.db)
  ExtractionElement[] ──group by product──→ VerbDetector.detect()
    ├─ pattern found  → 1 line: verb_ref + qty=N + origin dx/dy/dz
    └─ no pattern     → N lines: qty=1, per-instance dx/dy/dz

Phase 3: Compilation (PlacementCollectorVisitor)
  m_bom_line.verb_ref ──expandVerb()──→ double[qty][6] offsets (dx,dy,dz,w,d,h)
    ├─ TILE    → origin + ix*stepX, iy*stepY
    ├─ ROUTE   → per-leg start + i*step along axis
    ├─ FRAME   → cartesian product of gridlines
    └─ CLUSTER → exact per-instance offsets + dimensions (lossless)
```

Phase 1 runs once per building. Phase 2 can re-run freely (`rm *_BOM.db`).
The shell script (`run_RosettaStones.sh`) skip-guards Phase 1 by checking
`SELECT COUNT(*) FROM I_Element_Extraction WHERE building_type=?`. To force
re-populate: `DELETE FROM I_Element_Extraction WHERE building_type='...'`.

## verb_ref Format Specification

All coordinates in **metres**, floor-relative. dx/dy/dz on the BOM line = pattern origin.

```
TILE:nx:ny:stepX:stepY           — 2D grid, nx*ny instances
ROUTE:X:step:n|Y:step:n|...     — axis-aligned legs chained
FRAME:x1,x2,...|y1,y2,...        — gridline positions (floor-relative)
CLUSTER:dx,dy,dz,w,d,h;...      — exact per-instance offsets + dimensions (lossless)
# SPRAY deprecated — replaced by CLUSTER (session 32)
```

## TE Results (2026-03-17, updated 2026-03-19)

```
Recipe lines:     1,131        (was 48,485)
Compression:      42.8:1
Verb coverage:    98.4%        (47,715 of 48,485 instances)
Verb breakdown:   CLUSTER 47,607  FRAME 78  ROUTE 18  TILE 12
Flat (unfactored): 770 lines (non-uniform placements, unique fittings)
```

**History:** Initial VerbDetector (1,442 lines, 34:1). After R8 step-uniformity
guard + R9 grouping key fix (1,297 lines, 37:1). After CLUSTER optimisation
replacing SPRAY (1,131 lines, 42.8:1). TILE/ROUTE/FRAME are fidelity-verified
(PASS). CLUSTER stores exact per-instance offsets + dimensions (lossless encoding).
Session 32: per-instance W/D/H added → G2-VOLUME 13.71%→-0.056% (PASS).
Session 34: F3 sort fix (X,Y,Z) → (X,Y,Z,W,D,H) — 7 tie-breaking instabilities resolved.

## Anti-Drift

- BomValidator `checkExtractionReconciliation`: SUM(qty) must equal extraction count
- BomValidator `printComplianceReport`: verb lines, instances, compression ratio
- VerbDetector `MIN_GROUP=4`: groups < 4 elements always fall through (SH/DX safe)
- Non-Disturbance: verb expansion must reproduce original centroids (5mm tolerance)
- `checkVerbExpansionFidelity` (step 9b): round-trip centroid diff — expands verb_ref,
  converts to world coords via floor AABB chain, compares against extraction centroids.
  Groups by `storey|discipline|product` (R9 fix — was `storey|product`, which mixed
  centroids from different discipline BOMs producing phantom km-scale errors)
- SH 7/7, DX 7/7 unaffected (no verb_ref, qty=1 everywhere)
- TE 7/7 PASS: 48,428 elements compiled end-to-end after ordinal collision fix

### Fidelity Check Status (2026-03-17)

| Verb | Instances | Max Error | Avg Error | Status | Cause |
|------|-----------|-----------|-----------|--------|-------|
| TILE | 12 | 0.0000m | 0.0000m | PASS | Uniform grid — exact match |
| FRAME | 60 | 0.0001m | 0.0001m | PASS | Grid intersections — exact match |
| ROUTE | 533 | ~1,273m | ~295m | FAIL | Multi-leg chaining (inter-leg offset) |
| CLUSTER | 47,607 | 0.029m | 0.004m | PASS | Lossless per-instance encoding |

TILE, FRAME, and CLUSTER prove the fidelity check is sound. CLUSTER replaced
SPRAY in session 32 — exact per-instance offsets eliminate the 10% tolerance
errors. After R8 (step-uniformity), ROUTE only matches truly uniform legs —
remaining errors are multi-leg chaining (inter-leg X offset not encoded in
verb_ref). **Known gap (VPA-002):** ROUTE per-leg step-uniformity not verified
for multi-leg chains — 533 instances with multi-metre errors. Fix: validate
step uniformity per leg, fallback to CLUSTER for non-uniform legs.

## Mathematical Basis (CONCEPTUAL BLUEPRINT Theorems 1+5)

- **Theorem 1 (CLT):** Pattern aggregation reduces variance: sigma_wall = sqrt(N) * sigma_window << N * sigma_window
- **Theorem 5 (Information Theory):** Buildings are inherently compressible due to designed patterns. 73x compression is mathematically justified, not magic.

## Files

| File | Role |
|------|------|
| `migration/F2_001_bom_line_verb_ref.sql` | Schema migration |
| `IFCtoBOM/.../VerbDetector.java` | 4 detection algorithms |
| `IFCtoBOM/.../DisciplineBomBuilder.java` | F-2 factored writes |
| `DAGCompiler/.../PlacementCollectorVisitor.java` | Verb expansion |
| `IFCtoBOM/.../BomValidator.java` | Compliance report + verb fidelity check |
| `ORMSandbox/.../X_M_BOMLine.java` | verb_ref PO column |
