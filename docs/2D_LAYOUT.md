# 2D Layout — Architectural Drawings from Compiled BOM

> **Module:** `2D_Layout/` (Maven sibling of DAGCompiler) ·
> **Source spec:** `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md`

**Status:** POC complete on Sample House. Java stubs + Python prototype. Floor plan SVG generated.

---

## What It Does

Generates professional architectural drawings from the compiled 3D output:

```
compiled output.db → section cut → SVG floor plan / elevation / section / roof plan
```

The same [output.db](BOMBasedCompilation.md) that feeds
[4D–8D queries](ProjectOrderBlueprint.md) also produces 2D architectural
drawings. No separate drawing tool. No manual drafting. The 3D model IS
the drawing — sliced at the right plane.

## Why It Matters for the ERP Paradigm

In construction, 2D drawings are still the legal deliverable — the document
the contractor builds from, the regulator stamps, the QS prices. Most BIM
tools treat 2D as an afterthought (export to DWG, clean up manually).

Here, 2D drawings are a **query on the compiled BOM** — the same data that
produces [5D cost](ProjectOrderBlueprint.md#52-5d-cost-inherent-in-the-data-model)
and [6D carbon](TIER1_SRS.md) also produces the floor plan. One source of truth,
multiple views:

| View | Method | What it shows |
|------|--------|---------------|
| **Floor plan** | Horizontal section cut at 1.0–1.2m above floor level | Walls, doors, windows, furniture |
| **Elevation** | Orthographic projection onto vertical plane | Exterior appearance, roof profile |
| **Section** | Vertical section cut through building | Internal structure, wall composition |
| **Roof plan** | Top-down view | Ridge lines, slopes, drainage |

## The Round-Trip Proof

```
1D BOM recipe → 3D compilation → 2D drawing → back to BOM verification
```

The [Sample House floor plan](index.md) (Pelan Lantai) visible on the landing
page was generated from this pipeline. If the 2D drawing matches the architect's
original, the BOM grammar is proven from yet another angle.

## How It Connects

| Dimension | Role |
|-----------|------|
| **3D** | Source geometry — compiled element meshes from output.db |
| **2D** | Derived views — section cuts, projections, annotations |
| **[4D](ProjectOrderBlueprint.md#51-4d-schedule-topological-sort-of-bom-tree)** | Construction sequence overlaid on floor plans |
| **[5D](ProjectOrderBlueprint.md#52-5d-cost-inherent-in-the-data-model)** | QS takeoff reads the same elements the drawing shows |
| **[7D](TIER1_SRS.md)** | Facility management drawings from the same compiled model |
| **[AD_PrintFormat](https://wiki.idempiere.org/en/AD_PrintFormat)** | iDempiere's output selection pattern — which elements render, which hide |

## Current State

| Deliverable | Status |
|-------------|--------|
| Floor plan (SH) | POC — SVG generated from mesh section cut |
| Elevations | Designed — no ceiling overlap, level markers |
| Sections | Stub — hatching per material pending |
| Roof plan | Stub — envelope extraction designed |
| Annotations | Designed — grid lines, dimensions, room labels |
| Java port | 6 stub classes created, Python prototype as reference |

## Source

**Module:** `2D_Layout/` in [BIMCompiler](https://github.com/red1oon/BIMCompiler) repository.
See `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` for the full technical spec
(mesh section cut algorithm, annotation overlay, JKR/ISO conventions).
