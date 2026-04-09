# 2D Layout — Architectural Drawings from Compiled BOM

> **Module:** `2D_Layout/` (Maven sibling of DAGCompiler) ·
> **Source spec:** `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md`

<div class="bim-banner" markdown>
<b>The 3D model IS the drawing — no separate drafting tool.</b> Section cuts through compiled output.db produce SVG floor plans, elevations, and roof plans automatically.
</div>

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

---

## Visual Proof — Sample House

**The 3D compiled model (Bonsai/Blender viewport):**

![Sample House 3D — compiled from BOM, rendered in Bonsai](assets/images/SampleHouseOri.png)

*SH compiled from BOM recipe. Glass curtain wall meets pitched roof — the
TRIM verb adjusts wall panels at the roof intersection. Every element traces to
component_library.db via G5-PROVENANCE.*

**The 2D floor plan — generated from the same compiled output.db:**

![Pelan Lantai / Floor Plan — horizontal section cut at 1.2m](assets/images/2D_FLOOR_SAMPLEHOUSE.png)

*Pelan Lantai (Floor Plan): horizontal section cut at 1.2m above floor level.
Walls, doors, windows, furniture — all from the compiled BOM. Room labels
(Ruang Tamu, Ruang Makan, Bilik) from M_Product_Category names. Grid lines
and dimensions from AABB extents. Title block follows JKR/ISO convention.*

**The 2D roof plan — generated from the same compiled output.db:**

![Pelan Bumbung / Roof Plan — ridge lines and slope arrows](assets/images/2D_ROOF_SAMPLEHOUSE.png)

*Pelan Bumbung (Roof Plan): top-down projection showing ridge lines (Cumbung Sana,
Cumbung Sini), slope direction arrows, and drainage layout. Generated from the
same BOM tree — the roof is an M_BOM with TILE verb expansion.*

**The architect's original roof plan — the ground truth we compile against:**

![Architect's original roof plan — the reference drawing](assets/images/RoofPlan.png)

*The architect's hand-drawn roof plan (scale 1:100). Grid lines A-E, levels 1-5,
material hatching, drainage symbols, legend. The compiler-generated version above
reproduces the spatial layout from BOM data alone — no manual drafting.*

---

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

The floor plan and roof plan above were generated from this pipeline. Compare the
compiler-generated roof plan against the architect's original — the spatial layout
matches. If the 2D drawing reproduces the architect's intent from BOM data alone,
the BOM grammar is proven from yet another angle.

This is the **Rosetta Stone principle applied to drawings**: the architect's original
is the stone, the compiler-generated drawing is the translation. Match = proof.

## How It Connects

| Dimension | Role |
|-----------|------|
| **3D** | Source geometry — compiled element meshes from output.db |
| **2D** | Derived views — section cuts, projections, annotations |
| **[4D](ProjectOrderBlueprint.md#51-4d-schedule-topological-sort-of-bom-tree)** | Construction sequence overlaid on floor plans |
| **[5D](ProjectOrderBlueprint.md#52-5d-cost-inherent-in-the-data-model)** | QS takeoff reads the same elements the drawing shows |
| **[7D](TIER1_SRS.md)** | Facility management drawings from the same compiled model |
| **[AD_PrintFormat](https://wiki.idempiere.org/en/AD_PrintFormat)** | iDempiere's output selection pattern — which elements render, which hide |

## Layout Engine (§12a)

The elevation engine is **metadata-driven** — it reads zone widths and face
class rules from `drawing_template.json`, not from hardcoded face names.

**Face classification** derives from apparent width (DB geometry, not name):

| Face class | Apparent width | Carries |
|------------|---------------|---------|
| `annotation_face` | Shorter dimension (building depth) | Level markers, height dim chain |
| `form_face` | Longer dimension (building length) | Grid bubbles only — no markers |

For DX Duplex: FRONT/REAR are annotation faces (8.8m), LEFT/RIGHT are form faces (17.8m).
For SH Sample House: FRONT/REAR are form faces (14.1m), LEFT/RIGHT are annotation faces (6.1m).

The engine classifies both correctly from geometry — same code path, different output.

**Template keys driving the engine:**
```json
"elevation": {
  "level_zone_width_mm": 30,
  "height_zone_width_mm": 22,
  "form_face_level_zone_width_mm": 0,
  "form_face_height_zone_width_mm": 0
}
```

**Level labels** come verbatim from `2d_level_marker.display_text` (2D.db) — never
synthesised or abbreviated in code.

## Current State

| Deliverable | Status | Evidence |
|-------------|--------|----------|
| Floor plan (SH, DX) | DONE — DXF + SVG proof | See Pelan Lantai above |
| Roof plan (SH, DX) | DONE — DXF + SVG proof | See Pelan Bumbung above |
| Elevations (all faces) | DONE — face classification + zone layout engine | `§FACE_CLASS` + `§ZONE_LAYOUT` in diagnostic log |
| Level markers (annotation faces) | DONE — verbatim from `2d_level_marker.display_text` | `§LEVEL_MARKER src=2d_level_marker` |
| Grid bubbles (bottom) | In progress — I-31 | Top bubble done; bottom pending |
| Zone boundary enforcement | In progress — I-32 | `§ZONE_LAYOUT` logged; clamping TBD |
| Sections | Stub — hatching per material pending | — |
| Java port | 6 stub classes created, Python prototype as reference | `2D_Layout/src/main/java/com/bim/layout/` |

## Source

**Module:** `2D_Layout/` in [BIMCompiler](https://github.com/red1oon/BIMCompiler) repository.
Full technical spec: `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` (§12a Layout Engine,
mesh section cut algorithm, JKR/ISO conventions).

**DXF/SVG output:** `2D_Layout/output/` — floor plan, roof plan, 4 elevation DXFs + SVG proofs.
