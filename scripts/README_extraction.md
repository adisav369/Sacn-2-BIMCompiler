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

### `post_normalise_site_origin()` in `extract_merge_disciplines.py`

Post-action added at the end of `extract_merge_disciplines.py`, after all disciplines are
merged and **before** the camera target is written to `project_metadata`.

**Problem:** `USE_WORLD_COORDS=True` bakes the full IFC placement chain — including
`IfcSite.ObjectPlacement` — into every element coordinate. GIS-located buildings
(e.g. Hospital: site Z = 165.8m) produce element Z ranges like 165–203m. The camera
target inherits this offset, placing it at `view_center_z=181m`.

**Fix:** Read `IfcSite.ObjectPlacement` from the first discipline IFC via
`ifcopenshell.util.placement.get_local_placement()`. If `|site_oz| > 1.0m`, subtract
from all rows in `element_transforms.center_z` and `elements_rtree.minZ/maxZ`.
The camera target is then computed from the corrected `element_transforms` — no separate
camera fix needed, it self-corrects.

**Universal:** site at Z=0 → offset=0 → no-op. Works for any building.

**Logic source:** `scripts/topup_extracted_db.py` lines 49–147 (proven, same pattern).

**Witness:** W-SITE-Z-1 in `scripts/verify_extraction.sh` —
asserts `view_center_z < 50m` AND within element Z range after extraction.

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

| File | What it does | Status |
|---|---|---|
| `DAGCompiler/python/extractIFCtoDB.py` | Core extractor: `geom.iterator()` (S172), local coords (S168), rotation (S169), batch-commit (S170), site normalization | **Primary — all features here** |
| `scripts/extract_merge_disciplines.py` | Per-discipline orchestrator: parallel extraction (S172), `--library`, `--disc-map`, merge at DB level | **Primary — use for multi-discipline buildings** |
| `scripts/topup_extracted_db.py` | Add missed IFC classes to existing DB without re-extracting | Utility |
| `scripts/extractIFCtoDB_open.py` | Legacy open-filter extractor with Swedish keyword heuristics | Archive — features merged into main extractor |
| `scripts/merge_ifc_tagged.py` | IFC merger with Pset_BIMSource stamping | Archive — prefer DB-level merge |
| `DAGCompiler/.../db/ExtractionPostProcessor.java` | Java post-processor for existing DBs | Utility |

## Which Approach to Use

| Scenario | Command |
|---|---|
| **Multi-discipline building** (recommended) | `python3 scripts/extract_merge_disciplines.py --ifc-dir IFC/UNMERGED --pattern "Hospital_IFC4_*.ifc" --output Hospital_extracted.db --library library/component_library.db --disc-map Hospital_IFC4_ARC=ARC Hospital_IFC4_MECH=MEP` |
| **Single IFC file** | `python3 DAGCompiler/python/extractIFCtoDB.py --ifc model.ifc -o model_extracted.db --library library/component_library.db` |
| **Add missed classes** | `python3 scripts/topup_extracted_db.py --ifc source.ifc --db existing_extracted.db --classes IfcRoof,IfcBeam` |
| **Fix existing DB** | `ExtractionPostProcessor.java` (unit scale, discipline refinement) |

Building-specific extraction commands: see each `docs/{Building}Analysis.md`.

## Key Design Decisions (S168-S172)

1. **`geom.iterator()` not `create_shape()`** — iterator has built-in C++ dedup + instancing (S172)
2. **`USE_WORLD_COORDS=False`** — local coords for mesh dedup. Same door = same hash (S168)
3. **Mesh BLOBs in `component_library.db`** — singleton library, not per-building (S168)
4. **Parallel discipline extraction** — all disciplines concurrently, merge at DB level (S172)
5. **Batch-commit every 1000 elements** — enables concurrent extractions (S170)
6. **Site normalization** — subtract centroid for georeferenced IFC files (S169)
7. **DB-level merge preferred over IFC merge** — avoids dropped disciplines (Clinic lesson)

See `reference/README.md` §IfcOpenShell/Bonsai for community alignment.

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

## 5D BOQ / QTO

`scripts/simple_qto_extract.py` — copied from Federation addon (`boq/simple_qto_extract.py`),
adapted for our extracted DB schema. Reads `elements_meta` + `elements_rtree`, produces
`simple_qto` table with LINEAR/AREA/VOLUME/COUNT quantities + Malaysian RM unit costs.

```bash
python3 scripts/simple_qto_extract.py DAGCompiler/lib/input/LTU_AHouse_extracted.db
python3 scripts/simple_qto_extract.py DAGCompiler/lib/input/Clinic_extracted.db
```

| Building | QTO Lines | Grand Total (RM) |
|---|---|---|
| LTU A-House | 133 | 47,395,012 |
| Clinic | 41 | 11,457,516 |

The `simple_qto` table is written into the extracted DB — query it with:
```sql
SELECT discipline, ifc_class, storey, measurement_type, element_count,
       total_quantity, uom, total_cost_rm
FROM simple_qto ORDER BY total_cost_rm DESC;
```

## 4D Construction Schedule

`scripts/schedule_generator.py` — copied from Federation addon (`schedule/schedule_generator.py`).
Generates construction schedule from extracted DB using productivity rates and precedence rules.
Project name is derived dynamically from the DB filename.

```bash
python3 scripts/schedule_generator.py DAGCompiler/lib/input/LTU_AHouse_extracted.db
python3 scripts/schedule_generator.py DAGCompiler/lib/input/Clinic_extracted.db
```

Writes `construction_schedule` table into the extracted DB. Exports to Excel via
`schedule/excel_export.py` (in Federation addon). Includes phase distribution, discipline
breakdown, and Gantt chart data.

| Building | Tasks | Phases | Result |
|---|---|---|---|
| LTU A-House | 133 | All phases populated | Excel with charts |
| Clinic | 83 | All phases populated | Excel with charts |

See `docs/Enterprise.md` §4D for the scheduling algorithm (topological sort on
`CONSTRUCTION_SEQUENCE_RULES` by storey → phase → discipline).
