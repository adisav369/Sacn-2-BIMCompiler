# Scan-to-BOM Reference DB Spec

**Status:** Phase 1 of the Scan-to-BIM roadmap — contract definition, no code.
**Purpose:** the exact SQLite schema a point-cloud extractor must produce so that
everything downstream (`ExtractionPopulator`, `StructuralBomBuilder`, `ScopeBomBuilder`,
`BomValidator`, `CompilationPipeline`, BIM_COBOL's verbs, BIMEyes' gates) runs **unmodified**.
This schema is not new — it is `DAGCompiler/python/extractIFCtoDB.py`'s existing
`REFERENCE_SCHEMA`, which the current IFC pipeline already produces and every downstream
consumer already expects. A point-cloud extractor's only job is to populate the same tables
from a different source.

Every table below was checked against actual Java call sites (`grep -rl "FROM <table>"` across
`IFCtoBOM/src/main`, `DAGCompiler/src/main`, `BIM_COBOL/src/main`), not inferred from comments.
Usage counts are file counts, not call counts, but they separate "load-bearing" from "unused by
the core pipeline" reliably.

---

## 1. Required tables (v1 must-have)

### `elements_meta` — the element registry (43 consuming files — the most load-bearing table in the schema)

```sql
CREATE TABLE elements_meta (
    id INTEGER PRIMARY KEY,
    guid TEXT UNIQUE NOT NULL,
    discipline TEXT NOT NULL,       -- ARC/STR/MEP/ELEC/FP/... — see §4
    ifc_class TEXT NOT NULL,        -- semantic label — see §3, the crux of the whole project
    element_name TEXT,
    element_type TEXT,
    storey TEXT,
    material_name TEXT,
    material_rgba TEXT,
    is_anchor INTEGER DEFAULT 0     -- 1 = void-consumed logical anchor, never rendered/counted
);
```

- `guid`: any stable unique string per detected element. Does not need to be a real IFC GUID —
  downstream code treats it as an opaque key.
- `discipline` and `ifc_class` are `NOT NULL` — every element must get *some* value for both.
  `infer_discipline()`'s own fallback is `"ARC"` when a class is unmapped — cheap to replicate,
  and it means a classifier that's uncertain about discipline can default safely rather than block.
- `material_name`/`material_rgba`: **optional in practice** even though the columns exist — no
  consumer treats a NULL here as an error (confirmed: `get_material_for_element()` is IFC-relationship
  specific and has no point-cloud equivalent; the columns will just carry NULL until a materials
  phase exists). Leave NULL for v1 rather than inventing a color.
- `is_anchor`: leave at the default `0`. This flag exists for a specific IFC opening/void pattern
  (§ANCHOR) that has no point-cloud analogue yet.

### `elements_rtree` — spatial index (30 files)

```sql
CREATE VIRTUAL TABLE elements_rtree USING rtree(
    id, minX, maxX, minY, maxY, minZ, maxZ
);
```

`id` joins 1:1 to `elements_meta.id`. This is the AABB every gate, every BOM builder, and C9
(fidelity) reads. Pure geometry — a point-cloud cluster's bounding box populates this directly.

### `spatial_structure` — Building/Storey/Space hierarchy (9 files)

```sql
CREATE TABLE spatial_structure (
    guid TEXT PRIMARY KEY,
    type TEXT NOT NULL,             -- 'IfcBuilding' | 'IfcBuildingStorey' | 'IfcSpace'
    name TEXT,
    parent_guid TEXT,
    object_type TEXT,
    predefined_type TEXT,
    center_x REAL, center_y REAL, center_z REAL,
    size_x REAL, size_y REAL, size_z REAL,
    elevation REAL                  -- IfcBuildingStorey.Elevation equivalent; NULL for Building/Space
);
```

**Building and Storey rows are required** — `StructuralBomBuilder` groups elements by storey and
needs at least one storey row per floor. **`IfcSpace`-type rows (rooms) are optional for v1** —
verified directly in `ScopeBomBuilder.discoverIfcSpaces()`: it returns an empty list gracefully
when there are no `IfcSpace` rows, and `StructuralBomBuilder` falls back to flat floor-level BOMs
with no per-room SET BOMs. This is the cleanest place to cut scope: ship storey-level grouping
first, add room segmentation later without touching any Java.

### `base_geometries` + `element_instances` — mesh storage and linkage (11 + 6 files)

```sql
CREATE TABLE base_geometries (
    geometry_hash TEXT PRIMARY KEY,
    vertices BLOB, faces BLOB, vertex_count INTEGER, face_count INTEGER
);
CREATE TABLE element_instances (
    guid TEXT PRIMARY KEY,
    geometry_hash TEXT REFERENCES base_geometries(geometry_hash)
);
```

Required for the schema to be complete, but **the mesh fidelity bar is low for v1** — see §5. The
compiler does not consume this mesh to compile (it instances pre-existing library geometry
matched by `product_type` + dimensions); this table mainly feeds the C8/C10 fidelity gates and
the library gap-fill step.

### `element_transforms` — placement (3 files, but load-bearing where used)

```sql
CREATE TABLE element_transforms (
    guid TEXT PRIMARY KEY,
    center_x REAL, center_y REAL, center_z REAL,
    rotation_x REAL DEFAULT 0, rotation_y REAL DEFAULT 0, rotation_z REAL DEFAULT 0,
    bbox_x REAL, bbox_y REAL, bbox_z REAL,
    transform_source TEXT
);
```

`rotation_z` (yaw) is the one that matters for wall/furniture orientation logic downstream
(`ExtractionPopulator.classifyOrientationV2()`). `rotation_x`/`rotation_y` default to 0 — fine to
leave at 0 for v1 (point clouds of building interiors are overwhelmingly axis-aligned in roll/pitch;
tilted scans are a later-phase problem). Set `transform_source` to something identifying the
point-cloud pipeline (e.g. `'pointcloud:v1'`) — it's a free-text provenance field, not validated
against an enum anywhere in Java.

---

## 2. Optional tables — real features, skip for v1 without breaking anything

Verified via source, not inferred:

| Table | Consumer | Fallback when absent |
|---|---|---|
| `rel_aggregates` | `StructuralBomBuilder` | `extractionConn` param may be `null`; even when present, a missing table is caught (`catch (SQLException e) { // rel_aggregates may not exist — proceed without assemblies }`) and the builder produces flat (non-nested) BOM lines instead. |
| `rel_contained_in_space` | `ScopeBomBuilder` | LEFT JOIN against `spatial_structure`; absent → no elements assigned to any space → same empty-room fallback as above. |
| `rel_fills_host` | `ExtractionPopulator` (door/window → host wall linking, §R21) | 1 consumer file — a specific enrichment step, not a hard requirement for compile to succeed. |
| `material_layers` + `rel_material_layer_set` + `component_geometry_layers` | Python-side only (`compile_layer_geometry()`), **zero Java consumers** | Multi-layer wall/slab slicing at extraction time. Skip entirely for v1 — ship single-envelope geometry per element. This is the exact same LOD tradeoff the current IFC pipeline already makes for elements with missing layer metadata (`§ILLEGAL_LOD_FALLBACK`), so shipping envelope-only geometry for point-cloud buildings is consistent with, not a regression from, current behavior. |

## 3. Out of scope for v1 — zero Java consumers, confirmed

`rel_adjacency`, `datum_plane`, `rel_anchored`, `rel_spans`, `surface_styles`, `port_elements`,
`port_connections` — all show **zero** references from `IFCtoBOM`, `DAGCompiler`, or `BIM_COBOL`
source. These feed spatial-dependency-graph tooling and other analysis scripts outside the core
compile/gate path (some of that tooling was archived in the earlier cleanup pass; some may resurface
later). None of them block extract → classify → BOM → compile → gates. Don't build a point-cloud
equivalent for any of these until something in the kept pipeline actually asks for one.

---

## 4. Decision: label vocabulary — emit IFC-class-shaped strings

`elements_meta.ifc_class` is read by 43 files. Tracing what actually happens to that string value
(not the column's origin) shows every consumer keys off it as an opaque string match, never as
anything IFC-file-specific:

- `ProductRegistrar.deriveProductType()` — a 13-case `switch` on literal strings (`"IfcWall"`,
  `"IfcDoor"`, ...)
- `ProductResolver.formatDims()` — a 6-case switch on the same kind of literal
- `extractIFCtoDB.py`'s own `DISCIPLINE_MAP` — a ~30-entry `dict[str, str]`, default `"ARC"`

None of this parses an actual `.ifc` file or checks against the real IFC4 schema — it's string
matching against a fixed vocabulary the codebase's own authors chose. **Decision: the point-cloud
classifier should emit that same vocabulary** (`"IfcWall"`, `"IfcDoor"`, `"IfcWindow"`,
`"IfcColumn"`, `"IfcSlab"`, `"IfcCovering"`, `"IfcFurniture"`, ...) even though no IFC file is
involved. This is not fidelity theater — it's the literal string key every downstream switch/dict
already matches on, so doing anything else just means building and maintaining a second parallel
mapping table for zero benefit. `DISCIPLINE_MAP`'s ~30 entries and `deriveProductType()`'s 13
cases together define the vocabulary's practical floor for v1 — a classifier that can distinguish
those categories is sufficient to drive the whole downstream pipeline.

## 5. Decision: MVP mesh fidelity — gate on C9 only, defer C8/C10

Verified directly in `scripts/rosetta_fidelity.sh`'s `_c9_query()`: the C9 (per-axis dimension,
1mm tolerance) query joins only `elements_rtree` — no reference to `base_geometries` or
`geometry_hash` anywhere in it. C9 is purely AABB-based.

C8 (geometry diversity — distinct `geometry_hash` count per product type) and C10 (mesh centroid
offset) do need real per-instance mesh data in `base_geometries`.

**Decision:** ship v1 gated on C9 only. `base_geometries`/`element_instances` still need to exist
(schema completeness, and the compiler's library-matching step wants *a* `geometry_hash` per
element), but the mesh behind that hash can be a coarse fitted-primitive or convex-hull box for
v1 rather than a faithful tessellation of the scanned surface. Treat C8/C10 as a stretch goal once
real per-element mesh fitting exists — don't block the first working round-trip on it.

## 6. What "done" looks like for Phase 1

This document *is* Phase 1's deliverable — no code changes. The next phase (segmentation) can
start against a fixed target: populate `elements_meta`, `elements_rtree`, `spatial_structure`
(Building + Storey rows), `element_transforms`, `base_geometries` + `element_instances`, using the
IFC-class-shaped label vocabulary from §4, and validate early against a synthetic point cloud
sampled from Sample House's own already-known-correct geometry (per the roadmap's Phase 6.24) —
not real scan hardware — so schema mismatches surface immediately against known ground truth.
