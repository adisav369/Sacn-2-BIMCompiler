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
| SPRAY | Semi-regular grid | `SPRAY:stepX:stepY` | 10% tolerance on step |

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

## Data Flow

```
IFCtoBOM Pipeline:
  ExtractionElement[] ──group by product──→ VerbDetector.detect()
    ├─ pattern found  → 1 line: verb_ref + qty=N + origin dx/dy/dz
    └─ no pattern     → N lines: qty=1, per-instance dx/dy/dz

DAGCompiler (PlacementCollectorVisitor):
  m_bom_line.verb_ref ──expandVerb()──→ double[qty][3] offsets
    ├─ TILE  → origin + ix*stepX, iy*stepY
    ├─ ROUTE → per-leg start + i*step along axis
    ├─ FRAME → cartesian product of gridlines
    └─ SPRAY → semi-regular grid approximation
```

## verb_ref Format Specification

All coordinates in **metres**, floor-relative. dx/dy/dz on the BOM line = pattern origin.

```
TILE:nx:ny:stepX:stepY           — 2D grid, nx*ny instances
ROUTE:X:step:n|Y:step:n|...     — axis-aligned legs chained
FRAME:x1,x2,...|y1,y2,...        — gridline positions (floor-relative)
SPRAY:stepX:stepY                — semi-regular grid (TILE with 10% tolerance)
```

## TE Results (2026-03-17)

```
Recipe lines:     1,297        (was 48,428)
Compression:      37:1
Verb coverage:    98.1%        (47,487 of 48,428 instances)
Verb breakdown:   ROUTE 34,139  SPRAY 13,336  TILE 12
Flat (unfactored): 941 lines   (irregular placements, small groups)
```

## Anti-Drift

- BomValidator `checkExtractionReconciliation`: SUM(qty) must equal extraction count
- BomValidator `printComplianceReport`: verb lines, instances, compression ratio
- VerbDetector `MIN_GROUP=4`: groups < 4 elements always fall through (SH/DX safe)
- Non-Disturbance: verb expansion must reproduce original centroids (5mm tolerance)
- `checkVerbExpansionFidelity` (step 9b): round-trip centroid diff — expands verb_ref,
  converts to world coords via floor AABB chain, compares against extraction centroids
- SH 7/7, DX 7/7 unaffected (no verb_ref, qty=1 everywhere)
- TE 7/7 PASS: 48,428 elements compiled end-to-end after ordinal collision fix

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
