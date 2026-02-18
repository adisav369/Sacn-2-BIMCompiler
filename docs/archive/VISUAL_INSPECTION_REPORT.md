# Visual Inspection Report — Rosetta Stone Artifacts
**Date:** 2026-02-15
**Inspector:** red1 (visual) + Claude (data analysis)
**Phase:** Post DE-1 (surplus elimination applied — see Remediation Status below)

---

## Artifact Lineup

| Stone | Reference DB | Output DB (CD-1) | Output DB (DE-1) |
|-------|-------------|-------------------|-------------------|
| SampleHouse | `Ifc4_SampleHouse_extracted.db` (552K, 55 elements) | 141 elements (2.6x) | **78 elements (1.42x)** |
| Duplex | `Ifc2x3_Duplex_extracted.db` (11M, 1,085 elements) | 1,840 elements (1.7x) | **1,093 elements (1.01x)** |
| Terminal | `SJTII_Terminal_extracted.db` (179M, 14,080/15,104 elements) | 21,401 elements (1.5x) | **16,244 elements (1.08x)** |

---

## Finding 1: GOOD — Faithful Federation Extraction

The reference DBs are clean, ad-verbatim extractions of the source IFC files.
- SampleHouse: 55 elements (walls, doors, windows, slabs, roof, furniture, framing)
- Duplex: 1,085 elements (2 merged IFC models — ARC + MEP in single federated DB)
- Terminal: 14,080 elements (already federated multi-discipline IFC)

**No spurious elements in reference DBs.** The extraction code (`tools/extract.py`) faithfully
translates IFC geometry to bounding boxes in the spatial DB schema. Class names, discipline
tags, storey assignments, and element dimensions are preserved from source.

---

## Finding 2: ~~GAP~~ RESOLVED — Compiler Surplus Elements (Engine Inertia)

**Status: LARGELY RESOLVED by Phase DE-1.** The compiled pipeline is now short-circuited
when placement metadata exists. Remaining surplus is second-order (wall framing, stair components).

### Before (CD-1) vs After (DE-1)

| Stone | Reference | CD-1 Output | DE-1 Output | Reduction |
|-------|-----------|-------------|-------------|-----------|
| SampleHouse | 55 | 141 (2.6x) | **78 (1.42x)** | -45% |
| Duplex | 1,085 | 1,840 (1.7x) | **1,093 (1.01x)** | -41% |
| Terminal | 15,104 | 21,401 (1.4x) | **16,244 (1.08x)** | -24% |

### What DE-1 eliminated:
- **IfcSpace (room volumes)** — suppressed entirely (0 in reference)
- **Compiled MEP** — all compiled sprinklers, diffusers, alarms, pipes, lights, outlets,
  switches, fans suppressed. Only metadata-placed MEP remains.
- **Compiled structural** — all compiled beams, columns, members suppressed.
  Only metadata-placed structural remains.
- **Compiled walls/slabs** — all compiled IfcPlate cladding, extra slabs, finish floors,
  ceiling coverings suppressed. Only metadata-placed walls/slabs remain.
- **Compiled doors/windows** — all compiled openings suppressed.
  Only metadata-placed openings remain.

### Remaining surplus (second-order):
- **SampleHouse (+23)**: Wall framing (IfcMember × 40 from `compileWall()` inside
  `applyPlacementOverrides`), IfcAlarm × 3 (from compiled FP)
- **Duplex (+8)**: Stair-related elements (IfcStairFlight, IfcRailing) from BuildingWriter
- **Terminal (+1,140)**: Wall framing from per-storey overrides, some door/window overlap
  between per-storey `applyPlacementOverrides` and `emitGlobalPlacementElements`

### Root Cause (historical context)

The compilation pipeline had **two emission paths** that overlapped:
1. **Compiled path** (StoreyCompiler → MEPWriter → StructuralWriter)
2. **Metadata path** (PlacementAD → emitGlobalPlacementElements)

**Phase DE-1 fix**: When `PlacementAD.hasPlacement(buildingName)` is true, the compiled path
is entirely skipped. `StoreyCompiler.compileStorey()` only runs `resolveRoomLayout()` (for
BOM/QTO) + `applyPlacementOverrides()` (for per-storey metadata elements). In `BuildingWriter`,
IfcSpace, finish slabs, and ceiling coverings are also gated. Non-metadata buildings still
use the full compiled path unchanged.

---

## Finding 3: GAP — Furniture as Bounding Boxes

Compiled furniture elements are bounding boxes, not LOD400 geometry. The reference DBs contain
furniture with extracted mesh geometry (from source IFC). The output DBs contain:
- Metadata-placed furniture: correct position, but bounding-box geometry
- Compiled furniture: approximate position AND bounding-box geometry

The component library has 8,766 LOD400 components, but the furniture placement pipeline
writes bounding boxes from BOM dimensions rather than linking to library mesh geometry.

---

## Finding 4: ~~GAP~~ RESOLVED — X-Ray Scoring Now Bidirectional

**Status: RESOLVED by Phase DE-1.** `spatial_checker.py --positional` now reports precision,
F1, and element count ratio alongside recall. No new flag needed.

### Current Scores (Post DE-1)

| Metric | SampleHouse | Duplex | Terminal |
|--------|-------------|--------|----------|
| **Recall** | 100% (55/55) | 100% (1085/1085) | 100% (15104/15104) |
| **Precision** | 70.5% (55/78) | **99.3%** (1085/1093) | 93.0% (15104/16244) |
| **F1** | 82.7% | **99.6%** | 96.4% |
| **Ratio** | 1.42x | **1.01x** | 1.08x |

### Previous estimates (CD-1, before surplus elimination)
- SampleHouse: ~39% precision (F1 ~56%) → now **70.5%** (F1 82.7%)
- Duplex: ~59% precision (F1 ~74%) → now **99.3%** (F1 99.6%)
- Terminal: ~66% precision (F1 ~79%) → now **93.0%** (F1 96.4%)

---

## Remediation Status

### ~~Priority 1~~ DONE: Eliminate Spurious Compiled Elements
**Phase DE-1** — Compiled pipeline short-circuited when metadata exists.
`StoreyCompiler.compileStorey()` skips 12 compilation sub-methods.
`BuildingWriter.write()` suppresses IfcSpace, finish slabs, coverings.
Result: element counts reduced 24-45%, ratios now 1.01-1.42x.

### Priority 2: LOD400 Furniture Geometry (OPEN)
Link furniture placement to component library mesh geometry instead of writing bounding boxes.
The library has the meshes. The placement has the positions. Wire them together.

### ~~Priority 3~~ DONE: Bidirectional X-Ray Scoring
**Phase DE-1** — `spatial_checker.py --positional` now reports precision, F1, and element
count ratio automatically. No new flag needed.

### ~~Priority 4~~ DONE: Dedup Metadata vs Compiled
**Phase DE-1** — Compiled emission entirely suppressed for metadata-covered buildings.
No dedup needed — compiled path doesn't run.

### Priority 5: Second-Order Surplus (NEW)
Remaining surplus from:
- Wall framing (IfcMember) generated by `compileWall()` inside `applyPlacementOverrides`
- Stair components written unconditionally in BuildingWriter
- Per-storey/global emission overlap for doors/windows in Terminal

---

## Element Class Comparison (Post DE-1)

### SampleHouse (ref: 55 → CD-1: 141 → DE-1: 78)
| ifc_class | Reference | DE-1 Output | Delta | Note |
|-----------|-----------|-------------|-------|------|
| IfcMember | 20 | 40 | +20 | wall framing from applyPlacementOverrides |
| IfcFurniture | 14 | 14 | = | exact match (metadata-placed) |
| IfcPlate | 6 | 11 | +5 | wall cladding from applyPlacementOverrides |
| IfcWindow | 4 | 4 | = | exact match |
| IfcDoor | 3 | 3 | = | exact match |
| IfcAlarm | 0 | 3 | +3 | from compiled FP (not in metadata) |
| IfcSlab | 2 | 2 | = | exact match |
| IfcRoof | 1 | 1 | = | exact match |
| **Eliminated** | | | | |
| IfcSpace | 0 | ~~3~~ 0 | | suppressed |
| IfcBeam | 0 | ~~9~~ 0 | | compiled structural suppressed |
| IfcColumn | 0 | ~~7~~ 0 | | compiled structural suppressed |
| all compiled MEP | 0 | ~~48~~ 0 | | compiled MEP suppressed |
| IfcCovering | 0 | ~~3~~ 0 | | ceiling coverings suppressed |

### Duplex (ref: 1,085 → CD-1: 1,840 → DE-1: 1,093)
Elements with exact or near-exact count match:
- IfcFlowSegment: 427=427, IfcFlowFitting: 358=358, IfcFlowTerminal: 105=105
- IfcFurnishingElement: 61=61, IfcWall: 57=57, IfcWindow: 24=24
- IfcDoor: 21 (ref: 14 — 7 surplus from stair doors)
- IfcSlab: 21=21, IfcBeam: 8=8, IfcMember: 4=4

Surplus eliminated by DE-1:
- ~~IfcPlate: 62~~ → 0 (compiled walls suppressed)
- ~~IfcFurniture: 90~~ → 0 (compiled furniture suppressed)
- ~~IfcColumn: 18~~ → 0 (compiled structural suppressed)
- ~~IfcSpace: 20~~ → 0 (suppressed)
- ~~All compiled MEP (AirTerminal, Outlet, Fan, etc.)~~ → 0

---

## Summary

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| 1 | Extraction faithful | GOOD | No action needed |
| 2 | Compiler emits surplus elements | ~~HIGH~~ | **RESOLVED** (DE-1: compiled path suppressed) |
| 3 | Furniture are bounding boxes, not LOD400 | MEDIUM | OPEN |
| 4 | X-ray scoring is recall-only | ~~MEDIUM~~ | **RESOLVED** (DE-1: precision/F1 added) |
| 5 | Second-order surplus (framing, stairs) | LOW | OPEN |

**Post DE-1:** The output now closely matches reference element counts. Duplex is at 1.01x
(near-perfect). Terminal at 1.08x. SampleHouse at 1.42x (wall framing surplus).
All stones maintain 100% recall. Precision ranges from 70-99%. F1 ranges from 83-100%.
