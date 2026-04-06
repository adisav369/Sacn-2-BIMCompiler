# IFC Extraction Pipeline — Code Inventory

## Background

The pristine extractor (`DAGCompiler/python/extractIFCtoDB.py`) works for single-file,
metre-unit IFC files. Two problems arise with multi-discipline IFC2x3 buildings
(LTU A-House, FZK Haus, etc.):

1. **Geometry hell** — IFC2x3 Swedish/German files use millimetres. The extractor writes
   native units, so `base_geometries.vertices` is in mm while post-hoc fixes only scaled
   `element_transforms` to metres. Result: `vertex(mm) + center(m)` = geometry collapses.

2. **Discipline loss** — `DISCIPLINE_MAP` lumps all `IfcFlowSegment`/`IfcFlowFitting` to
   generic "MEP". Sub-disciplines (VENT/HVAC/HEAT/PLB/SAN) require source file identity,
   which is lost in a merged IFC.

IFC-level merging was attempted but OOMs on large buildings (125K+ elements, 173MB ARC file).

## What We Built

### `fix_vertex_scale()` in `extract_merge_disciplines.py`

Added to the existing per-discipline orchestrator. After each discipline file is extracted
to a tmp_db, this function:

- Opens the IFC with ifcopenshell, reads `calculate_unit_scale(ifc, "LENGTHUNIT")`
- If not 1.0 (i.e. mm), scales ALL three geometry tables in the tmp_db:
  - `base_geometries.vertices` — float32 blob, each coordinate multiplied by unit_scale
  - `element_transforms.center_x/y/z` — world-space centers
  - `elements_rtree.minX/maxX/minY/maxY/minZ/maxZ` — bounding boxes
- Runs BEFORE `merge_db()`, so the merged output DB is consistently in metres

This is the **Hell_GeometryScale fix**. No post-hoc SQL needed for new extractions.

### `scripts/extractIFCtoDB_open.py`

Standalone copy of the pristine extractor with three additions:

1. **Fine-grained discipline map** — `IfcDuctSegment→VENT`, `IfcPipeSegment→PLB`,
   `IfcSanitaryTerminal→SAN`, etc. (vs original's `IfcFlowSegment→MEP`)

2. **3-tier discipline inference:**
   - Priority 1: `Pset_BIMSource.Discipline` (stamped by merger, if present)
   - Priority 2: IFC class map (specific classes like IfcDuctSegment)
   - Priority 3: Keyword heuristic on `element_type`/`material_name`
     (Swedish: kanal→VENT, avlopp→SAN, värme→HEAT, vatten→PLB, kyla→HVAC)

3. **Built-in unit scale fix** — detects mm IFCs and scales geometry inline during extraction

4. **ForensicLog class** — structured abstract: by-discipline, by-class, by-storey,
   tier breakdown (T1/T2/T3), heuristic upgrade count, world extent

5. **project_metadata** populated with unit_scale, extractor tag, element counts, camera target

### `scripts/merge_ifc_tagged.py`

IFC merger that stamps `Pset_BIMSource` (Discipline + SourceFile) on each IfcProduct
before merging into target. Allows extractIFCtoDB_open.py to read sub-disciplines from
a merged IFC without needing the original source files.

**Limitation:** OOMs on 125K+ element buildings. Only viable for smaller buildings.

### `ExtractionPostProcessor.java`

Java post-processor (`DAGCompiler/src/main/java/com/bim/compiler/db/ExtractionPostProcessor.java`)
that fixes any already-extracted DB after the fact:

- **Unit scale detection** — reads `project_metadata.unit_scale`, or auto-detects from
  data range (if max abs center > 500, assumes mm)
- **Vertex scale fix** — reads float32 blobs, scales each coordinate, writes back (batch)
- **Transform + rtree scale** — SQL UPDATE with scale factor
- **Discipline refinement** — finds all `discipline='MEP'` rows, scans element_type/material_name
  with Swedish/English keyword heuristics, upgrades to VENT/SAN/PLB/HEAT/HVAC/ELEC/FP
- **Forensic abstract** via BIMLogger — by-discipline, by-class, by-storey, world extent
- Public accessors for test verification (`getUnitScale()`, `getByDiscipline()`, etc.)

Compiles clean in DAGCompiler module. Uses existing BIMLogger + sqlite-jdbc patterns.

## File Inventory

| File | What it does | Modifies original? |
|---|---|---|
| `DAGCompiler/python/extractIFCtoDB.py` | Pristine single-file extractor | **NEVER TOUCH** |
| `scripts/extract_merge_disciplines.py` | Per-discipline orchestrator + `fix_vertex_scale()` | Extended (new function added) |
| `scripts/extractIFCtoDB_open.py` | Open-filter extractor with fine-grained discipline | New file (copy of pristine) |
| `scripts/merge_ifc_tagged.py` | IFC merger with Pset_BIMSource stamping | New file |
| `DAGCompiler/.../db/ExtractionPostProcessor.java` | Java post-processor for any extracted DB | New file |
| `docs/LTUAHouseAnalysis.md` | Updated Step 3 (legacy fix label) + camera note | Updated |

## Which Approach to Use

| Scenario | Use |
|---|---|
| Multi-discipline building (production) | `extract_merge_disciplines.py` with `--disc-map` (Approach A) |
| Small merged IFC with Pset tagging | `merge_ifc_tagged.py` then `extractIFCtoDB_open.py` (Approach B) |
| Fix existing DB without re-extraction | `ExtractionPostProcessor.java` (Approach C) |
| Single-file metre-unit IFC | `extractIFCtoDB.py` directly (original, untouched) |

Building-specific extraction commands: see each `docs/{Building}Analysis.md`.

## Proven Scale

| Building | Elements | Disciplines | DB Size | Extraction | Bonsai Load |
|---|---|---|---|---|---|
| LTU A-House | 125,997 | 8 | 232.7 MB | ~20 min | 13.6 GB RAM, smooth 3D nav, 3s select |
| Clinic | 16,481 | 5 | 55.7 MB | ~5 min | Lightweight |

LTU A-House is the largest multi-discipline building tested. Full tessellated mesh for
all 125K elements loads in Bonsai/Blender without crashes, fan noise, or frame drops.
No LOD or culling — raw scene graph. Competitive with Navisworks/Solibri at zero licence cost.

IFC-level merging (426MB merged file) OOMs on buildings this size.
DB-level merging (Approach A) is the only viable path for 100K+ elements.
